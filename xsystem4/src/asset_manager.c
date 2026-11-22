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

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <dirent.h>
#include <limits.h>
#include <assert.h>

#include "system4.h"
#include "system4/ald.h"
#include "system4/afa.h"
#include "system4/cg.h"
#include "system4/file.h"
#include "system4/hashtable.h"
#include "system4/string.h"
#include "system4/utfsjis.h"

#include "xsystem4.h"
#include "asset_manager.h"
#include "savedata.h"
#include "gfx/font.h"

enum archive_type {
	AR_TYPE_ALD,
	AR_TYPE_AFA,
	AR_TYPE_MAX
};

const char *asset_strtype(enum asset_type type)
{
	switch (type) {
	case ASSET_BGM:   return "BGM";
	case ASSET_SOUND: return "Sound";
	case ASSET_VOICE: return "Voice";
	case ASSET_CG:    return "CG";
	case ASSET_FLAT:  return "Flat";
	case ASSET_PACT:  return "Pact";
	case ASSET_DATA:  return "Data";
	case ASSET_FLASH: return "Flash";
	}
	return "Invalid";
}

struct asset_manager {
	bool (*load_archive)(struct asset_manager *manager, const char *name);
	bool (*exists_by_id)(struct asset_manager *manager, int id);
	bool (*exists_by_name)(struct asset_manager *manager, const char *name, int *id_out);
	struct archive_data *(*get_by_id)(struct asset_manager *manager, int id);
	struct archive_data *(*get_by_name)(struct asset_manager *manager, const char *name, int *id_out);
};

// XXX: also used for AFAv1 in Daiteikoku, Shaman's Sanctuary
struct asset_manager_ald {
	struct asset_manager manager;
	struct archive *archive;
};

enum afa_archive_priority {
	AFA_PRIO_BACKUP = 0,
	AFA_PRIO_BASE   = 1,
	AFA_PRIO_PATCH  = 2,
	AFA_PRIO_MOD    = 3,
};

struct afa_canon_entry {
	struct afa_archive *ar;
	struct afa_entry *entry;
	int prio;
};

struct asset_manager_afa {
	struct asset_manager manager;
	struct afa_archive **archives;
	uint8_t *archive_priorities;
	size_t nr_archives;
	struct afa_archive **id_archives;
	size_t nr_id_archives;
	struct hash_table *canon_basename_table;
	struct afa_canon_entry *canon_entries;
	size_t nr_canon_entries;
	size_t cap_canon_entries;
	bool canon_index_valid;
	struct hash_table *miss_table;
	size_t nr_miss_entries;
};

static struct asset_manager *assets[ASSET_TYPE_MAX] = {0};
static struct asset_manager_afa *afa_create_manager(void);

bool asset_manager_load_archive(enum asset_type type, const char *archive_name)
{
	if (type < 0 || type >= ASSET_TYPE_MAX) return false;
	if (!assets[type]) assets[type] = &afa_create_manager()->manager;
	if (!assets[type]->load_archive)
		ERROR("load_archive not supported on this archive type");
	char rel_name[PATH_MAX];
	snprintf(rel_name, sizeof(rel_name), "%s.afa", archive_name);
	char *path = gamedir_path_icase(rel_name);
	if (!path || !file_exists(path)) {
		free(path);
		path = gamedir_path(archive_name);
	}
	bool r = assets[type]->load_archive(assets[type], path);
	free(path);
	if (r && type == ASSET_CG) {
		size_t len = strlen(archive_name);
		if (len >= 3 && !strcasecmp(archive_name + len - 3, "CG5")) {
			char cg6_rel[PATH_MAX];
			snprintf(cg6_rel, sizeof(cg6_rel), "%.*s6.afa", (int)(len - 1), archive_name);
			char *cg6_path = gamedir_path_icase(cg6_rel);
			if (cg6_path && file_exists(cg6_path))
				assets[type]->load_archive(assets[type], cg6_path);
			free(cg6_path);
		}
	}
	return r;
}

#define SAVE_CG_ID_TAG 0x7f000000u

static char **save_cg_names = NULL;
static size_t nr_save_cg_names = 0;
static size_t cap_save_cg_names = 0;

static char *asset_cg_save_path(const char *name)
{
	if (!name || strncmp(name, "<save>", 6) != 0)
		return NULL;
	const char *rel = name + 6;
	while (*rel == '/' || *rel == '\\')
		rel++;
	char *resolved = savedir_path(rel);
	if (resolved && !file_exists(resolved)) {
		char *icase = path_get_icase(resolved);
		if (icase) {
			free(resolved);
			resolved = icase;
		}
	}
	return resolved;
}

static bool is_save_cg_id(int id)
{
	return id > 0 && (((uint32_t)id & SAVE_CG_ID_TAG) == SAVE_CG_ID_TAG) && ((uint32_t)id & 0x00ffffffu) != 0;
}

static int save_cg_intern_name(const char *name)
{
	for (size_t i = 0; i < nr_save_cg_names; i++) {
		if (!strcmp(save_cg_names[i], name))
			return (int)(SAVE_CG_ID_TAG | (uint32_t)(i + 1));
	}
	if (nr_save_cg_names >= 0x00fffffeu)
		return 0;
	if (nr_save_cg_names == cap_save_cg_names) {
		size_t new_cap = cap_save_cg_names ? cap_save_cg_names * 2 : 32;
		save_cg_names = xrealloc(save_cg_names, new_cap * sizeof(*save_cg_names));
		cap_save_cg_names = new_cap;
	}
	save_cg_names[nr_save_cg_names] = xstrdup(name);
	nr_save_cg_names++;
	return (int)(SAVE_CG_ID_TAG | (uint32_t)nr_save_cg_names);
}

