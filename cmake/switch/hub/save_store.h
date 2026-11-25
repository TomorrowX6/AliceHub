#ifndef ALICEHUB_HUB_SAVE_STORE_H
#define ALICEHUB_HUB_SAVE_STORE_H

#include "model.h"
#include <stdatomic.h>

typedef enum {
    HUB_SAVE_PREPARE, HUB_SAVE_IMPORT, HUB_SAVE_EXPORT, HUB_SAVE_EXPORT_PC,
    HUB_SAVE_BACKUP, HUB_SAVE_RESTORE, HUB_SAVE_SYNC, HUB_SAVE_PULL, HUB_SAVE_PUSH,
    HUB_SAVE_OP_COUNT
} HubSaveOp;

typedef struct {
    unsigned copied, converted, conflicts;
    char error[160];
} HubSaveResult;

bool hub_save_run(const char *root, const HubGame *game, HubSaveOp op,
                  const atomic_bool *cancelled, HubSaveResult *result);

#endif
