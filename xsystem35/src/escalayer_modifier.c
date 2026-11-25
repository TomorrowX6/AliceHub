#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "sdl_compat.h"
#include "cJSON.h"
#include "nact.h"
#include "variable.h"
#include "escalayer_modifier.h"

#define ESC_LOCK_HP_FULL     (1 << 0)
#define ESC_LOCK_LIBIDO_MAX  (1 << 1)
#define ESC_LOCK_ONEHIT      (1 << 2)
#define ESC_LOCK_LOVE_MAX    (1 << 3)

static pthread_mutex_t live_state_lock = PTHREAD_MUTEX_INITIALIZER;
static char *live_state_json;
static bool published;
static uint32_t last_publish_tick;

char *xsystem35_modifier_live_state(void) {
	pthread_mutex_lock(&live_state_lock);
#ifdef ALICEHUB_IOS
	char *out = live_state_json ? strdup(live_state_json) : NULL;
#else
	char *out = strdup(live_state_json ? live_state_json : "{}");
#endif
	pthread_mutex_unlock(&live_state_lock);
	return out;
}

static void publish_live_state_json(char *out) {
	pthread_mutex_lock(&live_state_lock);
	char *old = live_state_json;
	live_state_json = out;
	pthread_mutex_unlock(&live_state_lock);
	free(old);
}

static pthread_mutex_t action_lock = PTHREAD_MUTEX_INITIALIZER;
static int esc_live_locks = 0;
#define MAX_PENDING_ACTIONS 16
static char pending_actions[MAX_PENDING_ACTIONS][64];
static int pending_action_count = 0;

static struct {
	char **ain_ptr;
	int varnum;
	bool checked;
	bool is_escalayer;
	int hp, hp_max;
	int atk, def, rsp;
	int libido, libido_max;
	int d2, love, training, peace;
	int enemy_hp, enemy_hp_max;
	int clear_count, ed_flags, cg_seen;
	int easy_menu, easy_menu2, hard_menu, hissatsu_flags;
	int recall_vars[16];
	int recall_count;
} esc_cache = {
	.hp = -1, .hp_max = -1, .atk = -1, .def = -1, .rsp = -1,
	.libido = -1, .libido_max = -1, .d2 = -1, .love = -1,
	.training = -1, .peace = -1, .enemy_hp = -1, .enemy_hp_max = -1,
	.clear_count = -1, .ed_flags = -1, .cg_seen = -1,
	.easy_menu = -1, .easy_menu2 = -1, .hard_menu = -1, .hissatsu_flags = -1,
};

static int find_var_names(const char *sjis_name, const char *utf8_name) {
	for (int i = 0; i < nact->ain.varnum; i++) {
		const char *n = v_name(i);
		if (!n)
			continue;
		if ((sjis_name && strcmp(n, sjis_name) == 0) ||
			(utf8_name && strcmp(n, utf8_name) == 0))
			return i;
	}
	return -1;
}

