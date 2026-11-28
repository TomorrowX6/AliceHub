#include "local_import.h"
#include "fs_util.h"
#include "install_transaction.h"

#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

typedef struct {
    HubEngine engine;
    char code[HUB_NAME_SIZE];
    char scenario[HUB_NAME_SIZE];
    bool ini, chs_config, dat, graphics;
} Markers;

typedef struct {
    char path[HUB_PATH_SIZE];
    Markers markers;
    unsigned depth;
} GameRoot;

static const char *leaf(const char *path)
{
    const char *slash = strrchr(path, '/');
    return slash ? slash + 1 : path;
}

bool import_path(char out[HUB_PATH_SIZE], const char *dir, const char *name)
{
    size_t len = strlen(dir);
    int n = snprintf(out, HUB_PATH_SIZE, "%s%s%s", dir, len && dir[len - 1] == '/' ? "" : "/", name);
    return n >= 0 && n < HUB_PATH_SIZE;
}

bool import_leaf(const char *name)
{
    return !strchr(name, '/') && hub_install_relative_path(name);
}

bool import_suffix(const char *name, const char *suffix)
{
    size_t a = strlen(name), b = strlen(suffix);
    return a >= b && !strcasecmp(name + a - b, suffix);
}

bool import_skip(const char *name, bool directory)
{
    if ((*name == '.' && (directory || strcasecmp(name, ".xsys35rc"))) ||
        !strcasecmp(name, "android-port") || !strcasecmp(name, "savedata") || !strcasecmp(name, "directx"))
        return true;
    static const char *const files[] = {"opensavefolder.exe", "resetconfig.exe", "uninstaller.exe"};
    static const char *const extensions[] = {".dll", ".lnk", ".bat", ".cmd", ".ps1", ".mds"};
    for (size_t i = 0; i < sizeof(files) / sizeof(*files); ++i)
        if (!strcasecmp(name, files[i])) return true;
    for (size_t i = 0; i < sizeof(extensions) / sizeof(*extensions); ++i)
        if (import_suffix(name, extensions[i])) return true;
    return false;
}

bool import_mkdir(const char *path)
{
    struct stat st;
    return !mkdir(path, 0777) || (errno == EEXIST && !lstat(path, &st) && S_ISDIR(st.st_mode));
}

bool import_update(const ImportCallbacks *cb, const char *detail, uint64_t current, uint64_t total)
{
    return !cb || !cb->update || cb->update(cb->data, detail, current, total);
}

bool import_seek(FILE *file, uint64_t offset)
{
    return offset <= INT64_MAX && (uint64_t)(off_t)offset == offset && (off_t)offset >= 0 &&
           !fseeko(file, (off_t)offset, SEEK_SET);
}

uint16_t import_le16(const unsigned char *p)
{
    return p[0] | (uint16_t)p[1] << 8;
}

uint32_t import_le32(const unsigned char *p)
{
    return import_le16(p) | (uint32_t)import_le16(p + 2) << 16;
}

static bool excluded_root(const char *name)
{
    static const char *const names[] = {
        "backup", "backdata", "patchcn", "patchwm", "补丁", "汉化补丁", "备份", "备份文件",
        "広告", "广告", "原版有码备份", "无码补丁备份", "汉化补丁备份", "特典相关", "全CG存档", "解答集"
    };
    for (size_t i = 0; i < sizeof(names) / sizeof(*names); ++i)
        if (!strcasecmp(name, names[i])) return true;
    return false;
}

static bool ain(const char *name)
{
    return import_suffix(name, ".ain") && strcasecmp(name, "system39.ain");
}

static bool chs(const char *name)
{
    return import_suffix(name, ".chs") && strcasecmp(name, "alicestart.chs");
}

static bool scenario(const char *name)
{
    return import_suffix(name, "sa.ald") || import_suffix(name, "sa_chs.ald") ||
           !strcasecmp(name, "adisk.ald");
}

static bool scenario_dat(const char *path, off_t size)
{
    unsigned char h[4];
    FILE *f = fopen(path, "rb");
    if (!f) return false;
    bool ok = fread(h, 1, sizeof(h), f) == sizeof(h);
    fclose(f);
    if (!ok) return false;
    unsigned link = import_le16(h), data = import_le16(h + 2);
    return link && data > link && (data - 1) * 256L < size;
}

