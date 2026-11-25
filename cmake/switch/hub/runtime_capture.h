#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <time.h>

bool switch_capture_write(const char *directory, const char *extension, time_t now,
                          bool (*capture)(void *, const char *), void *context,
                          char *filename, size_t size);
