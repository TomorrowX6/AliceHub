#!/usr/bin/env python3
"""Dependency-free native regression tests for Android synthetic touch-drag input.

Compiles the actual C implementations from xsystem4/src/input.c against minimal
SDL/input fixtures to verify:
1. Non-PartsEngine polling loops (e.g., Rance 9 pre-battle party drag using
   IbisInputEngine/SACT2 Key_IsDown + Mouse_GetPos while PE_Update runs without
   BeginInput) mark synthetic touch presses as observed so mouse_get_pos()
   tracks finger motion instead of freezing at touchdown coordinates.
2. Quick touch drags whose lift occurs before the next frame or before the 50ms
   synthetic press delay expires still expose the final release coordinates
   while VK_LBUTTON/VK_RBUTTON remains pressed prior to release.
3. PartsEngine.BeginInput (allow_press=true) quick taps and drags remain
   unchanged (hover -> press -> release).
4. Pure non-Parts loops without PE_UpdateInputState and RBUTTON/multi-touch reset
   paths preserve correct synthetic cursor tracking.
5. Disabling Parts input releases observed touches promptly without enabling new
   presses or consuming an unobserved press.

Run: python3 xsystem4/test/native/test_touch_drag_regressions.py
"""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def extract_function(rel_path: str, name: str) -> str:
    source = (ROOT / rel_path).read_text(encoding="utf-8")
    pattern = re.compile(
        rf"^(?:static\s+)?[A-Za-z_][A-Za-z0-9_\s\*]*\b{re.escape(name)}\([^;{{}}]*\)\s*\r?\n?\{{",
        re.MULTILINE,
    )
    match = pattern.search(source)
    if not match:
        raise ValueError(f"Function definition not found: {name} in {rel_path}")
    start = match.start()
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def compile_and_run(c_source: str) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "test.c"
        exe = Path(tmp) / "test_bin"
        src.write_text(c_source, encoding="utf-8")
        subprocess.run(
            ["cc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2", str(src), "-o", str(exe)],
            check=True,
        )
        subprocess.run([str(exe)], check=True)


