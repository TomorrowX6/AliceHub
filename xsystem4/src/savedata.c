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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <zlib.h>
#ifdef _WIN32
#include <io.h>
#else
#include <strings.h>
#include <unistd.h>
#endif
#include "cJSON.h"

#include "system4.h"
#include "system4/ain.h"
#include "system4/file.h"
#include "system4/savefile.h"
#include "system4/string.h"
#include "system4/utfsjis.h"

#include "modifier.h"
#include "savedata.h"
#include "vm.h"
#include "vm/heap.h"
#include "vm/page.h"
#include "vm/v12.h"
#include "xsystem4.h"

static int current_global;

// Preserve static EX-database caches initialized at startup (such as Rance 10's
// g_questCollection and g_quest) that were placed in the local save group, so
// loading pre-MOD / older saves does not overwrite active MOD quest tables.
static bool should_preserve_static_global(int global_index)
{
	if (global_index < 0 || global_index >= ain->nr_globals)
		return false;
	const char *name = ain->globals[global_index].name;
	if (!name)
		return false;
	if (!strcmp(name, "g_questCollection")) {
		struct page *p = heap_get_page(global_get(global_index).i);
		if (p && p->type == STRUCT_PAGE && p->nr_vars >= 2) {
			struct page *quests = heap_get_page(p->values[0].i);
			if (quests && quests->type == ARRAY_PAGE && quests->nr_vars > 0)
				return true;
		}
	} else if (!strcmp(name, "g_quest")) {
		struct page *arr = heap_get_page(global_get(global_index).i);
		if (arr && arr->type == ARRAY_PAGE && arr->nr_vars > 0)
			return true;
	}
	return false;
}

#define invalid_save_data(msg, data) {					\
		char *str = data ? cJSON_Print(data) : strdup("NULL");	\
		if (!str) str = strdup("PRINTING FAILED");		\
		WARNING("Invalid save data (%d): " msg ": %s", current_global, data); \
		free(str);						\
	}

static char *make_atomic_tmp_path(const char *path)
{
	const char *slash1 = strrchr(path, '/');
	const char *slash2 = strrchr(path, '\\');
	const char *slash = slash1;
	if (slash2 && (!slash || slash2 > slash))
		slash = slash2;
	size_t dir_len = slash ? (size_t)(slash - path + 1) : 0;
	const char *base = slash ? slash + 1 : path;
	size_t base_len = strlen(base);
	char *tmp = xmalloc(dir_len + 1 + base_len + 4 + 1);
	if (dir_len)
		memcpy(tmp, path, dir_len);
	tmp[dir_len] = '.';
	memcpy(tmp + dir_len + 1, base, base_len);
	memcpy(tmp + dir_len + 1 + base_len, ".tmp", 5);
	return tmp;
}

static char *make_backup_path(const char *path)
{
	size_t len = strlen(path);
	char *bak = xmalloc(len + 5);
	memcpy(bak, path, len);
	memcpy(bak + len, ".bak", 5);
	return bak;
}

static bool is_critical_global_save(const char *path)
{
	if (!path)
		return false;
	const char *slash1 = strrchr(path, '/');
	const char *slash2 = strrchr(path, '\\');
	const char *base = slash1;
	if (slash2 && (!base || slash2 > base))
		base = slash2;
	base = base ? base + 1 : path;
#ifdef _WIN32
	return !_stricmp(base, "GlobalSave.asd") || !_stricmp(base, "GlobalSaveData.asd") || !_stricmp(base, "Collection.asd") || !_stricmp(base, "GameConfig.asd");
#else
	return !strcasecmp(base, "GlobalSave.asd") || !strcasecmp(base, "GlobalSaveData.asd") || !strcasecmp(base, "Collection.asd") || !strcasecmp(base, "GameConfig.asd");
#endif
}

static bool is_game_config_save(const char *path)
{
	if (!path)
		return false;
	const char *slash1 = strrchr(path, '/');
	const char *slash2 = strrchr(path, '\\');
	const char *base = slash1;
	if (slash2 && (!base || slash2 > base))
		base = slash2;
	base = base ? base + 1 : path;
#ifdef _WIN32
	return !_stricmp(base, "GameConfig.asd");
#else
	return !strcasecmp(base, "GameConfig.asd");
#endif
}

#define MAX_TRACKED_GLOBAL_SAVES 8
struct tracked_global_save {
	char *keyname;
	char *filename;
	char *group_name;
};
static struct tracked_global_save tracked_global_saves[MAX_TRACKED_GLOBAL_SAVES];
static int nr_tracked_global_saves = 0;
static bool flushing_background_saves = false;

static const char *save_basename(const char *path)
{
	if (!path)
		return "";
	const char *slash1 = strrchr(path, '/');
	const char *slash2 = strrchr(path, '\\');
	const char *base = slash1;
	if (slash2 && (!base || slash2 > base))
		base = slash2;
	return base ? base + 1 : path;
}

static bool is_background_flush_save(const char *path)
{
	const char *base = save_basename(path);
	if (!base[0])
		return false;
#ifdef _WIN32
	return !_stricmp(base, "GlobalSave.asd") ||
	       !_stricmp(base, "Collection.asd") ||
	       !_stricmp(base, "GameConfig.asd") ||
	       !_stricmp(base, "GameEnquete.asd") ||
	       !_stricmp(base, "AFCommon.asd") ||
	       !_stricmp(base, "AFConfig.asd") ||
	       !_stricmp(base, "AFInfo.asd");
#else
	return !strcasecmp(base, "GlobalSave.asd") ||
	       !strcasecmp(base, "Collection.asd") ||
	       !strcasecmp(base, "GameConfig.asd") ||
	       !strcasecmp(base, "GameEnquete.asd") ||
	       !strcasecmp(base, "AFCommon.asd") ||
	       !strcasecmp(base, "AFConfig.asd") ||
	       !strcasecmp(base, "AFInfo.asd");
#endif
}

static void track_global_save(const char *keyname, const char *filename, const char *group_name)
{
	if (flushing_background_saves || !keyname || !filename || !group_name || !group_name[0])
		return;
	if (!is_background_flush_save(filename))
		return;
	for (int i = 0; i < nr_tracked_global_saves; i++) {
		if (!strcmp(tracked_global_saves[i].filename, filename)) {
			free(tracked_global_saves[i].keyname);
			free(tracked_global_saves[i].group_name);
			tracked_global_saves[i].keyname = xstrdup(keyname);
			tracked_global_saves[i].group_name = xstrdup(group_name);
			return;
		}
	}
	if (nr_tracked_global_saves >= MAX_TRACKED_GLOBAL_SAVES)
		return;
	tracked_global_saves[nr_tracked_global_saves].keyname = xstrdup(keyname);
	tracked_global_saves[nr_tracked_global_saves].filename = xstrdup(filename);
	tracked_global_saves[nr_tracked_global_saves].group_name = xstrdup(group_name);
	nr_tracked_global_saves++;
}

static void untrack_global_save(const char *filename)
{
	if (!filename)
		return;
	for (int i = 0; i < nr_tracked_global_saves; i++) {
		if (!strcmp(tracked_global_saves[i].filename, filename)) {
			free(tracked_global_saves[i].keyname);
			free(tracked_global_saves[i].filename);
			free(tracked_global_saves[i].group_name);
			for (int j = i; j + 1 < nr_tracked_global_saves; j++)
				tracked_global_saves[j] = tracked_global_saves[j + 1];
			nr_tracked_global_saves--;
			return;
		}
	}
}

void savedata_reset(void)
{
	for (int i = 0; i < nr_tracked_global_saves; i++) {
		free(tracked_global_saves[i].keyname);
		free(tracked_global_saves[i].filename);
		free(tracked_global_saves[i].group_name);
		tracked_global_saves[i].keyname = NULL;
		tracked_global_saves[i].filename = NULL;
		tracked_global_saves[i].group_name = NULL;
	}
	nr_tracked_global_saves = 0;
}

FILE *atomic_save_open(const char *path, const char *mode, char **tmp_path_out)
{
	char *tmp = make_atomic_tmp_path(path);
	char *tmp_copy = xstrdup(tmp);
	char *dir = path_dirname(tmp_copy);
	if (dir && dir[0])
		mkdir_p(dir);
	free(tmp_copy);
	FILE *fp = file_open_utf8(tmp, mode);
	if (!fp) {
		free(tmp);
		*tmp_path_out = NULL;
		return NULL;
	}
	*tmp_path_out = tmp;
	return fp;
}

void atomic_save_abort(FILE *fp, char *tmp_path)
{
	if (fp)
		fclose(fp);
	if (tmp_path) {
		remove_utf8(tmp_path);
		free(tmp_path);
	}
}

bool atomic_save_commit(FILE *fp, const char *path, char *tmp_path, bool keep_backup)
{
	bool ok = !ferror(fp) && fflush(fp) == 0;
#ifdef _WIN32
	if (ok && _commit(_fileno(fp)) != 0)
		ok = false;
#else
	if (ok && fsync(fileno(fp)) != 0 && errno != EINVAL && errno != EROFS)
		ok = false;
#endif
	if (fclose(fp) != 0)
		ok = false;
	if (!ok) {
		WARNING("Error flushing temporary save file %s: %s", display_utf0(tmp_path), strerror(errno));
		remove_utf8(tmp_path);
		free(tmp_path);
		return false;
	}

	if (keep_backup && file_exists(path) && file_size(path) > 16) {
		char *bak = make_backup_path(path);
		file_copy(path, bak);
		free(bak);
	}

#ifdef _WIN32
	wchar_t *wtmp = utf8_to_wchar(tmp_path);
	wchar_t *wpath = utf8_to_wchar(path);
	remove_utf8(path);
	int rc = _wrename(wtmp, wpath);
	free(wtmp);
	free(wpath);
#else
	int rc = rename(tmp_path, path);
#endif
	if (rc != 0) {
		WARNING("Failed renaming %s -> %s: %s", display_utf0(tmp_path), display_utf0(path), strerror(errno));
		remove_utf8(tmp_path);
		free(tmp_path);
		return false;
	}
	free(tmp_path);
	return true;
}

int save_json(const char *filename, cJSON *json)
{
	char *str = cJSON_Print(json);
	if (!str)
		return 0;

	char *path = savedir_path(filename);
	char *tmp_path = NULL;
	FILE *f = atomic_save_open(path, "wb", &tmp_path);
	if (!f) {
		WARNING("Failed to open save file: %s: %s", display_utf0(filename), strerror(errno));
		free(path);
		free(str);
		return 0;
	}

	size_t len = strlen(str);
	if (len > 0 && fwrite(str, len, 1, f) != 1) {
		WARNING("Failed to write save file: %s", strerror(errno));
		atomic_save_abort(f, tmp_path);
		free(path);
		free(str);
		return 0;
	}
	bool ok = atomic_save_commit(f, path, tmp_path, is_critical_global_save(filename));
	free(path);
	free(str);
	return ok ? 1 : 0;
}

cJSON *load_json(const char *filename)
{
	char *path = savedir_path(filename);
	char *json = file_read(path, NULL);
	free(path);
	if (!json)
		return NULL;
	cJSON *root = cJSON_Parse(json);
	free(json);
	return root;
}

struct cjk_kanji_pair {
	uint16_t from;
	uint16_t to;
};

