#include "update_screen.h"
#include "support_ui.h"
#include "update_check.h"

#include <curl/curl.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <switch.h>

#define RELEASE_URL "https://github.com/TomorrowX6/AliceHub/releases/tag/"
#define MAX_RESPONSE (256 * 1024)

static Thread worker;
static bool running, enabled;
static atomic_bool done, stop;
static char result[HUB_UPDATE_TAG_SIZE], available[HUB_UPDATE_TAG_SIZE];

typedef struct { char data[MAX_RESPONSE]; size_t length; } Response;

static size_t receive(char *bytes, size_t size, size_t count, void *context)
{
    Response *body = context;
    size_t length = size * count;
    if (length >= sizeof(body->data) - body->length)
        return 0;
    memcpy(body->data + body->length, bytes, length);
    body->length += length;
    body->data[body->length] = 0;
    return length;
}

static int interrupted(void *unused, curl_off_t total, curl_off_t current,
                       curl_off_t upload, curl_off_t sent)
{
    (void)unused; (void)total; (void)current; (void)upload; (void)sent;
    return atomic_load(&stop);
}

static bool fetch_json(CURL *curl, Response *body, const char *url)
{
    if (atomic_load(&stop))
        return false;
    body->length = 0;
    body->data[0] = 0;
    curl_easy_setopt(curl, CURLOPT_URL, url);
    long status = 0;
    return curl_easy_perform(curl) == CURLE_OK &&
           curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status) == CURLE_OK && status == 200;
}

static void fetch_release(void)
{
    CURL *curl = curl_easy_init();
    Response *body = calloc(1, sizeof(*body));
    if (curl && body) {
        curl_easy_setopt(curl, CURLOPT_USERAGENT, "AliceHub");
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 5L);
        curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
        curl_easy_setopt(curl, CURLOPT_NOPROGRESS, 0L);
        curl_easy_setopt(curl, CURLOPT_XFERINFOFUNCTION, interrupted);
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, receive);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, body);
        char tag[HUB_UPDATE_TAG_SIZE], url[HUB_UPDATE_COMPARE_URL_SIZE];
        if (fetch_json(curl, body, HUB_UPDATE_API "/releases/latest") &&
            hub_update_release_tag(body->data, tag) &&
            hub_update_compare_url(ALICEHUB_GIT_COMMIT, tag, url) &&
            fetch_json(curl, body, url) && hub_update_is_behind(body->data))
            strcpy(result, tag);
    }
    if (curl)
        curl_easy_cleanup(curl);
    free(body);
}

static void work(void *unused)
{
    (void)unused;
    if (hub_socket_acquire()) {
        fetch_release();
        hub_socket_release();
    }
    atomic_store(&done, true);
}

void hub_start_update_check(void)
{
    enabled = hub_update_enabled(HUB_ROOT);
    if (!enabled || !hub_https_ready() || !hub_update_valid_commit(ALICEHUB_GIT_COMMIT))
        return;
    if (R_SUCCEEDED(threadCreate(&worker, work, NULL, NULL, 128 * 1024, 0x2c, -2))) {
        running = R_SUCCEEDED(threadStart(&worker));
        if (!running)
            threadClose(&worker);
    }
}

void hub_stop_update_check(void)
{
    if (!running)
        return;
    atomic_store(&stop, true);
    threadWaitForExit(&worker);
    threadClose(&worker);
    running = false;
}

static const char *title(void)
{
    return support_tr("检查更新", "アップデートを確認", "Check for updates");
}

void hub_poll_update_check(HubApp *app)
{
    if (!running || !atomic_load(&done))
        return;
    bool canceled = atomic_load(&stop);
    hub_stop_update_check();
    if (enabled && !canceled && *result) {
        strcpy(available, result);
        hub_toast(app, support_tr("AliceHub 有可用更新：请查看设置", "AliceHub の更新は設定から確認できます",
                                  "AliceHub update available in Settings"));
    }
}

static void release_url(char url[sizeof(RELEASE_URL) + HUB_UPDATE_TAG_SIZE])
{
    snprintf(url, sizeof(RELEASE_URL) + HUB_UPDATE_TAG_SIZE, "%s%s", RELEASE_URL, available);
}

static void draw(HubApp *app, size_t *focus, const HubGame *unused)
{
    (void)focus; (void)unused;
    hub_heading(app, title(), enabled ? support_tr("已启用", "オン", "Enabled") :
                                      support_tr("已禁用", "オフ", "Disabled"));
    if (*available) {
        char url[sizeof(RELEASE_URL) + HUB_UPDATE_TAG_SIZE];
        release_url(url);
        hub_link_card(app, support_tr("AliceHub 有可用更新", "AliceHub のアップデートがあります",
                                      "AliceHub update available"), url);
    } else {
        hub_empty(app, title(), support_tr("启动时检查 GitHub 上的最新版本。", "起動時に GitHub の最新リリースを確認します。",
                                          "Check the latest GitHub release on startup."));
    }
    hub_hints(app, enabled ? support_tr("关闭", "オフにする", "Disable") :
                            support_tr("开启", "オンにする", "Enable"), support_back(),
              *available ? support_tr("查看发布页", "リリースを表示", "View release") : NULL);
}

static bool input(HubApp *app, size_t *focus, const HubGame *unused, HubInput event)
{
    (void)focus; (void)unused;
    if (event == HUB_CONFIRM) {
        if (hub_update_set_enabled(HUB_ROOT, !enabled)) {
            enabled = !enabled;
            if (!enabled) {
                *available = 0;
                atomic_store(&stop, true);
            }
        } else {
            hub_toast(app, support_tr("无法保存设置", "設定を保存できません", "Could not save settings"));
        }
        return true;
    }
    if (event == HUB_ACTION && *available) {
        char url[sizeof(RELEASE_URL) + HUB_UPDATE_TAG_SIZE];
        release_url(url);
        WebCommonConfig config;
        WebCommonReply reply;
        WebExitReason reason;
        Result rc = webPageCreate(&config, url);
        if (R_SUCCEEDED(rc))
            rc = webConfigShow(&config, &reply);
        hub_wait_for_release(app);
        if (R_FAILED(rc) || R_FAILED(webReplyGetExitReason(&reply, &reason)) ||
            reason == WebExitReason_ErrorDialog)
            hub_dialog(app, title(), url);
        return true;
    }
    return false;
}

void hub_register_update_check(HubApp *app)
{
    enabled = hub_update_enabled(HUB_ROOT);
    static const HubScreen screen = {"update-check", draw, input};
    static HubActionEntry entry = {.screen_id = "update-check"};
    entry.label = title();
    hub_register_screen(app, &screen);
    hub_register_action(app, HUB_SETTING_ENTRY, &entry);
}
