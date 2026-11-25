#include "local_import.h"

#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#define SECTOR 2048

typedef struct {
    FILE *file;
    uint64_t size;
    unsigned stride, offset;
    bool joliet;
} Image;

typedef struct {
    uint32_t block, size;
} Extent;

static uint32_t be32(const unsigned char *p)
{
    return (uint32_t)p[0] << 24 | (uint32_t)p[1] << 16 | (uint32_t)p[2] << 8 | p[3];
}

static bool block_read(const Image *image, uint64_t lba, void *buffer, size_t n)
{
    uint64_t position = lba * image->stride + image->offset;
    return position <= image->size && n <= image->size - position &&
           import_seek(image->file, position) && fread(buffer, 1, n, image->file) == n;
}

static bool extent(const unsigned char *record, Extent *e)
{
    uint32_t lba = import_le32(record + 2);
    e->block = lba + record[1];
    e->size = import_le32(record + 10);
    return e->block >= lba && lba == be32(record + 6) && e->size == be32(record + 14) &&
           !record[26] && !record[27] && !(record[25] & 128);
}

static bool descriptor(Image *image, Extent *root)
{
    static const unsigned formats[][2] = {{2048, 0}, {2352, 16}, {2352, 24}, {2448, 16}, {2448, 24}};
    for (size_t f = 0; f < sizeof(formats) / sizeof(*formats); ++f) {
        image->stride = formats[f][0];
        image->offset = formats[f][1];
        image->joliet = false;
        bool primary = false;
        Extent pvd, svd;
        for (uint64_t lba = 16; lba < 256; ++lba) {
            unsigned char data[SECTOR];
            if (!block_read(image, lba, data, sizeof(data)) || memcmp(data + 1, "CD001", 5) ||
                data[6] != 1)
                break;
            if (data[0] == 255) {
                if (!primary) break;
                *root = image->joliet ? svd : pvd;
                return true;
            }
            bool joliet = data[0] == 2 && data[88] == '%' && data[89] == '/' &&
                          (data[90] == '@' || data[90] == 'C' || data[90] == 'E');
            if (data[0] != 1 && !joliet) continue;
            const unsigned char *record = data + 156;
            Extent e;
            if (import_le16(data + 128) != SECTOR || data[130] != 8 || data[131] ||
                record[0] < 34 || !(record[25] & 2) || !extent(record, &e) || !e.size)
                break;
            if (joliet) {
                image->joliet = true;
                svd = e;
            } else {
                primary = true;
                pvd = e;
            }
        }
    }
    return false;
}

static bool decode(const Image *image, const unsigned char *data, size_t len, char name[HUB_NAME_SIZE])
{
    size_t n = 0;
    if (image->joliet) {
        if (len % 2) return false;
        for (size_t i = 0; i < len; i += 2) {
            unsigned c = data[i] << 8 | data[i + 1];
            if (!c || (c >= 0xd800 && c <= 0xdfff)) return false;
            if (n + 3 >= HUB_NAME_SIZE) return false;
            if (c < 0x80) {
                name[n++] = c;
            } else if (c < 0x800) {
                name[n++] = 0xc0 | (c >> 6);
                name[n++] = 0x80 | (c & 63);
            } else {
                name[n++] = 0xe0 | (c >> 12);
                name[n++] = 0x80 | ((c >> 6) & 63);
                name[n++] = 0x80 | (c & 63);
            }
        }
    } else {
        if (memchr(data, 0, len)) return false;
        memcpy(name, data, len);
        n = len;
    }
    name[n] = 0;
    char *version = strrchr(name, ';');
    if (version && version[1] && version[1 + strspn(version + 1, "0123456789")] == 0)
        *version = 0;
    n = strlen(name);
    if (n && name[n - 1] == '.') name[n - 1] = 0;
    return import_leaf(name);
}

static bool file_extract(const Image *image, Extent file, const char *path, const char *name,
                         const ImportCallbacks *cb)
{
    FILE *out = fopen(path, "wbx");
    if (!out) return false;
    uint64_t written = 0;
    bool ok = true;
    unsigned char buffer[32 * SECTOR];
    while (ok && written < file.size) {
        size_t capacity = image->stride == SECTOR ? sizeof(buffer) : SECTOR;
        size_t n = file.size - written < capacity ? file.size - written : capacity;
        ok = block_read(image, (uint64_t)file.block + written / SECTOR, buffer, n) &&
             fwrite(buffer, 1, n, out) == n && import_update(cb, name, written + n, file.size);
        written += n;
    }
    return !fclose(out) && ok;
}

static bool directory_extract(const Image *image, Extent dir, const char *dest, unsigned depth,
                              uint32_t parents[IMPORT_DEPTH + 1], const ImportCallbacks *cb)
{
    if (depth > IMPORT_DEPTH || !dir.size) return false;
    for (unsigned i = 0; i < depth; ++i)
        if (dir.block == parents[i]) return false;
    parents[depth] = dir.block;
    unsigned char sector[SECTOR];
    for (uint64_t pos = 0; pos < dir.size; pos += SECTOR) {
        size_t n = dir.size - pos < SECTOR ? dir.size - pos : SECTOR;
        if (!import_update(cb, "Reading disc directory", pos, dir.size) ||
            !block_read(image, (uint64_t)dir.block + pos / SECTOR, sector, n))
            return false;
        for (size_t p = 0; p < n && sector[p]; ) {
            const unsigned char *rec = sector + p;
            size_t len = rec[0];
            if (len < 34 || len > n - p || !rec[32] || rec[32] > len - 33)
                return false;
            p += len;
            if (rec[32] == 1 && rec[33] < 2) continue;
            char name[HUB_NAME_SIZE], path[HUB_PATH_SIZE];
            if (!decode(image, rec + 33, rec[32], name)) return false;
            bool directory = rec[25] & 2;
            if (import_skip(name, directory)) continue;
            Extent e;
            if (!extent(rec, &e) || !import_path(path, dest, name)) return false;
            if (!(directory ? import_mkdir(path) && directory_extract(image, e, path, depth + 1, parents, cb)
                            : file_extract(image, e, path, name, cb)))
                return false;
        }
    }
    return true;
}

bool import_iso(const char *source, const char *dest, const ImportCallbacks *cb)
{
    Image image = {.file = fopen(source, "rb")};
    if (!image.file) return false;
    struct stat st;
    Extent root;
    uint32_t parents[IMPORT_DEPTH + 1];
    bool ok = !fstat(fileno(image.file), &st) && st.st_size >= 0;
    if (ok) {
        image.size = st.st_size;
        ok = descriptor(&image, &root) && directory_extract(&image, root, dest, 0, parents, cb);
    }
    fclose(image.file);
    return ok;
}
