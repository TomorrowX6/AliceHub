/* PACT v6 / parts v34 layouts and activity ownership, GPL-2.0-or-later. */
#include <ctype.h>
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

#include "system4.h"
#include "system4/ain.h"
#include "system4/archive.h"
#include "system4/ex.h"
#include "system4/file.h"
#include "system4/string.h"
#include "system4/utfsjis.h"
#include "asset_manager.h"
#include "mainex_v12.h"
#include "parts.h"
#include "parts_activity.h"
#include "parts_widgets.h"
#include "parts_scrollbar.h"
#include "parts_message_window.h"
#include "vm/heap.h"
#include "vm/page.h"
#include "xsystem4.h"
#include "parts_internal.h"
#include "../hll/iarray.h"

struct activity_part {
	struct string *name;
	int no;
	bool closed, locked;
	struct activity_part *next;
};

struct activity_intent {
	struct string *src;
	int nr_dests;
	struct string **dests;
	int type;
	struct activity_intent *next;
};

struct activity_func {
	struct string *part_name;
	int state;
	struct string *id;
	struct string *func;
	struct activity_func *next;
};

struct activity {
	struct string *name, *background, *ex_text;
	struct activity_part *parts;
	struct activity_intent *intents;
	struct activity_func *funcs;
	int ex_id, nr_keys, *keys;
	struct activity *next;
};

static void free_activity_intents_and_funcs(struct activity *a)
{
	while (a->intents) {
		struct activity_intent *it = a->intents;
		a->intents = it->next;
		free_string(it->src);
		for (int i = 0; i < it->nr_dests; i++)
			free_string(it->dests[i]);
		free(it->dests);
		free(it);
	}
	while (a->funcs) {
		struct activity_func *fn = a->funcs;
		a->funcs = fn->next;
		free_string(fn->part_name);
		free_string(fn->id);
		free_string(fn->func);
		free(fn);
	}
}

static struct activity *activities;

static struct activity *find_activity(struct string *name)
{
	for (struct activity *a = activities; a; a = a->next)
		if (!strcmp(a->name->text, name->text)) return a;
	return NULL;
}

static struct activity_part *find_part(struct activity *a, struct string *name)
{
	if (a) for (struct activity_part *p = a->parts; p; p = p->next)
		if (!strcmp(p->name->text, name->text)) return p;
	return NULL;
}

bool PE_IsExistActivity(struct string *name) { return find_activity(name); }

bool PE_CreateActivity(struct string *name)
{
	if (find_activity(name)) return false;
	struct activity *a = xcalloc(1, sizeof(*a));
	a->name = string_dup(name);
	a->background = string_ref(&EMPTY_STRING);
	a->ex_text = string_ref(&EMPTY_STRING);
	a->ex_id = -1;
	a->next = activities;
	activities = a;
	return true;
}

bool PE_AddActivityParts(struct string *name, struct string *part_name, int no)
{
	struct activity *a = find_activity(name);
	if (!a || find_part(a, part_name)) return false;
	struct activity_part **at = &a->parts;
	while (*at && strcmp((*at)->name->text, part_name->text) < 0) at = &(*at)->next;
	struct activity_part *p = xcalloc(1, sizeof(*p));
	p->name = string_dup(part_name);
	p->no = no;
	p->next = *at;
	*at = p;
	parts_get(no);
	return true;
}

bool PE_RemoveActivityParts(struct string *name, struct string *part_name)
{
	struct activity *a = find_activity(name);
	if (!a) return false;
	for (struct activity_part **at = &a->parts; *at; at = &(*at)->next) {
		struct activity_part *p = *at;
		if (strcmp(p->name->text, part_name->text)) continue;
		*at = p->next;
		free_string(p->name);
		free(p);
		return true;
	}
	return false;
}

bool PE_RemoveAllActivityParts(struct string *name)
{
	struct activity *a = find_activity(name);
	if (!a) return false;
	while (a->parts) PE_RemoveActivityParts(name, a->parts->name);
	return true;
}

bool PE_ReleaseActivity(struct string *name, struct page **erased)
{
	for (struct activity **at = &activities; *at; at = &(*at)->next) {
		struct activity *a = *at;
		if (strcmp(a->name->text, name->text)) continue;
		*at = a->next;
		while (a->parts) {
			struct activity_part *p = a->parts;
			a->parts = p->next;
			struct parts *part = parts_try_get(p->no);
			if (part && erased)
				*erased = array_pushback(*erased, vm_int(part->delegate_index), AIN_ARRAY_INT, -1);
			parts_release(p->no);
			free_string(p->name);
			free(p);
		}
		mainex_v12_unregister_activity(a->ex_id);
		free_activity_intents_and_funcs(a);
		free_string(a->name);
		free_string(a->background);
		free_string(a->ex_text);
		free(a->keys);
		free(a);
		return true;
	}
	return false;
}

void PE_ReleaseActivities(void)
{
	while (activities) PE_ReleaseActivity(activities->name, NULL);
}

int PE_NumofActivityParts(struct string *name)
{
	struct activity *a = find_activity(name);
	int count = 0;
	if (a) for (struct activity_part *p = a->parts; p; p = p->next) count++;
	return count;
}

bool PE_GetActivityParts(int index, struct string *name, struct string **part_name, int *no)
{
	struct activity *a = find_activity(name);
	if (a && index >= 0) for (struct activity_part *p = a->parts; p; p = p->next) {
		if (index--) continue;
		free_string(*part_name);
		*part_name = string_dup(p->name);
		*no = p->no;
		return true;
	}
	return false;
}

bool PE_IsExistActivityPartsByName(struct string *name, struct string *part_name)
{
	return find_part(find_activity(name), part_name);
}

bool PE_IsExistActivityPartsByNumber(struct string *name, int no)
{
	struct activity *a = find_activity(name);
	if (a) for (struct activity_part *p = a->parts; p; p = p->next)
		if (p->no == no) return true;
	return false;
}

int PE_GetActivityPartsNumber(struct string *name, struct string *part_name)
{
	struct activity_part *p = find_part(find_activity(name), part_name);
	return p ? p->no : 0;
}

struct string *PE_GetActivityPartsName(struct string *name, int no)
{
	struct activity *a = find_activity(name);
	if (a) for (struct activity_part *p = a->parts; p; p = p->next)
		if (p->no == no) return string_dup(p->name);
	return string_ref(&EMPTY_STRING);
}

int PE_GetActivityEXID(struct string *name)
{
	struct activity *a = find_activity(name);
	return a ? a->ex_id : -1;
}

void PE_SetActivityEXText(struct string *name, struct string *text)
{
	struct activity *a = find_activity(name);
	if (!a) return;
	if (text->size) WARNING("Nonempty PACT EX text is not yet supported");
	free_string(a->ex_text);
	a->ex_text = string_dup(text);
	mainex_v12_unregister_activity(a->ex_id);
	a->ex_id = mainex_v12_register(xcalloc(1, sizeof(struct ex)));
}

struct string *PE_GetActivityEXText(struct string *name)
{
	struct activity *a = find_activity(name);
	return string_ref(a ? a->ex_text : &EMPTY_STRING);
}

void PE_SetActivityBG(struct string *name, struct string *cg)
{
	struct activity *a = find_activity(name);
	if (!a) return;
	free_string(a->background);
	a->background = string_dup(cg);
}

struct string *PE_GetActivityBG(struct string *name)
{
	struct activity *a = find_activity(name);
	return string_ref(a ? a->background : &EMPTY_STRING);
}

#define PART_FLAG_API(label, member) \
	void PE_AddActivity##label##Parts(struct string *name, struct string *part_name) { \
		struct activity_part *p = find_part(find_activity(name), part_name); \
		if (p) p->member = true; \
	} \
	bool PE_IsExistActivity##label##Parts(struct string *name, struct string *part_name) { \
		struct activity_part *p = find_part(find_activity(name), part_name); \
		return p && p->member; \
	}
PART_FLAG_API(Close, closed)
PART_FLAG_API(Locked, locked)

void PE_RemoveActivityCloseParts(struct string *name, struct string *part_name)
{
	struct activity_part *p = find_part(find_activity(name), part_name);
	if (p) p->closed = false;
}

void PE_RemoveAllActivityCloseParts(struct string *name)
{
	struct activity *a = find_activity(name);
	if (a) for (struct activity_part *p = a->parts; p; p = p->next) p->closed = false;
}

static struct activity_intent *find_intent(struct activity *a, struct string *part_name)
{
	if (a) for (struct activity_intent *it = a->intents; it; it = it->next)
		if (!strcmp(it->src->text, part_name->text)) return it;
	return NULL;
}

void PE_SetActivityIntentData(struct string *name, struct string *part_name, struct string *dest, int type)
{
	struct activity *a = find_activity(name);
	if (!a) return;
	struct activity_intent *it = find_intent(a, part_name);
	if (!it) {
		struct activity_intent **at = &a->intents;
		while (*at) at = &(*at)->next;
		it = xcalloc(1, sizeof(*it));
		it->src = string_dup(part_name);
		*at = it;
	} else {
		for (int i = 0; i < it->nr_dests; i++) free_string(it->dests[i]);
		free(it->dests);
		it->dests = NULL;
		it->nr_dests = 0;
	}
	it->type = type;
	it->dests = xmalloc(sizeof(*it->dests));
	it->dests[0] = string_dup(dest);
	it->nr_dests = 1;
}

void PE_AddActivityIntentDataDestination(struct string *name, struct string *part_name, struct string *dest)
{
	struct activity *a = find_activity(name);
	if (!a) return;
	struct activity_intent *it = find_intent(a, part_name);
	if (!it) {
		PE_SetActivityIntentData(name, part_name, dest, 0);
		return;
	}
	it->dests = xrealloc(it->dests, (size_t)(it->nr_dests + 1) * sizeof(*it->dests));
	it->dests[it->nr_dests++] = string_dup(dest);
}

bool PE_IsExistActivityIntentData(struct string *name, struct string *part_name)
{
	return find_intent(find_activity(name), part_name) != NULL;
}

int PE_NumofActivityIntentData(struct string *name)
{
	struct activity *a = find_activity(name);
	int count = 0;
	if (a) for (struct activity_intent *it = a->intents; it; it = it->next) count++;
	return count;
}

int PE_NumofActivityIntentDataDestination(struct string *name, struct string *part_name)
{
	struct activity_intent *it = find_intent(find_activity(name), part_name);
	return it ? it->nr_dests : 0;
}

struct string *PE_GetActivityIntentDataSource(struct string *name, int index)
{
	struct activity *a = find_activity(name);
	if (a && index >= 0) for (struct activity_intent *it = a->intents; it; it = it->next)
		if (!index--) return string_dup(it->src);
	return string_ref(&EMPTY_STRING);
}

struct string *PE_GetActivityIntentDataDestination(int index, struct string *name, struct string *part_name)
{
	struct activity_intent *it = find_intent(find_activity(name), part_name);
	if (it && index >= 0 && index < it->nr_dests)
		return string_dup(it->dests[index]);
	return string_ref(&EMPTY_STRING);
}

int PE_GetActivityIntentDataType(struct string *name, struct string *part_name)
{
	struct activity_intent *it = find_intent(find_activity(name), part_name);
	return it ? it->type : 0;
}

void PE_SetActivityFunc(struct string *name, struct string *part_name, int state,
		struct string *fmap_id, struct string *func_name)
{
	struct activity *a = find_activity(name);
	if (!a) return;
	for (struct activity_func *fn = a->funcs; fn; fn = fn->next) {
		if (fn->state == state && !strcmp(fn->part_name->text, part_name->text) &&
		    !strcmp(fn->id->text, fmap_id->text)) {
			free_string(fn->func);
			fn->func = string_dup(func_name);
			return;
		}
	}
	struct activity_func **at = &a->funcs;
	while (*at) at = &(*at)->next;
	struct activity_func *fn = xcalloc(1, sizeof(*fn));
	fn->part_name = string_dup(part_name);
	fn->state = state;
	fn->id = string_dup(fmap_id);
	fn->func = string_dup(func_name);
	*at = fn;
}

struct string *PE_GetActivityFunc(struct string *name, struct string *part_name, int state, struct string *fmap_id)
{
	struct activity *a = find_activity(name);
	if (a) for (struct activity_func *fn = a->funcs; fn; fn = fn->next) {
		if (fn->state == state && !strcmp(fn->part_name->text, part_name->text) &&
		    !strcmp(fn->id->text, fmap_id->text))
			return string_dup(fn->func);
	}
	return string_ref(&EMPTY_STRING);
}

int PE_NumofActivityFunc(struct string *name, struct string *part_name, int state)
{
	struct activity *a = find_activity(name);
	int count = 0;
	if (a) for (struct activity_func *fn = a->funcs; fn; fn = fn->next)
		if (fn->state == state && !strcmp(fn->part_name->text, part_name->text))
			count++;
	return count;
}

void PE_GetActivityFuncPair(int index, struct string *name, struct string *part_name, int state,
		struct string **fmap_id, struct string **func_name)
{
	struct activity *a = find_activity(name);
	if (a && index >= 0) for (struct activity_func *fn = a->funcs; fn; fn = fn->next) {
		if (fn->state != state || strcmp(fn->part_name->text, part_name->text))
			continue;
		if (index--)
			continue;
		if (*fmap_id) free_string(*fmap_id);
		if (*func_name) free_string(*func_name);
		*fmap_id = string_dup(fn->id);
		*func_name = string_dup(fn->func);
		return;
	}
	if (*fmap_id) free_string(*fmap_id);
	if (*func_name) free_string(*func_name);
	*fmap_id = string_ref(&EMPTY_STRING);
	*func_name = string_ref(&EMPTY_STRING);
}

bool PE_IsExistActivityEndKey(struct string *name, int key)
{
	struct activity *a = find_activity(name);
	if (a) for (int i = 0; i < a->nr_keys; i++) if (a->keys[i] == key) return true;
	return false;
}

void PE_SetActivityEndKey(struct string *name, int key)
{
	struct activity *a = find_activity(name);
	if (!a || PE_IsExistActivityEndKey(name, key)) return;
	a->keys = xrealloc(a->keys, (a->nr_keys + 1) * sizeof(*a->keys));
	int i = a->nr_keys;
	while (i && a->keys[i - 1] > key) { a->keys[i] = a->keys[i - 1]; i--; }
	a->keys[i] = key;
	a->nr_keys++;
}

void PE_EraseActivityEndKey(struct string *name, int key)
{
	struct activity *a = find_activity(name);
	if (a) for (int i = 0; i < a->nr_keys; i++) if (a->keys[i] == key) {
		memmove(a->keys + i, a->keys + i + 1, (--a->nr_keys - i) * sizeof(*a->keys));
		return;
	}
}

int PE_NumofActivityEndKey(struct string *name)
{
	struct activity *a = find_activity(name);
	return a ? a->nr_keys : 0;
}

