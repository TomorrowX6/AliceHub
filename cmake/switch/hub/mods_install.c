#include "mods.h"
#include "install_transaction.h"
#include "local_import.h"
#include "cJSON.h"

#include <archive.h>
#include <archive_entry.h>
#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

#define LEDGER ".hub-mod-v1.json"
#define BACKUP ".vanilla.bak"
#define BUFFER (64 * 1024)

static bool update(const ModsCallbacks *cb, const char *message)
{
    return !cb || !cb->update || cb->update(cb->data, message, 0, 0);
}

static bool present(const char *path, bool *found)
{
    struct stat st;
    if (lstat(path, &st)) {
        *found = false;
        return errno == ENOENT;
    }
    *found = true;
    return S_ISREG(st.st_mode);
}

static bool remove_file(const char *path)
{
    bool found;
    return present(path, &found) && (!found || !unlink(path));
}

static bool copy_file(const char *from, const char *to, const ModsCallbacks *cb)
{
    unsigned char *buffer = malloc(BUFFER);
    FILE *in = buffer ? fopen(from, "rb") : NULL;
    FILE *out = in ? fopen(to, "wbx") : NULL;
    bool ok = out != NULL;
    while (ok) {
        size_t len = fread(buffer, 1, BUFFER, in);
        ok = fwrite(buffer, 1, len, out) == len && update(cb, "Copying installed files");
        if (len < BUFFER) {
            ok = ok && feof(in) && !ferror(in);
            break;
        }
    }
    if (out && fclose(out)) ok = false;
    if (in) fclose(in);
    free(buffer);
    return ok;
}

static bool copy_tree(HubInstallTransaction *tx, const char *source, char *relative,
                      unsigned depth, const ModsCallbacks *cb)
{
    DIR *dir = opendir(source);
    if (!dir) return false;
    size_t n = strlen(relative);
    bool ok = true;
    while (ok) {
        errno = 0;
        const struct dirent *entry = readdir(dir);
        if (!entry) { ok = !errno; break; }
        if (!strcmp(entry->d_name, ".") || !strcmp(entry->d_name, "..")) continue;
        char from[HUB_PATH_SIZE], to[HUB_PATH_SIZE];
        struct stat st;
        size_t len = snprintf(relative + n, HUB_PATH_SIZE - n, "%s%s", n ? "/" : "", entry->d_name);
        if (len >= HUB_PATH_SIZE - n || !hub_install_relative_path(relative) ||
            !mods_path(from, source, entry->d_name) || lstat(from, &st) ||
            !update(cb, "Copying installed files")) {
            ok = false;
        } else if (S_ISDIR(st.st_mode)) {
            ok = depth < IMPORT_DEPTH && mods_path(to, hub_install_stage(tx), relative) &&
                 !mkdir(to, 0777) && copy_tree(tx, from, relative, depth + 1, cb);
        } else {
            ok = S_ISREG(st.st_mode) && hub_install_stage_file(tx, relative, to) &&
                 copy_file(from, to, cb);
        }
        relative[n] = 0;
    }
    closedir(dir);
    return ok;
}

static bool safe_overlay_path(const char *path)
{
    if (!hub_install_relative_path(path)) return false;
    for (const char *p = path; p && *p; ) {
        char name[256];
        size_t n = strcspn(p, "/");
        memcpy(name, p, n);
        name[n] = 0;
        bool dir = p[n] != 0;
        if (*p == '.' || import_skip(name, dir) || mods_uncensored_name(name) ||
            import_suffix(name, BACKUP) || import_suffix(name, BACKUP ".tmp") ||
            import_suffix(name, ".asd") || import_suffix(name, ".sav") ||
            import_suffix(name, ".exe") || import_suffix(name, ".torrent") ||
            !strcasecmp(name, "staff.png") ||
            (!strncmp(name, "请读我", strlen("请读我")) && import_suffix(name, ".png")))
            return false;
        p = dir ? p + n + 1 : NULL;
    }
    return true;
}

static const char *string_field(const cJSON *obj, const char *key)
{
    return cJSON_GetStringValue(cJSON_GetObjectItemCaseSensitive(obj, key));
}

