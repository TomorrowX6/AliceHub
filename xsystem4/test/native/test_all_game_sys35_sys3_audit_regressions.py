#!/usr/bin/env python3
"""Native C/C++ regression tests for verified xsystem35 (System 3.x) and
system3 (System 1-3) 170-game cross-platform audit fixes.
"""
import gzip
import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
XSYS35_ROOT = REPO_ROOT / "xsystem35"
XSYS35_SRC = XSYS35_ROOT / "src"
XSYS35_MOD = XSYS35_ROOT / "modules"
XSYS35_BUILD = Path("/root/Rance10_CN/host-build/xsystem35")
SYS3_CMAKE_DIR = REPO_ROOT / "cmake" / "system3"
SYS3_BUILD = Path("/root/Rance10_CN/host-build/system3")
SYS3_UPSTREAM_SRC = SYS3_BUILD / "_deps" / "system3_source-src" / "src"


def _pkg_flags(pkg: str, flag: str) -> list[str]:
    out = subprocess.check_output(["pkg-config", flag, pkg], text=True).strip()
    return out.split() if out else []


def compile_and_run_c(
    harness_code: str,
    sources: list[Path],
    extra_cflags: list[str] | None = None,
    extra_ldflags: list[str] | None = None,
    asan: bool = False,
) -> subprocess.CompletedProcess:
    with tempfile.TemporaryDirectory(prefix="audit_sys35_test_") as tmp:
        tmp_path = Path(tmp)
        harness = tmp_path / "harness.c"
        harness.write_text(harness_code, encoding="utf-8")
        exe = tmp_path / "test_bin"
        cmd = [
            "cc",
            "-std=gnu99",
            "-Wall",
            "-Wno-pointer-sign",
            "-Wno-unused-function",
        ]
        if extra_cflags:
            cmd += extra_cflags
        cmd += [
            f"-I{XSYS35_BUILD}",
            f"-I{XSYS35_SRC}",
            f"-I{XSYS35_MOD}",
            f"-I{XSYS35_MOD / 'lib'}",
        ]
        if asan:
            cmd += ["-fsanitize=address,undefined", "-fno-omit-frame-pointer"]
        cmd += [str(harness)] + [str(s) for s in sources]
        cmd += ["-o", str(exe)]
        if extra_ldflags:
            cmd += extra_ldflags
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise AssertionError(
                f"C compile failed (rc={res.returncode}):\nCMD: {' '.join(cmd)}\nSTDOUT:\n{res.stdout}\nSTDERR:\n{res.stderr}"
            )
        env = os.environ.copy()
        env.pop("LIBGL_ALWAYS_SOFTWARE", None)
        env["SDL_VIDEODRIVER"] = "offscreen"
        env["SDL_AUDIODRIVER"] = "dummy"
        env["ASAN_OPTIONS"] = "detect_leaks=0"
        run = subprocess.run([str(exe)], capture_output=True, text=True, env=env, timeout=30)
        if run.returncode != 0:
            raise AssertionError(
                f"C test binary failed (rc={run.returncode}):\nSTDOUT:\n{run.stdout}\nSTDERR:\n{run.stderr}"
            )
        return run


def compile_and_run_cpp(
    harness_code: str,
    sources: list[Path],
    include_dirs: list[Path],
    extra_cxxflags: list[str] | None = None,
    extra_ldflags: list[str] | None = None,
    asan: bool = True,
) -> subprocess.CompletedProcess:
    with tempfile.TemporaryDirectory(prefix="audit_sys3_test_") as tmp:
        tmp_path = Path(tmp)
        harness = tmp_path / "harness.cpp"
        harness.write_text(harness_code, encoding="utf-8")
        exe = tmp_path / "test_bin"
        cmd = [
            "c++",
            "-std=c++17",
            "-Wall",
            "-Wno-unused-function",
        ]
        for inc in include_dirs:
            cmd.append(f"-I{inc}")
        if asan:
            cmd += ["-fsanitize=address,undefined", "-fno-omit-frame-pointer"]
        if extra_cxxflags:
            cmd += extra_cxxflags
        cmd += [str(harness)] + [str(s) for s in sources]
        cmd += ["-o", str(exe)]
        if extra_ldflags:
            cmd += extra_ldflags
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise AssertionError(
                f"C++ compile failed (rc={res.returncode}):\nCMD: {' '.join(cmd)}\nSTDOUT:\n{res.stdout}\nSTDERR:\n{res.stderr}"
            )
        env = os.environ.copy()
        env.pop("LIBGL_ALWAYS_SOFTWARE", None)
        env["SDL_VIDEODRIVER"] = "offscreen"
        env["SDL_AUDIODRIVER"] = "dummy"
        env["ASAN_OPTIONS"] = "detect_leaks=0"
        run = subprocess.run([str(exe)], capture_output=True, text=True, env=env, timeout=30)
        if run.returncode != 0:
            raise AssertionError(
                f"C++ test binary failed (rc={run.returncode}):\nSTDOUT:\n{run.stdout}\nSTDERR:\n{run.stderr}"
            )
        return run


class XSystem35AuditRegressionTests(unittest.TestCase):
    def test_teardown_order_stops_midi_and_pcm_before_mixer_quit_midi(self):
        """P0: mus_exit() must stop MIDI and close PCM BEFORE calling musmidi_exit()/mixer_quit_midi()."""
        sdl_cflags = _pkg_flags("sdl2", "--cflags")
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "ald_manager.h"
#include "mixer.h"
#include "music.h"
#include "music_private.h"
#include "nact.h"

struct mixer_music {
    int id;
    float gain;
};

static NACTINFO nact_state;
NACTINFO *nact = &nact_state;

static int seq = 0;
static int order_midi_free = 0;
static int order_pcm_exit = 0;
static int order_mixer_quit_midi = 0;
static int ald_live = 0;
static float last_gain = -1.0f;
static uint32_t fake_ticks = 1000;

Uint32 SDL_GetTicks(void) { return fake_ticks; }
const char *SDL_GetError(void) { return "mock"; }
void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }

bool musbgm_init(DRIFILETYPE type, int ch) { (void)type; (void)ch; return true; }
void musbgm_exit(void) {}
void musbgm_reset(void) {}
void musbgm_reapply_valance(void) {}

bool muscd_init(void) { return true; }
void muscd_exit(void) {}
void muscd_reset(void) {}
bool muscd_fadestart(int t, int v, int s) { (void)t; (void)v; (void)s; return true; }
bool muscd_fading(void) { return false; }
void muscd_fadestop(void) {}
int muscd_get_volume(void) { return 100; }
bool muscd_set_volume(int v) { (void)v; return true; }
void muscd_reapply_valance(void) {}

bool muspcm_init(int buf) { (void)buf; return true; }
void muspcm_exit(void) { order_pcm_exit = ++seq; }
void muspcm_reset(void) {}
bool muspcm_load_no(int slot, int no) { (void)slot; (void)no; return false; }
bool muspcm_load_mixlr(int slot, int noL, int noR) { (void)slot; (void)noL; (void)noR; return false; }
bool muspcm_load_data(int slot, uint8_t *buf, uint32_t len) { (void)slot; (void)buf; (void)len; return false; }
void muspcm_unload(int slot) { (void)slot; }
bool muspcm_start(int slot, int loop) { (void)slot; (void)loop; return false; }
void muspcm_stop(int slot) { (void)slot; }
void muspcm_fadeout(int slot, int msec) { (void)slot; (void)msec; }
void muspcm_setvol(int slot, int lv) { (void)slot; (void)lv; }
int muspcm_getpos(int slot) { (void)slot; return 0; }
int muspcm_getwavelen(int slot) { (void)slot; return 0; }
void muspcm_waitend(int slot) { (void)slot; }
void muspcm_reapply_valance(void) {}

dridata *ald_getdata(DRIFILETYPE type, int no) {
    (void)type; (void)no;
    dridata *d = calloc(1, sizeof(*d));
    d->data = calloc(1, 32);
    d->size = 32;
    ald_live++;
    return d;
}
void ald_freedata(dridata *d) {
    if (!d) return;
    free(d->data);
    free(d);
    ald_live--;
}

bool mixer_init_midi(void) { return true; }
void mixer_quit_midi(void) { order_mixer_quit_midi = ++seq; }
mixer_music *mixer_music_load_midi(const void *data, size_t size) {
    (void)data; (void)size;
    mixer_music *m = calloc(1, sizeof(*m));
    m->gain = 1.0f;
    return m;
}
void mixer_music_free(mixer_music *m) {
    if (!m) return;
    order_midi_free = ++seq;
    free(m);
}
bool mixer_music_play(mixer_music *m, int loops, int fade_in_ms) { (void)m; (void)loops; (void)fade_in_ms; return true; }
void mixer_music_stop(mixer_music *m) { (void)m; }
void mixer_music_pause(mixer_music *m) { (void)m; }
void mixer_music_resume(mixer_music *m) { (void)m; }
bool mixer_music_playing(mixer_music *m) { return m != NULL; }
void mixer_music_set_gain(mixer_music *m, float gain) {
    if (m) m->gain = gain;
    last_gain = gain;
}
void mixer_music_fade_out(mixer_music *m, int ms) { (void)m; (void)ms; }

int main(void) {
    assert(mus_init(0));
    assert(mus_midi_get_state());
    assert(mus_pcm_get_state());

    // Start MIDI track 1, fade volume down to 0, then start track 2:
    // volume MUST reset to 1.0 (100%) on the new track!
    assert(mus_midi_start(1, 0));
    assert(last_gain > 0.99f);
    assert(mus_mixer_fadeout_start(MIX_MIDI, 0, 0, 0));
    assert(last_gain < 0.01f);

    assert(mus_midi_start(2, 0));
    assert(last_gain > 0.99f);
    assert(ald_live == 1);

    // Teardown order: stop/free MIDI -> exit PCM -> quit MIDI (dlclose fluidsynth)
    seq = order_midi_free = order_pcm_exit = order_mixer_quit_midi = 0;
    mus_exit();

    assert(order_midi_free > 0);
    assert(order_pcm_exit > 0);
    assert(order_mixer_quit_midi > 0);
    assert(order_midi_free < order_pcm_exit);
    assert(order_pcm_exit < order_mixer_quit_midi);
    assert(ald_live == 0);
    assert(!mus_midi_get_state());
    assert(!mus_pcm_get_state());
    return 0;
}
"""
        compile_and_run_c(
            harness,
            [
                XSYS35_SRC / "music.c",
                XSYS35_SRC / "music_midi.c",
                XSYS35_SRC / "midi.c",
                XSYS35_SRC / "midi.sdlmixer.c",
            ],
            extra_cflags=sdl_cflags + ["-DENABLE_MIDI_SDLMIXER"],
            asan=True,
        )

        # Also check MIDI volume reset on TSF and Android backends, and async-signal-safe signal_handler
        tsf_src = (XSYS35_SRC / "midi.tsf.c").read_text(encoding="utf-8")
        self.assertIn("volume = 100;\n\tfade_left = fade_total = 0;", tsf_src)
        android_src = (XSYS35_SRC / "midi.android.c").read_text(encoding="utf-8")
        self.assertIn("current_vol = 100;", android_src)
        xsys_src = (XSYS35_SRC / "xsystem35.c").read_text(encoding="utf-8")
        self.assertIn("static volatile sig_atomic_t quit_requested = 0;", xsys_src)
        self.assertNotIn("sys_set_signalhandler(SIGABRT", xsys_src)
        segv_block = xsys_src[xsys_src.index("signal_handler_segv"):xsys_src.index("void sys_set_signalhandler")]
        self.assertNotIn("sys35_remove(", segv_block)

    def test_gzipped_fallback_font_loading_and_switch_path(self):
        """P0: font.c must decompress .ttf.gz via zlib and reference romfs:/fonts/HanaMinA.ttf.gz on __SWITCH__."""
        font_src = (XSYS35_SRC / "font.c").read_text(encoding="utf-8")
        self.assertIn("romfs:/fonts/HanaMinA.ttf.gz", font_src)
        self.assertIn("inflateInit2(&zs, 16 + MAX_WBITS)", font_src)

        sample_ttf = SYS3_BUILD / "_deps" / "system3_source-src" / "resources" / "fonts" / "MTLc3m.ttf"
        if not sample_ttf.exists():
            self.skipTest(f"Sample TTF not found at {sample_ttf}")

        ft_cflags = _pkg_flags("freetype2", "--cflags")
        ft_libs = _pkg_flags("freetype2", "--libs")
        sdl_cflags = _pkg_flags("sdl2", "--cflags")

        with tempfile.TemporaryDirectory(prefix="audit_gzfont_") as tmp:
            gz_path = Path(tmp) / "fallback.ttf.gz"
            with open(sample_ttf, "rb") as fin, gzip.open(gz_path, "wb") as fout:
                shutil.copyfileobj(fin, fout)

            harness = f"""
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "font.h"
#include "utfsjis.h"

