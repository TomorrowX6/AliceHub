#include "runtime_menu.h"
#include "runtime_menu_engines.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "cJSON.h"
#include "modifier_api.h"
#include "savedata.h"

enum { CTRL_SKIP, FONT_SCALE, SCROLLBAR_SCALE, LOCK_BASE };

typedef struct {
    const char *game;
    enum modifier_lock_game id;
    const char *labels[12];
    int inverted;
} LiveLocks;

// In bit order from the engine's modifier lock masks.
static const LiveLocks supported_locks[] = {
    {"rance10", MODIFIER_LOCK_RANCE10,
     {"Lock party HP", "Infinite AP", "Lock combo", "Lock battle round to 1", "One-hit defeats",
      "Zero enemy attack", "Boost leader critical rate", "Infinite skill uses", "Infinite Zippo",
      "Max resources", "Guaranteed treasure", "Streamer mode"}, 1 << 11},
    {"dohnadohna", MODIFIER_LOCK_DOHNADOHNA,
     {"Lock party HP", "Lock party MP", "Lock party VP", "One-hit defeats", "Infinite money",
      "Infinite TP", "Best worker condition", "Freeze work timer"}, 0},
    {"evenicle", MODIFIER_LOCK_EVENICLE,
     {"No encounters", "Lock party HP", "Infinite SP", "Infinite BP", "One-hit defeats",
      "Infinite money"}, 0},
    {"sengokurance", MODIFIER_LOCK_SENGOKURANCE,
     {"Infinite gold", "Infinite AP", "Max satisfaction", "Lock ally HP", "One-hit defeats",
      "Unlimited actions"}, 0},
    {"rance01", MODIFIER_LOCK_RANCE01,
     {"Lock HP", "One-hit defeats", "Infinite gold", "Guaranteed drops", "Block debuffs"}, 0},
    {"rance02", MODIFIER_LOCK_RANCE02,
     {"Lock ally HP and SP", "One-hit defeats", "Infinite gold", "Infinite items"}, 0},
    {"rance03", MODIFIER_LOCK_RANCE03,
     {"Lock HP", "No cooldowns", "Infinite items", "Guaranteed drops", "One-hit defeats"}, 0},
    {"rance08", MODIFIER_LOCK_RANCE08,
     {"Lock ally HP", "One-hit defeats", "Infinite skills", "Max Magnum", "Infinite gold"}, 0},
    {"rance09", MODIFIER_LOCK_RANCE09,
     {"Ally invulnerability", "One-hit defeats", "Infinite actions", "Guaranteed drops",
      "Infinite resources"}, 0},
};

static const int font_steps[] = {75, 90, 100, 110, 125, 150, 160};
static const int scrollbar_steps[] = {75, 100, 125, 150, 200, 250, 300};
static char display_path[1024];

static const LiveLocks *current_locks(void)
{
    const char *game = modifier_api_current_game();
    for (size_t i = 0; i < SDL_arraysize(supported_locks); ++i) {
        if (!strcmp(game, supported_locks[i].game))
            return &supported_locks[i];
    }
    return NULL;
}

static bool save_display(int font, int scrollbar)
{
    if (!display_path[0])
        return false;
    char *tmp = NULL;
    FILE *fp = atomic_save_open(display_path, "wb", &tmp);
    if (!fp)
        return false;
    if (fprintf(fp, "{\"version\":1,\"font\":%d,\"scrollbar\":%d}\n", font, scrollbar) < 0) {
        atomic_save_abort(fp, tmp);
        return false;
    }
    return atomic_save_commit(fp, display_path, tmp, false);
}

