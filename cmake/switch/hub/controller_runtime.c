#include "controller_runtime.h"

#include <string.h>

static ControllerProfile profile;
static bool remapping, overlay_mouse;
static uint32_t new_touch_buttons;
static struct {
    bool active;
    SDL_TouchID device;
    SDL_FingerID finger;
    int slot;
} fingers[16];

void switch_controls_load(const char *engine, const char *game_folder)
{
    HubGame game;
    const char *name = strrchr(game_folder, '/');
    const HubGame *selected = NULL;
    for (int i = 0; i < HUB_ENGINE_COUNT; ++i)
        if (!strcmp(hub_engines[i].id, engine) && hub_game_init(&game, i, name ? name + 1 : game_folder))
            selected = &game;
    controller_profile_load(HUB_ROOT, selected, &profile);
    remapping = profile.touch_enabled;
    for (unsigned i = 0; i < CP_BUTTONS; ++i)
        remapping |= profile.binding[i] != i;
    switch_controls_release_touch();
}

void switch_controls_release_touch(void)
{
    memset(fingers, 0, sizeof(fingers));
    new_touch_buttons = 0;
    overlay_mouse = false;
}

bool switch_controls_remapping(void)
{
    return remapping;
}

bool switch_controls_touch_enabled(void)
{
    return profile.touch_enabled;
}

static uint32_t held_touch_buttons(void)
{
    uint32_t held = 0;
    for (size_t i = 0; i < SDL_arraysize(fingers); ++i)
        if (fingers[i].active && fingers[i].slot >= 0)
            held |= 1u << profile.touch[fingers[i].slot].button;
    return held;
}

uint32_t switch_controls_buttons(uint32_t physical)
{
    uint32_t result = controller_profile_map(&profile, physical) | held_touch_buttons() | new_touch_buttons;
    new_touch_buttons = 0;
    return result;
}

uint32_t switch_controls_gamecontroller(SDL_GameController *controller)
{
    if (!controller)
        return 0;
    SDL_Joystick *joystick = SDL_GameControllerGetJoystick(controller);
    uint32_t result = 0;
    for (unsigned i = 0; i < CP_BUTTONS; ++i)
        if (SDL_JoystickGetButton(joystick, i))
            result |= switch_controls_joystick_button(i);
    return result;
}

uint32_t switch_controls_joystick_button(unsigned index)
{
    static const ControllerButton raw[] = {
        CP_A, CP_B, CP_X, CP_Y, CP_LSTICK, CP_RSTICK, CP_L, CP_R,
        CP_ZL, CP_ZR, CP_PLUS, CP_MINUS, CP_LEFT, CP_UP, CP_RIGHT, CP_DOWN
    };
    return index < SDL_arraysize(raw) ? 1u << raw[index] : 0;
}

static int touch_hit(float x, float y)
{
    // SDL's renderer clamps touches in the letterbox to the exact edge.
    return x > 0 && y > 0 ? controller_profile_hit(&profile, x, y) : -1;
}

bool switch_controls_touch(const SDL_Event *event)
{
    if (!profile.touch_enabled)
        return false;
    switch (event->type) {
    case SDL_APP_WILLENTERBACKGROUND:
    case SDL_APP_DIDENTERBACKGROUND:
        switch_controls_release_touch();
        return false;
    case SDL_MULTIGESTURE:
        for (size_t i = 0; i < SDL_arraysize(fingers); ++i)
            if (fingers[i].active && fingers[i].device == event->mgesture.touchId && fingers[i].slot >= 0)
                return true;
        return false;
    case SDL_FINGERDOWN:
    case SDL_FINGERMOTION:
    case SDL_FINGERUP:
        break;
    default:
        return false;
    }
    const SDL_TouchFingerEvent *touch = &event->tfinger;
    if (touch->touchId == SDL_MOUSE_TOUCHID)
        return false;
    size_t free_slot = SDL_arraysize(fingers);
    for (size_t i = 0; i < SDL_arraysize(fingers); ++i) {
        if (!fingers[i].active) {
            free_slot = i;
        } else if (fingers[i].device == touch->touchId && fingers[i].finger == touch->fingerId) {
            if (event->type == SDL_FINGERUP)
                fingers[i].active = false;
            return fingers[i].slot >= 0;
        }
    }
    if (event->type != SDL_FINGERDOWN || free_slot == SDL_arraysize(fingers))
        return false;
    int slot = touch_hit(touch->x, touch->y);
    fingers[free_slot].active = true;
    fingers[free_slot].device = touch->touchId;
    fingers[free_slot].finger = touch->fingerId;
    fingers[free_slot].slot = slot;
    if (slot >= 0)
        new_touch_buttons |= 1u << profile.touch[slot].button;
    return slot >= 0;
}

int switch_controls_game_fingers(const SDL_TouchFingerEvent *event)
{
    if (!profile.touch_enabled || event->touchId == SDL_MOUSE_TOUCHID)
        return SDL_GetNumTouchFingers(event->touchId);
    int count = 0;
    for (size_t i = 0; i < SDL_arraysize(fingers); ++i)
        if (fingers[i].active && fingers[i].device == event->touchId && fingers[i].slot < 0)
            ++count;
    return count;
}

