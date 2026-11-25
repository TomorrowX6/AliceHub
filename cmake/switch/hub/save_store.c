#include "save_store.h"
#include "fs_util.h"
#include "savedata.h"

#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

enum CopyMode { COPY_ALL, COPY_PUBLIC, COPY_PC, COPY_MISSING };
enum { SAVE_PATH_SIZE = 1024, SAVE_TREE_DEPTH = 32 };
typedef struct {
    const atomic_bool *cancelled;
    HubSaveResult *result;
    unsigned char buffer[16384], compare[16384];
} Store;

static bool path_join(char out[SAVE_PATH_SIZE], const char *dir, const char *name)
{
    if ((size_t)snprintf(out, SAVE_PATH_SIZE, "%s/%s", dir, name) < SAVE_PATH_SIZE)
        return true;
    errno = ENAMETOOLONG;
    return false;
}

static bool inspect(const char *path, mode_t *kind)
{
    struct stat st;
#ifdef __SWITCH__
    if (!stat(path, &st)) {
#else
    if (!lstat(path, &st)) {
#endif
        *kind = st.st_mode & S_IFMT;
        if (*kind == S_IFDIR || *kind == S_IFREG)
            return true;
        errno = EINVAL;
        return false;
    }
    *kind = 0;
    return errno == ENOENT;
}

static bool ensure_dir(const char *path)
{
    mode_t kind;
    if (!inspect(path, &kind))
        return false;
    if (kind == S_IFDIR)
        return true;
    if (kind) {
        errno = ENOTDIR;
        return false;
    }
    return !mkdir(path, 0777);
}

static bool check_parents(const char *path, bool create)
{
    char parent[SAVE_PATH_SIZE];
    if ((size_t)snprintf(parent, sizeof(parent), "%s", path) >= sizeof(parent)) {
        errno = ENAMETOOLONG;
        return false;
    }
    for (char *p = parent + 1; (p = strchr(p, '/')); ++p) {
        if (p[-1] == ':')
            continue;
        *p = '\0';
        if (create) {
            if (!ensure_dir(parent))
                return false;
        } else {
            mode_t kind;
            if (!inspect(parent, &kind))
                return false;
            if (kind != S_IFDIR) {
                errno = kind ? ENOTDIR : ENOENT;
                return false;
            }
        }
        *p = '/';
    }
    return true;
}

static bool should_continue(const Store *store)
{
    if (!store->cancelled || !atomic_load(store->cancelled))
        return true;
    errno = ECANCELED;
    return false;
}

static void discard_tree(const char *path, unsigned depth)
{
    if (depth > SAVE_TREE_DEPTH + 2)
        return;
    DIR *dir = opendir(path);
    if (!dir) {
        unlink(path);
        return;
    }
    struct dirent *entry;
    while ((entry = readdir(dir))) {
        char child[SAVE_PATH_SIZE];
        mode_t kind;
        if (strcmp(entry->d_name, ".") && strcmp(entry->d_name, "..") &&
            path_join(child, path, entry->d_name) && inspect(child, &kind)) {
            if (kind == S_IFDIR)
                discard_tree(child, depth + 1);
            else if (kind == S_IFREG)
                unlink(child);
        }
    }
    closedir(dir);
    rmdir(path);
}

static bool copy_file(Store *store, const char *src, const char *dst)
{
    FILE *in = fopen(src, "rb");
    if (!in)
        return false;
    FILE *out = fopen(dst, "wb");
    bool ok = out != NULL;
    size_t n;
    while (ok && (ok = should_continue(store)) &&
           (n = fread(store->buffer, 1, sizeof(store->buffer), in))) {
        ok = fwrite(store->buffer, 1, n, out) == n;
    }
    if (ferror(in))
        ok = false;
    fclose(in);
    if (out && fclose(out))
        ok = false;
    return ok;
}

static bool same_file(Store *store, const char *a, const char *b, bool *same)
{
    FILE *left = fopen(a, "rb");
    if (!left)
        return false;
    FILE *right = fopen(b, "rb");
    bool ok = right != NULL;
    *same = false;
    while (ok && (ok = should_continue(store))) {
        size_t n = fread(store->buffer, 1, sizeof(store->buffer), left);
        size_t m = fread(store->compare, 1, sizeof(store->compare), right);
        if (ferror(left) || ferror(right)) {
            ok = false;
            break;
        }
        if (n != m || memcmp(store->buffer, store->compare, n))
            break;
        if (n < sizeof(store->buffer)) {
            *same = true;
            break;
        }
    }
    if (right)
        fclose(right);
    fclose(left);
    return ok;
}

static bool copy_tree(Store *store, const char *source, const char *dest,
                      enum CopyMode mode, unsigned depth, unsigned *count)
{
    if (depth > SAVE_TREE_DEPTH) {
        errno = ELOOP;
        return false;
    }
    DIR *dir = opendir(source);
    if (!dir)
        return false;
    bool ok = true;
    struct dirent *entry;
    while ((ok = should_continue(store))) {
        errno = 0;
        entry = readdir(dir);
        if (!entry) {
            ok = !errno;
            break;
        }
        const char *name = entry->d_name;
        if (!strcmp(name, ".") || !strcmp(name, "..") ||
            (mode != COPY_ALL && *name == '.'))
            continue;
        if (strpbrk(name, "\\:")) {
            errno = EINVAL;
            ok = false;
            break;
        }
        char src[SAVE_PATH_SIZE], dst[SAVE_PATH_SIZE];
        mode_t sk, dk;
        if (!(ok = path_join(src, source, name) && path_join(dst, dest, name) &&
                   inspect(src, &sk) && inspect(dst, &dk)))
            break;
        if (!sk) {
            errno = ENOENT;
            ok = false;
            break;
        }
        if (dk && dk != sk) {
            if (mode == COPY_MISSING) {
                ++store->result->conflicts;
                continue;
            }
            errno = EINVAL;
            ok = false;
            break;
        }
        if (sk == S_IFDIR) {
            if (!(ok = ensure_dir(dst) && copy_tree(store, src, dst, mode, depth + 1, count)))
                break;
            continue;
        }
        if (mode == COPY_MISSING && dk) {
            bool same;
            if (!(ok = same_file(store, src, dst, &same)))
                break;
            if (!same)
                ++store->result->conflicts;
            continue;
        }
        if (mode == COPY_PC) {
            const char *suffix = strrchr(name, '.');
            if (suffix && (!strcasecmp(suffix, ".bak") || !strcasecmp(suffix, ".log")))
                continue;
        }
        bool convert = mode == COPY_PC && x4ss_is_container_file(src);
        errno = 0;
        if (!(ok = convert ? x4ss_convert_to_gdss_file(src, dst) : copy_file(store, src, dst))) {
            if (!errno)
                errno = EIO;
            if (convert)
                snprintf(store->result->error, sizeof(store->result->error),
                         "Could not convert %.115s to PC format.", name);
            break;
        }
        if (convert)
            ++store->result->converted;
        ++*count;
    }
    int failure = errno;
    closedir(dir);
    errno = failure;
    return ok;
}

static bool start_stage(const char *dest, char stage[SAVE_PATH_SIZE], char next[SAVE_PATH_SIZE])
{
    const char *slash = strrchr(dest, '/');
    for (unsigned i = 0; i < 10000; ++i) {
        if (!slash || (size_t)snprintf(stage, SAVE_PATH_SIZE, "%.*s/.save-stage-%04u",
                                       (int)(slash - dest), dest, i) >= SAVE_PATH_SIZE) {
            errno = ENAMETOOLONG;
            break;
        }
        if (!mkdir(stage, 0700))
            return path_join(next, stage, "next") && ensure_dir(next);
        if (errno != EEXIST)
            break;
    }
    *stage = '\0';
    return false;
}

static bool backup_path(const char *backups, char path[SAVE_PATH_SIZE])
{
    char id[48], stamp[24];
    time_t now = time(NULL);
    struct tm tm;
    if (!gmtime_r(&now, &tm) || !strftime(stamp, sizeof(stamp), "%Y%m%d-%H%M%S", &tm))
        strcpy(stamp, "00000000-000000");
    for (unsigned i = 0; i < 10000; ++i) {
        snprintf(id, sizeof(id), "SaveData-%s-%04u", stamp, i);
        mode_t kind;
        if (!path_join(path, backups, id) || !inspect(path, &kind))
            return false;
        if (!kind)
            return true;
    }
    errno = ENOSPC;
    return false;
}

static bool latest_backup(const char *backups, char path[SAVE_PATH_SIZE])
{
    mode_t kind;
    if (!check_parents(backups, false) || !inspect(backups, &kind))
        return false;
    if (kind != S_IFDIR) {
        errno = kind ? ENOTDIR : ENOENT;
        return false;
    }
    DIR *dir = opendir(backups);
    if (!dir)
        return false;
    char latest[sizeof("SaveData-20260101-000000-0000")] = "";
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *entry = readdir(dir);
        if (!entry) {
            ok = !errno;
            break;
        }
        const char *name = entry->d_name;
        if (strlen(name) != sizeof(latest) - 1 || strncmp(name, "SaveData-", 9) ||
            strspn(name + 9, "0123456789-") != sizeof(latest) - 10 || strcmp(name, latest) <= 0)
            continue;
        if (!(ok = path_join(path, backups, name)))
            break;
        if (!hub_save_recover(path))
            continue;
        if (!(ok = inspect(path, &kind)))
            break;
        if (kind == S_IFDIR)
            strcpy(latest, name);
    }
    int failure = errno;
    closedir(dir);
    errno = failure;
    if (!ok)
        return false;
    if (!*latest) {
        errno = ENOENT;
        return false;
    }
    return path_join(path, backups, latest);
}