static void configured_code(const char *path, char code[HUB_NAME_SIZE])
{
    FILE *f = fopen(path, "rb");
    if (!f) return;
    char line[HUB_NAME_SIZE * 2];
    for (size_t i = 0; i < 256 && fgets(line, sizeof(line), f); ++i) {
        char *key = line;
        if (!i && !strncmp(key, "\xef\xbb\xbf", 3)) key += 3;
        while (isspace((unsigned char)*key)) ++key;
        if (strncasecmp(key, "codename", 8)) continue;
        key += 8;
        while (isspace((unsigned char)*key)) ++key;
        if (*key++ != '=') continue;
        while (isspace((unsigned char)*key)) ++key;
        char *end = key + strcspn(key, "\r\n");
        if (*key == '"') {
            ++key;
            end = strchr(key, '"');
            if (!end) break;
        } else {
            while (end > key && isspace((unsigned char)end[-1])) --end;
        }
        *end = 0;
        char *base = strrchr(key, '\\');
        key = (char *)leaf(base ? base + 1 : key);
        if (chs(key) && strlen(key) < HUB_NAME_SIZE)
            strcpy(code, key);
        break;
    }
    fclose(f);
}

static void pick_name(char to[HUB_NAME_SIZE], const char *from)
{
    if ((!*to || strcasecmp(from, to) < 0) && strlen(from) < HUB_NAME_SIZE)
        strcpy(to, from);
}

static bool markers(const char *dir, Markers *m)
{
    *m = (Markers){.engine = HUB_ENGINE_COUNT};
    DIR *d = opendir(dir);
    if (!d) return false;
    char chs_file[HUB_NAME_SIZE] = "", configured[HUB_NAME_SIZE] = "", configured_script[HUB_NAME_SIZE] = "";
    bool sys35 = false, ok = true;
    for (;;) {
        errno = 0;
        struct dirent *entry = readdir(d);
        if (!entry) {
            ok = !errno;
            break;
        }
        const char *name = entry->d_name;
        if (!import_leaf(name) || import_skip(name, false)) continue;
        char path[HUB_PATH_SIZE];
        struct stat st;
        if (!import_path(path, dir, name) || lstat(path, &st) || !S_ISREG(st.st_mode) || !st.st_size)
            continue;
        if (hub_system4_standard_ini(name)) {
            m->ini = true;
            m->chs_config |= !strcasecmp(name, "alicestart.chs");
            if (!*configured) configured_code(path, configured);
        } else if (ain(name)) {
            pick_name(m->code, name);
        } else if (chs(name)) {
            pick_name(chs_file, name);
        } else if (!strcasecmp(name, "chs.tbl")) {
            m->chs_config = true;
        } else if (scenario(name) || !strcasecmp(name, "xsystem35.gr")) {
            pick_name(m->scenario, name);
        } else if (!strcasecmp(name, "system39.ain") || !strcasecmp(name, "system39.ini")) {
            sys35 = true;
        } else if (!strcasecmp(name, "adisk.dat") || !strcasecmp(name, "gdisk.dat") ||
                   !strcasecmp(name, "agame.dat")) {
            m->dat |= scenario_dat(path, st.st_size);
        } else if (!strcasecmp(name, "acg.dat") || !strcasecmp(name, "acg0.dat") ||
                   !strcasecmp(name, "acg3.dat")) {
            m->graphics = true;
        }
        char script[HUB_PATH_SIZE];
        if (hub_system4_configured_script(dir, name, script) && !lstat(script, &st) && st.st_size) {
            m->ini = true;
            pick_name(configured_script, leaf(script));
        }
    }
    closedir(d);
    if (!*m->code && *configured_script)
        strcpy(m->code, configured_script);
    if (!*m->code && *chs_file) {
        if (*configured) {
            d = opendir(dir);
            if (!d) return false;
            for (struct dirent *e; (e = readdir(d)); ) {
                char path[HUB_PATH_SIZE];
                struct stat st;
                if (!strcasecmp(e->d_name, configured) && import_path(path, dir, e->d_name) &&
                    !lstat(path, &st) && S_ISREG(st.st_mode) && st.st_size) {
                    strcpy(m->code, e->d_name);
                    break;
                }
            }
            closedir(d);
        }
        if (!*m->code && m->chs_config)
            strcpy(m->code, chs_file);
    }
    if (m->ini && *m->code)
        m->engine = HUB_XSYSTEM4;
    else if (*m->scenario && !m->ini && !*m->code)
        m->engine = HUB_XSYSTEM35;
    else if (m->dat && m->graphics && !m->ini && !*m->code && !sys35)
        m->engine = HUB_SYSTEM3;
    return ok;
}

