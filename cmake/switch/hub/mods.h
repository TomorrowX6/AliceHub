#ifndef ALICEHUB_HUB_MODS_H
#define ALICEHUB_HUB_MODS_H

#include "hub.h"

#include <stdint.h>

#define MODS_MAX_MANIFEST (1024 * 1024)

typedef enum { MODS_ARCHIVE, MODS_TRANSLATION, MODS_UNCENSORED } ModsKind;

typedef struct {
    const char *id;
    ModsKind kind;
    const char *markers[10];
} ModsGame;

typedef struct {
    ModsKind kind;
    char id[24], version[96], group[96], name[256], key[HUB_PATH_SIZE], sha256[65];
    uint64_t size;
    bool patch;
    int file_id, base_id;
} ModsRelease;

typedef struct {
    ModsRelease *rows;
    size_t count;
} ModsCatalog;

const ModsGame *mods_game(const char *directory, HubEngine engine);
const char *mods_catalog_path(ModsKind kind);
bool mods_catalog_parse(ModsCatalog *, const ModsGame *, const char *json);
bool mods_archive_name(const char *);
bool mods_uncensored_name(const char *);
bool mods_path(char out[HUB_PATH_SIZE], const char *dir, const char *name);

typedef bool (*ModsProgress)(void *, const char *detail, uint64_t done, uint64_t total);
typedef bool (*ModsFetch)(void *, const ModsRelease *, const char *destination);
typedef struct {
    void *data;
    ModsProgress update;
    ModsFetch fetch;
} ModsCallbacks;

typedef struct {
    bool installed;
    int file_id, base_id;
    char version[96];
} ModsInstalled;

bool mods_installed(const char *directory, ModsKind kind, ModsInstalled *);
bool mods_apply(const char *root, const HubGame *, ModsKind kind,
                const ModsRelease *release, const ModsRelease *base,
                const char *local_archive, const ModsCallbacks *, char error[160]);

#endif
