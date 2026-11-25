#include "support_files.h"

#include <dirent.h>
#include <errno.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <unistd.h>

static bool make_path(char out[HUB_PATH_SIZE], const char *dir, const char *name)
{
    int n = snprintf(out, HUB_PATH_SIZE, "%s/%s", dir, name);
    return n >= 0 && n < HUB_PATH_SIZE;
}

static bool walk(const char *path, uint64_t *bytes, bool clear, unsigned depth)
{
    struct stat st;
    if (lstat(path, &st))
        return errno == ENOENT;
    if (clear && depth == 0 && !S_ISDIR(st.st_mode))
        return false;
    if (!S_ISDIR(st.st_mode)) {
        if (bytes && S_ISREG(st.st_mode))
            *bytes += st.st_size;
        return !clear || depth == 0 || !unlink(path);
    }
    if (depth >= 32)
        return false;
    DIR *dir = opendir(path);
    if (!dir)
        return false;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *entry = readdir(dir);
        if (!entry) {
            ok = ok && !errno;
            break;
        }
        if (!strcmp(entry->d_name, ".") || !strcmp(entry->d_name, ".."))
            continue;
        char child[HUB_PATH_SIZE];
        if (!make_path(child, path, entry->d_name) || !walk(child, bytes, clear, depth + 1))
            ok = false;
    }
    if (closedir(dir))
        ok = false;
    if (clear && depth && rmdir(path))
        ok = false;
    return ok;
}

HubStorage hub_support_storage(const char *root)
{
    HubStorage result = {.sizes_ok = true};
    char path[HUB_PATH_SIZE];
    for (size_t i = 0; i < HUB_ENGINE_COUNT; i++) {
        char engine[HUB_PATH_SIZE];
        if (!make_path(engine, root, hub_engines[i].id)) {
            result.sizes_ok = false;
            continue;
        }
        struct stat st;
        if (lstat(engine, &st)) {
            if (errno != ENOENT)
                result.sizes_ok = false;
            continue;
        }
        if (!S_ISDIR(st.st_mode) ||
            !make_path(path, engine, "games") || !walk(path, &result.games, false, 0))
            result.sizes_ok = false;
        if (!S_ISDIR(st.st_mode) ||
            !make_path(path, engine, "saves") || !walk(path, &result.saves, false, 0))
            result.sizes_ok = false;
    }
    if (!make_path(path, root, HUB_SUPPORT_CACHE) || !walk(path, &result.cache, false, 0))
        result.sizes_ok = false;
    struct statvfs volume;
    if (!statvfs(root, &volume)) {
        uint64_t unit = volume.f_frsize ? volume.f_frsize : volume.f_bsize;
        result.capacity = unit * volume.f_blocks;
        result.free = unit * volume.f_bavail;
        result.volume_ok = true;
    }
    return result;
}

bool hub_support_clear_cache(const char *root)
{
    char path[HUB_PATH_SIZE];
    return make_path(path, root, HUB_SUPPORT_CACHE) && walk(path, NULL, true, 0);
}

static bool publish_file(const char *root, const char *name, const char *text)
{
    char path[HUB_PATH_SIZE], temp[HUB_PATH_SIZE], backup[HUB_PATH_SIZE], leaf[64];
    if (!make_path(path, root, name))
        return false;
    snprintf(leaf, sizeof(leaf), "%s.tmp", name);
    if (!make_path(temp, root, leaf))
        return false;
    snprintf(leaf, sizeof(leaf), "%s.bak", name);
    if (!make_path(backup, root, leaf))
        return false;
    FILE *out = fopen(temp, "wb");
    if (!out)
        return false;
    bool ok = fputs(text, out) >= 0;
    if (!fclose(out) && ok) {
        if (!rename(temp, path))
            return true;
        struct stat st;
        if (!lstat(path, &st) && S_ISREG(st.st_mode)) {
            unlink(backup);
            if (!rename(path, backup)) {
                if (!rename(temp, path)) {
                    unlink(backup);
                    return true;
                }
                rename(backup, path);
            }
        }
    }
    unlink(temp);
    return false;
}

bool hub_support_record_error(const char *root, const HubGame *game, const char *error)
{
    if (!error[0] || (unsigned)game->engine >= HUB_ENGINE_COUNT)
        return false;
    char text[2048];
    int n = snprintf(text, sizeof(text), "AliceHub / Nintendo Switch\nEngine: %s\nGame: %s\n\n%s\n",
                     hub_engines[game->engine].label, game->name, error);
    return n > 0 && n < (int)sizeof(text) && publish_file(root, HUB_SUPPORT_LOG, text);
}

bool hub_support_export_error(const char *root)
{
    char path[HUB_PATH_SIZE];
    if (!make_path(path, root, HUB_SUPPORT_LOG))
        return false;
    struct stat st;
    if (lstat(path, &st) || !S_ISREG(st.st_mode))
        return false;
    FILE *in = fopen(path, "rb");
    if (!in)
        return false;
    char text[2048];
    size_t n = fread(text, 1, sizeof(text), in);
    bool ok = !ferror(in) && n && n < sizeof(text);
    fclose(in);
    if (!ok)
        return false;
    text[n] = '\0';
    return publish_file(root, HUB_SUPPORT_REPORT, text);
}
