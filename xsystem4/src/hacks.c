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
bool game_evenicle2 = false;
bool game_rance03 = false;
bool game_dungeons_and_dolls = false;
bool game_escalayer_reboot = false;
bool game_galzoo = false;
bool game_ixseal = false;
bool game_tsumashibori = false;
bool game_tsumamigui3 = false;

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

static bool ain_messages_look_gbk(struct ain *ain)
{
	unsigned long gbk_pairs = 0;
	unsigned long sjis_kana_pairs = 0;
	int limit = ain->nr_messages < 8192 ? ain->nr_messages : 8192;
	for (int i = 0; i < limit; i++) {
		if (!ain->messages[i])
			continue;
		const unsigned char *s = (const unsigned char *)ain->messages[i]->text;
		if (!s)
			continue;
		for (size_t j = 0; s[j] && s[j + 1]; j++) {
			unsigned char b0 = s[j];
			unsigned char b1 = s[j + 1];
			if (b0 >= 0xb0 && b0 <= 0xd7 && b1 >= 0xa1 && b1 <= 0xfe) {
				gbk_pairs++;
				j++;
			} else if ((b0 == 0x82 && b1 >= 0x9f && b1 <= 0xf1)
					|| (b0 == 0x83 && b1 >= 0x40 && b1 <= 0x96)) {
				sjis_kana_pairs++;
				j++;
			} else if ((((b0 >= 0x81 && b0 <= 0x9f) || (b0 >= 0xe0 && b0 <= 0xfe))
						&& b1 >= 0x40 && b1 <= 0xfe)
					|| (b0 >= 0xa1 && b0 <= 0xaf && b1 >= 0xa1 && b1 <= 0xfe)) {
				j++;
			}
		}
	}
	return gbk_pairs >= 64 && gbk_pairs > sjis_kana_pairs * 2;
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
	game_evenicle2 = false;
	game_rance03 = false;
	game_dungeons_and_dolls = false;
	game_escalayer_reboot = false;
	game_galzoo = false;
	game_ixseal = false;
	game_tsumashibori = false;
	game_tsumamigui3 = false;

	game_rance10 = ain->version == 12
		&& ain_get_function(ain, "CardConstructProcessCacheFront@CreateOrganizationProcess") >= 0;
	game_dohnadohna = ain->version == 14
		&& ain_get_function(ain, "DohnaDohna@CreateResources") >= 0;
	game_evenicle = ain->version == 6
		&& ain_get_struct(ain, "CBigMap") >= 0
		&& ain_get_function(ain, "CMapPlayer@RunPathFindMove") >= 0;
	game_evenicle2 = ain->version == 14
		&& ain_get_function(ain, "C_MedicaMenu@0") >= 0;
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
	if (ain->version == 7 && config.ain_filename) {
		const char *ain_base = strrchr(config.ain_filename, '/');
		ain_base = ain_base ? ain_base + 1 : config.ain_filename;
		game_tsumamigui3 = !strcasecmp(ain_base, "Tsumamigui3.ain");
	}
	// The Chinese release uses CP936 for script text and archive entry names.
	// Also detect via GBK symbols in AIN so mods that change GameName in AliceStart.ini still enable GBK.
	// UTF-8 releases are converted to GBK when loaded.
	sys4_set_text_gbk(sys4_source_is_utf8
		|| (config.game_name && (strstr(config.game_name, "\xc0\xbc\xcb\xb9")
			|| strstr(config.game_name, "\xb6\xe0\xc4\xc8")
			|| strstr(config.game_name, "\xcf\xc4\xcd\xde")
			|| strstr(config.game_name, "\xd6\xd0\xce\xc4")
			|| strstr(config.game_name, "\xba\xba\xbb\xaf")
			|| strstr(config.game_name, "\x9e\x61\xbb\xaf")
			|| strstr(config.game_name, "\xb3\xac\xb0\xba\xc9\xf1\xc6\xef")
			|| strstr(config.game_name, "\xc6\xde\xa4\xb7\xa4\xdc\xa4\xea")
			|| strstr(config.game_name, "\xb4\xf3\xb7\xac\xe9L")
			|| strstr(config.game_name, "\xb4\xf3\xb7\xac\xb3\xa4")
			|| strstr(config.game_name, "\xb4\xf3\xb5\xdb\xb9\xfa")
			|| strstr(config.game_name, "\xd0\xa3\xc4\xda\xb3\xe0\xcf\xdf")))
		|| ain_get_global(ain, "\xc8\xab\xcc\xe5\xa3\xc3\xa3\xc7\xb1\xa3\xb3\xd6") >= 0
		|| ain_get_struct(ain, "\xa3\xd3\xb2\xbf\xb6\xd3\xd2\xbb\xc0\xc0") >= 0
		|| (game_rance10 && ain_get_global(ain, "\x91\x53\x91\xcc\x82\x62\x82\x66\x95\xdb\x8e\x9d") < 0)
		|| (game_dohnadohna && ain_get_function(ain, "\x91\x53\x83\x81\x83\x62\x83\x5a\x81\x5b\x83\x57\x98\x67\x91\x53\x8f\xc1\x8b\x8e") < 0)
		|| (game_evenicle && (ain_get_global(ain, "g_a\xa5\xad\xa5\xe3\xa5\xe9\xa5\xc7\xa9`\xa5\xbf") >= 0
			|| ain_get_function(ain, "\xcb\xf9\xb3\xd6\xbd\xf0\xd4O\xb6\xa8") >= 0))
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
		|| ain_messages_look_gbk(ain));
	if (game_rance03) {
		apply_rance03_message_layout(ain);
		apply_rance03_card_caption_layout(ain);
		apply_rance03_effect_sound_paths(ain);
		apply_rance03_skill_cooldown_layout(ain);
	}
	if (game_rance6 && config.view_width == 800 && config.view_height == 600) {
		config.view_width = 640;
		config.view_height = 480;
	}
	if (game_rance10 && config.view_width == 800 && config.view_height == 600) {
		// The original Rance X executable stores this outside AliceStart.ini.
		config.view_width = 1920;
		config.view_height = 1080;
	}
	if ((game_dohnadohna || game_evenicle2 || game_ixseal)
			&& config.view_width == 800 && config.view_height == 600) {
		config.view_width = 1280;
		config.view_height = 720;
	}
	char *game_name = config.game_name ? sjis2utf(config.game_name, 0) : xstrdup("");
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
	if (game_tsumamigui3 && config.view_width == 800 && config.view_height == 600) {
		config.view_width = 1024;
		config.view_height = 768;
	}
	if (game_rance8 && config.view_width == 800 && config.view_height == 600) {
		config.view_width = 1024;
		config.view_height = 768;
	}
	if (game_rance9 && config.view_width == 800 && config.view_height == 600) {
		config.view_width = 1280;
		config.view_height = 720;
	}
	if (game_escalayer_reboot && config.view_width == 800 && config.view_height == 600) {
		config.view_width = 1024;
		config.view_height = 768;
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
}