static char *save_cg_resolve_id(int id)
{
	if (!is_save_cg_id(id))
		return NULL;
	size_t idx = (size_t)((uint32_t)id & 0x00ffffffu) - 1;
	if (idx >= nr_save_cg_names)
		return NULL;
	return asset_cg_save_path(save_cg_names[idx]);
}

bool asset_exists(enum asset_type type, int id)
{
	if (type < 0 || type >= ASSET_TYPE_MAX)
		return false;
	if (type == ASSET_CG && is_save_cg_id(id)) {
		char *save_path = save_cg_resolve_id(id);
		bool exists = save_path && file_exists(save_path);
		free(save_path);
		return exists;
	}
	if (!assets[type])
		return false;
	return assets[type]->exists_by_id(assets[type], id);
}

static bool asset_cg_format_special_org_fallback(const char *name, char *buf, size_t buf_size)
{
	const char *needle = sys4_text_is_gbk
		? "\xcc\xd8\xca\xe2\xcb\xf9\xca\xf4"  /* 特殊所属 (GBK) */
		: "\x93\xc1\x8e\xea\x8f\x8a\x91\xae"; /* 特殊所属 (SJIS) */
	const char *repl = sys4_text_is_gbk
		? "\xc6\xe4\xcb\xfb\xcb\xf9\xca\xf4"          /* 其他所属 (GBK) */
		: "\x82\xbb\x82\xcc\x91\xbc\x8f\x8a\x91\xae"; /* その他所属 (SJIS) */
	size_t needle_len = 8;
	size_t repl_len = sys4_text_is_gbk ? 8 : 10;

	for (const char *p = name; *p; p = sjis_skip_char(p)) {
		if (!memcmp(p, needle, needle_len)) {
			size_t prefix_len = (size_t)(p - name);
			const char *suffix = p + needle_len;
			size_t suffix_len = strlen(suffix);
			if (prefix_len + repl_len + suffix_len + 1 > buf_size)
				return false;
			memcpy(buf, name, prefix_len);
			memcpy(buf + prefix_len, repl, repl_len);
			memcpy(buf + prefix_len + repl_len, suffix, suffix_len + 1);
			return true;
		}
	}
	return false;
}

bool asset_exists_by_name(enum asset_type type, const char *name, int *id_out)
{
	if (type < 0 || type >= ASSET_TYPE_MAX || !name || !name[0])
		return false;
	if (type == ASSET_CG) {
		char *save_path = asset_cg_save_path(name);
		if (save_path) {
			bool exists = file_exists(save_path);
			free(save_path);
			if (exists && id_out)
				*id_out = save_cg_intern_name(name);
			return exists;
		}
	}
	if (!assets[type])
		return false;
	if (!assets[type]->exists_by_name)
		ERROR("exists_by_name not supported on this archive type");
	if (assets[type]->exists_by_name(assets[type], name, id_out))
		return true;
	char fallback[512];
	if (type == ASSET_CG && asset_cg_format_special_org_fallback(name, fallback, sizeof(fallback)))
		return assets[type]->exists_by_name(assets[type], fallback, id_out);
	return false;
}

struct archive_data *asset_get(enum asset_type type, int id)
{
	if (type < 0 || type >= ASSET_TYPE_MAX || !assets[type])
		return NULL;
	return assets[type]->get_by_id(assets[type], id);
}

struct archive_data *asset_get_by_name(enum asset_type type, const char *name, int *id_out)
{
	if (type < 0 || type >= ASSET_TYPE_MAX || !assets[type] || !name || !name[0])
		return NULL;
	if (!assets[type]->get_by_name)
		ERROR("get_by_name not supported on this archive type");
	struct archive_data *data = assets[type]->get_by_name(assets[type], name, id_out);
	if (data)
		return data;
	char fallback[512];
	if (type == ASSET_CG && asset_cg_format_special_org_fallback(name, fallback, sizeof(fallback)))
		return assets[type]->get_by_name(assets[type], fallback, id_out);
	return NULL;
}

struct cg *asset_cg_load(int id)
{
	if (is_save_cg_id(id)) {
		char *save_path = save_cg_resolve_id(id);
		struct cg *cg = (save_path && file_exists(save_path)) ? cg_load_file(save_path) : NULL;
		free(save_path);
		return cg;
	}
	struct archive_data *data = asset_get(ASSET_CG, id);
	if (!data)
		return NULL;
	struct cg *cg = cg_load_data(data);
	archive_free_data(data);
	return cg;
}

struct cg *asset_cg_load_by_name(const char *name, int *id_out)
{
	char *save_path = asset_cg_save_path(name);
	if (save_path) {
		struct cg *cg = file_exists(save_path) ? cg_load_file(save_path) : NULL;
		free(save_path);
		if (cg && id_out)
			*id_out = save_cg_intern_name(name);
		return cg;
	}
	struct archive_data *data = asset_get_by_name(ASSET_CG, name, id_out);
	if (!data)
		return NULL;
	struct cg *cg = cg_load_data(data);
	archive_free_data(data);
	return cg;
}

bool asset_cg_get_metrics(int id, struct cg_metrics *metrics)
{
	if (is_save_cg_id(id)) {
		char *save_path = save_cg_resolve_id(id);
		bool r = (save_path && file_exists(save_path)) ? cg_get_metrics_file(save_path, metrics) : false;
		free(save_path);
		return r;
	}
	struct archive_data *data = asset_get(ASSET_CG, id);
	if (!data)
		return false;
	bool r = cg_get_metrics_data(data, metrics);
	archive_free_data(data);
	return r;
}

