#ifndef SDL_MAIN_HANDLED
#define SDL_MAIN_HANDLED
#endif
#include <SDL.h>
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include "hub/model.h"
#include "hub/fs_util.h"
#include "hub/controller_runtime.h"
#include "launcher.h"
#include "picker_art.h"
#include "picker_renderer.h"

#define ROOT "sdmc:/switch/alicehub/" ALICEHUB_ENGINE
#define GAMES ROOT "/games"
#define SAVES ROOT "/saves"

int alicehub_switch_engine_main(int argc, char **argv);

static char launch_error[512];

void alicehub_switch_set_launch_error(const char *message)
{
    size_t length = strcspn(message, "\r\n");
    if (length)
        snprintf(launch_error, sizeof(launch_error), "%.*s", (int)length, message);
    else
        snprintf(launch_error, sizeof(launch_error), "Engine startup failed");
}

static void picker_draw(Picker *picker, char **names, size_t count, size_t selected,
                        const u16 *logo, u16 *const covers[CARD_COUNT],
                        const char *error_game, const char *error)
{
    for (int y = 0; y < PICKER_HEIGHT; ++y)
        picker_panel(picker, 0, y, PICKER_WIDTH, 1, 0, picker_mix(0x171C2E, 0x0E1014, y * 255 / PICKER_HEIGHT));
    picker_panel(picker, 0, 0, PICKER_WIDTH, 106, 0, SURFACE);
    picker_panel(picker, 0, 106, PICKER_WIDTH, 1, 0, OUTLINE);
    if (logo)
        picker_image(picker, 64, 23, 58, 58, 15, logo);
    picker_label(picker, 141, 65, 470, 36, INK, "AliceHub");
    picker_panel(picker, 944, 32, 272, 42, 21, CHIP);
    picker_label(picker, 969, 60, 1202, 21, PRIMARY, ALICEHUB_ENGINE "  /  SWITCH");

    picker_label(picker, 64, 151, 450, 17, PRIMARY, "LOCAL LIBRARY");
    picker_label(picker, 64, 203, 825, 44, INK, "Your games");
    if (count) {
        char position[64];
        snprintf(position, sizeof(position), "%zu of %zu", selected + 1, count);
        picker_label(picker, 1070, 200, 1216, 21, MUTED, position);
        size_t first = selected / CARD_COUNT * CARD_COUNT;
        for (size_t i = first; i < count && i < first + CARD_COUNT; ++i) {
            int x = 64 + (int)(i - first) * (CARD_WIDTH + 18);
            bool focus = i == selected;
            picker_panel(picker, x, 242, CARD_WIDTH, 334, 22, focus ? PRIMARY : OUTLINE);
            picker_panel(picker, x + 3, 245, CARD_WIDTH - 6, 328, 19, focus ? 0x252C47 : SURFACE);
            if (covers[i - first])
                picker_image(picker, x + 12, 254, ART_WIDTH, ART_HEIGHT, 16, covers[i - first]);
            else
                picker_placeholder(picker, x + 12, 254, names[i]);
            picker_label(picker, x + 18, 492, x + CARD_WIDTH - 15, 25, INK, names[i]);
            if (focus) {
                picker_panel(picker, x + 17, 518, 117, 34, 17, CHIP);
                picker_label(picker, x + 31, 542, x + 129, 17, PRIMARY, "SELECTED");
            } else {
                picker_label(picker, x + 18, 542, x + CARD_WIDTH - 15, 17, MUTED, "LOCAL GAME");
            }
        }
    } else {
        picker_panel(picker, 64, 242, 1152, 334, 22, OUTLINE);
        picker_panel(picker, 65, 243, 1150, 332, 21, SURFACE);
        picker_panel(picker, 596, 272, 88, 88, 23, CHIP);
        picker_panel(picker, 615, 300, 23, 12, 4, PRIMARY);
        picker_panel(picker, 615, 308, 50, 29, 6, PRIMARY);
        picker_label(picker, 484, 408, 900, 32, INK, "Your library is empty");
        picker_label(picker, 398, 447, 1080, 22, MUTED, "Copy each game into its own folder here:");
        picker_panel(picker, 288, 471, 704, 54, 15, VARIANT);
        picker_label(picker, 316, 505, 960, 21, PRIMARY, GAMES);
    }
    picker_panel(picker, 0, 628, PICKER_WIDTH, 1, 0, OUTLINE);
    picker_panel(picker, 0, 629, PICKER_WIDTH, 91, 0, SURFACE);
    if (count) {
        picker_panel(picker, 64, 652, 92, 34, 17, VARIANT);
        picker_label(picker, 78, 676, 150, 18, INK, "D-pad");
        picker_label(picker, 170, 676, 450, 21, MUTED, "Browse games");
        picker_hint(picker, 930, "A", error ? "Dismiss" : "Start", true);
    }
    picker_hint(picker, 1110, "B", "Exit", false);

    if (error) {
        for (int y = 107; y < 628; ++y) {
            u16 *row = picker->pixels + y * picker->stride;
            for (int x = 0; x < PICKER_WIDTH; ++x)
                row[x] = (row[x] & 0xf7de) >> 1;
        }
        picker_panel(picker, 222, 242, 836, 286, 23, OUTLINE);
        picker_panel(picker, 224, 244, 832, 282, 21, VARIANT);
        picker_panel(picker, 258, 274, 43, 43, 14, 0x3A2A0A);
        picker_label(picker, 274, 306, 295, 27, WARN, "!");
        picker_label(picker, 318, 305, 1022, 28, WARN, "Unable to start game");
        picker_label(picker, 259, 365, 1020, 32, INK, error_game);
        picker_label(picker, 259, 410, 1020, 21, MUTED, error);
        picker_panel(picker, 258, 450, 177, 46, 16, PRIMARY);
        picker_label(picker, 279, 479, 415, 20, ON_PRIMARY, "A   Dismiss");
    }
}

