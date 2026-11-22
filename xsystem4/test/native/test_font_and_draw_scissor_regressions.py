#!/usr/bin/env python3
"""Native C regression tests for FNL substitute font scaling and draw.c viewport scissor clipping."""
from pathlib import Path
import struct
import tempfile
import unittest

from test_evenicle2_regressions import LIBSYS4_CORE, ROOT, compile_and_run
from test_vm_reign_regressions import extract_function


def _fnl_char_to_index(code: int) -> int:
    if code < 0x20:
        return 0
    if code < 0x7F:
        return code - 0x20
    if code < 0xA1:
        return 0
    if code < 0xE0:
        return code - 0x42
    fst, snd = code >> 8, code & 0xFF
    if snd < 0x40 or snd == 0x7F or snd > 0xFC:
        return 0
    snd_index = snd - (0x40 + (1 if snd > 0x7F else 0))
    if fst < 0x81:
        return 0
    if fst < 0xA0:
        fst_index = fst - 0x81
    elif fst < 0xE0:
        return 0
    elif fst < 0xFD:
        fst_index = (fst - 0xE0) + 31
    else:
        return 0
    return 158 + fst_index * 188 + snd_index


def _build_fnl(fonts: list[list[tuple[int, dict[int, int]]]]) -> bytes:
    """Build a minimal FNL archive whose index records glyph widths/data_pos per font/face.

    `fonts` is a list of fonts; each font is a list of `(height, {sjis_code: glyph_width})` faces.
    """
    idx = bytearray()
    idx.extend(struct.pack("<I", len(fonts)))
    for faces in fonts:
        idx.extend(struct.pack("<I", len(faces)))
        for height, glyphs in faces:
            max_gi = max((_fnl_char_to_index(c) for c in glyphs), default=0)
            nr_glyphs = max_gi + 1
            idx.extend(struct.pack("<III", height, 0, nr_glyphs))
            table = bytearray(nr_glyphs * 10)
            for code, width in glyphs.items():
                gi = _fnl_char_to_index(code)
                struct.pack_into("<HII", table, gi * 10, width, 0x100, 16)
            idx.extend(table)
    header = b"FNA\x00" + struct.pack("<III", 0, len(idx) + 16, len(idx))
    return header + bytes(idx)