static const struct cjk_kanji_pair shinjitai_canon_map[] = {
	{ 0x4E21, 0x4E24 }, { 0x4E57, 0x4E58 }, { 0x4E80, 0x9F9F }, { 0x4E9C, 0x4E9A },
	{ 0x4EEE, 0x5047 }, { 0x4F1D, 0x4F20 }, { 0x4FA1, 0x4EF7 }, { 0x5075, 0x4FA6 },
	{ 0x507D, 0x4F2A }, { 0x5099, 0x5907 }, { 0x50B7, 0x4F24 }, { 0x50CD, 0x52A8 },
	{ 0x512A, 0x4F18 }, { 0x5150, 0x513F }, { 0x5186, 0x5706 }, { 0x51E6, 0x5904 },
	{ 0x5263, 0x5251 }, { 0x5264, 0x5242 }, { 0x5270, 0x5269 }, { 0x5275, 0x521B },
	{ 0x52B4, 0x52B3 }, { 0x52B9, 0x6548 }, { 0x52D5, 0x52A8 }, { 0x52D9, 0x52A1 },
	{ 0x52DD, 0x80DC }, { 0x52E2, 0x52BF }, { 0x52E7, 0x529D }, { 0x52F2, 0x52CB },
	{ 0x5354, 0x534F }, { 0x5358, 0x5355 }, { 0x53B3, 0x4E25 }, { 0x53CE, 0x6536 },
	{ 0x54E1, 0x5458 }, { 0x55B6, 0x8425 }, { 0x56E3, 0x56E2 }, { 0x56F2, 0x56F4 },
	{ 0x56F3, 0x56FE }, { 0x570F, 0x5708 }, { 0x5712, 0x56ED }, { 0x57F7, 0x6267 },
	{ 0x5805, 0x575A }, { 0x5831, 0x62A5 }, { 0x5834, 0x573A }, { 0x5841, 0x5792 },
	{ 0x584A, 0x5757 }, { 0x5857, 0x6D82 }, { 0x5869, 0x76D0 }, { 0x5897, 0x589E },
	{ 0x589C, 0x5760 }, { 0x58B3, 0x575F }, { 0x58CA, 0x574F }, { 0x58CC, 0x58E4 },
	{ 0x58F1, 0x58F9 }, { 0x58F2, 0x5356 }, { 0x5909, 0x53D8 }, { 0x5968, 0x5956 },
	{ 0x596A, 0x593A }, { 0x596E, 0x594B }, { 0x5A66, 0x5987 }, { 0x5B9F, 0x5B9E },
	{ 0x5BAE, 0x5BAB }, { 0x5BE9, 0x5BA1 }, { 0x5BFE, 0x5BF9 }, { 0x5C02, 0x4E13 },
	{ 0x5C0E, 0x5BFC }, { 0x5C64, 0x5C42 }, { 0x5CF6, 0x5C9B }, { 0x5DE3, 0x5DE2 },
	{ 0x5DFB, 0x5377 }, { 0x5E25, 0x5E05 }, { 0x5E2F, 0x5E26 }, { 0x5E30, 0x5F52 },
	{ 0x5E81, 0x5385 }, { 0x5E83, 0x5E7F }, { 0x5EC3, 0x5E9F }, { 0x5F10, 0x8D30 },
	{ 0x5F35, 0x5F20 }, { 0x5F37, 0x5F3A }, { 0x5F3E, 0x5F39 }, { 0x5F59, 0x6C47 },
	{ 0x5F6B, 0x96D5 }, { 0x5F8C, 0x540E }, { 0x5F93, 0x4ECE }, { 0x5FA9, 0x590D },
	{ 0x5FB3, 0x5FB7 }, { 0x5FB4, 0x5F81 }, { 0x5FB9, 0x5F7B }, { 0x5FDC, 0x5E94 },
	{ 0x6075, 0x60E0 }, { 0x60A9, 0x607C }, { 0x60AA, 0x6076 }, { 0x614B, 0x6001 },
	{ 0x6163, 0x60EF }, { 0x616E, 0x8651 }, { 0x6182, 0x5FE7 }, { 0x61A4, 0x6124 },
	{ 0x61B2, 0x5BAA }, { 0x61D0, 0x6000 }, { 0x61F2, 0x60E9 }, { 0x61F8, 0x60AC },
	{ 0x6226, 0x6218 }, { 0x622F, 0x620F }, { 0x6238, 0x6237 }, { 0x629C, 0x62D4 },
	{ 0x629E, 0x62E9 }, { 0x62DD, 0x62DC }, { 0x62E0, 0x636E }, { 0x62E1, 0x6269 },
	{ 0x6319, 0x4E3E }, { 0x633F, 0x63D2 }, { 0x635C, 0x641C }, { 0x6368, 0x820D },
	{ 0x6383, 0x626B }, { 0x63A1, 0x91C7 }, { 0x63B2, 0x63ED }, { 0x63DB, 0x6362 },
	{ 0x63FA, 0x6447 }, { 0x640D, 0x635F }, { 0x6442, 0x6444 }, { 0x6483, 0x51FB },
	{ 0x64EC, 0x62DF }, { 0x6557, 0x8D25 }, { 0x6575, 0x654C }, { 0x6589, 0x9F50 },
	{ 0x658E, 0x658B }, { 0x65E2, 0x5DF2 }, { 0x6642, 0x65F6 }, { 0x6681, 0x6653 },
	{ 0x66A6, 0x5386 }, { 0x66F8, 0x4E66 }, { 0x6804, 0x8363 }, { 0x685C, 0x6A31 },
	{ 0x685F, 0x6808 }, { 0x691C, 0x68C0 }, { 0x696D, 0x4E1A }, { 0x6975, 0x6781 },
	{ 0x697D, 0x4E50 }, { 0x69CB, 0x6784 }, { 0x69D8, 0x6837 }, { 0x6A19, 0x6807 },
	{ 0x6A29, 0x6743 }, { 0x6A4B, 0x6865 }, { 0x6A5F, 0x673A }, { 0x6B04, 0x680F },
	{ 0x6B53, 0x6B22 }, { 0x6B6F, 0x9F7F }, { 0x6B73, 0x5C81 }, { 0x6B74, 0x5386 },
	{ 0x6BB2, 0x6B7C }, { 0x6BBA, 0x6740 }, { 0x6BBB, 0x58F3 }, { 0x6BCE, 0x6BCF },
	{ 0x6C17, 0x6C14 }, { 0x6C37, 0x51B0 }, { 0x6C5A, 0x6C61 }, { 0x6C7A, 0x51B3 },
	{ 0x6D44, 0x51C0 }, { 0x6D5C, 0x6EE8 }, { 0x6D99, 0x6CEA }, { 0x6E07, 0x6E34 },
	{ 0x6E08, 0x6D4E }, { 0x6E09, 0x6D89 }, { 0x6E0B, 0x6DA9 }, { 0x6E13, 0x6EAA },
	{ 0x6E1B, 0x51CF }, { 0x6E26, 0x6DA1 }, { 0x6E2C, 0x6D4B }, { 0x6E80, 0x6EE1 },
	{ 0x6E96, 0x51C6 }, { 0x6EC5, 0x706D }, { 0x6F01, 0x6E14 }, { 0x6F22, 0x6C49 },
	{ 0x6F38, 0x6E10 }, { 0x6F64, 0x6DA6 }, { 0x6FC1, 0x6D4A }, { 0x6FC3, 0x6D53 },
	{ 0x6FEB, 0x6EE5 }, { 0x7015, 0x6FD2 }, { 0x702C, 0x6FD1 }, { 0x70BA, 0x4E3A },
	{ 0x7121, 0x65E0 }, { 0x713C, 0x70E7 }, { 0x7159, 0x70DF }, { 0x71B1, 0x70ED },
	{ 0x731F, 0x730E }, { 0x7363, 0x517D }, { 0x7372, 0x83B7 }, { 0x73FE, 0x73B0 },
	{ 0x7523, 0x4EA7 }, { 0x7570, 0x5F02 }, { 0x7573, 0x53E0 }, { 0x7642, 0x7597 },
	{ 0x7652, 0x6108 }, { 0x767A, 0x53D1 }, { 0x76E3, 0x76D1 }, { 0x770C, 0x53BF },
	{ 0x7815, 0x788E }, { 0x7832, 0x70AE }, { 0x78BA, 0x786E }, { 0x7A2E, 0x79CD },
	{ 0x7A32, 0x7A3B }, { 0x7A4D, 0x79EF }, { 0x7A4F, 0x7A33 }, { 0x7A6B, 0x83B7 },
	{ 0x7A93, 0x7A97 }, { 0x7ADC, 0x9F99 }, { 0x7AF6, 0x7ADE }, { 0x7B46, 0x7B14 },
	{ 0x7B87, 0x4E2A }, { 0x7BC0, 0x8282 }, { 0x7BC4, 0x8303 }, { 0x7BC9, 0x7B51 },
	{ 0x7C21, 0x7B80 }, { 0x7C8B, 0x7CB9 }, { 0x7CE7, 0x7CAE }, { 0x7CF8, 0x4E1D },
	{ 0x7D19, 0x7EB8 }, { 0x7D1A, 0x7EA7 }, { 0x7D4C, 0x7ECF }, { 0x7D75, 0x7ED8 },
	{ 0x7D99, 0x7EE7 }, { 0x7D9A, 0x7EED }, { 0x7DCF, 0x603B }, { 0x7DD1, 0x7EFF },
	{ 0x7DD2, 0x7EEA }, { 0x7DDA, 0x7EBF }, { 0x7DE8, 0x7F16 }, { 0x7E01, 0x7F18 },
	{ 0x7E04, 0x7EF3 }, { 0x7E26, 0x7EB5 }, { 0x7E2E, 0x7F29 }, { 0x7E3E, 0x7EE9 },
	{ 0x7E4A, 0x7EA4 }, { 0x7E54, 0x7EC7 }, { 0x7F70, 0x7F5A }, { 0x7F85, 0x7F57 },
	{ 0x7FA9, 0x4E49 }, { 0x8056, 0x5723 }, { 0x805E, 0x95FB }, { 0x8074, 0x542C },
	{ 0x8077, 0x804C }, { 0x8108, 0x8109 }, { 0x8133, 0x8111 }, { 0x81D3, 0x810F },
	{ 0x81E8, 0x4E34 }, { 0x820E, 0x820D }, { 0x8217, 0x94FA }, { 0x8266, 0x8230 },
	{ 0x8276, 0x8273 }, { 0x82B8, 0x827A }, { 0x8358, 0x5E84 }, { 0x83EF, 0x534E },
	{ 0x8449, 0x53F6 }, { 0x8535, 0x85CF }, { 0x85AB, 0x85B0 }, { 0x85AC, 0x836F },
	{ 0x865C, 0x864F }, { 0x86CD, 0x8424 }, { 0x8846, 0x4F17 }, { 0x885B, 0x536B },
	{ 0x885D, 0x51B2 }, { 0x88CF, 0x91CC }, { 0x88DC, 0x8865 }, { 0x88FD, 0x5236 },
	{ 0x8907, 0x590D }, { 0x8972, 0x88AD }, { 0x8996, 0x89C6 }, { 0x899A, 0x89C9 },
	{ 0x89A7, 0x89C8 }, { 0x89AA, 0x4EB2 }, { 0x89B3, 0x89C2 }, { 0x8A02, 0x8BA2 },
	{ 0x8A0E, 0x8BA8 }, { 0x8A13, 0x8BAD }, { 0x8A17, 0x6258 }, { 0x8A18, 0x8BB0 },
	{ 0x8A1F, 0x8BBC }, { 0x8A2A, 0x8BBF }, { 0x8A2D, 0x8BBE }, { 0x8A31, 0x8BB8 },
	{ 0x8A33, 0x8BD1 }, { 0x8A34, 0x8BC9 }, { 0x8A3A, 0x8BCA }, { 0x8A3C, 0x8BC1 },
	{ 0x8A50, 0x8BC8 }, { 0x8A55, 0x8BC4 }, { 0x8A5E, 0x8BCD }, { 0x8A60, 0x548F },
	{ 0x8A66, 0x8BD5 }, { 0x8A69, 0x8BD7 }, { 0x8A71, 0x8BDD }, { 0x8A72, 0x8BE5 },
	{ 0x8A73, 0x8BE6 }, { 0x8A87, 0x5938 }, { 0x8A8C, 0x5FD7 }, { 0x8A8D, 0x8BA4 },
	{ 0x8A95, 0x8BDE }, { 0x8A98, 0x8BF1 }, { 0x8A9E, 0x8BED }, { 0x8AA0, 0x8BDA },
	{ 0x8AA4, 0x8BEF }, { 0x8AAC, 0x8BF4 }, { 0x8AAD, 0x8BFB }, { 0x8AB0, 0x8C01 },
	{ 0x8AB2, 0x8BFE }, { 0x8ABF, 0x8C03 }, { 0x8AC7, 0x8C08 }, { 0x8ACB, 0x8BF7 },
	{ 0x8AD6, 0x8BBA }, { 0x8AF8, 0x8BF8 }, { 0x8AFE, 0x8BFA }, { 0x8B00, 0x8C0B },
	{ 0x8B01, 0x8C12 }, { 0x8B04, 0x8A8A }, { 0x8B0E, 0x8C1C }, { 0x8B19, 0x8C26 },
	{ 0x8B1B, 0x8BB2 }, { 0x8B1D, 0x8C22 }, { 0x8B21, 0x8C23 }, { 0x8B39, 0x8C28 },
	{ 0x8B58, 0x8BC6 }, { 0x8B5C, 0x8C31 }, { 0x8B70, 0x8BAE }, { 0x8B72, 0x8BA9 },
	{ 0x8B77, 0x62A4 }, { 0x8C4A, 0x4E30 }, { 0x8C9D, 0x8D1D }, { 0x8CA0, 0x8D1F },
	{ 0x8CA1, 0x8D22 }, { 0x8CA7, 0x8D2B }, { 0x8CA8, 0x8D27 }, { 0x8CA9, 0x8D29 },
	{ 0x8CAB, 0x8D2F }, { 0x8CAC, 0x8D23 }, { 0x8CAF, 0x8D2E }, { 0x8CB4, 0x8D35 },
	{ 0x8CB7, 0x4E70 }, { 0x8CB8, 0x8D37 }, { 0x8CBB, 0x8D39 }, { 0x8CBF, 0x8D38 },
	{ 0x8CC0, 0x8D3A }, { 0x8CC3, 0x8D41 }, { 0x8CC4, 0x8D3F }, { 0x8CC7, 0x8D44 },
	{ 0x8CCA, 0x8D3C }, { 0x8CD3, 0x5BBE }, { 0x8CDB, 0x8D5E }, { 0x8CDC, 0x8D50 },
	{ 0x8CDE, 0x8D4F }, { 0x8CE0, 0x8D54 }, { 0x8CE2, 0x8D24 }, { 0x8CE6, 0x8D4B },
	{ 0x8CEA, 0x8D28 }, { 0x8CED, 0x8D4C }, { 0x8CFC, 0x8D2D }, { 0x8D08, 0x8D60 },
	{ 0x8E8D, 0x8DC3 }, { 0x8ECA, 0x8F66 }, { 0x8ECC, 0x8F68 }, { 0x8ECD, 0x519B },
	{ 0x8ED2, 0x8F69 }, { 0x8EDF, 0x8F6F }, { 0x8EE2, 0x8F6C }, { 0x8EF8, 0x8F74 },
	{ 0x8EFD, 0x8F7B }, { 0x8F03, 0x8F83 }, { 0x8F09, 0x8F7D }, { 0x8F1D, 0x8F89 },
	{ 0x8F29, 0x8F88 }, { 0x8F2A, 0x8F6E }, { 0x8F38, 0x8F93 }, { 0x8F44, 0x8F96 },
	{ 0x8FB2, 0x519C }, { 0x8FBA, 0x8FB9 }, { 0x9023, 0x8FDE }, { 0x9032, 0x8FDB },
	{ 0x9045, 0x8FDF }, { 0x904B, 0x8FD0 }, { 0x904E, 0x8FC7 }, { 0x9054, 0x8FBE },
	{ 0x9055, 0x8FDD }, { 0x9060, 0x8FDC }, { 0x9069, 0x9002 }, { 0x9078, 0x9009 },
	{ 0x907A, 0x9057 }, { 0x9084, 0x8FD8 }, { 0x90F5, 0x90AE }, { 0x90F7, 0x4E61 },
	{ 0x9154, 0x9189 }, { 0x919C, 0x4E11 }, { 0x91B8, 0x917F }, { 0x91C8, 0x91CA },
	{ 0x91DD, 0x9488 }, { 0x91E3, 0x9493 }, { 0x920D, 0x949D }, { 0x9234, 0x94C3 },
	{ 0x9244, 0x94C1 }, { 0x925B, 0x94C5 }, { 0x9262, 0x94B5 }, { 0x9271, 0x77FF },
	{ 0x9280, 0x94F6 }, { 0x9283, 0x67AA }, { 0x9285, 0x94DC }, { 0x92AD, 0x94B1 },
	{ 0x92FC, 0x94A2 }, { 0x932C, 0x70BC }, { 0x932F, 0x9519 }, { 0x9332, 0x5F55 },
	{ 0x9396, 0x9501 }, { 0x93AE, 0x9547 }, { 0x93E1, 0x955C }, { 0x9418, 0x949F },
	{ 0x9451, 0x9274 }, { 0x9577, 0x957F }, { 0x9580, 0x95E8 }, { 0x9589, 0x95ED },
	{ 0x958B, 0x5F00 }, { 0x9591, 0x95F2 }, { 0x9593, 0x95F4 }, { 0x95A2, 0x5173 },
	{ 0x95A3, 0x9601 }, { 0x95A5, 0x9600 }, { 0x95B2, 0x9605 }, { 0x95D8, 0x6597 },
	{ 0x9665, 0x9677 }, { 0x9670, 0x9634 }, { 0x9673, 0x9648 }, { 0x9678, 0x9646 },
	{ 0x967A, 0x9669 }, { 0x967D, 0x9633 }, { 0x968A, 0x961F }, { 0x968E, 0x9636 },
	{ 0x969B, 0x9645 }, { 0x96A0, 0x9690 }, { 0x96A3, 0x90BB }, { 0x96BB, 0x53EA },
	{ 0x96D1, 0x6742 }, { 0x96E2, 0x79BB }, { 0x96E3, 0x96BE }, { 0x96F2, 0x4E91 },
	{ 0x96FB, 0x7535 }, { 0x970A, 0x7075 }, { 0x9727, 0x96FE }, { 0x97D3, 0x97E9 },
	{ 0x97FB, 0x97F5 }, { 0x97FF, 0x54CD }, { 0x9802, 0x9876 }, { 0x9803, 0x9877 },
	{ 0x9805, 0x9879 }, { 0x9806, 0x987A }, { 0x9810, 0x9884 }, { 0x9811, 0x987D },
	{ 0x9812, 0x9881 }, { 0x9818, 0x9886 }, { 0x982D, 0x5934 }, { 0x983B, 0x9891 },
	{ 0x983C, 0x8D56 }, { 0x984C, 0x9898 }, { 0x984D, 0x989D }, { 0x9854, 0x989C },
	{ 0x9855, 0x663E }, { 0x9858, 0x613F }, { 0x985E, 0x7C7B }, { 0x9867, 0x987E },
	{ 0x98A8, 0x98CE }, { 0x98DB, 0x98DE }, { 0x98E2, 0x9965 }, { 0x98EF, 0x996D },
	{ 0x98F2, 0x996E }, { 0x98FC, 0x9972 }, { 0x98FD, 0x9971 }, { 0x98FE, 0x9970 },
	{ 0x990A, 0x517B }, { 0x9928, 0x9986 }, { 0x99AC, 0x9A6C }, { 0x99C4, 0x9A6E },
	{ 0x99C5, 0x7AD9 }, { 0x99C6, 0x9A71 }, { 0x99D0, 0x9A7B }, { 0x9A0E, 0x9A91 },
	{ 0x9A12, 0x9A9A }, { 0x9A13, 0x9A8C }, { 0x9A5A, 0x60CA }, { 0x9ACF, 0x9AC5 },
	{ 0x9AD1, 0x9AB7 }, { 0x9AEA, 0x53D1 }, { 0x9B5A, 0x9C7C }, { 0x9BAE, 0x9C9C },
	{ 0x9BE8, 0x9CB8 }, { 0x9CE5, 0x9E1F }, { 0x9CF4, 0x9E23 }, { 0x9D8F, 0x9E21 },
	{ 0x9DB4, 0x9E64 }, { 0x9E97, 0x4E3D }, { 0x9EBA, 0x9762 }, { 0x9ED2, 0x9ED1 },
	{ 0x9ED9, 0x9ED8 }, { 0x9F62, 0x9F84 }, { 0x9F8D, 0x9F99 },
};

static bool cjk_is_ascii_only(const char *s)
{
	for (const unsigned char *p = (const unsigned char *)s; *p; p++) {
		if (*p >= 0x80)
			return false;
	}
	return true;
}

