#ifndef SDL_MAIN_HANDLED
#define SDL_MAIN_HANDLED
#endif
#include <SDL.h>
#include <switch.h>
#include <ctype.h>
#include <ft2build.h>
#include FT_FREETYPE_H
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include "launcher.h"
#include "picker_art.h"
#ifdef ALICEHUB_PICKER_COMPRESSED_FONT_SIZE
#include <zlib.h>
#endif

#define ROOT "sdmc:/switch/alicehub/" ALICEHUB_ENGINE
#define GAMES ROOT "/games"
#define SAVES ROOT "/saves"

enum {
    PICKER_WIDTH = 1280, PICKER_HEIGHT = 720, CARD_COUNT = 4,
    CARD_WIDTH = 274, ART_WIDTH = 250, ART_HEIGHT = 194,
};

enum {
    INK = 0xECEEF4, MUTED = 0xA4AAB8, PRIMARY = 0xA9B6FF,
    ON_PRIMARY = 0x0F1A5C, SURFACE = 0x181B21, VARIANT = 0x242832,
    CHIP = 0x2B3568, OUTLINE = 0x383F4F, WARN = 0xFFCB6B,
};

typedef struct {
    Framebuffer fb;
    FT_Library library;
    FT_Face bundled[2];
    unsigned char *fallback_data;
    FT_Face shared[PlSharedFontType_Total];
    bool shared_attempted;
    bool shared_open;
    int text_size;
    u16 *pixels;
    u32 stride;
} Picker;

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

static bool picker_load_fonts(Picker *picker)
{
    picker->text_size = 24;
    if (FT_Init_FreeType(&picker->library))
        return false;
#ifdef ALICEHUB_SWITCH_XSYSTEM4
    const char *font = "romfs:/fonts/VL-Gothic-Regular.ttf";
#else
    const char *font = "romfs:/fonts/MTLc3m.ttf";
#endif
    if (FT_New_Face(picker->library, font, 0, &picker->bundled[0]) ||
        FT_Set_Pixel_Sizes(picker->bundled[0], 0, 24))
        return false;

#ifdef ALICEHUB_PICKER_COMPRESSED_FONT_SIZE
    gzFile compressed = gzopen("romfs:/fonts/HanaMinA.ttf.gz", "rb");
    if (compressed) {
        const int size = ALICEHUB_PICKER_COMPRESSED_FONT_SIZE;
        unsigned char *data = malloc(size);
        int bytes = data ? gzread(compressed, data, size) : -1;
        if (gzclose(compressed) == Z_OK && bytes == size &&
            !FT_New_Memory_Face(picker->library, data, size, 0, &picker->bundled[1]))
            picker->fallback_data = data;
        else
            free(data);
    }
#else
    FT_New_Face(picker->library, "romfs:/fonts/HanaMinA.ttf", 0, &picker->bundled[1]);
#endif
    if (picker->bundled[1])
        FT_Set_Pixel_Sizes(picker->bundled[1], 0, 24);
    return true;
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
                    FT_Set_Pixel_Sizes(picker->shared[i], 0, picker->text_size);
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

static void picker_font_size(Picker *picker, int size)
{
    if (picker->text_size == size)
        return;
    picker->text_size = size;
    for (size_t i = 0; i < sizeof(picker->bundled) / sizeof(*picker->bundled); ++i)
        if (picker->bundled[i])
            FT_Set_Pixel_Sizes(picker->bundled[i], 0, size);
    for (int i = 0; i < PlSharedFontType_Total; ++i)
        if (picker->shared[i])
            FT_Set_Pixel_Sizes(picker->shared[i], 0, size);
}

static u16 picker_rgb(unsigned color)
{
    return RGB565_FROM_RGB8((color >> 16) & 255, (color >> 8) & 255, color & 255);
}

static unsigned picker_mix(unsigned from, unsigned to, unsigned alpha)
{
    unsigned rb = ((from & 0xff00ff) * (255 - alpha) + (to & 0xff00ff) * alpha) >> 8;
    unsigned g = ((from & 0x00ff00) * (255 - alpha) + (to & 0x00ff00) * alpha) >> 8;
    return (rb & 0xff00ff) | (g & 0x00ff00);
}

static void picker_text(Picker *picker, int x, int baseline, int right, unsigned color, const char *text)
{
    while (*text && x < right) {
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
        bool cropped = x + (glyph->advance.x >> 6) + (*text ? 12 : 0) > right;
        if (cropped && !(glyph = picker_glyph(picker, 0x2026)))
            break;
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
                    if (px >= 0 && px < right && px < PICKER_WIDTH && shade) {
                        u16 bg = dst[px];
                        unsigned background = ((bg & 0xf800u) << 8) | ((bg & 0x07e0u) << 5) | ((bg & 0x001fu) << 3);
                        dst[px] = picker_rgb(shade == 255 ? color : picker_mix(background, color, shade));
                    }
                }
            }
        }
        x += glyph->advance.x >> 6;
        if (cropped)
            break;
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

