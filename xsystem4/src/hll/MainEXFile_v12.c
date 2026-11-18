/* AIN v12 name-based EX readers, GPL-2.0-or-later. */
#include <ctype.h>
#include <stdlib.h>
#include <string.h>
#include "system4/ain.h"
#include "system4/archive.h"
#include "system4/ex.h"
#include "system4/hashtable.h"
#include "system4/little_endian.h"
#include "system4/string.h"
#include "system4/utfsjis.h"
#include "vm/heap.h"
#include "vm/page.h"
#include "vm/v12.h"
#include "reign.h"
#include "xsystem4.h"
#include "mainex_v12.h"
#include "hll.h"

static struct ex **readers;
static char **reader_paths;
static int nr_readers;
static char **added_ex_files;
static int nr_added_ex_files;
static char **added_ex_texts;
static int nr_added_ex_texts;
static struct archive *poly_ex_ar;
static bool poly_ex_ar_failed;
static struct hash_table *poly_ex_cache;
static struct ex missing_poly_ex;

static void free_poly_ex_cb(void *val)
{
	if (val && val != &missing_poly_ex)
		ex_free((struct ex *)val);
}

static void poly_ex_fini(void)
{
	if (poly_ex_cache) {
		ht_foreach_value(poly_ex_cache, free_poly_ex_cb);
		ht_free(poly_ex_cache);
		poly_ex_cache = NULL;
	}
	if (poly_ex_ar) {
		archive_free(poly_ex_ar);
		poly_ex_ar = NULL;
	}
	poly_ex_ar_failed = false;
}

static const char *find_first_dot_sjis(const char *p)
{
	const uint8_t *u = (const uint8_t *)p;
	while (*u == '/' || *u == '\\')
		u++;
	if (u[0] == '.' && (u[1] == '/' || u[1] == '\\'))
		u += 2;
	while (*u) {
		if (SJIS_2BYTE(*u) && u[1]) {
			u += 2;
			continue;
		}
		if (*u == '.')
			return (const char *)u;
		u++;
	}
	return NULL;
}

static char *normalize_poly_ex_dir(const char *s, size_t len)
{
	size_t i = 0;
	while (i < len && (s[i] == '/' || s[i] == '\\'))
		i++;
	if (i + 1 < len && s[i] == '.' && (s[i + 1] == '/' || s[i + 1] == '\\'))
		i += 2;
	char *out = xmalloc(len - i + 1);
	size_t j = 0;
	while (i < len) {
		uint8_t c = (uint8_t)s[i];
		if (SJIS_2BYTE(c) && i + 1 < len) {
			out[j++] = (char)c;
			out[j++] = s[i + 1];
			i += 2;
			continue;
		}
		if (c == '\\' || c == '/') {
			if (j > 0 && out[j - 1] != '/')
				out[j++] = '/';
			i++;
			continue;
		}
		out[j++] = (char)tolower(c);
		i++;
	}
	while (j > 0 && out[j - 1] == '/')
		j--;
	out[j] = '\0';
	return out;
}

static bool poly_ex_resolve(const char *path, struct ex **ex_out, const char **subpath_out)
{
	*ex_out = NULL;
	*subpath_out = "";
	if (!path || !*path)
		return false;
	const char *dot = find_first_dot_sjis(path);
	size_t dir_len = dot ? (size_t)(dot - path) : strlen(path);
	*subpath_out = dot ? dot + 1 : "";

	char *key = normalize_poly_ex_dir(path, dir_len);
	if (!*key) {
		free(key);
		return false;
	}
	if (!poly_ex_cache)
		poly_ex_cache = ht_create(256);

	struct ex *cached = ht_get(poly_ex_cache, key, NULL);
	if (cached) {
		free(key);
		if (cached == &missing_poly_ex)
			return false;
		*ex_out = cached;
		return true;
	}

	if (!poly_ex_ar && !poly_ex_ar_failed) {
		poly_ex_ar = RE_open_3d_archive();
		if (!poly_ex_ar)
			poly_ex_ar_failed = true;
	}

	struct ex *ex = NULL;
	if (poly_ex_ar) {
		char *raw_dir = xmalloc(dir_len + 1);
		memcpy(raw_dir, path, dir_len);
		raw_dir[dir_len] = '\0';
		struct archive_data *d = RE_archive_find_txtex(poly_ex_ar, raw_dir);
		free(raw_dir);
		if (d) {
			ex = ex_read(d->data, d->size);
			archive_free_data(d);
		}
	}

	ht_put(poly_ex_cache, key, NULL)->value = ex ? (void *)ex : (void *)&missing_poly_ex;
	free(key);
	*ex_out = ex;
	return ex != NULL;
}

