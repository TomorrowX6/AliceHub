#ifndef ALICEHUB_IOS_PLATFORM_H
#define ALICEHUB_IOS_PLATFORM_H

#include <SDL.h>
void alicehub_ios_set_background_hook(void (*hook)(SDL_bool backgrounded));
void alicehub_ios_prepare_window(SDL_Window *window);
void alicehub_ios_bind_drawable(SDL_Window *window, const SDL_Rect *viewport);

#endif
