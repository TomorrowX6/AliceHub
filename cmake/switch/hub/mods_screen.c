#include "mods.h"
#include "online_download.h"

#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <switch.h>

#define TASK_ID "mods.overlay"

typedef enum { BROWSE, INSTALL, LOCAL, REMOVE } ModJob;

static struct {
    const ModsGame *descriptor;
    ModsKind kind;
    ModsCatalog catalog;
    ModsInstalled installed[3];
    bool state_ok[3];
    char installed_text[3][112];
    char local[HUB_PATH_SIZE], error[160], network_error[160];
    HubGame game;
    ModsRelease release, base;
    ModJob operation;
    Thread thread;
    bool live, locked, ok;
    atomic_bool canceled, done;
    AppletHookCookie hook;
} screen;

static const char *kind_label(ModsKind kind)
{
    if (kind == MODS_TRANSLATION) return "Chinese translation";
    if (kind == MODS_UNCENSORED) return "Uncensored CG patch";
    return "Mods and patches";
}

static void clear_catalog(void)
{
    free(screen.catalog.rows);
    screen.catalog = (ModsCatalog){0};
}

static void read_state(const HubGame *game)
{
    char path[HUB_PATH_SIZE];
    screen.descriptor = hub_game_path(HUB_ROOT, game, path) ? mods_game(path, game->engine) : NULL;
    if (!screen.descriptor) return;
    for (ModsKind k = MODS_ARCHIVE; k <= MODS_UNCENSORED; ++k) {
        ModsInstalled *state = &screen.installed[k];
        screen.state_ok[k] = mods_installed(path, k, state);
        if (!screen.state_ok[k])
            strcpy(screen.installed_text[k], "Restore record unreadable");
        else if (!state->installed)
            strcpy(screen.installed_text[k], "Not installed");
        else if (*state->version)
            snprintf(screen.installed_text[k], sizeof(screen.installed_text[k]), "Installed: %s", state->version);
        else
            strcpy(screen.installed_text[k], "Installed");
    }
}

static bool stopping(void *unused)
{
    (void)unused;
    return atomic_load(&screen.canceled);
}

static bool progress(void *unused, const char *detail, uint64_t n, uint64_t total)
{
    if (stopping(unused)) return false;
    static u64 last;
    u64 now = armGetSystemTick();
    if (armTicksToNs(now - last) < 100000000 && n != total) return true;
    static char previous[160];
    if (!total && !strcmp(previous, detail) && armTicksToNs(now - last) < 100000000) return true;
    last = now;
    snprintf(previous, sizeof(previous), "%s", detail);
    HubProgress snapshot = {.id = TASK_ID, .state = HUB_PROGRESS_ACTIVE};
    snprintf(snapshot.title, sizeof(snapshot.title), "%s", kind_label(screen.kind));
    snprintf(snapshot.detail, sizeof(snapshot.detail), "%s", detail);
    if (total)
        snapshot.permille = (unsigned)(1000 * (n <= total ? n : total) / total);
    hub_progress_publish(&snapshot);
    return true;
}

typedef struct {
    OnlineTransfer transfer;
    const ModsRelease *release;
} ModTransfer;

static void report(void *context, uint64_t present, uint64_t received)
{
    (void)received;
    ModTransfer *run = context;
    progress(NULL, "Downloading overlay", present, run->release->size);
}

static bool fetch(void *context, const ModsRelease *release, const char *path)
{
    ModTransfer *run = context;
    run->release = release;
    OnlineEntry entry = {.path = (char *)release->name, .key = (char *)release->key, .size = release->size};
    memcpy(entry.sha256, release->sha256, sizeof(entry.sha256));
    progress(NULL, "Downloading overlay", 0, entry.size);
    return online_fetch_verified_to(release->id, &entry, path, &run->transfer);
}

