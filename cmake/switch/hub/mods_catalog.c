#include "mods.h"
#include "install_transaction.h"
#include "online_manifest.h"
#include "cJSON.h"

#include <ctype.h>
#include <dirent.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

static const ModsGame games[] = {
    {"rance10", MODS_ARCHIVE, {"Rance10.ain"}},
    {"evenicle", MODS_TRANSLATION, {"Evenicle.ain"}},
    {"evenicle2", MODS_TRANSLATION, {"Evenicle2.ain"}},
    {"rance01", MODS_UNCENSORED, {"Rance01.ain"}},
    {"rance02", MODS_UNCENSORED, {"Rance02.ain", "ランス０２.ain", "ランス０２GA.ald",
                                 "儔儞僗俷俲.ain", "儔儞僗俷俲GA.ald"}},
    {"rance03", MODS_UNCENSORED, {"Rance03.ain"}},
    {"rance5d", MODS_UNCENSORED, {"R5D_SA.ald"}},
    {"rance06", MODS_UNCENSORED, {"Rance6.ain", "Rance06.ain", "Rance6.chs", "Rance6GA.ald"}},
    {"sengokurance", MODS_ARCHIVE, {"Rance7.ain", "SengokuRance.ain",
                                    "DaiSengoku.ain", "DaiSengokuGA.ald",
                                    "戦国ランス.ain", "戦国ランスGA.ald",
                                    "愴崙儔儞僗.ain", "愴崙儔儞僗GA.ald",
                                    "战国兰斯.ain", "战国兰斯GA.ald"}},
    {"rance08", MODS_UNCENSORED, {"Rance08.ain", "Rance8.ain", "RanceQuest.ain",
                                 "ランス・クエスト.ain", "儔儞僗丒僋僄僗僩.ain"}},
    {"rance09", MODS_UNCENSORED, {"Rance9.ain", "Rance09.ain"}},
};

bool mods_path(char out[HUB_PATH_SIZE], const char *dir, const char *name)
{
    return (size_t)snprintf(out, HUB_PATH_SIZE, "%s/%s", dir, name) < HUB_PATH_SIZE;
}

const ModsGame *mods_game(const char *directory, HubEngine engine)
{
    if (engine == HUB_SYSTEM3)
        return NULL;
    DIR *dir = opendir(directory);
    if (!dir)
        return NULL;
    const ModsGame *match = NULL;
    for (const struct dirent *entry; (entry = readdir(dir));) {
        char path[HUB_PATH_SIZE];
        struct stat st;
        if (!mods_path(path, directory, entry->d_name) || lstat(path, &st) || !S_ISREG(st.st_mode) ||
            st.st_size <= 0)
            continue;
        if (!strcasecmp(entry->d_name, "Rance6After.ain") || !strcasecmp(entry->d_name, "Rance1.ain")) {
            match = NULL;
            break;
        }
        for (size_t i = 0; i < sizeof(games) / sizeof(*games); ++i)
            for (size_t j = 0; j < sizeof(games[i].markers) / sizeof(*games[i].markers) &&
                               games[i].markers[j]; ++j)
                if (engine == (!strcmp(games[i].id, "rance5d") ? HUB_XSYSTEM35 : HUB_XSYSTEM4) &&
                    !strcasecmp(entry->d_name, games[i].markers[j]) && (!match || match > &games[i]))
                    match = &games[i];
    }
    closedir(dir);
    return match;
}

bool mods_game_has_uncensored(const ModsGame *game)
{
    return game && (game->kind == MODS_UNCENSORED ||
                    !strcmp(game->id, "rance10") ||
                    !strcmp(game->id, "sengokurance"));
}

const char *mods_catalog_path(ModsKind kind)
{
    switch (kind) {
    case MODS_ARCHIVE: return "mods/index.json";
    case MODS_TRANSLATION: return "cn-patch/index.json";
    case MODS_UNCENSORED: return "uncensored/manifest.json";
    }
    return NULL;
}

static bool suffix(const char *name, const char *ext)
{
    size_t n = strlen(name), m = strlen(ext);
    return n >= m && !strcasecmp(name + n - m, ext);
}

bool mods_archive_name(const char *name)
{
    return suffix(name, ".zip") || suffix(name, ".rar") || suffix(name, ".7z");
}

bool mods_uncensored_name(const char *name)
{
    return !strcasecmp(name, "AliceHubUncensoredCG.afa") ||
           !strcasecmp(name, "AliceHubUncensoredCG.ald");
}