void sys_message(int lv, char *fmt, ...) {{ (void)lv; (void)fmt; }}
void sys_error(char *fmt, ...) {{ (void)fmt; abort(); }}

int main(void) {{
    setenv("XSYSTEM35_FONT_FALLBACK", "{gz_path}", 1);
    font_init();
    font_set_name_and_index(FONT_GOTHIC, "/nonexistent/missing.ttf", 0);
    font_set_name_and_index(FONT_MINCHO, "/nonexistent/missing.ttf", 0);

    // Loading a glyph via the gzipped fallback font must succeed cleanly.
    FontSpec spec = {{ .type = FONT_GOTHIC, .size = 16, .weight = FONT_WEIGHT_NORMAL }};
    font_set_antialias(true);
    SDL_Color white = {{ 255, 255, 255, 255 }};
    SDL_Surface *sf = font_render_text(spec, "A", white, true);
    assert(sf != NULL);
    assert(sf->w > 0 && sf->h > 0);
    SDL_FreeSurface(sf);

    // Re-init + teardown must cleanly free the decompressed buffer without double-free.
    font_fini();
    font_fini();
    return 0;
}}
"""
            sdl_libs = _pkg_flags("sdl2", "--libs")
            compile_and_run_c(
                harness,
                [XSYS35_SRC / "font.c", XSYS35_SRC / "utfsjis.c", XSYS35_SRC / "cache.c"],
                extra_cflags=ft_cflags + sdl_cflags,
                extra_ldflags=ft_libs + sdl_libs + ["-lz"],
                asan=True,
            )

    def test_gameresource_ios_switch_msgskip_and_case_insensitive_saves(self):
        """P1: iOS/Switch lowercase save names, msgskip.dat for empty basename (hiyoko2), and case-insensitive save read."""
        for define in ("-DALICEHUB_IOS", "-D__SWITCH__"):
            harness = r"""
#include <assert.h>
#include <dirent.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "gameresource.h"

void freeGameResource(GameResource *gr);
void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }
void sys_error(char *fmt, ...) { (void)fmt; abort(); }

static const char *const *mock_files;
static int mock_idx;

static struct dirent *mock_readdir(DIR *d) {
    (void)d;
    static struct dirent ent;
    if (!mock_files[mock_idx])
        return NULL;
    memset(&ent, 0, sizeof(ent));
    strncpy(ent.d_name, mock_files[mock_idx++], sizeof(ent.d_name) - 1);
    return &ent;
}

int main(void) {
    GameResource gr;

    // 1. Standard title: Rance4SA.ALD -> lowercase Rance4sa.asd & Rance4.msgskip
    static const char *const rance4_files[] = { "Rance4SA.ALD", "Rance4GA.ALD", NULL };
    mock_files = rance4_files;
    mock_idx = 0;
    assert(initGameResourceFromDir(&gr, (DIR *)1, "/tmp/saves", mock_readdir));
    assert(strcmp(gr.save_fname[0], "/tmp/saves/Rance4sa.asd") == 0);
    assert(strcmp(gr.save_fname[25], "/tmp/saves/Rance4sz.asd") == 0);
    assert(gr.msgskip != NULL);
    assert(strcmp(gr.msgskip, "/tmp/saves/Rance4.msgskip") == 0);
    freeGameResource(&gr);

    // 2. Empty basename (hiyoko2: sa.ald) -> sa.asd & msgskip.dat (NOT hidden .msgskip!)
    static const char *const hiyoko2_files[] = { "sa.ald", "ga.ald", NULL };
    mock_files = hiyoko2_files;
    mock_idx = 0;
    assert(initGameResourceFromDir(&gr, (DIR *)1, "/tmp/saves", mock_readdir));
    assert(strcmp(gr.save_fname[0], "/tmp/saves/sa.asd") == 0);
    assert(gr.msgskip != NULL);
    assert(strcmp(gr.msgskip, "/tmp/saves/msgskip.dat") == 0);
    freeGameResource(&gr);
    return 0;
}
"""
            compile_and_run_c(
                harness,
                [XSYS35_SRC / "gameresource.c"],
                extra_cflags=[define],
                asan=True,
            )

    def test_mmap_non_mmap_readwrite_fallback_and_sync(self):
        """P1: !HAVE_MMAP fallback (Switch) supports map_file_readwrite, sync_mapped_file, and unmap_file persistence."""
        harness = r"""
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "mmap.h"

void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }

int main(void) {
    char path[] = "/tmp/audit_mmap_rw_XXXXXX";
    int fd = mkstemp(path);
    assert(fd >= 0);
    close(fd);
    unlink(path);

    mmap_t *m = map_file_readwrite(path, 128);
    assert(m != NULL);
    assert(m->addr != NULL);
    uint8_t *p = (uint8_t *)m->addr;
    p[0] = 0xde;
    p[1] = 0xad;
    p[127] = 0x5a;
    assert(sync_mapped_file(m) == 0);

    // Verify synced bytes are on disk immediately
    FILE *fp = fopen(path, "rb");
    assert(fp != NULL);
    uint8_t disk[128] = {0};
    assert(fread(disk, 1, 128, fp) == 128);
    fclose(fp);
    assert(disk[0] == 0xde && disk[1] == 0xad && disk[127] == 0x5a);

    // Mutate again and rely on unmap_file() writeback
    p[64] = 0xbe;
    assert(unmap_file(m) == 0);

    // Reopen and verify persistence
    mmap_t *m2 = map_file_readwrite(path, 128);
    assert(m2 != NULL);
    uint8_t *p2 = (uint8_t *)m2->addr;
    assert(p2[0] == 0xde && p2[1] == 0xad && p2[64] == 0xbe && p2[127] == 0x5a);
    assert(unmap_file(m2) == 0);

    unlink(path);
    return 0;
}
"""
        # Force !HAVE_MMAP by shadowing config.h with HAVE_MMAP stripped (simulating Switch)
        with tempfile.TemporaryDirectory(prefix="audit_nommap_") as tmp:
            cfg = (XSYS35_BUILD / "config.h").read_text(encoding="utf-8")
            cfg = re.sub(r"^#define\s+HAVE_MMAP\b.*$", "/* #undef HAVE_MMAP */", cfg, flags=re.M)
            (Path(tmp) / "config.h").write_text(cfg, encoding="utf-8")
            compile_and_run_c(
                harness,
                [XSYS35_SRC / "mmap.c"],
                extra_cflags=[f"-I{tmp}"],
                asan=True,
            )

    def test_savedata_bounds_corruption_and_case_insensitive_read(self):
        """P1: savedata.c rejects negative slots, truncated headers, varSys==0, OOB pageNo/partial writes, and reads legacy uppercase saves."""
        sdl_cflags = _pkg_flags("sdl2", "--cflags")
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "filecheck.h"
#include "nact.h"
#include "savedata.h"
#include "scenario.h"
#include "variable.h"

static NACTINFO nact_state;
NACTINFO *nact = &nact_state;
int sl_page = 0;
int sl_index = 0;

void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }
void sys_error(char *fmt, ...) { (void)fmt; abort(); }
bool sl_jmpFar2(int p, int idx) { (void)p; (void)idx; return true; }
uint8_t *sl_saveStack(enum save_format fmt, int *size) { (void)fmt; *size = 0; return calloc(1, 4); }
void sl_loadStack(enum save_format fmt, uint8_t *data, int size) { (void)fmt; (void)data; (void)size; }
void sl_getStackInfo(struct stack_info *info) { memset(info, 0, sizeof(*info)); }
struct stack_frame_info *sl_next_stack_frame(struct stack_frame_info *sfi) { (void)sfi; return NULL; }

int main(void) {
    char dir[] = "/tmp/audit_save_XXXXXX";
    assert(mkdtemp(dir) != NULL);
    fc_init(dir);
    v_init();

    char lower_path[256], upper_path[256], trunc_path[256], bad_path[256];
    snprintf(lower_path, sizeof(lower_path), "%s/Rance4sa.asd", dir);
    snprintf(upper_path, sizeof(upper_path), "%s/Rance4SA.asd", dir);
    snprintf(trunc_path, sizeof(trunc_path), "%s/trunc.asd", dir);
    snprintf(bad_path, sizeof(bad_path), "%s/bad.asd", dir);

    nact->files.save_fname[0] = lower_path;
    nact->files.save_fname[1] = trunc_path;
    nact->files.save_fname[2] = bad_path;

    // 1. Negative slot indices rejected everywhere
    struct VarRef ref0 = { .page = 0, .index = 0 };
    assert(save_copyAll(-1, 0) == SAVE_SAVEERR);
    assert(save_copyAll(0, -1) == SAVE_SAVEERR);
    assert(save_loadAll(-1) == SAVE_LOADERR);
    assert(save_saveAll(-1) == SAVE_SAVEERR);
    assert(save_loadPartial(-1, &ref0, 1) == SAVE_LOADERR);
    assert(save_savePartial(-1, &ref0, 1) == SAVE_SAVEERR);

    // 2. Valid full save -> rename to UPPERCASE legacy filename -> readable via lowercase save_fname[0]!
    sysVar[0] = 1234;
    sysVar[1] = 5678;
    assert(save_saveAll(0) == SAVE_SAVEOK1);
    assert(rename(lower_path, upper_path) == 0);
    // save_get_file resolves to existing uppercase path
    const char *resolved = save_get_file(0);
    assert(resolved != NULL && strcmp(resolved, upper_path) == 0);
    sysVar[0] = 0;
    sysVar[1] = 0;
    assert(save_loadAll(0) == SAVE_LOADOK);
    assert(sysVar[0] == 1234 && sysVar[1] == 5678);

    // 3. save_savePartial on page with varSys[page]==0 must fail safely without corrupting file
    assert(v_allocatePage(5, 16, true));
    struct VarRef ref5 = { .page = 5, .index = 0 };
    assert(save_savePartial(0, &ref5, 4) == SAVE_SAVEERR);

    // 4. save_savePartial & save_loadPartial with OOB index/count exceeding filesize must fail safely
    assert(v_allocatePage(6, 4, true));
    assert(save_saveAll(2) == SAVE_SAVEOK1);
    assert(v_allocatePage(6, 2000, true));
    struct VarRef ref_oob = { .page = 6, .index = 0 };
    assert(save_loadPartial(2, &ref_oob, 2000) == SAVE_LOADERR);
    assert(save_savePartial(2, &ref_oob, 2000) == SAVE_SAVEERR);

    // 5. Truncated save (< sizeof(asd_baseHdr)) rejected cleanly by save_copyAll & save_loadAll
    FILE *fp = fopen(trunc_path, "wb");
    assert(fp != NULL);
    fwrite("SHORT", 1, 5, fp);
    fclose(fp);
    assert(save_copyAll(2, 1) == SAVE_SAVEERR);
    assert(save_loadAll(1) == SAVE_LOADERR);

    // 6. Corrupted variable page header with pageNo=999 rejected cleanly by loadVarPage
    // Save a valid file to slot 2, then corrupt the varPageHdr.pageNo of varSys[0] to 999
    assert(save_saveAll(2) == SAVE_SAVEOK1);
    fp = fopen(bad_path, "r+b");
    assert(fp != NULL);
    // Read varSys[0] offset from asd_baseHdr. Rather than hardcoding offset, locate pageNo=0 after header:
    // Actually we can compute exact offset via the struct layout or scan for size=65537*2, pageNo=0:
    fseek(fp, 0, SEEK_END);
    long sz = ftell(fp);
    uint8_t *buf = malloc((size_t)sz);
    fseek(fp, 0, SEEK_SET);
    assert(fread(buf, 1, (size_t)sz, fp) == (size_t)sz);
    int expected_size = 65537 * 2;
    int patched = 0;
    for (long off = 2000; off + 16 <= sz; off += 4) {
        int s, pno, r1, r2;
        memcpy(&s, buf + off, 4);
        memcpy(&pno, buf + off + 4, 4);
        memcpy(&r1, buf + off + 8, 4);
        memcpy(&r2, buf + off + 12, 4);
        if (s == expected_size && pno == 0 && r1 == 0 && r2 == 0) {
            int bad_page = 999;
            fseek(fp, off + 4, SEEK_SET);
            fwrite(&bad_page, 4, 1, fp);
            patched = 1;
            break;
        }
    }
    fclose(fp);
    free(buf);
    assert(patched == 1);
    assert(save_loadAll(2) == SAVE_LOADERR);

    // 7. save_resetFormat restores default
    assert(save_setFormat("xsystem35"));
    save_resetFormat();

    // Cleanup
    save_delete_file(0);
    unlink(trunc_path);
    unlink(bad_path);
    char bak[270];
    snprintf(bak, sizeof(bak), "%s.", lower_path); unlink(bak);
    snprintf(bak, sizeof(bak), "%s.", bad_path); unlink(bak);
    fc_fini();
    rmdir(dir);
    return 0;
}
"""
        compile_and_run_c(
            harness,
            [
                XSYS35_SRC / "savedata.c",
                XSYS35_SRC / "filecheck.c",
                XSYS35_SRC / "variable.c",
                XSYS35_SRC / "utfsjis.c",
            ],
            extra_cflags=sdl_cflags,
            asan=True,
        )

    def test_hacks_cn_rance4_tt2_relaunch_and_audio_meta_fd_leak(self):
        """P0/P1: hacks.c CN Rance4 + TT2 matching + hacks_reset, and audio_meta.c bgi_read fclose leak fix."""
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "audio_meta.h"
#include "hacks.h"
#include "input.h"

