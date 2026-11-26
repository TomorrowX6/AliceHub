#include "hub.h"

#include <string.h>

enum { PLAY, GAME_FOLDER, SAVE_FOLDER, BOOT_ENTRY };

static const char default_entry[] = "默认启动入口";
static HubBootEntries entries;
static bool loaded;
static size_t selected;

void hub_details_invalidate(void)
{
    hub_boot_entries_free(&entries);
    loaded = false;
    selected = 0;
}

static void load_entries(const HubGame *game)
{
    loaded = true;
    char dir[HUB_PATH_SIZE], entry[HUB_NAME_SIZE];
    if (game->engine != HUB_XSYSTEM4 || !hub_game_path(HUB_ROOT, game, dir) ||
        !hub_system4_boot_entries(dir, &entries) || !hub_game_boot_get(dir, entry))
        return;
    for (size_t i = 0; i < entries.count; ++i)
        if (!strcmp(entries.names[i], entry)) {
            selected = i + 1;
            break;
        }
}

static size_t first_action(void)
{
    return BOOT_ENTRY + (entries.count != 0);
}

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
    default:
        if (index < first_action())
            return (HubItem){"启动入口", selected ? entries.names[selected - 1] : default_entry};
    }
    const HubActionEntry *entry =
        hub_action_entry(details->app, HUB_DETAIL_ACTION, details->game, index - first_action());
    return (HubItem){entry->label, NULL};
}

static size_t count_actions(HubApp *app, const HubGame *game)
{
    size_t n = 0;
    while (hub_action_entry(app, HUB_DETAIL_ACTION, game, n))
        ++n;
    return n + first_action();
}

static void draw(HubApp *app, size_t *focus, const HubGame *game)
{
    if (!game)
        return;
    if (*focus == (size_t)-1)
        hub_details_invalidate();
    if (!loaded)
        load_entries(game);
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
    if (event == HUB_BACK) {
        hub_details_invalidate();
        return false;
    }
    if (event != HUB_CONFIRM)
        return hub_move_focus(focus, count_actions(app, game), 1, 5, event);
    if (*focus == PLAY)
        hub_launch(app, game);
    else if (*focus == GAME_FOLDER || *focus == SAVE_FOLDER) {
        char path[HUB_PATH_SIZE];
        bool is_game = *focus == GAME_FOLDER;
        bool ok = is_game ? hub_game_path(HUB_ROOT, game, path) : hub_game_save_path(HUB_ROOT, game, path);
        hub_dialog(app, is_game ? "Game folder" : "Save folder", ok ? path : "Path is too long");
    } else if (*focus < first_action()) {
        hub_push(app, "details.boot", game);
    } else {
        const HubActionEntry *entry =
            hub_action_entry(app, HUB_DETAIL_ACTION, game, *focus - first_action());
        if (entry)
            hub_push(app, entry->screen_id, game);
    }
    return true;
}

static HubItem boot_item(const void *unused, size_t index)
{
    (void)unused;
    return (HubItem){index ? entries.names[index - 1] : default_entry,
                     index == selected ? "Selected" : NULL};
}

static void boot_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    if (*focus > entries.count)
        *focus = selected;
    hub_heading(app, "启动入口", game->name);
    hub_list(app, entries.count + 1, *focus, boot_item, NULL);
    hub_hints_extra(app, "Select", "Back", NULL, NULL, entries.count + 1 > 5);
}

static bool boot_input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    if (event != HUB_CONFIRM)
        return hub_move_focus(focus, entries.count + 1, 1, 5, event);
    if (*focus > entries.count)
        return true;
    char dir[HUB_PATH_SIZE];
    const char *entry = *focus ? entries.names[*focus - 1] : "";
    if (*focus != selected && (!hub_game_path(HUB_ROOT, game, dir) || !hub_game_boot_set(dir, entry))) {
        hub_dialog(app, "Could not save launch entry", "Check the SD card.");
        return true;
    }
    selected = *focus;
    hub_pop(app);
    return true;
}

bool hub_register_details_screen(HubApp *app)
{
    static const HubScreen screen = {"details", draw, input};
    static const HubScreen boot = {"details.boot", boot_draw, boot_input};
    return hub_register_screen(app, &screen) && hub_register_screen(app, &boot);
}
