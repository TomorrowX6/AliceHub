#include "model.h"

#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

#if defined(__SWITCH__) && (defined(ALICEHUB_SWITCH_HUB) || defined(ALICEHUB_SWITCH_XSYSTEM4))
#include "system4/utfsjis.h"
#elif !defined(__SWITCH__)
#include <iconv.h>
#endif

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

static char *ini_value(const char *ini_path, const char *key, bool quoted)
{
    struct stat st;
    if (lstat(ini_path, &st) || !S_ISREG(st.st_mode) || st.st_size > 256 * 1024)
        return NULL;
    FILE *fp = fopen(ini_path, "rb");
    if (!fp)
        return NULL;
    size_t size = (size_t)st.st_size;
    char *contents = malloc(size + 1);
    if (contents)
        contents[fread(contents, 1, size, fp)] = '\0';
    fclose(fp);
    if (!contents)
        return NULL;
    char *state, *value = NULL;
    size_t key_len = strlen(key);
    for (char *line = strtok_r(contents, "\n", &state); line; line = strtok_r(NULL, "\n", &state)) {
        char *p = !strncmp(line, "\xef\xbb\xbf", 3) ? line + 3 : line;
        while (*p && isspace((unsigned char)*p))
            ++p;
        if (!*p || *p == ';' || *p == '#' || (p[0] == '/' && p[1] == '/'))
            continue;
        char *eq = strchr(p, '=');
        if (!eq)
            continue;
        char *kend = eq;
        while (kend > p && isspace((unsigned char)kend[-1]))
            --kend;
        if ((size_t)(kend - p) != key_len ||
            (quoted ? strncmp(p, key, key_len) : strncasecmp(p, key, key_len)))
            continue;
        char *v = eq + 1;
        while (*v && isspace((unsigned char)*v))
            ++v;
        char *vend = v + strlen(v);
        if (*v == '"') {
            ++v;
            char *q = strchr(v, '"');
            vend = q ? q : v;
        } else if (!quoted) {
            for (char *c = v; *c; ++c) {
                if (*c == ';' || *c == '#' || (*c == '/' && c[1] == '/')) {
                    vend = c;
                    break;
                }
            }
            while (vend > v && isspace((unsigned char)vend[-1]))
                --vend;
        } else {
            vend = v;
        }
        free(value);
        value = vend > v ? strndup(v, (size_t)(vend - v)) : NULL;
    }
    free(contents);
    return value;
}

static const char *const standard_ini[] = {"System40.ini", "AliceStart.ini", "AliceStart2.ini", "AliceStart.CHS"};

static bool suffix(const char *name, const char *end)
{
    size_t n = strlen(name), len = strlen(end);
    return n >= len && !strcasecmp(name + n - len, end);
}

static bool boot_leaf(const char *name)
{
    if (!*name || *name == '.' || strlen(name) >= HUB_NAME_SIZE)
        return false;
    for (const unsigned char *p = (const unsigned char *)name; *p; ++p)
        if (*p < 32 || *p == 127 || *p == '/' || *p == '\\' || *p == ':')
            return false;
    return true;
}

static bool boot_path(char path[HUB_PATH_SIZE], const char *dir, const char *name)
{
    if ((size_t)snprintf(path, HUB_PATH_SIZE, "%s/%s", dir, name) < HUB_PATH_SIZE)
        return true;
    *path = '\0';
    return false;
}

static bool boot_regular(const char *path)
{
    struct stat st;
    return !lstat(path, &st) && S_ISREG(st.st_mode);
}

static bool boot_match(char path[HUB_PATH_SIZE], const char *dir, const char *name, bool directory)
{
    DIR *d = opendir(dir);
    if (!d)
        return false;
    char match[HUB_NAME_SIZE] = "";
    for (struct dirent *e; (e = readdir(d)); ) {
        if (strlen(e->d_name) >= sizeof(match))
            continue;
        if (!strcmp(e->d_name, name)) {
            strcpy(match, e->d_name);
            break;
        }
        if (!*match && !strcasecmp(e->d_name, name))
            strcpy(match, e->d_name);
    }
    closedir(d);
    struct stat st;
    return *match && boot_path(path, dir, match) && !lstat(path, &st) &&
           (directory ? S_ISDIR(st.st_mode) : S_ISREG(st.st_mode));
}

