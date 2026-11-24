#ifndef ALICEHUB_IOS_PLATFORM_H
#define ALICEHUB_IOS_PLATFORM_H

#include <stdbool.h>
#include <SDL.h>
#ifdef __OBJC__
@class UIWindow;
#endif
#ifdef __cplusplus
extern "C" {
#endif
#ifdef __OBJC__
void alicehub_ios_set_gamepad_callbacks(
    void (*attach)(UIWindow *window), void (*command)(int),
    void (*frame)(float x, float y, bool cursor, bool movie, bool controller, bool ctrl));
#endif
void alicehub_ios_begin_game(void);
void alicehub_ios_end_game(void);
void alicehub_ios_request_quit(void);
void alicehub_ios_set_background_hook(void (*hook)(SDL_bool backgrounded));
void alicehub_ios_prepare_window(SDL_Window *window);
void alicehub_ios_bind_drawable(SDL_Window *window, const SDL_Rect *viewport);
void alicehub_ios_gamepad_command(int command);
void alicehub_ios_gamepad_frame(float x, float y, bool cursor, bool movie, bool controller, bool ctrl);

#ifdef __cplusplus
}
#endif
#endif