static uint16_t cjk_canon_bmp(uint16_t cp)
{
	size_t lo = 0;
	size_t hi = sizeof(shinjitai_canon_map) / sizeof(shinjitai_canon_map[0]);
	while (lo < hi) {
		size_t mid = lo + (hi - lo) / 2;
		uint16_t key = shinjitai_canon_map[mid].from;
		if (cp < key)
			hi = mid;
		else if (cp > key)
			lo = mid + 1;
		else
			return shinjitai_canon_map[mid].to;
	}
	return cp;
}

/* All mapped codepoints are BMP >= 0x4E00 (3 UTF-8 bytes), so in-place rewrite preserves byte length. */
static void canon_kanji_utf8_inplace(char *utf8)
{
	uint8_t *p = (uint8_t *)utf8;
	while (*p) {
		if (*p < 0x80) {
			p++;
		} else if ((*p & 0xe0) == 0xc0 && p[1]) {
			p += 2;
		} else if ((*p & 0xf0) == 0xe0 && p[1] && p[2]) {
			uint16_t cp = (uint16_t)(((p[0] & 0x0f) << 12) | ((p[1] & 0x3f) << 6) | (p[2] & 0x3f));
			uint16_t mapped = cjk_canon_bmp(cp);
			if (mapped != cp) {
				p[0] = (uint8_t)(0xe0 | (mapped >> 12));
				p[1] = (uint8_t)(0x80 | ((mapped >> 6) & 0x3f));
				p[2] = (uint8_t)(0x80 | (mapped & 0x3f));
			}
			p += 3;
		} else {
			p++;
		}
	}
}

static void decode_cjk_forms_buf(const char *s, bool canonicalize,
		char *sjis_buf, size_t sjis_cap, char **sjis_out,
		char *gbk_buf, size_t gbk_cap, char **gbk_out)
{
	bool ok_s = false, ok_g = false;
	size_t slen = strlen(s);
	*sjis_out = cjk2utf_strict_buf(s, slen, false, sjis_buf, sjis_cap, &ok_s);
	*gbk_out = cjk2utf_strict_buf(s, slen, true, gbk_buf, gbk_cap, &ok_g);
	if (!ok_s) {
		if (*sjis_out != sjis_buf)
			free(*sjis_out);
		*sjis_out = NULL;
	} else if (canonicalize) {
		canon_kanji_utf8_inplace(*sjis_out);
	}
	if (!ok_g) {
		if (*gbk_out != gbk_buf)
			free(*gbk_out);
		*gbk_out = NULL;
	} else if (canonicalize) {
		canon_kanji_utf8_inplace(*gbk_out);
	}
}

static bool save_cjk_match_impl(const char *a, const char *b, bool canonicalize)
{
	if (a == b)
		return true;
	if (!a || !b)
		return false;
	if (!strcmp(a, b))
		return true;
	if (cjk_is_ascii_only(a) && cjk_is_ascii_only(b))
		return false;

	char as_buf[512], ag_buf[512], bs_buf[512], bg_buf[512];
	char *as = NULL, *ag = NULL, *bs = NULL, *bg = NULL;
	decode_cjk_forms_buf(a, canonicalize, as_buf, sizeof(as_buf), &as, ag_buf, sizeof(ag_buf), &ag);
	decode_cjk_forms_buf(b, canonicalize, bs_buf, sizeof(bs_buf), &bs, bg_buf, sizeof(bg_buf), &bg);

	bool match = (as && bg && !strcmp(as, bg))
		|| (ag && bs && !strcmp(ag, bs))
		|| (as && bs && !strcmp(as, bs))
		|| (ag && bg && !strcmp(ag, bg));

	if (as != as_buf)
		free(as);
	if (ag != ag_buf)
		free(ag);
	if (bs != bs_buf)
		free(bs);
	if (bg != bg_buf)
		free(bg);
	return match;
}

bool save_names_equal(const char *a, const char *b)
{
	return save_cjk_match_impl(a, b, false);
}

bool save_symbols_equal(const char *a, const char *b)
{
	return save_cjk_match_impl(a, b, true);
}

void save_cjk_canon_forms_buf(const char *s, char *sjis_buf, size_t sjis_cap, char **sjis_out,
		char *gbk_buf, size_t gbk_cap, char **gbk_out)
{
	if (!s) {
		*sjis_out = NULL;
		*gbk_out = NULL;
		return;
	}
	decode_cjk_forms_buf(s, true, sjis_buf, sjis_cap, sjis_out, gbk_buf, gbk_cap, gbk_out);
}

void save_cjk_canon_forms(const char *s, char **sjis_out, char **gbk_out)
{
	save_cjk_canon_forms_buf(s, NULL, 0, sjis_out, NULL, 0, gbk_out);
}

bool save_symbol_prefix_equal(const char *full_sym, const char *prefix_buf, size_t prefix_len)
{
	if (!full_sym || !prefix_buf)
		return false;
	char *tmp = xmalloc(prefix_len + 1);
	memcpy(tmp, prefix_buf, prefix_len);
	tmp[prefix_len] = '\0';
	bool eq = save_symbols_equal(full_sym, tmp);
	free(tmp);
	return eq;
}

static uint16_t hankaku_to_zenkaku_u16(uint16_t cp)
{
	static const uint16_t map[0xa0 - 0x61] = {
		0x3002, 0x300C, 0x300D, 0x3001, 0x00B7, 0x30F2, 0x30A1, 0x30A3, // ｡｢｣､･ｦｧｨ
		0x30A5, 0x30A7, 0x30A9, 0x30E3, 0x30E5, 0x30E7, 0x30C3, 0x30FC, // ｩｪｫｬｭｮｯｰ
		0x30A2, 0x30A4, 0x30A6, 0x30A8, 0x30AA, 0x30AB, 0x30AD, 0x30AF, // ｱｲｳｴｵｶｷｸ
		0x30B1, 0x30B3, 0x30B5, 0x30B7, 0x30B9, 0x30BB, 0x30BD, 0x30BF, // ｹｺｻｼｽｾｿﾀ
		0x30C1, 0x30C4, 0x30C6, 0x30C8, 0x30CA, 0x30CB, 0x30CC, 0x30CD, // ﾁﾂﾃﾄﾅﾆﾇﾈ
		0x30CE, 0x30CF, 0x30D2, 0x30D5, 0x30D8, 0x30DB, 0x30DE, 0x30DF, // ﾉﾊﾋﾌﾍﾎﾏﾐ
		0x30E0, 0x30E1, 0x30E2, 0x30E4, 0x30E6, 0x30E8, 0x30E9, 0x30EA, // ﾑﾒﾓﾔﾕﾖﾗﾘ
		0x30EB, 0x30EC, 0x30ED, 0x30EF, 0x30F3, 0x309B, 0x309C          // ﾙﾚﾛﾜﾝﾞﾟ
	};
	if (cp >= 0xff61 && cp <= 0xff9f)
		return map[cp - 0xff61];
	return 0;
}

static int encode_utf8_cp_with_fallback(uint32_t u, bool dst_is_gbk)
{
	if (u <= 0x7f)
		return (int)u;
	if (u < 65536) {
		int c = cjk_encode_codepoint((int)u, dst_is_gbk);
		if (c)
			return c;
	}
	if (dst_is_gbk) {
		uint16_t zen = hankaku_to_zenkaku_u16((uint16_t)u);
		if (zen) {
			int c = cjk_encode_codepoint(zen, true);
			if (c)
				return c;
		}
		if (u == 0x30fb)
			return cjk_encode_codepoint(0x00b7, true);
		if (u == 0x301c)
			return cjk_encode_codepoint(0xff5e, true);
		if (u == 0x2212)
			return cjk_encode_codepoint(0xff0d, true);
	} else {
		if (u == 0x00b7)
			return cjk_encode_codepoint(0x30fb, false);
	}
	if (u > 0x7f && u < 65536) {
		uint16_t canon = cjk_canon_bmp((uint16_t)u);
		if (canon != u) {
			int c = cjk_encode_codepoint(canon, dst_is_gbk);
			if (c)
				return c;
		}
		size_t n = sizeof(shinjitai_canon_map) / sizeof(shinjitai_canon_map[0]);
		for (size_t i = 0; i < n; i++) {
			if (shinjitai_canon_map[i].to == canon || shinjitai_canon_map[i].from == canon) {
				int c = cjk_encode_codepoint(shinjitai_canon_map[i].from, dst_is_gbk);
				if (c)
					return c;
				c = cjk_encode_codepoint(shinjitai_canon_map[i].to, dst_is_gbk);
				if (c)
					return c;
			}
		}
	}
	return '?';
}

static char *decode_cjk_lenient(const char *s, size_t len, bool is_gbk)
{
	bool ok = false;
	char *u = cjk2utf_strict_buf(s, len, is_gbk, NULL, 0, &ok);
	if (ok && u)
		return u;
	free(u);
	// Lenient fallback: step character-by-character so one stray byte never drops a whole string.
	char *tmp = xmalloc(len + 1);
	memcpy(tmp, s, len);
	tmp[len] = '\0';
	size_t cap = len * 3 + 4;
	uint8_t *dst = xmalloc(cap);
	uint8_t *dstp = dst;
	const uint8_t *p = (const uint8_t *)tmp;
	while (*p) {
		if ((size_t)(dstp - dst) + 4 >= cap) {
			size_t used = (size_t)(dstp - dst);
			cap *= 2;
			dst = xrealloc(dst, cap);
			dstp = dst + used;
		}
		char one[3] = { (char)p[0], 0, 0 };
		size_t step = 1;
		if (p[0] >= 0x80 && (is_gbk || !(p[0] >= 0xa1 && p[0] <= 0xdf)) && p[1]) {
			one[1] = (char)p[1];
			step = 2;
		}
		bool one_ok = false;
		char *cu = cjk2utf_strict(one, is_gbk, &one_ok);
		if (one_ok && cu) {
			size_t clen = strlen(cu);
			memcpy(dstp, cu, clen);
			dstp += clen;
		} else {
			*dstp++ = '?';
		}
		free(cu);
		p += step;
	}
	*dstp = '\0';
	free(tmp);
	return (char *)dst;
}

int save_text_gbk_bias(const char *s)
{
	if (!s || !s[0] || cjk_is_ascii_only(s))
		return 0;
	bool ok_s = false, ok_g = false;
	char *us = cjk2utf_strict(s, false, &ok_s);
	char *ug = cjk2utf_strict(s, true, &ok_g);
	if (ok_s && !ok_g) {
		free(us);
		free(ug);
		return -100;
	}
	if (!ok_s && ok_g) {
		free(us);
		free(ug);
		return 100;
	}

	int bias = 0;
	if (us) {
		const uint8_t *p = (const uint8_t *)us;
		while (*p) {
			uint32_t cp = 0;
			if (*p < 0x80) {
				cp = *p++;
			} else if ((*p & 0xe0) == 0xc0 && p[1]) {
				cp = ((p[0] & 0x1f) << 6) | (p[1] & 0x3f);
				p += 2;
			} else if ((*p & 0xf0) == 0xe0 && p[1] && p[2]) {
				cp = ((p[0] & 0x0f) << 12) | ((p[1] & 0x3f) << 6) | (p[2] & 0x3f);
				p += 3;
			} else {
				p++;
			}
			if ((cp >= 0x3000 && cp <= 0x30ff) || (cp >= 0xff01 && cp <= 0xff5e))
				bias -= 12;
			else if (cp >= 0xff61 && cp <= 0xff9f)
				bias += 8;
		}
	}
	if (ug) {
		const uint8_t *p = (const uint8_t *)ug;
		while (*p) {
			uint32_t cp = 0;
			if (*p < 0x80) {
				cp = *p++;
			} else if ((*p & 0xe0) == 0xc0 && p[1]) {
				cp = ((p[0] & 0x1f) << 6) | (p[1] & 0x3f);
				p += 2;
			} else if ((*p & 0xf0) == 0xe0 && p[1] && p[2]) {
				cp = ((p[0] & 0x0f) << 12) | ((p[1] & 0x3f) << 6) | (p[2] & 0x3f);
				p += 3;
			} else {
				p++;
			}
			if ((cp >= 0x3000 && cp <= 0x30ff) || (cp >= 0xff01 && cp <= 0xff5e))
				bias += 12;
		}
	}
	for (const uint8_t *r = (const uint8_t *)s; *r;) {
		if (*r < 0x80) {
			r++;
		} else if (r[0] >= 0xb0 && r[0] <= 0xf7 && r[1] >= 0xa1 && r[1] <= 0xfe) {
			bias += 4;
			r += 2;
		} else if (r[1]) {
			r += 2;
		} else {
			break;
		}
	}
	free(us);
	free(ug);
	return bias;
}

static char *encode_utf8_to_cjk_fallback(const char *utf8, bool dst_is_gbk, size_t *out_len)
{
	size_t cap = strlen(utf8) * 2 + 4;
	uint8_t *dst = xmalloc(cap);
	uint8_t *dstp = dst;
	const uint8_t *p = (const uint8_t *)utf8;
	while (*p) {
		if (*p <= 0x7f) {
			*dstp++ = *p++;
			continue;
		}
		uint32_t u = '?';
		if ((*p & 0xe0) == 0xc0 && p[1]) {
			u = ((p[0] & 0x1f) << 6) | (p[1] & 0x3f);
			p += 2;
		} else if ((*p & 0xf0) == 0xe0 && p[1] && p[2]) {
			u = ((p[0] & 0x0f) << 12) | ((p[1] & 0x3f) << 6) | (p[2] & 0x3f);
			p += 3;
		} else if ((*p & 0xf8) == 0xf0 && p[1] && p[2] && p[3]) {
			p += 4;
		} else {
			p++;
		}
		int c = encode_utf8_cp_with_fallback(u, dst_is_gbk);
		if (c > 0xff) {
			*dstp++ = (uint8_t)(c >> 8);
			*dstp++ = (uint8_t)(c & 0xff);
		} else {
			*dstp++ = (uint8_t)(c ? c : '?');
		}
	}
	*dstp = '\0';
	if (out_len)
		*out_len = (size_t)(dstp - dst);
	return (char *)dst;
}

static char *copy_ascii_or_same_cstr(const char *s, size_t len, bool src_is_gbk,
		bool dst_is_gbk, size_t *out_len, bool *handled_out)
{
	*handled_out = false;
	bool ascii = true;
	for (size_t i = 0; i < len; i++) {
		if ((uint8_t)s[i] >= 0x80) {
			ascii = false;
			break;
		}
	}
	if (ascii || src_is_gbk == dst_is_gbk || len == 0) {
		char *copy = xmalloc(len + 1);
		memcpy(copy, s, len);
		copy[len] = '\0';
		if (out_len)
			*out_len = len;
		*handled_out = true;
		return copy;
	}
	return NULL;
}

char *save_transcode_cstr(const char *s, size_t len, bool src_is_gbk, bool dst_is_gbk, size_t *out_len)
{
	if (!s) {
		if (out_len)
			*out_len = 0;
		return xstrdup("");
	}
	bool handled = false;
	char *fast = copy_ascii_or_same_cstr(s, len, src_is_gbk, dst_is_gbk, out_len, &handled);
	if (handled)
		return fast;

	char *utf8 = decode_cjk_lenient(s, len, src_is_gbk);
	char *res = encode_utf8_to_cjk_fallback(utf8, dst_is_gbk, out_len);
	free(utf8);
	return res;
}

/*
 * Like save_transcode_cstr, but fails fast (returns NULL) when strict SJIS/GBK
 * decoding encounters an invalid byte sequence instead of leniently retrying per character.
 */
char *save_transcode_cstr_strict(const char *s, size_t len, bool src_is_gbk, bool dst_is_gbk, size_t *out_len)
{
	if (!s) {
		if (out_len)
			*out_len = 0;
		return NULL;
	}
	bool handled = false;
	char *fast = copy_ascii_or_same_cstr(s, len, src_is_gbk, dst_is_gbk, out_len, &handled);
	if (handled)
		return fast;

	char utf_stack[1536];
	bool ok = false;
	char *utf8 = cjk2utf_strict_buf(s, len, src_is_gbk, utf_stack, sizeof(utf_stack), &ok);
	if (!ok || !utf8) {
		if (utf8 != utf_stack)
			free(utf8);
		if (out_len)
			*out_len = 0;
		return NULL;
	}
	char *res = encode_utf8_to_cjk_fallback(utf8, dst_is_gbk, out_len);
	if (utf8 != utf_stack)
		free(utf8);
	return res;
}

