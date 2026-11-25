#ifndef ALICEHUB_HUB_INSTANCES_H
#define ALICEHUB_HUB_INSTANCES_H

#include "model.h"

typedef enum {
    HUB_INSTANCE_OK,
    HUB_INSTANCE_INVALID_NAME,
    HUB_INSTANCE_NAME_TAKEN,
    HUB_INSTANCE_COPY_FAILED,
    HUB_INSTANCE_DELETE_FAILED,
    HUB_INSTANCE_SAVE_DELETE_FAILED,
} HubInstanceResult;

bool hub_instance_suggest(const char *root, const HubGame *source, char name[HUB_NAME_SIZE]);
HubInstanceResult hub_instance_copy(const char *root, const HubGame *source, const char *name,
                                    bool (*cancelled)(void));
HubInstanceResult hub_instance_delete(const char *root, const HubGame *game, bool delete_saves);

#endif