static void refresh_escalayer_cache(void) {
	if (!nact || nact->ain.varnum <= 0 || !nact->ain.var)
		return;
	if (esc_cache.checked && esc_cache.ain_ptr == nact->ain.var && esc_cache.varnum == nact->ain.varnum)
		return;

	memset(&esc_cache, 0, sizeof(esc_cache));
	esc_cache.ain_ptr = nact->ain.var;
	esc_cache.varnum = nact->ain.varnum;
	esc_cache.checked = true;

	// System39.ain stores VARI names in Shift_JIS (CP932); accept UTF-8 too if symbol file is loaded.
	esc_cache.hp = find_var_names("\x81\x99\x82\x67\x82\x6f", "☆ＨＰ");
	esc_cache.hp_max = find_var_names("\x81\x99\x82\x67\x82\x6f\x82\x6c\x82\x60\x82\x77", "☆ＨＰＭＡＸ");
	esc_cache.atk = find_var_names("\x81\x99\x8d\x55\x8c\x82\x97\xcd", "☆攻撃力");
	esc_cache.def = find_var_names("\x81\x99\x96\x68\x8c\xe4\x97\xcd", "☆防御力");
	esc_cache.rsp = find_var_names("\x81\x99\x94\xbd\x89\x9e\x97\xcd", "☆反応力");
	esc_cache.libido = find_var_names("\x81\x99\x83\x8a\x83\x72\x83\x68\x81\x5b", "☆リビドー");
	esc_cache.libido_max = find_var_names("\x81\x99\x83\x8a\x83\x72\x83\x68\x81\x5b\x82\x6c\x82\x60\x82\x77", "☆リビドーＭＡＸ");
	esc_cache.d2 = find_var_names("\x81\x99\x82\x63\x82\x51", "☆Ｄ２");
	esc_cache.love = find_var_names("\x81\x99\x88\xa4\x8f\xee", "☆愛情");
	esc_cache.training = find_var_names("\x81\x99\x92\xb2\x8b\xb3", "☆調教");
	esc_cache.peace = find_var_names("\x81\x99\x83\x73\x81\x5b\x83\x58", "☆ピース");
	esc_cache.enemy_hp = find_var_names("\x81\x99\x93\x47\x81\x5e\x8c\xbb\x91\xcc\x97\xcd", "☆敵／現体力");
	esc_cache.enemy_hp_max = find_var_names("\x81\x99\x93\x47\x81\x5e\x91\x8d\x91\xcc\x97\xcd", "☆敵／総体力");
	esc_cache.clear_count = find_var_names("\x81\xa6\x83\x4e\x83\x8a\x83\x41\x89\xf1\x90\x94", "※クリア回数");
	esc_cache.easy_menu = find_var_names("\x81\xa6\x83\x43\x81\x5b\x83\x57\x81\x5b\x83\x81\x83\x6a\x83\x85\x81\x5b", "※イージーメニュー");
	esc_cache.easy_menu2 = find_var_names("\x81\xa6\x83\x43\x81\x5b\x83\x57\x81\x5b\x83\x81\x83\x6a\x83\x85\x81\x5b\x82\x51", "※イージーメニュー２");
	esc_cache.hard_menu = find_var_names("\x81\xa6\x83\x6e\x81\x5b\x83\x68\x83\x81\x83\x6a\x83\x85\x81\x5b", "※ハードメニュー");
	esc_cache.ed_flags = find_var_names("\x81\xa6\x82\x64\x82\x63", "※ＥＤ");
	esc_cache.hissatsu_flags = find_var_names("\x81\xa6\x95\x4b\x8e\x45\x8b\x5a", "※必殺技");
	esc_cache.cg_seen = find_var_names("\x81\xa6\x82\x62\x82\x66\x8c\xa9\x82\xbd", "※ＣＧ見た");

	esc_cache.is_escalayer = (esc_cache.hp >= 0 && esc_cache.libido >= 0 && esc_cache.d2 >= 0);
	for (int i = 0; i < nact->ain.varnum && esc_cache.recall_count < 16; i++) {
		const char *n = v_name(i);
		if (n && (strncmp(n, "\x81\xa6\x89\xf1\x91\x7a\x81\x5e", 8) == 0 ||
			strncmp(n, "※回想／", strlen("※回想／")) == 0))
			esc_cache.recall_vars[esc_cache.recall_count++] = i;
	}
}

static inline bool esc_valid_var(int idx) {
	return idx >= 0 && idx < nact->ain.varnum && idx < 65537;
}

static void esc_unlock_var_all(int var) {
	if (!esc_valid_var(var))
		return;
	struct VarRef ref;
	if (v_ref_indexed(var, 0, &ref) && ref.page > 0 && ref.page < 256 && varPage[ref.page].value) {
		int len = v_boundSliceSize(&ref);
		for (int i = ref.index; i < ref.index + len; i++) {
			if (varPage[ref.page].value[i] == 0)
				varPage[ref.page].value[i] = 1;
		}
	} else {
		if (sysVar[var] == 0)
			sysVar[var] = 1;
	}
}

