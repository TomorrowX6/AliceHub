"""Validate the SDL pause boundary without requiring a running simulator."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PATCH = ROOT / 'ios/patch-sdl-lifecycle.cmake'
RELATIVE = Path('src/video/uikit/SDL_uikitevents.m')


class LifecycleChecks(unittest.TestCase):
    def test_patch_and_idempotence_and_unknown_source(self):
        sources = list((ROOT / 'ios/build').glob('*/SDL2-prefix/src/SDL2/' + str(RELATIVE)))
        self.assertTrue(sources, 'Build native dependencies first')
        recipe = PATCH.read_text()
        old = re.search(r'set\(old \[=\[(.*?)\]=\]\)', recipe, re.S)[1]
        new = re.search(r'set\(new \[=\[(.*?)\]=\]\)', recipe, re.S)[1]
        original = sources[0].read_text().replace(new, old)
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / RELATIVE
            source.parent.mkdir(parents=True)

            def patch():
                return subprocess.run(['cmake', '-DSOURCE_DIR=' + temporary, '-P', str(PATCH)],
                                      capture_output=True, text=True)

            source.write_text(original)
            self.assertEqual(patch().returncode, 0)
            patched = source.read_text()
            self.assertIn(new, patched)
            self.assertEqual(patch().returncode, 0)
            self.assertEqual(source.read_text(), patched)
            for invalid in ('', 'unexpected SDL version'):
                source.write_text(invalid)
                result = patch()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('Unexpected SDL UIKit event pump source', result.stderr)
                self.assertEqual(source.read_text(), invalid)

    def test_waits_for_active_before_restoring_context(self):
        recipe = PATCH.read_text()
        new = re.search(r'set\(new \[=\[(.*?)\]=\]\)', recipe, re.S)[1]
        body = new.replace('UIApplication.sharedApplication.applicationState', 'state')
        body = body.replace('#if defined(SDL_VIDEO_OPENGL_ES) || defined(SDL_VIDEO_OPENGL_ES2)', '')
        code = r'''
#include <assert.h>
enum { UIApplicationStateActive, inactive, background, kCFRunLoopDefaultMode, TRUE };
static int state, pumps, restores;
static void CFRunLoopRunInMode(int mode, double delay, int once) {
    assert(mode == kCFRunLoopDefaultMode && delay == 0.05 && once == TRUE);
    assert(restores == 0);
    if (++pumps == 4) state = UIApplicationStateActive;
}
static void UIKit_GL_RestoreCurrentContext(void) {
    assert(state == UIApplicationStateActive); restores++;
}
static void pump(void) {
''' + body + r'''
}
int main(void) {
    pump(); assert(pumps == 0 && restores == 1);
    for (int start = inactive; start <= background; start++) {
        pumps = restores = 0; state = start;
        pump(); assert(pumps == 4 && restores == 1);
    }
}
'''
        with tempfile.TemporaryDirectory() as temporary:
            source, binary = Path(temporary) / 'pause.c', Path(temporary) / 'pause'
            source.write_text(code)
            subprocess.run(['cc', '-Wall', '-Wextra', '-Werror', str(source), '-o', str(binary)], check=True)
            subprocess.run([str(binary)], check=True)

    def test_finishes_gpu_and_flushes_saves_before_suspension(self):
        source = (ROOT / 'ios/EnginePlatform.m').read_text()
        start = source.index('object:native.windowScene queue:nil usingBlock:')
        body = source[source.index('{', start) + 1:source.index('}];', start)]
        code = r'''
#include <assert.h>
#include <stdbool.h>
static int context, step;
static int SDL_GL_GetCurrentContext(void) { return context; }
static void glFinish(void) { assert(step++ == 0); }
static void mixer_set_app_backgrounded(bool paused) { assert(paused); assert(step++ == context); }
static void savedata_flush_on_background(void) { assert(step++ == context + 1); }
static void resign(void) {
''' + body + r'''
}
int main(void) {
    resign(); assert(step == 2);
    context = 1; step = 0; resign(); assert(step == 3);
}
'''
        with tempfile.TemporaryDirectory() as temporary:
            source, binary = Path(temporary) / 'resign.c', Path(temporary) / 'resign'
            source.write_text(code)
            subprocess.run(['cc', '-Wall', '-Wextra', '-Werror', str(source), '-o', str(binary)], check=True)
            subprocess.run([str(binary)], check=True)


if __name__ == '__main__':
    unittest.main()
