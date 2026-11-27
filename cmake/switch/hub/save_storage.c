#include "save_storage.h"
#include "savedata.h"

#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

static const char *const unlock_files[] = {
    "GlobalSave.asd", "Collection.asd", "AFCGMode.asd", "AFBGMMode.asd"
};
static const char *const global_files[] = {
    "GlobalSave.asd", "Collection.asd", "CommonSystemData.sav"
};
static const char edit_suffix[] = ".hub-edit.bak";

enum { SAVE_SCAN_MAX_DEPTH = 64 };

bool hub_save_join(HubSavePath path, const char *dir, const char *name)
{
    int n = snprintf(path, HUB_SAVE_FILE_SIZE, "%s/%s", dir, name);
    return n >= 0 && (size_t)n < HUB_SAVE_FILE_SIZE;
}

static int file_state(const char *path)
{
    struct stat st;
    if (lstat(path, &st))
        return errno == ENOENT ? 0 : -1;
    return S_ISREG(st.st_mode) ? 1 : -1;
}

static bool is_dir(const char *path)
{
    struct stat st;
    return !lstat(path, &st) && S_ISDIR(st.st_mode);
}

bool hub_save_supported(const char *game_dir)
{
    DIR *dir = opendir(game_dir);
    if (!dir)
        return false;
    bool supported = false;
    for (struct dirent *ent; !supported && (ent = readdir(dir));) {
        const char *name = ent->d_name;
        const char *ext = strrchr(name, '.');
        HubSavePath path;
        supported = ext && (!strcasecmp(ext, ".ain") || !strcasecmp(ext, ".chs")) &&
            (!strcasecmp(name, "Rance10.ain") || !strcasecmp(name, "Rance10.chs") ||
             strstr(name, "ランス10") || strstr(name, "兰斯10") ||
             strstr(name, "ランス１０") || strstr(name, "兰斯１０")) &&
            hub_save_join(path, game_dir, name) && file_state(path) == 1;
    }
    closedir(dir);
    return supported;
}

static bool local_slot(const char *name)
{
    if (strncasecmp(name, "LocalSave", 9))
        return false;
    const char *digits = name + 9;
    const char *end = digits + strspn(digits, "0123456789");
    return end != digits && !strcasecmp(end, ".asd");
}

static bool scan(size_t root_len, char *dir, HubSaveSlots *slots, size_t *capacity, unsigned depth)
{
    if (depth > SAVE_SCAN_MAX_DEPTH) {
        errno = ELOOP;
        return false;
    }
    DIR *handle = opendir(dir);
    if (!handle)
        return errno == ENOENT;
    size_t dir_len = strlen(dir);
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *ent = readdir(handle);
        if (!ent) {
            ok = errno == 0;
            break;
        }
        if (ent->d_name[0] == '.')
            continue;
        int written = snprintf(dir + dir_len, HUB_SAVE_FILE_SIZE - dir_len, "/%s", ent->d_name);
        if (written < 0 || (size_t)written >= HUB_SAVE_FILE_SIZE - dir_len) {
            dir[dir_len] = '\0';
            continue;
        }
        struct stat st;
        if (lstat(dir, &st)) {
            dir[dir_len] = '\0';
            continue;
        }
        if (S_ISDIR(st.st_mode)) {
            if (!scan(root_len, dir, slots, capacity, depth + 1)) {
                ok = false;
                dir[dir_len] = '\0';
                break;
            }
            dir[dir_len] = '\0';
            continue;
        }
        if (!S_ISREG(st.st_mode)) {
            dir[dir_len] = '\0';
            continue;
        }
        if (!local_slot(ent->d_name)) {
            size_t length = dir_len + (size_t)written, suffix_length = strlen(edit_suffix);
            if (length <= suffix_length || strcmp(dir + length - suffix_length, edit_suffix)) {
                dir[dir_len] = '\0';
                continue;
            }
            dir[length - suffix_length] = 0;
            const char *name = strrchr(dir, '/') + 1;
            if (!local_slot(name) || file_state(dir) != 0) {
                dir[dir_len] = '\0';
                continue;
            }
        }
        if (slots->count == *capacity) {
            size_t n = *capacity ? 2 * *capacity : 16;
            HubSavePath *paths = realloc(slots->paths, n * sizeof(*paths));
            if (!paths) {
                ok = false;
                dir[dir_len] = '\0';
                break;
            }
            slots->paths = paths;
            *capacity = n;
        }
        strcpy(slots->paths[slots->count++], dir + root_len + 1);
        dir[dir_len] = '\0';
    }
    closedir(handle);
    return ok;
}

