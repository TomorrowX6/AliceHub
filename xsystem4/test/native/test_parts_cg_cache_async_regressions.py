#!/usr/bin/env python3
"""Native regression tests for PartsEngine refcounted CG asset cache, lazy alpha-mask,
and SDL2 multi-threaded SetPartsCGThread worker pool + asset_manager thread safety.

Compiles the real production src/parts/parts.c, src/parts/texture.c, and src/asset_manager.c
against lightweight GL/VM fixtures and real SDL2 threads.

Run: python3 -m unittest xsystem4/test/native/test_parts_cg_cache_async_regressions.py
"""
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[2]
LIBSYS4_INC = ROOT / "subprojects/libsys4/include"
LIBSYS4_SRC = ROOT / "subprojects/libsys4/src"


def sdl2_flags():
    try:
        out = subprocess.check_output(["pkg-config", "--cflags", "--libs", "sdl2"], text=True)
        return out.split()
    except (FileNotFoundError, subprocess.CalledProcessError):
        return ["-I/usr/include/SDL2", "-lSDL2"]


def make_qnt_v1(width, height, rgba_pixel):
    """Build a valid QNT v1 CG filled with a single (r, g, b, a) pixel."""
    r, g, b, a = rgba_pixel
    # QNT stores 2x2 blocks of delta-predicted BGR channels + delta-predicted alpha.
    pw = (width + 1) & ~1
    ph = (height + 1) & ~1
    # For a uniform image: first pixel has value c, all subsequent deltas are 0!
    # Wait: QNT unpacks planar 2x2 blocks (B, G, R) into pic[y*w+x], then applies
    # horizontal/vertical median-style delta predictor:
    #   pic[0] = raw_val
    #   x>0, y=0: pic[x] = pic[x-1] - raw_val -> raw_val = 0 keeps pic[x] == pic[0]
    #   x=0, y>0: pic[y*w] = pic[(y-1)*w] - raw_val -> raw_val = 0 keeps constant
    #   x>0, y>0: pic[y*w+x] = ((py+px)>>1) - raw_val -> raw_val = 0 keeps constant!
    # In extract_pixel: i=2 (B) -> pic[0*3+2] = raw[0]; i=1 (G) -> pic[0*3+1] = raw[plane];
    # i=0 (R) -> pic[0*3+0] = raw[2*plane].
    plane = pw * ph
    pix_raw = bytearray(plane * 3)
    pix_raw[0] = b
    pix_raw[plane] = g
    pix_raw[2 * plane] = r
    pix_comp = zlib.compress(bytes(pix_raw))

    # Alpha: raw[0] = a, all other deltas = 0 -> constant alpha = a!
    alpha_raw = bytearray(pw * ph)
    alpha_raw[0] = a
    alpha_comp = zlib.compress(bytes(alpha_raw))

    hdr_size = 48
    hdr = struct.pack(
        "<4s11I",
        b"QNT\0",
        1,          # version (rsv0 != 0 -> v1)
        hdr_size,
        0, 0,       # x0, y0
        width, height,
        24,         # bpp
        1,          # rsv
        len(pix_comp),
        len(alpha_comp),
        0,          # pad to 48 bytes
    )
    return hdr + pix_comp + alpha_comp


