#ifndef ALICEHUB_PICKER_ART_H
#define ALICEHUB_PICKER_ART_H

#include <stdint.h>

uint16_t *picker_art_load(const char *path, int width, int height);
uint16_t *picker_art_catalog(const char *name, int width, int height);
uint16_t *picker_art_cover(const char *directory, int width, int height);

#endif
