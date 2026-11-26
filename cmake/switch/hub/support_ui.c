#include "support_ui.h"
#include "support_files.h"
#include "support_text.h"

#include <string.h>
#include <switch.h>
#include <sys/stat.h>

enum { ENGLISH, CHINESE, JAPANESE };
static int language;
static SupportText document;
static const char *document_title;

const char *support_tr(const char *zh, const char *ja, const char *en)
{
    return language == CHINESE ? zh : language == JAPANESE ? ja : en;
}

static const char *select_hint(void)
{
    return support_tr("打开", "開く", "Open");
}

static bool show_document(HubApp *app, const char *title, const char *path, bool html)
{
    if (!support_text_load(&document, path, html)) {
        hub_dialog(app, title, support_tr("无法读取文件", "ファイルを読み込めません", "Could not read the file"));
        return true;
    }
    document_title = title;
    if (!hub_push(app, "support.document", NULL))
        support_text_free(&document);
    return true;
}

static HubItem doc_line(const void *data, size_t i)
{
    return (HubItem){((const SupportText *)data)->lines[i], NULL};
}

static void document_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    if (*focus >= document.count)
        *focus = 0;
    char page[64];
    snprintf(page, sizeof(page), "%s %zu / %zu", support_tr("页", "ページ", "Page"),
             *focus / 5 + 1, (document.count + 4) / 5);
    hub_heading(app, document_title, page);
    hub_list(app, document.count, *focus, doc_line, &document);
    hub_hints(app, NULL, support_back(), NULL);
}

static bool document_input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    (void)app;
    (void)game;
    if (event == HUB_BACK)
        support_text_free(&document);
    return hub_move_focus(focus, document.count, 1, 5, event);
}

static const char *manual_title(void)
{
    return support_tr("游戏说明书", "ゲームマニュアル", "Game Manual");
}

static bool manual_path(const HubGame *game, char out[HUB_PATH_SIZE])
{
    char folder[HUB_PATH_SIZE];
    if (!game || !hub_game_path(HUB_ROOT, game, folder))
        return false;
    int n = snprintf(out, HUB_PATH_SIZE, "%s/Manual/index.html", folder);
    struct stat st;
    return n >= 0 && n < HUB_PATH_SIZE && !stat(out, &st) && S_ISREG(st.st_mode);
}

static bool manual_available(const HubGame *game)
{
    char path[HUB_PATH_SIZE];
    return manual_path(game, path);
}

static void manual_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    *focus = 0;
    const HubItem entry[] = {{select_hint(), NULL}};
    hub_heading(app, manual_title(), support_tr("离线文本阅读", "オフラインのテキスト表示", "Offline text view"));
    hub_list(app, 1, *focus, support_item, entry);
    hub_hints(app, select_hint(), support_back(), NULL);
}

static bool manual_input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    (void)focus;
    if (event != HUB_CONFIRM)
        return false;
    char path[HUB_PATH_SIZE];
    if (manual_path(game, path))
        return show_document(app, manual_title(), path, true);
    hub_dialog(app, manual_title(), support_tr("找不到说明书", "マニュアルが見つかりません", "Manual not found"));
    return true;
}

static const HubItem licenses[] = {
    {"AliceHub / xsystem4", "GPL-2.0"}, {"libsys4", "GPL-2.0"},
    {"xsystem35", "GPL-2.0"}, {"VL Gothic", NULL}, {"HanaMinA", NULL},
    {"MTLc3m", NULL}, {"Mincho", NULL}, {"nanojpeg", NULL}, {"microui", NULL}, {"libxml2", NULL},
    {"libopus", "BSD-3-Clause"},
};
static const char *const license_paths[] = {
    "romfs:/support/xsystem4.txt", "romfs:/support/libsys4.txt", "romfs:/support/xsystem35.txt",
    "romfs:/fonts/VL-Gothic-Regular.license", "romfs:/fonts/HanaMinA.license",
    "romfs:/support/MTLc3m.txt", "romfs:/support/mincho.txt", "romfs:/support/nanojpeg.txt",
    "romfs:/support/microui.txt", "romfs:/support/libxml2.txt", "romfs:/support/libopus.txt",
};
#define COUNT(array) (sizeof(array) / sizeof(*(array)))
_Static_assert(COUNT(licenses) == COUNT(license_paths), "license entries require a path");

static const char *licenses_title(void)
{
    return support_tr("开源许可证", "オープンソースライセンス", "Open Source Licenses");
}

static void licenses_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    if (*focus >= COUNT(licenses))
        *focus = 0;
    hub_heading(app, licenses_title(), support_tr("随应用提供的许可文本", "同梱のライセンス文書", "Bundled license texts"));
    hub_list(app, COUNT(licenses), *focus, support_item, licenses);
    hub_hints(app, select_hint(), support_back(), NULL);
}

static bool licenses_input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    (void)game;
    if (event == HUB_CONFIRM && *focus < COUNT(licenses))
        return show_document(app, licenses[*focus].title, license_paths[*focus], false);
    return hub_move_focus(focus, COUNT(licenses), 1, 5, event);
}

