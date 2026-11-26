import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from test_escalayer_reboot_regressions import ROOT, extract_function


class SengokuRanceChineseUiRegressionTest(unittest.TestCase):
    def compile_and_run(self, harness):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "test.c"
            exe = Path(tmp) / "test"
            source.write_text(harness)
            subprocess.run(
                ["cc", "-std=c11", "-Wall", "-Wextra", "-Werror",
                 "-I", str(ROOT / "subprojects/libsys4/include"), str(source), "-o", str(exe)],
                check=True,
            )
            subprocess.run([str(exe)], check=True)

    def test_native_codename_resolves_nested_directory_casing(self):
        harness = r"""
#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include "system4/file.h"
#include "xsystem4.h"

struct config config;

static void check_path(const char *code_name, const char *expected, char *(*resolve)(const char *))
{
    char *path = resolve(code_name);
    assert(path && file_exists(path));
    assert(strcmp(path, expected) == 0);
    free(path);
}

int main(int argc, char **argv)
{
    assert(argc == 4);
    config.game_dir = argv[1];
    check_path("dAtA\\pAtChEs\\PATCH.AIN", argv[2], gamedir_path);
    check_path("dAtA\\pAtChEs\\PATCH.AIN", argv[2], gamedir_path_icase);
    check_path("LAUNCHERS\\eNtRiEs\\PATCH.AIN", argv[3], gamedir_path);
    return 0;
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            data = root / "Data"
            data.mkdir()
            if (root / "DATA").exists():
                self.skipTest("requires a case-sensitive filesystem")
            script = data / "Patches" / "Patch.ain"
            script.parent.mkdir()
            script.touch()
            other_script = root / "Launchers" / "Entries" / "Patch.ain"
            other_script.parent.mkdir(parents=True)
            other_script.touch()
            exact_script = root / "LAUNCHERS" / "Entries" / "Patch.ain"
            exact_script.parent.mkdir(parents=True)
            exact_script.touch()

            libsys4 = ROOT / "subprojects/libsys4"
            source = root / "test.c"
            source.write_text(harness)
            exe = root / "test"
            linker = ["-Wl,-dead_strip", "-framework", "CoreFoundation"] if sys.platform == "darwin" else ["-Wl,--gc-sections"]
            subprocess.run(
                ["cc", "-std=gnu11", "-ffunction-sections", "-fdata-sections",
                 "-I", str(ROOT / "include"), "-I", str(libsys4 / "include"),
                 str(source), str(ROOT / "src/util.c"),
                 *(str(libsys4 / "src" / name) for name in ("file.c", "system.c", "utfsjis.c")),
                 *linker, "-o", str(exe)],
                check=True,
            )
            subprocess.run([str(exe), str(root), str(script), str(exact_script)], check=True)

    def test_skip_graphic_only_relabels_the_original_chinese_session_button(self):
        fix = extract_function("src/asset_manager.c", "localize_sengoku_skip_cg")
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <string.h>
#include "system4/archive.h"
#include "system4/cg.h"

static bool sys4_text_is_gbk;
static unsigned long crc32(unsigned long seed, const uint8_t *data, size_t size)
{
    assert(seed == 0 && size == 3451);
    return data[0] ? 0xdd729ed3 : 0;
}
""" + fix + r"""

int main(void)
{
    uint8_t data[] = {1};
    struct archive_data source = {.data = data, .size = 3451};
    uint8_t pixels[20][108][4];
    uint8_t original[sizeof(pixels)];
    memset(pixels, 127, sizeof(pixels));
    memcpy(original, pixels, sizeof(pixels));
    struct cg cg = {.type = ALCG_QNT, .metrics = {.w = 108, .h = 20}, .pixels = pixels};
    localize_sengoku_skip_cg(10005, &source, &cg);
    assert(!memcmp(pixels, original, sizeof(pixels)));
    sys4_text_is_gbk = true;
    localize_sengoku_skip_cg(10004, &source, &cg);
    assert(!memcmp(pixels, original, sizeof(pixels)));
    data[0] = 0;
    localize_sengoku_skip_cg(10005, &source, &cg);
    assert(!memcmp(pixels, original, sizeof(pixels)));

    data[0] = 1;
    localize_sengoku_skip_cg(10005, &source, &cg);
    for (int frame = 0; frame < 3; frame++) {
        int left = 36 * frame + 2 + (frame == 2);
        int top = 3 + (frame == 2);
        assert(pixels[top][left][0] == 226);
        assert(pixels[top][left + 3][0] == (frame == 1 ? 213 : 193));
        assert(pixels[top][left + 3][3] == 127);
        assert(pixels[top - 1][left][0] == 127);
        assert(pixels[top][left - 1][0] == 127);
    }
    return 0;
}
"""
        self.compile_and_run(harness)

    def test_alk_rejects_truncated_logo_entries(self):
        harness = r"""
#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include "system4/alk.h"

FILE *file_open_utf8(const char *path, const char *mode) { return fopen(path, mode); }
void sys_warning(const char *fmt, ...) { (void)fmt; }
void *_xmalloc(size_t size, const char *func) { (void)func; return malloc(size); }
void *_xcalloc(size_t n, size_t size, const char *func) { (void)func; return calloc(n, size); }

static void check_archive(const char *path, int flags, bool has_logo)
{
    int error = ARCHIVE_SUCCESS;
    struct alk_archive *alk = alk_open(path, flags, &error);
    assert(alk && error == ARCHIVE_SUCCESS);
    assert(alk->ar.mmapped == (flags == ARCHIVE_MMAP));
    struct archive_data *logo = archive_get(&alk->ar, 45);
    if (has_logo) {
        assert(logo && logo->size == 4 && !memcmp(logo->data, "logo", 4));
        archive_free_data(logo);
    } else {
        assert(!logo);
    }
    archive_free(&alk->ar);
}

int main(int argc, char **argv)
{
    assert(argc == 4);
    for (int flags = 0; flags <= ARCHIVE_MMAP; flags++) {
        check_archive(argv[1], flags, true);
        check_archive(argv[2], flags, false);
        check_archive(argv[3], flags, false);
    }
    return 0;
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "test.c"
            exe = root / "test"
            source.write_text(harness)
            header = struct.pack("<4sI", b"ALK0", 46) + bytes(45 * 8)
            frame_offset = len(header) + 8
            fixtures = []
            for name, offset, data in (
                ("complete", frame_offset, b"logo"),
                ("truncated", frame_offset, b"lo"),
                ("missing", frame_offset + 4, b""),
            ):
                fixture = root / f"{name}.alk"
                fixture.write_bytes(header + struct.pack("<II", offset, 4) + data)
                fixtures.append(str(fixture))
            libsys4 = ROOT / "subprojects/libsys4"
            subprocess.run(
                ["cc", "-std=gnu11", "-I", str(libsys4 / "include"),
                 str(source), str(libsys4 / "src/alk.c"), "-o", str(exe)],
                check=True,
            )
            subprocess.run([str(exe), *fixtures], check=True)

    def test_startup_logo_renders_the_localization_credit_if_available(self):
        run = extract_function("src/hll/AliceLogo2.c", "AliceLogo2_Run")
        harness = r"""
