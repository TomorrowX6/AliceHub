/* Copyright (C) 2019 Nunuhara Cabbage <nunuhara@haniwa.technology>
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
 * along with this program; if not, see <http://gnu.org/licenses/>.
 */

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <math.h>

#include "system4.h"
#include "system4/ain.h"
#include "system4/buffer.h"
#include "system4/instructions.h"
#include "system4/string.h"
#include "system4/utfsjis.h"

#include "gfx/gfx.h"
#include "input.h"
#include "system4/little_endian.h"
#include "message_layout.h"
#include "xsystem4.h"
#include "escalayer_reboot_cn_ui.h"

#include "hll/hll.h"

bool game_daibanchou = false;
bool game_daibanchou_en = false;
bool game_daiteikoku = false;
bool game_haruurare = false;
bool game_rance01 = false;
bool game_rance02 = false;
bool game_rance02_mg = false;
bool game_rance6 = false;
bool game_rance6_mg = false;
bool game_rance7_mg = false;
bool game_sengokurance = false;
bool game_rance8 = false;
bool game_rance8_mg = false;
bool game_rance9 = false;
bool game_rance10 = false;
bool game_dohnadohna = false;
bool game_evenicle = false;
bool game_evenicle_rance = false;
bool game_evenicle_rance_cn = false;
bool game_evenicle_gaiden = false;
bool game_bladebriders = false;
bool game_evenicle2 = false;
bool game_evenicle2_rance = false;
bool game_haharanman = false;
bool game_okusama = false;
bool game_night_remaster = false;
bool game_oyakorankan = false;
bool game_toushin3 = false;
bool game_rance03 = false;
bool game_dungeons_and_dolls = false;
bool game_escalayer_reboot = false;
bool game_galzoo = false;
bool game_ixseal = false;
bool game_tsumashibori = false;
bool game_tsumamigui3 = false;
bool game_tsumamigui3_jun = false;
bool game_tsumamigui3_ntr = false;
bool game_maman = false;
bool game_waidonyo = false;

static void write_instruction0(struct buffer *out, enum opcode op)
{
	buffer_write_int16(out, op);
}

static void write_instruction1(struct buffer *out, enum opcode op, int32_t arg)
{
	buffer_write_int16(out, op);
	buffer_write_int32(out, arg);
}

static void write_instruction3(struct buffer *out, enum opcode op, int32_t arg0, int32_t arg1,
		int32_t arg2)
{
	buffer_write_int16(out, op);
	buffer_write_int32(out, arg0);
	buffer_write_int32(out, arg1);
	buffer_write_int32(out, arg2);
}

void set_msgskip_delay(struct ain *ain, unsigned ms)
{
	int orig_fno = ain_get_function(ain, "A");
	if (orig_fno <= 0) {
		WARNING("No 'A' function when applying msgskip delay");
		return;
	}

	int new_fno = ain_dup_function(ain, orig_fno);
	struct ain_function *override_f = &ain->functions[orig_fno];

	// override void A(void) {
	//     super();
	//     if (key_is_down(VK_CONTROL))
	//         System.sleep(ms);
	// }
	struct buffer out;
	buffer_init(&out, NULL, 0);
	write_instruction1(&out, FUNC, orig_fno);
	write_instruction1(&out, CALLFUNC, new_fno);
	write_instruction1(&out, PUSH, VK_CONTROL);
	write_instruction1(&out, CALLSYS, VM_XSYS_KEY_IS_DOWN);
	uint32_t ifz_addr = out.index;
	write_instruction1(&out, IFZ, 0);
	write_instruction1(&out, PUSH, ms);
	write_instruction1(&out, CALLSYS, SYS_SLEEP);
	buffer_write_int32_at(&out, ifz_addr + 2, ain->code_size + out.index);
	write_instruction0(&out, RETURN);
	write_instruction1(&out, ENDFUNC, orig_fno);

	override_f->address = ain->code_size + 6;
	ain->code = xrealloc(ain->code, ain->code_size + out.index);
	memcpy(ain->code + ain->code_size, out.buf, out.index);
	ain->code_size += out.index;
	free(out.buf);
}

static uint32_t find_func_end_addr(struct ain *ain, int fno)
{
	uint32_t begin = ain->functions[fno].address;
	uint32_t end = ain->code_size > 0 ? (uint32_t)ain->code_size : 0;
	for (int i = 0; i < ain->nr_functions; i++) {
		uint32_t addr = ain->functions[i].address;
		if (addr > begin && addr < end)
			end = addr;
	}
	return end;
}

// Rance 02 (MangaGamer version)
// -----------------------------
// Uses a custom proportional font implementation which makes assumptions
// about the font. We replace the _CalculateWidth function with a font-generic
// implementation.
//
// '^' character is mapped to 'é' (Rosé)
// '}' character is mapped to double-width dash
static void apply_rance02_hacks(struct ain *ain)
{
	int fno = ain_get_function(ain, "_CalculateWidth");
	if (fno <= 0) {
		WARNING("No '_CalculateWidth' function when applying Rance 02 compatibility hack");
		return;
	}
	struct ain_function *f = &ain->functions[fno];

	// Reduce the text scale if it wasn't set by the user
	if (!config.manual_text_x_scale) {
		config.text_x_scale = 0.9375;
	}

	// rewrite function via temporary buffer and verify bounds before writing
	uint32_t base = (uint32_t)f->address;
	uint32_t end = find_func_end_addr(ain, fno);
	struct buffer out;
	buffer_init(&out, NULL, 0);

	// _CalculateWidth(ref string str, int nFontSize, int nWeightSize) {
	//     if (str[0] > 255 || str[0] == '}')
	//         return nFontSize + nWeightSize;
	//     return nFontSize / 2 + nWeightSize;
	// }

	// if (str[0] > 255 || str[0] == '}')
	write_instruction1(&out, SH_LOCALREF, 0);
	write_instruction1(&out, PUSH, 0);
	write_instruction0(&out, C_REF);
	write_instruction0(&out, DUP);
	write_instruction1(&out, PUSH, 255);
	write_instruction0(&out, GT);
	write_instruction0(&out, SWAP);
	write_instruction1(&out, PUSH, '}');
	write_instruction0(&out, EQUALE);
	write_instruction0(&out, OR);
	size_t ifz_addr = out.index;
	write_instruction1(&out, IFZ, 0);

	// return nFontSize + nWeightSize
	write_instruction1(&out, SH_LOCALREF, 1);
	write_instruction1(&out, SH_LOCALREF, 2);
	write_instruction0(&out, ADD);
	size_t jump_addr = out.index;
	write_instruction1(&out, JUMP, 0);

	// return nFontSize / 2 + nWeightSize;
	buffer_write_int32_at(&out, ifz_addr + 2, (int32_t)(base + out.index));
	write_instruction1(&out, SH_LOCALREF, 1);
	write_instruction1(&out, PUSH, 2);
	write_instruction0(&out, DIV);
	write_instruction1(&out, SH_LOCALREF, 2);
	write_instruction0(&out, ADD);

	// multiply by config.text_x_scale (if not 1.0)
	buffer_write_int32_at(&out, jump_addr + 2, (int32_t)(base + out.index));
	if (fabsf(1.0f - config.text_x_scale) > 0.01) {
		union { int32_t i; float f; } cast = { .f = config.text_x_scale };
		write_instruction0(&out, ITOF);
		write_instruction1(&out, F_PUSH, cast.i);
		write_instruction0(&out, F_MUL);
		write_instruction0(&out, FTOI);
	}

	write_instruction0(&out, RETURN);
	write_instruction1(&out, ENDFUNC, fno);

	if (f->address >= 0 && end >= base && out.index <= end - base) {
		memcpy(ain->code + base, out.buf, out.index);
		game_rance02_mg = true;
	} else {
		WARNING("Rance 02 '_CalculateWidth' function body too small to patch safely");
	}
	free(out.buf);
}

