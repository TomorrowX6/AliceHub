#ifndef SDL_MAIN_HANDLED
#define SDL_MAIN_HANDLED
#endif
#include <SDL.h>
#include <switch.h>
#include <ft2build.h>
#include FT_FREETYPE_H
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#define ROOT "sdmc:/switch/alicehub/" ALICEHUB_ENGINE
#define GAMES ROOT "/games"
#define SAVES ROOT "/saves"

enum { PICKER_WIDTH = 1280, PICKER_HEIGHT = 720, PICKER_ROWS = 18, PICKER_LINE = 31 };

typedef struct {
    Framebuffer fb;
    FT_Library library;
    FT_Face bundled[2];
    FT_Face shared[PlSharedFontType_Total];
    bool shared_attempted;
    bool shared_open;
    u16 *pixels;
    u32 stride;
} Picker;

int alicehub_switch_engine_main(int argc, char **argv);

static bool picker_load_fonts(Picker *picker)
{
    if (FT_Init_FreeType(&picker->library))
        return false;
#ifdef ALICEHUB_SWITCH_XSYSTEM4
    const char *font = "romfs:/fonts/VL-Gothic-Regular.ttf";
    if (!FT_New_Face(picker->library, "romfs:/fonts/HanaMinA.ttf", 0, &picker->bundled[1]))
        FT_Set_Pixel_Sizes(picker->bundled[1], 0, 24);
#else
    const char *font = "romfs:/fonts/MTLc3m.ttf";
#endif
    return !FT_New_Face(picker->library, font, 0, &picker->bundled[0]) &&
        !FT_Set_Pixel_Sizes(picker->bundled[0], 0, 24);
}

static FT_GlyphSlot picker_glyph(Picker *picker, u32 codepoint)
{
    for (size_t i = 0; i < sizeof(picker->bundled) / sizeof(*picker->bundled); ++i) {
        FT_Face face = picker->bundled[i];
        if (face && FT_Get_Char_Index(face, codepoint) && !FT_Load_Char(face, codepoint, FT_LOAD_RENDER))
            return face->glyph;
    }
    if (!picker->shared_attempted) {
        picker->shared_attempted = true;
        picker->shared_open = R_SUCCEEDED(plInitialize(PlServiceType_User));
        if (picker->shared_open) {
            for (int i = 0; i < PlSharedFontType_Total; ++i) {
                PlFontData font;
                if (R_SUCCEEDED(plGetSharedFontByType(&font, i)) &&
                    !FT_New_Memory_Face(picker->library, font.address, font.size, 0, &picker->shared[i]))
                    FT_Set_Pixel_Sizes(picker->shared[i], 0, 24);
            }
        }
    }
    for (int i = 0; i < PlSharedFontType_Total; ++i) {
        FT_Face face = picker->shared[i];
        if (face && FT_Get_Char_Index(face, codepoint) && !FT_Load_Char(face, codepoint, FT_LOAD_RENDER))
            return face->glyph;
    }
    FT_Face face = picker->bundled[0];
    return FT_Load_Char(face, '?', FT_LOAD_RENDER) ? NULL : face->glyph;
}

static void picker_text(Picker *picker, int x, int baseline, const char *text)
{
    while (*text && x < PICKER_WIDTH - 32) {
        u32 codepoint;
        ssize_t bytes = decode_utf8(&codepoint, (const u8 *)text);
        if (bytes <= 0) {
            codepoint = '?';
            bytes = 1;
        }
        text += bytes;
        if (codepoint < 0x20 || codepoint == 0x7f)
            codepoint = '?';
        FT_GlyphSlot glyph = picker_glyph(picker, codepoint);
        if (!glyph)
            continue;
        const FT_Bitmap *bitmap = &glyph->bitmap;
        if (bitmap->pixel_mode == FT_PIXEL_MODE_GRAY && bitmap->pitch >= 0) {
            for (unsigned row = 0; row < bitmap->rows; ++row) {
                int y = baseline - glyph->bitmap_top + (int)row;
                if (y < 0 || y >= PICKER_HEIGHT)
                    continue;
                u16 *dst = picker->pixels + y * picker->stride;
                const u8 *src = bitmap->buffer + row * bitmap->pitch;
                for (unsigned col = 0; col < bitmap->width; ++col) {
                    int px = x + glyph->bitmap_left + (int)col;
                    u8 shade = src[col];
                    if (px >= 0 && px < PICKER_WIDTH && shade)
                        dst[px] = RGB565_FROM_RGB8(shade, shade, shade);
                }
            }
        }
        x += glyph->advance.x >> 6;
    }
}

static void picker_begin(Picker *picker, bool clear)
{
    u32 stride;
    picker->pixels = framebufferBegin(&picker->fb, &stride);
    picker->stride = stride / sizeof(*picker->pixels);
    if (clear)
        memset(picker->pixels, 0, stride * PICKER_HEIGHT);
}

