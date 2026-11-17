#!/usr/bin/env python3
"""Dependency-free regression harness for PartsEngine2 reversal/construction blur.

Compiles the actual function bodies from the engine against small matrix/texture
fixtures. No SDL, GL context or copyrighted game data is needed. This verifies
transform semantics and the no-feedback invariant, NOT real GPU/game rendering.
Run: python3 xsystem4/test/native/test_parts_render_regressions.py
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def function(path, name):
    source = (ROOT / path).read_text()
    start = source.rfind('\n', 0, source.index(name + '(')) + 1
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


FIXTURES = r'''
#include <assert.h>
#include <stdbool.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
typedef float mat4[4][4];
typedef float vec3[3];
typedef struct { float x, y; } SDL_FPoint;
typedef struct { int x, y; } Point;
#define PARTS_LAYOUT_BOX 1
#define PARTS_LAYOUT_FREE 2
struct parts {
    struct parts *parent;
    struct { int type; struct { int layout_type; } layout_box;
             struct { Point origin_offset; } common; } states[1];
    struct { float x, y, z; } dummy;
    struct { SDL_FPoint pos, scale; struct { float x, y, z; } rotation; } local;
    int scroll_links[2];
    struct { float pos; } *scrollbar;
    bool reverse_lr, reverse_tb;
};
static struct parts component;
static int transform_dirty, draw_dirty;
static struct parts *parts_get(int n) { assert(n == 1); return &component; }
static struct parts *parts_try_get(int n) { (void)n; return NULL; }
static SDL_FPoint parts_local_position(struct parts *p) { return p->local.pos; }
static void parts_component_dirty(struct parts *p) { (void)p; transform_dirty++; }
static void parts_dirty(struct parts *p) { (void)p; draw_dirty++; }
static float glm_rad(float x) { return x * 0.017453292519943295f; }
static void identity(mat4 m) {
    memset(m, 0, sizeof(mat4));
    for (int i = 0; i < 4; i++) m[i][i] = 1;
}
static void mul(mat4 a, mat4 b, mat4 out) {
    mat4 m = {0};
    for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++)
        for (int k = 0; k < 4; k++) m[c][r] += a[k][r] * b[c][k];
    memcpy(out, m, sizeof m);
}
static void glm_translate(mat4 m, vec3 p) {
    mat4 t; identity(t);
    for (int i = 0; i < 3; i++) t[3][i] = p[i];
    mul(m, t, m);
}
static void glm_scale(mat4 m, vec3 p) {
    for (int c = 0; c < 3; c++) for (int r = 0; r < 4; r++) m[c][r] *= p[c];
}
static void rotate(mat4 m, float a, mat4 out, int x, int y) {
    mat4 t; identity(t);
    t[x][x] = t[y][y] = cosf(a); t[x][y] = sinf(a); t[y][x] = -sinf(a);
    mul(m, t, out);
}
static void glm_rotate_z(mat4 m, float a, mat4 out) { rotate(m, a, out, 0, 1); }
static void glm_rotate_x(mat4 m, float a, mat4 out) { rotate(m, a, out, 1, 2); }
static void glm_rotate_y(mat4 m, float a, mat4 out) { rotate(m, a, out, 2, 0); }

typedef struct texture { int handle, w, h; unsigned char *pixels; } Texture;
struct parts_construction_process { struct { Texture texture; } common; };
struct parts_cp_filter { int x, y, w, h, blur; bool full_size; };
static int allocated, deleted, blurs, next_handle = 10;
static void gfx_init_texture_blank(Texture *t, int w, int h) {
    *t = (Texture){++next_handle, w, h, calloc((size_t)w*h, 4)};
    assert(t->pixels); allocated++;
}
static void gfx_delete_texture(Texture *t) { free(t->pixels); t->handle = 0; deleted++; }
static void gfx_copy_with_alpha_map(Texture *d, int dx, int dy, Texture *s,
                                   int sx, int sy, int w, int h) {
    assert(d->handle != s->handle);
    for (int y = 0; y < h; y++)
        memcpy(d->pixels + ((dy+y)*d->w+dx)*4, s->pixels + ((sy+y)*s->w+sx)*4, (size_t)w*4);
}
static void blur(Texture *d, int dx, int dy, Texture *s, int sx, int sy,
                 int w, int h, int radius, bool vertical) {
    // This is the GLES invariant: source MUST NOT be attached as destination.
    assert(s->handle != d->handle);
    assert(w > 0 && h > 0 && radius > 0);
    blurs++;
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
        for (int c = 0; c < 3; c++) {
            int sum = 0;
            for (int i = -radius; i <= radius; i++) {
                int xx = x + (vertical ? 0 : i), yy = y + (vertical ? i : 0);
                if (xx >= 0 && xx < w && yy >= 0 && yy < h)
                    sum += s->pixels[((sy+yy)*s->w + sx+xx)*4+c];
            }
            d->pixels[((dy+y)*d->w + dx+x)*4+c] = sum/(2*radius+1);
        }
        // Match the production blur functions: preserve destination alpha.
    }
}
static void gfx_copy_height_blur(Texture *d, int dx, int dy, Texture *s, int sx, int sy,
                                int w, int h, int r) { blur(d,dx,dy,s,sx,sy,w,h,r,true); }
static void gfx_copy_width_blur(Texture *d, int dx, int dy, Texture *s, int sx, int sy,
                               int w, int h, int r) { blur(d,dx,dy,s,sx,sy,w,h,r,false); }
'''

TESTS = r'''
static void close_matrix(mat4 a, mat4 b) {
    for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++)
        assert(fabsf(a[c][r] - b[c][r]) < 0.0001f);
}
static void test_reversal(void) {
    // CG and FLAT both take this same local transform through parts_world_matrix.
    for (int rotation = 0; rotation < 2; rotation++) {
        for (int flags = 0; flags < 4; flags++) {
            component = (struct parts){0};
            component.local.pos = (SDL_FPoint){120, 70};
            component.local.scale = (SDL_FPoint){2, 3};
            if (rotation) {
                component.local.rotation.x = 11;
                component.local.rotation.y = 19;
                component.local.rotation.z = 37;
            }
            component.reverse_lr = flags & 1;
            component.reverse_tb = flags & 2;
            mat4 actual, expected;
            identity(actual); identity(expected);
            // Simulate a transformed parent too: flips must not replace it.
            glm_translate(actual, (vec3){7, 9, 0});
            glm_translate(expected, (vec3){7, 9, 0});
            parts_apply_local_matrix(&component, actual);
            glm_translate(expected, (vec3){120,70,0});
            if (rotation) {
                glm_rotate_z(expected, glm_rad(37), expected);
                glm_rotate_x(expected, glm_rad(-11), expected);
                glm_rotate_y(expected, glm_rad(19), expected);
            }
            glm_scale(expected, (vec3){flags & 1 ? -2 : 2, flags & 2 ? -3 : 3, 1});
            close_matrix(actual, expected);
            // The public magnification stays independent of reversal.
            assert(component.local.scale.x == 2 && component.local.scale.y == 3);
        }
    }
    component = (struct parts){0};
    transform_dirty = draw_dirty = 0;
    PartsEngine_SetComponentReverseLR(1, true);
    assert(component.reverse_lr && transform_dirty == 1 && draw_dirty == 1);
    PartsEngine_SetComponentReverseLR(1, true);
    assert(transform_dirty == 1 && draw_dirty == 1);
    PartsEngine_SetComponentReverseLR(1, false);
    PartsEngine_SetComponentReverseTB(1, true);
    assert(!component.reverse_lr && component.reverse_tb);
    assert(transform_dirty == 3 && draw_dirty == 3);
}
static void test_blur(void) {
    for (int vertical = 0; vertical < 2; vertical++) for (int full = 0; full < 2; full++) {
        struct parts_construction_process cp = {0};
        gfx_init_texture_blank(&cp.common.texture, 7, 5);
        unsigned char original[7*5*4];
        for (int i = 0; i < 7*5*4; i++) original[i] = cp.common.texture.pixels[i] = i % 251;
        Texture expected = {0}, source = {0};
        gfx_init_texture_blank(&expected, 7, 5);
        gfx_init_texture_blank(&source, 7, 5);
        memcpy(expected.pixels, original, sizeof original);
        memcpy(source.pixels, original, sizeof original);
        struct parts_cp_filter op = {1, 1, 4, 3, 1, full};
        blur(&expected, full?0:1, full?0:1, &source, full?0:1, full?0:1,
             full?7:4, full?5:3, 1, vertical);
        assert(build_blur_filter(&cp, &op, vertical));
        assert(!memcmp(expected.pixels, cp.common.texture.pixels, sizeof original));
        // A zero radius or empty rect does not allocate/copy or touch the target.
        int before = allocated;
        op.blur = 0;
        assert(build_blur_filter(&cp, &op, vertical) && allocated == before);
        assert(!memcmp(expected.pixels, cp.common.texture.pixels, sizeof original));
        gfx_delete_texture(&cp.common.texture);
        gfx_delete_texture(&expected); gfx_delete_texture(&source);
    }
    struct parts_construction_process missing = {0};
    struct parts_cp_filter op = {0, 0, 7, 5, 2, true};
    assert(!build_blur_filter(&missing, &op, false));
    assert(allocated == deleted);
}
int main(void) { test_reversal(); test_blur(); puts("parts render regression fixtures passed"); }
'''


class PartsRenderingTests(unittest.TestCase):
    def test_production_functions(self):
        code = FIXTURES + '\n'.join([
            function('src/parts/parts.c', 'parts_apply_local_matrix'),
            function('src/hll/PartsEngine.c', 'PartsEngine_SetComponentReverseLR'),
            function('src/hll/PartsEngine.c', 'PartsEngine_SetComponentReverseTB'),
            function('src/parts/construction.c', 'build_blur_filter'),
        ]) + TESTS
        with tempfile.TemporaryDirectory() as tmp:
            c = Path(tmp) / 'test.c'
            exe = Path(tmp) / 'test'
            c.write_text(code)
            subprocess.run([os.environ.get('CC', 'cc'), '-std=c11', '-Wall', '-Wextra', '-Werror',
                            str(c), '-lm', '-o', str(exe)], check=True)
            subprocess.run([str(exe)], check=True)

    def test_activity_scale_legacy_and_v2(self):
        # Exercise the real activity scale helper instead of asserting that the
        # loader never bakes reversal (that is only correct for PartsEngine2).
        code = r'''
#include <assert.h>
#include <stdbool.h>
#include <string.h>
struct parts { bool reverse_lr, reverse_tb; float sx, sy; };
struct ex_tree { bool lr, tb; float sx, sy; };
static bool parts_v2;
static float number(struct ex_tree *t, const char *key, float fallback) {
    (void)fallback;
    return !strcmp(key, "左右反转") ? t->lr : t->tb;
}
static float item(struct ex_tree *t, const char *key, int i, float fallback) {
    (void)key; (void)fallback; return i ? t->sy : t->sx;
}
static void parts_set_scale_x(struct parts *p, float v) { p->sx = v; }
static void parts_set_scale_y(struct parts *p, float v) { p->sy = v; }
''' + function('src/parts/activity.c', 'load_part_scale') + r'''
int main(void) {
    for (int v2 = 0; v2 < 2; v2++) for (int f = 0; f < 4; f++)
    for (int signs = 0; signs < 4; signs++) {
        parts_v2 = v2;
        struct parts p = {0};
        struct ex_tree t = {f & 1, f & 2, signs & 1 ? -2 : 2, signs & 2 ? -3 : 3};
        load_part_scale(&p, &t);
        assert(p.reverse_lr == t.lr && p.reverse_tb == t.tb);
        assert(p.sx == (v2 || !t.lr ? t.sx : -t.sx));
        assert(p.sy == (v2 || !t.tb ? t.sy : -t.sy));
    }
}
'''
        with tempfile.TemporaryDirectory() as tmp:
            c, exe = Path(tmp) / 'activity.c', Path(tmp) / 'activity'
            c.write_text(code)
            subprocess.run([os.environ.get('CC', 'cc'), '-std=c11', '-Wall', '-Wextra', '-Werror',
                            str(c), '-o', str(exe)], check=True)
            subprocess.run([str(exe)], check=True)

    def test_save_flags_have_version_gate(self):
        save = (ROOT / 'src/parts/save.c').read_text()
        self.assertIn('#define CURRENT_SAVE_VERSION 32', save)
        for axis in ('lr', 'tb'):
            self.assertIn(f'iarray_write(w, parts->reverse_{axis});', save)
            self.assertIn(f'parts->reverse_{axis} = version >= 32 ? !!iarray_read(r) : false;', save)


if __name__ == '__main__':
    unittest.main()
