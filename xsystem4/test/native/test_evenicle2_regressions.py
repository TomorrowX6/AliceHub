#!/usr/bin/env python3
"""Host C regression tests for Evenicle 2 compatibility fixes in xsystem4."""

import shutil
import struct
import subprocess
import tempfile
import unittest
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def _ex_encode_tables():
    decode = [0] * 256
    inv = [0] * 256
    for i in range(256):
        t = i
        bc = bin(i).count("1")
        if bc % 2 == 0:
            t = ((t << (8 - bc)) | (t >> bc)) & 0xFF
        else:
            t = ((t >> (8 - bc)) | (t << bc)) & 0xFF
        decode[i] = t
        inv[t] = i
    return bytes(inv)


_EX_ENC = _ex_encode_tables()


def _sjis(text: str) -> bytes:
    return text.encode("cp932")


def _ex_pstr(b: bytes) -> bytes:
    return struct.pack("<i", len(b)) + b


def _ex_leaf(name: bytes, val_type: int, payload: bytes) -> bytes:
    body = _ex_pstr(name) + payload
    return (
        _ex_pstr(name)
        + struct.pack("<II", 1, val_type)
        + struct.pack("<I", len(body))
        + body
        + struct.pack("<I", 0)
    )


def _ex_tree(name: bytes, children: list[bytes]) -> bytes:
    out = bytearray(_ex_pstr(name) + struct.pack("<II", 0, len(children)))
    for c in children:
        out.extend(c)
    return bytes(out)


def _ex_list(items: list[tuple[int, bytes]]) -> bytes:
    out = bytearray(struct.pack("<I", len(items)))
    for vtype, payload in items:
        out.extend(struct.pack("<II", vtype, len(payload)))
        out.extend(payload)
    return bytes(out)


def _ex_block(name: bytes, val_type: int, payload: bytes) -> bytes:
    body = _ex_pstr(name) + payload
    return struct.pack("<II", val_type, len(body)) + body


def build_extf(blocks: list[bytes]) -> bytes:
    raw = b"".join(blocks)
    comp = zlib.compress(raw)
    enc = bytes(_EX_ENC[b] for b in comp)
    return (
        b"HEAD\x00\x00\x00\x00"
        b"EXTF\x00\x00\x00\x00"
        + struct.pack("<I", len(blocks))
        + b"DATA"
        + struct.pack("<II", len(comp), len(raw))
        + enc
    )


def build_zlb(raw: bytes) -> bytes:
    comp = zlib.compress(raw)
    return b"ZLB\x00" + struct.pack("<II", 0, len(raw)) + struct.pack("<I", len(comp)) + comp


def build_afa_v2(entries: list[tuple[bytes, bytes]]) -> bytes:
    """Build a valid AFA v2 archive with explicit entry names and payloads."""
    info_raw = bytearray()
    data_buf = bytearray()
    data_off_rel = 8
    for idx, (name, payload) in enumerate(entries):
        padded = len(name)
        info_raw.extend(struct.pack("<ii", len(name), padded))
        info_raw.extend(name)
        info_raw.extend(struct.pack("<iiiii", idx + 1, 0, 0, data_off_rel + len(data_buf), len(payload)))
        data_buf.extend(payload)

    info_comp = zlib.compress(bytes(info_raw))
    data_start = 44 + len(info_comp)
    header = (
        b"AFAH"
        + struct.pack("<I", 0x1C)
        + b"AlicArch"
        + struct.pack("<III", 2, 0, data_start)
        + b"INFO"
        + struct.pack("<III", len(info_comp) + 16, len(info_raw), len(entries))
    )
    data_hdr = b"DATA" + struct.pack("<I", 8 + len(data_buf))
    return header + info_comp + data_hdr + bytes(data_buf)


def sdl_cflags() -> list[str]:
    return subprocess.check_output(["pkg-config", "--cflags", "sdl2"], text=True).split()


