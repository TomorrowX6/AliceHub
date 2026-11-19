/*
 * AliceHub Android JNI bridge for xsystem35 (System 3.9):
 * - Virtual gamepad, physical Android controller, cursor overlay, and Ctrl skip
 * - Escalayer live state reading, locks, and actions
 * - Full GameModifierBridge JNI surface expected by the :game process
 */

#include "config.h"

#ifdef __ANDROID__

#include <jni.h>
#include <math.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "sdl_compat.h"
#include "cJSON.h"
#include "gfx.h"
#include "input.h"
#include "msgskip.h"
#include "nact.h"
#include "s39ain.h"
#include "system.h"
#include "variable.h"
#include "alicehub_bridge.h"

// Keep in sync with XSystem4Activity.kt and GamepadBridge.kt
#define COMMAND_TOGGLE_CTRL_OVERLAY 0x8002
#define COMMAND_CURSOR_STATE        0x8004
#define COMMAND_GAMEPAD_UI_EVENT    0x8005

#define GAMEPAD_UI_EMU_CMD          1

#define ACTION_NONE        0
#define ACTION_VK          1
#define ACTION_MOUSE_BTN   2
#define ACTION_MOUSE_WHEEL 3
#define ACTION_CTRL_TOGGLE 4
#define ACTION_JOYPAD_BTN  5
#define ACTION_COMBO       6
#define ACTION_EMU_CMD     7

#define STICK_NONE         0
#define STICK_MOUSE        1
#define STICK_ARROWS       2
#define STICK_WASD         3
#define STICK_WHEEL        4
#define STICK_JOYPAD       5

#define EMU_TOGGLE_VPAD    1
#define EMU_OPEN_MENU      2
#define EMU_TOGGLE_CURSOR  3
#define EMU_SCREENSHOT     4
#define EMU_OPEN_EDITOR    5

#define PAD_A              0
#define PAD_B              1
#define PAD_X              2
#define PAD_Y              3
#define PAD_BACK           4
#define PAD_GUIDE          5
#define PAD_START          6
#define PAD_L3             7
#define PAD_R3             8
#define PAD_LB             9
#define PAD_RB             10
#define PAD_DPAD_UP        11
#define PAD_DPAD_DOWN      12
#define PAD_DPAD_LEFT      13
#define PAD_DPAD_RIGHT     14
#define PAD_LT             15
#define PAD_RT             16
#define NR_PAD_SLOTS       17

#define MAX_VPAD_SLOTS     32
#define WHEEL_REPEAT_MS    120

#define ESC_LOCK_HP_FULL     (1 << 0)
#define ESC_LOCK_LIBIDO_MAX  (1 << 1)
#define ESC_LOCK_ONEHIT      (1 << 2)
#define ESC_LOCK_LOVE_MAX    (1 << 3)

typedef struct {
	uint8_t type;
	int16_t code;
	int16_t code2;
	bool toggle_mode;
} action_spec_t;

typedef struct {
	bool phys_down;
	bool pending_press_edge;
	action_spec_t pending_action;
	bool prev_phys_down;
	bool latched;
	action_spec_t action;
} vpad_slot_t;

static pthread_mutex_t bridge_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t live_state_lock = PTHREAD_MUTEX_INITIALIZER;
static char *live_state_json;

