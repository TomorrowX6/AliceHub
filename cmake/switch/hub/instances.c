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
#define COPY_MAX_DEPTH 64

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
    int n = snprintf(path + len, TREE_PATH_SIZE - len, "/%s", name);
    return n >= 0 && (size_t)n < TREE_PATH_SIZE - len;
}

static bool walk_tree(char *src, char *dst, const Copy *copy, bool make_dir, unsigned depth)
{
    if (depth > COPY_MAX_DEPTH) {
        errno = ELOOP;
        return false;
    }
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
        if (copying && !make_dir && (!strcasecmp(name, HUB_BOOT_SETTING) ||
            !strcasecmp(name, HUB_BOOT_SETTING ".tmp") || !strcasecmp(name, HUB_BOOT_SETTING ".bak")))
            continue;
        ok = append(src, sl, name) && (!copying || append(dst, dl, name));
        if (ok)
            ok = walk_tree(src, dst, copy, true, depth + 1);
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
                                      bool uses_dsr_save,
                                      char games[HUB_PATH_SIZE], char saves[HUB_PATH_SIZE])
{
    HubGame target;
    char alt_saves[HUB_PATH_SIZE];
    if (!hub_game_init(&target, engine, name) || strlen(name) > 255 ||
        *name == ' ' || name[strlen(name) - 1] == ' ' || name[strlen(name) - 1] == '.' ||
        strpbrk(name, "\"*<>?|") ||
        !hub_game_path(root, &target, games))
        return HUB_INSTANCE_INVALID_NAME;
    const char *suffix = (engine == HUB_XSYSTEM4 && uses_dsr_save) ? HUB_DSR_SUFFIX : "";
    const char *alt_suffix = (engine == HUB_XSYSTEM4 && !uses_dsr_save) ? HUB_DSR_SUFFIX : "";
    if ((size_t)snprintf(saves, HUB_PATH_SIZE, "%s/%s/saves/%s%s", root,
                         hub_engines[engine].id, name, suffix) >= HUB_PATH_SIZE)
        return HUB_INSTANCE_INVALID_NAME;
    HubInstanceResult result = name_available(games);
    if (result != HUB_INSTANCE_OK)
        return result;
    result = name_available(saves);
    if (result != HUB_INSTANCE_OK)
        return result;
    if (engine == HUB_XSYSTEM4) {
        if ((size_t)snprintf(alt_saves, sizeof(alt_saves), "%s/xsystem4/saves/%s%s",
                             root, name, alt_suffix) >= sizeof(alt_saves))
            return HUB_INSTANCE_INVALID_NAME;
        result = name_available(alt_saves);
        if (result != HUB_INSTANCE_OK)
            return result;
        if ((size_t)snprintf(alt_saves, sizeof(alt_saves), "%s/xsystem4/saves/" HUB_ENTRY_SAVES "/%s",
                             root, name) >= sizeof(alt_saves))
            return HUB_INSTANCE_INVALID_NAME;
        result = name_available(alt_saves);
    }
    return result;
}

static bool source_uses_dsr_save(const char *root, const HubGame *source)
{
    char src[HUB_PATH_SIZE];
    return source->engine == HUB_XSYSTEM4 &&
           hub_game_path(root, source, src) &&
           hub_game_uses_daisengoku_save_folder(src);
}

bool hub_instance_suggest(const char *root, const HubGame *source, char name[HUB_NAME_SIZE])
{
    char games[HUB_PATH_SIZE], saves[HUB_PATH_SIZE];
    bool uses_dsr = source_uses_dsr_save(root, source);
    size_t len = strlen(source->name);
    if (len > 60) {
        len = 60;
        while (len > 0 && ((unsigned char)source->name[len] & 0xc0) == 0x80)
            --len;
    }
    for (unsigned i = 1; i <= 9999; ++i) {
        if (len > 0)
            snprintf(name, HUB_NAME_SIZE, "%.*s-%u", (int)len, source->name, i);
        else
            snprintf(name, HUB_NAME_SIZE, "Copy-%u", i);
        HubInstanceResult result = target_paths(root, source->engine, name, uses_dsr, games, saves);
        if (result == HUB_INSTANCE_INVALID_NAME && len > 0) {
            snprintf(name, HUB_NAME_SIZE, "Copy-%u", i);
            result = target_paths(root, source->engine, name, uses_dsr, games, saves);
        }
        if (result != HUB_INSTANCE_NAME_TAKEN)
            return result == HUB_INSTANCE_OK;
    }
    return false;
}