int PE_GetActivityEndKey(struct string *name, int index)
{
	struct activity *a = find_activity(name);
	return a && index >= 0 && index < a->nr_keys ? a->keys[index] : 0;
}

void PE_SetSpecialComponentType(int no, int type)
{
	if (type == 2 || type == 3) {
		parts_scrollbar_create(parts_get(no), type == 3);
		return;
	}
	if (type == 1) {
		parts_checkbox_create(parts_get(no));
		return;
	}
	if (type == 12) {
		parts_panel_create(parts_get(no));
		return;
	}
	if (type == 10) {
		parts_message_window_create(parts_get(no));
		return;
	}
	if (type == 0) {
		parts_widget_create(parts_get(no));
		return;
	}
	if (type < 0 || type > 16 || type == 8) VM_ERROR("Unsupported special component %d", type);
	struct parts *p = parts_get(no);
	parts_widget_free(p);
	parts_panel_free(p);
	parts_scrollbar_free(p);
	parts_message_window_free(p);
	if (p->component_type != type) {
		PE_ClearRadioButtonBoxChild(no);
		for (int i = 0; i < PARTS_NR_STATES; i++) parts_state_reset(&p->states[i], PARTS_UNINITIALIZED);
	}
	p->component_type = type;
}

int PE_GetSpecialComponentType(int no)
{
	struct parts *p = parts_try_get(no);
	return p && (p->widget || p->component_type) ? p->component_type : -1;
}

void PE_SetUserComponentName(int no, struct string *name)
{
	struct parts *p = parts_get(no);
	free_string(p->user_component_name);
	p->user_component_name = string_dup(name);
}

struct string *PE_GetUserComponentName(int no)
{
	return string_ref(parts_get(no)->user_component_name);
}

void PE_SetUserComponentData(int no, struct string *key, struct string *value)
{
	struct parts *p = parts_get(no);
	for (struct parts_user_value *v = p->user_values; v; v = v->next) {
		if (strcmp(v->key->text, key->text)) continue;
		free_string(v->value);
		v->value = string_dup(value);
		return;
	}
	struct parts_user_value *v = xcalloc(1, sizeof(*v));
	v->key = string_dup(key);
	v->value = string_dup(value);
	v->next = p->user_values;
	p->user_values = v;
}

struct string *PE_GetUserComponentData(int no, struct string *key)
{
	for (struct parts_user_value *v = parts_get(no)->user_values; v; v = v->next)
		if (!strcmp(v->key->text, key->text)) return string_ref(v->value);
	return string_ref(&EMPTY_STRING);
}

// Layout keys in the Chinese edition are GBK, including the Japanese field names.
static struct ex_tree *node(struct ex_tree *tree, const char *key)
{
	if (!tree || tree->is_leaf) return NULL;
	char *encoded = utf2sjis(key, strlen(key));
	struct ex_tree *result = ex_tree_get_child(tree, encoded);
	free(encoded);
	return result;
}

static struct ex_tree *node2(struct ex_tree *tree, const char *k1, const char *k2)
{
	struct ex_tree *n = node(tree, k1);
	return n ? n : node(tree, k2);
}

static struct ex_value *field(struct ex_tree *tree, const char *key)
{
	struct ex_tree *n = node(tree, key);
	return n && n->is_leaf ? &n->leaf.value : NULL;
}

static struct ex_value *field2(struct ex_tree *tree, const char *k1, const char *k2)
{
	struct ex_value *v = field(tree, k1);
	return v ? v : field(tree, k2);
}

static struct string *str(struct ex_tree *tree, const char *key)
{
	struct ex_value *v = field(tree, key);
	return v && v->type == EX_STRING ? v->s : &EMPTY_STRING;
}

static struct string *str2(struct ex_tree *tree, const char *k1, const char *k2)
{
	struct ex_value *v = field2(tree, k1, k2);
	return v && v->type == EX_STRING ? v->s : &EMPTY_STRING;
}

static float number(struct ex_tree *tree, const char *key, float fallback)
{
	struct ex_value *v = field(tree, key);
	return !v ? fallback : v->type == EX_INT ? v->i : v->type == EX_FLOAT ? v->f : fallback;
}

static float number2(struct ex_tree *tree, const char *k1, const char *k2, float fallback)
{
	struct ex_value *v = field2(tree, k1, k2);
	return !v ? fallback : v->type == EX_INT ? v->i : v->type == EX_FLOAT ? v->f : fallback;
}

static float item(struct ex_tree *tree, const char *key, int index, float fallback)
{
	struct ex_value *v = field(tree, key);
	v = v && v->type == EX_LIST ? ex_list_get(v->list, index) : NULL;
	return !v ? fallback : v->type == EX_INT ? v->i : v->type == EX_FLOAT ? v->f : fallback;
}

static float item2(struct ex_tree *tree, const char *k1, const char *k2, int index, float fallback)
{
	struct ex_value *v = field2(tree, k1, k2);
	v = v && v->type == EX_LIST ? ex_list_get(v->list, index) : NULL;
	return !v ? fallback : v->type == EX_INT ? v->i : v->type == EX_FLOAT ? v->f : fallback;
}

static void load_state(struct parts *p, struct ex_tree *tree, int state)
{
	if (!tree) return;
	struct string *type = str2(tree, "パーツ类型", "部件タイプ");
	char *kind = sjis2utf(type->text, type->size);
	int no = p->no;
	if (!strcmp(kind, "ＣＧパーツ") || !strcmp(kind, "ＣＧ部件")) {
		struct string *cg = str(tree, "ＣＧ名");
		parts_get_cg(p, state - 1);
		if (cg->size) PE_SetPartsCG(no, cg, number2(tree, "变形", "変形", 0), state);
	} else if (!strcmp(kind, "フラットパーツ") || !strcmp(kind, "フラット部件")) {
		struct string *flat = str(tree, "フラット名");
		if (flat->size) {
			PE_SetPartsFlat(no, flat, state);
			int frame = number(tree, "开始時シーク量", 0);
			p->states[state - 1].flat.begin_seek_frame = frame;
			if (frame) PE_GoFramePartsFlat(no, frame, state);
		}
	} else if (!strcmp(kind, "矩形パーツ") || !strcmp(kind, "矩形部件")) {
		PE_SetRectangleDetectionPoint(no, item(tree, "左上", 0, 0), item(tree, "左上", 1, 0),
				item(tree, "右上", 0, 0), item(tree, "右上", 1, 0),
				item(tree, "左下", 0, 0), item(tree, "左下", 1, 0),
				item(tree, "右下", 0, 0), item(tree, "右下", 1, 0), state);
	} else if (!strcmp(kind, "ＣＧ判定パーツ") || !strcmp(kind, "ＣＧ判定部件")) {
		PE_SetPartsCGDetectionSize(no, str(tree, "ＣＧ名"), state);
	} else if (!strcmp(kind, "横向计量パーツ") || !strcmp(kind, "横ゲージ部件")) {
		struct string *cg = str(tree, "ＣＧ名");
		if (cg->size) PE_SetHGaugeCG(no, cg, state);
		PE_SetHGaugeReverse(no, !!number(tree, "反转", 0), state);
		PE_SetHGaugeRate(no, number(tree, "分子", 0), number(tree, "分母", 100), state);
	} else if (!strcmp(kind, "纵向计量パーツ") || !strcmp(kind, "縦ゲージ部件") || !strcmp(kind, "竖ゲージ部件")) {
		struct string *cg = str(tree, "ＣＧ名");
		if (cg->size) PE_SetVGaugeCG(no, cg, state);
		PE_SetVGaugeReverse(no, !!number(tree, "反转", 0), state);
		PE_SetVGaugeRate(no, number(tree, "分子", 0), number(tree, "分母", 100), state);
	} else if (!strcmp(kind, "数字パーツ") || !strcmp(kind, "数字部件")) {
		PE_SetNumeralFont(no,
				number2(tree, "字体类型", "フォントタイプ", 0),
				number2(tree, "字体尺寸", "フォントサイズ", 16),
				item2(tree, "字体色", "フォント色", 0, 255),
				item2(tree, "字体色", "フォント色", 1, 255),
				item2(tree, "字体色", "フォント色", 2, 255),
				number2(tree, "字体粗度", "フォント太さ", 0),
				item2(tree, "字体镶边色", "フォント縁取り色", 0, 0),
				item2(tree, "字体镶边色", "フォント縁取り色", 1, 0),
				item2(tree, "字体镶边色", "フォント縁取り色", 2, 0),
				number2(tree, "字体镶边", "フォント縁取り", 0), state);
		int show_type = number2(tree, "表示类型", "表示タイプ", 0);
		struct string *cg = str(tree, "ＣＧ名");
		if (cg->size && show_type == 0) PE_SetNumeralCG(no, cg, state);
		else if (cg->size && show_type == 1)
			PE_SetNumeralLinkedCGNumberWidthWidthList(no, cg,
				item(tree, "幅リスト", 0, 0), item(tree, "幅リスト", 1, 0), item(tree, "幅リスト", 2, 0),
				item(tree, "幅リスト", 3, 0), item(tree, "幅リスト", 4, 0), item(tree, "幅リスト", 5, 0),
				item(tree, "幅リスト", 6, 0), item(tree, "幅リスト", 7, 0), item(tree, "幅リスト", 8, 0),
				item(tree, "幅リスト", 9, 0), item(tree, "幅リスト", 10, 0), item(tree, "幅リスト", 11, 0), state);
		PE_SetNumeralShowType(no, show_type, state);
		PE_SetNumeralSpace(no, number2(tree, "字间距", "字間隔", 0), state);
		PE_SetNumeralShowComma(no, number(tree, "コンマ表示", 0), state);
		PE_SetNumeralFullPitch(no, number(tree, "全角", 0), state);
		PE_SetNumeralShowPadding(no, number(tree, "ゼロパディング", 1), state);
		PE_SetNumeralLength(no, number(tree, "桁数", 0), state);
		PE_SetNumeralNumber(no, number(tree, "数值", 0), state);
	} else if (!strcmp(kind, "文本パーツ") || !strcmp(kind, "文本部件")) {
		struct ex_tree *style = node(tree, "文本装飾");
		PE_SetPartsFontType(no, number2(style, "字体类型", "フォントタイプ", 0), state);
		PE_SetPartsFontSize(no, number2(style, "字体尺寸", "フォントサイズ", 16), state);
		PE_SetPartsFontColor(no,
				item2(style, "字体色", "フォント色", 0, 255),
				item2(style, "字体色", "フォント色", 1, 255),
				item2(style, "字体色", "フォント色", 2, 255), state);
		PE_SetPartsFontBoldWeight(no, number2(style, "字体粗度", "フォント太さ", 0), state);
		PE_SetPartsFontEdgeWeight(no, number2(style, "字体镶边", "フォント縁取り", 0), state);
		PE_SetPartsFontEdgeColor(no,
				item2(style, "字体镶边色", "フォント縁取り色", 0, 0),
				item2(style, "字体镶边色", "フォント縁取り色", 1, 0),
				item2(style, "字体镶边色", "フォント縁取り色", 2, 0), state);
		PE_SetTextCharSpace(no, number2(style, "字间距", "字間隔", 0), state);
		PE_SetTextLineSpace(no, number2(style, "行间距", "行間隔", 0), state);
		struct parts_text *pt = parts_get_text(p, state - 1);
		pt->text_pos = number(tree, "文本位置", 0);
		pt->enable_tag = !!number(tree, "タグ処理有効", 0);
		pt->show_time = number(tree, "一文字辺りの表示時間", 0);
		pt->count_return = !!number(tree, "改行を表示する", 0);
		struct ex_tree *rstyle = node(tree, "ルビ装飾");
		if (rstyle) {
			pt->ruby_ts.face = number2(rstyle, "字体类型", "フォントタイプ", 0);
			pt->ruby_ts.size = number2(rstyle, "字体尺寸", "フォントサイズ", 10);
			pt->ruby_ts.color = (SDL_Color){
				item2(rstyle, "字体色", "フォント色", 0, 255),
				item2(rstyle, "字体色", "フォント色", 1, 255),
				item2(rstyle, "字体色", "フォント色", 2, 255), 255
			};
			pt->ruby_ts.bold_width = number2(rstyle, "字体粗度", "フォント太さ", 0);
			pt->ruby_ts.edge_color = (SDL_Color){
				item2(rstyle, "字体镶边色", "フォント縁取り色", 0, 0),
				item2(rstyle, "字体镶边色", "フォント縁取り色", 1, 0),
				item2(rstyle, "字体镶边色", "フォント縁取り色", 2, 0), 255
			};
			text_style_set_edge_width(&pt->ruby_ts, number2(rstyle, "字体镶边", "フォント縁取り", 0));
			pt->ruby_char_space = number2(rstyle, "字间距", "字間隔", 0);
		}
		PE_SetText(no, str(tree, "文本"), state);
	} else if (!strcmp(kind, "構築パーツ") || !strcmp(kind, "構築部件")) {
		parts_get_construction_process(p, state - 1);
		struct ex_tree *steps = node(tree, "手順リスト");
		if (steps && !steps->is_leaf) {
			for (unsigned i = 0; i < steps->nr_children; i++) {
				struct ex_tree *step = &steps->children[i];
				int cmd = number(step, "コマンド", 0);
				int interp = number2(step, "补间类型", "補間タイプ", 0);
				int sx = item2(step, "原矩形", "元矩形", 0, 0), sy = item2(step, "原矩形", "元矩形", 1, 0);
				int sw = item2(step, "原矩形", "元矩形", 2, 0), sh = item2(step, "原矩形", "元矩形", 3, 0);
				int dx = item(step, "先矩形", 0, 0), dy = item(step, "先矩形", 1, 0);
				int dx2 = item(step, "先矩形", 2, 0), dy2 = item(step, "先矩形", 3, 0);
				int dw = item(step, "先矩形", 4, 0), dh = item(step, "先矩形", 5, 0);
				int r = item(step, "色１", 0, 0), g = item(step, "色１", 1, 0);
				int b = item(step, "色１", 2, 0), a = item(step, "色１", 3, 0);
				int r2 = item(step, "色２", 0, 0), g2 = item(step, "色２", 1, 0);
				int b2 = item(step, "色２", 2, 0), a2 = item(step, "色２", 3, 0);
				int cspace = number2(step, "文字间距", "文字間隔", 0);
				int lspace = number2(step, "行间距", "行間隔", 0);
				int ftype = number2(step, "字体类型", "フォントタイプ", 0);
				int fsize = number2(step, "字体尺寸", "フォントサイズ", 16);
				int fr = item2(step, "字体色", "フォント色", 0, 255);
				int fg = item2(step, "字体色", "フォント色", 1, 255);
				int fb = item2(step, "字体色", "フォント色", 2, 255);
				int er = item2(step, "字体镶边色", "フォント縁取り色", 0, 0);
				int eg = item2(step, "字体镶边色", "フォント縁取り色", 1, 0);
				int eb = item2(step, "字体镶边色", "フォント縁取り色", 2, 0);
				bool full = number(step, "全体", 0);
				int blur = number2(step, "ぼかし長さ", "ブラー", 0);
				float bold = number2(step, "字体粗度", "フォント太さ", 0);
				float edge = number2(step, "字体镶边", "フォント縁取り", 0);
				struct string *txt = str(step, "文本"), *cg = str(step, "ＣＧ名");
				switch (cmd) {
				case 0: PE_AddCreateToPartsConstructionProcess(no, dw, dh, state); break;
				case 1: PE_AddCreatePixelOnlyToPartsConstructionProcess(no, dw, dh, state); break;
				case 2: PE_AddCreateCGToProcess(no, cg, state); break;
				case 3:
					PE_AddFillToPartsConstructionProcess(no, dx, dy, dw, dh, r, g, b, state);
					parts_cp_set_last_fill_full_size(no, state, full);
					break;
				case 4:
					PE_AddFillAlphaColorToPartsConstructionProcess(no, dx, dy, dw, dh, r, g, b, a, state);
					parts_cp_set_last_fill_full_size(no, state, full);
					break;
				case 5:
					PE_AddFillAMapToPartsConstructionProcess(no, dx, dy, dw, dh, a, state);
					parts_cp_set_last_fill_full_size(no, state, full);
					break;
				case 6:
					PE_AddFillWithAlphaToPartsConstructionProcess(no, dx, dy, dw, dh, r, g, b, a, state);
					parts_cp_set_last_fill_full_size(no, state, full);
					break;
				case 7: PE_AddDrawTextToPartsConstructionProcess(no, dx, dy, txt, ftype, fsize, fr, fg, fb, bold, er, eg, eb, edge, cspace, lspace, state); break;
				case 8: PE_AddCopyTextToPartsConstructionProcess(no, dx, dy, txt, ftype, fsize, fr, fg, fb, bold, er, eg, eb, edge, cspace, lspace, state); break;
				case 9:
					PE_AddFillGradationHorizonToPartsConstructionProcess(no, dx, dy, dw, dh, r, g, b, r2, g2, b2, state);
					parts_cp_set_last_fill_full_size(no, state, full);
					break;
				case 10:
					PE_AddDrawRectToPartsConstructionProcess(no, dx, dy, dw, dh, r, g, b, state);
					parts_cp_set_last_fill_full_size(no, state, full);
					break;
				case 11: PE_AddDrawCutCGToPartsConstructionProcess(no, cg, dx, dy, dw, dh, sx, sy, dw, dh, interp, state); break;
				case 12: PE_AddCopyCutCGToPartsConstructionProcess(no, cg, dx, dy, dw, dh, sx, sy, dw, dh, interp, state); break;
				case 13: PE_AddDrawCutCGToPartsConstructionProcess(no, cg, dx, dy, dw, dh, sx, sy, sw, sh, interp, state); break;
				case 14: PE_AddCopyCutCGToPartsConstructionProcess(no, cg, dx, dy, dw, dh, sx, sy, sw, sh, interp, state); break;
				case 15: PE_AddGrayFilterToPartsConstructionProcess(no, dx, dy, dw, dh, full, state); break;
				case 16: PE_AddAddFilterToPartsConstructionProcess(no, dx, dy, dw, dh, r, g, b, full, state); break;
				case 17: PE_AddMulFilterToPartsConstructionProcess(no, dx, dy, dw, dh, r, g, b, full, state); break;
				case 18: PE_AddDrawLineToPartsConstructionProcess(no, dx, dy, dx2, dy2, r, g, b, a, state); break;
				case 19: PE_AddCutCGAlphaBlendToPartsConstructionProcess(no, cg, dx, dy, dw, dh, sx, sy, dw, dh, interp, false, state); break;
				case 20: PE_AddCutCGAlphaBlendToPartsConstructionProcess(no, cg, dx, dy, dw, dh, sx, sy, sw, sh, interp, true, state); break;
				case 21: PE_AddCutCGOnlyAlphaToPartsConstructionProcess(no, cg, dx, dy, dw, dh, sx, sy, dw, dh, interp, false, state); break;
				case 22: PE_AddCutCGOnlyAlphaToPartsConstructionProcess(no, cg, dx, dy, dw, dh, sx, sy, sw, sh, interp, true, state); break;
				case 23: PE_AddAlphaBlendTextToPartsConstructionProcess(no, dx, dy, txt, ftype, fsize, fr, fg, fb, bold, er, eg, eb, edge, cspace, lspace, state); break;
				case 24: PE_AddOnlyAlphaTextToPartsConstructionProcess(no, dx, dy, txt, ftype, fsize, fr, fg, fb, bold, er, eg, eb, edge, cspace, lspace, state); break;
				case 25:
					PE_AddMulAMapGradationHorizonToPartsConstructionProcess(no, dx, dy, dw, dh, a, a2, state);
					parts_cp_set_last_fill_full_size(no, state, full);
					break;
				case 26:
					PE_AddMulAMapGradationVerticalToPartsConstructionProcess(no, dx, dy, dw, dh, a, a2, state);
					parts_cp_set_last_fill_full_size(no, state, full);
					break;
				case 27: PE_AddHBlurFilterToPartsConstructionProcess(no, dx, dy, dw, dh, blur, full, state); break;
				case 28: PE_AddVBlurFilterToPartsConstructionProcess(no, dx, dy, dw, dh, blur, full, state); break;
				case 29: PE_AddCGBlendToPartsConstructionProcess(no, cg, dx, dy, state); break;
				case 30 ... 132: {
					struct parts_cp_v14 v14 = {
						.command = cmd,
						.interp_type = interp,
						.sx = sx, .sy = sy, .sw = sw, .sh = sh,
						.dx = dx, .dy = dy, .dx2 = dx2, .dy2 = dy2, .dw = dw, .dh = dh,
						.r = r, .g = g, .b = b, .a = a,
						.r2 = r2, .g2 = g2, .b2 = b2, .a2 = a2,
						.full_size = full,
						.blur = blur,
						.radius_x = item(step, "半径", 0, 0),
						.radius_y = item(step, "半径", 1, 0),
						.line_width = number(step, "线の幅", 1),
						.round_edge = !!item(step, "丸め", 0, 0),
						.round_corner = !!item(step, "丸め", 1, 0),
						.angle = number(step, "旋转角度", 0),
						.start_angle = item(step, "円弧角度", 0, 0),
						.sweep_angle = item(step, "円弧角度", 1, 0),
						.cg_name = cg,
					};
					struct ex_value *coords_val = field(step, "座標总览");
					if (coords_val && coords_val->type == EX_LIST && coords_val->list->nr_items > 0) {
						v14.nr_coords = (int)coords_val->list->nr_items;
						v14.coords = xmalloc((size_t)v14.nr_coords * sizeof(int));
						for (int k = 0; k < v14.nr_coords; k++) {
							struct ex_value *cv = ex_list_get(coords_val->list, k);
							v14.coords[k] = !cv ? 0 : cv->type == EX_INT ? cv->i : cv->type == EX_FLOAT ? (int)cv->f : 0;
						}
					}
					parts_cp_add_v14(no, &v14, state);
					free(v14.coords);
					break;
				}
				default: WARNING("Unsupported PACT construction step command: %d", cmd); break;
				}
			}
			PE_BuildPartsConstructionProcess(no, state);
		}
	} else if (*kind) {
		VM_ERROR("Unsupported PACT state component: %s", kind);
	}
	int x = item(tree, "サーフェイスエリア", 0, 0), y = item(tree, "サーフェイスエリア", 1, 0);
	int w = item(tree, "サーフェイスエリア", 2, 0), h = item(tree, "サーフェイスエリア", 3, 0);
	if (w > 0 && h > 0) p->states[state - 1].common.surface_area = (Rectangle){ x, y, w, h };
	free(kind);
}