#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include "system4/alk.h"
#include "system4/cg.h"

#define MMAP_IF_64BIT 0
#define UNIMPLEMENTED(...) ((void)0)

static bool game_sengokurance, sys4_text_is_gbk, has_archive, has_logo;
static int opened, closed, drawn, presented;
static void close_archive(struct archive *ar);
static struct archive_ops archive_ops = {.free = close_archive};
static struct alk_archive alk = {.ar = {.ops = &archive_ops}};
static struct cg credit;
typedef struct { int w, h; } Texture;
static Texture screen = {800, 600};

static char *gamedir_path_icase(const char *name)
{
    assert(!strcmp(name, "AliceLogo2.alk"));
    if (!has_archive)
        return NULL;
    char *path = malloc(strlen(name) + 1);
    return strcpy(path, name);
}

struct alk_archive *alk_open(const char *path, int flags, int *error)
{
    assert(!strcmp(path, "AliceLogo2.alk") && flags == 0 && *error == ARCHIVE_SUCCESS);
    opened++;
    return &alk;
}
struct cg *cg_load(struct archive *ar, int id)
{
    assert(ar == &alk.ar && id == 45);
    return has_logo ? &credit : NULL;
}
static void close_archive(struct archive *ar) { assert(ar == &alk.ar); closed++; }
void cg_free(struct cg *cg) { assert(cg == &credit); }
static Texture *gfx_main_surface(void) { return &screen; }
static void gfx_init_texture_with_cg(Texture *tex, struct cg *cg)
{
    assert(cg == &credit);
    *tex = (Texture){400, 300};
}
static void gfx_copy_stretch(Texture *dst, int x, int y, int w, int h,
                            Texture *src, int sx, int sy, int sw, int sh)
{
    assert(dst == &screen && !x && !y && w == 800 && h == 600);
    assert(!sx && !sy && sw == src->w && sh == src->h);
    drawn++;
}
static void gfx_delete_texture(Texture *tex) { assert(tex->w == 400); }
static void gfx_swap(void) { assert(drawn == 1); presented++; }
static void gfx_android_delay(unsigned ms) { assert(presented == 1 && ms > 0); }
""" + run + r"""

int main(void)
{
    has_archive = true;
    AliceLogo2_Run(0, 0);
    game_sengokurance = true;
    AliceLogo2_Run(0, 0);
    sys4_text_is_gbk = true;
    AliceLogo2_Run(1, 0);
    AliceLogo2_Run(0, 1);
    has_archive = false;
    AliceLogo2_Run(0, 0);
    assert(!opened && !drawn && !presented);

    has_archive = true;
    AliceLogo2_Run(0, 0);
    assert(opened == 1 && closed == 1 && !drawn && !presented);

    has_logo = true;
    AliceLogo2_Run(0, 0);
    assert(opened == 2 && closed == 2 && drawn == 1 && presented == 1);
    return 0;
}
"""
        self.compile_and_run(harness)
