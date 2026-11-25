#ifndef ALICEHUB_UPDATE_CHECK_H
#define ALICEHUB_UPDATE_CHECK_H

#include <stdbool.h>

#define HUB_UPDATE_TAG_SIZE 41

bool hub_update_enabled(const char *root);
bool hub_update_set_enabled(const char *root, bool enabled);
bool hub_update_valid_commit(const char *commit);
bool hub_update_parse(const char *commit, const char *json, char tag[HUB_UPDATE_TAG_SIZE]);

#endif
