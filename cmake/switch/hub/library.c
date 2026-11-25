#include "hub.h"

#include <string.h>
#include <switch.h>

static char query[257];
static char selected_id[HUB_ID_SIZE];

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

static size_t find_focus(const LibraryView *view, const char *id)
{
    if (*id) {
        size_t index = 0;
        for (size_t i = 0; i < view->library->count; ++i) {
            const HubGame *game = &view->library->games[i];
            if (hub_game_matches(game, query)) {
                if (!strcmp(game->instance_id, id))
                    return index;
                ++index;
            }
        }
    }
    return 0;
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
        *focus = 0;
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
    if (*focus >= view.count)
        *focus = view.count ? view.count - 1 : 0;
    char subtitle[sizeof(query) + 48];
    const char *sort = hub_settings(app)->sort == HUB_SORT_ENGINE ? "ENGINE" : "NAME";
    if (*query)
        snprintf(subtitle, sizeof(subtitle), "SEARCH: %s  /  SORT: %s", query, sort);
    else
        snprintf(subtitle, sizeof(subtitle), "ALL ENGINES  /  SORT: %s", sort);
    hub_heading(app, "Your games", subtitle);
    if (*query && !view.count)
        hub_empty(app, "No matching games", "Press X to edit your search or B to clear it.");
    else
        hub_grid(app, view.count, *focus, game_item, &view);
    hub_hints_extra(app, view.count ? "Details" : NULL, *query ? "Clear" : "Exit",
                    "Search", "Settings", view.count > 4);
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
        *focus = 0;
        return true;
    }
    const LibraryView view = library_view(app);
    if (event == HUB_CONFIRM || event == HUB_SECONDARY) {
        const HubGame *game = game_at(&view, *focus);
        if (event == HUB_CONFIRM && !game)
            return false;
        if (event == HUB_CONFIRM ? hub_push(app, "details", game) : hub_push(app, "settings", NULL)) {
            snprintf(selected_id, sizeof(selected_id), "%s", game ? game->instance_id : "");
            *focus = (size_t)-1;
        }
        return true;
    }
    return hub_move_focus(focus, view.count, 4, 4, event);
}

bool hub_register_library_screen(HubApp *app)
{
    static const HubScreen screen = {"library", draw, input};
    return hub_register_screen(app, &screen);
}