// Rance VI (MangaGamer version)
// -----------------------------
// 'ﾉ' character is mapped to 'é' (Rosé)
static void apply_rance6_hacks(struct ain *ain)
{
	if (ain_get_function(ain, "GetTextWidth") < 0)
		return;
	game_rance6_mg = true;
}

// Rance IX
// --------
// T_PartsNo class is implemented in an extremely inefficient way, which
// causes noticable performance issues when using the UI (e.g. when loading
// the items screen).
// We rewrite this class to improve performance.
static void apply_rance9_hacks(struct ain *ain)
{
	int add_fno = ain_get_function(ain, "T_PartsNo@add");
	int erase_fno = ain_get_function(ain, "T_PartsNo@erase");
	if (add_fno < 0 || erase_fno < 0) {
		WARNING("Couldn't location T_PartsNo@add or T_PartsNo@erase");
		return;
	}

	struct buffer add_buf, erase_buf;
	buffer_init(&add_buf, NULL, 0);
	buffer_init(&erase_buf, NULL, 0);

	size_t return_false_addr[3];
	struct ain_function *add_f = &ain->functions[add_fno];
	uint32_t add_base = (uint32_t)add_f->address;
	uint32_t add_end = find_func_end_addr(ain, add_fno);
	// if (nNo < 10000 || nNo > 999999) return false;
	write_instruction3(&add_buf, SH_IF_LOC_LT_IMM, 0, 10000, 0);
	return_false_addr[0] = add_buf.index - 4;
	write_instruction3(&add_buf, SH_IF_LOC_GT_IMM, 0, 999999, 0);
	return_false_addr[1] = add_buf.index - 4;
	// int idx = nNo - 10000;
	write_instruction0(&add_buf, PUSHLOCALPAGE);
	write_instruction1(&add_buf, PUSH, 1);
	write_instruction1(&add_buf, SH_LOCALREF, 0);
	write_instruction1(&add_buf, PUSH, 10000);
	write_instruction0(&add_buf, SUB);
	write_instruction0(&add_buf, ASSIGN);
	write_instruction0(&add_buf, POP);
	// if (this.m_abUsed[idx]) return false;
	write_instruction1(&add_buf, SH_STRUCTREF, 1);
	write_instruction1(&add_buf, SH_LOCALREF, 1);
	write_instruction0(&add_buf, REF);
	write_instruction1(&add_buf, IFNZ, 0);
	return_false_addr[2] = add_buf.index - 4;
	// return (this.m_abUsed[idx] = true);
	write_instruction1(&add_buf, SH_STRUCTREF, 1);
	write_instruction1(&add_buf, SH_LOCALREF, 1);
	write_instruction1(&add_buf, PUSH, 1);
	write_instruction0(&add_buf, ASSIGN);
	write_instruction0(&add_buf, RETURN);
	// [return false]
	buffer_write_int32_at(&add_buf, return_false_addr[0], (int32_t)(add_base + add_buf.index));
	buffer_write_int32_at(&add_buf, return_false_addr[1], (int32_t)(add_base + add_buf.index));
	buffer_write_int32_at(&add_buf, return_false_addr[2], (int32_t)(add_base + add_buf.index));
	write_instruction1(&add_buf, PUSH, 0);
	write_instruction0(&add_buf, RETURN);

	struct ain_function *erase_f = &ain->functions[erase_fno];
	uint32_t erase_base = (uint32_t)erase_f->address;
	uint32_t erase_end = find_func_end_addr(ain, erase_fno);
	// if (nNo < 10000 || nNo > 999999) return false;
	write_instruction3(&erase_buf, SH_IF_LOC_LT_IMM, 0, 10000, 0);
	return_false_addr[0] = erase_buf.index - 4;
	write_instruction3(&erase_buf, SH_IF_LOC_GT_IMM, 0, 999999, 0);
	return_false_addr[1] = erase_buf.index - 4;
	// this.m_abUsed[nNo - 10000] = false;
	write_instruction1(&erase_buf, SH_STRUCTREF, 1);
	write_instruction1(&erase_buf, SH_LOCALREF, 0);
	write_instruction1(&erase_buf, PUSH, 10000);
	write_instruction0(&erase_buf, SUB);
	write_instruction1(&erase_buf, PUSH, 0);
	write_instruction0(&erase_buf, ASSIGN);
	write_instruction0(&erase_buf, POP);
	// return true;
	write_instruction1(&erase_buf, PUSH, 1);
	write_instruction0(&erase_buf, RETURN);
	// [return false]
	buffer_write_int32_at(&erase_buf, return_false_addr[0], (int32_t)(erase_base + erase_buf.index));
	buffer_write_int32_at(&erase_buf, return_false_addr[1], (int32_t)(erase_base + erase_buf.index));
	write_instruction1(&erase_buf, PUSH, 0);
	write_instruction0(&erase_buf, RETURN);

	if (add_f->address >= 0 && add_end >= add_base && add_buf.index <= add_end - add_base &&
	    erase_f->address >= 0 && erase_end >= erase_base && erase_buf.index <= erase_end - erase_base) {
		memcpy(ain->code + add_base, add_buf.buf, add_buf.index);
		memcpy(ain->code + erase_base, erase_buf.buf, erase_buf.index);
	} else {
		WARNING("Rance 9 T_PartsNo functions too small to patch safely");
	}
	free(add_buf.buf);
	free(erase_buf.buf);
}

// Daibanchou (English fan translation)
// ------------------------------------
// Gpx2Plus.CopyStretchReduceAMap behaves differently in order to work around
// text rendering issues caused by how the game calculates text width in
// StructRegionalSelect::DrawMenuItem.
static void apply_daibanchou_hacks(struct ain *ain)
{
	if (ain->nr_strings > 2 && !strcmp(ain->strings[2]->text, "Seijou Academy")) {
		game_daibanchou_en = true;
	}
}


static bool ain_has_gbk_string(struct ain *ain, const char *needle)
{
	for (int i = 0; i < ain->nr_strings && i < 10000; i++) {
		if (strstr(ain->strings[i]->text, needle))
			return true;
	}
	return false;
}

static bool ain_has_gbk_r03_strings(struct ain *ain)
{
	for (int i = 0; i < ain->nr_strings && i < 10000; i++) {
		if (!strncmp(ain->strings[i]->text, "\xc3\xd4\xb9\xac/", 5))
			return true;
	}
	return false;
}

static bool ain_has_evenicle2_rance_gbk_prologue(struct ain *ain)
{
	const char prologue[] =
		"\xd5\xe2\xca\xc7\xcb\xc0\xcd\xf6\xb3\xa3\xb0\xe9\xc9\xed\xb1\xdf\xb5\xc4\xca\xc0\xbd\xe7\xa1\xa3";
	for (int i = 0; i < ain->nr_messages; i++) {
		if (ain->messages[i] && !strcmp(ain->messages[i]->text, prologue))
			return true;
	}
	return false;
}

static bool next_evenicle_rance_string_push(struct ain *ain, uint32_t end,
		uint32_t *addr, uint32_t *operand)
{
	while (*addr < end && end - *addr >= 2) {
		unsigned op = (uint16_t)LittleEndian_getW(ain->code, *addr);
		if (op >= NR_OPCODES || op == ENDFUNC || op == FUNC)
			return false;
		uint32_t width = instruction_width(op);
		if (width > end - *addr)
			return false;
		uint32_t pos = *addr + 2;
		*addr += width;
		if (op == S_PUSH) {
			*operand = pos;
			return true;
		}
	}
	return false;
}