static int esc_count_unlocked(int var) {
	if (!esc_valid_var(var))
		return 0;
	struct VarRef ref;
	if (v_ref_indexed(var, 0, &ref) && ref.page > 0 && ref.page < 256 && varPage[ref.page].value) {
		int len = v_boundSliceSize(&ref);
		int cnt = 0;
		for (int i = ref.index; i < ref.index + len; i++) {
			if (varPage[ref.page].value[i] != 0)
				cnt++;
		}
		return cnt;
	}
	return sysVar[var];
}

static vmvar_t clamp_u16(int v) {
	if (v < 0) return 0;
	if (v > 65535) return 65535;
	return (vmvar_t)v;
}

static void apply_single_action_sdl_thread(const char *spec) {
	refresh_escalayer_cache();
	if (!esc_cache.is_escalayer)
		return;

	if (strcmp(spec, "esc_heal") == 0) {
		if (esc_valid_var(esc_cache.hp) && esc_valid_var(esc_cache.hp_max)) {
			uint16_t maxv = sysVar[esc_cache.hp_max];
			if (maxv > 0) sysVar[esc_cache.hp] = maxv;
		}
	} else if (strcmp(spec, "esc_libido_max") == 0) {
		if (esc_valid_var(esc_cache.libido) && esc_valid_var(esc_cache.libido_max)) {
			uint16_t maxv = sysVar[esc_cache.libido_max];
			if (maxv > 0) sysVar[esc_cache.libido] = maxv;
		}
	} else if (strcmp(spec, "esc_enemy_onehit") == 0) {
		if (esc_valid_var(esc_cache.enemy_hp) && sysVar[esc_cache.enemy_hp] > 1)
			sysVar[esc_cache.enemy_hp] = 1;
	} else if (strcmp(spec, "esc_love_max") == 0) {
		if (esc_valid_var(esc_cache.love)) sysVar[esc_cache.love] = clamp_u16(sysVar[esc_cache.love] < 100 ? 100 : sysVar[esc_cache.love] + 50);
		if (esc_valid_var(esc_cache.training)) sysVar[esc_cache.training] = clamp_u16(sysVar[esc_cache.training] + 50);
	} else if (strcmp(spec, "esc_peace_100") == 0) {
		if (esc_valid_var(esc_cache.peace)) sysVar[esc_cache.peace] = 100;
	} else if (strcmp(spec, "esc_stats_boost") == 0) {
		if (esc_valid_var(esc_cache.hp_max) && sysVar[esc_cache.hp_max] < 9999)
			sysVar[esc_cache.hp_max] = 9999;
		if (esc_valid_var(esc_cache.hp) && esc_valid_var(esc_cache.hp_max))
			sysVar[esc_cache.hp] = sysVar[esc_cache.hp_max];
		if (esc_valid_var(esc_cache.atk)) sysVar[esc_cache.atk] = clamp_u16(sysVar[esc_cache.atk] + 300);
		if (esc_valid_var(esc_cache.def)) sysVar[esc_cache.def] = clamp_u16(sysVar[esc_cache.def] + 300);
		if (esc_valid_var(esc_cache.rsp)) sysVar[esc_cache.rsp] = clamp_u16(sysVar[esc_cache.rsp] + 100);
	} else if (strcmp(spec, "esc_unlock_gallery") == 0) {
		esc_unlock_var_all(esc_cache.clear_count);
		esc_unlock_var_all(esc_cache.easy_menu);
		esc_unlock_var_all(esc_cache.easy_menu2);
		esc_unlock_var_all(esc_cache.hard_menu);
		esc_unlock_var_all(esc_cache.ed_flags);
		esc_unlock_var_all(esc_cache.hissatsu_flags);
		esc_unlock_var_all(esc_cache.cg_seen);
		for (int i = 0; i < esc_cache.recall_count; i++)
			esc_unlock_var_all(esc_cache.recall_vars[i]);
	} else if (strncmp(spec, "esc_atk_add=", 12) == 0 && esc_valid_var(esc_cache.atk)) {
		sysVar[esc_cache.atk] = clamp_u16(sysVar[esc_cache.atk] + atoi(spec + 12));
	} else if (strncmp(spec, "esc_def_add=", 12) == 0 && esc_valid_var(esc_cache.def)) {
		sysVar[esc_cache.def] = clamp_u16(sysVar[esc_cache.def] + atoi(spec + 12));
	} else if (strncmp(spec, "esc_rsp_add=", 12) == 0 && esc_valid_var(esc_cache.rsp)) {
		sysVar[esc_cache.rsp] = clamp_u16(sysVar[esc_cache.rsp] + atoi(spec + 12));
	} else if (strncmp(spec, "esc_d2_set=", 11) == 0 && esc_valid_var(esc_cache.d2)) {
		sysVar[esc_cache.d2] = clamp_u16(atoi(spec + 11));
	}
}

