set(path "${SOURCE_DIR}/src/video/uikit/SDL_uikitmessagebox.m")
file(READ "${path}" source)

function(replace_checked old new)
  string(FIND "${source}" "${old}" position)
  if(position EQUAL -1)
    string(FIND "${source}" "${new}" position)
    if(position EQUAL -1)
      message(FATAL_ERROR "Unexpected SDL UIKit message box source")
    endif()
  else()
    string(REPLACE "${old}" "${new}" source "${source}")
    set(source "${source}" PARENT_SCOPE)
  endif()
endfunction()

set(old_wait [=[static void UIKit_WaitUntilMessageBoxClosed(const SDL_MessageBoxData *messageboxdata, int *clickedindex)
{
    *clickedindex = messageboxdata->numbuttons;

    @autoreleasepool {
        /* Run the main event loop until the alert has finished */
        /* Note that this needs to be done on the main thread */
        s_showingMessageBox = SDL_TRUE;
        while ((*clickedindex) == messageboxdata->numbuttons) {
            [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate distantFuture]];
        }
        s_showingMessageBox = SDL_FALSE;
    }
}]=])
set(new_wait [=[/* AliceHub: scene-aware presentation with a bounded wait for presentation failure. */
static void UIKit_WaitUntilMessageBoxClosed(const SDL_MessageBoxData *messageboxdata, int *clickedindex, UIAlertController *alert, UIWindow *window)
{
    *clickedindex = messageboxdata->numbuttons;

    @autoreleasepool {
        NSTimeInterval deadline = [NSDate timeIntervalSinceReferenceDate] + 5.0;
        BOOL presented = NO;
        s_showingMessageBox = SDL_TRUE;
        while ((*clickedindex) == messageboxdata->numbuttons) {
            if (window.hidden || window.windowScene.activationState != UISceneActivationStateForegroundActive) {
                break;
            }
            if (alert.presentingViewController && alert.view.window) {
                presented = YES;
            } else if (presented || [NSDate timeIntervalSinceReferenceDate] >= deadline) {
                break;
            }
            [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.01]];
        }
        s_showingMessageBox = SDL_FALSE;
    }
}]=])
replace_checked("${old_wait}" "${new_wait}")

set(old_present [=[    if (window == nil || window.rootViewController == nil) {
        alertwindow = [[UIWindow alloc] initWithFrame:[UIScreen mainScreen].bounds];
        alertwindow.rootViewController = [UIViewController new];
        alertwindow.windowLevel = UIWindowLevelAlert;

        window = alertwindow;

        [alertwindow makeKeyAndVisible];
    }

    [window.rootViewController presentViewController:alert animated:YES completion:nil];
    UIKit_WaitUntilMessageBoxClosed(messageboxdata, &clickedindex);

    if (alertwindow) {
        alertwindow.hidden = YES;
    }]=])
set(new_present [=[    /* AliceHub: SDL is hosted by a scene-based SwiftUI application. */
    UIWindowScene *scene = window.windowScene;
    if (!scene || scene.activationState != UISceneActivationStateForegroundActive) {
        scene = nil;
        for (UIScene *candidate in UIApplication.sharedApplication.connectedScenes) {
            if ([candidate isKindOfClass:UIWindowScene.class] && candidate.activationState == UISceneActivationStateForegroundActive) {
                scene = (UIWindowScene *)candidate;
                break;
            }
        }
    }
    if (!scene || messageboxdata->numbuttons <= 0) {
        return NO;
    }

    UIWindow *previousKeyWindow = nil;
    for (UIWindow *candidate in scene.windows) {
        if (candidate.isKeyWindow) {
            previousKeyWindow = candidate;
            break;
        }
    }
    alertwindow = [[UIWindow alloc] initWithWindowScene:scene];
    alertwindow.frame = scene.coordinateSpace.bounds;
    alertwindow.rootViewController = [UIViewController new];
    alertwindow.windowLevel = MAX(UIWindowLevelAlert, window.windowLevel + 1);
    [alertwindow makeKeyAndVisible];
    [alertwindow.rootViewController presentViewController:alert animated:NO completion:nil];
    UIKit_WaitUntilMessageBoxClosed(messageboxdata, &clickedindex, alert, alertwindow);

    [alert dismissViewControllerAnimated:NO completion:nil];
    alertwindow.hidden = YES;
    if (previousKeyWindow.windowScene == scene && !previousKeyWindow.hidden) {
        [previousKeyWindow makeKeyWindow];
    }
    if (clickedindex < 0 || clickedindex >= messageboxdata->numbuttons) {
        return NO;
    }]=])
replace_checked("${old_present}" "${new_present}")
file(WRITE "${path}" "${source}")