bool hub_instance_recover(const char *root)
{
    bool ok = true;
    for (HubEngine engine = 0; engine < HUB_ENGINE_COUNT; ++engine) {
        char folder[TREE_PATH_SIZE];
        int n = snprintf(folder, sizeof(folder), "%s/%s", root, hub_engines[engine].id);
        if (n < 0 || (size_t)n >= sizeof(folder)) {
            ok = false;
            continue;
        }
        DIR *dir = opendir(folder);
        if (!dir) {
            if (errno != ENOENT)
                ok = false;
            continue;
        }
        for (;;) {
            errno = 0;
            struct dirent *entry = readdir(dir);
            if (!entry) {
                if (errno)
                    ok = false;
                break;
            }
            const char *name = entry->d_name;
            if (strncmp(name, ".hub-copy-", 10) != 0 || !name[10] ||
                strspn(name + 10, "0123456789") != strlen(name + 10))
                continue;
            char stage[TREE_PATH_SIZE];
            int sn = snprintf(stage, sizeof(stage), "%s/%s", folder, name);
            if (sn < 0 || (size_t)sn >= sizeof(stage) || !walk_tree(stage, NULL, NULL, false, 0))
                ok = false;
        }
        closedir(dir);
    }
    return ok;
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

    bool uses_dsr = source->engine == HUB_XSYSTEM4 && hub_game_uses_daisengoku_save_folder(src);
    HubInstanceResult result = target_paths(root, source->engine, name, uses_dsr, games, saves);
    if (result != HUB_INSTANCE_OK)
        return result;
    Copy copy = {cancelled, malloc(COPY_BUFFER_SIZE)};
    if (!copy.buffer)
        return HUB_INSTANCE_COPY_FAILED;
    if (!make_stage(root, source->engine, stage)) {
        free(copy.buffer);
        return HUB_INSTANCE_COPY_FAILED;
    }
    bool ok = walk_tree(src, stage, &copy, false, 0);
    free(copy.buffer);
    result = HUB_INSTANCE_COPY_FAILED;
    if (ok && (!cancelled || !cancelled())) {
        bool staged_dsr = source->engine == HUB_XSYSTEM4 && hub_game_uses_daisengoku_save_folder(stage);
        result = target_paths(root, source->engine, name, staged_dsr, games, saves);
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
    walk_tree(stage, NULL, NULL, false, 0);
    return result;
}

static bool save_claim(const char *root, const char *name, bool *claimed)
{
    HubGame other;
    char install[HUB_PATH_SIZE];
    *claimed = false;
    if (!hub_game_init(&other, HUB_XSYSTEM4, name))
        return true;
    if (!hub_game_path(root, &other, install))
        return false;
    HubInstanceResult available = name_available(install);
    *claimed = available == HUB_INSTANCE_NAME_TAKEN;
    return available == HUB_INSTANCE_OK || *claimed;
}

static bool save_locations(const char *root, const HubGame *game, const char *install,
                           char paths[3][TREE_PATH_SIZE])
{
    char base[HUB_PATH_SIZE], other[HUB_NAME_SIZE];
    if ((size_t)snprintf(base, sizeof(base), "%s/%s/saves", root,
                         hub_engines[game->engine].id) >= sizeof(base))
        return false;
    struct stat st;
    if (lstat(base, &st))
        return errno == ENOENT;
    if (!S_ISDIR(st.st_mode))
        return false;
    size_t len = strlen(game->name);
    bool claimed = false;
    if (game->engine == HUB_XSYSTEM4 && len > strlen(HUB_DSR_SUFFIX) &&
        !strcasecmp(game->name + len - strlen(HUB_DSR_SUFFIX), HUB_DSR_SUFFIX)) {
        snprintf(other, sizeof(other), "%.*s", (int)(len - strlen(HUB_DSR_SUFFIX)), game->name);
        if (!save_claim(root, other, &claimed))
            return false;
    }
    if (!claimed && (size_t)snprintf(paths[0], TREE_PATH_SIZE, "%s/%s", base, game->name) >= TREE_PATH_SIZE)
        return false;
    if (game->engine != HUB_XSYSTEM4)
        return true;
    bool dsr = false;
    if (is_directory(install) && !hub_system4_has_daisengoku_entry(install, &dsr))
        return false;
    if (dsr && (size_t)snprintf(other, sizeof(other), "%s" HUB_DSR_SUFFIX, game->name) < sizeof(other)) {
        if (!save_claim(root, other, &claimed))
            return false;
        if (!claimed && (size_t)snprintf(paths[1], TREE_PATH_SIZE, "%s/%s", base, other) >= TREE_PATH_SIZE)
            return false;
    }
    char entries[HUB_PATH_SIZE];
    if ((size_t)snprintf(entries, sizeof(entries), "%s/" HUB_ENTRY_SAVES, base) >= sizeof(entries))
        return false;
    if (lstat(entries, &st))
        return errno == ENOENT;
    return S_ISDIR(st.st_mode) &&
           (size_t)snprintf(paths[2], TREE_PATH_SIZE, "%s/%s", entries, game->name) < TREE_PATH_SIZE;
}

HubInstanceResult hub_instance_delete(const char *root, const HubGame *game, bool delete_saves)
{
    HubGame checked;
    char install[TREE_PATH_SIZE], saves[3][TREE_PATH_SIZE] = {{0}};
    if (!hub_game_init(&checked, game->engine, game->name) ||
        !hub_game_path(root, &checked, install))
        return HUB_INSTANCE_DELETE_FAILED;
    bool mapped = !delete_saves || save_locations(root, &checked, install, saves);
    if (!walk_tree(install, NULL, NULL, false, 0))
        return HUB_INSTANCE_DELETE_FAILED;
    if (!mapped)
        return HUB_INSTANCE_SAVE_DELETE_FAILED;
    for (size_t i = 0; i < sizeof(saves) / sizeof(*saves); ++i)
        if (*saves[i] && ((i < 2 && !hub_save_recover(saves[i])) || !walk_tree(saves[i], NULL, NULL, false, 0)))
            return HUB_INSTANCE_SAVE_DELETE_FAILED;
    return HUB_INSTANCE_OK;
}
