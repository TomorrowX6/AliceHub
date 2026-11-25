#include "picker_art.h"
#include "hub/online_manifest.h"

#include <dirent.h>
#include <setjmp.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>
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

static uint16_t *icon_art(const unsigned char *data, unsigned w, unsigned h,
                         unsigned width, unsigned height)
{
    uint16_t *art = malloc(width * height * sizeof(*art));
    if (!art)
        return NULL;
    unsigned iw = width, ih = height;
    if (w * height > h * width)
        ih = (h * width + w - 1) / w;
    else
        iw = (w * height + h - 1) / h;
    if (w <= iw / 2 && h <= ih / 2) {
        iw = w * 2;
        ih = h * 2;
    }
    unsigned left = (width - iw) / 2, top = (height - ih) / 2;
    for (unsigned y = 0; y < height; ++y)
        for (unsigned x = 0; x < width; ++x) {
            uint16_t color = 0x18c4;
            if (x >= left && x < left + iw && y >= top && y < top + ih) {
                const unsigned char *p = data + ((y - top) * h / ih * w + (x - left) * w / iw) * 3;
                color = (uint16_t)((p[0] >> 3) << 11 | (p[1] >> 2) << 5 | p[2] >> 3);
            }
            art[y * width + x] = color;
        }
    return art;
}

static uint16_t *finish_png(png_image *image, int width, int height, bool icon)
{
    uint16_t *art = NULL;
    if (image_dimensions(image->width, image->height) &&
        (!icon || (image->width <= 256 && image->height <= 256))) {
        image->format = PNG_FORMAT_RGB;
        png_color background = { 0x18, 0x1b, 0x21 };
        unsigned char *data = malloc(PNG_IMAGE_SIZE(*image));
        if (data && png_image_finish_read(image, &background, data, 0, NULL))
            art = icon ? icon_art(data, image->width, image->height, width, height)
                       : scale_art(data, image->width, image->height, 3, width, height);
        free(data);
    }
    return art;
}