static void normalize_evenicle_rance_literals(struct ain *ain, const char *function,
		const char *literal, uint32_t expected_hash, const uint8_t *display_sites)
{
	int fno = ain_get_function(ain, (char *)function);
	if (fno < 0)
		return;
	uint32_t start = ain->functions[fno].address;
	uint32_t end = find_func_end_addr(ain, fno);
	uint32_t operand;
	if (expected_hash) {
		uint32_t hash = 2166136261u;
		for (uint32_t addr = start; next_evenicle_rance_string_push(ain, end, &addr, &operand); ) {
			int no = LittleEndian_getDW(ain->code, operand);
			if (no < 0 || no >= ain->nr_strings)
				return;
			const unsigned char *p = (const unsigned char *)ain->strings[no]->text;
			do {
				hash = (hash ^ *p) * 16777619u;
			} while (*p++);
		}
		if (hash != expected_hash)
			return;
	}

	unsigned site = 0;
	for (uint32_t addr = start; next_evenicle_rance_string_push(ain, end, &addr, &operand); ) {
		site++;
		if (display_sites) {
			if (!*display_sites)
				break;
			if (site != *display_sites)
				continue;
			display_sites++;
		}
		int no = LittleEndian_getDW(ain->code, operand);
		if (no < 0 || no >= ain->nr_strings)
			return;
		const char *text = ain->strings[no]->text;
		if (!*text || (literal && strcmp(text, literal)))
			continue;
		char *utf8 = cjk2utf_strict(text, false, NULL);
		if (!utf8)
			continue;
		char *gbk = utf2cjk(utf8, 0, true);
		char *round_trip = cjk2utf_strict(gbk, true, NULL);
		if (round_trip && !strcmp(utf8, round_trip) && strcmp(text, gbk))
			LittleEndian_putDW(ain->code, operand, ain_add_string(ain, gbk));
		free(round_trip);
		free(gbk);
		free(utf8);
	}
}

static void apply_evenicle_rance_cn_display_literals(struct ain *ain)
{
	// Catalog CN AIN: FNV-1a over each function's literal bytes (including NUL
	// separators). Sites are one-based S_PUSH ordinals; NULL selects every push.
	// Keep resource names, comparisons and translated literals on their original operands.
#define DISPLAY_SITES(...) ((const uint8_t[]){__VA_ARGS__, 0})
	const struct {
		const char *function;
		uint32_t literal_hash;
		const uint8_t *display_sites;
	} functions[] = {
		// Menus and dialog prompts.
		{ "ゲーム終了", 0xac990d0au, NULL },
		{ "タイトルに戻る", 0xf2ea0b36u, NULL },
		{ "T_TextEntryBox@ProgTextEntry", 0x3cf850bdu, DISPLAY_SITES(4) },
		{ "敗北時やり直し確認", 0x2bc04d54u, DISPLAY_SITES(6) },
		{ "＆装備アイテムスロット拡張", 0x75ec9f96u, NULL },
		{ "T_PMenu@Init_SCutCheckButton", 0x5651fc06u, DISPLAY_SITES(1, 2) },
		{ "ロード確認画面", 0xcb484ee3u, DISPLAY_SITES(2, 3, 4, 5) },
		{ "アイテム購入確認画面", 0x9505a4d6u, DISPLAY_SITES(2, 3, 4, 5) },
		{ "購入確認選択肢", 0x3d48e208u, DISPLAY_SITES(2, 3, 4, 7, 8, 9, 10, 11) },
		{ "購入＆使用確認選択肢", 0xbd974831u,
			DISPLAY_SITES(6, 7, 8, 10, 11, 13, 14, 15, 16, 17, 20, 21, 24, 25, 26, 27, 28, 30, 32, 33,
				36, 37) },
		{ "売却確認選択肢", 0x50b5c297u, DISPLAY_SITES(2, 3, 5, 6, 7) },
		{ "アイテム使用確認選択肢", 0x0462adcdu,
			DISPLAY_SITES(6, 7, 8, 10, 11, 13, 14, 15, 16, 17, 20, 21, 24, 25, 26, 27, 28, 30, 32, 33) },
		{ "アイテム使用選択肢", 0x7909d687u, NULL },
		{ "T_ItemMenu@InitButton", 0x60f8fcdeu, DISPLAY_SITES(31) },
		{ "T_ItemMenu@Create", 0xeb736932u, DISPLAY_SITES(1) },
		{ "T_ItemMenu@ExecuteSortTypeSelect", 0xb54cf1f7u, DISPLAY_SITES(6, 7, 8, 9, 10, 11, 12, 14) },
		{ "T_ItemMenu@SetSortSeqButton", 0x3463cdc1u, DISPLAY_SITES(1, 3) },
		{ "T_ItemPlate@GetItemName", 0x751a4839u, DISPLAY_SITES(1) },
		{ "T_ItemPlate@GetItemTypeName", 0x7e1a91a9u, DISPLAY_SITES(1, 2, 3, 5, 6, 7, 8, 9, 10, 11) },
		{ "T_ItemPlate@GetItemSetumei", 0x05029c7du, DISPLAY_SITES(1) },
		{ "T_SaveLoad@Create", 0x576cfbd6u, DISPLAY_SITES(14) },
		{ "T_SaveLoad@UpdatePageData", 0xb3462b55u, DISPLAY_SITES(9, 11, 13, 15, 17, 27, 28, 29, 30, 31) },
		{ "T_SaveLoad@ExecuteSave", 0x5186a456u, DISPLAY_SITES(3) },
		{ "T_Shop@CreateBase", 0x3743314eu, DISPLAY_SITES(4, 8) },
		{ "T_Shop@CreateChkConfirm", 0xc60f4d68u, DISPLAY_SITES(6, 7) },
		{ "T_Shop@ProgShopItem", 0x9be3affau, NULL },
		{ "T_SkillSetting@Create", 0x46090a57u, DISPLAY_SITES(26) },
		{ "T_StatusDisp@GetCharaName", 0xbad2df74u, DISPLAY_SITES(2) },
		{ "T_StatusDisp@GetCharaSecName", 0xf8a6c1f0u, DISPLAY_SITES(2) },
		{ "T_BackData@OpenTypeWindow", 0xd5843c1bu, DISPLAY_SITES(7) },
		{ "T_NaviMap@Create", 0xe1321917u, DISPLAY_SITES(24, 25) },
		{ "T_NaviMap@Update", 0x81a8a067u, DISPLAY_SITES(1, 2) },
		{ "T_Collection@Update_Fish", 0xd1fdf7b3u, DISPLAY_SITES(10, 11, 12, 13) },
		{ "気球に乗る確認", 0x414ecdb3u, DISPLAY_SITES(1, 2, 4, 5) },
		// Reward notices, battle results and log text.
		{ "宝箱からお金", 0x557d207eu, NULL },
		{ "宝箱からアイテム", 0x559aeddcu, NULL },
		{ "＆アイテム表示名取得", 0x9053008bu, DISPLAY_SITES(2) },
		{ "T_GetItemDisp@RunItem", 0xa59dbc38u, DISPLAY_SITES(6) },
		{ "T_GetItemDisp@RunMoney", 0x6bcd0ef9u, DISPLAY_SITES(3, 8) },
		{ "T_GetItemDisp@RunUseItem", 0x66af636au, DISPLAY_SITES(5) },
		{ "T_GetItemDisp@RunAutoSave", 0x27acd488u, DISPLAY_SITES(1, 3) },
		{ "T_GetItemDisp@RunTrap", 0xbe8dd8f0u, DISPLAY_SITES(3) },
		{ "T_GetItemDisp@RunPosInfo", 0xbe8dd8f0u, DISPLAY_SITES(3) },
		{ "T_GetItemDisp@RunNormalInfo", 0xbe8dd8f0u, DISPLAY_SITES(3) },
		{ "T_LevelUpDisp@SetInfoCharaData", 0xe7567454u,
			DISPLAY_SITES(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 21) },
		{ "T_Battle@Run_AfterBattle", 0x7c76fb0au, DISPLAY_SITES(2, 3, 4, 5, 6, 7, 8, 9) },
		{ "T_Battle@Act_UseItem", 0x9e78dee4u, DISPLAY_SITES(1, 3, 6, 7) },
		{ "T_BatProg_Sequence@ProgPoison", 0x23cb2090u, DISPLAY_SITES(2, 4) },
		{ "T_BatProg_Sequence@ProgStone", 0xe1399220u, DISPLAY_SITES(2) },
		{ "T_BatProg_Sequence@ProgBind", 0xe8646c6fu, DISPLAY_SITES(2) },
		{ "T_BatProg_Sequence@ProgPara", 0xe6e23a68u, DISPLAY_SITES(2) },
		{ "T_BatProg_Sequence@ProgRec", 0xf8172616u, DISPLAY_SITES(2) },
		{ "T_BatProg_Command@ProgEnemy", 0x8c5d593au, DISPLAY_SITES(1, 3) },
		{ "T_BatProg_Command@NoAction", 0xaa1fc138u, NULL },
		{ "T_BatProg_TurnEnd@CheckGenCounter", 0x097bcaf2u, DISPLAY_SITES(2, 3) },
		{ "T_BatProg_TurnEnd@CheckGuriguraPunch", 0x28519488u, NULL },
		{ "T_BatProg_TurnEnd@CheckTrap", 0xf39cdb9cu, DISPLAY_SITES(2, 3) },
		{ "T_BatProg_TurnEnd@CheckEnemyDead", 0xae5065bbu, NULL },
		{ "T_BatProg_TurnEnd@CheckAllyDead", 0xd93ecd16u, DISPLAY_SITES(1, 2, 4, 5, 6, 7) },
		{ "T_BatProg_Prog@Prog", 0xcb3382e5u, DISPLAY_SITES(1) },
		{ "T_BatProg_Prog@CulcDamage", 0x053c0743u, NULL },
		{ "T_BatProg_Prog@SubLastCatch", 0xb0ad50dbu, NULL },
		{ "T_BattleDispDamage@Set_Escape", 0xb2326b15u, DISPLAY_SITES(2, 3, 4, 5, 6) },
		{ "T_BattleDispDamage@Set_AllyBind", 0xbcd0ec02u, DISPLAY_SITES(2, 3, 4, 5, 6) },
		{ "T_BattleDispDamage@Set_GoldDamage", 0x48d076dbu, NULL },
		{ "T_BattleResult@Create_WinBase", 0x445bb480u, DISPLAY_SITES(17, 19, 22, 23, 24, 25) },
		{ "T_BattleResult@Create_WinItem", 0x6d94e603u, DISPLAY_SITES(5, 6) },
		{ "T_BattleResult@Create_Escape", 0xdc38be5cu, DISPLAY_SITES(2, 3) },
		{ "嫁イベント実行", 0x528cfe73u, DISPLAY_SITES(13, 14, 15, 16) },
		{ "回復ポイント実行処理", 0x800412fcu, NULL },
		{ "回復量指定実行処理", 0x82643cafu, NULL },
		{ "百景ボーナス判定", 0xad8cff76u, DISPLAY_SITES(1, 2, 3) },
		{ "戦闘割り込み", 0xa99b9043u, DISPLAY_SITES(4) },
		// Skill and item display data (not item keys or icon names).
		{ "T_SkillPlate@GetPlateText", 0x09ebebb4u, NULL },
		{ "ＰＧＳアイテム魚拓工場", 0x13e11ebbu, DISPLAY_SITES(1, 2) },
		{ "スキル初期化", 0xc41e014bu, NULL },
		{ "特典用キャラ設定", 0x9ebc9466u, DISPLAY_SITES(3, 4, 5, 7, 8, 9, 10) },
		{ "ＳＷ", 0xa975d888u, DISPLAY_SITES(1) },
		{ "アイテム工場", 0x3cc1efd4u,
			DISPLAY_SITES(3, 5, 7, 9, 12, 13, 15, 16, 18, 19, 21, 22, 24, 25, 27, 28, 31, 32, 34, 35,
				37, 38, 41, 42, 44, 46, 48, 50, 53, 54, 56, 57, 59, 60, 62, 63, 65, 66, 69,
				70, 72, 73, 75, 76, 79, 80, 82, 84, 86, 88, 90, 92, 94, 96, 98, 100, 102,
				104, 107, 109, 111, 113, 115, 117, 120, 122, 124, 126, 128, 130, 132, 134,
				136, 138) },
		{ "説明に機能追加", 0x65d04c02u,
			DISPLAY_SITES(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
				23, 24, 25, 26, 27, 28, 29, 30, 31) },
	};
#undef DISPLAY_SITES

	for (size_t i = 0; i < sizeof(functions) / sizeof(*functions); i++) {
		char *function = utf2cjk(functions[i].function, 0, false);
		normalize_evenicle_rance_literals(ain, function, NULL,
				functions[i].literal_hash, functions[i].display_sites);
		free(function);
	}

	normalize_evenicle_rance_literals(ain, "T_GetItemDisp@RunSkill", "\x83\x89\x83\x93\x83\x58", 0, NULL);
	normalize_evenicle_rance_literals(ain, "T_GetItemDisp@RunSkill", "\x82\xcd", 0, NULL);
	normalize_evenicle_rance_literals(ain, "\x95\xf3\x94\xa0\x82\xa9\x82\xe7\x83\x58\x83\x4c\x83\x8b",
			"\x82\xf0\x8a\x6f\x82\xa6\x82\xbd\x81\x49", 0, NULL);
	normalize_evenicle_rance_literals(ain, "GetSpaceTextFromString", "\x81\x40", 0, NULL);
}