static void free_string_list(char ***list, int *count)
{
	for (int i = 0; i < *count; i++)
		free((*list)[i]);
	free(*list);
	*list = NULL;
	*count = 0;
}

static void clear_dynamic_readers(void)
{
	for (int i = 2; i < nr_readers; i++) {
		if (readers[i]) {
			ex_free(readers[i]);
			readers[i] = NULL;
		}
		free(reader_paths[i]);
		reader_paths[i] = NULL;
	}
}

static void mainex_init(void)
{
	poly_ex_ar_failed = false;
	if (!readers) {
		readers = xcalloc(2, sizeof(*readers));
		reader_paths = xcalloc(2, sizeof(*reader_paths));
		nr_readers = 2;
	}
	if (readers[1]) {
		ex_free(readers[1]);
		readers[1] = NULL;
	}
	if (!config.ex_path || !(readers[1] = ex_read_file(config.ex_path)))
		ERROR("Failed to load main EX file: %s", config.ex_path);
}

static void mainex_fini(void)
{
	poly_ex_fini();
	for (int i = 0; i < nr_readers; i++) {
		if (readers && readers[i])
			ex_free(readers[i]);
		if (reader_paths)
			free(reader_paths[i]);
	}
	free(readers);
	free(reader_paths);
	readers = NULL;
	reader_paths = NULL;
	nr_readers = 0;
	free_string_list(&added_ex_files, &nr_added_ex_files);
	free_string_list(&added_ex_texts, &nr_added_ex_texts);
}

static struct ex *reader(int id)
{
	if (id == 0)
		id = 1;
	return id > 0 && id < nr_readers ? readers[id] : NULL;
}

static bool resolve_ex(struct string *path, int id, struct ex **ex_out, const char **subpath_out)
{
	if (game_evenicle2 && id == 1) {
		if (!path || !path->size) {
			*ex_out = NULL;
			*subpath_out = "";
			return false;
		}
		return poly_ex_resolve(path->text, ex_out, subpath_out);
	}
	struct ex *ex = reader(id);
	*ex_out = ex;
	*subpath_out = path ? path->text : "";
	return ex != NULL;
}

static struct ex_value *value(struct string *name, int id)
{
	if (!name)
		return NULL;
	struct ex *ex = NULL;
	const char *subpath = "";
	if (!resolve_ex(name, id, &ex, &subpath) || !*subpath)
		return NULL;
	return ex_get(ex, subpath);
}

static struct ex_value *avalue(struct string *name, int index, int id)
{
	struct ex_value *v = value(name, id);
	return v && v->type == EX_LIST ? ex_list_get(v->list, index) : NULL;
}

static struct ex_value *a2value(struct string *name, int row, int col, int id)
{
	struct ex_value *v = value(name, id);
	return v && v->type == EX_TABLE ? ex_table_get(v->t, row, col) : NULL;
}

static int mainex_row(struct string *name, int id)
{
	struct ex_value *v = value(name, id);
	return v && v->type == EX_TABLE ? v->t->nr_rows : 0;
}

static int mainex_col(struct string *name, int id)
{
	struct ex_value *v = value(name, id);
	if (!v) return 0;
	if (v->type == EX_TABLE) return v->t->nr_columns;
	if (v->type == EX_LIST) return v->list->nr_items;
	return 0;
}

static int mainex_type(struct string *name, int id)
{
	struct ex *ex = NULL;
	const char *subpath = "";
	if (!name || !resolve_ex(name, id, &ex, &subpath))
		return 0;
	if (!*subpath)
		return (game_evenicle2 && id == 1) ? EX_TREE : 0;
	struct ex_value *v = ex_get(ex, subpath);
	return v ? v->type : 0;
}

static int mainex_atype(struct string *name, int index, int id)
{
	struct ex_value *v = avalue(name, index, id);
	return v ? v->type : 0;
}

static int mainex_a2type(struct string *name, int row, int col, int id)
{
	struct ex_value *v = a2value(name, row, col, id);
	return v ? v->type : 0;
}