static uint16_t *load_png(FILE *fp, int width, int height)
{
    png_image image = { .version = PNG_IMAGE_VERSION };
    uint16_t *art = png_image_begin_read_from_stdio(&image, fp) ?
        finish_png(&image, width, height, false) : NULL;
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

static unsigned le16(const unsigned char *p)
{
    return p[0] | (unsigned)p[1] << 8;
}

static uint32_t le32(const unsigned char *p)
{
    return le16(p) | (uint32_t)le16(p + 2) << 16;
}

static bool within(size_t size, size_t offset, size_t length)
{
    return offset <= size && length <= size - offset;
}

static bool read_at(FILE *file, size_t size, size_t offset, void *data, size_t length)
{
    return within(size, offset, length) && !fseek(file, (long)offset, SEEK_SET) &&
           fread(data, 1, length, file) == length;
}

static uint16_t *dib_icon(const unsigned char *data, size_t size, int width, int height)
{
    if (size < 40 || le32(data) != 40 || le32(data + 16) || le16(data + 12) != 1)
        return NULL;
    unsigned w = le32(data + 4), double_h = le32(data + 8), bpp = le16(data + 14);
    if (!w || w > 256 || !double_h || double_h > 512 || (double_h & 1) ||
        (bpp != 1 && bpp != 4 && bpp != 8 && bpp != 24 && bpp != 32))
        return NULL;
    unsigned h = double_h / 2, colors = bpp <= 8 ? le32(data + 32) : 0;
    if (bpp <= 8 && !colors)
        colors = 1u << bpp;
    if (colors > 256 || (bpp <= 8 && colors > (1u << bpp)))
        return NULL;
    size_t base = 40 + colors * 4, stride = (w * bpp + 31) / 32 * 4;
    size_t mask = base + stride * h, mask_stride = (w + 31) / 32 * 4;
    if (mask > size || (!within(size, mask, mask_stride * h) && bpp != 32))
        return NULL;
    bool has_mask = within(size, mask, mask_stride * h), has_alpha = false;
    if (bpp == 32)
        for (unsigned y = 0; y < h && !has_alpha; ++y)
            for (unsigned x = 0; x < w; ++x)
                if (data[base + y * stride + x * 4 + 3])
                    has_alpha = true;
    unsigned char *rgb = malloc(w * h * 3);
    if (!rgb)
        return NULL;
    static const unsigned char bg[] = {0x18, 0x1b, 0x21};
    for (unsigned y = 0; y < h; ++y) {
        const unsigned char *row = data + base + (h - 1 - y) * stride;
        for (unsigned x = 0; x < w; ++x) {
            const unsigned char *p;
            if (bpp <= 8) {
                unsigned shift = 8 - bpp - (x * bpp % 8);
                unsigned index = (row[x * bpp / 8] >> shift) & ((1u << bpp) - 1);
                p = data + 40 + (index < colors ? index : 0) * 4;
            } else {
                p = row + x * (bpp / 8);
            }
            bool transparent = has_mask && (data[mask + (h - 1 - y) * mask_stride + x / 8] & (0x80 >> (x % 8)));
            unsigned alpha = has_alpha ? p[3] : transparent ? 0 : 255;
            for (unsigned c = 0; c < 3; ++c)
                rgb[(y * w + x) * 3 + c] = (p[2 - c] * alpha + bg[c] * (255 - alpha)) / 255;
        }
    }
    uint16_t *art = icon_art(rgb, w, h, width, height);
    free(rgb);
    return art;
}

static uint16_t *icon_payload(const unsigned char *data, size_t size, int width, int height)
{
    if (size >= 8 && !png_sig_cmp(data, 0, 8)) {
        png_image image = { .version = PNG_IMAGE_VERSION };
        uint16_t *art = png_image_begin_read_from_memory(&image, data, size) ?
            finish_png(&image, width, height, true) : NULL;
        png_image_free(&image);
        return art;
    }
    return dib_icon(data, size, width, height);
}

static uint16_t *load_ico(FILE *file, size_t size, int width, int height)
{
    unsigned char header[6], entries[64][16];
    if (!read_at(file, size, 0, header, sizeof(header)) || le16(header) || le16(header + 2) != 1)
        return NULL;
    unsigned count = le16(header + 4);
    if (!count || count > 64 || !read_at(file, size, 6, entries, count * 16))
        return NULL;
    while (true) {
        unsigned rank = 0, best = 0;
        for (unsigned i = 0; i < count; ++i) {
            const unsigned char *e = entries[i];
            unsigned area = (e[0] ? e[0] : 256) * (e[1] ? e[1] : 256);
            unsigned quality = area * 64 + (le16(e + 6) & 63);
            if (le32(e + 8) && quality > rank) {
                best = i;
                rank = quality;
            }
        }
        if (!rank)
            return NULL;
        unsigned char *entry = entries[best];
        size_t length = le32(entry + 8), offset = le32(entry + 12);
        memset(entry + 8, 0, 4);
        if (length > 512 * 1024 || offset < 6 + count * 16 || !within(size, offset, length))
            continue;
        unsigned char *data = malloc(length);
        if (!data)
            return NULL;
        uint16_t *art = read_at(file, size, offset, data, length) ? icon_payload(data, length, width, height) : NULL;
        free(data);
        if (art)
            return art;
    }
}

static unsigned resources(const unsigned char *data, size_t size, uint32_t dir)
{
    if (!within(size, dir, 16))
        return 0;
    unsigned count = le16(data + dir + 12) + le16(data + dir + 14);
    return count <= 256 && within(size, (size_t)dir + 16, count * 8) ? count : 0;
}

static uint16_t *pe_icons(const unsigned char *data, size_t size, uint32_t root,
                          uint32_t base_rva, int width, int height)
{
    if (root >= size)
        return NULL;
    const unsigned char *tree = data + root;
    size_t tree_size = size - root;
    unsigned types = resources(tree, tree_size, 0);
    for (unsigned i = 0; i < types; ++i) {
        const unsigned char *type = tree + 16 + i * 8;
        uint32_t group = le32(type + 4);
        if (le32(type) != 3 || !(group >> 31))
            continue;
        group &= 0x7fffffff;
        unsigned icons = resources(tree, tree_size, group), best_size = 0;
        uint16_t *best = NULL;
        for (unsigned j = 0; j < icons; ++j) {
            uint32_t lang = le32(tree + group + 20 + j * 8);
            if (!(lang >> 31))
                continue;
            lang &= 0x7fffffff;
            if (!resources(tree, tree_size, lang))
                continue;
            uint32_t leaf = le32(tree + lang + 20);
            if (!within(tree_size, leaf, 16))
                continue;
            uint32_t rva = le32(tree + leaf), length = le32(tree + leaf + 4);
            if (rva < base_rva || length <= best_size || length > 512 * 1024 ||
                !within(size, rva - base_rva, length))
                continue;
            uint16_t *art = icon_payload(data + (rva - base_rva), length, width, height);
            if (art) {
                free(best);
                best = art;
                best_size = length;
            }
        }
        return best;
    }
    return NULL;
}

static uint16_t *load_pe(FILE *file, size_t size, int width, int height)
{
    unsigned char dos[64], pe[24], optional[136], section[40];
    if (!read_at(file, size, 0, dos, sizeof(dos)) || le16(dos) != 0x5a4d)
        return NULL;
    size_t header = le32(dos + 60);
    if (!read_at(file, size, header, pe, sizeof(pe)) || le32(pe) != 0x4550)
        return NULL;
    size_t opt = header + sizeof(pe), opt_size = le16(pe + 20);
    unsigned count = le16(pe + 6);
    if (!count || count > 96 || !within(size, opt, opt_size) ||
        !read_at(file, size, opt, optional, opt_size < sizeof(optional) ? opt_size : sizeof(optional)))
        return NULL;
    unsigned magic = opt_size >= 2 ? le16(optional) : 0;
    unsigned dirs = magic == 0x10b ? 96 : magic == 0x20b ? 112 : 0;
    if (!dirs || opt_size < dirs + 24 || le32(optional + dirs - 4) < 3)
        return NULL;
    uint32_t root_rva = le32(optional + dirs + 16);
    if (!root_rva)
        return NULL;
    for (unsigned i = 0; i < count; ++i) {
        if (!read_at(file, size, opt + opt_size + i * 40, section, sizeof(section)))
            break;
        uint32_t addr = le32(section + 12), length = le32(section + 16), offset = le32(section + 20);
        if (root_rva < addr || root_rva - addr >= length || length > 4 * 1024 * 1024 ||
            !within(size, offset, length))
            continue;
        unsigned char *data = malloc(length);
        if (!data)
            return NULL;
        uint16_t *art = read_at(file, size, offset, data, length) ?
            pe_icons(data, length, root_rva - addr, addr, width, height) : NULL;
        free(data);
        return art;
    }
    return NULL;
}

static uint16_t *local_icon(const char *directory, bool exe, int width, int height)
{
    DIR *dir = opendir(directory);
    if (!dir)
        return NULL;
    char path[768];
    uint16_t *art = NULL;
    unsigned visited = 0, attempts = 0;
    struct dirent *e;
    while (!art && visited++ < 4096 && attempts < 16 && (e = readdir(dir))) {
        const char *ext = strrchr(e->d_name, '.');
        if (!ext || strcasecmp(ext, exe ? ".exe" : ".ico") ||
            (exe && (!strcasecmp(e->d_name, "OpenSaveFolder.exe") ||
                     !strcasecmp(e->d_name, "ResetConfig.exe") ||
                     !strcasecmp(e->d_name, "Uninstaller.exe"))))
            continue;
        if ((size_t)snprintf(path, sizeof(path), "%s/%s", directory, e->d_name) >= sizeof(path))
            continue;
        struct stat st;
        if (stat(path, &st) || !S_ISREG(st.st_mode) || st.st_size <= 0 || st.st_size > 64 * 1024 * 1024)
            continue;
        ++attempts;
        FILE *file = fopen(path, "rb");
        if (file) {
            art = (exe ? load_pe : load_ico)(file, st.st_size, width, height);
            fclose(file);
        }
    }
    closedir(dir);
    return art;
}

#ifdef ALICEHUB_BUNDLED_ARTWORK
typedef struct { const char *alias, *icon; } Alias;
static const Alias aliases[] = {
#include "artwork_index.inc"
};

static int compare_alias(const void *key, const void *entry)
{
    return strcmp(key, ((const Alias *)entry)->alias);
}
#endif

static bool catalog_path(const char *name, char path[192])
{
#ifdef ALICEHUB_BUNDLED_ARTWORK
    char key[128];
    size_t n = 0;
    for (const unsigned char *p = (const unsigned char *)name; *p; ++p) {
        if (n + 1 >= sizeof(key))
            return false;
        if (*p >= 'A' && *p <= 'Z')
            key[n++] = *p - 'A' + 'a';
        else if (*p >= 0x80 || (*p >= 'a' && *p <= 'z') || (*p >= '0' && *p <= '9'))
            key[n++] = *p;
    }
    key[n] = 0;
    const Alias *alias = bsearch(key, aliases, sizeof(aliases) / sizeof(*aliases),
                                 sizeof(*aliases), compare_alias);
    return alias && (size_t)snprintf(path, 192, "romfs:/artwork/%s.jpg", alias->icon) < 192;
#else
    (void)name;
    (void)path;
    return false;
#endif
}

uint16_t *picker_art_catalog(const char *name, int width, int height)
{
    char path[192];
    return catalog_path(name, path) ? picker_art_load(path, width, height) : NULL;
}

static void cache_online_icon(const char *source, const char *dest)
{
    enum { CACHE_LIMIT = 32 * 1024 };
    FILE *file = fopen(source, "rb");
    if (!file)
        return;
    unsigned char *bytes = malloc(CACHE_LIMIT);
    size_t size = bytes ? fread(bytes, 1, CACHE_LIMIT, file) : 0;
    bool complete = size && feof(file) && !ferror(file);
    fclose(file);
    if (complete && (file = fopen(dest, "wbx"))) {
        bool written = fwrite(bytes, 1, size, file) == size;
        if (fclose(file) || !written)
            unlink(dest);
    }
    free(bytes);
}

static uint16_t *online_icon(const char *directory, const char *id, int width, int height)
{
    char source[192], cache[768];
    if (!catalog_path(id, source))
        return NULL;
    bool cacheable = (size_t)snprintf(cache, sizeof(cache), "%s/" ONLINE_MARKER ".icon.jpg", directory)
                     < sizeof(cache);
    uint16_t *art = cacheable ? picker_art_load(cache, width, height) : NULL;
    if (art)
        return art;
    art = picker_art_load(source, width, height);
    if (art && cacheable)
        cache_online_icon(source, cache);
    return art;
}

uint16_t *picker_art_cover(const char *directory, int width, int height)
{
    static const char *const names[] = { "cover", "thumbnail", "folder", "poster", "banner", "icon" };
    static const char *const extensions[] = { "png", "jpg", "jpeg" };
    char path[768];
    if (width <= 0 || height <= 0 || width > 256 || height > 256)
        return NULL;
    for (size_t i = 0; i < sizeof(names) / sizeof(*names); i++) {
        for (size_t j = 0; j < sizeof(extensions) / sizeof(*extensions); j++) {
            if ((size_t)snprintf(path, sizeof(path), "%s/%s.%s", directory, names[i], extensions[j]) >= sizeof(path))
                return NULL;
            uint16_t *art = picker_art_load(path, width, height);
            if (art)
                return art;
        }
    }
    if ((size_t)snprintf(path, sizeof(path), "%s/" ONLINE_MARKER, directory) < sizeof(path)) {
        struct stat st;
        if (!stat(path, &st) && S_ISREG(st.st_mode) && st.st_size > 0 && st.st_size < 128) {
            FILE *file = fopen(path, "rb");
            if (file) {
                char id[128];
                size_t n = fread(id, 1, sizeof(id) - 1, file);
                fclose(file);
                id[n] = 0;
                uint16_t *art = online_icon(directory, id, width, height);
                if (art)
                    return art;
            }
        }
    }
    const char *name = strrchr(directory, '/');
    uint16_t *art = picker_art_catalog(name ? name + 1 : directory, width, height);
    if (!art)
        art = local_icon(directory, false, width, height);
    return art ? art : local_icon(directory, true, width, height);
}
