#include "local_import.h"

#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <zlib.h>

#if defined(__SWITCH__) && (defined(ALICEHUB_SWITCH_HUB) || defined(ALICEHUB_SWITCH_XSYSTEM4))
#include "system4/utfsjis.h"
#elif !defined(__SWITCH__)
#include <iconv.h>
#endif

#define ZIP_CHUNK 32768

static bool is_valid_utf8_bytes(const unsigned char *s, size_t len)
{
    size_t i = 0;
    while (i < len) {
        unsigned char c = s[i];
        if (c < 0x80) {
            ++i;
        } else if (c >= 0xc2 && c <= 0xdf && i + 1 < len && (s[i + 1] & 0xc0) == 0x80) {
            i += 2;
        } else if (c >= 0xe0 && c <= 0xef && i + 2 < len &&
                   (s[i + 1] & 0xc0) == 0x80 && (s[i + 2] & 0xc0) == 0x80 &&
                   !(c == 0xe0 && s[i + 1] < 0xa0) && !(c == 0xed && s[i + 1] >= 0xa0)) {
            i += 3;
        } else if (c >= 0xf0 && c <= 0xf4 && i + 3 < len &&
                   (s[i + 1] & 0xc0) == 0x80 && (s[i + 2] & 0xc0) == 0x80 && (s[i + 3] & 0xc0) == 0x80 &&
                   !(c == 0xf0 && s[i + 1] < 0x90) && !(c == 0xf4 && s[i + 1] >= 0x90)) {
            i += 4;
        } else {
            return false;
        }
    }
    return true;
}

static bool utf8_has_kana(const char *s);

static bool is_ascii_letter(unsigned char b)
{
    return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z');
}

static bool is_cp1252_punct_or_accented(unsigned char b)
{
    return b == 0x85 || (b >= 0x91 && b <= 0x94) || b == 0x96 || b == 0x97 || b == 0x99 ||
           b == 0xa9 || b == 0xae || b == 0xb0 ||
           (b >= 0xc0 && b != 0xd7 && b != 0xf7);
}

static bool is_english_contraction_after_apostrophe(const unsigned char *s, size_t len, size_t idx)
{
    if (idx < 2 || !is_ascii_letter(s[idx - 1]) || !is_ascii_letter(s[idx - 2]) || idx + 1 >= len)
        return false;
    unsigned char n1 = s[idx + 1];
    unsigned char l1 = (n1 >= 'A' && n1 <= 'Z') ? (unsigned char)(n1 + 32) : n1;
    int n2 = idx + 2 < len ? (int)s[idx + 2] : -1;
    int l2 = (n2 >= 'A' && n2 <= 'Z') ? (n2 + 32) : n2;
    int n3 = idx + 3 < len ? (int)s[idx + 3] : -1;
    bool n2_not_letter = n2 < 0 || !is_ascii_letter((unsigned char)n2);
    bool n3_not_letter = n3 < 0 || !is_ascii_letter((unsigned char)n3);
    if (l1 == 's' || l1 == 't' || l1 == 'd' || l1 == 'm')
        return n2_not_letter;
    if (l1 == 'r' || l1 == 'v')
        return l2 == 'e' && n3_not_letter;
    if (l1 == 'l')
        return l2 == 'l' && n3_not_letter;
    return false;
}

static size_t stem_ascii_letters_around(const unsigned char *s, size_t len, size_t idx)
{
    size_t seg_start = idx;
    while (seg_start > 0 && s[seg_start - 1] != '/' && s[seg_start - 1] != '\\')
        --seg_start;
    size_t seg_end = idx;
    while (seg_end < len && s[seg_end] != '/' && s[seg_end] != '\\')
        ++seg_end;
    size_t stem_end = seg_end;
    for (size_t k = seg_end; k > seg_start; --k) {
        if (s[k - 1] == '.') {
            if (k - 1 > seg_start)
                stem_end = k - 1;
            break;
        }
    }
    size_t count = 0;
    for (size_t k = seg_start; k < stem_end; ++k)
        if (is_ascii_letter(s[k]))
            ++count;
    return count;
}

