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
#include "system4/utfsjis.h"
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

static int get_group_index(const char *name)
{
	for (int i = 0; i < ain->nr_global_groups; i++) {
		if (!strcmp(ain->global_group_names[i], name))
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
	if (!key || strcmp(keyname, cJSON_GetStringValue(key)))
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
	return -1;
}

static union vm_value gsave_to_vm_value(struct gsave *save, enum ain_data_type type, int struct_type, int array_rank, int32_t value);
static union vm_value gsave_to_vm_value_typed(struct gsave *save, const struct ain_type *type, int32_t value);

static struct gsave_flat_array *gsave_load_array(struct gsave *save, struct page *page, struct gsave_flat_array *flat_array)
{
	assert(page->type == ARRAY_PAGE);
	if (nested_array(page)) {
		for (int i = 0; i < page->nr_vars; i++)
			flat_array = gsave_load_array(save, heap_get_page(page->values[i].i), flat_array);
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
		union vm_value val = type ? gsave_to_vm_value_typed(save, type, flat_array->values[i].value)
			: gsave_to_vm_value(save, data_type, struct_type, array_rank, flat_array->values[i].value);
		variable_fini(page->values[i], data_type, false);
		page->values[i] = val;
	}
	return flat_array + 1;
}

static union vm_value gsave_to_vm_value(struct gsave *save, enum ain_data_type type, int struct_type, int array_rank, int32_t value)
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
			heap[slot].s = string_ref(value == GSAVE7_EMPTY_STRING ? &EMPTY_STRING : save->strings[value]);
			return vm_int(slot);
		}
	case AIN_STRUCT:
		{
			struct gsave_record *rec = &save->records[value];
			struct ain_struct *st = &ain->structures[struct_type];
			struct gsave_struct_def *sd =
				save->version >= 7 ? &save->struct_defs[rec->struct_index] : NULL;
			const char *struct_name = sd ? sd->name : rec->struct_name;
			if (strcmp(struct_name, st->name))
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
				if (index < 0) {
					WARNING("structure %s has no member named %s", display_sjis0(struct_name), display_sjis1(field_name));
					continue;
				}
				int struct_type, array_rank;
				enum ain_data_type data_type = variable_type(page, index, &struct_type, &array_rank);
				if (data_type != field_type)
					VM_ERROR("Bad save file: structure member type mismatch");
				union vm_value val = gsave_to_vm_value_typed(save, &st->members[index].type, kv->value);
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
			gsave_load_array(save, page, array->flat_arrays);
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

static union vm_value gsave_to_vm_value_typed(struct gsave *save, const struct ain_type *type, int32_t value)
{
	if (type->data != AIN_ARRAY)
		return gsave_to_vm_value(save, type->data, type->struc, type->rank, value);
	if (value < 0 || value >= save->nr_arrays)
		VM_ERROR("Bad save file: invalid generic array index %d", value);
	struct gsave_array *array = &save->arrays[value];
	if (array->rank < 1) return variable_initval_typed(type);
	struct page *page = v12_saved_array_new(type, array->rank, array->dimensions);
	gsave_load_array(save, page, array->flat_arrays);
	return vm_int(heap_alloc_page(page));
}

int load_globals_from_gsave(struct gsave *save, const char *keyname, const char *group_name, int *n)
{
	if (strcmp(keyname, save->key))
		VM_ERROR("Attempted to load save data with wrong key: %s", display_sjis0(keyname));
	if (!group_name)
		group_name = "";
	if (!save->group)
		save->group = strdup("");
	if (strcmp(group_name, save->group))
		VM_ERROR("Attempted to load save data with wrong group name: '%s'", display_sjis0(group_name));

	for (struct gsave_global *g = save->globals; g < save->globals + save->nr_globals; g++) {
		if (g->type == AIN_VOID && !g->name[0]) continue;
		int global_index = ain_get_global(ain, g->name);
		if (global_index < 0) {
			for (int i = 0; i < ain->nr_globals; i++) {
				if (ain->globals[i].name2 && ain->globals[i].name2[0] && !strcmp(ain->globals[i].name2, g->name)) {
					global_index = i;
					break;
				}
			}
		}
		if (global_index < 0) {
			WARNING("invalid global name %s", display_sjis0(g->name));
			return 0;
		}
		struct ain_type *type = &ain->globals[global_index].type;
		if (type->data != g->type) {
			WARNING("%s: type mismatch", display_sjis0(g->name));
			return 0;
		}
		if (should_preserve_static_global(global_index)) {
			if (n)
				(*n)++;
			continue;
		}
		bool call_dtors = false; // Destructors for old objects should not be called.
		global_set(global_index, gsave_to_vm_value_typed(save, type, g->value), call_dtors);
		if (n)
			(*n)++;
	}

	return 1;
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
		id = ain_get_struct(ain, (char *)name);
		return id >= 0 ? id : fallback_id;
	case AIN_ENUM:
	case AIN_ENUM2:
	case AIN_REF_ENUM:
		for (int i = 0; i < ain->nr_enums; i++) {
			if (ain->enums[i].name && !strcmp(ain->enums[i].name, name))
				return i;
		}
		return fallback_id;
	case AIN_DELEGATE:
	case AIN_REF_DELEGATE:
		for (int i = 0; i < ain->nr_delegates; i++) {
			if (ain->delegates[i].name && !strcmp(ain->delegates[i].name, name))
				return i;
		}
		return fallback_id;
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
			int sno = sname ? ain_get_struct(ain, sname) : -1;
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
				if (sp && sp->type == STRUCT_PAGE && sno >= 0 && fname) {
					struct ain_struct *st = &ain->structures[sp->index];
					for (int m = 0; m < st->nr_members; m++) {
						if (st->members[m].name && !strcmp(st->members[m].name, fname)) {
							m_idx = m;
							break;
						}
					}
				}
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