static int compare_slots(const void *a, const void *b)
{
    const char *x = a, *y = b;
    const char *xn = strrchr(x, '/'), *yn = strrchr(y, '/');
    unsigned long nx = strtoul((xn ? xn + 1 : x) + 9, NULL, 10);
    unsigned long ny = strtoul((yn ? yn + 1 : y) + 9, NULL, 10);
    return nx < ny ? -1 : nx > ny ? 1 : strcmp(x, y);
}

bool hub_save_scan(const char *dir, HubSaveSlots *slots)
{
    *slots = (HubSaveSlots){0};
    if (!dir)
        return false;
    size_t root_len = strlen(dir);
    if (root_len >= HUB_SAVE_FILE_SIZE)
        return false;
    char *cursor = malloc(HUB_SAVE_FILE_SIZE);
    if (!cursor)
        return false;
    memcpy(cursor, dir, root_len + 1);
    size_t capacity = 0;
    bool ok = scan(root_len, cursor, slots, &capacity, 0);
    free(cursor);
    if (!ok) {
        free(slots->paths);
        *slots = (HubSaveSlots){0};
        return false;
    }
    if (slots->count > 1)
        qsort(slots->paths, slots->count, sizeof(*slots->paths), compare_slots);
    return true;
}

static bool find_global(const char *dir, const char *name, HubSavePath path)
{
    HubSavePath sub;
    if (hub_save_join(path, dir, name) && file_state(path) == 1)
        return true;
    if (hub_save_join(sub, dir, "SaveData") && hub_save_join(path, sub, name) && file_state(path) == 1)
        return true;
    path[0] = 0;
    return false;
}

bool hub_save_global_paths(const char *dir, HubSavePath global, HubSavePath collection)
{
    bool g = find_global(dir, global_files[0], global);
    bool c = find_global(dir, global_files[1], collection);
    if (g || c)
        return true;
    HubSavePath system;
    if (!find_global(dir, global_files[2], system))
        return false;
    *strrchr(system, '/') = 0;
    return hub_save_join(global, system, global_files[0]);
}

static char *with_suffix(const char *path, const char *suffix)
{
    char *out = malloc(strlen(path) + strlen(suffix) + 1);
    if (out)
        sprintf(out, "%s%s", path, suffix);
    return out;
}

static bool unlink_if_present(const char *path)
{
    return !unlink(path) || errno == ENOENT;
}

static bool replace(const char *temp, const char *path)
{
    char *old = with_suffix(path, ".hub-old");
    if (!old)
        return false;
    bool ok = false;
    int state = file_state(path), recovery = file_state(old);
    if (state >= 0 && recovery >= 0) {
        if (!state && recovery) {
            if (rename(old, path))
                goto done;
            state = 1;
        }
        if (!state) {
            ok = !rename(temp, path);
        } else if (unlink_if_present(old) && !rename(path, old)) {
            ok = !rename(temp, path);
            if (ok)
                unlink_if_present(old);
            else
                rename(old, path);
        }
    }
done:
    free(old);
    return ok;
}

FILE *atomic_save_open(const char *path, const char *mode, char **tmp_path)
{
    *tmp_path = with_suffix(path, ".hub-tmp");
    FILE *fp = *tmp_path && unlink_if_present(*tmp_path) ? fopen(*tmp_path, mode) : NULL;
    if (!fp) {
        free(*tmp_path);
        *tmp_path = NULL;
    }
    return fp;
}

void atomic_save_abort(FILE *fp, char *tmp_path)
{
    fclose(fp);
    unlink(tmp_path);
    free(tmp_path);
}

static bool finish_write(FILE *fp)
{
    bool ok = !ferror(fp) && !fflush(fp);
    if (ok && fsync(fileno(fp)) && errno != EINVAL && errno != ENOSYS)
        ok = false;
    return !fclose(fp) && ok;
}

static bool copy_to(const char *src, const char *dst)
{
    FILE *in = src ? fopen(src, "rb") : NULL;
    if (src && !in)
        return false;
    char *temp;
    FILE *out = atomic_save_open(dst, "wb", &temp);
    bool ok = out != NULL;
    if (in && out) {
        char buffer[8192];
        size_t read;
        while ((read = fread(buffer, 1, sizeof(buffer), in)) != 0) {
            if (fwrite(buffer, 1, read, out) != read) {
                ok = false;
                break;
            }
        }
        ok = ok && !ferror(in);
    }
    if (in && fclose(in))
        ok = false;
    if (out) {
        ok = finish_write(out) && ok && replace(temp, dst);
        if (!ok)
            unlink(temp);
        free(temp);
    }
    return ok;
}

static char *backup_path(const char *path, HubSaveBackup kind, bool absent)
{
    return with_suffix(path, kind == HUB_SAVE_EDIT_BACKUP
        ? (absent ? ".hub-edit.absent" : edit_suffix)
        : (absent ? ".hub-unlock.absent" : ".hub-unlock.bak"));
}

