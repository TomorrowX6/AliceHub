#include "hub.h"
#include "../picker_art.h"
#include "../picker_renderer.h"

#include <errno.h>
#include <sys/stat.h>
#include <unistd.h>

typedef struct {
    const HubScreen *screen;
    size_t focus;
    HubGame game;
    bool has_game;
} Frame;

struct HubApp {
    Picker picker;
    u16 *logo;
    HubLibrary library;
    HubSettings settings;
    HubHandoff returned;
    const HubScreen *screens[32];
    const HubActionEntry *actions[HUB_ACTION_GROUPS][32];
    Frame stack[12];
    size_t screen_count, action_count[HUB_ACTION_GROUPS], depth;
    u64 blocked;
    bool latch_release, running;
    char dialog_title[128], dialog_detail[512], toast[256];
    unsigned toast_frames;
};

bool hub_register_screen(HubApp *app, const HubScreen *screen)
{
    if (!screen || !screen->id || !*screen->id || !screen->draw ||
        app->screen_count == sizeof(app->screens) / sizeof(*app->screens))
        return false;
    for (size_t i = 0; i < app->screen_count; ++i)
        if (!strcmp(app->screens[i]->id, screen->id))
            return false;
    app->screens[app->screen_count++] = screen;
    return true;
}

bool hub_register_action(HubApp *app, HubActionGroup group, const HubActionEntry *entry)
{
    if ((unsigned)group >= HUB_ACTION_GROUPS || !entry || !entry->label || !entry->screen_id ||
        app->action_count[group] == sizeof(app->actions[group]) / sizeof(*app->actions[group]))
        return false;
    for (size_t i = 0; i < app->action_count[group]; ++i)
        if (!strcmp(app->actions[group][i]->screen_id, entry->screen_id))
            return false;
    app->actions[group][app->action_count[group]++] = entry;
    return true;
}

const HubActionEntry *hub_action_entry(const HubApp *app, HubActionGroup group,
                                      const HubGame *game, size_t index)
{
    if ((unsigned)group >= HUB_ACTION_GROUPS)
        return NULL;
    for (size_t i = 0; i < app->action_count[group]; ++i) {
        const HubActionEntry *entry = app->actions[group][i];
        if ((!entry->available || entry->available(game)) && !index--)
            return entry;
    }
    return NULL;
}

bool hub_push(HubApp *app, const char *id, const HubGame *game)
{
    if (app->depth == sizeof(app->stack) / sizeof(*app->stack))
        return false;
    for (size_t i = 0; i < app->screen_count; ++i) {
        if (!strcmp(app->screens[i]->id, id)) {
            Frame *frame = &app->stack[app->depth++];
            *frame = (Frame){.screen = app->screens[i], .focus = (size_t)-1};
            if (game) {
                frame->game = *game;
                frame->has_game = true;
            }
            hub_wait_for_release(app);
            return true;
        }
    }
    return false;
}

bool hub_pop(HubApp *app)
{
    if (app->depth <= 1)
        return false;
    --app->depth;
    hub_wait_for_release(app);
    return true;
}

void hub_wait_for_release(HubApp *app) { app->latch_release = true; }
const HubLibrary *hub_library(const HubApp *app) { return &app->library; }
bool hub_refresh_library(HubApp *app)
{
    if (!hub_library_scan(&app->library, HUB_ROOT))
        return false;
    hub_library_sort(&app->library, app->settings.sort);
    return true;
}

HubSettings *hub_settings(HubApp *app) { return &app->settings; }
bool hub_apply_settings(HubApp *app, const HubSettings *settings)
{
    if (!hub_settings_save(HUB_ROOT, settings))
        return false;
    app->settings = *settings;
    hub_library_sort(&app->library, settings->sort);
    return true;
}
const char *hub_returned_instance(const HubApp *app) { return app->returned.game.instance_id; }

void hub_dialog(HubApp *app, const char *title, const char *detail)
{
    snprintf(app->dialog_title, sizeof(app->dialog_title), "%s", title);
    snprintf(app->dialog_detail, sizeof(app->dialog_detail), "%s", detail);
    hub_wait_for_release(app);
}

void hub_toast(HubApp *app, const char *message)
{
    snprintf(app->toast, sizeof(app->toast), "%s", message);
    app->toast_frames = 150;
}

void hub_launch(HubApp *app, const HubGame *game)
{
    if (!envHasNextLoad()) {
        hub_dialog(app, "Cannot start game", "This loader does not support launching another NRO.");
        return;
    }
    const char *nro = hub_engines[game->engine].nro;
    if (access(nro, R_OK) != 0) {
        hub_dialog(app, "Engine NRO not found", nro);
        return;
    }
    HubHandoff request = {.game = *game};
    hub_handoff_clear(HUB_ROOT, true);
    if (!hub_handoff_write(HUB_ROOT, false, &request)) {
        hub_dialog(app, "Cannot start game", "Could not write the launch request to the SD card.");
        return;
    }
    char args[128];
    snprintf(args, sizeof(args), "%s %s", nro, HUB_REQUEST_ARG);
    if (R_FAILED(envSetNextLoad(nro, args))) {
        hub_handoff_clear(HUB_ROOT, false);
        hub_dialog(app, "Cannot start game", "The loader rejected the engine launch.");
        return;
    }
    app->running = false;
}

