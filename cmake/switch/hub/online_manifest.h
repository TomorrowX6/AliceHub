#ifndef ALICEHUB_ONLINE_MANIFEST_H
#define ALICEHUB_ONLINE_MANIFEST_H

#include "model.h"

#include <stdint.h>

#define ONLINE_SHA_SIZE 65
#define ONLINE_FILE_LIMIT UINT64_C(4294967295)
#define ONLINE_MANIFEST_LIMIT (4 * 1024 * 1024)
#define ONLINE_URL_SIZE 2048
#define ONLINE_ETAG_SIZE 192
#define ONLINE_MARKER ".alicehub-online-v1"

typedef struct {
    char *path, *key;
    char sha256[ONLINE_SHA_SIZE];
    uint64_t size;
    bool extract;
} OnlineEntry;

typedef struct {
    char folder[HUB_NAME_SIZE];
    OnlineEntry *files;
    size_t count;
    uint64_t total, direct_bytes;
} OnlineManifest;

bool online_manifest_parse(const char *json, OnlineManifest *manifest);
void online_manifest_free(OnlineManifest *manifest);
bool online_payload_url(const char *id, const char *key, char url[ONLINE_URL_SIZE]);
HubEngine online_boot_marker(const char *name);
bool online_strong_etag(const char *value);
bool online_content_range(const char *value, uint64_t start, uint64_t size);
bool online_resume_matches(const char *range, const char *tag, const char *previous_tag,
                           uint64_t start, uint64_t size);

#endif
