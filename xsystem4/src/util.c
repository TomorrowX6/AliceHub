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
#include <sys/stat.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#endif

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
	utf = mai_sjis2utf(sjis, 0);
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
	utf = mai_sjis2utf(sjis, 0);
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
	utf = mai_sjis2utf(sjis, 0);
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
#elif defined(__SWITCH__)
	const char *slash = strchr(path, '/');
	return slash && (slash == path || slash[-1] == ':');
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

static bool path_component_has_non_ascii(const char *s)
{
	for (const unsigned char *p = (const unsigned char *)s; p && *p; p++) {
		if (*p >= 0x80)
			return true;
	}
	return false;
}

static char *dir_find_mojibake_icase(const char *dir, const char *component)
{
	if (!path_component_has_non_ascii(component))
		return NULL;
	UDIR *d = opendir_utf8(dir);
	if (!d)
		return NULL;
	char *target_repaired = cjk_repair_sjis_as_gbk_utf8(component);
	const char *target = target_repaired ? target_repaired : component;
	char *found = NULL;
	char *d_name;
	while ((d_name = readdir_utf8(d))) {
		char *entry_repaired = cjk_repair_sjis_as_gbk_utf8(d_name);
		const char *candidate = entry_repaired ? entry_repaired : d_name;
		if (!strcmp(candidate, target)) {
			free(found);
			found = path_join(dir, d_name);
			free(entry_repaired);
			free(d_name);
			break;
		}
		if (!found && !strcasecmp(candidate, target)) {
			found = path_join(dir, d_name);
		}
		free(entry_repaired);
		free(d_name);
	}
	closedir_utf8(d);
	free(target_repaired);
	return found;
}

static char *relative_path_get_icase(const char *dir, char *relative)
{
	char *resolved = xstrdup(dir);
	char *component = relative;
	for (;;) {
		char *slash = strchr(component, '/');
		if (slash)
			*slash = '\0';
		if (*component) {
			char *next = path_join(resolved, component);
			if (!file_exists(next)) {
				char *matched = path_get_icase(next);
				if (!matched)
					matched = dir_find_mojibake_icase(resolved, component);
				free(next);
				next = matched;
			}
			free(resolved);
			resolved = next;
			if (!resolved) {
				if (slash)
					*slash = '/';
				return NULL;
			}
		}
		if (!slash) {
			if (*component)
				return resolved;
			char *with_separator = path_join(resolved, "");
			free(resolved);
			return with_separator;
		}
		*slash = '/';
		if (!is_directory(resolved)) {
			free(resolved);
			return NULL;
		}
		component = slash + 1;
	}
}

static char *find_path_icase(const char *resolved, const char *dir, char *relative, bool nested_icase)
{
	char *match = path_get_icase(resolved);
	if (!match && nested_icase && !is_absolute_path(relative))
		return relative_path_get_icase(dir, relative);
	return match;
}

static char *resolve_path(const char *dir, const char *path, bool nested_icase)
{
	const char *target = path;
	const char *stripped_rel = NULL;
	if (strip_cjk_dir_prefix(dir, path, &stripped_rel)) {
		target = stripped_rel;
	}

	char *utf_primary = unix_path_with(target, false);
	char *resolved_primary = join_dir_and_rel(dir, utf_primary);

	if (file_exists(resolved_primary)) {
		free(utf_primary);
		return resolved_primary;
	}

	char *icase_primary = find_path_icase(resolved_primary, dir, utf_primary, nested_icase);
	free(utf_primary);
	if (icase_primary) {
		free(resolved_primary);
		return icase_primary;
	}

	if (sys4_text_is_gbk) {
		char *utf_legacy = unix_path_with(target, true);
		char *resolved_legacy = join_dir_and_rel(dir, utf_legacy);
		if (strcmp(resolved_legacy, resolved_primary) != 0) {
			if (file_exists(resolved_legacy)) {
				free(utf_legacy);
				free(resolved_primary);
				return resolved_legacy;
			}
			char *icase_legacy = find_path_icase(resolved_legacy, dir, utf_legacy, nested_icase);
			if (icase_legacy) {
				free(utf_legacy);
				free(resolved_primary);
				free(resolved_legacy);
				return icase_legacy;
			}
		}
		free(utf_legacy);
		free(resolved_legacy);
	}

	return resolved_primary;
}

char *gamedir_path(const char *path)
{
	return resolve_path(config.game_dir, path, true);
}

char *gamedir_path_icase(const char *path)
{
	char *resolved = gamedir_path(path);
	if (!file_exists(resolved)) {
		char *tmp = path_get_icase(resolved);
		free(resolved);
		resolved = tmp;
	}
	return resolved;
}

