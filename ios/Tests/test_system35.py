"""Exercise iOS System 3 hooks without UIKit or game data."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_viewport import function

ROOT = Path(__file__).resolve().parents[2]


class System35Checks(unittest.TestCase):
    def compile_and_run(self, code):
        with tempfile.TemporaryDirectory() as temporary:
            source, binary = Path(temporary) / 'check.c', Path(temporary) / 'check'
            source.write_text(code)
            subprocess.run(['cc', '-DALICEHUB_IOS', '-Wall', '-Wextra', '-Werror',
                            str(source), '-o', str(binary)], check=True)
            subprocess.run([str(binary)], cwd=temporary, check=True)

    def test_virtual_stick_uses_configured_deadzone(self):
        bridge = 'xsystem35/src/alicehub_bridge.c'
        self.compile_and_run(r'''
#include <assert.h>
#include <math.h>
static struct { float deadzone; } gp_cfg;
''' + function(bridge, 'static float apply_deadzone(')
    + function(bridge, 'static void apply_virtual_stick_deadzone(') + r'''
int main(void) {
    float x = 0.2f, y = -0.2f;
    gp_cfg.deadzone = 0.45f;
    apply_virtual_stick_deadzone(&x, &y);
    assert(x == 0 && y == 0);

    x = 0.2f;
    y = -0.2f;
    gp_cfg.deadzone = 0.05f;
    apply_virtual_stick_deadzone(&x, &y);
    assert(x > 0 && x < 0.2f && y == -x);

    x = 1;
    y = -1;
    apply_virtual_stick_deadzone(&x, &y);
    assert(x == 1 && y == -1);
}
''')

    def test_background_audio_and_render_flush(self):
        self.compile_and_run(r'''
#include <assert.h>
typedef int SDL_bool;
static int gfx_renderer, step;
static void SDL_RenderFlush(int renderer) { (void)renderer; assert(step++ == 0); }
static void msgskip_syncFile(void) { assert(step++ == 1); }
static void mixer_pause_all(int paused) { assert(step++ == (paused ? 2 : 0)); }
''' + function('xsystem35/src/gfx_video.c', 'static void ios_background_changed(') + r'''
int main(void) {
    ios_background_changed(1); assert(step == 3);
    step = 0; ios_background_changed(0); assert(step == 1);
}
''')

    def test_crash_log_missing_invalid_and_valid_path(self):
        self.compile_and_run(r'''
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
static struct { int current_page, current_addr; } state = {42, 123}, *nact = &state;
''' + function('xsystem35/src/system.c', 'void sys_write_crash_log(') + r'''
int main(void) {
    unsetenv("XSYSTEM4_CRASH_LOG_PATH"); sys_write_crash_log(NULL);
    setenv("XSYSTEM4_CRASH_LOG_PATH", "", 1); sys_write_crash_log(NULL);
    setenv("XSYSTEM4_CRASH_LOG_PATH", "missing/crash.log", 1); sys_write_crash_log(NULL);
    setenv("XSYSTEM4_CRASH_LOG_PATH", "crash.log", 1); sys_write_crash_log("test error");
    FILE *file = fopen("crash.log", "r"); assert(file);
    char text[512] = {0}; assert(fread(text, 1, sizeof(text)-1, file)); fclose(file);
    assert(strstr(text, "test error") && strstr(text, "Page: 42 Address: 0x7b"));
}
''')

    def test_font_defaults_precede_profile_overrides(self):
        source = (ROOT / 'xsystem35/src/xsystem35.c').read_text()
        start = source.index('\tconst char *gothic = getenv(')
        end = source.index('#endif', start)
        self.assertLess(end, source.index('\tload_profile();', start))
        self.compile_and_run(r'''
#include <assert.h>
#include <stdlib.h>
#include <string.h>
enum { FONT_GOTHIC, FONT_MINCHO };
static const char *fontname_tt[] = {"default-gothic", "default-mincho"};
static void defaults(void) {
''' + source[start:end] + r'''
}
int main(void) {
    unsetenv("XSYSTEM35_FONT_GOTHIC"); setenv("XSYSTEM35_FONT_MINCHO", "", 1);
    defaults(); assert(!strcmp(fontname_tt[FONT_GOTHIC], "default-gothic"));
    assert(!strcmp(fontname_tt[FONT_MINCHO], "default-mincho"));
    setenv("XSYSTEM35_FONT_GOTHIC", "/bundle/MTLc3m.ttf", 1);
    setenv("XSYSTEM35_FONT_MINCHO", "/bundle/mincho.ttf", 1);
    defaults(); assert(!strcmp(fontname_tt[FONT_GOTHIC], "/bundle/MTLc3m.ttf"));
    assert(!strcmp(fontname_tt[FONT_MINCHO], "/bundle/mincho.ttf"));
}
''')


if __name__ == '__main__':
    unittest.main()