static void picker_close(Picker *picker)
{
    framebufferClose(&picker->fb);
    if (picker->library)
        FT_Done_FreeType(picker->library);
    if (picker->shared_open)
        plExit();
}

static int compare_names(const void *a, const void *b)
{
    return strcasecmp(*(const char *const *)a, *(const char *const *)b);
}

static int choose_game(Picker *picker, char *game, size_t size)
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
        // The linear framebuffer retains its pixels between presentations.
        picker_begin(picker, redraw);
        if (redraw) {
            picker_text(picker, 32, 40, "AliceHub / " ALICEHUB_ENGINE);
            picker_text(picker, 32, 74, "Up/Down: Select   A: Play   B/+: Exit");
            if (!count) {
                picker_text(picker, 32, 120, "Copy each game into its own folder under");
                picker_text(picker, 32, 151, GAMES);
            }
            size_t first = selected / PICKER_ROWS * PICKER_ROWS;
            for (size_t i = first; i < count && i < first + PICKER_ROWS; ++i) {
                int y = 120 + (int)(i - first) * PICKER_LINE;
                if (i == selected)
                    picker_text(picker, 32, y, ">");
                picker_text(picker, 56, y, names[i]);
            }
            redraw = 0;
        }
        framebufferEnd(&picker->fb);
        svcSleepThread(16000000);
    }
    for (size_t i = 0; i < count; ++i)
        free(names[i]);
    free(names);
    return accepted;
}

int main(void)
{
    char game[512], save[512];
    Picker picker = {0};
    if (R_FAILED(romfsInit()))
        return 1;
    NWindow *window = nwindowGetDefault();
    if (!picker_load_fonts(&picker) || !window ||
        R_FAILED(nwindowSetDimensions(window, PICKER_WIDTH, PICKER_HEIGHT)) ||
        R_FAILED(framebufferCreate(&picker.fb, window, PICKER_WIDTH, PICKER_HEIGHT, PIXEL_FORMAT_RGB_565, 2)) ||
        R_FAILED(framebufferMakeLinear(&picker.fb))) {
        picker_close(&picker);
        romfsExit();
        return 1;
    }
    if (!choose_game(&picker, game, sizeof(game))) {
        picker_close(&picker);
        romfsExit();
        return 0;
    }
    const char *name = strrchr(game, '/') + 1;
    struct stat st;
    if ((size_t)snprintf(save, sizeof(save), SAVES "/%s", name) >= sizeof(save) ||
        (mkdir(SAVES, 0777) && errno != EEXIST) ||
        (mkdir(save, 0777) && errno != EEXIST) || stat(save, &st) || !S_ISDIR(st.st_mode)) {
        PadState pad;
        padInitializeDefault(&pad);
        bool redraw = true;
        while (appletMainLoop()) {
            padUpdate(&pad);
            if (padGetButtonsDown(&pad) & (HidNpadButton_B | HidNpadButton_Plus))
                break;
            picker_begin(&picker, redraw);
            if (redraw) {
                picker_text(&picker, 32, 40, "Cannot create a writable save folder under");
                picker_text(&picker, 32, 74, SAVES);
                redraw = false;
            }
            framebufferEnd(&picker.fb);
            svcSleepThread(16000000);
        }
        picker_close(&picker);
        romfsExit();
        return 1;
    }

    // The picker and EGL share nvdrv. Keep it open while switching framebuffers.
    if (R_FAILED(nvInitialize())) {
        picker_close(&picker);
        romfsExit();
        return 1;
    }
    if (atexit(nvExit)) {
        nvExit();
        picker_close(&picker);
        romfsExit();
        return 1;
    }
    picker_close(&picker);

    SDL_SetMainReady();
#if defined(ALICEHUB_SWITCH_XSYSTEM4)
    setenv("XSYSTEM4_HOME", ROOT, 1);
    char *args[] = { ALICEHUB_ENGINE, "--save-folder", save, "--save-format=rsm", "--joypad", game, NULL };
#elif defined(ALICEHUB_SWITCH_XSYSTEM35)
    setenv("XSYSTEM35_SOUNDFONT", "romfs:/soundfonts/TimGM6mb.sf2", 1);
    char *args[] = { ALICEHUB_ENGINE, "-gamedir", game, "-savedir", save, NULL };
#else
    romfsExit(); // System 3 mounts its own ROMFS at entry.
    char *args[] = { ALICEHUB_ENGINE, "-gamedir", game, "-savedir", save, "-fullscreen", NULL };
#endif
    int result = alicehub_switch_engine_main(SDL_arraysize(args) - 1, args);
#if defined(ALICEHUB_SWITCH_XSYSTEM4) || defined(ALICEHUB_SWITCH_XSYSTEM35)
    romfsExit();
#endif
    return result;
}
