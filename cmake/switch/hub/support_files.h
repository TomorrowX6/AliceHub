#ifndef ALICEHUB_SUPPORT_FILES_H
#define ALICEHUB_SUPPORT_FILES_H

#include "model.h"
#include <stdint.h>

#define HUB_SUPPORT_CACHE "cache"
#define HUB_SUPPORT_LOG "last-error.log"
#define HUB_SUPPORT_REPORT "AliceHub-error-report.txt"

typedef struct {
    uint64_t games, saves, cache, free, capacity;
    bool sizes_ok, volume_ok;
} HubStorage;

HubStorage hub_support_storage(const char *root);
bool hub_support_clear_cache(const char *root);
bool hub_support_record_error(const char *root, const HubGame *game, const char *error);
bool hub_support_export_error(const char *root);

#endif