bool hub_system4_standard_ini(const char *name)
{
    for (size_t i = 0; i < sizeof(standard_ini) / sizeof(*standard_ini); ++i)
        if (!strcasecmp(name, standard_ini[i]))
            return true;
    return false;
}

static bool script_name(const char *name, bool chs)
{
    return (suffix(name, ".ain") && strcasecmp(name, "system39.ain")) ||
           (chs && suffix(name, ".chs") && strcasecmp(name, "AliceStart.CHS"));
}

static bool config_name(const char *name)
{
    return !strcasecmp(name, "AliceStart.CHS") || (suffix(name, ".ini") &&
           strcasecmp(name, "system3.ini") && strcasecmp(name, "system39.ini"));
}

static bool configured_path(const char *dir, char *code, char path[HUB_PATH_SIZE])
{
    if (*code == '/' || *code == '\\' || strchr(code, ':'))
        return false;
    char current[HUB_PATH_SIZE];
    if ((size_t)snprintf(current, sizeof(current), "%s", dir) >= sizeof(current))
        return false;
    char *state;
    for (char *p = strtok_r(code, "/\\", &state); p; ) {
        char *next = strtok_r(NULL, "/\\", &state);
        if (strcmp(p, ".")) {
            if (!strcmp(p, "..") || (!next && !script_name(p, true)) ||
                !boot_match(path, current, p, next != NULL))
                return false;
            if (!next)
                return true;
            strcpy(current, path);
        }
        p = next;
    }
    return false;
}

static bool decoded_path(const char *dir, const char *code, char path[HUB_PATH_SIZE])
{
    char decoded[HUB_PATH_SIZE];
    if (strlen(code) >= sizeof(decoded))
        return false;
    strcpy(decoded, code);
    if (configured_path(dir, decoded, path))
        return true;
#if !defined(__SWITCH__) || defined(ALICEHUB_SWITCH_HUB) || defined(ALICEHUB_SWITCH_XSYSTEM4)
    for (int gbk = 0; gbk <= 1; ++gbk) {
        bool valid = false;
#ifdef __SWITCH__
        char *converted = cjk2utf_strict_buf(code, strlen(code), gbk, decoded, sizeof(decoded), &valid);
        bool found = valid && strcmp(code, converted) && configured_path(dir, converted, path);
        if (converted != decoded)
            free(converted);
#else
        iconv_t cvt = iconv_open("UTF-8", gbk ? "GBK" : "CP932");
        if (cvt != (iconv_t)-1) {
            char *in = (char *)code, *out = decoded;
            size_t remaining = strlen(code), space = sizeof(decoded) - 1;
            valid = iconv(cvt, &in, &remaining, &out, &space) != (size_t)-1;
            *out = '\0';
            iconv_close(cvt);
        }
        bool found = valid && strcmp(code, decoded) && configured_path(dir, decoded, path);
#endif
        if (found)
            return true;
    }
#endif
    return false;
}

bool hub_system4_configured_script(const char *dir, const char *ini, char script[HUB_PATH_SIZE])
{
    char path[HUB_PATH_SIZE];
    if (!boot_leaf(ini) || !config_name(ini) || !boot_path(path, dir, ini))
        return false;
    char *code = ini_value(path, "CodeName", false);
    bool found = code && decoded_path(dir, code, script);
    free(code);
    return found;
}

static bool default_ini(const char *dir, char path[HUB_PATH_SIZE])
{
    char unused[HUB_PATH_SIZE];
    if ((boot_match(unused, dir, "CHS.TBL", false) && boot_match(path, dir, standard_ini[3], false)) ||
        (boot_match(unused, dir, "haru_c.ain", false) && boot_match(path, dir, standard_ini[2], false)))
        return true;
    for (size_t i = 0; i < sizeof(standard_ini) / sizeof(*standard_ini); ++i)
        if (boot_match(path, dir, standard_ini[i], false))
            return true;
    return false;
}

static bool ini_uses_dsr(const char *path)
{
    char *value = ini_value(path, "SaveFolder", false);
    bool dsr = value && !strcasecmp(value, "SaveDataDSR");
    free(value);
    return dsr;
}

typedef struct {
    char *name;
    char *script;
    bool listed;
} BootFile;

static int boot_order(const void *a, const void *b)
{
    return strcmp(((const BootFile *)a)->name, ((const BootFile *)b)->name);
}