static bool ain_messages_look_gbk(struct ain *ain)
{
	unsigned long gbk_score = 0;
	unsigned long sjis_score = 0;
	if (!ain || ain->nr_messages <= 0 || !ain->messages)
		return false;
	int step = ain->nr_messages > 8192 ? ain->nr_messages / 8192 : 1;
	for (int i = 0; i < ain->nr_messages; i += step) {
		if (!ain->messages[i])
			continue;
		const unsigned char *s = (const unsigned char *)ain->messages[i]->text;
		if (!s)
			continue;
		for (size_t j = 0; s[j]; ) {
			unsigned char b0 = s[j];
			if (b0 < 0x80) {
				j++;
				continue;
			}
			if (b0 >= 0x81 && b0 <= 0xfe && s[j + 1] >= 0x40 && s[j + 1] <= 0xfe && s[j + 1] != 0x7f) {
				unsigned char b1 = s[j + 1];
				if ((b0 >= 0xb0 && b0 <= 0xd7 && b1 >= 0xa1 && b1 <= 0xfe)
						|| (b0 >= 0xa1 && b0 <= 0xa9 && b1 >= 0xa1 && b1 <= 0xfe)
						|| (b0 >= 0xaa && b0 <= 0xdf && b1 >= 0x40 && b1 <= 0xa0)) {
					gbk_score++;
				}
				j += 2;
				continue;
			}
			j++;
		}
		for (size_t j = 0; s[j]; ) {
			unsigned char b0 = s[j];
			if (b0 < 0x80 || (b0 >= 0xa1 && b0 <= 0xdf)) {
				j++;
				continue;
			}
			if (((b0 >= 0x81 && b0 <= 0x9f) || (b0 >= 0xe0 && b0 <= 0xfc))
					&& ((s[j + 1] >= 0x40 && s[j + 1] <= 0x7e) || (s[j + 1] >= 0x80 && s[j + 1] <= 0xfc))) {
				unsigned char b1 = s[j + 1];
				if ((b0 == 0x82 && b1 >= 0x9f && b1 <= 0xf1)
						|| (b0 == 0x83 && b1 >= 0x40 && b1 <= 0x96)
						|| (b0 == 0x81 && b1 >= 0x40 && b1 <= 0xac)
						|| (b0 >= 0x88 && b0 <= 0x9f && b1 >= 0x40 && b1 <= 0x7e)) {
					sjis_score++;
				}
				j += 2;
				continue;
			}
			j++;
		}
	}
	return gbk_score >= 64 && gbk_score > sjis_score * 2;
}

