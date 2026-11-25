#ifndef ALICEHUB_HUB_H
#define ALICEHUB_HUB_H

#include "model.h"

typedef struct HubApp HubApp;
typedef enum {
    HUB_UP, HUB_DOWN, HUB_LEFT, HUB_RIGHT, HUB_CONFIRM, HUB_BACK,
    HUB_ACTION, HUB_PREV_PAGE, HUB_NEXT_PAGE, HUB_SECONDARY
} HubInput;

typedef struct {
    const char *id;
    void (*draw)(HubApp *, size_t *focus, const HubGame *game);
    bool (*input)(HubApp *, size_t *focus, const HubGame *game, HubInput);
} HubScreen;

typedef enum { HUB_DETAIL_ACTION, HUB_SETTING_ENTRY, HUB_ACTION_GROUPS } HubActionGroup;
typedef struct {
    const char *label;
    const char *screen_id;
    bool (*available)(const HubGame *game);
} HubActionEntry;

bool hub_register_screen(HubApp *, const HubScreen *);
bool hub_register_action(HubApp *, HubActionGroup, const HubActionEntry *);
const HubActionEntry *hub_action_entry(const HubApp *, HubActionGroup, const HubGame *, size_t);
bool hub_push(HubApp *, const char *id, const HubGame *);
bool hub_pop(HubApp *);
void hub_wait_for_release(HubApp *);
void hub_dialog(HubApp *, const char *title, const char *detail);
void hub_toast(HubApp *, const char *message);
const HubLibrary *hub_library(const HubApp *);
bool hub_refresh_library(HubApp *);
HubSettings *hub_settings(HubApp *);
bool hub_apply_settings(HubApp *, const HubSettings *);
const char *hub_returned_instance(const HubApp *);
void hub_launch(HubApp *, const HubGame *);

typedef struct { const char *title, *subtitle; } HubItem;
typedef HubItem (*HubItems)(const void *, size_t);
typedef bool (*HubArtworkKey)(const void *, size_t, char key[HUB_PATH_SIZE]);
bool hub_move_focus(size_t *, size_t count, size_t columns, size_t page, HubInput);
void hub_grid(HubApp *, size_t count, size_t focus, HubItems, const void *);
void hub_grid_art(HubApp *, size_t count, size_t focus, HubItems, HubArtworkKey, const void *);
void hub_list(HubApp *, size_t count, size_t focus, HubItems, const void *);
void hub_list_art(HubApp *, size_t count, size_t focus, HubItems, HubArtworkKey, const void *);
void hub_heading(HubApp *, const char *title, const char *subtitle);
void hub_hints(HubApp *, const char *confirm, const char *back, const char *action);
void hub_hints_extra(HubApp *, const char *confirm, const char *back, const char *action,
                     const char *secondary, bool paging);
void hub_empty(HubApp *, const char *title, const char *detail);

typedef enum { HUB_PROGRESS_ACTIVE, HUB_PROGRESS_DONE, HUB_PROGRESS_FAILED } HubProgressState;
typedef struct {
    char id[48], title[96], detail[160];
    unsigned permille;
    HubProgressState state;
} HubProgress;

bool hub_progress_publish(const HubProgress *);
size_t hub_progress_snapshot(HubProgress *, size_t capacity);
void hub_progress_remove(const char *id);

void hub_register_features(HubApp *);
bool hub_register_library_screen(HubApp *);
bool hub_register_details_screen(HubApp *);
bool hub_register_settings_screen(HubApp *);

#endif
