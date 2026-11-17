/* Copyright (C) 2026 AliceHub Contributors
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

#include <stdlib.h>
#include <string.h>

#include "system4.h"
#include "system4/cg.h"
#include "system4/little_endian.h"
#include "system4/tga.h"

#define TGA_HEADER_SIZE 18

bool tga_checkfmt(const uint8_t *data)
{
	if (!data)
		return false;
	if (data[1] != 0)
		return false;
	if (data[2] != 2 && data[2] != 10)
		return false;
	if (memcmp(data + 3, "\0\0\0\0\0", 5) != 0)
		return false;
	if (data[16] != 24 && data[16] != 32)
		return false;
	if ((data[17] & 0xc0) != 0)
		return false;
	uint16_t w = LittleEndian_getW(data, 12);
	uint16_t h = LittleEndian_getW(data, 14);
	return w > 0 && h > 0;
}

bool tga_get_metrics(const uint8_t *data, size_t size, struct cg_metrics *metrics)
{
	if (size < TGA_HEADER_SIZE) {
		WARNING("Data size too small for TGA header");
		return false;
	}
	metrics->x = LittleEndian_getW(data, 8);
	metrics->y = LittleEndian_getW(data, 10);
	metrics->w = LittleEndian_getW(data, 12);
	metrics->h = LittleEndian_getW(data, 14);
	metrics->bpp = data[16];
	metrics->has_pixel = true;
	metrics->has_alpha = (metrics->bpp == 32);
	metrics->pixel_pitch = metrics->w * (metrics->bpp / 8);
	metrics->alpha_pitch = 1;
	return true;
}

static inline void write_tga_pixel(uint8_t *dst, const uint8_t *src, int channels)
{
	dst[0] = src[2];
	dst[1] = src[1];
	dst[2] = src[0];
	dst[3] = (channels == 4) ? src[3] : 0xff;
}

void tga_extract(const uint8_t *data, size_t size, struct cg *cg)
{
	if (!tga_get_metrics(data, size, &cg->metrics))
		return;

	uint8_t id_len = data[0];
	uint8_t img_type = data[2];
	int w = cg->metrics.w;
	int h = cg->metrics.h;
	int channels = cg->metrics.bpp / 8;
	uint8_t desc = data[17];
	bool top_down = (desc & 0x20) != 0;
	bool right_to_left = (desc & 0x10) != 0;

	if (size < (size_t)TGA_HEADER_SIZE + id_len) {
		WARNING("TGA truncated in image ID");
		return;
	}

	const uint8_t *src = data + TGA_HEADER_SIZE + id_len;
	const uint8_t *end = data + size;
	size_t nr_pixels = (size_t)w * h;

	uint8_t *dst = xcalloc(1, nr_pixels * 4);

	if (img_type == 2) {
		if ((size_t)(end - src) < nr_pixels * channels) {
			WARNING("TGA pixel data truncated (have %zu, need %zu)",
				(size_t)(end - src), nr_pixels * channels);
			free(dst);
			return;
		}
		for (int y = 0; y < h; y++) {
			int dst_y = top_down ? y : (h - 1 - y);
			const uint8_t *row_src = src + (size_t)y * w * channels;
			uint8_t *row_dst = dst + (size_t)dst_y * w * 4;
			for (int x = 0; x < w; x++) {
				int dst_x = right_to_left ? (w - 1 - x) : x;
				write_tga_pixel(row_dst + (size_t)dst_x * 4,
					row_src + (size_t)x * channels, channels);
			}
		}
	} else if (img_type == 10) {
		size_t idx = 0;
		while (idx < nr_pixels) {
			if (src >= end) {
				WARNING("TGA RLE data truncated");
				free(dst);
				return;
			}
			uint8_t pkt = *src++;
			size_t count = (pkt & 0x7f) + 1;
			if (idx + count > nr_pixels)
				count = nr_pixels - idx;
			if (pkt & 0x80) {
				if ((size_t)(end - src) < (size_t)channels) {
					WARNING("TGA RLE packet truncated");
					free(dst);
					return;
				}
				for (size_t k = 0; k < count; k++, idx++) {
					int y = (int)(idx / w);
					int x = (int)(idx % w);
					int dst_y = top_down ? y : (h - 1 - y);
					int dst_x = right_to_left ? (w - 1 - x) : x;
					write_tga_pixel(dst + ((size_t)dst_y * w + dst_x) * 4, src, channels);
				}
				src += channels;
			} else {
				if ((size_t)(end - src) < count * channels) {
					WARNING("TGA RLE raw packet truncated");
					free(dst);
					return;
				}
				for (size_t k = 0; k < count; k++, idx++) {
					int y = (int)(idx / w);
					int x = (int)(idx % w);
					int dst_y = top_down ? y : (h - 1 - y);
					int dst_x = right_to_left ? (w - 1 - x) : x;
					write_tga_pixel(dst + ((size_t)dst_y * w + dst_x) * 4, src, channels);
					src += channels;
				}
			}
		}
	} else {
		free(dst);
		return;
	}

	cg->type = ALCG_TGA;
	cg->pixels = dst;
}