bool asset_cg_get_metrics_by_name(const char *name, struct cg_metrics *metrics)
{
	char *save_path = asset_cg_save_path(name);
	if (save_path) {
		bool r = file_exists(save_path) ? cg_get_metrics_file(save_path, metrics) : false;
		free(save_path);
		return r;
	}
	struct archive_data *data = asset_get_by_name(ASSET_CG, name, NULL);
	if (!data)
		return false;
	bool r = cg_get_metrics_data(data, metrics);
	archive_free_data(data);
	return r;
}

static bool ald_exists_by_id(struct asset_manager *_manager, int id)
{
	struct asset_manager_ald *manager = (struct asset_manager_ald*)_manager;
	return archive_exists(manager->archive, id - 1);
}

static struct archive_data *ald_get_by_id(struct asset_manager *_manager, int id)
{
	struct asset_manager_ald *manager = (struct asset_manager_ald*)_manager;
	return archive_get(manager->archive, id - 1);
}

static bool afa_is_mod_archive(const char *filepath)
{
	const char *base = strrchr(filepath, '/');
	base = base ? base + 1 : filepath;
	for (const char *p = base; p[0] && p[1] && p[2]; p++) {
		if (tolower((unsigned char)p[0]) == 'm' &&
		    tolower((unsigned char)p[1]) == 'o' &&
		    tolower((unsigned char)p[2]) == 'd')
			return true;
	}
	return false;
}

static bool afa_is_backup_archive(const char *filepath)
{
	size_t len = strlen(filepath);
	return len >= 12 && !strcasecmp(filepath + len - 12, ".vanilla.bak");
}

static int afa_default_priority(const char *filepath)
{
	if (afa_is_mod_archive(filepath))
		return AFA_PRIO_MOD;
	if (afa_is_backup_archive(filepath))
		return AFA_PRIO_BACKUP;
	return AFA_PRIO_BASE;
}

static size_t afa_find_insert_pos(struct asset_manager_afa *manager, int prio)
{
	size_t pos = 0;
	while (pos < manager->nr_archives && manager->archive_priorities[pos] > prio)
		pos++;
	return pos;
}

static void afa_invalidate_canon_index(struct asset_manager_afa *manager)
{
	if (manager->canon_basename_table) {
		ht_free(manager->canon_basename_table);
		manager->canon_basename_table = NULL;
	}
	if (manager->miss_table) {
		ht_free(manager->miss_table);
		manager->miss_table = NULL;
	}
	manager->nr_miss_entries = 0;
	free(manager->canon_entries);
	manager->canon_entries = NULL;
	manager->nr_canon_entries = 0;
	manager->cap_canon_entries = 0;
	manager->canon_index_valid = false;
}

static size_t afa_register_id_archive(struct asset_manager_afa *manager, struct afa_archive *ar)
{
	for (size_t i = 0; i < manager->nr_id_archives; i++) {
		if (manager->id_archives[i] == ar)
			return i;
	}
	manager->id_archives = xrealloc(manager->id_archives,
		(manager->nr_id_archives + 1) * sizeof(*manager->id_archives));
	manager->id_archives[manager->nr_id_archives] = ar;
	return manager->nr_id_archives++;
}

static bool afa_load_archive_prio(struct asset_manager_afa *manager, const char *name, int prio)
{
	char path[PATH_MAX];
	size_t name_len = strlen(name);
	if (name_len >= 4 && !strcasecmp(name + name_len - 4, ".afa"))
		snprintf(path, PATH_MAX, "%s", name);
	else
		snprintf(path, PATH_MAX, "%s.afa", name);
	for (size_t i = 0; i < manager->nr_archives; i++) {
		if (!strcasecmp(manager->archives[i]->filename, path))
			return true;
	}

	int error;
	struct afa_archive *ar = afa_open(path, MMAP_IF_64BIT, &error);
	if (!ar) {
		snprintf(path, PATH_MAX, "%s.AFA", name);
		ar = afa_open(path, MMAP_IF_64BIT, &error);
		if (!ar) {
			WARNING("Failed to open archive: %s", display_utf0(path));
			return false;
		}
	}

	if (prio < 0)
		prio = afa_default_priority(path);
	size_t pos = afa_find_insert_pos(manager, prio);
	manager->archives = xrealloc(manager->archives,
		(manager->nr_archives + 1) * sizeof(*manager->archives));
	manager->archive_priorities = xrealloc(manager->archive_priorities,
		(manager->nr_archives + 1) * sizeof(*manager->archive_priorities));
	memmove(manager->archives + pos + 1, manager->archives + pos,
		(manager->nr_archives - pos) * sizeof(*manager->archives));
	memmove(manager->archive_priorities + pos + 1, manager->archive_priorities + pos,
		(manager->nr_archives - pos) * sizeof(*manager->archive_priorities));
	manager->archives[pos] = ar;
	manager->archive_priorities[pos] = (uint8_t)prio;
	manager->nr_archives++;
	afa_register_id_archive(manager, ar);
	afa_invalidate_canon_index(manager);
	return true;
}

static bool afa_load_archive(struct asset_manager *_manager, const char *name)
{
	return afa_load_archive_prio((struct asset_manager_afa*)_manager, name, -1);
}

static int afa_encode_id(struct asset_manager_afa *manager, struct afa_archive *ar, int local_no)
{
	if (local_no < 0 || (uint32_t)local_no >= 0x00fffffeu)
		return 0;
	size_t idx = afa_register_id_archive(manager, ar);
	// 1..126 identify id_archives[ar_tag - 1]; 0 means priority-order scan; 127 is reserved for <save> CGs.
	uint32_t ar_tag = (idx < 126) ? (uint32_t)(idx + 1) : 0u;
	return (int)((ar_tag << 24) | ((uint32_t)(local_no + 1) & 0x00ffffffu));
}

