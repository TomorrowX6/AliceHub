#include "local_import.h"

#include <ctype.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

#define MAX_FDI_HEADER 65536u
#define MAX_FDI_DATA (32u * 1024u * 1024u)

typedef struct {
    FILE *file;
    uint64_t file_size;
    uint64_t header_size;
    uint64_t fdd_size;
    unsigned bps;
    unsigned spc;
    uint64_t fat_offset;
    size_t fat_bytes;
    uint64_t root_offset;
    size_t root_bytes;
    uint64_t data_offset;
    unsigned total_clusters;
    bool fat16;
    unsigned char *fat;
    uint64_t extracted_bytes;
} Floppy;

static bool read_at(FILE *file, uint64_t file_size, uint64_t offset, void *buf, size_t n)
{
    return offset <= file_size && n <= file_size - offset &&
           import_seek(file, offset) && fread(buf, 1, n, file) == n;
}

static bool open_floppy(Floppy *f, const char *source)
{
    memset(f, 0, sizeof(*f));
    f->file = fopen(source, "rb");
    if (!f->file)
        return false;
    struct stat st;
    if (fstat(fileno(f->file), &st) || st.st_size < 512)
        return false;
    f->file_size = (uint64_t)st.st_size;

    unsigned char hdr[32];
    if (!read_at(f->file, f->file_size, 0, hdr, sizeof(hdr)))
        return false;
    uint32_t zero = import_le32(hdr + 0);
    uint32_t fdd_type = import_le32(hdr + 4);
    uint32_t hdr_size = import_le32(hdr + 8);
    uint32_t fdd_size = import_le32(hdr + 12);
    uint32_t sec_size = import_le32(hdr + 16);
    uint32_t sectors = import_le32(hdr + 20);
    uint32_t heads = import_le32(hdr + 24);
    uint32_t cyls = import_le32(hdr + 28);

    if (zero != 0 || fdd_type == 0 || fdd_type > 0xffu)
        return false;
    if (hdr_size < 32u || hdr_size > MAX_FDI_HEADER || fdd_size == 0 || fdd_size > MAX_FDI_DATA)
        return false;
    if ((uint64_t)hdr_size + (uint64_t)fdd_size > f->file_size)
        return false;
    if (sec_size != 256u && sec_size != 512u && sec_size != 1024u)
        return false;
    if (sectors < 1u || sectors > 64u || heads < 1u || heads > 2u || cyls < 1u || cyls > 256u)
        return false;
    uint64_t geom = (uint64_t)sec_size * sectors * heads * cyls;
    if (!geom || geom > fdd_size)
        return false;

    unsigned char boot[64];
    if (!read_at(f->file, f->file_size, hdr_size, boot, sizeof(boot)))
        return false;
    unsigned bps = import_le16(boot + 11);
    unsigned spc = boot[13];
    unsigned rsv = import_le16(boot + 14);
    unsigned nfats = boot[16];
    unsigned root_ent = import_le16(boot + 17);
    unsigned tot16 = import_le16(boot + 19);
    unsigned spf = import_le16(boot + 22);
    uint32_t tot32 = import_le32(boot + 32);

    if ((bps != 256u && bps != 512u && bps != 1024u) ||
        !spc || spc > 128u || (spc & (spc - 1u)) != 0 ||
        rsv < 1u || nfats < 1u || nfats > 2u || root_ent < 1u || spf < 1u)
        return false;

    uint64_t tot_sec = tot16 ? (uint64_t)tot16 : (uint64_t)tot32;
    if (!tot_sec || tot_sec * bps > fdd_size)
        return false;

    uint64_t fat_offset = (uint64_t)hdr_size + (uint64_t)rsv * bps;
    uint64_t fat_bytes = (uint64_t)spf * bps;
    uint64_t root_offset = fat_offset + (uint64_t)nfats * fat_bytes;
    uint64_t root_bytes = (uint64_t)root_ent * 32u;
    uint64_t root_secs = (root_bytes + bps - 1u) / bps;
    uint64_t data_offset = root_offset + root_secs * bps;
    uint64_t non_data = (uint64_t)rsv + (uint64_t)nfats * spf + root_secs;
    if (tot_sec <= non_data || data_offset >= (uint64_t)hdr_size + fdd_size || fat_bytes > 512u * 1024u)
        return false;
    uint64_t total_clusters = (tot_sec - non_data) / spc;
    if (total_clusters < 2u || total_clusters >= 65525u)
        return false;

    f->fat = malloc((size_t)fat_bytes);
    if (!f->fat || !read_at(f->file, f->file_size, fat_offset, f->fat, (size_t)fat_bytes))
        return false;

    f->header_size = hdr_size;
    f->fdd_size = fdd_size;
    f->bps = bps;
    f->spc = spc;
    f->fat_offset = fat_offset;
    f->fat_bytes = (size_t)fat_bytes;
    f->root_offset = root_offset;
    f->root_bytes = (size_t)root_bytes;
    f->data_offset = data_offset;
    f->total_clusters = (unsigned)total_clusters;
    f->fat16 = total_clusters >= 4085u;
    return true;
}

