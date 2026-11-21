#ifndef ALICEHUB_ARCHIVE_BRIDGE_H
#define ALICEHUB_ARCHIVE_BRIDGE_H
#include <stdint.h>
#include <stddef.h>
/* Return a malloc-owned output path, or NULL to skip. Callbacks are synchronous. */
typedef char *(*AHModArchiveEntry)(void *, const uint8_t *, size_t, int64_t);
/* Nonzero cancels extraction. Called before headers and after each data chunk. */
typedef int (*AHModArchiveProgress)(void *, int64_t);
/* Returns malloc-owned UTF-8, replacing malformed source bytes with U+FFFD. */
char *ah_archive_decode(const uint8_t *bytes, size_t size, const char *encoding);
int ah_archive_extract(const char *file, AHModArchiveEntry entry, AHModArchiveProgress progress,
                       void *context, char *error, size_t error_size);
#endif