#define SAVEDIR_CACHE_SLOTS 4
#define SAVEDIR_CACHE_TTL_MS 250u

struct savedir_cache_entry {
	char *name;
	uint32_t hash_icase;
};

struct savedir_cache_slot {
	char *dir_path;
	bool dir_missing;
	bool dir_unreadable;
	uint64_t mutation_gen;
	uint64_t last_validated_ms;
	uint64_t last_used_seq;
	ustat dir_stat;
	struct savedir_cache_entry *entries;
	size_t nr_entries;
	int32_t *buckets;
	size_t bucket_cap;
};

static struct savedir_cache_slot savedir_cache[SAVEDIR_CACHE_SLOTS];
static uint64_t savedir_cache_seq = 0;

#ifdef _WIN32
static SRWLOCK savedir_cache_lock = SRWLOCK_INIT;
static inline void savedir_lock(void) { AcquireSRWLockExclusive(&savedir_cache_lock); }
static inline void savedir_unlock(void) { ReleaseSRWLockExclusive(&savedir_cache_lock); }
#else
static pthread_mutex_t savedir_cache_lock = PTHREAD_MUTEX_INITIALIZER;
static inline void savedir_lock(void) { pthread_mutex_lock(&savedir_cache_lock); }
static inline void savedir_unlock(void) { pthread_mutex_unlock(&savedir_cache_lock); }
#endif

static uint64_t savedir_monotonic_ms(void)
{
#ifdef _WIN32
	return (uint64_t)GetTickCount64();
#else
	struct timespec ts;
	if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0)
		return (uint64_t)ts.tv_sec * 1000u + (uint64_t)(ts.tv_nsec / 1000000L);
	return 0;
#endif
}

static bool savedir_stat_eq(const ustat *a, const ustat *b)
{
	if (a->st_dev != b->st_dev || a->st_ino != b->st_ino
			|| a->st_size != b->st_size
			|| a->st_mtime != b->st_mtime
			|| a->st_ctime != b->st_ctime)
		return false;
#if defined(__APPLE__)
	if (a->st_mtimespec.tv_nsec != b->st_mtimespec.tv_nsec
			|| a->st_ctimespec.tv_nsec != b->st_ctimespec.tv_nsec)
		return false;
#elif defined(__linux__) || defined(__ANDROID__)
	if (a->st_mtim.tv_nsec != b->st_mtim.tv_nsec
			|| a->st_ctim.tv_nsec != b->st_ctim.tv_nsec)
		return false;
#endif
	return true;
}

static uint32_t savedir_hash_ascii_icase(const char *s)
{
	uint32_t h = 2166136261u;
	for (const unsigned char *p = (const unsigned char *)s; *p; p++) {
		unsigned char c = *p;
		if (c >= 'A' && c <= 'Z')
			c = (unsigned char)(c - 'A' + 'a');
		h ^= c;
		h *= 16777619u;
	}
	return h;
}

static void savedir_slot_reset(struct savedir_cache_slot *slot)
{
	free(slot->dir_path);
	for (size_t i = 0; i < slot->nr_entries; i++)
		free(slot->entries[i].name);
	free(slot->entries);
	free(slot->buckets);
	memset(slot, 0, sizeof(*slot));
}

static void savedir_slot_populate(struct savedir_cache_slot *slot, const char *parent_dir,
		uint64_t cur_gen, uint64_t now_ms)
{
	savedir_slot_reset(slot);
	slot->dir_path = xstrdup(parent_dir);
	slot->mutation_gen = cur_gen;
	slot->last_validated_ms = now_ms;

	ustat st;
	if (stat_utf8(parent_dir, &st) != 0 || !S_ISDIR(st.st_mode)) {
		slot->dir_missing = true;
		return;
	}
	slot->dir_stat = st;

	UDIR *d = opendir_utf8(parent_dir);
	if (!d) {
		// Directory exists (stat_utf8 succeeded with S_ISDIR) but cannot be listed
		// (e.g. traverse-only permissions): fall back to direct file_exists() probes.
		slot->dir_unreadable = true;
		return;
	}

	size_t cap = 0;
	char *d_name;
	while ((d_name = readdir_utf8(d)) != NULL) {
		if (!strcmp(d_name, ".") || !strcmp(d_name, "..")) {
			free(d_name);
			continue;
		}
		if (slot->nr_entries == cap) {
			size_t ncap = cap ? cap * 2 : 64;
			slot->entries = xrealloc(slot->entries, ncap * sizeof(*slot->entries));
			cap = ncap;
		}
		slot->entries[slot->nr_entries].name = d_name;
		slot->entries[slot->nr_entries].hash_icase = savedir_hash_ascii_icase(d_name);
		slot->nr_entries++;
	}
	closedir_utf8(d);

	if (slot->nr_entries > 0) {
		size_t bcap = 16;
		while (bcap < slot->nr_entries * 2)
			bcap <<= 1;
		slot->buckets = xmalloc(bcap * sizeof(*slot->buckets));
		for (size_t i = 0; i < bcap; i++)
			slot->buckets[i] = -1;
		slot->bucket_cap = bcap;
		size_t mask = bcap - 1;
		for (size_t i = 0; i < slot->nr_entries; i++) {
			size_t idx = slot->entries[i].hash_icase & mask;
			while (slot->buckets[idx] != -1)
				idx = (idx + 1) & mask;
			slot->buckets[idx] = (int32_t)i;
		}
	}
}

