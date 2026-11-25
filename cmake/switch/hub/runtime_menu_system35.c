#include "runtime_menu.h"
#include "runtime_menu_engines.h"

#include <stdio.h>
#include "escalayer_modifier.h"
#include "gfx.h"

static bool ctrl_skip;

enum {
    CTRL_SKIP, HEAL, REFILL_LIBIDO, WEAKEN_ENEMY,
    LOCK_HP, LOCK_LIBIDO, LOCK_ENEMY, LOCK_LOVE
};

static bool present(void *renderer, SDL_Surface *panel)
{
    return switch_menu_present_sdl(renderer, panel);
}

static bool capture(void *context, const char *path)
{
    (void)context;
    return save_screenshot(path);
}

static size_t options(void *context, bool modifiers, SwitchMenuRow *rows)
{
    (void)context;
    if (!modifiers) {
        rows[0] = (SwitchMenuRow){"Ctrl skip", CTRL_SKIP, true, {0}};
        SDL_strlcpy(rows[0].value, ctrl_skip ? "On" : "Off", sizeof(rows[0].value));
        return 1;
    }
    if (!escalayer_modifier_available())
        return 0;

    size_t n = 0;
    rows[n++] = (SwitchMenuRow){.label = "Heal now", .action = HEAL};
    rows[n++] = (SwitchMenuRow){.label = "Refill libido", .action = REFILL_LIBIDO};
    rows[n++] = (SwitchMenuRow){.label = "Set enemy HP to 1", .action = WEAKEN_ENEMY};
    static const char *const labels[] = {"Lock HP", "Lock libido", "One-hit defeats", "Lock affection"};
    int locks = xsystem35_modifier_get_locks();
    for (size_t i = 0; i < SDL_arraysize(labels); ++i) {
        rows[n] = (SwitchMenuRow){labels[i], LOCK_HP + (int)i, true, {0}};
        SDL_strlcpy(rows[n].value, (locks & (1 << i)) ? "On" : "Off", sizeof(rows[n].value));
        ++n;
    }
    return n;
}

static void activate(void *context, int action, int direction, char *status)
{
    (void)context;
    (void)direction;
    switch (action) {
    case CTRL_SKIP: ctrl_skip = !ctrl_skip; break;
    case HEAL: xsystem35_modifier_apply_action("esc_heal"); break;
    case REFILL_LIBIDO: xsystem35_modifier_apply_action("esc_libido_max"); break;
    case WEAKEN_ENEMY: xsystem35_modifier_apply_action("esc_enemy_onehit"); break;
    case LOCK_HP: case LOCK_LIBIDO: case LOCK_ENEMY: case LOCK_LOVE:
        xsystem35_modifier_set_locks(xsystem35_modifier_get_locks() ^ (1 << (action - LOCK_HP)));
        break;
    }
    if (action >= HEAL && action <= WEAKEN_ENEMY)
        snprintf(status, SWITCH_MENU_STATUS, "Queued for when the game resumes.");
}

static const SwitchMenu menu = {
    "romfs:/fonts/MTLc3m.ttf", "bmp", present, capture, options, activate
};

bool switch_menu35_poll(SDL_Renderer *renderer)
{
    return renderer && switch_menu_poll(&menu, renderer);
}

bool switch_menu35_ctrl_skip(void)
{
    return ctrl_skip;
}

void switch_menu35_release_skip(void)
{
    ctrl_skip = false;
}