static bool afa_exists_by_id(struct asset_manager *_manager, int id)
{
	struct asset_manager_afa *manager = (struct asset_manager_afa*)_manager;
	if (id <= 0)
		return false;
	uint32_t uid = (uint32_t)id;
	size_t ar_tag = (uid >> 24) & 0x7fu;
	int local_id = (int)(uid & 0x00ffffffu);
	if (local_id <= 0)
		return false;
	if (ar_tag == 0) {
		for (size_t i = 0; i < manager->nr_archives; i++) {
			if (archive_exists(&manager->archives[i]->ar, local_id - 1))
				return true;
		}
		return false;
	}
	size_t idx = ar_tag - 1;
	if (idx >= manager->nr_id_archives)
		return false;
	return archive_exists(&manager->id_archives[idx]->ar, local_id - 1);
}

static struct archive_data *afa_get_by_id(struct asset_manager *_manager, int id)
{
	struct asset_manager_afa *manager = (struct asset_manager_afa*)_manager;
	if (id <= 0)
		return NULL;
	uint32_t uid = (uint32_t)id;
	size_t ar_tag = (uid >> 24) & 0x7fu;
	int local_id = (int)(uid & 0x00ffffffu);
	if (local_id <= 0)
		return NULL;
	if (ar_tag == 0) {
		for (size_t i = 0; i < manager->nr_archives; i++) {
			struct archive_data *data = archive_get(&manager->archives[i]->ar, local_id - 1);
			if (data)
				return data;
		}
		return NULL;
	}
	size_t idx = ar_tag - 1;
	if (idx >= manager->nr_id_archives)
		return NULL;
	return archive_get(&manager->id_archives[idx]->ar, local_id - 1);
}

static uint64_t afa_entry_timestamp(const struct afa_entry *e)
{
	if (!e->unknown0 && !e->unknown1)
		return 0;
	return ((uint64_t)(e->unknown1 & 0x7fffffffu) << 32) | (uint64_t)e->unknown0;
}

static bool name_has_non_ascii(const char *s)
{
	for (const unsigned char *p = (const unsigned char *)s; p && *p; p++) {
		if (*p >= 0x80)
			return true;
	}
	return false;
}

static struct afa_entry *afa_find_best_raw(struct asset_manager_afa *manager,
		const char *name, struct afa_archive **ar_out)
{
	struct afa_archive *best_ar = NULL;
	struct afa_entry *best_entry = NULL;
	int best_prio = -1;
	uint64_t best_ts = 0;

	for (size_t i = 0; i < manager->nr_archives; i++) {
		struct afa_archive *ar = manager->archives[i];
		int prio = manager->archive_priorities[i];
		if (best_entry && prio < best_prio)
			break;

		struct afa_entry *e = afa_lookup_by_basename(ar, name);
		if (!e)
			continue;

		if (prio >= AFA_PRIO_MOD) {
			*ar_out = ar;
			return e;
		}

		uint64_t ts = afa_entry_timestamp(e);
		if (!best_entry || prio > best_prio) {
			best_ar = ar;
			best_entry = e;
			best_prio = prio;
			best_ts = ts;
			if (ts == 0) {
				*ar_out = ar;
				return e;
			}
			continue;
		}

		if (ts == 0)
			continue;
		if (ts > best_ts) {
			best_ar = ar;
			best_entry = e;
			best_ts = ts;
		}
	}

	if (best_entry)
		*ar_out = best_ar;
	return best_entry;
}

static bool afa_candidate_beats(int cur_prio, struct afa_entry *cur_e,
		int cand_prio, struct afa_entry *cand_e)
{
	if (!cur_e)
		return true;
	if (cur_prio != cand_prio)
		return cand_prio > cur_prio;
	if (cur_prio >= AFA_PRIO_MOD)
		return false;

	uint64_t cur_ts = afa_entry_timestamp(cur_e);
	uint64_t cand_ts = afa_entry_timestamp(cand_e);
	if (cur_ts == 0)
		return false;
	if (cand_ts == 0)
		return true;
	return cand_ts > cur_ts;
}

static void afa_canon_index_insert(struct asset_manager_afa *manager, const char *key,
		struct afa_archive *ar, struct afa_entry *entry, int prio)
{
	struct ht_slot *slot = ht_put(manager->canon_basename_table, key, NULL);
	if (!slot->value) {
		if (manager->nr_canon_entries == manager->cap_canon_entries) {
			size_t new_cap = manager->cap_canon_entries ? manager->cap_canon_entries * 2 : 256;
			manager->canon_entries = xrealloc(manager->canon_entries,
					new_cap * sizeof(*manager->canon_entries));
			manager->cap_canon_entries = new_cap;
		}
		size_t idx = manager->nr_canon_entries++;
		manager->canon_entries[idx].ar = ar;
		manager->canon_entries[idx].entry = entry;
		manager->canon_entries[idx].prio = prio;
		slot->value = (void *)(uintptr_t)(idx + 1);
		return;
	}
	size_t idx = (size_t)(uintptr_t)slot->value - 1;
	if (afa_candidate_beats(manager->canon_entries[idx].prio, manager->canon_entries[idx].entry, prio, entry)) {
		manager->canon_entries[idx].ar = ar;
		manager->canon_entries[idx].entry = entry;
		manager->canon_entries[idx].prio = prio;
	}
}