static char *copy_live_state_json(void) {
	pthread_mutex_lock(&live_state_lock);
	char *out = strdup(live_state_json ? live_state_json : "{}");
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

static void update_live_state_sdl_thread(int locks);

static struct {
	bool initialized;
	bool phys_enabled;
	bool swap_ab_xy;
	int phys_left_stick_mode;
	int phys_right_stick_mode;
	int phys_dpad_mode;
	int vpad_left_stick_mode;
	int vpad_right_stick_mode;
	float deadzone;
	float cursor_speed_px;
	action_spec_t phys_bindings[NR_PAD_SLOTS];
} gp_cfg;

static float vpad_stick_x[2];
static float vpad_stick_y[2];
static int vpad_dpad_mode = STICK_NONE;
static bool vpad_dpad_dir[4]; // L, R, U, D
static vpad_slot_t vpad_slots[MAX_VPAD_SLOTS];

static struct {
	bool active;
	int device_id;
	bool btn_down[NR_PAD_SLOTS];
	bool prev_down[NR_PAD_SLOTS];
	bool latched[NR_PAD_SLOTS];
	float axes[6]; // lx, ly, rx, ry, lt, rt
	float hat_x, hat_y;
} android_pad;

// Cursor and applied state (SDL_main thread only after pump)
static float cursor_x = -1.0f;
static float cursor_y = -1.0f;
static bool cursor_visible = false;
static bool pending_cursor_reset = false;
static bool pending_cursor_vis = false;
static uint32_t last_pump_tick = 0;
static uint32_t last_wheel_tick = 0;
static int prev_stick_wheel_dir = 0;

static bool ctrl_skip_latched = false;
static bool ctrl_skip_applied = false;
static bool pending_ctrl_set = false;
static bool pending_ctrl_val = false;

static bool bridge_vk_down[256];
static int bridge_mouse_mask = 0;
static int bridge_joy_mask = 0;

// Escalayer modifier state
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

static void init_cfg_defaults_locked(void) {
	if (gp_cfg.initialized)
		return;
	gp_cfg.initialized = true;
	gp_cfg.phys_enabled = true;
	gp_cfg.swap_ab_xy = false;
	gp_cfg.phys_left_stick_mode = STICK_MOUSE;
	gp_cfg.phys_right_stick_mode = STICK_WHEEL;
	gp_cfg.phys_dpad_mode = STICK_ARROWS;
	gp_cfg.vpad_left_stick_mode = STICK_MOUSE;
	gp_cfg.vpad_right_stick_mode = STICK_NONE;
	gp_cfg.deadzone = 0.18f;
	gp_cfg.cursor_speed_px = 520.0f;

	memset(gp_cfg.phys_bindings, 0, sizeof(gp_cfg.phys_bindings));
	gp_cfg.phys_bindings[PAD_A] = (action_spec_t){ACTION_MOUSE_BTN, KEY_MOUSE_LEFT, 0, false};
	gp_cfg.phys_bindings[PAD_B] = (action_spec_t){ACTION_MOUSE_BTN, KEY_MOUSE_RIGHT, 0, false};
	gp_cfg.phys_bindings[PAD_X] = (action_spec_t){ACTION_VK, KEY_SPACE, 0, false};
	gp_cfg.phys_bindings[PAD_Y] = (action_spec_t){ACTION_VK, KEY_RETURN, 0, false};
	gp_cfg.phys_bindings[PAD_LB] = (action_spec_t){ACTION_MOUSE_WHEEL, 1, 0, false};
	gp_cfg.phys_bindings[PAD_RB] = (action_spec_t){ACTION_MOUSE_WHEEL, -1, 0, false};
	gp_cfg.phys_bindings[PAD_LT] = (action_spec_t){ACTION_VK, KEY_ESCAPE, 0, false};
	gp_cfg.phys_bindings[PAD_RT] = (action_spec_t){ACTION_CTRL_TOGGLE, 0, 0, true};
	gp_cfg.phys_bindings[PAD_BACK] = (action_spec_t){ACTION_EMU_CMD, EMU_TOGGLE_CURSOR, 0, false};
	gp_cfg.phys_bindings[PAD_START] = (action_spec_t){ACTION_EMU_CMD, EMU_OPEN_MENU, 0, false};
	gp_cfg.phys_bindings[PAD_GUIDE] = (action_spec_t){ACTION_EMU_CMD, EMU_TOGGLE_VPAD, 0, false};
	gp_cfg.phys_bindings[PAD_L3] = (action_spec_t){ACTION_VK, KEY_SHIFT, 0, false};
	gp_cfg.phys_bindings[PAD_R3] = (action_spec_t){ACTION_MOUSE_BTN, KEY_MOUSE_LEFT, 0, false};
}

static void parse_binding_json(cJSON *obj, action_spec_t *out) {
	if (!obj || !cJSON_IsObject(obj))
		return;
	cJSON *t = cJSON_GetObjectItemCaseSensitive(obj, "type");
	cJSON *c = cJSON_GetObjectItemCaseSensitive(obj, "code");
	cJSON *c2 = cJSON_GetObjectItemCaseSensitive(obj, "code2");
	cJSON *tg = cJSON_GetObjectItemCaseSensitive(obj, "toggle");
	out->type = (uint8_t)(cJSON_IsNumber(t) ? t->valueint : 0);
	out->code = (int16_t)(cJSON_IsNumber(c) ? c->valueint : 0);
	out->code2 = (int16_t)(cJSON_IsNumber(c2) ? c2->valueint : 0);
	out->toggle_mode = cJSON_IsBool(tg) ? cJSON_IsTrue(tg) : false;
}

static int android_keycode_to_pad_slot(int keycode) {
	switch (keycode) {
	case 96:  return PAD_A;
	case 97:  return PAD_B;
	case 99:  return PAD_X;
	case 100: return PAD_Y;
	case 102: return PAD_LB;
	case 103: return PAD_RB;
	case 104: return PAD_LT;
	case 105: return PAD_RT;
	case 106: return PAD_L3;
	case 107: return PAD_R3;
	case 108: return PAD_START;
	case 109: return PAD_BACK;
	case 110: return PAD_GUIDE;
	case 19:  return PAD_DPAD_UP;
	case 20:  return PAD_DPAD_DOWN;
	case 21:  return PAD_DPAD_LEFT;
	case 22:  return PAD_DPAD_RIGHT;
	case 23:  return PAD_A;
	case 188: return PAD_A;
	case 189: return PAD_B;
	case 190: return PAD_X;
	case 191: return PAD_Y;
	case 192: return PAD_LB;
	case 193: return PAD_RB;
	case 194: return PAD_LT;
	case 195: return PAD_RT;
	case 196: return PAD_BACK;
	case 197: return PAD_START;
	case 198: return PAD_L3;
	case 199: return PAD_R3;
	default:  return -1;
	}
}

static int vk_to_sdl_mouse_button(int vk) {
	switch (vk) {
	case KEY_MOUSE_LEFT:   return SDL_BUTTON_LEFT;
	case KEY_MOUSE_RIGHT:  return SDL_BUTTON_RIGHT;
	case KEY_MOUSE_MIDDLE: return SDL_BUTTON_MIDDLE;
	case 3:                return SDL_BUTTON_RIGHT;
	default:               return 0;
	}
}

static void send_cursor_ui_state(bool force) {
	static int last_param = -1;
	SDL_Window *win = gfx_getWindow();
	int ww = 0, wh = 0;
	if (win)
		SDL_GetWindowSize(win, &ww, &wh);
	if (ww <= 1 || wh <= 1)
		return;

	int vw = 640, vh = 480;
	gfx_getViewSize(&vw, &vh);
	if (vw <= 0) vw = 640;
	if (vh <= 0) vh = 480;

	int cx = (cursor_x >= 0.0f) ? (int)(cursor_x + 0.5f) : (vw / 2);
	int cy = (cursor_y >= 0.0f) ? (int)(cursor_y + 0.5f) : (vh / 2);
	SDL_Rect wrect = gfx_viewToWindowRect((SDL_Rect){cx, cy, 1, 1});

	float nx = (float)wrect.x / (float)(ww - 1);
	float ny = (float)wrect.y / (float)(wh - 1);
	if (nx < 0.0f) nx = 0.0f;
	if (nx > 1.0f) nx = 1.0f;
	if (ny < 0.0f) ny = 0.0f;
	if (ny > 1.0f) ny = 1.0f;

	int ix = (int)(nx * 16383.0f + 0.5f);
	int iy = (int)(ny * 16383.0f + 0.5f);
	int vis = cursor_visible ? 1 : 0;
	int param = (ix & 0x3FFF) | ((iy & 0x3FFF) << 14) | (vis << 28);
	if (force || param != last_param) {
		last_param = param;
		SDL_AndroidSendMessage(COMMAND_CURSOR_STATE, param);
	}
}

static void apply_vk_or_mouse(int vk, bool *desired_vk, int *desired_mouse_mask) {
	int sdl_btn = 0;
	if (vk == KEY_MOUSE_LEFT)
		sdl_btn = SDL_BUTTON_LEFT;
	else if (vk == KEY_MOUSE_RIGHT)
		sdl_btn = SDL_BUTTON_RIGHT;
	else if (vk == KEY_MOUSE_MIDDLE)
		sdl_btn = SDL_BUTTON_MIDDLE;

	if (sdl_btn != 0) {
		*desired_mouse_mask |= (1 << sdl_btn);
	} else if (vk > 0 && vk < 256) {
		desired_vk[vk] = true;
	}
}

static void dispatch_emu_cmd(int cmd) {
	if (cmd == EMU_TOGGLE_CURSOR) {
		int vw = 640, vh = 480;
		gfx_getViewSize(&vw, &vh);
		if (cursor_x < 0.0f || cursor_y < 0.0f) {
			cursor_x = vw * 0.5f;
			cursor_y = vh * 0.5f;
		}
		cursor_visible = !cursor_visible;
		if (cursor_visible)
			event_inject_mouse_motion((int)(cursor_x + 0.5f), (int)(cursor_y + 0.5f));
		send_cursor_ui_state(true);
	} else if (cmd > 0) {
		SDL_AndroidSendMessage(COMMAND_GAMEPAD_UI_EVENT, GAMEPAD_UI_EMU_CMD | ((cmd & 0xFF) << 8));
	}
}

static void accumulate_action(const action_spec_t *act, bool active, bool press_edge,
	bool *desired_vk, int *desired_mouse_mask, int *desired_joy_mask,
	int *wheel_dir, bool *want_ctrl) {
	switch (act->type) {
	case ACTION_VK:
		if (active) {
			if (act->code == KEY_CTRL)
				*want_ctrl = true;
			else
				apply_vk_or_mouse(act->code, desired_vk, desired_mouse_mask);
		}
		break;
	case ACTION_MOUSE_BTN:
		if (active) {
			int btn = vk_to_sdl_mouse_button(act->code);
			if (btn > 0)
				*desired_mouse_mask |= (1 << btn);
		}
		break;
	case ACTION_MOUSE_WHEEL:
		if (active) {
			uint32_t now = SDL_GetTicks();
			if (press_edge || (now - last_wheel_tick) >= WHEEL_REPEAT_MS) {
				*wheel_dir = (act->code >= 0) ? 1 : -1;
				last_wheel_tick = now;
			}
		}
		break;
	case ACTION_CTRL_TOGGLE:
		if (press_edge)
			ctrl_skip_latched = !ctrl_skip_latched;
		if (!act->toggle_mode && active)
			*want_ctrl = true;
		break;
	case ACTION_JOYPAD_BTN:
		if (active) {
			int bit = act->code;
			if (bit >= 0 && bit <= 7)
				*desired_joy_mask |= (1 << bit);
			else
				*desired_joy_mask |= (bit & 0xFF);
		}
		break;
	case ACTION_COMBO:
		if (active) {
			apply_vk_or_mouse(act->code, desired_vk, desired_mouse_mask);
			apply_vk_or_mouse(act->code2, desired_vk, desired_mouse_mask);
		}
		break;
	case ACTION_EMU_CMD:
		if (press_edge)
			dispatch_emu_cmd(act->code);
		break;
	}
}

static void process_directional_mode(int mode, float x, float y, float dt_sec, float speed_px,
	int vw, int vh, bool *desired_vk, int *desired_joy_mask, int *wheel_dir, bool *moved_cursor) {
	if (mode == STICK_NONE || (x == 0.0f && y == 0.0f))
		return;
	if (mode == STICK_MOUSE) {
		if (cursor_x < 0.0f || cursor_y < 0.0f) {
			cursor_x = vw * 0.5f;
			cursor_y = vh * 0.5f;
		}
		cursor_x += x * speed_px * dt_sec;
		cursor_y += y * speed_px * dt_sec;
		if (cursor_x < 0.0f) cursor_x = 0.0f;
		if (cursor_x > (float)(vw - 1)) cursor_x = (float)(vw - 1);
		if (cursor_y < 0.0f) cursor_y = 0.0f;
		if (cursor_y > (float)(vh - 1)) cursor_y = (float)(vh - 1);
		cursor_visible = true;
		*moved_cursor = true;
	} else if (mode == STICK_ARROWS) {
		if (x < -0.35f) desired_vk[KEY_LEFT] = true;
		if (x >  0.35f) desired_vk[KEY_RIGHT] = true;
		if (y < -0.35f) desired_vk[KEY_UP] = true;
		if (y >  0.35f) desired_vk[KEY_DOWN] = true;
	} else if (mode == STICK_WASD) {
		if (x < -0.35f) desired_vk[KEY_A] = true;
		if (x >  0.35f) desired_vk[KEY_D] = true;
		if (y < -0.35f) desired_vk[KEY_W] = true;
		if (y >  0.35f) desired_vk[KEY_S] = true;
	} else if (mode == STICK_WHEEL) {
		int dir = (y < -0.45f) ? 1 : ((y > 0.45f) ? -1 : 0);
		if (dir != 0) {
			uint32_t now = SDL_GetTicks();
			if (dir != prev_stick_wheel_dir || (now - last_wheel_tick) >= WHEEL_REPEAT_MS) {
				*wheel_dir = dir;
				last_wheel_tick = now;
				prev_stick_wheel_dir = dir;
			}
		} else {
			prev_stick_wheel_dir = 0;
		}
	} else if (mode == STICK_JOYPAD) {
		if (x < -0.35f) *desired_joy_mask |= SYS35KEY_LEFT;
		if (x >  0.35f) *desired_joy_mask |= SYS35KEY_RIGHT;
		if (y < -0.35f) *desired_joy_mask |= SYS35KEY_UP;
		if (y >  0.35f) *desired_joy_mask |= SYS35KEY_DOWN;
	}
}

static float apply_deadzone(float v, float dz) {
	float a = fabsf(v);
	if (a <= dz)
		return 0.0f;
	float s = (a - dz) / (1.0f - dz);
	return v < 0.0f ? -s : s;
}

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

void alicehub_on_touch_input(void) {
	if (cursor_visible) {
		cursor_visible = false;
		send_cursor_ui_state(true);
	}
}

void alicehub_pump_events(void) {
	uint32_t now = SDL_GetTicks();
	float dt_sec = last_pump_tick ? (float)(now - last_pump_tick) / 1000.0f : 0.016f;
	if (dt_sec < 0.001f) dt_sec = 0.001f;
	if (dt_sec > 0.1f) dt_sec = 0.1f;
	last_pump_tick = now;

	bool desired_vk[256];
	memset(desired_vk, 0, sizeof(desired_vk));
	int desired_mouse_mask = 0;
	int desired_joy_mask = 0;
	int wheel_dir = 0;
	bool want_ctrl = false;
	bool moved_cursor = false;

	int vw = 640, vh = 480;
	gfx_getViewSize(&vw, &vh);
	if (vw <= 0) vw = 640;
	if (vh <= 0) vh = 480;

	char local_actions[MAX_PENDING_ACTIONS][64];
	int n_actions = 0;
	int locks_snapshot = 0;

	pthread_mutex_lock(&bridge_lock);
	init_cfg_defaults_locked();

	if (pending_ctrl_set) {
		ctrl_skip_latched = pending_ctrl_val;
		pending_ctrl_set = false;
	}
	if (pending_cursor_reset) {
		pending_cursor_reset = false;
		cursor_visible = pending_cursor_vis;
		if (cursor_visible && (cursor_x < 0.0f || cursor_y < 0.0f)) {
			cursor_x = vw * 0.5f;
			cursor_y = vh * 0.5f;
		}
		if (cursor_visible)
			moved_cursor = true;
	}

	// Snapshot queued live modifier actions
	n_actions = pending_action_count;
	for (int i = 0; i < n_actions; i++)
		memcpy(local_actions[i], pending_actions[i], sizeof(local_actions[i]));
	pending_action_count = 0;
	locks_snapshot = esc_live_locks;

	// Virtual buttons
	for (int i = 0; i < MAX_VPAD_SLOTS; i++) {
		vpad_slot_t *s = &vpad_slots[i];
		bool down = s->phys_down;
		action_spec_t act = s->action;
		if (s->pending_press_edge) {
			s->pending_press_edge = false;
			down = true;
			act = s->pending_action;
		}
		bool edge = down && !s->prev_phys_down;
		s->prev_phys_down = down;
		if (act.toggle_mode && act.type != ACTION_CTRL_TOGGLE) {
			if (edge)
				s->latched = !s->latched;
			accumulate_action(&act, s->latched, edge, desired_vk, &desired_mouse_mask, &desired_joy_mask, &wheel_dir, &want_ctrl);
		} else {
			accumulate_action(&act, down, edge, desired_vk, &desired_mouse_mask, &desired_joy_mask, &wheel_dir, &want_ctrl);
		}
	}

	// Virtual sticks & D-pad
	process_directional_mode(gp_cfg.vpad_left_stick_mode, vpad_stick_x[0], vpad_stick_y[0],
		dt_sec, gp_cfg.cursor_speed_px, vw, vh, desired_vk, &desired_joy_mask, &wheel_dir, &moved_cursor);
	process_directional_mode(gp_cfg.vpad_right_stick_mode, vpad_stick_x[1], vpad_stick_y[1],
		dt_sec, gp_cfg.cursor_speed_px, vw, vh, desired_vk, &desired_joy_mask, &wheel_dir, &moved_cursor);
	float vdx = (vpad_dpad_dir[1] ? 1.0f : 0.0f) - (vpad_dpad_dir[0] ? 1.0f : 0.0f);
	float vdy = (vpad_dpad_dir[3] ? 1.0f : 0.0f) - (vpad_dpad_dir[2] ? 1.0f : 0.0f);
	process_directional_mode(vpad_dpad_mode, vdx, vdy,
		dt_sec, gp_cfg.cursor_speed_px, vw, vh, desired_vk, &desired_joy_mask, &wheel_dir, &moved_cursor);

	// Physical Android controller
	if (gp_cfg.phys_enabled && android_pad.active) {
		for (int s = 0; s < NR_PAD_SLOTS; s++) {
			int mapped_slot = s;
			if (gp_cfg.swap_ab_xy) {
				if (s == PAD_A) mapped_slot = PAD_B;
				else if (s == PAD_B) mapped_slot = PAD_A;
				else if (s == PAD_X) mapped_slot = PAD_Y;
				else if (s == PAD_Y) mapped_slot = PAD_X;
			}
			bool down = android_pad.btn_down[s];
			if (s == PAD_LT && android_pad.axes[4] > 0.45f) down = true;
			if (s == PAD_RT && android_pad.axes[5] > 0.45f) down = true;
			bool edge = down && !android_pad.prev_down[s];
			android_pad.prev_down[s] = down;

			const action_spec_t *act = &gp_cfg.phys_bindings[mapped_slot];
			if (act->toggle_mode && act->type != ACTION_CTRL_TOGGLE) {
				if (edge)
					android_pad.latched[s] = !android_pad.latched[s];
				accumulate_action(act, android_pad.latched[s], edge, desired_vk, &desired_mouse_mask, &desired_joy_mask, &wheel_dir, &want_ctrl);
			} else {
				accumulate_action(act, down, edge, desired_vk, &desired_mouse_mask, &desired_joy_mask, &wheel_dir, &want_ctrl);
			}
		}

		float lx = apply_deadzone(android_pad.axes[0], gp_cfg.deadzone);
		float ly = apply_deadzone(android_pad.axes[1], gp_cfg.deadzone);
		float rx = apply_deadzone(android_pad.axes[2], gp_cfg.deadzone);
		float ry = apply_deadzone(android_pad.axes[3], gp_cfg.deadzone);
		process_directional_mode(gp_cfg.phys_left_stick_mode, lx, ly,
			dt_sec, gp_cfg.cursor_speed_px, vw, vh, desired_vk, &desired_joy_mask, &wheel_dir, &moved_cursor);
		process_directional_mode(gp_cfg.phys_right_stick_mode, rx, ry,
			dt_sec, gp_cfg.cursor_speed_px, vw, vh, desired_vk, &desired_joy_mask, &wheel_dir, &moved_cursor);

		float dx = android_pad.hat_x;
		float dy = android_pad.hat_y;
		if (android_pad.btn_down[PAD_DPAD_LEFT]) dx = -1.0f;
		if (android_pad.btn_down[PAD_DPAD_RIGHT]) dx = 1.0f;
		if (android_pad.btn_down[PAD_DPAD_UP]) dy = -1.0f;
		if (android_pad.btn_down[PAD_DPAD_DOWN]) dy = 1.0f;
		process_directional_mode(gp_cfg.phys_dpad_mode, dx, dy,
			dt_sec, gp_cfg.cursor_speed_px, vw, vh, desired_vk, &desired_joy_mask, &wheel_dir, &moved_cursor);
	}

	bool final_ctrl = ctrl_skip_latched || want_ctrl;
	pthread_mutex_unlock(&bridge_lock);

	// Apply Escalayer live actions & locks safely on SDL_main thread
	for (int i = 0; i < n_actions; i++)
		apply_single_action_sdl_thread(local_actions[i]);
	apply_escalayer_locks_sdl_thread(locks_snapshot);
	update_live_state_sdl_thread(locks_snapshot);

	// Emit cursor motion before button events
	if (moved_cursor && cursor_x >= 0.0f && cursor_y >= 0.0f) {
		event_inject_mouse_motion((int)(cursor_x + 0.5f), (int)(cursor_y + 0.5f));
	}
	send_cursor_ui_state(false);

	// Emit mouse buttons cleanly on edges
	for (int btn = 1; btn <= 3; btn++) {
		int bit = 1 << btn;
		bool now_down = (desired_mouse_mask & bit) != 0;
		bool prev = (bridge_mouse_mask & bit) != 0;
		if (now_down != prev) {
			event_inject_mouse_button(btn, now_down);
			if (now_down) bridge_mouse_mask |= bit;
			else bridge_mouse_mask &= ~bit;
		}
	}

	// Emit wheel steps
	if (wheel_dir != 0)
		event_inject_mouse_wheel(wheel_dir);

	// Emit Ctrl skip state
	if (final_ctrl != ctrl_skip_applied) {
		ctrl_skip_applied = final_ctrl;
		event_inject_rawkey(KEY_CTRL, final_ctrl);
		msgskip_activate(final_ctrl);
	}

	// Emit virtual/pad keyboard keys
	for (int k = 1; k < 256; k++) {
		if (k == KEY_CTRL || k == KEY_MOUSE_LEFT || k == KEY_MOUSE_RIGHT || k == KEY_MOUSE_MIDDLE)
			continue;
		if (desired_vk[k] != bridge_vk_down[k]) {
			bridge_vk_down[k] = desired_vk[k];
			event_inject_rawkey(k, desired_vk[k]);
		}
	}

	// Emit joyinfo mask changes
	int diff_joy = desired_joy_mask ^ bridge_joy_mask;
	if (diff_joy) {
		for (int bit = 0; bit < 8; bit++) {
			int mask = 1 << bit;
			if (diff_joy & mask)
				event_inject_joy_mask(mask, (desired_joy_mask & mask) != 0);
		}
		bridge_joy_mask = desired_joy_mask;
	}
}

// ---------------------------------------------------------------------------
// GamepadBridge JNI methods
// ---------------------------------------------------------------------------

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeSetGamepadConfigJson(
	JNIEnv *env, jclass clazz, jstring jjson) {
	(void)clazz;
	if (!jjson)
		return;
	const char *json = (*env)->GetStringUTFChars(env, jjson, NULL);
	if (!json)
		return;

	cJSON *root = cJSON_Parse(json);
	(*env)->ReleaseStringUTFChars(env, jjson, json);
	if (!root || !cJSON_IsObject(root)) {
		cJSON_Delete(root);
		return;
	}

	pthread_mutex_lock(&bridge_lock);
	init_cfg_defaults_locked();

	cJSON *phys = cJSON_GetObjectItemCaseSensitive(root, "physical");
	if (cJSON_IsObject(phys)) {
		cJSON *en = cJSON_GetObjectItemCaseSensitive(phys, "enabled");
		if (cJSON_IsBool(en)) gp_cfg.phys_enabled = cJSON_IsTrue(en);
		cJSON *swap = cJSON_GetObjectItemCaseSensitive(phys, "swapAbXy");
		if (cJSON_IsBool(swap)) gp_cfg.swap_ab_xy = cJSON_IsTrue(swap);
		cJSON *ls = cJSON_GetObjectItemCaseSensitive(phys, "leftStickMode");
		if (cJSON_IsNumber(ls)) gp_cfg.phys_left_stick_mode = ls->valueint;
		cJSON *rs = cJSON_GetObjectItemCaseSensitive(phys, "rightStickMode");
		if (cJSON_IsNumber(rs)) gp_cfg.phys_right_stick_mode = rs->valueint;
		cJSON *dp = cJSON_GetObjectItemCaseSensitive(phys, "dpadMode");
		if (cJSON_IsNumber(dp)) gp_cfg.phys_dpad_mode = dp->valueint;
		cJSON *dz = cJSON_GetObjectItemCaseSensitive(phys, "deadzonePct");
		if (cJSON_IsNumber(dz)) {
			int p = dz->valueint;
			if (p < 5) p = 5;
			if (p > 45) p = 45;
			gp_cfg.deadzone = (float)p / 100.0f;
		}
		cJSON *sp = cJSON_GetObjectItemCaseSensitive(phys, "cursorSpeedPct");
		if (cJSON_IsNumber(sp)) {
			int p = sp->valueint;
			if (p < 30) p = 30;
			if (p > 250) p = 250;
			gp_cfg.cursor_speed_px = 520.0f * ((float)p / 100.0f);
		}
		cJSON *bindings = cJSON_GetObjectItemCaseSensitive(phys, "bindings");
		if (cJSON_IsObject(bindings)) {
			for (int s = 0; s < NR_PAD_SLOTS; s++) {
				char key[8];
				snprintf(key, sizeof(key), "%d", s);
				cJSON *item = cJSON_GetObjectItemCaseSensitive(bindings, key);
				if (item)
					parse_binding_json(item, &gp_cfg.phys_bindings[s]);
			}
		}
	}

	cJSON *vpad = cJSON_GetObjectItemCaseSensitive(root, "virtual");
	if (cJSON_IsObject(vpad)) {
		cJSON *vls = cJSON_GetObjectItemCaseSensitive(vpad, "leftStickMode");
		if (cJSON_IsNumber(vls)) gp_cfg.vpad_left_stick_mode = vls->valueint;
		cJSON *vrs = cJSON_GetObjectItemCaseSensitive(vpad, "rightStickMode");
		if (cJSON_IsNumber(vrs)) gp_cfg.vpad_right_stick_mode = vrs->valueint;
	}

	memset(android_pad.latched, 0, sizeof(android_pad.latched));
	for (int i = 0; i < MAX_VPAD_SLOTS; i++)
		vpad_slots[i].latched = false;

	pthread_mutex_unlock(&bridge_lock);
	cJSON_Delete(root);
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeSetVirtualStick(
	JNIEnv *env, jclass clazz, jint stickId, jfloat x, jfloat y) {
	(void)env; (void)clazz;
	if (stickId < 0 || stickId > 1)
		return;
	if (x < -1.0f) x = -1.0f;
	if (x > 1.0f) x = 1.0f;
	if (y < -1.0f) y = -1.0f;
	if (y > 1.0f) y = 1.0f;
	pthread_mutex_lock(&bridge_lock);
	vpad_stick_x[stickId] = x;
	vpad_stick_y[stickId] = y;
	pthread_mutex_unlock(&bridge_lock);
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeSetVirtualDPad(
	JNIEnv *env, jclass clazz, jint stickMode, jboolean left, jboolean right, jboolean up, jboolean down) {
	(void)env; (void)clazz;
	pthread_mutex_lock(&bridge_lock);
	vpad_dpad_mode = stickMode;
	vpad_dpad_dir[0] = left != JNI_FALSE;
	vpad_dpad_dir[1] = right != JNI_FALSE;
	vpad_dpad_dir[2] = up != JNI_FALSE;
	vpad_dpad_dir[3] = down != JNI_FALSE;
	pthread_mutex_unlock(&bridge_lock);
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeSetVirtualButton(
	JNIEnv *env, jclass clazz, jint slotId, jint actionType, jint code, jint code2,
	jboolean toggleMode, jboolean pressed) {
	(void)env; (void)clazz;
	if (slotId < 0 || slotId >= MAX_VPAD_SLOTS)
		return;
	pthread_mutex_lock(&bridge_lock);
	vpad_slot_t *s = &vpad_slots[slotId];
	action_spec_t act = {
		.type = (uint8_t)actionType,
		.code = (int16_t)code,
		.code2 = (int16_t)code2,
		.toggle_mode = (toggleMode != JNI_FALSE),
	};
	if (pressed != JNI_FALSE && !s->phys_down) {
		s->pending_press_edge = true;
		s->pending_action = act;
	}
	s->action = act;
	s->phys_down = (pressed != JNI_FALSE);
	pthread_mutex_unlock(&bridge_lock);
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeClearVirtualInputs(
	JNIEnv *env, jclass clazz) {
	(void)env; (void)clazz;
	pthread_mutex_lock(&bridge_lock);
	for (int i = 0; i < MAX_VPAD_SLOTS; i++) {
		vpad_slots[i].phys_down = false;
		vpad_slots[i].pending_press_edge = false;
		vpad_slots[i].latched = false;
	}
	vpad_stick_x[0] = vpad_stick_y[0] = 0.0f;
	vpad_stick_x[1] = vpad_stick_y[1] = 0.0f;
	memset(vpad_dpad_dir, 0, sizeof(vpad_dpad_dir));
	pthread_mutex_unlock(&bridge_lock);
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeSetAndroidControllerKey(
	JNIEnv *env, jclass clazz, jint deviceId, jint keycode, jboolean pressed) {
	(void)env; (void)clazz;
	if (keycode < 0 && !pressed) {
		pthread_mutex_lock(&bridge_lock);
		if (android_pad.active && (deviceId < 0 || android_pad.device_id == deviceId))
			memset(&android_pad, 0, sizeof(android_pad));
		pthread_mutex_unlock(&bridge_lock);
		return;
	}
	int slot = android_keycode_to_pad_slot(keycode);
	if (slot < 0)
		return;
	pthread_mutex_lock(&bridge_lock);
	android_pad.active = true;
	android_pad.device_id = deviceId;
	android_pad.btn_down[slot] = (pressed != JNI_FALSE);
	pthread_mutex_unlock(&bridge_lock);
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeSetAndroidControllerMotion(
	JNIEnv *env, jclass clazz, jint deviceId,
	jfloat lx, jfloat ly, jfloat rx, jfloat ry, jfloat lt, jfloat rt, jfloat hatX, jfloat hatY) {
	(void)env; (void)clazz;
	pthread_mutex_lock(&bridge_lock);
	android_pad.active = true;
	android_pad.device_id = deviceId;
	android_pad.axes[0] = lx;
	android_pad.axes[1] = ly;
	android_pad.axes[2] = rx;
	android_pad.axes[3] = ry;
	android_pad.axes[4] = lt;
	android_pad.axes[5] = rt;
	android_pad.hat_x = hatX;
	android_pad.hat_y = hatY;
	pthread_mutex_unlock(&bridge_lock);
}

JNIEXPORT jstring JNICALL
Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeGetControllerStatusJson(
	JNIEnv *env, jclass clazz) {
	(void)clazz;
	cJSON *root = cJSON_CreateObject();
	cJSON *arr = cJSON_CreateArray();
	pthread_mutex_lock(&bridge_lock);
	uint32_t bmask = 0;
	for (int s = 0; s < NR_PAD_SLOTS; s++) {
		if (android_pad.btn_down[s])
			bmask |= (1u << s);
	}
	float ax[6];
	memcpy(ax, android_pad.axes, sizeof(ax));
	bool has_pad = android_pad.active;
	pthread_mutex_unlock(&bridge_lock);

	cJSON_AddNumberToObject(root, "count", has_pad ? 1 : 0);
	cJSON_AddItemToArray(arr, cJSON_CreateString(has_pad ? "Android Controller" : "None"));
	cJSON_AddItemToObject(root, "controllers", has_pad ? arr : cJSON_CreateArray());
	if (!has_pad) cJSON_Delete(arr);
	cJSON_AddNumberToObject(root, "buttonsMask", (double)bmask);
	cJSON_AddNumberToObject(root, "lx", ax[0]);
	cJSON_AddNumberToObject(root, "ly", ax[1]);
	cJSON_AddNumberToObject(root, "rx", ax[2]);
	cJSON_AddNumberToObject(root, "ry", ax[3]);
	cJSON_AddNumberToObject(root, "lt", ax[4]);
	cJSON_AddNumberToObject(root, "rt", ax[5]);
	char *out = cJSON_PrintUnformatted(root);
	cJSON_Delete(root);
	jstring ret = (*env)->NewStringUTF(env, out ? out : "{}");
	free(out);
	return ret;
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GamepadBridge_nativeResetGamepadCursor(
	JNIEnv *env, jclass clazz, jboolean visible) {
	(void)env; (void)clazz;
	pthread_mutex_lock(&bridge_lock);
	pending_cursor_reset = true;
	pending_cursor_vis = (visible != JNI_FALSE);
	pthread_mutex_unlock(&bridge_lock);
}

// ---------------------------------------------------------------------------
// GameModifierBridge JNI methods
// ---------------------------------------------------------------------------

JNIEXPORT jstring JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeReadLocalSaveSummary(
	JNIEnv *env, jclass clazz, jstring jpath) {
	(void)clazz; (void)jpath;
	return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jboolean JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeWriteLocalSaveMod(
	JNIEnv *env, jclass clazz, jstring jpath, jstring jmodSpec) {
	(void)env; (void)clazz; (void)jpath; (void)jmodSpec;
	return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeReadGlobalSaveSummary(
	JNIEnv *env, jclass clazz, jstring jglobalPath, jstring jcollectionPath) {
	(void)clazz; (void)jglobalPath; (void)jcollectionPath;
	return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jboolean JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeWriteGlobalSaveMod(
	JNIEnv *env, jclass clazz, jstring jglobalPath, jstring jcollectionPath, jstring jmodSpec) {
	(void)env; (void)clazz; (void)jglobalPath; (void)jcollectionPath; (void)jmodSpec;
	return JNI_FALSE;
}

static void update_live_state_sdl_thread(int locks) {
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

JNIEXPORT jstring JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetLiveStateJson(
	JNIEnv *env, jclass clazz) {
	(void)clazz;
	char *out = copy_live_state_json();
	jstring ret = (*env)->NewStringUTF(env, out ? out : "{}");
	free(out);
	return ret;
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetLiveLocks(
	JNIEnv *env, jclass clazz, jint mask) {
	(void)env; (void)clazz;
	pthread_mutex_lock(&bridge_lock);
	esc_live_locks = mask;
	pthread_mutex_unlock(&bridge_lock);
}

JNIEXPORT jint JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetLiveLocks(
	JNIEnv *env, jclass clazz) {
	(void)env; (void)clazz;
	pthread_mutex_lock(&bridge_lock);
	int v = esc_live_locks;
	pthread_mutex_unlock(&bridge_lock);
	return v;
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetDohnaLiveLocks(
	JNIEnv *env, jclass clazz, jint mask) {
	(void)env; (void)clazz; (void)mask;
}
JNIEXPORT jint JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetDohnaLiveLocks(
	JNIEnv *env, jclass clazz) {
	(void)env; (void)clazz;
	return 0;
}
JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetEvenicleLiveLocks(
	JNIEnv *env, jclass clazz, jint mask) {
	(void)env; (void)clazz; (void)mask;
}
JNIEXPORT jint JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetEvenicleLiveLocks(
	JNIEnv *env, jclass clazz) {
	(void)env; (void)clazz;
	return 0;
}
JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetSrLiveLocks(
	JNIEnv *env, jclass clazz, jint mask) {
	(void)env; (void)clazz; (void)mask;
}
JNIEXPORT jint JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetSrLiveLocks(
	JNIEnv *env, jclass clazz) {
	(void)env; (void)clazz;
	return 0;
}
JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetR03LiveLocks(
	JNIEnv *env, jclass clazz, jint mask) {
	(void)env; (void)clazz; (void)mask;
}
JNIEXPORT jint JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetR03LiveLocks(
	JNIEnv *env, jclass clazz) {
	(void)env; (void)clazz;
	return 0;
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeApplyLiveAction(
	JNIEnv *env, jclass clazz, jstring jactionSpec) {
	(void)clazz;
	if (!jactionSpec)
		return;
	const char *s = (*env)->GetStringUTFChars(env, jactionSpec, NULL);
	if (!s)
		return;
	pthread_mutex_lock(&bridge_lock);
	if (pending_action_count < MAX_PENDING_ACTIONS) {
		snprintf(pending_actions[pending_action_count], 64, "%s", s);
		pending_action_count++;
	}
	pthread_mutex_unlock(&bridge_lock);
	(*env)->ReleaseStringUTFChars(env, jactionSpec, s);
}

JNIEXPORT jint JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeReadStreamerMode(
	JNIEnv *env, jclass clazz, jstring jconfigPath) {
	(void)env; (void)clazz; (void)jconfigPath;
	return -1;
}

JNIEXPORT jboolean JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeWriteStreamerMode(
	JNIEnv *env, jclass clazz, jstring jconfigPath, jboolean enabled) {
	(void)env; (void)clazz; (void)jconfigPath; (void)enabled;
	return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetConfigPath(
	JNIEnv *env, jclass clazz, jstring jpath) {
	(void)env; (void)clazz; (void)jpath;
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetCtrlSkip(
	JNIEnv *env, jclass clazz, jboolean enabled) {
	(void)env; (void)clazz;
	pthread_mutex_lock(&bridge_lock);
	pending_ctrl_set = true;
	pending_ctrl_val = (enabled != JNI_FALSE);
	pthread_mutex_unlock(&bridge_lock);
}

JNIEXPORT jboolean JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetCtrlSkip(
	JNIEnv *env, jclass clazz) {
	(void)env; (void)clazz;
	pthread_mutex_lock(&bridge_lock);
	bool val = pending_ctrl_set ? pending_ctrl_val : ctrl_skip_latched;
	pthread_mutex_unlock(&bridge_lock);
	return (val || ctrl_skip_applied) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeSetUiScale(
	JNIEnv *env, jclass clazz, jint fontPct, jint scrollbarPct) {
	(void)env; (void)clazz; (void)fontPct; (void)scrollbarPct;
}

JNIEXPORT jint JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetFontScale(
	JNIEnv *env, jclass clazz) {
	(void)env; (void)clazz;
	return 100;
}

JNIEXPORT jint JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeGetScrollbarScale(
	JNIEnv *env, jclass clazz) {
	(void)env; (void)clazz;
	return 100;
}

JNIEXPORT jboolean JNICALL
Java_io_github_kichikuou_xsystem4_GameModifierBridge_nativeExportPcSaveFile(
	JNIEnv *env, jclass clazz, jstring jsrcPath, jstring jdstPath) {
	(void)clazz;
	if (!jsrcPath || !jdstPath)
		return JNI_FALSE;
	const char *src = (*env)->GetStringUTFChars(env, jsrcPath, NULL);
	const char *dst = (*env)->GetStringUTFChars(env, jdstPath, NULL);
	if (!src || !dst) {
		if (src) (*env)->ReleaseStringUTFChars(env, jsrcPath, src);
		if (dst) (*env)->ReleaseStringUTFChars(env, jdstPath, dst);
		return JNI_FALSE;
	}

	bool ok = false;
	FILE *in = fopen(src, "rb");
	FILE *out = in ? fopen(dst, "wb") : NULL;
	if (in && out) {
		uint8_t buf[16384];
		size_t n;
		ok = true;
		while ((n = fread(buf, 1, sizeof(buf), in)) > 0) {
			if (fwrite(buf, 1, n, out) != n) {
				ok = false;
				break;
			}
		}
		if (ferror(in))
			ok = false;
	}
	if (out) fclose(out);
	if (in) fclose(in);

	(*env)->ReleaseStringUTFChars(env, jsrcPath, src);
	(*env)->ReleaseStringUTFChars(env, jdstPath, dst);
	return ok ? JNI_TRUE : JNI_FALSE;
}

#endif // __ANDROID__
