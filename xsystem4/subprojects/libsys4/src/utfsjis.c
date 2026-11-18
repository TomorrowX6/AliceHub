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
		if (SJIS_2BYTE(*src) && !(src + 1 < end && src[1] != '\0')) {
			c = sys4_text_is_gbk ? 0xfffd : '?';
			src++;
		} else {
			src = (const uint8_t*)sjis_char2unicode((const char*)src, &c);
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

		int c = cjk_encode_codepoint(u, gbk);
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

char *utf2sjis(const char *_src, size_t len) {
	return utf2cjk(_src, len, sys4_text_is_gbk);
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