enum ain_text_encoding {
	AIN_ENC_UNKNOWN = 0,
	AIN_ENC_GBK,
	AIN_ENC_SJIS,
};

static void scan_string_array_encoding(struct string **arr, int count,
		unsigned long *gbk_score_out, unsigned long *sjis_score_out)
{
	unsigned long gbk_score = 0;
	unsigned long sjis_score = 0;
	if (!arr || count <= 0) {
		*gbk_score_out = 0;
		*sjis_score_out = 0;
		return;
	}
	int step = count > 8192 ? count / 8192 : 1;
	for (int i = 0; i < count; i += step) {
		if (!arr[i])
			continue;
		const unsigned char *s = (const unsigned char *)arr[i]->text;
		for (size_t j = 0; s[j]; ) {
			unsigned char b0 = s[j];
			if (b0 < 0x80) {
				j++;
				continue;
			}
			if (b0 >= 0x81 && b0 <= 0xfe && s[j + 1] >= 0x40 && s[j + 1] <= 0xfe && s[j + 1] != 0x7f) {
				unsigned char b1 = s[j + 1];
				if ((b0 >= 0xb0 && b0 <= 0xd7 && b1 >= 0xa1 && b1 <= 0xfe)
						|| (b0 >= 0xa1 && b0 <= 0xa9 && b1 >= 0xa1 && b1 <= 0xfe)
						|| (b0 >= 0xaa && b0 <= 0xdf && b1 >= 0x40 && b1 <= 0xa0)) {
					gbk_score++;
				}
				j += 2;
				continue;
			}
			j++;
		}
		for (size_t j = 0; s[j]; ) {
			unsigned char b0 = s[j];
			if (b0 < 0x80 || (b0 >= 0xa1 && b0 <= 0xdf)) {
				j++;
				continue;
			}
			if (((b0 >= 0x81 && b0 <= 0x9f) || (b0 >= 0xe0 && b0 <= 0xfc))
					&& ((s[j + 1] >= 0x40 && s[j + 1] <= 0x7e) || (s[j + 1] >= 0x80 && s[j + 1] <= 0xfc))) {
				unsigned char b1 = s[j + 1];
				if ((b0 == 0x82 && b1 >= 0x9f && b1 <= 0xf1)
						|| (b0 == 0x83 && b1 >= 0x40 && b1 <= 0x96)
						|| (b0 == 0x81 && b1 >= 0x40 && b1 <= 0xac)
						|| (b0 >= 0x88 && b0 <= 0x9f && b1 >= 0x40 && b1 <= 0x7e)) {
					sjis_score++;
				}
				j += 2;
				continue;
			}
			j++;
		}
	}
	*gbk_score_out = gbk_score;
	*sjis_score_out = sjis_score;
}

static enum ain_text_encoding ain_detect_text_encoding(struct ain *ain)
{
	if (!ain)
		return AIN_ENC_UNKNOWN;
	unsigned long gbk_score = 0, sjis_score = 0;
	scan_string_array_encoding(ain->messages, ain->nr_messages, &gbk_score, &sjis_score);
	if (gbk_score >= 64 && gbk_score > sjis_score * 2)
		return AIN_ENC_GBK;
	if (sjis_score >= 64 && sjis_score > gbk_score * 16)
		return AIN_ENC_SJIS;
	if (gbk_score + sjis_score < 32) {
		scan_string_array_encoding(ain->strings, ain->nr_strings, &gbk_score, &sjis_score);
		if (gbk_score >= 64 && gbk_score > sjis_score * 2)
			return AIN_ENC_GBK;
		if (sjis_score >= 64 && sjis_score > gbk_score * 16)
			return AIN_ENC_SJIS;
	}
	return AIN_ENC_UNKNOWN;
}

static bool config_game_name_has_gbk_marker(const char *name)
{
	if (!name)
		return false;
	return strstr(name, "\xc0\xbc\xcb\xb9")
		|| strstr(name, "\xb6\xe0\xc4\xc8")
		|| strstr(name, "\xcf\xc4\xcd\xde")
		|| strstr(name, "\xd6\xd0\xce\xc4")
		|| strstr(name, "\xba\xba\xbb\xaf")
		|| strstr(name, "\x9e\x61\xbb\xaf")
		|| strstr(name, "\xb3\xac\xb0\xba\xc9\xf1\xc6\xef")
		|| strstr(name, "\xc6\xde\xa4\xb7\xa4\xdc\xa4\xea")
		|| strstr(name, "\xb4\xf3\xb7\xac\xe9L")
		|| strstr(name, "\xb4\xf3\xb7\xac\xb3\xa4")
		|| strstr(name, "\xb4\xf3\xb5\xdb\xb9\xfa")
		|| strstr(name, "\xd0\xa3\xc4\xda\xb3\xe0\xcf\xdf")
		|| strstr(name, "\xcc\xab\xcc\xab")
		|| strstr(name, "\xd2\xec\xbf\xd5\xbf\xdf")
		|| strstr(name, "\xd0\xb0\xd2\xb9")
		|| strstr(name, "\xb6\xb7\xc9\xf1");
}

static void apply_rance03_message_layout(struct ain *ain)
{
	// Reflow raw glyph positions before the script applies centre/right alignment.
	int fno = ain_get_function(ain, "CMessageTextView@MoveTextPosForLayout");
	if (fno < 0 || ain->functions[fno].nr_args != 1 || !message_layout_init_rance03(ain))
		return;
	int original = ain_dup_function(ain, fno);
	struct buffer out;
	buffer_init(&out, NULL, 0);
	write_instruction1(&out, FUNC, fno);
	write_instruction0(&out, PUSHSTRUCTPAGE);
	write_instruction1(&out, CALLSYS, VM_XSYS_FIT_RANCE03_MESSAGE);
	write_instruction0(&out, PUSHSTRUCTPAGE);
	write_instruction1(&out, SH_LOCALREF, 0);
	write_instruction1(&out, CALLMETHOD, original);
	write_instruction0(&out, RETURN);
	write_instruction1(&out, ENDFUNC, fno);
	ain->functions[fno].address = ain->code_size + 6;
	ain->code = xrealloc(ain->code, ain->code_size + out.index);
	memcpy(ain->code + ain->code_size, out.buf, out.index);
	ain->code_size += out.index;
	free(out.buf);
}

static void apply_rance03_card_caption_layout(struct ain *ain)
{
	int fno = ain_get_function(ain, "AdvSelectionButton@setPartsPos");
	int set_pos = ain_get_function(ain, "Parts@setPos");
	if (fno < 0 || set_pos < 0)
		return;
	uint32_t end = find_func_end_addr(ain, fno);
	uint32_t found = 0;
	union { float f; uint32_t i; } old = { .f = 125.f }, replacement = { .f = 121.f };
	for (uint32_t address = ain->functions[fno].address; address + 2 <= end;) {
		enum opcode op = LittleEndian_getW(ain->code, address);
		if (op >= NR_OPCODES)
			return;
		int size = instruction_width(op);
		if (size <= 0 || address + size > end)
			return;
		if (op == F_PUSH && LittleEndian_getDW(ain->code, address + 2) == old.i
				&& address + size + 6 <= end && LittleEndian_getW(ain->code, address + size) == CALLMETHOD
				&& LittleEndian_getDW(ain->code, address + size + 2) == (uint32_t)set_pos) {
			if (found)
				return;
			found = address + 2;
		}
		address += size;
	}
	if (found)
		LittleEndian_putDW(ain->code, found, replacement.i);
}

