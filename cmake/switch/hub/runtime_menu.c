#include "runtime_menu.h"
#include "runtime_capture.h"
#include "controller_runtime.h"

#include <stdio.h>
#include <switch.h>
#include <ft2build.h>
#include FT_FREETYPE_H

#define CAPTURE_DIR "sdmc:/switch/alicehub/" ALICEHUB_ENGINE "/screenshots"

enum { PANEL_W = 640, PANEL_H = 360, VISIBLE_ROWS = 6 };
enum { MENU_RESUME = -1, MENU_CAPTURE = -2, MENU_MODIFIERS = -3 };
enum { UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8, ACCEPT = 16, BACK = 32, OPEN = 64 };

typedef struct {
    bool modifiers;
    size_t focus;
    size_t top;
    char status[SWITCH_MENU_STATUS];
} MenuPage;

typedef struct {
    unsigned keys;
    bool neutral;
} MenuInput;

static bool has_chord(u64 buttons, u64 chord)
{
    return (buttons & chord) == chord;
}

static MenuInput read_input(bool capture_input)
{
    static PadState pad;
    static bool initialized;
    if (!initialized) {
        padInitializeDefault(&pad);
        initialized = true;
    }
    padUpdate(&pad);
    u64 buttons = padGetButtons(&pad);
    u64 up = HidNpadButton_Up | HidNpadButton_StickLUp;
    u64 down = HidNpadButton_Down | HidNpadButton_StickLDown;
    u64 left = HidNpadButton_Left | HidNpadButton_StickLLeft;
    u64 right = HidNpadButton_Right | HidNpadButton_StickLRight;
    u64 accept = HidNpadButton_A, back = HidNpadButton_B;
    u32 style = padGetStyleSet(&pad);
    u64 chord = HidNpadButton_L | HidNpadButton_R;
    // Match SDL's positional mapping for one sideways Joy-Con.
    if (style == HidNpadStyleTag_NpadJoyLeft) {
        up = HidNpadButton_StickLRight;
        down = HidNpadButton_StickLLeft;
        left = HidNpadButton_StickLUp;
        right = HidNpadButton_StickLDown;
        accept = HidNpadButton_Left;
        back = HidNpadButton_Down;
        chord = HidNpadButton_LeftSL | HidNpadButton_LeftSR;
    } else if (style == HidNpadStyleTag_NpadJoyRight) {
        up = HidNpadButton_StickRLeft;
        down = HidNpadButton_StickRRight;
        left = HidNpadButton_StickRDown;
        right = HidNpadButton_StickRUp;
        accept = HidNpadButton_A;
        back = HidNpadButton_X;
        chord = HidNpadButton_RightSL | HidNpadButton_RightSR;
    }
    int num_keys;
    const Uint8 *key = SDL_GetKeyboardState(&num_keys);
    unsigned keys = 0;
    if ((buttons & up) || key[SDL_SCANCODE_UP]) keys |= UP;
    if ((buttons & down) || key[SDL_SCANCODE_DOWN]) keys |= DOWN;
    if ((buttons & left) || key[SDL_SCANCODE_LEFT]) keys |= LEFT;
    if ((buttons & right) || key[SDL_SCANCODE_RIGHT]) keys |= RIGHT;
    if ((buttons & accept) || key[SDL_SCANCODE_RETURN]) keys |= ACCEPT;
    if ((buttons & back) || key[SDL_SCANCODE_ESCAPE]) keys |= BACK;
    if (has_chord(buttons, chord) || key[SDL_SCANCODE_F1]) keys |= OPEN;
    bool neutral = capture_input && !buttons && !keys && !SDL_GetMouseState(NULL, NULL);
    if (neutral) {
        for (int i = 0; i < num_keys && neutral; ++i)
            neutral = !key[i];
        for (int i = 0, n = SDL_GetNumTouchDevices(); i < n && neutral; ++i)
            neutral = SDL_GetNumTouchFingers(SDL_GetTouchDevice(i)) <= 0;
    }
    return (MenuInput){keys, neutral};
}

static int SDLCALL keep_engine_event(void *unused, SDL_Event *event)
{
    (void)unused;
    Uint32 type = event->type;
    return !((type >= SDL_KEYDOWN && type <= SDL_MOUSEWHEEL) ||
             (type >= SDL_JOYAXISMOTION && type <= SDL_JOYBUTTONUP) ||
             (type >= SDL_CONTROLLERAXISMOTION && type <= SDL_CONTROLLERBUTTONUP) ||
             (type >= SDL_FINGERDOWN && type <= SDL_FINGERMOTION));
}