static const char *links_title(void)
{
    return support_tr("关于与链接", "情報とリンク", "About & Links");
}

static const char *const links[] = {
    "https://github.com/TomorrowX6/AliceHub",
    "https://github.com/TomorrowX6/AliceHub/issues",
};

static void links_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    const HubItem rows[] = {
        {links[0], "GitHub"},
        {links[1], support_tr("反馈问题", "問題を報告", "Report an issue")},
        {support_tr("QQ交流群", "QQ交流グループ", "QQ Group"), "383917628"},
    };
    if (*focus >= COUNT(rows))
        *focus = 0;
    hub_heading(app, links_title(), "AliceHub · Nintendo Switch");
    hub_list(app, COUNT(rows), *focus, support_item, rows);
    hub_hints(app, *focus < COUNT(links) ? select_hint() : NULL, support_back(), NULL);
}

static bool links_input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    (void)game;
    if (event == HUB_CONFIRM && *focus < COUNT(links)) {
        WebCommonConfig config;
        Result rc = webPageCreate(&config, links[*focus]);
        if (R_SUCCEEDED(rc))
            rc = webConfigShow(&config, NULL);
        hub_wait_for_release(app);
        if (R_FAILED(rc))
            hub_dialog(app, support_tr("浏览器不可用", "ブラウザーを利用できません", "Browser unavailable"), links[*focus]);
        return true;
    }
    return hub_move_focus(focus, COUNT(links) + 1, 1, 5, event);
}

static const char *logs_title(void)
{
    return support_tr("错误报告", "エラーレポート", "Error Reports");
}

static bool log_available(void)
{
    struct stat st;
    return !stat(HUB_ROOT "/" HUB_SUPPORT_LOG, &st) && S_ISREG(st.st_mode) && st.st_size > 0;
}

static void logs_draw(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    bool available = log_available();
    if (*focus >= 2 || !available)
        *focus = 0;
    hub_heading(app, logs_title(),
                available ? support_tr("最近一次游戏启动错误", "直近のゲーム起動エラー", "Most recent game launch error") :
                support_tr("暂无日志", "ログはありません", "No logs yet"));
    if (available) {
        const HubItem rows[] = {
            {support_tr("查看日志", "ログを表示", "View log"), NULL},
            {support_tr("导出至 SD 卡", "SD カードに書き出す", "Export to SD card"), NULL},
        };
        hub_list(app, COUNT(rows), *focus, support_item, rows);
    }
    hub_hints(app, available ? select_hint() : NULL, support_back(), NULL);
}

static bool logs_input(HubApp *app, size_t *focus, const HubGame *game, HubInput event)
{
    (void)game;
    if (!log_available())
        return false;
    if (event == HUB_CONFIRM && *focus == 0)
        return show_document(app, logs_title(), HUB_ROOT "/" HUB_SUPPORT_LOG, false);
    if (event == HUB_CONFIRM && *focus == 1) {
        if (hub_support_export_error(HUB_ROOT))
            hub_dialog(app, support_tr("报告已导出", "書き出しました", "Report exported"), HUB_ROOT "/" HUB_SUPPORT_REPORT);
        else
            hub_dialog(app, logs_title(), support_tr("无法导出报告", "書き出せません", "Could not export the report"));
        return true;
    }
    return hub_move_focus(focus, 2, 1, 5, event);
}

void hub_register_support(HubApp *app)
{
    if (R_SUCCEEDED(setInitialize())) {
        u64 code;
        SetLanguage system;
        if (R_SUCCEEDED(setGetSystemLanguage(&code)) && R_SUCCEEDED(setMakeLanguage(code, &system))) {
            if (system == SetLanguage_JA)
                language = JAPANESE;
            else if (system == SetLanguage_ZHCN || system == SetLanguage_ZHTW ||
                     system == SetLanguage_ZHHANS || system == SetLanguage_ZHHANT)
                language = CHINESE;
        }
        setExit();
    }
    static const HubScreen screens[] = {
        {"support.document", document_draw, document_input},
        {"support.manual", manual_draw, manual_input},
        {"support.licenses", licenses_draw, licenses_input},
        {"support.links", links_draw, links_input},
        {"support.logs", logs_draw, logs_input},
    };
    for (size_t i = 0; i < COUNT(screens); i++)
        hub_register_screen(app, &screens[i]);
    static HubActionEntry entries[] = {
        {.screen_id = "support.manual", .available = manual_available},
        {.screen_id = "support.licenses"},
        {.screen_id = "support.links"},
        {.screen_id = "support.logs"},
    };
    entries[0].label = manual_title();
    entries[1].label = licenses_title();
    entries[2].label = links_title();
    entries[3].label = logs_title();
    hub_register_action(app, HUB_DETAIL_ACTION, &entries[0]);
    support_register_storage(app);
    hub_register_action(app, HUB_SETTING_ENTRY, &entries[3]);
    hub_register_action(app, HUB_SETTING_ENTRY, &entries[1]);
    hub_register_action(app, HUB_SETTING_ENTRY, &entries[2]);
}