static cJSON *load_ledger(const char *dir, bool *found)
{
    char path[HUB_PATH_SIZE];
    *found = false;
    if (!mods_path(path, dir, LEDGER) || !present(path, found)) {
        *found = true;
        return NULL;
    }
    if (!*found) return NULL;
    FILE *in = fopen(path, "rb");
    if (!in) return NULL;
    char *data = malloc(MODS_MAX_MANIFEST + 1);
    size_t n = data ? fread(data, 1, MODS_MAX_MANIFEST + 1, in) : 0;
    cJSON *ledger = NULL;
    if (data && n <= MODS_MAX_MANIFEST && feof(in) && !ferror(in)) {
        data[n] = 0;
        if (!memchr(data, 0, n)) ledger = cJSON_ParseWithOpts(data, NULL, true);
    }
    free(data);
    fclose(in);
    if (!cJSON_IsArray(cJSON_GetObjectItemCaseSensitive(ledger, "files")) ||
        !string_field(ledger, "version")) {
        cJSON_Delete(ledger);
        return NULL;
    }
    return ledger;
}

bool mods_installed(const char *directory, ModsKind kind, ModsInstalled *installed)
{
    *installed = (ModsInstalled){0};
    if (kind == MODS_UNCENSORED) {
        DIR *dir = opendir(directory);
        if (!dir) return false;
        for (const struct dirent *entry; (entry = readdir(dir));) {
            char path[HUB_PATH_SIZE];
            bool found;
            if (mods_uncensored_name(entry->d_name) && mods_path(path, directory, entry->d_name) &&
                present(path, &found) && found) {
                installed->installed = true;
                break;
            }
        }
        closedir(dir);
        return true;
    }
    bool found;
    cJSON *ledger = load_ledger(directory, &found);
    if (!ledger) return !found;
    installed->installed = true;
    const cJSON *id = cJSON_GetObjectItemCaseSensitive(ledger, "id");
    const cJSON *base = cJSON_GetObjectItemCaseSensitive(ledger, "base");
    installed->file_id = cJSON_IsNumber(id) ? id->valueint : 0;
    installed->base_id = cJSON_IsNumber(base) ? base->valueint : 0;
    snprintf(installed->version, sizeof(installed->version), "%s", string_field(ledger, "version"));
    cJSON_Delete(ledger);
    return true;
}

static bool backup_path(char out[HUB_PATH_SIZE], const char *path)
{
    return (size_t)snprintf(out, HUB_PATH_SIZE, "%s" BACKUP, path) < HUB_PATH_SIZE;
}

static bool restore(const char *stage, const cJSON *ledger, const ModsCallbacks *cb)
{
    const cJSON *record;
    cJSON_ArrayForEach(record, cJSON_GetObjectItemCaseSensitive(ledger, "files")) {
        const char *relative = string_field(record, "path");
        const cJSON *original = cJSON_GetObjectItemCaseSensitive(record, "original");
        char path[HUB_PATH_SIZE], backup[HUB_PATH_SIZE];
        bool found;
        if (!update(cb, "Restoring original files") || !relative || !safe_overlay_path(relative) ||
            !cJSON_IsBool(original) || !mods_path(path, stage, relative))
            return false;
        if (cJSON_IsTrue(original)) {
            if (!backup_path(backup, path) || !present(backup, &found) || !found ||
                !remove_file(path) || rename(backup, path))
                return false;
        } else if (!remove_file(path)) {
            return false;
        }
    }
    char path[HUB_PATH_SIZE];
    return mods_path(path, stage, LEDGER) && remove_file(path);
}

static bool clear_uncensored(const char *stage)
{
    DIR *dir = opendir(stage);
    if (!dir) return false;
    bool ok = true;
    for (;;) {
        errno = 0;
        const struct dirent *entry = readdir(dir);
        if (!entry) { ok = !errno; break; }
        char path[HUB_PATH_SIZE];
        if (mods_uncensored_name(entry->d_name) &&
            (!mods_path(path, stage, entry->d_name) || !remove_file(path))) {
            ok = false;
            break;
        }
    }
    closedir(dir);
    return ok;
}