def make_afa_v2(entries):
    """Build a valid AFAv2 archive from [(name_bytes, payload_bytes), ...]."""
    file_table = bytearray()
    data_blob = bytearray(b"DATA" + b"\x00\x00\x00\x00")
    for idx, (name_bytes, payload) in enumerate(entries):
        off = len(data_blob)
        data_blob.extend(payload)
        padded = ((len(name_bytes) + 4) // 4) * 4
        file_table.extend(struct.pack("<II", len(name_bytes), padded))
        file_table.extend(name_bytes)
        file_table.extend(b"\x00" * (padded - len(name_bytes)))
        # AFAv2 with has_number: no(1-based), unknown0, unknown1, off, size
        file_table.extend(struct.pack("<IIIII", idx + 1, 0, 0, off, len(payload)))
    struct.pack_into("<I", data_blob, 4, len(data_blob))

    comp_table = zlib.compress(bytes(file_table))
    info_size = 16 + len(comp_table)
    data_start = 44 + len(comp_table)
    header = struct.pack(
        "<4sI8sIII4sIII",
        b"AFAH",
        0x1C,
        b"AlicArch",
        2,              # version
        0,              # unknown
        data_start,
        b"INFO",
        info_size,
        len(file_table),
        len(entries),
    )
    return header + comp_table + bytes(data_blob)


PARTS_HARNESS = r'''
#include <assert.h>
#include <limits.h>
#include <math.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <SDL.h>

#define ALICEHUB_IOS
#include "system4.h"
#include "system4/ain.h"
#include "system4/cg.h"
#include "system4/hashtable.h"
#include "system4/string.h"
#include "system4/utfsjis.h"
#include "vm.h"
#include "vm/page.h"
#include "xsystem4.h"
#include "asset_manager.h"
#include "audio.h"
#include "movie.h"
#include "sact.h"
#include "sprite.h"
#include "scene.h"
#include "reign.h"
#include "parts.h"
#include "parts_activity.h"
#include "parts/parts_internal.h"

/* --- Minimal engine stubs --- */
struct config config = { .view_width = 1280, .view_height = 720 };
static struct ain fake_ain = { .version = 14 };
struct ain *ain = &fake_ain;
uint64_t parts_render_gen = 1;
bool parts_began_click = false;

_Noreturn void _vm_error(const char *fmt, ...) { (void)fmt; abort(); }

/* --- Texture tracking & mock GL state --- */
#define MAX_MOCK_TEX 4096
struct mock_tex {
    bool alive;
    int w, h;
    uint32_t gen;
    uint8_t *pixels;
};
static struct mock_tex mock_textures[MAX_MOCK_TEX];
static GLuint next_tex_handle = 1;
static uint32_t mock_ctx_gen = 1;
static int mock_upload_count = 0;
static int mock_delete_count = 0;
static int mock_live_textures = 0;
static int mock_get_pixels_calls = 0;
static bool mock_get_pixels_fail = false;
static bool mock_remap_fail_all = false;
static gfx_reset_callback registered_reset_cb = NULL;
static void *registered_reset_user = NULL;

void gfx_register_reset_callback(gfx_reset_callback cb, void *user) {
    registered_reset_cb = cb;
    registered_reset_user = user;
}

bool gfx_remap_texture(struct texture *t) {
    if (!t || !t->handle)
        return false;
    if (mock_remap_fail_all && t->ctx_gen != mock_ctx_gen) {
        t->handle = 0;
        t->opaque = false;
        t->ctx_gen = 0;
        return false;
    }
    if (t->handle < MAX_MOCK_TEX && mock_textures[t->handle].alive) {
        t->ctx_gen = mock_ctx_gen;
        return true;
    }
    t->handle = 0;
    t->ctx_gen = 0;
    return false;
}

void gfx_init_texture_with_pixels(struct texture *t, int w, int h, void *pixels) {
    assert(next_tex_handle < MAX_MOCK_TEX);
    GLuint id = next_tex_handle++;
    mock_textures[id].alive = true;
    mock_textures[id].w = w;
    mock_textures[id].h = h;
    mock_textures[id].gen = mock_ctx_gen;
    if (pixels && w > 0 && h > 0) {
        size_t sz = (size_t)w * (size_t)h * 4;
        mock_textures[id].pixels = malloc(sz);
        memcpy(mock_textures[id].pixels, pixels, sz);
    } else {
        mock_textures[id].pixels = NULL;
    }
    t->handle = id;
    t->w = w;
    t->h = h;
    t->opaque = false;
    t->ctx_gen = mock_ctx_gen;
    mock_upload_count++;
    mock_live_textures++;
}

void gfx_init_texture_with_cg(struct texture *t, struct cg *cg) {
    gfx_init_texture_with_pixels(t, cg->metrics.w, cg->metrics.h, cg->pixels);
    if (!cg->metrics.has_alpha)
        t->opaque = true;
}

void gfx_delete_texture(struct texture *t) {
    if (!t || !t->handle)
        return;
    assert(t->handle < MAX_MOCK_TEX);
    assert(mock_textures[t->handle].alive && "DOUBLE FREE of GL texture!");
    mock_textures[t->handle].alive = false;
    free(mock_textures[t->handle].pixels);
    mock_textures[t->handle].pixels = NULL;
    t->handle = 0;
    t->opaque = false;
    t->ctx_gen = 0;
    mock_delete_count++;
    mock_live_textures--;
}

void *gfx_get_pixels(Texture *t) {
    mock_get_pixels_calls++;
    if (mock_get_pixels_fail || !t || !t->handle || t->handle >= MAX_MOCK_TEX)
        return NULL;
    struct mock_tex *mt = &mock_textures[t->handle];
    assert(mt->alive);
    if (!mt->pixels)
        return NULL;
    size_t sz = (size_t)mt->w * (size_t)mt->h * 4;
    void *copy = malloc(sz);
    memcpy(copy, mt->pixels, sz);
    return copy;
}

/* Mock GL functions used by texture.c for private (<save>) textures */
void glBindTexture(GLenum target, GLuint tex) { (void)target; (void)tex; }
void glGetTexParameteriv(GLenum target, GLenum pname, GLint *params) {
    (void)target; (void)pname; *params = 0;
}
void glTexParameteri(GLenum target, GLenum pname, GLint param) {
    (void)target; (void)pname; (void)param;
}
void glGetIntegerv(GLenum pname, GLint *data) { (void)pname; *data = 0; }

/* Unused engine stubs required to link parts.c */
void gfx_init_texture_rgba(struct texture *t, int w, int h, SDL_Color c) {
    (void)c; gfx_init_texture_with_pixels(t, w, h, NULL);
}
void gfx_init_texture_blank(struct texture *t, int w, int h) {
    gfx_init_texture_with_pixels(t, w, h, NULL);
}
Texture *gfx_main_surface(void) { static Texture t = {1, 64, 64, true, 1}; return &t; }
int gfx_save_texture(Texture *t, const char *p, enum cg_type f) { (void)t;(void)p;(void)f; return 0; }
void gfx_begin_draw_target(Texture *t) { (void)t; }
void gfx_end_draw_target(void) {}
void gfx_copy_with_alpha_map(Texture *d,int dx,int dy,Texture *s,int sx,int sy,int w,int h) {
    (void)d;(void)dx;(void)dy;(void)s;(void)sx;(void)sy;(void)w;(void)h;
}
void gfx_copy_stretch_with_alpha_map(Texture *d,int dx,int dy,int dw,int dh,Texture *s,int sx,int sy,int sw,int sh) {
    (void)d;(void)dx;(void)dy;(void)dw;(void)dh;(void)s;(void)sx;(void)sy;(void)sw;(void)sh;
}
void gfx_fill_amap(Texture *d,int x,int y,int w,int h,int a) { (void)d;(void)x;(void)y;(void)w;(void)h;(void)a; }
float gfx_size_char(struct text_style *ts, const char *ch) { (void)ts;(void)ch; return 8.0f; }
float gfx_render_textf(Texture *d,float x,int y,char *msg,struct text_style *ts,bool b) {
    (void)d;(void)x;(void)y;(void)msg;(void)ts;(void)b; return 0.0f;
}
char *savedir_path(const char *p) { return strdup(p); }
bool scene_is_dirty = false;
void scene_register_sprite(struct sprite *sp) { (void)sp; }
void scene_unregister_sprite(struct sprite *sp) { (void)sp; }
bool scene_sprite_check_z_order(struct sprite *sp, int z, int z2) { (void)sp;(void)z;(void)z2; return true; }
const char *display_sjis0(const char *s) { return s ? s : ""; }
const char *display_utf0(const char *s) { return s ? s : ""; }
int sact_init(int a, enum sprite_engine_type b) { (void)a;(void)b; return 1; }
void sact_ModuleFini(void) {}
int sact_SP_GetUnuseNum(int n) { (void)n; return 1; }
struct sact_sprite *sact_create_sprite(int a,int b,int c,int d,int e,int f,int g) {
    (void)a;(void)b;(void)c;(void)d;(void)e;(void)f;(void)g; return NULL;
}
int sact_SP_Delete(int n) { (void)n; return 1; }
struct sact_sprite *sact_try_get_sprite(int n) { (void)n; return NULL; }
struct texture *sprite_get_texture(struct sact_sprite *s) { (void)s; return NULL; }
void audio_update(void) {}
void movie_free(struct movie_context *mc) { (void)mc; }
bool movie_draw(struct movie_context *mc, struct sact_sprite *sp) { (void)mc;(void)sp; return false; }
int ReignEngine_create_plugin(enum RE_plugin_version t) { (void)t; return -1; }
bool ReignEngine_BindPlugin(int h, int s) { (void)h;(void)s; return false; }
bool ReignEngine_ReleasePlugin(int h) { (void)h; return false; }
struct page *array_pushback(struct page *p, union vm_value v, enum ain_data_type t, int s) {
    (void)v;(void)t;(void)s; return p;
}
struct page *alloc_page(enum page_type t, int idx, int nr) { (void)t;(void)idx;(void)nr; return NULL; }
void delete_page_vars(struct page *p) { (void)p; }
void free_page(struct page *p) { (void)p; }
void parts_render_init(void) {}
void parts_render_update(void) {}
float parts_message_window_mul_color_rate = 1.0f;
float parts_message_window_alpha_rate = 1.0f;
void parts_text_end_session(void) {}
bool parts_is_dirty(void) { return false; }
void parts_engine_dirty(void) {}
void parts_engine_clean(void) {}
void parts_dirty(struct parts *p) { (void)p; }
void parts_sprite_render(struct sprite *sp) { (void)sp; }
bool parts_sprite_is_fullscreen_opaque(struct sprite *sp) { (void)sp; return false; }
bool parts_cg_on_screen(struct parts *p, struct parts_common *c) { (void)p;(void)c; return false; }
void parts_render(struct parts *p) { (void)p; }
void parts_debug_init(void) {}
cJSON *parts_sprite_to_json(struct sprite *sp, bool v) { (void)sp;(void)v; return NULL; }
void parts_clear_motion(struct parts *p) { (void)p; }
void parts_input_reset_part(struct parts *p) { (void)p; }
void parts_input_reset(void) {}
bool parts_input_is_busy(void) { return false; }
void PE_UpdateInputState(int t) { (void)t; }
void PE_ClearBackScene(void) {}
struct parts_context *parts_get_back_scene(void) { return NULL; }
void PE_ReleaseActivities(void) {}
void PE_ReleaseMessage(void) {}
void parts_cproc_cache_clear(void) {}
bool parts_clear_construction_process(struct parts_construction_process *c) { (void)c; return true; }
bool parts_build_construction_process(struct parts *p, struct parts_construction_process *c) { (void)p;(void)c; return true; }
void parts_flat_cache_clear(void) {}
void parts_flat_free(struct parts_flat *f) { (void)f; }
bool parts_flat_update(struct parts_flat *f, int t) { (void)f;(void)t; return false; }
void parts_flat_recover_widget_skin(struct parts_flat *f, bool s) { (void)f;(void)s; }
void parts_flash_free(struct parts_flash *f) { (void)f; }
bool parts_flash_update(struct parts_flash *f, int t) { (void)f;(void)t; return false; }
void parts_text_free(struct parts_text *t) { (void)t; }
void parts_text_rerender_all(struct parts *p, struct parts_text *t) { (void)p;(void)t; }
struct text_style parts_get_effective_text_style(const struct text_style *s) { return *s; }
void parts_widget_free(struct parts *p) { (void)p; }
void parts_widget_get_size(struct parts *p, int *w, int *h) { (void)p; *w = 0; *h = 0; }
void parts_widget_rebuild_label(struct parts *p) { (void)p; }
void parts_widget_restore_plain_skin(struct parts *p, struct parts_state *s, int i, bool c) { (void)p;(void)s;(void)i;(void)c; }
void parts_panel_free(struct parts *p) { (void)p; }
void parts_panel_rebuild(struct parts *p) { (void)p; }
void parts_scrollbar_free(struct parts *p) { (void)p; }
void parts_scrollbar_rebuild(struct parts *p) { (void)p; }
bool parts_scrollbar_update(struct parts *p, int e) { (void)p;(void)e; return false; }
void parts_scrollbar_update_links(void) {}
void parts_scrollbar_notify_changes(void) {}
void parts_message_window_free(struct parts *p) { (void)p; }
void parts_message_window_rebuild_text(struct parts *p) { (void)p; }
bool parts_message_window_update(struct parts *p, int t) { (void)p;(void)t; return false; }
struct parts_state *parts_display_state(struct parts *p) { return &p->states[p->state]; }
void parts_do_layout(struct parts *p) { (void)p; }
void parts_get_layout_size(struct parts *p, int *w, int *h) { (void)p; *w = 0; *h = 0; }
void parts_update_layout_tree(struct parts *p) { (void)p; }
void PE_SetSpecialComponentType(int n, int t) { (void)n;(void)t; }
int PE_GetSpecialComponentType(int n) { (void)n; return -1; }
struct string *string_format(struct string *f, union vm_value a, enum string_format_type t) {
    (void)f;(void)a;(void)t; return make_string("", 0);
}
bool asset_exists(enum asset_type t, int no) { (void)t;(void)no; return true; }
bool asset_exists_by_name(enum asset_type t, const char *n, int *id) { (void)t;(void)n; if (id) *id = 1; return true; }
static bool mock_cg_supports_name = true;
bool asset_cg_supports_name(void) { return mock_cg_supports_name; }
struct cg *cg_load_file(const char *path) { (void)path; return NULL; }
void cg_free(struct cg *cg) { if (cg) { free(cg->pixels); free(cg); } }

/* --- Thread-safe Mock CG Loader --- */
static atomic_int mock_decode_total = 0;
static atomic_int mock_in_flight = 0;
static atomic_int mock_max_concurrent = 0;
static atomic_int mock_async_delay_ms = 0;
static atomic_int mock_per_cg_decodes[64];

static struct cg *make_mock_cg(int w, int h, bool has_alpha, bool transparent_top_left) {
    struct cg *cg = calloc(1, sizeof(*cg));
    cg->metrics.w = w;
    cg->metrics.h = h;
    cg->metrics.has_pixel = true;
    cg->metrics.has_alpha = has_alpha;
    uint8_t *px = calloc((size_t)w * (size_t)h, 4);
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            size_t off = ((size_t)y * (size_t)w + (size_t)x) * 4;
            px[off + 0] = (uint8_t)(x * 7);
            px[off + 1] = (uint8_t)(y * 11);
            px[off + 2] = 0xCC;
            if (transparent_top_left && x < w / 2 && y < h / 2)
                px[off + 3] = 0;
            else
                px[off + 3] = 255;
        }
    }
    cg->pixels = px;
    return cg;
}

static void record_concurrency_enter(void) {
    int cur = atomic_fetch_add(&mock_in_flight, 1) + 1;
    int prev = atomic_load(&mock_max_concurrent);
    while (cur > prev && !atomic_compare_exchange_weak(&mock_max_concurrent, &prev, cur)) {}
    int delay = atomic_load(&mock_async_delay_ms);
    if (delay > 0)
        SDL_Delay((Uint32)delay);
}

static void record_concurrency_leave(void) {
    atomic_fetch_sub(&mock_in_flight, 1);
}

struct cg *asset_cg_load(int no) {
    atomic_fetch_add(&mock_decode_total, 1);
    if (no == 9001)
        return make_mock_cg(32, 32, true, true);
    if (no == 9999)
        return NULL;
    return make_mock_cg(64, 64, true, false);
}

struct cg *asset_cg_load_by_name(const char *name, int *id_out) {
    assert(mock_cg_supports_name && "asset_cg_load_by_name must not be called when ASSET_CG lacks name lookup (ALD)");
    record_concurrency_enter();
    atomic_fetch_add(&mock_decode_total, 1);
    struct cg *res = NULL;
    int id = 0;
    if (!strcmp(name, "missing_cg") || !strcmp(name, "9001")) {
        res = NULL;
    } else if (!strcmp(name, "a") || !strcmp(name, "bg") || !strcmp(name, "<save")) {
        id = 8100 + (unsigned char)name[0];
        res = make_mock_cg(16, 16, true, false);
    } else if (!strcmp(name, "ult_frame_01")) {
        id = 1001;
        res = make_mock_cg(64, 64, true, false);
    } else if (!strncmp(name, "evict_", 6)) {
        id = 2000 + (unsigned char)name[6];
        res = make_mock_cg(64, 64, true, false);
    } else if (!strncmp(name, "<save>", 6)) {
        id = 0x7f000001;
        res = make_mock_cg(32, 32, true, false);
    } else if (!strcmp(name, "shared_ui")) {
        id = 3001;
        res = make_mock_cg(48, 48, true, false);
    } else if (!strcmp(name, "hero_cg")) {
        id = 4001;
        res = make_mock_cg(40, 40, true, false);
    } else if (!strcmp(name, "idle_cg")) {
        id = 4002;
        res = make_mock_cg(40, 40, true, false);
    } else if (!strcmp(name, "alpha_btn")) {
        id = 5001;
        res = make_mock_cg(16, 16, true, true);
    } else if (!strcmp(name, "alpha_fallback")) {
        id = 5002;
        res = make_mock_cg(16, 16, true, true);
    } else if (!strcmp(name, "opaque_bg")) {
        id = 5003;
        res = make_mock_cg(16, 16, false, false);
    } else if (!strcmp(name, "override_sync_cg")) {
        id = 6001;
        res = make_mock_cg(32, 32, true, false);
    } else if (!strncmp(name, "async_cg_", 9)) {
        int k = atoi(name + 9);
        assert(k >= 0 && k < 64);
        atomic_fetch_add(&mock_per_cg_decodes[k], 1);
        id = 7000 + k;
        res = make_mock_cg(32, 32, true, false);
    } else {
        id = 8000;
        res = make_mock_cg(32, 32, true, false);
    }
    if (id_out)
        *id_out = id;
    record_concurrency_leave();
    return res;
}

/* Include REAL production parts.c and texture.c directly! */
#include "parts/parts.c"
#include "parts/texture.c"

static void reset_counters(void) {
    atomic_store(&mock_decode_total, 0);
    atomic_store(&mock_in_flight, 0);
    atomic_store(&mock_max_concurrent, 0);
    atomic_store(&mock_async_delay_ms, 0);
    for (int i = 0; i < 64; i++)
        atomic_store(&mock_per_cg_decodes[i], 0);
    mock_upload_count = 0;
    mock_delete_count = 0;
    mock_get_pixels_calls = 0;
    mock_get_pixels_fail = false;
    mock_remap_fail_all = false;
}

static void test_shared_cache_lru_suspend_gl_reset(void) {
    reset_counters();
    PE_Init();
    parts_v2 = true;

    struct string *s_ult = make_string("ult_frame_01", 12);
    assert(PE_SetPartsCG(100, s_ult, 0, 1));
    assert(PE_SetPartsCG(101, s_ult, 0, 1));
    assert(PE_SetPartsCG(102, s_ult, 0, 1));

    assert(atomic_load(&mock_decode_total) == 1);
    assert(mock_upload_count == 1);
    assert(mock_delete_count == 0);
    assert(mock_live_textures == 1);

    struct parts *p100 = parts_try_get(100);
    struct parts *p101 = parts_try_get(101);
    struct parts *p102 = parts_try_get(102);
    assert(p100 && p101 && p102);
    GLuint h = p100->states[0].common.texture.handle;
    assert(h != 0);
    assert(p101->states[0].common.texture.handle == h);
    assert(p102->states[0].common.texture.handle == h);
    struct parts_cg_asset *asset = p100->states[0].cg.cg_asset;
    assert(asset != NULL);
    assert(p101->states[0].cg.cg_asset == asset);
    assert(p102->states[0].cg.cg_asset == asset);
    assert(asset->refcount == 3);
    assert(cg_asset_idle_count == 0);

    // Re-setting same CG on same part is O(1) no-op
    assert(PE_SetPartsCG(100, s_ult, 0, 1));
    assert(asset->refcount == 3);
    assert(atomic_load(&mock_decode_total) == 1);
    assert(mock_upload_count == 1);
    assert(mock_delete_count == 0);

    // Releasing 2 of 3 parts keeps shared texture live
    PE_ReleaseParts(100);
    assert(asset->refcount == 2 && mock_delete_count == 0 && cg_asset_idle_count == 0);
    PE_ReleaseParts(101);
    assert(asset->refcount == 1 && mock_delete_count == 0 && cg_asset_idle_count == 0);

    // Releasing 3rd part moves asset to idle LRU without deleting texture
    PE_ReleaseParts(102);
    assert(asset->refcount == 0 && mock_delete_count == 0);
    assert(cg_asset_idle_count == 1);
    assert(cg_asset_idle_vram_bytes == 64u * 64u * 4u);

    // Reloading hits idle LRU (zero decode, zero upload, same handle)
    assert(PE_SetPartsCG(103, s_ult, 0, 1));
    assert(atomic_load(&mock_decode_total) == 1);
    assert(mock_upload_count == 1);
    assert(cg_asset_idle_count == 0);
    assert(parts_try_get(103)->states[0].common.texture.handle == h);
    assert(asset->refcount == 1);

    // By-index load for same resolved cg_no also shares the exact same asset!
    assert(PE_SetPartsCG_by_index(104, 1001, 0, 1));
    assert(atomic_load(&mock_decode_total) == 1);
    assert(mock_upload_count == 1);
    assert(parts_try_get(104)->states[0].common.texture.handle == h);
    assert(asset->refcount == 2);

    PE_ReleaseParts(103);
    PE_ReleaseParts(104);
    PE_FlushCGCache();
    assert(cg_asset_idle_count == 0 && cg_asset_idle_vram_bytes == 0);
    assert(mock_live_textures == 0 && mock_delete_count == 1);

    // VRAM byte-budget LRU eviction: allow at most 2 * (64*64*4) bytes idle
    size_t saved_budget = parts_cg_idle_vram_budget;
    parts_cg_idle_vram_budget = 2u * 64u * 64u * 4u;
    struct string *sa = make_string("evict_A", 7);
    struct string *sb = make_string("evict_B", 7);
    struct string *sc = make_string("evict_C", 7);
    int dec0 = atomic_load(&mock_decode_total);
    int del0 = mock_delete_count;
    assert(PE_SetPartsCG(105, sa, 0, 1)); PE_ReleaseParts(105);
    assert(PE_SetPartsCG(106, sb, 0, 1)); PE_ReleaseParts(106);
    assert(cg_asset_idle_count == 2 && mock_delete_count == del0);
    // 3rd idle entry evicts oldest ("evict_A")
    assert(PE_SetPartsCG(107, sc, 0, 1)); PE_ReleaseParts(107);
    assert(cg_asset_idle_count == 2 && mock_delete_count == del0 + 1);
    assert(atomic_load(&mock_decode_total) == dec0 + 3);
    // "evict_B" is still cached (hit), "evict_A" was evicted (miss)
    assert(PE_SetPartsCG(108, sb, 0, 1));
    assert(atomic_load(&mock_decode_total) == dec0 + 3);
    assert(PE_SetPartsCG(109, sa, 0, 1));
    assert(atomic_load(&mock_decode_total) == dec0 + 4);
    PE_ReleaseParts(108);
    PE_ReleaseParts(109);
    parts_cg_idle_vram_budget = saved_budget;
    PE_FlushCGCache();
    assert(mock_live_textures == 0);

    // <save> CGs must NOT be shared/cached
    struct string *ssave = make_string("<save>slot01.qnt", 16);
    int dec_s = atomic_load(&mock_decode_total);
    assert(PE_SetPartsCG(110, ssave, 0, 1));
    assert(PE_SetPartsCG(111, ssave, 0, 1));
    assert(atomic_load(&mock_decode_total) == dec_s + 2);
    assert(parts_try_get(110)->states[0].cg.cg_asset == NULL);
    assert(parts_try_get(111)->states[0].cg.cg_asset == NULL);
    assert(parts_try_get(110)->states[0].common.texture.handle !=
           parts_try_get(111)->states[0].common.texture.handle);
    PE_ReleaseParts(110);
    PE_ReleaseParts(111);
    assert(mock_live_textures == 0);

    // PE_SuspendTexture / PE_ResumeTexture with shared parts_cg_asset
    struct string *sui = make_string("shared_ui", 9);
    int dec_ui = atomic_load(&mock_decode_total);
    int del_ui = mock_delete_count;
    assert(PE_SetPartsCG(120, sui, 0, 1));
    assert(PE_SetPartsCG(121, sui, 0, 1));
    GLuint ui_h = parts_try_get(120)->states[0].common.texture.handle;
    assert(parts_try_get(121)->states[0].common.texture.handle == ui_h);
    PE_SuspendTexture(120);
    // Suspending part 120 must NOT delete shared handle still used by part 121!
    assert(mock_delete_count == del_ui);
    assert(parts_try_get(120)->states[0].common.texture.handle == 0);
    assert(parts_try_get(120)->states[0].cg.cg_asset == NULL);
    assert(parts_try_get(121)->states[0].common.texture.handle == ui_h);
    assert(parts_try_get(121)->states[0].cg.cg_asset->refcount == 1);
    PE_SuspendTexture(121);
    assert(cg_asset_idle_count == 1 && mock_delete_count == del_ui);
    // Resume hits idle LRU with zero re-decode
    PE_ResumeTexture(120);
    assert(atomic_load(&mock_decode_total) == dec_ui + 1);
    assert(parts_try_get(120)->states[0].common.texture.handle == ui_h);
    // Suspend again, flush cache, then resume -> cleanly reloads from archive
    PE_SuspendTexture(120);
    PE_FlushCGCache();
    assert(mock_live_textures == 0);
    PE_ResumeTexture(120);
    assert(atomic_load(&mock_decode_total) == dec_ui + 2);
    assert(parts_try_get(120)->states[0].common.texture.handle != 0);
    PE_ReleaseParts(120);
    PE_ReleaseParts(121);
    PE_FlushCGCache();
    assert(mock_live_textures == 0);

    // GL context reset: shared active asset reloads ONCE, idle asset evicted without reload
    struct string *shero = make_string("hero_cg", 7);
    struct string *sidle = make_string("idle_cg", 7);
    assert(PE_SetPartsCG(130, shero, 0, 1));
    assert(PE_SetPartsCG(131, shero, 0, 1));
    assert(PE_SetPartsCG(132, sidle, 0, 1));
    PE_ReleaseParts(132); // sidle now in idle LRU
    GLuint old_hero_h = parts_try_get(130)->states[0].common.texture.handle;
    int dec_rst = atomic_load(&mock_decode_total);
    // Simulate GL context loss (bump ctx_gen, remap fails)
    mock_ctx_gen++;
    mock_remap_fail_all = true;
    // Mark old textures dead as if GL context was destroyed
    for (GLuint i = 1; i < next_tex_handle; i++) {
        if (mock_textures[i].alive) {
            mock_textures[i].alive = false;
            free(mock_textures[i].pixels);
            mock_textures[i].pixels = NULL;
            mock_live_textures--;
        }
    }
    assert(registered_reset_cb != NULL);
    registered_reset_cb(registered_reset_user);
    mock_remap_fail_all = false;
    // hero_cg reloaded ONCE (not twice for 2 parts!), idle_cg evicted without reload!
    assert(atomic_load(&mock_decode_total) == dec_rst + 1);
    assert(cg_asset_idle_count == 0);
    GLuint new_hero_h = parts_try_get(130)->states[0].common.texture.handle;
    assert(new_hero_h != 0 && new_hero_h != old_hero_h);
    assert(parts_try_get(131)->states[0].common.texture.handle == new_hero_h);
    assert(parts_try_get(130)->states[0].cg.cg_asset == parts_try_get(131)->states[0].cg.cg_asset);
    assert(parts_try_get(130)->states[0].cg.cg_asset->refcount == 2);

    free_string(s_ult); free_string(sa); free_string(sb); free_string(sc);
    free_string(ssave); free_string(sui); free_string(shero); free_string(sidle);

    // parts_end_session frees everything cleanly
    parts_end_session();
    assert(TAILQ_EMPTY(&cg_asset_all));
    assert(TAILQ_EMPTY(&cg_asset_idle_lru));
    assert(cg_asset_idle_count == 0);
    assert(cg_asset_idle_vram_bytes == 0);
    assert(mock_live_textures == 0);
}

static void test_lazy_alpha_mask(void) {
    reset_counters();
    PE_Init();
    parts_v2 = true;

    struct string *s_alpha = make_string("alpha_btn", 9);
    struct string *s_opaque = make_string("opaque_bg", 9);
    struct string *s_fb = make_string("alpha_fallback", 14);

    assert(PE_SetPartsCG(200, s_alpha, 0, 1));
    assert(PE_SetPartsCG(201, s_alpha, 0, 1));
    struct parts *p200 = parts_try_get(200);
    struct parts *p201 = parts_try_get(201);
    struct parts_cg_asset *asset = p200->states[0].cg.cg_asset;

    // Must NOT eagerly scan pixels or build alpha_mask during SetPartsCG!
    assert(asset != NULL);
    assert(asset->alpha_mask_built == false);
    assert(asset->alpha_mask == NULL);
    assert(p200->states[0].common.alpha_mask == NULL);
    assert(mock_get_pixels_calls == 0);

    // First hit-test builds shared 1-bit alpha_mask ONCE
    assert(parts_common_check_alpha(&p200->states[0], 0, 0) == false);  // top-left transparent
    assert(asset->alpha_mask_built == true);
    assert(asset->alpha_mask != NULL);
    assert(asset->alpha_w == 16 && asset->alpha_h == 16);
    assert(mock_get_pixels_calls == 1);

    // Subsequent hit-tests across BOTH parts reuse the shared mask (no extra readback)
    assert(parts_common_check_alpha(&p200->states[0], 7, 7) == false);
    assert(parts_common_check_alpha(&p200->states[0], 8, 8) == true);
    assert(parts_common_check_alpha(&p201->states[0], 2, 3) == false);
    assert(parts_common_check_alpha(&p201->states[0], 15, 15) == true);
    assert(parts_common_check_alpha(&p201->states[0], -1, 0) == false);
    assert(parts_common_check_alpha(&p201->states[0], 16, 0) == false);
    assert(mock_get_pixels_calls == 1);

    // Opaque CG (has_alpha == false): zero GPU readback, zero mask allocation!
    assert(PE_SetPartsCG(202, s_opaque, 0, 1));
    struct parts *p202 = parts_try_get(202);
    struct parts_cg_asset *op_asset = p202->states[0].cg.cg_asset;
    assert(op_asset->alpha_mask_built == false);
    assert(parts_common_check_alpha(&p202->states[0], 0, 0) == true);
    assert(op_asset->alpha_mask_built == true);
    assert(op_asset->alpha_w == -1 && op_asset->alpha_mask == NULL);
    assert(mock_get_pixels_calls == 1);

    // Fallback path when gfx_get_pixels returns NULL (e.g. headless): reloads CG once
    assert(PE_SetPartsCG(203, s_fb, 0, 1));
    struct parts *p203 = parts_try_get(203);
    int dec_before = atomic_load(&mock_decode_total);
    mock_get_pixels_fail = true;
    assert(parts_common_check_alpha(&p203->states[0], 1, 1) == false);
    assert(parts_common_check_alpha(&p203->states[0], 12, 12) == true);
    mock_get_pixels_fail = false;
    assert(atomic_load(&mock_decode_total) == dec_before + 1);

    free_string(s_alpha); free_string(s_opaque); free_string(s_fb);
    parts_end_session();
    assert(mock_live_textures == 0);
}

static void test_async_worker_pool_dedup_cancel_sync_collision(void) {
    reset_counters();
    PE_Init();
    parts_v2 = true;

    // 4ms simulated CPU decode delay so worker threads overlap reliably
    atomic_store(&mock_async_delay_ms, 4);

    assert(PE_IsExistWorkerThread() == false);

    // Enqueue 64 async requests across 32 unique CGs (2 parts per CG)
    for (int i = 0; i < 64; i++) {
        char buf[32];
        snprintf(buf, sizeof(buf), "async_cg_%02d", i % 32);
        struct string *s = make_string(buf, strlen(buf));
        assert(PE_SetPartsCGThread(300 + i, s, 1));
        free_string(s);
    }

    assert(PE_IsExistWorkerThread() == true);
    assert(PE_IsThreadLoading(363, 1) == true);

    // Cancel part 305 via PE_ReleaseParts and immediately recreate same parts_no!
    PE_ReleaseParts(305);
    assert(PE_IsThreadLoading(305, 1) == false);
    struct parts *recreated_305 = parts_get(305);
    assert(recreated_305->states[0].type == PARTS_UNINITIALIZED);

    // Overwrite part 306 with synchronous SetPartsCG while async job is in flight
    struct string *s_over = make_string("override_sync_cg", 16);
    assert(PE_SetPartsCG(306, s_over, 0, 1));
    assert(PE_IsThreadLoading(306, 1) == false);
    GLuint over_h = parts_try_get(306)->states[0].common.texture.handle;
    assert(over_h != 0);

    // Synchronous collision on an async CG ("async_cg_31" / "async_cg_10")!
    struct string *s_col1 = make_string("async_cg_31", 11);
    struct string *s_col2 = make_string("async_cg_10", 11);
    assert(PE_SetPartsCG(400, s_col1, 0, 1));
    assert(PE_SetPartsCG(401, s_col2, 0, 1));
    GLuint col1_h = parts_try_get(400)->states[0].common.texture.handle;
    GLuint col2_h = parts_try_get(401)->states[0].common.texture.handle;
    assert(col1_h != 0 && col2_h != 0);
    // Both async targets for async_cg_31 (331, 363) should already be attached or share col1_h!
    assert(PE_IsThreadLoading(331, 1) == false);
    assert(PE_IsThreadLoading(363, 1) == false);
    assert(parts_try_get(331)->states[0].common.texture.handle == col1_h);
    assert(parts_try_get(363)->states[0].common.texture.handle == col1_h);

    // Also test non-existent CG via async thread (must cleanly finish with IsThreadLoading -> false)
    struct string *s_miss = make_string("missing_cg", 10);
    assert(PE_SetPartsCGThread(402, s_miss, 1));

    // Drain until all worker jobs complete
    int polls = 0;
    while (PE_IsExistWorkerThread()) {
        PE_UpdateComponent(16);
        SDL_Delay(2);
        polls++;
        assert(polls < 2000 && "Async CG pool did not finish in time!");
    }

    // Verify true parallel execution across >= 2 worker threads
    assert(atomic_load(&mock_max_concurrent) >= 2);

    // Verify 100% deduplication: EVERY unique async_cg_00..31 decoded EXACTLY ONCE!
    for (int k = 0; k < 32; k++) {
        assert(atomic_load(&mock_per_cg_decodes[k]) == 1);
    }

    // Verify stale-generation rejection on released+recreated 305 and overwritten 306
    assert(parts_try_get(305)->states[0].type == PARTS_UNINITIALIZED);
    assert(parts_try_get(305)->states[0].common.texture.handle == 0);
    assert(parts_try_get(306)->states[0].common.texture.handle == over_h);
    assert(!strcmp(parts_try_get(306)->states[0].cg.name->text, "override_sync_cg"));

    // Verify missing CG completed cleanly
    assert(PE_IsThreadLoading(402, 1) == false);
    assert(parts_try_get(402)->states[0].common.texture.handle == 0);

    // Verify all remaining duplicate async pairs share identical texture handles
    for (int k = 0; k < 32; k++) {
        assert(PE_IsThreadLoading(300 + k, 1) == false);
        assert(PE_IsThreadLoading(300 + 32 + k, 1) == false);
        if (k == 5 || k == 6)
            continue;
        struct parts *a = parts_try_get(300 + k);
        struct parts *b = parts_try_get(300 + 32 + k);
        assert(a->states[0].common.texture.handle != 0);
        assert(a->states[0].common.texture.handle == b->states[0].common.texture.handle);
        assert(a->states[0].cg.cg_asset == b->states[0].cg.cg_asset);
    }

    // Cache-hit fast path on PE_SetPartsCGThread: immediate O(1), no worker queued
    struct string *s_hit = make_string("async_cg_00", 11);
    assert(PE_SetPartsCGThread(500, s_hit, 1));
    assert(PE_IsThreadLoading(500, 1) == false);
    assert(PE_IsExistWorkerThread() == false);
    assert(atomic_load(&mock_per_cg_decodes[0]) == 1);
    assert(parts_try_get(500)->states[0].common.texture.handle ==
           parts_try_get(300)->states[0].common.texture.handle);

    // PE_Reset shuts down worker pool cleanly and frees all textures; pool restarts cleanly!
    PE_Reset();
    assert(cg_async.initialized == false);
    assert(mock_live_textures == 0);

    atomic_store(&mock_async_delay_ms, 1);
    assert(PE_SetPartsCGThread(600, s_hit, 1));
    while (PE_IsExistWorkerThread()) {
        PE_UpdateComponent(16);
        SDL_Delay(1);
    }
    assert(parts_try_get(600)->states[0].common.texture.handle != 0);

    free_string(s_over); free_string(s_col1); free_string(s_col2);
    free_string(s_miss); free_string(s_hit);

    parts_end_session();
    assert(cg_async.initialized == false);
    assert(TAILQ_EMPTY(&cg_asset_all));
    assert(mock_live_textures == 0);
}

static void test_short_cg_names_and_string_index_resume(void) {
    reset_counters();
    mock_cg_supports_name = true;
    PE_Init();
    parts_v2 = true;

    // Short CG names (< 6 bytes, including "<save" prefix without closing '>') must never
    // over-read or be misclassified as <save> CGs across sync, async, and suspend/resume.
    const char *short_names[] = { "a", "bg", "<save" };
    for (int i = 0; i < 3; i++) {
        struct string *s = make_string(short_names[i], strlen(short_names[i]));
        assert(PE_SetPartsCG(700 + i, s, 0, 1));
        assert(parts_try_get(700 + i)->states[0].cg.cg_asset != NULL);
        GLuint h = parts_try_get(700 + i)->states[0].common.texture.handle;
        assert(h != 0);

        // Async path hits cache or loads safely without over-reading
        assert(PE_SetPartsCGThread(710 + i, s, 1));
        while (PE_IsExistWorkerThread()) {
            PE_UpdateComponent(16);
            SDL_Delay(1);
        }
        assert(parts_try_get(710 + i)->states[0].common.texture.handle == h);

        // Suspend + flush + resume recovers cleanly
        PE_SuspendTexture(700 + i);
        PE_SuspendTexture(710 + i);
        PE_FlushCGCache();
        PE_ResumeTexture(700 + i);
        assert(parts_try_get(700 + i)->states[0].common.texture.handle != 0);
        assert(parts_try_get(700 + i)->states[0].cg.cg_asset != NULL);
        free_string(s);
    }

    // PE_SetPartsCG_by_string_index (Rance Quest) suspend/resume on AFA:
    // cg->name is numeric string "9001" (not present by name in AFA) and cg->no is 9001.
    // Resume must fall back to asset_cg_load(9001) without blanking or caching under "9001".
    struct string *s_num = make_string("9001", 4);
    mock_cg_supports_name = true;
    assert(PE_SetPartsCG_by_string_index(720, s_num, 0, 1));
    assert(parts_try_get(720)->states[0].cg.no == 9001);
    assert(parts_try_get(720)->states[0].cg.name != NULL);
    PE_SuspendTexture(720);
    PE_FlushCGCache();
    assert(parts_try_get(720)->states[0].common.texture.handle == 0);
    PE_ResumeTexture(720);
    assert(parts_try_get(720)->states[0].common.texture.handle != 0);
    assert(parts_try_get(720)->states[0].cg.cg_asset != NULL);
    assert(parts_try_get(720)->states[0].cg.cg_asset->name == NULL);
    assert(parts_try_get(720)->states[0].cg.cg_asset->cg_no == 9001);

    // PE_SetPartsCG_by_string_index suspend/resume on ALD (asset_cg_supports_name() == false):
    // Resume MUST skip asset_cg_load_by_name (which would fatal-ERROR on ALD) and reload by ID!
    mock_cg_supports_name = false;
    assert(PE_SetPartsCG_by_string_index(721, s_num, 0, 1));
    PE_SuspendTexture(721);
    PE_ReleaseParts(720);
    PE_FlushCGCache();
    assert(parts_try_get(721)->states[0].common.texture.handle == 0);
    PE_ResumeTexture(721);
    assert(parts_try_get(721)->states[0].common.texture.handle != 0);
    assert(parts_try_get(721)->states[0].cg.cg_asset != NULL);
    assert(parts_try_get(721)->states[0].cg.cg_asset->name == NULL);
    assert(parts_try_get(721)->states[0].cg.cg_asset->cg_no == 9001);
    mock_cg_supports_name = true;

    free_string(s_num);
    parts_end_session();
    assert(TAILQ_EMPTY(&cg_asset_all));
    assert(mock_live_textures == 0);
}

int main(void) {
    test_shared_cache_lru_suspend_gl_reset();
    test_lazy_alpha_mask();
    test_async_worker_pool_dedup_cancel_sync_collision();
    test_short_cg_names_and_string_index_resume();
    puts("PARTS_CG_CACHE_ASYNC_OK");
    return 0;
}
'''

ASSET_THREAD_HARNESS = r'''
#include <assert.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <SDL.h>

#include "system4.h"
#include "system4/afa.h"
#include "system4/archive.h"
#include "system4/cg.h"
#include "system4/file.h"
#include "system4/string.h"
#include "xsystem4.h"
#include "asset_manager.h"

struct config config;

const char *display_utf0(const char *s) { return s ? s : ""; }
const char *display_sjis0(const char *s) { return s ? s : ""; }
char *gamedir_path(const char *path) { return path_join(config.game_dir, path); }
char *gamedir_path_icase(const char *path) { return path_join(config.game_dir, path); }
char *savedir_path(const char *path) { return path_join(config.game_dir, path); }
void save_cjk_canon_forms_buf(const char *s, char *sb, size_t sc, char **so,
        char *gb, size_t gc, char **go) {
    (void)s;(void)sb;(void)sc;(void)gb;(void)gc;
    if (so) *so = NULL;
    if (go) *go = NULL;
}
char *save_transcode_cstr_strict(const char *s, size_t n, bool sg, bool dg, size_t *ol) {
    (void)s;(void)n;(void)sg;(void)dg;(void)ol; return NULL;
}

#include "asset_manager.c"

#define NR_CGS 32
#define NR_WORKERS 4
#define ITERS_PER_WORKER 64

static atomic_int failures = 0;
static atomic_int cjk_ready = 0;
static atomic_int cjk_go = 0;
static atomic_int cjk_failures = 0;

static int cjk_race_worker(void *arg) {
    (void)arg;
    atomic_fetch_add(&cjk_ready, 1);
    while (!atomic_load(&cjk_go)) {}
    static const int gbk_cps[] = { 0x4e00, 0x9f99, 0x9fa0, 0x266a, 0x2764, 0x30fb };
    static const int sjis_cps[] = { 0x4e00, 0x3042, 0x9f8d, 0x6f22, 0x5b57 };
    for (int rep = 0; rep < 32; rep++) {
        for (size_t i = 0; i < sizeof(gbk_cps) / sizeof(gbk_cps[0]); i++) {
            int enc = cjk_encode_codepoint(gbk_cps[i], true);
            if (enc <= 0)
                atomic_fetch_add(&cjk_failures, 1);
        }
        for (size_t i = 0; i < sizeof(sjis_cps) / sizeof(sjis_cps[0]); i++) {
            int enc = cjk_encode_codepoint(sjis_cps[i], false);
            if (enc <= 0)
                atomic_fetch_add(&cjk_failures, 1);
        }
    }
    return 0;
}

static void test_concurrent_cjk_reverse_tables(void) {
    enum { N = 8 };
    SDL_Thread *threads[N];
    for (int i = 0; i < N; i++) {
        threads[i] = SDL_CreateThread(cjk_race_worker, "cjk-worker", NULL);
        assert(threads[i] != NULL);
    }
    while (atomic_load(&cjk_ready) < N)
        SDL_Delay(1);
    atomic_store(&cjk_go, 1);
    for (int i = 0; i < N; i++)
        SDL_WaitThread(threads[i], NULL);
    assert(atomic_load(&cjk_failures) == 0);
    // Spot-check gbk_extras and late-table entries initialized cleanly
    assert(cjk_encode_codepoint(0x266a, true) == 0xaaa1);
    assert(cjk_encode_codepoint(0x2764, true) == 0xaaa3);
    assert(cjk_encode_codepoint(0x9f99, true) > 0);
    assert(cjk_encode_codepoint(0x9f8d, false) > 0);
}

struct foreach_reenter_ctx {
    int count;
    int reenter_hits;
};

static void foreach_reenter_cb(struct archive_data *data, void *user) {
    struct foreach_reenter_ctx *ctx = user;
    ctx->count++;
    // Re-enter asset_manager inside ASSET_CG foreach callback (must not deadlock!)
    if (data && data->name && asset_exists_by_name(ASSET_CG, data->name, NULL))
        ctx->reenter_hits++;
}

static int worker_fn(void *arg) {
    int tid = (int)(intptr_t)arg;
    for (int iter = 0; iter < ITERS_PER_WORKER; iter++) {
        int idx = (tid * 7 + iter) % NR_CGS;
        char name[32];
        snprintf(name, sizeof(name), "cg_%02d", idx);
        int id = 0;
        struct cg *cg = asset_cg_load_by_name(name, &id);
        if (!cg || !cg->pixels || cg->metrics.w != 16 || cg->metrics.h != 16 || id <= 0) {
            atomic_fetch_add(&failures, 1);
            cg_free(cg);
            continue;
        }
        uint8_t *px = cg->pixels;
        uint8_t exp_r = (uint8_t)(idx * 7 + 10);
        uint8_t exp_g = (uint8_t)(idx * 5 + 20);
        uint8_t exp_b = (uint8_t)(idx * 3 + 30);
        uint8_t exp_a = (uint8_t)(200 + (idx % 50));
        if (px[0] != exp_r || px[1] != exp_g || px[2] != exp_b || px[3] != exp_a) {
            atomic_fetch_add(&failures, 1);
        }
        cg_free(cg);

        // Exercise locked base-CG lookups (both hit and miss) concurrently with workers
        if ((iter & 7) == 0) {
            struct cg *miss = cg_load_base_by_name("no_such_base_cg");
            if (miss) {
                atomic_fetch_add(&failures, 1);
                cg_free(miss);
            }
            struct cg *hit = cg_load_base_by_name("cg_00");
            if (!hit || hit->metrics.w != 16)
                atomic_fetch_add(&failures, 1);
            cg_free(hit);
        }
    }
    return 0;
}

static void run_parallel_stress(void) {
    SDL_Thread *threads[NR_WORKERS];
    for (int i = 0; i < NR_WORKERS; i++) {
        threads[i] = SDL_CreateThread(worker_fn, "am-worker", (void *)(intptr_t)i);
        assert(threads[i] != NULL);
    }
    // Concurrently run asset_foreach_file(ASSET_CG) with a re-entrant callback
    struct foreach_reenter_ctx fctx = {0};
    asset_foreach_file(ASSET_CG, foreach_reenter_cb, &fctx);
    assert(fctx.count == NR_CGS && fctx.reenter_hits == NR_CGS);
    // Main thread concurrently loads CGs too
    worker_fn((void *)(intptr_t)99);
    for (int i = 0; i < NR_WORKERS; i++)
        SDL_WaitThread(threads[i], NULL);
    assert(atomic_load(&failures) == 0);
}

int main(int argc, char **argv) {
    assert(argc == 2);
    test_concurrent_cjk_reverse_tables();

    memset(&config, 0, sizeof(config));
    config.game_dir = argv[1];
    config.ain_filename = "TestGame.ain";
    asset_manager_init();
    assert(asset_cg_supports_name() == true);

    // Pass 1: mmapped AFA (Linux/Android/iOS/macOS)
    run_parallel_stress();

    // Pass 2: force non-mmapped FILE* fseek/fread path (Switch/Windows)!
    struct asset_manager_afa *mgr = (struct asset_manager_afa *)assets[ASSET_CG];
    assert(mgr && mgr->nr_archives == 1);
    struct afa_archive *ar = mgr->archives[0];
    if (ar->ar.mmapped) {
        ar->f = fopen(ar->filename, "rb");
        assert(ar->f != NULL);
        ar->ar.mmapped = false;
    }
    run_parallel_stress();

    // Verify locked cg_load_base_by_id fallback on valid and missing IDs under non-mmapped FILE*
    struct cg *b_ok = cg_load_base_by_id(&ar->ar, 1);
    assert(b_ok && b_ok->metrics.w == 16 && b_ok->metrics.h == 16);
    cg_free(b_ok);
    struct cg *b_miss = cg_load_base_by_id(&ar->ar, 9999);
    assert(b_miss == NULL);

    puts("ASSET_MANAGER_MT_OK");
    return 0;
}
'''


class PartsCGCacheAndAsyncTests(unittest.TestCase):
    def test_shared_cg_asset_cache_lazy_alpha_and_async_worker_pool(self):
        with tempfile.TemporaryDirectory() as tmp:
            td = Path(tmp)
            # Provide an OpenGLES/ES3/gl.h shim so ALICEHUB_IOS parts_end_session()
            # compiles and runs on Linux hosts without Apple SDK headers.
            shim_dir = td / "OpenGLES" / "ES3"
            shim_dir.mkdir(parents=True)
            (shim_dir / "gl.h").write_text(
                "typedef unsigned int GLuint;\n"
                "typedef unsigned int GLenum;\n"
                "typedef int GLint;\n"
                "#define GL_TEXTURE_2D 0x0DE1\n"
                "#define GL_TEXTURE_MIN_FILTER 0x2801\n"
                "#define GL_TEXTURE_MAG_FILTER 0x2800\n"
                "#define GL_TEXTURE_WRAP_S 0x2802\n"
                "#define GL_TEXTURE_WRAP_T 0x2803\n"
                "#define GL_TEXTURE_BINDING_2D 0x8069\n"
                "void glBindTexture(GLenum, GLuint);\n"
                "void glGetTexParameteriv(GLenum, GLenum, GLint *);\n"
                "void glTexParameteri(GLenum, GLenum, GLint);\n"
                "void glGetIntegerv(GLenum, GLint *);\n",
                encoding="utf-8",
            )
            harness_c = td / "parts_cg_test.c"
            harness_c.write_text(PARTS_HARNESS, encoding="utf-8")
            exe = td / "parts_cg_test"
            cmd = [
                os.environ.get("CC", "cc"),
                "-std=gnu11",
                "-O2",
                "-Wall",
                "-Wextra",
                "-Wno-unused-parameter",
                f"-I{td}",
                f"-I{ROOT / 'include'}",
                f"-I{ROOT / 'src'}",
                f"-I{LIBSYS4_INC}",
                "-I/usr/include/cglm",
                str(harness_c),
                str(LIBSYS4_SRC / "hashtable.c"),
                str(LIBSYS4_SRC / "string.c"),
                str(LIBSYS4_SRC / "utfsjis.c"),
                str(LIBSYS4_SRC / "file.c"),
                str(LIBSYS4_SRC / "system.c"),
                *sdl2_flags(),
                "-lz",
                "-lm",
                "-o",
                str(exe),
            ]
            subprocess.run(cmd, check=True)
            out = subprocess.check_output([str(exe)], text=True)
            self.assertEqual(out.strip(), "PARTS_CG_CACHE_ASYNC_OK")

    def test_asset_manager_parallel_cg_decode_mmapped_and_file_io(self):
        with tempfile.TemporaryDirectory() as tmp:
            td = Path(tmp)
            entries = []
            for idx in range(32):
                rgba = ((idx * 7 + 10) & 0xFF, (idx * 5 + 20) & 0xFF, (idx * 3 + 30) & 0xFF, 200 + (idx % 50))
                qnt = make_qnt_v1(16, 16, rgba)
                entries.append((f"cg_{idx:02d}.qnt".encode("ascii"), qnt))
            (td / "TestGameCG.afa").write_bytes(make_afa_v2(entries))

            harness_c = td / "asset_mt_test.c"
            harness_c.write_text(ASSET_THREAD_HARNESS, encoding="utf-8")
            exe = td / "asset_mt_test"
            libsys4_files = [
                LIBSYS4_SRC / f"{name}.c"
                for name in (
                    "afa", "afa3", "ald", "archive", "buffer", "file", "hashtable",
                    "string", "system", "utfsjis", "zlib",
                    "cg", "qnt", "ajp", "png", "webp", "dcf", "pms", "jpeg", "pcf", "rou", "tga",
                )
            ]
            cmd = [
                os.environ.get("CC", "cc"),
                "-std=gnu11",
                "-O2",
                "-Wall",
                "-Wextra",
                "-Wno-unused-parameter",
                f"-I{ROOT / 'include'}",
                f"-I{ROOT / 'src'}",
                f"-I{LIBSYS4_INC}",
                str(harness_c),
                *(str(p) for p in libsys4_files),
                *sdl2_flags(),
                "-lz",
                "-lm",
                "-lpng16",
                "-lturbojpeg",
                "-lwebp",
                "-o",
                str(exe),
            ]
            subprocess.run(cmd, check=True)
            out = subprocess.check_output([str(exe), str(td)], text=True)
            self.assertEqual(out.strip(), "ASSET_MANAGER_MT_OK")


if __name__ == "__main__":
    unittest.main()
