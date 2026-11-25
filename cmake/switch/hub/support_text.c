#include "support_text.h"

#include <libxml/HTMLparser.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <system4/utfsjis.h>

enum { TEXT_LIMIT = 256 * 1024, LINE_WIDTH = 50 };

void support_text_free(SupportText *doc)
{
    free(doc->text);
    free(doc->lines);
    *doc = (SupportText){0};
}

static bool tag_is(xmlNode *node, const char *name)
{
    return !xmlStrcasecmp(node->name, BAD_CAST name);
}

static bool is_block(xmlNode *node)
{
    const char *const tags[] = {"p", "br", "div", "li", "tr", "td", "th", "hr", "pre",
                               "h1", "h2", "h3", "h4", "h5", "h6", "section"};
    for (size_t i = 0; i < sizeof(tags) / sizeof(*tags); i++)
        if (tag_is(node, tags[i]))
            return true;
    return false;
}

static void paragraph(char *out, size_t *pos)
{
    if (*pos && out[*pos - 1] == ' ')
        --*pos;
    if (*pos && out[*pos - 1] != '\n' && *pos < TEXT_LIMIT)
        out[(*pos)++] = '\n';
}

static bool ignored(xmlNode *node)
{
    return node->type == XML_ELEMENT_NODE &&
           (tag_is(node, "head") || tag_is(node, "script") || tag_is(node, "style"));
}

static void flatten_html(xmlNode *node, char *out, size_t *pos)
{
    for (; node && *pos < TEXT_LIMIT; node = node->next) {
        if (ignored(node))
            continue;
        if (node->type == XML_ELEMENT_NODE) {
            bool block = is_block(node);
            if (block)
                paragraph(out, pos);
            flatten_html(node->children, out, pos);
            if (block)
                paragraph(out, pos);
        } else if (node->type == XML_TEXT_NODE && node->content) {
            for (const xmlChar *p = node->content; *p && *pos < TEXT_LIMIT; ++p) {
                if (*p == '\n' || *p == '\r' || *p == '\t' || *p == ' ') {
                    if (*pos && out[*pos - 1] != ' ' && out[*pos - 1] != '\n')
                        out[(*pos)++] = ' ';
                } else {
                    out[(*pos)++] = (char)*p;
                }
            }
        }
    }
}

static size_t utf8_chars(const unsigned char *p)
{
    if (*p < 0x80)
        return 1;
    size_t n = *p >= 0xF0 ? 4 : *p >= 0xE0 ? 3 : *p >= 0xC2 ? 2 : 1;
    for (size_t i = 1; i < n; i++)
        if ((p[i] & 0xC0) != 0x80)
            return 1;
    return n;
}

static bool build_lines(SupportText *doc, const char *source)
{
    size_t length = strlen(source);
    doc->text = malloc(2 * length + 1);
    doc->lines = malloc((length + 1) * sizeof(*doc->lines));
    if (!doc->text || !doc->lines)
        return false;
    char *out = doc->text;
    while (*source) {
        const char *end = source, *space = NULL;
        size_t width = 0;
        while (*end && *end != '\n') {
            size_t n = utf8_chars((const unsigned char *)end);
            size_t cells = n >= 3 || *end == 'W' || *end == 'M' ? 2 : 1;
            if (width + cells > LINE_WIDTH)
                break;
            width += cells;
            if (*end == ' ' && end != source)
                space = end;
            end += n;
        }
        if (*end && *end != '\n' && *end != ' ' && space)
            end = space;
        doc->lines[doc->count++] = out;
        for (const char *p = source; p < end; ) {
            size_t n = utf8_chars((const unsigned char *)p);
            if (n == 1 && ((unsigned char)*p < 0x20 || (unsigned char)*p >= 0x80))
                *out++ = ' ';
            else {
                memcpy(out, p, n);
                out += n;
            }
            p += n;
        }
        *out++ = '\0';
        source = end;
        if (*source == '\n')
            ++source;
        else
            while (*source == ' ')
                ++source;
    }
    return doc->count > 0;
}

bool support_text_load(SupportText *doc, const char *path, bool html)
{
    support_text_free(doc);
    FILE *in = fopen(path, "rb");
    if (!in)
        return false;
    char *source = malloc(TEXT_LIMIT + 1);
    size_t n = source ? fread(source, 1, TEXT_LIMIT, in) : 0;
    bool ok = n && !ferror(in);
    fclose(in);
    if (ok) {
        source[n] = '\0';
        if (html) {
            char *decoded = NULL;
            bool utf16 = n >= 2 && (!memcmp(source, "\xff\xfe", 2) || !memcmp(source, "\xfe\xff", 2));
            if (!utf16 && !xmlCheckUTF8(BAD_CAST source))
                decoded = cjk2utf(source, n, false);
            htmlDocPtr tree = htmlReadMemory(decoded ? decoded : source, decoded ? strlen(decoded) : n,
                                             NULL, utf16 ? NULL : "UTF-8", HTML_PARSE_NONET |
                                             HTML_PARSE_NOERROR | HTML_PARSE_NOWARNING | HTML_PARSE_RECOVER);
            free(decoded);
            ok = tree != NULL;
            if (tree) {
                size_t pos = 0;
                flatten_html(xmlDocGetRootElement(tree), source, &pos);
                source[pos] = '\0';
                xmlFreeDoc(tree);
            }
        }
        ok = ok && build_lines(doc, source);
    }
    free(source);
    if (!ok)
        support_text_free(doc);
    return ok;
}
