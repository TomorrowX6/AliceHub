#import <UIKit/UIKit.h>
#include <SDL.h>
#include <SDL_syswm.h>

SDL_Window *alicehub_system3_create_window(const char *title, int x, int y, int w, int h, Uint32 flags)
{
    return SDL_CreateWindow(title, x, y, w, h,
        flags | SDL_WINDOW_FULLSCREEN_DESKTOP | SDL_WINDOW_ALLOW_HIGHDPI);
}

SDL_Renderer *alicehub_system3_create_renderer(SDL_Window *window, int index, Uint32 flags)
{
    SDL_Renderer *renderer = SDL_CreateRenderer(window, index, flags);
    SDL_SysWMinfo info = {0};
    SDL_VERSION(&info.version);
    if (!renderer || !SDL_GetWindowWMInfo(window, &info) || info.subsystem != SDL_SYSWM_UIKIT)
        return renderer;

    UIWindow *native = info.info.uikit.window;
    for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
        if ([scene isKindOfClass:UIWindowScene.class] &&
            scene.activationState == UISceneActivationStateForegroundActive) {
            native.windowScene = (UIWindowScene *)scene;
            break;
        }
    }
    native.windowLevel = UIWindowLevelNormal + 1;
    [native makeKeyAndVisible];
    if (@available(iOS 16.0, *)) {
        [native.rootViewController setNeedsUpdateOfSupportedInterfaceOrientations];
        UIWindowSceneGeometryPreferencesIOS *preferences = [[UIWindowSceneGeometryPreferencesIOS alloc]
            initWithInterfaceOrientations:UIInterfaceOrientationMaskLandscape];
        [native.windowScene requestGeometryUpdateWithPreferences:preferences errorHandler:nil];
    } else {
        [UIViewController attemptRotationToDeviceOrientation];
    }

    UIView *view = native.rootViewController.view;
    UIButton *quit = [UIButton buttonWithType:UIButtonTypeSystem];
    [quit setImage:[UIImage systemImageNamed:@"xmark.circle.fill"] forState:UIControlStateNormal];
    quit.tintColor = UIColor.whiteColor;
    quit.backgroundColor = [UIColor colorWithWhite:0 alpha:0.5];
    quit.layer.cornerRadius = 20;
    quit.accessibilityLabel = NSLocalizedString(@"Quit", nil);
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
    return renderer;
}
