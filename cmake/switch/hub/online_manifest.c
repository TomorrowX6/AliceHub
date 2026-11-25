#include "online_manifest.h"
#include "install_transaction.h"
#include "cJSON.h"

#include <stdlib.h>
#include <string.h>
#include <strings.h>

static const char *const cdn = "https://alicehub.000.moe";

static bool suffix(const char *s, const char *end)
{
    size_t len = strlen(s), n = strlen(end);
    return len >= n && !strcasecmp(s + len - n, end);
}

HubEngine online_boot_marker(const char *name)
{
    if (strchr(name, '/'))
        return HUB_ENGINE_COUNT;
    if (!strcasecmp(name, "System40.ini") || !strcasecmp(name, "AliceStart.ini") ||
        !strcasecmp(name, "AliceStart2.ini") || !strcasecmp(name, "AliceStart.CHS"))
        return HUB_XSYSTEM4;
    if (!strcasecmp(name, "xsystem35.gr") || !strcasecmp(name, "adisk.ald") ||
        suffix(name, "sa.ald") || suffix(name, "sa_chs.ald"))
        return HUB_XSYSTEM35;
    if (!strcasecmp(name, "ADISK.DAT") || !strcasecmp(name, "GDISK.DAT") ||
        !strcasecmp(name, "AGAME.DAT"))
        return HUB_SYSTEM3;
    return HUB_ENGINE_COUNT;
}

static const char hex[] = "0123456789abcdef";

static bool valid_sha(const char *value, char result[ONLINE_SHA_SIZE])
{
    if (!value || strlen(value) != ONLINE_SHA_SIZE - 1)
        return false;
    for (size_t i = 0; i < ONLINE_SHA_SIZE - 1; ++i) {
        char c = value[i];
        if (c >= 'A' && c <= 'F')
            c += 'a' - 'A';
        if (!strchr(hex, c))
            return false;
        result[i] = c;
    }
    result[ONLINE_SHA_SIZE - 1] = 0;
    return true;
}

static const char *string(const cJSON *value, const char *key)
{
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(value, key);
    return cJSON_IsString(item) ? item->valuestring : NULL;
}

static int compare(const void *a, const void *b)
{
    const OnlineEntry *x = a, *y = b;
    return strcasecmp(x->path, y->path);
}

static bool collides(const OnlineManifest *manifest)
{
    for (size_t i = 0; i < manifest->count; ++i) {
        char name[HUB_PATH_SIZE];
        const OnlineEntry *entry = &manifest->files[i];
        if (i && !strcasecmp(manifest->files[i - 1].path, entry->path))
            return true;
        strcpy(name, entry->path);
        for (char *p = strchr(name, '/'); p; p = strchr(p + 1, '/')) {
            *p = 0;
            OnlineEntry parent = {.path = name};
            if (bsearch(&parent, manifest->files, manifest->count, sizeof(*entry), compare))
                return true;
            *p = '/';
        }
    }
    return false;
}

static bool bounded_json(const char *json)
{
    if (strlen(json) > ONLINE_MANIFEST_LIMIT)
        return false;
    unsigned depth = 0;
    bool quoted = false;
    for (const char *p = json; *p; ++p) {
        if (quoted && *p == '\\') {
            if (!*++p || !strncmp(p, "u0000", 5))
                return false;
        } else if (*p == '"') {
            quoted = !quoted;
        } else if (!quoted && (*p == '[' || *p == '{')) {
            if (++depth > 32)
                return false;
        } else if (!quoted && (*p == ']' || *p == '}')) {
            if (!depth--)
                return false;
        }
    }
    return !depth && !quoted;
}

