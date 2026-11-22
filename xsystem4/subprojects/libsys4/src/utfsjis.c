/*
 * utfsjis.c -- utf-8/sjis related function
 *
 * Copyright (C) 1997 Yutaka OIWA <oiwa@is.s.u-tokyo.ac.jp>
 *
 * written for Satoshi KURAMOCHI's "eplaymidi"
 *                                   <satoshi@ueda.info.waseda.ac.jp>
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "system4.h"
#include "system4/file.h"
#include "system4/utfsjis.h"
#include "system4/s2utbl.h"
#include "system4/string.h"
#include "gbk_table.h"

bool sys4_text_is_gbk = false;
bool sys4_source_is_utf8 = false;

/* Symbols used by UTF-8 releases that GBK lacks, parked in the GBK user-defined area. */
static const struct { uint16_t gbk; uint16_t u; } gbk_extras[] = {
	{ 0xaaa1, 0x266a }, // ♪
	{ 0xaaa2, 0x30fb }, // ・
	{ 0xaaa3, 0x2764 }, // ❤
	{ 0xaaa4, 0x2665 }, // ♥
};

static int gbk_lookup(uint8_t lead, uint8_t trail)
{
	int u = gbk_to_unicode[lead - 0x81][trail - 0x40];
	if (u)
		return u;
	uint16_t code = (uint16_t)((lead << 8) | trail);
	for (size_t i = 0; i < sizeof(gbk_extras) / sizeof(gbk_extras[0]); i++) {
		if (gbk_extras[i].gbk == code)
			return gbk_extras[i].u;
	}
	return 0;
}
static uint16_t unicode_to_gbk[65536];
static uint16_t unicode_to_sjis_tbl[65536];

static void ensure_unicode_to_gbk(void)
{
	if (unicode_to_gbk[0x4e00])
		return;
	for (int lead = 0x81; lead <= 0xfe; lead++) {
		for (int trail = 0x40; trail <= 0xfe; trail++) {
			int code = gbk_to_unicode[lead - 0x81][trail - 0x40];
			if (code && code < 65536)
				unicode_to_gbk[code] = (uint16_t)((lead << 8) | trail);
		}
	}
	for (size_t i = 0; i < sizeof(gbk_extras) / sizeof(gbk_extras[0]); i++) {
		if (!unicode_to_gbk[gbk_extras[i].u])
			unicode_to_gbk[gbk_extras[i].u] = gbk_extras[i].gbk;
	}
}

static void ensure_unicode_to_sjis(void)
{
	if (unicode_to_sjis_tbl[0x4e00])
		return;
	for (int b1 = 0x81; b1 <= 0xfc; b1++) {
		if (b1 >= 0xa0 && b1 <= 0xdf)
			continue;
		for (int b2 = 0x40; b2 <= 0xfc; b2++) {
			int u = s2u[b1 - 0x80][b2 - 0x40];
			if (u > 0 && u < 65536 && !unicode_to_sjis_tbl[u])
				unicode_to_sjis_tbl[u] = (uint16_t)((b1 << 8) | b2);
		}
	}
}

#define MAI_WORD_TABLE_SIZE 65536
#define MAI_NR_ROWS 128
#define MAI_NR_COLS 192

static bool sys4_mai_active = false;
static uint16_t mai_s2u[MAI_NR_ROWS][MAI_NR_COLS];
static uint16_t mai_u2s[MAI_WORD_TABLE_SIZE];

void sys4_reset_mai_word_table(void)
{
	sys4_mai_active = false;
	memset(mai_s2u, 0, sizeof(mai_s2u));
	memset(mai_u2s, 0, sizeof(mai_u2s));
}

bool sys4_has_mai_word_table(void)
{
	return sys4_mai_active;
}

static bool mai_valid_slot_lead(int hi)
{
	return (hi >= 0x81 && hi <= 0x9f) || (hi >= 0xe0 && hi <= 0xef);
}

static bool mai_valid_slot_trail(int lo)
{
	return (lo >= 0x40 && lo <= 0x7e) || (lo >= 0x80 && lo <= 0x9e) || (lo >= 0xa0 && lo <= 0xfc);
}

/* Build mai.dll-compatible custom Shift-JIS <-> UCS-2 mapping tables from
 * a 65536-byte word.mai BMP bitmap without mutating the base CP932 tables. */