bool switch_menu_lifecycle_pending(void)
{
    return SDL_HasEvent(SDL_QUIT) || SDL_HasEvent(SDL_APP_WILLENTERBACKGROUND) ||
           SDL_HasEvent(SDL_APP_DIDENTERBACKGROUND) || SDL_HasEvent(SDL_RENDER_DEVICE_RESET);
}

bool switch_menu_capture(const SwitchMenu *menu, void *context, char *filename, size_t size)
{
    return switch_capture_write(CAPTURE_DIR, menu->capture_extension, time(NULL),
                                menu->capture, context, filename, size);
}

static void fill(SDL_Surface *panel, int x, int y, int w, int h, Uint32 rgb)
{
    SDL_Rect rect = {x, y, w, h};
    SDL_FillRect(panel, &rect, SDL_MapRGBA(panel->format, rgb >> 16, rgb >> 8, rgb, 255));
}

static void text(SDL_Surface *panel, FT_Face face, int x, int y, int limit, int size,
                 Uint32 rgb, const char *str)
{
    FT_Set_Pixel_Sizes(face, 0, size);
    for (; *str && x < limit; ++str) {
        if (FT_Load_Char(face, (unsigned char)*str, FT_LOAD_RENDER))
            continue;
        FT_GlyphSlot glyph = face->glyph;
        FT_Bitmap *bmp = &glyph->bitmap;
        for (unsigned j = 0; j < bmp->rows; ++j) {
            int py = y - glyph->bitmap_top + (int)j;
            if (py < 0 || py >= panel->h)
                continue;
            Uint8 *pixels = (Uint8 *)panel->pixels + py * panel->pitch;
            for (unsigned i = 0; i < bmp->width; ++i) {
                int px = x + glyph->bitmap_left + (int)i;
                if (px < 0 || px >= limit)
                    continue;
                unsigned a = bmp->buffer[(ptrdiff_t)j * bmp->pitch + i];
                for (unsigned k = 0; k < 3; ++k) {
                    Uint8 *p = &pixels[px * 4 + k];
                    unsigned color = (rgb >> (16 - 8 * k)) & 255;
                    *p = (*p * (255 - a) + color * a) / 255;
                }
            }
        }
        x += glyph->advance.x >> 6;
    }
}

static size_t build_rows(const SwitchMenu *menu, void *context, bool modifiers, SwitchMenuRow *rows)
{
    if (modifiers)
        return menu->options(context, true, rows);
    size_t n = 0;
    rows[n++] = (SwitchMenuRow){.label = "Resume game", .action = MENU_RESUME};
    rows[n++] = (SwitchMenuRow){.label = "Save screenshot to SD", .action = MENU_CAPTURE};
    SwitchMenuRow extra[SWITCH_MENU_ROWS] = {0};
    size_t count = menu->options(context, false, extra);
    for (size_t i = 0; i < count && n < SWITCH_MENU_ROWS - 1; ++i)
        rows[n++] = extra[i];
    if (menu->options(context, true, extra))
        rows[n++] = (SwitchMenuRow){.label = "Live modifiers", .action = MENU_MODIFIERS};
    return n;
}

static void draw(SDL_Surface *panel, FT_Face face, const MenuPage *page,
                 const SwitchMenuRow *rows, size_t count)
{
    fill(panel, 0, 0, PANEL_W, PANEL_H, 0x111827);
    fill(panel, 0, 0, PANEL_W, 72, 0x18233a);
    text(panel, face, 28, 25, 612, 14, 0x93ace0, "ALICEHUB  /  " ALICEHUB_ENGINE);
    text(panel, face, 28, 56, 612, 27, 0xf5f7ff,
         page->modifiers ? "Live modifiers" : "Game menu");
    for (size_t i = page->top; i < count && i < page->top + VISIBLE_ROWS; ++i) {
        int y = 82 + (int)(i - page->top) * 36;
        fill(panel, 22, y, 590, 32, i == page->focus ? 0x2a4777 : 0x1a2538);
        text(panel, face, 34, y + 22, 464, 19, 0xf5f7ff, rows[i].label);
        text(panel, face, 470, y + 22, 600, 17, 0xafcdfd, rows[i].value);
    }
    if (count > VISIBLE_ROWS) {
        int thumb = VISIBLE_ROWS * 212 / (int)count;
        fill(panel, 619, 82, 3, 212, 0x273650);
        fill(panel, 619, 82 + (212 - thumb) * (int)page->top / (int)(count - VISIBLE_ROWS),
             3, thumb, 0x8daef0);
    }
    text(panel, face, 28, 321, 612, 13, 0xb7d5ff, page->status);
    text(panel, face, 28, 346, 612, 15, 0xb7c3d9,
         "Up/Down: Move    Left/Right: Adjust    A: Select    B: Back");
}