static bool scan_roots(const char *dir, unsigned depth, GameRoot *primary, const ImportCallbacks *cb)
{
    if (!dir || strlen(dir) >= HUB_PATH_SIZE)
        return false;
    Markers m;
    if (!import_update(cb, "Looking for game data", 0, 0) || !markers(dir, &m))
        return false;
    if (m.engine != HUB_ENGINE_COUNT) {
        if (!*primary->path || depth < primary->depth ||
            (depth == primary->depth && strcasecmp(dir, primary->path) < 0)) {
            strcpy(primary->path, dir);
            primary->depth = depth;
            primary->markers = m;
        }
        return true;
    }
    if (depth == IMPORT_DEPTH || (*primary->path && depth >= primary->depth))
        return true;
    DIR *d = opendir(dir);
    if (!d) return false;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *e = readdir(d);
        if (!e) {
            ok = !errno;
            break;
        }
        char path[HUB_PATH_SIZE];
        struct stat st;
        if (!import_leaf(e->d_name) || import_skip(e->d_name, true) || excluded_root(e->d_name))
            continue;
        if (import_path(path, dir, e->d_name) && !lstat(path, &st) && S_ISDIR(st.st_mode) &&
            !scan_roots(path, depth + 1, primary, cb)) {
            ok = false;
            break;
        }
    }
    closedir(d);
    return ok;
}

static bool copy_file(const char *from, const char *to, const ImportCallbacks *cb, uint64_t total)
{
    unsigned char *buf = malloc(65536);
    FILE *in = buf ? fopen(from, "rb") : NULL;
    FILE *out = in ? fopen(to, "wbx") : NULL;
    bool ok = out != NULL;
    uint64_t bytes = 0;
    while (ok) {
        size_t n = fread(buf, 1, 65536, in);
        bytes += n;
        ok = fwrite(buf, 1, n, out) == n && import_update(cb, leaf(from), bytes, total);
        if (n < 65536) {
            ok = ok && !ferror(in);
            break;
        }
    }
    if (out && fclose(out)) ok = false;
    if (in) fclose(in);
    free(buf);
    return ok;
}

static bool copy_tree(const char *from, const char *to, unsigned depth, const ImportCallbacks *cb)
{
    DIR *d = opendir(from);
    if (!d) return false;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *e = readdir(d);
        if (!e) {
            ok = !errno;
            break;
        }
        if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
        char source[HUB_PATH_SIZE], dest[HUB_PATH_SIZE];
        struct stat st;
        if (!import_leaf(e->d_name) || !import_path(source, from, e->d_name) || lstat(source, &st)) {
            ok = false;
            break;
        }
        bool folder = S_ISDIR(st.st_mode);
        if (import_skip(e->d_name, folder) || (!folder && !S_ISREG(st.st_mode))) continue;
        if (!import_update(cb, e->d_name, 0, 0) || !import_path(dest, to, e->d_name) ||
            !(folder ? depth < IMPORT_DEPTH && import_mkdir(dest) && copy_tree(source, dest, depth + 1, cb)
                     : copy_file(source, dest, cb, st.st_size))) {
            ok = false;
            break;
        }
    }
    closedir(d);
    return ok;
}

static bool remove_tree(char path[HUB_PATH_SIZE])
{
    struct stat st;
    if (lstat(path, &st)) return errno == ENOENT;
    if (!S_ISDIR(st.st_mode)) return !unlink(path);
    DIR *d = opendir(path);
    if (!d) return false;
    size_t prefix = strlen(path);
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *e = readdir(d);
        if (!e) {
            ok = !errno && ok;
            break;
        }
        if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
        if (strlen(e->d_name) + prefix + 1 < HUB_PATH_SIZE) {
            path[prefix] = '/';
            strcpy(path + prefix + 1, e->d_name);
            ok = remove_tree(path) && ok;
            path[prefix] = 0;
        } else {
            ok = false;
        }
    }
    closedir(d);
    return ok && !rmdir(path);
}