struct string *save_decode_vm_string(const char *s, size_t len, bool src_is_gbk)
{
	if (!s || len == 0)
		return make_string("", 0);
	size_t out_len = 0;
	char *conv = save_transcode_cstr(s, len, src_is_gbk, sys4_text_is_gbk, &out_len);
	struct string *str = make_string(conv, out_len);
	free(conv);
	return str;
}

static size_t utf8_to_canon_u32(const char *utf8, uint32_t *out, size_t max_len)
{
	size_t n = 0;
	const uint8_t *p = (const uint8_t *)utf8;
	while (*p && n < max_len) {
		uint32_t cp;
		if (*p < 0x80) {
			cp = *p++;
		} else if ((*p & 0xe0) == 0xc0 && p[1]) {
			cp = ((p[0] & 0x1f) << 6) | (p[1] & 0x3f);
			p += 2;
		} else if ((*p & 0xf0) == 0xe0 && p[1] && p[2]) {
			uint16_t bmp = (uint16_t)(((p[0] & 0x0f) << 12) | ((p[1] & 0x3f) << 6) | (p[2] & 0x3f));
			cp = cjk_canon_bmp(bmp);
			p += 3;
		} else if ((*p & 0xf8) == 0xf0 && p[1] && p[2] && p[3]) {
			cp = ((p[0] & 0x07) << 18) | ((p[1] & 0x3f) << 12) | ((p[2] & 0x3f) << 6) | (p[3] & 0x3f);
			p += 4;
		} else {
			cp = *p++;
		}
		out[n++] = cp;
	}
	return n;
}

static char *decode_best_utf8(const char *s, bool prefer_gbk)
{
	bool ok = false;
	char *u = cjk2utf_strict(s, prefer_gbk, &ok);
	if (ok && u)
		return u;
	free(u);
	u = cjk2utf_strict(s, !prefer_gbk, &ok);
	if (ok && u)
		return u;
	free(u);
	bool prev = sys4_text_is_gbk;
	sys4_set_text_gbk(prefer_gbk);
	u = sjis2utf(s ? s : "", 0);
	sys4_set_text_gbk(prev);
	return u;
}

int save_symbol_similarity(const char *a, const char *b)
{
	if (!a || !b)
		return 0;
	char *au = decode_best_utf8(a, sys4_text_is_gbk);
	char *bu = decode_best_utf8(b, !sys4_text_is_gbk);
	uint32_t ua[256], ub[256];
	size_t na = utf8_to_canon_u32(au, ua, 256);
	size_t nb = utf8_to_canon_u32(bu, ub, 256);
	free(au);
	free(bu);

	size_t lcp = 0;
	while (lcp < na && lcp < nb && ua[lcp] == ub[lcp])
		lcp++;
	size_t lcs = 0;
	while (lcs < na - lcp && lcs < nb - lcp && ua[na - 1 - lcs] == ub[nb - 1 - lcs])
		lcs++;

	int shared = 0;
	for (size_t i = lcp; i < na - lcs; i++) {
		for (size_t j = lcp; j < nb - lcs; j++) {
			if (ua[i] == ub[j]) {
				shared++;
				break;
			}
		}
	}
	int unmatched = (int)((na - lcp - lcs) + (nb - lcp - lcs));
	return (int)((lcp + lcs) * 100) + shared * 15 - unmatched * 10;
}

static int find_named_entry_compat(const void *base, size_t stride, int count, size_t name_off, const char *name)
{
	const char *p = (const char *)base;
	for (int i = 0; i < count; i++, p += stride) {
		const char *entry_name = *(const char * const *)(p + name_off);
		if (entry_name && save_symbols_equal(entry_name, name))
			return i;
	}
	return -1;
}

int save_find_struct(const char *name)
{
	if (!name || !name[0])
		return -1;
	int id = ain_get_struct(ain, (char *)name);
	if (id >= 0)
		return id;
	return find_named_entry_compat(ain->structures, sizeof(*ain->structures), ain->nr_structures,
		offsetof(struct ain_struct, name), name);
}

int save_find_enum(const char *name)
{
	if (!name || !name[0])
		return -1;
	int id = ain_get_enum(ain, (char *)name);
	if (id >= 0)
		return id;
	return find_named_entry_compat(ain->enums, sizeof(*ain->enums), ain->nr_enums,
		offsetof(struct ain_enum, name), name);
}

int save_find_delegate(const char *name)
{
	if (!name || !name[0])
		return -1;
	int id = ain_get_delegate(ain, (char *)name);
	if (id >= 0)
		return id;
	return find_named_entry_compat(ain->delegates, sizeof(*ain->delegates), ain->nr_delegates,
		offsetof(struct ain_function_type, name), name);
}

int save_find_functype(const char *name)
{
	if (!name || !name[0])
		return -1;
	int id = ain_get_functype(ain, (char *)name);
	if (id >= 0)
		return id;
	return find_named_entry_compat(ain->function_types, sizeof(*ain->function_types), ain->nr_function_types,
		offsetof(struct ain_function_type, name), name);
}

static int get_group_index(const char *name)
{
	for (int i = 0; i < ain->nr_global_groups; i++) {
		if (!strcmp(ain->global_group_names[i], name))
			return i;
	}
	for (int i = 0; i < ain->nr_global_groups; i++) {
		if (save_symbols_equal(ain->global_group_names[i], name))
			return i;
	}
	return -1;
}

static int32_t add_value_to_gsave(enum ain_data_type type, union vm_value val, struct gsave *save);

static bool nested_array(struct page *page)
{
	return page->a_type == AIN_ARRAY ? page->array.element_type.data == AIN_ARRAY : page->array.rank > 1;
}

static int page_array_rank(struct page *page)
{
	if (page->a_type != AIN_ARRAY) return page->array.rank;
	int rank = 1;
	for (const struct ain_type *t = &page->array.element_type; t && t->data == AIN_ARRAY; t = t->array_type)
		rank++;
	return rank;
}

static struct gsave_flat_array *collect_flat_arrays(struct page *page, struct gsave_flat_array *fa, struct gsave *save)
{
	if (nested_array(page)) {
		for (int i = 0; i < page->nr_vars; i++)
			fa = collect_flat_arrays(heap_get_page(page->values[i].i), fa, save);
		return fa;
	}
	enum ain_data_type type = variable_type(page, 0, NULL, NULL);
	fa->nr_values = page->nr_vars;
	fa->type = type;
	fa->values = xcalloc(fa->nr_values, sizeof(struct gsave_array_value));
	for (int i = 0; i < page->nr_vars; i++) {
		fa->values[i].type = type;
		fa->values[i].value = add_value_to_gsave(type, page->values[i], save);
	}
	return ++fa;
}

static int32_t add_value_to_gsave(enum ain_data_type type, union vm_value val, struct gsave *save)
{
	switch (type) {
	case AIN_VOID:
	case AIN_INT:
	case AIN_BOOL:
	case AIN_FUNC_TYPE:
	case AIN_LONG_INT:
	case AIN_FLOAT:
	case AIN_ENUM:
	case AIN_ENUM2:
		return val.i;
	case AIN_DELEGATE:
		// Group saves contain persistent data; the original v12 engine clears events.
		return ain->version >= 11 ? 0 : val.i;
	case AIN_STRING:
		return gsave_add_string(save, heap[val.i].s);
	case AIN_STRUCT:
		{
			struct page *page = heap_get_page(val.i);
			assert(page->type == STRUCT_PAGE);
			struct ain_struct *st = &ain->structures[page->index];
			assert(st->nr_members == page->nr_vars);
			struct gsave_record rec = {
				.type = GSAVE_RECORD_STRUCT,
				.struct_name = strdup(st->name),
				.nr_indices = st->nr_members,
				.indices = xcalloc(st->nr_members, sizeof(int32_t)),
			};
			if (save->version >= 7) {
				rec.struct_index = gsave_get_struct_def(save, st->name);
				if (rec.struct_index < 0)
					rec.struct_index = gsave_add_struct_def(save, st);
			}
			for (int i = 0; i < page->nr_vars; i++) {
				enum ain_data_type type = st->members[i].type.data;
				const char *field_name = st->members[i].name2 ? st->members[i].name2 : st->members[i].name;
				int32_t value = field_name[0] ? add_value_to_gsave(type, page->values[i], save) : 0;
				struct gsave_keyval kv = {
					.name = strdup(field_name),
					.type = type,
					.value = value,
				};
				rec.indices[i] = gsave_add_keyval(save, &kv);
			}
			return gsave_add_record(save, &rec);
		}
	case AIN_ARRAY_TYPE:
	case AIN_ARRAY:
		{
			struct page *page = heap_get_page(val.i);
			if (!page) {
				struct gsave_array array = { .rank = -1 };
				return gsave_add_array(save, &array);
			}
			assert(page->type == ARRAY_PAGE);
			int rank = page_array_rank(page);
			struct gsave_array array = {
				.rank = rank,
				.dimensions = xcalloc(rank, sizeof(int32_t)),
				.nr_flat_arrays = 1
			};
			struct page *p = page;
			for (int dim = rank - 1; dim >= 0; dim--) {
				int count = p ? p->nr_vars : 0;
				array.dimensions[dim] = count;
				if (dim) {
					array.nr_flat_arrays *= count;
					p = count ? heap_get_page(p->values[0].i) : NULL;
				}
			}
			array.flat_arrays = xcalloc(array.nr_flat_arrays, sizeof(struct gsave_flat_array));
			collect_flat_arrays(page, array.flat_arrays, save);
			return gsave_add_array(save, &array);
		}
	case AIN_REF_TYPE:
		return -1;
	default:
		WARNING("Unhandled type: %s", ain_strtype(ain, type, -1));
		return -1;
	}
}

static int get_gsave_version(void)
{
	if (ain->version >= 11) return 8;
	if (AIN_VERSION_GTE(ain, 6, 0)) {
		return config.vm_name ? 5 : 7;
	}
	return AIN_VERSION_GTE(ain, 5, 0) ? 5 : 4;
}

int save_globals(const char *keyname, const char *filename, const char *group_name, int *n_out)
{
	int group = -1;
	int nr_vars = 0;
	if (group_name) {
		if ((group = get_group_index(group_name)) < 0) {
			WARNING("Unregistered global group: %s", display_sjis0(group_name));
		} else {
			for (int i = 0; i < ain->nr_globals; i++) {
				if (ain->globals[i].name2 && !ain->globals[i].name2[0])
					continue;
				if (ain->globals[i].group_index == group)
					nr_vars++;
			}
		}
	} else {
		for (int i = 0; i < ain->nr_globals; i++) {
			if (!(ain->globals[i].name2 && !ain->globals[i].name2[0]))
				nr_vars++;
		}
	}

	struct gsave *save = gsave_create(get_gsave_version(), keyname, ain->nr_globals, group_name);
	gsave_add_globals_record(save, nr_vars);

	if (nr_vars > 0) {
		struct gsave_global *global = save->globals;
		for (int i = 0; i < ain->nr_globals; i++) {
			if (ain->globals[i].name2 && !ain->globals[i].name2[0])
				continue;
			if (group >= 0 && ain->globals[i].group_index != group)
				continue;
			const char *gname = (ain->globals[i].name2 && ain->globals[i].name2[0]) ? ain->globals[i].name2 : ain->globals[i].name;
			global->name = strdup(gname);
			global->type = ain->globals[i].type.data;
			global->value = add_value_to_gsave(global->type, global_get(i), save);
			global++;
		}
	}

	char *path = savedir_path(filename);
	char *tmp_path = NULL;
	FILE *fp = atomic_save_open(path, "wb", &tmp_path);
	if (!fp) {
		WARNING("Failed to open save file %s: %s", display_utf0(path), strerror(errno));
		free(path);
		gsave_free(save);
		return 0;
	}

	bool encrypt = !AIN_VERSION_GTE(ain, 6, 0);
	int compression_level = AIN_VERSION_GTE(ain, 6, 0) ? 1 : 9;
	enum savefile_error error = gsave_write(save, fp, encrypt, compression_level);
	gsave_free(save);
	if (error != SAVEFILE_SUCCESS) {
		WARNING("Failed to write save file: %s", savefile_strerror(error));
		atomic_save_abort(fp, tmp_path);
		free(path);
		return 0;
	}
	bool ok = atomic_save_commit(fp, path, tmp_path, is_critical_global_save(filename));
	free(path);
	if (ok) {
		track_global_save(keyname, filename, group_name);
		if (n_out)
			*n_out = nr_vars;
	}
	return ok ? 1 : 0;
}

static union vm_value json_to_vm_value(enum ain_data_type type, enum ain_data_type struct_type, int array_rank, cJSON *json);

void get_array_dims(cJSON *json, int rank, union vm_value *dims)
{
	cJSON *array = json;
	for (int i = 0; i < rank; i++) {
		dims[i].i = cJSON_GetArraySize(array);
		array = cJSON_GetArrayItem(array, 0);
	}
}

void json_load_page(struct page *page, cJSON *vars, bool call_dtors)
{
	int i = 0;
	cJSON *v;
	cJSON_ArrayForEach(v, vars) {
		int struct_type, array_rank;
		enum ain_data_type data_type = variable_type(page, i, &struct_type, &array_rank);
		union vm_value val = json_to_vm_value(data_type, struct_type, array_rank, v);
		if (call_dtors)
			variable_set(page, i, data_type, val);
		else
			page->values[i] = val;
		i++;
	}
}

static union vm_value json_to_vm_value(enum ain_data_type type, enum ain_data_type struct_type, int array_rank, cJSON *json)
{
	char *str;
	int slot;
	union vm_value *dims;
	struct page *page;
	switch (type) {
	case AIN_INT:
	case AIN_BOOL:
	case AIN_LONG_INT:
		if (!cJSON_IsNumber(json)) {
			invalid_save_data("Not a number", json);
			return vm_int(0);
		}
		return vm_int(json->valueint);
	case AIN_FLOAT:
		if (!cJSON_IsNumber(json)) {
			invalid_save_data("Not a number", json);
			return vm_float(0);
		}
		return vm_float(json->valuedouble);
	case AIN_STRING:
		slot = heap_alloc_slot(VM_STRING);
		if (!cJSON_IsString(json)) {
			invalid_save_data("Not a string", json);
			heap[slot].s = string_ref(&EMPTY_STRING);
			return vm_int(slot);
		}
		str = cJSON_GetStringValue(json);
		if (!str[0]) {
			heap[slot].s = string_ref(&EMPTY_STRING);
		} else {
			heap[slot].s = make_string(str, strlen(str));
		}
		return vm_int(slot);
	case AIN_STRUCT:
		slot = alloc_struct(struct_type);
		if (!cJSON_IsArray(json) || cJSON_GetArraySize(json) != ain->structures[struct_type].nr_members) {
			invalid_save_data("Not an array", json);
		} else {
			json_load_page(heap[slot].page, json, false);
		}
		return vm_int(slot);
	case AIN_ARRAY_TYPE:
		slot = heap_alloc_slot(VM_PAGE);
		if (cJSON_IsNull(json)) {
			heap[slot].page = NULL;
			return vm_int(slot);
		}
		if (!cJSON_IsArray(json)) {
			invalid_save_data("Not an array", json);
			heap[slot].page = NULL;
			return vm_int(slot);
		}
		dims = xmalloc(sizeof(union vm_value) * array_rank);
		get_array_dims(json, array_rank, dims);
		page = alloc_array(array_rank, dims, type, struct_type, false);
		heap[slot].page = page;
		free(dims);
		json_load_page(heap[slot].page, json, false);
		return vm_int(slot);
	case AIN_REF_TYPE:
		return vm_int(-1);
	default:
		WARNING("Unhandled data type: %s", ain_strtype(ain, type, -1));
		return vm_int(-1);
	}
}

static cJSON *read_save_file(const char *path)
{
	FILE *f;
	long len;
	char *buf;

	if (!(f = file_open_utf8(path, "rb"))) {
		WARNING("Failed to open save file: %s: %s", display_utf0(path), strerror(errno));
		return NULL;
	}

	fseek(f, 0, SEEK_END);
	len = ftell(f);
	fseek(f, 0, SEEK_SET);

	buf = xmalloc(len+1);
	buf[len] = '\0';
	if (fread(buf, len, 1, f) != 1) {
		WARNING("Failed to read save file: %s", display_utf0(path));
		free(buf);
		return 0;
	}
	fclose(f);

	cJSON *r = cJSON_Parse(buf);
	free(buf);
	return r;
}