static void afa_ensure_canon_index(struct asset_manager_afa *manager)
{
	if (manager->canon_index_valid)
		return;
	afa_invalidate_canon_index(manager);

	size_t total_files = 0;
	for (size_t i = 0; i < manager->nr_archives; i++)
		total_files += manager->archives[i]->nr_files;
	manager->canon_basename_table = ht_create(max((size_t)64, total_files * 2));

	for (size_t i = 0; i < manager->nr_archives; i++) {
		struct afa_archive *ar = manager->archives[i];
		int prio = manager->archive_priorities[i];
		for (uint32_t j = 0; j < ar->nr_files; j++) {
			const char *ename = ar->files[j].name->text;
			if (!name_has_non_ascii(ename))
				continue;
			char base_buf[512];
			char sjis_buf[1536];
			char gbk_buf[1536];
			char *ebase = archive_basename_buf(ename, base_buf, sizeof(base_buf));
			char *sjis_form = NULL;
			char *gbk_form = NULL;
			save_cjk_canon_forms_buf(ebase, sjis_buf, sizeof(sjis_buf), &sjis_form,
					gbk_buf, sizeof(gbk_buf), &gbk_form);
			if (ebase != base_buf)
				free(ebase);
			if (sjis_form)
				afa_canon_index_insert(manager, sjis_form, ar, &ar->files[j], prio);
			if (gbk_form && (!sjis_form || strcmp(sjis_form, gbk_form) != 0))
				afa_canon_index_insert(manager, gbk_form, ar, &ar->files[j], prio);
			if (sjis_form != sjis_buf)
				free(sjis_form);
			if (gbk_form != gbk_buf)
				free(gbk_form);
		}
	}
	manager->canon_index_valid = true;
}

static struct afa_entry *afa_find_best_by_basename(struct asset_manager_afa *manager,
		const char *name, struct afa_archive **ar_out)
{
	struct afa_entry *best_entry = afa_find_best_raw(manager, name, ar_out);
	if (best_entry || !name || !name_has_non_ascii(name))
		return best_entry;

	if (manager->miss_table && ht_get(manager->miss_table, name, NULL))
		return NULL;

	// Normalize GBK fullwidth digits/punctuation (e.g. %D/%F spliced into SJIS paths)
	// back to Shift-JIS before canonical/opposite-encoding fallback.
	char norm_buf[512];
	char *norm_alloc = NULL;
	const char *lookup_name = name;
	if (sys4_text_is_gbk) {
		bool norm_changed = false;
		norm_alloc = cjk_gbk_zenkaku_to_sjis_buf(name, strlen(name), norm_buf, sizeof(norm_buf), &norm_changed);
		if (norm_changed) {
			best_entry = afa_find_best_raw(manager, norm_alloc, ar_out);
			if (best_entry) {
				if (norm_alloc != norm_buf)
					free(norm_alloc);
				return best_entry;
			}
			lookup_name = norm_alloc;
		} else {
			if (norm_alloc != norm_buf)
				free(norm_alloc);
			norm_alloc = NULL;
		}
	}

	// Lazily indexed O(1) canonical Shinjitai/Simplified fallback across non-ASCII entries.
	afa_ensure_canon_index(manager);

	char base_buf[512];
	char *target_base = archive_basename_buf(lookup_name, base_buf, sizeof(base_buf));
	char q_sjis_buf[1536];
	char q_gbk_buf[1536];
	char *q_sjis = NULL;
	char *q_gbk = NULL;
	save_cjk_canon_forms_buf(target_base, q_sjis_buf, sizeof(q_sjis_buf), &q_sjis,
			q_gbk_buf, sizeof(q_gbk_buf), &q_gbk);
	if (target_base != base_buf)
		free(target_base);

	struct afa_archive *best_ar = NULL;
	int best_prio = -1;
	best_entry = NULL;
	if (manager->nr_canon_entries > 0) {
		const char *queries[2] = { q_sjis, q_gbk };
		for (int k = 0; k < 2; k++) {
			if (!queries[k])
				continue;
			if (k == 1 && q_sjis && !strcmp(q_sjis, q_gbk))
				continue;
			void *val = ht_get(manager->canon_basename_table, queries[k], NULL);
			if (!val)
				continue;
			size_t idx = (size_t)(uintptr_t)val - 1;
			struct afa_canon_entry *ce = &manager->canon_entries[idx];
			if (afa_candidate_beats(best_prio, best_entry, ce->prio, ce->entry)) {
				best_ar = ce->ar;
				best_entry = ce->entry;
				best_prio = ce->prio;
			}
		}
	}
	if (best_entry) {
		*ar_out = best_ar;
		goto out_free;
	}

	// Strict opposite-encoding fallback for halfwidth kana / middot / wave dash pairs
	// not normalized into canon_basename_table; skip directions that failed strict decode.
	size_t lookup_len = strlen(lookup_name);
	bool can_decode[2] = { q_sjis != NULL, q_gbk != NULL };
	for (int dir = 0; dir < 2; dir++) {
		bool src_gbk = (dir == 0) ? !sys4_text_is_gbk : sys4_text_is_gbk;
		if (!can_decode[src_gbk ? 1 : 0])
			continue;
		char *alt = save_transcode_cstr_strict(lookup_name, lookup_len, src_gbk, !src_gbk, NULL);
		if (alt) {
			best_entry = afa_find_best_raw(manager, alt, ar_out);
			free(alt);
			if (best_entry)
				goto out_free;
		}
	}

out_free:
	if (norm_alloc && norm_alloc != norm_buf)
		free(norm_alloc);
	if (q_sjis != q_sjis_buf)
		free(q_sjis);
	if (q_gbk != q_gbk_buf)
		free(q_gbk);
	if (!best_entry) {
		if (!manager->miss_table)
			manager->miss_table = ht_create(1024);
		if (manager->nr_miss_entries < 65536) {
			struct ht_slot *ms = ht_put(manager->miss_table, name, NULL);
			if (!ms->value) {
				ms->value = (void *)1;
				manager->nr_miss_entries++;
			}
		}
	}
	return best_entry;
}

