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

#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <ctype.h>

#include "system4.h"
#include "system4/file.h"
#include "system4/utfsjis.h"

#include "xsystem4.h"
#include "debugger.h"

// In a Windows environment, console output should be SJIS-encoded
// (assuming the user is using Japanese non-unicode locale).
#if (defined(_WIN32) || defined(__WIN32))
#define NATIVE_SJIS
#endif

const char *display_sjis0(const char *sjis)
{
	if (!sjis)
		return "";
#ifdef NATIVE_SJIS
	return sjis;
#else
	static _Thread_local char *utf = NULL;
	free(utf);
	utf = sjis2utf(sjis, 0);
	return utf;
#endif
}

const char *display_sjis1(const char *sjis)
{
	if (!sjis)
		return "";
#ifdef NATIVE_SJIS
	return sjis;
#else
	static _Thread_local char *utf = NULL;
	free(utf);
	utf = sjis2utf(sjis, 0);
	return utf;
#endif
}

const char *display_sjis2(const char *sjis)
{
	if (!sjis)
		return "";
#ifdef NATIVE_SJIS
	return sjis;
#else
	static _Thread_local char *utf = NULL;
	free(utf);
	utf = sjis2utf(sjis, 0);
	return utf;
#endif
}

const char *display_utf0(const char *utf)
{
	if (!utf)
		return "";
#ifdef NATIVE_SJIS
	static _Thread_local char *sjis = NULL;
	free(sjis);
	sjis = utf2sjis(utf, 0);
	return sjis;
#else
	return utf;
#endif
}

const char *display_utf1(const char *utf)
{
	if (!utf)
		return "";
#ifdef NATIVE_SJIS
	static _Thread_local char *sjis = NULL;
	free(sjis);
	sjis = utf2sjis(utf, 0);
	return sjis;
#else
	return utf;
#endif
}

const char *display_utf2(const char *utf)
{
	if (!utf)
		return "";
#ifdef NATIVE_SJIS
	static _Thread_local char *sjis = NULL;
	free(sjis);
	sjis = utf2sjis(utf, 0);
	return sjis;
#else
	return utf;
#endif
}

static char *unix_path_with(const char *path, bool legacy_gbk)
{
	if (!path)
		return xstrdup("");
	char *utf = legacy_gbk ? sjis2utf(path, strlen(path)) : sjis_filename2utf(path, strlen(path));
	for (int i = 0; utf[i]; i++) {
		if (utf[i] == '\\')
			utf[i] = '/';
	}
	return utf;
}

char *unix_path(const char *path)
{
	return unix_path_with(path, false);
}

bool is_absolute_path(const char *path)
{
	if (!path || !path[0])
		return false;
#if (defined(_WIN32) || defined(__WIN32__))
	int i = (isalpha((unsigned char)path[0]) && path[1] == ':') ? 2 : 0;
	return path[i] == '/' || path[i] == '\\';
#else
	return path[0] == '/';
#endif
}

static bool strip_cjk_dir_prefix(const char *dir, const char *path, const char **rel_out)
{
	if (!dir || !*dir || !path || !*path)
		return false;
	for (int mode = 0; mode < 2; mode++) {
		if (mode == 1 && !sys4_text_is_gbk)
			break;
		char *cjk_dir = (mode == 0) ? utf2sjis(dir, strlen(dir)) : utf2cjk(dir, strlen(dir), false);
		size_t pfx = strlen(cjk_dir);
		if (pfx > 0 && !strncmp(path, cjk_dir, pfx) &&
		    (path[pfx] == '/' || path[pfx] == '\\' || path[pfx] == '\0')) {
			const char *rel = path + pfx;
			while (*rel == '/' || *rel == '\\')
				rel++;
			free(cjk_dir);
			*rel_out = rel;
			return true;
		}
		free(cjk_dir);
	}
	return false;
}

static char *join_dir_and_rel(const char *dir, const char *utf_rel)
{
	if (is_absolute_path(utf_rel))
		return xstrdup(utf_rel);
	char *resolved = xmalloc(strlen(dir) + strlen(utf_rel) + 2);
	strcpy(resolved, dir);
	if (*utf_rel) {
		strcat(resolved, "/");
		strcat(resolved, utf_rel);
	}
	return resolved;
}

static char *resolve_path(const char *dir, const char *path)
{
	const char *target = path;
	const char *stripped_rel = NULL;
	if (strip_cjk_dir_prefix(dir, path, &stripped_rel)) {
		target = stripped_rel;
	}

	char *utf_primary = unix_path_with(target, false);
	char *resolved_primary = join_dir_and_rel(dir, utf_primary);
	free(utf_primary);

	if (file_exists(resolved_primary))
		return resolved_primary;

	char *icase_primary = path_get_icase(resolved_primary);
	if (icase_primary) {
		free(resolved_primary);
		return icase_primary;
	}

	if (sys4_text_is_gbk) {
		char *utf_legacy = unix_path_with(target, true);
		char *resolved_legacy = join_dir_and_rel(dir, utf_legacy);
		free(utf_legacy);
		if (strcmp(resolved_legacy, resolved_primary) != 0) {
			if (file_exists(resolved_legacy)) {
				free(resolved_primary);
				return resolved_legacy;
			}
			char *icase_legacy = path_get_icase(resolved_legacy);
			if (icase_legacy) {
				free(resolved_primary);
				free(resolved_legacy);
				return icase_legacy;
			}
		}
		free(resolved_legacy);
	}

	return resolved_primary;
}

char *gamedir_path(const char *path)
{
	return resolve_path(config.game_dir, path);
}

char *gamedir_path_icase(const char *path)
{
	char *resolved = resolve_path(config.game_dir, path);
	if (!file_exists(resolved)) {
		char *tmp = path_get_icase(resolved);
		free(resolved);
		resolved = tmp;
	}
	return resolved;
}

char *savedir_path(const char *path)
{
	return resolve_path(config.save_dir, path);
}

static void get_local_tm(struct tm *tm_out, long *nsec_out)
{
	struct timespec ts;
	clock_gettime(CLOCK_REALTIME, &ts);
	time_t t = ts.tv_sec;
	memset(tm_out, 0, sizeof(*tm_out));
#if (defined(_WIN32) || defined(__WIN32__))
	localtime_s(tm_out, &t);
#else
	localtime_r(&t, tm_out);
#endif
	if (nsec_out)
		*nsec_out = ts.tv_nsec;
}

void get_date(int *year, int *month, int *mday, int *wday)
{
	struct tm tm;
	get_local_tm(&tm, NULL);

	*year  = tm.tm_year + 1900;
	*month = tm.tm_mon + 1;
	*mday  = tm.tm_mday;
	*wday  = tm.tm_wday;
}

void get_time(int *hour, int *min, int *sec, int *ms)
{
	struct tm tm;
	long nsec = 0;
	get_local_tm(&tm, &nsec);

	*hour = tm.tm_hour;
	*min  = tm.tm_min;
	*sec  = tm.tm_sec;
	*ms   = (int)(nsec / 1000000);
}

void indent_message(int indent, const char *fmt, ...)
{
	for (int i = 0; i < indent; i++)
		putchar('\t');

	va_list ap;
	va_start(ap, fmt);
	sys_vmessage(fmt, ap);
	va_end(ap);
}

void log_message(const char *log, const char *fmt, ...)
{
	va_list ap;
	va_start(ap, fmt);
	if (dbg_dap)
		dbg_dap_log(log, fmt, ap);
	else
		sys_vmessage(fmt, ap);
	va_end(ap);
}