static int load_globals_from_json(const char *path, const char *keyname, const char *group_name, int *n)
{
	int retval = 0;
	cJSON *save = read_save_file(path);
	if (!save)
		return 0;
	if (!cJSON_IsObject(save)) {
		invalid_save_data("Not an object", save);
		goto cleanup;
	}

	cJSON *key = cJSON_GetObjectItem(save, "key");
	if (!key || !save_names_equal(keyname, cJSON_GetStringValue(key)))
		VM_ERROR("Attempted to load save data with wrong key: %s", display_sjis0(keyname));

	if (group_name) {
		// TODO?
	}

	cJSON *globals = cJSON_GetObjectItem(save, "globals");
	if (!globals || !cJSON_IsArray(globals)) {
		invalid_save_data("Not an array", globals);
		goto cleanup;
	}

	cJSON *g;
	cJSON_ArrayForEach(g, globals) {
		cJSON *index, *value;
		if (!(index = cJSON_GetObjectItem(g, "index"))) {
			invalid_save_data("Missing index", g);
			goto cleanup;
		}
		if (!(value = cJSON_GetObjectItem(g, "value"))) {
			invalid_save_data("Missing value", g);
			goto cleanup;
		}
		if (!cJSON_IsNumber(index)) {
			invalid_save_data("Not a number", index);
			goto cleanup;
		}

		int i = index->valueint;
		if (i < 0 || i > ain->nr_globals) {
			invalid_save_data("Invalid global index", index);
			goto cleanup;
		}
		current_global = i;

		if (should_preserve_static_global(i)) {
			if (n)
				(*n)++;
			continue;
		}
		bool call_dtors = false; // Destructors for old objects are not called.
		global_set(i, json_to_vm_value(ain->globals[i].type.data, ain->globals[i].type.struc, ain->globals[i].type.rank, value), call_dtors);
		if (n)
			(*n)++;
	}

	retval = 1;
cleanup:
	cJSON_Delete(save);
	return retval;
}

static int struct_member_index(struct ain_struct *st, const char *name)
{
	if (!name || !name[0])
		return -1;
	for (int i = 0; i < st->nr_members; i++) {
		if (st->members[i].name2 && st->members[i].name2[0] && !strcmp(st->members[i].name2, name))
			return i;
		if (!strcmp(st->members[i].name, name))
			return i;
	}
	for (int i = 0; i < st->nr_members; i++) {
		if (st->members[i].name2 && st->members[i].name2[0] && save_symbols_equal(st->members[i].name2, name))
			return i;
		if (st->members[i].name && save_symbols_equal(st->members[i].name, name))
			return i;
	}
	return -1;
}

static bool gsave_struct_matches(const struct ain_struct *st, const char *struct_name,
		const struct gsave_record *rec, const struct gsave *save,
		const struct gsave_struct_def *sd)
{
	if (save_symbols_equal(struct_name, st->name))
		return true;
	if (!rec || rec->nr_indices != st->nr_members || st->nr_members <= 0)
		return false;
	for (int i = 0; i < rec->nr_indices; i++) {
		const struct gsave_keyval *kv = &save->keyvals[rec->indices[i]];
		const char *field_name = sd ? sd->fields[i].name : kv->name;
		enum ain_data_type field_type = sd ? sd->fields[i].type : kv->type;
		if (!field_name || !field_name[0]) {
			if (st->members[i].name && st->members[i].name[0])
				return false;
			continue;
		}
		if (st->members[i].type.data != field_type)
			return false;
	}
	return true;
}

static union vm_value gsave_to_vm_value(struct gsave *save, enum ain_data_type type, int struct_type, int array_rank, int32_t value, bool src_is_gbk);
static union vm_value gsave_to_vm_value_typed(struct gsave *save, const struct ain_type *type, int32_t value, bool src_is_gbk);

static struct gsave_flat_array *gsave_load_array(struct gsave *save, struct page *page, struct gsave_flat_array *flat_array, bool src_is_gbk)
{
	assert(page->type == ARRAY_PAGE);
	if (nested_array(page)) {
		for (int i = 0; i < page->nr_vars; i++)
			flat_array = gsave_load_array(save, heap_get_page(page->values[i].i), flat_array, src_is_gbk);
		return flat_array;
	}

	if (page->nr_vars != flat_array->nr_values)
		VM_ERROR("Bad save file: unexpected number of array elements");
	for (int i = 0; i < page->nr_vars; i++) {
		int struct_type, array_rank;
		enum ain_data_type data_type = variable_type(page, i, &struct_type, &array_rank);
		if (data_type != flat_array->values[i].type &&
		    !(data_type == AIN_REF_STRUCT && flat_array->values[i].type == AIN_STRUCT))
			VM_ERROR("Bad save file: unexpected array element type");
		const struct ain_type *type = variable_full_type(page, i);
		union vm_value val = type ? gsave_to_vm_value_typed(save, type, flat_array->values[i].value, src_is_gbk)
			: gsave_to_vm_value(save, data_type, struct_type, array_rank, flat_array->values[i].value, src_is_gbk);
		variable_fini(page->values[i], data_type, false);
		page->values[i] = val;
	}
	return flat_array + 1;
}

static union vm_value gsave_to_vm_value(struct gsave *save, enum ain_data_type type, int struct_type, int array_rank, int32_t value, bool src_is_gbk)
{
	switch (type) {
	case AIN_VOID:
	case AIN_INT:
	case AIN_BOOL:
	case AIN_LONG_INT:
	case AIN_FLOAT:
	case AIN_ENUM:
	case AIN_ENUM2:
	case AIN_FUNC_TYPE:
		return vm_int(value);
	case AIN_DELEGATE:
		return variable_initval(AIN_DELEGATE);
	case AIN_STRING:
		{
			int slot = heap_alloc_slot(VM_STRING);
			if (value == GSAVE7_EMPTY_STRING || !save->strings[value]->size) {
				heap[slot].s = string_ref(&EMPTY_STRING);
			} else {
				heap[slot].s = save_decode_vm_string(save->strings[value]->text,
					save->strings[value]->size, src_is_gbk);
			}
			return vm_int(slot);
		}
	case AIN_STRUCT:
		{
			struct gsave_record *rec = &save->records[value];
			struct ain_struct *st = &ain->structures[struct_type];
			struct gsave_struct_def *sd =
				save->version >= 7 ? &save->struct_defs[rec->struct_index] : NULL;
			const char *struct_name = sd ? sd->name : rec->struct_name;
			if (!gsave_struct_matches(st, struct_name, rec, save, sd))
				VM_ERROR("Bad save file: structure name mismatch");
			int slot = alloc_struct(struct_type);
			struct page *page = heap[slot].page;
			for (int i = 0; i < rec->nr_indices; i++) {
				struct gsave_keyval *kv = &save->keyvals[rec->indices[i]];
				const char *field_name = sd ? sd->fields[i].name : kv->name;
				enum ain_data_type field_type = sd ? sd->fields[i].type : kv->type;
				if (!field_name || !field_name[0])
					continue;
				int index = struct_member_index(st, field_name);
				if (index < 0 && i < st->nr_members && rec->nr_indices == st->nr_members
						&& st->members[i].name && st->members[i].name[0]) {
					int cand_struct_type, cand_array_rank;
					if (variable_type(page, i, &cand_struct_type, &cand_array_rank) == field_type)
						index = i;
				}
				if (index < 0) {
					WARNING("structure %s has no member named %s", display_sjis0(struct_name), display_sjis1(field_name));
					continue;
				}
				int struct_type, array_rank;
				enum ain_data_type data_type = variable_type(page, index, &struct_type, &array_rank);
				if (data_type != field_type)
					VM_ERROR("Bad save file: structure member type mismatch");
				union vm_value val = gsave_to_vm_value_typed(save, &st->members[index].type, kv->value, src_is_gbk);
				variable_fini(page->values[index], data_type, false);
				page->values[index] = val;
			}
			return vm_int(slot);
		}
	case AIN_ARRAY_TYPE:
		{
			struct gsave_array *array = &save->arrays[value];
			int slot = heap_alloc_slot(VM_PAGE);
			if (array->rank == -1) {
				heap[slot].page = NULL;
				return vm_int(slot);
			}
			if (array_rank != array->rank)
				VM_ERROR("Bad save file: array rank mismatch");
			union vm_value *dims = xmalloc(sizeof(union vm_value) * array_rank);
			for (int i = 0; i < array_rank; i++)
				dims[i].i = array->dimensions[array_rank - 1 - i];
			struct page *page = alloc_array(array_rank, dims, type, struct_type, false);
			heap[slot].page = page;
			free(dims);
			gsave_load_array(save, page, array->flat_arrays, src_is_gbk);
			return vm_int(slot);
		}
	case AIN_REF_TYPE:
		return vm_int(-1);
	default:
		WARNING("Unhandled data type: %s", ain_strtype(ain, type, -1));
		return vm_int(-1);
	}
}

static struct page *v12_saved_array_new(const struct ain_type *type, int rank, int32_t *dimensions)
{
	if (type->data != AIN_ARRAY || rank < 1 || !type->array_type)
		VM_ERROR("Bad save file: nested array type mismatch");
	struct page *page = v12_array_new(type->array_type, dimensions[rank - 1], false);
	if (rank > 1) {
		for (int i = 0; i < page->nr_vars; i++) {
			int slot = page->values[i].i;
			struct page *child = v12_saved_array_new(type->array_type, rank - 1, dimensions);
			delete_page(slot);
			heap_set_page(slot, child);
		}
	}
	return page;
}

static union vm_value gsave_to_vm_value_typed(struct gsave *save, const struct ain_type *type, int32_t value, bool src_is_gbk)
{
	if (type->data != AIN_ARRAY)
		return gsave_to_vm_value(save, type->data, type->struc, type->rank, value, src_is_gbk);
	if (value < 0 || value >= save->nr_arrays)
		VM_ERROR("Bad save file: invalid generic array index %d", value);
	struct gsave_array *array = &save->arrays[value];
	if (array->rank < 1) return variable_initval_typed(type);
	struct page *page = v12_saved_array_new(type, array->rank, array->dimensions);
	gsave_load_array(save, page, array->flat_arrays, src_is_gbk);
	return vm_int(heap_alloc_page(page));
}

static int *resolve_gsave_globals(struct gsave *save, const char *group_name)
{
	if (!save || save->nr_globals <= 0)
		return NULL;
	int *resolved = xmalloc(sizeof(int) * (size_t)save->nr_globals);
	for (int i = 0; i < save->nr_globals; i++)
		resolved[i] = -1;
	bool *claimed = xcalloc((size_t)(ain->nr_globals > 0 ? ain->nr_globals : 1), sizeof(bool));

	// Grouped saves pack only globals matching group_name in .ain order; ungrouped
	// saves either omit suppressed (blank name2) entries or dump all ain->globals.
	int target_group = (group_name && group_name[0]) ? get_group_index(group_name) : -1;
	int *expected = NULL;
	int nr_expected = 0;
	if (ain->nr_globals > 0 && (!group_name || !group_name[0] || target_group >= 0)) {
		expected = xmalloc(sizeof(int) * (size_t)ain->nr_globals);
		for (int i = 0; i < ain->nr_globals; i++) {
			if (ain->globals[i].name2 && !ain->globals[i].name2[0])
				continue;
			if (target_group >= 0 && ain->globals[i].group_index != target_group)
				continue;
			expected[nr_expected++] = i;
		}
		if (target_group < 0 && save->nr_globals == ain->nr_globals && nr_expected != ain->nr_globals) {
			for (int i = 0; i < ain->nr_globals; i++)
				expected[i] = i;
			nr_expected = ain->nr_globals;
		}
	}

	int aligned = 0;
	int shifted = 0;

	// Pass 1: exact name & name2 lookup
	for (int gi = 0; gi < save->nr_globals; gi++) {
		struct gsave_global *g = &save->globals[gi];
		if (g->type == AIN_VOID && !g->name[0])
			continue;
		int idx = ain_get_global(ain, g->name);
		if (idx < 0) {
			for (int i = 0; i < ain->nr_globals; i++) {
				if (ain->globals[i].name2 && ain->globals[i].name2[0] && !strcmp(ain->globals[i].name2, g->name)) {
					idx = i;
					break;
				}
			}
		}
		if (idx >= 0 && idx < ain->nr_globals) {
			resolved[gi] = idx;
			claimed[idx] = true;
			int exp_idx = (expected && gi < nr_expected) ? expected[gi] : -1;
			if (idx == exp_idx)
				aligned++;
			else
				shifted++;
		}
	}

	// Pass 2: cross-encoding UTF-8 + Kanji-normalized lookup
	for (int gi = 0; gi < save->nr_globals; gi++) {
		struct gsave_global *g = &save->globals[gi];
		if (resolved[gi] >= 0 || (g->type == AIN_VOID && !g->name[0]))
			continue;
		int idx = -1;
		int exp_idx = (expected && gi < nr_expected) ? expected[gi] : -1;
		if (exp_idx >= 0 && !claimed[exp_idx] && ain->globals[exp_idx].type.data == g->type) {
			if ((ain->globals[exp_idx].name2 && ain->globals[exp_idx].name2[0] && save_symbols_equal(ain->globals[exp_idx].name2, g->name))
					|| (ain->globals[exp_idx].name && save_symbols_equal(ain->globals[exp_idx].name, g->name)))
				idx = exp_idx;
		}
		if (idx < 0) {
			for (int pass_type = 0; pass_type < 2 && idx < 0; pass_type++) {
				for (int i = 0; i < ain->nr_globals; i++) {
					if (claimed[i])
						continue;
					if (pass_type == 0 && ain->globals[i].type.data != g->type)
						continue;
					if ((ain->globals[i].name2 && ain->globals[i].name2[0] && save_symbols_equal(ain->globals[i].name2, g->name))
							|| (ain->globals[i].name && save_symbols_equal(ain->globals[i].name, g->name))) {
						idx = i;
						break;
					}
				}
			}
		}
		if (idx >= 0) {
			resolved[gi] = idx;
			claimed[idx] = true;
			if (idx == exp_idx)
				aligned++;
			else
				shifted++;
		}
	}

	// Pass 3: aligned structural fallback when localized multi-char terms differ
	if (expected && save->nr_globals <= nr_expected && shifted == 0
			&& (aligned > 0 || (save->nr_globals == 1 && nr_expected == 1))) {
		for (int gi = 0; gi < save->nr_globals; gi++) {
			struct gsave_global *g = &save->globals[gi];
			if (resolved[gi] >= 0 || (g->type == AIN_VOID && !g->name[0]))
				continue;
			int exp_idx = gi < nr_expected ? expected[gi] : -1;
			if (exp_idx >= 0 && !claimed[exp_idx] && ain->globals[exp_idx].name && ain->globals[exp_idx].name[0]
					&& ain->globals[exp_idx].type.data == g->type
					&& (target_group < 0 || ain->globals[exp_idx].group_index == target_group)) {
				resolved[gi] = exp_idx;
				claimed[exp_idx] = true;
			}
		}
	}

	free(expected);
	free(claimed);
	return resolved;
}

static bool gsave_detect_is_gbk(const struct gsave *save, const int *resolved,
		const char *keyname, const char *group_name)
{
	int native_votes = 0;
	int opp_votes = 0;
	if (keyname && save->key && !cjk_is_ascii_only(save->key)) {
		if (!strcmp(keyname, save->key))
			native_votes += 20;
		else if (save_names_equal(keyname, save->key))
			opp_votes += 20;
	}
	if (group_name && group_name[0] && save->group && !cjk_is_ascii_only(save->group)) {
		if (!strcmp(group_name, save->group))
			native_votes += 20;
		else if (save_names_equal(group_name, save->group))
			opp_votes += 20;
	}
	for (int gi = 0; gi < save->nr_globals && resolved; gi++) {
		int idx = resolved[gi];
		const char *gname = save->globals[gi].name;
		if (idx < 0 || idx >= ain->nr_globals || !gname || cjk_is_ascii_only(gname))
			continue;
		const char *aname = (ain->globals[idx].name2 && ain->globals[idx].name2[0])
			? ain->globals[idx].name2 : ain->globals[idx].name;
		if (!strcmp(aname, gname))
			native_votes += 10;
		else
			opp_votes += 10;
	}
	if (opp_votes > native_votes)
		return !sys4_text_is_gbk;
	if (native_votes > opp_votes)
		return sys4_text_is_gbk;
	int bias = 0;
	for (int i = 0; i < save->nr_strings && i < 64; i++) {
		if (save->strings[i] && save->strings[i]->size > 0)
			bias += save_text_gbk_bias(save->strings[i]->text);
	}
	if (bias > 15)
		return true;
	if (bias < -15)
		return false;
	return sys4_text_is_gbk;
}

