#!/usr/bin/env python3
"""Native C regression tests for xsystem4 (AFA/ALD/DCF) and xsystem35 (ALD) uncensored CG overlays."""
import struct
import subprocess
import tempfile
import unittest
import zlib
from pathlib import Path

from test_evenicle2_regressions import LIBSYS4_CORE, ROOT, compile_and_run

REPO_ROOT = ROOT.parent


def make_afa(entries, version=2):
    """Build an AFA v1 (with 1-based IDs) or AFA v2 archive.
    entries: list of (name_bytes, id_1based, ts0, ts1, data_bytes)
    """
    has_num = version == 1
    offs = []
    cur = 8
    table = bytearray()
    for name, cg_id, ts0, ts1, data in entries:
        offs.append(cur)
        cur += (len(data) + 15) & ~15
        nlen = len(name)
        plen = (nlen + 4) & ~3
        table += struct.pack("<II", nlen, plen)
        table += name + b"\x00" * (plen - nlen)
        if has_num:
            table += struct.pack("<I", cg_id)
        table += struct.pack("<IIII", ts0, ts1, offs[-1], len(data))

    comp = zlib.compress(bytes(table), 9)
    data_start = 44 + len(comp)
    info_size = 16 + len(comp)
    out = bytearray()
    out += b"AFAH"
    out += struct.pack("<I", 0x1C)
    out += b"AlicArch"
    out += struct.pack("<III", version, 1, data_start)
    out += b"INFO"
    out += struct.pack("<III", info_size, len(table), len(entries))
    out += comp
    out += b"DATA"
    out += struct.pack("<I", cur)
    for _, _, _, _, data in entries:
        out += data
        pad = ((len(data) + 15) & ~15) - len(data)
        out += b"\x00" * pad
    return bytes(out)


def make_ald(entries):
    """Build a single-volume ALD archive.
    entries: list of (no_0based, name_ascii, data_bytes)
    """
    entries = sorted(entries, key=lambda x: x[0])
    max_no = entries[-1][0]
    nr_slots = max_no + 1
    ofssize = (((len(entries) + 2) * 3) + 255) & ~255
    linksize = ((nr_slots * 3) + 255) & ~255
    otbl = bytearray(ofssize)
    ltbl = bytearray(linksize)

    def put_u24(buf, off, val):
        buf[off] = val & 0xFF
        buf[off + 1] = (val >> 8) & 0xFF
        buf[off + 2] = (val >> 16) & 0xFF

    put_u24(otbl, 0, ofssize >> 8)
    cur = ofssize + linksize
    body = bytearray()
    for i, (no, name, data) in enumerate(entries):
        put_u24(otbl, (i + 1) * 3, cur >> 8)
        ltbl[no * 3] = 1
        ltbl[no * 3 + 1] = (i + 1) & 0xFF
        ltbl[no * 3 + 2] = ((i + 1) >> 8) & 0xFF
        eh = bytearray(32)
        struct.pack_into("<II", eh, 0, 32, len(data))
        nb = name.encode("ascii")[:15]
        eh[16 : 16 + len(nb)] = nb
        body += eh + data
        total = 32 + len(data)
        pad = ((total + 255) & ~255) - total
        body += b"\x00" * pad
        cur += total + pad

    put_u24(otbl, (len(entries) + 1) * 3, cur >> 8)
    body += b"\x00" * 256
    return bytes(otbl) + bytes(ltbl) + bytes(body)


def make_qnt_solid(w, h, r, g, b):
    """Build a valid QNT v1 RGB image filled with a solid (r,g,b) color."""
    # QNT stores BGR planar or interleaved after zlib; easier: write valid PNG!
    raw = bytearray()
    row = bytes([r, g, b, 255]) * w
    for _ in range(h):
        raw += b"\x00" + row

    def chunk(tag, payload):
        crc = zlib.crc32(tag + payload) & 0xFFFFFFFF
        return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(bytes(raw)))
        + chunk(b"IEND", b"")
    )


