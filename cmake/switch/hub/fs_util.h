#ifndef ALICEHUB_FS_UTIL_H
#define ALICEHUB_FS_UTIL_H

#include <stdbool.h>

/* Copy to a new path without removing the source. */
int hub_copy_dir(const char *source, const char *destination);

/* Publish a private staging directory to a new path. If directory rename is
 * unavailable, the destination is copied before the source is discarded. */
int hub_rename_dir(const char *source, const char *destination);

bool hub_save_recover(const char *destination);
bool hub_save_publish(const char *stage, const char *destination, bool existed, bool *pending);

#endif