bool sys4_load_mai_word_table_mem(const void *data, size_t size)
{
	if (!data || size != MAI_WORD_TABLE_SIZE)
		return false;

	const uint8_t *raw = (const uint8_t *)data;
	bool has_entry = false;
	for (int u = 0x80; u < MAI_WORD_TABLE_SIZE; u++) {
		if (raw[u]) {
			has_entry = true;
			break;
		}
	}
	if (!has_entry)
		return false;

	uint8_t *needed = xmalloc(MAI_WORD_TABLE_SIZE);
	uint8_t *free_slots = xcalloc(1, MAI_WORD_TABLE_SIZE);
	uint16_t *base_u2s = xcalloc(MAI_WORD_TABLE_SIZE, sizeof(uint16_t));
	for (int u = 0; u < MAI_WORD_TABLE_SIZE; u++)
		needed[u] = raw[u] ? 1 : 0;

	sys4_reset_mai_word_table();

	for (int c = 0xa1; c <= 0xdf; c++)
		base_u2s[0xff60 + (c - 0xa0)] = (uint16_t)c;

	/* Mirror mai.dll: retain CP932 in 0x81..0xEC, clear 0xED00..0xFCFF,
	 * and drop s2utbl.h's 0x30FB placeholder from unmapped cells. */
	for (int b1 = 0x81; b1 <= 0xec; b1++) {
		if (b1 >= 0xa0 && b1 <= 0xdf)
			continue;
		for (int b2 = 0x40; b2 <= 0xfc; b2++) {
			if (b2 == 0x7f)
				continue;
			uint16_t u = (uint16_t)s2u[b1 - 0x80][b2 - 0x40];
			uint16_t sjis = (uint16_t)((b1 << 8) | b2);
			if (u == 0x30fb && sjis != 0x8145)
				continue;
			mai_s2u[b1 - 0x80][b2 - 0x40] = u;
			if (u >= 0x80)
				base_u2s[u] = sjis;
		}
	}

	for (int hi = 0x88; hi <= 0xef; hi++) {
		if (!mai_valid_slot_lead(hi))
			continue;
		for (int lo = 0x40; lo <= 0xfc; lo++) {
			if (!mai_valid_slot_trail(lo))
				continue;
			int slot = (hi << 8) | lo;
			if (slot >= 0x889f)
				free_slots[slot] = 1;
		}
	}

	/* Pass 1: preserve existing CP932 codes required by word.mai. */
	for (int u = 0x80; u < MAI_WORD_TABLE_SIZE; u++) {
		if (needed[u] && base_u2s[u]) {
			uint16_t s = base_u2s[u];
			mai_u2s[u] = s;
			free_slots[s] = 0;
			needed[u] = 0;
		}
	}

	/* Pass 2: assign unmapped word.mai characters into unused 2-byte slots. */
	int slot = 0x889f;
	for (int u = 0x80; u < MAI_WORD_TABLE_SIZE; u++) {
		if (!needed[u])
			continue;
		while (slot < MAI_WORD_TABLE_SIZE && !free_slots[slot])
			slot++;
		if (slot >= MAI_WORD_TABLE_SIZE) {
			free(base_u2s);
			free(free_slots);
			free(needed);
			sys4_reset_mai_word_table();
			return false;
		}
		mai_u2s[u] = (uint16_t)slot;
		mai_s2u[(slot >> 8) - 0x80][(slot & 0xff) - 0x40] = (uint16_t)u;
		free_slots[slot] = 0;
		slot++;
	}

	/* Pass 3: populate reverse lookup for unallocated surviving CP932 chars. */
	for (int c = 0xa1; c <= 0xdf; c++) {
		uint16_t u = (uint16_t)(0xff60 + (c - 0xa0));
		if (!mai_u2s[u])
			mai_u2s[u] = (uint16_t)c;
	}
	for (int b1 = 0x81; b1 <= 0xef; b1++) {
		if (b1 >= 0xa0 && b1 <= 0xdf)
			continue;
		for (int b2 = 0x40; b2 <= 0xfc; b2++) {
			if (b2 == 0x7f)
				continue;
			uint16_t u = mai_s2u[b1 - 0x80][b2 - 0x40];
			if (u >= 0x80 && !mai_u2s[u])
				mai_u2s[u] = (uint16_t)((b1 << 8) | b2);
		}
	}

	free(base_u2s);
	free(free_slots);
	free(needed);
	sys4_mai_active = true;
	return true;
}

bool sys4_load_mai_word_table(const char *path)
{
	if (!path || !*path)
		return false;
	size_t size = 0;
	void *buf = file_read(path, &size);
	if (!buf)
		return false;
	bool ok = sys4_load_mai_word_table_mem(buf, size);
	free(buf);
	return ok;
}

/* Load a Longinus CHS.TBL remap table (up to 65536 little-endian uint16 entries
 * mapping 2-byte Shift-JIS codepoints -> GBK byte pairs) into the custom Shift-JIS
 * render table without mutating base CP932 archive/path conversions. */
bool sys4_load_chs_tbl_mem(const void *data, size_t size)
{
	if (!data || size < 2 || (size % 2) != 0 || size > 131072)
		return false;

	const uint8_t *raw = (const uint8_t *)data;
	size_t count = size / 2;

	sys4_reset_mai_word_table();

	// Seed with base CP932 table so unmapped punctuation/kana remain intact.
	for (int b1 = 0x81; b1 <= 0xfc; b1++) {
		if (b1 >= 0xa0 && b1 <= 0xdf)
			continue;
		for (int b2 = 0x40; b2 <= 0xfc; b2++) {
			if (b2 == 0x7f)
				continue;
			uint16_t u = (uint16_t)s2u[b1 - 0x80][b2 - 0x40];
			uint16_t sjis = (uint16_t)((b1 << 8) | b2);
			if (u == 0x30fb && sjis != 0x8145)
				continue;
			mai_s2u[b1 - 0x80][b2 - 0x40] = u;
		}
	}

	size_t mapped_count = 0;
	for (int b1 = 0x81; b1 <= 0xfc; b1++) {
		if (b1 >= 0xa0 && b1 <= 0xdf)
			continue;
		for (int b2 = 0x40; b2 <= 0xfc; b2++) {
			if (b2 == 0x7f)
				continue;
			size_t code = ((size_t)b1 << 8) | (size_t)b2;
			if (code >= count)
				continue;
			uint16_t mapped = (uint16_t)(raw[code * 2] | ((uint16_t)raw[code * 2 + 1] << 8));
			// Skip empty slots and '?' (0x003f) fallback placeholders so CP932 symbols
			// absent from GBK (e.g. ・ 0x8145, ♪ 0x81f4) remain legible.
			if (!mapped || mapped == 0x003f)
				continue;
			int u = 0;
			if (mapped <= 0x7f) {
				u = mapped;
			} else {
				uint8_t hi = (uint8_t)(mapped >> 8);
				uint8_t lo = (uint8_t)(mapped & 0xff);
				if (hi >= 0x81 && hi <= 0xfe && lo >= 0x40 && lo <= 0xfe)
					u = gbk_lookup(hi, lo);
			}
			if (u > 0 && u < 65536) {
				mai_s2u[b1 - 0x80][b2 - 0x40] = (uint16_t)u;
				if (!mai_u2s[u])
					mai_u2s[u] = (uint16_t)code;
				mapped_count++;
			}
		}
	}

	if (!mapped_count) {
		sys4_reset_mai_word_table();
		return false;
	}

	for (int c = 0xa1; c <= 0xdf; c++) {
		uint16_t u = (uint16_t)(0xff60 + (c - 0xa0));
		if (!mai_u2s[u])
			mai_u2s[u] = (uint16_t)c;
	}
	for (int b1 = 0x81; b1 <= 0xfc; b1++) {
		if (b1 >= 0xa0 && b1 <= 0xdf)
			continue;
		for (int b2 = 0x40; b2 <= 0xfc; b2++) {
			if (b2 == 0x7f)
				continue;
			uint16_t u = mai_s2u[b1 - 0x80][b2 - 0x40];
			if (u >= 0x80 && !mai_u2s[u])
				mai_u2s[u] = (uint16_t)((b1 << 8) | b2);
		}
	}

	sys4_mai_active = true;
	return true;
}

