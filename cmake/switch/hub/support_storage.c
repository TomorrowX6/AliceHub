#include "hub.h"
#include "support_files.h"
#include "support_ui.h"

#include <pthread.h>
#include <stdlib.h>

static pthread_t worker;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static HubStorage result, display;
static bool running, done, operation_ok;

static void *storage_job(void *arg)
{
    bool clear = arg != NULL;
    bool ok = !clear || hub_support_clear_cache(HUB_ROOT);
    HubStorage storage = hub_support_storage(HUB_ROOT);
    pthread_mutex_lock(&mutex);
    result = storage;
    operation_ok = ok;
    done = true;
    pthread_mutex_unlock(&mutex);
    return NULL;
}

static void join_storage(void)
{
    if (running)
        pthread_join(worker, NULL);
    running = false;
}

static bool start_storage(HubApp *app, bool clear)
{
    static int clear_job;
    pthread_attr_t attr;
    if (pthread_attr_init(&attr))
        return false;
    done = false;
    running = !pthread_attr_setstacksize(&attr, 128 * 1024) &&
              !pthread_create(&worker, &attr, storage_job, clear ? &clear_job : NULL);
    pthread_attr_destroy(&attr);
    if (!running)
        hub_toast(app, support_tr("无法读取存储", "ストレージを読み込めません", "Could not read storage"));
    return running;
}

static void poll_storage(HubApp *app)
{
    pthread_mutex_lock(&mutex);
    bool ready = running && done;
    pthread_mutex_unlock(&mutex);
    if (!ready)
        return;
    join_storage();
    display = result;
    if (!operation_ok)
        hub_toast(app, support_tr("部分缓存无法清理", "一部のキャッシュを削除できません", "Some cache files could not be cleared"));
}

static void size_label(char text[32], uint64_t bytes)
{
    static const char *const units[] = {"B", "KiB", "MiB", "GiB", "TiB"};
    unsigned i = 0;
    double value = bytes;
    while (i < 4 && value >= 1024) {
        value /= 1024;
        i++;
    }
    if (i)
        snprintf(text, 32, "%.1f %s", value, units[i]);
    else
        snprintf(text, 32, "%llu B", (unsigned long long)bytes);
}

static const char *storage_title(void)
{
    return support_tr("缓存与存储", "キャッシュとストレージ", "Cache & Storage");
}

static void storage_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    if (*focus == (size_t)-1) {
        *focus = 0;
        if (!running)
            start_storage(app, false);
    }
    poll_storage(app);
    hub_heading(app, storage_title(),
                running ? support_tr("正在读取 SD 卡…", "SD カードを確認中…", "Reading SD card…") :
                !display.sizes_ok ? support_tr("部分使用量不可用", "一部の使用量は取得できません", "Some usage figures are unavailable") :
                support_tr("不会删除游戏、存档或下载数据", "ゲーム・セーブ・ダウンロードは削除されません", "Games, saves and downloads are not deleted"));
    if (running) {
        hub_hints(app, NULL, support_back(), NULL);
        return;
    }
    char used[5][32];
    const uint64_t values[] = {display.cache, display.free, display.capacity, display.games, display.saves};
    for (size_t i = 0; i < 5; i++)
        size_label(used[i], values[i]);
    const HubItem rows[] = {
        {support_tr("清理缓存", "キャッシュを削除", "Clear Cache"), used[0]},
        {support_tr("SD 卡可用空间", "SD カードの空き容量", "SD card free space"), display.volume_ok ? used[1] : "—"},
        {support_tr("SD 卡总容量", "SD カードの総容量", "SD card capacity"), display.volume_ok ? used[2] : "—"},
        {support_tr("游戏", "ゲーム", "Games"), used[3]},
        {support_tr("存档", "セーブ", "Saves"), used[4]},
    };
    hub_list(app, 5, *focus, support_item, rows);
    hub_hints(app, !*focus ? support_tr("清理", "削除", "Clear") : NULL, support_back(),
              support_tr("刷新", "更新", "Refresh"));
}

static bool storage_input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    (void)game;
    if (running)
        return event != HUB_BACK;
    if (event == HUB_ACTION)
        return start_storage(app, false);
    if (event == HUB_CONFIRM && *focus == 0)
        return hub_push(app, "support.clear-cache", NULL);
    return hub_move_focus(focus, 5, 1, 5, event);
}

static void clear_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    if (*focus >= 2)
        *focus = 0;
    hub_heading(app, support_tr("清理缓存", "キャッシュを削除", "Clear Cache"),
                support_tr("仅删除临时缓存。保留游戏、存档和下载数据。",
                           "一時キャッシュのみ削除します。ゲーム・セーブ・ダウンロードは保持されます。",
                           "Only temporary cache files will be deleted. Games, saves and downloads are kept."));
    const HubItem rows[] = {
        {support_tr("取消", "キャンセル", "Cancel"), NULL},
        {support_tr("删除临时缓存", "一時キャッシュを削除", "Delete temporary cache"), NULL},
    };
    hub_list(app, 2, *focus, support_item, rows);
    hub_hints(app, support_tr("确定", "決定", "Select"), support_back(), NULL);
}

static bool clear_input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    (void)game;
    if (event == HUB_CONFIRM) {
        if (*focus == 1)
            start_storage(app, true);
        hub_pop(app);
        return true;
    }
    return hub_move_focus(focus, 2, 1, 5, event);
}

void support_register_storage(HubApp *app)
{
    static const HubScreen storage = {"support.storage", storage_draw, storage_input};
    static const HubScreen clear = {"support.clear-cache", clear_draw, clear_input};
    static HubActionEntry action = {.screen_id = "support.storage"};
    action.label = storage_title();
    if (atexit(join_storage))
        return;
    hub_register_screen(app, &storage);
    hub_register_screen(app, &clear);
    hub_register_action(app, HUB_SETTING_ENTRY, &action);
}