def build_input_harness(body: str) -> str:
    fns = "\n\n".join([
        extract_function("src/input.c", "input_parts_poll_begin"),
        extract_function("src/input.c", "input_notify_parts_poll"),
        extract_function("src/input.c", "key_is_down"),
        extract_function("src/input.c", "key_clear_flag"),
        extract_function("src/input.c", "mouse_get_pos"),
        extract_function("src/input.c", "mouse_set_pos"),
        extract_function("src/input.c", "synthetic_mouse_event"),
        extract_function("src/input.c", "pop_deferred_synthetic_mouse_event"),
        extract_function("src/input.c", "fire_deferred_events"),
        extract_function("src/input.c", "enqueue_synthetic_mouse_button"),
    ])
    return f"""
#include <assert.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

#define min(a, b) ((a) < (b) ? (a) : (b))
#define max(a, b) ((a) > (b) ? (a) : (b))

enum sact_keycode {{
    VK_LBUTTON = 0x01,
    VK_RBUTTON = 0x02,
    VK_MBUTTON = 0x04,
    VK_CONTROL = 0x11,
    VK_NR_KEYCODES = 256,
}};

enum {{
    SDL_RELEASED = 0,
    SDL_PRESSED = 1,
    SDL_BUTTON_LEFT = 1,
    SDL_BUTTON_MIDDLE = 2,
    SDL_BUTTON_RIGHT = 3,
    SDL_MOUSEBUTTONDOWN = 0x401,
    SDL_MOUSEBUTTONUP = 0x402,
    SDL_TOUCH_MOUSEID = 0xFFFFFFFFu,
}};

typedef struct {{ int x, y; }} SDL_Point;
typedef struct {{ int x, y, w, h; }} SDL_Rect;
typedef struct {{
    uint32_t type;
    uint32_t timestamp;
    uint32_t which;
    uint8_t button;
    uint8_t state;
    uint8_t clicks;
    int x;
    int y;
}} SDL_MouseButtonEvent;

struct sdl_private {{
    void *window;
    int w, h;
    SDL_Rect viewport;
}};

static struct sdl_private sdl = {{
    .window = (void *)0x1,
    .w = 1280,
    .h = 720,
    .viewport = {{ 0, 0, 1280, 720 }},
}};

#define MAYBE_UNUSED __attribute__((unused))

static uint32_t fake_ticks = 1000;
static int fake_sdl_mouse_x = 0;
static int fake_sdl_mouse_y = 0;
MAYBE_UNUSED static int warp_calls = 0;

MAYBE_UNUSED static uint32_t SDL_GetTicks(void) {{ return fake_ticks; }}
MAYBE_UNUSED static void SDL_PumpEvents(void) {{}}
MAYBE_UNUSED static uint32_t SDL_GetMouseState(int *x, int *y) {{
    if (x) *x = fake_sdl_mouse_x;
    if (y) *y = fake_sdl_mouse_y;
    return 0;
}}
static void SDL_WarpMouseInWindow(void *window, int x, int y) {{
    (void)window;
    warp_calls++;
    fake_sdl_mouse_x = x;
    fake_sdl_mouse_y = y;
}}
static bool SDL_PointInRect(const SDL_Point *p, const SDL_Rect *r) {{
    return p->x >= r->x && p->x < r->x + r->w && p->y >= r->y && p->y < r->y + r->h;
}}

static bool key_state[VK_NR_KEYCODES];
static bool kbd_key_state[VK_NR_KEYCODES];
static bool vpad_vk_down[VK_NR_KEYCODES];
static bool pad_vk_down[VK_NR_KEYCODES];
static bool sdl_mouse_down[VK_NR_KEYCODES];
static bool prev_gamepad_mouse[VK_NR_KEYCODES];
static bool suppressed_movie_keys[VK_NR_KEYCODES];
static bool kbd_ctrl_down = false;
static atomic_bool touch_ctrl_skip = false;
static bool gamepad_cursor_active = false;
static bool vpad_cursor_btn_down = false;

static bool movie_has_active_playback(void) {{ return false; }}
static bool is_movie_skip_suppressed_key(enum sact_keycode code) {{ (void)code; return false; }}

#define MAX_DEFERRED_SYNTHETIC_MOUSE_EVENTS 32
static SDL_MouseButtonEvent deferred_synthetic_mouse_events[MAX_DEFERRED_SYNTHETIC_MOUSE_EVENTS];
static int nr_deferred_synthetic_mouse_events = 0;
static bool synthetic_mouse_hover_observed = false;
static bool synthetic_mouse_down_observed = false;
static bool synthetic_mouse_up_observed = true;
static bool has_synthetic_mouse_pos = false;
static int synthetic_mouse_x = 0;
static int synthetic_mouse_y = 0;
static bool has_last_touch_down_pos = false;
static SDL_Point last_touch_down_pos = {{0, 0}};
static uint32_t last_parts_poll_timestamp = 0;
static bool in_parts_poll = false;
static bool mouse_down_unobserved[VK_NR_KEYCODES];
static uint32_t mouse_down_timestamp[VK_NR_KEYCODES];
static bool deferred_mouse_release[VK_NR_KEYCODES];
#define DEFERRED_MOUSE_RELEASE_MAX_DELAY 100
#define SYNTHETIC_MOUSE_EVENT_DELAY 50
#define SYNTHETIC_MOUSE_RELEASE_FALLBACK_DELAY 250

static void fire_deferred_events(bool from_parts_poll, bool allow_press);

static void handle_events(void) {{
    static const enum sact_keycode mouse_btns[] = {{ VK_LBUTTON, VK_RBUTTON, VK_MBUTTON }};
    uint32_t poll_now = SDL_GetTicks();
    for (size_t i = 0; i < sizeof(mouse_btns) / sizeof(mouse_btns[0]); i++) {{
        enum sact_keycode code = mouse_btns[i];
        if (deferred_mouse_release[code] &&
            (!mouse_down_unobserved[code] || (poll_now - mouse_down_timestamp[code]) >= DEFERRED_MOUSE_RELEASE_MAX_DELAY)) {{
            key_state[code] = false;
            deferred_mouse_release[code] = false;
            mouse_down_unobserved[code] = false;
        }}
    }}
    fire_deferred_events(false, false);
}}

{fns}

static void sim_touch_down_btn(int x, int y, uint8_t button) {{
    fake_sdl_mouse_x = x;
    fake_sdl_mouse_y = y;
    last_touch_down_pos.x = x;
    last_touch_down_pos.y = y;
    has_last_touch_down_pos = true;
    SDL_MouseButtonEvent ev = {{
        .type = SDL_MOUSEBUTTONDOWN,
        .which = SDL_TOUCH_MOUSEID,
        .button = button,
        .state = SDL_PRESSED,
        .clicks = 1,
        .x = x,
        .y = y,
    }};
    enqueue_synthetic_mouse_button(&ev);
}}

MAYBE_UNUSED static void sim_touch_down(int x, int y) {{
    sim_touch_down_btn(x, y, SDL_BUTTON_LEFT);
}}

MAYBE_UNUSED static void sim_touch_move(int x, int y) {{
    fake_sdl_mouse_x = x;
    fake_sdl_mouse_y = y;
}}

MAYBE_UNUSED static void sim_touch_up_btn(int x, int y, uint8_t button) {{
    fake_sdl_mouse_x = x;
    fake_sdl_mouse_y = y;
    int ex = x, ey = y;
    if (has_last_touch_down_pos) {{
        int dx = abs(ex - last_touch_down_pos.x);
        int dy = abs(ey - last_touch_down_pos.y);
        int snap_dist = max(16, min(sdl.viewport.w, sdl.viewport.h) / 40);
        if (dx <= snap_dist && dy <= snap_dist) {{
            ex = last_touch_down_pos.x;
            ey = last_touch_down_pos.y;
        }}
        has_last_touch_down_pos = false;
    }}
    if (nr_deferred_synthetic_mouse_events == 0 && !key_state[VK_LBUTTON] && !key_state[VK_RBUTTON]) {{
        return;
    }}
    SDL_MouseButtonEvent ev = {{
        .type = SDL_MOUSEBUTTONUP,
        .which = SDL_TOUCH_MOUSEID,
        .button = button,
        .state = SDL_RELEASED,
        .clicks = 1,
        .x = ex,
        .y = ey,
    }};
    enqueue_synthetic_mouse_button(&ev);
}}

MAYBE_UNUSED static void sim_touch_up(int x, int y) {{
    sim_touch_up_btn(x, y, SDL_BUTTON_LEFT);
}}

MAYBE_UNUSED static void sim_pe_update(bool allow_press, int *out_x, int *out_y, bool *out_clicking) {{
    input_parts_poll_begin(allow_press);
    int x = 0, y = 0;
    mouse_get_pos(&x, &y);
    bool clicking = key_is_down(VK_LBUTTON) || key_is_down(VK_RBUTTON);
    input_notify_parts_poll(allow_press, clicking);
    if (out_x) *out_x = x;
    if (out_y) *out_y = y;
    if (out_clicking) *out_clicking = clicking;
}}

static void reset_test_state(void) {{
    key_clear_flag(false);
    nr_deferred_synthetic_mouse_events = 0;
    synthetic_mouse_hover_observed = false;
    synthetic_mouse_down_observed = false;
    synthetic_mouse_up_observed = true;
    has_synthetic_mouse_pos = false;
    has_last_touch_down_pos = false;
    last_parts_poll_timestamp = 0;
    in_parts_poll = false;
    gamepad_cursor_active = false;
    vpad_cursor_btn_down = false;
    fake_ticks += 2000;
}}

int main(void) {{
{body}
    return 0;
}}
"""