def compile_and_run(c_src: str, extra_sources: list[Path], extra_flags: list[str] | None = None, args: list[str] | None = None):
    cc = shutil.which("cc") or shutil.which("gcc")
    assert cc, "C compiler required"
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        src_path = tmp / "harness.c"
        bin_path = tmp / "harness"
        src_path.write_text(c_src, encoding="utf-8")
        cmd = [
            cc,
            "-std=c11",
            "-O2",
            "-Wall",
            "-Wextra",
            "-Wno-unused-parameter",
            "-Wno-unused-function",
            "-Wno-sign-compare",
            "-Wno-type-limits",
            "-D_DEFAULT_SOURCE",
            "-DUSE_GLES",
            f"-I{ROOT / 'include'}",
            f"-I{ROOT / 'src'}",
            f"-I{ROOT / 'src' / 'hll'}",
            f"-I{ROOT / 'src' / 'parts'}",
            f"-I{ROOT / 'subprojects' / 'libsys4' / 'include'}",
            *sdl_cflags(),
            str(src_path),
            *[str(p) for p in extra_sources],
            *(extra_flags or []),
            "-o",
            str(bin_path),
        ]
        res_c = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if res_c.returncode != 0:
            raise AssertionError(f"Compile failed ({res_c.returncode}):\n{res_c.stderr}")
        res_r = subprocess.run([str(bin_path), *(args or [])], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if res_r.returncode != 0:
            raise AssertionError(f"Run failed ({res_r.returncode}):\nstdout:\n{res_r.stdout}\nstderr:\n{res_r.stderr}")
        return res_r.stdout


LIBSYS4_CORE = [
    ROOT / "subprojects/libsys4/src/buffer.c",
    ROOT / "subprojects/libsys4/src/hashtable.c",
    ROOT / "subprojects/libsys4/src/string.c",
    ROOT / "subprojects/libsys4/src/system.c",
    ROOT / "subprojects/libsys4/src/utfsjis.c",
    ROOT / "subprojects/libsys4/src/file.c",
    ROOT / "subprojects/libsys4/src/ex.c",
    ROOT / "subprojects/libsys4/src/archive.c",
    ROOT / "subprojects/libsys4/src/afa.c",
    ROOT / "subprojects/libsys4/src/afa3.c",
    ROOT / "subprojects/libsys4/src/aar.c",
    ROOT / "subprojects/libsys4/src/zlib.c",
    ROOT / "subprojects/libsys4/src/ain.c",
    ROOT / "subprojects/libsys4/src/instructions.c",
    ROOT / "subprojects/libsys4/src/mt19937int.c",
]


class TestEvenicle2Regressions(unittest.TestCase):
    def test_hacks_detection_and_default_resolution(self):
        harness = r"""
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "xsystem4.h"
#include "system4/ain.h"

void apply_game_specific_hacks(struct ain *ain);

struct config config;
void message_layout_init_rance03(void) {}

int main(void) {
    struct ain *ain = ain_new(14, 0);
    ain_add_function(ain, "C_MedicaMenu@0");

    config.view_width = 800;
    config.view_height = 600;
    apply_game_specific_hacks(ain);
    assert(game_evenicle2 == true);
    assert(config.view_width == 1280);
    assert(config.view_height == 720);

    // Explicit non-default viewport is preserved.
    config.view_width = 1920;
    config.view_height = 1080;
    apply_game_specific_hacks(ain);
    assert(game_evenicle2 == true);
    assert(config.view_width == 1920);
    assert(config.view_height == 1080);
    ain_free(ain);

    // Non-Evenicle2 ain does not set game_evenicle2.
    struct ain *ain_v12 = ain_new(12, 0);
    ain_add_function(ain_v12, "C_MedicaMenu@0");
    config.view_width = 800;
    config.view_height = 600;
    apply_game_specific_hacks(ain_v12);
    assert(game_evenicle2 == false);
    assert(config.view_width == 800);
    assert(config.view_height == 600);
    ain_free(ain_v12);

    struct ain *ain_other = ain_new(14, 0);
    ain_add_function(ain_other, "OtherGame@0");
    apply_game_specific_hacks(ain_other);
    assert(game_evenicle2 == false);
    ain_free(ain_other);

    puts("OK");
    return 0;
}
"""
        out = compile_and_run(harness, [ROOT / "src/hacks.c", ROOT / "src/escalayer_reboot_cn_ui.c", *LIBSYS4_CORE], ["-lz", "-lm"])
        self.assertEqual(out.strip(), "OK")

    def test_kiwi_v12_channels_and_audio_mixer_non_nested_voice(self):
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sndfile.h>
#include <SDL.h>

#include "xsystem4.h"
#include "asset_manager.h"
#include "audio.h"
#include "id_pool.h"
#include "mixer.h"
#include "system4/archive.h"
#include "system4/string.h"
#include "hll/hll.h"

struct config config;
bool game_rance10 = false;
bool game_dohnadohna = false;
bool game_evenicle2 = true;

int SDL_InitSubSystem(Uint32 flags) { return 0; }
SDL_AudioDeviceID SDL_OpenAudioDevice(const char *dev, int iscap, const SDL_AudioSpec *desired, SDL_AudioSpec *obtained, int allowed) {
    if (obtained) *obtained = *desired;
    return 2;
}
void SDL_PauseAudioDevice(SDL_AudioDeviceID dev, int pause_on) {}
void SDL_CloseAudioDevice(SDL_AudioDeviceID dev) {}
void SDL_LockAudioDevice(SDL_AudioDeviceID dev) {}
void SDL_UnlockAudioDevice(SDL_AudioDeviceID dev) {}
const char *SDL_GetError(void) { return ""; }
Uint32 SDL_GetTicks(void) { return 0; }

SNDFILE *sf_open(const char *p, int m, SF_INFO *i) { return NULL; }
SNDFILE *sf_open_virtual(SF_VIRTUAL_IO *v, int m, SF_INFO *i, void *u) { return NULL; }
int sf_close(SNDFILE *s) { return 0; }
int sf_error(SNDFILE *s) { return 0; }
const char *sf_strerror(SNDFILE *s) { return ""; }
sf_count_t sf_seek(SNDFILE *s, sf_count_t f, int w) { return 0; }
sf_count_t sf_readf_float(SNDFILE *s, float *p, sf_count_t f) { return 0; }

bool asset_exists_by_name(enum asset_type t, const char *n, int *id) { return false; }
struct archive_data *asset_get(enum asset_type t, int no) { return NULL; }
struct archive_data *asset_get_by_name(enum asset_type t, const char *n, int *id_out) { return NULL; }
void asset_foreach_file(enum asset_type t, void (*cb)(struct archive_data*, void*), void *u) {}
void bgi_read(const char *path) {}
struct bgi *bgi_get(int no) { return NULL; }
void wai_load(const char *path) {}
struct wai *wai_get(int no) { return NULL; }
const char *display_sjis0(const char *s) { return s ? s : ""; }
char *gamedir_path_icase(const char *p) { return strdup(p); }
void audio_init(void) {}
void id_pool_init(struct id_pool *p, int b) {}
void id_pool_delete(struct id_pool *p) {}
int id_pool_get_first(struct id_pool *p) { return -1; }
int id_pool_get_next(struct id_pool *p, int i) { return -1; }
void *id_pool_get(struct id_pool *p, int i) { return NULL; }
void *id_pool_set(struct id_pool *p, int i, void *d) { return NULL; }
void *id_pool_release(struct id_pool *p, int i) { return NULL; }
void static_library_replace(struct static_library *l, const char *n, void *f) {}

#include "audio_mixer.c"
#include "hll/KiwiSoundEngine_v12.c"

int main(void) {
    memset(&config, 0, sizeof(config));
    config.default_volume = 100;
    game_evenicle2 = true;
    kiwi_prelink();

    assert(config.mixer_nr_channels == 14);
    assert(strcmp(config.mixer_channels[0], "Master") == 0);
    assert(strcmp(config.mixer_channels[3], "Voice") == 0);
    assert(config.mixer_volumes[3] == 100);
    assert(config.mixer_volumes[1] == 80);
    assert(strcmp(config.mixer_channels[13], "BackVoiceChara2") == 0);

    mixer_init();
    assert(nr_mixers == 14);
    assert(mixers[0].parent == NULL);
    for (int i = 1; i < 14; i++) {
        assert(mixers[i].parent == &mixers[0]);
    }

    // Legacy non-E2 / non-R10 title still nests channels after Voice under Voice.
    game_evenicle2 = false;
    mixer_init();
    assert(mixers[3].parent == &mixers[0]);
    assert(mixers[4].parent == &mixers[3]);

    puts("OK");
    return 0;
}
"""
        out = compile_and_run(harness, LIBSYS4_CORE, ["-lz", "-lm"])
        self.assertEqual(out.strip(), "OK")

    def test_3d_sjis_paths_afa_zlb_txtex_and_thread_loading_mode(self):
        txtex_ex = build_extf([
            _ex_block(
                _sjis("メッシュ追加データ"),
                6,
                _ex_tree(_sjis("メッシュ追加データ"), [
                    _ex_tree(b"body", [
                        _ex_leaf(_sjis("アルファブレンディング"), 1, struct.pack("<i", 1)),
                        _ex_leaf(_sjis("両面レンダリング"), 1, struct.pack("<i", 1)),
                        _ex_leaf(_sjis("加算合成"), 1, struct.pack("<i", 1)),
                    ])
                ]),
            ),
            _ex_block(
                _sjis("モーション"),
                6,
                _ex_tree(_sjis("モーション"), [
                    _ex_tree(b"walk", [
                        _ex_leaf(_sjis("モーション名"), 3, _ex_pstr(b"areq_walk_taiki")),
                        _ex_leaf(_sjis("フレーム"), 5, _ex_list([
                            (2, struct.pack("<f", 10.0)),
                            (2, struct.pack("<f", 210.0)),
                        ])),
                        _ex_leaf(_sjis("スケール"), 2, struct.pack("<f", 1.8)),
                    ])
                ]),
            ),
        ])

        sjis_dir = b"PolyData\\Chara\\\x95\x5c"
        afa_bytes = build_afa_v2([
            (sjis_dir + b"\\HasMotion.hm", b"HM"),
            (sjis_dir + b"\\\x95\x5c.txtex.ex.zlb", build_zlb(txtex_ex)),
            (b"PolyData\\Etc\\D_rockB\\D_rock.txtex.ex", txtex_ex),
            (b"PolyData\\Chara\\fallback\\fallback.MOT", b"MOT"),
            (b"PolyData\\Billboard\\b01\\b01.png", b"PNG"),
            (b"PolyData\\Effect\\e01\\e01.3de", b"3DE"),
            (b"PolyData\\RawDir\\item", b"RAW"),
        ])

        with tempfile.TemporaryDirectory() as tmpdir:
            afa_path = Path(tmpdir) / "Evenicle23D.afa"
            afa_path.write_bytes(afa_bytes)

            harness = r"""
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <cglm/cglm.h>

#include "xsystem4.h"
#include "reign.h"
#include "sprite.h"
#include "3d/3d_internal.h"
#include "system4.h"
#include "system4/aar.h"
#include "system4/archive.h"
#include "system4/ex.h"
#include "system4/string.h"

struct config config;
static const char *test_afa_path = NULL;

const char *display_utf0(const char *s) { return s ? s : ""; }
char *gamedir_path(const char *p) {
    if (p && (strstr(p, ".afa") || strstr(p, ".AFA"))) return strdup(test_afa_path);
    return strdup("/nonexistent/Data/3DData.red");
}
char *gamedir_path_icase(const char *p) {
    if (p && (strstr(p, ".afa") || strstr(p, ".AFA"))) return strdup(test_afa_path);
    return strdup("/nonexistent");
}
#include <stdarg.h>
_Noreturn void _vm_error(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    abort();
}
void gfx_delete_texture(struct texture *t) {}
void gfx_init_texture_with_cg(struct texture *t, struct cg *cg) {}
void gfx_register_reset_callback(void (*f)(void*), void *u) {}
void gfx_unregister_reset_callback(void (*f)(void*), void *u) {}
bool gfx_remap_texture(struct texture *t) { return false; }
void glBindBuffer(GLenum t, GLuint b) {}
void glBufferData(GLenum t, GLsizeiptr s, const void *d, GLenum u) {}
void glBufferSubData(GLenum t, GLintptr o, GLsizeiptr s, const void *d) {}
void glDeleteBuffers(GLsizei n, const GLuint *b) {}
void glGenBuffers(GLsizei n, GLuint *b) { for (int i = 0; i < n; i++) b[i] = 1; }
void glBindTexture(GLenum t, GLuint tx) {}
void glBindVertexArray(GLuint v) {}
void glDeleteTextures(GLsizei n, const GLuint *t) {}
void glDeleteVertexArrays(GLsizei n, const GLuint *v) {}
void glDisableVertexAttribArray(GLuint i) {}
void glEnableVertexAttribArray(GLuint i) {}
void glGenTextures(GLsizei n, GLuint *t) {}
void glGenVertexArrays(GLsizei n, GLuint *v) {}
void glTexImage2D(GLenum t, GLint l, GLint i, GLsizei w, GLsizei h, GLint b, GLenum f, GLenum ty, const void *d) {}
void glVertexAttrib1f(GLuint i, GLfloat v) {}
void glVertexAttrib2f(GLuint i, GLfloat x, GLfloat y) {}
void glVertexAttrib3f(GLuint i, GLfloat x, GLfloat y, GLfloat z) {}
void glVertexAttrib4f(GLuint i, GLfloat x, GLfloat y, GLfloat z, GLfloat w) {}
void glVertexAttribI4i(GLuint i, GLint x, GLint y, GLint z, GLint w) {}
void glVertexAttribIPointer(GLuint i, GLint s, GLenum t, GLsizei st, const void *p) {}
void glVertexAttribPointer(GLuint i, GLint s, GLenum t, GLboolean n, GLsizei st, const void *p) {}
struct cg *cg_load_data(struct archive_data *d) { return NULL; }
void cg_free(struct cg *c) {}
struct cg *asset_cg_load(int no) { return NULL; }
struct collider *collider_create_from_plugin(struct RE_plugin *p) { return NULL; }
struct collider *collider_create(struct pol_mesh *mesh) { return NULL; }
struct collider *collider_create_raycast(struct pol_mesh **meshes, int nr_meshes) { return NULL; }
void collider_free(struct collider *c) {}
bool collider_height(struct collider *c, vec2 p, float *h) { return false; }
bool check_collision(struct collider *c, vec2 a, vec2 b, float r, vec2 o) { return false; }
bool collider_raycast(struct collider *c, vec3 o, vec3 d, vec3 hit) { return false; }
bool collider_find_path(struct collider *c, vec3 s, vec3 g, mat4 vp) { return false; }
bool collider_optimize_path(struct collider *c) { return false; }
void RE_calc_view_matrix(struct RE_camera *camera, vec3 up, mat4 out) {}
void re_debug_init(void) {}
void RE_render(struct sact_sprite *s) {}
cJSON *RE_to_json(struct sact_sprite *sp, bool verbose) { return NULL; }
struct RE_renderer *RE_renderer_new(enum RE_plugin_version v) { return xcalloc(1, 256); }
void RE_renderer_free(struct RE_renderer *r) { free(r); }
void RE_renderer_set_viewport_size(struct RE_renderer *r, int w, int h) {}
bool RE_renderer_load_billboard_texture_by_no(struct RE_renderer *r, int cg_no) { return false; }
struct billboard_texture *RE_renderer_load_billboard_texture_by_path(struct RE_renderer *r, struct archive *a, const char *p) { return NULL; }
void RE_renderer_reload_billboard_textures_by_path(struct RE_renderer *r, struct archive *a) {}
struct height_detector *RE_renderer_create_height_detector(struct RE_renderer *r, struct model *model) { return NULL; }
void RE_renderer_free_height_detector(struct height_detector *h) {}
bool RE_renderer_detect_height(struct height_detector *h, float x, float z, float *y) { return false; }
GLuint RE_create_texture_from_cg(struct cg *cg, GLint wrap, bool *ha) { return 0; }
struct pae *pae_load(struct archive *a, const char *n, enum RE_plugin_version v) { return NULL; }
void pae_free(struct pae *p) {}
void pae_reset_gl(struct pae *p, struct archive *a, enum RE_plugin_version v) {}
struct particle_effect *particle_effect_create(struct pae *p) { return NULL; }
void particle_effect_free(struct particle_effect *e) {}
void particle_effect_update(struct RE_instance *i) {}
void pae_calc_frame_range(struct pae *p, struct motion *m) {}
struct s3de *s3de_load(struct archive *a, const char *p) { return NULL; }
void s3de_free(struct s3de *s) {}
void s3de_reset_gl(struct s3de *s, struct archive *a) {}
struct s3de_effect *s3de_effect_create(struct s3de *s, struct archive *a) { return NULL; }
void s3de_effect_free(struct s3de_effect *e) {}
void s3de_effect_update(struct RE_instance *i) {}
void s3de_calc_frame_range(struct s3de *s, struct motion *m) {}
struct sact_sprite *sact_try_get_sprite(int sp) { return NULL; }
bool scene_is_dirty = false;
void scene_register_sprite(struct sprite *s) {}
void scene_unregister_sprite(struct sprite *s) {}
void sprite_bind_plugin(struct sact_sprite *sp, struct draw_plugin *plugin) {}
struct texture *sprite_get_texture(struct sact_sprite *s) { return NULL; }
void sprite_init_color(struct sact_sprite *s, int w, int h, int r, int g, int b, int a) {}
void sprite_set_pos(struct sact_sprite *s, int x, int y) {}
int iarray_read(struct iarray_reader *r) { return 0; }
float iarray_read_float(struct iarray_reader *r) { return 0.f; }
struct string *iarray_read_string_or_null(struct iarray_reader *r) { return NULL; }
void iarray_write(struct iarray_writer *w, int v) {}
void iarray_write_float(struct iarray_writer *w, float v) {}
void iarray_write_string_or_null(struct iarray_writer *w, struct string *s) {}

int main(int argc, char **argv) {
    assert(argc == 2);
    test_afa_path = argv[1];
    config.ain_filename = "Evenicle2.ain";

    // 1. RE_path_basename preserves SJIS 0x95 0x5c (表)
    const char *sjis_model = "PolyData\\Chara\\\x95\x5c";
    assert(strcmp(RE_path_basename(sjis_model), "\x95\x5c") == 0);
    const char *sjis_file = "PolyData\\Chara\\\x95\x5c\\foo.POL";
    assert(strcmp(RE_path_basename(sjis_file), "foo.POL") == 0);
    assert(strcmp(RE_path_basename("a/b/c"), "c") == 0);

    // 2. RE_is_exf
    const uint8_t valid_exf[12] = "HEAD\x00\x00\x00\x00" "EXTF";
    assert(RE_is_exf(valid_exf, 12));
    assert(!RE_is_exf(valid_exf, 11));
    assert(!RE_is_exf((const uint8_t *)"NOPE0000EXTF", 12));

    // 3. Default ThreadLoadingMode by plugin version
    struct RE_plugin *tapir = RE_plugin_new(RE_TAPIR_PLUGIN);
    assert(tapir && tapir->thread_loading_mode == false);
    RE_plugin_free(tapir);

    struct RE_plugin *seal = RE_plugin_new(RE_SEAL_PLUGIN);
    assert(seal && seal->thread_loading_mode == true);

    // 4. RE_plugin_poly_motion_exists: SJIS trailing-sep safety + HasMotion.hm + .MOT fallback
    assert(RE_plugin_poly_motion_exists(seal, "PolyData\\Chara\\\x95\x5c\\") == true);
    assert(RE_plugin_poly_motion_exists(seal, "PolyData\\Chara\\\x95\x5c") == true);
    assert(RE_plugin_poly_motion_exists(seal, "PolyData\\Chara\\fallback\\") == true);
    assert(RE_plugin_poly_motion_exists(seal, "PolyData\\Chara\\nonexistent\\") == false);

    // 5. RE_plugin_data_exists for RE_ITYPE_UNINITIALIZED, BILLBOARD, PARTICLE_EFFECT
    assert(RE_plugin_data_exists(seal, RE_ITYPE_UNINITIALIZED, "Data\\PolyData\\RawDir\\item") == true);
    assert(RE_plugin_data_exists(seal, RE_ITYPE_BILLBOARD, "PolyData\\Billboard\\b01\\") == true);
    assert(RE_plugin_data_exists(seal, RE_ITYPE_PARTICLE_EFFECT, "PolyData\\Effect\\e01") == true);
    assert(RE_plugin_data_exists(seal, RE_ITYPE_UNINITIALIZED, "PolyData\\Missing\\nope") == false);

    // 6. RE_archive_find_txtex + transparent ZLB\0 decompression + dirname fallback + Data\ prefix
    struct archive_data *d_sjis = RE_archive_find_txtex(seal->aar, "PolyData\\Chara\\\x95\x5c\\");
    assert(d_sjis && d_sjis->data && RE_is_exf(d_sjis->data, d_sjis->size));

    struct pol_mesh mesh;
    memset(&mesh, 0, sizeof(mesh));
    mesh.name = "body";
    struct pol_mesh *mesh_ptr = &mesh;
    struct pol pol;
    memset(&pol, 0, sizeof(pol));
    pol.nr_meshes = 1;
    pol.meshes = &mesh_ptr;
    txtex_opr_load(d_sjis->data, d_sjis->size, &pol);
    assert((mesh.flags & (MESH_ALPHA | MESH_BOTH | MESH_BLEND_ADDITIVE)) == (MESH_ALPHA | MESH_BOTH | MESH_BLEND_ADDITIVE));

    struct mpr *mpr = mpr_load_txtex(d_sjis->data, d_sjis->size, NULL, "areq_walk_taiki");
    if (mpr) mpr_free(mpr);
    archive_free_data(d_sjis);

    struct archive_data *d_rock = RE_archive_find_txtex(seal->aar, "Data\\PolyData\\Etc\\D_rockB");
    assert(d_rock && d_rock->data && RE_is_exf(d_rock->data, d_rock->size));
    archive_free_data(d_rock);

    RE_plugin_free(seal);
    puts("OK");
    return 0;
}
"""
            out = compile_and_run(
                harness,
                [
                    ROOT / "src/3d/reign.c",
                    ROOT / "src/3d/parser.c",
                    ROOT / "src/3d/mpr.c",
                    ROOT / "src/3d/model.c",
                    *LIBSYS4_CORE,
                ],
                ["-lz", "-lm"],
                [str(afa_path)],
            )
            self.assertEqual(out.strip(), "OK")

    def test_mainex_v12_polyex_id1_resolution(self):
        main_ex = build_extf([
            _ex_block(b"MainTitle", 3, _ex_pstr(b"Evenicle2Main")),
        ])
        poly_ex = build_extf([
            _ex_block(
                _sjis("モーション"),
                6,
                _ex_tree(_sjis("モーション"), [
                    _ex_tree(_sjis("待機"), [
                        _ex_leaf(_sjis("モーション名"), 3, _ex_pstr(b"areq_walk_taiki")),
                        _ex_leaf(_sjis("フレーム"), 5, _ex_list([
                            (2, struct.pack("<f", 10.0)),
                            (2, struct.pack("<f", 210.0)),
                        ])),
                        _ex_leaf(_sjis("スケール"), 2, struct.pack("<f", 1.8)),
                    ])
                ]),
            ),
        ])
        afa_bytes = build_afa_v2([
            (b"PolyData\\Chara\\\x95\x5c\\\x95\x5c.txtex.ex", poly_ex),
        ])

        with tempfile.TemporaryDirectory() as tmpdir:
            main_ex_path = Path(tmpdir) / "Evenicle2EX.ex"
            main_ex_path.write_bytes(main_ex)
            afa_path = Path(tmpdir) / "Evenicle23D.afa"
            afa_path.write_bytes(afa_bytes)

            harness = r"""
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "xsystem4.h"
#include "reign.h"
#include "hll/hll.h"
#include "mainex_v12.h"
#include "vm/heap.h"
#include "vm/page.h"
#include "vm/v12.h"
#include "system4/afa.h"
#include "system4/archive.h"
#include "system4/ex.h"
#include "system4/string.h"

struct config config;
bool game_evenicle2 = true;
struct vm_pointer *heap;
size_t heap_size = 256;

static struct vm_pointer heap_slots[256];
static int next_slot = 1;
static const char *test_afa_path = NULL;
static const char *names[64];
static void *funs[64];
static int nr_exports = 0;

void static_library_replace(struct static_library *l, const char *n, void *f) {
    assert(nr_exports < 64);
    names[nr_exports] = n;
    funs[nr_exports] = f;
    nr_exports++;
}
static void *find_fn(const char *n) {
    for (int i = 0; i < nr_exports; i++)
        if (!strcmp(names[i], n)) return funs[i];
    return NULL;
}

char *gamedir_path(const char *p) { return strdup(p); }
char *gamedir_path_icase(const char *p) { return strdup(p); }
struct page *v12_array_new(const struct ain_type *type, int size, bool init) {
    struct page *p = xcalloc(1, sizeof(struct page) + size * sizeof(union vm_value));
    p->type = ARRAY_PAGE;
    p->nr_vars = size;
    for (int i = 0; i < size; i++) {
        p->values[i].i = next_slot++;
        heap_slots[p->values[i].i].s = string_ref(&EMPTY_STRING);
    }
    return p;
}
void delete_page_vars(struct page *p) {
    if (!p) return;
    for (int i = 0; i < p->nr_vars; i++)
        free_string(heap_slots[p->values[i].i].s);
}
void free_page(struct page *p) { free(p); }

struct archive *RE_open_3d_archive(void) {
    int err = 0;
    struct afa_archive *afa = afa_open(test_afa_path, 0, &err);
    return afa ? &afa->ar : NULL;
}
struct archive_data *RE_archive_find_txtex(struct archive *ar, const char *model_dir) {
    if (!ar || !model_dir) return NULL;
    char buf[512];
    snprintf(buf, sizeof(buf), "%s\\\x95\x5c.txtex.ex", model_dir);
    return archive_get_by_name(ar, buf);
}

void mainex_v12_prelink(struct static_library *library);

int main(int argc, char **argv) {
    assert(argc == 3);
    config.ex_path = argv[1];
    test_afa_path = argv[2];
    heap = heap_slots;

    struct static_library lib;
    memset(&lib, 0, sizeof(lib));
    mainex_v12_prelink(&lib);

    void (*init_fn)(void) = find_fn("_ModuleInit");
    void (*fini_fn)(void) = find_fn("_ModuleFini");
    bool (*exists_fn)(struct string *, int) = find_fn("Exists");
    int (*type_fn)(struct string *, int) = find_fn("Type");
    struct string *(*str_fn)(struct string *, struct string *, int) = find_fn("String");
    float (*flt_fn)(struct string *, float, int) = find_fn("Float");
    float (*aflt_fn)(struct string *, int, float, int) = find_fn("AFloat");
    bool (*nodes_fn)(struct string *, struct page **, int) = find_fn("GetNodeNameList");
    assert(init_fn && fini_fn && exists_fn && type_fn && str_fn && flt_fn && aflt_fn && nodes_fn);

    init_fn();

    struct string *s_main = make_string("MainTitle", 9);
    struct string *s_fb = make_string("FB", 2);
    struct string *got0 = str_fn(s_main, s_fb, 0);
    assert(strcmp(got0->text, "Evenicle2Main") == 0);
    free_string(got0);

    // With game_evenicle2=false, ID=1 aliases main EX as in standard v12 games.
    game_evenicle2 = false;
    struct string *got1_legacy = str_fn(s_main, s_fb, 1);
    assert(strcmp(got1_legacy->text, "Evenicle2Main") == 0);
    free_string(got1_legacy);

    // With game_evenicle2=true, ID=1 resolves PolyEX trees in Evenicle23D.afa (with SJIS 0x5c).
    game_evenicle2 = true;
    assert(exists_fn(s_main, 1) == false);

    struct string *s_root = make_string("PolyData\\Chara\\\x95\x5c", strlen("PolyData\\Chara\\\x95\x5c"));
    assert(exists_fn(s_root, 1) == true);
    assert(type_fn(s_root, 1) == EX_TREE);

    struct page *list = NULL;
    const char *p_mot = "PolyData\\Chara\\\x95\x5c.\x83\x82\x81\x5b\x83\x56\x83\x87\x83\x93";
    struct string *s_mot = make_string(p_mot, strlen(p_mot));
    assert(nodes_fn(s_mot, &list, 1) == true);
    assert(list && list->nr_vars == 1);
    assert(strcmp(heap[list->values[0].i].s->text, "\x91\xd2\x8b\x40") == 0);
    delete_page_vars(list);
    free_page(list);

    const char *p_mname = "PolyData\\Chara\\\x95\x5c.\x83\x82\x81\x5b\x83\x56\x83\x87\x83\x93.\x91\xd2\x8b\x40.\x83\x82\x81\x5b\x83\x56\x83\x87\x83\x93\x96\xbc";
    struct string *s_mname = make_string(p_mname, strlen(p_mname));
    struct string *got_mot = str_fn(s_mname, s_fb, 1);
    assert(strcmp(got_mot->text, "areq_walk_taiki") == 0);
    free_string(got_mot);

    const char *p_scale = "PolyData\\Chara\\\x95\x5c.\x83\x82\x81\x5b\x83\x56\x83\x87\x83\x93.\x91\xd2\x8b\x40.\x83\x58\x83\x50\x81\x5b\x83\x8b";
    struct string *s_scale = make_string(p_scale, strlen(p_scale));
    assert(fabsf(flt_fn(s_scale, 1.0f, 1) - 1.8f) < 1e-4f);

    const char *p_frame = "PolyData\\Chara\\\x95\x5c.\x83\x82\x81\x5b\x83\x56\x83\x87\x83\x93.\x91\xd2\x8b\x40.\x83\x74\x83\x8c\x81\x5b\x83\x80";
    struct string *s_frame = make_string(p_frame, strlen(p_frame));
    assert(fabsf(aflt_fn(s_frame, 0, -1.0f, 1) - 10.0f) < 1e-4f);
    assert(fabsf(aflt_fn(s_frame, 1, -1.0f, 1) - 210.0f) < 1e-4f);

    free_string(s_main); free_string(s_fb); free_string(s_root);
    free_string(s_mot); free_string(s_mname); free_string(s_scale); free_string(s_frame);
    fini_fn();
    puts("OK");
    return 0;
}
"""
            out = compile_and_run(
                harness,
                [ROOT / "src/hll/MainEXFile_v12.c", *LIBSYS4_CORE],
                ["-lz", "-lm"],
                [str(main_ex_path), str(afa_path)],
            )
            self.assertEqual(out.strip(), "OK")

    def test_parts_render_3dlayer_viewport_offset(self):
        harness = r"""
#include <assert.h>
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <cglm/cglm.h>

#include "xsystem4.h"
#include "parts_internal.h"
#include "gfx/gfx.h"
#include "gfx/private.h"
#include "sact.h"
#include "sprite.h"
#include "reign.h"
#include "system4/string.h"

struct config config = { .view_width = 1280, .view_height = 720 };
struct sdl_private sdl;
bool parts_multi_controller = false;
int parts_default_motion_time = 0;

static mat4 captured_mw;
static int captured_draws = 0;

static struct sact_sprite fake_sp;
static struct texture fake_tex = { .handle = 42, .w = 780, .h = 720 };
static struct RE_plugin fake_plugin = { .suspended = false };

void gfx_load_shader(struct shader *s, const char *v, const char *f) {
    memset(s, 0, sizeof(*s));
    s->program = 1;
    s->world_transform = 10;
    s->view_transform = 11;
    s->texture = 12;
    s->vertex_pos = 0;
    s->vertex_uv = 1;
}
GLint glGetUniformLocation(GLuint p, const GLchar *n) { return 2; }
GLuint gfx_set_framebuffer(GLenum t, Texture *tx, int x, int y, int w, int h) { return 0; }
void gfx_reset_framebuffer(GLenum t, GLuint f) {}
void gfx_register_reset_callback(gfx_reset_callback cb, void *user) {}
bool gfx_remap_texture(struct texture *t) { return t && t->handle != 0; }
void glActiveTexture(GLenum t) {}
void glBindBuffer(GLenum t, GLuint b) {}
void glBindTexture(GLenum t, GLuint tx) {}
void glBindVertexArray(GLuint v) {}
void glBlendFuncSeparate(GLenum s, GLenum d, GLenum sa, GLenum da) {}
void glDisable(GLenum c) {}
void glDisableVertexAttribArray(GLuint i) {}
void glDrawArrays(GLenum m, GLint f, GLsizei c) { captured_draws++; }
void glDrawElements(GLenum m, GLsizei c, GLenum t, const void *i) { captured_draws++; }
void glEnable(GLenum c) {}
void glEnableVertexAttribArray(GLuint i) {}
void glUniform1f(GLint l, GLfloat v) {}
void glUniform1i(GLint l, GLint v) {}
void glUniform3fv(GLint l, GLsizei c, const GLfloat *v) {}
void glUniform4fv(GLint l, GLsizei c, const GLfloat *v) {}
void glUniformMatrix4fv(GLint l, GLsizei c, GLboolean t, const GLfloat *v) {
    if (l == 10 && c == 1)
        memcpy(captured_mw, v, sizeof(mat4));
}
void glUseProgram(GLuint p) {}
void glVertexAttribPointer(GLuint i, GLint s, GLenum t, GLboolean n, GLsizei st, const void *p) {}

bool scene_is_dirty = false;
struct parts_context *parts_current = NULL;

struct sact_sprite *sact_try_get_sprite(int sp) { return sp == 99 ? &fake_sp : NULL; }
struct texture *sprite_get_texture(struct sact_sprite *sp) { return &fake_tex; }
struct RE_plugin *RE_get_plugin(int p) { return p == 0 ? &fake_plugin : NULL; }
struct parts *parts_try_get(int no) { return NULL; }
struct parts *parts_get(int no) { return NULL; }
struct parts_cg *parts_get_cg(struct parts *p, int s) { return NULL; }
void PE_render_component_state(struct parts *p, int s) {}
bool flat_cg_cache_find(void *c, const char *n, Texture *t) { return false; }
void parts_world_matrix(struct parts *parts, mat4 out) { glm_mat4_identity(out); }
bool parts_is_visible(struct parts *parts) { return true; }
struct parts_state *parts_display_state(struct parts *parts) { return parts ? &parts->states[0] : NULL; }
int parts_get_modifier_font_scale(void) { return 1; }
int parts_get_modifier_scrollbar_scale(void) { return 1; }
bool parts_build_construction_process(struct parts *parts, struct parts_construction_process *cproc) { return true; }
int parts_scrollbar_range(struct parts_scrollbar *s) { return 0; }
int parts_scrollbar_skin_state(struct parts_scrollbar *s, int piece) { return 0; }
void parts_scrollbar_rects(struct parts *parts, Rectangle rects[4]) {}
void parts_widget_get_size(struct parts *parts, int *width, int *height) { if (width) *width = 0; if (height) *height = 0; }
void parts_flat_clear_emitter_births(struct flat_layer_state *state) {}
struct flat_emitter_birth *parts_flat_get_emitter_birth(struct flat_layer_state *state, const struct flat_timeline *tl, int idx, int frame, unsigned inst, bool create) { return NULL; }
unsigned parts_flat_key_count(const struct flat_timeline *tl, int frame) { return 0; }
const struct flat_key_data_graphic *parts_flat_key(const struct flat_timeline *tl, int frame, unsigned instance) { return NULL; }
int parts_flat_find_library(struct flat *fl, const char *name) { return -1; }
int parts_flat_stop_motion_get_cg_lib(struct parts_flat *f, int sm_lib_idx, int local) { return -1; }
bool parts_flat_emitter_get_align_offset(struct parts_flat *f, int emitter_lib_idx, vec2 out) { return false; }
void parts_flat_foreach_emitter_particle(struct parts_flat *f, int emitter_lib_idx, const struct flat_timeline *tl, unsigned instance, int birth_frame, int age, int frame_count, flat_emitter_particle_fn fn, void *ud) {}
void parts_flat_build_layer_matrix(const struct flat_key_data_graphic *key, vec2 pos, bool use_rot, bool use_scale, bool use_origin, bool rev_lr, bool rev_tb, mat4 out) { glm_mat4_identity(out); }
void parts_flat_build_emitter_base_matrix(const struct flat_emitter *em, const struct flat_key_stack *stack, mat4 out) { glm_mat4_identity(out); }
void parts_flat_emitter_resolve_layer(const struct flat_emitter *em, const struct flat_key_data_graphic *key, float pa, float la, struct flat_emitter_layer_effective *out) {}

#include "parts/render.c"

int main(void) {
    parts_render_init();

    struct parts p;
    memset(&p, 0, sizeof(p));
    p.global.scale.x = 1.0f;
    p.global.scale.y = 1.0f;
    p.global.alpha = 255;
    p.global.multiply_color = (SDL_Color){255, 255, 255, 255};
    p.states[0].type = PARTS_3DLAYER;
    p.states[0].layer3d.plugin = 0;
    p.states[0].layer3d.sprite_no = 99;

    // Viewport (250, 0, 780, 720) -> world_transform must place quad at (250, 0) sized (780, 720).
    fake_sp.rect = (Rectangle){250, 0, 780, 720};
    fake_tex.w = 780;
    fake_tex.h = 720;
    captured_draws = 0;
    parts_render_3dlayer(&p, &p.states[0].layer3d);
    assert(captured_draws == 1);
    assert(fabsf(captured_mw[3][0] - 250.0f) < 1e-4f);
    assert(fabsf(captured_mw[3][1] - 0.0f) < 1e-4f);
    assert(fabsf(captured_mw[0][0] - 780.0f) < 1e-4f);
    assert(fabsf(captured_mw[1][1] - 720.0f) < 1e-4f);

    // Repeated frames must not mutate l->common.origin_offset.
    parts_render_3dlayer(&p, &p.states[0].layer3d);
    assert(captured_draws == 2);
    assert(fabsf(captured_mw[3][0] - 250.0f) < 1e-4f);
    assert(p.states[0].layer3d.common.origin_offset.x == 0);
    assert(p.states[0].layer3d.common.origin_offset.y == 0);

    // Fullscreen (0, 0, 1280, 720) -> origin (0, 0), size (1280, 720).
    fake_sp.rect = (Rectangle){0, 0, 1280, 720};
    fake_tex.w = 1280;
    fake_tex.h = 720;
    parts_render_3dlayer(&p, &p.states[0].layer3d);
    assert(captured_draws == 3);
    assert(fabsf(captured_mw[3][0] - 0.0f) < 1e-4f);
    assert(fabsf(captured_mw[0][0] - 1280.0f) < 1e-4f);

    puts("OK");
    return 0;
}
"""
        out = compile_and_run(harness, LIBSYS4_CORE, ["-lz", "-lm"])
        self.assertEqual(out.strip(), "OK")

    def test_parts_activity_evenicle2_jp_pact_aliases(self):
        def state_node(kind: str, extra: list[bytes] | None = None) -> bytes:
            kids = [_ex_leaf(_sjis("パーツタイプ"), 3, _ex_pstr(_sjis(kind)))]
            if extra:
                kids.extend(extra)
            return _ex_tree(_sjis("通常状態"), kids)

        def part_node(name: str, kind: str, info_children: list[bytes], children_list: list[bytes] | None = None) -> bytes:
            info = [_ex_leaf(_sjis("パーツタイプ"), 3, _ex_pstr(_sjis(kind))), *info_children]
            fields = [
                _ex_leaf(_sjis("名前"), 3, _ex_pstr(_sjis(name))),
                _ex_tree(_sjis("種類別情報"), info),
            ]
            if children_list is not None:
                fields.append(_ex_tree(_sjis("子パーツ"), children_list))
            return _ex_tree(_sjis(name), fields)

        root_part = part_node("fg", "フォームグループ", [], children_list=[
            part_node("txt", "低レベルパーツ", [
                state_node("テキストパーツ", [
                    _ex_tree(_sjis("テキスト装飾"), []),
                    _ex_leaf(_sjis("テキスト"), 3, _ex_pstr(b"hi")),
                ]),
            ]),
            part_node("btn", "ボタン", []),
            part_node("chk", "チェックボックス", [
                _ex_leaf(_sjis("上書きチェックＣＧ名"), 3, _ex_pstr(b"chk_over.cg")),
            ]),
            part_node("lcg", "低レベルパーツ", [
                state_node("ループＣＧパーツ", [
                    _ex_leaf(_sjis("ＣＧ名"), 3, _ex_pstr(b"loop.cg")),
                    _ex_leaf(_sjis("ループＣＧ開始番号"), 1, struct.pack("<i", 3)),
                    _ex_leaf(_sjis("ループＣＧ枚数"), 1, struct.pack("<i", 5)),
                    _ex_leaf(_sjis("ループＣＧ切り替え時間"), 1, struct.pack("<i", 120)),
                ]),
            ]),
            part_node("hg", "低レベルパーツ", [
                state_node("横ゲージパーツ", [_ex_leaf(_sjis("ＣＧ名"), 3, _ex_pstr(b"hg.cg"))]),
            ]),
            part_node("vg", "低レベルパーツ", [
                state_node("縦ゲージパーツ", [_ex_leaf(_sjis("ＣＧ名"), 3, _ex_pstr(b"vg.cg"))]),
            ]),
            part_node("vsb", "縦スクロールバー", []),
            part_node("vsld", "縦スライダー", []),
            part_node("rb", "ラジオボタンボックス", []),
            part_node("mw", "メッセージウィンドウ", []),
        ])
        pact_ex = build_extf([
            _ex_block(b"PactData", 6, _ex_tree(b"PactData", [root_part])),
        ])

        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "xsystem4.h"
#include "asset_manager.h"
#include "parts.h"
#include "parts_activity.h"
#include "parts_message_window.h"
#include "parts_widgets.h"
#include "parts_internal.h"
#include "vm/heap.h"
#include "vm/page.h"
#include "system4/ex.h"
#include "system4/string.h"
#include "system4/utfsjis.h"

struct ain *ain = NULL;
struct vm_pointer *heap = NULL;
size_t heap_size = 0;
bool parts_v2 = true;

#include <stdarg.h>
_Noreturn void _vm_error(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    abort();
}

const char *parts_utf8_to_cjk_literal(const char *literal) {
    if (!literal || !literal[0])
        return "";
    static char bufs[32][256];
    static int next = 0;
    char *enc = utf2sjis(literal, strlen(literal));
    char *slot = bufs[next++ % 32];
    snprintf(slot, 256, "%s", enc);
    free(enc);
    return slot;
}

static int got_special_10 = 0, got_text = 0;
static int got_button = 0, got_checkbox = 0, got_scrollbar = 0, got_slider = 0;
static int got_h_gauge = 0, got_v_gauge = 0, got_loop_cg = 0;
static int got_override_cg = 0, got_child_parent = 0;

static struct parts fake_parts[64];
static struct parts_widget dummy_widgets[64];
static struct parts_text dummy_texts[64];
static int next_pno = 1;

struct page *array_pushback(struct page *dst, union vm_value v, enum ain_data_type dt, int st) { return dst; }
bool asset_exists_by_name(enum asset_type t, const char *n, int *id) { return false; }
struct archive_data *asset_get_by_name(enum asset_type t, const char *n, int *id_out) { return NULL; }
const char *display_sjis0(const char *s) { return s ? s : ""; }
char *gamedir_path_icase(const char *p) { return NULL; }
void iarray_write(struct iarray_writer *w, int v) {}
void iarray_write_string(struct iarray_writer *w, struct string *s) {}
int iarray_read(struct iarray_reader *r) { return 0; }
struct string *iarray_read_string(struct iarray_reader *r) { return NULL; }
int mainex_v12_register(struct ex *e) { return 2; }
int mainex_v12_register_at(int i, struct ex *e) { return 2; }
void mainex_v12_unregister_activity(int i) {}
int PE_GetFreeNumber(void) { return next_pno++; }
struct parts *parts_get(int no) {
    struct parts *p = &fake_parts[no];
    p->no = no;
    return p;
}
struct parts *parts_try_get(int no) { return (no > 0 && no < next_pno) ? &fake_parts[no] : NULL; }
void parts_release(int no) {}
void parts_state_reset(struct parts_state *s, enum parts_type t) {}
void parts_set_dims(struct parts *p, struct parts_common *c, int w, int h) {}
void parts_set_scale_x(struct parts *p, float s) {}
void parts_set_scale_y(struct parts *p, float s) {}
struct parts_cg *parts_get_cg(struct parts *p, int s) { return NULL; }
struct parts_construction_process *parts_get_construction_process(struct parts *p, int s) { return NULL; }
struct parts_text *parts_get_text(struct parts *p, int s) { return &dummy_texts[p->no]; }
void parts_message_window_create(struct parts *p) { got_special_10++; }
void parts_message_window_free(struct parts *p) {}
void parts_panel_create(struct parts *p) {}
void parts_panel_free(struct parts *p) {}
void parts_checkbox_create(struct parts *p) {}
static struct parts_scrollbar *make_dummy_scrollbar(void) {
    struct parts_scrollbar *s = xcalloc(1, sizeof(struct parts_scrollbar));
    for (int r = 0; r < 2; r++)
        for (int p = 0; p < 5; p++)
            s->names[r][p] = string_ref(&EMPTY_STRING);
    return s;
}
void parts_scrollbar_create(struct parts *p, bool h) { p->scrollbar = make_dummy_scrollbar(); got_scrollbar++; }
void parts_scrollbar_free(struct parts *p) {}
int parts_scrollbar_range(struct parts_scrollbar *s) { return 0; }
void parts_scrollbar_rebuild(struct parts *p) {}
void parts_sliderbar_create(struct parts *p, bool h) { p->scrollbar = make_dummy_scrollbar(); got_slider++; }
void parts_widget_create_deferred(struct parts *p, int t) {
    p->widget = &dummy_widgets[p->no];
    if (t == 0) got_button++;
    if (t == 1) got_checkbox++;
}
void parts_widget_create(struct parts *p) {}
void parts_widget_end_batch(struct parts *p) {}
void parts_widget_free(struct parts *p) {}
bool parts_cp_add_v14(int parts_no, const struct parts_cp_v14 *src, int state) { return true; }
void parts_cp_set_last_fill_full_size(int parts_no, int state, bool full_size) {}
void PE_Parts_SetSoundName(int a, struct string *b, int c) {}

#include "parts/activity.c"

bool PE_SetHGaugeCG(int no, struct string *s, int st) { got_h_gauge++; return true; }
bool PE_SetVGaugeCG(int no, struct string *s, int st) { got_v_gauge++; return true; }
bool PE_SetLoopCG(int no, struct string *s, int start, int nr, int spd, int st) {
    assert(start == 3 && nr == 5 && spd == 120);
    got_loop_cg++;
    return true;
}
void PE_SetCheckBoxOverrideCheckedCGName(int a, struct string *b) {
    if (b && !strcmp(b->text, "chk_over.cg")) got_override_cg++;
}
void PE_SetParentPartsNumber(int c, int p) { got_child_parent++; }

void PE_SetButtonSize(int a, int b, int c) {}
void PE_SetButtonEnable(int a, bool b) {}
void PE_SetButtonColor(int a, int b, int c, int d) {}
void PE_SetButtonFontProperty(int a, int b, int c, int d, int e, int f, float g, int h, int i, int j, float k) {}
void PE_SetButtonText(int a, struct string *b) {}
void PE_SetButtonTextOriginPosMode(int a, int b) {}
void PE_SetButtonCharSpace(int a, int b) {}
void PE_SetButtonLineSpace(int a, int b) {}
void PE_SetButtonCGName(int a, struct string *b) {}
void PE_SetButtonFlatName(int a, struct string *b) {}
void PE_SetCheckBoxButtonMode(int a, bool b) {}
void PE_SetCheckBoxCGName(int a, struct string *b) {}
void PE_SetCheckBoxCharSpace(int a, int b) {}
void PE_SetCheckBoxColor(int a, int b, int c, int d) {}
void PE_SetCheckBoxEnable(int a, bool b) {}
void PE_SetCheckBoxFlatName(int a, struct string *b) {}
void PE_SetCheckBoxFontProperty(int a, int b, int c, int d, int e, int f, float g, int h, int i, int j, float k) {}
void PE_SetCheckBoxLineSpace(int a, int b) {}
void PE_SetCheckBoxOverrideCheckedCGName(int a, struct string *b);
void PE_SetCheckBoxOverrideCheckedFlatName(int a, struct string *b) {}
void PE_SetCheckBoxOverrideCGName(int a, struct string *b) {}
void PE_SetCheckBoxOverrideFlatName(int a, struct string *b) {}
void PE_SetCheckBoxSize(int a, int b, int c) {}
void PE_SetCheckBoxText(int a, struct string *b) {}
void PE_SetCheckBoxTextOriginPosMode(int a, int b) {}
void PE_SetClickable(int a, bool b) {}
void PE_SetComment(int a, struct string *b) {}
void PE_SetComponentCheckBoxShowLinkNumber(int a, int b) {}
void PE_SetComponentMargin(int a, int b, int c, int d, int e) {}
void PE_SetComponentPos(int a, float b, float c) {}
void PE_SetComponentSubColorMode(int a, bool b) {}
void PE_SetDrag(int a, bool b) {}
void PE_SetEnableSpeedUp(int a, bool b) {}
void PE_SetFlatFPSRate(int a, float b, int c) {}
bool PE_SetHGaugeRate(int a, float b, float c, int d) { return true; }
void PE_SetHGaugeReverse(int a, bool b, int c) {}
bool PE_SetHGaugeSurfaceArea(int a, int b, int c, int d, int e, int f) { return true; }
void PE_SetInputState(int a, int b) {}
void PE_SetKeyWaitCGName(int a, struct string *b, int c, int d, int e) {}
void PE_SetKeyWaitFlatName(int a, struct string *b) {}
void PE_SetKeyWaitPos(int a, int b, int c, int d) {}
void PE_SetLayoutBoxAlign(int a, int b) {}
void PE_SetLayoutBoxLayoutType(int a, int b) {}
void PE_set_layoutbox_padding(int a, int b, int c, int d, int e) {}
void PE_SetLayoutBoxReturnF(int a, bool b, float c) {}
void PE_SetLayoutBoxReturnSizeForRate(int a, bool b) {}
bool PE_SetLoopCGSurfaceArea(int a, int b, int c, int d, int e, int f) { return true; }
void PE_SetMessageWindowActive(int a, bool b) {}
void PE_SetMessageWindowCGName(int a, struct string *b) {}
void PE_SetMessageWindowFlatName(int a, struct string *b) {}
void PE_SetMessageWindowInactiveMultipleColor(int a, int b, int c, int d) {}
void PE_SetMessageWindowRubyCharSpace(int a, int b) {}
void PE_SetMessageWindowRubyFont(int a, int b, int c, int d, int e, int f, float g, int h, int i, int j, float k) {}
void PE_SetMessageWindowRubyLineSpace(int a, int b) {}
void PE_SetMessageWindowText(int a, struct string *b, int c, struct string *d, int e, int f) {}
void PE_SetMessageWindowTextArea(int a, int b, int c, int d, int e) {}
void PE_SetMessageWindowTextFont(int a, int b, int c, int d, int e, int f, float g, int h, int i, int j, float k) {}
void PE_SetMessageWindowTextOriginPosMode(int a, int b) {}
void PE_SetMessageWindowTextSpace(int a, int b, int c) {}
void PE_SetMessageWindowTextSpeed(int a, int b) {}
void PE_SetMultiplyColor(int a, int b, int c, int d) {}
bool PE_SetNumeralCG(int a, struct string *b, int c) { return true; }
void PE_SetNumeralFont(int a, int b, int c, int d, int e, int f, float g, int h, int i, int j, float k, int l) {}
void PE_SetNumeralFullPitch(int a, bool b, int c) {}
bool PE_SetNumeralLength(int a, int b, int c) { return true; }
bool PE_SetNumeralLinkedCGNumberWidthWidthList(int a, struct string *b, int c, int d, int e, int f, int g, int h, int i, int j, int k, int l, int m, int n, int o) { return true; }
bool PE_SetNumeralNumber(int a, int b, int c) { return true; }
bool PE_SetNumeralShowComma(int a, bool b, int c) { return true; }
void PE_SetNumeralShowPadding(int a, bool b, int c) {}
void PE_SetNumeralShowType(int a, int b, int c) {}
bool PE_SetNumeralSpace(int a, int b, int c) { return true; }
bool PE_SetNumeralSurfaceArea(int a, int b, int c, int d, int e, int f) { return true; }
void PE_SetOnCursorShowLinkPartsNumber(int a, int b) {}
void PE_SetPanelAlphaGradationBottom(int a, int b) {}
void PE_SetPanelAlphaGradationLeft(int a, int b) {}
void PE_SetPanelAlphaGradationRight(int a, int b) {}
void PE_SetPanelAlphaGradationTop(int a, int b) {}
void PE_SetPanelColor(int a, int b, int c, int d, int e) {}
void PE_SetPanelSize(int a, int b, int c) {}
void PE_SetPartsAlphaClipperPartsNumber(int a, int b) {}
bool PE_SetPartsCG(int a, struct string *b, int c, int d) { return true; }
bool PE_SetPartsCGDetectionSize(int a, struct string *b, int c) { return true; }
bool PE_SetPartsCGSurfaceArea(int a, int b, int c, int d, int e, int f) { return true; }
bool PE_SetPartsConstructionSurfaceArea(int a, int b, int c, int d, int e, int f) { return true; }
void PE_SetPartsDrawFilter(int a, int b) {}
bool PE_SetPartsFlat(int a, struct string *b, int c) { return true; }
bool PE_SetPartsFlatSurfaceArea(int a, int b, int c, int d, int e, int f) { return true; }
bool PE_SetPartsFontBoldWeight(int a, float b, int c) { return true; }
bool PE_SetPartsFontColor(int a, int b, int c, int d, int e) { return true; }
bool PE_SetPartsFontEdgeColor(int a, int b, int c, int d, int e) { return true; }
bool PE_SetPartsFontEdgeWeight(int a, float b, int c) { return true; }
bool PE_SetPartsFontSize(int a, int b, int c) { return true; }
bool PE_SetPartsFontType(int a, int b, int c) { return true; }
void PE_SetPartsMagX(int a, float b) {}
void PE_SetPartsMagY(int a, float b) {}
void PE_SetPartsMessageWindowShowLink(int a, bool b) {}
void PE_SetPartsOriginPosMode(int a, int b) {}
void PE_SetPartsPixelDecide(int a, bool b) {}
void PE_SetPartsRotateX(int a, float b) {}
void PE_SetPartsRotateY(int a, float b) {}
void PE_SetPartsRotateZ(int a, float b) {}
bool PE_SetPartsTextSurfaceArea(int a, int b, int c, int d, int e, int f) { return true; }
void PE_SetPassCursor(int a, bool b) {}
void PE_SetRectangleDetectionPoint(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {}
void PE_SetShow(int a, bool b) {}
bool PE_SetText(int a, struct string *b, int c) {
    if (b && !strcmp(b->text, "hi")) got_text++;
    return true;
}
void PE_SetTextBoxText(int a, struct string *b) {}
bool PE_SetTextCharSpace(int a, int b, int c) { return true; }
bool PE_SetTextLineSpace(int a, int b, int c) { return true; }
bool PE_SetVGaugeRate(int a, float b, float c, int d) { return true; }
void PE_SetVGaugeReverse(int a, bool b, int c) {}
bool PE_SetVGaugeSurfaceArea(int a, int b, int c, int d, int e, int f) { return true; }
void PE_SetWheelable(int a, bool b) {}
void PE_SetZ(int a, int b) {}
void PE_SetAddColor(int a, int b, int c, int d) {}
void PE_SetAlpha(int a, int b) {}
void PE_UpdateComponent(int a) {}
void PE_ChangeFlatCG(int a, struct string *b, struct string *c, int d) {}
void PE_ChangeFlatSound(int a, struct string *b, struct string *c, int d) {}
bool PE_GoFramePartsFlat(int a, int b, int c) { return true; }
bool PE_AddAddFilterToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h, bool i, int j) { return true; }
bool PE_AddAlphaBlendTextToPartsConstructionProcess(int a, int b, int c, struct string *d, int e, int f, int g, int h, int i, float j, int k, int l, int m, float n, int o, int p, int q) { return true; }
bool PE_AddCGBlendToPartsConstructionProcess(int a, struct string *b, int c, int d, int e) { return true; }
bool PE_AddCopyCutCGToPartsConstructionProcess(int a, struct string *b, int c, int d, int e, int f, int g, int h, int i, int j, int k, int l) { return true; }
bool PE_AddCopyTextToPartsConstructionProcess(int a, int b, int c, struct string *d, int e, int f, int g, int h, int i, float j, int k, int l, int m, float n, int o, int p, int q) { return true; }
bool PE_AddCreateCGToProcess(int a, struct string *b, int c) { return true; }
bool PE_AddCreatePixelOnlyToPartsConstructionProcess(int a, int b, int c, int d) { return true; }
bool PE_AddCreateToPartsConstructionProcess(int a, int b, int c, int d) { return true; }
bool PE_AddCutCGAlphaBlendToPartsConstructionProcess(int a, struct string *b, int c, int d, int e, int f, int g, int h, int i, int j, int k, bool l, int m) { return true; }
bool PE_AddCutCGOnlyAlphaToPartsConstructionProcess(int a, struct string *b, int c, int d, int e, int f, int g, int h, int i, int j, int k, bool l, int m) { return true; }
bool PE_AddDrawCutCGToPartsConstructionProcess(int a, struct string *b, int c, int d, int e, int f, int g, int h, int i, int j, int k, int l) { return true; }
bool PE_AddDrawLineToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) { return true; }
bool PE_AddDrawRectToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h, int i) { return true; }
bool PE_AddDrawTextToPartsConstructionProcess(int a, int b, int c, struct string *d, int e, int f, int g, int h, int i, float j, int k, int l, int m, float n, int o, int p, int q) { return true; }
bool PE_AddFillAlphaColorToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) { return true; }
bool PE_AddFillAMapToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g) { return true; }
bool PE_AddFillGradationHorizonToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j, int k, int l) { return true; }
bool PE_AddFillToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h, int i) { return true; }
bool PE_AddFillWithAlphaToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) { return true; }
bool PE_AddGrayFilterToPartsConstructionProcess(int a, int b, int c, int d, int e, bool f, int g) { return true; }
bool PE_AddHBlurFilterToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, bool g, int h) { return true; }
bool PE_AddMulAMapGradationHorizonToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h) { return true; }
bool PE_AddMulAMapGradationVerticalToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h) { return true; }
bool PE_AddMulFilterToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, int g, int h, bool i, int j) { return true; }
bool PE_AddOnlyAlphaTextToPartsConstructionProcess(int a, int b, int c, struct string *d, int e, int f, int g, int h, int i, float j, int k, int l, int m, float n, int o, int p, int q) { return true; }
bool PE_AddVBlurFilterToPartsConstructionProcess(int a, int b, int c, int d, int e, int f, bool g, int h) { return true; }
bool PE_BuildPartsConstructionProcess(int a, int b) { return true; }
void PE_AddRadioButtonBoxChild(int a, int b) {}
void PE_ClearRadioButtonBoxChild(int a) {}

int main(int argc, char **argv) {
    assert(argc == 2);
    size_t sz = 0;
    uint8_t *raw = file_read(argv[1], &sz);
    struct ex *ex = ex_read(raw, sz);
    free(raw);
    assert(ex);

    struct string *act_name = make_string("test", 4);
    assert(PE_CreateActivity(act_name));
    struct activity *a = find_activity(act_name);
    assert(a);
    struct ex_tree *root = ex->blocks[0].val.tree->children;
    load_part(a, &root[0], 0, false);

    int special_14 = 0, special_9 = 0;
    for (int i = 1; i < next_pno; i++) {
        if (fake_parts[i].component_type == 14) special_14++;
        if (fake_parts[i].component_type == 9) special_9++;
    }
    assert(special_14 == 1);
    assert(got_text == 1);
    assert(got_button == 1);
    assert(got_checkbox == 1);
    assert(got_override_cg == 1);
    assert(got_loop_cg == 1);
    assert(got_h_gauge == 1);
    assert(got_v_gauge == 1);
    assert(got_scrollbar == 1);
    assert(got_slider == 1);
    assert(special_9 == 1);
    assert(got_special_10 == 1);
    assert(got_child_parent == 10);

    ex_free(ex);
    free_string(act_name);
    puts("OK");
    return 0;
}
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            ex_path = Path(tmpdir) / "pact.ex"
            ex_path.write_bytes(pact_ex)
            out = compile_and_run(harness, LIBSYS4_CORE, ["-lz", "-lm"], [str(ex_path)])
            self.assertEqual(out.strip(), "OK")


if __name__ == "__main__":
    unittest.main()
