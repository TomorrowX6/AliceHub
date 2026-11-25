#include "fs_util.h"
#include "model.h"

#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static bool child_path(char path[HUB_PATH_SIZE], const char *base, const char *name)
{
    if (snprintf(path, HUB_PATH_SIZE, "%s/%s", base, name) < HUB_PATH_SIZE)
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
        char from[HUB_PATH_SIZE], to[HUB_PATH_SIZE];
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