static void work(void *unused)
{
    (void)unused;
    bool online = screen.operation == BROWSE || screen.operation == INSTALL;
    bool socket = false, curl = false;
    ModTransfer run = {.transfer = {.stopping = stopping, .report = report}};
    run.transfer.context = &run;
    if (online) {
        socket = hub_socket_acquire();
        curl = socket && hub_https_ready();
    }
    if (online && !curl) {
        strcpy(screen.error, socket ? "Could not initialize HTTPS." : "Network unavailable.");
    } else if (screen.operation == BROWSE) {
        progress(NULL, "Fetching available releases", 0, 0);
        char *json = NULL;
        screen.ok = online_fetch_json(screen.descriptor->id, mods_catalog_path(screen.kind),
                                      MODS_MAX_MANIFEST, &json, &run.transfer) &&
                    mods_catalog_parse(&screen.catalog, &(ModsGame){
                        .id = screen.descriptor->id, .kind = screen.kind}, json);
        free(json);
        if (!screen.ok) strcpy(screen.error, "No valid overlay releases were found.");
    } else {
        const ModsCallbacks cb = {.update = progress, .fetch = fetch, .data = &run};
        screen.ok = mods_apply(HUB_ROOT, &screen.game, screen.kind,
                               screen.operation == INSTALL ? &screen.release : NULL,
                               screen.base.file_id ? &screen.base : NULL,
                               screen.operation == LOCAL ? screen.local : NULL, &cb, screen.error);
    }
    if (socket) hub_socket_release();
    snprintf(screen.network_error, sizeof(screen.network_error), "%s", run.transfer.error);
    atomic_store(&screen.done, true);
}

static void join(void)
{
    threadWaitForExit(&screen.thread);
    threadClose(&screen.thread);
    screen.live = false;
    hub_progress_remove(TASK_ID);
    if (screen.locked) {
        appletUnlockExit();
        screen.locked = false;
    }
}

static void on_applet(AppletHookType type, void *unused)
{
    (void)unused;
    if (screen.live && (type == AppletHookType_OnExitRequest ||
        (type == AppletHookType_OnFocusState && appletGetFocusState() == AppletFocusState_Background))) {
        atomic_store(&screen.canceled, true);
        if (type == AppletHookType_OnExitRequest) join();
    }
}

static void finish(void)
{
    appletUnhook(&screen.hook);
    if (screen.live) {
        atomic_store(&screen.canceled, true);
        join();
    }
    clear_catalog();
}

static bool start(HubApp *app, const HubGame *game, ModJob operation)
{
    if ((operation == BROWSE || operation == INSTALL) &&
        (!hub_online_access_enabled() || hub_online_worker_busy())) {
        hub_toast(app, "Enable Online access or wait for the other download.");
        return false;
    }
    screen.game = *game;
    screen.operation = operation;
    screen.ok = false;
    *screen.error = *screen.network_error = 0;
    atomic_store(&screen.canceled, false);
    atomic_store(&screen.done, false);
    screen.locked = R_SUCCEEDED(appletLockExit());
    Result rc = threadCreate(&screen.thread, work, NULL, NULL, 512 * 1024, 0x2c, -2);
    if (R_SUCCEEDED(rc)) {
        rc = threadStart(&screen.thread);
        if (R_FAILED(rc)) threadClose(&screen.thread);
    }
    if (R_SUCCEEDED(rc)) {
        screen.live = true;
        return true;
    }
    if (screen.locked) {
        appletUnlockExit();
        screen.locked = false;
    }
    hub_toast(app, "Could not start the overlay worker.");
    return false;
}

static HubItem one_item(const void *data, size_t i)
{
    (void)i;
    return *(const HubItem *)data;
}