bool switch_menu_present_sdl(SDL_Renderer *renderer, SDL_Surface *panel)
{
    SDL_Texture *texture = SDL_CreateTextureFromSurface(renderer, panel);
    if (!texture)
        return false;
    bool ok = SDL_RenderCopy(renderer, texture, NULL, NULL) == 0;
    if (ok)
        SDL_RenderPresent(renderer);
    SDL_DestroyTexture(texture);
    return ok;
}

bool switch_menu_poll(const SwitchMenu *menu, void *context)
{
    static bool open_held;
    MenuInput input = read_input(false);
    bool pressed = (input.keys & OPEN) && !open_held;
    open_held = (input.keys & OPEN) != 0;
    if (!pressed || switch_menu_lifecycle_pending())
        return false;

    FT_Library library;
    FT_Face face;
    if (FT_Init_FreeType(&library))
        return false;
    if (FT_New_Face(library, menu->font, 0, &face)) {
        FT_Done_FreeType(library);
        return false;
    }
    SDL_Surface *panel = SDL_CreateRGBSurfaceWithFormat(0, PANEL_W, PANEL_H, 32, SDL_PIXELFORMAT_RGBA32);
    bool shown = false;
    if (!panel)
        goto done;

    MenuPage page = {0};
    SwitchMenuRow rows[SWITCH_MENU_ROWS] = {0};
    size_t count = 0, parent_focus = 0, parent_top = 0;
    bool armed = false, dirty = true, closing = false;
    unsigned previous = input.keys;
    while (!switch_menu_lifecycle_pending()) {
        if (dirty && !closing) {
            count = build_rows(menu, context, page.modifiers, rows);
            if (page.focus >= count)
                page.focus = count ? count - 1 : 0;
            draw(panel, face, &page, rows, count);
            if (!menu->present(context, panel))
                break;
            shown = true;
            dirty = false;
        }
        SDL_Delay(16);
        SDL_PumpEvents();
        SDL_FilterEvents(keep_engine_event, NULL);
        input = read_input(true);
        if (switch_menu_lifecycle_pending())
            break;
        if (closing) {
            if (input.neutral)
                break;
            continue;
        }
        if (!armed) {
            armed = input.neutral;
            previous = input.keys;
            continue;
        }
        unsigned edge = input.keys & ~previous;
        previous = input.keys;
        if (!edge)
            continue;
        if (edge & OPEN) {
            closing = true;
        } else if (edge & BACK) {
            if (!page.modifiers)
                closing = true;
            else {
                page = (MenuPage){.focus = parent_focus, .top = parent_top};
                dirty = true;
            }
        } else if ((edge & UP) && page.focus > 0) {
            if (--page.focus < page.top)
                --page.top;
            dirty = true;
        } else if ((edge & DOWN) && page.focus + 1 < count) {
            if (++page.focus >= page.top + VISIBLE_ROWS)
                ++page.top;
            dirty = true;
        } else if (count && ((edge & ACCEPT) || ((edge & (LEFT | RIGHT)) && rows[page.focus].adjustable))) {
            int action = rows[page.focus].action;
            page.status[0] = 0;
            if (action == MENU_RESUME) {
                closing = true;
            } else if (action == MENU_MODIFIERS) {
                parent_focus = page.focus;
                parent_top = page.top;
                page = (MenuPage){.modifiers = true};
            } else if (action == MENU_CAPTURE) {
                if (!switch_menu_capture(menu, context, page.status, sizeof(page.status)))
                    snprintf(page.status, sizeof(page.status), "Couldn't save screenshot to SD.");
            } else {
                menu->activate(context, action, (edge & LEFT) ? -1 : 1, page.status);
            }
            dirty = true;
        }
    }
    open_held = (input.keys & OPEN) != 0;
    SDL_FreeSurface(panel);
done:
    FT_Done_Face(face);
    FT_Done_FreeType(library);
    if (shown)
        switch_controls_release_touch();
    return shown;
}