static int compare_names(const void *a, const void *b)
{
    return strcasecmp(*(const char *const *)a, *(const char *const *)b);
}

static const char *resolve_game(const char *game, char target[HUB_PATH_SIZE], bool *uses_dsr)
{
#if defined(ALICEHUB_SWITCH_XSYSTEM4)
    return hub_system4_resolve(game, target, uses_dsr) ? NULL : "No readable System 4 launcher or script";
#else
    strcpy(target, game);
    *uses_dsr = false;
    DIR *dir = opendir(game);
    if (!dir)
        return "Cannot open the game folder";
    bool found = false;
    for (struct dirent *ent = readdir(dir); ent; ent = readdir(dir)) {
        const char *name = ent->d_name;
#if defined(ALICEHUB_SWITCH_XSYSTEM35)
        if (strcasecmp(name, "xsystem35.gr") && strcasecmp(name, "adisk.ald")) {
            size_t len = strlen(name);
            size_t suffix = len >= 10 && !strcasecmp(name + len - 8, "_chs.ald") ? 8 : 4;
            if (len < suffix + 2 || strcasecmp(name + len - 4, ".ald") ||
                toupper((unsigned char)name[len - suffix - 2]) != 'S' ||
                toupper((unsigned char)name[len - suffix - 1]) < 'A' ||
                toupper((unsigned char)name[len - suffix - 1]) > 'Z')
                continue;
        }
#else
        if (strcasecmp(name, "ADISK.DAT") && strcasecmp(name, "GDISK.DAT") && strcasecmp(name, "AGAME.DAT"))
            continue;
#endif
        char path[768];
        struct stat st;
        if ((size_t)snprintf(path, sizeof(path), "%s/%s", game, name) >= sizeof(path) ||
            stat(path, &st) || !S_ISREG(st.st_mode))
            continue;
        FILE *fp = fopen(path, "rb");
        if (!fp)
            continue;
#if !defined(ALICEHUB_SWITCH_XSYSTEM35)
        unsigned char header[4];
        if (fread(header, 1, sizeof(header), fp) == sizeof(header)) {
            int link = header[0] | header[1] << 8;
            int data = header[2] | header[3] << 8;
            found = link > 0 && data > link && (data - 1) * 256L < st.st_size;
        }
#else
        found = fgetc(fp) != EOF;
#endif
        fclose(fp);
        if (found)
            break;
    }
    closedir(dir);
    if (found)
        return NULL;
#if defined(ALICEHUB_SWITCH_XSYSTEM35)
    return "No readable scenario ALD or xsystem35.gr";
#else
    return "Missing or invalid System 3 scenario disk (ADISK.DAT, GDISK.DAT, or AGAME.DAT)";
#endif
#endif
}

