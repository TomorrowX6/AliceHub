#ifndef ALICEHUB_ONLINE_DOWNLOAD_H
#define ALICEHUB_ONLINE_DOWNLOAD_H

#include "online_manifest.h"

typedef struct {
    bool (*stopping)(void *);
    void (*report)(void *, uint64_t present, uint64_t received);
    void *context;
    char error[160];
    bool rollback_failed;
} OnlineTransfer;

bool hub_online_access_enabled(void);
bool hub_online_worker_busy(void);

bool online_fetch_manifest(const char *id, OnlineManifest *, OnlineTransfer *);
bool online_fetch_json(const char *id, const char *key, size_t limit, char **json, OnlineTransfer *);
bool online_fetch_verified_to(const char *id, const OnlineEntry *, const char *path, OnlineTransfer *);
bool online_fetch_file(const char *id, const OnlineEntry *, const char *cache,
                       char path[HUB_PATH_SIZE], OnlineTransfer *);
uint64_t online_cached_bytes(const char *id, const OnlineEntry *, const char *cache);
bool online_verified_file(const char *path, const OnlineEntry *, OnlineTransfer *);

#endif