bool sys4_load_chs_tbl(const char *path)
{
	if (!path || !*path)
		return false;
	size_t size = 0;
	void *buf = file_read(path, &size);
	if (!buf)
		return false;
	bool ok = sys4_load_chs_tbl_mem(buf, size);
	free(buf);
	return ok;
}

struct text_dict_entry {
	char *src;
	char *dst;
};

static struct text_dict_entry *text_dict_entries = NULL;
static size_t text_dict_count = 0;

static int text_dict_cmp(const void *a, const void *b)
{
	const struct text_dict_entry *ea = (const struct text_dict_entry *)a;
	const struct text_dict_entry *eb = (const struct text_dict_entry *)b;
	return strcmp(ea->src, eb->src);
}

void sys4_reset_text_dict(void)
{
	for (size_t i = 0; i < text_dict_count; i++) {
		free(text_dict_entries[i].src);
		free(text_dict_entries[i].dst);
	}
	free(text_dict_entries);
	text_dict_entries = NULL;
	text_dict_count = 0;
}

/* Load an XOR-0x49 UI/EX text dictionary (e.g. NightRemasterDict.dat), stored
 * as 3-line records: Shift-JIS source line, GBK target line, separator line. */
bool sys4_load_text_dict_mem(const void *data, size_t size)
{
	sys4_reset_text_dict();
	if (!data || size < 4 || size > 4 * 1024 * 1024)
		return false;

	const uint8_t *raw = (const uint8_t *)data;
	char *dec = xmalloc(size + 1);
	size_t nl_count = 0;
	for (size_t i = 0; i < size; i++) {
		char c = (char)(raw[i] ^ 0x49);
		dec[i] = c;
		if (c == '\n')
			nl_count++;
	}
	dec[size] = '\0';
	if (nl_count < 1) {
		free(dec);
		return false;
	}

	char **lines = xmalloc((nl_count + 1) * sizeof(*lines));
	size_t nr_lines = 0;
	char *p = dec;
	while (true) {
		char *nl = strchr(p, '\n');
		if (nl)
			*nl = '\0';
		size_t len = strlen(p);
		if (len > 0 && p[len - 1] == '\r')
			p[len - 1] = '\0';
		lines[nr_lines++] = p;
		if (!nl)
			break;
		p = nl + 1;
	}

	size_t cap = (nr_lines / 3) + 1;
	struct text_dict_entry *entries = xmalloc(cap * sizeof(*entries));
	size_t count = 0;
	for (size_t i = 0; i + 1 < nr_lines; i += 3) {
		const char *src = lines[i];
		const char *dst = lines[i + 1];
		if (!src[0] || !dst[0] || !strcmp(src, dst))
			continue;
		entries[count].src = xstrdup(src);
		entries[count].dst = xstrdup(dst);
		count++;
	}
	free(lines);
	free(dec);

	if (!count) {
		free(entries);
		return false;
	}

	qsort(entries, count, sizeof(*entries), text_dict_cmp);
	size_t unique = 0;
	for (size_t i = 0; i < count; i++) {
		if (unique > 0 && !strcmp(entries[unique - 1].src, entries[i].src)) {
			free(entries[i].src);
			free(entries[i].dst);
			continue;
		}
		if (unique != i)
			entries[unique] = entries[i];
		unique++;
	}

	text_dict_entries = entries;
	text_dict_count = unique;
	return true;
}

bool sys4_load_text_dict(const char *path)
{
	if (!path || !*path)
		return false;
	size_t size = 0;
	void *buf = file_read(path, &size);
	if (!buf)
		return false;
	bool ok = sys4_load_text_dict_mem(buf, size);
	free(buf);
	return ok;
}

const char *sys4_text_dict_lookup(const char *src)
{
	if (!text_dict_count || !src || !*src)
		return NULL;
	size_t lo = 0, hi = text_dict_count;
	while (lo < hi) {
		size_t mid = lo + (hi - lo) / 2;
		int cmp = strcmp(text_dict_entries[mid].src, src);
		if (cmp == 0)
			return text_dict_entries[mid].dst;
		if (cmp < 0)
			lo = mid + 1;
		else
			hi = mid;
	}
	return NULL;
}