bool hub_move_focus(size_t *focus, size_t count, size_t columns, size_t page, HubInput event)
{
    if (!count || !columns || !page)
        return false;
    if (*focus >= count)
        *focus = 0;
    size_t step;
    switch (event) {
    case HUB_LEFT: case HUB_RIGHT: step = 1; break;
    case HUB_UP: case HUB_DOWN: step = columns; break;
    case HUB_PREV_PAGE: case HUB_NEXT_PAGE: step = page; break;
    default: return false;
    }
    if (event == HUB_LEFT || event == HUB_UP || event == HUB_PREV_PAGE)
        *focus = step > *focus ? 0 : *focus - step;
    else
        *focus += step < count - *focus ? step : count - *focus - 1;
    return true;
}

void hub_heading(HubApp *app, const char *title, const char *subtitle)
{
    picker_label(&app->picker, 64, 149, 1216, 18, PRIMARY, subtitle);
    picker_label(&app->picker, 64, 205, 1216, 45, INK, title);
}

void hub_hints(HubApp *app, const char *confirm, const char *back, const char *action)
{
    hub_hints_extra(app, confirm, back, action, NULL, true);
}

void hub_hints_extra(HubApp *app, const char *confirm, const char *back, const char *action,
                     const char *secondary, bool paging)
{
    Picker *p = &app->picker;
    picker_panel(p, 0, 628, PICKER_WIDTH, 1, 0, OUTLINE);
    picker_panel(p, 0, 629, PICKER_WIDTH, 91, 0, SURFACE);
    if (paging) {
        picker_hint(p, 64, "L", "Previous page", false);
        picker_hint(p, 296, "R", "Next page", false);
    }
    if (secondary)
        picker_hint(p, 506, "Y", secondary, false);
    if (action)
        picker_hint(p, 692, "X", action, false);
    if (confirm)
        picker_hint(p, 900, "A", confirm, true);
    picker_hint(p, 1094, "B", back, false);
}

void hub_empty(HubApp *app, const char *title, const char *detail)
{
    Picker *p = &app->picker;
    picker_panel(p, 64, 242, 1152, 334, 22, SURFACE);
    picker_label(p, 322, 377, 1070, 35, INK, title);
    picker_label(p, 322, 426, 1070, 22, MUTED, detail);
}

void hub_grid(HubApp *app, size_t count, size_t focus, HubItems items, const void *data)
{
    Picker *p = &app->picker;
    if (!count) {
        hub_empty(app, "No games to show", "Add a game to its engine's games folder on the SD card.");
        return;
    }
    size_t first = focus < count ? focus / CARD_COUNT * CARD_COUNT : 0;
    for (size_t i = first; i < count && i < first + CARD_COUNT; ++i) {
        int x = 64 + (int)(i - first) * 292;
        bool active = i == focus;
        HubItem item = items(data, i);
        picker_panel(p, x, 242, CARD_WIDTH, 334, 22, active ? PRIMARY : OUTLINE);
        picker_panel(p, x + 3, 245, CARD_WIDTH - 6, 328, 19, active ? CHIP : SURFACE);
        picker_placeholder(p, x + 12, 254, item.title);
        picker_label(p, x + 17, 494, x + CARD_WIDTH - 17, 24, INK, item.title);
        if (item.subtitle)
            picker_label(p, x + 17, 550, x + CARD_WIDTH - 17, 18, MUTED, item.subtitle);
    }
    char position[52];
    snprintf(position, sizeof(position), "%zu of %zu", focus < count ? focus + 1 : 1, count);
    picker_label(p, 1070, 205, 1216, 21, MUTED, position);
}

void hub_list(HubApp *app, size_t count, size_t focus, HubItems items, const void *data)
{
    Picker *p = &app->picker;
    const size_t page = 5;
    size_t first = focus < count ? focus / page * page : 0;
    for (size_t i = first; i < count && i < first + page; ++i) {
        int y = 239 + (int)(i - first) * 70;
        HubItem item = items(data, i);
        picker_panel(p, 64, y, 1152, 60, 14, i == focus ? CHIP : SURFACE);
        picker_label(p, 88, y + 38, 850, 26, INK, item.title);
        if (item.subtitle)
            picker_label(p, 870, y + 38, 1190, 21, MUTED, item.subtitle);
    }
}