static void apply_escalayer_locks_sdl_thread(int locks) {
	if (!locks)
		return;
	refresh_escalayer_cache();
	if (!esc_cache.is_escalayer)
		return;
	if ((locks & ESC_LOCK_HP_FULL) && esc_valid_var(esc_cache.hp) && esc_valid_var(esc_cache.hp_max)) {
		uint16_t m = sysVar[esc_cache.hp_max];
		if (m > 0 && sysVar[esc_cache.hp] < m)
			sysVar[esc_cache.hp] = m;
	}
	if ((locks & ESC_LOCK_LIBIDO_MAX) && esc_valid_var(esc_cache.libido) && esc_valid_var(esc_cache.libido_max)) {
		uint16_t m = sysVar[esc_cache.libido_max];
		if (m > 0 && sysVar[esc_cache.libido] < m)
			sysVar[esc_cache.libido] = m;
	}
	if ((locks & ESC_LOCK_ONEHIT) && esc_valid_var(esc_cache.enemy_hp)) {
		if (sysVar[esc_cache.enemy_hp] > 1)
			sysVar[esc_cache.enemy_hp] = 1;
	}
	if ((locks & ESC_LOCK_LOVE_MAX) && esc_valid_var(esc_cache.love)) {
		if (sysVar[esc_cache.love] < 100)
			sysVar[esc_cache.love] = 100;
	}
}
static void update_live_state_sdl_thread(int locks) {
	uint32_t now = SDL_GetTicks();
	if (published && (uint32_t)(now - last_publish_tick) < 250)
		return;
	published = true;
	last_publish_tick = now;
	refresh_escalayer_cache();
	if (!esc_cache.is_escalayer) {
		publish_live_state_json(strdup("{\"game\":\"system3\",\"ready\":true}"));
		return;
	}

	cJSON *root = cJSON_CreateObject();
	cJSON_AddStringToObject(root, "game", "escalayer");
	cJSON_AddBoolToObject(root, "ready", true);
	if (esc_valid_var(esc_cache.hp)) cJSON_AddNumberToObject(root, "hp", sysVar[esc_cache.hp]);
	if (esc_valid_var(esc_cache.hp_max)) cJSON_AddNumberToObject(root, "hpMax", sysVar[esc_cache.hp_max]);
	if (esc_valid_var(esc_cache.atk)) cJSON_AddNumberToObject(root, "atk", sysVar[esc_cache.atk]);
	if (esc_valid_var(esc_cache.def)) cJSON_AddNumberToObject(root, "def", sysVar[esc_cache.def]);
	if (esc_valid_var(esc_cache.rsp)) cJSON_AddNumberToObject(root, "rsp", sysVar[esc_cache.rsp]);
	if (esc_valid_var(esc_cache.libido)) cJSON_AddNumberToObject(root, "libido", sysVar[esc_cache.libido]);
	if (esc_valid_var(esc_cache.libido_max)) cJSON_AddNumberToObject(root, "libidoMax", sysVar[esc_cache.libido_max]);
	if (esc_valid_var(esc_cache.d2)) cJSON_AddNumberToObject(root, "d2", sysVar[esc_cache.d2]);
	if (esc_valid_var(esc_cache.love)) cJSON_AddNumberToObject(root, "love", sysVar[esc_cache.love]);
	if (esc_valid_var(esc_cache.training)) cJSON_AddNumberToObject(root, "training", sysVar[esc_cache.training]);
	if (esc_valid_var(esc_cache.peace)) cJSON_AddNumberToObject(root, "peace", sysVar[esc_cache.peace]);
	if (esc_valid_var(esc_cache.enemy_hp)) cJSON_AddNumberToObject(root, "enemyHp", sysVar[esc_cache.enemy_hp]);
	if (esc_valid_var(esc_cache.enemy_hp_max)) cJSON_AddNumberToObject(root, "enemyHpMax", sysVar[esc_cache.enemy_hp_max]);
	if (esc_valid_var(esc_cache.clear_count)) cJSON_AddNumberToObject(root, "clearCount", sysVar[esc_cache.clear_count]);
	if (esc_valid_var(esc_cache.ed_flags)) cJSON_AddNumberToObject(root, "edFlags", esc_count_unlocked(esc_cache.ed_flags));
	if (esc_valid_var(esc_cache.cg_seen)) cJSON_AddNumberToObject(root, "cgSeen", esc_count_unlocked(esc_cache.cg_seen));
	cJSON_AddNumberToObject(root, "locks", locks);

	char *out = cJSON_PrintUnformatted(root);
	cJSON_Delete(root);
	publish_live_state_json(out);
}