static bool field(const cJSON *obj, const char *key, char *out, size_t max, const char *fallback)
{
    const char *s = cJSON_GetStringValue(cJSON_GetObjectItemCaseSensitive(obj, key));
    if (!s || !*s) s = fallback;
    if (!s || strlen(s) >= max) return false;
    strcpy(out, s);
    return true;
}

static bool parse_release(ModsRelease *r, const ModsGame *game, const cJSON *row, int ordinal)
{
    const cJSON *size = cJSON_GetObjectItemCaseSensitive(row, "size");
    const cJSON *id = cJSON_GetObjectItemCaseSensitive(row, "fileId");
    const cJSON *patch = cJSON_GetObjectItemCaseSensitive(row, "isPatch");
    const char *prefix = game->kind == MODS_ARCHIVE ? "mods/" :
                         game->kind == MODS_TRANSLATION ? "cn-patch/" : "uncensored/";
    r->kind = game->kind;
    if (strlen(game->id) >= sizeof(r->id)) return false;
    strcpy(r->id, game->id);
    if (!cJSON_IsObject(row) || !field(row, "fileName", r->name, sizeof(r->name), NULL) ||
        !field(row, "key", r->key, sizeof(r->key), NULL) ||
        !field(row, "version", r->version, sizeof(r->version),
               game->kind == MODS_UNCENSORED ? "1.0" : NULL) ||
        !field(row, "group", r->group, sizeof(r->group), "") ||
        !field(row, "sha256", r->sha256, sizeof(r->sha256), NULL) ||
        strlen(r->sha256) != 64 || strspn(r->sha256, "0123456789abcdefABCDEF") != 64 ||
        !hub_install_relative_path(r->key) || strncmp(r->key, prefix, strlen(prefix)) ||
        !hub_install_relative_path(r->name) || strchr(r->name, '/') ||
        !cJSON_IsNumber(size) || !(size->valuedouble > 0 && size->valuedouble <= ONLINE_FILE_LIMIT))
        return false;
    for (char *p = r->sha256; *p; ++p) *p = tolower((unsigned char)*p);
    r->size = (uint64_t)size->valuedouble;
    if ((double)r->size != size->valuedouble) return false;
    if (game->kind == MODS_UNCENSORED) {
        const char *game_id = cJSON_GetStringValue(cJSON_GetObjectItemCaseSensitive(row, "gameId"));
        return game_id && !strcmp(game_id, game->id) && mods_uncensored_name(r->name);
    }
    if (!mods_archive_name(r->name)) return false;
    if (id && (!cJSON_IsNumber(id) || id->valueint <= 0 || id->valuedouble != id->valueint))
        return false;
    r->file_id = id ? id->valueint : 1000 - ordinal;
    r->patch = cJSON_IsBool(patch) ? cJSON_IsTrue(patch) :
               strstr(r->version, "补丁") || strstr(r->name, "补丁");
    return true;
}

bool mods_catalog_parse(ModsCatalog *catalog, const ModsGame *game, const char *json)
{
    if (!json || strlen(json) > MODS_MAX_MANIFEST)
        return false;
    cJSON *root = cJSON_ParseWithOpts(json, NULL, true);
    const cJSON *array = cJSON_GetObjectItemCaseSensitive(root, "releases");
    int count = game->kind == MODS_UNCENSORED ? 1 : cJSON_GetArraySize(array);
    ModsCatalog result = {0};
    if (root && count > 0 && count <= 128 && (game->kind == MODS_UNCENSORED || cJSON_IsArray(array)))
        result.rows = calloc(count, sizeof(*result.rows));
    if (result.rows)
        for (int i = 0; i < count; ++i) {
            ModsRelease release = {0};
            const cJSON *row = game->kind == MODS_UNCENSORED ? root : cJSON_GetArrayItem(array, i);
            if (parse_release(&release, game, row, i))
                result.rows[result.count++] = release;
        }
    cJSON_Delete(root);
    if (!result.count) {
        free(result.rows);
        return false;
    }
    for (size_t i = 0; i < result.count; ++i) {
        ModsRelease *row = &result.rows[i];
        if (!row->patch) continue;
        for (size_t j = i + 1; j < result.count; ++j)
            if (!result.rows[j].patch && !strcmp(row->group, result.rows[j].group)) {
                row->base_id = result.rows[j].file_id;
                break;
            }
    }
    *catalog = result;
    return true;
}