static bool transfer(Store *store, const char *src, const char *dest, const char *backups,
                     enum CopyMode mode, bool merge, bool replace_empty, unsigned *copied)
{
    char stage[SAVE_PATH_SIZE] = "", next[SAVE_PATH_SIZE];
    bool pending = false;
    mode_t source, kind;
    if (!check_parents(src, false) || !hub_save_recover(src) || !inspect(src, &source))
        return false;
    if (source != S_IFDIR) {
        errno = source ? ENOTDIR : ENOENT;
        return false;
    }
    bool ok = check_parents(dest, true) && hub_save_recover(dest) && inspect(dest, &kind);
    if (ok && kind && kind != S_IFDIR) {
        errno = ENOTDIR;
        return false;
    }
    if (ok)
        ok = start_stage(dest, stage, next);
    unsigned old_count = 0;
    if (ok && merge && kind)
        ok = copy_tree(store, dest, next, COPY_ALL, 0, &old_count);
    if (ok)
        ok = copy_tree(store, src, next, mode, 0, copied);
    if (ok && (*copied || replace_empty)) {
        if (backups && kind) {
            char backup[SAVE_PATH_SIZE];
            unsigned count = 0;
            ok = backup_path(backups, backup) &&
                 transfer(store, dest, backup, NULL, COPY_ALL, false, false, &count);
        }
        if (ok)
            ok = should_continue(store) && hub_save_publish(stage, dest, kind != 0, &pending);
    }
    int failure = errno;
    if (pending)
        snprintf(store->result->error, sizeof(store->result->error),
                 "Save publication is pending recovery in %.110s.", stage);
    else if (*stage)
        discard_tree(stage, 0);
    errno = failure;
    return ok;
}