static bool save_ledger(const char *stage, cJSON *ledger, const ModsRelease *release)
{
    char path[HUB_PATH_SIZE];
    const char *version = release ? release->version : "Local archive";
    cJSON_DeleteItemFromObjectCaseSensitive(ledger, "version");
    cJSON_DeleteItemFromObjectCaseSensitive(ledger, "id");
    cJSON_DeleteItemFromObjectCaseSensitive(ledger, "base");
    if (!cJSON_AddStringToObject(ledger, "version", version) ||
        !cJSON_AddNumberToObject(ledger, "id", release ? release->file_id : 0) ||
        !cJSON_AddNumberToObject(ledger, "base", release && release->patch ? release->base_id : 0) ||
        !mods_path(path, stage, LEDGER)) return false;
    char *json = cJSON_PrintUnformatted(ledger);
    FILE *out = json && strlen(json) <= MODS_MAX_MANIFEST ? fopen(path, "wb") : NULL;
    bool ok = out && fputs(json, out) != EOF;
    if (out && fclose(out)) ok = false;
    cJSON_free(json);
    return ok;
}

static bool resolve_relative(const char *stage, char path[HUB_PATH_SIZE])
{
    char dir[HUB_PATH_SIZE];
    size_t n = strlen(stage);
    memcpy(dir, stage, n + 1);
    for (char *p = path; *p;) {
        size_t len = strcspn(p, "/");
        if (n + len + 2 > sizeof(dir)) return false;
        char name[256];
        memcpy(name, p, len);
        name[len] = 0;
        DIR *d = opendir(dir);
        if (!d) return errno == ENOENT;
        unsigned matches = 0;
        for (;;) {
            errno = 0;
            const struct dirent *entry = readdir(d);
            if (!entry) break;
            if (!strcasecmp(entry->d_name, name)) {
                ++matches;
                if (strlen(entry->d_name) == len) memcpy(p, entry->d_name, len);
                else matches = 2;
            }
        }
        bool ok = !errno && matches <= 1;
        closedir(d);
        if (!ok) return false;
        if (!matches || !p[len]) return true;
        dir[n++] = '/';
        memcpy(dir + n, p, len);
        n += len;
        dir[n] = 0;
        p += len + 1;
    }
    return false;
}

static bool overlay_destination(HubInstallTransaction *tx, cJSON *files, char relative[HUB_PATH_SIZE],
                                char path[HUB_PATH_SIZE], const ModsCallbacks *cb)
{
    const char *stage = hub_install_stage(tx);
    if (!resolve_relative(stage, relative) || !hub_install_stage_file(tx, relative, path))
        return false;
    const cJSON *record;
    cJSON_ArrayForEach(record, files) {
        const char *name = string_field(record, "path");
        if (!name) return false;
        if (!strcasecmp(name, relative)) return true;
    }
    bool original, found;
    char backup[HUB_PATH_SIZE];
    if (!present(path, &original) ||
        (original && (!backup_path(backup, path) || !present(backup, &found) || found ||
                      !copy_file(path, backup, cb)))) return false;
    cJSON *entry = cJSON_CreateObject();
    if (!entry || !cJSON_AddStringToObject(entry, "path", relative) ||
        !cJSON_AddBoolToObject(entry, "original", original)) {
        cJSON_Delete(entry);
        return false;
    }
    cJSON_AddItemToArray(files, entry);
    return true;
}

static struct archive *open_archive(const char *path)
{
    struct stat st;
    if (lstat(path, &st) || !S_ISREG(st.st_mode)) return NULL;
    struct archive *a = archive_read_new();
    if (!a) return NULL;
    if (archive_read_support_format_zip(a) != ARCHIVE_OK ||
        archive_read_support_format_rar(a) != ARCHIVE_OK ||
        archive_read_support_format_rar5(a) != ARCHIVE_OK ||
        archive_read_support_format_7zip(a) != ARCHIVE_OK ||
        archive_read_open_filename(a, path, BUFFER) != ARCHIVE_OK) {
        archive_read_free(a);
        return NULL;
    }
    return a;
}

static bool asset(const char *name)
{
    static const char *const ext[] = {".ain", ".afa", ".ex", ".ald", ".bgi", ".wai", ".fnl"};
    for (size_t i = 0; i < sizeof(ext) / sizeof(*ext); ++i)
        if (import_suffix(name, ext[i])) return true;
    return false;
}

