#include "hub.h"
#include "save_store.h"

#include <pthread.h>
#include <string.h>
#include <switch.h>

#define SAVE_SCREEN "saves.manager"
#define SAVE_CONFIRM "saves.confirm"
#define SAVE_PROGRESS "saves.operation"

static const struct {
    const char *label, *folder, *confirmation;
} actions[HUB_SAVE_OP_COUNT] = {
    [HUB_SAVE_PREPARE] = {"Prepare SD folders", "Import and Sync", NULL},
    [HUB_SAVE_IMPORT] = {"Import from SD", "Import", "Merge imported saves? Existing files are backed up first."},
    [HUB_SAVE_EXPORT] = {"Export original saves", "SaveData", "Replace the SaveData export folder?"},
    [HUB_SAVE_EXPORT_PC] = {"Export PC-compatible saves", "PC_SaveData", "Replace the PC_SaveData export folder?"},
    [HUB_SAVE_BACKUP] = {"Create backup", "Backups", NULL},
    [HUB_SAVE_RESTORE] = {"Restore latest backup", "Backups", "Replace all active saves? Existing files are backed up first."},
    [HUB_SAVE_SYNC] = {"Sync new files both ways", "Sync", "Copy missing files both ways? Different files are left untouched."},
    [HUB_SAVE_PULL] = {"Pull from sync folder", "Sync", "Use SD copies for matching files? Active saves are backed up first."},
    [HUB_SAVE_PUSH] = {"Push to sync folder", "Sync", "Use active saves for matching files in the sync folder?"}
};

static struct {
    HubGame game;
    HubSaveOp op;
    pthread_t thread;
    AppletHookCookie hook;
    atomic_bool cancel, done;
    bool running, exit_locked;
} job;
static HubSaveOp pending;

static void *work(void *unused)
{
    (void)unused;
    HubProgress progress = {.id = SAVE_PROGRESS, .state = HUB_PROGRESS_ACTIVE};
    snprintf(progress.title, sizeof(progress.title), "%s", actions[job.op].label);
    snprintf(progress.detail, sizeof(progress.detail), "Working on the SD card...");
    hub_progress_publish(&progress);
    HubSaveResult result;
    bool ok = hub_save_run(HUB_ROOT, &job.game, job.op, &job.cancel, &result);
    progress.state = ok ? HUB_PROGRESS_DONE : HUB_PROGRESS_FAILED;
    progress.permille = ok ? 1000 : 0;
    if (!ok)
        snprintf(progress.detail, sizeof(progress.detail), "%s", result.error);
    else if (job.op == HUB_SAVE_PREPARE)
        snprintf(progress.detail, sizeof(progress.detail), "Import and Sync folders are ready.");
    else if (job.op == HUB_SAVE_SYNC)
        snprintf(progress.detail, sizeof(progress.detail), "%u copied, %u conflicts left unchanged.",
                 result.copied, result.conflicts);
    else if (job.op == HUB_SAVE_EXPORT_PC)
        snprintf(progress.detail, sizeof(progress.detail), "%u exported, %u converted to PC format.",
                 result.copied, result.converted);
    else
        snprintf(progress.detail, sizeof(progress.detail), "%u files copied.", result.copied);
    hub_progress_publish(&progress);
    atomic_store(&job.done, true);
    return NULL;
}

static void join_work(void)
{
    pthread_join(job.thread, NULL);
    job.running = false;
    if (job.exit_locked) {
        appletUnlockExit();
        job.exit_locked = false;
    }
}

static void save_applet_hook(AppletHookType event, void *unused)
{
    (void)unused;
    if (event == AppletHookType_OnExitRequest && job.running) {
        atomic_store(&job.cancel, true);
        join_work();
        hub_progress_remove(SAVE_PROGRESS);
    }
}

static void start_work(HubApp *app, const HubGame *game, HubSaveOp op)
{
    pthread_attr_t attr;
    int error = pthread_attr_init(&attr);
    if (!error) {
        job.game = *game;
        job.op = op;
        atomic_store(&job.cancel, false);
        atomic_store(&job.done, false);
        error = pthread_attr_setstacksize(&attr, 512 * 1024);
        if (!error) {
            job.exit_locked = R_SUCCEEDED(appletLockExit());
            error = pthread_create(&job.thread, &attr, work, NULL);
            if (!error) {
                job.running = true;
            } else if (job.exit_locked) {
                appletUnlockExit();
                job.exit_locked = false;
            }
        }
        pthread_attr_destroy(&attr);
    }
    if (error)
        hub_dialog(app, "Could not start save operation", strerror(error));
    hub_wait_for_release(app);
}

