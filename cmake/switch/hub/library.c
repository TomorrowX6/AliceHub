#include "hub.h"

#include <string.h>

static HubItem game_item(const void *data, size_t index)
{
    const HubLibrary *library = data;
    const HubGame *game = &library->games[index];
    return (HubItem){game->name, hub_engines[game->engine].label};
}

static void draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)unused;
    const HubLibrary *library = hub_library(app);
    const char *returned = hub_returned_instance(app);
    if (*focus == (size_t)-1) {
        *focus = 0;
        for (size_t i = 0; i < library->count; ++i)
            if (!strcmp(library->games[i].instance_id, returned)) {
                *focus = i;
                break;
            }
    }
    hub_heading(app, "Your games", "SYSTEM 3  /  SYSTEM 3.5  /  SYSTEM 4");
    hub_grid(app, library->count, *focus, game_item, library);
    hub_hints(app, "Start", "Exit", NULL);
}

static bool input(HubApp *app, size_t *focus, const HubGame *unused, HubInput event)
{
    (void)unused;
    const HubLibrary *library = hub_library(app);
    if (event == HUB_CONFIRM && *focus < library->count) {
        hub_launch(app, &library->games[*focus]);
        return true;
    }
    return hub_move_focus(focus, library->count, 4, 4, event);
}

bool hub_register_library_screen(HubApp *app)
{
    static const HubScreen screen = {"library", draw, input};
    return hub_register_screen(app, &screen);
}
