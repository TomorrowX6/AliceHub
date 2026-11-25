#ifndef ALICEHUB_PICKER_RENDERER_H
#define ALICEHUB_PICKER_RENDERER_H

#include <switch.h>
#include <ctype.h>
#include <ft2build.h>
#include FT_FREETYPE_H
#include <stdlib.h>
#include <string.h>
#ifdef ALICEHUB_PICKER_COMPRESSED_FONT_SIZE
#include <zlib.h>
#endif

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

static inline bool picker_load_fonts(Picker *picker)
{
    picker->text_size = 24;
    if (FT_Init_FreeType(&picker->library))
        return false;
#if defined(ALICEHUB_SWITCH_XSYSTEM4) || defined(ALICEHUB_SWITCH_HUB)
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

static inline FT_GlyphSlot picker_glyph(Picker *picker, u32 codepoint)
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

static inline void picker_font_size(Picker *picker, int size)
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

static inline u16 picker_rgb(unsigned color)
{
    return RGB565_FROM_RGB8((color >> 16) & 255, (color >> 8) & 255, color & 255);
}

static inline unsigned picker_mix(unsigned from, unsigned to, unsigned alpha)
{
    unsigned rb = ((from & 0xff00ff) * (255 - alpha) + (to & 0xff00ff) * alpha) >> 8;
    unsigned g = ((from & 0x00ff00) * (255 - alpha) + (to & 0x00ff00) * alpha) >> 8;
    return (rb & 0xff00ff) | (g & 0x00ff00);
}

static inline void picker_text(Picker *picker, int x, int baseline, int right, unsigned color, const char *text)
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

static inline void picker_begin(Picker *picker, bool clear)
{
    u32 stride;
    picker->pixels = framebufferBegin(&picker->fb, &stride);
    picker->stride = stride / sizeof(*picker->pixels);
    if (clear)
        memset(picker->pixels, 0, stride * PICKER_HEIGHT);
}

static inline void picker_label(Picker *picker, int x, int baseline, int right, int size, unsigned color, const char *text)
{
    picker_font_size(picker, size);
    picker_text(picker, x, baseline, right, color, text);
}

static inline int rounded_inset(int w, int h, int radius, int y)
{
    int dy = radius - (y < h / 2 ? y : h - y - 1);
    int inset = 0;
    if (dy > 0)
        while (inset < w / 2 && (radius - inset) * (radius - inset) + dy * dy > radius * radius)
            ++inset;
    return inset;
}

static inline void picker_panel(Picker *picker, int x, int y, int w, int h, int radius, unsigned color)
{
    u16 fill = picker_rgb(color);
    for (int row = 0; row < h; row++) {
        int inset = rounded_inset(w, h, radius, row);
        u16 *pixels = picker->pixels + (y + row) * picker->stride + x;
        for (int col = inset; col < w - inset; col++)
            pixels[col] = fill;
    }
}

static inline void picker_image(Picker *picker, int x, int y, int w, int h, int radius, const u16 *image)
{
    for (int row = 0; row < h; row++) {
        int inset = rounded_inset(w, h, radius, row);
        memcpy(picker->pixels + (y + row) * picker->stride + x + inset,
               image + row * w + inset, (w - 2 * inset) * sizeof(*image));
    }
}

static inline void picker_placeholder(Picker *picker, int x, int y, const char *name)
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

static inline void picker_hint(Picker *picker, int x, const char *key, const char *label, bool accent)
{
    picker_panel(picker, x, 652, 34, 34, 17, accent ? PRIMARY : VARIANT);
    picker_label(picker, x + 10, 676, x + 32, 18, accent ? ON_PRIMARY : INK, key);
    picker_label(picker, x + 44, 676, x + 240, 21, INK, label);
}

static inline void picker_close(Picker *picker)
{
    framebufferClose(&picker->fb);
    if (picker->library)
        FT_Done_FreeType(picker->library);
    free(picker->fallback_data);
    if (picker->shared_open)
        plExit();
}

static inline bool picker_open(Picker *picker)
{
    NWindow *window = nwindowGetDefault();
    return picker_load_fonts(picker) && window &&
        R_SUCCEEDED(nwindowSetDimensions(window, PICKER_WIDTH, PICKER_HEIGHT)) &&
        R_SUCCEEDED(framebufferCreate(&picker->fb, window, PICKER_WIDTH, PICKER_HEIGHT, PIXEL_FORMAT_RGB_565, 2)) &&
        R_SUCCEEDED(framebufferMakeLinear(&picker->fb));
}

#endif
