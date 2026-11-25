#include "runtime_menu.h"
#include "runtime_menu_engines.h"

#include <stdio.h>
#include <string.h>
#include "escalayer_modifier.h"
#include "gfx.h"

static bool ctrl_skip;

enum {
    CTRL_SKIP = 0,
    ACT_BASE = 10,
    LOCK_BASE = 30
};

typedef struct {
    const char *game;
    const char *action_labels[4];
    const char *action_specs[4];
    const char *lock_labels[4];
} Sys35GameSpec;

static const Sys35GameSpec sys35_specs[] = {
    {"escalayer",
     {"Heal now", "Refill libido", "Set enemy HP to 1", NULL},
     {"esc_heal", "esc_libido_max", "esc_enemy_onehit", NULL},
     {"Lock HP", "Lock libido", "One-hit defeats", "Lock affection"}},
    {"daiakuji",
     {"Max office gold", "Clear debt", "Boost ally HP", "Max love & popularity"},
     {"da_gold_max", "da_debt_zero", "da_hp_boost", "da_love_max"},
     {"Infinite gold", "Zero debt", "Lock ally HP", "Max love & popularity"}},
    {"rance5d",
     {"Heal party", "Boost party stats", "Max gold bars & EXP orbs", "Max affection"},
     {"r5d_heal", "r5d_stats_boost", "r5d_gold_exp", "r5d_love_max"},
     {"Lock party HP", "Infinite dungeon actions", "Infinite durability", "Max gold & EXP orbs"}},
    {"tsumamigui",
     {"Max stamina", "Max technique levels", "Max heroine levels", NULL},
     {"tm1_stamina_max", "tm1_tech_max", "tm1_heroine_max", NULL},
     {"Lock stamina", "Lock technique levels", NULL, NULL}},
    {"kichikuou",
     {"Gold +10M", "Max gold", "Reset actions", NULL},
     {"kr_gold_add=1000", "kr_gold_max", "kr_reset_actions", NULL},
     {"Infinite gold", "Unlimited actions", NULL, NULL}},
    {"kaerunyo",
     {"Gold +10000", "Max gold", "Max ropes & feathers", NULL},
     {"kn_gold_add=10000", "kn_gold_max", "kn_items_max", NULL},
     {"Infinite gold", "Infinite capture ropes", NULL, NULL}},
};

static const Sys35GameSpec *current_spec(void)
{
    const char *game = xsystem35_modifier_current_game();
    if (!game || !*game)
        return NULL;
    for (size_t i = 0; i < SDL_arraysize(sys35_specs); ++i) {
        if (!strcmp(game, sys35_specs[i].game))
            return &sys35_specs[i];
    }
    return NULL;
}

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
    const Sys35GameSpec *spec = current_spec();
    if (!spec)
        return 0;

    size_t n = 0;
    for (size_t i = 0; i < 4 && spec->action_labels[i]; ++i) {
        rows[n++] = (SwitchMenuRow){.label = spec->action_labels[i], .action = ACT_BASE + (int)i};
    }
    int locks = xsystem35_modifier_get_locks();
    for (size_t i = 0; i < 4 && spec->lock_labels[i]; ++i) {
        rows[n] = (SwitchMenuRow){spec->lock_labels[i], LOCK_BASE + (int)i, true, {0}};
        SDL_strlcpy(rows[n].value, (locks & (1 << i)) ? "On" : "Off", sizeof(rows[n].value));
        ++n;
    }
    return n;
}

static void activate(void *context, int action, int direction, char *status)
{
    (void)context;
    (void)direction;
    if (action == CTRL_SKIP) {
        ctrl_skip = !ctrl_skip;
        return;
    }
    const Sys35GameSpec *spec = current_spec();
    if (!spec)
        return;
    if (action >= ACT_BASE && action < ACT_BASE + 4) {
        int idx = action - ACT_BASE;
        if (spec->action_specs[idx]) {
            xsystem35_modifier_apply_action(spec->action_specs[idx]);
            snprintf(status, SWITCH_MENU_STATUS, "Queued for when the game resumes.");
        }
    } else if (action >= LOCK_BASE && action < LOCK_BASE + 4) {
        int bit = action - LOCK_BASE;
        xsystem35_modifier_set_locks(xsystem35_modifier_get_locks() ^ (1 << bit));
    }
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