static bool scan_boot(const char *dir, HubBootEntries *entries, char target[HUB_PATH_SIZE],
                      bool *uses_dsr, bool *has_dsr)
{
    *entries = (HubBootEntries){0};
    *target = '\0';
    *uses_dsr = false;
    DIR *d = opendir(dir);
    if (!d)
        return false;
    char path[HUB_PATH_SIZE], preferred[HUB_PATH_SIZE];
    bool standard = default_ini(dir, preferred);
    bool isolated = standard && ini_uses_dsr(preferred);
    *uses_dsr = isolated;
    if (standard && strlen(dir) >= HUB_PATH_SIZE) {
        closedir(d);
        return false;
    }
    bool chs = boot_match(path, dir, "CHS.TBL", false);
    size_t count = 0, capacity = 0;
    BootFile *files = NULL;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *e = readdir(d);
        if (!e) {
            ok = !errno;
            break;
        }
        const char *name = e->d_name;
        if (!boot_leaf(name) || (!config_name(name) && !script_name(name, chs)) ||
            !boot_path(path, dir, name) || !boot_regular(path))
            continue;
        char script[HUB_PATH_SIZE];
        bool ini = config_name(name);
        if (ini && !hub_system4_configured_script(dir, name, script))
            continue;
        if (count == capacity) {
            size_t next = capacity ? capacity * 2 : 8;
            BootFile *p = realloc(files, next * sizeof(*p));
            if (!p) { ok = false; break; }
            files = p;
            capacity = next;
        }
        BootFile *file = &files[count++];
        *file = (BootFile){.name = strdup(name), .script = ini ? strdup(script) : NULL};
        if (!file->name || (ini && !file->script)) { ok = false; break; }
        isolated |= ini && ini_uses_dsr(path);
    }
    closedir(d);
    if (ok && count) {
        entries->names = calloc(count, sizeof(*entries->names));
        ok = entries->names != NULL;
    }
    if (ok) {
        if (count > 1)
            qsort(files, count, sizeof(*files), boot_order);
        const char *config = NULL, *ain = NULL, *other_script = NULL;
        bool alternate = false;
        for (size_t i = 0; i < count; ++i) {
            BootFile *file = &files[i];
            if (file->script) {
                if (!config) config = file->name;
                file->listed = true;
                entries->names[entries->count++] = file->name;
                alternate |= standard && strcmp(strrchr(preferred, '/') + 1, file->name);
            }
        }
        for (size_t i = 0; i < count; ++i) {
            BootFile *file = &files[i];
            if (file->script)
                continue;
            if (!other_script) other_script = file->name;
            if (!ain && suffix(file->name, ".ain")) ain = file->name;
            if (isolated && strcasecmp(file->name, "AliceCN.ain"))
                continue;
            if (!boot_path(path, dir, file->name))
                continue;
            bool configured = false;
            for (size_t j = 0; j < count; ++j)
                if (files[j].script && !strcmp(path, files[j].script)) {
                    configured = true;
                    break;
                }
            if (!configured) {
                file->listed = true;
                entries->names[entries->count++] = file->name;
                alternate = true;
            }
        }
        const char *automatic = config ? config : ain ? ain : other_script;
        if (standard)
            strcpy(target, dir);
        else if (automatic && boot_path(target, dir, automatic)) {
            *uses_dsr = config && ini_uses_dsr(target);
        }
        if (entries->count < 2 && !(standard && alternate))
            hub_boot_entries_free(entries);
        if (has_dsr)
            *has_dsr = isolated;
    }
    for (size_t i = 0; i < count; ++i) {
        if (!files[i].listed)
            free(files[i].name);
        free(files[i].script);
    }
    free(files);
    return ok;
}

bool hub_system4_boot_entries(const char *dir, HubBootEntries *entries)
{
    char target[HUB_PATH_SIZE];
    bool uses_dsr;
    return scan_boot(dir, entries, target, &uses_dsr, NULL);
}

void hub_boot_entries_free(HubBootEntries *entries)
{
    for (size_t i = 0; i < entries->count; ++i)
        free(entries->names[i]);
    free(entries->names);
    *entries = (HubBootEntries){0};
}

