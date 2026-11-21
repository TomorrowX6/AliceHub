#import <UIKit/UIKit.h>
#import <OpenGLES/ES3/gl.h>
#include <SDL_syswm.h>
#include "EnginePlatform.h"
#include "system4.h"
#include "mixer.h"

void savedata_flush_on_background(void);

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
    button.accessibilityLabel = @"修改器";
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
    add_modifier_button(native.rootViewController);
    static id inactiveObserver;
    if (!inactiveObserver) {
        inactiveObserver = [NSNotificationCenter.defaultCenter addObserverForName:UISceneWillDeactivateNotification
            object:native.windowScene queue:nil usingBlock:^(NSNotification *notification) {
                if (SDL_GL_GetCurrentContext())
                    glFinish();
                mixer_set_app_backgrounded(true);
                savedata_flush_on_background();
            }];
    }
    static id activeObserver;
    if (!activeObserver) {
        activeObserver = [NSNotificationCenter.defaultCenter addObserverForName:UISceneDidActivateNotification
            object:native.windowScene queue:nil usingBlock:^(NSNotification *notification) {
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
