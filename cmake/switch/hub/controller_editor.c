#include "controller_profile.h"
#include "hub.h"

#define PROFILE_SCREEN "controller.profile"
#define TOUCH_SCREEN "controller.touch"
#define POSITION_SCREEN "controller.position"

enum { EDIT_TOUCH, FIRST_BINDING, RESET_PROFILE = FIRST_BINDING + CP_BUTTONS, PROFILE_ITEMS };
enum { TOGGLE_TOUCH = CP_TOUCH_BUTTONS, TOUCH_ITEMS };

static ControllerProfile profile;
static ControllerProfileScope scope;
static size_t touch_slot;
static char labels[CP_TOUCH_BUTTONS][24], positions[CP_TOUCH_BUTTONS][48];
static const ControllerButton display_order[CP_BUTTONS] = {
    CP_A, CP_B, CP_X, CP_Y, CP_UP, CP_DOWN, CP_LEFT, CP_RIGHT,
    CP_L, CP_R, CP_ZL, CP_ZR, CP_MINUS, CP_PLUS, CP_LSTICK, CP_RSTICK
};

static const char *scope_name(void)
{
    switch (scope) {
    case CP_GAME: return "Per-game override";
    case CP_GLOBAL: return "Global profile";
    case CP_UNREADABLE: return "Could not read saved profile";
    default: return "Standard controls";
    }
}

static void save(HubApp *app, const HubGame *game, ControllerProfile next)
{
    if (!controller_profile_save(HUB_ROOT, game, &next)) {
        hub_dialog(app, "Could not save controls", "Check that the SD card is writable.");
        return;
    }
    profile = next;
    scope = game ? CP_GAME : CP_GLOBAL;
}

static unsigned cycle(unsigned current, int delta, unsigned count)
{
    return (current + count + delta) % count;
}

static HubItem profile_item(const void *context, size_t index)
{
    const HubGame *game = context;
    if (index == EDIT_TOUCH)
        return (HubItem){"Touch layout", profile.touch_enabled ? "Enabled" : "Disabled"};
    if (index == RESET_PROFILE)
        return (HubItem){game ? "Use global profile" : "Reset global profile", scope_name()};
    ControllerButton button = display_order[index - FIRST_BINDING];
    return (HubItem){controller_button_names[button], controller_button_names[profile.binding[button]]};
}

static void profile_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    if (*focus >= PROFILE_ITEMS) {
        *focus = FIRST_BINDING;
        scope = controller_profile_load(HUB_ROOT, game, &profile);
    }
    hub_heading(app, "Controller profile", game ? game->name : "Global controls");
    hub_list(app, PROFILE_ITEMS, *focus, profile_item, game);
    hub_hints(app, *focus >= FIRST_BINDING && *focus < RESET_PROFILE ? "Change binding" : "Select",
              "Back", "Touch layout");
}

static unsigned cycle_button(const HubGame *game, unsigned current, int delta)
{
    unsigned count = game && game->engine != HUB_XSYSTEM4 ? CP_L : CP_BUTTONS;
    unsigned index = current < count ? current : count;
    unsigned next = cycle(index, delta, count + 1);
    return next < count ? next : CP_BUTTONS;
}

static bool profile_input(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    if (input == HUB_ACTION || (input == HUB_CONFIRM && *focus == EDIT_TOUCH))
        return hub_push(app, TOUCH_SCREEN, game);
    if (input == HUB_CONFIRM && *focus == RESET_PROFILE) {
        if (controller_profile_reset(HUB_ROOT, game)) {
            scope = controller_profile_load(HUB_ROOT, game, &profile);
            hub_toast(app, game ? "Using global controls" : "Using standard controls");
        } else {
            hub_dialog(app, "Could not reset controls", "Check that the SD card is writable.");
        }
        return true;
    }
    if (*focus >= FIRST_BINDING && *focus < RESET_PROFILE &&
        (input == HUB_LEFT || input == HUB_RIGHT || input == HUB_CONFIRM)) {
        ControllerProfile next = profile;
        ControllerButton button = display_order[*focus - FIRST_BINDING];
        next.binding[button] = cycle_button(game, profile.binding[button], input == HUB_LEFT ? -1 : 1);
        save(app, game, next);
        return true;
    }
    return hub_move_focus(focus, PROFILE_ITEMS, 1, 5, input);
}

static HubItem touch_item(const void *context, size_t index)
{
    (void)context;
    if (index == TOGGLE_TOUCH)
        return (HubItem){"In-game touch overlay", profile.touch_enabled ? "Enabled" : "Disabled"};
    return (HubItem){labels[index], positions[index]};
}

