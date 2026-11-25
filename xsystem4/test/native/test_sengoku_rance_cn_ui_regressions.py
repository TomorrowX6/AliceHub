import subprocess
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
