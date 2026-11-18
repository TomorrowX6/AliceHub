#!/usr/bin/env python3
"""Dependency-free native regression tests for Beat Angel Escalayer Reboot fixes.

Covers:
1. cjk_gbk_zenkaku_to_sjis_buf() (libsys4/src/utfsjis.c) folds GBK fullwidth
   digits/punctuation emitted by %D/%F formatting back to Shift-JIS while leaving
   SJIS and GBK double-byte characters intact. The CN fan patch formats SJIS EX
   keys / CG names such as 'メッセージウィンドウ.%02D番.テキスト.サイズ' and
   'カレンダー／月／%02D' while sys4_text_is_gbk is set, producing mixed strings.
2. ex_cjk_equals_n() (libsys4/src/ex.c) matches those mixed keys against the
   SJIS keys stored in EscalayerReboot.ex, and every EX lookup path uses it.
3. AFA basename lookup normalizes the same mixed names before falling back.
4. PartsFunc IDs are shifted by 3 for Escalayer Reboot (CDrawMovie@CreateMovieParts
   passes 162 for InitPartsMovie with 8 ints).
5. SystemService.EscalayerReboot011116 returns a key accepted by main().
6. Game detection / GBK detection / window title wiring.

Run: python3 xsystem4/test/native/test_escalayer_reboot_regressions.py
"""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(rel_path: str) -> str:
    return (ROOT / rel_path).read_text(encoding="utf-8", errors="surrogateescape")


def extract_function(rel_path: str, name: str) -> str:
    source = read(rel_path)
    # Skip forward declarations and call sites: take the first match whose
    # parameter list is followed by '{' before any ';'.
    pattern = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(name) + r"\(")
    for m in pattern.finditer(source):
        semi = source.find(";", m.end())
        brace = source.find("{", m.end())
        if brace != -1 and (semi == -1 or brace < semi):
            idx = m.start()
            break
    else:
        raise ValueError(f"definition of {name} not found in {rel_path}")
    start = source.rfind("\n", 0, idx) + 1
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def compile_and_run(c_source: str) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "test.c"
        exe = Path(tmp) / "test_bin"
        src.write_text(c_source, encoding="utf-8", errors="surrogateescape")
        subprocess.run(
            ["cc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2", str(src), "-o", str(exe)],
            check=True,
        )
        subprocess.run([str(exe)], check=True)


PRELUDE = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

static bool sys4_text_is_gbk = true;
static void *xmalloc(size_t n) { void *p = malloc(n); assert(p); return p; }
"""


class EscalayerRebootRegressionTests(unittest.TestCase):
    def test_gbk_zenkaku_fold_and_ex_key_match(self) -> None:
        fold = extract_function("subprojects/libsys4/src/utfsjis.c", "cjk_gbk_zenkaku_to_sjis_buf")
        has_non_ascii = extract_function("subprojects/libsys4/src/ex.c", "ex_bytes_has_non_ascii")
        equals = extract_function("subprojects/libsys4/src/ex.c", "ex_cjk_equals_n")
        c_code = PRELUDE + fold + r"""
