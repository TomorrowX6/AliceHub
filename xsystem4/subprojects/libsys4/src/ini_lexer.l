/* Copyright (C) 2019 Nunuhara Cabbage <nunuhara@haniwa.technology>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://gnu.org/licenses/>.
 */

%{

#pragma GCC diagnostic ignored "-Wunused-function"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ini_parser.tab.h"
#include "system4.h"
#include "system4/string.h"
#include "system4/utfsjis.h"

char string_buf[65536];
char *string_buf_ptr;

unsigned long yini_line = 1;

static int parse_unquoted_rhs(const char *text, size_t len)
{
    size_t end = len;
    for (size_t i = 0; i < len; i++) {
        if (text[i] == ';' || (text[i] == '/' && i + 1 < len && text[i + 1] == '/') ||
            (text[i] == '#' && (i == 0 || text[i - 1] == ' ' || text[i - 1] == '\t'))) {
            end = i;
            break;
        }
    }
    while (end > 0 && (text[end - 1] == ' ' || text[end - 1] == '\t' || text[end - 1] == '\r'))
        end--;
    if (end == 4 && !memcmp(text, "true", 4))
        return TRUE;
    if (end == 5 && !memcmp(text, "false", 5))
        return FALSE;
    if (end > 0) {
        size_t i = 0;
        while (i < end && isdigit((unsigned char)text[i]))
            i++;
        if (i == end) {
            char num[64];
            size_t n = end < sizeof(num) - 1 ? end : sizeof(num) - 1;
            memcpy(num, text, n);
            num[n] = '\0';
            yini_lval.i = atoi(num);
            return INTEGER;
        }
        if (i > 0 && i < end && text[i] == '.') {
            size_t j = i + 1;
            while (j < end && isdigit((unsigned char)text[j]))
                j++;
            if (j == end && j > i + 1) {
                char num[64];
                size_t n = end < sizeof(num) - 1 ? end : sizeof(num) - 1;
                memcpy(num, text, n);
                num[n] = '\0';
                yini_lval.f = strtof(num, NULL);
                return FLOAT;
            }
        }
    }
    if (end >= sizeof(string_buf))
        end = sizeof(string_buf) - 1;
    memcpy(string_buf, text, end);
    string_buf[end] = '\0';
    yini_lval.s = sys4_source_make_string(string_buf, end);
    return STRING;
}

%}

%option noyywrap
%option prefix="yini_"

%x str
%x rhs

%%

\/\/[^\n]*                ;
[ \t\r]                   ;
\n                        yini_line++;
;[^\n]*                   ;
\{                        return '{';
\}                        return '}';
\[                        return '[';
\]                        return ']';
=                         BEGIN(rhs); return '=';
,                         return ',';
true                      return TRUE;
false                     return FALSE;
Formation                 return FORMATION;
[0-9]+\.[0-9]+            yini_lval.f = strtof(yini_text, NULL); return FLOAT;
[0-9]+                    yini_lval.i = atoi(yini_text); return INTEGER;
[a-zA-Z]+[a-zA-Z0-9_-]*   yini_lval.s = make_string(yini_text, yini_leng); return IDENTIFIER;

\"      string_buf_ptr = string_buf; BEGIN(str);

<rhs>{
    [ \t\r]+              ;
    \/\/[^\n]*            BEGIN(INITIAL);
    ;[^\n]*               BEGIN(INITIAL);
    \n                    yini_line++; BEGIN(INITIAL);
    \"                    string_buf_ptr = string_buf; BEGIN(str);
    \{                    BEGIN(INITIAL); return '{';
    [^ \t\r\n,\"\{\};][^\r\n,}]* {
        BEGIN(INITIAL);
        return parse_unquoted_rhs(yini_text, (size_t)yini_leng);
    }
}

<str>{
    \" {
        BEGIN(INITIAL);
        *string_buf_ptr = '\0';
        yini_lval.s = sys4_source_make_string(string_buf, string_buf_ptr - string_buf);
        return STRING;
    }

    \n   ERROR("Unterminated string literal at line %lu", yini_line);

    [^\n\"]+ {
        char *yptr = yini_text;
        while (*yptr && string_buf_ptr < string_buf + sizeof(string_buf) - 1)
            *string_buf_ptr++ = *yptr++;
    }
}

%%
