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

typedef enum { HUB_SORT_NAME, HUB_SORT_ENGINE, HUB_SORT_COUNT } HubSort;
typedef struct { HubSort sort; } HubSettings;

typedef struct {
    HubGame game;
    char error[512];
} HubHandoff;

bool hub_game_init(HubGame *game, HubEngine engine, const char *name);
bool hub_game_path(const char *root, const HubGame *game, char path[HUB_PATH_SIZE]);
bool hub_game_save_path(const char *root, const HubGame *game, char path[HUB_PATH_SIZE]);
bool hub_library_scan(HubLibrary *library, const char *root);
void hub_library_free(HubLibrary *library);
bool hub_settings_load(const char *root, HubSettings *settings);
bool hub_settings_save(const char *root, const HubSettings *settings);
bool hub_handoff_parse(FILE *file, HubHandoff *handoff);
bool hub_handoff_write(const char *root, bool returning, const HubHandoff *handoff);
bool hub_handoff_take(const char *root, bool returning, HubHandoff *handoff);
void hub_handoff_clear(const char *root, bool returning);

#endif
