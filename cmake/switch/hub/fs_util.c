#include "fs_util.h"
#include "model.h"

#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#ifdef __SWITCH__
#include <switch.h>
#endif

enum { TREE_PATH_SIZE = 1024, TREE_MAX_DEPTH = 64 };

static bool child_path(char path[TREE_PATH_SIZE], const char *base, const char *name)
{
    int n = snprintf(path, TREE_PATH_SIZE, "%s/%s", base, name);
    if (n >= 0 && (size_t)n < TREE_PATH_SIZE)
        return true;
    errno = ENAMETOOLONG;
    return false;
}

static bool append_child(char *path, size_t base_len, const char *name)
{
    int n = snprintf(path + base_len, TREE_PATH_SIZE - base_len, "/%s", name);
    if (n >= 0 && (size_t)n < TREE_PATH_SIZE - base_len)
        return true;
    path[base_len] = '\0';
    errno = ENAMETOOLONG;
    return false;
}

static bool copy_file(const char *src, const char *dst, unsigned char *buffer, size_t capacity)
{
    FILE *in = fopen(src, "rb");
    if (!in) return false;
    FILE *out = fopen(dst, "wbx");
    bool ok = out != NULL;
    while (ok) {
        size_t n = fread(buffer, 1, capacity, in);
        ok = fwrite(buffer, 1, n, out) == n && !ferror(in);
        if (n < capacity) break;
    }
    if (out && fclose(out)) ok = false;
    fclose(in);
    return ok;
}

static bool walk_rec(char *src, char *dst, unsigned char *buffer, size_t capacity, unsigned depth)
{
    if (depth > TREE_MAX_DEPTH) {
        errno = ELOOP;
        return false;
    }
    DIR *dir = opendir(src);
    if (!dir) return false;
    size_t src_len = strlen(src);
    size_t dst_len = dst ? strlen(dst) : 0;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *entry = readdir(dir);
        if (!entry) {
            ok = !errno && ok;
            break;
        }
        if (!strcmp(entry->d_name, ".") || !strcmp(entry->d_name, ".."))
            continue;
        struct stat st;
        bool done = append_child(src, src_len, entry->d_name) && !lstat(src, &st);
        if (done && !dst) {
            done = S_ISDIR(st.st_mode) ? walk_rec(src, NULL, NULL, 0, depth + 1) && !rmdir(src) : !unlink(src);
        } else if (done && append_child(dst, dst_len, entry->d_name)) {
            if (S_ISDIR(st.st_mode)) {
                if (depth >= TREE_MAX_DEPTH) {
                    errno = ELOOP;
                    done = false;
                } else {
                    done = !mkdir(dst, 0777) && walk_rec(src, dst, buffer, capacity, depth + 1);
                }
            } else if (S_ISREG(st.st_mode))
                done = copy_file(src, dst, buffer, capacity);
            else {
                errno = EINVAL;
                done = false;
            }
        } else {
            done = false;
        }
        int saved_err = errno;
        src[src_len] = '\0';
        if (dst)
            dst[dst_len] = '\0';
        errno = saved_err;
        if (!done) {
            ok = false;
            break;
        }
    }
    int saved = errno;
    closedir(dir);
    errno = saved;
    return ok;
}

static bool walk(const char *src, const char *dst, unsigned char *buffer, size_t capacity)
{
    size_t src_len = strlen(src);
    size_t dst_len = dst ? strlen(dst) : 0;
    if (src_len >= TREE_PATH_SIZE || (dst && dst_len >= TREE_PATH_SIZE)) {
        errno = ENAMETOOLONG;
        return false;
    }
    char *src_buf = malloc(TREE_PATH_SIZE);
    char *dst_buf = dst ? malloc(TREE_PATH_SIZE) : NULL;
    if (!src_buf || (dst && !dst_buf)) {
        free(src_buf);
        free(dst_buf);
        errno = ENOMEM;
        return false;
    }
    memcpy(src_buf, src, src_len + 1);
    if (dst)
        memcpy(dst_buf, dst, dst_len + 1);
    bool ok = walk_rec(src_buf, dst_buf, buffer, capacity, 0);
    int saved = errno;
    free(src_buf);
    free(dst_buf);
    errno = saved;
    return ok;
}

static int check_directory_destination(const char *source, const char *destination)
{
    struct stat st;
    if (lstat(source, &st)) return -1;
    if (!S_ISDIR(st.st_mode)) {
        errno = ENOTDIR;
        return -1;
    }
    size_t prefix = strlen(source);
    while (prefix > 1 && source[prefix - 1] == '/')
        --prefix;
    if (prefix > 0 && !strncmp(source, destination, prefix) &&
        (destination[prefix] == '/' || (prefix == 1 && source[0] == '/' && destination[1] != '\0'))) {
        errno = EINVAL;
        return -1;
    }
    if (!lstat(destination, &st)) {
        errno = EEXIST;
        return -1;
    }
    return errno == ENOENT ? 0 : -1;
}