bool hub_system4_resolve(const char *dir, char target[HUB_PATH_SIZE], bool *uses_dsr)
{
    HubBootEntries entries;
    if (!scan_boot(dir, &entries, target, uses_dsr, NULL))
        return false;
    char selected[HUB_NAME_SIZE];
    if (hub_game_boot_get(dir, selected))
        for (size_t i = 0; i < entries.count; ++i)
            if (!strcmp(selected, entries.names[i])) {
                if (boot_path(target, dir, selected))
                    *uses_dsr = config_name(selected) && ini_uses_dsr(target);
                break;
            }
    hub_boot_entries_free(&entries);
    return *target != '\0';
}

bool hub_game_uses_daisengoku_save_folder(const char *game_dir)
{
    char target[HUB_PATH_SIZE];
    bool uses_dsr;
    return game_dir && *game_dir && hub_system4_resolve(game_dir, target, &uses_dsr) && uses_dsr;
}

bool hub_system4_has_daisengoku_entry(const char *dir, bool *has_dsr)
{
    HubBootEntries entries;
    char target[HUB_PATH_SIZE];
    bool uses_dsr;
    bool ok = scan_boot(dir, &entries, target, &uses_dsr, has_dsr);
    hub_boot_entries_free(&entries);
    return ok;
}

static const char *path_leaf(const char *path)
{
    const char *slash = strrchr(path, '/');
    return slash ? slash + 1 : path;
}

bool hub_system4_save_key(const char *name, const char *dir, const char *target,
                          char key[HUB_PATH_SIZE])
{
    char *folder = NULL, ini[HUB_PATH_SIZE];
    if (target && !strcmp(dir, target) && default_ini(dir, ini))
        folder = ini_value(ini, "SaveFolder", false);
    else if (target && strcmp(dir, target) && config_name(path_leaf(target)))
        folder = ini_value(target, "SaveFolder", false);
    int length;
    if (folder && !strcasecmp(folder, "SaveDataDSR")) {
        length = snprintf(key, HUB_PATH_SIZE, "%s" HUB_DSR_SUFFIX, name);
    } else if (!folder || !strcasecmp(folder, "SaveData")) {
        length = snprintf(key, HUB_PATH_SIZE, "%s", name);
    } else {
        uint64_t hash = UINT64_C(14695981039346656037);
        for (const unsigned char *p = (const unsigned char *)folder; *p; ++p)
            hash = (hash ^ *p) * UINT64_C(1099511628211);
        length = snprintf(key, HUB_PATH_SIZE, HUB_ENTRY_SAVES "/%s/%016" PRIx64, name, hash);
    }
    free(folder);
    return (size_t)length < HUB_PATH_SIZE;
}

bool hub_system4_save_folder(const char *dir, char folder[HUB_NAME_SIZE])
{
    strcpy(folder, "SaveData");
    if (!dir || !*dir)
        return false;
    char target[HUB_PATH_SIZE], ini[HUB_PATH_SIZE];
    bool uses_dsr = false;
    if (!hub_system4_resolve(dir, target, &uses_dsr))
        return false;
    char *value = NULL;
    if (!strcmp(dir, target) && default_ini(dir, ini))
        value = ini_value(ini, "SaveFolder", false);
    else if (strcmp(dir, target) && config_name(path_leaf(target)))
        value = ini_value(target, "SaveFolder", false);
    bool valid = value && boot_leaf(value);
    if (valid)
        snprintf(folder, HUB_NAME_SIZE, "%s", value);
    free(value);
    return valid;
}

bool hub_system4_active_script(const char *dir, char script[HUB_PATH_SIZE])
{
    *script = '\0';
    if (!dir || !*dir)
        return false;
    char target[HUB_PATH_SIZE], ini[HUB_PATH_SIZE];
    bool uses_dsr = false;
    if (!hub_system4_resolve(dir, target, &uses_dsr))
        return false;
    if (!strcmp(dir, target)) {
        if (default_ini(dir, ini) && hub_system4_configured_script(dir, path_leaf(ini), script))
            return true;
    } else if (config_name(path_leaf(target))) {
        if (hub_system4_configured_script(dir, path_leaf(target), script))
            return true;
    } else if (script_name(path_leaf(target), true) && boot_regular(target)) {
        snprintf(script, HUB_PATH_SIZE, "%s", target);
        return true;
    }
    DIR *d = opendir(dir);
    if (!d)
        return false;
    char best[HUB_NAME_SIZE] = "";
    char path[HUB_PATH_SIZE];
    for (struct dirent *e; (e = readdir(d)); ) {
        const char *name = e->d_name;
        if (!boot_leaf(name) || !suffix(name, ".ain") || !strcasecmp(name, "system39.ain") ||
            !boot_path(path, dir, name) || !boot_regular(path))
            continue;
        if (!*best || strcmp(name, best) < 0)
            strcpy(best, name);
    }
    closedir(d);
    return *best && boot_path(script, dir, best);
}

