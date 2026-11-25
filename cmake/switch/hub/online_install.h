#ifndef ALICEHUB_ONLINE_INSTALL_H
#define ALICEHUB_ONLINE_INSTALL_H

#include "online_download.h"

typedef struct {
    HubGame game;
    uint64_t download_bytes, stage_bytes, available_bytes;
    bool installed, conflict, archives;
} OnlinePlan;

typedef void (*OnlineStep)(void *, const char *step, uint64_t done, uint64_t total);

bool online_inspect(const char *id, OnlinePlan *plan, OnlineTransfer *transfer);
bool online_install(const char *id, const OnlinePlan *expected, OnlineTransfer *transfer,
                    OnlineStep stage);
bool online_clear_cache(const char *id);

#endif