char *sys4_text_dict_translate_lines(const char *src)
{
	if (!text_dict_count || !src || !*src)
		return NULL;
	const char *whole = sys4_text_dict_lookup(src);
	if (whole)
		return xstrdup(whole);
	if (!strchr(src, '\n'))
		return NULL;

	// Translate multi-line strings line-by-line only if at least one line matches.
	char *copy = xstrdup(src);
	bool any = false;
	size_t total = 0;
	size_t nlines = 0;
	for (char *ln = copy; ln;) {
		char *nxt = strchr(ln, '\n');
		if (nxt)
			*nxt = '\0';
		const char *rep = sys4_text_dict_lookup(ln);
		if (rep)
			any = true;
		total += strlen(rep ? rep : ln) + (nxt ? 1 : 0);
		nlines++;
		ln = nxt ? nxt + 1 : NULL;
	}
	if (!any) {
		free(copy);
		return NULL;
	}

	char *out = xmalloc(total + 1);
	char *dst = out;
	char *ln = copy;
	for (size_t i = 0; i < nlines; i++) {
		const char *rep = sys4_text_dict_lookup(ln);
		const char *piece = rep ? rep : ln;
		size_t len = strlen(piece);
		memcpy(dst, piece, len);
		dst += len;
		if (i + 1 < nlines)
			*dst++ = '\n';
		ln += strlen(ln) + 1;
	}
	*dst = '\0';
	free(copy);
	return out;
}

void sys4_set_text_gbk(bool enabled)
{
	sys4_text_is_gbk = enabled;
	if (enabled)
		ensure_unicode_to_gbk();
}

void sys4_set_source_utf8(bool enabled)
{
	sys4_source_is_utf8 = enabled;
}

bool sys4_bytes_look_utf8(const uint8_t *s, size_t len)
{
	if (len >= 3 && s[0] == 0xef && s[1] == 0xbb && s[2] == 0xbf)
		return true;
	bool multibyte = false;
	for (size_t i = 0; i < len;) {
		uint8_t b = s[i];
		size_t n = b < 0x80 ? 1 : (b & 0xe0) == 0xc0 ? 2 : (b & 0xf0) == 0xe0 ? 3 : (b & 0xf8) == 0xf0 ? 4 : 0;
		if (!n || i + n > len)
			return false;
		for (size_t j = 1; j < n; j++) {
			if ((s[i + j] & 0xc0) != 0x80)
				return false;
		}
		if (n >= 3)
			multibyte = true;
		i += n;
	}
	return multibyte;
}

// Convert a character-index to a byte-index for a given SJIS string.
int sjis_index(const char *_src, int index)
{
	const uint8_t *src = (uint8_t*)_src;
	int i, c;
	for (i = 0, c = 0; c < index && src[i]; i++, c++) {
		if (SJIS_2BYTE(src[i])) {
			i++;
			// check for invalid sjis data
			if (!src[i])
				return -1;
		}
	}
	return src[i] ? i : -1;
}

static char *sjis_char2unicode_impl(const char *_src, int *dst, bool use_mai)
{
	const uint8_t *src = (const uint8_t*)_src;
	if (*src <= 0x7f) {
		*dst = *src;
		return (char*)src + 1;
	}
	if (!use_mai && sys4_text_is_gbk) {
		if (*src == 0x80) { *dst = 0x20ac; return (char*)src + 1; }
		if (*src >= 0x81 && *src <= 0xfe && src[1] >= 0x40 && src[1] <= 0xfe) {
			*dst = gbk_lookup(src[0], src[1]);
			if (!*dst) *dst = 0xfffd;
			return (char*)src + 2;
		}
		*dst = 0xfffd;
		return (char*)src + 1;
	}

	if (*src >= 0xa0 && *src <= 0xdf) {
		*dst = 0xff60 + *src - 0xa0;
		return (char*)src + 1;
	}

	// guard against invalid byte sequence
	int c = *(src+1);
	if (c < 0x40 || c == 0x7f || c > 0xfc) {
		*dst = '?';
		return (char*)src + 1;
	}

	if (use_mai) {
		uint16_t u = mai_s2u[*src - 0x80][c - 0x40];
		*dst = u ? u : 0xfffd;
	} else {
		*dst = s2u[*src - 0x80][c - 0x40];
	}
	return (char*)src + 2;
}

char *sjis_char2unicode(const char *_src, int *dst)
{
	return sjis_char2unicode_impl(_src, dst, false);
}

char *mai_sjis_char2unicode(const char *_src, int *dst)
{
	return sjis_char2unicode_impl(_src, dst, sys4_mai_active);
}

static char *sjis2utf_impl(const char *_src, size_t len, bool use_mai) {
	if (!_src)
		return NULL;
	const uint8_t *src = (const uint8_t*)_src;
	if (!len)
		len = strlen(_src);
	const uint8_t *end = src + len;
	uint8_t* dst = xmalloc(len * 3 + 1);
	uint8_t* dstp = dst;

	while (src < end && *src) {
		int c;
		bool is_2b = use_mai ? (((*src) & 0xe0) == 0x80 || ((*src) & 0xe0) == 0xe0) : SJIS_2BYTE(*src);
		if (is_2b && !(src + 1 < end && src[1] != '\0')) {
			c = (!use_mai && sys4_text_is_gbk) ? 0xfffd : '?';
			src++;
		} else {
			src = (const uint8_t*)sjis_char2unicode_impl((const char*)src, &c, use_mai);
		}

		if (c <= 0x7f) {
			*dstp++ = c;
		} else if (c <= 0x7ff) {
			*dstp++ = 0xc0 | (c >> 6);
			*dstp++ = 0x80 | (c & 0x3f);
		} else {
			*dstp++ = 0xe0 | (c >> 12);
			*dstp++ = 0x80 | ((c >> 6) & 0x3f);
			*dstp++ = 0x80 | (c & 0x3f);
		}
	}
	*dstp = '\0';
	return (char*)dst;
}