unsigned Y3waitFlags = KEYWAIT_CANCELABLE;
void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }
void sys_error(char *fmt, ...) { (void)fmt; abort(); }

int main(void) {
    // 1. UTF-8 CN Rance IV triggers GAME_RANCE4_V2 & KEYWAIT_NONCANCELABLE
    hacks_reset();
    assert(game_id == GAME_UNKNOWN);
    assert(Y3waitFlags == KEYWAIT_CANCELABLE);
    enable_hack_by_title("［Ｒａｎｃｅ　ＩＶ　－教团的遗产－　Ｆｏｒ　Ｗｉｎ９５］");
    assert(game_id == GAME_RANCE4_V2);
    assert(Y3waitFlags == KEYWAIT_NONCANCELABLE);

    // 2. hacks_reset clears all leaked state for iOS relaunch
    daiakuji_cx_hack = true;
    hacks_reset();
    assert(game_id == GAME_UNKNOWN);
    assert(daiakuji_cx_hack == false);
    assert(Y3waitFlags == KEYWAIT_CANCELABLE);

    // 3. Toushin Toshi II case-insensitive + ASCII/fullwidth whitespace trimmed
    enable_hack_by_title("  toushin toshi ii for win95　");
    assert(game_id == GAME_TT2);
    hacks_reset();
    enable_hack_by_title("TOUSHIN TOSHI II FOR WIN95");
    assert(game_id == GAME_TT2);

    // 4. bgi_read closes FILE* cleanly across many reloads and audio_meta_fini resets state
    char bgi_path[] = "/tmp/audit_bgi_XXXXXX";
    int fd = mkstemp(bgi_path);
    assert(fd >= 0);
    const char *plain = "1, 0, 100, 200, -1\n2, 0, 300, 400, -1\n";
    size_t plen = strlen(plain);
    unsigned char enc[64];
    for (size_t i = 0; i < plen; i++) {
        unsigned char c = (unsigned char)plain[i];
        enc[i] = (unsigned char)((c >> 4) | ((c & 0xf) << 4));
    }
    assert(write(fd, enc, plen) == (ssize_t)plen);
    close(fd);

    for (int i = 0; i < 300; i++) {
        assert(bgi_read(bgi_path));
        bgi_t *entry = bgi_find(1);
        assert(entry != NULL && entry->looptop == 100 && entry->len == 200);
    }
    audio_meta_fini();
    assert(bgi_find(1) == NULL);
    unlink(bgi_path);
    return 0;
}
"""
        compile_and_run_c(
            harness,
            [XSYS35_SRC / "hacks.c", XSYS35_SRC / "audio_meta.c"],
            extra_cflags=_pkg_flags("sdl2", "--cflags"),
            asan=True,
        )

    def test_utf8_width_svar_utfsjis_and_alk_null_guard(self):
        """P1/P2: UTF-8 display width, svar_toVars/fromVars 3-byte round-trip, SJIS/UTF-8 OOB guards, alk_new NULL check."""
        harness = r"""
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "alk.h"
#include "utfsjis.h"
#include "variable.h"

void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }
void sys_error(char *fmt, ...) { (void)fmt; abort(); }

int main(void) {
    // 1. UTF-8 display column width: 2 CJK (4 cols) + 2 ASCII (2 cols) + 1 halfwidth katakana (1 col) = 7
    const char *s = "\xe4\xb8\xad\xe6\x96\x87" "AB" "\xef\xbd\xb1"; // 中文ABｱ
    assert(cjk_display_width(s, UTF8) == 7);
    assert(cjk_display_width("ABCD", SHIFT_JIS) == 4);

    // 2. variable.c UTF-8 svar_width + svar_toVars/svar_fromVars round-trip
    v_init();
    v_set_encoding(UTF8);
    svar_set(1, "\xe4\xb8\xad\xe6\x96\x87" "A"); // 中文A
    assert(svar_width(1) == 5);
    vmvar_t vars[16] = {0};
    int cnt = svar_toVars(1, vars);
    assert(cnt == 4); // 中, 文, A, NUL
    assert(vars[0] == 0x4e2d);
    assert(vars[1] == 0x6587);
    assert(vars[2] == 'A');
    assert(vars[3] == 0);
    svar_fromVars(2, vars);
    assert(strcmp(svar_get(2), "\xe4\xb8\xad\xe6\x96\x87" "A") == 0);

    // 3. sjis2utf invalid trail byte (< 0x40) must not OOB read s2u
    const uint8_t bad_sjis[] = { 0x81, 0x01, 0x82, 0x3f, 0x00 };
    uint8_t *u = sjis2utf(bad_sjis);
    assert(u != NULL && strcmp((const char *)u, "??") == 0);
    free(u);

    // 4. Truncated UTF-8 sequences must not OOB read past NUL
    const char *trunc1 = "\xe4\xb8";
    const char *p = trunc1;
    assert(utf8_next_codepoint(&p) == 0xfffd);
    uint8_t *sj = utf2sjis((const uint8_t *)"\xe4\xb8");
    assert(sj != NULL);
    free(sj);

    // 5. alk_new NULL check when file does not exist
    assert(alk_new("/nonexistent/path/does_not_exist.alk") == NULL);
    alk_free(NULL);
    return 0;
}
"""
        compile_and_run_c(
            harness,
            [
                XSYS35_SRC / "utfsjis.c",
                XSYS35_SRC / "variable.c",
                XSYS35_MOD / "lib" / "alk.c",
                XSYS35_SRC / "mmap.c",
            ],
            extra_cflags=_pkg_flags("sdl2", "--cflags"),
            asan=True,
        )

    def test_sys35_source_audit_invariants(self):
        """Verify remaining xsystem35 module/source fixes (NFD kaerunyo, sprite_msg, cmd2F60, oujimisc, dDemo, Sh*, sactlog, profile, cdrom.mp3, iOS relaunch)."""
        esc = (XSYS35_SRC / "escalayer_modifier.c").read_text(encoding="utf-8")
        self.assertIn(r"\xe3\x81\xaf\xe3\x82\x9a\xe3\x81\xab\xe3\x82\x87", esc)

        smsg = (XSYS35_MOD / "SACT" / "sprite_msg.c").read_text(encoding="utf-8")
        self.assertIn("advance_char_display_width", smsg)
        self.assertIn("cjk_display_width(mbuf, nact->encoding)", smsg)
        self.assertIn("MSGBUFMAX - 1 - (int)strlen(sact.msgbuf)", smsg)
        self.assertIn("MSGBUFMAX - 1 - (int)strlen(sact.msgbuf2)", smsg)
        self.assertIn("while (*msg && *msg != '|')", smsg)

        cmd2f60 = (XSYS35_SRC / "cmd2F60.c").read_text(encoding="utf-8")
        self.assertIn('"<invalid>"', cmd2f60)

        ouji = (XSYS35_MOD / "oujimisc" / "oujimisc.c").read_text(encoding="utf-8")
        self.assertIn("char buf[16];", ouji)
        self.assertIn('snprintf(buf, sizeof(buf), "%5d", num);', ouji)
        self.assertIn("if (map < 0 || map >= NUM_MAPS || !mapdata[map])", ouji)

        ddemo = (XSYS35_MOD / "dDemo" / "dDemo.c").read_text(encoding="utf-8")
        self.assertIn("alk_free(alk);\n\t*var = 1;", ddemo)
        self.assertIn('WARNING("dDEMO.alk not found");\n\t\t*var = 0;', ddemo)

        shsound = (XSYS35_MOD / "ShSound" / "ShSound.c").read_text(encoding="utf-8")
        self.assertIn("ald_freedata(dfile);", shsound)
        self.assertIn("free_memory_wav};", shsound)

        shgraph = (XSYS35_MOD / "ShGraph" / "ShGraph.c").read_text(encoding="utf-8")
        self.assertNotIn("mus_pcm_stop(wavno % 255)", shgraph)
        self.assertIn("mus_wav_stop(wavno % 256);", shgraph)

        shport = (XSYS35_MOD / "ShPort" / "ShPort.c").read_text(encoding="utf-8")
        self.assertIn("if (no <= 0 || no > KEYMAP_MAX)", shport)
        self.assertIn("ShPort_reset", shport)
        self.assertIn("sys_show_message_box(MESSAGEBOX_INFO, title_utf8, msg_utf8);\n\t*res = 0;", shport)

        sactlog = (XSYS35_MOD / "SACT" / "sactlog.c").read_text(encoding="utf-8")
        self.assertIn("SDL_DestroySurface(hline);", sactlog)

        profile = (XSYS35_SRC / "profile.c").read_text(encoding="utf-8")
        self.assertGreaterEqual(profile.count("fclose(fp);"), 4)

        cdmp3 = (XSYS35_SRC / "cdrom.mp3.c").read_text(encoding="utf-8")
        self.assertIn("#ifndef _WIN32", cdmp3)
        self.assertIn("clear_playlist();", cdmp3)

        xsys = (XSYS35_SRC / "xsystem35.c").read_text(encoding="utf-8")
        for fn in (
            "muscd_reset_playlist();",
            "audio_meta_fini();",
            "fc_fini();",
            "volume_fini();",
            "hacks_reset();",
            "save_resetFormat();",
            "chs_tbl_reset();",
            "cmdf_reset();",
        ):
            self.assertIn(fn, xsys)

    def test_cursor_oob_and_reset_under_asan(self):
        """Wave-2 [S2-7]: cursor_load rejects no < 100 and no >= 256 (including 256), cursor_set_type bounds-checks, and cursor_reset frees cursors without destroying default cursor."""
        sdl_cflags = _pkg_flags("sdl2", "--cflags")
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "sdl_compat.h"
#include "ald_manager.h"
#include "cursor.h"

struct SDL_Cursor { int tag; };
static struct SDL_Cursor default_cur = { 999 };
static SDL_Cursor *active_cur = NULL;
static int live_cursors = 0;
static int ald_get_calls = 0;

SDL_Cursor *SDL_GetDefaultCursor(void) { return &default_cur; }
SDL_Cursor *SDL_CreateSystemCursor(SDL_SystemCursor id) {
    struct SDL_Cursor *c = malloc(sizeof(*c));
    c->tag = (int)id;
    live_cursors++;
    return c;
}
SDL_Cursor *SDL_CreateCursor(const Uint8 *data, const Uint8 *mask, int w, int h, int hot_x, int hot_y) {
    (void)data; (void)mask; (void)w; (void)h; (void)hot_x; (void)hot_y;
    struct SDL_Cursor *c = malloc(sizeof(*c));
    c->tag = 500;
    live_cursors++;
    return c;
}
void SDL_FreeCursor(SDL_Cursor *cursor) {
    assert(cursor != NULL);
    assert(cursor != &default_cur);
    live_cursors--;
    free(cursor);
}
void SDL_SetCursor(SDL_Cursor *cursor) { active_cur = cursor; }
void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }
void sys_error(char *fmt, ...) { (void)fmt; abort(); }

static void put_u16(uint8_t *p, uint16_t v) { p[0] = v & 0xff; p[1] = v >> 8; }
static void put_u32(uint8_t *p, uint32_t v) { p[0] = v & 0xff; p[1] = (v >> 8) & 0xff; p[2] = (v >> 16) & 0xff; p[3] = v >> 24; }

dridata *ald_getdata(DRIFILETYPE type, int no) {
    (void)type; (void)no;
    ald_get_calls++;
    // Build minimal valid 32x32 1bpp monochrome .cur blob
    uint8_t *buf = calloc(1, 512);
    put_u16(buf + 0, 0);   // idReserved
    put_u16(buf + 2, 2);   // idType = cursor
    put_u16(buf + 4, 1);   // idCount
    buf[6] = 32;           // bWidth
    buf[7] = 32;           // bHeight
    put_u16(buf + 10, 4);  // wxHotspot
    put_u16(buf + 12, 5);  // wyHotspot
    put_u32(buf + 14, 304);// dwBytesInRes
    put_u32(buf + 18, 22); // dwImageOffset
    // BITMAPINFOHEADER at offset 22
    put_u32(buf + 22, 40); // biSize
    put_u32(buf + 26, 32); // biWidth
    put_u32(buf + 30, 64); // biHeight
    put_u16(buf + 34, 1);  // biPlanes
    put_u16(buf + 36, 1);  // biBitCount
    put_u32(buf + 38, 0);  // biCompression
    put_u32(buf + 42, 256);// biSizeImage
    dridata *d = calloc(1, sizeof(*d));
    d->data = buf;
    d->size = 512;
    return d;
}
void ald_freedata(dridata *d) {
    if (!d) return;
    free(d->data);
    free(d);
}

int main(void) {
    cursor_init();
    assert(live_cursors == 11);

    // Out-of-bounds cursor indices MUST return before calling ald_getdata or writing cursor[no]
    cursor_load(-1, 1);
    cursor_load(0, 1);
    cursor_load(99, 1);
    cursor_load(256, 1);
    cursor_load(999, 1);
    assert(ald_get_calls == 0);
    assert(live_cursors == 11);

    // Valid upper boundary no == 255 succeeds
    cursor_load(255, 1);
    assert(ald_get_calls == 1);
    assert(live_cursors == 12);

    // cursor_set_type bounds checks OOB indices safely
    active_cur = &default_cur;
    cursor_set_type(-1);
    cursor_set_type(256);
    assert(active_cur == &default_cur);
    cursor_set_type(255);
    assert(active_cur != NULL && active_cur != &default_cur);

    // cursor_reset destroys all non-default cursors, restores default, and is idempotent
    cursor_reset();
    assert(live_cursors == 0);
    assert(active_cur == &default_cur);
    cursor_reset();
    assert(live_cursors == 0);
    return 0;
}
"""
        compile_and_run_c(
            harness,
            [XSYS35_SRC / "cursor.c"],
            extra_cflags=sdl_cflags,
            asan=True,
        )

    def test_cmdf_bounds_and_reset_and_shgraph_reset_under_asan(self):
        """Wave-2 [S2-7]: cmdf.c bounds-checks F6/F7 and clears F6Index on cmdf_reset(); ShGraph registers reset clearing stale vmvar_t* pointers."""
        sdl_cflags = _pkg_flags("sdl2", "--cflags")
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cmd_check.h"
#include "hacks.h"
#include "modules.h"
#include "nact.h"

