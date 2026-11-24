#ifndef SDL_MAIN_HANDLED
#define SDL_MAIN_HANDLED
#endif
#include <SDL.h>
#include <switch.h>
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#define ROOT "sdmc:/switch/alicehub/" ALICEHUB_ENGINE
#define GAMES ROOT "/games"
#define SAVES ROOT "/saves"

int alicehub_switch_engine_main(int argc, char **argv);

static int compare_names(const void *a, const void *b)
{
    return strcasecmp(*(const char *const *)a, *(const char *const *)b);
}

static int choose_game(char *game, size_t size)
{
    DIR *dir = opendir(GAMES);
    char **names = NULL;
    size_t count = 0;
    if (dir) {
        for (struct dirent *ent; (ent = readdir(dir));) {
            struct stat st;
            if (ent->d_name[0] == '.' ||
                (size_t)snprintf(game, size, GAMES "/%s", ent->d_name) >= size ||
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

    PadState pad;
    padConfigureInput(1, HidNpadStyleSet_NpadStandard);
    padInitializeDefault(&pad);
    size_t selected = 0;
    int accepted = 0;
    int redraw = 1;
    while (appletMainLoop()) {
        padUpdate(&pad);
        u64 keys = padGetButtonsDown(&pad);
        if (keys & (HidNpadButton_B | HidNpadButton_Plus))
            break;
        if (count && (keys & HidNpadButton_A)) {
            snprintf(game, size, GAMES "/%s", names[selected]);
            accepted = 1;
            break;
        }
        if (count && (keys & (HidNpadButton_Up | HidNpadButton_Down))) {
            selected = keys & HidNpadButton_Up ? (selected + count - 1) % count : (selected + 1) % count;
            redraw = 1;
        }
        if (redraw) {
            consoleClear();
            printf("AliceHub / %s\n\n", ALICEHUB_ENGINE);
            puts("Up/Down: Select   A: Play   B/+: Exit\n");
            if (!count)
                puts("Copy each game into its own folder under\n" GAMES);
            size_t first = selected / 18 * 18;
            for (size_t i = first; i < count && i < first + 18; ++i)
                printf("%c %s\n", i == selected ? '>' : ' ', names[i]);
            redraw = 0;
        }
        consoleUpdate(NULL);
    }
    for (size_t i = 0; i < count; ++i)
        free(names[i]);
    free(names);
    return accepted;
}

int main(void)
{
    char game[512], save[512];
    consoleInit(NULL);
    if (!choose_game(game, sizeof(game))) {
        consoleExit(NULL);
        return 0;
    }
    const char *name = strrchr(game, '/') + 1;
    struct stat st;
    if ((size_t)snprintf(save, sizeof(save), SAVES "/%s", name) >= sizeof(save) ||
        (mkdir(SAVES, 0777) && errno != EEXIST) ||
        (mkdir(save, 0777) && errno != EEXIST) || stat(save, &st) || !S_ISDIR(st.st_mode)) {
        printf("\nCannot create a writable save folder under\n" SAVES "\n");
        PadState pad;
        padInitializeDefault(&pad);
        while (appletMainLoop()) {
            padUpdate(&pad);
            if (padGetButtonsDown(&pad) & (HidNpadButton_B | HidNpadButton_Plus))
                break;
            consoleUpdate(NULL);
        }
        consoleExit(NULL);
        return 1;
    }

    // The software console and EGL share nvdrv. Keep it open while switching framebuffers.
    if (R_FAILED(nvInitialize())) {
        consoleExit(NULL);
        return 1;
    }
    if (atexit(nvExit)) {
        nvExit();
        consoleExit(NULL);
        return 1;
    }
    consoleExit(NULL);

    SDL_SetMainReady();
    // Use the Switch A/B labels instead of SDL's positional mapping.
    SDL_SetHint(SDL_HINT_GAMECONTROLLERCONFIG,
        "000038f853776974636820436f6e7400,Switch Controller,"
        "a:b0,b:b1,back:b11,dpdown:b15,dpleft:b12,dpright:b14,dpup:b13,"
        "leftshoulder:b6,leftstick:b4,lefttrigger:b8,leftx:a0,lefty:a1,"
        "rightshoulder:b7,rightstick:b5,righttrigger:b9,rightx:a2,righty:a3,"
        "start:b10,x:b3,y:b2,");
#if defined(ALICEHUB_SWITCH_XSYSTEM4)
    if (R_FAILED(romfsInit()))
        return 1;
    setenv("XSYSTEM4_HOME", ROOT, 1);
    char *args[] = { ALICEHUB_ENGINE, "--save-folder", save, "--save-format=rsm", "--joypad", game, NULL };
#elif defined(ALICEHUB_SWITCH_XSYSTEM35)
    if (R_FAILED(romfsInit()))
        return 1;
    setenv("XSYSTEM35_SOUNDFONT", "romfs:/soundfonts/TimGM6mb.sf2", 1);
    char *args[] = { ALICEHUB_ENGINE, "-gamedir", game, "-savedir", save, NULL };
#else
    char *args[] = { ALICEHUB_ENGINE, "-gamedir", game, "-savedir", save, "-fullscreen", NULL };
#endif
    int result = alicehub_switch_engine_main(SDL_arraysize(args) - 1, args);
#if defined(ALICEHUB_SWITCH_XSYSTEM4) || defined(ALICEHUB_SWITCH_XSYSTEM35)
    romfsExit();
#endif
    return result;
}
