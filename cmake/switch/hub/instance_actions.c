#include "hub.h"
#include "instances.h"

#include <pthread.h>
#include <stdatomic.h>
#include <string.h>
#include <switch.h>

#define PROGRESS_ID "instances.operation"
#define COPY_SCREEN "instances.copy"
#define DELETE_SCREEN "instances.delete"
#define SAVES_SCREEN "instances.delete-saves"

typedef enum { COPY, DELETE_KEEP_SAVES, DELETE_WITH_SAVES } OperationKind;

static struct {
    pthread_t thread;
    atomic_bool cancelled;
    bool running, exit_locked;
    OperationKind kind;
    HubGame game;
    char name[HUB_NAME_SIZE];
    HubInstanceResult result;
} operation;

static const char *result_message(HubInstanceResult result, OperationKind kind)
{
    switch (result) {
    case HUB_INSTANCE_OK:
        if (kind == COPY)
            return "Copy created with its own save folder.";
        return kind == DELETE_KEEP_SAVES ? "Game deleted. Saves kept." : "Game and saves deleted.";
    case HUB_INSTANCE_INVALID_NAME: return "Choose a valid SD folder name.";
    case HUB_INSTANCE_NAME_TAKEN: return "An install or save folder already uses that name.";
    case HUB_INSTANCE_COPY_FAILED: return "Could not copy the game. Check the SD card and free space.";
    case HUB_INSTANCE_DELETE_FAILED: return "Could not delete the game. Saves were not touched.";
    case HUB_INSTANCE_SAVE_DELETE_FAILED: return "Game deleted, but its saves could not be removed.";
    }
    return "SD card operation failed.";
}

static bool copy_cancelled(void)
{
    return atomic_load(&operation.cancelled);
}

static void *perform_operation(void *unused)
{
    (void)unused;
    OperationKind kind = operation.kind;
    operation.result = kind == COPY
        ? hub_instance_copy(HUB_ROOT, &operation.game, operation.name, copy_cancelled)
        : hub_instance_delete(HUB_ROOT, &operation.game, kind == DELETE_WITH_SAVES);
    HubProgress progress = {
        .id = PROGRESS_ID,
        .permille = operation.result == HUB_INSTANCE_OK ? 1000 : 0,
        .state = operation.result == HUB_INSTANCE_OK ? HUB_PROGRESS_DONE : HUB_PROGRESS_FAILED,
    };
    snprintf(progress.title, sizeof(progress.title), "%s %s", kind == COPY ? "Copy" : "Delete",
             progress.state == HUB_PROGRESS_DONE ? "complete" : "failed");
    snprintf(progress.detail, sizeof(progress.detail), "%s", result_message(operation.result, kind));
    hub_progress_publish(&progress);
    return NULL;
}

static void stop_worker(void)
{
    if (!operation.running)
        return;
    atomic_store(&operation.cancelled, true);
    pthread_join(operation.thread, NULL);
    operation.running = false;
    hub_progress_remove(PROGRESS_ID);
    if (operation.exit_locked)
        appletUnlockExit();
}

static void applet_hook(AppletHookType hook, void *unused)
{
    (void)unused;
    if (hook == AppletHookType_OnExitRequest)
        stop_worker();
}

static bool start_worker(HubApp *app, const HubGame *game, OperationKind kind, const char *name)
{
    HubProgress progress = {.id = PROGRESS_ID, .state = HUB_PROGRESS_ACTIVE};
    snprintf(progress.title, sizeof(progress.title), "%s", kind == COPY ? "Copying game..." : "Deleting game...");
    snprintf(progress.detail, sizeof(progress.detail), "Please wait. Do not remove the SD card.");
    if (!hub_progress_publish(&progress)) {
        hub_toast(app, "Too many operations are running.");
        return false;
    }
    operation.kind = kind;
    operation.game = *game;
    if (name)
        snprintf(operation.name, sizeof(operation.name), "%s", name);
    atomic_store(&operation.cancelled, false);
    pthread_attr_t attr;
    bool initialized = !pthread_attr_init(&attr);
    operation.exit_locked = R_SUCCEEDED(appletLockExit());
    operation.running = initialized && !pthread_attr_setstacksize(&attr, 256 * 1024) &&
                        !pthread_create(&operation.thread, &attr, perform_operation, NULL);
    if (initialized)
        pthread_attr_destroy(&attr);
    if (!operation.running) {
        if (operation.exit_locked)
            appletUnlockExit();
        hub_progress_remove(PROGRESS_ID);
        hub_toast(app, "Could not start the SD card operation.");
    }
    return operation.running;
}

static HubProgress operation_progress(void)
{
    HubProgress snapshots[16];
    size_t count = hub_progress_snapshot(snapshots, sizeof(snapshots) / sizeof(*snapshots));
    for (size_t i = 0; i < count; ++i)
        if (!strcmp(snapshots[i].id, PROGRESS_ID))
            return snapshots[i];
    return (HubProgress){.state = HUB_PROGRESS_ACTIVE};
}

static void draw_operation(HubApp *app)
{
    HubProgress progress = operation_progress();
    hub_heading(app, progress.title, progress.detail);
    if (progress.state != HUB_PROGRESS_ACTIVE)
        hub_hints_extra(app, "Continue", "Continue", NULL, NULL, false);
}