static NACTINFO nact_state;
NACTINFO *nact = &nact_state;
bool daiakuji_cx_hack = false;

static uint8_t subcmd_queue[32];
static int subcmd_w = 0;
const uint8_t *sl_sco = subcmd_queue;
int sl_index = 0;
static int val_queue[64];
static int val_r = 0, val_w = 0;
static vmvar_t *var_queue[16];
static int var_r = 0, var_w = 0;

static void push_subcmd(int v) { subcmd_queue[subcmd_w++] = (uint8_t)v; }
static void push_val(int v) { val_queue[val_w++] = v; }
static void push_var(vmvar_t *p) { var_queue[var_w++] = p; }

int getCaliValue(void) { assert(val_r < val_w); return val_queue[val_r++]; }
vmvar_t *getCaliVariable(void) { assert(var_r < var_w); return var_queue[var_r++]; }
void svar_set(int no, const char *str) { (void)no; (void)str; }
void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }
void sys_error(char *fmt, ...) { (void)fmt; abort(); }
bool ags_check_param(int *x, int *y, int *w, int *h) { (void)x; (void)y; (void)w; (void)h; return true; }
surface_t *ags_getDIB(void) { return NULL; }
void ags_copyArea(int sx, int sy, int w, int h, int dx, int dy) { (void)sx; (void)sy; (void)w; (void)h; (void)dx; (void)dy; }
void ags_updateArea(int x, int y, int w, int h) { (void)x; (void)y; (void)w; (void)h; }
bool mus_wav_stop(int ch) { (void)ch; return true; }
bool mus_wav_play(int ch, int loop) { (void)ch; (void)loop; return true; }
uint32_t sys_get_ticks(void) { return 0; }
void sys_sleep(int msec) { (void)msec; }
Uint32 SDL_MapRGB(const SDL_PixelFormat *fmt, Uint8 r, Uint8 g, Uint8 b) { (void)fmt; (void)r; (void)g; (void)b; return 0; }
int SDL_SetColorKey(SDL_Surface *s, int flag, Uint32 key) { (void)s; (void)flag; (void)key; return 0; }
int SDL_UpperBlit(SDL_Surface *src, const SDL_Rect *sr, SDL_Surface *dst, SDL_Rect *dr) { (void)src; (void)sr; (void)dst; (void)dr; return 0; }

static void (*find_mod_fn(const Module *m, const char *name))(void) {
    for (int i = 0; i < m->nfunc; i++) {
        if (strcmp(m->funcs[i].name, name) == 0)
            return m->funcs[i].entrypoint;
    }
    return NULL;
}

