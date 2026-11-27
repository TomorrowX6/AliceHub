#include "local_import.h"

#include <dirent.h>
#include <errno.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <switch.h>
#include <sys/stat.h>

#define ROOT "sdmc:/"
#define TASK_ID "local-import.copy"

typedef struct {
    char *name;
    bool folder;
} FileItem;

static struct {
    char dir[HUB_PATH_SIZE], source[HUB_PATH_SIZE], error[160];
    FileItem *files;
    size_t count;
    Thread thread;
    bool running, locked, ok;
    atomic_bool cancel, done;
    AppletHookCookie hook;
    HubGame game;
} screen;

static void clear_files(void)
{
    for (size_t i = 0; i < screen.count; ++i)
        free(screen.files[i].name);
    free(screen.files);
    screen.files = NULL;
    screen.count = 0;
}

static int file_order(const void *a, const void *b)
{
    const FileItem *x = a, *y = b;
    return x->folder == y->folder ? strcasecmp(x->name, y->name) : x->folder ? -1 : 1;
}

static bool read_files(void)
{
    clear_files();
    DIR *d = opendir(screen.dir);
    if (!d) return false;
    size_t capacity = 0;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *e = readdir(d);
        if (!e) {
            ok = !errno;
            break;
        }
        if (!import_leaf(e->d_name) || import_skip(e->d_name, true)) continue;
        bool folder = e->d_type == DT_DIR;
        bool regular = e->d_type == DT_REG;
        if (e->d_type == DT_UNKNOWN) {
            char path[HUB_PATH_SIZE];
            struct stat st;
            if (!import_path(path, screen.dir, e->d_name) || lstat(path, &st)) continue;
            folder = S_ISDIR(st.st_mode);
            regular = S_ISREG(st.st_mode);
        }
        if (!folder && !(regular && (import_suffix(e->d_name, ".zip") ||
                         import_suffix(e->d_name, ".iso") || import_suffix(e->d_name, ".mdf"))))
            continue;
        if (screen.count == capacity) {
            size_t next = capacity ? capacity * 2 : 32;
            FileItem *files = realloc(screen.files, next * sizeof(*files));
            if (!files) {
                ok = false;
                break;
            }
            screen.files = files;
            capacity = next;
        }
        char *name = strdup(e->d_name);
        if (!name) {
            ok = false;
            break;
        }
        screen.files[screen.count++] = (FileItem){name, folder};
    }
    closedir(d);
    if (!ok) clear_files();
    if (screen.count > 1) qsort(screen.files, screen.count, sizeof(*screen.files), file_order);
    return ok;
}

static bool update(void *unused, const char *detail, uint64_t current, uint64_t total)
{
    (void)unused;
    if (atomic_load(&screen.cancel)) return false;
    const char *slash = strrchr(detail, '/');
    HubProgress progress = {.id = TASK_ID, .title = "Importing local game", .state = HUB_PROGRESS_ACTIVE};
    if (total)
        progress.permille = current >= total ? 1000 : (unsigned)(1000.0 * current / total);
    snprintf(progress.detail, sizeof(progress.detail), "%s", slash ? slash + 1 : detail);
    hub_progress_publish(&progress);
    return true;
}

static void worker(void *unused)
{
    (void)unused;
    const ImportCallbacks cb = {.update = update};
    screen.ok = hub_local_import(HUB_ROOT, screen.source, &cb, &screen.game, screen.error);
    HubProgress progress = {.id = TASK_ID, .title = "Importing local game",
        .permille = screen.ok ? 1000 : 0, .state = screen.ok ? HUB_PROGRESS_DONE : HUB_PROGRESS_FAILED};
    bool rollback_error = !screen.ok && strstr(screen.error, "Could not clean up the install") != NULL;
    snprintf(progress.detail, sizeof(progress.detail), "%.159s",
             screen.ok ? screen.game.name : rollback_error ? screen.error :
             atomic_load(&screen.cancel) ? "Import canceled" : screen.error);
    hub_progress_publish(&progress);
    atomic_store(&screen.done, true);
}

static void join_worker(void)
{
    threadWaitForExit(&screen.thread);
    threadClose(&screen.thread);
    screen.running = false;
    hub_progress_remove(TASK_ID);
    if (screen.locked) {
        appletUnlockExit();
        screen.locked = false;
    }
}

static void on_applet(AppletHookType type, void *unused)
{
    (void)unused;
    if (type == AppletHookType_OnExitRequest && screen.running) {
        atomic_store(&screen.cancel, true);
        join_worker();
    }
}