static void load_part(struct activity *a, struct ex_tree *tree, int parent, bool edit)
{
	if (!edit && !number2(tree, "エディタ上表示", "编辑上表示", 1))
		return;
	int no = PE_GetFreeNumber();
	struct parts *p = parts_get(no);
	if (!PE_AddActivityParts(a->name, tree->name, no))
		VM_ERROR("Duplicate PACT parts name: %s", display_sjis0(tree->name->text));
	struct activity_part *binding = find_part(a, tree->name);
	binding->closed = number(tree, "閉じている", 0);
	binding->locked = number(tree, "ロックしている", 0);
	p->position_is_ratio = number2(tree, "座標を率として認識する", "座標をレートとして認識する", 0);
	PE_SetComponentPos(no, item(tree, "座標", 0, 0), item(tree, "座標", 1, 0));
	PE_SetZ(no, item(tree, "座標", 2, 1));
	PE_SetShow(no, number(tree, "表示", 1));
	PE_SetAlpha(no, number(tree, "アルファ", 255));
	PE_SetPartsOriginPosMode(no, number(tree, "原点座標模式", 1));
	p->reverse_lr = !!number(tree, "左右反转", 0);
	p->reverse_tb = !!number(tree, "上下反转", 0);
	PE_SetPartsMagX(no, item(tree, "拡大縮小", 0, 1) * (p->reverse_lr ? -1 : 1));
	PE_SetPartsMagY(no, item(tree, "拡大縮小", 1, 1) * (p->reverse_tb ? -1 : 1));
	PE_SetPartsRotateX(no, item(tree, "旋转", 0, 0));
	PE_SetPartsRotateY(no, item(tree, "旋转", 1, 0));
	PE_SetPartsRotateZ(no, item(tree, "旋转", 2, 0));
	PE_SetAddColor(no, item2(tree, "加法色", "加算色", 0, 0),
			item2(tree, "加法色", "加算色", 1, 0),
			item2(tree, "加法色", "加算色", 2, 0));
	PE_SetComponentSubColorMode(no, !!number(tree, "減算色模式", 0));
	PE_SetMultiplyColor(no, item2(tree, "乘法色", "乗算色", 0, 255),
			item2(tree, "乘法色", "乗算色", 1, 255),
			item2(tree, "乘法色", "乗算色", 2, 255));
	PE_SetPartsDrawFilter(no, number(tree, "描画フィルタ", 0));
	PE_SetClickable(no, number(tree, "点击許可", 0));
	PE_SetPassCursor(no, number2(tree, "光标选中透過", "オン指针透過", 0));
	PE_SetPartsPixelDecide(no, !!number2(tree, "鼠标指针ピクセル判定", "マウスカーソルピクセル判定", 0));
	PE_SetDrag(no, number(tree, "ドラッグ", 0));
	PE_SetWheelable(no, !!number2(tree, "ホイール有効", "ホイール有效", 0));
	PE_SetEnableSpeedUp(no, !!number2(tree, "スピードアップ有効", "スピードアップ有效", 1));
	p->lock_update = !!number(tree, "更新のロック", 0);
	p->swipe_type = number2(tree, "滑动", "スワイプ", 0);
	PE_SetComment(no, str2(tree, "批注", "コメント"));
	PE_SetPartsMessageWindowShowLink(no, number2(tree, "消息窗口表示連動", "信息窗口表示連動", 0));
	p->message_window_effect_link = !!number2(tree, "信息窗口視覚效果連動", "消息窗口視覚效果連動", 0);
	PE_SetComponentMargin(no, item2(tree, "边距", "マージン", 0, 0),
			item2(tree, "边距", "マージン", 1, 0),
			item2(tree, "边距", "マージン", 2, 0),
			item2(tree, "边距", "マージン", 3, 0));
	p->anchor_top = !!item(tree, "アンカー", 0, 0);
	p->anchor_bottom = !!item(tree, "アンカー", 1, 0);
	p->anchor_left = !!item(tree, "アンカー", 2, 0);
	p->anchor_right = !!item(tree, "アンカー", 3, 0);
	p->texture_filter_type = number(tree, "テクスチャフィルタリング", 0);
	p->texture_address_type = number(tree, "テクスチャアドレッシング", 0);
	p->mipmap = !!number(tree, "ミップ地图", 0);
	p->update_look_at_pos = !!number(tree, "注視点更新", 0);
	PE_Parts_SetSoundName(no, str2(tree, "光标选中效果音", "オン指针音效"), 2);
	PE_Parts_SetSoundName(no, str2(tree, "点击效果音", "点击音效"), 3);
	if (parent) PE_SetParentPartsNumber(no, parent);
	struct ex_tree *info = node2(tree, "种类別情报", "种类別情報");
	struct string *type = str2(info, "パーツ类型", "部件タイプ");
	char *kind = sjis2utf(type->text, type->size);
	if (!strcmp(kind, "レイアウトボックス")) {
		PE_SetLayoutBoxLayoutType(no, number2(info, "レイアウト类型", "レイアウトタイプ", 0));
		PE_SetLayoutBoxAlign(no, number(info, "配置", 1));
		PE_SetLayoutBoxReturnF(no, number2(info, "翻折許可", "折り返し許可", 0),
				number2(info, "翻折尺寸", "折り返しサイズ", 200));
		PE_SetLayoutBoxReturnSizeForRate(no, number2(info, "翻折尺寸を率として認識する", "折り返しサイズをレートとして認識する", 0));
		PE_set_layoutbox_padding(no, item(info, "パディング", 0, 0), item(info, "パディング", 1, 0), item(info, "パディング", 2, 0), item(info, "パディング", 3, 0));
	} else if (!strcmp(kind, "低レベルパーツ") || !strcmp(kind, "低等级部件")) {
		load_state(p, node2(info, "一般状态", "普通状態"), 1);
		load_state(p, node2(info, "光标选中状态", "オン指针状態"), 2);
		load_state(p, node2(info, "键DOWN状态", "キーダウン状態"), 3);
	} else if (!strcmp(kind, "按钮")) {
		PE_SetSpecialComponentType(no, 0);
		PE_SetButtonSize(no, item2(info, "尺寸", "サイズ", 0, 200), item2(info, "尺寸", "サイズ", 1, 30));
		PE_SetButtonEnable(no, number2(info, "有效", "有効", 1));
		PE_SetButtonColor(no, item(info, "按钮の色", 0, 200), item(info, "按钮の色", 1, 200), item(info, "按钮の色", 2, 200));
		PE_SetButtonFontProperty(no,
				number2(info, "字体类型", "フォントタイプ", 0),
				number2(info, "字体尺寸", "フォントサイズ", 16),
				item2(info, "字体色", "フォント色", 0, 0),
				item2(info, "字体色", "フォント色", 1, 0),
				item2(info, "字体色", "フォント色", 2, 0),
				number2(info, "字体粗度", "フォント太さ", 0),
				item2(info, "字体镶边色", "フォント縁取り色", 0, 0),
				item2(info, "字体镶边色", "フォント縁取り色", 1, 0),
				item2(info, "字体镶边色", "フォント縁取り色", 2, 0),
				number2(info, "字体镶边", "フォント縁取り", 0));
		PE_SetButtonTextOriginPosMode(no, number(info, "文本位置", 5));
		PE_SetButtonCharSpace(no, number2(info, "文字间距", "文字間隔", 0));
		PE_SetButtonLineSpace(no, number2(info, "行间距", "行間隔", 0));
		PE_SetButtonText(no, str(info, "文本"));
		struct string *flat = str(info, "フラット名"), *cg = str(info, "ＣＧ名");
		if (cg->size) PE_SetButtonCGName(no, cg);
		if (flat->size) PE_SetButtonFlatName(no, flat);
	} else if (!strcmp(kind, "复选框") || !strcmp(kind, "检查ボックス")) {
		parts_checkbox_create(p);
		p->widget->checked = number2(info, "选择状态", "检查状態", 0);
		p->widget->button_mode = number(info, "按钮模式", 0);
		PE_SetCheckBoxSize(no, item2(info, "尺寸", "サイズ", 0, 16), item2(info, "尺寸", "サイズ", 1, 16));
		PE_SetCheckBoxEnable(no, number2(info, "有效状态", "有効状態", 1));
		PE_SetCheckBoxColor(no, item(info, "按钮の色", 0, 200), item(info, "按钮の色", 1, 200), item(info, "按钮の色", 2, 200));
		PE_SetCheckBoxFontProperty(no,
				number2(info, "字体类型", "フォントタイプ", 0),
				number2(info, "字体尺寸", "フォントサイズ", 16),
				item2(info, "字体色", "フォント色", 0, 255),
				item2(info, "字体色", "フォント色", 1, 255),
				item2(info, "字体色", "フォント色", 2, 255),
				number2(info, "字体粗度", "フォント太さ", 0),
				item2(info, "字体镶边色", "フォント縁取り色", 0, 0),
				item2(info, "字体镶边色", "フォント縁取り色", 1, 0),
				item2(info, "字体镶边色", "フォント縁取り色", 2, 0),
				number2(info, "字体镶边", "フォント縁取り", 0));
		PE_SetCheckBoxTextOriginPosMode(no, number(info, "文本位置", 4));
		PE_SetCheckBoxCharSpace(no, number2(info, "文字间距", "文字間隔", 0));
		PE_SetCheckBoxLineSpace(no, number2(info, "行间距", "行間隔", 0));
		PE_SetCheckBoxButtonMode(no, number(info, "按钮模式", 0));
		PE_SetCheckBoxText(no, str(info, "文本"));
		struct string *cg = str(info, "ＣＧ名"), *flat = str(info, "フラット名");
		if (cg->size) PE_SetCheckBoxCGName(no, cg);
		if (flat->size) PE_SetCheckBoxFlatName(no, flat);
		cg = str2(info, "上書きＣＧ名", "覆盖ＣＧ名");
		if (cg->size) PE_SetCheckBoxOverrideCGName(no, cg);
		cg = str2(info, "上書き检查ＣＧ名", "覆盖检查ＣＧ名");
		if (cg->size) PE_SetCheckBoxOverrideCheckedCGName(no, cg);
		flat = str2(info, "上書きフラット名", "覆盖フラット名");
		if (flat->size) PE_SetCheckBoxOverrideFlatName(no, flat);
		flat = str2(info, "上書き检查フラット名", "覆盖检查フラット名");
		if (flat->size) PE_SetCheckBoxOverrideCheckedFlatName(no, flat);
	} else if (!strcmp(kind, "纵向滚动条栏") || !strcmp(kind, "横向滚动条栏") ||
	           !strcmp(kind, "竖滚动条") || !strcmp(kind, "横滚动条") ||
	           !strcmp(kind, "竖スライダー") || !strcmp(kind, "横スライダー") ||
	           !strcmp(kind, "纵向滑动条") || !strcmp(kind, "横向滑动条")) {
		bool horizontal = !strcmp(kind, "横向滚动条栏") || !strcmp(kind, "横滚动条") ||
		                  !strcmp(kind, "横スライダー") || !strcmp(kind, "横向滑动条");
		bool is_slider = !strcmp(kind, "竖スライダー") || !strcmp(kind, "横スライダー") ||
		                 !strcmp(kind, "纵向滑动条") || !strcmp(kind, "横向滑动条");
		if (is_slider)
			parts_sliderbar_create(p, horizontal);
		else
			parts_scrollbar_create(p, horizontal);
		struct parts_scrollbar *s = p->scrollbar;
		int length = number(info, "長さ", 256), width = number(info, "幅", 24);
		s->width = horizontal ? length : width;
		s->height = horizontal ? width : length;
		s->before = is_slider ? 0 : number2(info, "前尺寸", "前サイズ", 24);
		s->after = is_slider ? 0 : number2(info, "次尺寸", "次サイズ", 24);
		s->total = number2(info, "全体滚动条量", "全体スクロール量", 100);
		s->view = number(info, "表示量", 1);
		s->pos = min(parts_scrollbar_range(s), max(0, number2(info, "滚动条位置", "スクロール位置", 0)));
		s->rate = number2(info, "滚动率", "スクロールレート",
				parts_scrollbar_range(s) ? (float)s->pos / parts_scrollbar_range(s) : 1.f);
		s->rate = min(1.f, max(0.f, s->rate));
		s->step = number(info, "按钮点击移动量", 1);
		const char *fields1[2][5] = {
			{"ＣＧ名", "上書き前ＣＧ名", "上書き次ＣＧ名", "上書きバーＣＧ名", "上書き背景ＣＧ名"},
			{"フラット名", "上書き前フラット名", "上書き次フラット名", "上書きバーフラット名", "上書き背景フラット名"}
		};
		const char *fields2[2][5] = {
			{"ＣＧ名", "覆盖前ＣＧ名", "覆盖次ＣＧ名", "覆盖バーＣＧ名", "覆盖背景ＣＧ名"},
			{"フラット名", "覆盖前フラット名", "覆盖次フラット名", "覆盖バーフラット名", "覆盖背景フラット名"}
		};
		for (int resource = 0; resource < 2; resource++) for (int piece = 0; piece < 5; piece++) {
			free_string(s->names[resource][piece]);
			s->names[resource][piece] = string_dup(str2(info, fields1[resource][piece], fields2[resource][piece]));
		}
		parts_scrollbar_rebuild(p);
	} else if (!strcmp(kind, "面板") || !strcmp(kind, "パネル")) {
		PE_SetPanelSize(no, item2(info, "尺寸", "サイズ", 0, 200), item2(info, "尺寸", "サイズ", 1, 200));
		PE_SetPanelColor(no, item(info, "色", 0, 220), item(info, "色", 1, 220),
				item(info, "色", 2, 220), item(info, "色", 3, 255));
		PE_SetPanelAlphaGradationTop(no, item2(info, "アルファ色彩渐变", "アルファグラデーション", 0, 0));
		PE_SetPanelAlphaGradationBottom(no, item2(info, "アルファ色彩渐变", "アルファグラデーション", 1, 0));
		PE_SetPanelAlphaGradationLeft(no, item2(info, "アルファ色彩渐变", "アルファグラデーション", 2, 0));
		PE_SetPanelAlphaGradationRight(no, item2(info, "アルファ色彩渐变", "アルファグラデーション", 3, 0));
	} else if (!strcmp(kind, "消息窗口") || !strcmp(kind, "信息窗口")) {
		PE_SetSpecialComponentType(no, 10);
		PE_SetMessageWindowActive(no, number(info, "アクティブ", 0));
		PE_SetMessageWindowInactiveMultipleColor(no,
				item2(info, "非アクティブ時の乘法颜色", "非アクティブ時の乗算カラー", 0, 255),
				item2(info, "非アクティブ時の乘法颜色", "非アクティブ時の乗算カラー", 1, 255),
				item2(info, "非アクティブ時の乘法颜色", "非アクティブ時の乗算カラー", 2, 255));
		PE_SetMessageWindowTextArea(no, item(info, "文本エリア", 0, 10), item(info, "文本エリア", 1, 15),
				item(info, "文本エリア", 2, 0), item(info, "文本エリア", 3, 0));
		PE_SetMessageWindowTextOriginPosMode(no, number(info, "文本位置", 1));
		PE_SetMessageWindowTextFont(no,
				number2(info, "字体类型", "フォントタイプ", 0),
				number2(info, "字体尺寸", "フォントサイズ", 26),
				item2(info, "字体色", "フォント色", 0, 255),
				item2(info, "字体色", "フォント色", 1, 255),
				item2(info, "字体色", "フォント色", 2, 255),
				number2(info, "字体粗度", "フォント太さ", 0),
				item2(info, "字体镶边色", "フォント縁取り色", 0, 0),
				item2(info, "字体镶边色", "フォント縁取り色", 1, 0),
				item2(info, "字体镶边色", "フォント縁取り色", 2, 0),
				number2(info, "字体镶边", "フォント縁取り", 0));
		PE_SetMessageWindowTextSpeed(no, number(info, "字速度", 23));
		PE_SetMessageWindowTextSpace(no, number2(info, "文字间距", "文字間隔", 0),
				number2(info, "行间距", "行間隔", 3));
		struct ex_tree *ruby = node(info, "ルビ");
		if (ruby) {
			PE_SetMessageWindowRubyFont(no,
					number2(ruby, "字体类型", "フォントタイプ", 0),
					number2(ruby, "字体尺寸", "フォントサイズ", 10),
					item2(ruby, "字体色", "フォント色", 0, 255),
					item2(ruby, "字体色", "フォント色", 1, 255),
					item2(ruby, "字体色", "フォント色", 2, 255),
					number2(ruby, "字体粗度", "フォント太さ", 0),
					item2(ruby, "字体镶边色", "フォント縁取り色", 0, 0),
					item2(ruby, "字体镶边色", "フォント縁取り色", 1, 0),
					item2(ruby, "字体镶边色", "フォント縁取り色", 2, 0),
					number2(ruby, "字体镶边", "フォント縁取り", 0));
			PE_SetMessageWindowRubyCharSpace(no, number2(ruby, "文字间距", "文字間隔", 0));
			PE_SetMessageWindowRubyLineSpace(no, number2(ruby, "行间距", "行間隔", 0));
		}
		struct string *flat = str(info, "フラット名"), *cg = str(info, "ＣＧ名");
		if (cg->size) PE_SetMessageWindowCGName(no, cg);
		if (flat->size) PE_SetMessageWindowFlatName(no, flat);
		PE_SetMessageWindowText(no, str(info, "文本"), number2(info, "消息编号", "信息番号", -1), &EMPTY_STRING, 0, 0);
		struct ex_tree *key = node(info, "キー待ちマーク");
		if (key) {
			PE_SetKeyWaitPos(no, item(key, "座標", 0, 0), item(key, "座標", 1, 0), item(key, "座標", 2, 0));
			PE_SetKeyWaitCGName(no, str(key, "ＣＧ名"),
					number2(key, "循环ＣＧ开始编号", "循环ＣＧ开始番号", 0),
					number2(key, "循环ＣＧ张数", "循环ＣＧ枚数", 0),
					number2(key, "循环ＣＧ切り替え时间", "循环ＣＧ切换時間", 0));
			flat = str(key, "フラット名");
			if (flat->size) PE_SetKeyWaitFlatName(no, flat);
		}
	} else if (!strcmp(kind, "ラジオ按钮ボックス") || !strcmp(kind, "单选按钮框")) {
		PE_SetSpecialComponentType(no, 9);
	} else if (!strcmp(kind, "文本框") || !strcmp(kind, "テキストボックス")) {
		PE_SetSpecialComponentType(no, 4);
		parts_set_dims(p, &p->states[0].common, item2(info, "尺寸", "サイズ", 0, 200), item2(info, "尺寸", "サイズ", 1, 30));
		PE_SetTextBoxText(no, str(info, "文本"));
	} else if (!strcmp(kind, "リストボックス")) {
		PE_SetSpecialComponentType(no, 5);
		parts_set_dims(p, &p->states[0].common, item2(info, "尺寸", "サイズ", 0, 200), item2(info, "尺寸", "サイズ", 1, 200));
	} else if (!strcmp(kind, "コンボボックス")) {
		PE_SetSpecialComponentType(no, 6);
		parts_set_dims(p, &p->states[0].common, item2(info, "尺寸", "サイズ", 0, 200), item2(info, "尺寸", "サイズ", 1, 30));
	} else if (!strcmp(kind, "多行文本框") || !strcmp(kind, "マルチラインテキストボックス")) {
		PE_SetSpecialComponentType(no, 7);
		parts_set_dims(p, &p->states[0].common, item2(info, "尺寸", "サイズ", 0, 200), item2(info, "尺寸", "サイズ", 1, 200));
		PE_SetTextBoxText(no, str(info, "文本"));
	} else if (!strcmp(kind, "スピンボックス")) {
		PE_SetSpecialComponentType(no, 11);
		parts_set_dims(p, &p->states[0].common, item2(info, "尺寸", "サイズ", 0, 100), item2(info, "尺寸", "サイズ", 1, 24));
	} else if (!strcmp(kind, "フォーム")) {
		PE_SetSpecialComponentType(no, 13);
		parts_set_dims(p, &p->states[0].common, item2(info, "尺寸", "サイズ", 0, 200), item2(info, "尺寸", "サイズ", 1, 200));
	} else if (!strcmp(kind, "用户组件") || !strcmp(kind, "ユーザコンポーネント")) {
		PE_SetSpecialComponentType(no, 15);
		PE_SetUserComponentName(no, str2(info, "用户组件名", "ユーザコンポーネント名"));
		struct ex_value *data = field(info, "数据");
		if (data && data->type == EX_LIST) {
			for (unsigned i = 0; i < data->list->nr_items; ) {
				struct ex_value *first = ex_list_get(data->list, i);
				if (first && first->type == EX_LIST) {
					struct ex_value *key = ex_list_get(first->list, 0);
					struct ex_value *value = ex_list_get(first->list, 1);
					if (key && value && key->type == EX_STRING && value->type == EX_STRING)
						PE_SetUserComponentData(no, key->s, value->s);
					i++;
				} else if (i + 1 < data->list->nr_items) {
					struct ex_value *value = ex_list_get(data->list, i + 1);
					if (first && value && first->type == EX_STRING && value->type == EX_STRING)
						PE_SetUserComponentData(no, first->s, value->s);
					i += 2;
				} else {
					break;
				}
			}
		}
	} else {
		VM_ERROR("Unsupported PACT component: %s", kind);
	}
	free(kind);
	struct ex_tree *children = node2(tree, "子パーツ", "子部件");
	if (children) for (unsigned i = 0; i < children->nr_children; i++)
		load_part(a, &children->children[i], no, edit);
}

