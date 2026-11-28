#!/usr/bin/env python3
"""Comprehensive native regression tests for multilingual, English, and partial-translation
fixes across xsystem4/libsys4, xsystem35, and system3."""

from pathlib import Path
import subprocess
import tempfile
import unittest

from test_evenicle2_regressions import LIBSYS4_CORE, ROOT, compile_and_run

REPO = ROOT.parent
XSYS35_SRC = REPO / "xsystem35" / "src"
SYS3_BUILD = Path("/root/Rance10_CN/host-build/system3")
SYS3_UPSTREAM = SYS3_BUILD / "_deps" / "system3_source-src" / "src"


def _pkg_flags(pkg: str, flag: str) -> list[str]:
    out = subprocess.check_output(["pkg-config", flag, pkg], text=True).strip()
    return out.split() if out else []


def _compile_and_run_c(code: str, sources: list[Path], include_dirs: list[Path], extra_flags: list[str] | None = None) -> str:
    with tempfile.TemporaryDirectory(prefix="multilang_c_") as td:
        src = Path(td) / "harness.c"
        exe = Path(td) / "harness"
        src.write_text(code, encoding="utf-8")
        cmd = [
            "cc", "-std=gnu11", "-O1", "-g", "-fsanitize=address,undefined",
            *[f"-I{d}" for d in include_dirs],
            str(src), *[str(s) for s in sources],
            "-o", str(exe),
            *(extra_flags or []),
        ]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise AssertionError(f"Compile failed:\nSTDOUT:\n{res.stdout}\nSTDERR:\n{res.stderr}")
        run = subprocess.run([str(exe)], capture_output=True, text=True)
        if run.returncode != 0:
            raise AssertionError(f"Run failed (rc={run.returncode}):\nSTDOUT:\n{run.stdout}\nSTDERR:\n{run.stderr}")
        return run.stdout.strip()


def _compile_and_run_cpp(code: str, sources: list[Path], include_dirs: list[Path], extra_flags: list[str] | None = None) -> str:
    with tempfile.TemporaryDirectory(prefix="multilang_cpp_") as td:
        src = Path(td) / "harness.cpp"
        exe = Path(td) / "harness"
        src.write_text(code, encoding="utf-8")
        cmd = [
            "c++", "-std=c++17", "-O1", "-g", "-fsanitize=address,undefined",
            *[f"-I{d}" for d in include_dirs],
            str(src), *[str(s) for s in sources],
            "-o", str(exe),
            *(extra_flags or []),
        ]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise AssertionError(f"C++ compile failed:\nSTDOUT:\n{res.stdout}\nSTDERR:\n{res.stderr}")
        run = subprocess.run([str(exe)], capture_output=True, text=True)
        if run.returncode != 0:
            raise AssertionError(f"C++ run failed (rc={run.returncode}):\nSTDOUT:\n{run.stdout}\nSTDERR:\n{run.stderr}")
        return run.stdout.strip()