static struct archive_data *afa_get_by_name(struct asset_manager *_manager, const char *name,
		int *id_out)
{
	struct asset_manager_afa *manager = (struct asset_manager_afa*)_manager;
	struct afa_archive *ar = NULL;
	struct afa_entry *entry = afa_find_best_by_basename(manager, name, &ar);
	if (!entry)
		return NULL;
	struct archive_data *data = afa_load_entry(ar, entry);
	if (data && id_out)
		*id_out = afa_encode_id(manager, ar, data->no);
	return data;
}

static bool afa_exists_by_name(struct asset_manager *_manager, const char *name, int *id_out)
{
	struct asset_manager_afa *manager = (struct asset_manager_afa*)_manager;
	struct afa_archive *ar = NULL;
	struct afa_entry *entry = afa_find_best_by_basename(manager, name, &ar);
	if (!entry)
		return false;
	if (id_out)
		*id_out = afa_encode_id(manager, ar, entry->no);
	return true;
}

void asset_foreach_file(enum asset_type type,
		void (*callback)(struct archive_data *data, void *user), void *user)
{
	if (type < 0 || type >= ASSET_TYPE_MAX || !assets[type]) return;
	if (assets[type]->get_by_name == afa_get_by_name) {
		struct asset_manager_afa *manager = (struct asset_manager_afa *)assets[type];
		for (size_t i = 0; i < manager->nr_archives; i++)
			archive_for_each(&manager->archives[i]->ar, callback, user);
	} else {
		struct asset_manager_ald *manager = (struct asset_manager_ald *)assets[type];
		archive_for_each(manager->archive, callback, user);
	}
}

static struct asset_manager_afa *afa_create_manager(void)
{
	struct asset_manager_afa *manager = xcalloc(1, sizeof(*manager));
	manager->manager.load_archive = afa_load_archive;
	manager->manager.exists_by_id = afa_exists_by_id;
	manager->manager.get_by_id = afa_get_by_id;
	manager->manager.exists_by_name = afa_exists_by_name;
	manager->manager.get_by_name = afa_get_by_name;
	return manager;
}

static void _ald_init(enum asset_type type, struct archive *ar)
{
	struct asset_manager_ald *manager = xcalloc(1, sizeof(struct asset_manager_ald));
	manager->manager.exists_by_id = ald_exists_by_id;
	manager->manager.get_by_id = ald_get_by_id;
	manager->manager.load_archive = NULL;
	manager->manager.exists_by_name = NULL;
	manager->manager.get_by_name = NULL;
	manager->archive = ar;
	assets[type] = &manager->manager;
}

static void ald_init(enum asset_type type, char **files, int count)
{
	if (count < 1)
		return;
	if (assets[type])
		WARNING("Multiple asset archives for type %s", asset_strtype(type));

	int error;
	struct archive *ar = ald_open(files, count, MMAP_IF_64BIT, &error);
	if (!ar)
		ERROR("Failed to open ALD file: %s", archive_strerror(error));

	_ald_init(type, ar);

	for (int i = 0; i < count; i++) {
		free(files[i]);
	}
}

static void afa_init(enum asset_type type, char *file)
{
	if (!file)
		return;
	if (assets[type])
		WARNING("Multiple asset archives for type %s", asset_strtype(type));

	int error;
	struct afa_archive *ar = afa_open(file, MMAP_IF_64BIT, &error);
	if (!ar)
		ERROR("Failed to open AFA file: %s", archive_strerror(error));

	struct asset_manager_afa *manager = afa_create_manager();
	manager->archives = xmalloc(sizeof(*manager->archives));
	manager->archive_priorities = xmalloc(sizeof(*manager->archive_priorities));
	manager->archives[0] = ar;
	manager->archive_priorities[0] = AFA_PRIO_BASE;
	manager->nr_archives = 1;
	afa_register_id_archive(manager, ar);
	assets[type] = &manager->manager;

	char bak_path[PATH_MAX];
	snprintf(bak_path, sizeof(bak_path), "%s.vanilla.bak", file);
	if (file_exists(bak_path)) {
		struct afa_archive *bak_ar = afa_open(bak_path, MMAP_IF_64BIT, &error);
		if (bak_ar) {
			manager->archives = xrealloc(manager->archives, 2 * sizeof(*manager->archives));
			manager->archive_priorities = xrealloc(manager->archive_priorities, 2 * sizeof(*manager->archive_priorities));
			manager->archives[1] = bak_ar;
			manager->archive_priorities[1] = AFA_PRIO_BACKUP;
			manager->nr_archives = 2;
			afa_register_id_archive(manager, bak_ar);
		}
	}

	free(file);
}

static char *get_base_name(const char *ain_filename)
{
	char *path = (config.ain_basename_utf8 && *config.ain_basename_utf8)
		? xstrdup(config.ain_basename_utf8)
		: sjis_filename2utf(ain_filename, 0);
	char *dot = strrchr(path, '.');
	if (dot)
		*dot = '\0';
	return path;
}

static bool register_ald_file(char *ald_filenames[ASSET_TYPE_MAX][ALD_FILEMAX],
		int ald_count[ASSET_TYPE_MAX], const char *d_name, size_t prefix_len)
{
	int dno = toupper((unsigned char)d_name[prefix_len + 1]) - 'A';
	if (dno < 0 || dno >= ALD_FILEMAX) {
		WARNING("Invalid ALD index: %s", display_utf0(d_name));
		return false;
	}

	switch (d_name[prefix_len]) {
	case 'b':
	case 'B':
		ald_filenames[ASSET_BGM][dno] = path_join(config.game_dir, d_name);
		ald_count[ASSET_BGM] = max(ald_count[ASSET_BGM], dno + 1);
		return true;
	case 'g':
	case 'G':
		ald_filenames[ASSET_CG][dno] = path_join(config.game_dir, d_name);
		ald_count[ASSET_CG] = max(ald_count[ASSET_CG], dno + 1);
		return true;
	case 'w':
	case 'W':
		ald_filenames[ASSET_SOUND][dno] = path_join(config.game_dir, d_name);
		ald_count[ASSET_SOUND] = max(ald_count[ASSET_SOUND], dno + 1);
		return true;
	case 'd':
	case 'D':
		ald_filenames[ASSET_DATA][dno] = path_join(config.game_dir, d_name);
		ald_count[ASSET_DATA] = max(ald_count[ASSET_DATA], dno + 1);
		return true;
	default:
		WARNING("Unhandled ALD file: %s", display_utf0(d_name));
		return false;
	}
}

