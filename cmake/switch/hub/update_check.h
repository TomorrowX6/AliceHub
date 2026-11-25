#ifndef ALICEHUB_UPDATE_CHECK_H
#define ALICEHUB_UPDATE_CHECK_H

#include <stdbool.h>

#define HUB_UPDATE_TAG_SIZE 41
#define HUB_UPDATE_COMPARE_URL_SIZE 160
#define HUB_UPDATE_API "https://api.github.com/repos/TomorrowX6/AliceHub"

bool hub_update_enabled(const char *root);
bool hub_update_set_enabled(const char *root, bool enabled);
bool hub_update_valid_commit(const char *commit);
bool hub_update_release_tag(const char *json, char tag[HUB_UPDATE_TAG_SIZE]);
bool hub_update_compare_url(const char *commit, const char *tag,
                            char url[HUB_UPDATE_COMPARE_URL_SIZE]);
bool hub_update_is_behind(const char *json);

#endif