class MultiLangNativeRegressionTests(unittest.TestCase):
    def test_libsys4_utf8_detection_accepts_2byte_european_cyrillic_and_rejects_gbk(self):
        harness = r"""
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "system4/utfsjis.h"

static bool check(const char *s) {
    return sys4_bytes_look_utf8((const uint8_t *)s, strlen(s));
}

int main(void) {
    // 2-byte UTF-8 accented Latin / European titles
    assert(check("Caf\xc3\xa9") == true);
    assert(check("\xc3\x89" "dition Fran\xc3\xa7" "aise") == true);
    assert(check("Gro\xc3\x9f" "e Stra\xc3\x9f" "e") == true);
    // 2-byte UTF-8 Russian Cyrillic
    assert(check("\xd0\xa0\xd0\xb0\xd0\xbd\xd1\x81 10") == true); // "Ранс 10"
    assert(check("\xd0\x9f\xd1\x80\xd0\xb8\xd0\xb2\xd0\xb5\xd1\x82 \xd0\xbc\xd0\xb8\xd1\x80") == true);
    // 3-byte UTF-8 CJK
    assert(check("\xe4\xb8\xad\xe6\x96\x87") == true);
    // Single GB2312 coincidence bytes that look like 2-byte UTF-8 must NOT falsely trigger
    assert(check("\xd2\xbb") == false); // GB2312 "一"
    assert(check("\xca\xae") == false); // GB2312 "十"
    // Pure ASCII returns false
    assert(check("Rance 10 English") == false);
    // Invalid UTF-8 (CP1252 / GBK / SJIS) returns false
    assert(check("Caf\xe9") == false);
    assert(check("\xc0\xbc\xcb\xb9") == false); // GBK "兰斯"
    assert(check("\x83\x89\x83\x93\x83\x58") == false); // SJIS "ランス"
    puts("OK");
    return 0;
}
"""
        out = compile_and_run(harness, [*LIBSYS4_CORE], ["-lz", "-lm"])
        self.assertEqual(out.strip(), "OK")

    def test_xsystem4_hacks_english_russian_cp1252_and_partial_gbk_detection(self):
        harness = r"""
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "system4.h"
#include "system4/ain.h"
#include "system4/string.h"
#include "system4/utfsjis.h"
#include "xsystem4.h"

struct config config;
int current_ain_version = 14;

void apply_game_specific_hacks(struct ain *ain);
void *message_layout_init_rance03(struct ain *ain) { (void)ain; return NULL; }
void *message_layout_init_rance10(struct ain *ain) { (void)ain; return NULL; }

static void add_msgs(struct ain *ain, const char *txt, int n) {
    int old = ain->nr_messages;
    ain->messages = realloc(ain->messages, sizeof(struct string *) * (old + n));
    for (int i = 0; i < n; i++) {
        ain->messages[old + i] = make_string(txt, strlen(txt));
    }
    ain->nr_messages = old + n;
}

int main(void) {
    // 1. MangaGamer Rance VI (0xC9 -> é inside ASCII: "Rosés") MUST NOT flip to GBK!
    struct ain *mg_r6 = ain_new(4, 0);
    ain_add_library(mg_r6, "Rance6DLL");
    ain_add_function(mg_r6, "GetTextWidth");
    add_msgs(mg_r6, "Ros\xc9s caf\xc9s and heroines in Zeth!", 120);
    memset(&config, 0, sizeof(config));
    config.game_name = strdup("Rance6");
    sys4_set_text_gbk(false);
    apply_game_specific_hacks(mg_r6);
    assert(sys4_text_is_gbk == false);
    free(config.game_name);
    ain_free(mg_r6);

    // 2. Pure-ASCII English Rance 10 MUST NOT flip to GBK even when JP symbols are renamed!
    struct ain *en_r10 = ain_new(14, 0);
    ain_add_library(en_r10, "Rance10");
    add_msgs(en_r10, "Welcome to Rance X English Edition!", 100);
    memset(&config, 0, sizeof(config));
    config.game_name = strdup("Rance 10");
    sys4_set_text_gbk(false);
    apply_game_specific_hacks(en_r10);
    assert(sys4_text_is_gbk == false);
    free(config.game_name);
    ain_free(en_r10);

    // 3. UTF-8 Russian AIN MUST NOT flip to GBK via 0xD0 0xA0..0xBF coincidences!
    struct ain *ru_ain = ain_new(6, 0);
    add_msgs(ru_ain, "\xd0\xa0\xd0\xb0\xd0\xbd\xd1\x81: \xd0\x9f\xd1\x80\xd0\xb8\xd0\xb2\xd0\xb5\xd1\x82 \xd0\xbc\xd0\xb8\xd1\x80, \xd0\xb3\xd0\xb5\xd1\x80\xd0\xbe\xd0\xb9!", 100);
    memset(&config, 0, sizeof(config));
    config.game_name = strdup("Rance RU");
    sys4_set_text_gbk(false);
    apply_game_specific_hacks(ru_ain);
    assert(sys4_text_is_gbk == false);
    free(config.game_name);
    ain_free(ru_ain);

    // 4. Partially translated GBK + SJIS (60% GBK, 40% untranslated SJIS):
    //    MUST detect GBK AND set game_has_mixed_gbk_sjis_text = true!
    struct ain *partial = ain_new(6, 0);
    const char *gbk_line = "\xbf\xaa\xca\xbc\xd3\xce\xcf\xb7\xa1\xa2\xb6\xc1\xc8\xa1\xbd\xf8\xb6\xc8\xa1\xa3";
    const char *sjis_line = "\x82\xa8\x82\xcd\x82\xe6\x82\xa4\x82\xb2\x82\xb4\x82\xa2\x82\xdc\x82\xb7\x81\x42";
    add_msgs(partial, gbk_line, 12);
    add_msgs(partial, sjis_line, 8);
    memset(&config, 0, sizeof(config));
    config.game_name = strdup("PartialGame");
    sys4_set_text_gbk(false);
    apply_game_specific_hacks(partial);
    assert(sys4_text_is_gbk == true);
    assert(game_has_mixed_gbk_sjis_text == true);
    free(config.game_name);
    ain_free(partial);

    puts("OK");
    return 0;
}
"""
        out = compile_and_run(
            harness,
            [ROOT / "src/hacks.c", ROOT / "src/escalayer_reboot_cn_ui.c", *LIBSYS4_CORE],
            ["-lz", "-lm"],
        )
        self.assertEqual(out.strip(), "OK")

    def test_xsystem4_savedata_gbk_bias_and_font_halfwidth(self):
        savedata_src = (ROOT / "src/savedata.c").read_text(encoding="utf-8")
        bstart = savedata_src.index("int save_text_gbk_bias(")
        bend = savedata_src.index("\nstatic ", bstart + 1)
        mstart = savedata_src.index("struct cjk_kanji_pair {")
        helpers_and_bias = savedata_src[mstart:bend]

        font_src = (ROOT / "src/font_freetype.c").read_text(encoding="utf-8")
        fstart = font_src.index("static bool is_half_width(")
        fend = font_src.index("\n}", fstart) + 2
        halfwidth_fn = font_src[fstart:fend]

        harness = f"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "system4.h"
