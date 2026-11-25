#include "instances.h"
#include "fs_util.h"

#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

#define TREE_PATH_SIZE 769
#define COPY_BUFFER_SIZE (256 * 1024)

typedef struct {
    bool (*cancelled)(void);
    void *buffer;
} Copy;

static bool is_directory(const char *path)
{
    struct stat st;
    return !lstat(path, &st) && S_ISDIR(st.st_mode);
}

static bool copy_file(const char *src, const char *dst, const Copy *copy)
{
    FILE *in = fopen(src, "rb");
    if (!in)
        return false;
    FILE *out = fopen(dst, "wb");
    bool ok = out != NULL;
    while (ok) {
        if (copy->cancelled && copy->cancelled()) {
            ok = false;
            break;
        }
        size_t size = fread(copy->buffer, 1, COPY_BUFFER_SIZE, in);
        ok = fwrite(copy->buffer, 1, size, out) == size;
        if (size < COPY_BUFFER_SIZE) {
            ok = ok && !ferror(in);
            break;
        }
    }
    if (out && fclose(out))
        ok = false;
    fclose(in);
    return ok;
}

static bool append(char *path, size_t len, const char *name)
{
    return (size_t)snprintf(path + len, TREE_PATH_SIZE - len, "/%s", name) < TREE_PATH_SIZE - len;
}

static bool walk_tree(char *src, char *dst, const Copy *copy, bool make_dir)
{
    bool copying = dst != NULL;
    if (copying && copy->cancelled && copy->cancelled())
        return false;
    struct stat st;
    if (lstat(src, &st))
        return !copying && errno == ENOENT;
    if (!S_ISDIR(st.st_mode))
        return copying ? S_ISREG(st.st_mode) && copy_file(src, dst, copy) : !unlink(src);

    if (copying && make_dir && mkdir(dst, 0777))
        return false;
    DIR *dir = opendir(src);
    if (!dir)
        return false;
    size_t sl = strlen(src), dl = copying ? strlen(dst) : 0;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *entry = readdir(dir);
        if (!entry) {
            ok = errno == 0;
            break;
        }
        const char *name = entry->d_name;
        if (!strcmp(name, ".") || !strcmp(name, ".."))
            continue;
        ok = append(src, sl, name) && (!copying || append(dst, dl, name));
        if (ok)
            ok = walk_tree(src, dst, copy, true);
        src[sl] = '\0';
        if (copying)
            dst[dl] = '\0';
        if (!ok)
            break;
    }
    if (closedir(dir))
        ok = false;
    return ok && (copying || !rmdir(src));
}

static HubInstanceResult name_available(const char *path)
{
    char parent[HUB_PATH_SIZE];
    const char *last = strrchr(path, '/');
    size_t length = (size_t)(last - path);
    memcpy(parent, path, length);
    parent[length] = '\0';
    DIR *dir = opendir(parent);
    if (!dir)
        return errno == ENOENT ? HUB_INSTANCE_OK : HUB_INSTANCE_COPY_FAILED;
    HubInstanceResult result = HUB_INSTANCE_OK;
    for (;;) {
        errno = 0;
        struct dirent *entry = readdir(dir);
        if (!entry) {
            if (errno)
                result = HUB_INSTANCE_COPY_FAILED;
            break;
        }
        if (!strcasecmp(entry->d_name, last + 1)) {
            result = HUB_INSTANCE_NAME_TAKEN;
            break;
        }
    }
    closedir(dir);
    if (result != HUB_INSTANCE_OK)
        return result;
    struct stat st;
    if (!lstat(path, &st))
        return HUB_INSTANCE_NAME_TAKEN;
    return errno == ENOENT ? HUB_INSTANCE_OK : HUB_INSTANCE_COPY_FAILED;
}

static HubInstanceResult target_paths(const char *root, HubEngine engine, const char *name,
                                      char games[HUB_PATH_SIZE], char saves[HUB_PATH_SIZE])
{
    HubGame target;
    if (!hub_game_init(&target, engine, name) || strlen(name) > 255 ||
        *name == ' ' || name[strlen(name) - 1] == ' ' || name[strlen(name) - 1] == '.' ||
        strpbrk(name, "\"*<>?|") ||
        !hub_game_path(root, &target, games) || !hub_game_save_path(root, &target, saves))
        return HUB_INSTANCE_INVALID_NAME;
    HubInstanceResult result = name_available(games);
    return result == HUB_INSTANCE_OK ? name_available(saves) : result;
}

