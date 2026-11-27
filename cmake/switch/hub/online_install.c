#include "online_install.h"
#include "install_transaction.h"

#include <archive.h>
#include <archive_entry.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <unistd.h>

#define CACHE_ROOT HUB_ROOT "/.online-v1"
#define HEADROOM UINT64_C(67108864)
#define EXPANSION_LIMIT (UINT64_C(8) * 1024 * 1024 * 1024)

static bool fail(OnlineTransfer *transfer, const char *reason)
{
    snprintf(transfer->error, sizeof(transfer->error), "%s", reason);
    return false;
}

static bool join(char path[HUB_PATH_SIZE], const char *base, const char *name)
{
    return (size_t)snprintf(path, HUB_PATH_SIZE, "%s/%s", base, name) < HUB_PATH_SIZE;
}

static bool cache_path(const char *id, char path[HUB_PATH_SIZE])
{
    return *id && strspn(id, "abcdefghijklmnopqrstuvwxyz0123456789_-") == strlen(id) &&
           join(path, CACHE_ROOT, id);
}

static bool ensure_dir(const char *path)
{
    if (!mkdir(path, 0777))
        return true;
    struct stat st;
    return errno == EEXIST && !lstat(path, &st) && S_ISDIR(st.st_mode);
}

bool online_clear_cache(const char *id)
{
    char cache[HUB_PATH_SIZE];
    if (!cache_path(id, cache))
        return false;
    DIR *dir = opendir(cache);
    if (!dir)
        return errno == ENOENT;
    bool okay = true;
    for (;;) {
        errno = 0;
        const struct dirent *e = readdir(dir);
        if (!e) {
            okay = okay && !errno;
            break;
        }
        size_t len = strspn(e->d_name, "0123456789abcdef");
        if (len != ONLINE_SHA_SIZE - 1 ||
            (strcmp(e->d_name + len, ".part") && strcmp(e->d_name + len, ".resume")))
            continue;
        char path[HUB_PATH_SIZE];
        if (!join(path, cache, e->d_name) || unlink(path))
            okay = false;
    }
    closedir(dir);
    return okay && !rmdir(cache);
}

static bool available(uint64_t *bytes)
{
    struct statvfs st;
    if (statvfs(HUB_ROOT, &st))
        return false;
    *bytes = (uint64_t)st.f_bavail * st.f_frsize;
    return true;
}

static bool owned(const char *root, const char *id)
{
    char path[HUB_PATH_SIZE], owner[256];
    if (!join(path, root, ONLINE_MARKER))
        return false;
    FILE *f = fopen(path, "rb");
    if (!f)
        return false;
    size_t n = fread(owner, 1, sizeof(owner), f);
    bool okay = n == strlen(id) && !memcmp(owner, id, n) && !ferror(f);
    fclose(f);
    return okay;
}

static bool plan_target(const char *id, const OnlineManifest *manifest,
                        OnlinePlan *plan, OnlineTransfer *transfer)
{
    HubEngine engine = HUB_ENGINE_COUNT;
    for (size_t i = 0; i < manifest->count; ++i) {
        const OnlineEntry *entry = &manifest->files[i];
        plan->archives |= entry->extract;
        HubEngine candidate = online_boot_marker(entry->path);
        if (candidate != HUB_ENGINE_COUNT && (engine == HUB_ENGINE_COUNT || candidate > engine))
            engine = candidate;
    }
    char path[HUB_PATH_SIZE];
    if (engine == HUB_ENGINE_COUNT || !hub_game_init(&plan->game, engine, manifest->folder) ||
        !hub_game_path(HUB_ROOT, &plan->game, path))
        return fail(transfer, "No supported Switch engine or install path in this manifest");
    struct stat st;
    plan->conflict = plan->installed = false;
    if (!lstat(path, &st)) {
        plan->installed = S_ISDIR(st.st_mode) && owned(path, id);
        plan->conflict = !plan->installed;
    } else if (errno != ENOENT) {
        return fail(transfer, "Could not check the SD install path");
    }
    plan->stage_bytes = manifest->direct_bytes;
    if (!available(&plan->available_bytes))
        return fail(transfer, "Could not read free SD space");
    return true;
}

bool online_inspect(const char *id, OnlinePlan *plan, OnlineTransfer *transfer)
{
    OnlineManifest manifest = {0};
    char cache[HUB_PATH_SIZE];
    *plan = (OnlinePlan){0};
    if (!cache_path(id, cache) || !online_fetch_manifest(id, &manifest, transfer))
        return false;
    bool okay = plan_target(id, &manifest, plan, transfer);
    if (okay)
        for (size_t i = 0; i < manifest.count; ++i)
            plan->download_bytes += manifest.files[i].size - online_cached_bytes(id, &manifest.files[i], cache);
    online_manifest_free(&manifest);
    return okay;
}

