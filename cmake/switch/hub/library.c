#include "hub.h"

#include <string.h>
#include <switch.h>

static char query[257];
static char selected_id[HUB_ID_SIZE];

enum { ONLINE_DOWNLOAD, FIRST_GAME, GAME_COLUMNS = 4 };

typedef struct {
    const HubLibrary *library;
    size_t count;
} LibraryView;

static LibraryView library_view(HubApp *app)
{
    LibraryView view = {.library = hub_library(app)};
    if (!*query)
        view.count = view.library->count;
    else
        for (size_t i = 0; i < view.library->count; ++i)
            view.count += hub_game_matches(&view.library->games[i], query);
    return view;
}

static const HubGame *game_at(const LibraryView *view, size_t index)
{
    if (index >= view->count)
        return NULL;
    if (!*query)
        return &view->library->games[index];
    for (size_t i = 0; i < view->library->count; ++i) {
        const HubGame *game = &view->library->games[i];
        if (hub_game_matches(game, query) && !index--)
            return game;
    }
    return NULL;
}

static HubItem game_item(const void *data, size_t index)
{
    const HubGame *game = game_at(data, index);
    return (HubItem){game->name, hub_engines[game->engine].label};
}

static bool game_artwork(const void *data, size_t index, char key[HUB_PATH_SIZE])
{
    return hub_game_path(HUB_ROOT, game_at(data, index), key);
}

static size_t find_focus(const LibraryView *view, const char *id)
{
    if (*id) {
        size_t index = 0;
        for (size_t i = 0; i < view->library->count; ++i) {
            const HubGame *game = &view->library->games[i];
            if (hub_game_matches(game, query)) {
                if (!strcmp(game->instance_id, id))
                    return FIRST_GAME + index;
                ++index;
            }
        }
    }
    return ONLINE_DOWNLOAD;
}

static void search(HubApp *app, size_t *focus)
{
    char text[sizeof(query)] = {0};
    SwkbdConfig keyboard;
    Result rc = swkbdCreate(&keyboard, 0);
    if (R_SUCCEEDED(rc)) {
        swkbdConfigMakePresetDefault(&keyboard);
        swkbdConfigSetType(&keyboard, SwkbdType_Normal);
        swkbdConfigSetHeaderText(&keyboard, "Search your games");
        swkbdConfigSetGuideText(&keyboard, "Enter part of a game name");
        swkbdConfigSetInitialText(&keyboard, query);
        swkbdConfigSetStringLenMax(&keyboard, 64);
        rc = swkbdShow(&keyboard, text, sizeof(text));
        swkbdClose(&keyboard);
    }
    hub_wait_for_release(app);
    if (R_SUCCEEDED(rc)) {
        strcpy(query, text);
        *focus = library_view(app).count ? FIRST_GAME : ONLINE_DOWNLOAD;
    } else if (rc != MAKERESULT(Module_Libnx, LibnxError_LibAppletBadExit)) {
        hub_toast(app, "Could not open the software keyboard");
    }
}

static void draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)unused;
    const LibraryView view = library_view(app);
    if (*focus == (size_t)-1) {
        *focus = find_focus(&view, *selected_id ? selected_id : hub_returned_instance(app));
        *selected_id = 0;
    }
    if (*focus > view.count)
        *focus = view.count;
    char subtitle[sizeof(query) + 48];
    const char *sort = hub_settings(app)->sort == HUB_SORT_ENGINE ? "ENGINE" : "NAME";
    if (*query)
        snprintf(subtitle, sizeof(subtitle), "SEARCH: %s  /  SORT: %s", query, sort);
    else
        snprintf(subtitle, sizeof(subtitle), "ALL ENGINES  /  SORT: %s", sort);
    hub_library_heading(app, subtitle, *focus == ONLINE_DOWNLOAD);
    if (*query && !view.count)
        hub_empty(app, "No matching games", "Press X to edit your search or B to clear it.");
    else
        hub_grid_art(app, view.count, *focus == ONLINE_DOWNLOAD ? SIZE_MAX : *focus - FIRST_GAME,
                     game_item, game_artwork, &view);
    hub_hints_extra(app, *focus == ONLINE_DOWNLOAD ? "Open" : "Details", *query ? "Clear" : "Exit",
                    "Search", "Settings", view.count > GAME_COLUMNS);
}

static bool input(HubApp *app, size_t *focus, const HubGame *unused, HubInput event)
{
    (void)unused;
    if (event == HUB_ACTION) {
        search(app, focus);
        return true;
    }
    if (event == HUB_BACK && *query) {
        *query = 0;
        *focus = ONLINE_DOWNLOAD;
        return true;
    }
    const LibraryView view = library_view(app);
    if (event == HUB_CONFIRM && *focus == ONLINE_DOWNLOAD)
        return hub_push(app, "online.catalog", NULL);
    if (event == HUB_CONFIRM || event == HUB_SECONDARY) {
        const HubGame *game = *focus == ONLINE_DOWNLOAD ? NULL : game_at(&view, *focus - FIRST_GAME);
        if (event == HUB_CONFIRM && !game)
            return false;
        if (event == HUB_CONFIRM ? hub_push(app, "details", game) : hub_push(app, "settings", NULL)) {
            snprintf(selected_id, sizeof(selected_id), "%s", game ? game->instance_id : "");
            *focus = (size_t)-1;
        }
        return true;
    }
    if (*focus == ONLINE_DOWNLOAD) {
        if (view.count && (event == HUB_DOWN || event == HUB_NEXT_PAGE)) {
            *focus = FIRST_GAME;
            return true;
        }
        return false;
    }
    size_t game = *focus - FIRST_GAME;
    if (event == HUB_UP && game < GAME_COLUMNS) {
        *focus = ONLINE_DOWNLOAD;
        return true;
    }
    if (!hub_move_focus(&game, view.count, GAME_COLUMNS, GAME_COLUMNS, event))
        return false;
    *focus = FIRST_GAME + game;
    return true;
}

bool hub_register_library_screen(HubApp *app)
{
    static const HubScreen screen = {"library", draw, input};
    return hub_register_screen(app, &screen);
}