char *sjis2utf(const char *_src, size_t len) {
	return sjis2utf_impl(_src, len, false);
}

char *mai_sjis2utf(const char *_src, size_t len) {
	return sjis2utf_impl(_src, len, sys4_mai_active);
}

static void encode_utf8_bmp(int u, uint8_t **dstp)
{
	if (u <= 0x7f) {
		*(*dstp)++ = (uint8_t)u;
	} else if (u <= 0x7ff) {
		*(*dstp)++ = (uint8_t)(0xc0 | (u >> 6));
		*(*dstp)++ = (uint8_t)(0x80 | (u & 0x3f));
	} else {
		*(*dstp)++ = (uint8_t)(0xe0 | (u >> 12));
		*(*dstp)++ = (uint8_t)(0x80 | ((u >> 6) & 0x3f));
		*(*dstp)++ = (uint8_t)(0x80 | (u & 0x3f));
	}
}

/* Decode SJIS (gbk=false) or GBK (gbk=true) to UTF-8 without mutating sys4_text_is_gbk.
 * Writes into caller-supplied `buf` when `len * 3 + 1 <= buf_cap`, otherwise allocates.
 * Returns NULL and sets *valid_out=false if any byte sequence is invalid or unmapped. */
static char *cjk2utf_strict_buf_impl(const char *_src, size_t len, bool gbk, char *buf, size_t buf_cap, bool *valid_out, bool use_mai)
{
	if (valid_out)
		*valid_out = false;
	if (!_src)
		return NULL;
	const uint8_t *src = (const uint8_t *)_src;
	const uint8_t *end = src + len;
	size_t need = len * 3 + 1;
	uint8_t *dst = (buf && need <= buf_cap) ? (uint8_t *)buf : malloc(need);
	if (!dst)
		return NULL;
	uint8_t *dstp = dst;

	while (src < end && *src) {
		uint8_t b0 = *src;
		if (b0 <= 0x7f) {
			*dstp++ = b0;
			src++;
			continue;
		}
		int u = 0;
		if (!gbk) {
			if (b0 >= 0xa1 && b0 <= 0xdf) {
				u = 0xff60 + (b0 - 0xa0);
				src++;
			} else if (b0 >= 0x81 && b0 != 0xa0 && ((b0 & 0xe0) == 0x80 || (b0 & 0xe0) == 0xe0) && src + 1 < end && src[1]) {
				uint8_t b1 = src[1];
				size_t r = (size_t)(b0 - 0x80);
				size_t c = (size_t)(b1 - 0x40);
				if (b1 >= 0x40 && r < sizeof(s2u) / sizeof(s2u[0]) && c < sizeof(s2u_80) / sizeof(s2u_80[0]))
					u = use_mai ? mai_s2u[r][c] : s2u[r][c];
				src += 2;
			}
		} else {
			if (b0 >= 0x81 && b0 <= 0xfe && src + 1 < end && src[1]) {
				uint8_t b1 = src[1];
				size_t r = (size_t)(b0 - 0x81);
				size_t c = (size_t)(b1 - 0x40);
				if (b1 >= 0x40 && r < sizeof(gbk_to_unicode) / sizeof(gbk_to_unicode[0]) &&
				    c < sizeof(gbk_to_unicode[0]) / sizeof(gbk_to_unicode[0][0]))
					u = gbk_lookup(b0, b1);
				src += 2;
			}
		}
		if (u <= 0) {
			if (dst != (uint8_t *)buf)
				free(dst);
			return NULL;
		}
		encode_utf8_bmp(u, &dstp);
	}
	*dstp = '\0';
	if (valid_out)
		*valid_out = true;
	return (char *)dst;
}

char *cjk2utf_strict_buf(const char *_src, size_t len, bool gbk, char *buf, size_t buf_cap, bool *valid_out)
{
	return cjk2utf_strict_buf_impl(_src, len, gbk, buf, buf_cap, valid_out, false);
}

/* Decode SJIS (gbk=false) or GBK (gbk=true) to UTF-8 without mutating sys4_text_is_gbk.
 * Returns NULL and sets *valid_out=false if any byte sequence is invalid or unmapped. */
char *cjk2utf_strict(const char *_src, bool gbk, bool *valid_out)
{
	return cjk2utf_strict_buf(_src, _src ? strlen(_src) : 0, gbk, NULL, 0, valid_out);
}

char *mai_cjk2utf_strict(const char *_src, bool *valid_out)
{
	if (!sys4_mai_active)
		return cjk2utf_strict(_src, sys4_text_is_gbk, valid_out);
	return cjk2utf_strict_buf_impl(_src, _src ? strlen(_src) : 0, false, NULL, 0, valid_out, true);
}

static int cjk_encode_codepoint_impl(int u, bool gbk, bool use_mai)
{
	if (u <= 0x7f)
		return u >= 0 ? u : 0;
	if (!gbk && u > 0xff60 && u <= 0xff9f)
		return u - 0xff60 + 0xa0;
	if (gbk && u == 0x20ac)
		return 0x80;
	if (u >= 65536)
		return 0;
	if (use_mai && !gbk)
		return mai_u2s[u];
	if (gbk) {
		ensure_unicode_to_gbk();
		return unicode_to_gbk[u];
	}
	ensure_unicode_to_sjis();
	return unicode_to_sjis_tbl[u];
}

