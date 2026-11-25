#ifndef ALICEHUB_HUB_SAVE_PRESETS_H
#define ALICEHUB_HUB_SAVE_PRESETS_H

#include "cJSON.h"

#include <stdbool.h>
#include <stddef.h>

size_t hub_save_preset_count(bool global);
const char *hub_save_preset_label(bool global, size_t index);
cJSON *hub_save_preset_changes(bool global, size_t index, const cJSON *summary);

#endif
