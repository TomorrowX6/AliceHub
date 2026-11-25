#include "hub.h"
#include "install_transaction.h"
#include "online_install.h"

#include <curl/curl.h>
#include <errno.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <switch.h>
#include <unistd.h>

#define CONSENT HUB_ROOT "/.online-consent-v1"
#define TASK_ID "online.install"
#define COUNT(array) (sizeof(array) / sizeof(*(array)))

typedef struct { const char *id, *title; } OnlineGame;
#include "online_catalog.inc"

typedef enum { INSPECT, INSTALL, CLEAR } JobKind;
typedef enum { IDLE, ACTIVE, PAUSED, FAILED, COMPLETED, CANCELED } TaskState;
enum { RUN, PAUSE, CANCEL };

static struct {
    Thread thread;
    atomic_bool done;
    atomic_int stop;
    bool live, okay, rollback_failed;
    JobKind kind;
    const OnlineGame *game;
    OnlinePlan plan;
    char error[160];
} job;

static struct {
    const OnlineGame *game;
    OnlinePlan plan;
    bool ready, okay;
    char error[160];
} preview;

static struct {
    const OnlineGame *game;
    OnlinePlan plan;
    TaskState state;
    bool clearing;
} task;

static bool consent, recovery_ok;
static AppletHookCookie applet_hook;
static char query[65];

bool hub_online_access_enabled(void)
{
    return consent;
}

bool hub_online_worker_busy(void)
{
    return job.live && !atomic_load(&job.done);
}

typedef struct {
    OnlineTransfer transfer;
    char step[128];
    uint64_t base, total, start, last;
} InstallRun;

static bool stopping(void *unused)
{
    (void)unused;
    return atomic_load(&job.stop) != RUN;
}

static void publish(const char *detail, HubProgressState state, unsigned permille)
{
    HubProgress p = {.id = TASK_ID, .permille = permille, .state = state};
    snprintf(p.title, sizeof(p.title), "%s", job.game->title);
    snprintf(p.detail, sizeof(p.detail), "%s", detail);
    hub_progress_publish(&p);
}

static unsigned position(uint64_t done, uint64_t total)
{
    if (!total)
        return 0;
    return (unsigned)((done > total ? total : done) * 1000 / total);
}

static void stage(void *arg, const char *name, uint64_t done, uint64_t total)
{
    InstallRun *run = arg;
    run->base = done;
    run->total = total;
    run->start = run->last = armGetSystemTick();
    snprintf(run->step, sizeof(run->step), "%s", name);
    publish(run->step, HUB_PROGRESS_ACTIVE, position(done, total));
}

static void report(void *arg, uint64_t present, uint64_t received)
{
    InstallRun *run = arg;
    uint64_t now = armGetSystemTick();
    if (armTicksToNs(now - run->last) < 200000000)
        return;
    run->last = now;
    double elapsed = armTicksToNs(now - run->start) / 1e9;
    char detail[160];
    snprintf(detail, sizeof(detail), "%.105s · %.1f MiB/s", run->step, received / elapsed / 1048576);
    publish(detail, HUB_PROGRESS_ACTIVE, position(run->base + present, run->total));
}

static void work(void *unused)
{
    (void)unused;
    InstallRun run = {.transfer = {.stopping = stopping, .report = report}};
    run.transfer.context = &run;
    if (job.kind != INSPECT)
        publish(job.kind == CLEAR ? "Removing cached downloads" : "Fetching manifest",
                HUB_PROGRESS_ACTIVE, 0);
    if (job.kind == CLEAR) {
        job.okay = online_clear_cache(job.game->id);
    } else if (R_FAILED(socketInitializeDefault())) {
        snprintf(run.transfer.error, sizeof(run.transfer.error), "Network unavailable");
    } else {
        if (curl_global_init(CURL_GLOBAL_DEFAULT) != CURLE_OK)
            snprintf(run.transfer.error, sizeof(run.transfer.error), "Could not initialize HTTPS");
        else {
            if (job.kind == INSPECT)
                job.okay = online_inspect(job.game->id, &job.plan, &run.transfer);
            else
                job.okay = online_install(job.game->id, &job.plan, &run.transfer, stage);
            curl_global_cleanup();
        }
        socketExit();
    }
    if (!job.okay && job.kind == INSTALL && !run.transfer.rollback_failed &&
        atomic_load(&job.stop) == CANCEL) {
        job.okay = online_clear_cache(job.game->id);
        job.kind = CLEAR;
    }
    job.rollback_failed = run.transfer.rollback_failed;
    snprintf(job.error, sizeof(job.error), "%s", *run.transfer.error ? run.transfer.error :
             job.kind == CLEAR ? "Could not remove the download cache" : "Operation could not finish");
    atomic_store(&job.done, true);
}