static bool operation_input(HubApp *app, HubInput input)
{
    if ((input != HUB_CONFIRM && input != HUB_BACK) || operation_progress().state == HUB_PROGRESS_ACTIVE)
        return true;
    stop_worker();
    bool refreshed = hub_refresh_library(app);
    if (!refreshed)
        hub_toast(app, "Could not refresh the library.");
    bool deleting = operation.kind != COPY;
    bool deleted = deleting && operation.result != HUB_INSTANCE_DELETE_FAILED;
    hub_pop(app);
    if (deleting)
        hub_pop(app);
    if (deleted)
        hub_pop(app);
    return true;
}

static HubItem list_item(const void *data, size_t index)
{
    const HubItem *items = data;
    return items[index];
}

static void draw_choices(HubApp *app, size_t *focus, const HubGame *game, const char *title,
                         const HubItem *items, size_t count)
{
    if (operation.running) {
        draw_operation(app);
        return;
    }
    if (*focus >= count)
        *focus = 0;
    hub_heading(app, title, game->name);
    hub_list(app, count, *focus, list_item, items);
    hub_hints_extra(app, "Select", "Back", NULL, NULL, false);
}

static void draw_copy(HubApp *app, size_t *focus, const HubGame *game)
{
    static const HubItem items[] = {
        {"Create a numbered copy", "Starts with empty saves"},
        {"Choose a name for the copy", "Starts with empty saves"},
    };
    draw_choices(app, focus, game, "Copy game", items, 2);
}

static bool copy_input(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    if (operation.running)
        return operation_input(app, input);
    if (input != HUB_CONFIRM)
        return hub_move_focus(focus, 2, 1, 5, input);
    char suggestion[HUB_NAME_SIZE], name[HUB_NAME_SIZE];
    if (!hub_instance_suggest(HUB_ROOT, game, suggestion)) {
        hub_dialog(app, "Cannot make a copy", "Could not find a free name on the SD card.");
        return true;
    }
    if (*focus != 1) {
        start_worker(app, game, COPY, suggestion);
        return true;
    }
    SwkbdConfig keyboard;
    if (R_FAILED(swkbdCreate(&keyboard, 0))) {
        hub_dialog(app, "Cannot name copy", "Could not open the on-screen keyboard.");
        return true;
    }
    swkbdConfigMakePresetDefault(&keyboard);
    swkbdConfigSetHeaderText(&keyboard, "Name this copy");
    swkbdConfigSetSubText(&keyboard, "This name identifies its game and save folders.");
    swkbdConfigSetInitialText(&keyboard, suggestion);
    swkbdConfigSetStringLenMax(&keyboard, 80);
    swkbdConfigSetStringLenMin(&keyboard, 1);
    Result rc = swkbdShow(&keyboard, name, sizeof(name));
    swkbdClose(&keyboard);
    hub_wait_for_release(app);
    if (R_SUCCEEDED(rc))
        start_worker(app, game, COPY, name);
    else if (rc != MAKERESULT(Module_Libnx, LibnxError_LibAppletBadExit))
        hub_toast(app, "Could not open the software keyboard.");
    return true;
}

static void draw_delete(HubApp *app, size_t *focus, const HubGame *game)
{
    static const HubItem items[] = {{"Cancel", NULL}, {"Continue to save options", NULL}};
    draw_choices(app, focus, game, "Delete this installation?", items, 2);
}

static bool delete_input(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    if (input != HUB_CONFIRM)
        return hub_move_focus(focus, 2, 1, 5, input);
    if (*focus == 1)
        hub_push(app, SAVES_SCREEN, game);
    else
        hub_pop(app);
    return true;
}

static void draw_saves(HubApp *app, size_t *focus, const HubGame *game)
{
    static const HubItem items[] = {
        {"Delete game, keep saves", "Default"},
        {"Delete game and saves", "Cannot be undone"},
    };
    draw_choices(app, focus, game, "Also delete this installation's saves?", items, 2);
}

static bool saves_input(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    if (operation.running)
        return operation_input(app, input);
    if (input != HUB_CONFIRM)
        return hub_move_focus(focus, 2, 1, 5, input);
    start_worker(app, game, *focus == 1 ? DELETE_WITH_SAVES : DELETE_KEEP_SAVES, NULL);
    return true;
}

static bool installed_game(const HubGame *game)
{
    return game != NULL;
}

void hub_register_instances(HubApp *app)
{
    static AppletHookCookie hook;
    static const HubScreen copy = {COPY_SCREEN, draw_copy, copy_input};
    static const HubScreen delete = {DELETE_SCREEN, draw_delete, delete_input};
    static const HubScreen saves = {SAVES_SCREEN, draw_saves, saves_input};
    static const HubActionEntry copy_action = {"Copy game", COPY_SCREEN, installed_game};
    static const HubActionEntry delete_action = {"Delete installation", DELETE_SCREEN, installed_game};
    hub_register_screen(app, &copy);
    hub_register_screen(app, &delete);
    hub_register_screen(app, &saves);
    hub_register_action(app, HUB_DETAIL_ACTION, &copy_action);
    hub_register_action(app, HUB_DETAIL_ACTION, &delete_action);
    appletHook(&hook, applet_hook, NULL);
}
