#include "runtime_menu.h"
#include "ags.h"
#include "msgskip.h"

extern SDL_Renderer *g_renderer;

namespace {
struct Game {
    AGS *ags;
    MsgSkip *skip;
};

bool present(void *, SDL_Surface *panel)
{
    return switch_menu_present_sdl(g_renderer, panel);
}

bool capture(void *context, const char *path)
{
    return static_cast<Game *>(context)->ags->save_screenshot(path);
}

size_t options(void *context, bool modifiers, SwitchMenuRow *rows)
{
    if (modifiers)
        return 0;
    rows[0] = {"Skip read text", 0, true, {}};
    SDL_strlcpy(rows[0].value, static_cast<Game *>(context)->skip->is_activated() ? "On" : "Off",
                sizeof(rows[0].value));
    return 1;
}

void activate(void *context, int action, int direction, char *status)
{
    (void)action;
    (void)direction;
    (void)status;
    MsgSkip *skip = static_cast<Game *>(context)->skip;
    skip->activate(!skip->is_activated());
}

constexpr SwitchMenu menu{"romfs:/fonts/MTLc3m.ttf", "bmp", present, capture, options, activate};
}

bool switch_menu_system3_poll(AGS *ags, MsgSkip *skip)
{
    if (!ags || !skip || !g_renderer)
        return false;
    Game game{ags, skip};
    if (!switch_menu_poll(&menu, &game))
        return false;
    if (!switch_menu_lifecycle_pending())
        ags->update_screen();
    return true;
}