static bool draw_progress(HubApp *app, bool close_after)
{
    if (!screen.live) return false;
    if (atomic_load(&screen.done)) {
        join();
        if (close_after)
            hub_pop(app);
        if (screen.ok) {
            if (screen.operation != BROWSE) {
                read_state(&screen.game);
                if (!hub_refresh_library(app))
                    hub_toast(app, "Overlay updated; library refresh failed.");
                else
                    hub_toast(app, screen.operation == REMOVE ? "Overlay removed." : "Overlay installed.");
            }
        } else if (strstr(screen.error, "Rollback is incomplete")) {
            hub_dialog(app, "SD recovery needed", screen.error);
        } else if (atomic_load(&screen.canceled)) {
            hub_toast(app, "Canceled.");
        } else {
            const char *detail = *screen.network_error ? screen.network_error : screen.error;
            hub_dialog(app, "Overlay operation failed", detail);
        }
        return close_after;
    }
    HubProgress snapshots[16];
    char percent[16] = "";
    const char *detail = "Preparing...";
    size_t n = hub_progress_snapshot(snapshots, sizeof(snapshots) / sizeof(*snapshots));
    for (size_t i = 0; i < n; ++i) {
        if (strcmp(snapshots[i].id, TASK_ID)) continue;
        detail = snapshots[i].detail;
        if (snapshots[i].permille)
            snprintf(percent, sizeof(percent), "%u%%", snapshots[i].permille / 10);
        break;
    }
    bool canceled = atomic_load(&screen.canceled);
    HubItem item = {canceled ? "Canceling..." : detail, percent};
    hub_heading(app, kind_label(screen.kind), "KEEP THIS SCREEN OPEN");
    hub_list(app, 1, 0, one_item, &item);
    hub_hints_extra(app, NULL, canceled ? "Wait" : "Cancel", NULL, NULL, false);
    return true;
}

static bool working_input(HubInput input)
{
    if (!screen.live) return false;
    if (input == HUB_BACK) atomic_store(&screen.canceled, true);
    return true;
}

static ModsKind category(size_t row)
{
    return row ? MODS_UNCENSORED : screen.descriptor->kind;
}

static size_t categories(void)
{
    if (!screen.descriptor) return 0;
    return (screen.descriptor->kind != MODS_UNCENSORED &&
            mods_game_has_uncensored(screen.descriptor)) ? 2 : 1;
}

static HubItem category_item(const void *unused, size_t i)
{
    (void)unused;
    ModsKind k = category(i);
    return (HubItem){kind_label(k), screen.installed_text[k]};
}

static void categories_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    if (*focus == (size_t)-1) {
        *focus = 0;
        read_state(game);
    }
    hub_heading(app, "Mods, translation and patches", game->name);
    if (categories())
        hub_list(app, categories(), *focus, category_item, NULL);
    else
        hub_empty(app, "No overlay catalog for this game", "Could not match the installed game files to a supported title.");
    hub_hints_extra(app, categories() ? "Open" : NULL, "Back", NULL, NULL, false);
}

static bool categories_input(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    if (!categories()) return false;
    if (input == HUB_CONFIRM && *focus < categories()) {
        screen.kind = category(*focus);
        clear_catalog();
        return hub_push(app, "mods.releases", game);
    }
    return hub_move_focus(focus, categories(), 1, 5, input);
}

static HubItem release_item(const void *unused, size_t i)
{
    (void)unused;
    if (!screen.catalog.count)
        return (HubItem){hub_online_access_enabled() ? "Browse online releases" : "Enable online access",
                         "Local install and removal do not require internet"};
    const ModsRelease *r = &screen.catalog.rows[i];
    if (screen.installed[screen.kind].installed &&
        (screen.kind == MODS_UNCENSORED || screen.installed[screen.kind].file_id == r->file_id))
        return (HubItem){r->version, "Installed"};
    return (HubItem){r->version, *r->group ? r->group : r->name};
}

static void releases_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    if (draw_progress(app, false)) return;
    size_t count = screen.catalog.count ? screen.catalog.count : 1;
    if (*focus >= count) *focus = 0;
    hub_heading(app, kind_label(screen.kind), screen.installed_text[screen.kind]);
    hub_list(app, count, *focus, release_item, NULL);
    hub_hints_extra(app, screen.catalog.count ? "Select" : "Open", "Back",
                    screen.kind != MODS_UNCENSORED && screen.state_ok[screen.kind] ? "SD archive" : NULL,
                    screen.installed[screen.kind].installed ? "Remove" : NULL, count > 5);
}

