#include "model.h"

#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

const HubEngineInfo hub_engines[HUB_ENGINE_COUNT] = {
    [HUB_SYSTEM3] = {"system3", "System 3", "sdmc:/switch/system3.nro"},
    [HUB_XSYSTEM35] = {"xsystem35", "System 3.5", "sdmc:/switch/xsystem35.nro"},
    [HUB_XSYSTEM4] = {"xsystem4", "System 4", "sdmc:/switch/xsystem4.nro"},
};

bool hub_game_init(HubGame *game, HubEngine engine, const char *name)
{
    size_t n = strlen(name);
    if ((unsigned)engine >= HUB_ENGINE_COUNT || !n || n >= sizeof(game->name) || *name == '.')
        return false;
    for (const unsigned char *p = (const unsigned char *)name; *p; ++p)
        if (*p < 32 || *p == 127 || *p == '/' || *p == '\\' || *p == ':')
            return false;
    game->engine = engine;
    memcpy(game->name, name, n + 1);
    snprintf(game->game_id, sizeof(game->game_id), "%s:%s", hub_engines[engine].id, name);
    snprintf(game->instance_id, sizeof(game->instance_id), "local:%s:%s", hub_engines[engine].id, name);
    return true;
}

static bool game_path(const char *root, const HubGame *game, const char *base,
                      char path[HUB_PATH_SIZE])
{
    return (unsigned)game->engine < HUB_ENGINE_COUNT &&
           (size_t)snprintf(path, HUB_PATH_SIZE, "%s/%s/%s/%s", root,
                            hub_engines[game->engine].id, base, game->name) < HUB_PATH_SIZE;
}

bool hub_game_path(const char *root, const HubGame *game, char path[HUB_PATH_SIZE])
{
    return game_path(root, game, "games", path);
}

bool hub_game_save_path(const char *root, const HubGame *game, char path[HUB_PATH_SIZE])
{
    return game_path(root, game, "saves", path);
}

static int by_name(const void *a, const void *b)
{
    const HubGame *x = a, *y = b;
    int order = strcasecmp(x->name, y->name);
    return order ? order : strcmp(x->instance_id, y->instance_id);
}

bool hub_library_scan(HubLibrary *library, const char *root)
{
    HubLibrary next = {0};
    size_t capacity = 0;
    for (HubEngine i = 0; i < HUB_ENGINE_COUNT; ++i) {
        char base[HUB_PATH_SIZE];
        if ((size_t)snprintf(base, sizeof(base), "%s/%s/games", root, hub_engines[i].id) >= sizeof(base))
            goto fail;
        DIR *dir = opendir(base);
        if (!dir) {
            if (errno == ENOENT)
                continue;
            goto fail;
        }
        for (;;) {
            errno = 0;
            struct dirent *ent = readdir(dir);
            if (!ent) {
                int error = errno;
                closedir(dir);
                if (error)
                    goto fail;
                break;
            }
            HubGame game = {0};
            char path[HUB_PATH_SIZE];
            struct stat st;
            if (!hub_game_init(&game, i, ent->d_name) || !hub_game_path(root, &game, path) ||
                stat(path, &st) || !S_ISDIR(st.st_mode))
                continue;
            if (next.count == capacity) {
                size_t grown = capacity ? capacity * 2 : 32;
                HubGame *games = realloc(next.games, grown * sizeof(*games));
                if (!games) {
                    closedir(dir);
                    goto fail;
                }
                next.games = games;
                capacity = grown;
            }
            next.games[next.count++] = game;
        }
    }
    if (next.count > 1)
        qsort(next.games, next.count, sizeof(*next.games), by_name);
    hub_library_free(library);
    *library = next;
    return true;
fail:
    hub_library_free(&next);
    return false;
}

void hub_library_free(HubLibrary *library)
{
    free(library->games);
    *library = (HubLibrary){0};
}

static bool read_line(FILE *file, char *buffer, size_t size)
{
    size_t used = 0;
    for (int c; (c = fgetc(file)) != EOF;) {
        if (c == '\n') {
            buffer[used] = '\0';
            return true;
        }
        if (c < 32 || c == 127 || used + 1 == size)
            return false;
        buffer[used++] = c;
    }
    return false;
}

static bool storage_path(char path[HUB_PATH_SIZE], const char *root, const char *name, const char *suffix)
{
    return (size_t)snprintf(path, HUB_PATH_SIZE, "%s/%s%s", root, name, suffix) < HUB_PATH_SIZE;
}

