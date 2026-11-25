#ifndef ALICEHUB_SWITCH_CONTROLLER_RUNTIME_H
#define ALICEHUB_SWITCH_CONTROLLER_RUNTIME_H

#include <SDL.h>
#include "controller_profile.h"

#ifdef __cplusplus
extern "C" {
#endif

void switch_controls_load(const char *engine, const char *game_folder);
void switch_controls_release_touch(void);
bool switch_controls_remapping(void);
bool switch_controls_touch_enabled(void);
uint32_t switch_controls_buttons(uint32_t physical);
uint32_t switch_controls_gamecontroller(SDL_GameController *);
uint32_t switch_controls_joystick_button(unsigned index);
bool switch_controls_touch(const SDL_Event *);
int switch_controls_game_fingers(const SDL_TouchFingerEvent *);
bool switch_controls_gl_touch_event(const SDL_Event *, SDL_Window *);

typedef void (*ControllerDrawRect)(void *, const SDL_Rect *, SDL_Color);
void switch_controls_draw(int width, int height, ControllerDrawRect, void *);
void switch_controls_draw_sdl(SDL_Renderer *);

#ifdef __cplusplus
}
#endif
#endif