static bool savedir_split_parent_base(const char *resolved, char **parent_out, const char **base_out)
{
	const char *slash = strrchr(resolved, '/');
	if (!slash || slash[1] == '\0')
		return false;
	const char *base = slash + 1;
	if (!strcmp(base, ".") || !strcmp(base, ".."))
		return false;

	size_t plen = (size_t)(slash - resolved);
	while (plen > 1 && resolved[plen - 1] == '/'
#ifdef __SWITCH__
			&& !(plen >= 2 && resolved[plen - 2] == ':')
#endif
			) {
		plen--;
	}

	char *parent;
	if (plen == 0) {
		parent = xstrdup("/");
	} else if (plen == 2 && isalpha((unsigned char)resolved[0]) && resolved[1] == ':') {
		parent = xmalloc(4);
		memcpy(parent, resolved, 3);
		parent[3] = '\0';
	}
#ifdef __SWITCH__
	else if (plen > 0 && resolved[plen - 1] == ':') {
		parent = xmalloc(plen + 2);
		memcpy(parent, resolved, plen + 1);
		parent[plen + 1] = '\0';
	}
#endif
	else {
		parent = xmalloc(plen + 1);
		memcpy(parent, resolved, plen);
		parent[plen] = '\0';
	}

	*parent_out = parent;
	*base_out = base;
	return true;
}

static bool savedir_cache_lookup(const char *resolved, char **matched_out, bool *dir_missing_out)
{
	*matched_out = NULL;
	if (dir_missing_out)
		*dir_missing_out = false;

	char *parent_dir = NULL;
	const char *base_name = NULL;
	if (!savedir_split_parent_base(resolved, &parent_dir, &base_name))
		return false;

	savedir_lock();
	uint64_t cur_gen = file_mutation_gen();
	uint64_t now_ms = savedir_monotonic_ms();

	struct savedir_cache_slot *slot = NULL;
	for (int i = 0; i < SAVEDIR_CACHE_SLOTS; i++) {
		if (savedir_cache[i].dir_path && !strcmp(savedir_cache[i].dir_path, parent_dir)) {
			slot = &savedir_cache[i];
			break;
		}
	}

	bool valid = false;
	if (slot && slot->mutation_gen == cur_gen) {
		if (now_ms > 0 && now_ms >= slot->last_validated_ms
				&& (now_ms - slot->last_validated_ms) < SAVEDIR_CACHE_TTL_MS) {
			valid = true;
		} else {
			ustat st;
			int rc = stat_utf8(parent_dir, &st);
			if (slot->dir_missing) {
				valid = (rc != 0 || !S_ISDIR(st.st_mode));
			} else if (slot->dir_unreadable) {
				valid = (rc == 0 && S_ISDIR(st.st_mode)
						&& slot->dir_stat.st_mode == st.st_mode
						&& savedir_stat_eq(&slot->dir_stat, &st));
			} else {
				valid = (rc == 0 && S_ISDIR(st.st_mode) && savedir_stat_eq(&slot->dir_stat, &st));
			}
			if (valid)
				slot->last_validated_ms = now_ms;
		}
	}

	if (!valid) {
		if (!slot) {
			slot = &savedir_cache[0];
			for (int i = 0; i < SAVEDIR_CACHE_SLOTS; i++) {
				if (!savedir_cache[i].dir_path) {
					slot = &savedir_cache[i];
					break;
				}
				if (savedir_cache[i].last_used_seq < slot->last_used_seq)
					slot = &savedir_cache[i];
			}
		}
		savedir_slot_populate(slot, parent_dir, cur_gen, now_ms);
	}

	slot->last_used_seq = ++savedir_cache_seq;
	if (slot->dir_unreadable) {
		savedir_unlock();
		free(parent_dir);
		return false;
	}
	if (dir_missing_out)
		*dir_missing_out = slot->dir_missing;

	if (!slot->dir_missing && slot->nr_entries > 0 && slot->buckets) {
		uint32_t h = savedir_hash_ascii_icase(base_name);
		size_t mask = slot->bucket_cap - 1;
		size_t idx = h & mask;
		const char *exact = NULL;
		const char *icase = NULL;
		while (slot->buckets[idx] != -1) {
			struct savedir_cache_entry *e = &slot->entries[slot->buckets[idx]];
			if (e->hash_icase == h) {
				if (!strcmp(e->name, base_name)) {
					exact = e->name;
					break;
				}
				if (!icase && !strcasecmp(e->name, base_name))
					icase = e->name;
			}
			idx = (idx + 1) & mask;
		}
		const char *found = exact ? exact : icase;
		if (found)
			*matched_out = path_join(parent_dir, found);
	}

	savedir_unlock();
	free(parent_dir);
	return true;
}

