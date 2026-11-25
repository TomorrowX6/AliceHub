#ifndef ALICEHUB_SUPPORT_TEXT_H
#define ALICEHUB_SUPPORT_TEXT_H

#include <stdbool.h>
#include <stddef.h>

typedef struct {
    char *text;
    char **lines;
    size_t count;
} SupportText;

bool support_text_load(SupportText *, const char *path, bool html);
void support_text_free(SupportText *);

#endif