int main(void) {
    // 1. cmdf.c F6 OOB index rejected safely
    vmvar_t *slot0 = calloc(4, sizeof(vmvar_t));
    uint8_t tbl[8] = { 0x34, 0x12, 0x78, 0x56, 0x9a, 0xbc, 0xde, 0xf0 };
    nact->datatbl_addr = tbl;

    push_subcmd(6); push_var(slot0); push_val(-1); commandF();
    push_subcmd(6); push_var(slot0); push_val(256); commandF();
    push_subcmd(6); push_var(slot0); push_val(999); commandF();

    // Valid F6 at index 0 + valid F7
    push_subcmd(6); push_var(slot0); push_val(0); commandF();
    push_subcmd(7); push_val(1); push_val(2); commandF();
    assert(slot0[0] == 0x1234 && slot0[1] == 0x5678);

    // Invalid F7 data_width / count rejected safely
    push_subcmd(7); push_val(257); push_val(1); commandF();
    push_subcmd(7); push_val(-1); push_val(1); commandF();
    push_subcmd(7); push_val(1); push_val(-1); commandF();

    // cmdf_reset clears F6Index[]; freeing slot0 and invoking F7 must NOT UAF
    cmdf_reset();
    free(slot0);
    nact->datatbl_addr = tbl;
    push_subcmd(7); push_val(1); push_val(1); commandF();

    // 2. ShGraph registers module reset clearing s0/add_p5 vmvar_t* pointers
    assert(module_ShGraph.reset != NULL);
    void (*fn_src)(void) = find_mod_fn(&module_ShGraph, "SetAnimeSrc");
    void (*fn_dst)(void) = find_mod_fn(&module_ShGraph, "SetAnimeDst");
    void (*fn_add)(void) = find_mod_fn(&module_ShGraph, "AddAnimeData");
    void (*fn_play)(void) = find_mod_fn(&module_ShGraph, "PlayAnimeData");
    assert(fn_src && fn_dst && fn_add && fn_play);

    vmvar_t pal[3] = { 0, 0, 0 };
    vmvar_t *vx = calloc(1, sizeof(vmvar_t));
    vmvar_t *vy = calloc(1, sizeof(vmvar_t));
    vmvar_t *vp5 = calloc(1, sizeof(vmvar_t));

    // SetAnimeSrc(1, 0, 0, 16, 16, 2, 2, pal)
    push_val(1); push_val(0); push_val(0); push_val(16); push_val(16); push_val(2); push_val(2); push_var(pal);
    fn_src();
    // SetAnimeDst(1, vx, vy, 10000, 10000, 10000, 10000)
    push_val(1); push_var(vx); push_var(vy); push_val(10000); push_val(10000); push_val(10000); push_val(10000);
    fn_dst();
    // AddAnimeData(1, 1, 1, 2, vp5, 0)
    push_val(1); push_val(1); push_val(1); push_val(2); push_var(vp5); push_val(0);
    fn_add();

    // Reset module, free backing vmvar_t buffers, and ensure PlayAnimeData never UAFs
    module_ShGraph.reset();
    free(vx);
    free(vy);
    free(vp5);

    push_val(0); push_val(0); push_val(0); push_val(0); push_val(0); push_val(0);
    fn_play();
    push_val(1); push_val(0); push_val(0); push_val(0); push_val(0); push_val(0);
    fn_play();
    return 0;
}
"""
        compile_and_run_c(
            harness,
            [XSYS35_SRC / "cmdf.c", XSYS35_MOD / "ShGraph" / "ShGraph.c"],
            extra_cflags=sdl_cflags,
            asan=True,
        )

    def test_sact_reset_sactamask_and_sactlog_cleanup_under_asan(self):
        """Wave-2 [S2-7]: smask_reset unmaps Game A SACTEFAM.KLD; sblog_end/sblog_reset clear back surface & free sact.log nodes/strings safely."""
        sdl_cflags = _pkg_flags("sdl2", "--cflags")
        sdl_libs = _pkg_flags("sdl2", "--libs")
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "sact.h"
#include "sactamask.h"
#include "sactlog.h"
#include "pms.h"
#include "gfx.h"

sact_t sactprv;
SDL_Surface *main_surface = NULL;
static uint8_t last_mask_byte = 0;
static int pms_calls = 0;

void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }
void sys_error(char *fmt, ...) { (void)fmt; abort(); }
void ags_updateFull(void) {}
void ags_runEffect(int d, bool c, ags_EffectStepFunc step, void *arg) { (void)d; (void)c; (void)step; (void)arg; }
struct effect *effect_sactamask_init(SDL_Surface *sf) { (void)sf; return NULL; }
void effect_step(struct effect *e, float p) { (void)e; (void)p; }
void effect_finish(struct effect *e) { (void)e; }
void sp_update_all(bool sync) { (void)sync; }
SDL_Rect gfx_drawString(int x, int y, const char *str, uint8_t col, FontSpec font) {
    (void)x; (void)y; (void)str; (void)col; (void)font;
    return (SDL_Rect){0, 0, 10, 10};
}
cgdata *pms256_extract(uint8_t *data) {
    pms_calls++;
    last_mask_byte = data[0];
    return NULL;
}
void cgdata_free(cgdata *cg) { (void)cg; }

static void write_kld(const char *path, uint32_t mask_no, uint8_t payload_byte) {
    uint8_t buf[64];
    memset(buf, 0, sizeof(buf));
    // datanum = 1 at offset 0
    buf[0] = 1;
    // entry 0 at offset 16: no at +0, offset at +8
    buf[16] = (uint8_t)(mask_no & 0xff);
    buf[24] = 32; // offset = 32
    buf[32] = payload_byte;
    FILE *fp = fopen(path, "wb");
    assert(fp != NULL);
    assert(fwrite(buf, 1, sizeof(buf), fp) == sizeof(buf));
    assert(fclose(fp) == 0);
}

int main(void) {
    // 1. sactamask: Game A mmap -> smask_reset() -> Game B mmap replaces Game A cleanly
    char path_a[] = "/tmp/audit_kld_a_XXXXXX";
    char path_b[] = "/tmp/audit_kld_b_XXXXXX";
    int fda = mkstemp(path_a); assert(fda >= 0); close(fda);
    int fdb = mkstemp(path_b); assert(fdb >= 0); close(fdb);
    write_kld(path_a, 10, 0xAA);
    write_kld(path_b, 20, 0xBB);

    assert(smask_init(path_a));
    sp_eupdate_amap(10, 0, 0);
    assert(pms_calls == 1 && last_mask_byte == 0xAA);

    smask_reset();
    pms_calls = 0;
    sp_eupdate_amap(10, 0, 0);
    assert(pms_calls == 0);

    assert(smask_init(path_b));
    sp_eupdate_amap(20, 0, 0);
    assert(pms_calls == 1 && last_mask_byte == 0xBB);
    smask_reset();
    smask_reset(); // idempotent

    unlink(path_a);
    unlink(path_b);

    // 2. sactlog: back surface cleared on sblog_end & sblog_reset, heap strings freed without freeing static logmsg[]
    main_surface = sdl_CreateSurface(320, 240, SDL_PIXELFORMAT_XRGB8888);
    assert(main_surface != NULL);
    sact.version = 120;
    sact.log = list_append(sact.log, strdup("line one"));
    sact.log = list_append(sact.log, strdup("\n"));

    assert(sblog_start());
    sblog_end();
    // Second sblog_end() must NOT double-free `back`
    sblog_end();

    // Open backlog again and invoke sblog_reset() while static logmsg[] and `back` are live
    assert(sblog_start());
    sblog_reset();
    assert(sact.log == NULL);
    // Idempotent second reset
    sblog_reset();

    SDL_DestroySurface(main_surface);
    main_surface = NULL;
    return 0;
}
"""
        compile_and_run_c(
            harness,
            [
                XSYS35_MOD / "SACT" / "sactamask.c",
                XSYS35_MOD / "SACT" / "sactlog.c",
                XSYS35_MOD / "SACT" / "list.c",
                XSYS35_SRC / "mmap.c",
            ],
            extra_cflags=sdl_cflags + [f"-I{XSYS35_MOD / 'SACT'}"],
            extra_ldflags=sdl_libs,
            asan=True,
        )

        sact_c = (XSYS35_MOD / "SACT" / "SACT.c").read_text(encoding="utf-8")
        sact_reset_block = sact_c[sact_c.index("static void SACT_reset"):sact_c.index("static const ModuleFunc")]
        self.assertIn("smask_reset();", sact_reset_block)
        self.assertIn("sblog_reset();", sact_reset_block)

    def test_android_jni_input_modal_midi_and_bridge_under_asan(self):
        """Wave-2 [S3-1, S3-3, S3-5, S3-16]: Android JNI UTF-8->UTF-16 NewString, exception/NULL/local-ref guards, MIDI I/O verification, bridge OOM clearing & ctrl_skip_applied sync."""
        sdl_cflags = _pkg_flags("sdl2", "--cflags")
        mock_jni_h = r"""
#ifndef MOCK_JNI_H
#define MOCK_JNI_H
#include <stdint.h>
#include <stddef.h>
#include <stdarg.h>

typedef uint8_t jboolean;
typedef uint16_t jchar;
typedef int32_t jint;
typedef int32_t jsize;
typedef float jfloat;
typedef void *jobject;
typedef jobject jclass;
typedef jobject jstring;
typedef void *jmethodID;

#define JNI_FALSE 0
#define JNI_TRUE 1
#define JNIEXPORT
#define JNICALL

struct JNINativeInterface_;
typedef const struct JNINativeInterface_ *JNIEnv;

struct JNINativeInterface_ {
    jint (*PushLocalFrame)(JNIEnv *env, jint capacity);
    jobject (*PopLocalFrame)(JNIEnv *env, jobject result);
    void (*DeleteLocalRef)(JNIEnv *env, jobject ref);
    jboolean (*ExceptionCheck)(JNIEnv *env);
    void (*ExceptionClear)(JNIEnv *env);
    jclass (*GetObjectClass)(JNIEnv *env, jobject obj);
    jmethodID (*GetMethodID)(JNIEnv *env, jclass clazz, const char *name, const char *sig);
    jobject (*CallObjectMethod)(JNIEnv *env, jobject obj, jmethodID mid, ...);
    jint (*CallIntMethod)(JNIEnv *env, jobject obj, jmethodID mid, ...);
    void (*CallVoidMethod)(JNIEnv *env, jobject obj, jmethodID mid, ...);
    jboolean (*CallBooleanMethod)(JNIEnv *env, jobject obj, jmethodID mid, ...);
    jstring (*NewString)(JNIEnv *env, const jchar *unicodeChars, jsize len);
    jstring (*NewStringUTF)(JNIEnv *env, const char *bytes);
    jsize (*GetStringUTFLength)(JNIEnv *env, jstring str);
    const char *(*GetStringUTFChars)(JNIEnv *env, jstring str, jboolean *isCopy);
    void (*ReleaseStringUTFChars)(JNIEnv *env, jstring str, const char *utf);
};
#endif
"""
        with tempfile.TemporaryDirectory(prefix="audit_mock_jni_") as jni_dir:
            (Path(jni_dir) / "jni.h").write_text(mock_jni_h, encoding="utf-8")

            # A. input_modal.c Android JNI safety + UTF-8 -> UTF-16 conversion
            harness_modal = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include "input_modal.h"

void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }

typedef struct {
    int kind; // 1=jstring, 2=activity, 3=class
    jchar *u16;
    jsize u16_len;
    char *utf8;
} MockObj;

static bool pending_exc = false;
static int live_refs = 0;
static int frame_depth = 0;
static bool return_null_env = false;
static bool return_null_activity = false;
static bool return_null_cls = false;
static bool return_null_mid = false;
static bool throw_on_call_obj = false;
static bool throw_on_call_int = false;
static bool oom_on_get_utf_chars = false;
static jint mock_int_ret = 42;
static const char *mock_str_ret = "typed_ok";

static jchar last_u16_0[512];
static jsize last_u16_0_len = 0;
static jchar last_u16_1[512];
static jsize last_u16_1_len = 0;
static int new_string_calls = 0;

static void check_no_pending(void) { assert(!pending_exc); }

static jint j_push_frame(JNIEnv *e, jint cap) { (void)e; (void)cap; check_no_pending(); frame_depth++; return 0; }
static jobject j_pop_frame(JNIEnv *e, jobject res) { (void)e; (void)res; assert(frame_depth > 0); frame_depth--; return NULL; }
static void j_del_ref(JNIEnv *e, jobject ref) {
    (void)e;
    if (!ref) return;
    MockObj *o = (MockObj *)ref;
    free(o->u16);
    free(o->utf8);
    free(o);
    live_refs--;
}
static jboolean j_exc_check(JNIEnv *e) { (void)e; return pending_exc ? JNI_TRUE : JNI_FALSE; }
static void j_exc_clear(JNIEnv *e) { (void)e; pending_exc = false; }
static jclass j_get_cls(JNIEnv *e, jobject obj) {
    (void)e; check_no_pending(); assert(obj != NULL);
    if (return_null_cls) return NULL;
    MockObj *c = calloc(1, sizeof(*c)); c->kind = 3; live_refs++; return c;
}
static jmethodID j_get_mid(JNIEnv *e, jclass cls, const char *name, const char *sig) {
    (void)e; (void)name; (void)sig; check_no_pending(); assert(cls != NULL);
    if (return_null_mid) { pending_exc = true; return NULL; }
    return (jmethodID)0x1234;
}
static jstring j_new_string(JNIEnv *e, const jchar *chars, jsize len) {
    (void)e; check_no_pending(); assert(chars != NULL && len >= 0);
    MockObj *s = calloc(1, sizeof(*s));
    s->kind = 1;
    s->u16_len = len;
    if (len > 0) {
        s->u16 = malloc((size_t)len * sizeof(jchar));
        memcpy(s->u16, chars, (size_t)len * sizeof(jchar));
    }
    if (new_string_calls == 0 && len <= 512) {
        memcpy(last_u16_0, chars, (size_t)len * sizeof(jchar));
        last_u16_0_len = len;
    } else if (new_string_calls == 1 && len <= 512) {
        memcpy(last_u16_1, chars, (size_t)len * sizeof(jchar));
        last_u16_1_len = len;
    }
    new_string_calls++;
    live_refs++;
    return s;
}
static jstring j_new_string_utf(JNIEnv *e, const char *b) {
    (void)e; (void)b;
    // input_modal.c must NEVER call NewStringUTF!
    assert(0 && "input_modal.c must use NewString, not NewStringUTF");
    return NULL;
}
static jobject j_call_obj(JNIEnv *e, jobject obj, jmethodID mid, ...) {
    (void)e; check_no_pending(); assert(obj != NULL && mid != NULL);
    if (throw_on_call_obj) { pending_exc = true; return NULL; }
    if (!mock_str_ret) return NULL;
    MockObj *s = calloc(1, sizeof(*s));
    s->kind = 1;
    s->utf8 = strdup(mock_str_ret);
    live_refs++;
    return s;
}
static jint j_call_int(JNIEnv *e, jobject obj, jmethodID mid, ...) {
    (void)e; check_no_pending(); assert(obj != NULL && mid != NULL);
    if (throw_on_call_int) { pending_exc = true; return 0; }
    return mock_int_ret;
}
static jsize j_get_utf_len(JNIEnv *e, jstring s) {
    (void)e; check_no_pending(); MockObj *o = (MockObj *)s;
    return (jsize)strlen(o->utf8);
}
static const char *j_get_utf_chars(JNIEnv *e, jstring s, jboolean *isCopy) {
    (void)e; (void)isCopy; check_no_pending();
    if (oom_on_get_utf_chars) { pending_exc = true; return NULL; }
    MockObj *o = (MockObj *)s;
    return o->utf8;
}
static void j_rel_utf_chars(JNIEnv *e, jstring s, const char *utf) { (void)e; (void)s; (void)utf; }

