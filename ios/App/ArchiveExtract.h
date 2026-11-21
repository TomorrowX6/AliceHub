#ifndef ALICEHUB_ARCHIVE_EXTRACT_H
#define ALICEHUB_ARCHIVE_EXTRACT_H
#include <stddef.h>
#include <stdint.h>
/* Entry returns 1 with a malloc-owned UTF-8 output path, 0 to skip, -1 to cancel.
 * Progress returns nonzero to continue. The caller owns the fd and error buffer. */
typedef int (*AHArchiveEntry)(void *, const unsigned char *, size_t, int64_t, char **);
typedef int (*AHArchiveProgress)(void *, int64_t);
int AHExtractArchiveFD(int fd, void *context, AHArchiveEntry entry,
                       AHArchiveProgress progress, char *error, size_t error_size);
/* malloc-owned UTF-8 text, replacing malformed legacy input with U+FFFD. */
char *AHDecodeLegacyText(const unsigned char *bytes, size_t size, const char *encoding);
#endif