static bool unpack(const char *source, const char *dest, const ImportCallbacks *cb)
{
    if (import_suffix(source, ".zip")) return import_zip(source, dest, cb);
    if (import_suffix(source, ".iso") || import_suffix(source, ".mdf")) return import_iso(source, dest, cb);
    if (import_suffix(source, ".fdi")) return import_fdi(source, dest, cb);
    return false;
}

static bool disc_key(const char *name, char key[HUB_NAME_SIZE], unsigned *number)
{
    size_t n = strlen(name), end = n;
    while (n && isdigit((unsigned char)name[n - 1])) --n;
    if (n == end || end - n > 3) return false;
    *number = strtoul(name + n, NULL, 10);
    while (n && strchr(" _-", name[n - 1])) --n;
    size_t marker = 0;
    if (n >= 4 && (!strncasecmp(name + n - 4, "disc", 4) || !strncasecmp(name + n - 4, "disk", 4)))
        marker = 4;
    else if (n >= 2 && !strncasecmp(name + n - 2, "cd", 2))
        marker = 2;
    if (!marker) return false;
    n -= marker;
    if (n && !strchr(" _-", name[n - 1])) return false;
    while (n && strchr(" _-", name[n - 1])) --n;
    if (n >= HUB_NAME_SIZE) return false;
    memcpy(key, name, n);
    key[n] = 0;
    return true;
}

static bool disc_ancestor(const char *search, const char *path, char parent[HUB_PATH_SIZE],
                          char member[HUB_NAME_SIZE], char key[HUB_NAME_SIZE], unsigned *number)
{
    size_t boundary = strlen(search), n = strlen(path);
    if (n >= HUB_PATH_SIZE) return false;
    memcpy(parent, path, n + 1);
    while (n > boundary) {
        char *slash = strrchr(parent, '/');
        if (!slash || slash + 1 < parent + boundary) break;
        if (disc_key(slash + 1, key, number)) {
            if (strlen(slash + 1) >= HUB_NAME_SIZE) return false;
            strcpy(member, slash + 1);
            *slash = 0;
            return true;
        }
        *slash = 0;
        n = slash - parent;
    }
    return false;
}

typedef struct {
    char path[HUB_PATH_SIZE];
    char name[HUB_NAME_SIZE];
} DiscImage;

static bool find_images(const char *dir, DiscImage *images, size_t *count, unsigned depth,
                        const char *set, const ImportCallbacks *cb)
{
    if (!import_update(cb, "Looking for disc images", 0, 0)) return false;
    DIR *d = opendir(dir);
    if (!d) return false;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *e = readdir(d);
        if (!e) {
            ok = !errno;
            break;
        }
        const char *name = e->d_name;
        char path[HUB_PATH_SIZE];
        struct stat st;
        if (!import_leaf(name) || import_skip(name, true) || excluded_root(name) ||
            !import_path(path, dir, name) || lstat(path, &st))
            continue;
        if (S_ISDIR(st.st_mode) && !set && depth < 3) {
            if (!find_images(path, images, count, depth + 1, NULL, cb)) {
                ok = false;
                break;
            }
        } else if (S_ISREG(st.st_mode) &&
                   (import_suffix(name, ".iso") || import_suffix(name, ".mdf") || import_suffix(name, ".fdi"))) {
            char stem[HUB_NAME_SIZE], key[HUB_NAME_SIZE];
            unsigned number;
            snprintf(stem, sizeof(stem), "%.*s", (int)strlen(name) - 4, name);
            if (set && (!disc_key(stem, key, &number) || strcasecmp(set, key)))
                continue;
            if (*count == 32) {
                ok = false;
                break;
            }
            DiscImage *image = &images[(*count)++];
            strcpy(image->path, path);
            strcpy(image->name, stem);
        }
    }
    closedir(d);
    return ok;
}

