#include "hub.h"

enum { PLAY, GAME_FOLDER, SAVE_FOLDER, DETAIL_ACTIONS };

typedef struct {
    HubApp *app;
    const HubGame *game;
} Details;

static HubItem item(const void *data, size_t index)
{
    const Details *details = data;
    switch (index) {
    case PLAY: return (HubItem){"Start game", NULL};
    case GAME_FOLDER: return (HubItem){"Game folder", "SD card"};
    case SAVE_FOLDER: return (HubItem){"Save folder", "SD card"};
    default: {
        const HubActionEntry *entry =
            hub_action_entry(details->app, HUB_DETAIL_ACTION, details->game, index - DETAIL_ACTIONS);
        return (HubItem){entry->label, NULL};
    }
    }
}

static size_t count_actions(HubApp *app, const HubGame *game)
{
    size_t n = 0;
    while (hub_action_entry(app, HUB_DETAIL_ACTION, game, n))
        ++n;
    return n + DETAIL_ACTIONS;
}

static void draw(HubApp *app, size_t *focus, const HubGame *game)
{
    if (!game)
        return;
    size_t count = count_actions(app, game);
    if (*focus >= count)
        *focus = 0;
    Details details = {app, game};
    char subtitle[64];
    snprintf(subtitle, sizeof(subtitle), "%s  /  LOCAL GAME", hub_engines[game->engine].label);
    hub_heading(app, game->name, subtitle);
    hub_list(app, count, *focus, item, &details);
    hub_hints_extra(app, *focus == PLAY ? "Start" : "Open", "Back", NULL, NULL, count > 5);
}

static bool input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    if (!game)
        return false;
    if (event != HUB_CONFIRM)
        return hub_move_focus(focus, count_actions(app, game), 1, 5, event);
    if (*focus == PLAY)
        hub_launch(app, game);
    else if (*focus == GAME_FOLDER || *focus == SAVE_FOLDER) {
        char path[HUB_PATH_SIZE];
        bool is_game = *focus == GAME_FOLDER;
        bool ok = is_game ? hub_game_path(HUB_ROOT, game, path) : hub_game_save_path(HUB_ROOT, game, path);
        hub_dialog(app, is_game ? "Game folder" : "Save folder", ok ? path : "Path is too long");
    } else {
        const HubActionEntry *entry =
            hub_action_entry(app, HUB_DETAIL_ACTION, game, *focus - DETAIL_ACTIONS);
        if (entry)
            hub_push(app, entry->screen_id, game);
    }
    return true;
}

bool hub_register_details_screen(HubApp *app)
{
    static const HubScreen screen = {"details", draw, input};
    return hub_register_screen(app, &screen);
}