bool online_manifest_parse(const char *json, OnlineManifest *out)
{
    OnlineManifest manifest = {0};
    if (!bounded_json(json))
        return false;
    cJSON *doc = cJSON_ParseWithOpts(json, NULL, true);
    const char *folder = string(doc, "baseName");
    const cJSON *files = cJSON_GetObjectItemCaseSensitive(doc, "files");
    int count = cJSON_IsArray(files) ? cJSON_GetArraySize(files) : 0;
    HubGame checked;
    if (!folder || strchr(folder, '/') || !hub_install_relative_path(folder) ||
        !hub_game_init(&checked, HUB_SYSTEM3, folder) || count < 1 || count > 8192)
        goto fail;
    strcpy(manifest.folder, folder);
    manifest.files = calloc(count, sizeof(*manifest.files));
    if (!manifest.files)
        goto fail;
    manifest.count = count;
    const cJSON *item;
    size_t i = 0;
    cJSON_ArrayForEach(item, files) {
        OnlineEntry *entry = &manifest.files[i++];
        const char *path = string(item, "path"), *key = string(item, "key");
        const cJSON *size = cJSON_GetObjectItemCaseSensitive(item, "size");
        const cJSON *extract = cJSON_GetObjectItemCaseSensitive(item, "extract");
        char url[ONLINE_URL_SIZE];
        if (!path || strlen(path) >= HUB_PATH_SIZE || !key || !online_payload_url("game", key, url) ||
            !valid_sha(string(item, "sha256"), entry->sha256) || !cJSON_IsNumber(size) ||
            !(size->valuedouble >= 0 && size->valuedouble <= ONLINE_FILE_LIMIT) ||
            (uint64_t)size->valuedouble != size->valuedouble || (extract && !cJSON_IsBool(extract)))
            goto fail;
        entry->path = strdup(path);
        entry->key = strdup(key);
        if (!entry->path || !entry->key)
            goto fail;
        for (char *p = entry->path; *p; ++p)
            if (*p == '\\')
                *p = '/';
        if (!hub_install_relative_path(entry->path) ||
            !strncasecmp(entry->path, ONLINE_MARKER, sizeof(ONLINE_MARKER) - 1))
            goto fail;
        entry->size = (uint64_t)size->valuedouble;
        entry->extract = cJSON_IsTrue(extract);
        manifest.total += entry->size;
        if (!entry->extract)
            manifest.direct_bytes += entry->size;
    }
    qsort(manifest.files, manifest.count, sizeof(*manifest.files), compare);
    if (collides(&manifest))
        goto fail;
    cJSON_Delete(doc);
    *out = manifest;
    return true;
fail:
    cJSON_Delete(doc);
    online_manifest_free(&manifest);
    return false;
}

void online_manifest_free(OnlineManifest *manifest)
{
    for (size_t i = 0; i < manifest->count; ++i) {
        free(manifest->files[i].path);
        free(manifest->files[i].key);
    }
    free(manifest->files);
    *manifest = (OnlineManifest){0};
}

bool online_payload_url(const char *id, const char *key, char url[ONLINE_URL_SIZE])
{
    if (!*id || strspn(id, "abcdefghijklmnopqrstuvwxyz0123456789_-") != strlen(id) ||
        (key && !hub_install_relative_path(key)))
        return false;
    int n = snprintf(url, ONLINE_URL_SIZE, "%s/%s/%s", cdn, id, key ? "files/" : "manifest.json");
    if (n < 0 || n >= ONLINE_URL_SIZE)
        return false;
    if (!key)
        return true;
    char *out = url + n;
    for (const unsigned char *p = (const unsigned char *)key; *p; ++p) {
        unsigned char c = *p;
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') || strchr("-_.~/", c)) {
            if (out >= url + ONLINE_URL_SIZE - 1)
                return false;
            *out++ = c;
        } else {
            if (out >= url + ONLINE_URL_SIZE - 3)
                return false;
            *out++ = '%';
            *out++ = hex[c >> 4];
            *out++ = hex[c & 15];
        }
    }
    *out = 0;
    return true;
}

bool online_strong_etag(const char *value)
{
    size_t n = strlen(value);
    if (n < 3 || n >= ONLINE_ETAG_SIZE || value[0] != '"' || value[n - 1] != '"')
        return false;
    for (size_t i = 1; i < n - 1; ++i)
        if ((unsigned char)value[i] <= 32 || value[i] == '"' || value[i] == 127)
            return false;
    return true;
}

static bool number(const char **in, uint64_t *value)
{
    const char *p = *in;
    *value = 0;
    if (*p < '0' || *p > '9')
        return false;
    for (; *p >= '0' && *p <= '9'; ++p) {
        unsigned digit = *p - '0';
        if (*value > (UINT64_MAX - digit) / 10)
            return false;
        *value = *value * 10 + digit;
    }
    *in = p;
    return true;
}

bool online_content_range(const char *value, uint64_t start, uint64_t size)
{
    if (!size || strncasecmp(value, "bytes ", 6))
        return false;
    uint64_t first, last, total;
    value += 6;
    return number(&value, &first) && *value++ == '-' && number(&value, &last) &&
           *value++ == '/' && number(&value, &total) && !*value &&
           first == start && last >= first && last == size - 1 && total == size;
}

bool online_resume_matches(const char *range, const char *tag, const char *previous_tag,
                           uint64_t start, uint64_t size)
{
    return start && online_content_range(range, start, size) &&
           online_strong_etag(tag) && !strcmp(tag, previous_tag);
}
