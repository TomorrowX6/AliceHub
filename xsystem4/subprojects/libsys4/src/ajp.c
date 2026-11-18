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

#include <stdbool.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <turbojpeg.h>
#include <webp/decode.h>
#include "little_endian.h"
#include "system4.h"
#include "system4/cg.h"
#include "system4/pms.h"
#include "system4/webp.h"
#include "system4/zlib.h"

bool ajp_checkfmt(const uint8_t *data)
{
	if (data[0] != 'A' || data[1] != 'J' || data[2] != 'P' || data[3] != '\0')
		return false;
	return true;
}

static const uint8_t ajp_key[] = {
	0x5d, 0x91, 0xae, 0x87,
	0x4a, 0x56, 0x41, 0xcd,
	0x83, 0xec, 0x4c, 0x92,
	0xb5, 0xcb, 0x16, 0x34
};

static void ajp_decrypt(uint8_t *data, size_t size)
{
	for (size_t i = 0; i < 16 && i < size; i++) {
		data[i] ^= ajp_key[i];
	}
}

struct ajp_header {
	uint32_t width;
	uint32_t height;
	uint32_t jpeg_off;
	uint32_t jpeg_size;
	uint32_t mask_off;
	uint32_t mask_size;
};

static void ajp_extract_header(const uint8_t *data, struct ajp_header *ajp)
{
	ajp->width     = LittleEndian_getDW(data, 12);
	ajp->height    = LittleEndian_getDW(data, 16);
	ajp->jpeg_off  = LittleEndian_getDW(data, 20);
	ajp->jpeg_size = LittleEndian_getDW(data, 24);
	ajp->mask_off  = LittleEndian_getDW(data, 28);
	ajp->mask_size = LittleEndian_getDW(data, 32);
}

static void ajp_init_metrics(struct ajp_header *ajp, struct cg_metrics *dst)
{
	dst->x = dst->y = 0;
	dst->w = ajp->width;
	dst->h = ajp->height;
	dst->bpp = 24;
	dst->has_pixel = ajp->jpeg_size > 0;
	dst->has_alpha = ajp->mask_size > 0;
	dst->pixel_pitch = ajp->width * 3;
	dst->alpha_pitch = 1;
}

bool ajp_get_metrics(const uint8_t *data, size_t size, struct cg_metrics *dst)
{
	if (!data || size < 36 || !ajp_checkfmt(data)) return false;
	struct ajp_header header;
	ajp_extract_header(data, &header);
	if (!header.width || !header.height || header.width > 32768 || header.height > 32768)
		return false;
	ajp_init_metrics(&header, dst);
	return true;
}

static uint8_t *read_mask(uint8_t *pixels, uint8_t *mask_data, struct ajp_header *ajp)
{
	(void)pixels;
	if (ajp->mask_size == 0)
		return NULL;
	if (ajp->mask_size >= 4 && pms8_checkfmt(mask_data)) {
		return pms_extract_mask(mask_data, ajp->mask_size);
	} else if (ajp->mask_size >= 12 && webp_checkfmt(mask_data)) {
		int w = 0, h = 0;
		uint8_t *tmp = WebPDecodeRGBA(mask_data, ajp->mask_size, &w, &h);
		if (!tmp || w <= 0 || h <= 0 || (uint32_t)w != ajp->width || (uint32_t)h != ajp->height) {
			WARNING("Unexpected AJP mask size");
			WebPFree(tmp);
			return NULL;
		}
		// discard pixel data
		size_t npix = (size_t)ajp->width * (size_t)ajp->height;
		uint8_t *mask = xmalloc(npix);
		for (size_t i = 0; i < npix; i++) {
			mask[i] = tmp[i*4+3];
		}
		WebPFree(tmp);
		return mask;
	} else if (mask_data[0] == 0x78) {
		// compressed
		size_t uncompressed_size = (size_t)ajp->width * (size_t)ajp->height;
		uint8_t *mask = xmalloc(uncompressed_size);
		if (!zlib_decompress_exact(mask, uncompressed_size, mask_data, ajp->mask_size)) {
			WARNING("uncompress failed");
			free(mask);
			return NULL;
		}
		return mask;
	} else if (ajp->mask_size >= 4) {
		WARNING("Unsupported AJP mask format: %02x %02x %02x %02x",
				mask_data[0], mask_data[1], mask_data[2], mask_data[3]);
	}

	return NULL;
}