static int copy_directory(const char *source, const char *destination)
{
    size_t capacity = 64 * 1024;
    unsigned char *buffer = malloc(capacity);
    if (!buffer) return -1;
    if (mkdir(destination, 0777)) {
        free(buffer);
        return -1;
    }
    bool ok = walk(source, destination, buffer, capacity);
    int saved = errno;
    free(buffer);
    if (!ok) {
        if (walk(destination, NULL, NULL, 0))
            rmdir(destination);
        errno = saved ? saved : EIO;
        return -1;
    }
    return 0;
}

int hub_copy_dir(const char *source, const char *destination)
{
    return check_directory_destination(source, destination) ? -1 : copy_directory(source, destination);
}

int hub_rename_dir(const char *source, const char *destination)
{
    if (check_directory_destination(source, destination)) return -1;
    if (!rename(source, destination)) return 0;
    if (copy_directory(source, destination)) return -1;
    if (walk(source, NULL, NULL, 0))
        rmdir(source);
    return 0;
}

static bool save_flush(void)
{
#ifdef __SWITCH__
    if (R_FAILED(fsdevCommitDevice("sdmc"))) {
        errno = EIO;
        return false;
    }
#endif
    return true;
}

static bool remove_save_tree(const char *path)
{
    struct stat st;
    if (lstat(path, &st))
        return errno == ENOENT;
    if (!S_ISDIR(st.st_mode)) {
        errno = ENOTDIR;
        return false;
    }
    return walk(path, NULL, NULL, 0) && !rmdir(path);
}

static bool finish_save_publish(const char *journal)
{
    return save_flush() && !unlink(journal) && save_flush();
}

static bool rollback_save_publish(const char *destination, const char *previous,
                                  const char *journal, bool existed)
{
    struct stat st;
    if (existed && (lstat(previous, &st) || !S_ISDIR(st.st_mode)))
        return false;
    return remove_save_tree(destination) &&
           (!existed || !hub_copy_dir(previous, destination)) &&
           finish_save_publish(journal);
}

bool hub_save_publish(const char *stage, const char *destination, bool existed, bool *pending)
{
    char next[TREE_PATH_SIZE], previous[TREE_PATH_SIZE], journal[TREE_PATH_SIZE], temp[TREE_PATH_SIZE];
    *pending = false;
    if (!child_path(next, stage, "next") || !child_path(previous, stage, "previous") ||
        !child_path(journal, stage, "publishing") || !child_path(temp, stage, "intent"))
        return false;
    const char *leaf = strrchr(destination, '/');
    leaf = leaf ? leaf + 1 : destination;
    if (existed && hub_copy_dir(destination, previous))
        return false;
    FILE *file = fopen(temp, "wbx");
    if (!file)
        return false;
    bool written = fputc(existed ? '1' : '0', file) != EOF && fputs(leaf, file) != EOF;
    if (fclose(file) || !written || !save_flush() || rename(temp, journal))
        return false;
    *pending = true;
    if (!save_flush())
        return false;
    if ((!existed || remove_save_tree(destination)) &&
        (!rename(next, destination) || !hub_copy_dir(next, destination))) {
        if (!finish_save_publish(journal))
            return false;
        *pending = false;
        return true;
    }
    int failure = errno;
    if (rollback_save_publish(destination, previous, journal, existed))
        *pending = false;
    errno = failure;
    return false;
}

bool hub_save_recover(const char *destination)
{
    char parent[TREE_PATH_SIZE], stage[TREE_PATH_SIZE], journal[TREE_PATH_SIZE], previous[TREE_PATH_SIZE];
    const char *slash = strrchr(destination, '/');
    const char *leaf = slash ? slash + 1 : destination;
    int n = slash ? snprintf(parent, sizeof(parent), "%.*s", (int)(slash - destination), destination)
                  : snprintf(parent, sizeof(parent), ".");
    if (n < 0 || (size_t)n >= sizeof(parent)) {
        errno = ENAMETOOLONG;
        return false;
    }
    DIR *dir = opendir(*parent ? parent : "/");
    if (!dir)
        return errno == ENOENT;
    bool ok = true;
    for (;;) {
        errno = 0;
        struct dirent *entry = readdir(dir);
        if (!entry) {
            ok = !errno;
            break;
        }
        const char *name = entry->d_name;
        if (strlen(name) != 16 || strncmp(name, ".save-stage-", 12) ||
            strspn(name + 12, "0123456789") != 4)
            continue;
        if (!child_path(stage, parent, name) || !child_path(journal, stage, "publishing") ||
            !child_path(previous, stage, "previous")) {
            ok = false;
            break;
        }
        FILE *file = fopen(journal, "rb");
        if (!file) {
            if (errno == ENOENT)
                continue;
            ok = false;
            break;
        }
        int flag = fgetc(file);
        char target[HUB_NAME_SIZE];
        size_t length = fread(target, 1, sizeof(target), file);
        bool valid = (flag == '0' || flag == '1') && length && length < sizeof(target) && !ferror(file);
        fclose(file);
        if (!valid) {
            errno = EINVAL;
            ok = false;
            break;
        }
        if (strlen(leaf) != length || memcmp(leaf, target, length))
            continue;
        if (!rollback_save_publish(destination, previous, journal, flag == '1')) {
            ok = false;
            break;
        }
        remove_save_tree(stage);
    }
    int saved = errno;
    closedir(dir);
    errno = saved;
    return ok;
}