void savedir_cache_invalidate(void)
{
	file_note_fs_mutation();
}

static char *resolve_savedir_path(const char *path, bool *exists_out)
{
	if (exists_out)
		*exists_out = false;
	if (!config.save_dir || !config.save_dir[0]) {
		char *p = resolve_path(config.save_dir ? config.save_dir : ".", path, false);
		if (exists_out)
			*exists_out = p && file_exists(p);
		return p;
	}

	const char *target = path ? path : "";
	const char *stripped_rel = NULL;
	if (strip_cjk_dir_prefix(config.save_dir, target, &stripped_rel))
		target = stripped_rel;

	char *utf_primary = unix_path_with(target, false);
	char *resolved_primary = join_dir_and_rel(config.save_dir, utf_primary);

	char *matched = NULL;
	bool dir_missing = false;
	if (!savedir_cache_lookup(resolved_primary, &matched, &dir_missing)) {
		free(utf_primary);
		free(resolved_primary);
		char *p = resolve_path(config.save_dir, path, false);
		if (exists_out)
			*exists_out = p && file_exists(p);
		return p;
	}

	if (matched) {
		free(utf_primary);
		if (!exists_out) {
			free(resolved_primary);
			return matched;
		}
		if (file_exists(matched)) {
			*exists_out = true;
			free(resolved_primary);
			return matched;
		}
		// External deletion inside the TTL window: invalidate immediately.
		file_note_fs_mutation();
		free(matched);
		return resolved_primary;
	}

	if (sys4_text_is_gbk) {
		char *utf_legacy = unix_path_with(target, true);
		char *resolved_legacy = join_dir_and_rel(config.save_dir, utf_legacy);
		if (strcmp(resolved_legacy, resolved_primary) != 0) {
			char *legacy_match = NULL;
			bool legacy_missing = false;
			if (savedir_cache_lookup(resolved_legacy, &legacy_match, &legacy_missing) && legacy_match) {
				free(utf_legacy);
				free(resolved_legacy);
				free(utf_primary);
				if (!exists_out) {
					free(resolved_primary);
					return legacy_match;
				}
				if (file_exists(legacy_match)) {
					*exists_out = true;
					free(resolved_primary);
					return legacy_match;
				}
				file_note_fs_mutation();
				free(legacy_match);
				return resolved_primary;
			}
#ifdef __SWITCH__
			if (legacy_missing && strchr(utf_legacy, '/')) {
				char *icase = path_get_icase(resolved_legacy);
				if (icase) {
					free(utf_legacy);
					free(resolved_legacy);
					free(utf_primary);
					free(resolved_primary);
					if (exists_out)
						*exists_out = true;
					return icase;
				}
			}
#endif
		}
		free(utf_legacy);
		free(resolved_legacy);
	}

#ifdef __SWITCH__
	if (dir_missing && strchr(utf_primary, '/')) {
		char *icase = path_get_icase(resolved_primary);
		if (icase) {
			free(utf_primary);
			free(resolved_primary);
			if (exists_out)
				*exists_out = true;
			return icase;
		}
	}
#endif

	free(utf_primary);
	return resolved_primary;
}

char *savedir_path(const char *path)
{
	return resolve_savedir_path(path, NULL);
}

bool savedir_file_exists(const char *path)
{
	if (!path || !*path)
		return false;
	bool exists = false;
	char *resolved = resolve_savedir_path(path, &exists);
	free(resolved);
	return exists;
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