static bool get_local_path(HubApp *app)
{
    SwkbdConfig keyboard;
    if (R_FAILED(swkbdCreate(&keyboard, 0))) {
        hub_toast(app, "Could not open the keyboard.");
        return false;
    }
    swkbdConfigMakePresetDefault(&keyboard);
    swkbdConfigSetHeaderText(&keyboard, "Install an overlay from the SD card");
    swkbdConfigSetGuideText(&keyboard, "Enter a ZIP, RAR or 7z path on the SD card");
    swkbdConfigSetInitialText(&keyboard, "sdmc:/");
    swkbdConfigSetStringLenMax(&keyboard, 255);
    Result rc = swkbdShow(&keyboard, screen.local, sizeof(screen.local));
    swkbdClose(&keyboard);
    hub_wait_for_release(app);
    if (R_FAILED(rc)) return false;
    if (strncmp(screen.local, "sdmc:/", 6) || !mods_archive_name(screen.local)) {
        hub_toast(app, "Enter a ZIP, RAR or 7z path starting with sdmc:/.");
        return false;
    }
    return true;
}

static bool releases_input(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    if (working_input(input)) return true;
    if (input == HUB_BACK) {
        clear_catalog();
        return false;
    }
    if (input == HUB_ACTION && screen.kind != MODS_UNCENSORED && screen.state_ok[screen.kind]) {
        if (get_local_path(app)) {
            screen.operation = LOCAL;
            hub_push(app, "mods.confirm", game);
        }
        return true;
    }
    if (input == HUB_SECONDARY && screen.installed[screen.kind].installed) {
        screen.operation = REMOVE;
        return hub_push(app, "mods.confirm", game);
    }
    if (input == HUB_CONFIRM) {
        if (!hub_online_access_enabled()) return hub_push(app, "online.access", NULL);
        if (!screen.catalog.count) {
            start(app, game, BROWSE);
        } else if (*focus < screen.catalog.count && screen.state_ok[screen.kind]) {
            screen.base = (ModsRelease){0};
            screen.release = screen.catalog.rows[*focus];
            if (screen.release.patch) {
                for (size_t i = *focus + 1; i < screen.catalog.count; ++i)
                    if (!screen.catalog.rows[i].patch &&
                        screen.catalog.rows[i].file_id == screen.release.base_id &&
                        !strcmp(screen.catalog.rows[i].group, screen.release.group)) {
                        screen.base = screen.catalog.rows[i];
                        break;
                    }
                if (!screen.base.file_id) {
                    hub_dialog(app, "Required full release unavailable", "This patch cannot be installed by itself.");
                    return true;
                }
            }
            screen.operation = INSTALL;
            hub_push(app, "mods.confirm", game);
        }
        return true;
    }
    return hub_move_focus(focus, screen.catalog.count, 1, 5, input);
}

static void confirm_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)focus;
    if (draw_progress(app, true)) return;
    hub_heading(app, screen.operation == REMOVE ? "Remove overlay?" : "Install overlay?", game->name);
    const char *name = screen.operation == INSTALL ? screen.release.version : kind_label(screen.kind);
    const char *detail = screen.operation == REMOVE ?
        (screen.kind == MODS_UNCENSORED ? "Only the standalone CG patch will be removed."
                                      : "The files replaced by this overlay will be restored.") :
        screen.operation == LOCAL ? screen.local : screen.base.file_id ?
        "Installs the required full release if needed, followed by this patch." :
        "The installed game is backed up before publishing. Saves are not changed.";
    hub_empty(app, name, detail);
    hub_hints_extra(app, "Confirm", "Back", NULL, NULL, false);
}

static bool confirm_input(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    (void)focus;
    if (working_input(input)) return true;
    if (input != HUB_CONFIRM) return false;
    start(app, game, screen.operation);
    return true;
}

static bool available(const HubGame *game)
{
    return game && game->engine != HUB_SYSTEM3;
}

void hub_register_mods(HubApp *app)
{
    static const HubScreen screens[] = {
        {"mods.categories", categories_draw, categories_input},
        {"mods.releases", releases_draw, releases_input},
        {"mods.confirm", confirm_draw, confirm_input},
    };
    static const HubActionEntry action = {"Mods, translation and patches", "mods.categories", available};
    for (size_t i = 0; i < sizeof(screens) / sizeof(*screens); ++i)
        hub_register_screen(app, &screens[i]);
    hub_register_action(app, HUB_DETAIL_ACTION, &action);
    appletHook(&screen.hook, on_applet, NULL);
    atexit(finish);
}