class UncensoredOverlayRegressionTest(unittest.TestCase):
    def test_xsystem4_afa_ald_and_dcf_overlay_priority(self):
        with tempfile.TemporaryDirectory() as tmp:
            td = Path(tmp)
            # 1. AFAv2 directory: base (new ts), MOD, and AliceHubUncensoredCG.afa (ts=0)
            afa_dir = td / "afa_game"
            afa_dir.mkdir()
            (afa_dir / "TestGame.ain").write_bytes(b"VERS")
            base_png = make_qnt_solid(16, 16, 10, 20, 30)
            mod_png = make_qnt_solid(16, 16, 40, 50, 60)
            unc_png = make_qnt_solid(16, 16, 200, 100, 50)
            keep_png = make_qnt_solid(16, 16, 11, 22, 33)

            (afa_dir / "TestGameCG.afa").write_bytes(
                make_afa(
                    [
                        (b"hcg_01.png", 0, 999999, 999999, base_png),
                        (b"ui_01.png", 0, 999999, 999999, keep_png),
                    ],
                    version=2,
                )
            )
            (afa_dir / "TestGameMODCG.afa").write_bytes(
                make_afa(
                    [
                        (b"hcg_01.png", 0, 999999, 999999, mod_png),
                        (b"mod_only.png", 0, 999999, 999999, mod_png),
                    ],
                    version=2,
                )
            )
            (afa_dir / "AliceHubUncensoredCG.afa").write_bytes(
                make_afa([(b"hcg_01.png", 0, 0, 0, unc_png)], version=2)
            )

            # 2. ALD directory: base GA.ald (id 10 & id 11) + AliceHubUncensoredCG.afa (AFAv1 id 10)
            ald_dir = td / "ald_game"
            ald_dir.mkdir()
            (ald_dir / "AldGame.ain").write_bytes(b"VERS")
            (ald_dir / "AldGameGA.ald").write_bytes(
                make_ald([(9, "CG00010.PNG", base_png), (10, "CG00011.PNG", keep_png)])
            )
            (ald_dir / "AliceHubUncensoredCG.afa").write_bytes(
                make_afa([(b"CG00010.PNG", 10, 0, 0, unc_png)], version=1)
            )

            harness = f"""
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "system4.h"
#include "system4/cg.h"
#include "system4/dcf.h"
#include "system4/file.h"
#include "xsystem4.h"
#include "asset_manager.h"

struct config config = {{0}};
const char *display_utf0(const char *s) {{ return s; }}
char *savedir_path(const char *filename) {{ return strdup(filename); }}
char *gamedir_path(const char *path) {{ return path_join(config.game_dir, path); }}
char *gamedir_path_icase(const char *path) {{ return path_join(config.game_dir, path); }}
void save_cjk_canon_forms_buf(const char *s, char *b1, size_t s1, char **o1, char *b2, size_t s2, char **o2) {{
    (void)s; (void)b1; (void)s1; (void)b2; (void)s2; if (o1) *o1 = NULL; if (o2) *o2 = NULL;
}}
char *save_transcode_cstr_strict(const char *s, size_t n, bool a, bool b, size_t *out) {{
    (void)s; (void)n; (void)a; (void)b; (void)out; return NULL;
}}

static void assert_rgb(struct cg *cg, uint8_t r, uint8_t g, uint8_t b) {{
    assert(cg && cg->pixels && cg->metrics.w == 16 && cg->metrics.h == 16);
    uint8_t *px = cg->pixels;
    assert(px[0] == r && px[1] == g && px[2] == b);
}}

int main(void) {{
    // Phase 1: AFAv2 game with base + MOD + AliceHubUncensoredCG.afa (ts=0)
    config.game_dir = "{afa_dir}";
    config.ain_filename = "TestGame.ain";
    asset_manager_init();
    assert(asset_manager_load_archive(ASSET_CG, "TestGameMODCG"));

    struct cg *cg_h = asset_cg_load_by_name("hcg_01", NULL);
    assert_rgb(cg_h, 200, 100, 50); // Uncensored wins over both base (newer ts) and MOD!
    cg_free(cg_h);

    struct cg *cg_ui = asset_cg_load_by_name("ui_01", NULL);
    assert_rgb(cg_ui, 11, 22, 33); // Untouched UI stays from base
    cg_free(cg_ui);

    struct cg *cg_mod = asset_cg_load_by_name("mod_only", NULL);
    assert_rgb(cg_mod, 40, 50, 60); // MOD CGs remain active alongside uncensored patch
    cg_free(cg_mod);

    // Verify DCF base CG loader resolves hcg_01 through asset_manager -> uncensored base!
    struct cg *dcf_base = cg_load_base_by_name("hcg_01");
    assert_rgb(dcf_base, 200, 100, 50);
    cg_free(dcf_base);

    // Phase 2: ALD game + AFAv1 AliceHubUncensoredCG.afa
    config.game_dir = "{ald_dir}";
    config.ain_filename = "AldGame.ain";
    asset_manager_init();

    assert(asset_exists(ASSET_CG, 10));
    assert(asset_exists(ASSET_CG, 11));
    struct cg *ald_unc = asset_cg_load(10);
    assert_rgb(ald_unc, 200, 100, 50); // ID 10 comes from AFAv1 uncensored overlay!
    cg_free(ald_unc);

    struct cg *ald_base = asset_cg_load(11);
    assert_rgb(ald_base, 11, 22, 33); // ID 11 falls back to AldGameGA.ald!
    cg_free(ald_base);

    puts("OK_XSYS4");
    return 0;
}}
"""
            libsys4_extra = [
                ROOT / "subprojects/libsys4/src/ald.c",
                ROOT / "subprojects/libsys4/src/ajp.c",
                ROOT / "subprojects/libsys4/src/cg.c",
                ROOT / "subprojects/libsys4/src/dcf.c",
                ROOT / "subprojects/libsys4/src/jpeg.c",
                ROOT / "subprojects/libsys4/src/pcf.c",
                ROOT / "subprojects/libsys4/src/pms.c",
                ROOT / "subprojects/libsys4/src/png.c",
                ROOT / "subprojects/libsys4/src/qnt.c",
                ROOT / "subprojects/libsys4/src/rou.c",
                ROOT / "subprojects/libsys4/src/tga.c",
                ROOT / "subprojects/libsys4/src/webp.c",
                ROOT / "src/asset_manager.c",
            ]
            out = compile_and_run(
                harness,
                [*LIBSYS4_CORE, *libsys4_extra],
                ["-lz", "-lm", "-lpng16", "-lturbojpeg", "-lwebp"],
            )
            self.assertEqual(out.strip(), "OK_XSYS4")

    def test_xsystem35_ald_overlay_and_gameresource_isolation(self):
        with tempfile.TemporaryDirectory() as tmp:
            td = Path(tmp)
            base_ald = td / "R5D_GA.ALD"
            ov_ald = td / "AliceHubUncensoredCG.ald"
            base_ald.write_bytes(make_ald([(50, "CG0051.QNT", b"BASE_51"), (51, "CG0052.QNT", b"BASE_52")]))
            ov_ald.write_bytes(make_ald([(50, "CG0051.QNT", b"UNC_51_LONG")]))

            src_c = td / "test_x35.c"
            exe = td / "test_x35"
            (td / "config.h").write_text("/* stub config.h */\n", encoding="utf-8")
            x35_src = REPO_ROOT / "xsystem35/src"
            src_c.write_text(
                f"""
#include <assert.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ald_manager.h"
#include "gameresource.h"

void sys_error(char *format, ...) {{ (void)format; abort(); }}
void sys_message(int lv, char *format, ...) {{ (void)lv; (void)format; }}
void sys_show_message_box(int type, const char *title, const char *text) {{ (void)type; (void)title; (void)text; }}
mmap_t *map_file(const char *path) {{ (void)path; return NULL; }}

static const char *fake_files[] = {{
    "R5D_SA.ALD", "R5D_GA.ALD", "AliceHubUncensoredCG.ald", "R5D_UncensoredGA.ald", NULL
}};
static int fake_idx = 0;
static struct dirent fake_ent;
static struct dirent *fake_readdir(DIR *d) {{
    (void)d;
    if (!fake_files[fake_idx]) return NULL;
    strncpy(fake_ent.d_name, fake_files[fake_idx++], sizeof(fake_ent.d_name) - 1);
    return &fake_ent;
}}

int main(void) {{
    GameResource gr;
    assert(initGameResourceFromDir(&gr, (DIR*)1, NULL, fake_readdir));
    assert(gr.cnt[DRIFILE_CG] == 1);
    assert(strcmp(gr.game_fname[DRIFILE_CG][0], "R5D_GA.ALD") == 0);

    const char *base_files[1] = {{ "{base_ald}" }};
    ald_init(DRIFILE_CG, base_files, 1, false);
    ald_init_overlay(DRIFILE_CG, "{ov_ald}", false);

    assert(ald_exists(DRIFILE_CG, 50));
    assert(ald_exists(DRIFILE_CG, 51));
    assert(!ald_exists(DRIFILE_CG, 52));

    dridata *d50 = ald_getdata(DRIFILE_CG, 50);
    assert(d50 && d50->size == 11 && memcmp(d50->data, "UNC_51_LONG", 11) == 0);
    ald_freedata(d50);

    dridata *d51 = ald_getdata(DRIFILE_CG, 51);
    assert(d51 && d51->size == 7 && memcmp(d51->data, "BASE_52", 7) == 0);
    ald_freedata(d51);

    puts("OK_XSYS35");
    return 0;
}}
""",
                encoding="utf-8",
            )
            subprocess.run(
                [
                    "gcc",
                    "-O2",
                    "-DXSYSTEM35_SDL_VERSION=2",
                    "-I/usr/include/SDL2",
                    f"-I{td}",
                    f"-I{x35_src}",
                    str(src_c),
                    str(x35_src / "ald_manager.c"),
                    str(x35_src / "dri.c"),
                    str(x35_src / "cache.c"),
                    str(x35_src / "gameresource.c"),
                    "-o",
                    str(exe),
                ],
                check=True,
            )
            res = subprocess.run([str(exe)], check=True, capture_output=True, text=True)
            self.assertEqual(res.stdout.strip(), "OK_XSYS35")


if __name__ == "__main__":
    unittest.main()