static bool archive_path(struct archive_entry *entry, char path[HUB_PATH_SIZE])
{
    const char *name = archive_entry_pathname_utf8(entry);
    if (!name) name = archive_entry_pathname(entry);
    if (!name || strlen(name) >= HUB_PATH_SIZE) return false;
    strcpy(path, name);
    for (char *p = path; *p; ++p) if (*p == '\\') *p = '/';
    return hub_install_relative_path(path) && !archive_entry_symlink(entry) &&
           !archive_entry_hardlink(entry);
}

static bool archive_root(const char *source, bool evenicle2, char root[HUB_PATH_SIZE],
                         const ModsCallbacks *cb)
{
    struct archive *a = open_archive(source);
    if (!a) return false;
    struct archive_entry *entry;
    unsigned best = IMPORT_DEPTH + 1;
    int rc;
    while ((rc = archive_read_next_header(a, &entry)) == ARCHIVE_OK || rc == ARCHIVE_WARN) {
        if (!update(cb, "Reading archive")) { rc = ARCHIVE_FATAL; break; }
        if (archive_entry_filetype(entry) != AE_IFREG) continue;
        char path[HUB_PATH_SIZE];
        if (!archive_path(entry, path)) { rc = ARCHIVE_FATAL; break; }
        if (!safe_overlay_path(path) || !asset(path)) continue;
        char *leaf = strrchr(path, '/');
        size_t n = leaf ? (size_t)(leaf - path + 1) : 0;
        if (evenicle2 && n >= 7 && !strncasecmp(path + n - 7, "mai/sc/", 7) &&
            (n == 7 || path[n - 8] == '/'))
            n -= 7;
        path[n] = 0;
        unsigned depth = 0;
        for (char *p = path; *p; ++p) depth += *p == '/';
        if (depth < best) {
            best = depth;
            strcpy(root, path);
        }
    }
    archive_read_free(a);
    return rc == ARCHIVE_EOF && best <= IMPORT_DEPTH;
}

static bool apply_archive(HubInstallTransaction *tx, cJSON *files, const char *source,
                          bool evenicle2, const ModsCallbacks *cb)
{
    char root[HUB_PATH_SIZE];
    if (!archive_root(source, evenicle2, root, cb)) return false;
    struct archive *a = open_archive(source);
    if (!a) return false;
    struct archive_entry *entry;
    unsigned char *buffer = malloc(BUFFER);
    size_t prefix = strlen(root), count = 0;
    int rc = ARCHIVE_FATAL;
    while (buffer && ((rc = archive_read_next_header(a, &entry)) == ARCHIVE_OK || rc == ARCHIVE_WARN)) {
        if (!update(cb, "Applying archive")) break;
        if (archive_entry_filetype(entry) != AE_IFREG) continue;
        char name[HUB_PATH_SIZE], relative[HUB_PATH_SIZE], path[HUB_PATH_SIZE];
        if (!archive_path(entry, name)) break;
        if (strncmp(name, root, prefix) || !safe_overlay_path(name)) continue;
        const char *start = name + prefix;
        if (evenicle2 && !strncasecmp(start, "mai/sc/", 7) && !strchr(start + 7, '/'))
            start += 7;
        if (!safe_overlay_path(start)) continue;
        strcpy(relative, start);
        if (!overlay_destination(tx, files, relative, path, cb)) break;
        FILE *out = fopen(path, "wb");
        if (!out) break;
        la_ssize_t n;
        bool ok = true;
        while ((n = archive_read_data(a, buffer, BUFFER)) > 0) {
            if (fwrite(buffer, 1, n, out) != (size_t)n || !update(cb, "Applying archive")) {
                ok = false;
                break;
            }
        }
        if (fclose(out) || !ok || n < 0) break;
        ++count;
    }
    free(buffer);
    archive_read_free(a);
    return rc == ARCHIVE_EOF && count;
}

static bool apply_download(HubInstallTransaction *tx, cJSON *files, const ModsRelease *release,
                           const ModsCallbacks *cb)
{
    char temp[HUB_PATH_SIZE];
    const char *name = release->kind == MODS_UNCENSORED ? release->name : ".hub-mod-download";
    bool found;
    if (!cb || !cb->fetch || (release->kind == MODS_UNCENSORED && !mods_uncensored_name(name)) ||
        !hub_install_stage_file(tx, name, temp) || !present(temp, &found) || found ||
        !cb->fetch(cb->data, release, temp))
        return false;
    if (release->kind == MODS_UNCENSORED) return true;
    return apply_archive(tx, files, temp, !strcmp(release->id, "evenicle2"), cb) && !unlink(temp);
}