static void touch_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    if (*focus >= TOUCH_ITEMS)
        *focus = TOGGLE_TOUCH;
    for (size_t i = 0; i < CP_TOUCH_BUTTONS; ++i) {
        ControllerTouchButton button = profile.touch[i];
        snprintf(labels[i], sizeof(labels[i]), "Virtual button %zu", i + 1);
        snprintf(positions[i], sizeof(positions[i]), "%s  /  Col %u, Row %u",
                 controller_button_names[button.button], button.column + 1u, button.row + 1u);
    }
    hub_heading(app, "Touch layout", game ? game->name : "Global controls");
    hub_list(app, TOUCH_ITEMS, *focus, touch_item, NULL);
    hub_hints(app, *focus == TOGGLE_TOUCH ? "Toggle overlay" : "Edit button", "Back", "Reset layout");
}

static bool touch_input(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    if (input == HUB_CONFIRM) {
        if (*focus == TOGGLE_TOUCH) {
            ControllerProfile next = profile;
            next.touch_enabled = !next.touch_enabled;
            save(app, game, next);
            return true;
        }
        if (*focus < CP_TOUCH_BUTTONS) {
            touch_slot = *focus;
            return hub_push(app, POSITION_SCREEN, game);
        }
    } else if (input == HUB_ACTION) {
        ControllerProfile next = profile;
        ControllerProfile defaults;
        controller_profile_defaults(&defaults);
        for (size_t i = 0; i < CP_TOUCH_BUTTONS; ++i)
            next.touch[i] = defaults.touch[i];
        save(app, game, next);
        return true;
    }
    return hub_move_focus(focus, TOUCH_ITEMS, 1, 5, input);
}

enum { VIRTUAL_BINDING, VIRTUAL_COLUMN, VIRTUAL_ROW, POSITION_ITEMS };

static HubItem position_item(const void *context, size_t index)
{
    const char *const *values = context;
    static const char *const names[] = {"Binding", "Column (left to right)", "Row (top to bottom)"};
    return (HubItem){names[index], values[index]};
}

static void position_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    if (*focus >= POSITION_ITEMS)
        *focus = 0;
    ControllerTouchButton button = profile.touch[touch_slot];
    char column[24], row[24];
    snprintf(column, sizeof(column), "%u of %u", button.column + 1u, CP_COLUMNS);
    snprintf(row, sizeof(row), "%u of %u", button.row + 1u, CP_ROWS);
    const char *values[] = {controller_button_names[button.button], column, row};
    hub_heading(app, labels[touch_slot], "Left / right to change; moving onto another button swaps positions");
    hub_list(app, POSITION_ITEMS, *focus, position_item, values);
    hub_hints(app, "Change value", "Back", NULL);
}

static bool position_input(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    if (*focus < POSITION_ITEMS && (input == HUB_LEFT || input == HUB_RIGHT || input == HUB_CONFIRM)) {
        int delta = input == HUB_LEFT ? -1 : 1;
        ControllerProfile next = profile;
        ControllerTouchButton button = next.touch[touch_slot];
        switch (*focus) {
        case VIRTUAL_BINDING:
            next.touch[touch_slot].button = cycle_button(game, button.button, delta);
            break;
        case VIRTUAL_COLUMN:
            controller_profile_move(&next, touch_slot, cycle(button.column, delta, CP_COLUMNS), button.row);
            break;
        case VIRTUAL_ROW:
            controller_profile_move(&next, touch_slot, button.column, cycle(button.row, delta, CP_ROWS));
            break;
        }
        save(app, game, next);
        return true;
    }
    return hub_move_focus(focus, POSITION_ITEMS, 1, 5, input);
}

void hub_register_controller_profiles(HubApp *app)
{
    static const HubScreen editor = {PROFILE_SCREEN, profile_draw, profile_input};
    static const HubScreen touch = {TOUCH_SCREEN, touch_draw, touch_input};
    static const HubScreen position = {POSITION_SCREEN, position_draw, position_input};
    static const HubActionEntry entry = {"Controller profile", PROFILE_SCREEN, NULL};
    hub_register_screen(app, &editor);
    hub_register_screen(app, &touch);
    hub_register_screen(app, &position);
    hub_register_action(app, HUB_SETTING_ENTRY, &entry);
    hub_register_action(app, HUB_DETAIL_ACTION, &entry);
}
