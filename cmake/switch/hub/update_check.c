#include "update_check.h"
#include "model.h"
#include "cJSON.h"

#include <errno.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>

static bool preference_path(char path[HUB_PATH_SIZE], const char *root)
{
    int n = snprintf(path, HUB_PATH_SIZE, "%s/update-check-disabled-v1", root);
    return n > 0 && n < HUB_PATH_SIZE;
}

bool hub_update_enabled(const char *root)
{
    char path[HUB_PATH_SIZE];
    return preference_path(path, root) && access(path, F_OK) != 0 && errno == ENOENT;
}

bool hub_update_set_enabled(const char *root, bool enabled)
{
    char path[HUB_PATH_SIZE];
    if (!preference_path(path, root))
        return false;
    if (enabled)
        return unlink(path) == 0 || errno == ENOENT;
    FILE *file = fopen(path, "ab");
    return file && !fclose(file);
}

static bool is_sha(const char *value, size_t min)
{
    size_t size = strlen(value);
    return size >= min && size < HUB_UPDATE_TAG_SIZE &&
           strspn(value, "0123456789abcdefABCDEF") == size;
}

bool hub_update_valid_commit(const char *commit)
{
    return is_sha(commit, HUB_UPDATE_TAG_SIZE - 1);
}

bool hub_update_parse(const char *commit, const char *json, char tag[HUB_UPDATE_TAG_SIZE])
{
    cJSON *release = cJSON_ParseWithOpts(json, NULL, true);
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(release, "tag_name");
    const char *value = cJSON_IsString(item) ? item->valuestring : "";
    bool available = hub_update_valid_commit(commit) && is_sha(value, 7) &&
                     strncasecmp(commit, value, strlen(value));
    if (available)
        strcpy(tag, value);
    cJSON_Delete(release);
    return available;
}