static void draw_frame(HubApp *app)
{
    Picker *p = &app->picker;
    picker_begin(p, false);
    for (int y = 0; y < PICKER_HEIGHT; ++y)
        picker_panel(p, 0, y, PICKER_WIDTH, 1, 0, picker_mix(0x171C2E, 0x0E1014, y * 255 / PICKER_HEIGHT));
    picker_panel(p, 0, 0, PICKER_WIDTH, 106, 0, SURFACE);
    if (app->logo)
        picker_image(p, 64, 24, 58, 58, 16, app->logo);
    picker_label(p, 141, 69, 800, 40, INK, "AliceHub");
    picker_panel(p, 1028, 32, 188, 42, 21, CHIP);
    picker_label(p, 1055, 60, 1196, 21, PRIMARY, "SWITCH HUB");
    Frame *top = &app->stack[app->depth - 1];
    top->screen->draw(app, &top->focus, top->has_game ? &top->game : NULL);
    if (app->toast_frames) {
        --app->toast_frames;
        picker_panel(p, 362, 558, 556, 56, 16, CHIP);
        picker_label(p, 385, 595, 896, 22, INK, app->toast);
    }
    if (*app->dialog_title) {
        for (int y = 107; y < 628; ++y)
            for (int x = 0; x < PICKER_WIDTH; ++x) {
                u16 *pixel = p->pixels + y * p->stride + x;
                *pixel = (*pixel & 0xf7de) >> 1;
            }
        picker_panel(p, 222, 242, 836, 240, 22, OUTLINE);
        picker_panel(p, 224, 244, 832, 236, 20, VARIANT);
        picker_label(p, 260, 308, 1020, 32, WARN, app->dialog_title);
        picker_label(p, 260, 365, 1020, 22, INK, app->dialog_detail);
        picker_panel(p, 260, 401, 164, 45, 16, PRIMARY);
        picker_label(p, 278, 431, 415, 20, ON_PRIMARY, "A   Dismiss");
    }
    framebufferEnd(&p->fb);
}

static void dispatch(HubApp *app, HubInput input)
{
    if (*app->dialog_title) {
        if (input == HUB_CONFIRM || input == HUB_BACK) {
            *app->dialog_title = 0;
            hub_wait_for_release(app);
        }
        return;
    }
    Frame *top = &app->stack[app->depth - 1];
    if (top->screen->input &&
        top->screen->input(app, &top->focus, top->has_game ? &top->game : NULL, input))
        return;
    if (input == HUB_BACK && !hub_pop(app))
        app->running = false;
}

int main(int argc, char **argv)
{
    if (R_FAILED(romfsInit()))
        return 1;
    static HubApp app;
    int status = 1;
    if (!picker_open(&app.picker))
        goto finish;
    app.logo = picker_art_load("romfs:/alicehub.png", 58, 58);
    hub_register_features(&app);
    if (!hub_push(&app, "library", NULL))
        goto finish;
    if (!hub_settings_load(HUB_ROOT, &app.settings))
        hub_toast(&app, "Unsupported or damaged settings; using defaults");
    if ((mkdir(HUB_ROOT, 0777) && errno != EEXIST) || !hub_refresh_library(&app))
        hub_toast(&app, "Could not scan the SD card library");
    if (argc == 2 && !strcmp(argv[1], HUB_RETURN_ARG)) {
        if (!hub_handoff_take(HUB_ROOT, true, &app.returned))
            hub_toast(&app, "Could not read the engine result");
        else if (*app.returned.error)
            hub_dialog(&app, app.returned.game.name, app.returned.error);
    }

    const u64 directions = HidNpadButton_Up | HidNpadButton_Down | HidNpadButton_Left | HidNpadButton_Right;
    const struct { u64 button; HubInput event; } inputs[] = {
        {HidNpadButton_B | HidNpadButton_Plus, HUB_BACK}, {HidNpadButton_A, HUB_CONFIRM},
        {HidNpadButton_X, HUB_ACTION}, {HidNpadButton_Y, HUB_SECONDARY},
        {HidNpadButton_L, HUB_PREV_PAGE}, {HidNpadButton_R, HUB_NEXT_PAGE},
        {HidNpadButton_Up, HUB_UP}, {HidNpadButton_Down, HUB_DOWN},
        {HidNpadButton_Left, HUB_LEFT}, {HidNpadButton_Right, HUB_RIGHT},
    };
    PadState pad;
    PadRepeater repeat;
    padConfigureInput(1, HidNpadStyleSet_NpadStandard);
    padInitializeDefault(&pad);
    padRepeaterInitialize(&repeat, 18, 6);
    app.running = true;
    while (app.running && appletMainLoop()) {
        padUpdate(&pad);
        u64 held = padGetButtons(&pad);
        if (app.latch_release) {
            app.blocked = held;
            app.latch_release = false;
        }
        app.blocked &= held;
        padRepeaterUpdate(&repeat, held & directions & ~app.blocked);
        u64 keys = (padGetButtonsDown(&pad) | padRepeaterGetButtons(&repeat)) & ~app.blocked;
        for (size_t i = 0; i < sizeof(inputs) / sizeof(*inputs); ++i)
            if (keys & inputs[i].button) {
                dispatch(&app, inputs[i].event);
                break;
            }
        if (app.running)
            draw_frame(&app);
        svcSleepThread(16000000);
    }
    status = 0;
finish:
    free(app.logo);
    hub_library_free(&app.library);
    picker_close(&app.picker);
    romfsExit();
    return status;
}