static void load_part_links(struct activity *a, struct ex_tree *tree)
{
	struct activity_part *binding = find_part(a, tree->name);
	struct parts *p = binding ? parts_try_get(binding->no) : NULL;
	if (!p) return;
	struct activity_part *clipper = find_part(a, str2(tree, "α剪切器ー", "アルファクリッパー"));
	if (clipper) PE_SetPartsAlphaClipperPartsNumber(p->no, clipper->no);
	struct activity_part *cursor_link = find_part(a, str2(tree, "光标选中表示連動", "オン指针表示連動"));
	if (cursor_link) PE_SetOnCursorShowLinkPartsNumber(p->no, cursor_link->no);
	struct activity_part *cb_link = find_part(a, str2(tree, "复选框表示連動", "检查ボックス表示連動"));
	if (cb_link) PE_SetComponentCheckBoxShowLinkNumber(p->no, cb_link->no);
	if (p->component_type == 9) {
		struct ex_tree *info = node2(tree, "种类別情报", "种类別情報");
		struct ex_value *list = field2(info, "复选框リスト", "检查ボックスリスト");
		if (list && list->type == EX_LIST) {
			for (unsigned i = 0; i < list->list->nr_items; i++) {
				struct ex_value *item = ex_list_get(list->list, i);
				if (item && item->type == EX_STRING) {
					struct activity_part *cb = find_part(a, item->s);
					if (cb) PE_AddRadioButtonBoxChild(p->no, cb->no);
				}
			}
		}
	}
	const char *links1[] = {"滚动条Ｘ座標連動", "滚动条Ｙ座標連動", "滚动条アルファ連動"};
	const char *links2[] = {"スクロールＸ座標連動", "スクロールＹ座標連動", "スクロールアルファ連動"};
	for (int i = 0; i < 3; i++) {
		struct activity_part *target = find_part(a, str2(tree, links1[i], links2[i]));
		p->scroll_links[i] = target ? target->no : 0;
	}
	if (p->scrollbar) {
		struct ex_tree *info = node2(tree, "种类別情报", "种类別情報");
		struct activity_part *total = find_part(a, str2(info, "全体滚动条量尺寸連動", "全体スクロール量サイズ連動"));
		struct activity_part *view = find_part(a, str2(info, "表示量尺寸連動", "表示量サイズ連動"));
		p->scrollbar->total_link = total ? total->no : 0;
		p->scrollbar->view_link = view ? view->no : 0;
	}
	struct ex_tree *children = node2(tree, "子パーツ", "子部件");
	if (children) for (unsigned i = 0; i < children->nr_children; i++) load_part_links(a, &children->children[i]);
}