static unsigned next_cluster(const Floppy *f, unsigned c)
{
    if (f->fat16) {
        size_t off = (size_t)c * 2u;
        return off + 2u > f->fat_bytes ? 0xffffu : import_le16(f->fat + off);
    }
    size_t off = ((size_t)c * 3u) / 2u;
    if (off + 2u > f->fat_bytes)
        return 0xfffu;
    unsigned raw = import_le16(f->fat + off);
    return (c & 1u) ? (raw >> 4) : (raw & 0x0fffu);
}

static unsigned char *read_chain(const Floppy *f, unsigned start, uint64_t limit, size_t *out_len)
{
    *out_len = 0;
    uint64_t max_bytes = limit ? limit : f->fdd_size;
    if (max_bytes > f->fdd_size)
        return NULL;
    size_t cluster_bytes = (size_t)f->spc * f->bps;
    unsigned eof = f->fat16 ? 0xfff8u : 0x0ff8u;
    unsigned char *visited = calloc(f->total_clusters + 2u, 1);
    unsigned char *buf = malloc((size_t)max_bytes + 1u);
    if (!visited || !buf) {
        free(visited);
        free(buf);
        return NULL;
    }
    size_t pos = 0;
    unsigned c = start;
    bool ok = true;
    while (c >= 2u && c < eof && pos < (size_t)max_bytes) {
        if (c >= f->total_clusters + 2u || visited[c]) {
            ok = false;
            break;
        }
        visited[c] = 1;
        uint64_t off = f->data_offset + (uint64_t)(c - 2u) * cluster_bytes;
        size_t rem = (size_t)max_bytes - pos;
        size_t n = cluster_bytes < rem ? cluster_bytes : rem;
        if (!read_at(f->file, f->file_size, off, buf + pos, n)) {
            ok = false;
            break;
        }
        pos += n;
        c = next_cluster(f, c);
    }
    free(visited);
    if (!ok) {
        free(buf);
        return NULL;
    }
    *out_len = pos;
    return buf;
}

static bool is_dos_boot_file(const char *name)
{
    return !strcasecmp(name, "IO.SYS") || !strcasecmp(name, "MSDOS.SYS") ||
           !strcasecmp(name, "COMMAND.COM") || !strcasecmp(name, "AUTOEXEC.BAT") ||
           !strcasecmp(name, "CONFIG.SYS");
}

