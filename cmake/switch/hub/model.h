#ifndef ALICEHUB_HUB_MODEL_H
#define ALICEHUB_HUB_MODEL_H

#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>

#define HUB_ROOT "sdmc:/switch/alicehub"
#define HUB_NRO "sdmc:/switch/AliceHub.nro"
#define HUB_REQUEST_ARG "--alicehub-play"
#define HUB_RETURN_ARG "--alicehub-return"
#define HUB_NAME_SIZE 512
#define HUB_ID_SIZE 544
#define HUB_PATH_SIZE 512
#define HUB_BOOT_SETTING ".alicehub-launch-entry"
#define HUB_ENTRY_SAVES ".launch-entries"
#define HUB_DSR_SUFFIX "__SaveDataDSR"

typedef enum { HUB_SYSTEM3, HUB_XSYSTEM35, HUB_XSYSTEM4, HUB_ENGINE_COUNT } HubEngine;

typedef struct {
    const char *id;
    const char *label;
    const char *nro;
} HubEngineInfo;

extern const HubEngineInfo hub_engines[HUB_ENGINE_COUNT];

typedef struct {
    HubEngine engine;
    char name[HUB_NAME_SIZE];
    char game_id[HUB_ID_SIZE];
    char instance_id[HUB_ID_SIZE];
} HubGame;

typedef struct {
    HubGame *games;
    size_t count;
} HubLibrary;

typedef struct {
    char **names;
    size_t count;
} HubBootEntries;

typedef enum { HUB_SORT_NAME, HUB_SORT_ENGINE, HUB_SORT_COUNT } HubSort;
typedef struct { HubSort sort; } HubSettings;

typedef struct {
    HubGame game;
    char error[512];
} HubHandoff;

bool hub_game_init(HubGame *game, HubEngine engine, const char *name);
bool hub_game_path(const char *root, const HubGame *game, char path[HUB_PATH_SIZE]);
bool hub_game_uses_daisengoku_save_folder(const char *game_dir);
bool hub_system4_standard_ini(const char *name);
bool hub_system4_configured_script(const char *dir, const char *ini, char script[HUB_PATH_SIZE]);
bool hub_system4_boot_entries(const char *dir, HubBootEntries *entries);
void hub_boot_entries_free(HubBootEntries *entries);
bool hub_game_boot_get(const char *dir, char entry[HUB_NAME_SIZE]);
bool hub_game_boot_set(const char *dir, const char *entry);
bool hub_system4_resolve(const char *dir, char target[HUB_PATH_SIZE], bool *uses_dsr);
bool hub_system4_has_daisengoku_entry(const char *dir, bool *has_dsr);
bool hub_system4_save_key(const char *name, const char *dir, const char *target,
                          char key[HUB_PATH_SIZE]);
bool hub_system4_save_folder(const char *dir, char folder[HUB_NAME_SIZE]);
bool hub_system4_active_script(const char *dir, char script[HUB_PATH_SIZE]);
bool hub_game_save_path(const char *root, const HubGame *game, char path[HUB_PATH_SIZE]);
bool hub_game_save_manager_path(const char *root, const HubGame *game, char path[HUB_PATH_SIZE]);
bool hub_library_scan(HubLibrary *library, const char *root);
void hub_library_free(HubLibrary *library);
void hub_library_sort(HubLibrary *library, HubSort sort);
bool hub_game_matches(const HubGame *game, const char *query);
bool hub_settings_load(const char *root, HubSettings *settings);
bool hub_settings_save(const char *root, const HubSettings *settings);
bool hub_handoff_parse(FILE *file, HubHandoff *handoff);
bool hub_handoff_write(const char *root, bool returning, const HubHandoff *handoff);
bool hub_handoff_take(const char *root, bool returning, HubHandoff *handoff);
void hub_handoff_clear(const char *root, bool returning);

#endif