static int choose_game(Picker *picker, char game[HUB_PATH_SIZE], char target[HUB_PATH_SIZE],
                       bool *uses_dsr, const char *initial_error)
{
    char error_game[512];
    const char *error = initial_error;
    if (error)
        snprintf(error_game, sizeof(error_game), "%s", strrchr(game, '/') + 1);
    DIR *dir = opendir(GAMES);
    char **names = NULL;
    size_t count = 0;
    if (dir) {
        for (struct dirent *ent; (ent = readdir(dir));) {
            struct stat st;
            if (ent->d_name[0] == '.' ||
                (size_t)snprintf(game, HUB_PATH_SIZE, GAMES "/%s", ent->d_name) >= HUB_PATH_SIZE ||
                stat(game, &st) || !S_ISDIR(st.st_mode))
                continue;
            char *name = strdup(ent->d_name);
            char **next = name ? realloc(names, (count + 1) * sizeof(*names)) : NULL;
            if (!next) {
                free(name);
                break;
            }
            names = next;
            names[count++] = name;
        }
        closedir(dir);
    }
    if (count > 1)
        qsort(names, count, sizeof(*names), compare_names);

    const u64 directions = HidNpadButton_Up | HidNpadButton_Down | HidNpadButton_Left | HidNpadButton_Right;
    PadState pad;
    PadRepeater repeater;
    padConfigureInput(1, HidNpadStyleSet_NpadStandard);
    padInitializeDefault(&pad);
    padRepeaterInitialize(&repeater, 18, 6);
    size_t selected = 0;
    for (size_t i = 0; error && i < count; ++i)
        if (!strcmp(names[i], error_game)) {
            selected = i;
            break;
        }
    u16 *logo = picker_art_load("romfs:/alicehub.png", 58, 58);
    u16 *covers[CARD_COUNT] = {0};
    size_t page = selected / CARD_COUNT * CARD_COUNT, loaded = 0;
    int accepted = 0;
    bool redraw = true;
    bool wait_for_a_release = initial_error != NULL;
    while (appletMainLoop()) {
        padUpdate(&pad);
        u64 keys = padGetButtonsDown(&pad);
        u64 held = padGetButtons(&pad);
        if (keys & (HidNpadButton_B | HidNpadButton_Plus))
            break;
        if (wait_for_a_release) {
            keys &= ~HidNpadButton_A;
            wait_for_a_release = (held & HidNpadButton_A) != 0;
        }
        padRepeaterUpdate(&repeater, held & directions);
        keys |= padRepeaterGetButtons(&repeater);
        if (count && (keys & HidNpadButton_A)) {
            if (error) {
                error = NULL;
            } else {
                snprintf(game, HUB_PATH_SIZE, GAMES "/%s", names[selected]);
                error = resolve_game(game, target, uses_dsr);
                if (!error) {
                    accepted = 1;
                    break;
                }
                snprintf(error_game, sizeof(error_game), "%s", names[selected]);
            }
            redraw = true;
        }
        if (count && (keys & directions)) {
            size_t next = keys & (HidNpadButton_Up | HidNpadButton_Left)
                ? (selected + count - 1) % count : (selected + 1) % count;
            redraw |= selected != next || error != NULL;
            selected = next;
            error = NULL;
        }
        size_t first = selected / CARD_COUNT * CARD_COUNT;
        if (first != page) {
            for (size_t i = 0; i < loaded; ++i) {
                free(covers[i]);
                covers[i] = NULL;
            }
            page = first;
            loaded = 0;
        }
        if (!redraw && loaded < CARD_COUNT && first + loaded < count) {
            char path[512];
            if ((size_t)snprintf(path, sizeof(path), GAMES "/%s", names[first + loaded]) < sizeof(path)) {
                covers[loaded] = picker_art_cover(path, ART_WIDTH, ART_HEIGHT);
                redraw = covers[loaded] != NULL;
            }
            ++loaded;
        }
        // The linear framebuffer retains its pixels between presentations.
        picker_begin(picker, false);
        if (redraw) {
            picker_draw(picker, names, count, selected, logo, covers, error_game, error);
            redraw = false;
        }
        framebufferEnd(&picker->fb);
        svcSleepThread(16000000);
    }
    free(logo);
    for (size_t i = 0; i < loaded; ++i)
        free(covers[i]);
    for (size_t i = 0; i < count; ++i)
        free(names[i]);
    free(names);
    return accepted;
}

static bool ensure_save_dir(const char *path)
{
    struct stat st;
    return (!mkdir(path, 0777) || errno == EEXIST) && !lstat(path, &st) && S_ISDIR(st.st_mode);
}

static bool prepare_save(const char *game, const char *target, char *save, size_t size)
{
    const char *name = strrchr(game, '/') + 1;
    char key[HUB_PATH_SIZE];
#if defined(ALICEHUB_SWITCH_XSYSTEM4)
    HubGame checked;
    if (!hub_game_init(&checked, HUB_XSYSTEM4, name) ||
        !hub_system4_save_key(checked.name, game, target, key))
        return false;
#else
    (void)target;
    if ((size_t)snprintf(key, sizeof(key), "%s", name) >= sizeof(key))
        return false;
#endif
    if ((size_t)snprintf(save, size, SAVES "/%s", key) >= size || !ensure_save_dir(SAVES))
        return false;
    for (char *slash = strchr(save + sizeof(SAVES), '/'); slash; slash = strchr(slash + 1, '/')) {
        *slash = '\0';
        bool ok = ensure_save_dir(save);
        *slash = '/';
        if (!ok)
            return false;
    }
    return hub_save_recover(save) && ensure_save_dir(save);
}