class TouchDragRegressionTests(unittest.TestCase):
    def test_rance9_party_drag_tracks_pointer_with_pe_update_without_begin_input(self) -> None:
        c_code = build_input_harness("""
    reset_test_state();

    // Idle frames with PE_Update(allow_press=false), matching Rance 9 T_Party.
    for (int i = 0; i < 3; i++) {
        fake_ticks += 16;
        sim_pe_update(false, NULL, NULL, NULL);
        (void)key_is_down(VK_LBUTTON);
    }

    sim_touch_down(300, 500);
    bool pressed = false;
    for (int f = 0; f < 6; f++) {
        fake_ticks += 16;
        sim_pe_update(false, NULL, NULL, NULL);
        if (key_is_down(VK_LBUTTON)) {
            pressed = true;
            break;
        }
    }
    assert(pressed);

    // T_DragMouse@start reads mouse_get_pos without calling mouse_set_pos.
    int sx = 0, sy = 0;
    mouse_get_pos(&sx, &sy);
    assert(sx == 300 && sy == 500);

    // Drag across multiple frames while finger stays down.
    const int pts[3][2] = { {450, 420}, {600, 350}, {700, 300} };
    for (int i = 0; i < 3; i++) {
        fake_ticks += 16;
        sim_touch_move(pts[i][0], pts[i][1]);
        sim_pe_update(false, NULL, NULL, NULL);
        int mx = 0, my = 0;
        mouse_get_pos(&mx, &my);
        assert(key_is_down(VK_LBUTTON));
        assert(mx == pts[i][0] && my == pts[i][1]);
    }

    sim_touch_up(700, 300);
    bool released = false;
    for (int f = 0; f < 25; f++) {
        fake_ticks += 16;
        sim_pe_update(false, NULL, NULL, NULL);
        if (!key_is_down(VK_LBUTTON)) {
            released = true;
            break;
        }
    }
    assert(released);
""")
        compile_and_run(c_code)

    def test_rance9_quick_drag_and_sub50ms_swipe_expose_drop_coords_while_pressed(self) -> None:
        c_code = build_input_harness("""
    // Case A: Final move + lift arrive together between frames during an active drag.
    reset_test_state();
    sim_touch_down(300, 500);
    for (int f = 0; f < 6; f++) {
        fake_ticks += 16;
        sim_pe_update(false, NULL, NULL, NULL);
        if (key_is_down(VK_LBUTTON))
            break;
    }
    assert(key_is_down(VK_LBUTTON));

    fake_ticks += 16;
    sim_touch_move(520, 380);
    sim_pe_update(false, NULL, NULL, NULL);
    int mx = 0, my = 0;
    mouse_get_pos(&mx, &my);
    assert(mx == 520 && my == 380);
    (void)key_is_down(VK_LBUTTON);

    fake_ticks += 16;
    sim_touch_move(700, 300);
    sim_touch_up(700, 300);
    sim_pe_update(false, NULL, NULL, NULL);
    // While still pressed on the drag frame before fallback release pops, coordinates reach (700, 300).
    mouse_get_pos(&mx, &my);
    assert(key_is_down(VK_LBUTTON));
    assert(mx == 700 && my == 300);
    fake_ticks += 260;
    sim_pe_update(false, NULL, NULL, NULL);
    assert(!key_is_down(VK_LBUTTON));

    // Case B: Ultra-fast <50ms swipe + lift before deferred PRESSED fires.
    reset_test_state();
    sim_touch_down(300, 500);
    fake_ticks += 20;
    sim_touch_move(700, 300);
    sim_touch_up(700, 300);
    sim_pe_update(false, NULL, NULL, NULL);
    assert(!key_is_down(VK_LBUTTON));

    fake_ticks += 40;
    sim_pe_update(false, NULL, NULL, NULL);
    assert(key_is_down(VK_LBUTTON));
    mouse_get_pos(&mx, &my);
    assert(mx == 700 && my == 300);
    fake_ticks += 260;
    sim_pe_update(false, NULL, NULL, NULL);
    assert(!key_is_down(VK_LBUTTON));
""")
        compile_and_run(c_code)

    def test_non_parts_loop_without_pe_update_tracks_drag_and_fast_lift(self) -> None:
        c_code = build_input_harness("""
    reset_test_state();
    sim_touch_down(200, 400);
    fake_ticks += 60;
    handle_events();
    assert(key_is_down(VK_LBUTTON));

    fake_ticks += 16;
    sim_touch_move(500, 250);
    handle_events();
    int mx = 0, my = 0;
    mouse_get_pos(&mx, &my);
    assert(key_is_down(VK_LBUTTON));
    assert(mx == 500 && my == 250);

    // Final move + lift in fallback delay window (<250ms): pos still updates while held.
    fake_ticks += 16;
    sim_touch_move(640, 220);
    sim_touch_up(640, 220);
    handle_events();
    mouse_get_pos(&mx, &my);
    assert(key_is_down(VK_LBUTTON));
    assert(mx == 640 && my == 220);

    fake_ticks += 260;
    handle_events();
    assert(!key_is_down(VK_LBUTTON));
""")
        compile_and_run(c_code)

    def test_parts_engine_begin_input_quick_tap_and_drag_preserved(self) -> None:
        c_code = build_input_harness("""
    // Quick <50ms tap with BeginInput=true must deliver hover -> press -> release across 3 frames.
    reset_test_state();
    sim_touch_down(400, 300);
    fake_ticks += 16;
    sim_touch_up(404, 302); // snaps back to (400, 300)

    int x1 = 0, y1 = 0;
    bool c1 = false;
    sim_pe_update(true, &x1, &y1, &c1);
    assert(!c1 && x1 == 400 && y1 == 300);

    int x2 = 0, y2 = 0;
    bool c2 = false;
    fake_ticks += 16;
    sim_pe_update(true, &x2, &y2, &c2);
    assert(c2 && x2 == 400 && y2 == 300);

    bool c3 = true;
    fake_ticks += 16;
    sim_pe_update(true, NULL, NULL, &c3);
    assert(!c3);

    // Touch drag with BeginInput=true also tracks finger motion while held.
    reset_test_state();
    sim_touch_down(150, 180);
    fake_ticks += 16;
    sim_pe_update(true, &x1, &y1, &c1);
    assert(!c1 && x1 == 150 && y1 == 180);
    fake_ticks += 16;
    sim_pe_update(true, &x2, &y2, &c2);
    assert(c2);
    fake_ticks += 16;
    sim_touch_move(510, 420);
    sim_pe_update(true, &x2, &y2, &c2);
    assert(c2 && x2 == 510 && y2 == 420);
    sim_touch_up(510, 420);
    fake_ticks += 16;
    sim_pe_update(true, &x2, &y2, &c2);
    assert(!c2);
""")
        compile_and_run(c_code)

    def test_parts_input_disabled_releases_observed_touches_promptly(self) -> None:
        c_code = build_input_harness("""
    const uint8_t buttons[] = { SDL_BUTTON_LEFT, SDL_BUTTON_RIGHT };
    const enum sact_keycode keys[] = { VK_LBUTTON, VK_RBUTTON };
    for (int b = 0; b < 2; b++) {
        for (int moved = 0; moved < 2; moved++) {
            reset_test_state();
            sim_touch_down_btn(300, 500, buttons[b]);
            fake_ticks += 16;
            sim_pe_update(true, NULL, NULL, NULL); // hover
            fake_ticks += 16;
            sim_pe_update(true, NULL, NULL, NULL); // observed press
            assert(key_is_down(keys[b]));
            assert(synthetic_mouse_down_observed);

            // EndInput disables new presses, not release of this observed press.
            int drop_x = moved ? 700 : 300;
            int drop_y = moved ? 300 : 500;
            sim_touch_up_btn(drop_x, drop_y, buttons[b]);
            uint32_t lifted_at = fake_ticks;
            fake_ticks += 16;
            int x = 0, y = 0;
            sim_pe_update(false, &x, &y, NULL);
            assert(x == drop_x && y == drop_y);
            if (moved) {
                // Preserve one pressed frame at the final drag coordinates.
                assert(key_is_down(keys[b]));
                fake_ticks += 16;
                sim_pe_update(false, &x, &y, NULL);
            }
            assert(!key_is_down(keys[b]));
            assert(nr_deferred_synthetic_mouse_events == 0);
            assert(fake_ticks - lifted_at <= 32);
            assert(fake_ticks - last_parts_poll_timestamp < 500);

            // A queued new touch must still not become pressed while disabled.
            sim_touch_down_btn(120, 180, buttons[b]);
            for (int f = 0; f < 4; f++) {
                fake_ticks += 16;
                sim_pe_update(false, NULL, NULL, NULL);
                assert(!key_is_down(keys[b]));
                assert(nr_deferred_synthetic_mouse_events == 1);
            }
            // Re-enabling still follows hover -> press, with no stale release.
            sim_pe_update(true, NULL, NULL, NULL);
            assert(!key_is_down(keys[b]));
            fake_ticks += 16;
            sim_pe_update(true, NULL, NULL, NULL);
            assert(key_is_down(keys[b]));
        }
    }
""")
        compile_and_run(c_code)

    def test_parts_input_disabled_does_not_release_an_unobserved_press(self) -> None:
        c_code = build_input_harness("""
    reset_test_state();
    sim_touch_down(300, 500);
    fake_ticks += 16;
    sim_pe_update(true, NULL, NULL, NULL); // hover
    fake_ticks += 16;
    input_parts_poll_begin(true); // press delivered, not yet observed by caller
    assert(key_state[VK_LBUTTON]);
    assert(!synthetic_mouse_down_observed);
    input_notify_parts_poll(false, true);
    sim_touch_up(300, 500);
    for (int f = 0; f < 3; f++) {
        fake_ticks += 16;
        sim_pe_update(false, NULL, NULL, NULL);
        assert(key_state[VK_LBUTTON]);
        assert(!synthetic_mouse_down_observed);
        assert(nr_deferred_synthetic_mouse_events == 1);
    }
    // An enabled poll observes the press before the next poll releases it.
    sim_pe_update(true, NULL, NULL, NULL);
    assert(synthetic_mouse_down_observed);
    fake_ticks += 16;
    sim_pe_update(false, NULL, NULL, NULL);
    assert(!key_state[VK_LBUTTON]);
    assert(nr_deferred_synthetic_mouse_events == 0);
""")
        compile_and_run(c_code)

    def test_rbutton_drag_and_multitouch_cancel_reset(self) -> None:
        c_code = build_input_harness("""
    reset_test_state();
    sim_touch_down_btn(320, 240, SDL_BUTTON_RIGHT);
    fake_ticks += 60;
    sim_pe_update(false, NULL, NULL, NULL);
    assert(key_is_down(VK_RBUTTON));

    fake_ticks += 16;
    sim_touch_move(480, 360);
    sim_pe_update(false, NULL, NULL, NULL);
    int mx = 0, my = 0;
    mouse_get_pos(&mx, &my);
    assert(key_is_down(VK_RBUTTON));
    assert(mx == 480 && my == 360);

    // Simulate multi-touch gesture cancelling active synthetic touch state.
    key_state[VK_LBUTTON] = false;
    key_state[VK_RBUTTON] = false;
    nr_deferred_synthetic_mouse_events = 0;
    synthetic_mouse_hover_observed = false;
    synthetic_mouse_down_observed = false;
    synthetic_mouse_up_observed = true;
    has_last_touch_down_pos = false;

    // Subsequent single-finger drag starts cleanly with fresh observation.
    sim_touch_down(100, 120);
    fake_ticks += 60;
    sim_pe_update(false, NULL, NULL, NULL);
    assert(key_is_down(VK_LBUTTON));
    fake_ticks += 16;
    sim_touch_move(260, 290);
    sim_pe_update(false, NULL, NULL, NULL);
    mouse_get_pos(&mx, &my);
    assert(mx == 260 && my == 290);
""")
        compile_and_run(c_code)


if __name__ == "__main__":
    unittest.main()
