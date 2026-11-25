#include "runtime_capture.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

bool switch_capture_write(const char *directory, const char *extension, time_t now,
                          bool (*capture)(void *, const char *), void *context,
                          char *filename, size_t size)
{
    if (!capture || (mkdir(directory, 0777) != 0 && errno != EEXIST))
        return false;
    struct tm utc;
    char stamp[24];
    if (!gmtime_r(&now, &utc) || !strftime(stamp, sizeof(stamp), "%Y%m%d-%H%M%S", &utc))
        strcpy(stamp, "capture");
    for (unsigned i = 0; i < 1000; ++i) {
        char path[512];
        int n = snprintf(path, sizeof(path), "%s/%s-%03u.%s", directory, stamp, i, extension);
        if (n < 0 || (size_t)n >= sizeof(path) || (size_t)n + 1 > size)
            return false;
        struct stat info;
        if (stat(path, &info) == 0)
            continue;
        if (errno != ENOENT)
            return false;
        if (!capture(context, path)) {
            unlink(path);
            return false;
        }
        memcpy(filename, path, (size_t)n + 1);
        return true;
    }
    return false;
}