static void collect(HubApp *app)
{
    if (!job.live || !atomic_load(&job.done))
        return;
    threadWaitForExit(&job.thread);
    threadClose(&job.thread);
    job.live = false;
    if (job.kind == INSPECT) {
        preview.ready = true;
        preview.okay = job.okay;
        preview.plan = job.plan;
        snprintf(preview.error, sizeof(preview.error), "%s", atomic_load(&job.stop) == PAUSE ?
                 "Manifest request paused; press A to retry" : job.error);
        return;
    }
    task.clearing = false;
    unsigned permille = 0;
    HubProgress items[16];
    size_t count = hub_progress_snapshot(items, COUNT(items));
    for (size_t i = 0; i < count; ++i)
        if (!strcmp(items[i].id, TASK_ID))
            permille = items[i].permille;
    if (job.rollback_failed) {
        recovery_ok = false;
        task.state = FAILED;
        publish(job.error, HUB_PROGRESS_FAILED, permille);
    } else if (job.kind == CLEAR && job.okay) {
        task.state = CANCELED;
        publish("Canceled; partial files removed", HUB_PROGRESS_DONE, 0);
    } else if (job.kind == INSTALL && job.okay) {
        task.state = COMPLETED;
        publish("Verified and installed", HUB_PROGRESS_DONE, 1000);
        if (preview.game == job.game) {
            preview.plan.installed = true;
            preview.plan.conflict = false;
        }
        if (!hub_refresh_library(app))
            hub_toast(app, "Installed, but the SD library could not be refreshed");
    } else if (atomic_load(&job.stop) == PAUSE) {
        task.state = PAUSED;
        publish("Paused; partial files kept", HUB_PROGRESS_DONE, permille);
    } else {
        task.state = FAILED;
        publish(job.kind == CLEAR ? "Could not remove the download cache" : job.error,
                HUB_PROGRESS_FAILED, permille);
    }
}

static bool start(HubApp *app, JobKind kind, const OnlineGame *game, const OnlinePlan *plan)
{
    collect(app);
    if (job.live) {
        hub_toast(app, "Another online operation is running");
        return false;
    }
    if (kind != CLEAR && (!consent || !recovery_ok)) {
        hub_toast(app, "Online access or SD install recovery is unavailable");
        return false;
    }
    atomic_store(&job.stop, RUN);
    atomic_store(&job.done, false);
    job.okay = false;
    job.kind = kind;
    job.game = game;
    job.plan = plan ? *plan : (OnlinePlan){0};
    Result rc = threadCreate(&job.thread, work, NULL, NULL, 512 * 1024, 0x2c, -2);
    if (R_SUCCEEDED(rc)) {
        rc = threadStart(&job.thread);
        if (R_FAILED(rc))
            threadClose(&job.thread);
    }
    if (R_FAILED(rc)) {
        hub_toast(app, "Could not start the online worker");
        return false;
    }
    job.live = true;
    if (kind != INSPECT) {
        task.game = game;
        if (plan)
            task.plan = *plan;
        task.state = ACTIVE;
        task.clearing = kind == CLEAR;
    }
    return true;
}

static void pause_work(void)
{
    int running = RUN;
    atomic_compare_exchange_strong(&job.stop, &running, PAUSE);
}

static void finish(void)
{
    appletUnhook(&applet_hook);
    if (job.live) {
        pause_work();
        threadWaitForExit(&job.thread);
        threadClose(&job.thread);
    }
}

static void applet_event(AppletHookType type, void *unused)
{
    (void)unused;
    if (type == AppletHookType_OnExitRequest ||
        (type == AppletHookType_OnFocusState && appletGetFocusState() == AppletFocusState_Background))
        pause_work();
}

