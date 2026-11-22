#import <UIKit/UIKit.h>
#include <SDL_syswm.h>
#include "nact.h"
#include "encoding.h"

extern SDL_Window *g_window;

void NACT::text_dialog()
{
    if (tvar_index < 1 || tvar_index > MAX_STRVAR)
        return;

    @autoreleasepool {
        SDL_SysWMinfo info = {};
        SDL_VERSION(&info.version);
        if (!SDL_GetWindowWMInfo(g_window, &info) || info.subsystem != SDL_SYSWM_UIKIT)
            return;
        UIWindow *window = info.info.uikit.window;
        UIViewController *presenter = window.rootViewController;
        if (!presenter.view.window || presenter.presentedViewController)
            return;

        auto &value = tvar[tvar_index - 1];
        const auto initial = encoding->toUtf8(value);
        UIAlertController *dialog = [UIAlertController alertControllerWithTitle:nil message:nil
            preferredStyle:UIAlertControllerStyleAlert];
        [dialog addTextFieldWithConfigurationHandler:^(UITextField *field) {
            field.text = @(initial.c_str());
        }];
        __block BOOL done = NO;
        [dialog addAction:[UIAlertAction actionWithTitle:NSLocalizedString(@"Cancel", nil)
            style:UIAlertActionStyleCancel handler:^(UIAlertAction *action) { done = YES; }]];
        const NSUInteger maxLength = std::max(0, tvar_maxlen);
        __block NSString *result = nil;
        [dialog addAction:[UIAlertAction actionWithTitle:NSLocalizedString(@"OK", nil)
            style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
                NSString *input = dialog.textFields.firstObject.text ?: @"";
                NSUInteger end = input.length;
                if (end > maxLength)
                    end = [input rangeOfComposedCharacterSequenceAtIndex:maxLength].location;
                result = [input substringToIndex:end];
                done = YES;
            }]];

        [presenter presentViewController:dialog animated:NO completion:nil];
        while (!done && !window.hidden && presenter.presentedViewController == dialog) {
            @autoreleasepool {
                [NSRunLoop.currentRunLoop runMode:NSDefaultRunLoopMode
                    beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.05]];
            }
        }
        if (result)
            value = encoding->fromUtf8(result.UTF8String);
    }
}

void NACT::platform_initialize()
{
    mouse_move_enabled = false;
}

void NACT::platform_finalize()
{
}

void NACT::trace(const char *format, ...)
{
    va_list args;
    va_start(args, format);
    vfprintf(stdout, format, args);
    va_end(args);
}

void NACT::set_skip_menu_state(bool, bool)
{
}

bool NACT::handle_platform_event(const SDL_Event &)
{
    return false;
}
