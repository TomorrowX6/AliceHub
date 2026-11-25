#include "picker_art.h"

#include <setjmp.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <sys/stat.h>
#include <jpeglib.h>
#include <png.h>

enum { MAX_PIXELS = 2048 * 2048, MAX_FILE_BYTES = 8 * 1024 * 1024 };

static bool image_dimensions(unsigned w, unsigned h)
{
    return w && h && w <= 4096 && h <= 4096 && w <= MAX_PIXELS / h;
}

static uint16_t *scale_art(const unsigned char *data, unsigned w, unsigned h, unsigned channels,
                           unsigned width, unsigned height)
{
    uint16_t *art = malloc(width * height * sizeof(*art));
    if (!art)
        return NULL;
    unsigned source_w = w, source_h = h;
    if (w * height > h * width)
        source_w = (h * width + height - 1) / height;
    else
        source_h = (w * height + width - 1) / width;
    for (unsigned y = 0; y < height; y++) {
        for (unsigned x = 0; x < width; x++) {
            unsigned col = (w - source_w) / 2 + x * source_w / width;
            unsigned row = (h - source_h) / 2 + y * source_h / height;
            const unsigned char *p = data + (row * w + col) * channels;
            art[y * width + x] = (uint16_t)((p[0] >> 3) << 11 | (p[1] >> 2) << 5 | p[2] >> 3);
        }
    }
    return art;
}

static uint16_t *load_png(FILE *fp, int width, int height)
{
    png_image image = { .version = PNG_IMAGE_VERSION };
    uint16_t *art = NULL;
    if (png_image_begin_read_from_stdio(&image, fp) && image_dimensions(image.width, image.height)) {
        image.format = PNG_FORMAT_RGB;
        png_color background = { 0x18, 0x1b, 0x21 };
        unsigned char *data = malloc(PNG_IMAGE_SIZE(image));
        if (data && png_image_finish_read(&image, &background, data, 0, NULL))
            art = scale_art(data, image.width, image.height, 3, width, height);
        free(data);
    }
    png_image_free(&image);
    return art;
}

struct jpeg_art {
    struct jpeg_error_mgr error;
    struct jpeg_decompress_struct decoder;
    jmp_buf recover;
    unsigned char *data;
};

static void jpeg_art_error(j_common_ptr decoder)
{
    struct jpeg_art *state = decoder->client_data;
    longjmp(state->recover, 1);
}

static uint16_t *load_jpeg(FILE *fp, int width, int height)
{
    struct jpeg_art *state = calloc(1, sizeof(*state));
    if (!state)
        return NULL;
    struct jpeg_decompress_struct *decoder = &state->decoder;
    decoder->err = jpeg_std_error(&state->error);
    decoder->err->error_exit = jpeg_art_error;
    decoder->client_data = state;
    uint16_t *art = NULL;
    if (setjmp(state->recover))
        goto finish;
    jpeg_create_decompress(decoder);
    jpeg_stdio_src(decoder, fp);
    if (jpeg_read_header(decoder, TRUE) != JPEG_HEADER_OK ||
        !image_dimensions(decoder->image_width, decoder->image_height))
        goto finish;
    decoder->out_color_space = JCS_RGB;
    decoder->scale_denom = 1;
    while (decoder->scale_denom < 8 &&
           decoder->image_width / decoder->scale_denom >= (unsigned)width * 2 &&
           decoder->image_height / decoder->scale_denom >= (unsigned)height * 2)
        decoder->scale_denom *= 2;
    jpeg_start_decompress(decoder);
    size_t stride = decoder->output_width * decoder->output_components;
    state->data = malloc(stride * decoder->output_height);
    if (!state->data)
        goto finish;
    while (decoder->output_scanline < decoder->output_height) {
        JSAMPROW row = state->data + stride * decoder->output_scanline;
        jpeg_read_scanlines(decoder, &row, 1);
    }
    jpeg_finish_decompress(decoder);
    art = scale_art(state->data, decoder->output_width, decoder->output_height, 3, width, height);
finish:
    jpeg_destroy_decompress(decoder);
    free(state->data);
    free(state);
    return art;
}

uint16_t *picker_art_load(const char *path, int width, int height)
{
    struct stat st;
    if (width <= 0 || height <= 0 || width > 256 || height > 256 ||
        stat(path, &st) || !S_ISREG(st.st_mode) || st.st_size > MAX_FILE_BYTES)
        return NULL;
    FILE *fp = fopen(path, "rb");
    if (!fp)
        return NULL;
    uint16_t *art = NULL;
    int first = fgetc(fp), second = fgetc(fp);
    rewind(fp);
    if (first == 0x89 && second == 'P')
        art = load_png(fp, width, height);
    else if (first == 0xff && second == 0xd8)
        art = load_jpeg(fp, width, height);
    fclose(fp);
    return art;
}

uint16_t *picker_art_cover(const char *directory, int width, int height)
{
    static const char *const names[] = { "cover", "thumbnail", "folder", "poster", "banner", "icon" };
    static const char *const extensions[] = { "png", "jpg", "jpeg" };
    char path[768];
    for (size_t i = 0; i < sizeof(names) / sizeof(*names); i++) {
        for (size_t j = 0; j < sizeof(extensions) / sizeof(*extensions); j++) {
            if ((size_t)snprintf(path, sizeof(path), "%s/%s.%s", directory, names[i], extensions[j]) >= sizeof(path))
                return NULL;
            uint16_t *art = picker_art_load(path, width, height);
            if (art)
                return art;
        }
    }
    return NULL;
}