static bool copy_payload(const char *source, const char *dest, const OnlineEntry *entry,
                         OnlineTransfer *transfer)
{
    FILE *in = fopen(source, "rb");
    if (!in)
        return fail(transfer, "Could not read verified payload");
    int fd = open(dest, O_WRONLY | O_CREAT | O_EXCL, 0666);
    FILE *out = fd >= 0 ? fdopen(fd, "wb") : NULL;
    bool okay = out != NULL;
    if (out) {
        unsigned char block[32768];
        uint64_t bytes = 0;
        while (!transfer->stopping(transfer->context)) {
            size_t n = fread(block, 1, sizeof(block), in);
            if (!n || fwrite(block, 1, n, out) != n)
                break;
            bytes += n;
        }
        okay = !ferror(in) && !ferror(out) && bytes == entry->size &&
               !fflush(out) && !fsync(fd);
        okay = !fclose(out) && okay;
    } else if (fd >= 0) {
        close(fd);
    }
    fclose(in);
    return (okay && online_verified_file(dest, entry, transfer)) ||
           fail(transfer, "Could not stage and verify game file");
}

static bool unpack_file(struct archive *arc, int fd, uint64_t bytes, OnlineTransfer *transfer)
{
    unsigned char block[32768];
    for (;;) {
        if (transfer->stopping(transfer->context))
            return false;
        la_ssize_t n = archive_read_data(arc, block, sizeof(block));
        if (n <= 0)
            return n == 0 && !bytes && !fsync(fd);
        if ((uint64_t)n > bytes)
            return false;
        for (ssize_t offset = 0; offset < n;) {
            ssize_t written = write(fd, block + offset, n - offset);
            if (written <= 0)
                return false;
            offset += written;
        }
        bytes -= n;
    }
}

static bool unpack(const char *path, HubInstallTransaction *tx, OnlineTransfer *transfer,
                   uint64_t *expansion_budget)
{
    struct archive *arc = archive_read_new();
    if (!arc)
        return fail(transfer, "Could not open archive");
    bool okay = archive_read_support_format_zip(arc) == ARCHIVE_OK &&
                archive_read_support_format_7zip(arc) == ARCHIVE_OK &&
                archive_read_open_filename(arc, path, 32768) == ARCHIVE_OK;
    size_t count = 0;
    struct archive_entry *entry;
    int result = ARCHIVE_FATAL;
    while (okay && !transfer->stopping(transfer->context) &&
           ((result = archive_read_next_header(arc, &entry)) == ARCHIVE_OK ||
            result == ARCHIVE_WARN)) {
        const char *name = archive_entry_pathname_utf8(entry);
        if (!name)
            name = archive_entry_pathname(entry);
        char relative[HUB_PATH_SIZE], dest[HUB_PATH_SIZE];
        size_t n = name ? strlen(name) : 0;
        if (++count > 65536 || n >= sizeof(relative) || !n) {
            okay = false;
            break;
        }
        memcpy(relative, name, n + 1);
        for (size_t i = 0; i < n; ++i)
            if (relative[i] == '\\')
                relative[i] = '/';
        int type = archive_entry_filetype(entry);
        if (type == AE_IFDIR && relative[n - 1] == '/')
            relative[n - 1] = 0;
        if (!hub_install_relative_path(relative) ||
            !strncasecmp(relative, ONLINE_MARKER, sizeof(ONLINE_MARKER) - 1) ||
            archive_entry_symlink(entry) || archive_entry_hardlink(entry)) {
            okay = false;
            break;
        }
        if (type == AE_IFDIR)
            continue;
        int64_t size = archive_entry_size(entry);
        uint64_t space;
        if (type != AE_IFREG || !archive_entry_size_is_set(entry) || size < 0 ||
            (uint64_t)size > ONLINE_FILE_LIMIT || (uint64_t)size > *expansion_budget ||
            !available(&space) || space < (uint64_t)size + HEADROOM ||
            !hub_install_stage_file(tx, relative, dest)) {
            okay = false;
            break;
        }
        int fd = open(dest, O_WRONLY | O_CREAT | O_EXCL, 0666);
        okay = fd >= 0 && unpack_file(arc, fd, size, transfer);
        if (fd >= 0)
            okay = !close(fd) && okay;
        *expansion_budget -= size;
    }
    archive_read_free(arc);
    return (okay && result == ARCHIVE_EOF) ||
           fail(transfer, "Archive is unsupported, unsafe or too large for the SD card");
}

static bool verify_boot(const char *stage, void *context)
{
    HubEngine engine = *(const HubEngine *)context;
    DIR *dir = opendir(stage);
    if (!dir)
        return false;
    bool okay = false;
    for (const struct dirent *e; !okay && (e = readdir(dir));) {
        char path[HUB_PATH_SIZE];
        struct stat st;
        if (online_boot_marker(e->d_name) != engine || !join(path, stage, e->d_name) ||
            stat(path, &st) || !S_ISREG(st.st_mode))
            continue;
        FILE *f = fopen(path, "rb");
        if (!f)
            continue;
        if (engine == HUB_SYSTEM3) {
            unsigned char b[4];
            if (fread(b, 1, sizeof(b), f) == sizeof(b)) {
                int link = b[0] | b[1] << 8, data = b[2] | b[3] << 8;
                okay = link > 0 && data > link && (data - 1) * 256L < st.st_size;
            }
        } else {
            okay = fgetc(f) != EOF;
        }
        fclose(f);
    }
    closedir(dir);
    return okay;
}