static const struct JNINativeInterface_ vtbl = {
    .PushLocalFrame = j_push_frame,
    .PopLocalFrame = j_pop_frame,
    .DeleteLocalRef = j_del_ref,
    .ExceptionCheck = j_exc_check,
    .ExceptionClear = j_exc_clear,
    .GetObjectClass = j_get_cls,
    .GetMethodID = j_get_mid,
    .CallObjectMethod = j_call_obj,
    .CallIntMethod = j_call_int,
    .NewString = j_new_string,
    .NewStringUTF = j_new_string_utf,
    .GetStringUTFLength = j_get_utf_len,
    .GetStringUTFChars = j_get_utf_chars,
    .ReleaseStringUTFChars = j_rel_utf_chars,
};
static JNIEnv g_env = &vtbl;

void *SDL_AndroidGetJNIEnv(void) { return return_null_env ? NULL : &g_env; }
void *SDL_AndroidGetActivity(void) {
    check_no_pending();
    if (return_null_activity) return NULL;
    MockObj *a = calloc(1, sizeof(*a)); a->kind = 2; live_refs++; return a;
}

int main(void) {
    // 1. Non-BMP emoji + CJK + invalid UTF-8 converted cleanly to UTF-16 via NewString
    INPUTSTRING_PARAM sp = {
        .title = "\xe4\xb8\xad" "\xf0\x9f\x98\x80" "A", // 中 + U+1F600 + A
        .oldstring = "\xc0\xaf\xed\xa0\x80\xff\xe4\xb8", // overlong, surrogate, 0xff, truncated
        .max = 32,
    };
    new_string_calls = 0;
    assert(input_modal_string(&sp));
    assert(strcmp(sp.newstring, "typed_ok") == 0);
    assert(last_u16_0_len == 4);
    assert(last_u16_0[0] == 0x4E2D);
    assert(last_u16_0[1] == 0xD83D);
    assert(last_u16_0[2] == 0xDE00);
    assert(last_u16_0[3] == 'A');
    assert(last_u16_1_len >= 4);
    for (jsize i = 0; i < last_u16_1_len; i++) assert(last_u16_1[i] == 0xFFFD);
    assert(live_refs == 0 && frame_depth == 0 && !pending_exc);

    // 2. Long >256-byte string exercises heap buffer branch & NULL title/oldstring safely handled
    char long_str[400];
    memset(long_str, 'X', 399);
    long_str[399] = '\0';
    INPUTSTRING_PARAM sp2 = { .title = NULL, .oldstring = long_str, .max = 400 };
    new_string_calls = 0;
    assert(input_modal_string(&sp2));
    assert(last_u16_0_len == 0 && last_u16_1_len == 399);
    assert(live_refs == 0 && frame_depth == 0 && !pending_exc);

    // 3. NULL guards (env, activity, cls, mid) & Java exceptions cleared cleanly
    return_null_env = true;
    assert(!input_modal_string(&sp2));
    INPUTNUM_PARAM np = { .title = NULL, .min = 0, .max = 100, .def = 5 };
    assert(!input_modal_number(&np));
    return_null_env = false;

    return_null_activity = true;
    assert(!input_modal_string(&sp2));
    assert(!input_modal_number(&np));
    return_null_activity = false;
    assert(live_refs == 0 && !pending_exc);

    return_null_cls = true;
    assert(!input_modal_string(&sp2));
    assert(!input_modal_number(&np));
    return_null_cls = false;
    assert(live_refs == 0 && !pending_exc);

    return_null_mid = true;
    assert(!input_modal_string(&sp2));
    assert(!input_modal_number(&np));
    return_null_mid = false;
    assert(live_refs == 0 && !pending_exc);

    throw_on_call_obj = true;
    assert(!input_modal_string(&sp2));
    throw_on_call_obj = false;
    assert(live_refs == 0 && !pending_exc);

    oom_on_get_utf_chars = true;
    assert(!input_modal_string(&sp2));
    oom_on_get_utf_chars = false;
    assert(live_refs == 0 && !pending_exc);

    throw_on_call_int = true;
    assert(!input_modal_number(&np));
    throw_on_call_int = false;
    assert(live_refs == 0 && !pending_exc);

    // 4. Valid number input succeeds
    assert(input_modal_number(&np));
    assert(np.value == 42);
    assert(live_refs == 0 && frame_depth == 0 && !pending_exc);
    return 0;
}
"""
            compile_and_run_c(
                harness_modal,
                [XSYS35_SRC / "input_modal.c"],
                extra_cflags=[f"-I{jni_dir}", "-D__ANDROID__"] + sdl_cflags,
                asan=True,
            )

            # B. midi.android.c JNI safety + I/O verification
            harness_midi = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <jni.h>

#include "midi.h"
#include "music_private.h"

extern mididevice_t midi_android;
struct _musprvdat prv;
void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }

static bool pending_exc = false;
static int live_refs = 0;
static int frame_depth = 0;
static bool null_env = false;
static bool null_activity = false;
static bool null_cls = false;
static bool null_mid = false;
static bool null_str = false;
static bool throw_void = false;
static bool throw_int = false;
static bool throw_bool = false;
static int midi_start_calls = 0;
static const char *storage_dir = NULL;

static void check_no_pending(void) { assert(!pending_exc); }
static jint j_push_frame(JNIEnv *e, jint cap) { (void)e; (void)cap; check_no_pending(); frame_depth++; return 0; }
static jobject j_pop_frame(JNIEnv *e, jobject res) { (void)e; (void)res; assert(frame_depth > 0); frame_depth--; return NULL; }
static void j_del_ref(JNIEnv *e, jobject ref) { (void)e; if (!ref) return; free(ref); live_refs--; }
static jboolean j_exc_check(JNIEnv *e) { (void)e; return pending_exc ? JNI_TRUE : JNI_FALSE; }
static void j_exc_clear(JNIEnv *e) { (void)e; pending_exc = false; }
static jclass j_get_cls(JNIEnv *e, jobject obj) {
    (void)e; check_no_pending(); assert(obj != NULL);
    if (null_cls) return NULL;
    live_refs++; return malloc(8);
}
static jmethodID j_get_mid(JNIEnv *e, jclass cls, const char *name, const char *sig) {
    (void)e; (void)sig; check_no_pending(); assert(cls != NULL);
    if (null_mid) { pending_exc = true; return NULL; }
    return (jmethodID)name;
}
static jstring j_new_str(JNIEnv *e, const char *b) {
    (void)e; check_no_pending(); assert(b != NULL);
    if (null_str) { pending_exc = true; return NULL; }
    live_refs++; return strdup(b);
}
static void j_call_void(JNIEnv *e, jobject obj, jmethodID mid, ...) {
    (void)e; check_no_pending(); assert(obj != NULL && mid != NULL);
    if (strcmp((const char *)mid, "midiStart") == 0) midi_start_calls++;
    if (throw_void) pending_exc = true;
}
static jint j_call_int(JNIEnv *e, jobject obj, jmethodID mid, ...) {
    (void)e; check_no_pending(); assert(obj != NULL && mid != NULL);
    if (throw_int) { pending_exc = true; return 0; }
    return 1234;
}
static jboolean j_call_bool(JNIEnv *e, jobject obj, jmethodID mid, ...) {
    (void)e; check_no_pending(); assert(obj != NULL && mid != NULL);
    if (throw_bool) { pending_exc = true; return JNI_TRUE; }
    return JNI_TRUE;
}

static const struct JNINativeInterface_ vtbl = {
    .PushLocalFrame = j_push_frame,
    .PopLocalFrame = j_pop_frame,
    .DeleteLocalRef = j_del_ref,
    .ExceptionCheck = j_exc_check,
    .ExceptionClear = j_exc_clear,
    .GetObjectClass = j_get_cls,
    .GetMethodID = j_get_mid,
    .CallVoidMethod = j_call_void,
    .CallIntMethod = j_call_int,
    .CallBooleanMethod = j_call_bool,
    .NewStringUTF = j_new_str,
};
static JNIEnv g_env = &vtbl;

void *SDL_AndroidGetJNIEnv(void) { return null_env ? NULL : &g_env; }
void *SDL_AndroidGetActivity(void) {
    check_no_pending();
    if (null_activity) return NULL;
    live_refs++; return malloc(8);
}
const char *SDL_AndroidGetInternalStoragePath(void) { return storage_dir; }

int main(void) {
    prv.volval[BGM_VOLVAL_CH] = 100;
    const uint8_t sample[] = { 'M', 'T', 'h', 'd', 0, 0, 0, 6 };

    // 1. NULL storage path & unwritable path fail cleanly before invoking midiStart
    storage_dir = NULL;
    assert(!midi_android.start(1, 0, sample, sizeof(sample)));
    assert(midi_start_calls == 0);

    storage_dir = "/nonexistent_audit_midi_dir_xyz";
    assert(!midi_android.start(1, 0, sample, sizeof(sample)));
    assert(midi_start_calls == 0);

    // 2. Writable storage dir writes exact file and invokes midiStart
    char dir[] = "/tmp/audit_midi_XXXXXX";
    assert(mkdtemp(dir) != NULL);
    storage_dir = dir;
    assert(midi_android.start(1, 1, sample, sizeof(sample)));
    assert(midi_start_calls == 1);
    assert(live_refs == 0 && frame_depth == 0 && !pending_exc);

    char tmp_mid[256];
    snprintf(tmp_mid, sizeof(tmp_mid), "%s/tmp.mid", dir);
    FILE *fp = fopen(tmp_mid, "rb");
    assert(fp != NULL);
    uint8_t got[8] = {0};
    assert(fread(got, 1, 8, fp) == 8);
    fclose(fp);
    assert(memcmp(got, sample, 8) == 0);

    // 3. NULL env / activity / cls / mid / NewStringUTF & Java exceptions handled safely
    null_env = true;
    assert(!midi_android.start(1, 0, sample, sizeof(sample)));
    midiplaystate st;
    assert(!midi_android.getpos(&st));
    assert(!midi_android.fadestart(100, 50, 0));
    assert(!midi_android.fading());
    midi_android.stop();
    null_env = false;

    null_activity = true;
    assert(!midi_android.start(1, 0, sample, sizeof(sample)));
    assert(!midi_android.getpos(&st));
    assert(!midi_android.fadestart(100, 50, 0));
    assert(!midi_android.fading());
    midi_android.stop();
    null_activity = false;
    assert(live_refs == 0 && !pending_exc);

    null_cls = true;
    assert(!midi_android.start(1, 0, sample, sizeof(sample)));
    assert(!midi_android.getpos(&st));
    null_cls = false;
    assert(live_refs == 0 && !pending_exc);

    null_mid = true;
    assert(!midi_android.start(1, 0, sample, sizeof(sample)));
    assert(!midi_android.getpos(&st));
    assert(!midi_android.fadestart(100, 50, 0));
    assert(!midi_android.fading());
    null_mid = false;
    assert(live_refs == 0 && !pending_exc);

    null_str = true;
    assert(!midi_android.start(1, 0, sample, sizeof(sample)));
    null_str = false;
    assert(live_refs == 0 && !pending_exc);

    throw_void = throw_int = throw_bool = true;
    assert(!midi_android.start(1, 0, sample, sizeof(sample)));
    assert(!midi_android.getpos(&st));
    assert(!midi_android.fadestart(100, 50, 0));
    assert(!midi_android.fading());
    midi_android.stop();
    throw_void = throw_int = throw_bool = false;
    assert(live_refs == 0 && frame_depth == 0 && !pending_exc);

    // 4. Normal getpos / fadestart / fading succeed
    assert(midi_android.getpos(&st));
    assert(st.in_play && st.loc_ms == 1234);
    assert(midi_android.fadestart(100, 50, 0));
    assert(midi_android.fading());
    assert(live_refs == 0 && frame_depth == 0 && !pending_exc);

    unlink(tmp_mid);
    rmdir(dir);
    return 0;
}
"""
            compile_and_run_c(
                harness_midi,
                [XSYS35_SRC / "midi.android.c"],
                extra_cflags=[f"-I{jni_dir}", "-D__ANDROID__"] + sdl_cflags,
                asan=True,
            )

            # C. alicehub_bridge.c pending JNI OOM clearing + ctrl_skip_applied mutex sync
            harness_bridge = r"""
#include <assert.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>

#include "sdl_compat.h"
#include "alicehub_bridge.h"

extern void Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeSetGamepadConfigJson(JNIEnv *, jclass, jstring);
extern void Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeApplyLiveAction(JNIEnv *, jclass, jstring);
extern void Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetCtrlSkip(JNIEnv *, jclass, jboolean);
extern jboolean Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetCtrlSkip(JNIEnv *, jclass);
extern jboolean Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeExportPcSaveFile(JNIEnv *, jclass, jstring, jstring);

// Stubs for xsystem35 runtime hooks used by alicehub_bridge.c
void event_inject_mouse_motion(int x, int y) { (void)x; (void)y; }
void event_inject_mouse_button(int b, bool p, int x, int y) { (void)b; (void)p; (void)x; (void)y; }
void event_inject_mouse_wheel(int d) { (void)d; }
void event_inject_rawkey(int k, bool p) { (void)k; (void)p; }
void event_inject_joy_mask(int m, bool p) { (void)m; (void)p; }
void msgskip_activate(bool enable) { (void)enable; }
void escalayer_modifier_tick(void) {}
char *xsystem35_modifier_live_state(void) { return strdup("{}"); }
void xsystem35_modifier_set_locks(int m) { (void)m; }
int xsystem35_modifier_get_locks(void) { return 0; }
void xsystem35_modifier_apply_action(const char *a) { (void)a; }
const char *xsystem35_modifier_current_game(void) { return ""; }
SDL_Window *gfx_getWindow(void) { return NULL; }
void gfx_getViewSize(int *w, int *h) { *w = 640; *h = 480; }
SDL_Rect gfx_viewToWindowRect(SDL_Rect r) { return r; }
void SDL_GetWindowSize(SDL_Window *w, int *ww, int *wh) { (void)w; *ww = 640; *wh = 480; }
Uint32 SDL_GetTicks(void) { return 1000; }
int SDL_AndroidSendMessage(Uint32 cmd, int param) { (void)cmd; (void)param; return 0; }

static bool pending_exc = false;
static int fail_utf_call_index = -1;
static int utf_call_count = 0;
static int release_calls = 0;

static jboolean j_exc_check(JNIEnv *e) { (void)e; return pending_exc ? JNI_TRUE : JNI_FALSE; }
static void j_exc_clear(JNIEnv *e) { (void)e; pending_exc = false; }
static const char *j_get_utf(JNIEnv *e, jstring s, jboolean *isCopy) {
    (void)e; (void)isCopy;
    // CheckJNI invariant: MUST NEVER call GetStringUTFChars while an exception is pending!
    assert(!pending_exc);
    int idx = utf_call_count++;
    if (idx == fail_utf_call_index) {
        pending_exc = true;
        return NULL;
    }
    return (const char *)s;
}
static void j_rel_utf(JNIEnv *e, jstring s, const char *utf) {
    (void)e; (void)s; (void)utf;
    release_calls++;
}

static const struct JNINativeInterface_ vtbl = {
    .ExceptionCheck = j_exc_check,
    .ExceptionClear = j_exc_clear,
    .GetStringUTFChars = j_get_utf,
    .ReleaseStringUTFChars = j_rel_utf,
};
static JNIEnv g_env = &vtbl;

static void *ui_ctrl_worker(void *arg) {
    (void)arg;
    for (int i = 0; i < 2000; i++) {
        Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetCtrlSkip(&g_env, NULL, (i & 1) ? JNI_TRUE : JNI_FALSE);
        (void)Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetCtrlSkip(&g_env, NULL);
    }
    return NULL;
}

int main(void) {
    // 1. First GetStringUTFChars fails with OOM in nativeExportPcSaveFile:
    //    must NOT call second GetStringUTFChars with pending exception, and must clear exception!
    pending_exc = false;
    utf_call_count = 0;
    release_calls = 0;
    fail_utf_call_index = 0;
    assert(Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeExportPcSaveFile(
               &g_env, NULL, (jstring)"/tmp/a", (jstring)"/tmp/b") == JNI_FALSE);
    assert(utf_call_count == 1);
    assert(release_calls == 0);
    assert(!pending_exc);

    // 2. Second GetStringUTFChars fails with OOM: clears exception and releases first string
    utf_call_count = 0;
    release_calls = 0;
    fail_utf_call_index = 1;
    assert(Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeExportPcSaveFile(
               &g_env, NULL, (jstring)"/tmp/a", (jstring)"/tmp/b") == JNI_FALSE);
    assert(utf_call_count == 2);
    assert(release_calls == 1);
    assert(!pending_exc);

    // 3. nativeSetGamepadConfigJson & nativeApplyLiveAction clear pending OOM exception
    utf_call_count = 0;
    fail_utf_call_index = 0;
    Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeSetGamepadConfigJson(&g_env, NULL, (jstring)"{}");
    assert(!pending_exc);

    utf_call_count = 0;
    fail_utf_call_index = 0;
    Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeApplyLiveAction(&g_env, NULL, (jstring)"esc_heal");
    assert(!pending_exc);

    // 4. Multithreaded ctrl_skip_applied synchronization stress test
    pthread_t th;
    assert(pthread_create(&th, NULL, ui_ctrl_worker, NULL) == 0);
    for (int i = 0; i < 2000; i++) {
        alicehub_pump_events();
    }
    assert(pthread_join(th, NULL) == 0);
    Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetCtrlSkip(&g_env, NULL, JNI_TRUE);
    alicehub_pump_events();
    assert(Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetCtrlSkip(&g_env, NULL) == JNI_TRUE);
    Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetCtrlSkip(&g_env, NULL, JNI_FALSE);
    alicehub_pump_events();
    assert(Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetCtrlSkip(&g_env, NULL) == JNI_FALSE);
    return 0;
}
"""
            compile_and_run_c(
                harness_bridge,
                [XSYS35_SRC / "alicehub_bridge.c", XSYS35_SRC / "cJSON.c"],
                extra_cflags=[f"-I{jni_dir}", "-D__ANDROID__", "-DSDL_MAIN_HANDLED", "-pthread"] + sdl_cflags,
                extra_ldflags=["-pthread", "-lm"],
                asan=True,
            )

    def test_escalayer_modifier_esc_cache_thread_safety_under_asan(self):
        """Wave-2 [S3-4]: esc_cache refresh & reads are protected by esc_cache_lock and checked is set only after full population."""
        sdl_cflags = _pkg_flags("sdl2", "--cflags")
        harness = r"""
#include <assert.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "escalayer_modifier.h"
#include "nact.h"
#include "variable.h"

static NACTINFO nact_state;
NACTINFO *nact = &nact_state;
static uint32_t fake_ticks = 1000;

Uint32 SDL_GetTicks(void) { return __atomic_add_fetch(&fake_ticks, 300, __ATOMIC_RELAXED); }
char *SDL_getenv(const char *name) { return getenv(name); }
void sys_message(int lv, char *fmt, ...) { (void)lv; (void)fmt; }
void sys_error(char *fmt, ...) { (void)fmt; abort(); }

static volatile int stop_readers = 0;

static void *reader_thread(void *arg) {
    (void)arg;
    while (!__atomic_load_n(&stop_readers, __ATOMIC_ACQUIRE)) {
        const char *g = xsystem35_modifier_current_game();
        assert(strcmp(g, "escalayer") == 0);
        assert(escalayer_modifier_available());
        char *js = xsystem35_modifier_live_state();
        assert(js != NULL);
        free(js);
    }
    return NULL;
}

int main(void) {
    v_init();
    static char *var_names[] = {
        "dummy0",
        "☆ＨＰ",
        "☆ＨＰＭＡＸ",
        "☆リビドー",
        "☆リビドーＭＡＸ",
        "☆Ｄ２",
    };
    nact->ain.varnum = (int)(sizeof(var_names) / sizeof(var_names[0]));
    nact->ain.var = var_names;
    v_set_names(nact->ain.varnum, var_names);
    sysVar[1] = 50;
    sysVar[2] = 100;

    pthread_t th[4];
    for (int i = 0; i < 4; i++)
        assert(pthread_create(&th[i], NULL, reader_thread, NULL) == 0);

    for (int i = 0; i < 500; i++) {
        xsystem35_modifier_set_locks(i & 1);
        xsystem35_modifier_apply_action("esc_heal");
        escalayer_modifier_tick();
    }
    __atomic_store_n(&stop_readers, 1, __ATOMIC_RELEASE);
    for (int i = 0; i < 4; i++)
        assert(pthread_join(th[i], NULL) == 0);

    assert(sysVar[1] == 100);
    escalayer_modifier_reset(false);
    v_set_names(0, NULL);
    return 0;
}
"""
        compile_and_run_c(
            harness,
            [
                XSYS35_SRC / "escalayer_modifier.c",
                XSYS35_SRC / "cJSON.c",
                XSYS35_SRC / "variable.c",
                XSYS35_SRC / "utfsjis.c",
            ],
            extra_cflags=["-pthread"] + sdl_cflags,
            extra_ldflags=["-pthread"],
            asan=True,
        )

        esc = (XSYS35_SRC / "escalayer_modifier.c").read_text(encoding="utf-8")
        self.assertIn("static pthread_mutex_t esc_cache_lock = PTHREAD_MUTEX_INITIALIZER;", esc)
        refresh_block = esc[esc.index("refresh_escalayer_cache_locked"):esc.index("static const char *game_id_str")]
        self.assertLess(refresh_block.index("detect_sco_game("), refresh_block.index("esc_cache.checked = true;"))