static void start(HubApp *app, const char *path)
{
    strcpy(screen.source, path);
    atomic_store(&screen.cancel, false);
    atomic_store(&screen.done, false);
    screen.locked = R_SUCCEEDED(appletLockExit());
    Result rc = threadCreate(&screen.thread, worker, NULL, NULL, 0x40000, 0x2c, -2);
    if (R_SUCCEEDED(rc)) {
        rc = threadStart(&screen.thread);
        if (R_FAILED(rc)) threadClose(&screen.thread);
    }
    if (R_SUCCEEDED(rc)) {
        screen.running = true;
        return;
    }
    if (screen.locked) {
        appletUnlockExit();
        screen.locked = false;
    }
    hub_dialog(app, "Could not start import", "The background worker could not be started.");
}

static HubItem file_item(const void *unused, size_t index)
{
    (void)unused;
    if (!screen.count)
        return (HubItem){"No folders or supported archives here", NULL};
    const FileItem *file = &screen.files[index];
    return (HubItem){file->name, file->folder ? "Folder" : "Import archive"};
}

static HubItem progress_item(const void *data, size_t index)
{
    (void)index;
    return *(const HubItem *)data;
}

static void draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)unused;
    if (*focus == (size_t)-1) {
        strcpy(screen.dir, ROOT);
        if (!read_files()) hub_dialog(app, "Could not read the SD card", screen.dir);
        *focus = 0;
    }
    if (screen.running && atomic_load(&screen.done)) {
        join_worker();
        if (screen.ok) {
            if (hub_refresh_library(app))
                hub_toast(app, "Game imported");
            else
                hub_dialog(app, "Game imported", "Could not refresh the library. Reopen AliceHub to see it.");
        } else if (strstr(screen.error, "Could not clean up the install")) {
            hub_dialog(app, "SD recovery needed", screen.error);
        } else if (atomic_load(&screen.cancel)) {
            hub_toast(app, "Import canceled");
        } else {
            hub_dialog(app, "Import failed", screen.error);
        }
    }
    if (!screen.running) {
        hub_heading(app, "Import local game", screen.dir);
        hub_list(app, screen.count ? screen.count : 1, *focus, file_item, NULL);
        hub_hints_extra(app, screen.count ? (screen.files[*focus].folder ? "Open" : "Import") : NULL,
                        "Back", strcmp(screen.dir, ROOT) ? "Import folder" : NULL, NULL, screen.count > 5);
        return;
    }
    HubProgress progress[16];
    char percent[20] = "";
    const char *detail = "Preparing import";
    size_t count = hub_progress_snapshot(progress, 16);
    for (size_t i = 0; i < count; ++i) {
        if (strcmp(progress[i].id, TASK_ID)) continue;
        detail = progress[i].detail;
        if (progress[i].permille)
            snprintf(percent, sizeof(percent), "%u%%", progress[i].permille / 10);
        break;
    }
    bool canceling = atomic_load(&screen.cancel);
    HubItem item = {canceling ? "Canceling import..." : detail, percent};
    hub_heading(app, "Importing local game", "KEEP THIS SCREEN OPEN UNTIL IMPORT FINISHES");
    hub_list(app, 1, 0, progress_item, &item);
    hub_hints_extra(app, NULL, canceling ? "Wait" : "Cancel", NULL, NULL, false);
}

static bool input(HubApp *app, size_t *focus, const HubGame *unused, HubInput event)
{
    (void)unused;
    if (screen.running) {
        if (event == HUB_BACK) atomic_store(&screen.cancel, true);
        return true;
    }
    if (event == HUB_BACK) {
        if (!strcmp(screen.dir, ROOT)) {
            clear_files();
            return false;
        }
        char *slash = strrchr(screen.dir, '/');
        *focus = 0;
        if (slash == screen.dir + sizeof(ROOT) - 2) ++slash;
        *slash = 0;
        if (!read_files()) hub_dialog(app, "Could not open folder", screen.dir);
        return true;
    }
    if (event == HUB_ACTION && strcmp(screen.dir, ROOT)) {
        start(app, screen.dir);
        return true;
    }
    if (event == HUB_CONFIRM && *focus < screen.count) {
        const FileItem *item = &screen.files[*focus];
        char path[HUB_PATH_SIZE];
        if (!import_path(path, screen.dir, item->name)) {
            hub_dialog(app, "Path is too long", "Move the game closer to the root of your SD card.");
        } else if (item->folder) {
            strcpy(screen.dir, path);
            *focus = 0;
            if (!read_files()) hub_dialog(app, "Could not open folder", screen.dir);
        } else {
            start(app, path);
        }
        return true;
    }
    return hub_move_focus(focus, screen.count, 1, 5, event);
}

void hub_register_local_import(HubApp *app)
{
    static const HubScreen page = {"local-import.files", draw, input};
    static const HubActionEntry settings = {"Import local game", "local-import.files", NULL};
    if (hub_register_screen(app, &page)) {
        hub_register_action(app, HUB_SETTING_ENTRY, &settings);
        appletHook(&screen.hook, on_applet, NULL);
    }
}
