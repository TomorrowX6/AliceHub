/* PACT v6 / parts v34 layouts and activity ownership, GPL-2.0-or-later. */
#include <ctype.h>
#include <stdlib.h>
#include <string.h>

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

struct activity {
	struct string *name, *background, *ex_text;
	struct activity_part *parts;
	int ex_id, nr_keys, *keys;
	struct activity *next;
};

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

static char *activity_file_path(struct string *file)
{
	char *path = gamedir_path_icase(file->text);
	if (path && file_exists(path)) return path;
	free(path);
	char *name = xmalloc(file->size + 8);
	memcpy(name, file->text, file->size);
	strcpy(name + file->size, ".pactex");
	path = gamedir_path_icase(name);
	free(name);
	return path;
}

bool PE_IsExistActivityFile(struct string *file)
{
	if (asset_exists_by_name(ASSET_PACT, file->text, NULL)) return true;
	char *path = activity_file_path(file);
	bool exists = path && file_exists(path);
	free(path);
	return exists;
}

bool PE_ReadActivityFile(struct string *name, struct string *file, bool edit)
{
	struct archive_data *data = asset_get_by_name(ASSET_PACT, file->text, NULL);
	struct ex *ex;
	if (data) {
		ex = ex_read(data->data, data->size);
		archive_free_data(data);
	} else {
		char *path = activity_file_path(file);
		ex = path ? ex_read_file(path) : NULL;
		free(path);
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
	struct archive_data *data = asset_get_by_name(ASSET_PACT, file->text, NULL);
	struct ex *ex = NULL;
	if (data) {
		ex = ex_read(data->data, data->size);
		archive_free_data(data);
	} else {
		char *path = activity_file_path(file);
		ex = path ? ex_read_file(path) : NULL;
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