class System3AuditRegressionTests(unittest.TestCase):
    def test_makofm_bounds_and_yakata1_amus_under_asan(self):
        """P0: Patched MakoFM rejects non-MAKO yakata1 AMUS.DAT and adversarial/truncated streams with ZERO ASan/UBSan violations."""
        patched_makofm_cpp = SYS3_BUILD / "fm" / "makofm.cpp"
        self.assertTrue(patched_makofm_cpp.exists(), f"Expected patched {patched_makofm_cpp}")

        yakata_amus = Path("/root/Rance10_CN/wave5_staged/yakata1/AMUS.DAT")
        yakata_path_c = str(yakata_amus) if yakata_amus.exists() else ""

        harness = f"""
#include <assert.h>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <vector>
#include "fm/makofm.h"

void sys_warning(const char* fmt, ...) {{ (void)fmt; }}

class TestMakoFM : public MakoFM {{
public:
    explicit TestMakoFM(std::vector<uint8_t> d) : MakoFM(std::move(d)) {{}}
protected:
    void SetReg(RegType, uint8_t, uint8_t) override {{}}
}};

static void run_safely(std::vector<uint8_t> data, bool expect_disabled) {{
    TestMakoFM fm(std::move(data));
    for (int i = 0; i < 64; i++) {{
        fm.MainLoop();
    }}
    if (expect_disabled)
        assert(fm.Looped());
}}

int main() {{
    // 1. Empty & tiny buffers
    run_safely({{}}, true);
    run_safely({{0x01}}, true);
    run_safely(std::vector<uint8_t>(10, 0x41), true);

    // 2. Pre-MAKO zero-header buffer (yakata1 AMUS.DAT style)
    std::vector<uint8_t> zero_hdr(128, 0);
    zero_hdr[4] = 0x28; // fake part offset
    zero_hdr[0x28] = 0x30;
    for (size_t i = 0x30; i < zero_hdr.size(); i++) zero_hdr[i] = 'A';
    run_safely(zero_hdr, true);

    // 3. Adversarial valid-looking header with OOB part_offset / block0 / jump_ptr / truncated opcodes / tempo=0 / OOB tone
    auto make_hdr = [](uint16_t tone_off, uint16_t ver, std::vector<uint8_t> body) {{
        std::vector<uint8_t> v(4 + 9 * 4 + 4 + body.size(), 0);
        v[0] = tone_off & 0xff; v[1] = tone_off >> 8;
        v[2] = ver & 0xff; v[3] = ver >> 8;
        // ch 0 part_offset = 40, block0_offset = 44
        v[4] = 40; v[5] = 0;
        v[40] = 44; v[41] = 0;
        v[42] = 0x00; v[43] = 0xff; // loop marker
        for (size_t i = 0; i < body.size(); i++) v[44 + i] = body[i];
        return v;
    }};

    // Unknown note opcode (0x30) like yakata1 heap over-read trigger
    run_safely(make_hdr(4, 0x200, {{0x30, 0x31, 0x32}}), true);
    // Truncated 8-byte / 10-byte / 3-byte / 2-byte / 1-byte commands at EOF
    run_safely(make_hdr(4, 0x200, {{0xe7, 1, 2, 3}}), true);
    run_safely(make_hdr(4, 0x200, {{0xe8, 1, 2, 3, 4}}), true);
    run_safely(make_hdr(4, 0x200, {{0xe4, 1}}), true);
    run_safely(make_hdr(4, 0x200, {{0xf3, 0x81}}), true);
    run_safely(make_hdr(4, 0x200, {{0x05}}), true); // note < 13 missing length
    run_safely(make_hdr(4, 0x200, {{0x10, 0x01}}), true); // note < 26 missing 2nd length byte
    // OOB FM tone index
    run_safely(make_hdr(4, 0x200, {{0xf5, 250, 0xec}}), true);
    // Tempo = 0 (must not divide by zero!)
    run_safely(make_hdr(4, 0x200, {{0xf4, 0x00, 0x80, 0xec}}), false);
    // Infinite 0xff jump loop must terminate via step cap
    run_safely(make_hdr(4, 0x200, {{0xff}}), true);

    // 4. Actual yakata1 AMUS.DAT chunks if present
    const char *yakata_path = "{yakata_path_c}";
    if (yakata_path[0]) {{
        FILE *fp = fopen(yakata_path, "rb");
        if (fp) {{
            fseek(fp, 0, SEEK_END);
            long sz = ftell(fp);
            fseek(fp, 0, SEEK_SET);
            if (sz > 0) {{
                std::vector<uint8_t> raw(static_cast<size_t>(sz));
                if (fread(raw.data(), 1, raw.size(), fp) == raw.size()) {{
                    // Feed raw slice directly into MakoFM as worst-case fuzz
                    std::vector<uint8_t> slice(raw.begin(), raw.begin() + std::min<size_t>(raw.size(), 4096));
                    run_safely(slice, false);
                }}
            }}
            fclose(fp);
        }}
    }}
    return 0;
}}
"""
        compile_and_run_cpp(
            harness,
            [patched_makofm_cpp],
            include_dirs=[SYS3_BUILD, SYS3_UPSTREAM_SRC, SYS3_UPSTREAM_SRC / "sys"],
            extra_cxxflags=_pkg_flags("sdl2", "--cflags"),
            asan=True,
        )

    def test_system3_encoding_truncated_utf8_and_invalid_sjis_under_asan(self):
        """P0: Patched encoding.cpp handles truncated UTF-8 & invalid SJIS trail bytes with zero OOB/UB."""
        patched_enc = SYS3_BUILD / "encoding.cpp"
        self.assertTrue(patched_enc.exists())

        harness = r"""
#include <assert.h>
#include <string>
#include <string_view>
#include "encoding.h"

void sys_warning(const char* fmt, ...) { (void)fmt; }

int main() {
    auto utf8 = Encoding::create("UTF-8");
    auto sjis = Encoding::create("Shift_JIS");

    // 1. Truncated 2, 3, 4-byte UTF-8 sequences
    for (std::string bad : { std::string("\xc2"), std::string("\xe4\xb8"), std::string("\xf0\x9f\x98"), std::string("\x80") }) {
        std::string_view sv = bad;
        int cp = utf8->next_codepoint(sv);
        assert(cp == 0xfffd || cp == '?');
        assert(utf8->mbslen(bad) >= 1);
        std::string s = sjis->fromUtf8(bad);
        assert(!s.empty());
    }

    // 2. Valid UTF-8 CJK + ASCII
    std::string ok = "\xe4\xb8\xad\xe6\x96\x87" "A";
    assert(utf8->mbslen(ok) == 3);
    std::string_view sv = ok;
    assert(utf8->next_codepoint(sv) == 0x4e2d);
    assert(utf8->next_codepoint(sv) == 0x6587);
    assert(utf8->next_codepoint(sv) == 'A');
    assert(sv.empty());

    // 3. Truncated SJIS & invalid trail byte (< 0x40)
    std::string bad_sjis_1("\x82", 1);
    std::string_view ssv1 = bad_sjis_1;
    assert(sjis->next_codepoint(ssv1) == '?');
    assert(sjis->toUtf8(bad_sjis_1) == "?");

    std::string bad_sjis_2("\x82\x01", 2);
    std::string_view ssv2 = bad_sjis_2;
    assert(sjis->next_codepoint(ssv2) == '?');
    assert(sjis->toUtf8(bad_sjis_2) == "?");
    assert(sjis->mbslen(bad_sjis_1) == 1);
    return 0;
}
"""
        compile_and_run_cpp(
            harness,
            [patched_enc],
            include_dirs=[SYS3_BUILD, SYS3_UPSTREAM_SRC, SYS3_UPSTREAM_SRC / "sys"],
            extra_cxxflags=_pkg_flags("sdl2", "--cflags"),
            asan=True,
        )

    def test_system3_patched_sources_and_ios_switch_cmake_guards(self):
        """P0: Verify all system3 configure-time patches (stack overflows, NULL glyphs, 480p resize, iOS crash isolation & foreground redraw)."""
        # Verify generated host build files contain every common patch
        nact_cpp = (SYS3_BUILD / "nact.cpp").read_text(encoding="utf-8")
        self.assertNotIn("char buf[200];", nact_cpp)
        self.assertIn("std::string buf;", nact_cpp)
        self.assertIn("system3_reset_input_state();", nact_cpp)
        self.assertIn("SDL_GameControllerClose(sdl_gamecontroller);", nact_cpp)

        sys2_cpp = (SYS3_BUILD / "nact_sys2.cpp").read_text(encoding="utf-8")
        self.assertNotIn("char string[33];", sys2_cpp)
        self.assertIn("std::string string;", sys2_cpp)

        sys3_cpp = (SYS3_BUILD / "nact_sys3.cpp").read_text(encoding="utf-8")
        self.assertNotIn("char string[22];", sys3_cpp)
        self.assertIn("std::string string;", sys3_cpp)
        self.assertIn("ags->window_height = ags->screen_height;", sys3_cpp)
        self.assertIn("ags->invalidate_screen(0, 0, ags->screen_width, ags->screen_height);", sys3_cpp)

        ags_text = (SYS3_BUILD / "ags_text.cpp").read_text(encoding="utf-8")
        self.assertIn("TTF_Font* font = hFont16;", ags_text)
        self.assertIn("SDL_Surface* fs = TTF_RenderGlyph_Solid(font, code, white);\n\tif (!fs)\n\t\treturn;", ags_text)
        self.assertIn("SDL_Surface* fs = TTF_RenderGlyph_Shaded(font, code, white, black);\n\tif (!fs)\n\t\treturn;", ags_text)

        ags_cpp = (SYS3_BUILD / "ags.cpp").read_text(encoding="utf-8")
        self.assertIn("ags_setAntialiasedStringMode(!config.no_antialias);", ags_cpp)
        self.assertIn("SDL_DestroyTexture(scanline_texture);", ags_cpp)
        self.assertIn("SDL_QueryTexture(sdlTexture, NULL, NULL, &tex_w, &tex_h)", ags_cpp)
        self.assertIn("if (SDL_LockTextureToSurface(sdlTexture, &dirty_rect, &sf) != 0 || !sf)", ags_cpp)

        mako_cpp = (SYS3_BUILD / "mako.cpp").read_text(encoding="utf-8")
        self.assertIn("if (g_device && !midi->is_available())", mako_cpp)
        self.assertIn("data.size() < 4 + 9 * 4", mako_cpp)

        nact_input = (SYS3_BUILD / "nact_input.cpp").read_text(encoding="utf-8")
        self.assertIn("void system3_reset_input_state()", nact_input)

        # Verify iOS foreground redraw in nact.ios.mm
        ios_mm = (SYS3_CMAKE_DIR / "nact.ios.mm").read_text(encoding="utf-8")
        self.assertIn("SDL_APP_DIDENTERFOREGROUND", ios_mm)
        self.assertIn("SDL_RENDER_DEVICE_RESET", ios_mm)
        self.assertIn("ags->invalidate_screen(0, 0, ags->screen_width, ags->screen_height);", ios_mm)

        # Verify iOS sdlmain.cpp patches in CMakeLists.txt match upstream verbatim!
        cm = (SYS3_CMAKE_DIR / "CMakeLists.txt").read_text(encoding="utf-8")
        self.assertIn("throw System3IosError{};", cm)
        self.assertIn("catch (const System3IosError&)", cm)
        self.assertIn('set_source_files_properties("${sdlmain_source}" PROPERTIES\n    COMPILE_DEFINITIONS "main=system3_entry', cm)

        upstream_sdlmain = (SYS3_UPSTREAM_SRC / "sdlmain.cpp").read_text(encoding="utf-8")
        # Extract the 3 iOS alicehub_patch_replace(ios_main ...) orig blocks and assert verbatim match
        ios_section = cm[cm.index("elseif(ALICEHUB_IOS)"):cm.index("elseif(NINTENDO_SWITCH)")]
        ios_origs = re.findall(r'alicehub_patch_replace\(ios_main\s+\[=\[(.*?)\]=\]\s+\[=\[', ios_section, re.S)
        self.assertEqual(len(ios_origs), 3)
        for orig in ios_origs:
            self.assertIn(orig, upstream_sdlmain, f"iOS sdlmain.cpp patch target not found in upstream:\n{orig}")

        # Verify Switch layers cleanly onto patched_ags_cpp & patched_nact_input
        self.assertIn("\tSDL_RenderPresent(g_renderer);", ags_cpp)
        for frag in (
            "case SDL_QUIT:\n\t\tshow_quit_dialog();",
            "void NACT::handle_event(SDL_Event e)\n{",
            "\tif(sdl_gamecontroller) {",
            "void NACT::pump_events()\n{\n",
        ):
            self.assertIn(frag, nact_input, f"Switch patch fragment missing from patched nact_input.cpp: {frag}")


if __name__ == "__main__":
    unittest.main()