static bool run(Store *store, const char *root, const HubGame *game, HubSaveOp op)
{
    char local[HUB_PATH_SIZE], engine[SAVE_PATH_SIZE], game_dir[SAVE_PATH_SIZE],
         base[SAVE_PATH_SIZE], target[SAVE_PATH_SIZE], sync[SAVE_PATH_SIZE], backups[SAVE_PATH_SIZE];
    HubGame validated;
    if (!hub_game_init(&validated, game->engine, game->name)) {
        errno = EINVAL;
        return false;
    }
    if (!hub_game_save_path(root, &validated, local)) {
        errno = ENAMETOOLONG;
        return false;
    }
    const char *save_name = strrchr(local, '/');
    save_name = (save_name && save_name[1]) ? save_name + 1 : validated.name;
    if (!path_join(base, root, "save-manager") ||
        !path_join(engine, base, hub_engines[validated.engine].id) ||
        !path_join(game_dir, engine, save_name) ||
        !path_join(backups, game_dir, "Backups") || !path_join(sync, game_dir, "Sync"))
        return false;

    unsigned *copied = &store->result->copied;
    mode_t kind;
    switch (op) {
    case HUB_SAVE_PREPARE:
        return path_join(target, game_dir, "Import") && check_parents(target, true) &&
               ensure_dir(target) && ensure_dir(sync);
    case HUB_SAVE_IMPORT: {
        char nested[SAVE_PATH_SIZE];
        if (!path_join(target, game_dir, "Import") || !path_join(nested, target, "SaveData") ||
            !check_parents(nested, false) ||
            !inspect(nested, &kind))
            return false;
        return transfer(store, kind == S_IFDIR ? nested : target, local, backups,
                        COPY_PUBLIC, true, false, copied);
    }
    case HUB_SAVE_EXPORT:
    case HUB_SAVE_EXPORT_PC:
        return path_join(target, game_dir, op == HUB_SAVE_EXPORT_PC ? "PC_SaveData" : "SaveData") &&
               transfer(store, local, target, NULL,
                        op == HUB_SAVE_EXPORT_PC ? COPY_PC : COPY_PUBLIC, false, false, copied);
    case HUB_SAVE_BACKUP:
        return backup_path(backups, target) &&
               transfer(store, local, target, NULL, COPY_ALL, false, false, copied);
    case HUB_SAVE_RESTORE:
        return latest_backup(backups, target) &&
               transfer(store, target, local, backups, COPY_ALL, false, true, copied);
    case HUB_SAVE_SYNC: {
        if (!check_parents(sync, true) || !hub_save_recover(sync) || !ensure_dir(sync) ||
            !hub_save_recover(local) || !inspect(local, &kind) ||
            !transfer(store, sync, local, backups, COPY_MISSING, true, false, copied))
            return false;
        if (!kind && !*copied)
            return true;
        unsigned conflicts = store->result->conflicts;
        unsigned sent = 0;
        bool ok = transfer(store, local, sync, NULL, COPY_MISSING, true, false, &sent);
        store->result->conflicts = conflicts;
        *copied += sent;
        return ok;
    }
    case HUB_SAVE_PULL:
        return transfer(store, sync, local, backups, COPY_PUBLIC, true, false, copied);
    case HUB_SAVE_PUSH:
        return transfer(store, local, sync, NULL, COPY_PUBLIC, true, false, copied);
    case HUB_SAVE_OP_COUNT:
        break;
    }
    errno = EINVAL;
    return false;
}

bool hub_save_run(const char *root, const HubGame *game, HubSaveOp op,
                  const atomic_bool *cancelled, HubSaveResult *result)
{
    *result = (HubSaveResult){0};
    Store *store = calloc(1, sizeof(*store));
    if (!store) {
        snprintf(result->error, sizeof(result->error), "Not enough memory for save transfer.");
        return false;
    }
    store->cancelled = cancelled;
    store->result = result;
    bool ok = should_continue(store) && run(store, root, game, op);
    int failure = errno;
    free(store);
    if (!ok && !*result->error)
        snprintf(result->error, sizeof(result->error), "%s",
                 failure == ECANCELED ? "Cancelled." : strerror(failure ? failure : EIO));
    return ok;
}