static bool extract_images(const char *search, const char *images_dir, const ImportCallbacks *cb,
                           const char *set, bool *found)
{
    DiscImage *images = calloc(32, sizeof(*images));
    size_t count = 0;
    bool ok = images && find_images(search, images, &count, 0, set, cb);
    *found = count != 0;
    if (ok && count) {
        char key[HUB_NAME_SIZE] = "";
        unsigned number = 0;
        size_t parent = leaf(images[0].path) - images[0].path;
        ok = (count < 2 || disc_key(images[0].name, key, &number)) && import_mkdir(images_dir);
        for (size_t i = 0; ok && i < count; ++i) {
            char path[HUB_PATH_SIZE], next[HUB_NAME_SIZE];
            const DiscImage *image = &images[i];
            if (count > 1 && (!disc_key(image->name, next, &number) || strcasecmp(key, next) ||
                (size_t)(leaf(image->path) - image->path) != parent ||
                strncmp(images[0].path, image->path, parent))) {
                ok = false;
                break;
            }
            ok = import_leaf(image->name) && import_path(path, images_dir, image->name) &&
                 !mkdir(path, 0777) &&
                 (import_suffix(image->path, ".fdi") ? import_fdi(image->path, path, cb)
                                                    : import_iso(image->path, path, cb));
        }
    }
    free(images);
    return ok;
}

static void scenario_key(char name[HUB_NAME_SIZE])
{
    if (import_suffix(name, "_chs.ald"))
        strcpy(name + strlen(name) - 8, ".ald");
}

static bool compatible(const Markers *primary, Markers *other)
{
    if (other->engine != HUB_ENGINE_COUNT && other->engine != primary->engine)
        return false;
    if (primary->engine == HUB_XSYSTEM4)
        return !*other->scenario && !other->dat &&
               (!*other->code || !strcasecmp(primary->code, other->code));
    if (other->ini || *other->code || other->dat) return false;
    if (!*other->scenario) return true;
    char wanted[HUB_NAME_SIZE];
    strcpy(wanted, primary->scenario);
    scenario_key(wanted);
    scenario_key(other->scenario);
    return !strcasecmp(wanted, other->scenario);
}

static bool disc_asset(const Markers *primary, const char *name)
{
    if (primary->engine == HUB_XSYSTEM4) {
        const char *dot = strrchr(primary->code, '.');
        return dot && import_suffix(name, ".afa") && !strncasecmp(primary->code, name, dot - primary->code);
    }
    static const char *const extensions[] = {".ald", ".bgi", ".wai", ".alk", ".s3l", ".ini", ".ain", ".gr", ".tbl"};
    for (size_t i = 0; i < sizeof(extensions) / sizeof(*extensions); ++i)
        if (import_suffix(name, extensions[i])) return true;
    return !strcasecmp(name, "alice.exe");
}

static bool already_present(const char *dir, const char *name, bool *exists)
{
    DIR *d = opendir(dir);
    if (!d) return false;
    bool ok = true;
    *exists = false;
    for (;;) {
        errno = 0;
        struct dirent *e = readdir(d);
        if (!e) {
            ok = !errno;
            break;
        }
        if (!strcasecmp(e->d_name, name)) {
            *exists = true;
            break;
        }
    }
    closedir(d);
    return ok;
}

static bool merge_disc(const char *dir, const char *dest, const Markers *main, bool move,
                       unsigned depth, const ImportCallbacks *cb)
{
    if (depth > IMPORT_DEPTH) return true;
    Markers m;
    if (!import_update(cb, "Checking companion disc", 0, 0) || !markers(dir, &m))
        return false;
    if (!compatible(main, &m))
        return true;
    DIR *d = opendir(dir);
    if (!d) return false;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *e = readdir(d);
        if (!e) {
            ok = !errno;
            break;
        }
        const char *name = e->d_name;
        char source[HUB_PATH_SIZE], path[HUB_PATH_SIZE];
        struct stat st;
        bool exists;
        if (!import_leaf(name) || import_skip(name, true) || excluded_root(name) ||
            !import_path(source, dir, name) || lstat(source, &st))
            continue;
        if (S_ISDIR(st.st_mode))
            ok = m.engine == HUB_ENGINE_COUNT && *m.code == 0 && !*m.scenario
                ? merge_disc(source, dest, main, move, depth + 1, cb) : true;
        else if (S_ISREG(st.st_mode) && disc_asset(main, name))
            ok = import_update(cb, name, 0, 0) && import_path(path, dest, name) &&
                 already_present(dest, name, &exists) &&
                 (exists || (move ? !rename(source, path) : copy_file(source, path, cb, st.st_size)));
        if (!ok) break;
    }
    closedir(d);
    return ok;
}