static void fix_rance03_effect_sound_prefix(struct ain *ain, const char *method,
		const char *folder, int old_skip, int nr_args)
{
	int fno = ain_get_function(ain, (char *)method);
	if (fno < 0 || ain->functions[fno].nr_args != nr_args)
		return;
	char *prefix = utf2sjis(folder, 0);
	size_t prefix_bytes = strlen(prefix);
	bool found_prefix = false;
	for (int i = 0; i < ain->nr_strings; i++) {
		const char *s = ain->strings[i]->text;
		if (!strncmp(s, prefix, prefix_bytes) && (s[prefix_bytes] == '/' || s[prefix_bytes] == '\\')) {
			found_prefix = true;
			break;
		}
	}
	int replacement = sjis_count_char(prefix) + 1;
	free(prefix);
	if (!found_prefix || replacement == old_skip)
		return;
	uint32_t begin = ain->functions[fno].address;
	uint32_t end = find_func_end_addr(ain, fno);
	uint32_t operands[2];
	int count = 0;
	uint32_t length = 0, substring = 0;
	for (uint32_t p = begin; p + 2 <= end;) {
		enum opcode op = LittleEndian_getW(ain->code, p);
		if (op >= NR_OPCODES)
			return;
		int size = instruction_width(op);
		if (size <= 0 || p + size > end)
			return;
		if (op == PUSH && LittleEndian_getDW(ain->code, p + 2) == (uint32_t)old_skip) {
			if (count == 2)
				return;
			operands[count++] = p;
		}
		if (op == S_LENGTH) {
			if (length)
				return;
			length = p;
		}
		if (op == S_GETPART) {
			if (substring)
				return;
			substring = p;
		}
		p += size;
	}
	if (count != 2 || !length || !substring || !(operands[0] < length && length < operands[1])
			|| operands[1] + 8 != substring || LittleEndian_getW(ain->code, operands[1] + 6) != SUB)
		return;
	for (int i = 0; i < 2; i++)
		LittleEndian_putDW(ain->code, operands[i] + 2, replacement);
}

static void apply_rance03_effect_sound_paths(struct ain *ain)
{
	fix_rance03_effect_sound_prefix(ain, "DamageEffect@playSound", "戦闘效果", 8, 0);
	fix_rance03_effect_sound_prefix(ain, "SceneDungeon@openDungeonEffectFlat", "迷宫", 6, 1);
}

static void apply_rance03_skill_cooldown_layout(struct ain *ain)
{
	int fno = ain_get_function(ain, "SkillSelector@setPartsPos");
	int set_pos = ain_get_function(ain, "Parts@setPos");
	if (fno < 0 || set_pos < 0 || ain->functions[fno].nr_args)
		return;
	uint32_t begin = ain->functions[fno].address;
	uint32_t end = find_func_end_addr(ain, fno);
	uint32_t found = 0;
	union { float f; uint32_t i; } old_x = { .f = -30.f }, old_y = { .f = 18.f };
	for (uint32_t p = begin; p + 2 <= end;) {
		enum opcode op = LittleEndian_getW(ain->code, p);
		if (op >= NR_OPCODES)
			return;
		int size = instruction_width(op);
		if (size <= 0 || p + size > end)
			return;
		if (op == F_PUSH && p >= begin + 8 && p + 18 <= end
				&& LittleEndian_getW(ain->code, p - 8) == PUSH
				&& LittleEndian_getDW(ain->code, p - 6) == 8
				&& LittleEndian_getW(ain->code, p - 2) == REF
				&& LittleEndian_getDW(ain->code, p + 2) == old_x.i
				&& LittleEndian_getW(ain->code, p + 6) == F_PUSH
				&& LittleEndian_getDW(ain->code, p + 8) == old_y.i
				&& LittleEndian_getW(ain->code, p + 12) == CALLMETHOD
				&& LittleEndian_getDW(ain->code, p + 14) == (uint32_t)set_pos) {
			if (found)
				return;
			found = p;
		}
		p += size;
	}
	if (found) {
		// parts[8] uses origin_mode=2 (top-center, origin_offset.x=-45, y=0) after R03 remapping;
		// pos=(59, 16) places the 90x90 cooldown overlay at (14, 16), centered on (59, 61).
		union { float f; uint32_t i; } x = { .f = 59.f }, y = { .f = 16.f };
		LittleEndian_putDW(ain->code, found + 2, x.i);
		LittleEndian_putDW(ain->code, found + 8, y.i);
	}
}