static uint8_t *load_mask(uint8_t *pixels, uint8_t *mask_data, struct ajp_header *ajp)
{
	size_t npix = (size_t)ajp->width * (size_t)ajp->height;
	uint8_t *mask = read_mask(pixels, mask_data, ajp);
	if (!mask) {
		mask = xmalloc(npix);
		memset(mask, 0xFF, npix);
	}

	uint8_t *out = xmalloc(npix * 4);
	for (size_t i = 0; i < npix; i++) {
		out[i*4+0] = pixels[i*3+0];
		out[i*4+1] = pixels[i*3+1];
		out[i*4+2] = pixels[i*3+2];
		out[i*4+3] = mask[i];
	}
	free(pixels);
	free(mask);
	return out;
}

void ajp_extract(const uint8_t *data, size_t size, struct cg *cg)
{
	uint8_t *buf = NULL, *jpeg_data = NULL, *mask_data = NULL;
	int width = 0, height = 0, subsamp = 0;
	struct ajp_header ajp;
	if (!data || size < 36 || !ajp_checkfmt(data))
		return;
	ajp_extract_header(data, &ajp);
	if (!ajp.width || !ajp.height || ajp.width > 32768 || ajp.height > 32768) {
		WARNING("Invalid AJP dimensions: %ux%u", ajp.width, ajp.height);
		return;
	}

	if (ajp.jpeg_off > size || ajp.jpeg_size > size - ajp.jpeg_off || ajp.jpeg_size < 2) {
		WARNING("AJP JPEG range invalid");
		return;
	}
	if (ajp.mask_off > size || ajp.mask_size > size - ajp.mask_off) {
		WARNING("AJP mask range invalid");
		return;
	}

	ajp_init_metrics(&ajp, &cg->metrics);

	jpeg_data = xmalloc(ajp.jpeg_size);
	mask_data = xmalloc(ajp.mask_size ? ajp.mask_size : 1);
	memcpy(jpeg_data, data + ajp.jpeg_off, ajp.jpeg_size);
	if (ajp.mask_size > 0)
		memcpy(mask_data, data + ajp.mask_off, ajp.mask_size);
	ajp_decrypt(jpeg_data, ajp.jpeg_size);
	ajp_decrypt(mask_data, ajp.mask_size);

	tjhandle decompressor = tjInitDecompress();
	if (!decompressor) {
		free(jpeg_data);
		free(mask_data);
		return;
	}
	if (tjDecompressHeader2(decompressor, jpeg_data, ajp.jpeg_size, &width, &height, &subsamp) < 0 ||
	    width <= 0 || height <= 0 || width > 32768 || height > 32768 ||
	    (uint32_t)width != ajp.width || (uint32_t)height != ajp.height) {
		WARNING("AJP JPEG header invalid or dimension mismatch (%dx%d vs. %ux%u)",
			width, height, ajp.width, ajp.height);
		goto cleanup;
	}

	buf = xmalloc((size_t)width * (size_t)height * 3);
	if (tjDecompress2(decompressor, jpeg_data, ajp.jpeg_size, buf, width, 0, height, TJPF_RGB, TJFLAG_FASTDCT) < 0) {
		WARNING("JPEG decompression failed: %s", tjGetErrorStr());
		free(buf);
		goto cleanup;
	}

	buf = load_mask(buf, mask_data, &ajp);

	cg->type = ALCG_AJP;
	cg->pixels = buf;

cleanup:
	free(jpeg_data);
	free(mask_data);
	tjDestroy(decompressor);
}