int load_globals_from_gsave(struct gsave *save, const char *keyname, const char *group_name, int *n)
{
	if (!save_names_equal(keyname, save->key))
		VM_ERROR("Attempted to load save data with wrong key: %s", display_sjis0(keyname));
	if (!group_name)
		group_name = "";
	if (!save->group)
		save->group = strdup("");
	if (!save_names_equal(group_name, save->group))
		VM_ERROR("Attempted to load save data with wrong group name: '%s'", display_sjis0(group_name));

	int *resolved = resolve_gsave_globals(save, group_name);
	bool src_is_gbk = gsave_detect_is_gbk(save, resolved, keyname, group_name);
	int retval = 1;
	for (int gi = 0; gi < save->nr_globals; gi++) {
		struct gsave_global *g = &save->globals[gi];
		if (g->type == AIN_VOID && !g->name[0]) continue;
		int global_index = resolved ? resolved[gi] : -1;
		if (global_index < 0) {
			WARNING("invalid global name %s", display_sjis0(g->name));
			retval = 0;
			goto cleanup;
		}
		struct ain_type *type = &ain->globals[global_index].type;
		if (type->data != g->type) {
			WARNING("%s: type mismatch", display_sjis0(g->name));
			retval = 0;
			goto cleanup;
		}
		if (should_preserve_static_global(global_index)) {
			if (n)
				(*n)++;
			continue;
		}
		bool call_dtors = false; // Destructors for old objects should not be called.
		global_set(global_index, gsave_to_vm_value_typed(save, type, g->value, src_is_gbk), call_dtors);
		if (n)
			(*n)++;
	}

cleanup:
	free(resolved);
	return retval;
}

int load_globals(const char *keyname, const char *filename, const char *group_name, int *n)
{
	if (n)
		*n = 0;
	char *path = savedir_path(filename);
	int retval;

	bool is_cfg = is_game_config_save(filename);

	// First, try reading as a gsave.
	enum savefile_error error;
	struct gsave *save = gsave_read(path, &error);
	if (error == SAVEFILE_SUCCESS) {
		free(path);
		retval = load_globals_from_gsave(save, keyname, group_name, n);
		gsave_free(save);
		if (retval) {
			track_global_save(keyname, filename, group_name);
			if (is_cfg)
				modifier_on_config_loaded();
			modifier_on_globals_loaded();
		} else if (is_cfg) {
			modifier_on_config_loaded();
		}
		return retval;
	}
	if (error == SAVEFILE_INVALID_SIGNATURE) {
		// If not a System4 save file, try reading as a json.
		retval = load_globals_from_json(path, keyname, group_name, n);
		if (retval) {
			free(path);
			track_global_save(keyname, filename, group_name);
			if (is_cfg)
				modifier_on_config_loaded();
			modifier_on_globals_loaded();
			return retval;
		}
	} else {
		WARNING("%s: %s", display_utf0(path), savefile_strerror(error));
	}

	// Fall back to .bak if a backup exists (e.g. GlobalSave.asd.bak / Collection.asd.bak).
	char *bak_path = make_backup_path(path);
	bool had_any_file = file_exists(path) || file_exists(bak_path);
	if (file_exists(bak_path)) {
		enum savefile_error bak_err;
		struct gsave *bak_save = gsave_read(bak_path, &bak_err);
		if (bak_err == SAVEFILE_SUCCESS) {
			WARNING("Recovered corrupted save %s from backup %s", display_utf0(path), display_utf0(bak_path));
			file_copy(bak_path, path);
			free(bak_path);
			free(path);
			retval = load_globals_from_gsave(bak_save, keyname, group_name, n);
			gsave_free(bak_save);
			if (retval) {
				track_global_save(keyname, filename, group_name);
				if (is_cfg)
					modifier_on_config_loaded();
				modifier_on_globals_loaded();
			} else if (is_cfg) {
				modifier_on_config_loaded();
			}
			return retval;
		}
	}
	free(bak_path);
	free(path);
	if (!had_any_file)
		track_global_save(keyname, filename, group_name);
	if (is_cfg)
		modifier_on_config_loaded();
	return 0;
}

int delete_save_file(const char *filename)
{
	untrack_global_save(filename);
	char *path = savedir_path(filename);
	char *bak_path = make_backup_path(path);
	if (file_exists(bak_path))
		remove_utf8(bak_path);
	free(bak_path);
	if (!file_exists(path)) {
		free(path);
		return 0;
	}
	if (remove_utf8(path)) {
		WARNING("remove(\"%s\"): %s", display_utf0(path), strerror(errno));
		free(path);
		return 0;
	}
	free(path);
	return 1;
}

void savedata_flush_on_background(void)
{
	if (flushing_background_saves || !ain || !heap || heap_size <= 0)
		return;
	flushing_background_saves = true;
	for (int i = 0; i < nr_tracked_global_saves; i++) {
		const struct tracked_global_save *entry = &tracked_global_saves[i];
		if (!entry->keyname || !entry->filename || !entry->group_name)
			continue;
		if (get_group_index(entry->group_name) < 0)
			continue;
		save_globals(entry->keyname, entry->filename, entry->group_name, NULL);
	}
	flushing_background_saves = false;
}

/* =========================================================================
 * AIN v14 SerializeStruct / DeserializeStruct & Comment Container (X4SS v1)
 * ========================================================================= */

static const uint8_t X4SS_MAGIC[8] = { 'X', '4', 'S', 'S', 1, 0, 0, 0 };

enum x4ss_obj_kind {
	X4SS_OBJ_STRUCT = 1,
	X4SS_OBJ_ARRAY  = 2,
	X4SS_OBJ_NULL   = 3,
};

enum x4ss_val_tag {
	X4SS_VAL_NONE     = 0,
	X4SS_VAL_SCALAR   = 1,
	X4SS_VAL_STRING   = 2,
	X4SS_VAL_OBJ      = 3,
	X4SS_VAL_IFACE    = 4,
	X4SS_VAL_OPTION   = 5,
	X4SS_VAL_PAGE_VAR = 6,
};

struct x4ss_buf {
	uint8_t *data;
	size_t len;
	size_t cap;
};

static void x4ss_buf_ensure(struct x4ss_buf *b, size_t extra)
{
	if (b->len + extra <= b->cap)
		return;
	size_t new_cap = b->cap ? b->cap * 2 : 4096;
	while (new_cap < b->len + extra)
		new_cap *= 2;
	b->data = xrealloc(b->data, new_cap);
	b->cap = new_cap;
}

static void x4ss_put_bytes(struct x4ss_buf *b, const void *src, size_t n)
{
	if (!n)
		return;
	x4ss_buf_ensure(b, n);
	memcpy(b->data + b->len, src, n);
	b->len += n;
}

static void x4ss_put_u8(struct x4ss_buf *b, uint8_t v)
{
	x4ss_put_bytes(b, &v, 1);
}

static void x4ss_put_u32(struct x4ss_buf *b, uint32_t v)
{
	uint8_t le[4] = {
		(uint8_t)(v & 0xff),
		(uint8_t)((v >> 8) & 0xff),
		(uint8_t)((v >> 16) & 0xff),
		(uint8_t)((v >> 24) & 0xff),
	};
	x4ss_put_bytes(b, le, 4);
}

static void x4ss_put_i32(struct x4ss_buf *b, int32_t v)
{
	x4ss_put_u32(b, (uint32_t)v);
}

static void x4ss_put_cstr(struct x4ss_buf *b, const char *s)
{
	uint32_t n = s ? (uint32_t)strlen(s) : 0;
	x4ss_put_u32(b, n);
	if (n)
		x4ss_put_bytes(b, s, n);
}

struct x4ss_reader {
	const uint8_t *data;
	size_t len;
	size_t pos;
	bool error;
};

static bool x4ss_get_bytes(struct x4ss_reader *r, void *dst, size_t n)
{
	if (r->error || r->pos + n > r->len) {
		r->error = true;
		return false;
	}
	if (n && dst)
		memcpy(dst, r->data + r->pos, n);
	r->pos += n;
	return true;
}

static uint8_t x4ss_get_u8(struct x4ss_reader *r)
{
	uint8_t v = 0;
	x4ss_get_bytes(r, &v, 1);
	return v;
}

static uint32_t x4ss_get_u32(struct x4ss_reader *r)
{
	uint8_t le[4] = {0};
	if (!x4ss_get_bytes(r, le, 4))
		return 0;
	return (uint32_t)le[0] | ((uint32_t)le[1] << 8)
		| ((uint32_t)le[2] << 16) | ((uint32_t)le[3] << 24);
}

static int32_t x4ss_get_i32(struct x4ss_reader *r)
{
	return (int32_t)x4ss_get_u32(r);
}

static char *x4ss_get_cstr(struct x4ss_reader *r, uint32_t *out_len)
{
	uint32_t n = x4ss_get_u32(r);
	if (r->error || r->pos + n > r->len) {
		r->error = true;
		if (out_len)
			*out_len = 0;
		return NULL;
	}
	char *s = xmalloc(n + 1);
	if (n)
		memcpy(s, r->data + r->pos, n);
	s[n] = '\0';
	r->pos += n;
	if (out_len)
		*out_len = n;
	return s;
}

/* Interned ain_type pool for nested array element types reconstructed on load */
struct x4ss_intern_type {
	struct ain_type type;
	struct x4ss_intern_type *next;
};
static struct x4ss_intern_type *x4ss_intern_pool = NULL;

static struct ain_type *x4ss_intern_ain_type(const struct ain_type *src)
{
	for (struct x4ss_intern_type *it = x4ss_intern_pool; it; it = it->next) {
		if (it->type.data == src->data &&
		    it->type.struc == src->struc &&
		    it->type.rank == src->rank &&
		    it->type.array_type == src->array_type) {
			return &it->type;
		}
	}
	struct x4ss_intern_type *node = xcalloc(1, sizeof(*node));
	node->type = *src;
	node->next = x4ss_intern_pool;
	x4ss_intern_pool = node;
	return &node->type;
}

static const char *x4ss_type_symbol_name(enum ain_data_type data, int id)
{
	if (id < 0)
		return "";
	switch (data) {
	case AIN_STRUCT:
	case AIN_REF_STRUCT:
	case AIN_IFACE:
	case AIN_IFACE_WRAP:
		return id < ain->nr_structures ? ain->structures[id].name : "";
	case AIN_ENUM:
	case AIN_ENUM2:
	case AIN_REF_ENUM:
		return id < ain->nr_enums ? ain->enums[id].name : "";
	case AIN_DELEGATE:
	case AIN_REF_DELEGATE:
		return id < ain->nr_delegates ? ain->delegates[id].name : "";
	case AIN_FUNC_TYPE:
	case AIN_REF_FUNC_TYPE:
		return id < ain->nr_function_types ? ain->function_types[id].name : "";
	default:
		return "";
	}
}

static int x4ss_resolve_type_symbol(enum ain_data_type data, const char *name, int fallback_id)
{
	if (!name || !name[0])
		return fallback_id;
	int id = -1;
	switch (data) {
	case AIN_STRUCT:
	case AIN_REF_STRUCT:
	case AIN_IFACE:
	case AIN_IFACE_WRAP:
		id = save_find_struct(name);
		return id >= 0 ? id : fallback_id;
	case AIN_ENUM:
	case AIN_ENUM2:
	case AIN_REF_ENUM:
		id = save_find_enum(name);
		return id >= 0 ? id : fallback_id;
	case AIN_DELEGATE:
	case AIN_REF_DELEGATE:
		id = save_find_delegate(name);
		return id >= 0 ? id : fallback_id;
	default:
		return fallback_id;
	}
}

static void x4ss_write_ain_type(struct x4ss_buf *b, const struct ain_type *t)
{
	if (!t) {
		x4ss_put_i32(b, AIN_VOID);
		x4ss_put_i32(b, -1);
		x4ss_put_i32(b, 0);
		x4ss_put_cstr(b, "");
		x4ss_put_u8(b, 0);
		return;
	}
	x4ss_put_i32(b, (int32_t)t->data);
	x4ss_put_i32(b, (int32_t)t->struc);
	x4ss_put_i32(b, (int32_t)t->rank);
	x4ss_put_cstr(b, x4ss_type_symbol_name(t->data, t->struc));
	if (t->array_type) {
		x4ss_put_u8(b, 1);
		x4ss_write_ain_type(b, t->array_type);
	} else {
		x4ss_put_u8(b, 0);
	}
}

static struct ain_type x4ss_read_ain_type(struct x4ss_reader *r)
{
	struct ain_type t = {0};
	t.data = (enum ain_data_type)x4ss_get_i32(r);
	int fallback_id = x4ss_get_i32(r);
	t.rank = x4ss_get_i32(r);
	char *sym = x4ss_get_cstr(r, NULL);
	t.struc = x4ss_resolve_type_symbol(t.data, sym, fallback_id);
	free(sym);
	uint8_t has_inner = x4ss_get_u8(r);
	if (has_inner && !r->error) {
		struct ain_type inner = x4ss_read_ain_type(r);
		t.array_type = x4ss_intern_ain_type(&inner);
	} else {
		t.array_type = NULL;
	}
	return t;
}

struct x4ss_save_ctx {
	int *slot_to_id;
	int *id_to_slot;
	int nr_objs;
	int cap_objs;
};

static uint32_t x4ss_register_page(struct x4ss_save_ctx *ctx, int slot)
{
	if (slot < 0 || !page_index_valid(slot))
		return 0;
	struct page *p = heap[slot].page;
	if (p && p->type != STRUCT_PAGE && p->type != ARRAY_PAGE)
		return 0;
	if (ctx->slot_to_id[slot] > 0)
		return (uint32_t)ctx->slot_to_id[slot];
	uint32_t id = (uint32_t)(++ctx->nr_objs);
	if (ctx->nr_objs >= ctx->cap_objs) {
		ctx->cap_objs = ctx->cap_objs ? ctx->cap_objs * 2 : 256;
		ctx->id_to_slot = xrealloc(ctx->id_to_slot, (size_t)ctx->cap_objs * sizeof(int));
	}
	ctx->slot_to_id[slot] = (int)id;
	ctx->id_to_slot[id] = slot;
	return id;
}

static void x4ss_collect_value(struct x4ss_save_ctx *ctx, const union vm_value *vals, const struct ain_type *t)
{
	if (!t)
		return;
	switch (t->data) {
	case AIN_STRUCT:
	case AIN_REF_STRUCT:
	case AIN_ARRAY:
	case AIN_ARRAY_TYPE:
	case AIN_REF_ARRAY:
	case AIN_REF_ARRAY_TYPE:
		x4ss_register_page(ctx, vals[0].i);
		break;
	case AIN_IFACE:
	case AIN_IFACE_WRAP:
		x4ss_register_page(ctx, vals[0].i);
		break;
	case AIN_WRAP:
		if (!t->array_type)
			break;
		if (t->array_type->data == AIN_STRING || t->array_type->data == AIN_DELEGATE)
			break;
		x4ss_register_page(ctx, vals[0].i);
		break;
	case AIN_REF_INT:
	case AIN_REF_FLOAT:
	case AIN_REF_BOOL:
	case AIN_REF_LONG_INT:
	case AIN_REF_ENUM:
	case AIN_REF_OPTION:
		x4ss_register_page(ctx, vals[0].i);
		break;
	case AIN_OPTION: {
		int w = ain_type_slot_width(t);
		if (vals[w - 1].i == 0 && t->array_type)
			x4ss_collect_value(ctx, vals, t->array_type);
		break;
	}
	default:
		break;
	}
}

