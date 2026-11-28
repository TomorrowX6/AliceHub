#ifndef ALICEHUB_LOCAL_IMPORT_H
#define ALICEHUB_LOCAL_IMPORT_H

#include "hub.h"

#include <stdint.h>

#define IMPORT_DEPTH 24

typedef struct {
    void *data;
    bool (*update)(void *, const char *, uint64_t, uint64_t);
} ImportCallbacks;

bool hub_local_import(const char *root, const char *source, const ImportCallbacks *callbacks,
                      HubGame *game, char error[160]);

bool import_path(char out[HUB_PATH_SIZE], const char *dir, const char *leaf);
bool import_leaf(const char *name);
bool import_skip(const char *name, bool directory);
bool import_suffix(const char *name, const char *suffix);
bool import_mkdir(const char *path);
bool import_update(const ImportCallbacks *, const char *, uint64_t, uint64_t);
bool import_seek(FILE *file, uint64_t offset);
uint16_t import_le16(const unsigned char *);
uint32_t import_le32(const unsigned char *);

bool import_zip(const char *source, const char *dest, const ImportCallbacks *);
bool import_iso(const char *source, const char *dest, const ImportCallbacks *);
bool import_fdi(const char *source, const char *dest, const ImportCallbacks *);
void hub_register_local_import(HubApp *);

#endif
