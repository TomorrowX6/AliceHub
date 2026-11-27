#include "local_import.h"

#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <zlib.h>

#define ZIP_CHUNK 32768

static uint64_t le64(const unsigned char *p)
{
    return import_le32(p) | (uint64_t)import_le32(p + 4) << 32;
}

static bool at(FILE *f, uint64_t pos, void *buf, size_t n)
{
    return import_seek(f, pos) && fread(buf, 1, n, f) == n;
}

static bool central_directory(FILE *f, uint64_t size, uint64_t *start, uint64_t *end,
                              uint64_t *count)
{
    size_t n = size < 65557 ? size : 65557;
    if (n < 22)
        return false;
    unsigned char *tail = malloc(n);
    if (!tail)
        return false;
    bool ok = false;
    if (!at(f, size - n, tail, n))
        goto done;
    for (size_t i = n - 22 + 1; i-- > 0;) {
        const unsigned char *p = tail + i;
        if (import_le32(p) != 0x06054b50 || i + 22 + import_le16(p + 20) != n)
            continue;
        uint64_t limit = size - n + i, length = import_le32(p + 12);
        *start = import_le32(p + 16);
        *count = import_le16(p + 10);
        if (import_le16(p + 4) || import_le16(p + 6) || import_le16(p + 8) != *count)
            break;
        if (*count == UINT16_MAX || *start == UINT32_MAX || length == UINT32_MAX) {
            unsigned char locator[20], zip64[56];
            if (limit < sizeof(locator) || !at(f, limit - sizeof(locator), locator, sizeof(locator)) ||
                import_le32(locator) != 0x07064b50 || import_le32(locator + 4) ||
                import_le32(locator + 16) != 1)
                break;
            uint64_t record = le64(locator + 8);
            if (record > limit - sizeof(locator) || limit - sizeof(locator) - record < sizeof(zip64) ||
                !at(f, record, zip64, sizeof(zip64)) || import_le32(zip64) != 0x06064b50 ||
                import_le32(zip64 + 16) || import_le32(zip64 + 20) ||
                le64(zip64 + 24) != le64(zip64 + 32))
                break;
            limit = record;
            *count = le64(zip64 + 32);
            length = le64(zip64 + 40);
            *start = le64(zip64 + 48);
        }
        if (*start > limit || length > limit - *start || *count > length / 46)
            break;
        *end = *start + length;
        ok = true;
        break;
    }
done:
    free(tail);
    return ok;
}

static bool zip64_entry(FILE *f, size_t length, uint64_t *size, uint64_t *packed, uint64_t *offset)
{
    if (*size != UINT32_MAX && *packed != UINT32_MAX && *offset != UINT32_MAX)
        return true;
    unsigned char field[4], value[8];
    while (length >= 4 && fread(field, 1, 4, f) == 4) {
        length -= 4;
        size_t n = import_le16(field + 2);
        if (n > length)
            return false;
        if (import_le16(field) == 1) {
            uint64_t *values[] = {size, packed, offset};
            for (size_t i = 0; i < 3; ++i) {
                if (*values[i] == UINT32_MAX) {
                    if (n < 8 || fread(value, 1, 8, f) != 8)
                        return false;
                    *values[i] = le64(value);
                    n -= 8;
                }
            }
            return true;
        }
        if (fseek(f, (long)n, SEEK_CUR))
            return false;
        length -= n;
    }
    return false;
}

static bool zip_path(const char *dest, char *name, bool directory, char path[HUB_PATH_SIZE],
                     bool *skip)
{
    size_t depth = 0;
    *skip = false;
    for (char *p = name; ; ) {
        char *slash = strchr(p, '/');
        if (slash)
            *slash = 0;
        bool last = !slash || !slash[1];
        if (++depth > IMPORT_DEPTH || !import_leaf(p))
            return false;
        *skip |= import_skip(p, !last || directory);
        if (slash) *slash = '/';
        if (last) break;
        p = slash + 1;
    }
    if (*skip)
        return true;
    if (directory) {
        size_t len = strlen(name);
        if (len && name[len - 1] == '/')
            name[len - 1] = 0;
    }
    if (!import_path(path, dest, name))
        return false;
    for (char *p = path + strlen(dest) + 1; (p = strchr(p, '/')); ++p) {
        *p = 0;
        bool ok = import_mkdir(path);
        *p = '/';
        if (!ok) return false;
    }
    return !directory || import_mkdir(path);
}