static bool read_progress(HubProgress *progress)
{
    HubProgress entries[16];
    size_t n = hub_progress_snapshot(entries, sizeof(entries) / sizeof(*entries));
    for (size_t i = 0; i < n; ++i)
        if (!strcmp(entries[i].id, SAVE_PROGRESS)) {
            *progress = entries[i];
            return true;
        }
    return false;
}

static HubItem action_item(const void *data, size_t index)
{
    (void)data;
    return (HubItem){actions[index].label, actions[index].folder};
}

static HubItem game_item(const void *data, size_t index)
{
    const HubLibrary *library = data;
    const HubGame *game = &library->games[index];
    return (HubItem){game->name, hub_engines[game->engine].label};
}

static void draw(HubApp *app, size_t *focus, const HubGame *game)
{
    if (*focus == (size_t)-1)
        *focus = 0;
    if (!game) {
        const HubLibrary *library = hub_library(app);
        hub_heading(app, "Save manager", "Choose an installed game");
        if (library->count)
            hub_list(app, library->count, *focus, game_item, library);
        else
            hub_empty(app, "No installed games", "Add a game to its engine's games folder on the SD card.");
        hub_hints_extra(app, library->count ? "Open" : NULL, "Back", NULL, NULL, library->count > 5);
        return;
    }
    if (job.running) {
        HubProgress progress;
        bool have_progress = read_progress(&progress);
        if (atomic_load(&job.done)) {
            join_work();
            have_progress = read_progress(&progress);
            hub_progress_remove(SAVE_PROGRESS);
            hub_dialog(app, have_progress && progress.state == HUB_PROGRESS_FAILED
                          ? "Save operation stopped" : actions[job.op].label,
                       have_progress ? progress.detail : "Save operation ended.");
        } else {
            hub_heading(app, actions[job.op].label, atomic_load(&job.cancel)
                        ? "Cancelling..." : have_progress ? progress.detail : "Starting...");
            hub_hints_extra(app, NULL, "Cancel", NULL, NULL, false);
            return;
        }
    }
    hub_heading(app, "Save manager", game->name);
    hub_list(app, HUB_SAVE_OP_COUNT, *focus, action_item, NULL);
    hub_hints(app, "Select", "Back", "SD folder");
}

static bool input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    if (job.running) {
        if (event == HUB_BACK)
            atomic_store(&job.cancel, true);
        return true;
    }
    if (!game) {
        const HubLibrary *library = hub_library(app);
        if (event == HUB_CONFIRM && *focus < library->count) {
            hub_push(app, SAVE_SCREEN, &library->games[*focus]);
            return true;
        }
        return hub_move_focus(focus, library->count, 1, 5, event);
    }
    if (event == HUB_ACTION) {
        char path[1024];
        snprintf(path, sizeof(path), HUB_ROOT "/save-manager/%s/%s",
                 hub_engines[game->engine].id, game->name);
        hub_dialog(app, "Save manager folder", path);
        return true;
    }
    if (event == HUB_CONFIRM && *focus < HUB_SAVE_OP_COUNT) {
        HubSaveOp op = (HubSaveOp)*focus;
        if (actions[op].confirmation) {
            pending = op;
            hub_push(app, SAVE_CONFIRM, game);
            hub_wait_for_release(app);
        } else {
            start_work(app, game, op);
        }
        return true;
    }
    return hub_move_focus(focus, HUB_SAVE_OP_COUNT, 1, 5, event);
}

static void confirm_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)focus;
    (void)game;
    hub_heading(app, actions[pending].label, actions[pending].confirmation);
    hub_hints_extra(app, "Continue", "Cancel", NULL, NULL, false);
}

static bool confirm_input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    (void)focus;
    if (event != HUB_CONFIRM || !game)
        return false;
    HubGame selected = *game;
    hub_pop(app);
    start_work(app, &selected, pending);
    return true;
}

void hub_register_save_manager(HubApp *app)
{
    static const HubScreen screen = {SAVE_SCREEN, draw, input};
    static const HubScreen confirm = {SAVE_CONFIRM, confirm_draw, confirm_input};
    static const HubActionEntry entry = {"Save manager", SAVE_SCREEN, NULL};
    hub_register_screen(app, &screen);
    hub_register_screen(app, &confirm);
    hub_register_action(app, HUB_DETAIL_ACTION, &entry);
    hub_register_action(app, HUB_SETTING_ENTRY, &entry);
    appletHook(&job.hook, save_applet_hook, NULL);
}