class FontAndDrawScissorTests(unittest.TestCase):
    def test_fnl_substitute_scale_per_font_and_gbk_modes(self):
        fn_read_le32 = extract_function("src/text.c", "read_le32")
        fn_get_scale = extract_function("src/text.c", "get_fnl_substitute_scale")
        fn_get_ypad = extract_function("src/text.c", "get_fnl_substitute_y_pad")
        fn_init_scale = extract_function("src/text.c", "init_fnl_substitute_scale")

        # Multi-font .fnl matching real games (e.g. Rance09 / Rance08 / Evenicle):
        # font 0: h=384, 確 w=268 (0.6979)
        # font 1: h=192, 確 w=149 (0.7760)
        # font 2: h=200, 確 absent, fallback 亜 (0x889f) w=109 (0.5450)
        multi_fnl = _build_fnl([
            [(192, {0x8A6D: 134}), (384, {0x8A6D: 268})],
            [(192, {0x8A6D: 149})],
            [(200, {0x889F: 109})],
        ])
        # 1) File shorter than header's index_size (short fread -> count == 0, safe 1.0 fallback).
        short_file_fnl = _build_fnl([[(384, {0x8A6D: 268})]])[:-17]
        # 2) Index truncated mid-font 1 (header index_size matches payload -> font 0 parsed, font 1 skipped).
        full_two = _build_fnl([[(384, {0x8A6D: 268})], [(192, {0x8A6D: 149})]])
        trunc_idx_payload = full_two[16:-17]
        trunc_idx_fnl = b"FNA\x00" + struct.pack("<III", 0, len(trunc_idx_payload) + 16, len(trunc_idx_payload)) + trunc_idx_payload

        harness = f"""
#include <assert.h>
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "system4.h"
#include "system4/file.h"
#include "system4/fnl.h"
#include "system4/utfsjis.h"
#include "xsystem4.h"

#define MAX_FNL_FONTS 32
struct config config;
static float fnl_substitute_scales[MAX_FNL_FONTS];
static unsigned fnl_substitute_count = 0;

{fn_read_le32}
{fn_get_scale}
{fn_get_ypad}
{fn_init_scale}

int main(int argc, char **argv) {{
    assert(argc == 4);
    memset(&config, 0, sizeof(config));
    config.fnl_path = argv[1];

    // 1. Native JP mode (!gbk && !mai) does NOT substitute or scale.
    sys4_set_text_gbk(false);
    sys4_set_source_utf8(false);
    sys4_reset_mai_word_table();
    init_fnl_substitute_scale();
    assert(fnl_substitute_count == 0);
    assert(fabsf(get_fnl_substitute_scale(256) - 1.0f) < 1e-4f);
    assert(get_fnl_substitute_y_pad(256, 30.0f) == 0);

    // 2. GBK mode (non-UTF8, e.g. Rance01/03/08/09/Evenicle/Ixseal/Oyakorankan) parses ALL fonts.
    sys4_set_text_gbk(true);
    sys4_set_source_utf8(false);
    init_fnl_substitute_scale();
    assert(fnl_substitute_count == 3);
    assert(fabsf(get_fnl_substitute_scale(0) - 1.0f) < 1e-4f);
    assert(fabsf(get_fnl_substitute_scale(1) - 1.0f) < 1e-4f);
    assert(fabsf(get_fnl_substitute_scale(256) - (268.0f / 384.0f)) < 1e-3f);
    assert(fabsf(get_fnl_substitute_scale(257) - (149.0f / 192.0f)) < 1e-3f);
    assert(fabsf(get_fnl_substitute_scale(258) - (109.0f / 200.0f)) < 1e-3f);
    // Out-of-range FNL face falls back to font 0.
    assert(fabsf(get_fnl_substitute_scale(270) - (268.0f / 384.0f)) < 1e-3f);
    // Vertical centering pad: size=30, scaled=round(30*0.6979)=21 -> (30-21)/2 = 4.5 -> 5.
    assert(get_fnl_substitute_y_pad(256, 30.0f) == 5);
    assert(get_fnl_substitute_y_pad(0, 30.0f) == 0);

    // 3. CHS.TBL / mai word table mode also scales even if sys4_text_is_gbk is false.
    sys4_set_text_gbk(false);
    uint8_t word[65536] = {{0}};
    word[0x8bf7] = 1;
    assert(sys4_load_mai_word_table_mem(word, sizeof(word)));
    init_fnl_substitute_scale();
    assert(fnl_substitute_count == 3);
    assert(fabsf(get_fnl_substitute_scale(256) - (268.0f / 384.0f)) < 1e-3f);
    sys4_reset_mai_word_table();

    // 4. File shorter than index_size stops safely with count == 0 and scale 1.0.
    sys4_set_text_gbk(true);
    config.fnl_path = argv[2];
    init_fnl_substitute_scale();
    assert(fnl_substitute_count == 0);
    assert(fabsf(get_fnl_substitute_scale(256) - 1.0f) < 1e-4f);

    // 5. Index truncated mid-font 1 keeps font 0 cleanly without crashing.
    config.fnl_path = argv[3];
    init_fnl_substitute_scale();
    assert(fnl_substitute_count == 1);
    assert(fabsf(get_fnl_substitute_scale(256) - (268.0f / 384.0f)) < 1e-3f);
    assert(fabsf(get_fnl_substitute_scale(257) - (268.0f / 384.0f)) < 1e-3f);

    puts("OK");
    return 0;
}}
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            td = Path(tmpdir)
            p_multi = td / "multi.fnl"
            p_short = td / "short.fnl"
            p_trunc = td / "trunc.fnl"
            p_multi.write_bytes(multi_fnl)
            p_short.write_bytes(short_file_fnl)
            p_trunc.write_bytes(trunc_idx_fnl)
            out = compile_and_run(
                harness,
                [*LIBSYS4_CORE, ROOT / "subprojects/libsys4/src/fnl.c"],
                ["-lz", "-lm"],
                [str(p_multi), str(p_short), str(p_trunc)],
            )
            self.assertEqual(out.strip(), "OK")

    def test_draw_shader_viewport_scissor_clipping(self):
        fn_run_draw = extract_function("src/draw.c", "run_draw_shader")
        fn_run_copy = extract_function("src/draw.c", "_run_copy_shader")

        harness = f"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

typedef int GLint;
typedef unsigned int GLuint;
typedef int GLsizei;
typedef float GLfloat;
typedef unsigned int GLenum;
typedef float mat4[4][4];

#define GL_DRAW_FRAMEBUFFER 1
#define GL_SCISSOR_TEST 2
#define GFX_RECTANGLE 1

#define MAT4(a00, a01, a02, a03, a10, a11, a12, a13, a20, a21, a22, a23, a30, a31, a32, a33) \\
    {{ {{a00, a10, a20, a30}}, {{a01, a11, a21, a31}}, {{a02, a12, a22, a32}}, {{a03, a13, a23, a33}} }}
#define WV_TRANSFORM(w, h) MAT4(2.0f/(w), 0, 0, -1, 0, 2.0f/(h), 0, -1, 0, 0, 1, 0, 0, 0, 0, 1)

typedef struct shader {{ int id; }} Shader;
typedef struct texture {{ GLuint handle; int w, h; }} Texture;
struct gfx_render_job {{
    Shader *shader;
    int shape;
    GLuint texture;
    float *world_transform;
    float *view_transform;
    void *data;
}};
struct copy_data {{
    int dx, dy, sx, sy, w, h;
    int vpx, vpy, vpw, vph;
    int sw, sh;
}};

static bool scissor_enabled = false;
static bool saw_scissor_on_render = false;
static int last_vp[4];
static int last_sc[4];
static int render_calls = 0;

static bool gfx_remap_texture(Texture *t) {{ return t && t->handle != 0; }}
static void gfx_flush_draw_target(Texture *t) {{ (void)t; }}
static GLuint gfx_set_framebuffer(GLenum target, Texture *t, int x, int y, int w, int h) {{
    (void)target; (void)t;
    last_vp[0] = x; last_vp[1] = y; last_vp[2] = w; last_vp[3] = h;
    return 42;
}}
static void gfx_reset_framebuffer(GLenum target, GLuint fbo) {{
    (void)target; assert(fbo == 42);
    assert(!scissor_enabled);
}}
static void glEnable(GLenum cap) {{
    assert(cap == GL_SCISSOR_TEST);
    scissor_enabled = true;
}}
static void glDisable(GLenum cap) {{
    assert(cap == GL_SCISSOR_TEST);
    scissor_enabled = false;
}}
static void glScissor(GLint x, GLint y, GLsizei w, GLsizei h) {{
    assert(x >= 0 && y >= 0 && w > 0 && h > 0);
    last_sc[0] = x; last_sc[1] = y; last_sc[2] = w; last_sc[3] = h;
}}
static void gfx_render(struct gfx_render_job *job) {{
    (void)job;
    saw_scissor_on_render = scissor_enabled;
    render_calls++;
}}

{fn_run_draw}
{fn_run_copy}

int main(void) {{
    Shader s = {{ 1 }};
    Texture dst = {{ .handle = 10, .w = 800, .h = 600 }};
    Texture src = {{ .handle = 20, .w = 464, .h = 44 }};

    // 1. Sub-rectangle spritesheet draw (Daiteikoku title button): scissor clips exactly to [684, 79, 116, 44].
    struct copy_data d1 = {{ .dx = 684, .dy = 79, .sx = 348, .sy = 0, .w = 116, .h = 44,
                             .vpx = 684, .vpy = 79, .vpw = 116, .vph = 44, .sw = 116, .sh = 44 }};
    _run_copy_shader(&s, &dst, &src, src.w, src.h, &d1);
    assert(render_calls == 1 && saw_scissor_on_render && !scissor_enabled);
    assert(last_vp[0] == 684 && last_vp[1] == 79 && last_vp[2] == 116 && last_vp[3] == 44);
    assert(last_sc[0] == 684 && last_sc[1] == 79 && last_sc[2] == 116 && last_sc[3] == 44);

    // 2. Negative destination x (scrolling background): viewport stays negative, scissor clamps to [0, 0, 800, 600].
    struct copy_data d2 = {{ .dx = -449, .dy = 0, .sx = 0, .sy = 0, .w = 1267, .h = 600,
                             .vpx = -449, .vpy = 0, .vpw = 1267, .vph = 600, .sw = 1267, .sh = 600 }};
    _run_copy_shader(&s, &dst, &src, 1267, 600, &d2);
    assert(render_calls == 2 && saw_scissor_on_render && !scissor_enabled);
    assert(last_vp[0] == -449 && last_vp[2] == 1267);
    assert(last_sc[0] == 0 && last_sc[1] == 0 && last_sc[2] == 800 && last_sc[3] == 600);

    // 3. Partially off bottom-right edge clamps scissor to remaining intersection.
    struct copy_data d3 = {{ .dx = 750, .dy = 580, .sx = 0, .sy = 0, .w = 100, .h = 50,
                             .vpx = 750, .vpy = 580, .vpw = 100, .vph = 50, .sw = 100, .sh = 50 }};
    _run_copy_shader(&s, &dst, &src, 100, 50, &d3);
    assert(render_calls == 3 && !scissor_enabled);
    assert(last_sc[0] == 750 && last_sc[1] == 580 && last_sc[2] == 50 && last_sc[3] == 20);

    // 4. Completely off-screen or zero/negative dimensions skip rendering cleanly.
    struct copy_data d4 = {{ .dx = -200, .dy = 0, .sx = 0, .sy = 0, .w = 100, .h = 44,
                             .vpx = -200, .vpy = 0, .vpw = 100, .vph = 44, .sw = 100, .sh = 44 }};
    struct copy_data d5 = {{ .dx = 0, .dy = 0, .sx = 0, .sy = 0, .w = 0, .h = 44,
                             .vpx = 0, .vpy = 0, .vpw = 0, .vph = 44, .sw = 0, .sh = 44 }};
    _run_copy_shader(&s, &dst, &src, 100, 44, &d4);
    _run_copy_shader(&s, &dst, &src, 100, 44, &d5);
    assert(render_calls == 3 && !scissor_enabled);

    puts("OK");
    return 0;
}}
"""
        out = compile_and_run(harness, [], [])
        self.assertEqual(out.strip(), "OK")

    def test_asset_manager_fallback_fnl_discovery(self):
        fn_valid_fnl = extract_function("src/asset_manager.c", "is_valid_fnl_file")
        harness = f"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>