static void picker_label(Picker *picker, int x, int baseline, int right, int size, unsigned color, const char *text)
{
    picker_font_size(picker, size);
    picker_text(picker, x, baseline, right, color, text);
}

static int rounded_inset(int w, int h, int radius, int y)
{
    int dy = radius - (y < h / 2 ? y : h - y - 1);
    int inset = 0;
    if (dy > 0)
        while (inset < w / 2 && (radius - inset) * (radius - inset) + dy * dy > radius * radius)
            ++inset;
    return inset;
}

static void picker_panel(Picker *picker, int x, int y, int w, int h, int radius, unsigned color)
{
    u16 fill = picker_rgb(color);
    for (int row = 0; row < h; row++) {
        int inset = rounded_inset(w, h, radius, row);
        u16 *pixels = picker->pixels + (y + row) * picker->stride + x;
        for (int col = inset; col < w - inset; col++)
            pixels[col] = fill;
    }
}

static void picker_image(Picker *picker, int x, int y, int w, int h, int radius, const u16 *image)
{
    for (int row = 0; row < h; row++) {
        int inset = rounded_inset(w, h, radius, row);
        memcpy(picker->pixels + (y + row) * picker->stride + x + inset,
               image + row * w + inset, (w - 2 * inset) * sizeof(*image));
    }
}

static void picker_placeholder(Picker *picker, int x, int y, const char *name)
{
    static const unsigned tones[][2] = {
        { 0x483B88, 0x211B45 }, { 0x246152, 0x143533 },
        { 0x743F59, 0x3A223D }, { 0x345D87, 0x192D4E },
    };
    unsigned hash = 0;
    for (const unsigned char *p = (const unsigned char *)name; *p; ++p)
        hash = hash * 31 + *p;
    const unsigned *tone = tones[hash % 4];
    for (int row = 0; row < ART_HEIGHT; ++row) {
        int inset = rounded_inset(ART_WIDTH, ART_HEIGHT, 16, row);
        unsigned gradient = picker_mix(tone[0], tone[1], row * 255 / ART_HEIGHT);
        u16 normal = picker_rgb(gradient), ring = picker_rgb(picker_mix(gradient, PRIMARY, 32));
        u16 *pixels = picker->pixels + (y + row) * picker->stride + x;
        for (int col = inset; col < ART_WIDTH - inset; ++col) {
            int dx = col - 187, dy = row - 65;
            int d2 = dx * dx + dy * dy;
            pixels[col] = (d2 >= 70 * 70 && d2 <= 72 * 72) ||
                          (d2 >= 100 * 100 && d2 <= 102 * 102) ? ring : normal;
        }
    }
    const u8 *initial = (const u8 *)name;
    u32 cp;
    ssize_t bytes = decode_utf8(&cp, initial);
    char monogram[5] = "?";
    if (bytes > 0 && bytes <= 4) {
        memcpy(monogram, initial, bytes);
        monogram[bytes] = '\0';
        if (bytes == 1)
            monogram[0] = toupper((unsigned char)monogram[0]);
    }
    picker_label(picker, x + 22, y + 136, x + ART_WIDTH - 20, 78, INK, monogram);
}