static bool publish(const char *root, const char *name, const char *record)
{
    char path[HUB_PATH_SIZE], temp[HUB_PATH_SIZE], backup[HUB_PATH_SIZE];
    if (!storage_path(path, root, name, "") || !storage_path(temp, root, name, ".tmp") ||
        !storage_path(backup, root, name, ".bak"))
        return false;
    FILE *file = fopen(temp, "wb");
    if (!file)
        return false;
    bool ok = fputs(record, file) >= 0;
    if (fclose(file) || !ok) {
        remove(temp);
        return false;
    }
    if (!rename(temp, path))
        return true;
    remove(backup);
    if (rename(path, backup)) {
        remove(temp);
        return false;
    }
    if (rename(temp, path)) {
        rename(backup, path);
        remove(temp);
        return false;
    }
    remove(backup);
    return true;
}

bool hub_settings_load(const char *root, HubSettings *settings)
{
    char path[HUB_PATH_SIZE], header[24], sort[16];
    *settings = (HubSettings){0};
    if (!storage_path(path, root, "settings", ""))
        return false;
    FILE *file = fopen(path, "rb");
    if (!file && errno == ENOENT && storage_path(path, root, "settings", ".bak"))
        file = fopen(path, "rb");
    if (!file)
        return errno == ENOENT;
    bool ok = read_line(file, header, sizeof(header)) && !strcmp(header, "alicehub-settings 1") &&
              read_line(file, sort, sizeof(sort)) && fgetc(file) == EOF && !ferror(file);
    fclose(file);
    if (!ok)
        return false;
    if (!strcmp(sort, "engine"))
        settings->sort = HUB_SORT_ENGINE;
    else if (strcmp(sort, "name"))
        return false;
    return true;
}

bool hub_settings_save(const char *root, const HubSettings *settings)
{
    switch (settings->sort) {
    case HUB_SORT_NAME: return publish(root, "settings", "alicehub-settings 1\nname\n");
    case HUB_SORT_ENGINE: return publish(root, "settings", "alicehub-settings 1\nengine\n");
    default: return false;
    }
}

bool hub_handoff_parse(FILE *file, HubHandoff *handoff)
{
    char header[24], engine[16], name[HUB_NAME_SIZE];
    HubHandoff result = {0};
    if (!read_line(file, header, sizeof(header)) || strcmp(header, "alicehub-handoff 1") ||
        !read_line(file, engine, sizeof(engine)) || !read_line(file, name, sizeof(name)) ||
        !read_line(file, result.error, sizeof(result.error)) || fgetc(file) != EOF || ferror(file))
        return false;
    for (HubEngine i = 0; i < HUB_ENGINE_COUNT; ++i) {
        if (!strcmp(engine, hub_engines[i].id) && hub_game_init(&result.game, i, name)) {
            *handoff = result;
            return true;
        }
    }
    return false;
}

static const char *handoff_file(bool returning)
{
    return returning ? "hub.return" : "hub.request";
}

bool hub_handoff_write(const char *root, bool returning, const HubHandoff *handoff)
{
    const HubGame *game = &handoff->game;
    HubGame checked;
    if (!hub_game_init(&checked, game->engine, game->name) || (!returning && *handoff->error))
        return false;
    char error[sizeof(handoff->error)];
    size_t i = 0;
    for (; i + 1 < sizeof(error) && handoff->error[i]; ++i) {
        unsigned char c = handoff->error[i];
        error[i] = c < 32 || c == 127 ? ' ' : c;
    }
    error[i] = '\0';
    char record[HUB_NAME_SIZE + sizeof(error) + 64];
    int n = snprintf(record, sizeof(record), "alicehub-handoff 1\n%s\n%s\n%s\n",
                     hub_engines[game->engine].id, game->name, error);
    return n > 0 && n < (int)sizeof(record) && publish(root, handoff_file(returning), record);
}

bool hub_handoff_take(const char *root, bool returning, HubHandoff *handoff)
{
    char path[HUB_PATH_SIZE];
    if (!storage_path(path, root, handoff_file(returning), ""))
        return false;
    FILE *file = fopen(path, "rb");
    if (!file)
        return false;
    bool ok = hub_handoff_parse(file, handoff) && (returning || !*handoff->error);
    fclose(file);
    return remove(path) == 0 && ok;
}

void hub_handoff_clear(const char *root, bool returning)
{
    char path[HUB_PATH_SIZE];
    if (storage_path(path, root, handoff_file(returning), ""))
        remove(path);
}