static int launch_game(const char *game, char *target, char *save)
{
    SDL_SetMainReady();
    switch_controls_load(ALICEHUB_ENGINE, game);
#if defined(ALICEHUB_SWITCH_XSYSTEM4)
    setenv("XSYSTEM4_HOME", ROOT, 1);
    char *args[] = { ALICEHUB_ENGINE, "--save-folder", save, "--save-format=rsm", "--joypad", target, NULL };
#elif defined(ALICEHUB_SWITCH_XSYSTEM35)
    setenv("XSYSTEM35_SOUNDFONT", "romfs:/soundfonts/TimGM6mb.sf2", 1);
    char *args[] = { ALICEHUB_ENGINE, "-gamedir", target, "-savedir", save, NULL };
#else
    romfsExit(); // System 3 mounts its own ROMFS at entry.
    char literal_save[HUB_PATH_SIZE + 1];
    snprintf(literal_save, sizeof(literal_save), "%s/", save);
    char *args[] = { ALICEHUB_ENGINE, "-gamedir", target, "-savedir", literal_save, "-fullscreen", NULL };
#endif
    return alicehub_switch_engine_main(SDL_arraysize(args) - 1, args);
}

static int standalone_main(void)
{
    char game[HUB_PATH_SIZE], target[HUB_PATH_SIZE], save[HUB_PATH_SIZE];
    bool uses_dsr;
    if (R_FAILED(romfsInit()))
        return 1;
    bool retained_nv = false;
    int result = 1;
    for (;;) {
        Picker picker = {0};
        if (!picker_open(&picker)) {
            picker_close(&picker);
            break;
        }
        bool selected;
        while ((selected = choose_game(&picker, game, target, &uses_dsr, *launch_error ? launch_error : NULL)) &&
               !prepare_save(game, target, save, sizeof(save)))
            alicehub_switch_set_launch_error("Cannot create a writable save folder under " SAVES);
        if (!selected) {
            picker_close(&picker);
            result = 0;
            break;
        }
        // The picker and EGL share nvdrv. Keep it open while switching framebuffers.
        if (!retained_nv) {
            if (R_FAILED(nvInitialize())) {
                picker_close(&picker);
                break;
            }
            if (atexit(nvExit)) {
                picker_close(&picker);
                nvExit();
                break;
            }
            retained_nv = true;
        }
        picker_close(&picker);
        *launch_error = '\0';
        result = launch_game(game, target, save);
#if !defined(ALICEHUB_SWITCH_XSYSTEM4) && !defined(ALICEHUB_SWITCH_XSYSTEM35)
        if (!*launch_error)
            return result;
        if (R_FAILED(romfsInit()))
            return result;
#endif
        if (!*launch_error)
            break;
        SDL_Quit();
    }
    romfsExit();
    return result;
}

static HubHandoff request;

static void return_to_hub(void)
{
    if (!envHasNextLoad() || access(HUB_NRO, R_OK) != 0)
        return;
    snprintf(request.error, sizeof(request.error), "%s", launch_error);
    if (!hub_handoff_write(HUB_ROOT, true, &request))
        hub_handoff_clear(HUB_ROOT, true);
    if (R_FAILED(envSetNextLoad(HUB_NRO, HUB_NRO " " HUB_RETURN_ARG)))
        hub_handoff_clear(HUB_ROOT, true);
}

int main(int argc, char **argv)
{
    if (argc != 2 || strcmp(argv[1], HUB_REQUEST_ARG))
        return standalone_main();
    if (!hub_handoff_take(HUB_ROOT, false, &request) ||
        strcmp(hub_engines[request.game.engine].id, ALICEHUB_ENGINE))
        return standalone_main();
    if (atexit(return_to_hub))
        return 1;
    char game[HUB_PATH_SIZE], target[HUB_PATH_SIZE], save[HUB_PATH_SIZE];
    if (!hub_game_path(HUB_ROOT, &request.game, game)) {
        alicehub_switch_set_launch_error("Game path is too long");
        return 1;
    }
    bool uses_dsr;
    const char *reason = resolve_game(game, target, &uses_dsr);
    if (reason) {
        alicehub_switch_set_launch_error(reason);
        return 1;
    }
    if (!prepare_save(game, target, save, sizeof(save))) {
        alicehub_switch_set_launch_error("Cannot create a writable save folder under " SAVES);
        return 1;
    }
    if (R_FAILED(romfsInit())) {
        alicehub_switch_set_launch_error("Cannot open engine resources");
        return 1;
    }
    return launch_game(game, target, save);
}
