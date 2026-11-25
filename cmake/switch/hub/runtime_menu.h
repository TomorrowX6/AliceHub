#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <SDL.h>

#ifdef __cplusplus
extern "C" {
#endif

#define SWITCH_MENU_ROWS 16
#define SWITCH_MENU_STATUS 96

typedef struct {
    const char *label;
    int action;
    bool adjustable;
    char value[24];
} SwitchMenuRow;

typedef struct {
    const char *font;
    const char *capture_extension;
    bool (*present)(void *, SDL_Surface *);
    bool (*capture)(void *, const char *);
    size_t (*options)(void *, bool modifiers, SwitchMenuRow rows[SWITCH_MENU_ROWS]);
    void (*activate)(void *, int action, int direction, char status[SWITCH_MENU_STATUS]);
} SwitchMenu;

// Runs on the engine thread; returns true if menu input was consumed.
bool switch_menu_poll(const SwitchMenu *menu, void *context);
bool switch_menu_lifecycle_pending(void);
bool switch_menu_present_sdl(SDL_Renderer *renderer, SDL_Surface *panel);
bool switch_menu_capture(const SwitchMenu *menu, void *context, char *filename, size_t size);

#ifdef __cplusplus
}
#endif
