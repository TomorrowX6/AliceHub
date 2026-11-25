#ifndef ALICEHUB_SWITCH_CONTROLLER_PROFILE_H
#define ALICEHUB_SWITCH_CONTROLLER_PROFILE_H

#include "model.h"
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    CP_UP, CP_DOWN, CP_LEFT, CP_RIGHT, CP_A, CP_B, CP_X, CP_Y,
    CP_L, CP_R, CP_ZL, CP_ZR, CP_MINUS, CP_PLUS, CP_LSTICK, CP_RSTICK,
    CP_BUTTONS
} ControllerButton;

enum { CP_COLUMNS = 16, CP_ROWS = 9, CP_TOUCH_BUTTONS = 8 };

typedef struct {
    uint8_t button, column, row;
} ControllerTouchButton;

typedef struct {
    bool touch_enabled;
    uint8_t binding[CP_BUTTONS];
    ControllerTouchButton touch[CP_TOUCH_BUTTONS];
} ControllerProfile;

typedef enum { CP_DEFAULTS, CP_GLOBAL, CP_GAME, CP_UNREADABLE } ControllerProfileScope;

extern const char *const controller_button_names[CP_BUTTONS + 1];
void controller_profile_defaults(ControllerProfile *);
ControllerProfileScope controller_profile_load(const char *root, const HubGame *, ControllerProfile *);
bool controller_profile_save(const char *root, const HubGame *, const ControllerProfile *);
bool controller_profile_reset(const char *root, const HubGame *);
uint32_t controller_profile_map(const ControllerProfile *, uint32_t physical);
int controller_profile_hit(const ControllerProfile *, float x, float y);
bool controller_profile_move(ControllerProfile *, size_t slot, unsigned column, unsigned row);

#ifdef __cplusplus
}
#endif
#endif