static bool save_consent(bool enabled)
{
    if (!enabled)
        return !unlink(CONSENT) || errno == ENOENT;
    FILE *file = fopen(CONSENT, "wb");
    if (!file)
        return false;
    bool okay = fputc('1', file) != EOF && !fflush(file) && !fsync(fileno(file));
    okay = !fclose(file) && okay;
    if (!okay)
        unlink(CONSENT);
    return okay;
}

static bool load_consent(void)
{
    FILE *file = fopen(CONSENT, "rb");
    if (!file)
        return false;
    bool okay = fgetc(file) == '1' && fgetc(file) == EOF && !ferror(file);
    fclose(file);
    return okay;
}

static HubItem term_item(const void *unused, size_t i)
{
    (void)unused;
    return (HubItem){online_terms[i], NULL};
}

static void terms_draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)unused;
    if (*focus >= COUNT(online_terms))
        *focus = 0;
    hub_heading(app, "Online Install terms", "Read all terms before enabling the catalog.");
    hub_list(app, COUNT(online_terms), *focus, term_item, NULL);
    hub_hints_extra(app, *focus == COUNT(online_terms) - 1 ? "Accept" : NULL,
                    "Leave disabled", NULL, NULL, true);
}

static bool terms_input(HubApp *app, size_t *focus, const HubGame *unused, HubInput input)
{
    (void)unused;
    if (input == HUB_CONFIRM && *focus == COUNT(online_terms) - 1) {
        if (save_consent(true)) {
            consent = true;
            hub_pop(app);
        } else
            hub_toast(app, "Could not save Online Install consent");
        return true;
    }
    return hub_move_focus(focus, COUNT(online_terms), 1, 5, input);
}

static void access_draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)focus;
    (void)unused;
    collect(app);
    hub_heading(app, "Online access", consent ? "Enabled" : "Disabled");
    hub_empty(app, consent ? "Stop Online Install" : "Online Install is off",
              consent ? "Disabling pauses active work. Local games remain available." :
                        "Online access is optional. Accept the terms to use the catalog.");
    hub_hints(app, consent ? "Disable" : "Read terms", "Back", NULL);
}

static bool access_input(HubApp *app, size_t *focus, const HubGame *unused, HubInput input)
{
    (void)focus;
    (void)unused;
    if (input != HUB_CONFIRM)
        return false;
    if (!consent)
        hub_push(app, "online.terms", NULL);
    else if (save_consent(false)) {
        pause_work();
        consent = false;
    } else
        hub_toast(app, "Could not disable Online Install on SD");
    return true;
}

static bool contains(const char *s, const char *token)
{
    size_t n = strlen(token);
    for (; *s; ++s)
        if (!strncasecmp(s, token, n))
            return true;
    return !n;
}

typedef struct {
    const OnlineGame *games[COUNT(online_games)];
    size_t count;
} CatalogView;

static CatalogView view_catalog(void)
{
    CatalogView view = {0};
    for (size_t i = 0; i < COUNT(online_games); ++i)
        if (contains(online_games[i].title, query) || contains(online_games[i].id, query))
            view.games[view.count++] = &online_games[i];
    return view;
}

static HubItem catalog_item(const void *data, size_t i)
{
    const OnlineGame *game = ((const CatalogView *)data)->games[i];
    return (HubItem){game->title, game->id};
}

static void catalog_draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)unused;
    collect(app);
    hub_heading(app, "Online catalog", *query && consent ? query : "ALICEHUB CDN");
    if (!recovery_ok) {
        hub_empty(app, "SD recovery needed", "Online installs are unavailable. Local games can still be used.");
        hub_hints(app, NULL, "Back", "Tasks");
    } else if (!consent) {
        hub_empty(app, "Online Install is off", "Read and accept the terms before contacting the CDN.");
        hub_hints(app, "Read terms", "Back", "Tasks");
    } else {
        CatalogView view = view_catalog();
        if (*focus >= view.count)
            *focus = 0;
        if (view.count)
            hub_list(app, view.count, *focus, catalog_item, &view);
        else
            hub_empty(app, "No matching games", "Press Y to change the search.");
        hub_hints_extra(app, view.count ? "Details" : NULL, "Back", "Tasks", "Search", view.count > 5);
    }
}