bool hub_instance_suggest(const char *root, const HubGame *source, char name[HUB_NAME_SIZE])
{
    char games[HUB_PATH_SIZE], saves[HUB_PATH_SIZE];
    size_t len = strlen(source->name);
    if (len > 60) {
        len = 60;
        while (((unsigned char)source->name[len] & 0xc0) == 0x80)
            --len;
    }
    for (unsigned i = 1; i <= 9999; ++i) {
        snprintf(name, HUB_NAME_SIZE, "%.*s-%u", (int)len, source->name, i);
        HubInstanceResult result = target_paths(root, source->engine, name, games, saves);
        if (result == HUB_INSTANCE_INVALID_NAME) {
            snprintf(name, HUB_NAME_SIZE, "Copy-%u", i);
            result = target_paths(root, source->engine, name, games, saves);
        }
        if (result != HUB_INSTANCE_NAME_TAKEN)
            return result == HUB_INSTANCE_OK;
    }
    return false;
}

static bool make_stage(const char *root, HubEngine engine, char stage[TREE_PATH_SIZE])
{
    for (unsigned i = 1; i <= 9999; ++i) {
        if ((size_t)snprintf(stage, TREE_PATH_SIZE, "%s/%s/.hub-copy-%u", root,
                             hub_engines[engine].id, i) >= TREE_PATH_SIZE)
            return false;
        if (!mkdir(stage, 0777))
            return true;
        if (errno != EEXIST)
            return false;
    }
    return false;
}

HubInstanceResult hub_instance_copy(const char *root, const HubGame *source, const char *name,
                                    bool (*cancelled)(void))
{
    HubGame checked;
    char src[TREE_PATH_SIZE], stage[TREE_PATH_SIZE], games[HUB_PATH_SIZE], saves[HUB_PATH_SIZE];
    if (!hub_game_init(&checked, source->engine, source->name) ||
        !hub_game_path(root, &checked, src) || !is_directory(src))
        return HUB_INSTANCE_COPY_FAILED;

    HubInstanceResult result = target_paths(root, source->engine, name, games, saves);
    if (result != HUB_INSTANCE_OK)
        return result;
    Copy copy = {cancelled, malloc(COPY_BUFFER_SIZE)};
    if (!copy.buffer)
        return HUB_INSTANCE_COPY_FAILED;
    if (!make_stage(root, source->engine, stage)) {
        free(copy.buffer);
        return HUB_INSTANCE_COPY_FAILED;
    }
    bool ok = walk_tree(src, stage, &copy, false);
    free(copy.buffer);
    result = HUB_INSTANCE_COPY_FAILED;
    if (ok && (!cancelled || !cancelled())) {
        result = target_paths(root, source->engine, name, games, saves);
        if (result == HUB_INSTANCE_OK) {
            char parent[HUB_PATH_SIZE];
            strcpy(parent, saves);
            *strrchr(parent, '/') = '\0';
            if ((!mkdir(parent, 0777) || errno == EEXIST) && !mkdir(saves, 0777)) {
                if (!hub_rename_dir(stage, games))
                    return HUB_INSTANCE_OK;
                rmdir(saves);
            }
            result = HUB_INSTANCE_COPY_FAILED;
        }
    }
    walk_tree(stage, NULL, NULL, false);
    return result;
}

HubInstanceResult hub_instance_delete(const char *root, const HubGame *game, bool delete_saves)
{
    HubGame checked;
    char install[TREE_PATH_SIZE], saves[TREE_PATH_SIZE];
    if (!hub_game_init(&checked, game->engine, game->name) ||
        !hub_game_path(root, &checked, install) || !hub_game_save_path(root, &checked, saves) ||
        !walk_tree(install, NULL, NULL, false))
        return HUB_INSTANCE_DELETE_FAILED;
    if (delete_saves && (!hub_save_recover(saves) || !walk_tree(saves, NULL, NULL, false)))
        return HUB_INSTANCE_SAVE_DELETE_FAILED;
    return HUB_INSTANCE_OK;
}