static bool selected_save_key(const char *root, const HubGame *game, char key[HUB_PATH_SIZE])
{
    if ((unsigned)game->engine >= HUB_ENGINE_COUNT)
        return false;
    if (game->engine != HUB_XSYSTEM4)
        return (size_t)snprintf(key, HUB_PATH_SIZE, "%s", game->name) < HUB_PATH_SIZE;
    char dir[HUB_PATH_SIZE], target[HUB_PATH_SIZE];
    bool uses_dsr;
    if (!hub_game_path(root, game, dir))
        return false;
    bool found = hub_system4_resolve(dir, target, &uses_dsr);
    return hub_system4_save_key(game->name, dir, found ? target : NULL, key);
}

bool hub_game_save_path(const char *root, const HubGame *game, char path[HUB_PATH_SIZE])
{
    char key[HUB_PATH_SIZE];
    return selected_save_key(root, game, key) &&
           (size_t)snprintf(path, HUB_PATH_SIZE, "%s/%s/saves/%s", root,
                            hub_engines[game->engine].id, key) < HUB_PATH_SIZE;
}

bool hub_game_save_manager_path(const char *root, const HubGame *game, char path[HUB_PATH_SIZE])
{
    char key[HUB_PATH_SIZE];
    return selected_save_key(root, game, key) &&
           (size_t)snprintf(path, HUB_PATH_SIZE, "%s/save-manager/%s/%s", root,
                            hub_engines[game->engine].id, key) < HUB_PATH_SIZE;
}

static int by_name(const void *a, const void *b)
{
    const HubGame *x = a, *y = b;
    int order = strcasecmp(x->name, y->name);
    return order ? order : strcmp(x->instance_id, y->instance_id);
}

static int by_engine(const void *a, const void *b)
{
    const HubGame *x = a, *y = b;
    return x->engine == y->engine ? by_name(a, b) : (int)x->engine - (int)y->engine;
}

void hub_library_sort(HubLibrary *library, HubSort sort)
{
    if (library->count > 1)
        qsort(library->games, library->count, sizeof(*library->games),
              sort == HUB_SORT_ENGINE ? by_engine : by_name);
}

static unsigned char fold_ascii(unsigned char c)
{
    return c >= 'A' && c <= 'Z' ? c + ('a' - 'A') : c;
}

bool hub_game_matches(const HubGame *game, const char *query)
{
    if (!*query)
        return true;
    for (const char *start = game->name; *start; ++start) {
        const char *title = start;
        const char *term = query;
        while (*title && *term && fold_ascii(*title) == fold_ascii(*term)) {
            ++title;
            ++term;
        }
        if (!*term)
            return true;
    }
    return false;
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
    hub_library_sort(&next, HUB_SORT_NAME);
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

bool hub_game_boot_get(const char *dir, char entry[HUB_NAME_SIZE])
{
    char path[HUB_PATH_SIZE], saved[HUB_NAME_SIZE];
    *entry = '\0';
    if (!storage_path(path, dir, HUB_BOOT_SETTING, ""))
        return false;
    FILE *file = boot_regular(path) ? fopen(path, "rb") : NULL;
    if (!file && storage_path(path, dir, HUB_BOOT_SETTING, ".bak") && boot_regular(path))
        file = fopen(path, "rb");
    if (!file)
        return false;
    bool ok = read_line(file, saved, sizeof(saved)) && (!*saved || boot_leaf(saved)) &&
              fgetc(file) == EOF && !ferror(file);
    fclose(file);
    if (ok)
        strcpy(entry, saved);
    return ok;
}

bool hub_game_boot_set(const char *dir, const char *entry)
{
    char record[HUB_NAME_SIZE + 1];
    if (*entry && !boot_leaf(entry))
        return false;
    snprintf(record, sizeof(record), "%s\n", entry);
    return publish(dir, HUB_BOOT_SETTING, record);
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