void switch_menu4_load_display(const char *save_dir)
{
    display_path[0] = 0;
    if (!save_dir || !*save_dir)
        return;
    int length = snprintf(display_path, sizeof(display_path), "%s/.alicehub-display.json", save_dir);
    if (length < 0 || (size_t)length >= sizeof(display_path)) {
        display_path[0] = 0;
        return;
    }
    FILE *fp = fopen(display_path, "rb");
    if (!fp)
        return;
    char buf[128];
    size_t len = fread(buf, 1, sizeof(buf) - 1, fp);
    bool complete = feof(fp) && !ferror(fp);
    fclose(fp);
    if (!complete)
        return;
    buf[len] = 0;
    cJSON *config = cJSON_ParseWithOpts(buf, NULL, true);
    const cJSON *version = cJSON_GetObjectItemCaseSensitive(config, "version");
    const cJSON *font = cJSON_GetObjectItemCaseSensitive(config, "font");
    const cJSON *scrollbar = cJSON_GetObjectItemCaseSensitive(config, "scrollbar");
    if (cJSON_IsNumber(version) && version->valuedouble == 1 &&
        cJSON_IsNumber(font) && font->valuedouble >= 75 && font->valuedouble <= 160 &&
        cJSON_IsNumber(scrollbar) && scrollbar->valuedouble >= 75 && scrollbar->valuedouble <= 300)
        modifier_api_set_ui_scale(font->valueint, scrollbar->valueint);
    cJSON_Delete(config);
}

static int next_scale(int current, int direction, const int *steps, size_t count)
{
    if (direction > 0) {
        for (size_t i = 0; i < count; ++i)
            if (steps[i] > current)
                return steps[i];
        return steps[count - 1];
    }
    for (size_t i = count; i > 0; --i)
        if (steps[i - 1] < current)
            return steps[i - 1];
    return steps[0];
}

static size_t options(void *context, bool modifiers, SwitchMenuRow *rows)
{
    (void)context;
    if (!modifiers) {
        rows[0] = (SwitchMenuRow){"Ctrl skip", CTRL_SKIP, true, {0}};
        SDL_strlcpy(rows[0].value, modifier_api_get_ctrl_skip() ? "On" : "Off", sizeof(rows[0].value));
        rows[1] = (SwitchMenuRow){"Text scale", FONT_SCALE, true, {0}};
        snprintf(rows[1].value, sizeof(rows[1].value), "%d%%", modifier_api_get_font_scale());
        rows[2] = (SwitchMenuRow){"Scrollbar scale", SCROLLBAR_SCALE, true, {0}};
        snprintf(rows[2].value, sizeof(rows[2].value), "%d%%", modifier_api_get_scrollbar_scale());
        return 3;
    }
    const LiveLocks *locks = current_locks();
    if (!locks)
        return 0;
    int mask = modifier_api_get_lock_mask(locks->id) ^ locks->inverted;
    size_t i;
    for (i = 0; i < SDL_arraysize(locks->labels) && locks->labels[i]; ++i) {
        rows[i] = (SwitchMenuRow){locks->labels[i], LOCK_BASE + (int)i, true, {0}};
        SDL_strlcpy(rows[i].value, (mask & (1 << i)) ? "On" : "Off", sizeof(rows[i].value));
    }
    return i;
}

static void activate(void *context, int action, int direction, char *status)
{
    (void)context;
    if (action == CTRL_SKIP) {
        modifier_api_set_ctrl_skip(!modifier_api_get_ctrl_skip());
    } else if (action == FONT_SCALE || action == SCROLLBAR_SCALE) {
        int font = modifier_api_get_font_scale(), scrollbar = modifier_api_get_scrollbar_scale();
        if (action == FONT_SCALE)
            font = next_scale(font, direction, font_steps, SDL_arraysize(font_steps));
        else
            scrollbar = next_scale(scrollbar, direction, scrollbar_steps, SDL_arraysize(scrollbar_steps));
        if (font == modifier_api_get_font_scale() && scrollbar == modifier_api_get_scrollbar_scale())
            return;
        modifier_api_set_ui_scale(font, scrollbar);
        if (!save_display(font, scrollbar))
            snprintf(status, SWITCH_MENU_STATUS, "Applied for this session; couldn't save to SD.");
    } else {
        const LiveLocks *locks = current_locks();
        if (locks) {
            int mask = modifier_api_get_lock_mask(locks->id) ^ (1 << (action - LOCK_BASE));
            modifier_api_set_lock_mask(locks->id, mask);
        }
    }
}

static const SwitchMenu menu = {
    "romfs:/fonts/VL-Gothic-Regular.ttf", "png", gfx_switch_menu_present,
    gfx_switch_menu_capture, options, activate
};

bool switch_menu4_poll(void)
{
    return switch_menu_poll(&menu, NULL);
}

void switch_menu4_screenshot(void)
{
    char path[SWITCH_MENU_STATUS];
    switch_menu_capture(&menu, NULL, path, sizeof(path));
}