#include "system4/utfsjis.h"

{helpers_and_bias}

{halfwidth_fn}

int main(void) {{
    // MG Rance VI 'Rosés cafés' (0xC9 -> é) must NOT have positive GBK bias!
    assert(save_text_gbk_bias("Ros\\xc9s caf\\xc9s") <= 0);
    // Half-width katakana 'ｱｲ' (0xB1 0xB2) must NOT have positive GBK bias!
    assert(save_text_gbk_bias("\\xb1\\xb2") <= 0);
    // Real GBK Chinese save comment ('存档一：开始') MUST have positive GBK bias!
    assert(save_text_gbk_bias("\\xb4\\xe6\\xb5\\xb5\\xd2\\xbb\\xa3\\xba\\xbf\\xaa\\xca\\xbc") > 0);
    // Real Shift-JIS save comment ('セーブ１') MUST have negative GBK bias!
    assert(save_text_gbk_bias("\\x83\\x5a\\x81\\x5b\\x83\\x75\\x82\\x50") < 0);

    // is_half_width: Latin Extended-A/B, Greek, Cyrillic are half-width; ×/÷ & CJK are full-width
    assert(is_half_width(0x00E9) == true);  // é
    assert(is_half_width(0x010D) == true);  // č (Latin Ext-A)
    assert(is_half_width(0x0152) == true);  // Œ
    assert(is_half_width(0x03B1) == true);  // α (Greek)
    assert(is_half_width(0x0410) == true);  // А (Cyrillic)
    assert(is_half_width(0x044F) == true);  // я (Cyrillic)
    assert(is_half_width(0x00D7) == false); // ×
    assert(is_half_width(0x00F7) == false); // ÷
    assert(is_half_width(0x4E2D) == false); // 中
    puts("OK");
    return 0;
}}
"""
        out = compile_and_run(harness, [*LIBSYS4_CORE], ["-lz", "-lm"])
        self.assertEqual(out.strip(), "OK")

    def test_xsystem35_gbk_detection_utf8_display_width_and_hankaku(self):
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "utfsjis.h"
#include "hankaku.h"

void sys_message(const char *format, ...) { (void)format; }
void sys_error(const char *format, ...) { (void)format; abort(); }

int main(void) {
    // 1. UTF-8 Russian and UTF-8 Chinese must NOT trigger cjk_looks_like_gbk()!
    assert(cjk_looks_like_gbk((const uint8_t *)"\xd0\xa0\xd0\xb0\xd0\xbd\xd1\x81 10") == false);
    assert(cjk_looks_like_gbk((const uint8_t *)"\xe5\xbc\x80\xe5\xa7\x8b\xe6\xb8\xb8\xe6\x88\x8f") == false);
    // 2. Single CP1252 ellipsis 'Wait…really?' (0x85) or short half-width kana 'ｱｲ' must NOT trigger GBK!
    assert(cjk_looks_like_gbk((const uint8_t *)"Wait\x85really?") == false);
    assert(cjk_looks_like_gbk((const uint8_t *)"\xb1\xb2") == false);
    // 3. Real GBK sentence MUST trigger cjk_looks_like_gbk()!
    assert(cjk_looks_like_gbk((const uint8_t *)"\xbf\xaa\xca\xbc\xd3\xce\xcf\xb7\xa1\xa3") == true);

    // 4. UTF-8 mode display width & hankaku/zenkaku detection
    assert(cjk_display_width("Caf\xc3\xa9", UTF8) == 4);
    assert(cjk_has_hankaku((const uint8_t *)"Caf\xc3\xa9", UTF8) == true);
    assert(cjk_has_zenkaku((const uint8_t *)"Caf\xc3\xa9", UTF8) == false);
    // "Ранс" (4 Cyrillic chars) = 4 columns
    assert(cjk_display_width("\xd0\xa0\xd0\xb0\xd0\xbd\xd1\x81", UTF8) == 4);
    assert(cjk_has_hankaku((const uint8_t *)"\xd0\xa0\xd0\xb0\xd0\xbd\xd1\x81", UTF8) == true);
    assert(cjk_has_zenkaku((const uint8_t *)"\xd0\xa0\xd0\xb0\xd0\xbd\xd1\x81", UTF8) == false);
    // Half-width katakana in UTF-8 "ｱｲ" (EF BD B1 EF BD B2) = 2 columns
    assert(cjk_display_width("\xef\xbd\xb1\xef\xbd\xb2", UTF8) == 2);
    assert(cjk_has_hankaku((const uint8_t *)"\xef\xbd\xb1\xef\xbd\xb2", UTF8) == true);
    assert(cjk_has_zenkaku((const uint8_t *)"\xef\xbd\xb1\xef\xbd\xb2", UTF8) == false);
    // Full-width CJK "中文" = 4 columns, zenkaku=true, hankaku=false
    assert(cjk_display_width("\xe4\xb8\xad\xe6\x96\x87", UTF8) == 4);
    assert(cjk_has_zenkaku((const uint8_t *)"\xe4\xb8\xad\xe6\x96\x87", UTF8) == true);
    assert(cjk_has_hankaku((const uint8_t *)"\xe4\xb8\xad\xe6\x96\x87", UTF8) == false);

    // 5. GBK zen2han conversion (GBK full-width 'Ａ' = A3 C1, '１' = A3 B1, '　' = A1 A1)
    uint8_t *han = zen2han((const uint8_t *)"\xa3\xc1\xa3\xb1\xa1\xa1\xbf\xaa", GBK);
    assert(strcmp((const char *)han, "A1 \xbf\xaa") == 0);
    free(han);

    puts("OK");
    return 0;
}
"""
        out = _compile_and_run_c(
            harness,
            [XSYS35_SRC / "utfsjis.c", XSYS35_SRC / "hankaku.c"],
            [XSYS35_SRC, Path("/root/Rance10_CN/host-build/xsystem35")],
        )
        self.assertEqual(out, "OK")

    def test_system3_safe_mblen_lf_ag00_case_insensitive_bom_ini_and_tilde(self):
        if not (SYS3_BUILD.is_dir() and SYS3_UPSTREAM.is_dir()):
            self.skipTest("host-build/system3 not available")
        harness = r"""
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <string>
#include <vector>
#include "config.h"
#include "encoding.h"
#include "fileio.h"
#include "scenario.h"

[[noreturn]] void sys_error(const char *format, ...) {
    fprintf(stderr, "sys_error: %s\n", format);
    abort();
}
void sys_warning(const char *format, ...) { (void)format; }

// Minimal Dri mock that serves one scenario page from memory
#include "dri.h"
void Dri::open(const char *file_name) { (void)file_name; }
std::vector<uint8_t> g_test_page;
std::vector<uint8_t> Dri::load(int page) {
    return (page == 1) ? g_test_page : std::vector<uint8_t>();
}

int main() {
    auto utf8 = Encoding::create("UTF-8");
    auto sjis = Encoding::create("Shift_JIS");

    // 1. Partial UTF-8 scenario with untranslated Shift-JIS bytes before closing '\'' terminator:
    //    safe_mblen MUST NOT eat the closing '\''!
    g_test_page = { 2, 0, '\'', 0x82, 0xbb, 0x82, 0xa4, 0xc4, '\'', 'R' };
    Scenario sco;
    sco.open("ADISK.DAT");
    sco.page_jump(0, 2);
    assert(sco.getd_checked() == '\'');
    std::string got = sco.get_syseng_string_dynamic(utf8.get(), '\'');
    assert(got.size() == 5);
    assert(sco.getd_checked() == 'R');

    // 2. Shift-JIS scenario with stray UTF-8 curly apostrophe (E2 80 99) before closing '\'':
    //    safe_mblen MUST NOT eat closing '\'' (0x27 < 0x40)!
    g_test_page = { 2, 0, '\'', 'H', 'i', 0xe2, 0x80, 0x99, '\'', 'A' };
    Scenario sco2;
    sco2.open("ADISK.DAT");
    sco2.page_jump(0, 2);
    assert(sco2.getd_checked() == '\'');
    std::string got2 = sco2.get_syseng_string_dynamic(sjis.get(), '\'');
    assert(got2.size() == 5);
    assert(sco2.getd_checked() == 'A');

    // 3. SjisEncoding::toUtf8 never embeds NUL on unmapped pair and preserves ASCII after lone lead byte
    std::string unmapped("\x85\x40" "OK", 4);
    std::string u1 = sjis->toUtf8(unmapped);
    assert(u1.find('\0') == std::string::npos);
    assert(u1.size() >= 3 && u1.substr(u1.size() - 2) == "OK");
    std::string lone_before_quote("\x82'hi'", 5);
    assert(sjis->toUtf8(lone_before_quote) == "?'hi'");

    // 4. Case-insensitive SYSTEM3.INI with UTF-8 BOM + LF-only AG00.DAT
    char tmpdir[] = "/tmp/sys3_multilang_XXXXXX";
    assert(mkdtemp(tmpdir) != NULL);
    assert(chdir(tmpdir) == 0);

    FILE *ini = fopen("SYSTEM3.INI", "wb");
    assert(ini != NULL);
    const char *ini_data = "\xef\xbb\xbf[config]\ngame = rance41_eng\nencoding = utf-8\nfontfile = Missing.ttf\n";
    fwrite(ini_data, 1, strlen(ini_data), ini);
    fclose(ini);

    char arg0[] = "system3";
    char *argv[] = { arg0 };
    Config cfg(1, argv);
    assert(cfg.game_id == "rance41_eng");
    assert(cfg.encoding == "utf-8");
    assert(cfg.font_file == "Missing.ttf");

    FILE *ag = fopen("AG00.DAT", "wb");
    assert(ag != NULL);
    const char *ag_data = "0,2,2,0\nLook\nTalk\nDoor\nKey\n";
    fwrite(ag_data, 1, strlen(ag_data), ag);
    fclose(ag);

    auto fio = FILEIO::open("AG00.DAT", FILEIO_READ_BINARY);
    assert(fio != nullptr);
    assert(fio->gets() == "0,2,2,0");
    assert(fio->gets() == "Look");
    assert(fio->gets() == "Talk");
    assert(fio->gets() == "Door");
    assert(fio->gets() == "Key");
    fio.reset();

    unlink("SYSTEM3.INI");
    unlink("AG00.DAT");
    assert(chdir("/tmp") == 0);
    rmdir(tmpdir);

    puts("OK");
    return 0;
}
"""
        out = _compile_and_run_cpp(
            harness,
            [SYS3_BUILD / "config.cpp", SYS3_BUILD / "fileio.cpp", SYS3_BUILD / "encoding.cpp", SYS3_BUILD / "scenario.cpp"],
            [SYS3_BUILD, SYS3_UPSTREAM, SYS3_UPSTREAM / "sys"],
            _pkg_flags("sdl2", "--cflags"),
        )
        self.assertEqual(out, "OK")


if __name__ == "__main__":
    unittest.main()
