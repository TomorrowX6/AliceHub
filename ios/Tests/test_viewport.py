"""Compile the runtime viewport functions against SDL/GL fixtures, without UIKit."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def function(path, signature):
    source = (ROOT / path).read_text()
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class ViewportChecks(unittest.TestCase):
    def test_window_info_failure(self):
        code = r'''
#include <assert.h>
#include <setjmp.h>
typedef void SDL_Window;
typedef struct { int version, subsystem; } SDL_SysWMinfo;
#define SDL_SYSWM_UIKIT 1
#define SDL_VERSION(version) (*(version) = 2)
#define ERROR(...) longjmp(failure, 1)
static jmp_buf failure;
static int available = 1, subsystem = SDL_SYSWM_UIKIT;
static int SDL_GetWindowWMInfo(SDL_Window *window, SDL_SysWMinfo *info) {
    (void)window; assert(info->version == 2);
    info->subsystem = subsystem; return available;
}
''' + function('ios/EnginePlatform.m', 'static SDL_SysWMinfo window_info(') + r'''
int main(void) {
    assert(window_info(0).subsystem == SDL_SYSWM_UIKIT);
    available = 0;
    if (setjmp(failure) == 0) { window_info(0); assert(0); }
    available = 1; subsystem = 99;
    if (setjmp(failure) == 0) { window_info(0); assert(0); }
}
'''
        self.compile_and_run(code)

    def test_points_pixels_framebuffer_and_invalid_sizes(self):
        code = r'''
#include <assert.h>
#define ALICEHUB_IOS
#define GL_FRAMEBUFFER 1
#define GL_RENDERBUFFER 2
typedef void SDL_Window;
typedef struct { int x, y, w, h; } SDL_Rect;
typedef struct { struct { struct { int framebuffer, colorbuffer; } uikit; } info; } SDL_SysWMinfo;
static SDL_SysWMinfo window_info(SDL_Window *window) {
    (void)window;
    return (SDL_SysWMinfo){ .info.uikit = { 7, 9 } };
}
static struct { SDL_Window *window; int w, h; SDL_Rect viewport; } sdl = {
    .w = 1280, .h = 720
};
static int points_w, points_h, pixels_w, pixels_h, calls;
static SDL_Rect actual;
static void SDL_GetWindowSize(SDL_Window *window, int *w, int *h) {
    (void)window; *w = points_w; *h = points_h;
}
static void SDL_GL_GetDrawableSize(SDL_Window *window, int *w, int *h) {
    (void)window; *w = pixels_w; *h = pixels_h;
}
static void glBindFramebuffer(int target, int name) {
    assert(target == GL_FRAMEBUFFER && name == 7);
}
static void glBindRenderbuffer(int target, int name) {
    assert(target == GL_RENDERBUFFER && name == 9);
}
static void glViewport(int x, int y, int w, int h) {
    actual = (SDL_Rect){ x, y, w, h }; calls++;
}
''' + function('xsystem4/src/video.c', 'void gfx_update_screen_scale(') + '\n' + function(
            'ios/EnginePlatform.m', 'void alicehub_ios_bind_drawable(') + r'''
int main(void) {
    for (int scale = 1; scale <= 3; scale++) {
        points_w = 1280; points_h = 720;
        pixels_w = points_w * scale; pixels_h = points_h * scale;
        gfx_update_screen_scale();
        assert(sdl.viewport.x == 0 && sdl.viewport.y == 0);
        assert(sdl.viewport.w == 1280 && sdl.viewport.h == 720);
        alicehub_ios_bind_drawable(NULL, &sdl.viewport);
        assert(actual.x == 0 && actual.y == 0);
        assert(actual.w == pixels_w && actual.h == pixels_h);
    }
    points_w = 874; points_h = 402; pixels_w = 2622; pixels_h = 1206;
    gfx_update_screen_scale();
    assert(sdl.viewport.x == 80 && sdl.viewport.y == 0);
    assert(sdl.viewport.w == 714 && sdl.viewport.h == 402);
    alicehub_ios_bind_drawable(NULL, &sdl.viewport);
    assert(actual.x == 240 && actual.y == 0 && actual.w == 2142 && actual.h == 1206);
    points_w = 720; points_h = 1280; pixels_w = 1440; pixels_h = 2560;
    gfx_update_screen_scale();
    assert(sdl.viewport.x == 0 && sdl.viewport.y == 437);
    assert(sdl.viewport.w == 720 && sdl.viewport.h == 405);
    alicehub_ios_bind_drawable(NULL, &sdl.viewport);
    assert(actual.x == 0 && actual.y == 874 && actual.w == 1440 && actual.h == 810);
    int previous_calls = calls;
    points_w = 0;
    gfx_update_screen_scale();
    alicehub_ios_bind_drawable(NULL, &sdl.viewport);
    assert(calls == previous_calls && sdl.viewport.h == 405);
    points_w = 720; points_h = 0;
    gfx_update_screen_scale();
    alicehub_ios_bind_drawable(NULL, &sdl.viewport);
    assert(calls == previous_calls && sdl.viewport.h == 405);
    points_h = 1280; sdl.w = 0;
    gfx_update_screen_scale();
    assert(sdl.viewport.h == 405);
    sdl.w = 1280; sdl.h = 0;
    gfx_update_screen_scale();
    assert(sdl.viewport.h == 405);
}
'''
        self.compile_and_run(code)

    def compile_and_run(self, code):
        with tempfile.TemporaryDirectory() as temporary:
            source, binary = Path(temporary) / 'viewport.c', Path(temporary) / 'viewport'
            source.write_text(code)
            subprocess.run(['cc', '-std=c11', '-Wall', '-Wextra', '-Werror', '-include',
                            'stddef.h', str(source), '-o', str(binary)], check=True)
            subprocess.run([str(binary)], check=True)


if __name__ == '__main__':
    unittest.main()
