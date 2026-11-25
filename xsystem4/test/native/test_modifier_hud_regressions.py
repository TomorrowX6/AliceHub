#!/usr/bin/env python3
"""Exercise VM arguments and visible-widget guards without game assets."""

from pathlib import Path
import subprocess
import tempfile
import unittest

from test_modifier_injected_key_regressions import extract_function


HARNESS = r"""
#include <assert.h>
#include <stdbool.h>
#include <string.h>

typedef union vm_value { int i; } value;
enum { ARRAY_PAGE = 1 };
struct page { int type, nr_vars; struct { int rank; } array; value values[2]; };
struct sact_sprite { bool visible; };
static struct page sprites = { ARRAY_PAGE, 2, { 1 }, {{0}, {7}} };
static struct page widget, busho, busho_id;
static struct page battle_widgets = { ARRAY_PAGE, 1, { 1 }, {{9}} };
static struct sact_sprite hp = { true };
static int s_r02_stat_sprites_glob = 1, s_r02_fn_disp_stat = 2, s_sr_fn_set_number = 3;
static int s_sr_glob_battle_place = 4, s_sr_fn_set_battle_hp = 5, s_sr_fn_get_busho_no = 7;
static bool dg_callbegin_pending, already_on_stack;
static int pushed[2], count, calls, function, receiver, live_gold, shown_gold;
static bool widget_initialized, busho_visible, busho_destroyed;

static bool vm_callstack_has_fno(int fno) { return already_on_stack && fno == 2; }
static void stack_push(int arg) { assert(count < 2); pushed[count++] = arg; }
static void vm_call(int fno, int slot) {
    assert(!dg_callbegin_pending && count == 2);
    ++calls;
    function = fno;
    receiver = slot;
    count = 0;
}
static struct page *vm_get_global_page_idx(int idx) {
    return idx == 1 ? &sprites : idx == 4 ? &battle_widgets : 0;
}
static struct sact_sprite *sact_try_get_sprite(int id) { return id == 7 ? &hp : 0; }
static bool sprite_get_show(struct sact_sprite *sprite) { return sprite->visible; }
static value *vm_get_struct_member(struct page *page, const char *name, const char *unused) {
    static value slot = {8};
    return (page == &widget && !strcmp(name, "m_sGold"))
        || (page == &busho && !strcmp(name, "m_sHp")) ? &slot : 0;
}
static struct page *vm_safe_get_page(int slot) {
    return slot == 8 ? &widget : slot == 9 ? &busho : 0;
}
static struct page *vm_get_field_page(struct page *page, const char *name, const char *unused) {
    if (page == &busho && !strcmp(name, "m_sBushoNo")) return &busho_id;
    if (page == &battle_widgets && !strcmp(name, "m_asMikaBusho")) return &battle_widgets;
    return 0;
}
static int vm_get_field_int(struct page *page, const char *name, const char *unused, int fallback) {
    if (page == &busho_id) return !strcmp(name, "m_nCharaNo") ? 2003 : 0;
    if (page == &busho) {
        if (!strcmp(name, "m_bBattle")) return 1;
        if (!strcmp(name, "m_bDestroy")) return busho_destroyed;
        return busho_visible;
    }
    if (page != &widget) return fallback;
    if (!strcmp(name, "m_bShow")) return widget_initialized;
    return !strcmp(name, "m_bActive") ? widget_initialized : shown_gold;
}
static value global_get(int idx) { assert(idx == 6); return (value){live_gold}; }
static int vm_dd_call_int_0(int fno, int slot, int fallback) {
    assert(fno == 7 && slot == 9);
    return 11;
}
static struct page *sr_get_chara_page(int faction, int index) {
    assert(faction == 0 && index == 11);
    return &busho;
}

%s

int main(void) {
    hp.visible = false;
    r02_refresh_hud();
    assert(calls == 0);
    hp.visible = true;
    already_on_stack = true;
    r02_refresh_hud();
    assert(calls == 0 && count == 0);
    already_on_stack = false;
    dg_callbegin_pending = true;
    r02_refresh_hud();
    assert(calls == 1 && function == 2 && receiver == -1);
    assert(pushed[0] == 1 && pushed[1] == 0 && dg_callbegin_pending);

    live_gold = 103000;
    shown_gold = 3000;
    sr_refresh_number(&widget, "m_sGold", 6);
    assert(calls == 1);
    widget_initialized = true;
    sr_refresh_number(&widget, "m_sGold", 6);
    assert(calls == 2 && function == 3 && receiver == 8);
    assert(pushed[0] == 103000 && pushed[1] == 0 && dg_callbegin_pending);
    shown_gold = live_gold;
    sr_refresh_number(&widget, "m_sGold", 6);
    assert(calls == 2);

    sr_refresh_battle_hp(true);
    assert(calls == 2);
    busho_visible = true;
    sr_refresh_battle_hp(true);
    assert(calls == 3 && function == 5 && receiver == 8);
    assert(pushed[0] == 0 && pushed[1] == 11);
    busho_destroyed = true;
    sr_refresh_battle_hp(true);
    assert(calls == 3);
    return 0;
}
"""


class ModifierHudTests(unittest.TestCase):
    def test_hud_refresh_only_calls_initialized_widgets_with_game_arguments(self):
        functions = "\n".join(
            extract_function("src/modifier.c", name)
            for name in (
                "vm_call_hud_setter", "r02_refresh_hud", "sr_refresh_number",
                "sr_battle_widgets", "sr_is_active_busho", "sr_get_busho_index",
                "sr_refresh_battle_hp",
            )
        )
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "hud.c"
            binary = Path(tmp) / "hud"
            source.write_text(HARNESS % functions, encoding="utf-8")
            subprocess.run(["cc", "-std=c11", "-o", str(binary), str(source)], check=True)
            subprocess.run([str(binary)], check=True)


if __name__ == "__main__":
    unittest.main()
