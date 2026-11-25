#include "hub.h"

enum { SORT, RESCAN, SETTING_ENTRIES };

static HubItem item(const void *data, size_t index)
{
    HubApp *app = *(HubApp *const *)data;
    if (index == SORT)
        return (HubItem){"Library sort order", hub_settings(app)->sort == HUB_SORT_ENGINE ? "By engine" : "By name"};
    if (index == RESCAN)
        return (HubItem){"Rescan game folders", "SD card"};
    const HubActionEntry *entry = hub_action_entry(app, HUB_SETTING_ENTRY, NULL, index - SETTING_ENTRIES);
    return (HubItem){entry->label, NULL};
}

static size_t count_entries(HubApp *app)
{
    size_t n = 0;
    while (hub_action_entry(app, HUB_SETTING_ENTRY, NULL, n))
        ++n;
    return n + SETTING_ENTRIES;
}

static void draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)unused;
    size_t count = count_entries(app);
    if (*focus >= count)
        *focus = 0;
    hub_heading(app, "Settings", "GLOBAL  /  ALICEHUB");
    hub_list(app, count, *focus, item, &app);
    hub_hints_extra(app, *focus == SORT ? "Change" : "Select", "Back", NULL, NULL, count > 5);
}

static bool input(HubApp *app, size_t *focus, const HubGame *unused, HubInput event)
{
    (void)unused;
    if (event != HUB_CONFIRM)
        return hub_move_focus(focus, count_entries(app), 1, 5, event);
    if (*focus == SORT) {
        HubSettings next = *hub_settings(app);
        next.sort = next.sort == HUB_SORT_ENGINE ? HUB_SORT_NAME : HUB_SORT_ENGINE;
        if (!hub_apply_settings(app, &next))
            hub_dialog(app, "Could not save settings", "Check that the SD card is writable.");
    } else if (*focus == RESCAN) {
        hub_toast(app, hub_refresh_library(app) ? "Game library refreshed" : "Could not scan the SD card library");
    } else {
        const HubActionEntry *entry = hub_action_entry(app, HUB_SETTING_ENTRY, NULL, *focus - SETTING_ENTRIES);
        if (entry)
            hub_push(app, entry->screen_id, NULL);
    }
    return true;
}

bool hub_register_settings_screen(HubApp *app)
{
    static const HubScreen screen = {"settings", draw, input};
    return hub_register_screen(app, &screen);
}