static struct ex_value *block(struct ex *ex, const char *key)
{
	char *encoded = utf2sjis(key, strlen(key));
	struct ex_value *v = ex_get(ex, encoded);
	free(encoded);
	return v;
}

static struct ex_value *block2(struct ex *ex, const char *k1, const char *k2)
{
	struct ex_value *v = block(ex, k1);
	return v ? v : block(ex, k2);
}

static char *activity_file_path_cstr(const char *text, size_t size)
{
	char *path = gamedir_path_icase(text);
	if (path && file_exists(path)) return path;
	free(path);
	char *name = xmalloc(size + 8);
	memcpy(name, text, size);
	strcpy(name + size, ".pactex");
	path = gamedir_path_icase(name);
	if (path && file_exists(path)) {
		free(name);
		return path;
	}
	free(path);
	strcpy(name + size, ".pact");
	path = gamedir_path_icase(name);
	free(name);
	return path;
}

static const char *pact_leaf_name(const char *path)
{
	const char *leaf = path;
	for (const uint8_t *p = (const uint8_t *)path; *p; p++) {
		if (SJIS_2BYTE(*p) && p[1]) {
			p++;
			continue;
		}
		if (*p == '/' || *p == '\\')
			leaf = (const char *)(p + 1);
	}
	return leaf;
}

static bool pact_has_asra_prefix(const char *path)
{
	return tolower((unsigned char)path[0]) == 'a'
		&& tolower((unsigned char)path[1]) == 's'
		&& tolower((unsigned char)path[2]) == 'r'
		&& tolower((unsigned char)path[3]) == 'a'
		&& (path[4] == '\\' || path[4] == '/');
}

/*
 * Game scripts and PACT intents sometimes reference packaged .pact assets via
 * the authoring prefix "Asra\" instead of "config\" or archive root (e.g.
 * "Asra\コンフィグ／０４／サウンドページ.pact" or "Asra\バックログ").
 * Because archive_basename preserves directory prefixes, resolve exact first
 * and then fall back through stripped / "config\" / leaf forms.
 */
static size_t pact_build_candidates(const char *orig, char *out[5], char *scratch[4])
{
	size_t n = 0;
	int s = 0;
	out[n++] = (char *)orig;
	if (pact_has_asra_prefix(orig)) {
		const char *rest = orig + 5;
		if (rest[0]) {
			out[n++] = (char *)rest;
			size_t rlen = strlen(rest);
			char *cfg = xmalloc(7 + rlen + 1);
			memcpy(cfg, "config\\", 7);
			memcpy(cfg + 7, rest, rlen + 1);
			scratch[s++] = cfg;
			out[n++] = cfg;
		}
	}
	const char *leaf = pact_leaf_name(orig);
	if (leaf > orig && leaf[0]) {
		bool dup = false;
		for (size_t i = 0; i < n; i++) {
			if (!strcmp(out[i], leaf)) {
				dup = true;
				break;
			}
		}
		if (!dup)
			out[n++] = (char *)leaf;
		size_t llen = strlen(leaf);
		char *cfg_leaf = xmalloc(7 + llen + 1);
		memcpy(cfg_leaf, "config\\", 7);
		memcpy(cfg_leaf + 7, leaf, llen + 1);
		bool dup_cfg = false;
		for (size_t i = 0; i < n; i++) {
			if (!strcmp(out[i], cfg_leaf)) {
				dup_cfg = true;
				break;
			}
		}
		if (dup_cfg) {
			free(cfg_leaf);
		} else {
			scratch[s++] = cfg_leaf;
			out[n++] = cfg_leaf;
		}
	}
	while (s < 4)
		scratch[s++] = NULL;
	return n;
}

static bool pact_lookup_asset(const char *orig, struct archive_data **data_out, char **fs_path_out)
{
	if (data_out)
		*data_out = NULL;
	if (fs_path_out)
		*fs_path_out = NULL;
	if (!orig || !orig[0])
		return false;

	char *cands[5];
	char *scratch[4];
	size_t ncands = pact_build_candidates(orig, cands, scratch);
	bool found = false;

	for (size_t i = 0; i < ncands; i++) {
		if (data_out) {
			struct archive_data *d = asset_get_by_name(ASSET_PACT, cands[i], NULL);
			if (d) {
				*data_out = d;
				found = true;
				break;
			}
		} else if (asset_exists_by_name(ASSET_PACT, cands[i], NULL)) {
			found = true;
			break;
		}
	}

	if (!found) {
		for (size_t i = 0; i < ncands; i++) {
			char *path = activity_file_path_cstr(cands[i], strlen(cands[i]));
			if (path && file_exists(path)) {
				if (fs_path_out)
					*fs_path_out = path;
				else
					free(path);
				found = true;
				break;
			}
			free(path);
		}
	}

	for (int i = 0; i < 4; i++)
		free(scratch[i]);
	return found;
}

bool PE_IsExistActivityFile(struct string *file)
{
	return pact_lookup_asset(file->text, NULL, NULL);
}

struct pact_bin_reader {
	const uint8_t *buf;
	size_t size;
	size_t pos;
	bool err;
};

static uint8_t pbr_u8(struct pact_bin_reader *r)
{
	if (r->err || r->pos + 1 > r->size) { r->err = true; return 0; }
	return r->buf[r->pos++];
}

static bool pbr_bool(struct pact_bin_reader *r)
{
	uint8_t v = pbr_u8(r);
	if (v > 1) { r->err = true; return false; }
	return v == 1;
}

static int32_t pbr_i32(struct pact_bin_reader *r)
{
	if (r->err || r->pos + 4 > r->size) { r->err = true; return 0; }
	int32_t v;
	memcpy(&v, r->buf + r->pos, 4);
	r->pos += 4;
	return v;
}

static float pbr_f32(struct pact_bin_reader *r)
{
	if (r->err || r->pos + 4 > r->size) { r->err = true; return 0.f; }
	float v;
	memcpy(&v, r->buf + r->pos, 4);
	r->pos += 4;
	return v;
}

static const char *pbr_cstr(struct pact_bin_reader *r)
{
	if (r->err || r->pos >= r->size) { r->err = true; return ""; }
	const char *s = (const char *)(r->buf + r->pos);
	const void *nul = memchr(s, 0, r->size - r->pos);
	if (!nul) { r->err = true; return ""; }
	r->pos += ((const char *)nul - s) + 1;
	return s;
}

static const char *pbr_opt_cstr(struct pact_bin_reader *r)
{
	return pbr_bool(r) ? pbr_cstr(r) : NULL;
}

struct pact_font40 {
	int type, size, r, g, b;
	float bold, edge;
	int er, eg, eb;
};

struct pact_font48 {
	int type, size, r, g, b;
	float bold, edge;
	int er, eg, eb, cspace, lspace;
};

static struct pact_font40 pbr_font40(struct pact_bin_reader *r)
{
	struct pact_font40 f;
	f.type = pbr_i32(r); f.size = pbr_i32(r);
	f.r = pbr_i32(r); f.g = pbr_i32(r); f.b = pbr_i32(r);
	f.bold = pbr_f32(r); f.edge = pbr_f32(r);
	f.er = pbr_i32(r); f.eg = pbr_i32(r); f.eb = pbr_i32(r);
	return f;
}

static struct pact_font48 pbr_font48(struct pact_bin_reader *r)
{
	struct pact_font48 f;
	f.type = pbr_i32(r); f.size = pbr_i32(r);
	f.r = pbr_i32(r); f.g = pbr_i32(r); f.b = pbr_i32(r);
	f.bold = pbr_f32(r); f.edge = pbr_f32(r);
	f.er = pbr_i32(r); f.eg = pbr_i32(r); f.eb = pbr_i32(r);
	f.cspace = pbr_i32(r); f.lspace = pbr_i32(r);
	return f;
}

static void pbr_skip_mitem(struct pact_bin_reader *r, bool is_float)
{
	pbr_cstr(r);
	pbr_cstr(r);
	if (is_float) { pbr_f32(r); pbr_f32(r); }
	else { pbr_i32(r); pbr_i32(r); }
	pbr_i32(r);
	pbr_i32(r);
}

static void pbr_skip_mlist(struct pact_bin_reader *r, bool is_float)
{
	int32_t m1 = pbr_i32(r);
	if (m1 < 0 || m1 > 100000) { r->err = true; return; }
	for (int32_t i = 0; i < m1 && !r->err; i++) pbr_skip_mitem(r, is_float);
	int32_t m2 = pbr_i32(r);
	if (m2 < 0 || m2 > 100000) { r->err = true; return; }
	for (int32_t i = 0; i < m2 && !r->err; i++) {
		int32_t m3 = pbr_i32(r);
		if (m3 < 0 || m3 > 100000) { r->err = true; return; }
		for (int32_t j = 0; j < m3 && !r->err; j++) pbr_skip_mitem(r, is_float);
	}
}

struct pact_bin_part_links {
	int no;
	int32_t nr_children;
	const char **children;
	const char *links[6];
	int32_t nr_radio;
	const char **radio_names;
};

static struct activity_part *find_part_cstr(struct activity *a, const char *cstr)
{
	if (!a || !cstr || !cstr[0]) return NULL;
	for (struct activity_part *p = a->parts; p; p = p->next)
		if (!strcmp(p->name->text, cstr)) return p;
	return NULL;
}

