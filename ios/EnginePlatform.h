#ifndef ALICEHUB_IOS_PLATFORM_H
#define ALICEHUB_IOS_PLATFORM_H

#include <stdbool.h>
#include <SDL.h>
#ifdef __OBJC__
@class UIWindow;
void alicehub_ios_set_gamepad_callbacks(
    void (*attach)(UIWindow *window), void (*command)(int),
    void (*frame)(float x, float y, bool cursor, bool movie, bool controller, bool ctrl));
#endif
void alicehub_ios_prepare_window(SDL_Window *window);
void alicehub_ios_bind_drawable(SDL_Window *window, const SDL_Rect *viewport);
void alicehub_ios_gamepad_command(int command);
void alicehub_ios_gamepad_frame(float x, float y, bool cursor, bool movie, bool controller, bool ctrl);

#endif