static void x4ss_write_value(struct x4ss_save_ctx *ctx, struct x4ss_buf *b, const union vm_value *vals, const struct ain_type *t)
{
	if (!t) {
		x4ss_put_u8(b, X4SS_VAL_NONE);
		return;
	}
	switch (t->data) {
	case AIN_VOID:
	case AIN_DELEGATE:
	case AIN_REF_DELEGATE:
		x4ss_put_u8(b, X4SS_VAL_NONE);
		break;
	case AIN_STRING: {
		x4ss_put_u8(b, X4SS_VAL_STRING);
		if (string_index_valid(vals[0].i)) {
			struct string *s = heap_get_string(vals[0].i);
			x4ss_put_u32(b, (uint32_t)s->size);
			x4ss_put_bytes(b, s->text, (size_t)s->size);
		} else {
			x4ss_put_u32(b, 0);
		}
		break;
	}
	case AIN_STRUCT:
	case AIN_REF_STRUCT:
	case AIN_ARRAY:
	case AIN_ARRAY_TYPE:
	case AIN_REF_ARRAY:
	case AIN_REF_ARRAY_TYPE: {
		x4ss_put_u8(b, X4SS_VAL_OBJ);
		uint32_t id = (vals[0].i >= 0 && page_index_valid(vals[0].i) && ctx->slot_to_id[vals[0].i] > 0)
			? (uint32_t)ctx->slot_to_id[vals[0].i] : 0;
		x4ss_put_u32(b, id);
		break;
	}
	case AIN_IFACE:
	case AIN_IFACE_WRAP: {
		x4ss_put_u8(b, X4SS_VAL_IFACE);
		uint32_t id = (vals[0].i >= 0 && page_index_valid(vals[0].i) && ctx->slot_to_id[vals[0].i] > 0)
			? (uint32_t)ctx->slot_to_id[vals[0].i] : 0;
		x4ss_put_u32(b, id);
		x4ss_put_i32(b, id ? vals[1].i : 0);
		break;
	}
	case AIN_WRAP: {
		if (!t->array_type || t->array_type->data == AIN_DELEGATE) {
			x4ss_put_u8(b, X4SS_VAL_NONE);
			break;
		}
		if (t->array_type->data == AIN_STRING) {
			x4ss_put_u8(b, X4SS_VAL_STRING);
			if (string_index_valid(vals[0].i)) {
				struct string *s = heap_get_string(vals[0].i);
				x4ss_put_u32(b, (uint32_t)s->size);
				x4ss_put_bytes(b, s->text, (size_t)s->size);
			} else {
				x4ss_put_u32(b, 0);
			}
			break;
		}
		if (t->array_type->data == AIN_IFACE || t->array_type->data == AIN_IFACE_WRAP) {
			x4ss_put_u8(b, X4SS_VAL_IFACE);
			uint32_t id = (vals[0].i >= 0 && page_index_valid(vals[0].i) && ctx->slot_to_id[vals[0].i] > 0)
				? (uint32_t)ctx->slot_to_id[vals[0].i] : 0;
			x4ss_put_u32(b, id);
			x4ss_put_i32(b, id ? vals[1].i : 0);
			break;
		}
		if (ain_type_slot_width(t) == 2) {
			x4ss_put_u8(b, X4SS_VAL_PAGE_VAR);
			uint32_t id = (vals[0].i >= 0 && page_index_valid(vals[0].i) && ctx->slot_to_id[vals[0].i] > 0)
				? (uint32_t)ctx->slot_to_id[vals[0].i] : 0;
			x4ss_put_u32(b, id);
			x4ss_put_i32(b, id ? vals[1].i : 0);
			break;
		}
		x4ss_put_u8(b, X4SS_VAL_OBJ);
		uint32_t id = (vals[0].i >= 0 && page_index_valid(vals[0].i) && ctx->slot_to_id[vals[0].i] > 0)
			? (uint32_t)ctx->slot_to_id[vals[0].i] : 0;
		x4ss_put_u32(b, id);
		break;
	}
	case AIN_REF_INT:
	case AIN_REF_FLOAT:
	case AIN_REF_BOOL:
	case AIN_REF_LONG_INT:
	case AIN_REF_ENUM:
	case AIN_REF_OPTION: {
		x4ss_put_u8(b, X4SS_VAL_PAGE_VAR);
		uint32_t id = (vals[0].i >= 0 && page_index_valid(vals[0].i) && ctx->slot_to_id[vals[0].i] > 0)
			? (uint32_t)ctx->slot_to_id[vals[0].i] : 0;
		x4ss_put_u32(b, id);
		x4ss_put_i32(b, id ? vals[1].i : 0);
		break;
	}
	case AIN_OPTION: {
		int w = ain_type_slot_width(t);
		bool is_none = (vals[w - 1].i != 0);
		x4ss_put_u8(b, X4SS_VAL_OPTION);
		x4ss_put_u8(b, is_none ? 1 : 0);
		if (!is_none)
			x4ss_write_value(ctx, b, vals, t->array_type);
		break;
	}
	default:
		x4ss_put_u8(b, X4SS_VAL_SCALAR);
		x4ss_put_i32(b, vals[0].i);
		break;
	}
}

static void x4ss_skip_value(struct x4ss_reader *r)
{
	uint8_t tag = x4ss_get_u8(r);
	if (r->error)
		return;
	switch (tag) {
	case X4SS_VAL_NONE:
		break;
	case X4SS_VAL_SCALAR:
		x4ss_get_i32(r);
		break;
	case X4SS_VAL_STRING: {
		uint32_t len = x4ss_get_u32(r);
		x4ss_get_bytes(r, NULL, len);
		break;
	}
	case X4SS_VAL_OBJ:
		x4ss_get_u32(r);
		break;
	case X4SS_VAL_IFACE:
	case X4SS_VAL_PAGE_VAR:
		x4ss_get_u32(r);
		x4ss_get_i32(r);
		break;
	case X4SS_VAL_OPTION: {
		uint8_t is_none = x4ss_get_u8(r);
		if (!is_none && !r->error)
			x4ss_skip_value(r);
		break;
	}
	default:
		r->error = true;
		break;
	}
}

static int x4ss_resolve_vtable_offset(int concrete_sno, int iface_sno, int saved_voff)
{
	if (concrete_sno >= 0 && concrete_sno < ain->nr_structures && iface_sno >= 0) {
		struct ain_struct *st = &ain->structures[concrete_sno];
		for (int i = 0; i < st->nr_interfaces; i++) {
			if (st->interfaces[i].struct_type == iface_sno)
				return st->interfaces[i].vtable_offset;
		}
	}
	return saved_voff;
}

struct x4ss_load_ctx {
	uint32_t nr_objs;
	int *id_to_slot;
	bool *is_root;
};

static void x4ss_read_value(struct x4ss_load_ctx *ctx, struct x4ss_reader *r,
		union vm_value *dst, const struct ain_type *t, bool has_existing)
{
	uint8_t tag = x4ss_get_u8(r);
	if (r->error)
		return;
	if (!t || t->data == AIN_VOID || t->data == AIN_DELEGATE || t->data == AIN_REF_DELEGATE) {
		/* Re-wind tag? No, consume remainder of tag payload cleanly */
		switch (tag) {
		case X4SS_VAL_NONE: break;
		case X4SS_VAL_SCALAR: x4ss_get_i32(r); break;
		case X4SS_VAL_STRING: { uint32_t l = x4ss_get_u32(r); x4ss_get_bytes(r, NULL, l); break; }
		case X4SS_VAL_OBJ: x4ss_get_u32(r); break;
		case X4SS_VAL_IFACE: case X4SS_VAL_PAGE_VAR: x4ss_get_u32(r); x4ss_get_i32(r); break;
		case X4SS_VAL_OPTION: if (!x4ss_get_u8(r)) x4ss_skip_value(r); break;
		default: r->error = true; break;
		}
		return;
	}

	if (has_existing) {
		elem_value_fini(dst, t, false);
		int w = ain_type_slot_width(t);
		for (int k = 0; k < w; k++)
			dst[k].i = 0;
	}

	if (t->data == AIN_OPTION) {
		int w = ain_type_slot_width(t);
		if (tag != X4SS_VAL_OPTION) {
			/* Fallback: default to none */
			for (int k = 0; k < w - 1; k++)
				dst[k].i = -1;
			dst[w - 1].i = 1;
			return;
		}
		uint8_t is_none = x4ss_get_u8(r);
		if (is_none) {
			for (int k = 0; k < w - 1; k++)
				dst[k].i = -1;
			dst[w - 1].i = 1;
		} else {
			x4ss_read_value(ctx, r, dst, t->array_type, false);
			dst[w - 1].i = 0;
		}
		return;
	}

	switch (tag) {
	case X4SS_VAL_NONE:
		variable_init_typed(dst, t, false, false);
		break;
	case X4SS_VAL_SCALAR:
		dst[0].i = x4ss_get_i32(r);
		break;
	case X4SS_VAL_STRING: {
		uint32_t len = x4ss_get_u32(r);
		if (r->error || r->pos + len > r->len) {
			r->error = true;
			dst[0].i = heap_alloc_string(string_ref(&EMPTY_STRING));
			break;
		}
		struct string *s = make_string((const char *)(r->data + r->pos), len);
		r->pos += len;
		dst[0].i = heap_alloc_string(s);
		break;
	}
	case X4SS_VAL_OBJ: {
		uint32_t id = x4ss_get_u32(r);
		int slot = (id > 0 && id <= ctx->nr_objs) ? ctx->id_to_slot[id] : -1;
		if (slot >= 0)
			heap_ref(slot);
		dst[0].i = slot;
		break;
	}
	case X4SS_VAL_IFACE: {
		uint32_t id = x4ss_get_u32(r);
		int32_t saved_voff = x4ss_get_i32(r);
		int slot = (id > 0 && id <= ctx->nr_objs) ? ctx->id_to_slot[id] : -1;
		if (slot >= 0 && page_index_valid(slot) && heap[slot].page) {
			int concrete_sno = heap[slot].page->index;
			int iface_sno = (t->data == AIN_WRAP && t->array_type) ? t->array_type->struc : t->struc;
			heap_ref(slot);
			dst[0].i = slot;
			dst[1].i = x4ss_resolve_vtable_offset(concrete_sno, iface_sno, saved_voff);
		} else {
			dst[0].i = -1;
			dst[1].i = 0;
		}
		break;
	}
	case X4SS_VAL_PAGE_VAR: {
		uint32_t id = x4ss_get_u32(r);
		int32_t varno = x4ss_get_i32(r);
		int slot = (id > 0 && id <= ctx->nr_objs) ? ctx->id_to_slot[id] : -1;
		if (slot >= 0) {
			heap_ref(slot);
			dst[0].i = slot;
			dst[1].i = varno;
		} else {
			dst[0].i = -1;
			dst[1].i = 0;
		}
		break;
	}
	default:
		r->error = true;
		break;
	}
}

static char *resolve_struct_save_path(const char *filename, bool save_folder)
{
	return save_folder ? savedir_path(filename) : gamedir_path(filename);
}

static bool x4ss_read_existing_parts(const char *path,
		uint8_t **comment_out, uint32_t *comment_len_out,
		uint32_t *uncomp_len_out, uint8_t **comp_out, uint32_t *comp_len_out)
{
	if (comment_out) *comment_out = NULL;
	if (comment_len_out) *comment_len_out = 0;
	if (uncomp_len_out) *uncomp_len_out = 0;
	if (comp_out) *comp_out = NULL;
	if (comp_len_out) *comp_len_out = 0;

	FILE *fp = file_open_utf8(path, "rb");
	if (!fp)
		return false;

	uint8_t hdr[12];
	if (fread(hdr, 1, 12, fp) != 12 || memcmp(hdr, X4SS_MAGIC, 8) != 0) {
		fclose(fp);
		return false;
	}
	uint32_t clen = (uint32_t)hdr[8] | ((uint32_t)hdr[9] << 8)
		| ((uint32_t)hdr[10] << 16) | ((uint32_t)hdr[11] << 24);
	if (clen > 16 * 1024 * 1024) {
		fclose(fp);
		return false;
	}
	if (comment_out) {
		uint8_t *cbuf = xmalloc(clen + 1);
		if (clen && fread(cbuf, 1, clen, fp) != clen) {
			free(cbuf);
			fclose(fp);
			return false;
		}
		cbuf[clen] = 0;
		*comment_out = cbuf;
		if (comment_len_out)
			*comment_len_out = clen;
	} else {
		if (clen && fseek(fp, (long)clen, SEEK_CUR) != 0) {
			fclose(fp);
			return false;
		}
		if (comment_len_out)
			*comment_len_out = clen;
	}

	if (!comp_out && !uncomp_len_out && !comp_len_out) {
		fclose(fp);
		return true;
	}

	uint8_t phdr[8];
	if (fread(phdr, 1, 8, fp) != 8) {
		fclose(fp);
		return true;
	}
	uint32_t ulen = (uint32_t)phdr[0] | ((uint32_t)phdr[1] << 8)
		| ((uint32_t)phdr[2] << 16) | ((uint32_t)phdr[3] << 24);
	uint32_t zlen = (uint32_t)phdr[4] | ((uint32_t)phdr[5] << 8)
		| ((uint32_t)phdr[6] << 16) | ((uint32_t)phdr[7] << 24);
	if (uncomp_len_out)
		*uncomp_len_out = ulen;
	if (comp_len_out)
		*comp_len_out = zlen;
	if (comp_out && zlen > 0) {
		uint8_t *zbuf = xmalloc(zlen);
		if (fread(zbuf, 1, zlen, fp) != zlen) {
			free(zbuf);
			fclose(fp);
			return false;
		}
		*comp_out = zbuf;
	}
	fclose(fp);
	return true;
}

static bool x4ss_write_container(const char *path,
		const uint8_t *comment, uint32_t comment_len,
		uint32_t uncomp_len, const uint8_t *comp, uint32_t comp_len)
{
	char *tmp_path = NULL;
	FILE *fp = atomic_save_open(path, "wb", &tmp_path);
	if (!fp)
		return false;
	uint8_t hdr[12];
	memcpy(hdr, X4SS_MAGIC, 8);
	hdr[8]  = (uint8_t)(comment_len & 0xff);
	hdr[9]  = (uint8_t)((comment_len >> 8) & 0xff);
	hdr[10] = (uint8_t)((comment_len >> 16) & 0xff);
	hdr[11] = (uint8_t)((comment_len >> 24) & 0xff);
	bool ok = fwrite(hdr, 1, 12, fp) == 12;
	if (ok && comment_len > 0)
		ok = fwrite(comment, 1, comment_len, fp) == comment_len;
	uint8_t phdr[8] = {
		(uint8_t)(uncomp_len & 0xff), (uint8_t)((uncomp_len >> 8) & 0xff),
		(uint8_t)((uncomp_len >> 16) & 0xff), (uint8_t)((uncomp_len >> 24) & 0xff),
		(uint8_t)(comp_len & 0xff), (uint8_t)((comp_len >> 8) & 0xff),
		(uint8_t)((comp_len >> 16) & 0xff), (uint8_t)((comp_len >> 24) & 0xff),
	};
	if (ok)
		ok = fwrite(phdr, 1, 8, fp) == 8;
	if (ok && comp_len > 0)
		ok = fwrite(comp, 1, comp_len, fp) == comp_len;
	if (!ok) {
		atomic_save_abort(fp, tmp_path);
		return false;
	}
	return atomic_save_commit(fp, path, tmp_path, is_critical_global_save(path));
}