int cjk_encode_codepoint(int u, bool gbk)
{
	return cjk_encode_codepoint_impl(u, gbk, false);
}

static char *utf2cjk_impl(const char *_src, size_t len, bool gbk, bool use_mai) {
	if (!_src)
		return NULL;
	const uint8_t *src = (const uint8_t*)_src;
	if (!len)
		len = strlen(_src);
	const uint8_t *end = src + len;
	uint8_t* dst = xmalloc(len * 2 + 1);
	uint8_t* dstp = dst;

	while (src < end && *src) {
		if (*src <= 0x7f) {
			*dstp++ = *src++;
			continue;
		}

		int u;
		if ((*src & 0xe0) == 0xc0) {
			if (src + 1 >= end || (src[1] & 0xc0) != 0x80) {
				*dstp++ = '?';
				src++;
				continue;
			}
			u = ((src[0] & 0x1f) << 6) | (src[1] & 0x3f);
			src += 2;
		} else if ((*src & 0xf0) == 0xe0) {
			if (src + 2 >= end || (src[1] & 0xc0) != 0x80 || (src[2] & 0xc0) != 0x80) {
				*dstp++ = '?';
				src++;
				while (src < end && (*src & 0xc0) == 0x80)
					src++;
				continue;
			}
			u = ((src[0] & 0x0f) << 12) | ((src[1] & 0x3f) << 6) | (src[2] & 0x3f);
			src += 3;
		} else {
			*dstp++ = '?';
			src++;
			while (src < end && (*src & 0xc0) == 0x80)
				src++;
			continue;
		}

		int c = cjk_encode_codepoint_impl(u, gbk, use_mai);
		if (c > 0xff) {
			*dstp++ = (uint8_t)(c >> 8);
			*dstp++ = (uint8_t)(c & 0xff);
		} else if (c) {
			*dstp++ = (uint8_t)(c & 0xff);
		} else {
			*dstp++ = '?';
		}
	}
	*dstp = '\0';
	return (char*)dst;
}

char *utf2cjk(const char *_src, size_t len, bool gbk) {
	return utf2cjk_impl(_src, len, gbk, false);
}

/* Game data from UTF-8 releases is converted to GBK on load so it runs on the GBK text path. */
char *sys4_source_strdup(const char *src)
{
	if (!sys4_source_is_utf8)
		return strdup(src);
	return utf2cjk_impl(src, strlen(src), sys4_text_is_gbk, false);
}

struct string *sys4_source_make_string(const char *src, size_t len)
{
	if (!sys4_source_is_utf8 || !len)
		return make_string(src, len);
	char *conv = utf2cjk_impl(src, len, sys4_text_is_gbk, false);
	struct string *s = make_string(conv, strlen(conv));
	free(conv);
	return s;
}

char *utf2sjis(const char *_src, size_t len) {
	return utf2cjk_impl(_src, len, sys4_text_is_gbk, false);
}

char *mai_utf2sjis(const char *_src, size_t len) {
	if (!sys4_mai_active)
		return utf2sjis(_src, len);
	return utf2cjk_impl(_src, len, false, true);
}

static bool seg_is_strict_sjis_non_gb2312(const uint8_t *src, size_t len)
{
	if (!src || !len)
		return false;
	size_t cjk_pairs = 0;
	size_t non_gb2312 = 0;
	for (size_t i = 0; i < len && src[i];) {
		uint8_t b0 = src[i];
		if (b0 <= 0x7f) {
			i++;
			continue;
		}
		// Halfwidth katakana in CP932 (0xA1..0xDF) cannot disambiguate from GB2312 lead bytes.
		if (b0 >= 0xa0 && b0 <= 0xdf)
			return false;
		if (i + 1 >= len || !src[i + 1])
			return false;
		uint8_t b1 = src[i + 1];
		if (b1 < 0x40 || b1 == 0x7f || b1 > 0xfc)
			return false;
		size_t r = (size_t)(b0 - 0x80);
		size_t c = (size_t)(b1 - 0x40);
		if (r >= sizeof(s2u) / sizeof(s2u[0]) || c >= sizeof(s2u_80) / sizeof(s2u_80[0]) || s2u[r][c] <= 0)
			return false;
		cjk_pairs++;
		if (b0 < 0xa1 || b1 < 0xa1)
			non_gb2312++;
		i += 2;
	}
	return cjk_pairs > 0 && non_gb2312 == cjk_pairs;
}

static char *decode_cjk_raw_seg(const uint8_t *seg, size_t seg_len, bool use_sjis)
{
	bool ok = false;
	char *u = cjk2utf_strict_buf((const char *)seg, seg_len, !use_sjis, NULL, 0, &ok);
	if (u && ok)
		return u;
	free(u);
	char *tmp = xmalloc(seg_len + 1);
	memcpy(tmp, seg, seg_len);
	tmp[seg_len] = '\0';
	char *fallback = sjis2utf(tmp, seg_len);
	free(tmp);
	return fallback;
}

/* Decode VM/INI path string when sys4_text_is_gbk is enabled:
 * per path segment, if raw bytes are valid extended-SJIS (never GB2312 and no halfwidth kana),
 * decode as CP932 instead of GBK so filenames like 戦国ランス_Sys.ASD / ランス・クエスト.ain
 * do not turn into 愴崙儔儞僗_Sys.ASD / 儔儞僗丒僋僄僗僩.ain. */