static bool mainex_exists(struct string *name, int id)
{
	struct ex *ex = NULL;
	const char *subpath = "";
	if (!name || !resolve_ex(name, id, &ex, &subpath))
		return false;
	if (!*subpath)
		return game_evenicle2 && id == 1;
	return ex_get(ex, subpath) != NULL;
}
static bool mainex_aexists(struct string *name, int index, int id) { return avalue(name, index, id); }
static bool mainex_a2exists(struct string *name, int row, int col, int id) { return a2value(name, row, col, id); }

#define EX_NUMERIC_GETTERS(label, ctype, ex_type, member) \
	static ctype mainex_##label(struct string *name, ctype fallback, int id) { \
		struct ex_value *v = value(name, id); \
		return v && v->type == ex_type ? v->member : fallback; \
	} \
	static ctype mainex_a##label(struct string *name, int index, ctype fallback, int id) { \
		struct ex_value *v = avalue(name, index, id); \
		return v && v->type == ex_type ? v->member : fallback; \
	} \
	static ctype mainex_a2##label(struct string *name, int row, int col, ctype fallback, int id) { \
		struct ex_value *v = a2value(name, row, col, id); \
		return v && v->type == ex_type ? v->member : fallback; \
	}
EX_NUMERIC_GETTERS(integer, int, EX_INT, i)
EX_NUMERIC_GETTERS(floating, float, EX_FLOAT, f)
#undef EX_NUMERIC_GETTERS

static struct string *string_value(struct ex_value *v, struct string *fallback)
{
	if (v && v->type == EX_STRING && v->s)
		return string_dup(v->s);
	return fallback ? string_dup(fallback) : string_ref(&EMPTY_STRING);
}

static struct string *mainex_string(struct string *name, struct string *fallback, int id)
{
	return string_value(value(name, id), fallback);
}

static struct string *mainex_astring(struct string *name, int index, struct string *fallback, int id)
{
	return string_value(avalue(name, index, id), fallback);
}

static struct string *mainex_a2string(struct string *name, int row, int col, struct string *fallback, int id)
{
	return string_value(a2value(name, row, col, id), fallback);
}

static int mainex_int_key(struct string *name, int key, int id)
{
	struct ex_value *v = value(name, id);
	return v && v->type == EX_TABLE ? ex_row_at_int_key(v->t, key) : -1;
}

static int mainex_string_key(struct string *name, struct string *key, int id)
{
	if (!key)
		return -1;
	struct ex_value *v = value(name, id);
	return v && v->type == EX_TABLE ? ex_row_at_string_key(v->t, key->text) : -1;
}

static int mainex_column(struct string *name, struct string *field, int id)
{
	if (!field)
		return -1;
	struct ex_value *v = value(name, id);
	return v && v->type == EX_TABLE ? ex_col_from_name(v->t, field->text) : -1;
}

static struct ex *read_ex_cstr(const char *filename)
{
	char *path = gamedir_path_icase(filename);
	if (!path)
		path = gamedir_path(filename);
	struct ex *ex = ex_read_file(path);
	free(path);
	return ex;
}

static struct ex *read_ex(struct string *file)
{
	if (!file)
		return NULL;
	return read_ex_cstr(file->text);
}

static void ensure_reader_capacity(int min_nr)
{
	if (min_nr <= nr_readers)
		return;
	readers = xrealloc(readers, min_nr * sizeof(*readers));
	reader_paths = xrealloc(reader_paths, min_nr * sizeof(*reader_paths));
	for (int i = nr_readers; i < min_nr; i++) {
		readers[i] = NULL;
		reader_paths[i] = NULL;
	}
	nr_readers = min_nr;
}

int mainex_v12_register(struct ex *ex)
{
	if (!ex) return -1;
	ensure_reader_capacity(2);
	int id;
	for (id = 2; id < nr_readers && readers[id]; id++);
	ensure_reader_capacity(id + 1);
	readers[id] = ex;
	return id;
}

int mainex_v12_register_at(int preferred_id, struct ex *ex)
{
	if (!ex) return -1;
	if (preferred_id >= 2) {
		ensure_reader_capacity(preferred_id + 1);
		if (!readers[preferred_id]) {
			readers[preferred_id] = ex;
			return preferred_id;
		}
	}
	return mainex_v12_register(ex);
}