#include "system4.h"
#include "system4/file.h"
#include "system4/string.h"

{fn_valid_fnl}

static char *pick_fallback_fnl(const char *dir_path) {{
    UDIR *dir = opendir_utf8(dir_path);
    assert(dir != NULL);
    char *best = NULL;
    char *d_name;
    while ((d_name = readdir_utf8(dir))) {{
        const char *ext = file_extension(d_name);
        if (ext && !strcasecmp(ext, "fnl") && d_name[0] != '.') {{
            if (!best || strcmp(d_name, best) < 0) {{
                char *cand = path_join(dir_path, d_name);
                if (is_valid_fnl_file(cand)) {{
                    free(best);
                    best = xstrdup(d_name);
                }}
                free(cand);
            }}
        }}
        free(d_name);
    }}
    closedir_utf8(dir);
    return best;
}}

int main(int argc, char **argv) {{
    assert(argc == 3);
    // Dir 1: AppleDouble ._GameFont.fnl, non-FNA A_corrupt.fnl, directory B_dir.fnl, valid Z_real.fnl.
    char *chosen = pick_fallback_fnl(argv[1]);
    assert(chosen != NULL && strcmp(chosen, "Z_real.fnl") == 0);
    free(chosen);

    // Dir 2: only AppleDouble sidecar and corrupt file -> must return NULL.
    char *none = pick_fallback_fnl(argv[2]);
    assert(none == NULL);

    puts("OK");
    return 0;
}}
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            td = Path(tmpdir)
            d1 = td / "with_real"
            d1.mkdir()
            (d1 / "._GameFont.fnl").write_bytes(b"\x00\x05\x16\x07AppleDouble")
            (d1 / "A_corrupt.fnl").write_bytes(b"NOT_FNA_HEADER")
            (d1 / "B_dir.fnl").mkdir()
            (d1 / "Z_real.fnl").write_bytes(_build_fnl([[(384, {0x8A6D: 268})]]))

            d2 = td / "sidecar_only"
            d2.mkdir()
            (d2 / "._GameFont.fnl").write_bytes(b"\x00\x05\x16\x07AppleDouble")
            (d2 / "broken.fnl").write_bytes(b"BAD!")

            out = compile_and_run(harness, LIBSYS4_CORE, ["-lz", "-lm"], [str(d1), str(d2)])
            self.assertEqual(out.strip(), "OK")

        # Verify asset_manager.c validates fallback *.fnl candidates and ignores dotfiles.
        src = (ROOT / "src/asset_manager.c").read_text(encoding="utf-8")
        self.assertIn('!strcasecmp(ext, "fnl") && d_name[0] != \'.\'', src)
        self.assertIn("is_valid_fnl_file(cand_path)", src)
        self.assertIn("if (!config.fnl_path && fallback_fnl_name)", src)

        # Verify text.c enables gfx_text_advance_edges whenever config.fnl_path is present.
        text_src = (ROOT / "src/text.c").read_text(encoding="utf-8")
        self.assertIn("(config.fnl_path || (ain && ain->version >= 12)) && !game_rance7_mg", text_src)


if __name__ == "__main__":
    unittest.main()
