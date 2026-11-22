/*
 * Copyright (C) 2020 <KichikuouChrome@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
#include "hankaku.h"
#include "utfsjis.h"
#include "unittest.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

typedef struct {
	const char *zen;
	const char *han;
} Zen2hanTestCase;

typedef struct {
	const char *han;
	const char *zen;
} Han2ZenTestCase;

static void zen2han_test(void) {
	const Zen2hanTestCase testcases[] = {
		{"", ""},
		{"　", " "},
		{"、。，．", "､｡,."},
		{"・：；？！", "･:;?!"},
		{"゛゜｀＾", "ﾞﾟ`^"},
		{"￣＿ー／｜", "~_ｰ/|"},
		{"’”", "'\""},
		{"（）［］｛｝「」", "()[]{}｢｣"},
		{"＋－＝＜＞￥＄％＃＆＊＠", "+-=<>\\$%#&*@"},
		{"０１２３４５６７８９", "0123456789"},
		{"ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ", "ABCDEFGHIJKLMNOPQRSTUVWXYZ"},
		{"ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ", "abcdefghijklmnopqrstuvwxyz"},
		{"ァアィイゥウェエォオカキクケコサシスセソタチッツテトナニヌネノハヒフヘホマミムメモャヤュユョヨラリルレロワヲン", "ｧｱｨｲｩｳｪｴｫｵｶｷｸｹｺｻｼｽｾｿﾀﾁｯﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓｬﾔｭﾕｮﾖﾗﾘﾙﾚﾛﾜｦﾝ"},
		{NULL, NULL},
	};
	const char unconvertable[] =
		"´¨ヽヾゝゞ〃仝々〆〇―‐＼～∥…‥‘“〔〕〈〉《》『』【】±×÷≠≦≧"
		"∞∴♂♀°′″℃￠￡§☆★○●◎◇◆□■△▲▽▼※〒→←↑↓〓∈∋⊆⊇"
		"⊂⊃∪∩∧∨￢⇒⇔∀∃∠⊥⌒∂∇≡≒≪≫√∽∝∵∫∬Å‰♯♭♪†‡¶◯"
		"ぁあぃいぅうぇえぉおかがきぎくぐけげこごさざしじすずせぜそぞただちぢっ"
		"つづてでとどなにぬねのはばぱひびぴふぶぷへべぺほぼぽまみむめもゃやゅゆ"
		"ょよらりるれろゎわゐゑをんゔガギグゲゴザジズゼゾダヂヅデドバパビピブプ"
		"ベペボポヮヰヱヴヵヶΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩα"
		"βγδεζηθικλμνξοπρστυφχψω";
	for (const Zen2hanTestCase *tc = testcases; tc->zen; tc++) {
		ASSERT_STRCMP(sjis2utf(zen2han(utf2sjis(tc->zen), SHIFT_JIS)), tc->han);
		ASSERT_STRCMP(zen2han(tc->zen, UTF8), tc->han);
	}
	ASSERT_STRCMP(sjis2utf(zen2han(utf2sjis(unconvertable), SHIFT_JIS)), unconvertable);
	ASSERT_STRCMP(zen2han(unconvertable, UTF8), unconvertable);
}

static void han2zen_test(void) {
	const Han2ZenTestCase testcases[] = {
		{"", ""},
		{" ", "　"},
		{"ｧｱｨｲｩｳｪｴｫｵｶｷｸｹｺｻｼｽｾｿﾀﾁｯﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓｬﾔｭﾕｮﾖﾗﾘﾙﾚﾛﾜｦﾝ", "ぁあぃいぅうぇえぉおかきくけこさしすせそたちっつてとなにぬねのはひふへほまみむめもゃやゅゆょよらりるれろわをん"},
		{"､｡｢｣･ﾞﾟ", "、。「」・゛゜"},
		{"0123456789", "0123456789"},
		{"ABCDEFGHIJKLMNOPQRSTUVWXYZ", "ABCDEFGHIJKLMNOPQRSTUVWXYZ"},
		{"abcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyz"},
		{NULL, NULL},
	};
	for (const Han2ZenTestCase *tc = testcases; tc->han; tc++)
		ASSERT_STRCMP(sjis2utf(han2zen(utf2sjis(tc->han), SHIFT_JIS)), tc->zen);
}

static void format_number_test(void) {
	char buf[256];
	ASSERT_STRCMP(format_number(0, 0, buf), "0");
	ASSERT_STRCMP(format_number(42, 0, buf), "42");
	ASSERT_STRCMP(format_number(0, 3, buf), "  0");
	ASSERT_STRCMP(format_number(42, 3, buf), " 42");
	ASSERT_STRCMP(format_number(65535, 3, buf), "535");
}

static void format_number_zenkaku_test(void) {
	char buf[512];
	ASSERT_STRCMP(sjis2utf(format_number_zenkaku(0, 0, buf)), "０");
	ASSERT_STRCMP(sjis2utf(format_number_zenkaku(42, 0, buf)), "４２");
	ASSERT_STRCMP(sjis2utf(format_number_zenkaku(0, 3, buf)), "　　０");
	ASSERT_STRCMP(sjis2utf(format_number_zenkaku(42, 3, buf)), "　４２");
	ASSERT_STRCMP(sjis2utf(format_number_zenkaku(65535, 3, buf)), "５３５");
}

static void chs_tbl_test(void) {
	char dir_tmpl[] = "/tmp/xsys35_chs_tbl_XXXXXX";
	char *dir = mkdtemp(dir_tmpl);
	ASSERT_TRUE(dir != NULL);

	char tbl_path[256];
	snprintf(tbl_path, sizeof(tbl_path), "%s/chs.tbl", dir);
	uint8_t *buf = calloc(61440, 2);
	ASSERT_TRUE(buf != NULL);
	// Map SJIS 0x8141 -> GBK 0xD6D0 (U+4E2D "中"), SJIS 0x8142 -> ASCII '!'
	buf[0x8141 * 2] = 0xD0;
	buf[0x8141 * 2 + 1] = 0xD6;
	buf[0x8142 * 2] = '!';
	buf[0x8142 * 2 + 1] = 0x00;

	FILE *fp = fopen(tbl_path, "wb");
	ASSERT_TRUE(fp != NULL);
	ASSERT_EQUAL(fwrite(buf, 2, 61440, fp), 61440);
	fclose(fp);
	free(buf);

	ASSERT_TRUE(chs_tbl_load_from_dir(dir));
	ASSERT_TRUE(chs_tbl_is_loaded());

	// 0x8141 -> "中", 0x8142 -> "!", unmapped 0x82A0 -> SJIS "あ"
	const uint8_t sample[] = { 0x81, 0x41, 0x81, 0x42, 0x82, 0xA0, 0x00 };
	uint8_t *utf = sjis2utf(sample);
	ASSERT_STRCMP((const char *)utf, "中!あ");
	free(utf);

	chs_tbl_reset();
	ASSERT_TRUE(!chs_tbl_is_loaded());
	uint8_t *orig = sjis2utf(sample);
	ASSERT_STRCMP((const char *)orig, "、。あ");
	free(orig);

	unlink(tbl_path);
	rmdir(dir);
}

void hankaku_test(void) {
	zen2han_test();
	han2zen_test();
	format_number_test();
	format_number_zenkaku_test();
	chs_tbl_test();
}
