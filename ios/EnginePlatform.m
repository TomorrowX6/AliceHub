#import <UIKit/UIKit.h>
#import <OpenGLES/ES3/gl.h>
#include <SDL_syswm.h>
#include "EnginePlatform.h"
#include "system4.h"
#include "mixer.h"

void savedata_flush_on_background(void);

static void (*gamepad_attach)(UIWindow *);
static void (*gamepad_command)(int);
static void (*gamepad_frame)(float, float, bool, bool, bool, bool);

void alicehub_ios_set_gamepad_callbacks(
    void (*attach)(UIWindow *), void (*command)(int),
    void (*frame)(float, float, bool, bool, bool, bool))
{
    gamepad_attach = attach;
    gamepad_command = command;
    gamepad_frame = frame;
}

void alicehub_ios_gamepad_command(int command)
{
    if (gamepad_command)
        gamepad_command(command);
}

void alicehub_ios_gamepad_frame(float x, float y, bool cursor, bool movie, bool controller, bool ctrl)
{
    if (gamepad_frame)
        gamepad_frame(x, y, cursor, movie, controller, ctrl);
}

static void (*background_hook)(SDL_bool);

void alicehub_ios_set_background_hook(void (*hook)(SDL_bool))
{
    background_hook = hook;
}

static SDL_SysWMinfo window_info(SDL_Window *window)
{
    SDL_SysWMinfo info = {0};
    SDL_VERSION(&info.version);
    if (!SDL_GetWindowWMInfo(window, &info) || info.subsystem != SDL_SYSWM_UIKIT)
        ERROR("Cannot access SDL UIKit window: %s", SDL_GetError());
    return info;
}

void alicehub_ios_prepare_window(SDL_Window *window)
{
    SDL_SysWMinfo info = window_info(window);
    UIWindow *native = info.info.uikit.window;
    for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
        if ([scene isKindOfClass:UIWindowScene.class] && scene.activationState == UISceneActivationStateForegroundActive) {
            native.windowScene = (UIWindowScene *)scene;
            break;
        }
    }
    native.windowLevel = UIWindowLevelNormal + 1;
    [native makeKeyAndVisible];
    static id inactiveObserver;
    if (!inactiveObserver) {
        inactiveObserver = [NSNotificationCenter.defaultCenter addObserverForName:UISceneWillDeactivateNotification
            object:native.windowScene queue:nil usingBlock:^(NSNotification *notification) {
                if (SDL_GL_GetCurrentContext())
                    glFinish();
                if (background_hook) {
                    background_hook(SDL_TRUE);
                } else {
                    mixer_set_app_backgrounded(true);
                    savedata_flush_on_background();
                }
            }];
    }
    static id activeObserver;
    if (!activeObserver) {
        activeObserver = [NSNotificationCenter.defaultCenter addObserverForName:UISceneDidActivateNotification
            object:native.windowScene queue:nil usingBlock:^(NSNotification *notification) {
                if (background_hook)
                    background_hook(SDL_FALSE);
                else
                    mixer_set_app_backgrounded(false);
            }];
    }
    if (@available(iOS 16.0, *)) {
        [native.rootViewController setNeedsUpdateOfSupportedInterfaceOrientations];
        UIWindowSceneGeometryPreferencesIOS *preferences = [[UIWindowSceneGeometryPreferencesIOS alloc]
            initWithInterfaceOrientations:UIInterfaceOrientationMaskLandscape];
        [native.windowScene requestGeometryUpdateWithPreferences:preferences errorHandler:^(NSError *error) {
            SDL_LogWarn(SDL_LOG_CATEGORY_VIDEO, "Landscape request failed: %s", error.localizedDescription.UTF8String);
        }];
    } else {
        [UIViewController attemptRotationToDeviceOrientation];
    }
    SDL_Log("iOS window attached: %s", native.windowScene ? "yes" : "no");
    if (gamepad_attach)
        gamepad_attach(native);
}

void alicehub_ios_bind_drawable(SDL_Window *window, const SDL_Rect *viewport)
{
    SDL_SysWMinfo info = window_info(window);
    glBindFramebuffer(GL_FRAMEBUFFER, info.info.uikit.framebuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, info.info.uikit.colorbuffer);
    int w, h, drawable_w, drawable_h;
    SDL_GetWindowSize(window, &w, &h);
    SDL_GL_GetDrawableSize(window, &drawable_w, &drawable_h);
    if (w > 0 && h > 0) {
        glViewport(viewport->x * drawable_w / w, viewport->y * drawable_h / h,
                   viewport->w * drawable_w / w, viewport->h * drawable_h / h);
    }
}