/* Strict decode stub: report failure so only the zenkaku fold is exercised. */
static char *cjk2utf_strict_buf(const char *src, size_t len, bool gbk, char *buf, size_t cap, bool *ok)
{
    (void)gbk;
    char *out = (buf && len < cap) ? buf : xmalloc(len + 1);
    memcpy(out, src, len);
    out[len] = 0;
    *ok = false;
    return out;
}
""" + has_non_ascii + "\n" + equals + r"""
int main(void)
{
    /* GBK '０１' + SJIS '番' (as produced by %02D in GBK mode) -> SJIS '０１番' */
    const char mixed[] = "\xa3\xb0\xa3\xb1\x94\xd4";
    const char sjis[] = "\x82\x4f\x82\x50\x94\xd4";
    bool changed = false;
    char buf[64];
    char *out = cjk_gbk_zenkaku_to_sjis_buf(mixed, strlen(mixed), buf, sizeof(buf), &changed);
    assert(changed);
    assert(!strcmp(out, sjis));

    /* 'カレンダー／月／' (SJIS) + GBK '０７' -> SJIS 'カレンダー／月／０７' */
    const char cg_mixed[] = "\x83\x4a\x83\x8c\x83\x93\x83\x5f\x81\x5b\x81\x5e\x8c\x8e\x81\x5e\xa3\xb0\xa3\xb7";
    const char cg_sjis[]  = "\x83\x4a\x83\x8c\x83\x93\x83\x5f\x81\x5b\x81\x5e\x8c\x8e\x81\x5e\x82\x4f\x82\x56";
    out = cjk_gbk_zenkaku_to_sjis_buf(cg_mixed, strlen(cg_mixed), buf, sizeof(buf), &changed);
    assert(changed);
    assert(!strcmp(out, cg_sjis));

    /* Fullwidth minus / period / ideographic space */
    out = cjk_gbk_zenkaku_to_sjis_buf("\xa3\xad\xa3\xae\xa1\xa1", 6, buf, sizeof(buf), &changed);
    assert(changed);
    assert(!memcmp(out, "\x81\x7c\x81\x44\x81\x40", 7));

    /* Pure SJIS / ASCII / ordinary GBK text is untouched. */
    out = cjk_gbk_zenkaku_to_sjis_buf(sjis, strlen(sjis), buf, sizeof(buf), &changed);
    assert(!changed && !strcmp(out, sjis));
    out = cjk_gbk_zenkaku_to_sjis_buf("abc.01", 6, buf, sizeof(buf), &changed);
    assert(!changed && !strcmp(out, "abc.01"));
    const char gbk_text[] = "\xbd\xf8\xd0\xd0\xbf\xec\xcb\xd9"; /* 进行快速 */
    out = cjk_gbk_zenkaku_to_sjis_buf(gbk_text, strlen(gbk_text), buf, sizeof(buf), &changed);
    assert(!changed && !strcmp(out, gbk_text));
    /* SJIS trail byte 0xa3 inside a double-byte char must not be folded. */
    const char sjis_trail[] = "\x83\xa3\xb0";
    out = cjk_gbk_zenkaku_to_sjis_buf(sjis_trail, 3, buf, sizeof(buf), &changed);
    assert(!changed && !memcmp(out, sjis_trail, 4));

    /* Heap path when buffer is too small. */
    char tiny[2];
    out = cjk_gbk_zenkaku_to_sjis_buf(mixed, strlen(mixed), tiny, sizeof(tiny), &changed);
    assert(out != tiny && changed && !strcmp(out, sjis));
    free(out);

    /* EX key comparison */
    assert(ex_cjk_equals_n(sjis, strlen(sjis), mixed, strlen(mixed)));
    assert(!ex_cjk_equals_n(sjis, strlen(sjis), "\xa3\xb0\xa3\xb2\x94\xd4", 6));
    assert(!ex_cjk_equals_n("abc", 3, "abc", 3)); /* ASCII handled by strcmp callers */
    sys4_text_is_gbk = false;
    assert(!ex_cjk_equals_n(sjis, strlen(sjis), mixed, strlen(mixed)));
    return 0;
}
"""
        compile_and_run(c_code)

    def test_ex_lookup_paths_use_cjk_fallback(self) -> None:
        ex_c = read("subprojects/libsys4/src/ex.c")
        for fn in ("ex_tree_get_path", "_ex_get", "ex_get_block", "ex_tree_get_child",
                   "ex_row_at_string_key", "ex_col_from_name"):
            body = extract_function("subprojects/libsys4/src/ex.c", fn)
            self.assertIn("ex_cjk_equals_n(", body, fn)
            self.assertIn("sys4_text_is_gbk", body, fn)
        self.assertIn('#include "system4/utfsjis.h"', ex_c)
        header = read("subprojects/libsys4/include/system4/utfsjis.h")
        self.assertIn("cjk_gbk_zenkaku_to_sjis_buf(", header)

    def test_afa_lookup_normalizes_gbk_zenkaku(self) -> None:
        body = extract_function("src/asset_manager.c", "afa_find_best_by_basename")
        self.assertIn("cjk_gbk_zenkaku_to_sjis_buf(", body)
        norm = body.index("cjk_gbk_zenkaku_to_sjis_buf(")
        self.assertLess(norm, body.index("afa_ensure_canon_index(manager)"))
        self.assertIn("archive_basename_buf(lookup_name", body)
        self.assertIn("save_transcode_cstr_strict(lookup_name", body)

    def test_parts_func_id_shift(self) -> None:
        body = extract_function("src/hll/PartsEngine.c", "PartsEngine_PartsFunc")
        self.assertRegex(body, r"game_escalayer_reboot\)\s*&&\s*func_id\s*>=\s*3")
        # 162 (+3) must land on InitPartsMovie with 8 ints.
        case = re.search(r"case 165:.*?REQUIRE_INTS\((\d+)\).*?PE_init_parts_movie", body, re.S)
        self.assertIsNotNone(case)
        self.assertEqual(case.group(1), "8")

    def test_system_service_key(self) -> None:
        src = read("src/hll/SystemService.c")
        m = re.search(r'SystemService_EscalayerReboot011116\(struct string \*\*text\)\s*\{\s*'
                      r'set_out_cstr\(text, "([^"]*)"\);', src)
        self.assertIsNotNone(m)
        key = m.group(1)
        # main() in EscalayerReboot.ain: Length()==14 && s[0]+s[2]+s[4]+s[9..11] == "kkrsre"
        self.assertEqual(len(key), 14)
        self.assertEqual(key[0] + key[2] + key[4] + key[9:12], "kkrsre")
        self.assertIn("HLL_EXPORT(EscalayerReboot011116, SystemService_EscalayerReboot011116)", src)

    def test_game_detection_and_title(self) -> None:
        hacks = read("src/hacks.c")
        self.assertIn('"C_BATTLE_PLAYER_RESULT_COMMAND@SetEscalayerPunch"', hacks)
        self.assertIn("game_escalayer_reboot = false;", hacks)
        self.assertRegex(hacks, r"game_escalayer_reboot && \(ain_has_gbk_string\(ain,")
        self.assertIn("extern bool game_escalayer_reboot;", read("include/xsystem4.h"))
        title = extract_function("src/video.c", "set_window_title")
        self.assertIn("sjis_filename2utf(config.game_name", title)


if __name__ == "__main__":
    unittest.main()