static bool decode_83(const unsigned char ent[32], char name[HUB_NAME_SIZE])
{
    unsigned char raw_name[8], raw_ext[3];
    memcpy(raw_name, ent, 8);
    memcpy(raw_ext, ent + 8, 3);
    if (raw_name[0] == 0x05)
        raw_name[0] = 0xe5;
    size_t nlen = 8, elen = 3;
    while (nlen && raw_name[nlen - 1] == ' ') --nlen;
    while (elen && raw_ext[elen - 1] == ' ') --elen;
    if (!nlen)
        return false;

    static const char hex[] = "0123456789abcdef";
    size_t out = 0;
    for (size_t i = 0; i < nlen; ++i) {
        unsigned char c = raw_name[i];
        if (c >= 0x20 && c < 0x7f && c != '/' && c != '\\') {
            if (out + 1 >= HUB_NAME_SIZE) return false;
            name[out++] = (char)c;
        } else {
            if (out + 3 >= HUB_NAME_SIZE) return false;
            name[out++] = '_';
            name[out++] = hex[c >> 4];
            name[out++] = hex[c & 15];
        }
    }
    if (elen) {
        if (out + 1 >= HUB_NAME_SIZE) return false;
        name[out++] = '.';
        for (size_t i = 0; i < elen; ++i) {
            unsigned char c = raw_ext[i];
            if (c >= 0x20 && c < 0x7f && c != '/' && c != '\\') {
                if (out + 1 >= HUB_NAME_SIZE) return false;
                name[out++] = (char)c;
            } else {
                if (out + 3 >= HUB_NAME_SIZE) return false;
                name[out++] = '_';
                name[out++] = hex[c >> 4];
                name[out++] = hex[c & 15];
            }
        }
    }
    name[out] = 0;
    return import_leaf(name);
}

static bool extract_dir(Floppy *f, const unsigned char *dir_bytes, size_t dir_len,
                        const char *dest, unsigned depth, unsigned char *visited_dirs,
                        const ImportCallbacks *cb)
{
    if (depth > IMPORT_DEPTH)
        return false;
    for (size_t off = 0; off + 32u <= dir_len; off += 32u) {
        const unsigned char *ent = dir_bytes + off;
        if (ent[0] == 0x00)
            break;
        if (ent[0] == 0xe5)
            continue;
        unsigned attr = ent[11];
        if (attr == 0x0f || (attr & 0x08))
            continue;
        char name[HUB_NAME_SIZE], path[HUB_PATH_SIZE];
        if (!decode_83(ent, name))
            continue;
        bool is_dir = (attr & 0x10) != 0;
        if (!is_dir && depth == 0 && is_dos_boot_file(name))
            continue;
        if (import_skip(name, is_dir))
            continue;
        if (!import_path(path, dest, name))
            return false;
        unsigned start = import_le16(ent + 26);
        uint32_t size = import_le32(ent + 28);
        if (is_dir) {
            if (!import_mkdir(path))
                return false;
            if (start >= 2u) {
                if (start >= f->total_clusters + 2u || visited_dirs[start])
                    return false;
                visited_dirs[start] = 1;
                size_t sub_len = 0;
                unsigned char *sub = read_chain(f, start, 0, &sub_len);
                if (!sub)
                    return false;
                bool ok = extract_dir(f, sub, sub_len, path, depth + 1, visited_dirs, cb);
                free(sub);
                if (!ok)
                    return false;
            }
        } else {
            if ((uint64_t)size > f->fdd_size || f->extracted_bytes + size > f->fdd_size * 2u)
                return false;
            if (!import_update(cb, name, f->extracted_bytes, f->fdd_size))
                return false;
            size_t data_len = 0;
            unsigned char *data = NULL;
            if (size && start >= 2u) {
                data = read_chain(f, start, size, &data_len);
                if (!data || data_len < size) {
                    free(data);
                    return false;
                }
            }
            FILE *out = fopen(path, "wbx");
            bool ok = out && (!size || fwrite(data, 1, size, out) == size);
            if (out && fclose(out))
                ok = false;
            free(data);
            if (!ok)
                return false;
            f->extracted_bytes += size;
        }
    }
    return true;
}

bool import_fdi(const char *source, const char *dest, const ImportCallbacks *cb)
{
    Floppy f;
    bool ok = open_floppy(&f, source);
    if (ok) {
        unsigned char *root = malloc(f.root_bytes);
        unsigned char *visited = calloc(f.total_clusters + 2u, 1);
        ok = root && visited &&
             read_at(f.file, f.file_size, f.root_offset, root, f.root_bytes) &&
             extract_dir(&f, root, f.root_bytes, dest, 0, visited, cb);
        free(root);
        free(visited);
    }
    free(f.fat);
    if (f.file)
        fclose(f.file);
    return ok;
}