static bool parse_binary_pact_stream(struct string *act_name, const uint8_t *buf, size_t size)
{
	struct pact_bin_reader R = { buf, size, 0, false };
	struct pact_bin_reader *r = &R;
	int32_t version = pbr_i32(r);
	int32_t flag = pbr_i32(r);
	int32_t nr_parts = pbr_i32(r);
	(void)flag;
	if (r->err || version < 1 || version > 20 || nr_parts < 0 || nr_parts > 100000)
		return false;
	if (!PE_CreateActivity(act_name))
		return false;
	struct activity *a = find_activity(act_name);
	struct pact_bin_part_links *plinks = nr_parts > 0 ? xcalloc((size_t)nr_parts, sizeof(*plinks)) : NULL;

	for (int32_t idx = 0; idx < nr_parts && !r->err; idx++) {
		const char *pname_c = pbr_cstr(r);
		int32_t nr_children = pbr_i32(r);
		if (nr_children < 0 || nr_children > 10000) { r->err = true; break; }
		plinks[idx].nr_children = nr_children;
		if (nr_children > 0) {
			plinks[idx].children = xmalloc((size_t)nr_children * sizeof(const char *));
			for (int32_t c = 0; c < nr_children; c++)
				plinks[idx].children[c] = pbr_cstr(r);
		}
		for (int k = 0; k < 6; k++)
			plinks[idx].links[k] = pbr_opt_cstr(r);
		if (r->err) break;

		int no = PE_GetFreeNumber();
		plinks[idx].no = no;
		struct parts *p = parts_get(no);
		struct string *pname = make_string(pname_c, (unsigned)strlen(pname_c));
		PE_AddActivityParts(act_name, pname, no);
		free_string(pname);

		int32_t st0 = pbr_i32(r);
		bool editor_show = pbr_bool(r);
		bool clickable = pbr_bool(r);
		float x = pbr_f32(r);
		float y = pbr_f32(r);
		int32_t z = pbr_i32(r);
		int32_t orig = pbr_i32(r);
		// CAEPropertyPlain serializes IsShow ("表示") immediately after orig.
		bool show = pbr_bool(r);
		bool msgw = pbr_bool(r);
		int32_t alpha = pbr_i32(r);
		int32_t add_r = pbr_i32(r), add_g = pbr_i32(r), add_b = pbr_i32(r);
		int32_t mul_r = pbr_i32(r), mul_g = pbr_i32(r), mul_b = pbr_i32(r);
		int32_t draw_filter = pbr_i32(r);
		float mag_x = pbr_f32(r), mag_y = pbr_f32(r);
		float rot_x = pbr_f32(r), rot_y = pbr_f32(r), rot_z = pbr_f32(r);
		int32_t i11[11];
		for (int i = 0; i < 11; i++) i11[i] = pbr_i32(r);
		pbr_bool(r);
		pbr_i32(r);
		pbr_i32(r);
		int32_t nv1 = pbr_i32(r);
		if (nv1 < 0 || nv1 > 10000) { r->err = true; break; }
		for (int32_t i = 0; i < nv1; i++) pbr_i32(r);
		pbr_i32(r);
		pbr_i32(r);
		for (int i = 0; i < 2; i++) pbr_skip_mlist(r, true);
		for (int i = 0; i < 9; i++) pbr_skip_mlist(r, false);
		pbr_i32(r);
		pbr_i32(r);
		for (int i = 0; i < 5; i++) pbr_skip_mlist(r, true);
		pbr_skip_mlist(r, false);
		for (int i = 0; i < 4; i++) pbr_skip_mlist(r, true);
		pbr_skip_mlist(r, false);
		int32_t nv2 = pbr_i32(r);
		if (nv2 < 0 || nv2 > 10000) { r->err = true; break; }
		for (int32_t i = 0; i < nv2; i++) pbr_i32(r);
		int32_t nv3 = pbr_i32(r);
		if (nv3 < 0 || nv3 > 10000) { r->err = true; break; }
		for (int32_t i = 0; i < nv3; i++) pbr_i32(r);
		bool b4[4];
		for (int i = 0; i < 4; i++) b4[i] = pbr_bool(r);
		(void)b4;
		if (version >= 4)
			p->position_is_ratio = pbr_bool(r);
		if (version >= 3) {
			for (int s = 0; s < 4; s++) {
				const char *snd = pbr_cstr(r);
				if (s >= 1 && snd[0]) {
					struct string *ss = make_string(snd, (unsigned)strlen(snd));
					PE_Parts_SetSoundName(no, ss, s);
					free_string(ss);
				}
			}
		} else {
			for (int s = 0; s < 4; s++) pbr_i32(r);
		}
		for (int i = 0; i < 4; i++) pbr_i32(r);
		if (r->err) break;

		if (st0 >= 1 && st0 <= 3) PE_SetInputState(no, st0);
		PE_SetShow(no, editor_show && show);
		PE_SetClickable(no, clickable);
		PE_SetComponentPos(no, x, y);
		PE_SetZ(no, z);
		PE_SetPartsOriginPosMode(no, orig);
		PE_SetPartsMessageWindowShowLink(no, msgw);
		PE_SetAlpha(no, alpha);
		PE_SetAddColor(no, add_r, add_g, add_b);
		PE_SetMultiplyColor(no, mul_r, mul_g, mul_b);
		PE_SetPartsDrawFilter(no, draw_filter);
		PE_SetPartsMagX(no, mag_x);
		PE_SetPartsMagY(no, mag_y);
		PE_SetPartsRotateX(no, rot_x);
		PE_SetPartsRotateY(no, rot_y);
		PE_SetPartsRotateZ(no, rot_z);
		PE_SetComponentMargin(no, i11[0], i11[1], i11[2], i11[3]);
		PE_SetEnableSpeedUp(no, !!i11[10]);

		int32_t ck = pbr_i32(r);
		switch (ck) {
		case 0: { // Button
			int32_t bw = pbr_i32(r), bh = pbr_i32(r);
			bool ben = pbr_bool(r);
			int32_t br = pbr_i32(r), bg = pbr_i32(r), bb = pbr_i32(r);
			struct pact_font40 bf = pbr_font40(r);
			const char *bcg = pbr_cstr(r);
			const char *bflat = version >= 2 ? pbr_cstr(r) : "";
			const char *btxt = pbr_cstr(r);
			int32_t tpos = pbr_i32(r), cspace = pbr_i32(r), lspace = pbr_i32(r);
			PE_SetSpecialComponentType(no, 0);
			PE_SetButtonSize(no, bw, bh);
			PE_SetButtonEnable(no, ben);
			PE_SetButtonColor(no, br, bg, bb);
			PE_SetButtonFontProperty(no, bf.type, bf.size, bf.r, bf.g, bf.b, bf.bold, bf.er, bf.eg, bf.eb, bf.edge);
			PE_SetButtonTextOriginPosMode(no, tpos);
			PE_SetButtonCharSpace(no, cspace);
			PE_SetButtonLineSpace(no, lspace);
			struct string *ts = make_string(btxt, (unsigned)strlen(btxt));
			PE_SetButtonText(no, ts);
			free_string(ts);
			if (bcg[0]) {
				struct string *cs = make_string(bcg, (unsigned)strlen(bcg));
				PE_SetButtonCGName(no, cs);
				free_string(cs);
			}
			if (bflat[0]) {
				struct string *fs = make_string(bflat, (unsigned)strlen(bflat));
				PE_SetButtonFlatName(no, fs);
				free_string(fs);
			}
			break;
		}
		case 1: { // CheckBox
			int32_t cw = pbr_i32(r), ch = pbr_i32(r);
			bool checked = pbr_bool(r);
			int32_t cr = pbr_i32(r), cg0 = pbr_i32(r), cb0 = pbr_i32(r);
			struct pact_font40 cf = pbr_font40(r);
			const char *ccg = pbr_cstr(r);
			const char *cflat = version >= 2 ? pbr_cstr(r) : "";
			const char *ctxt = pbr_cstr(r);
			int32_t tpos = pbr_i32(r);
			bool button_mode = pbr_bool(r);
			int32_t cspace = pbr_i32(r), lspace = pbr_i32(r);
			parts_checkbox_create(p);
			p->widget->checked = checked;
			PE_SetCheckBoxButtonMode(no, button_mode);
			PE_SetCheckBoxSize(no, cw, ch);
			PE_SetCheckBoxColor(no, cr, cg0, cb0);
			PE_SetCheckBoxFontProperty(no, cf.type, cf.size, cf.r, cf.g, cf.b, cf.bold, cf.er, cf.eg, cf.eb, cf.edge);
			PE_SetCheckBoxTextOriginPosMode(no, tpos);
			PE_SetCheckBoxCharSpace(no, cspace);
			PE_SetCheckBoxLineSpace(no, lspace);
			struct string *ts = make_string(ctxt, (unsigned)strlen(ctxt));
			PE_SetCheckBoxText(no, ts);
			free_string(ts);
			if (ccg[0]) {
				struct string *cs = make_string(ccg, (unsigned)strlen(ccg));
				PE_SetCheckBoxCGName(no, cs);
				free_string(cs);
			}
			if (cflat[0]) {
				struct string *fs = make_string(cflat, (unsigned)strlen(cflat));
				PE_SetCheckBoxFlatName(no, fs);
				free_string(fs);
			}
			break;
		}
		case 2:
		case 3: { // VScrollbar / HScrollbar
			bool horizontal = (ck == 3);
			int32_t v7[7];
			for (int i = 0; i < 7; i++) v7[i] = pbr_i32(r);
			float rate = pbr_f32(r);
			int32_t step = pbr_i32(r);
			const char *scg = pbr_cstr(r);
			const char *sflat = version >= 2 ? pbr_cstr(r) : "";
			parts_scrollbar_create(p, horizontal);
			struct parts_scrollbar *s = p->scrollbar;
			s->width = horizontal ? v7[0] : v7[1];
			s->height = horizontal ? v7[1] : v7[0];
			s->before = v7[2];
			s->after = v7[3];
			s->total = v7[4];
			s->view = v7[5];
			s->pos = min(parts_scrollbar_range(s), max(0, v7[6]));
			s->rate = min(1.f, max(0.f, rate));
			s->step = step;
			if (scg[0]) {
				free_string(s->names[0][0]);
				s->names[0][0] = make_string(scg, (unsigned)strlen(scg));
			}
			if (sflat[0]) {
				free_string(s->names[1][0]);
				s->names[1][0] = make_string(sflat, (unsigned)strlen(sflat));
			}
			parts_scrollbar_rebuild(p);
			break;
		}
		case 4: { // TextBox
			int32_t tw = pbr_i32(r), th = pbr_i32(r);
			(void)pbr_font40(r);
			pbr_bool(r);
			const char *ttxt = pbr_cstr(r);
			pbr_i32(r);
			for (int i = 0; i < 13; i++) pbr_i32(r);
			pbr_cstr(r);
			pbr_i32(r);
			if (version >= 5) pbr_bool(r);
			PE_SetSpecialComponentType(no, 4);
			parts_set_dims(p, &p->states[0].common, tw, th);
			struct string *ts = make_string(ttxt, (unsigned)strlen(ttxt));
			PE_SetTextBoxText(no, ts);
			free_string(ts);
			break;
		}
		case 5: { // ListBox
			int32_t lw = pbr_i32(r), lh = pbr_i32(r);
			pbr_i32(r); pbr_i32(r); pbr_i32(r);
			pbr_cstr(r);
			pbr_i32(r);
			(void)pbr_font40(r);
			int32_t ni = pbr_i32(r);
			if (ni < 0 || ni > 100000) { r->err = true; break; }
			for (int32_t i = 0; i < ni; i++) pbr_cstr(r);
			pbr_i32(r); pbr_i32(r);
			PE_SetSpecialComponentType(no, 5);
			parts_set_dims(p, &p->states[0].common, lw, lh);
			break;
		}
		case 6: { // ComboBox
			int32_t cw = pbr_i32(r), ch = pbr_i32(r);
			pbr_i32(r); pbr_i32(r);
			pbr_cstr(r);
			pbr_cstr(r);
			(void)pbr_font40(r);
			pbr_i32(r);
			PE_SetSpecialComponentType(no, 6);
			parts_set_dims(p, &p->states[0].common, cw, ch);
			break;
		}
		case 7: { // MultiTextBox
			int32_t mw = pbr_i32(r), mh = pbr_i32(r);
			(void)pbr_font40(r);
			pbr_bool(r);
			const char *mtxt = pbr_cstr(r);
			for (int i = 0; i < 15; i++) pbr_i32(r);
			pbr_cstr(r);
			pbr_i32(r); pbr_i32(r);
			PE_SetSpecialComponentType(no, 7);
			parts_set_dims(p, &p->states[0].common, mw, mh);
			struct string *ts = make_string(mtxt, (unsigned)strlen(mtxt));
			PE_SetTextBoxText(no, ts);
			free_string(ts);
			break;
		}
		case 8: { // LayoutBox
			int32_t ltype = pbr_i32(r);
			bool ret_flag = pbr_bool(r);
			float ret_size = version < 4 ? (float)pbr_i32(r) : pbr_f32(r);
			bool ret_rate = version >= 4 ? pbr_bool(r) : false;
			int32_t align = pbr_i32(r);
			int32_t pt0 = pbr_i32(r), pb0 = pbr_i32(r), pl0 = pbr_i32(r), pr0 = pbr_i32(r);
			PE_SetLayoutBoxLayoutType(no, ltype);
			PE_SetLayoutBoxAlign(no, align);
			PE_SetLayoutBoxReturnF(no, ret_flag, ret_size);
			PE_SetLayoutBoxReturnSizeForRate(no, ret_rate);
			PE_set_layoutbox_padding(no, pt0, pb0, pl0, pr0);
			break;
		}
		case 9: { // RadioButtonBox
			int32_t nid = pbr_i32(r);
			if (nid < 0 || nid > 10000) { r->err = true; break; }
			for (int32_t i = 0; i < nid; i++) pbr_i32(r);
			int32_t nnm = pbr_i32(r);
			if (nnm < 0 || nnm > 10000) { r->err = true; break; }
			plinks[idx].nr_radio = nnm;
			if (nnm > 0) {
				plinks[idx].radio_names = xmalloc((size_t)nnm * sizeof(const char *));
				for (int32_t i = 0; i < nnm; i++)
					plinks[idx].radio_names[i] = pbr_cstr(r);
			}
			PE_SetSpecialComponentType(no, 9);
			break;
		}
		case 10: { // State Part
			bool pix = pbr_bool(r);
			PE_SetPartsPixelDecide(no, pix);
			int32_t nst = pbr_i32(r);
			if (nst < 0 || nst > 10) { r->err = true; break; }
			for (int32_t s = 0; s < nst && !r->err; s++) {
				int32_t st = pbr_i32(r);
				bool apply = (s >= 1 && s <= PARTS_NR_STATES);
				int state = s;
				switch (st) {
				case 10:
					break;
				case 11: { // CG
					const char *cg_c = pbr_cstr(r);
					int32_t deform = pbr_i32(r);
					int32_t sa[4];
					for (int i = 0; i < 4; i++) sa[i] = pbr_i32(r);
					if (apply) {
						parts_get_cg(p, state - 1);
						if (cg_c[0]) {
							struct string *cs = make_string(cg_c, (unsigned)strlen(cg_c));
							PE_SetPartsCG(no, cs, deform, state);
							free_string(cs);
						}
						if (sa[2] > 0 && sa[3] > 0)
							PE_SetPartsCGSurfaceArea(no, sa[0], sa[1], sa[2], sa[3], state);
					}
					break;
				}
				case 12: { // LoopCG
					const char *cg_c = pbr_cstr(r);
					int32_t start_no = pbr_i32(r), nr_frames = pbr_i32(r), ftime = pbr_i32(r);
					int32_t sa[4];
					for (int i = 0; i < 4; i++) sa[i] = pbr_i32(r);
					if (apply && cg_c[0]) {
						struct string *cs = make_string(cg_c, (unsigned)strlen(cg_c));
						PE_SetLoopCG(no, cs, start_no, nr_frames, ftime, state);
						free_string(cs);
						if (sa[2] > 0 && sa[3] > 0)
							PE_SetLoopCGSurfaceArea(no, sa[0], sa[1], sa[2], sa[3], state);
					}
					break;
				}
				case 13: { // Text
					struct pact_font48 f1 = pbr_font48(r);
					struct pact_font48 f2 = pbr_font48(r);
					bool b0 = pbr_bool(r);
					int32_t v7[7];
					for (int i = 0; i < 7; i++) v7[i] = pbr_i32(r);
					int32_t nl = pbr_i32(r);
					if (nl < 0 || nl > 100000) { r->err = true; break; }
					for (int32_t i = 0; i < nl; i++)
						for (int j = 0; j < 5; j++) pbr_i32(r);
					const char *tx_c = pbr_cstr(r);
					bool b1 = pbr_bool(r), b2 = pbr_bool(r);
					(void)b1;
					if (apply) {
						PE_SetPartsFontType(no, f1.type, state);
						PE_SetPartsFontSize(no, f1.size, state);
						PE_SetPartsFontColor(no, f1.r, f1.g, f1.b, state);
						PE_SetPartsFontBoldWeight(no, f1.bold, state);
						PE_SetPartsFontEdgeWeight(no, f1.edge, state);
						PE_SetPartsFontEdgeColor(no, f1.er, f1.eg, f1.eb, state);
						PE_SetTextCharSpace(no, f1.cspace, state);
						PE_SetTextLineSpace(no, f1.lspace, state);
						struct parts_text *pt = parts_get_text(p, state - 1);
						pt->show_time = v7[1];
						pt->text_pos = v7[2];
						pt->enable_tag = b0;
						pt->count_return = b2;
						pt->ruby_ts.face = f2.type;
						pt->ruby_ts.size = f2.size;
						pt->ruby_ts.color = (SDL_Color){ f2.r, f2.g, f2.b, 255 };
						pt->ruby_ts.bold_width = f2.bold;
						pt->ruby_ts.edge_color = (SDL_Color){ f2.er, f2.eg, f2.eb, 255 };
						text_style_set_edge_width(&pt->ruby_ts, f2.edge);
						pt->ruby_char_space = f2.cspace;
						struct string *ts = make_string(tx_c, (unsigned)strlen(tx_c));
						PE_SetText(no, ts, state);
						free_string(ts);
						if (v7[5] > 0 && v7[6] > 0)
							PE_SetPartsTextSurfaceArea(no, v7[3], v7[4], v7[5], v7[6], state);
					}
					break;
				}
				case 14:
				case 15: { // HGauge / VGauge
					const char *cg_c = pbr_cstr(r);
					float num = pbr_f32(r), den = pbr_f32(r);
					int32_t sa[4];
					for (int i = 0; i < 4; i++) sa[i] = pbr_i32(r);
					if (apply) {
						struct string *cs = make_string(cg_c, (unsigned)strlen(cg_c));
						if (st == 14) {
							if (cg_c[0]) PE_SetHGaugeCG(no, cs, state);
							PE_SetHGaugeRate(no, num, den, state);
							if (sa[2] > 0 && sa[3] > 0)
								PE_SetHGaugeSurfaceArea(no, sa[0], sa[1], sa[2], sa[3], state);
						} else {
							if (cg_c[0]) PE_SetVGaugeCG(no, cs, state);
							PE_SetVGaugeRate(no, num, den, state);
							if (sa[2] > 0 && sa[3] > 0)
								PE_SetVGaugeSurfaceArea(no, sa[0], sa[1], sa[2], sa[3], state);
						}
						free_string(cs);
					}
					break;
				}
				case 16: { // Numeral
					const char *cg_c = pbr_cstr(r);
					int32_t show_type = pbr_i32(r);
					int32_t nw = pbr_i32(r);
					if (nw < 0 || nw > 1000) { r->err = true; break; }
					int32_t w12[12] = {0};
					for (int32_t i = 0; i < nw; i++) {
						int32_t wv = pbr_i32(r);
						if (i < 12) w12[i] = wv;
					}
					int32_t num_val = pbr_i32(r);
					bool show_comma = pbr_bool(r);
					int32_t space = pbr_i32(r);
					struct pact_font40 nf = pbr_font40(r);
					bool full_pitch = pbr_bool(r);
					int32_t length = pbr_i32(r);
					bool show_pad = pbr_bool(r);
					int32_t sa[4];
					for (int i = 0; i < 4; i++) sa[i] = pbr_i32(r);
					if (apply) {
						PE_SetNumeralFont(no, nf.type, nf.size, nf.r, nf.g, nf.b, nf.bold, nf.er, nf.eg, nf.eb, nf.edge, state);
						struct string *cs = make_string(cg_c, (unsigned)strlen(cg_c));
						if (cg_c[0] && show_type == 0)
							PE_SetNumeralCG(no, cs, state);
						else if (cg_c[0] && show_type == 1)
							PE_SetNumeralLinkedCGNumberWidthWidthList(no, cs,
								w12[0], w12[1], w12[2], w12[3], w12[4], w12[5],
								w12[6], w12[7], w12[8], w12[9], w12[10], w12[11], state);
						free_string(cs);
						PE_SetNumeralShowType(no, show_type, state);
						PE_SetNumeralSpace(no, space, state);
						PE_SetNumeralShowComma(no, show_comma, state);
						PE_SetNumeralFullPitch(no, full_pitch, state);
						PE_SetNumeralShowPadding(no, show_pad, state);
						PE_SetNumeralLength(no, length, state);
						PE_SetNumeralNumber(no, num_val, state);
						if (sa[2] > 0 && sa[3] > 0)
							PE_SetNumeralSurfaceArea(no, sa[0], sa[1], sa[2], sa[3], state);
					}
					break;
				}
				case 17: { // RectDetection
					pbr_bool(r);
					float f8[8];
					for (int i = 0; i < 8; i++) f8[i] = pbr_f32(r);
					int32_t sa[4];
					for (int i = 0; i < 4; i++) sa[i] = pbr_i32(r);
					(void)sa;
					if (apply)
						PE_SetRectangleDetectionPoint(no, (int)f8[0], (int)f8[1], (int)f8[2], (int)f8[3],
							(int)f8[4], (int)f8[5], (int)f8[6], (int)f8[7], state);
					break;
				}
				case 18: { // ConstructionProcess
					int32_t nops = pbr_i32(r);
					if (nops < 0 || nops > 100000) { r->err = true; break; }
					if (apply)
						parts_get_construction_process(p, state - 1);
					for (int32_t i = 0; i < nops && !r->err; i++) {
						int32_t cmd = pbr_i32(r);
						switch (cmd) {
						case 0: {
							int32_t dw = pbr_i32(r), dh = pbr_i32(r);
							if (apply) PE_AddCreateToPartsConstructionProcess(no, dw, dh, state);
							break;
						}
						case 1: {
							int32_t dw = pbr_i32(r), dh = pbr_i32(r);
							if (apply) PE_AddCreatePixelOnlyToPartsConstructionProcess(no, dw, dh, state);
							break;
						}
						case 2: {
							const char *cg_c = pbr_cstr(r);
							if (apply) {
								struct string *cs = make_string(cg_c, (unsigned)strlen(cg_c));
								PE_AddCreateCGToProcess(no, cs, state);
								free_string(cs);
							}
							break;
						}
						case 3:
						case 10: {
							int32_t v[7];
							for (int j = 0; j < 7; j++) v[j] = pbr_i32(r);
							if (apply) {
								if (cmd == 3) PE_AddFillToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], v[4], v[5], v[6], state);
								else PE_AddDrawRectToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], v[4], v[5], v[6], state);
							}
							break;
						}
						case 4:
						case 6:
						case 18: {
							int32_t v[8];
							for (int j = 0; j < 8; j++) v[j] = pbr_i32(r);
							if (apply) {
								if (cmd == 4) PE_AddFillAlphaColorToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], state);
								else if (cmd == 6) PE_AddFillWithAlphaToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], state);
								else PE_AddDrawLineToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], state);
							}
							break;
						}
						case 5: {
							int32_t v[5];
							for (int j = 0; j < 5; j++) v[j] = pbr_i32(r);
							if (apply) PE_AddFillAMapToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], v[4], state);
							break;
						}
						case 7:
						case 8: {
							int32_t dx = pbr_i32(r), dy = pbr_i32(r);
							const char *tx_c = pbr_cstr(r);
							int32_t ftype = pbr_i32(r), fsize = pbr_i32(r);
							int32_t fr = pbr_i32(r), fg = pbr_i32(r), fb = pbr_i32(r);
							float bold = pbr_f32(r);
							int32_t er = pbr_i32(r), eg = pbr_i32(r), eb = pbr_i32(r);
							float edge = pbr_f32(r);
							int32_t cs0 = pbr_i32(r), ls0 = pbr_i32(r);
							if (apply) {
								struct string *ts = make_string(tx_c, (unsigned)strlen(tx_c));
								if (cmd == 7)
									PE_AddDrawTextToPartsConstructionProcess(no, dx, dy, ts, ftype, fsize, fr, fg, fb, bold, er, eg, eb, edge, cs0, ls0, state);
								else
									PE_AddCopyTextToPartsConstructionProcess(no, dx, dy, ts, ftype, fsize, fr, fg, fb, bold, er, eg, eb, edge, cs0, ls0, state);
								free_string(ts);
							}
							break;
						}
						case 9: {
							int32_t v[10];
							for (int j = 0; j < 10; j++) v[j] = pbr_i32(r);
							if (apply)
								PE_AddFillGradationHorizonToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], state);
							break;
						}
						case 11:
						case 12:
						case 13:
						case 14: {
							const char *cg_c = pbr_cstr(r);
							int32_t v[9];
							for (int j = 0; j < 9; j++) v[j] = pbr_i32(r);
							if (apply) {
								struct string *cs = make_string(cg_c, (unsigned)strlen(cg_c));
								int sw = (cmd == 11 || cmd == 12) ? v[2] : v[6];
								int sh = (cmd == 11 || cmd == 12) ? v[3] : v[7];
								if (cmd == 11 || cmd == 13)
									PE_AddDrawCutCGToPartsConstructionProcess(no, cs, v[0], v[1], v[2], v[3], v[4], v[5], sw, sh, v[8], state);
								else
									PE_AddCopyCutCGToPartsConstructionProcess(no, cs, v[0], v[1], v[2], v[3], v[4], v[5], sw, sh, v[8], state);
								free_string(cs);
							}
							break;
						}
						case 15: {
							int32_t v[4];
							for (int j = 0; j < 4; j++) v[j] = pbr_i32(r);
							bool full = pbr_bool(r);
							if (apply) PE_AddGrayFilterToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], full, state);
							break;
						}
						case 16:
						case 17: {
							int32_t v[7];
							for (int j = 0; j < 7; j++) v[j] = pbr_i32(r);
							bool full = pbr_bool(r);
							if (apply) {
								if (cmd == 16) PE_AddAddFilterToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], v[4], v[5], v[6], full, state);
								else PE_AddMulFilterToPartsConstructionProcess(no, v[0], v[1], v[2], v[3], v[4], v[5], v[6], full, state);
							}
							break;
						}
						default:
							r->err = true;
							break;
						}
					}
					int32_t sa[4];
					for (int i = 0; i < 4; i++) sa[i] = pbr_i32(r);
					if (apply && !r->err) {
						PE_BuildPartsConstructionProcess(no, state);
						if (sa[2] > 0 && sa[3] > 0)
							PE_SetPartsConstructionSurfaceArea(no, sa[0], sa[1], sa[2], sa[3], state);
					}
					break;
				}
				case 19: { // CGDetection
					const char *cg_c = pbr_cstr(r);
					int32_t sa[4];
					for (int i = 0; i < 4; i++) sa[i] = pbr_i32(r);
					(void)sa;
					if (apply && cg_c[0]) {
						struct string *cs = make_string(cg_c, (unsigned)strlen(cg_c));
						PE_SetPartsCGDetectionSize(no, cs, state);
						free_string(cs);
					}
					break;
				}
				case 20: { // Flat
					const char *fl_c = pbr_cstr(r);
					int32_t sa[4];
					for (int i = 0; i < 4; i++) sa[i] = pbr_i32(r);
					if (apply && fl_c[0]) {
						struct string *fs = make_string(fl_c, (unsigned)strlen(fl_c));
						PE_SetPartsFlat(no, fs, state);
						free_string(fs);
					}
					int32_t ncg = pbr_i32(r);
					if (ncg < 0 || ncg > 10000) { r->err = true; break; }
					for (int32_t i = 0; i < ncg; i++) {
						const char *ocg = pbr_cstr(r), *ncg_s = pbr_cstr(r);
						if (apply && ocg[0]) {
							struct string *os = make_string(ocg, (unsigned)strlen(ocg));
							struct string *ns = make_string(ncg_s, (unsigned)strlen(ncg_s));
							PE_ChangeFlatCG(no, os, ns, state);
							free_string(os);
							free_string(ns);
						}
					}
					int32_t nsnd = pbr_i32(r);
					if (nsnd < 0 || nsnd > 10000) { r->err = true; break; }
					for (int32_t i = 0; i < nsnd; i++) {
						const char *osnd = pbr_cstr(r);
						if (version >= 3) {
							const char *nsnd_s = pbr_cstr(r);
							if (apply && osnd[0]) {
								struct string *os = make_string(osnd, (unsigned)strlen(osnd));
								struct string *ns = make_string(nsnd_s, (unsigned)strlen(nsnd_s));
								PE_ChangeFlatSound(no, os, ns, state);
								free_string(os);
								free_string(ns);
							}
						} else {
							pbr_i32(r);
						}
					}
					float fps_rate = pbr_f32(r);
					if (apply) {
						PE_SetFlatFPSRate(no, fps_rate, state);
						if (sa[2] > 0 && sa[3] > 0)
							PE_SetPartsFlatSurfaceArea(no, sa[0], sa[1], sa[2], sa[3], state);
					}
					break;
				}
				case 21: // Sound
					pbr_bool(r);
					break;
				case 22: // Movie
					pbr_bool(r);
					for (int i = 0; i < 5; i++) pbr_i32(r);
					break;
				default:
					r->err = true;
					break;
				}
			}
			break;
		}
		default:
			r->err = true;
			break;
		}
	}

	if (!r->err) {
		for (int32_t idx = 0; idx < nr_parts; idx++) {
			int no = plinks[idx].no;
			struct parts *p = parts_try_get(no);
			if (!p) continue;
			for (int32_t c = 0; c < plinks[idx].nr_children; c++) {
				struct activity_part *cp = find_part_cstr(a, plinks[idx].children[c]);
				if (cp) PE_SetParentPartsNumber(cp->no, no);
			}
			struct activity_part *lp;
			if ((lp = find_part_cstr(a, plinks[idx].links[0])))
				PE_SetPartsAlphaClipperPartsNumber(no, lp->no);
			if ((lp = find_part_cstr(a, plinks[idx].links[1])))
				PE_SetOnCursorShowLinkPartsNumber(no, lp->no);
			for (int s = 0; s < 3; s++) {
				lp = find_part_cstr(a, plinks[idx].links[2 + s]);
				p->scroll_links[s] = lp ? lp->no : 0;
			}
			if ((lp = find_part_cstr(a, plinks[idx].links[5])))
				PE_SetComponentCheckBoxShowLinkNumber(no, lp->no);
			for (int32_t i = 0; i < plinks[idx].nr_radio; i++) {
				struct activity_part *rp = find_part_cstr(a, plinks[idx].radio_names[i]);
				if (rp) PE_AddRadioButtonBoxChild(no, rp->no);
			}
		}

		int32_t n_close = pbr_i32(r);
		if (n_close < 0 || n_close > 10000) r->err = true;
		for (int32_t i = 0; i < n_close && !r->err; i++) {
			const char *cn = pbr_cstr(r);
			struct string *cs = make_string(cn, (unsigned)strlen(cn));
			PE_AddActivityCloseParts(act_name, cs);
			free_string(cs);
		}

		int32_t n_intent = pbr_i32(r);
		if (n_intent < 0 || n_intent > 10000) r->err = true;
		for (int32_t i = 0; i < n_intent && !r->err; i++) {
			const char *src_c = pbr_cstr(r);
			int32_t nd = pbr_i32(r);
			if (nd < 0 || nd > 10000) { r->err = true; break; }
			const char **dsts = nd > 0 ? xmalloc((size_t)nd * sizeof(const char *)) : NULL;
			for (int32_t j = 0; j < nd; j++) dsts[j] = pbr_cstr(r);
			int32_t itype = pbr_i32(r);
			if (!r->err) {
				struct string *ss = make_string(src_c, (unsigned)strlen(src_c));
				if (nd == 0) {
					PE_SetActivityIntentData(act_name, ss, &EMPTY_STRING, itype);
				} else {
					for (int32_t j = 0; j < nd; j++) {
						struct string *ds = make_string(dsts[j], (unsigned)strlen(dsts[j]));
						if (j == 0) PE_SetActivityIntentData(act_name, ss, ds, itype);
						else PE_AddActivityIntentDataDestination(act_name, ss, ds);
						free_string(ds);
					}
				}
				free_string(ss);
			}
			free(dsts);
		}

		int32_t n_fmap = pbr_i32(r);
		if (n_fmap < 0 || n_fmap > 10000) r->err = true;
		for (int32_t i = 0; i < n_fmap && !r->err; i++) {
			const char *pn_c = pbr_cstr(r);
			int32_t nst = pbr_i32(r);
			if (nst < 0 || nst > 1000) { r->err = true; break; }
			struct string *pns = make_string(pn_c, (unsigned)strlen(pn_c));
			for (int32_t j = 0; j < nst && !r->err; j++) {
				int32_t st = pbr_i32(r);
				int32_t np = pbr_i32(r);
				if (np < 0 || np > 10000) { r->err = true; break; }
				for (int32_t k = 0; k < np && !r->err; k++) {
					const char *id_c = pbr_cstr(r), *fn_c = pbr_cstr(r);
					struct string *ids = make_string(id_c, (unsigned)strlen(id_c));
					struct string *fns = make_string(fn_c, (unsigned)strlen(fn_c));
					PE_SetActivityFunc(act_name, pns, st, ids, fns);
					free_string(ids);
					free_string(fns);
				}
			}
			free_string(pns);
		}

		int32_t n_endkey = pbr_i32(r);
		if (n_endkey < 0 || n_endkey > 10000) r->err = true;
		for (int32_t i = 0; i < n_endkey && !r->err; i++)
			PE_SetActivityEndKey(act_name, pbr_i32(r));
	}

	for (int32_t idx = 0; idx < nr_parts; idx++) {
		free(plinks[idx].children);
		free(plinks[idx].radio_names);
	}
	free(plinks);

	if (r->err) {
		PE_ReleaseActivity(act_name, NULL);
		return false;
	}
	PE_UpdateComponent(0);
	return true;
}