static int mainex_add_reader(struct string *file)
{
	if (!file)
		return -1;
	int id = mainex_v12_register(read_ex(file));
	if (id >= 2) {
		free(reader_paths[id]);
		reader_paths[id] = xstrdup(file->text);
	}
	return id;
}

void mainex_v12_unregister(int id)
{
	if (id < 2 || id >= nr_readers || !readers[id]) return;
	ex_free(readers[id]);
	readers[id] = NULL;
	free(reader_paths[id]);
	reader_paths[id] = NULL;
}

void mainex_v12_unregister_activity(int id)
{
	if (id < 2 || id >= nr_readers || !readers[id] || reader_paths[id]) return;
	ex_free(readers[id]);
	readers[id] = NULL;
}

static void mainex_erase_reader(int id) { mainex_v12_unregister(id); }

static void append_string_entry(char ***list, int *count, const char *s)
{
	*list = xrealloc(*list, (*count + 1) * sizeof(**list));
	(*list)[(*count)++] = xstrdup(s);
}

static bool mainex_add_cstr(const char *filename)
{
	if (!filename || nr_readers < 2 || !readers[1])
		return false;
	struct ex *ex = read_ex_cstr(filename);
	if (!ex) return false;
	ex_append(readers[1], ex);
	ex_free(ex);
	append_string_entry(&added_ex_files, &nr_added_ex_files, filename);
	return true;
}

static bool mainex_add(struct string *file)
{
	if (!file)
		return false;
	return mainex_add_cstr(file->text);
}

static bool mainex_add_text_cstr(const char *filename)
{
	if (!filename || nr_readers < 2 || !readers[1])
		return false;
	struct ex *ex = read_ex_cstr(filename);
	if (!ex) return false;
	ex_append(readers[1], ex);
	ex_free(ex);
	append_string_entry(&added_ex_texts, &nr_added_ex_texts, filename);
	return true;
}

static bool mainex_add_text(struct string *file)
{
	if (!file)
		return false;
	return mainex_add_text_cstr(file->text);
}

static bool mainex_save(struct page **out)
{
	if (!out)
		return false;
	size_t byte_len = 12;
	for (int i = 0; i < nr_added_ex_files; i++)
		byte_len += strlen(added_ex_files[i]) + 1;
	for (int i = 0; i < nr_added_ex_texts; i++)
		byte_len += strlen(added_ex_texts[i]) + 1;
	int active_readers = 0;
	for (int id = 2; id < nr_readers; id++) {
		if (readers[id] && reader_paths[id]) {
			active_readers++;
			byte_len += 4 + strlen(reader_paths[id]) + 1;
		}
	}

	int nr_ints = (int)(byte_len / 4) + 1;
	uint8_t *buf = xcalloc(nr_ints, 4);
	size_t pos = 0;

	LittleEndian_putDW(buf, pos, nr_added_ex_files);
	pos += 4;
	for (int i = 0; i < nr_added_ex_files; i++) {
		size_t len = strlen(added_ex_files[i]) + 1;
		memcpy(buf + pos, added_ex_files[i], len);
		pos += len;
	}

	LittleEndian_putDW(buf, pos, nr_added_ex_texts);
	pos += 4;
	for (int i = 0; i < nr_added_ex_texts; i++) {
		size_t len = strlen(added_ex_texts[i]) + 1;
		memcpy(buf + pos, added_ex_texts[i], len);
		pos += len;
	}

	LittleEndian_putDW(buf, pos, active_readers);
	pos += 4;
	for (int id = 2; id < nr_readers; id++) {
		if (readers[id] && reader_paths[id]) {
			LittleEndian_putDW(buf, pos, id);
			pos += 4;
			size_t len = strlen(reader_paths[id]) + 1;
			memcpy(buf + pos, reader_paths[id], len);
			pos += len;
		}
	}

	struct ain_type type = { .data = AIN_INT, .struc = -1 };
	struct page *page = v12_array_new(&type, nr_ints, false);
	for (int i = 0; i < nr_ints; i++)
		page->values[i].i = LittleEndian_getDW(buf, i * 4);
	free(buf);

	if (*out) {
		delete_page_vars(*out);
		free_page(*out);
	}
	*out = page;
	return true;
}

static const char *read_cstr_checked(const uint8_t *buf, size_t cap, size_t *pos)
{
	if (*pos >= cap) return NULL;
	const void *nul = memchr(buf + *pos, 0, cap - *pos);
	if (!nul) return NULL;
	const char *s = (const char *)(buf + *pos);
	*pos = (const uint8_t *)nul - buf + 1;
	return s;
}