static size_t fallback_ald_prefix_length(const char *name, const char *kinds)
{
	size_t len = strlen(name);
	if (len <= 6)
		return 0;
	size_t plen = len - 6;
	int disk = toupper((unsigned char)name[plen + 1]);
	if (!strchr(kinds, toupper((unsigned char)name[plen])) || disk < 'A' || disk > 'Z')
		return 0;
	return plen;
}

static void register_fallback_alds(char *ald_filenames[ASSET_TYPE_MAX][ALD_FILEMAX],
		int ald_count[ASSET_TYPE_MAX], char **names, size_t count, const char *kinds,
		bool require_unique)
{
	const char *prefix = NULL;
	size_t prefix_len = 0;
	for (size_t i = 0; i < count; i++) {
		size_t len = fallback_ald_prefix_length(names[i], kinds);
		if (!len)
			continue;
		if (!prefix) {
			prefix = names[i];
			prefix_len = len;
			if (!require_unique)
				break;
		} else if (len != prefix_len || strncasecmp(prefix, names[i], len)) {
			return;
		}
	}
	if (!prefix)
		return;

	for (size_t i = 0; i < count; i++) {
		if (fallback_ald_prefix_length(names[i], kinds) == prefix_len
				&& !strncasecmp(names[i], prefix, prefix_len))
			register_ald_file(ald_filenames, ald_count, names[i], prefix_len);
	}
}

static char *find_ald_audio_metadata(char **volumes, int count, const char *extension)
{
	for (int i = 0; i < count; i++) {
		if (!volumes[i])
			continue;
		char *path = xstrdup(volumes[i]);
		// Metadata is usually named after its volume (BA.bgi or WA.wai).
		char *dot = strrchr(path, '.');
		strcpy(dot + 1, extension);
		char *found = path_get_icase(path);
		if (!found) {
			// Also accept metadata named after the archive family.
			memmove(dot - 2, dot, strlen(dot) + 1);
			found = path_get_icase(path);
		}
		free(path);
		if (found)
			return found;
	}
	return NULL;
}