char *sjis_filename2utf(const char *_src, size_t len)
{
	if (!_src)
		return NULL;
	if (!len)
		len = strlen(_src);
	if (!sys4_text_is_gbk)
		return sjis2utf(_src, len);

	const uint8_t *src = (const uint8_t *)_src;
	size_t out_cap = len * 3 + 1;
	char *out = xmalloc(out_cap);
	size_t out_len = 0;

	size_t i = 0;
	while (i < len && src[i]) {
		size_t seg_start = i;
		while (i < len && src[i] && src[i] != '/' && src[i] != '\\') {
			if ((sys4_text_is_gbk ? (src[i] >= 0x81 && src[i] <= 0xfe) : (((src[i]) & 0xe0) == 0x80 || ((src[i]) & 0xe0) == 0xe0)) &&
			    i + 1 < len && src[i + 1]) {
				i += 2;
			} else {
				i++;
			}
		}
		size_t seg_len = i - seg_start;
		if (seg_len > 0) {
			bool use_sjis = seg_is_strict_sjis_non_gb2312(src + seg_start, seg_len);
			char *seg_utf = decode_cjk_raw_seg(src + seg_start, seg_len, use_sjis);
			size_t slen = strlen(seg_utf);
			if (out_len + slen + 2 > out_cap) {
				out_cap = out_len + slen + 64;
				out = xrealloc(out, out_cap);
			}
			memcpy(out + out_len, seg_utf, slen);
			out_len += slen;
			free(seg_utf);
		}
		if (i < len && (src[i] == '/' || src[i] == '\\')) {
			if (out_len + 2 > out_cap) {
				out_cap += 64;
				out = xrealloc(out, out_cap);
			}
			out[out_len++] = (char)src[i++];
		}
	}
	out[out_len] = '\0';
	return out;
}

static bool encode_utf8_to_gbk_strict(const char *seg, size_t seg_len, uint8_t **out_buf, size_t *out_len)
{
	uint8_t *dst = xmalloc(seg_len + 1);
	size_t pos = 0;
	const uint8_t *p = (const uint8_t *)seg;
	const uint8_t *end = p + seg_len;
	while (p < end && *p) {
		if (*p <= 0x7f) {
			dst[pos++] = *p++;
			continue;
		}
		int u = 0;
		if (*p <= 0xdf && p + 1 < end) {
			if ((*p & 0xe0) != 0xc0 || (p[1] & 0xc0) != 0x80) { free(dst); return false; }
			u = ((p[0] & 0x1f) << 6) | (p[1] & 0x3f);
			p += 2;
		} else if (*p <= 0xef && p + 2 < end) {
			if ((*p & 0xf0) != 0xe0 || (p[1] & 0xc0) != 0x80 || (p[2] & 0xc0) != 0x80) { free(dst); return false; }
			u = ((p[0] & 0x0f) << 12) | ((p[1] & 0x3f) << 6) | (p[2] & 0x3f);
			p += 3;
		} else {
			free(dst);
			return false;
		}
		int c = cjk_encode_codepoint(u, true);
		if (c <= 0xff) {
			free(dst);
			return false;
		}
		dst[pos++] = (uint8_t)(c >> 8);
		dst[pos++] = (uint8_t)(c & 0xff);
	}
	dst[pos] = '\0';
	*out_buf = dst;
	*out_len = pos;
	return true;
}

/* Repair a UTF-8 path/filename that contains CP932-as-GBK mojibake
 * (e.g. "愴崙儔儞僗GA.ald" -> "戦国ランスGA.ald"). Returns newly allocated string if repaired, else NULL. */
char *cjk_repair_sjis_as_gbk_utf8(const char *utf)
{
	if (!utf)
		return NULL;
	bool has_non_ascii = false;
	for (const uint8_t *p = (const uint8_t *)utf; *p; p++) {
		if (*p > 0x7f) {
			has_non_ascii = true;
			break;
		}
	}
	if (!has_non_ascii)
		return NULL;

	size_t len = strlen(utf);
	size_t out_cap = len * 2 + 1;
	char *out = xmalloc(out_cap);
	size_t out_len = 0;
	bool changed = false;

	size_t i = 0;
	while (i < len) {
		size_t seg_start = i;
		while (i < len && utf[i] != '/' && utf[i] != '\\')
			i++;
		size_t seg_len = i - seg_start;
		if (seg_len > 0) {
			bool seg_repaired = false;
			uint8_t *gbk_bytes = NULL;
			size_t gbk_len = 0;
			if (encode_utf8_to_gbk_strict(utf + seg_start, seg_len, &gbk_bytes, &gbk_len) &&
			    seg_is_strict_sjis_non_gb2312(gbk_bytes, gbk_len)) {
				bool ok = false;
				char *sjis_utf = cjk2utf_strict_buf((const char *)gbk_bytes, gbk_len, false, NULL, 0, &ok);
				if (sjis_utf && ok) {
					size_t rlen = strlen(sjis_utf);
					if (out_len + rlen + 2 > out_cap) {
						out_cap = out_len + rlen + 64;
						out = xrealloc(out, out_cap);
					}
					memcpy(out + out_len, sjis_utf, rlen);
					out_len += rlen;
					changed = true;
					seg_repaired = true;
				}
				free(sjis_utf);
			}
			free(gbk_bytes);
			if (!seg_repaired) {
				if (out_len + seg_len + 2 > out_cap) {
					out_cap = out_len + seg_len + 64;
					out = xrealloc(out, out_cap);
				}
				memcpy(out + out_len, utf + seg_start, seg_len);
				out_len += seg_len;
			}
		}
		if (i < len && (utf[i] == '/' || utf[i] == '\\')) {
			if (out_len + 2 > out_cap) {
				out_cap += 64;
				out = xrealloc(out, out_cap);
			}
			out[out_len++] = utf[i++];
		}
	}
	if (!changed) {
		free(out);
		return NULL;
	}
	out[out_len] = '\0';
	return out;
}

