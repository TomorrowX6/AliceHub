#ifndef ALICEHUB_FS_UTIL_H
#define ALICEHUB_FS_UTIL_H

/* Publish a private staging directory to a new path. If directory rename is
 * unavailable, the destination is copied before the source is discarded. */
int hub_rename_dir(const char *source, const char *destination);

#endif