int serialize_structs(const char *filename, struct page *struct_page_list, bool save_folder)
{
	if (!filename || !struct_page_list || struct_page_list->type != ARRAY_PAGE)
		return 0;

	int nr_roots = v12_array_length(struct_page_list);
	for (int i = 0; i < nr_roots; i++) {
		int s = struct_page_list->values[i].i;
		if (!page_index_valid(s) || !heap[s].page || heap[s].page->type != STRUCT_PAGE) {
			WARNING("SerializeStruct: invalid root page %d at index %d", s, i);
			return 0;
		}
	}

	struct x4ss_save_ctx ctx = {
		.slot_to_id = xcalloc((size_t)max(heap_size, 1), sizeof(int)),
		.id_to_slot = xcalloc(256, sizeof(int)),
		.nr_objs = 0,
		.cap_objs = 256,
	};

	uint32_t *root_ids = xcalloc((size_t)max(nr_roots, 1), sizeof(uint32_t));
	for (int i = 0; i < nr_roots; i++)
		root_ids[i] = x4ss_register_page(&ctx, struct_page_list->values[i].i);

	/* BFS over all reachable STRUCT_PAGE and ARRAY_PAGE objects */
	for (int id = 1; id <= ctx.nr_objs; id++) {
		int slot = ctx.id_to_slot[id];
		struct page *p = heap[slot].page;
		if (!p)
			continue;
		if (p->type == STRUCT_PAGE) {
			struct ain_struct *st = &ain->structures[p->index];
			for (int m = 0; m < st->nr_members; m++) {
				if (st->members[m].type.data == AIN_VOID)
					continue;
				if (st->members[m].name && !strcmp(st->members[m].name, "<vtable>"))
					continue;
				x4ss_collect_value(&ctx, &p->values[m], &st->members[m].type);
			}
		} else if (p->type == ARRAY_PAGE) {
			struct ain_type et = p->a_type == AIN_ARRAY
				? p->array.element_type
				: (struct ain_type){ .data = array_type(p->a_type), .struc = p->array.struct_type };
			int w = ain_type_slot_width(&et);
			int len = p->nr_vars / w;
			for (int k = 0; k < len; k++)
				x4ss_collect_value(&ctx, &p->values[k * w], &et);
		}
	}

	struct x4ss_buf payload = {0};
	x4ss_put_u32(&payload, (uint32_t)nr_roots);
	for (int i = 0; i < nr_roots; i++)
		x4ss_put_u32(&payload, root_ids[i]);
	x4ss_put_u32(&payload, (uint32_t)ctx.nr_objs);

	/* Pass 1: object headers so cycles & forward refs allocate first on load */
	for (int id = 1; id <= ctx.nr_objs; id++) {
		int slot = ctx.id_to_slot[id];
		struct page *p = heap[slot].page;
		if (!p) {
			x4ss_put_u8(&payload, X4SS_OBJ_NULL);
		} else if (p->type == STRUCT_PAGE) {
			x4ss_put_u8(&payload, X4SS_OBJ_STRUCT);
			x4ss_put_cstr(&payload, ain->structures[p->index].name);
		} else {
			struct ain_type et = p->a_type == AIN_ARRAY
				? p->array.element_type
				: (struct ain_type){ .data = array_type(p->a_type), .struc = p->array.struct_type };
			int w = ain_type_slot_width(&et);
			int len = p->nr_vars / w;
			x4ss_put_u8(&payload, X4SS_OBJ_ARRAY);
			x4ss_put_i32(&payload, p->a_type);
			x4ss_put_i32(&payload, p->array.rank);
			x4ss_write_ain_type(&payload, &et);
			x4ss_put_u32(&payload, (uint32_t)len);
		}
	}

	/* Pass 2: object bodies */
	for (int id = 1; id <= ctx.nr_objs; id++) {
		int slot = ctx.id_to_slot[id];
		struct page *p = heap[slot].page;
		if (!p)
			continue;
		if (p->type == STRUCT_PAGE) {
			struct ain_struct *st = &ain->structures[p->index];
			uint32_t nr_fields = 0;
			for (int m = 0; m < st->nr_members; m++) {
				enum ain_data_type dt = st->members[m].type.data;
				if (dt == AIN_VOID || dt == AIN_DELEGATE || dt == AIN_REF_DELEGATE)
					continue;
				if (st->members[m].name && !strcmp(st->members[m].name, "<vtable>"))
					continue;
				nr_fields++;
			}
			x4ss_put_u32(&payload, nr_fields);
			for (int m = 0; m < st->nr_members; m++) {
				enum ain_data_type dt = st->members[m].type.data;
				if (dt == AIN_VOID || dt == AIN_DELEGATE || dt == AIN_REF_DELEGATE)
					continue;
				if (st->members[m].name && !strcmp(st->members[m].name, "<vtable>"))
					continue;
				x4ss_put_cstr(&payload, st->members[m].name);
				x4ss_write_value(&ctx, &payload, &p->values[m], &st->members[m].type);
			}
		} else if (p->type == ARRAY_PAGE) {
			struct ain_type et = p->a_type == AIN_ARRAY
				? p->array.element_type
				: (struct ain_type){ .data = array_type(p->a_type), .struc = p->array.struct_type };
			int w = ain_type_slot_width(&et);
			int len = p->nr_vars / w;
			for (int k = 0; k < len; k++)
				x4ss_write_value(&ctx, &payload, &p->values[k * w], &et);
		}
	}

	free(root_ids);
	free(ctx.slot_to_id);
	free(ctx.id_to_slot);

	uLongf comp_cap = compressBound((uLong)payload.len);
	uint8_t *comp = xmalloc(comp_cap);
	if (compress2(comp, &comp_cap, payload.data, (uLong)payload.len, Z_BEST_SPEED) != Z_OK) {
		free(comp);
		free(payload.data);
		return 0;
	}

	char *path = resolve_struct_save_path(filename, save_folder);
	uint8_t *existing_comment = NULL;
	uint32_t existing_comment_len = 0;
	x4ss_read_existing_parts(path, &existing_comment, &existing_comment_len, NULL, NULL, NULL);

	bool ok = x4ss_write_container(path, existing_comment, existing_comment_len,
			(uint32_t)payload.len, comp, (uint32_t)comp_cap);

	free(existing_comment);
	free(path);
	free(comp);
	free(payload.data);
	return ok ? 1 : 0;
}

struct x4ss_obj_meta {
	uint8_t kind;
	int struct_no;
	struct ain_type elem_type;
	uint32_t elem_count;
};

int deserialize_structs(const char *filename, struct page *struct_page_list, bool save_folder)
{
	if (!filename || !struct_page_list || struct_page_list->type != ARRAY_PAGE)
		return 0;

	int nr_caller_roots = v12_array_length(struct_page_list);
	for (int i = 0; i < nr_caller_roots; i++) {
		int s = struct_page_list->values[i].i;
		if (!page_index_valid(s) || !heap[s].page || heap[s].page->type != STRUCT_PAGE) {
			WARNING("DeserializeStruct: invalid root page %d at index %d", s, i);
			return 0;
		}
	}

	char *path = resolve_struct_save_path(filename, save_folder);
	uint32_t uncomp_len = 0, comp_len = 0;
	uint8_t *comp = NULL;
	if (!x4ss_read_existing_parts(path, NULL, NULL, &uncomp_len, &comp, &comp_len) ||
	    uncomp_len == 0 || comp_len == 0 || !comp) {
		/* Try .bak recovery if critical save */
		char *bak_path = make_backup_path(path);
		free(comp);
		comp = NULL;
		if (!file_exists(bak_path) ||
		    !x4ss_read_existing_parts(bak_path, NULL, NULL, &uncomp_len, &comp, &comp_len) ||
		    uncomp_len == 0 || comp_len == 0 || !comp) {
			free(comp);
			free(bak_path);
			free(path);
			return 0;
		}
		WARNING("Recovered %s from backup %s", display_utf0(path), display_utf0(bak_path));
		file_copy(bak_path, path);
		free(bak_path);
	}
	free(path);

	uint8_t *raw = xmalloc(uncomp_len);
	uLongf dst_len = (uLongf)uncomp_len;
	if (uncompress(raw, &dst_len, comp, (uLong)comp_len) != Z_OK || dst_len != uncomp_len) {
		free(raw);
		free(comp);
		WARNING("DeserializeStruct: decompression failed for %s", display_sjis0(filename));
		return 0;
	}
	free(comp);

	struct x4ss_reader r = { .data = raw, .len = uncomp_len, .pos = 0, .error = false };
	uint32_t nr_saved_roots = x4ss_get_u32(&r);
	if (r.error || (int)nr_saved_roots != nr_caller_roots) {
		free(raw);
		WARNING("DeserializeStruct: root count mismatch (%u vs %d)", nr_saved_roots, nr_caller_roots);
		return 0;
	}
	uint32_t *root_ids = xcalloc(max(nr_saved_roots, 1u), sizeof(uint32_t));
	for (uint32_t i = 0; i < nr_saved_roots; i++)
		root_ids[i] = x4ss_get_u32(&r);

	uint32_t nr_objs = x4ss_get_u32(&r);
	if (r.error || nr_objs > 16 * 1024 * 1024) {
		free(root_ids);
		free(raw);
		return 0;
	}

	struct x4ss_load_ctx ctx = {
		.nr_objs = nr_objs,
		.id_to_slot = xcalloc((size_t)nr_objs + 1, sizeof(int)),
		.is_root = xcalloc((size_t)nr_objs + 1, sizeof(bool)),
	};
	for (uint32_t id = 0; id <= nr_objs; id++)
		ctx.id_to_slot[id] = -1;
	for (uint32_t i = 0; i < nr_saved_roots; i++) {
		uint32_t rid = root_ids[i];
		if (rid > 0 && rid <= nr_objs) {
			ctx.id_to_slot[rid] = struct_page_list->values[i].i;
			ctx.is_root[rid] = true;
		}
	}

	struct x4ss_obj_meta *metas = xcalloc((size_t)nr_objs + 1, sizeof(*metas));

	/* Pass 1: read headers and pre-allocate non-root pages */
	for (uint32_t id = 1; id <= nr_objs && !r.error; id++) {
		uint8_t kind = x4ss_get_u8(&r);
		metas[id].kind = kind;
		if (kind == X4SS_OBJ_NULL) {
			if (!ctx.is_root[id]) {
				int slot = heap_alloc_slot(VM_PAGE);
				heap_set_page(slot, NULL);
				ctx.id_to_slot[id] = slot;
			}
		} else if (kind == X4SS_OBJ_STRUCT) {
			char *sname = x4ss_get_cstr(&r, NULL);
			int sno = sname ? save_find_struct(sname) : -1;
			free(sname);
			metas[id].struct_no = sno;
			if (!ctx.is_root[id] && sno >= 0) {
				ctx.id_to_slot[id] = alloc_struct(sno);
			}
		} else if (kind == X4SS_OBJ_ARRAY) {
			int32_t a_type = x4ss_get_i32(&r);
			int32_t rank = x4ss_get_i32(&r);
			struct ain_type et = x4ss_read_ain_type(&r);
			uint32_t count = x4ss_get_u32(&r);
			metas[id].elem_type = et;
			metas[id].elem_count = count;
			if (!r.error && !ctx.is_root[id]) {
				int w = ain_type_slot_width(&et);
				struct page *ap = alloc_page(ARRAY_PAGE, a_type, (int)count * w);
				ap->array.rank = rank > 0 ? rank : 1;
				ap->array.struct_type = et.struc;
				ap->array.element_type = et;
				for (uint32_t k = 0; k < count; k++)
					variable_init_typed(&ap->values[k * w], &et, false, false);
				ctx.id_to_slot[id] = heap_alloc_page(ap);
			}
		} else {
			r.error = true;
		}
	}

	/* Pass 2: populate bodies */
	for (uint32_t id = 1; id <= nr_objs && !r.error; id++) {
		uint8_t kind = metas[id].kind;
		int slot = ctx.id_to_slot[id];
		if (kind == X4SS_OBJ_NULL) {
			continue;
		} else if (kind == X4SS_OBJ_STRUCT) {
			uint32_t nr_fields = x4ss_get_u32(&r);
			int sno = metas[id].struct_no;
			struct page *sp = (slot >= 0 && page_index_valid(slot)) ? heap[slot].page : NULL;
			if (sp && sp->type == STRUCT_PAGE && ctx.is_root[id]) {
				/* Clear any existing delegate listeners on root struct */
				struct ain_struct *rst = &ain->structures[sp->index];
				for (int m = 0; m < rst->nr_members; m++) {
					if (rst->members[m].type.data == AIN_DELEGATE) {
						int dslot = sp->values[m].i;
						if (page_index_valid(dslot) && heap[dslot].page)
							heap_set_page(dslot, delegate_clear(heap[dslot].page));
					}
				}
			}
			for (uint32_t f = 0; f < nr_fields && !r.error; f++) {
				char *fname = x4ss_get_cstr(&r, NULL);
				int m_idx = -1;
				if (sp && sp->type == STRUCT_PAGE && sno >= 0 && fname)
					m_idx = struct_member_index(&ain->structures[sp->index], fname);
				free(fname);
				if (m_idx >= 0) {
					struct ain_struct *st = &ain->structures[sp->index];
					x4ss_read_value(&ctx, &r, &sp->values[m_idx], &st->members[m_idx].type, true);
				} else {
					x4ss_skip_value(&r);
				}
			}
		} else if (kind == X4SS_OBJ_ARRAY) {
			struct page *ap = (slot >= 0 && page_index_valid(slot)) ? heap[slot].page : NULL;
			int w = ain_type_slot_width(&metas[id].elem_type);
			for (uint32_t k = 0; k < metas[id].elem_count && !r.error; k++) {
				if (ap && ap->type == ARRAY_PAGE) {
					x4ss_read_value(&ctx, &r, &ap->values[k * w], &metas[id].elem_type, true);
				} else {
					x4ss_skip_value(&r);
				}
			}
		}
	}

	bool ok = !r.error;

	/* Release temporary table reference on all newly allocated non-root pages */
	for (uint32_t id = 1; id <= nr_objs; id++) {
		if (!ctx.is_root[id] && ctx.id_to_slot[id] >= 0)
			exit_unref(ctx.id_to_slot[id]);
	}

	free(metas);
	free(ctx.is_root);
	free(ctx.id_to_slot);
	free(root_ids);
	free(raw);

	if (ok && save_folder && is_game_config_save(filename))
		modifier_on_config_loaded();

	return ok ? 1 : 0;
}

int write_serialize_struct_comment(const char *filename, const struct string *comment, bool save_folder)
{
	if (!filename)
		return 0;
	char *path = resolve_struct_save_path(filename, save_folder);
	uint32_t uncomp_len = 0, comp_len = 0;
	uint8_t *comp = NULL;
	x4ss_read_existing_parts(path, NULL, NULL, &uncomp_len, &comp, &comp_len);

	const uint8_t *cbuf = comment ? (const uint8_t *)comment->text : (const uint8_t *)"";
	uint32_t clen = comment ? (uint32_t)comment->size : 0;
	bool ok = x4ss_write_container(path, cbuf, clen, uncomp_len, comp, comp_len);

	free(comp);
	free(path);
	return ok ? 1 : 0;
}

int read_serialize_struct_comment(const char *filename, struct string **comment_out, bool save_folder)
{
	if (!filename || !comment_out)
		return 0;
	char *path = resolve_struct_save_path(filename, save_folder);
	uint8_t *cbuf = NULL;
	uint32_t clen = 0;
	bool ok = x4ss_read_existing_parts(path, &cbuf, &clen, NULL, NULL, NULL);
	free(path);
	if (!ok || !cbuf)
		return 0;
	*comment_out = make_string((const char *)cbuf, clen);
	free(cbuf);
	return 1;
}

int write_group_save_comment(const char *keyname, const char *filename, const struct string *log)
{
	(void)keyname;
	if (!filename)
		return 0;
	char *base = savedir_path(filename);
	size_t blen = strlen(base);
	char *cpath = xmalloc(blen + 9);
	memcpy(cpath, base, blen);
	memcpy(cpath + blen, ".comment", 9);
	free(base);

	char *tmp = NULL;
	FILE *fp = atomic_save_open(cpath, "wb", &tmp);
	if (!fp) {
		free(cpath);
		return 0;
	}
	size_t slen = log ? (size_t)log->size : 0;
	bool ok = (slen == 0) || (fwrite(log->text, 1, slen, fp) == slen);
	if (!ok) {
		atomic_save_abort(fp, tmp);
		free(cpath);
		return 0;
	}
	ok = atomic_save_commit(fp, cpath, tmp, false);
	free(cpath);
	return ok ? 1 : 0;
}

int read_group_save_comment(const char *keyname, const char *filename, struct string **log_out)
{
	(void)keyname;
	if (!filename || !log_out)
		return 0;
	char *base = savedir_path(filename);
	size_t blen = strlen(base);
	char *cpath = xmalloc(blen + 9);
	memcpy(cpath, base, blen);
	memcpy(cpath + blen, ".comment", 9);
	free(base);

	size_t len = 0;
	uint8_t *data = file_read(cpath, &len);
	free(cpath);
	if (!data)
		return 0;
	*log_out = make_string((const char *)data, (unsigned int)len);
	free(data);
	return 1;
}