void asset_manager_init(void)
{
	char *ald_filenames[ASSET_TYPE_MAX][ALD_FILEMAX] = {0};
	int ald_count[ASSET_TYPE_MAX] = {0};
	char *afa_filenames[ASSET_TYPE_MAX] = {0};
	char *afa_chs_cg = NULL;
	char *afa_chs_flat = NULL;

	UDIR *dir;
	char *d_name;

	if (!(dir = opendir_utf8(config.game_dir))) {
		ERROR("Failed to open directory: %s", display_utf0(config.game_dir));
	}

	char *base = get_base_name(config.ain_filename);
	size_t base_len = strlen(base);

	bool ex_is_canonical = false;
	bool has_base_ald = false;
	char **fallback_alds = NULL;
	size_t nr_fallback_alds = 0;

	// get ALD filenames
	while ((d_name = readdir_utf8(dir))) {
		char *repaired = cjk_repair_sjis_as_gbk_utf8(d_name);
		const char *match_name = repaired ? repaired : d_name;
		const char *ext = file_extension(match_name);
		if (!ext)
			goto loop_next;
		// archive filename must normally begin with ain filename; retain *.ald candidates for renamed CN AINs
		// and standalone CHS.afa overlays used by Longinus CN translations (e.g. Oyakorankan).
		if (strncasecmp(base, match_name, base_len)) {
			if (!strcasecmp(ext, "ald")) {
				fallback_alds = xrealloc(fallback_alds, (nr_fallback_alds + 1) * sizeof(*fallback_alds));
				fallback_alds[nr_fallback_alds++] = xstrdup(d_name);
			} else if (!strcasecmp(match_name, "CHS.afa") && !afa_chs_cg) {
				afa_chs_cg = path_join(config.game_dir, d_name);
			}
			goto loop_next;
		}
		if (!strcasecmp(ext, "ald")) {
			size_t mlen = strlen(match_name);
			size_t dlen = strlen(d_name);
			if (mlen > base_len + 1 && dlen >= mlen - base_len) {
				size_t d_prefix_len = dlen - (mlen - base_len);
				if (register_ald_file(ald_filenames, ald_count, d_name, d_prefix_len))
					has_base_ald = true;
			}
		} else if (!strcasecmp(ext, "bgi")) {
			if (config.bgi_path) {
				WARNING("Multiple bgi files");
				goto loop_next;
			}
			config.bgi_path = path_join(config.game_dir, d_name);
		} else if (!strcasecmp(ext, "wai")) {
			if (config.wai_path) {
				WARNING("Multiple wai files");
				goto loop_next;
			}
			config.wai_path = path_join(config.game_dir, d_name);
		} else if (!strcasecmp(ext, "ex")) {
			const char *suffix = match_name + base_len;
			bool canonical = !strcasecmp(suffix, "EX.ex") || !strcasecmp(suffix, ".ex");
			if (config.ex_path) {
				WARNING("Multiple ex files");
				if (!canonical || ex_is_canonical)
					goto loop_next;
				free(config.ex_path);
			}
			config.ex_path = path_join(config.game_dir, d_name);
			ex_is_canonical = canonical;
		} else if (!strcasecmp(ext, "afa")) {
			const char *type = match_name + base_len;
			if (!strcasecmp(type, "BA.afa")) {
				afa_filenames[ASSET_BGM] = path_join(config.game_dir, d_name);
			} else if (!strcasecmp(type, "WA.afa") || !strcasecmp(type, "Sound.afa")) {
				afa_filenames[ASSET_SOUND] = path_join(config.game_dir, d_name);
			} else if (!strcasecmp(type, "Voice.afa")) {
				afa_filenames[ASSET_VOICE] = path_join(config.game_dir, d_name);
			} else if (!strcasecmp(type, "CG.afa")) {
				afa_filenames[ASSET_CG] = path_join(config.game_dir, d_name);
			} else if (!strcasecmp(type, "Flat.afa")) {
				afa_filenames[ASSET_FLAT] = path_join(config.game_dir, d_name);
			} else if (!strcasecmp(type, "Pact.afa")) {
				afa_filenames[ASSET_PACT] = path_join(config.game_dir, d_name);
			} else if (!strcasecmp(type, "AFF.afa")) {
				afa_filenames[ASSET_FLASH] = path_join(config.game_dir, d_name);
			} else if (!strcasecmp(type, "CHS.afa")) {
				free(afa_chs_cg);
				afa_chs_cg = path_join(config.game_dir, d_name);
			} else if (!strcasecmp(type, "CHS2.afa")) {
				free(afa_chs_flat);
				afa_chs_flat = path_join(config.game_dir, d_name);
			}
		} else if (!strcasecmp(ext, "fnl")) {
			if (!config.fnl_path)
				config.fnl_path = path_join(config.game_dir, d_name);
		}
	loop_next:
		free(repaired);
		free(d_name);
	}
	closedir_utf8(dir);
	free(base);

	if (!has_base_ald) {
		register_fallback_alds(ald_filenames, ald_count, fallback_alds, nr_fallback_alds, "BGWD", false);
	} else {
		// A translated script can have matching graphics but keep the original audio.
		if (!ald_count[ASSET_BGM] && !afa_filenames[ASSET_BGM])
			register_fallback_alds(ald_filenames, ald_count, fallback_alds, nr_fallback_alds, "B", true);
		if (!ald_count[ASSET_SOUND] && !afa_filenames[ASSET_SOUND])
			register_fallback_alds(ald_filenames, ald_count, fallback_alds, nr_fallback_alds, "W", true);
	}
	for (size_t i = 0; i < nr_fallback_alds; i++)
		free(fallback_alds[i]);
	free(fallback_alds);

	if (!config.bgi_path && !afa_filenames[ASSET_BGM])
		config.bgi_path = find_ald_audio_metadata(ald_filenames[ASSET_BGM], ald_count[ASSET_BGM], "bgi");
	if (!config.wai_path && !afa_filenames[ASSET_SOUND])
		config.wai_path = find_ald_audio_metadata(ald_filenames[ASSET_SOUND], ald_count[ASSET_SOUND], "wai");

	// open ALD archives
	ald_init(ASSET_BGM, ald_filenames[ASSET_BGM], ald_count[ASSET_BGM]);
	ald_init(ASSET_SOUND, ald_filenames[ASSET_SOUND], ald_count[ASSET_SOUND]);
	ald_init(ASSET_VOICE, ald_filenames[ASSET_VOICE], ald_count[ASSET_VOICE]);
	ald_init(ASSET_CG, ald_filenames[ASSET_CG], ald_count[ASSET_CG]);
	ald_init(ASSET_FLAT, ald_filenames[ASSET_FLAT], ald_count[ASSET_FLAT]);
	ald_init(ASSET_PACT, ald_filenames[ASSET_PACT], ald_count[ASSET_PACT]);
	ald_init(ASSET_DATA, ald_filenames[ASSET_DATA], ald_count[ASSET_DATA]);
	ald_init(ASSET_FLASH, ald_filenames[ASSET_FLASH], ald_count[ASSET_FLASH]);

	// open AFA archives
	afa_init(ASSET_BGM, afa_filenames[ASSET_BGM]);
	afa_init(ASSET_SOUND, afa_filenames[ASSET_SOUND]);
	afa_init(ASSET_VOICE, afa_filenames[ASSET_VOICE]);
	afa_init(ASSET_CG, afa_filenames[ASSET_CG]);
	afa_init(ASSET_FLAT, afa_filenames[ASSET_FLAT]);
	afa_init(ASSET_PACT, afa_filenames[ASSET_PACT]);
	afa_init(ASSET_DATA, afa_filenames[ASSET_DATA]);
	afa_init(ASSET_FLASH, afa_filenames[ASSET_FLASH]);

	// Mount companion CN CG/Flat patch archives (e.g. Evenicle2RanceCHS.afa / CHS2.afa)
	// with patch overlay priority (above base archives, below user mods) so translated
	// UI skins and flats take precedence even when base entries have newer timestamps.
	if (afa_chs_cg) {
		if (!assets[ASSET_CG])
			assets[ASSET_CG] = &afa_create_manager()->manager;
		if (assets[ASSET_CG]->load_archive == afa_load_archive)
			afa_load_archive_prio((struct asset_manager_afa *)assets[ASSET_CG], afa_chs_cg, AFA_PRIO_PATCH);
		free(afa_chs_cg);
	}
	if (afa_chs_flat) {
		if (!assets[ASSET_FLAT])
			assets[ASSET_FLAT] = &afa_create_manager()->manager;
		if (assets[ASSET_FLAT]->load_archive == afa_load_archive)
			afa_load_archive_prio((struct asset_manager_afa *)assets[ASSET_FLAT], afa_chs_flat, AFA_PRIO_PATCH);
		free(afa_chs_flat);
	}
}