void xsystem35_modifier_set_locks(int mask)
{
	pthread_mutex_lock(&action_lock);
	esc_live_locks = mask;
	pthread_mutex_unlock(&action_lock);
}

int xsystem35_modifier_get_locks(void)
{
	pthread_mutex_lock(&action_lock);
	int mask = esc_live_locks;
	pthread_mutex_unlock(&action_lock);
	return mask;
}

void xsystem35_modifier_apply_action(const char *action)
{
	pthread_mutex_lock(&action_lock);
	if (pending_action_count < MAX_PENDING_ACTIONS) {
		snprintf(pending_actions[pending_action_count], sizeof(pending_actions[0]), "%s", action);
		pending_action_count++;
	}
	pthread_mutex_unlock(&action_lock);
}

bool escalayer_modifier_available(void)
{
	refresh_escalayer_cache();
	return esc_cache.is_escalayer;
}

void escalayer_modifier_tick(void)
{
	char actions[MAX_PENDING_ACTIONS][sizeof(pending_actions[0])];
	pthread_mutex_lock(&action_lock);
	int count = pending_action_count;
	memcpy(actions, pending_actions, count * sizeof(actions[0]));
	pending_action_count = 0;
	int locks = esc_live_locks;
	pthread_mutex_unlock(&action_lock);

	for (int i = 0; i < count; i++)
		apply_single_action_sdl_thread(actions[i]);
	apply_escalayer_locks_sdl_thread(locks);
	update_live_state_sdl_thread(locks);
}

void escalayer_modifier_reset(bool active)
{
	memset(&esc_cache, 0, sizeof(esc_cache));
	published = false;
	pthread_mutex_lock(&action_lock);
	pending_action_count = 0;
	esc_live_locks = 0;
	pthread_mutex_unlock(&action_lock);
	publish_live_state_json(active ? strdup("{\"game\":\"system3\"}") : NULL);
}