void apply_game_specific_hacks(struct ain *ain)
{
	game_daibanchou = false;
	game_daibanchou_en = false;
	game_daiteikoku = false;
	game_haruurare = false;
	game_rance02_mg = false;
	game_rance6 = false;
	game_rance6_mg = false;
	game_rance7_mg = false;
	game_sengokurance = false;
	game_rance8 = false;
	game_rance8_mg = false;
	game_rance9 = false;
	game_rance10 = false;
	game_dohnadohna = false;
	game_evenicle = false;
	game_evenicle_rance = false;
	game_evenicle_gaiden = false;
	game_bladebriders = false;
	game_evenicle2 = false;
	game_evenicle2_rance = false;
	game_haharanman = false;
	game_okusama = false;
	game_night_remaster = false;
	game_oyakorankan = false;
	game_toushin3 = false;
	game_rance03 = false;
	game_dungeons_and_dolls = false;
	game_escalayer_reboot = false;
	game_galzoo = false;
	game_ixseal = false;
	game_tsumashibori = false;
	game_tsumamigui3 = false;
	game_tsumamigui3_jun = false;
	game_tsumamigui3_ntr = false;
	game_maman = false;
	game_waidonyo = false;

	game_rance10 = ain->version == 12
		&& ain_get_function(ain, "CardConstructProcessCacheFront@CreateOrganizationProcess") >= 0;
	game_dohnadohna = ain->version == 14
		&& ain_get_function(ain, "DohnaDohna@CreateResources") >= 0;
	game_evenicle_rance = ain->version == 6
		&& ain_get_struct(ain, "CBigMap") >= 0
		&& ain_has_gbk_string(ain, "56s5d4");
	game_evenicle_gaiden = ain->version == 6
		&& ain_get_struct(ain, "CBigMap") >= 0
		&& ain_has_gbk_string(ain, "aa6sdd5432");
	game_bladebriders = ain->version == 6
		&& ain_has_gbk_string(ain, "wf:-3");
	game_evenicle = ain->version == 6
		&& !game_evenicle_rance
		&& !game_evenicle_gaiden
		&& ain_get_struct(ain, "CBigMap") >= 0
		&& ain_get_function(ain, "CMapPlayer@RunPathFindMove") >= 0;
	game_evenicle2 = ain->version == 14
		&& ain_get_function(ain, "C_MedicaMenu@0") >= 0;
	game_evenicle2_rance = game_evenicle2
		&& ain_has_gbk_string(ain, "th74s65a");
	game_haharanman = ain->version == 14
		&& ain_has_gbk_string(ain, "83h2mjr");
	game_okusama = ain->version == 14
		&& ain_get_function(ain, "AdvGauge@UpdatePregnancy") >= 0
		&& ain_has_gbk_string(ain, "4fnd6e97tn9");
	game_night_remaster = ain->version == 14
		&& ain_get_function(ain, "C_Monster@nMoonPiece::get") >= 0
		&& ain_has_gbk_string(ain, "45qsdbdsfq5");
	game_oyakorankan = ain->version == 6
		&& ain_get_struct(ain, "StatusHeroineInfomation") >= 0
		&& ain_get_struct(ain, "HeroineMapIcon") >= 0;
	game_toushin3 = ain->version == 5
		&& ain_get_library(ain, "Toushin3Loader") >= 0;
	game_rance01 = ain->version == 6
		&& ain_get_struct(ain, "Player") >= 0
		&& ain_get_struct(ain, "DungeonLine") >= 0
		&& ain_get_global(ain, "g_chipDepository") >= 0;
	game_rance02 = ain->version == 6
		&& ain_get_struct(ain, "\x83\x4c\x83\x83\x83\x89\x83\x66\x81\x5b\x83\x5e") >= 0
		&& ain_get_global(ain, "\x90\xa2\x90\x46\x8a\xe0") >= 0
		&& ain_get_global(ain, "\xb6\xd9\xcf") >= 0;
	game_rance03 = ain->version == 6
		&& ain_get_struct(ain, "CMessageTextView") >= 0
		&& ain_get_global(ain, "g_playerCommonParam") >= 0
		&& ain_get_function(ain, "SceneCamp@onClickSaveLion") >= 0;
	game_escalayer_reboot = ain->version == 6
		&& ain_get_function(ain, "C_BATTLE_PLAYER_RESULT_COMMAND@SetEscalayerPunch") >= 0;
	game_ixseal = ain->version == 11
		&& ain_get_struct(ain, "TrainingSelect::C_EXECUTE") >= 0;
	game_rance6 = ain->version == 4
		&& ain_get_library(ain, "DrawDungeon") >= 0
		&& ain_get_library(ain, "ACXLoader") >= 0
		&& ain_get_struct(ain, "CDungeon") >= 0;
	game_galzoo = ain->version == 5
		&& ain_get_library(ain, "DrawDungeon14") >= 0
		&& ain_get_struct(ain, "CDungeon") >= 0;
	game_tsumashibori = ain->version == 5
		&& ain_get_struct(ain, "PLAY_TABLE_SAKURA") >= 0
		&& ain_get_struct(ain, "PLAY_TABLE_MADOKA") >= 0
		&& ain_get_struct(ain, "SAKURA_SIDE_SPRITE") >= 0;
	game_daibanchou = ain->version == 4
		&& ain_get_library(ain, "BanMisc") >= 0
		&& ain_get_struct(ain, "StructPowerUpPhase") >= 0;
	game_daiteikoku = ain->version == 6
		&& ain_get_struct(ain, "StarBattleArea") >= 0
		&& ain_get_struct(ain, "AdmiralSkillInfoConstant") >= 0;
	game_haruurare = ain->version == 6
		&& ain_get_struct(ain, "HaruApp") >= 0
		&& ain_get_struct(ain, "CustomerRequirement") >= 0;
	game_tsumamigui3 = ain->version == 7
		&& ain_get_struct(ain, "C_LOVE_SHIELD") >= 0
		&& ain_get_struct(ain, "C_LOVE_SHIELD_GAUGE") >= 0;
	if (!game_tsumamigui3 && ain->version == 7 && config.ain_filename) {
		const char *ain_base = strrchr(config.ain_filename, '/');
		ain_base = ain_base ? ain_base + 1 : config.ain_filename;
		game_tsumamigui3 = !strcasecmp(ain_base, "Tsumamigui3.ain");
	}
	game_tsumamigui3_jun = ain->version == 8
		&& ain_get_struct(ain, "C_BOOT_WARNING") >= 0
		&& ain_has_gbk_string(ain, "-asat;df,zx");
	if (!game_tsumamigui3_jun && ain->version == 8 && config.ain_filename) {
		const char *ain_base = strrchr(config.ain_filename, '/');
		ain_base = ain_base ? ain_base + 1 : config.ain_filename;
		game_tsumamigui3_jun = !strcasecmp(ain_base, "Tsuma3SO_Junai.ain");
	}
	game_tsumamigui3_ntr = ain->version == 8
		&& ain_get_struct(ain, "C_BOOT_WARNING") >= 0
		&& ain_has_gbk_string(ain, "asd34k8jklh");
	if (!game_tsumamigui3_ntr && ain->version == 8 && config.ain_filename) {
		const char *ain_base = strrchr(config.ain_filename, '/');
		ain_base = ain_base ? ain_base + 1 : config.ain_filename;
		game_tsumamigui3_ntr = !strcasecmp(ain_base, "Tsuma3SO_Netorare.ain");
	}
	game_maman = ain->version == 8
		&& ain_get_struct(ain, "C_BOOT_WARNING") >= 0
		&& ain_get_struct(ain, "SaveData::C_PARAMETER") >= 0;
	if (!game_maman && ain->version == 8 && config.ain_filename) {
		const char *ain_base = strrchr(config.ain_filename, '/');
		ain_base = ain_base ? ain_base + 1 : config.ain_filename;
		game_maman = !strcasecmp(ain_base, "Maman.ain");
	}
	game_waidonyo = ain->version == 6
		&& ain_get_library(ain, "ChrLoader") >= 0
		&& ain_get_library(ain, "MapLoader") >= 0
		&& ain_get_struct(ain, "T_MamatotoDamage") >= 0;
	// The Chinese release uses CP936 for script text and archive entry names.
	// Detect actual AIN text encoding first so Shift-JIS releases/repacks with a Chinese
	// GameName in AliceStart.ini (e.g. Shift-JIS Rance IX with GameName="兰斯9") are not
	// forced into GBK mode, while GBK releases with an unmodified JP/EN GameName still
	// enable GBK. Fall back to GameName/symbol heuristics only when AIN text is inconclusive.
	// UTF-8 releases are converted to GBK when loaded.
	enum ain_text_encoding text_enc = sys4_source_is_utf8
		? AIN_ENC_GBK
		: ain_detect_text_encoding(ain);
	bool gbk_game_name = config_game_name_has_gbk_marker(config.game_name);
	game_evenicle_rance_cn = game_evenicle_rance
		&& (ain_has_gbk_string(ain, "\xb4\xcb\xce\xaa\xd2\xbb\xcc\xf5\xb2\xe2\xca\xd4\xce\xc4\xb1\xbe")
			|| ain_has_gbk_string(ain, "\xc0\xbc\xcb\xb9"));
	bool gbk_heuristics = gbk_game_name
		|| ain_get_global(ain, "\xc8\xab\xcc\xe5\xa3\xc3\xa3\xc7\xb1\xa3\xb3\xd6") >= 0
		|| ain_get_struct(ain, "\xa3\xd3\xb2\xbf\xb6\xd3\xd2\xbb\xc0\xc0") >= 0
		|| (game_rance10 && ain_get_global(ain, "\x91\x53\x91\xcc\x82\x62\x82\x66\x95\xdb\x8e\x9d") < 0)
		|| (game_dohnadohna && ain_get_function(ain, "\x91\x53\x83\x81\x83\x62\x83\x5a\x81\x5b\x83\x57\x98\x67\x91\x53\x8f\xc1\x8b\x8e") < 0)
		|| (game_evenicle && (ain_get_global(ain, "g_a\xa5\xad\xa5\xe3\xa5\xe9\xa5\xc7\xa9`\xa5\xbf") >= 0
			|| ain_get_function(ain, "\xcb\xf9\xb3\xd6\xbd\xf0\xd4O\xb6\xa8") >= 0))
		|| game_evenicle_rance_cn
		|| (game_evenicle2_rance && ain_has_evenicle2_rance_gbk_prologue(ain))
		|| (game_rance01 && ain_has_gbk_string(ain, "\xd7\xee\xb4\xf3\xb5\xc8\xbc\xb6"))
		|| (game_rance02 && ain_has_gbk_string(ain, "\xa5\xe9\xa5\xf3\xa5\xb9\xa3\xb0\xa3\xb2.alm"))
		|| (game_rance03 && ain_has_gbk_r03_strings(ain))
		|| (game_escalayer_reboot && (ain_has_gbk_string(ain, "\xbd\xf8\xd0\xd0\xbf\xec\xcb\xd9\xb1\xa3\xb4\xe6\xc2\xf0\xa3\xbf")
			|| ain_has_gbk_string(ain, "\xb0\xac\xcb\xb9\xbf\xa8\xc0\xd9\xb6\xfb")))
		|| (game_rance6 && (ain_has_gbk_string(ain, "\xba\xda\xcc\xe5")
			|| ain_has_gbk_string(ain, "\xd6\xd5\xc1\xcb")
			|| ain_has_gbk_string(ain, "\xa3\xcd\xa3\xd3\x20\xa5\xb4\xa5\xb7\xa5\xc3\xa5\xaf")))
		|| (game_galzoo && (ain_has_gbk_string(ain, "\xba\xda\xcc\xe5")
			|| ain_has_gbk_string(ain, "\xcd\xf2\xc0\xef\xb3\xa4\xb3\xc7")
			|| ain_has_gbk_string(ain, "\xd2\xd1\xb6\xc1\xce\xc4\xb1\xbe\xcc\xf8\xb9\xfd")))
		|| (game_tsumashibori && (ain_has_gbk_string(ain, "\xc6\xde\xa4\xb7\xa4\xdc\xa4\xea")
			|| ain_has_gbk_string(ain, "\xba\xda\xcc\xe5")))
		|| (game_ixseal && ain_get_function(ain, "\x82\x6f\x81\x51\x83\x41\x83\x4e\x83\x65\x83\x42\x83\x72\x83\x65\x83\x42\x8e\xc0\x8d\x73") < 0)
		|| ain_messages_look_gbk(ain);
	sys4_set_text_gbk(text_enc == AIN_ENC_GBK || (text_enc != AIN_ENC_SJIS && gbk_heuristics));
	if (game_evenicle_rance_cn && sys4_text_is_gbk && !sys4_source_is_utf8)
		apply_evenicle_rance_cn_display_literals(ain);
	if (game_rance03) {
		apply_rance03_message_layout(ain);
		apply_rance03_card_caption_layout(ain);
		apply_rance03_effect_sound_paths(ain);
		apply_rance03_skill_cooldown_layout(ain);
	}
	bool default_view_size = !config.view_width_set && !config.view_height_set
		&& config.view_width == 800 && config.view_height == 600;
	if (game_rance6 && config.view_width == 800 && config.view_height == 600) {
		config.view_width = 640;
		config.view_height = 480;
	}
	if (game_rance10 && config.view_width == 800 && config.view_height == 600) {
		// The original Rance X executable stores this outside AliceStart.ini.
		config.view_width = 1920;
		config.view_height = 1080;
	}
	if (((game_dohnadohna || game_evenicle2 || game_ixseal || game_haharanman
				|| game_okusama || game_night_remaster || game_maman)
				&& config.view_width == 800 && config.view_height == 600)
			|| ((game_evenicle || game_evenicle_rance || game_evenicle_gaiden || game_waidonyo)
				&& default_view_size)) {
		config.view_width = 1280;
		config.view_height = 720;
	}
	if (game_oyakorankan && ((config.view_width == 1280 && config.view_height == 720)
			|| default_view_size)) {
		// The Longinus AliceStart.CHS patch mistakenly sets 1280x720; the game assets are 1024x768.
		config.view_width = 1024;
		config.view_height = 768;
	}
	char *game_name = config.game_name
		? cjk2utf(config.game_name, 0, sys4_text_is_gbk || gbk_game_name)
		: xstrdup("");
	if (game_daibanchou || !strcmp(game_name, "大番長")) {
		game_daibanchou = true;
		apply_daibanchou_hacks(ain);
	} else if (!strcmp(game_name, "Rance 02")) {
		apply_rance02_hacks(ain);
	} else if (game_rance6 || !strncmp(game_name, "Rance6", 6)
			|| !strcmp(game_name, "ランス６") || !strcmp(game_name, "ランス6")
			|| !strncmp(game_name, "兰斯6", strlen("兰斯6"))
			|| !strncmp(game_name, "兰斯06", strlen("兰斯06"))) {
		game_rance6 = true;
		apply_rance6_hacks(ain);
	} else if (!strcmp(game_name, "ランス・クエスト") || !strcmp(game_name, "兰斯8")) {
		game_rance8 = true;
	} else if (!strcmp(game_name, "Rance Quest")) {
		game_rance8 = true;
		game_rance8_mg = true;
	} else if (!strcmp(game_name, "ＤＵＮＧＥＯＮＳ＆ＤＯＬＬＳ")) {
		game_dungeons_and_dolls = true;
	} else if (!strcmp(game_name, "ランス９")
			|| !strcmp(game_name, "Rance 9 - The Helmanian Revolution")
			|| !strcmp(game_name, "兰斯9")) {
		game_rance9 = true;
		apply_rance9_hacks(ain);
	}
	free(game_name);

	if (!game_rance8 && !game_dungeons_and_dolls && ain->version == 6
			&& ain_get_library(ain, "TapirEngine") >= 0
			&& ain_get_library(ain, "MonsterInfo") >= 0
			&& ain_get_library(ain, "GoatGUIEngine") >= 0) {
		game_rance8 = true;
	}
	if (!game_rance9 && ain->version == 6
			&& ain_get_library(ain, "SealEngine") >= 0
			&& ain_get_library(ain, "DrawMovie3") >= 0
			&& ain_get_struct(ain, "T_PartsNo") >= 0) {
		game_rance9 = true;
		apply_rance9_hacks(ain);
	}
	if ((game_rance6 && config.view_width == 800 && config.view_height == 600)
			|| (game_daibanchou && default_view_size)) {
		config.view_width = 640;
		config.view_height = 480;
	}
	if (((game_tsumamigui3 || game_tsumamigui3_jun || game_tsumamigui3_ntr || game_rance8 || game_escalayer_reboot)
				&& config.view_width == 800 && config.view_height == 600)
			|| ((game_rance01 || game_rance03 || game_haruurare) && default_view_size)) {
		config.view_width = 1024;
		config.view_height = 768;
	}
	if (game_rance9 && config.view_width == 800 && config.view_height == 600) {
		config.view_width = 1280;
		config.view_height = 720;
	}
	/* Leftover SJIS *display* strings in the CN edition. Exact matches only. */
	if (game_escalayer_reboot && sys4_text_is_gbk)
		escalayer_reboot_replace_cn_ui_text(ain->strings, ain->nr_strings);

	// XXX: we don't rely on game_name because mods change it
	if (ain_get_library(ain, "SengokuRanceFont") >= 0)
		game_rance7_mg = true;
	game_sengokurance = game_rance7_mg || (
		ain->version == 5 &&
		ain_get_struct(ain, "s\x83L\x83\x83\x83\x89") >= 0 &&
		ain_get_global(ain, "g_sBattle") >= 0 &&
		ain_get_global(ain, "\x81\x9f\x8b\xe0") >= 0
	);

	// Confirm3 DVD authentication in Shaman's Sanctuary computes expected protect-file
	// bytes using real Math.SetSeed/Math.Rand rather than stubbed Confirm3.GetRandom,
	// so Confirm3.File_ReadByte returning 0 always fails. Short-circuit the void
	// "ゲームディスク認証" check when both Confirm3 and CheckDVD_CheckProtectFile exist.
	if (ain_get_library(ain, "Confirm3") >= 0
			&& ain_get_function(ain, "CheckDVD_CheckProtectFile") > 0) {
		int auth_fno = ain_get_function(ain, "\x83\x51\x81\x5b\x83\x80\x83\x66\x83\x42\x83\x58\x83\x4e\x94\x46\x8f\xd8");
		if (auth_fno > 0) {
			struct ain_function *f = &ain->functions[auth_fno];
			if (f->return_type.data == AIN_VOID && f->address >= 0) {
				uint32_t base = (uint32_t)f->address;
				uint32_t end = find_func_end_addr(ain, auth_fno);
				if (end >= base && end - base >= 2 && (size_t)base + 2 <= ain->code_size) {
					struct buffer out;
					buffer_init(&out, NULL, 0);
					write_instruction0(&out, RETURN);
					memcpy(ain->code + base, out.buf, out.index);
					free(out.buf);
				}
			}
		}
	}
}