static bool load_binary_pact(struct string *act_name, const uint8_t *raw, size_t raw_size)
{
	if (!raw || raw_size < 4)
		return false;
	uint32_t usize;
	memcpy(&usize, raw, 4);
	if (usize == 0 || usize > 64u * 1024u * 1024u)
		return false;
	uint8_t *ubuf = malloc(usize);
	if (!ubuf)
		return false;
	uLongf dest_len = usize;
	int zrc = uncompress(ubuf, &dest_len, raw + 4, (uLong)(raw_size - 4));
	if (zrc != Z_OK || dest_len != usize) {
		free(ubuf);
		return false;
	}
	bool ok = parse_binary_pact_stream(act_name, ubuf, usize);
	free(ubuf);
	return ok;
}

bool PE_CrateActivityBinary(struct string *name, struct page **data)
{
	(void)name;
	(void)data;
	WARNING("PE_CrateActivityBinary is not implemented");
	return false;
}

bool PE_ReadActivityBinary(struct string *name, struct page **data)
{
	if (!data || !*data || (*data)->nr_vars <= 0)
		return false;
	size_t n = (size_t)(*data)->nr_vars;
	uint8_t *raw = xmalloc(n * 4);
	for (size_t i = 0; i < n; i++) {
		int32_t v = (*data)->values[i].i;
		memcpy(raw + i * 4, &v, 4);
	}
	bool ok = load_binary_pact(name, raw, n * 4) || parse_binary_pact_stream(name, raw, n * 4);
	free(raw);
	return ok;
}

