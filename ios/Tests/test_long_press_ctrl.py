"""Drive the xsystem4 touch handler with SDL events to check long-press Ctrl."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
ENGINE = ROOT / 'xsystem4'

HARNESS = r'''
#include <assert.h>
#include <stdlib.h>
#include <SDL.h>
#include "gfx/private.h"
#include "input.h"

struct sdl_private sdl;
void PE_FlushCGCache(void) {}
void alicehub_ios_gamepad_command(int command) { (void)command; }
void alicehub_ios_gamepad_frame(float x, float y, bool cursor, bool movie, bool controller, bool ctrl)
{ (void)x; (void)y; (void)cursor; (void)movie; (void)controller; (void)ctrl; }
bool gfx_android_consume_lifecycle_event(Uint32 event_type) { (void)event_type; return false; }
void gfx_android_on_background(void) {}
void gfx_android_on_foreground(void) {}
bool gfx_recover_gl_if_needed(void) { return false; }
void gfx_release_context_backup_if_ready(void) {}
void gfx_reset_frame_pacer(void) {}
void gfx_restore_window(void) {}
void gfx_swap(void) {}
void gfx_toggle_fullscreen(void) {}
void gfx_trace_resume(const char *phase) { (void)phase; }
void gfx_update_screen_scale(void) {}
void mixer_handle_device_removed(uint32_t dev) { (void)dev; }
void mixer_retry_audio_device(void) {}
void mixer_set_app_backgrounded(bool backgrounded) { (void)backgrounded; }
void mixer_set_window_focus(bool focus) { (void)focus; }
bool movie_has_active_playback(void) { return false; }
uint32_t movie_get_playback_seq(void) { return 0; }
bool movie_skip_active(void) { return false; }
void savedata_flush_on_background(void) {}
void screenshot_save(void) {}
_Noreturn void vm_exit(int code) { exit(code); }
void vm_stack_trace(void) {}

static bool saw_lbutton;
static int fingers_down;

int SDL_GetNumTouchFingers(SDL_TouchID touch_id)
{
	(void)touch_id;
	return fingers_down;
}

static void finger(Uint32 type, SDL_FingerID id, float x, float y)
{
	fingers_down += (type == SDL_FINGERDOWN) - (type == SDL_FINGERUP);
	SDL_Event e = { .tfinger = { .type = type, .touchId = 1, .fingerId = id, .x = x, .y = y } };
	SDL_PushEvent(&e);
}

static void button(bool down)
{
	SDL_Event e = { .button = {
		.type = down ? SDL_MOUSEBUTTONDOWN : SDL_MOUSEBUTTONUP, .which = SDL_TOUCH_MOUSEID,
		.button = SDL_BUTTON_LEFT, .state = down ? SDL_PRESSED : SDL_RELEASED,
		.clicks = 1, .x = 320, .y = 240 } };
	SDL_PushEvent(&e);
}

static void pump(Uint32 ms)
{
	Uint32 end = SDL_GetTicks() + ms;
	saw_lbutton = false;
	do {
		handle_events();
		saw_lbutton |= key_is_down(VK_LBUTTON);
		SDL_Delay(5);
	} while (SDL_GetTicks() < end);
}

static void press(void)
{
	button(true);
	finger(SDL_FINGERDOWN, 7, 0.5f, 0.5f);
	pump(200);
	assert(saw_lbutton && !key_is_down(VK_CONTROL));
}

static void lift(bool finger_first)
{
	if (finger_first) {
		finger(SDL_FINGERUP, 7, 0.5f, 0.5f);
		button(false);
	} else {
		button(false);
		finger(SDL_FINGERUP, 7, 0.5f, 0.5f);
	}
}

int main(void)
{
	assert(SDL_Init(SDL_INIT_EVENTS) == 0);
	sdl.w = sdl.viewport.w = 640;
	sdl.h = sdl.viewport.h = 480;

	press();
	pump(1100);
	assert(saw_lbutton && !key_is_down(VK_CONTROL));
	lift(false);
	pump(400);
	assert(!key_is_down(VK_LBUTTON));

	input_set_long_press_ctrl(true);
	for (int finger_first = 0; finger_first < 2; finger_first++) {
		press();
		pump(1000);
		assert(key_is_down(VK_CONTROL) && !key_is_down(VK_LBUTTON));
		lift(finger_first);
		pump(400);
		assert(!saw_lbutton && !key_is_down(VK_CONTROL));
	}

	press();
	finger(SDL_FINGERMOTION, 7, 0.6f, 0.5f);
	pump(1100);
	assert(saw_lbutton && !key_is_down(VK_CONTROL));
	lift(false);
	pump(400);

	press();
	input_cancel_long_touch();
	pump(1100);
	assert(saw_lbutton && !key_is_down(VK_CONTROL));
	lift(false);
	pump(400);

	press();
	pump(1000);
	assert(key_is_down(VK_CONTROL));
	finger(SDL_FINGERDOWN, 8, 0.2f, 0.2f);
	pump(50);
	assert(!key_is_down(VK_CONTROL));
	finger(SDL_FINGERUP, 8, 0.2f, 0.2f);
	lift(false);
	pump(400);
	assert(!saw_lbutton && !key_is_down(VK_CONTROL));

	press();
	input_set_touch_ctrl_skip(true);
	pump(1000);
	lift(false);
	pump(400);
	assert(key_is_down(VK_CONTROL));
	input_set_touch_ctrl_skip(false);
	pump(50);
	assert(!key_is_down(VK_CONTROL));
	return 0;
}
'''


class LongPressCtrlChecks(unittest.TestCase):
    def test_long_press_holds_ctrl_only_when_enabled(self):
        if not shutil.which('pkg-config') or subprocess.run(
                ['pkg-config', '--exists', 'sdl2', 'freetype2']).returncode:
            self.skipTest('Host SDL2 and FreeType development files are required')
        flags = subprocess.check_output(['pkg-config', '--cflags', '--libs', 'sdl2', 'freetype2'],
                                        text=True).split()
        with tempfile.TemporaryDirectory() as temporary:
            harness = Path(temporary) / 'harness.c'
            binary = Path(temporary) / 'harness'
            harness.write_text(HARNESS)
            subprocess.run(['cc', '-std=gnu11', '-DALICEHUB_IOS', '-I' + str(ENGINE / 'include'),
                            '-I' + str(ENGINE / 'subprojects/libsys4/include'), '-I' + str(ROOT / 'ios'),
                            str(harness), str(ENGINE / 'src/input.c'), str(ENGINE / 'src/cJSON.c'),
                            '-o', str(binary), *flags, '-lm'], check=True)
            subprocess.run([str(binary)], check=True, timeout=60,
                           env={'SDL_VIDEODRIVER': 'dummy', 'PATH': '/usr/bin:/bin'})


if __name__ == '__main__':
    unittest.main()