static void picker_hint(Picker *picker, int x, const char *key, const char *label, bool accent)
{
    picker_panel(picker, x, 652, 34, 34, 17, accent ? PRIMARY : VARIANT);
    picker_label(picker, x + 10, 676, x + 32, 18, accent ? ON_PRIMARY : INK, key);
    picker_label(picker, x + 44, 676, x + 240, 21, INK, label);
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

static void picker_close(Picker *picker)
{
    framebufferClose(&picker->fb);
    if (picker->library)
        FT_Done_FreeType(picker->library);
    free(picker->fallback_data);
    if (picker->shared_open)
        plExit();
}

static int compare_names(const void *a, const void *b)
{
    return strcasecmp(*(const char *const *)a, *(const char *const *)b);
}

static const char *validate_game(const char *game)
{
    DIR *dir = opendir(game);
    if (!dir)
        return "Cannot open the game folder";
    bool found = false;
    for (struct dirent *ent = readdir(dir); ent; ent = readdir(dir)) {
        const char *name = ent->d_name;
#if defined(ALICEHUB_SWITCH_XSYSTEM4)
        if (strcasecmp(name, "System40.ini") && strcasecmp(name, "AliceStart.ini") &&
            strcasecmp(name, "AliceStart2.ini") && strcasecmp(name, "AliceStart.CHS"))
            continue;
#elif defined(ALICEHUB_SWITCH_XSYSTEM35)
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
#if !defined(ALICEHUB_SWITCH_XSYSTEM4) && !defined(ALICEHUB_SWITCH_XSYSTEM35)
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
#if defined(ALICEHUB_SWITCH_XSYSTEM4)
    return "No readable System40.ini or AliceStart configuration";
#elif defined(ALICEHUB_SWITCH_XSYSTEM35)
    return "No readable scenario ALD or xsystem35.gr";
#else
    return "Missing or invalid System 3 scenario disk (ADISK.DAT, GDISK.DAT, or AGAME.DAT)";
#endif
}

static int choose_game(Picker *picker, char *game, size_t size, const char *initial_error)
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
    while (appletMainLoop()) {
        padUpdate(&pad);
        u64 keys = padGetButtonsDown(&pad);
        if (keys & (HidNpadButton_B | HidNpadButton_Plus))
            break;
        padRepeaterUpdate(&repeater, padGetButtons(&pad) & directions);
        keys |= padRepeaterGetButtons(&repeater);
        if (count && (keys & HidNpadButton_A)) {
            if (error) {
                error = NULL;
            } else {
                snprintf(game, size, GAMES "/%s", names[selected]);
                error = validate_game(game);
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

static bool picker_open(Picker *picker)
{
    NWindow *window = nwindowGetDefault();
    return picker_load_fonts(picker) && window &&
        R_SUCCEEDED(nwindowSetDimensions(window, PICKER_WIDTH, PICKER_HEIGHT)) &&
        R_SUCCEEDED(framebufferCreate(&picker->fb, window, PICKER_WIDTH, PICKER_HEIGHT, PIXEL_FORMAT_RGB_565, 2)) &&
        R_SUCCEEDED(framebufferMakeLinear(&picker->fb));
}

static bool prepare_save(const char *game, char *save, size_t size)
{
    const char *name = strrchr(game, '/') + 1;
    struct stat st;
    return (size_t)snprintf(save, size, SAVES "/%s", name) < size &&
        (!mkdir(SAVES, 0777) || errno == EEXIST) &&
        (!mkdir(save, 0777) || errno == EEXIST) && !stat(save, &st) && S_ISDIR(st.st_mode);
}

static int launch_game(char *game, char *save)
{
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
    return alicehub_switch_engine_main(SDL_arraysize(args) - 1, args);
}

int main(void)
{
    char game[512], save[512];
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
        while ((selected = choose_game(&picker, game, sizeof(game), *launch_error ? launch_error : NULL)) &&
               !prepare_save(game, save, sizeof(save)))
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
        result = launch_game(game, save);
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