static bool is_exf_buffer(const uint8_t *buf, size_t size)
{
	return buf && size >= 12 && !memcmp(buf, "HEAD", 4) && !memcmp(buf + 8, "EXTF", 4);
}

bool PE_ReadActivityFile(struct string *name, struct string *file, bool edit)
{
	struct archive_data *data = NULL;
	char *path = NULL;
	pact_lookup_asset(file->text, &data, &path);
	struct ex *ex = NULL;
	if (data) {
		if (!is_exf_buffer(data->data, data->size)) {
			bool ok = load_binary_pact(name, data->data, data->size);
			archive_free_data(data);
			return ok;
		}
		ex = ex_read(data->data, data->size);
		archive_free_data(data);
	} else if (path) {
		size_t raw_size = 0;
		uint8_t *raw = file_read(path, &raw_size);
		free(path);
		if (!raw)
			return false;
		if (!is_exf_buffer(raw, raw_size)) {
			bool ok = load_binary_pact(name, raw, raw_size);
			free(raw);
			return ok;
		}
		ex = ex_read(raw, raw_size);
		free(raw);
	}
	if (!ex) return false;
	struct ex_value *layout = block(ex, "アクティビティ");
	if (!layout || layout->type != EX_TREE || layout->tree->is_leaf || !PE_CreateActivity(name)) {
		ex_free(ex);
		return false;
	}
	struct activity *a = find_activity(name);
	struct ex_value *v = block2(ex, "扩大", "扩张");
	PE_SetActivityEXText(name, v && v->type == EX_STRING ? v->s : &EMPTY_STRING);
	v = block2(ex, "背景图片", "背景画像");
	if (v && v->type == EX_STRING) PE_SetActivityBG(name, v->s);
	v = block(ex, "结束キー");
	if (v && v->type == EX_LIST) {
		for (unsigned i = 0; i < v->list->nr_items; i++) {
			struct ex_value *kv = ex_list_get(v->list, i);
			if (kv && kv->type == EX_INT)
				PE_SetActivityEndKey(name, kv->i);
		}
	}
	for (unsigned i = 0; i < layout->tree->nr_children; i++) load_part(a, &layout->tree->children[i], 0, edit);
	for (unsigned i = 0; i < layout->tree->nr_children; i++) load_part_links(a, &layout->tree->children[i]);
	PE_UpdateComponent(0);
	ex_free(ex);
	return true;
}

static void free_activity_metadata(struct activity *a)
{
	while (a->parts) {
		struct activity_part *p = a->parts;
		a->parts = p->next;
		free_string(p->name);
		free(p);
	}
	mainex_v12_unregister_activity(a->ex_id);
	free_activity_intents_and_funcs(a);
	free_string(a->name);
	free_string(a->background);
	free_string(a->ex_text);
	free(a->keys);
	free(a);
}

static void clear_activities_metadata(void)
{
	while (activities) {
		struct activity *a = activities;
		activities = a->next;
		free_activity_metadata(a);
	}
}

void parts_activities_save(struct iarray_writer *w)
{
	int nr_activities = 0;
	for (struct activity *a = activities; a; a = a->next)
		nr_activities++;
	iarray_write(w, nr_activities);
	for (struct activity *a = activities; a; a = a->next) {
		iarray_write_string(w, a->name);
		iarray_write_string(w, a->background);
		iarray_write_string(w, a->ex_text);
		iarray_write(w, a->ex_id);
		iarray_write(w, a->nr_keys);
		for (int i = 0; i < a->nr_keys; i++)
			iarray_write(w, a->keys[i]);
		int nr_parts = 0;
		for (struct activity_part *p = a->parts; p; p = p->next) {
			if (parts_try_get(p->no))
				nr_parts++;
		}
		iarray_write(w, nr_parts);
		for (struct activity_part *p = a->parts; p; p = p->next) {
			if (!parts_try_get(p->no))
				continue;
			iarray_write_string(w, p->name);
			iarray_write(w, p->no);
			iarray_write(w, p->closed);
			iarray_write(w, p->locked);
		}
	}
}

void parts_activities_load(struct iarray_reader *r)
{
	clear_activities_metadata();
	int nr_activities = iarray_read(r);
	if (nr_activities < 0)
		return;
	struct activity **tail = &activities;
	for (int i = 0; i < nr_activities; i++) {
		struct activity *a = xcalloc(1, sizeof(*a));
		a->name = iarray_read_string(r);
		a->background = iarray_read_string(r);
		a->ex_text = iarray_read_string(r);
		int saved_ex_id = iarray_read(r);
		a->ex_id = saved_ex_id >= 2
			? mainex_v12_register_at(saved_ex_id, xcalloc(1, sizeof(struct ex)))
			: -1;
		a->nr_keys = iarray_read(r);
		a->keys = a->nr_keys > 0 ? xmalloc(a->nr_keys * sizeof(int)) : NULL;
		for (int k = 0; k < a->nr_keys; k++)
			a->keys[k] = iarray_read(r);
		int nr_parts = iarray_read(r);
		struct activity_part **ptail = &a->parts;
		for (int j = 0; j < nr_parts; j++) {
			struct string *pname = iarray_read_string(r);
			int no = iarray_read(r);
			bool closed = iarray_read(r);
			bool locked = iarray_read(r);
			if (parts_try_get(no)) {
				struct activity_part *p = xcalloc(1, sizeof(*p));
				p->name = pname;
				p->no = no;
				p->closed = closed;
				p->locked = locked;
				*ptail = p;
				ptail = &p->next;
			} else {
				free_string(pname);
			}
		}
		*tail = a;
		tail = &a->next;
	}
}

static void bind_legacy_tree(struct activity *a, struct ex_tree *tree, struct parts *part)
{
	if (!part)
		return;
	if (!find_part(a, tree->name)) {
		struct activity_part **at = &a->parts;
		while (*at && strcmp((*at)->name->text, tree->name->text) < 0)
			at = &(*at)->next;
		struct activity_part *bp = xcalloc(1, sizeof(*bp));
		bp->name = string_dup(tree->name);
		bp->no = part->no;
		bp->closed = number(tree, "閉じている", 0);
		bp->locked = number(tree, "ロックしている", 0);
		bp->next = *at;
		*at = bp;
	}
	struct ex_tree *children = node(tree, "子パーツ列表");
	if (!children)
		return;
	struct parts *child_part = TAILQ_FIRST(&part->children);
	for (unsigned i = 0; i < children->nr_children && child_part; i++) {
		if (!number(&children->children[i], "エディタ上表示", 1))
			continue;
		bind_legacy_tree(a, &children->children[i], child_part);
		child_part = TAILQ_NEXT(child_part, child_list_entry);
	}
}

static void recover_legacy_activity_from_pact(struct string *handle, struct parts *root_part)
{
	struct string *file = string_dup(handle);
	if (!PE_IsExistActivityFile(file)) {
		const char *colon = strrchr(handle->text, ':');
		if (colon && colon > handle->text && colon[1] != '\0') {
			bool all_digits = true;
			for (const char *c = colon + 1; *c; c++) {
				if (!isdigit((unsigned char)*c)) {
					all_digits = false;
					break;
				}
			}
			if (all_digits) {
				free_string(file);
				file = make_string(handle->text, colon - handle->text);
			}
		}
	}
	struct archive_data *data = NULL;
	char *path = NULL;
	pact_lookup_asset(file->text, &data, &path);
	struct ex *ex = NULL;
	if (data) {
		if (is_exf_buffer(data->data, data->size))
			ex = ex_read(data->data, data->size);
		archive_free_data(data);
	} else if (path) {
		size_t raw_size = 0;
		uint8_t *raw = file_read(path, &raw_size);
		if (is_exf_buffer(raw, raw_size))
			ex = ex_read(raw, raw_size);
		free(raw);
		free(path);
	}
	free_string(file);
	if (!ex)
		return;
	struct ex_value *layout = block(ex, "アクティビティ");
	if (!layout || layout->type != EX_TREE || layout->tree->is_leaf || !PE_CreateActivity(handle)) {
		ex_free(ex);
		return;
	}
	struct activity *a = find_activity(handle);
	struct ex_value *v = block(ex, "扩大");
	PE_SetActivityEXText(handle, v && v->type == EX_STRING ? v->s : &EMPTY_STRING);
	v = block(ex, "背景图片");
	if (v && v->type == EX_STRING)
		PE_SetActivityBG(handle, v->s);
	for (unsigned i = 0; i < layout->tree->nr_children; i++) {
		if (!number(&layout->tree->children[i], "エディタ上表示", 1))
			continue;
		bind_legacy_tree(a, &layout->tree->children[i], root_part);
		break;
	}
	ex_free(ex);
}

void parts_activities_reconcile_legacy(void)
{
	for (struct activity **at = &activities; *at; ) {
		struct activity *a = *at;
		for (struct activity_part **pt = &a->parts; *pt; ) {
			struct activity_part *p = *pt;
			if (!parts_try_get(p->no)) {
				*pt = p->next;
				free_string(p->name);
				free(p);
			} else {
				pt = &p->next;
			}
		}
		if (!a->parts) {
			*at = a->next;
			free_activity_metadata(a);
		} else {
			if (a->ex_id >= 2)
				a->ex_id = mainex_v12_register_at(a->ex_id, xcalloc(1, sizeof(struct ex)));
			at = &a->next;
		}
	}
	if (!ain || ain->version < 12 || !heap)
		return;
	int cwrap_s = ain_get_struct(ain, "activity::detail::CActivityWrap");
	int cparts_s = ain_get_struct(ain, "parts::detail::CParts");
	if (cwrap_s < 0 || cparts_s < 0)
		return;
	for (size_t i = 1; i < heap_size; i++) {
		struct page *pg = heap[i].page;
		if (!pg || pg->type != STRUCT_PAGE || pg->index != cwrap_s || pg->nr_vars < 4)
			continue;
		int h_slot = pg->values[2].i;
		if (h_slot <= 0 || (size_t)h_slot >= heap_size || !heap[h_slot].s || heap[h_slot].s->size <= 0)
			continue;
		struct string *handle = heap[h_slot].s;
		if (find_activity(handle))
			continue;
		int r_slot = pg->values[3].i;
		if (r_slot <= 0 || (size_t)r_slot >= heap_size || !heap[r_slot].page)
			continue;
		struct page *rpg = heap[r_slot].page;
		if (rpg->type != STRUCT_PAGE || rpg->index != cparts_s || rpg->nr_vars < 2)
			continue;
		struct parts *root_part = rpg->values[1].i > 0 ? parts_try_get(rpg->values[1].i) : NULL;
		if (!root_part)
			continue;
		recover_legacy_activity_from_pact(handle, root_part);
	}
}