bool online_install(const char *id, const OnlinePlan *expected, OnlineTransfer *transfer,
                    OnlineStep step)
{
    OnlineManifest manifest = {0};
    OnlinePlan plan = {0};
    char cache[HUB_PATH_SIZE], target[HUB_PATH_SIZE];
    HubInstallTransaction *tx = NULL;
    bool *reuse = NULL, okay = false;
    if (!cache_path(id, cache) || !online_fetch_manifest(id, &manifest, transfer))
        return false;
    if (!plan_target(id, &manifest, &plan, transfer))
        goto done;
    if (plan.conflict || plan.installed != expected->installed ||
        strcmp(plan.game.instance_id, expected->game.instance_id)) {
        fail(transfer, "Install target changed or contains a different game; reopen the catalog entry");
        goto done;
    }
    if (!hub_game_path(HUB_ROOT, &plan.game, target) || !(tx = hub_install_begin(HUB_ROOT, &plan.game))) {
        fail(transfer, "Could not begin the SD install transaction");
        goto done;
    }
    if (!plan_target(id, &manifest, &plan, transfer) || plan.conflict || plan.installed != expected->installed) {
        fail(transfer, "Install target changed during staging");
        goto done;
    }
    reuse = calloc(manifest.count, sizeof(*reuse));
    if (!reuse || !ensure_dir(CACHE_ROOT) || !ensure_dir(cache)) {
        fail(transfer, "Could not prepare download cache");
        goto done;
    }
    step(transfer->context, "Checking installed files and free space", 0, manifest.total);
    for (size_t i = 0; i < manifest.count; ++i) {
        const OnlineEntry *entry = &manifest.files[i];
        char old[HUB_PATH_SIZE];
        reuse[i] = plan.installed && !entry->extract && join(old, target, entry->path) &&
                   online_verified_file(old, entry, transfer);
        if (!reuse[i])
            plan.download_bytes += entry->size - online_cached_bytes(id, entry, cache);
        if (transfer->stopping(transfer->context))
            goto done;
    }
    if (plan.available_bytes < plan.stage_bytes + plan.download_bytes + HEADROOM) {
        fail(transfer, "Not enough SD space for remaining downloads and staged files");
        goto done;
    }
    uint64_t completed = 0, expansion_budget = EXPANSION_LIMIT;
    for (int extract = 0; extract <= 1; ++extract) {
        for (size_t i = 0; i < manifest.count; ++i) {
            const OnlineEntry *entry = &manifest.files[i];
            if (entry->extract != (bool)extract)
                continue;
            if (transfer->stopping(transfer->context))
                goto done;
            char payload[HUB_PATH_SIZE], dest[HUB_PATH_SIZE];
            step(transfer->context, entry->path, completed, manifest.total);
            if (reuse[i]) {
                if (!join(payload, target, entry->path))
                    goto done;
            } else if (!online_fetch_file(id, entry, cache, payload, transfer)) {
                goto done;
            }
            if (extract) {
                step(transfer->context, "Unpacking verified archive", completed + entry->size, manifest.total);
                if (!unpack(payload, tx, transfer, &expansion_budget))
                    goto done;
            } else {
                if (!hub_install_stage_file(tx, entry->path, dest) ||
                    !copy_payload(payload, dest, entry, transfer))
                    goto done;
            }
            completed += entry->size;
        }
    }
    char marker[HUB_PATH_SIZE];
    FILE *owner = hub_install_stage_file(tx, ONLINE_MARKER, marker) ? fopen(marker, "wb") : NULL;
    bool marked = owner && fputs(id, owner) >= 0 && !fflush(owner) && !fsync(fileno(owner));
    if (owner)
        marked = !fclose(owner) && marked;
    if (!marked || !hub_install_verify(tx, verify_boot, &plan.game.engine)) {
        fail(transfer, "Staged game failed the engine startup check");
        goto done;
    }
    if (transfer->stopping(transfer->context))
        goto done;
    step(transfer->context, "Committing verified install", completed, manifest.total);
    if (!(okay = hub_install_commit(tx))) {
        fail(transfer, "Could not commit the game transaction");
        goto done;
    }
    tx = NULL;
    online_clear_cache(id);
done:
    if (tx && !hub_install_rollback(tx)) {
        transfer->rollback_failed = true;
        fail(transfer, "SD rollback incomplete; restart the Hub to recover");
    }
    free(reuse);
    online_manifest_free(&manifest);
    return okay;
}
