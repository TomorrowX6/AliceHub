#include "ArchiveExtract.h"
#include <archive.h>
#include <archive_entry.h>
#include <errno.h>
#include <fcntl.h>
#include <iconv.h>
#include <locale.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

char *AHDecodeLegacyText(const unsigned char *bytes, size_t size, const char *encoding)
{
    if (size > (SIZE_MAX - 1) / 4) return NULL;
    iconv_t converter = iconv_open("UTF-8", encoding);
    if (converter == (iconv_t)-1) return NULL;
    size_t capacity = size * 4, remaining = size, room = capacity;
    char *result = malloc(capacity + 1);
    if (!result) { iconv_close(converter); return NULL; }
    char *input = (char *)bytes, *output = result;
    while (remaining) {
        if (iconv(converter, &input, &remaining, &output, &room) != (size_t)-1) break;
        if (errno != EILSEQ && errno != EINVAL) { free(result); iconv_close(converter); return NULL; }
        memcpy(output, "\xEF\xBF\xBD", 3);
        output += 3;
        room -= 3;
        input++;
        remaining--;
        iconv(converter, NULL, NULL, NULL, NULL);
    }
    *output = 0;
    iconv_close(converter);
    return result;
}

struct extracted_path {
    char *name;
    char *path;
    struct extracted_path *next;
};

static char *relative_link_name(const char *name)
{
    if (!name || !*name || *name == '/' || *name == '\\' || strchr(name, ':')) return NULL;
    char *result = strdup(name);
    if (!result) return NULL;
    char *out = result;
    const char *p = name;
    while (*p) {
        const char *start = p;
        while (*p && *p != '/' && *p != '\\') p++;
        size_t length = (size_t)(p - start);
        if (length == 2 && start[0] == '.' && start[1] == '.') { free(result); return NULL; }
        if (length && !(length == 1 && start[0] == '.')) {
            if (out != result) *out++ = '/';
            memcpy(out, start, length);
            out += length;
        }
        if (*p) p++;
    }
    *out = 0;
    return result;
}

static int replace_hard_link(const char *source, const char *destination)
{
    size_t length = strlen(destination) + sizeof(".link.XXXXXX");
    char *temporary = malloc(length);
    if (!temporary) { errno = ENOMEM; return -1; }
    snprintf(temporary, length, "%s.link.XXXXXX", destination);
    int fd = mkstemp(temporary);
    if (fd < 0) { free(temporary); return -1; }
    close(fd);
    unlink(temporary);
    int result = link(source, temporary);
    if (!result) result = rename(temporary, destination);
    int saved_errno = errno;
    unlink(temporary);
    free(temporary);
    errno = saved_errno;
    return result;
}

