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

enum { TREE_PATH_SIZE = 1024 };

static bool child_path(char path[TREE_PATH_SIZE], const char *base, const char *name)
{
    if (snprintf(path, TREE_PATH_SIZE, "%s/%s", base, name) < TREE_PATH_SIZE)
        return true;
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

static bool walk(const char *src, const char *dst, unsigned char *buffer, size_t capacity)
{
    DIR *dir = opendir(src);
    if (!dir) return false;
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
        char from[TREE_PATH_SIZE], to[TREE_PATH_SIZE];
        struct stat st;
        bool done = child_path(from, src, entry->d_name) && !lstat(from, &st);
        if (done && !dst) {
            done = S_ISDIR(st.st_mode) ? walk(from, NULL, NULL, 0) && !rmdir(from) : !unlink(from);
        } else if (done && child_path(to, dst, entry->d_name)) {
            if (S_ISDIR(st.st_mode))
                done = !mkdir(to, 0777) && walk(from, to, buffer, capacity);
            else if (S_ISREG(st.st_mode))
                done = copy_file(from, to, buffer, capacity);
            else {
                errno = EINVAL;
                done = false;
            }
        } else {
            done = false;
        }
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

static int check_directory_destination(const char *source, const char *destination)
{
    struct stat st;
    if (lstat(source, &st)) return -1;
    if (!S_ISDIR(st.st_mode)) {
        errno = ENOTDIR;
        return -1;
    }
    size_t prefix = strlen(source);
    if (!strncmp(source, destination, prefix) && destination[prefix] == '/') {
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
    if (n >= (int)sizeof(parent)) {
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