static bool backup(const char *path, HubSaveBackup kind)
{
    char *bak = backup_path(path, kind, false), *absent = backup_path(path, kind, true);
    int state = file_state(path), b = bak ? file_state(bak) : -1, a = absent ? file_state(absent) : -1;
    bool ok = state >= 0 && b >= 0 && a >= 0 && !(b && a) &&
        (b || a || (state ? copy_to(path, bak) : copy_to(NULL, absent)));
    free(bak);
    free(absent);
    return ok;
}

bool atomic_save_commit(FILE *fp, const char *path, char *tmp_path, bool keep_backup)
{
    (void)keep_backup;
    bool ok = finish_write(fp) && backup(path, HUB_SAVE_EDIT_BACKUP) && replace(tmp_path, path);
    if (!ok)
        unlink(tmp_path);
    free(tmp_path);
    return ok;
}

bool hub_save_has_backup(const char *path, HubSaveBackup kind)
{
    char *bak = backup_path(path, kind, false), *absent = backup_path(path, kind, true);
    bool has = (bak && file_state(bak) == 1) || (absent && file_state(absent) == 1);
    free(bak);
    free(absent);
    return has;
}

static bool clear_backup(const char *path, HubSaveBackup kind)
{
    char *bak = backup_path(path, kind, false), *absent = backup_path(path, kind, true);
    bool ok = bak && absent && unlink_if_present(bak) && unlink_if_present(absent);
    free(bak);
    free(absent);
    return ok;
}

bool hub_save_restore(const char *path, HubSaveBackup kind)
{
    char *bak = backup_path(path, kind, false), *absent = backup_path(path, kind, true);
    char *old = with_suffix(path, ".hub-old");
    int b = bak ? file_state(bak) : -1, a = absent ? file_state(absent) : -1;
    bool ok = false;
    if (old && b == 1 && a == 0)
        ok = copy_to(bak, path) && unlink(bak) == 0;
    else if (old && b == 0 && a == 1 && file_state(path) >= 0)
        ok = unlink_if_present(old) && unlink_if_present(path) && unlink(absent) == 0;
    if (ok && kind == HUB_SAVE_UNLOCK_BACKUP)
        ok = clear_backup(path, HUB_SAVE_EDIT_BACKUP);
    free(bak);
    free(absent);
    free(old);
    return ok;
}

static bool walk_set(const char *dir, HubSaveBackup kind, const char *assets, bool restore)
{
    const char *const *files = kind == HUB_SAVE_UNLOCK_BACKUP ? unlock_files : global_files;
    size_t count = kind == HUB_SAVE_UNLOCK_BACKUP ? 4 : 3;
    HubSavePath nested, source;
    HubSavePath targets[8];
    const char *dirs[] = {dir, nested};
    size_t total = 0;
    for (size_t d = 0; d < (hub_save_join(nested, dir, "SaveData") && is_dir(nested) ? 2u : 1u); ++d)
        for (size_t i = 0; i < count; ++i)
            if (!hub_save_join(targets[total++], dirs[d], files[i]))
                return false;
    if (assets) {
        for (size_t i = 0; i < count; ++i)
            if (!hub_save_join(source, assets, files[i]) || file_state(source) != 1)
                return false;
        for (size_t i = 0; i < total; ++i)
            if (!backup(targets[i], kind))
                return false;
        for (size_t i = 0; i < total; ++i) {
            if (!hub_save_join(source, assets, files[i % count]) || !copy_to(source, targets[i]))
                return false;
            if (kind == HUB_SAVE_UNLOCK_BACKUP && !clear_backup(targets[i], HUB_SAVE_EDIT_BACKUP))
                return false;
        }
        return true;
    }
    bool found = false, ok = true;
    for (size_t i = 0; i < total; ++i) {
        if (hub_save_has_backup(targets[i], kind)) {
            if (!restore)
                return true;
            found = true;
            ok = hub_save_restore(targets[i], kind) && ok;
        }
    }
    return found && ok;
}

bool hub_save_has_set_backup(const char *dir, HubSaveBackup kind)
{
    return walk_set(dir, kind, NULL, false);
}

bool hub_save_restore_set(const char *dir, HubSaveBackup kind)
{
    return walk_set(dir, kind, NULL, true);
}

bool hub_save_install_unlock(const char *dir, const char *assets)
{
    HubSavePath parent;
    if (strlen(dir) >= sizeof(parent))
        return false;
    strcpy(parent, dir);
    char *slash = strrchr(parent, '/');
    if (!slash)
        return false;
    *slash = 0;
    if ((!is_dir(parent) && mkdir(parent, 0777)) || (!is_dir(dir) && mkdir(dir, 0777)))
        return false;
    return walk_set(dir, HUB_SAVE_UNLOCK_BACKUP, assets, false);
}