static void search(HubApp *app, size_t *focus)
{
    SwkbdConfig keyboard;
    if (R_FAILED(swkbdCreate(&keyboard, 0))) {
        hub_toast(app, "Could not open the software keyboard");
        return;
    }
    swkbdConfigMakePresetDefault(&keyboard);
    swkbdConfigSetHeaderText(&keyboard, "Search the online catalog");
    swkbdConfigSetInitialText(&keyboard, query);
    swkbdConfigSetStringLenMax(&keyboard, 64);
    char input[sizeof(query)] = {0};
    if (R_SUCCEEDED(swkbdShow(&keyboard, input, sizeof(input)))) {
        strcpy(query, input);
        *focus = 0;
    }
    swkbdClose(&keyboard);
    hub_wait_for_release(app);
}

static bool catalog_input(HubApp *app, size_t *focus, const HubGame *unused, HubInput input)
{
    (void)unused;
    if (input == HUB_ACTION) {
        hub_push(app, "online.tasks", NULL);
        return true;
    }
    if (!recovery_ok)
        return false;
    if (!consent)
        return input == HUB_CONFIRM && hub_push(app, "online.terms", NULL);
    if (input == HUB_SECONDARY) {
        search(app, focus);
        return true;
    }
    CatalogView view = view_catalog();
    if (input == HUB_CONFIRM && *focus < view.count) {
        const OnlineGame *game = view.games[*focus];
        if (start(app, INSPECT, game, NULL)) {
            memset(&preview, 0, sizeof(preview));
            preview.game = game;
            hub_push(app, "online.detail", NULL);
        }
        return true;
    }
    return hub_move_focus(focus, view.count, 1, 5, input);
}

typedef struct { HubItem rows[5]; size_t count; } Detail;

static HubItem detail_item(const void *data, size_t i)
{
    return ((const Detail *)data)->rows[i];
}

static void size_text(char text[32], uint64_t bytes)
{
    snprintf(text, 32, "%.1f MiB", bytes / 1048576.0);
}

static void detail_draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)focus;
    (void)unused;
    collect(app);
    const OnlinePlan *plan = &preview.plan;
    hub_heading(app, preview.game->title, preview.ready && preview.okay ? plan->game.name : "Online manifest");
    if (!preview.ready) {
        hub_empty(app, "Fetching manifest", "You can go back without waiting.");
        hub_hints(app, NULL, "Back", "Tasks");
    } else if (!preview.okay) {
        hub_empty(app, "Could not load install details", preview.error);
        hub_hints(app, "Retry", "Back", "Tasks");
    } else {
        char download[32], stage_space[32], sd[32];
        size_text(download, plan->download_bytes);
        size_text(stage_space, plan->stage_bytes);
        size_text(sd, plan->available_bytes);
        Detail detail = {.rows = {
            {"Engine", hub_engines[plan->game.engine].label},
            {"Status", plan->installed ? "Installed" : plan->conflict ? "Folder occupied" : "Not installed"},
            {"Remaining download (upper estimate)", download},
            {plan->archives ? "Staged files (plus archive expansion)" : "Staged files", stage_space},
            {"Free SD space (old install stays until commit)", sd}
        }, .count = 5};
        hub_list(app, detail.count, 0, detail_item, &detail);
        hub_hints(app, plan->conflict ? NULL : plan->installed ? "Review update" : "Review install",
                  "Back", "Tasks");
    }
}

static bool detail_input(HubApp *app, size_t *focus, const HubGame *unused, HubInput input)
{
    (void)focus;
    (void)unused;
    collect(app);
    if (input == HUB_ACTION) {
        hub_push(app, "online.tasks", NULL);
        return true;
    }
    if (input != HUB_CONFIRM || !preview.ready)
        return false;
    if (!preview.okay) {
        if (start(app, INSPECT, preview.game, NULL))
            preview.ready = false;
    } else if (!preview.plan.conflict)
        hub_push(app, "online.confirm", NULL);
    return true;
}

static void confirm_draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)focus;
    (void)unused;
    collect(app);
    bool update = preview.plan.installed;
    hub_heading(app, update ? "Update game" : "Install game", preview.game->title);
    hub_empty(app, update ? "Replace installed game files?" : "Download and install?",
              update ? "This replaces mods and other edits in the game folder. Separate saves are kept." :
                       "The download is verified before adding the game to your library.");
    hub_hints(app, update ? "Update" : "Install", "Back", NULL);
}

