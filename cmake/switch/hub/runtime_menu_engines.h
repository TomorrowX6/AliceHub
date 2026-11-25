#pragma once

#include <stdbool.h>
#include <SDL.h>

bool switch_menu35_poll(SDL_Renderer *renderer);
bool switch_menu35_ctrl_skip(void);
void switch_menu35_release_skip(void);

bool switch_menu4_poll(void);
void switch_menu4_load_display(const char *save_dir);
void switch_menu4_screenshot(void);
bool gfx_switch_menu_present(void *context, SDL_Surface *panel);
bool gfx_switch_menu_capture(void *context, const char *path);