static bool merge_discs(const char *parent, const char *member, const char *key, const GameRoot *main,
                        const char *dest, bool move, const ImportCallbacks *cb)
{
    if (main->markers.engine == HUB_SYSTEM3) return true;
    DIR *d = opendir(parent);
    if (!d) return false;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *e = readdir(d);
        if (!e) {
            ok = !errno;
            break;
        }
        char next[HUB_NAME_SIZE], path[HUB_PATH_SIZE];
        unsigned number;
        struct stat st;
        if (!strcmp(e->d_name, member) || !disc_key(e->d_name, next, &number) || strcasecmp(key, next))
            continue;
        if (import_path(path, parent, e->d_name) && !lstat(path, &st) && S_ISDIR(st.st_mode) &&
            !merge_disc(path, dest, &main->markers, move, 0, cb)) {
            ok = false;
            break;
        }
    }
    closedir(d);
    return ok;
}

static bool copy_companion_pc98_font(const char *dir, const char *dest, unsigned depth,
                                     const ImportCallbacks *cb)
{
    if (depth > 4) return true;
    bool exists = false;
    if (!already_present(dest, "FONT.bmp", &exists) || exists)
        return true;
    DIR *d = opendir(dir);
    if (!d) return true;
    bool copied = false;
    for (struct dirent *e; !copied && (e = readdir(d)); ) {
        const char *name = e->d_name;
        if (!import_leaf(name)) continue;
        char path[HUB_PATH_SIZE];
        struct stat st;
        if (!import_path(path, dir, name) || lstat(path, &st)) continue;
        if (S_ISDIR(st.st_mode)) {
            if (strcmp(path, dest) != 0)
                copy_companion_pc98_font(path, dest, depth + 1, cb);
            if (already_present(dest, "FONT.bmp", &exists) && exists)
                copied = true;
        } else if (S_ISREG(st.st_mode) && !strcasecmp(name, "FONT.bmp") && st.st_size >= 62 + 256 * 2048) {
            unsigned char hdr[34];
            FILE *f = fopen(path, "rb");
            bool valid = f && fread(hdr, 1, sizeof(hdr), f) == sizeof(hdr) &&
                         hdr[0] == 'B' && hdr[1] == 'M' &&
                         import_le32(hdr + 14) >= 40 &&
                         import_le32(hdr + 18) == 2048 &&
                         import_le16(hdr + 26) == 1 &&
                         import_le16(hdr + 28) == 1 &&
                         import_le32(hdr + 30) == 0;
            if (f) fclose(f);
            char out[HUB_PATH_SIZE];
            if (valid && import_path(out, dest, "FONT.bmp"))
                copied = copy_file(path, out, cb, st.st_size);
        }
    }
    closedir(d);
    return true;
}

static bool verify_stage(const char *stage, void *data)
{
    const HubGame *game = data;
    Markers m;
    return markers(stage, &m) && m.engine == game->engine;
}