static bool confirm_input(HubApp *app, size_t *focus, const HubGame *unused, HubInput input)
{
    (void)focus;
    (void)unused;
    if (input != HUB_CONFIRM)
        return false;
    if (start(app, INSTALL, preview.game, &preview.plan)) {
        hub_pop(app);
        hub_push(app, "online.tasks", NULL);
    }
    return true;
}

typedef struct { HubProgress items[16]; size_t count; char percent[16][24]; } Drawer;

static Drawer drawer(void)
{
    Drawer view = {0};
    view.count = hub_progress_snapshot(view.items, COUNT(view.items));
    for (size_t i = 0; i < view.count; ++i) {
        const HubProgress *p = &view.items[i];
        const char *status = p->state == HUB_PROGRESS_ACTIVE ? "Active" :
                             p->state == HUB_PROGRESS_FAILED ? "Failed" : "Done";
        if (!strcmp(p->id, TASK_ID))
            status = task.state == PAUSED ? "Paused" : task.state == CANCELED ? "Canceled" : status;
        snprintf(view.percent[i], sizeof(view.percent[i]), "%u%% %s", p->permille / 10, status);
    }
    return view;
}

static HubItem task_item(const void *data, size_t i)
{
    const Drawer *view = data;
    return (HubItem){view->items[i].title, view->percent[i]};
}

static void tasks_draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)unused;
    collect(app);
    Drawer view = drawer();
    if (*focus >= view.count)
        *focus = 0;
    bool own = view.count && !strcmp(view.items[*focus].id, TASK_ID);
    hub_heading(app, "Download tasks", view.count ? view.items[*focus].detail : "Switch Hub");
    if (view.count)
        hub_list(app, view.count, *focus, task_item, &view);
    else
        hub_empty(app, "No download tasks", "Install a title from the online catalog to see its progress here.");
    const char *confirm = NULL, *action = NULL;
    if (own) {
        if (task.state == ACTIVE && !task.clearing) {
            confirm = "Pause";
            action = "Cancel";
        } else if (task.state == PAUSED || task.state == FAILED) {
            confirm = consent && recovery_ok ? "Retry" : NULL;
            action = "Clear cache";
        } else if (task.state != ACTIVE)
            action = "Dismiss";
    }
    hub_hints_extra(app, confirm, "Back", action, NULL, view.count > 5);
}

static bool tasks_input(HubApp *app, size_t *focus, const HubGame *unused, HubInput input)
{
    (void)unused;
    collect(app);
    Drawer view = drawer();
    bool own = *focus < view.count && !strcmp(view.items[*focus].id, TASK_ID);
    if (own && input == HUB_CONFIRM && !task.clearing) {
        if (task.state == ACTIVE)
            pause_work();
        else if (task.state == PAUSED || task.state == FAILED)
            start(app, INSTALL, task.game, &task.plan);
        return true;
    }
    if (own && input == HUB_ACTION && !(task.state == ACTIVE && task.clearing)) {
        if (task.state == ACTIVE)
            atomic_store(&job.stop, CANCEL);
        else if (task.state == PAUSED || task.state == FAILED)
            start(app, CLEAR, task.game, NULL);
        else {
            hub_progress_remove(TASK_ID);
            task.state = IDLE;
        }
        return true;
    }
    return hub_move_focus(focus, view.count, 1, 5, input);
}

void hub_register_online(HubApp *app)
{
    static const HubScreen screens[] = {
        {"online.catalog", catalog_draw, catalog_input},
        {"online.access", access_draw, access_input},
        {"online.terms", terms_draw, terms_input},
        {"online.detail", detail_draw, detail_input},
        {"online.confirm", confirm_draw, confirm_input},
        {"online.tasks", tasks_draw, tasks_input}
    };
    static const HubActionEntry entries[] = {
        {"Online catalog", "online.catalog", NULL},
        {"Download tasks", "online.tasks", NULL},
        {"Online access", "online.access", NULL}
    };
    for (size_t i = 0; i < COUNT(screens); ++i)
        hub_register_screen(app, &screens[i]);
    for (size_t i = 0; i < COUNT(entries); ++i)
        hub_register_action(app, HUB_SETTING_ENTRY, &entries[i]);
    consent = load_consent();
    recovery_ok = hub_install_recover(HUB_ROOT);
    if (!recovery_ok)
        hub_toast(app, "Some interrupted SD installs could not be recovered");
    appletHook(&applet_hook, applet_event, NULL);
    atexit(finish);
}