typedef struct {
    const ModsGame *game;
    HubEngine engine;
} VerifyGame;

static bool verify_game(const char *stage, void *context)
{
    VerifyGame *verify = context;
    return mods_game(stage, verify->engine) == verify->game;
}

bool mods_apply(const char *root, const HubGame *game, ModsKind kind,
                const ModsRelease *release, const ModsRelease *base,
                const char *local_archive, const ModsCallbacks *cb, char error[160])
{
    char source[HUB_PATH_SIZE], rel[HUB_PATH_SIZE] = "";
    HubInstallTransaction *tx = NULL;
    cJSON *ledger = NULL;
    const char *why = "The installed game could not be identified.";
    bool ok = false;
    if (!hub_game_path(root, game, source)) goto done;
    why = "Could not open an install transaction. SD storage may be busy.";
    tx = hub_install_begin(root, game);
    if (!tx) goto done;
    const ModsGame *descriptor = mods_game(source, game->engine);
    why = "The patch does not match this installed game.";
    if (!descriptor || (kind != descriptor->kind &&
        (kind != MODS_UNCENSORED || !mods_game_has_uncensored(descriptor))) ||
        (release && (release->kind != kind || strcmp(release->id, descriptor->id))) ||
        (local_archive && kind == MODS_UNCENSORED) || (local_archive && release))
        goto done;
    why = "Could not stage the installed game. Check free SD space.";
    if (!copy_tree(tx, source, rel, 0, cb)) goto done;
    const char *stage = hub_install_stage(tx);
    if (kind == MODS_UNCENSORED) {
        why = "Could not update the standalone CG patch.";
        if (!clear_uncensored(stage) || (release && !apply_download(tx, NULL, release, cb)))
            goto done;
    } else {
        bool found;
        ledger = load_ledger(stage, &found);
        why = "The installed overlay's restore record is unreadable.";
        if (found && !ledger) goto done;
        bool keep = false;
        if (release && release->patch && release->base_id && ledger) {
            const cJSON *id = cJSON_GetObjectItemCaseSensitive(ledger, "id");
            keep = cJSON_IsNumber(id) && id->valueint == release->base_id;
        }
        why = "Could not restore the previous overlay.";
        if (ledger && !keep) {
            if (!restore(stage, ledger, cb)) goto done;
            cJSON_Delete(ledger);
            ledger = NULL;
        }
        if (release || local_archive) {
            why = "Could not apply the archive. The installed game was not changed.";
            if (!ledger) {
                ledger = cJSON_CreateObject();
                if (!ledger || !cJSON_AddArrayToObject(ledger, "files")) goto done;
            }
            cJSON *files = cJSON_GetObjectItemCaseSensitive(ledger, "files");
            if (release && release->patch && !keep) {
                why = "Could not install the required full release.";
                if (!base || !release->base_id || base->patch || base->file_id != release->base_id ||
                    base->kind != kind || strcmp(base->id, release->id) ||
                    strcmp(base->group, release->group) || !apply_download(tx, files, base, cb))
                    goto done;
            }
            why = "Could not download, verify or apply the overlay archive.";
            if (!(release ? apply_download(tx, files, release, cb) :
                  apply_archive(tx, files, local_archive, !strcmp(descriptor->id, "evenicle2"), cb)) ||
                !save_ledger(stage, ledger, release))
                goto done;
        }
    }
    why = "The staged game did not pass verification or could not be published.";
    VerifyGame verify = {descriptor, game->engine};
    if (!update(cb, "Verifying staged game") || !hub_install_verify(tx, verify_game, &verify) ||
        !update(cb, "Publishing on SD") || !hub_install_commit(tx))
        goto done;
    tx = NULL;
    ok = true;
done:
    cJSON_Delete(ledger);
    if (tx && !hub_install_rollback(tx))
        why = "Rollback is incomplete. Restart AliceHub to recover this game.";
    snprintf(error, 160, "%s", ok ? "" : why);
    return ok;
}
