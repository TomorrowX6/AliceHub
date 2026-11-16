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

#include "system4/utfsjis.h"
#include "system4/s2utbl.h"
#include "gbk_table.h"

bool sys4_text_is_gbk = false;
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

void sys4_set_text_gbk(bool enabled)
{
	sys4_text_is_gbk = enabled;
	if (enabled)
		ensure_unicode_to_gbk();
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

#include "system4.h"

char *sjis_char2unicode(const char *_src, int *dst)
{
	const uint8_t *src = (const uint8_t*)_src;
	if (*src <= 0x7f) {
		*dst = *src;
		return (char*)src + 1;
	}
	if (sys4_text_is_gbk) {
		if (*src == 0x80) { *dst = 0x20ac; return (char*)src + 1; }
		if (*src >= 0x81 && *src <= 0xfe && src[1] >= 0x40 && src[1] <= 0xfe) {
			*dst = gbk_to_unicode[*src - 0x81][src[1] - 0x40];
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

	*dst = s2u[*src - 0x80][*(src+1) - 0x40];
	return (char*)src + 2;
}

char *sjis2utf(const char *_src, size_t len) {
	if (!len)
		len = strlen(_src);
	const uint8_t *src = (uint8_t*)_src;
	uint8_t* dst = malloc(len * 3 + 1);
	uint8_t* dstp = dst;

	while (*src) {
		int c;
		src = (uint8_t*)sjis_char2unicode((const char*)src, &c);

		if (c <= 0x7f) {
			*dstp++ = c;
		} else if (c <= 0x7ff) {
			*dstp++ = 0xc0 | c >> 6;
			*dstp++ = 0x80 | (c & 0x3f);
		} else {
			*dstp++ = 0xe0 | c >> 12;
			*dstp++ = 0x80 | (c >> 6 & 0x3f);
			*dstp++ = 0x80 | (c & 0x3f);
		}
	}
	*dstp = '\0';
	return (char*)dst;
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
char *cjk2utf_strict_buf(const char *_src, size_t len, bool gbk, char *buf, size_t buf_cap, bool *valid_out)
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
					u = s2u[r][c];
				src += 2;
			}
		} else {
			if (b0 >= 0x81 && b0 <= 0xfe && src + 1 < end && src[1]) {
				uint8_t b1 = src[1];
				size_t r = (size_t)(b0 - 0x81);
				size_t c = (size_t)(b1 - 0x40);
				if (b1 >= 0x40 && r < sizeof(gbk_to_unicode) / sizeof(gbk_to_unicode[0]) &&
				    c < sizeof(gbk_to_unicode[0]) / sizeof(gbk_to_unicode[0][0]))
					u = gbk_to_unicode[r][c];
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

/* Decode SJIS (gbk=false) or GBK (gbk=true) to UTF-8 without mutating sys4_text_is_gbk.
 * Returns NULL and sets *valid_out=false if any byte sequence is invalid or unmapped. */
char *cjk2utf_strict(const char *_src, bool gbk, bool *valid_out)
{
	return cjk2utf_strict_buf(_src, _src ? strlen(_src) : 0, gbk, NULL, 0, valid_out);
}

int cjk_encode_codepoint(int u, bool gbk)
{
	if (u <= 0x7f)
		return u >= 0 ? u : 0;
	if (!gbk && u > 0xff60 && u <= 0xff9f)
		return u - 0xff60 + 0xa0;
	if (gbk && u == 0x20ac)
		return 0x80;
	if (u >= 65536)
		return 0;
	if (gbk) {
		ensure_unicode_to_gbk();
		return unicode_to_gbk[u];
	}
	ensure_unicode_to_sjis();
	return unicode_to_sjis_tbl[u];
}

char *utf2cjk(const char *_src, size_t len, bool gbk) {
	if (!len)
		len = strlen(_src);
	const uint8_t *src = (uint8_t*)_src;
	uint8_t* dst = malloc(len + 1);
	uint8_t* dstp = dst;

	while (*src) {
		if (*src <= 0x7f) {
			*dstp++ = *src++;
			continue;
		}

		int u;
		if (*src <= 0xdf) {
			u = (src[0] & 0x1f) << 6 | (src[1] & 0x3f);
			src += 2;
		} else if (*src <= 0xef) {
			u = (src[0] & 0xf) << 12 | (src[1] & 0x3f) << 6 | (src[2] & 0x3f);
			src += 3;
		} else {
			*dstp++ = '?';
			do src++; while ((*src & 0xc0) == 0x80);
			continue;
		}

		int c = cjk_encode_codepoint(u, gbk);
		if (c > 0xff) {
			*dstp++ = c >> 8;
			*dstp++ = c & 0xff;
		} else if (c) {
			*dstp++ = c & 0xff;
		} else {
			*dstp++ = '?';
		}
	}
	*dstp = '\0';
	return (char*)dst;
}

char *utf2sjis(const char *_src, size_t len) {
	return utf2cjk(_src, len, sys4_text_is_gbk);
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