int AHExtractArchiveFD(int fd, void *context, AHArchiveEntry on_entry,
                       AHArchiveProgress progress, char *error, size_t error_size)
{
    locale_t utf8_locale = newlocale(LC_CTYPE_MASK, "en_US.UTF-8", NULL);
    locale_t previous_locale = utf8_locale ? uselocale(utf8_locale) : (locale_t)0;
    struct archive *a = archive_read_new();
    char *path = NULL;
    int out = -1, count = -1, files = 0;
    int64_t total = 0;
    const char *failure = NULL;
    struct extracted_path *paths = NULL;
    if (!a) { failure = "内存不足"; goto done; }
    archive_read_support_filter_all(a);
    archive_read_support_format_zip(a);
    archive_read_support_format_rar(a);
    archive_read_support_format_rar5(a);
    archive_read_support_format_7zip(a);
    archive_read_support_format_tar(a);
    if (lseek(fd, 0, SEEK_SET) < 0) { failure = strerror(errno); goto done; }
    if (archive_read_open_fd(a, fd, 256 * 1024) != ARCHIVE_OK) goto archive_error;
    for (;;) {
        struct archive_entry *entry;
        int r = archive_read_next_header(a, &entry);
        if (r == ARCHIVE_EOF) break;
        if (r < ARCHIVE_WARN) goto archive_error;
        if (archive_entry_is_encrypted(entry)) { failure = "压缩包已加密，暂不支持密码"; goto done; }
        mode_t type = archive_entry_filetype(entry);
        if ((type != AE_IFREG && type != AE_IFDIR && type != 0) ||
            archive_entry_symlink(entry)) {
            archive_read_data_skip(a);
            continue;
        }
        /* Do not request libarchive's locale-dependent pathname conversion. */
        const char *raw = archive_entry_pathname(entry);
        if (!raw || !*raw) { failure = "无法解码压缩包文件名"; goto done; }
        int64_t size = archive_entry_size_is_set(entry) ? archive_entry_size(entry) : -1;
        size_t raw_length = strlen(raw);
        char *directory_path = NULL;
        if (type == AE_IFDIR && raw[raw_length - 1] != '/') {
            directory_path = malloc(raw_length + 2);
            if (!directory_path) { failure = "内存不足"; goto done; }
            memcpy(directory_path, raw, raw_length);
            directory_path[raw_length++] = '/';
            directory_path[raw_length] = 0;
        }
        int accepted = on_entry(context, (const unsigned char *)(directory_path ? directory_path : raw), raw_length, size, &path);
        free(directory_path);
        if (accepted < 0) { failure = "已取消解压"; goto done; }
        if (!accepted || !path) { archive_read_data_skip(a); continue; }
        const char *hardlink = archive_entry_hardlink(entry);
        if (hardlink) {
            char *target = relative_link_name(hardlink);
            if (!target) { failure = "压缩包包含不安全的硬链接"; goto done; }
            struct extracted_path *source = paths;
            while (source && strcmp(source->name, target)) source = source->next;
            free(target);
            if (!source) { failure = "压缩包硬链接的目标尚未解压"; goto done; }
            if (replace_hard_link(source->path, path) < 0) goto system_error;
            archive_read_data_skip(a);
            goto entry_complete;
        }
        out = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, 0644);
        if (out < 0) goto system_error;
        for (;;) {
            const void *block;
            size_t length;
            la_int64_t offset;
            r = archive_read_data_block(a, &block, &length, &offset);
            if (r == ARCHIVE_EOF) break;
            if (r < ARCHIVE_WARN) goto archive_error;
            if (lseek(out, offset, SEEK_SET) < 0) goto system_error;
            const char *p = block;
            size_t remaining = length;
            while (remaining) {
                ssize_t n = write(out, p, remaining);
                if (n < 0 && errno == EINTR) continue;
                if (n <= 0) goto system_error;
                p += n;
                remaining -= n;
            }
            total += length;
            if (progress && !progress(context, total)) { failure = "已取消解压"; goto done; }
        }
        if (size >= 0 && ftruncate(out, size) < 0) goto system_error;
        if (close(out) < 0) { out = -1; goto system_error; }
        out = -1;
entry_complete:;
        char *name = relative_link_name(raw);
        if (name) {
            struct extracted_path *saved = malloc(sizeof(*saved));
            if (!saved) { free(name); failure = "内存不足"; goto done; }
            saved->name = name;
            saved->path = path;
            saved->next = paths;
            paths = saved;
            path = NULL;
        }
        free(path);
        path = NULL;
        files++;
    }
    if (progress && !progress(context, total)) { failure = "已取消解压"; goto done; }
    count = files;
    goto done;
system_error:
    failure = (errno == ENOSPC || errno == EDQUOT) ? "存储空间不足" : strerror(errno);
    goto done;
archive_error:
    failure = archive_error_string(a);
done:
    if (count < 0) snprintf(error, error_size, "%s", failure ? failure : "压缩包损坏或格式不受支持");
    if (out >= 0) close(out);
    if (count < 0 && path) unlink(path);
    free(path);
    while (paths) {
        struct extracted_path *next = paths->next;
        free(paths->name);
        free(paths->path);
        free(paths);
        paths = next;
    }
    archive_read_free(a);
    if (utf8_locale) { uselocale(previous_locale); freelocale(utf8_locale); }
    return count;
}