bool hub_local_import(const char *root, const char *source, const ImportCallbacks *cb,
                      HubGame *game, char error[160])
{
    struct stat st;
    char work[HUB_PATH_SIZE], stage[HUB_PATH_SIZE], dest[HUB_PATH_SIZE], name[HUB_NAME_SIZE];
    const char *why = "Could not create temporary storage on SD.";
    GameRoot primary = {0};
    HubInstallTransaction *tx = NULL;
    bool ok = false;
    if (!source || strlen(source) >= HUB_PATH_SIZE) {
        strcpy(error, !source ? "Choose an existing folder, ZIP or ISO on SD." : "Source path is too long.");
        return false;
    }
    if (lstat(source, &st) || (!S_ISDIR(st.st_mode) && !S_ISREG(st.st_mode))) {
        strcpy(error, "Choose an existing folder, ZIP or ISO on SD.");
        return false;
    }
    bool folder = S_ISDIR(st.st_mode), move = !folder;
    char set[HUB_NAME_SIZE], image_parent[HUB_PATH_SIZE];
    unsigned number;
    bool direct_set = false;
    if (!folder && (import_suffix(source, ".iso") || import_suffix(source, ".mdf")) &&
        import_leaf(leaf(source))) {
        const char *base = leaf(source);
        char stem[HUB_NAME_SIZE];
        snprintf(stem, sizeof(stem), "%.*s", (int)strlen(base) - 4, base);
        direct_set = disc_key(stem, set, &number);
        if (direct_set) {
            int n = base == source ? snprintf(image_parent, sizeof(image_parent), ".") :
                snprintf(image_parent, sizeof(image_parent), "%.*s", (int)(base - source), source);
            if (n < 0 || n >= (int)sizeof(image_parent)) {
                strcpy(error, "Disc image path is too long.");
                return false;
            }
        }
    }
    if (!import_path(work, root, ".local-import-v1") || !remove_tree(work) || mkdir(work, 0777)) {
        strcpy(error, why);
        return false;
    }
    if (!import_path(stage, work, "source"))
        goto done;
    const char *search = folder ? source : stage;
    if (!folder && !direct_set) {
        why = "Could not extract archive. It may be invalid, unsupported or too large for the SD card.";
        if (!import_mkdir(stage) || !unpack(source, stage, cb))
            goto done;
    }
    why = "No playable System 3, 3.5 or 4 game data was found.";
    if (!direct_set && !scan_roots(search, 0, &primary, cb))
        goto done;
    char images_dir[HUB_PATH_SIZE];
    if (!*primary.path && (folder || direct_set || import_suffix(source, ".zip"))) {
        bool found;
        why = "Could not read the disc set. Choose one game folder or one numbered set of ISO images.";
        if (!import_path(images_dir, work, "discs") ||
            !extract_images(direct_set ? image_parent : search, images_dir, cb,
                            direct_set ? set : NULL, &found))
            goto done;
        why = "No playable System 3, 3.5 or 4 game data was found.";
        if (!found || !scan_roots(images_dir, 0, &primary, cb))
            goto done;
        search = images_dir;
        move = true;
    }
    if (!*primary.path) goto done;
    char parent[HUB_PATH_SIZE], member[HUB_NAME_SIZE], key[HUB_NAME_SIZE];
    unsigned disc_number;
    bool disc = disc_ancestor(search, primary.path, parent, member, key, &disc_number);
    bool use_source = !primary.depth || (disc && !*key && (!strcmp(search, parent) || search == images_dir));
    const char *label = use_source ? leaf(source) : disc ? (*key ? key : leaf(parent)) : leaf(primary.path);
    if (strlen(label) >= sizeof(name)) goto done;
    strcpy(name, label);
    if (!folder && use_source) {
        char *dot = strrchr(name, '.');
        if (dot) *dot = 0;
    }
    why = "The game folder name is invalid or too long.";
    if (!import_leaf(name) || !hub_game_init(game, primary.markers.engine, name) ||
        !hub_game_path(root, game, dest))
        goto done;
    why = "Could not prepare the game directory on SD. Try again after other installs finish.";
    if (!(tx = hub_install_begin(root, game)))
        goto done;
    if (!lstat(dest, &st)) {
        why = "A game with this folder name is already installed.";
        goto done;
    }
    why = "Could not stage the game. Check the available SD card space.";
    if (errno != ENOENT)
        goto done;
    const char *payload = hub_install_stage(tx);
    if (move ? rmdir(payload) || hub_rename_dir(primary.path, payload) : !copy_tree(primary.path, payload, 0, cb))
        goto done;
    if (disc && !merge_discs(parent, member, key, &primary, payload, move, cb))
        goto done;
    if (primary.markers.engine == HUB_SYSTEM3) {
        copy_companion_pc98_font(folder ? source : work, payload, 0, cb);
    }
    why = "Could not publish the game to the library.";
    if (!import_update(cb, "Adding game to the library", 1, 1) ||
        !hub_install_verify(tx, verify_stage, game) || !lstat(dest, &st) ||
        errno != ENOENT || !hub_install_commit_new(tx))
        goto done;
    tx = NULL;
    ok = true;
done:
    if (tx && !hub_install_rollback(tx))
        why = "Could not clean up the install. Restart AliceHub before retrying.";
    remove_tree(work);
    if (!ok) strcpy(error, why);
    return ok;
}