static bool extract(FILE *f, const char *path, uint64_t packed, uint64_t size,
                    unsigned method, uint32_t crc, unsigned char *buffer,
                    const ImportCallbacks *cb, uint64_t offset, uint64_t total)
{
    FILE *out = fopen(path, "wbx");
    if (!out)
        return false;
    unsigned char *output = buffer + ZIP_CHUNK;
    z_stream stream = {0};
    uint32_t actual = crc32(0, Z_NULL, 0);
    uint64_t written = 0;
    bool ready = method == 8 && inflateInit2(&stream, -MAX_WBITS) == Z_OK;
    bool ok = false;
    int status = Z_OK;
    if (method == 8 && !ready)
        goto done;
    while (packed || stream.avail_in || (method == 8 && status != Z_STREAM_END)) {
        if (!stream.avail_in && packed) {
            size_t n = packed < ZIP_CHUNK ? packed : ZIP_CHUNK;
            if (fread(buffer, 1, n, f) != n)
                goto done;
            packed -= n;
            offset += n;
            stream.next_in = buffer;
            stream.avail_in = n;
        }
        size_t n;
        const unsigned char *bytes;
        if (method == 8) {
            stream.next_out = output;
            stream.avail_out = ZIP_CHUNK;
            status = inflate(&stream, Z_NO_FLUSH);
            if (status != Z_OK && status != Z_STREAM_END)
                goto done;
            n = ZIP_CHUNK - stream.avail_out;
            bytes = output;
        } else {
            n = stream.avail_in;
            stream.avail_in = 0;
            bytes = buffer;
        }
        if (n > size - written || fwrite(bytes, 1, n, out) != n ||
            !import_update(cb, path, offset, total))
            goto done;
        written += n;
        actual = crc32(actual, bytes, n);
        if (status == Z_STREAM_END)
            break;
    }
    ok = !packed && !stream.avail_in && written == size && actual == crc;
done:
    if (ready) inflateEnd(&stream);
    return !fclose(out) && ok;
}

bool import_zip(const char *source, const char *dest, const ImportCallbacks *cb)
{
    FILE *f = fopen(source, "rb");
    if (!f) return false;
    struct stat st;
    unsigned char *buffer = malloc(2 * ZIP_CHUNK);
    uint64_t pos, end, count;
    bool ok = false;
    if (!buffer || fstat(fileno(f), &st) || st.st_size < 0 ||
        !central_directory(f, st.st_size, &pos, &end, &count))
        goto done;
    const uint64_t start = pos;
    for (uint64_t i = 0; i < count; ++i) {
        unsigned char h[46], local[30];
        char name[HUB_PATH_SIZE], path[HUB_PATH_SIZE], local_name[HUB_PATH_SIZE];
        if (!import_update(cb, source, i, count) || end - pos < sizeof(h) ||
            !at(f, pos, h, sizeof(h)) || import_le32(h) != 0x02014b50)
            goto done;
        size_t n = import_le16(h + 28), extra = import_le16(h + 30);
        size_t record = sizeof(h) + n + extra + import_le16(h + 32);
        unsigned flags = import_le16(h + 8), method = import_le16(h + 10);
        uint64_t size = import_le32(h + 24), packed = import_le32(h + 20), offset = import_le32(h + 42);
        if (!n || n >= sizeof(name) || record > end - pos || import_le16(h + 34) ||
            fread(name, 1, n, f) != n || memchr(name, 0, n) ||
            !zip64_entry(f, extra, &size, &packed, &offset))
            goto done;
        pos += record;
        name[n] = 0;
        for (size_t j = 0; j < n; ++j)
            if (name[j] == '\\') name[j] = '/';
        unsigned mode = h[5] == 3 ? (import_le32(h + 38) >> 16) & 0170000 : 0;
        bool directory = name[n - 1] == '/' || mode == 0040000, skip;
        if ((mode && mode != 0100000 && mode != 0040000) ||
            !zip_path(dest, name, directory, path, &skip))
            goto done;
        if (skip || directory)
            continue;
        if ((flags & 0x2041) || (method != 0 && method != 8) || (method == 0 && packed != size) ||
            offset > start || start - offset < sizeof(local) + n ||
            !at(f, offset, local, sizeof(local)) || import_le32(local) != 0x04034b50 ||
            import_le16(local + 6) != flags || import_le16(local + 8) != method ||
            import_le16(local + 26) != n || fread(local_name, 1, n, f) != n)
            goto done;
        for (size_t j = 0; j < n; ++j)
            if (local_name[j] == '\\') local_name[j] = '/';
        offset += sizeof(local) + n + import_le16(local + 28);
        if (memcmp(name, local_name, n) || offset > start || packed > start - offset ||
            !import_seek(f, offset) || !extract(f, path, packed, size, method,
                import_le32(h + 16), buffer, cb, 0, 0))
            goto done;
    }
    ok = pos == end;
done:
    free(buffer);
    fclose(f);
    return ok;
}