static bool mainex_load(struct page **in)
{
	if (!in || !*in || (*in)->nr_vars < 3)
		return false;

	size_t cap = (size_t)(*in)->nr_vars * 4;
	uint8_t *buf = xmalloc(cap);
	for (int i = 0; i < (*in)->nr_vars; i++)
		LittleEndian_putDW(buf, i * 4, (*in)->values[i].i);

	size_t pos = 0;
	if (pos + 4 > cap) { free(buf); return false; }
	int n_add = LittleEndian_getDW(buf, pos);
	pos += 4;
	if (n_add < 0) { free(buf); return false; }

	clear_dynamic_readers();
	free_string_list(&added_ex_files, &nr_added_ex_files);
	free_string_list(&added_ex_texts, &nr_added_ex_texts);
	mainex_init();

	bool ok = true;
	for (int i = 0; i < n_add; i++) {
		const char *s = read_cstr_checked(buf, cap, &pos);
		if (!s || !mainex_add_cstr(s)) { ok = false; break; }
	}

	int n_text = 0;
	if (ok) {
		if (pos + 4 > cap) ok = false;
		else {
			n_text = LittleEndian_getDW(buf, pos);
			pos += 4;
			if (n_text < 0) ok = false;
		}
	}
	for (int i = 0; ok && i < n_text; i++) {
		const char *s = read_cstr_checked(buf, cap, &pos);
		if (!s || !mainex_add_text_cstr(s)) { ok = false; break; }
	}

	int n_rdr = 0;
	if (ok) {
		if (pos + 4 > cap) ok = false;
		else {
			n_rdr = LittleEndian_getDW(buf, pos);
			pos += 4;
			if (n_rdr < 0) ok = false;
		}
	}
	for (int i = 0; ok && i < n_rdr; i++) {
		if (pos + 4 > cap) { ok = false; break; }
		int id = LittleEndian_getDW(buf, pos);
		pos += 4;
		const char *s = read_cstr_checked(buf, cap, &pos);
		if (!s || id < 2) { ok = false; break; }
		struct ex *ex = read_ex_cstr(s);
		if (!ex) { ok = false; break; }
		ensure_reader_capacity(id + 1);
		if (readers[id]) ex_free(readers[id]);
		free(reader_paths[id]);
		readers[id] = ex;
		reader_paths[id] = xstrdup(s);
	}

	free(buf);
	return ok;
}

static struct string *child_name_cstr(struct ex *ex, const char *path, bool leaf, int target, int *count)
{
	*count = 0;
	if (!ex || !path) return NULL;
	struct string *found = NULL;
	if (!*path) {
		for (unsigned i = 0; i < ex->nr_blocks; i++) {
			if ((ex->blocks[i].val.type != EX_TREE) != leaf) continue;
			if ((*count)++ == target) found = ex->blocks[i].name;
		}
		return found;
	}
	struct ex_value *v = ex_get(ex, path);
	if (!v || v->type != EX_TREE || v->tree->is_leaf) return NULL;
	for (unsigned i = 0; i < v->tree->nr_children; i++) {
		struct ex_tree *node = &v->tree->children[i];
		if (node->is_leaf != leaf) continue;
		if ((*count)++ == target) found = leaf ? node->leaf.name : node->name;
	}
	return found;
}

static int name_count(struct string *path, int id, bool leaf)
{
	struct ex *ex = NULL;
	const char *subpath = "";
	if (!resolve_ex(path, id, &ex, &subpath))
		return 0;
	int count;
	child_name_cstr(ex, subpath, leaf, -1, &count);
	return count;
}

static bool get_name(struct string *path, int index, struct string **out, int id, bool leaf)
{
	if (!out)
		return false;
	struct ex *ex = NULL;
	const char *subpath = "";
	if (!resolve_ex(path, id, &ex, &subpath))
		return false;
	int count;
	struct string *name = child_name_cstr(ex, subpath, leaf, index, &count);
	if (!name) return false;
	if (*out)
		free_string(*out);
	*out = string_dup(name);
	return true;
}