static bool looks_like_cp1252(const unsigned char *s, size_t len, const char *sjis, const char *gbk)
{
    size_t high = 0, ascii_letters = 0;
    bool adj_high = false, all_cp1252 = true, all_strict = true;
    for (size_t i = 0; i < len; ++i) {
        unsigned char b = s[i];
        if (b < 0x80) {
            if (is_ascii_letter(b))
                ++ascii_letters;
            continue;
        }
        ++high;
        int prev = i > 0 ? (int)s[i - 1] : -1;
        int next = i + 1 < len ? (int)s[i + 1] : -1;
        if (prev >= 0x80 || next >= 0x80) {
            adj_high = true;
            all_cp1252 = false;
        }
        if (!is_cp1252_punct_or_accented(b))
            all_cp1252 = false;
        bool byte_ok;
        if (b >= 0x80 && b <= 0x9f) {
            if (next >= 0x40)
                byte_ok = b == 0x92 && is_english_contraction_after_apostrophe(s, len, i);
            else
                byte_ok = is_cp1252_punct_or_accented(b);
        } else if (b >= 0xe0 && b <= 0xef) {
            size_t stem = stem_ascii_letters_around(s, len, i);
            byte_ok = stem >= 2 && (
                (prev >= 0 && is_ascii_letter((unsigned char)prev)) ||
                (next >= 'a' && next <= 'z' && i + 2 < len && is_ascii_letter(s[i + 2]))
            );
        } else {
            size_t stem = stem_ascii_letters_around(s, len, i);
            byte_ok = is_cp1252_punct_or_accented(b) && stem >= 2 && (
                (prev >= 0 && is_ascii_letter((unsigned char)prev)) ||
                (next >= 'a' && next <= 'z' && (prev < 0 || prev == ' ' || prev == '/' || prev == '\\' || prev == '-' || prev == '_'))
            );
        }
        if (!byte_ok)
            all_strict = false;
    }
    if (!high)
        return false;
    if (!sjis && !gbk)
        return all_cp1252 || (ascii_letters >= 2 && !adj_high);
    if (!adj_high && all_cp1252 && all_strict && ascii_letters >= 2)
        return !sjis || !utf8_has_kana(sjis);
    return false;
}

static bool cp1252_to_utf8(const unsigned char *s, size_t len, char out[HUB_PATH_SIZE])
{
    static const uint16_t map80[32] = {
        0x20ac, 0x0081, 0x201a, 0x0192, 0x201e, 0x2026, 0x2020, 0x2021,
        0x02c6, 0x2030, 0x0160, 0x2039, 0x0152, 0x008d, 0x017d, 0x008f,
        0x0090, 0x2018, 0x2019, 0x201c, 0x201d, 0x2022, 0x2013, 0x2014,
        0x02dc, 0x2122, 0x0161, 0x203a, 0x0153, 0x009d, 0x017e, 0x0178,
    };
    size_t pos = 0;
    for (size_t i = 0; i < len; ++i) {
        uint32_t cp = s[i] < 0x80 ? s[i] : (s[i] < 0xa0 ? map80[s[i] - 0x80] : s[i]);
        if (cp < 0x80) {
            if (pos + 1 >= HUB_PATH_SIZE) return false;
            out[pos++] = (char)cp;
        } else if (cp < 0x800) {
            if (pos + 2 >= HUB_PATH_SIZE) return false;
            out[pos++] = (char)(0xc0 | (cp >> 6));
            out[pos++] = (char)(0x80 | (cp & 0x3f));
        } else {
            if (pos + 3 >= HUB_PATH_SIZE) return false;
            out[pos++] = (char)(0xe0 | (cp >> 12));
            out[pos++] = (char)(0x80 | ((cp >> 6) & 0x3f));
            out[pos++] = (char)(0x80 | (cp & 0x3f));
        }
    }
    out[pos] = '\0';
    return true;
}

static bool cjk_to_utf8(const char *raw, size_t len, bool gbk, char out[HUB_PATH_SIZE])
{
#if defined(__SWITCH__) && (defined(ALICEHUB_SWITCH_HUB) || defined(ALICEHUB_SWITCH_XSYSTEM4))
    bool valid = false;
    char *converted = cjk2utf_strict_buf(raw, len, gbk, out, HUB_PATH_SIZE, &valid);
    if (converted != out) {
        valid = valid && strlen(converted) < HUB_PATH_SIZE;
        if (valid)
            strcpy(out, converted);
        free(converted);
    }
    return valid;
#elif !defined(__SWITCH__)
    iconv_t cvt = iconv_open("UTF-8", gbk ? "GBK" : "CP932");
    if (cvt == (iconv_t)-1)
        return false;
    char *in = (char *)raw;
    char *dst = out;
    size_t in_left = len, out_left = HUB_PATH_SIZE - 1;
    bool ok = iconv(cvt, &in, &in_left, &dst, &out_left) != (size_t)-1 && in_left == 0;
    *dst = '\0';
    iconv_close(cvt);
    return ok;
#else
    (void)raw; (void)len; (void)gbk; (void)out;
    return false;
#endif
}

static bool utf8_has_kana(const char *s)
{
    for (const unsigned char *p = (const unsigned char *)s; *p; ) {
        if (p[0] == 0xe3 && (p[1] & 0xc0) == 0x80 && (p[2] & 0xc0) == 0x80) {
            uint32_t cp = ((uint32_t)(p[0] & 0x0f) << 12) | ((uint32_t)(p[1] & 0x3f) << 6) | (p[2] & 0x3f);
            if ((cp >= 0x3041 && cp <= 0x309f) || (cp >= 0x30a1 && cp <= 0x30ff))
                return true;
            p += 3;
        } else {
            ++p;
        }
    }
    return false;
}