bool switch_controls_gl_touch_event(const SDL_Event *event, SDL_Window *window, bool *redraw)
{
    if (!profile.touch_enabled || !window)
        return false;
    uint32_t held = held_touch_buttons();
    bool handled = switch_controls_touch(event);
    *redraw |= held != held_touch_buttons();
    if (handled)
        return true;
    // SDL sends the first finger's synthetic mouse down before the finger event.
    if (event->type == SDL_MOUSEBUTTONDOWN && event->button.which == SDL_TOUCH_MOUSEID) {
        int w, h;
        SDL_GetWindowSize(window, &w, &h);
        overlay_mouse = w > 0 && h > 0 &&
            touch_hit((float)event->button.x / w, (float)event->button.y / h) >= 0;
        return overlay_mouse;
    }
    if (event->type == SDL_MOUSEBUTTONUP && event->button.which == SDL_TOUCH_MOUSEID) {
        bool was_overlay = overlay_mouse;
        overlay_mouse = false;
        return was_overlay;
    }
    return event->type == SDL_MOUSEMOTION && event->motion.which == SDL_TOUCH_MOUSEID && overlay_mouse;
}

void switch_controls_draw(int width, int height, ControllerDrawRect draw, void *context)
{
    static const uint8_t glyph[CP_BUTTONS][5] = {
        {4, 14, 21, 4, 4}, {4, 4, 21, 14, 4}, {4, 8, 31, 8, 4}, {4, 2, 31, 2, 4},
        {14, 17, 31, 17, 17}, {30, 17, 30, 17, 30}, {17, 10, 4, 10, 17}, {17, 10, 4, 4, 4},
        {16, 16, 16, 16, 31}, {30, 17, 30, 18, 17}, {31, 2, 4, 8, 31}, {31, 2, 4, 8, 31},
        {0, 0, 31, 0, 0}, {0, 4, 31, 4, 0}, {16, 16, 16, 16, 31}, {30, 17, 30, 18, 17}
    };
    if (!profile.touch_enabled || width < CP_COLUMNS * 8 || height < CP_ROWS * 8)
        return;
    uint32_t held = held_touch_buttons();
    int w = width / CP_COLUMNS, h = height / CP_ROWS;
    int cell = w < h ? w : h;
    int inset = cell / 12;
    for (size_t i = 0; i < CP_TOUCH_BUTTONS; ++i) {
        ControllerTouchButton button = profile.touch[i];
        if (button.button >= CP_BUTTONS)
            continue;
        bool active = held & (1u << button.button);
        SDL_Rect rect = {width * button.column / CP_COLUMNS + inset,
                         height * button.row / CP_ROWS + inset, w - 2 * inset, h - 2 * inset};
        draw(context, &rect, active ? (SDL_Color){127, 206, 255, 240} : (SDL_Color){164, 185, 228, 205});
        rect.x += 2;
        rect.y += 2;
        rect.w -= 4;
        rect.h -= 4;
        draw(context, &rect, active ? (SDL_Color){32, 80, 120, 235} : (SDL_Color){21, 28, 49, 178});
        int scale = cell / 10;
        if (scale < 1)
            scale = 1;
        int x = rect.x + (rect.w - 5 * scale) / 2;
        int y = rect.y + (rect.h - 5 * scale) / 2;
        for (int row = 0; row < 5; ++row)
            for (int col = 0; col < 5; ++col)
                if (glyph[button.button][row] & (16 >> col)) {
                    SDL_Rect pixel = {x + col * scale, y + row * scale, scale, scale};
                    draw(context, &pixel, (SDL_Color){244, 248, 255, 255});
                }
    }
}

static void draw_sdl_rect(void *context, const SDL_Rect *rect, SDL_Color color)
{
    SDL_SetRenderDrawColor(context, color.r, color.g, color.b, color.a);
    SDL_RenderFillRect(context, rect);
}

void switch_controls_draw_sdl(SDL_Renderer *renderer)
{
    if (!profile.touch_enabled)
        return;
    int w, h;
    SDL_RenderGetLogicalSize(renderer, &w, &h);
    if (!w || !h)
        SDL_GetRendererOutputSize(renderer, &w, &h);
    SDL_Color previous;
    SDL_BlendMode blend;
    SDL_GetRenderDrawColor(renderer, &previous.r, &previous.g, &previous.b, &previous.a);
    SDL_GetRenderDrawBlendMode(renderer, &blend);
    SDL_SetRenderDrawBlendMode(renderer, SDL_BLENDMODE_BLEND);
    switch_controls_draw(w, h, draw_sdl_rect, renderer);
    SDL_SetRenderDrawBlendMode(renderer, blend);
    SDL_SetRenderDrawColor(renderer, previous.r, previous.g, previous.b, previous.a);
}