static bool has_unencoded_qmark(const char *utf, const char *cjk)
{
	if (!utf || !cjk)
		return false;
	size_t uq = 0, cq = 0;
	for (const char *p = utf; *p; p++) {
		if (*p == '?') uq++;
	}
	for (const char *p = cjk; *p; p++) {
		if (*p == '?') cq++;
	}
	return cq > uq;
}

char *utf2filename_cjk(const char *src, size_t len)
{
	if (!src)
		return NULL;
	if (!len)
		len = strlen(src);
	char *primary = utf2cjk(src, len, sys4_text_is_gbk);
	if (sys4_text_is_gbk && has_unencoded_qmark(src, primary)) {
		char *sjis = utf2cjk(src, len, false);
		if (sjis && !has_unencoded_qmark(src, sjis)) {
			free(primary);
			return sjis;
		}
		free(sjis);
	}
	return primary;
}

/* src 内に半角カナもしくはASCII文字があるかどうか */
bool sjis_has_hankaku(const char *_src) {
	const uint8_t *src = (uint8_t*)_src;
	while(*src) {
		if (SJIS_2BYTE(*src) && src[1]) {
			src++;
		} else {
			return true;
		}
		src++;
	}
	return false;
}

/* src 内に 全角文字があるかどうか */
bool sjis_has_zenkaku(const char *_src) {
	const uint8_t *src = (uint8_t*)_src;
	while(*src) {
		if (SJIS_2BYTE(*src) && src[1]) {
			return true;
		}
		src++;
	}
	return false;
}

/* src 中の文字数を数える 全角文字も１文字 */
int sjis_count_char(const char *_src) {
	const uint8_t *src = (uint8_t*)_src;
	int c = 0;

	while(*src) {
		if (SJIS_2BYTE(*src) && src[1]) {
			src++;
		}
		c++; src++;
	}
	return c;
}

// Replaces lowercase letters with uppercase letters and slashes with backslashes.
void sjis_normalize_path(char *_src) {
	for (uint8_t *src = (uint8_t*)_src; *src; src++) {
		if (SJIS_2BYTE(*src) && src[1]) {
			src++;
		} else if ('a' <= *src && *src <= 'z') {
			*src -= 'a' - 'A';
		} else if (*src == '/') {
			*src = '\\';
		}
	}
}

char *cjk_gbk_zenkaku_to_sjis_buf(const char *src, size_t len, char *buf, size_t cap, bool *changed_out)
{
	if (changed_out)
		*changed_out = false;
	if (!src)
		return NULL;
	char *out = (buf && len < cap) ? buf : xmalloc(len + 1);
	bool changed = false;
	size_t i = 0;
	while (i < len) {
		uint8_t b = (uint8_t)src[i];
		if (b < 0x80) {
			out[i] = (char)b;
			i++;
			continue;
		}
		if (i + 1 < len) {
			uint8_t b2 = (uint8_t)src[i + 1];
			if (b == 0xa3 && b2 >= 0xb0 && b2 <= 0xb9) {
				out[i] = (char)0x82;
				out[i + 1] = (char)(0x4f + (b2 - 0xb0));
				changed = true;
				i += 2;
				continue;
			}
			if (b == 0xa3 && b2 == 0xad) {
				out[i] = (char)0x81;
				out[i + 1] = (char)0x7c;
				changed = true;
				i += 2;
				continue;
			}
			if (b == 0xa3 && b2 == 0xae) {
				out[i] = (char)0x81;
				out[i + 1] = (char)0x44;
				changed = true;
				i += 2;
				continue;
			}
			if (b == 0xa1 && b2 == 0xa1) {
				out[i] = (char)0x81;
				out[i + 1] = (char)0x40;
				changed = true;
				i += 2;
				continue;
			}
			bool sjis_lead = ((b >= 0x81 && b <= 0x9f) || (b >= 0xe0 && b <= 0xfc));
			bool sjis_trail = ((b2 >= 0x40 && b2 <= 0x7e) || (b2 >= 0x80 && b2 <= 0xfc));
			bool gbk_2byte = (sys4_text_is_gbk && b >= 0xa1 && b <= 0xdf && b2 >= 0xa1 && b2 <= 0xfe);
			if ((sjis_lead && sjis_trail) || gbk_2byte) {
				out[i] = (char)b;
				out[i + 1] = (char)b2;
				i += 2;
				continue;
			}
		}
		out[i] = (char)b;
		i++;
	}
	out[len] = '\0';
	if (changed_out)
		*changed_out = changed;
	return out;
}

#ifdef _WIN32
#include <windows.h>

wchar_t *utf8_to_wchar(const char *str)
{
	int nr_wchars = MultiByteToWideChar(CP_UTF8, 0, str, -1, NULL, 0);
	wchar_t *wstr = xmalloc(nr_wchars * sizeof(wchar_t));
	MultiByteToWideChar(CP_UTF8, 0, str, -1, wstr, nr_wchars);
	return wstr;
}

char *wchar_to_utf8(const wchar_t *wstr)
{
	int nr_chars = WideCharToMultiByte(CP_UTF8, 0, wstr, -1, NULL, 0, NULL, NULL);
	char *str = xmalloc(nr_chars);
	WideCharToMultiByte(CP_UTF8, 0, wstr, -1, str, nr_chars, NULL, NULL);
	return str;
}
#endif