static bool decode_zip_name(const char *raw, size_t len, bool utf8_flag, char out[HUB_PATH_SIZE])
{
    if (!len || len >= HUB_PATH_SIZE)
        return false;
    if (utf8_flag || is_valid_utf8_bytes((const unsigned char *)raw, len)) {
        memcpy(out, raw, len);
        out[len] = '\0';
    } else {
        char sjis[HUB_PATH_SIZE], gbk[HUB_PATH_SIZE];
        bool ok_sjis = cjk_to_utf8(raw, len, false, sjis);
        bool ok_gbk = cjk_to_utf8(raw, len, true, gbk);
        if (looks_like_cp1252((const unsigned char *)raw, len, ok_sjis ? sjis : NULL, ok_gbk ? gbk : NULL)) {
            if (!cp1252_to_utf8((const unsigned char *)raw, len, out))
                return false;
        } else if (ok_sjis && !ok_gbk) {
            strcpy(out, sjis);
        } else if (ok_gbk && !ok_sjis) {
            strcpy(out, gbk);
        } else if (ok_sjis && ok_gbk) {
            size_t gb2312_pairs = 0;
            for (size_t i = 0; i < len; ) {
                unsigned char a = (unsigned char)raw[i];
                if (a < 0x80) { ++i; continue; }
                if (i + 1 < len) {
                    unsigned char b = (unsigned char)raw[i + 1];
                    if (a >= 0xb0 && a <= 0xf7 && b >= 0xa1 && b <= 0xfe)
                        ++gb2312_pairs;
                    i += 2;
                } else {
                    break;
                }
            }
            strcpy(out, (gb2312_pairs > 0 && !utf8_has_kana(sjis)) ? gbk : sjis);
        } else if (!cp1252_to_utf8((const unsigned char *)raw, len, out)) {
            return false;
        }
    }
    for (char *p = out; *p; ++p)
        if (*p == '\\') *p = '/';
    return true;
}

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
        if (!*p || !strcmp(p, "..") || ++depth > IMPORT_DEPTH) {
            if (slash) *slash = '/';
            return false;
        }
        if (!strcasecmp(p, "__MACOSX") || !strcasecmp(p, ".DS_Store") ||
            import_skip(p, !last || directory)) {
            *skip = true;
        } else if (!*skip && !import_leaf(p)) {
            if (slash) *slash = '/';
            return false;
        }
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
        char raw_name[HUB_PATH_SIZE], name[HUB_PATH_SIZE], path[HUB_PATH_SIZE];
        char raw_local_name[HUB_PATH_SIZE], local_name[HUB_PATH_SIZE];
        if (!import_update(cb, source, i, count) || end - pos < sizeof(h) ||
            !at(f, pos, h, sizeof(h)) || import_le32(h) != 0x02014b50)
            goto done;
        size_t n = import_le16(h + 28), extra = import_le16(h + 30);
        size_t record = sizeof(h) + n + extra + import_le16(h + 32);
        unsigned flags = import_le16(h + 8), method = import_le16(h + 10);
        uint64_t size = import_le32(h + 24), packed = import_le32(h + 20), offset = import_le32(h + 42);
        if (!n || n >= sizeof(raw_name) || record > end - pos || import_le16(h + 34) ||
            fread(raw_name, 1, n, f) != n || memchr(raw_name, 0, n) ||
            !zip64_entry(f, extra, &size, &packed, &offset) ||
            !decode_zip_name(raw_name, n, (flags & 0x0800) != 0, name))
            goto done;
        pos += record;
        size_t name_len = strlen(name);
        unsigned mode = h[5] == 3 ? (import_le32(h + 38) >> 16) & 0170000 : 0;
        bool directory = (name_len > 0 && name[name_len - 1] == '/') || mode == 0040000, skip;
        if ((mode && mode != 0100000 && mode != 0040000) ||
            !zip_path(dest, name, directory, path, &skip))
            goto done;
        if (skip || directory)
            continue;
        if ((flags & 0x2041) || (method != 0 && method != 8) || (method == 0 && packed != size) ||
            offset > start || start - offset < sizeof(local) + n ||
            !at(f, offset, local, sizeof(local)) || import_le32(local) != 0x04034b50 ||
            import_le16(local + 6) != flags || import_le16(local + 8) != method ||
            import_le16(local + 26) != n || fread(raw_local_name, 1, n, f) != n ||
            !decode_zip_name(raw_local_name, n, (flags & 0x0800) != 0, local_name))
            goto done;
        offset += sizeof(local) + n + import_le16(local + 28);
        if (strcmp(name, local_name) || offset > start || packed > start - offset ||
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
