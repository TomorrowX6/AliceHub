#include "ArchiveBridge.h"
#include <archive.h>
#include <archive_entry.h>
#include <errno.h>
#include <fcntl.h>
#include <iconv.h>
#include <locale.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static pthread_once_t locale_once = PTHREAD_ONCE_INIT;
struct extracted_path {
    char *name;
    char *path;
    struct extracted_path *next;
};
static void init_locale(void)
{
    setlocale(LC_CTYPE, "en_US.UTF-8");
}

char *ah_archive_decode(const uint8_t *bytes, size_t size, const char *encoding)
{
    if (size > (SIZE_MAX - 1) / 4) return NULL;
    iconv_t converter = iconv_open("UTF-8", encoding);
    if (converter == (iconv_t)-1) return NULL;
    char *result = malloc(size * 4 + 1);
    if (!result) { iconv_close(converter); return NULL; }
    char *input = (char *)bytes, *output = result;
    size_t remaining = size, available = size * 4;
    while (remaining) {
        if (iconv(converter, &input, &remaining, &output, &available) != (size_t)-1) break;
        if (errno != EILSEQ && errno != EINVAL) {
            free(result); iconv_close(converter); return NULL;
        }
        memcpy(output, "\xef\xbf\xbd", 3);
        output += 3; available -= 3; input++; remaining--;
        iconv(converter, NULL, NULL, NULL, NULL);
    }
    *output = 0;
    iconv_close(converter);
    return result;
}

int ah_archive_extract(const char *file, AHArchiveEntry accept, AHArchiveProgress progress,
                       void *context, char *error, size_t error_size)
{
    pthread_once(&locale_once, init_locale);
    struct archive *a = archive_read_new();
    if (!a) { snprintf(error, error_size, "Cannot allocate archive reader"); return -1; }
    archive_read_support_format_zip(a);
    archive_read_support_format_rar(a);
    archive_read_support_format_rar5(a);
    archive_read_support_format_7zip(a);
    archive_read_support_format_tar(a);
    archive_read_support_format_iso9660(a);
    archive_read_support_filter_all(a);
    int result = -1, fd = -1, source_fd = -1, count = 0, opened = 0;
    struct extracted_path *paths = NULL;
    int64_t total = 0;
    char *path = NULL;
    const char *message = NULL;
    if (archive_read_open_filename(a, file, 131072) != ARCHIVE_OK) goto archive_error;
    for (;;) {
        if (progress(context, total)) { message = "Extraction cancelled"; goto cleanup; }
        struct archive_entry *entry;
        int status = archive_read_next_header(a, &entry);
        if (status == ARCHIVE_EOF) break;
        if (status != ARCHIVE_OK && status != ARCHIVE_WARN) goto archive_error;
        if (archive_entry_is_encrypted(entry)) { message = "ENCRYPTED: Archive is password-protected"; goto cleanup; }
        mode_t type = archive_entry_filetype(entry);
        if ((type != AE_IFREG && type != 0) || archive_entry_symlink(entry)) {
            if (archive_read_data_skip(a) < ARCHIVE_WARN) goto archive_error;
            continue;
        }
        const char *raw = archive_entry_pathname(entry);
        if (!raw || !*raw) continue;
        path = accept(context, (const uint8_t *)raw, strlen(raw),
                      archive_entry_size_is_set(entry) ? archive_entry_size(entry) : -1);
        if (progress(context, total)) { message = "Extraction cancelled"; goto cleanup; }
        if (!path) {
            if (archive_read_data_skip(a) < ARCHIVE_WARN) goto archive_error;
            continue;
        }
        const char *hardlink = archive_entry_hardlink(entry);
        if (hardlink) {
            struct extracted_path *source = paths;
            while (source && strcmp(source->name, hardlink)) source = source->next;
            if (!source || !strcmp(source->path, path)) {
                message = "Archive hard link target was not previously extracted or is invalid";
                goto cleanup;
            }
            source_fd = open(source->path, O_RDONLY | O_NOFOLLOW);
            if (source_fd < 0) goto system_error;
        }
        fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_NOFOLLOW, 0600);
        if (fd < 0) goto system_error;
        opened = 1;
        char buffer[131072];
        for (;;) {
            la_ssize_t n = source_fd >= 0 ? read(source_fd, buffer, sizeof(buffer)) : archive_read_data(a, buffer, sizeof(buffer));
            if (n < 0) {
                if (source_fd >= 0) {
                    if (errno == EINTR) continue;
                    goto system_error;
                }
                goto archive_error;
            }
            if (!n) break;
            for (la_ssize_t offset = 0; offset < n;) {
                ssize_t written = write(fd, buffer + offset, (size_t)(n - offset));
                if (written < 0 && errno == EINTR) continue;
                if (written <= 0) goto system_error;
                offset += written;
            }
            total += n;
            if (progress(context, total)) { message = "Extraction cancelled"; goto cleanup; }
        }
        if (close(fd) != 0) { fd = -1; goto system_error; }
        fd = -1;
        if (source_fd >= 0) {
            close(source_fd); source_fd = -1;
            if (archive_read_data_skip(a) < ARCHIVE_WARN) goto archive_error;
        }
        struct extracted_path *saved = calloc(1, sizeof(*saved));
        if (!saved) { message = "Cannot allocate extracted path"; goto cleanup; }
        saved->name = strdup(raw);
        if (!saved->name) { free(saved); message = "Cannot allocate extracted path"; goto cleanup; }
        saved->path = path; saved->next = paths; paths = saved;
        path = NULL;
        opened = 0;
        count++;
    }
    result = count;
    goto cleanup;
archive_error:
    message = archive_error_string(a);
    if (!message) message = "Corrupt or unsupported archive";
    goto cleanup;
system_error:
    message = strerror(errno);
cleanup:
    if (result < 0) snprintf(error, error_size, "%s", message ? message : "Extraction failed");
    if (fd >= 0) close(fd);
    if (source_fd >= 0) close(source_fd);
    if (path) { if (opened) unlink(path); free(path); }
    while (paths) {
        struct extracted_path *next = paths->next;
        free(paths->name); free(paths->path); free(paths); paths = next;
    }
    archive_read_free(a);
    return result;
}