static bool name_list(struct string *path, struct page **out, int id, bool leaf)
{
	struct ex *ex = NULL;
	const char *subpath = "";
	if (!out || !resolve_ex(path, id, &ex, &subpath)) return false;
	int count;
	child_name_cstr(ex, subpath, leaf, -1, &count);
	struct ain_type type = { .data = AIN_STRING, .struc = -1 };
	struct page *page = v12_array_new(&type, count, false);
	for (int i = 0; i < count; i++) {
		int unused;
		struct string *s = child_name_cstr(ex, subpath, leaf, i, &unused);
		int slot = page->values[i].i;
		free_string(heap[slot].s);
		heap[slot].s = s ? string_dup(s) : string_ref(&EMPTY_STRING);
	}
	if (*out) { delete_page_vars(*out); free_page(*out); }
	*out = page;
	return true;
}

static int mainex_node_count(struct string *path, int id) { return name_count(path, id, false); }
static int mainex_name_count(struct string *path, int id) { return name_count(path, id, true); }
static bool mainex_node(struct string *p, int i, struct string **s, int id) { return get_name(p, i, s, id, false); }
static bool mainex_name(struct string *p, int i, struct string **s, int id) { return get_name(p, i, s, id, true); }
static bool mainex_nodes(struct string *p, struct page **a, int id) { return name_list(p, a, id, false); }
static bool mainex_names(struct string *p, struct page **a, int id) { return name_list(p, a, id, true); }

static bool mainex_format_names(struct string *name, struct page **out, int id)
{
	if (!out)
		return false;
	struct ex_value *v = value(name, id);
	if (!v || v->type != EX_TABLE || !v->t)
		return false;
	int count = (int)v->t->nr_fields;
	struct ain_type type = { .data = AIN_STRING, .struc = -1 };
	struct page *page = v12_array_new(&type, count, false);
	for (int i = 0; i < count; i++) {
		int slot = page->values[i].i;
		free_string(heap[slot].s);
		heap[slot].s = v->t->fields[i].name ? string_dup(v->t->fields[i].name) : string_ref(&EMPTY_STRING);
	}
	if (*out) { delete_page_vars(*out); free_page(*out); }
	*out = page;
	return true;
}

void mainex_v12_prelink(struct static_library *library)
{
	const struct static_hll_function functions[] = {
		HLL_EXPORT(_ModuleInit, mainex_init), HLL_EXPORT(_ModuleFini, mainex_fini),
		HLL_EXPORT(AddEXReader, mainex_add_reader), HLL_EXPORT(EraseEXReader, mainex_erase_reader),
		HLL_EXPORT(AddEX, mainex_add), HLL_EXPORT(AddEXText, mainex_add_text),
		HLL_EXPORT(Save, mainex_save), HLL_EXPORT(Load, mainex_load),
		HLL_EXPORT(Row, mainex_row), HLL_EXPORT(Col, mainex_col), HLL_EXPORT(Type, mainex_type),
		HLL_EXPORT(AType, mainex_atype), HLL_EXPORT(A2Type, mainex_a2type),
		HLL_EXPORT(Exists, mainex_exists), HLL_EXPORT(AExists, mainex_aexists), HLL_EXPORT(A2Exists, mainex_a2exists),
		HLL_EXPORT(Int, mainex_integer), HLL_EXPORT(Float, mainex_floating), HLL_EXPORT(String, mainex_string),
		HLL_EXPORT(AInt, mainex_ainteger), HLL_EXPORT(AFloat, mainex_afloating), HLL_EXPORT(AString, mainex_astring),
		HLL_EXPORT(A2Int, mainex_a2integer), HLL_EXPORT(A2Float, mainex_a2floating), HLL_EXPORT(A2String, mainex_a2string),
		HLL_EXPORT(GetRowAtIntKey, mainex_int_key), HLL_EXPORT(GetRowAtStringKey, mainex_string_key),
		HLL_EXPORT(GetColAtFormatName, mainex_column),
		HLL_EXPORT(GetNodeNameCount, mainex_node_count), HLL_EXPORT(GetEXNameCount, mainex_name_count),
		HLL_EXPORT(GetNodeName, mainex_node), HLL_EXPORT(GetEXName, mainex_name),
		HLL_EXPORT(GetNodeNameList, mainex_nodes), HLL_EXPORT(GetEXNameList, mainex_names),
		HLL_EXPORT(GetFormatNameList, mainex_format_names),
	};
	for (unsigned i = 0; i < sizeof(functions) / sizeof(*functions); i++)
		static_library_replace(library, functions[i].name, functions[i].fun);
}
