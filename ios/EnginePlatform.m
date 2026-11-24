#import <UIKit/UIKit.h>
#import <OpenGLES/ES3/gl.h>
#include <SDL_syswm.h>
#include "EnginePlatform.h"
#include "system4.h"
#include "mixer.h"

void savedata_flush_on_background(void);
void alicehub_ios_localize_modifier_button(void *button);
void *alicehub_ios_copy_localized_text(const char *key);

static NSString *hub_text(const char *key)
{
    return CFBridgingRelease(alicehub_ios_copy_localized_text(key));
}

static void (*gamepad_attach)(UIWindow *);
static void (*gamepad_command)(int);
static void (*gamepad_frame)(float, float, bool, bool, bool, bool);
static __weak UIWindow *game_window;

void alicehub_ios_open_manual(const char *filename)
{
    NSString *path = [NSString stringWithUTF8String:filename];
    UIViewController *host = game_window.rootViewController;
    if (path && host)
        [NSNotificationCenter.defaultCenter postNotificationName:@"AliceHubOpenManual" object:host
            userInfo:@{@"path": path}];
}

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
static __weak UIWindow *launcher_window;
static id inactive_observer;
static id active_observer;

void alicehub_ios_begin_game(void)
{
    for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
        if ([scene isKindOfClass:UIWindowScene.class] && scene.activationState == UISceneActivationStateForegroundActive) {
            launcher_window = ((UIWindowScene *)scene).keyWindow;
            break;
        }
    }
}

void alicehub_ios_end_game(void)
{
    NSNotificationCenter *notifications = NSNotificationCenter.defaultCenter;
    if (inactive_observer)
        [notifications removeObserver:inactive_observer];
    if (active_observer)
        [notifications removeObserver:active_observer];
    inactive_observer = active_observer = nil;
    background_hook = NULL;
    alicehub_ios_set_gamepad_callbacks(NULL, NULL, NULL);
    game_window.hidden = YES;
    game_window = nil;
    [launcher_window makeKeyAndVisible];
    if (@available(iOS 16.0, *))
        [launcher_window.rootViewController setNeedsUpdateOfSupportedInterfaceOrientations];
    launcher_window = nil;
}

void alicehub_ios_request_quit(void)
{
    UIViewController *host = game_window.rootViewController;
    if (!host.view.window || host.presentedViewController)
        return;
    UIAlertController *dialog = [UIAlertController
        alertControllerWithTitle:hub_text("退出游戏")
        message:nil preferredStyle:UIAlertControllerStyleAlert];
    [dialog addAction:[UIAlertAction actionWithTitle:hub_text("取消")
        style:UIAlertActionStyleCancel handler:nil]];
    [dialog addAction:[UIAlertAction actionWithTitle:hub_text("退出")
        style:UIAlertActionStyleDestructive handler:^(UIAlertAction *action) {
            SDL_Event event = {.type = SDL_QUIT};
            SDL_PushEvent(&event);
        }]];
    [host presentViewController:dialog animated:YES completion:nil];
}

static void add_quit_button(UIView *view)
{
    UIButton *quit = [UIButton buttonWithType:UIButtonTypeSystem];
    [quit setImage:[UIImage systemImageNamed:@"xmark.circle.fill"] forState:UIControlStateNormal];
    quit.tintColor = UIColor.whiteColor;
    quit.backgroundColor = [UIColor colorWithWhite:0 alpha:0.5];
    quit.layer.cornerRadius = 20;
    quit.accessibilityLabel = hub_text("退出游戏");
    [quit addAction:[UIAction actionWithHandler:^(UIAction *action) {
        SDL_Event event = {.type = SDL_QUIT};
        SDL_PushEvent(&event);
    }] forControlEvents:UIControlEventTouchUpInside];
    quit.translatesAutoresizingMaskIntoConstraints = NO;
    [view addSubview:quit];
    [NSLayoutConstraint activateConstraints:@[
        [quit.topAnchor constraintEqualToAnchor:view.safeAreaLayoutGuide.topAnchor constant:6],
        [quit.trailingAnchor constraintEqualToAnchor:view.safeAreaLayoutGuide.trailingAnchor constant:-6],
        [quit.widthAnchor constraintEqualToConstant:40],
        [quit.heightAnchor constraintEqualToConstant:40],
    ]];
}

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

static void add_modifier_button(UIViewController *root)
{
    UIView *view = root.view;
    if (!view || [view viewWithTag:0x4D4F44])
        return;
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    button.tag = 0x4D4F44;
    button.frame = CGRectMake(12, 60, 44, 44);
    button.layer.cornerRadius = 22;
    button.backgroundColor = [UIColor colorWithWhite:0 alpha:0.45];
    button.tintColor = UIColor.whiteColor;
    alicehub_ios_localize_modifier_button((__bridge void *)button);
    [button setImage:[UIImage systemImageNamed:@"wand.and.stars"] forState:UIControlStateNormal];
    [button addAction:[UIAction actionWithHandler:^(UIAction *action) {
        [NSNotificationCenter.defaultCenter postNotificationName:@"AliceHubOpenModifier" object:root];
    }] forControlEvents:UIControlEventTouchUpInside];
    UIPanGestureRecognizer *pan = [[UIPanGestureRecognizer alloc] init];
    [pan addTarget:button action:@selector(alicehub_dragModifierButton:)];
    [button addGestureRecognizer:pan];
    [view addSubview:button];
    dispatch_async(dispatch_get_main_queue(), ^{
        UIEdgeInsets safe = view.safeAreaInsets;
        button.frame = CGRectOffset(button.frame, safe.left, MAX(safe.top - 48, 0));
    });
}

@implementation UIButton (AliceHubModifier)
- (void)alicehub_dragModifierButton:(UIPanGestureRecognizer *)pan
{
    CGPoint delta = [pan translationInView:self.superview];
    CGRect bounds = UIEdgeInsetsInsetRect(self.superview.bounds, self.superview.safeAreaInsets);
    CGFloat half = self.bounds.size.width / 2;
    self.center = CGPointMake(MIN(MAX(self.center.x + delta.x, CGRectGetMinX(bounds) + half), CGRectGetMaxX(bounds) - half),
                              MIN(MAX(self.center.y + delta.y, CGRectGetMinY(bounds) + half), CGRectGetMaxY(bounds) - half));
    [pan setTranslation:CGPointZero inView:self.superview];
}
@end

static void show_game_window(SDL_Window *window, bool show_quit_button)
{
    SDL_SysWMinfo info = window_info(window);
    UIWindow *native = info.info.uikit.window;
    if (launcher_window.windowScene)
        native.windowScene = launcher_window.windowScene;
    game_window = native;
    native.windowLevel = UIWindowLevelNormal + 1;
    [native makeKeyAndVisible];
    if (show_quit_button)
        add_quit_button(native.rootViewController.view);
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
}

void alicehub_ios_prepare_window(SDL_Window *window)
{
    show_game_window(window, background_hook != NULL);
    UIWindow *native = game_window;
    add_modifier_button(native.rootViewController);
    if (!inactive_observer) {
        inactive_observer = [NSNotificationCenter.defaultCenter addObserverForName:UISceneWillDeactivateNotification
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
    if (!active_observer) {
        active_observer = [NSNotificationCenter.defaultCenter addObserverForName:UISceneDidActivateNotification
            object:native.windowScene queue:nil usingBlock:^(NSNotification *notification) {
                if (background_hook)
                    background_hook(SDL_FALSE);
                else {
                    mixer_set_app_backgrounded(false);
                    mixer_retry_audio_device();
                }
            }];
    }
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
