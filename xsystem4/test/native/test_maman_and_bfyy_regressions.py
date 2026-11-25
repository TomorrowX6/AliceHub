#!/usr/bin/env python3
"""Native C regression tests for:
1. Rance 10 BFYY (全系列MOD) archive priority recognition in afa_is_mod_archive.
2. Heartful Maman (は～とふるママン) game detection & 1280x720 default viewport in hacks.c.
3. AIN v8 builtin HLL enablement (String/Int/Float), PartsEngine 5-arg bool IsSkip dispatch,
   Parts message_type_v8 + -1 empty-queue sentinel, and parts_v2 scrollbar no <= 0 guard.
4. Heartful Maman backlog text and voice HLL return signatures.
5. Heartful Maman mixed-encoding speaker labels.
"""
import unittest

from test_evenicle2_regressions import (
    LIBSYS4_CORE,
    ROOT,
    compile_and_run,
)


def _extract_def(rel_path: str, name: str) -> str:
    source = (ROOT / rel_path).read_text(encoding="utf-8", errors="ignore")
    pos = 0
    while True:
        idx = source.index(name + "(", pos)
        start = source.rfind("\n", 0, idx) + 1
        semi = source.find(";", idx)
        brace = source.find("{", idx)
        if brace != -1 and (semi == -1 or brace < semi):
            break
        pos = idx + len(name)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


class MamanAndBfyyRegressionsTest(unittest.TestCase):
    def test_maman_cn_speaker_literals(self):
        harness = r"""
#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "xsystem4.h"
#include "system4/ain.h"
#include "system4/buffer.h"
#include "system4/instructions.h"
#include "system4/little_endian.h"
#include "system4/string.h"
#include "system4/utfsjis.h"

void apply_game_specific_hacks(struct ain *ain);
struct config config;
bool message_layout_init_rance03(struct ain *ain) { return false; }
void escalayer_reboot_replace_cn_ui_text(struct ain *ain) {}

static size_t add_literal(struct ain *ain, struct buffer *code, const char *name, int literal)
{
    int fn = ain_add_function(ain, name);
    buffer_write_int16(code, FUNC);
    buffer_write_int32(code, fn);
    ain->functions[fn].address = code->index;
    size_t operand = code->index + 2;
    buffer_write_int16(code, S_PUSH);
    buffer_write_int32(code, literal);
    buffer_write_int16(code, ENDFUNC);
    buffer_write_int32(code, fn);
    return operand;
}

static void verify(bool maman, bool gbk)
{
    struct ain *ain = ain_new(8, 0);
    if (maman) {
        ain_add_struct(ain, "C_BOOT_WARNING");
        ain_add_struct(ain, "SaveData::C_PARAMETER");
    }
    config.game_name = gbk ? "\xb6\xe0\xc4\xc8" : NULL;
    const char *format = "\x81\x79%s\x81\x7a";
    const char *placeholder = "\x81\x96\x81\x96\x81\x96";
    int format_no = ain_add_string(ain, format);
    int placeholder_no = ain_add_string(ain, placeholder);
    struct buffer code;
    buffer_init(&code, NULL, 0);
    size_t name_op = add_literal(ain, &code, "MessageWindow::C_NAME_PLATE@Open", format_no);
    size_t scene_op = add_literal(ain, &code, "\x82\x64\x82\x75\x81\x5e" "scene", placeholder_no);
    size_t unrelated_op = add_literal(ain, &code, "OtherFunction", placeholder_no);
    ain->code = code.buf;
    ain->code_size = code.index;
    apply_game_specific_hacks(ain);
    assert(sys4_text_is_gbk == gbk);
    assert(!strcmp(ain->strings[LittleEndian_getDW(ain->code, name_op)]->text,
                   maman && gbk ? "\xa1\xbe%s\xa1\xbf" : format));
    assert(!strcmp(ain->strings[LittleEndian_getDW(ain->code, scene_op)]->text,
                   maman && gbk ? "\xa3\xaa\xa3\xaa\xa3\xaa" : placeholder));
    assert(LittleEndian_getDW(ain->code, unrelated_op) == placeholder_no);
    assert(!strcmp(ain->strings[placeholder_no]->text, placeholder));
    ain_free(ain);
}

int main(void)
{
    verify(true, true);
    verify(true, false);
    verify(false, true);
    puts("OK");
    return 0;
}
"""
        out = compile_and_run(
            harness,
            [ROOT / "src/hacks.c", *LIBSYS4_CORE],
            ["-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections", "-lz", "-lm"],
        )
        self.assertEqual(out.strip(), "OK")

    def test_maman_backlog_hll_string_returns(self):
        harness = """
#include <assert.h>
#include "AnteaterADVEngine.h"
#include "hll.h"
#include "system4/string.h"
#include "vm/heap.h"
#include "vm/page.h"

struct ain *ain;
extern struct static_library lib_AnteaterADVEngine;

_Noreturn void _vm_error(const char *fmt, ...) { abort(); }
struct page *alloc_array(int r, union vm_value *d, enum ain_data_type t, int s, bool i) { abort(); }
struct page *alloc_page(enum page_type t, int i, int n) { abort(); }
enum ain_data_type array_type(enum ain_data_type t) { abort(); }
void delete_page_vars(struct page *p) { abort(); }
void free_page(struct page *p) { abort(); }
int32_t heap_alloc_page(struct page *p) { abort(); }
int32_t heap_alloc_string(struct string *s) { abort(); }
struct page *heap_get_page(int i) { abort(); }
struct string *heap_get_string(int i) { abort(); }

static struct static_hll_function *find_export(const char *name) {
    for (struct static_hll_function *f = lib_AnteaterADVEngine.functions; f->name; f++)
        if (!strcmp(f->name, name))
            return f;
    abort();
}

void static_library_replace(struct static_library *lib, const char *name, void *fun) {
    assert(lib == &lib_AnteaterADVEngine);
    find_export(name)->fun = fun;
}

int main(void) {
    struct ain_hll_argument window_args[] = {
        { .type.data = AIN_STRING }, { .type.data = AIN_STRING },
    };
    struct ain_hll_argument getter_args[] = {
        { .type.data = AIN_INT }, { .type.data = AIN_INT }, { .type.data = AIN_REF_STRING },
    };
    struct ain_hll_function functions[] = {
        { .name = "ADVLogList_AddText", .nr_arguments = 2, .arguments = window_args },
        { .name = "ADVLogList_AddVoice", .nr_arguments = 1, .arguments = window_args },
        { .name = "ADVLogList_GetADVLogText", .return_type.data = AIN_STRING,
          .nr_arguments = 2, .arguments = getter_args },
        { .name = "ADVLogList_GetADVLogVoice", .return_type.data = AIN_STRING,
          .nr_arguments = 2, .arguments = getter_args },
    };
    struct ain_library library = {
        .name = "AnteaterADVEngine", .nr_functions = 4, .functions = functions,
    };
    struct ain maman = { .version = 8, .nr_libraries = 1, .libraries = &library };
    ain = &maman;
    ((void (*)(void))find_export("_PreLink")->fun)();

    struct string *line = make_string("dialogue", 8);
    struct string *name = make_string("voice_01", 8);
    ((void (*)(struct string *, struct string *))find_export("ADVLogList_AddText")->fun)
        (line, &EMPTY_STRING);
    ((void (*)(struct string *))find_export("ADVLogList_AddVoice")->fun)(name);
    free_string(line);
    free_string(name);

    struct static_hll_function *text = find_export(functions[2].name);
    struct static_hll_function *voice = find_export(functions[3].name);
    assert(text->resolve && voice->resolve);
    struct string *(*get_text)(int, int) = text->resolve(&functions[2]);
    struct string *(*get_voice)(int, int) = voice->resolve(&functions[3]);
    line = get_text(0, 0);
    name = get_voice(0, 0);
    struct string *missing = get_voice(0, 1);
    assert(!missing->size);
    free_string(missing);

    struct ain_hll_function ref_string = {
        .nr_arguments = 3, .arguments = getter_args, .return_type.data = AIN_VOID,
    };
    void (*get_text_ref)(int, int, struct string **) = text->resolve(&ref_string);
    void (*get_voice_ref)(int, int, struct string **) = voice->resolve(&ref_string);
    struct string *ref = NULL;
    get_text_ref(0, 0, &ref);
    assert(ref == line);
    get_voice_ref(0, 0, &ref);
    assert(ref == name);
    free_string(ref);
    ((void (*)(void))find_export("ADVLogList_Clear")->fun)();
    assert(!strcmp(line->text, "dialogue"));
    assert(!strcmp(name->text, "voice_01"));
    free_string(line);
    free_string(name);

    struct ain_hll_function int_voice = {
        .nr_arguments = 2, .arguments = getter_args, .return_type.data = AIN_INT,
    };
    int (*get_int_voice)(int, int) = voice->resolve(&int_voice);
    ADVLogList_AddVoice(42);
    assert(get_int_voice(0, 0) == 42);
    ADVLogList_Clear();
    puts("OK");
    return 0;
}
"""
        out = compile_and_run(
            harness,
            [ROOT / "src/hll/AnteaterADVEngine.c", ROOT / "src/hll/iarray.c", *LIBSYS4_CORE],
            ["-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections", "-lz", "-lm"],
        )
        self.assertEqual(out.strip(), "OK")

    def test_bfyy_mod_archive_and_maman_hacks(self):
        afa_fn = _extract_def("src/asset_manager.c", "afa_is_mod_archive")
        harness = f"""
#include <assert.h>
#include <ctype.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "xsystem4.h"
#include "system4/ain.h"
#include "system4/string.h"

void apply_game_specific_hacks(struct ain *ain);
struct config config;
void message_layout_init_rance03(void) {{}}
void escalayer_reboot_replace_cn_ui_text(struct ain *ain) {{ (void)ain; }}
_Noreturn void _vm_error(const char *fmt, ...) {{ abort(); }}

{afa_fn}

int main(void) {{
    // 1. BFYY and Ribuluo MOD .afa archives must be recognized as MOD priority.
    assert(afa_is_mod_archive("Rance10CGBFYY.afa"));
    assert(afa_is_mod_archive("/data/Rance10CGBFYY2.afa"));
    assert(afa_is_mod_archive("Rance10FlatBFYY.afa"));
    assert(afa_is_mod_archive("rance10cgbfyy3.afa"));
    assert(afa_is_mod_archive("Rance10MODCG.afa"));
    assert(afa_is_mod_archive("Rance10ModFlat.afa"));
    assert(!afa_is_mod_archive("Rance10CG.afa"));
    assert(!afa_is_mod_archive("Rance10Flat.afa"));
    assert(!afa_is_mod_archive("MamanCG.afa"));
    assert(!afa_is_mod_archive("MamanFlat.afa"));

    // 2. Heartful Maman AIN v8 detection via structs sets game_maman and 1280x720 default viewport.
    struct ain *maman_ain = ain_new(8, 0);
    ain_add_struct(maman_ain, "C_BOOT_WARNING");
    ain_add_struct(maman_ain, "SaveData::C_PARAMETER");
    memset(&config, 0, sizeof(config));
    config.view_width = 800;
    config.view_height = 600;
    apply_game_specific_hacks(maman_ain);
    assert(game_maman == true);
    assert(config.view_width == 1280 && config.view_height == 720);
    ain_free(maman_ain);

    // 3. Filename fallback when structs differ still detects Maman.ain on AIN v8.
    struct ain *maman_ain2 = ain_new(8, 0);
    memset(&config, 0, sizeof(config));
    config.view_width = 800;
    config.view_height = 600;
    config.ain_filename = strdup("/games/Maman/Maman.ain");
    apply_game_specific_hacks(maman_ain2);
    assert(game_maman == true);
    assert(config.view_width == 1280 && config.view_height == 720);
    free(config.ain_filename);
    ain_free(maman_ain2);

    // 4. Non-Maman AIN v8 does not falsely match game_maman.
    struct ain *other_ain = ain_new(8, 0);
    memset(&config, 0, sizeof(config));
    config.view_width = 800;
    config.view_height = 600;
    config.ain_filename = strdup("/games/Other/Other.ain");
    apply_game_specific_hacks(other_ain);
    assert(game_maman == false);
    assert(config.view_width == 800 && config.view_height == 600);
    free(config.ain_filename);
    ain_free(other_ain);

    puts("OK");
    return 0;
}}
"""
        out = compile_and_run(
            harness,
            [ROOT / "src/hacks.c", *LIBSYS4_CORE],
            ["-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections", "-lz", "-lm"],
        )
        self.assertEqual(out.strip(), "OK")

    def test_ain_v8_hll_message_and_parts_update_dispatch(self):
        v12_builtin_fn = _extract_def("src/vm_v12.c", "v12_builtin_library")
        parts_engine_src = (ROOT / "src/hll/PartsEngine.c").read_text(encoding="utf-8", errors="ignore")
        self.assertIn("PartsEngine_Update_v8", parts_engine_src)
        self.assertIn("fun->arguments[1].type.data == AIN_BOOL", parts_engine_src)

        harness = f"""
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "system4.h"
#include "system4/ain.h"
#include "system4/string.h"
#include "parts.h"
#include "parts_internal.h"
#include "vm.h"

struct ain *ain = NULL;
bool parts_v2 = true;
bool parts_multi_controller = true;
bool game_tsumamigui3_jun = false;
bool game_tsumamigui3_ntr = false;
_Noreturn void _vm_error(const char *fmt, ...) {{ abort(); }}

{v12_builtin_fn}

int main(void) {{
    // 1. v12_builtin_library: AIN v8 enables String/Int/Float only; AIN v12 enables all 6; AIN v6 enables none.
    struct ain a6 = {{ .version = 6 }};
    struct ain a8 = {{ .version = 8 }};
    struct ain a12 = {{ .version = 12 }};

    ain = &a6;
    assert(!v12_builtin_library("String"));
    assert(!v12_builtin_library("Int"));
    assert(!v12_builtin_library("Float"));
    assert(!v12_builtin_library("Array"));

    ain = &a8;
    assert(v12_builtin_library("String"));
    assert(v12_builtin_library("Int"));
    assert(v12_builtin_library("Float"));
    assert(!v12_builtin_library("Array"));
    assert(!v12_builtin_library("Delegate"));
    assert(!v12_builtin_library("system"));

    ain = &a12;
    assert(v12_builtin_library("String"));
    assert(v12_builtin_library("Int"));
    assert(v12_builtin_library("Float"));
    assert(v12_builtin_library("Array"));
    assert(v12_builtin_library("Delegate"));
    assert(v12_builtin_library("system"));

    // 2. Parts message queue on AIN v8: empty queue returns -1; mouse/swipe IDs map via message_type_v8.
    ain = &a8;
    PE_ReleaseMessage();
    assert(PE_GetMessageType() == -1);

    struct parts dummy_part;
    memset(&dummy_part, 0, sizeof(dummy_part));
    dummy_part.no = 42;
    dummy_part.delegate_index = 7;

    parts_msg_push(&dummy_part, PARTS_MSG_MOUSE_ENTER, "");
    assert(PE_GetMessageType() == 0);
    PE_PopMessage();

    parts_msg_push(&dummy_part, PARTS_MSG_MOUSE_CLICK, "ii", 10, 20);
    assert(PE_GetMessageType() == 4);
    assert(PE_GetMessagePartsNumber() == 42);
    PE_PopMessage();

    parts_msg_push(&dummy_part, PARTS_MSG_SWIPE_BEGIN, "");
    assert(PE_GetMessageType() == 26);
    PE_PopMessage();

    assert(PE_GetMessageType() == -1);

    // 3. AIN v6 empty queue still returns 0 (no regression for Rance IX).
    ain = &a6;
    assert(PE_GetMessageType() == 0);

    puts("OK");
    return 0;
}}
"""
        out = compile_and_run(
            harness,
            [ROOT / "src/parts/message.c", *LIBSYS4_CORE],
            [f"-I{ROOT / 'src/parts'}", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections", "-lz", "-lm"],
        )
        self.assertEqual(out.strip(), "OK")

    def test_parts_v2_scrollbar_ignores_nonpositive_part_numbers(self):
        harness = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "system4.h"
#include "system4/ain.h"
#include "system4/string.h"
#include "parts.h"
#include "parts_scrollbar.h"
#include "parts_internal.h"
#include "vm.h"

struct ain *ain = NULL;
bool parts_v2 = true;

static struct parts g_parts[16];
static bool g_exists[16];
static int g_parts_get_calls_on_nonpositive = 0;

struct parts *parts_get(int parts_no) {
    if (parts_no <= 0) {
        g_parts_get_calls_on_nonpositive++;
    }
    int slot = parts_no < 0 ? 0 : (parts_no < 16 ? parts_no : 15);
    g_exists[slot] = true;
    g_parts[slot].no = parts_no;
    return &g_parts[slot];
}

struct parts *parts_try_get(int parts_no) {
    if (parts_no < 0 || parts_no >= 16)
        return NULL;
    return g_exists[parts_no] ? &g_parts[parts_no] : NULL;
}

void parts_dirty(struct parts *parts) { (void)parts; }
void parts_set_dims(struct parts *parts, struct parts_common *common, int w, int h) {
    (void)parts; (void)common; (void)w; (void)h;
}
void parts_component_dirty(struct parts *parts) { (void)parts; }
void parts_recalculate_hitbox(struct parts *parts) { (void)parts; }
void parts_widget_free(struct parts *parts) { (void)parts; }
void parts_panel_free(struct parts *parts) { (void)parts; }
void parts_message_window_free(struct parts *parts) { (void)parts; }
void parts_state_reset(struct parts_state *state, enum parts_type type) {
    memset(state, 0, sizeof(*state));
    state->type = type;
}
bool parts_cg_set(struct parts *parts, struct parts_cg *cg, struct string *name) {
    (void)parts; (void)cg; (void)name; return false;
}
bool parts_flat_load(struct parts *parts, struct parts_flat *flat, struct string *name) {
    (void)parts; (void)flat; (void)name; return false;
}
const char *parts_utf8_to_cjk_literal(const char *utf8) { return utf8; }
void parts_msg_push(struct parts *parts, int type, const char *fmt, ...) {
    (void)parts; (void)type; (void)fmt;
}
void parts_set_surface_area(struct parts *parts, struct parts_common *common, int x, int y, int w, int h) {
    (void)parts; (void)common; (void)x; (void)y; (void)w; (void)h;
}
void parts_render_state(struct parts *parts, struct parts_state *state) { (void)parts; (void)state; }
void gfx_init_texture_rgba(Texture *dst, int w, int h, SDL_Color color) { (void)dst; (void)w; (void)h; (void)color; }
void gfx_delete_texture(Texture *t) { (void)t; }
void gfx_render_texture(Texture *t, Rectangle *r) { (void)t; (void)r; }
void gfx_fill_amap(Texture *t, int x, int y, int w, int h, int a) { (void)t; (void)x; (void)y; (void)w; (void)h; (void)a; }
void gfx_fill(Texture *t, int x, int y, int w, int h, int r, int g, int b) { (void)t; (void)x; (void)y; (void)w; (void)h; (void)r; (void)g; (void)b; }
_Noreturn void _vm_error(const char *fmt, ...) { abort(); }

int main(void) {
    memset(g_parts, 0, sizeof(g_parts));
    memset(g_exists, 0, sizeof(g_exists));
    parts_v2 = true;

    // Calling scrollbar/sliderbar APIs with no <= 0 in parts_v2 must NOT allocate part 0 or a phantom scrollbar!
    PE_SetHScrollbarScrollRate(0, 1.0f);
    PE_SetVScrollbarScrollRate(0, 0.5f);
    PE_SetHScrollbarTotalSize(0, 100);
    PE_SetVScrollbarViewSize(0, 50);
    PE_SetHScrollbarMoveSizeByButton(0, 10);
    PE_SetHSliderBarScrollRate(0, 0.75f);
    PE_SetVSliderBarScrollPos(-1, 5);
    assert(PE_GetHScrollbarTotalSize(0) == 0);
    assert(PE_GetVScrollbarScrollPos(0) == 0);
    assert(PE_GetHScrollbarScrollRate(0) == 0.f);
    assert(PE_GetHSliderBarScrollRate(0) == 0.f);
    assert(PE_GetHScrollTotalSizeLinkNumber(0) == 0);
    assert(g_parts_get_calls_on_nonpositive == 0);
    assert(!g_exists[0]);
    assert(g_parts[0].scrollbar == NULL);

    // Valid positive part numbers still create and update scrollbars normally in parts_v2.
    PE_SetHScrollbarTotalSize(5, 200);
    PE_SetHScrollbarViewSize(5, 100);
    PE_SetHScrollbarScrollRate(5, 0.5f);
    assert(g_exists[5] && g_parts[5].scrollbar != NULL);
    assert(PE_GetHScrollbarTotalSize(5) == 200);
    assert(PE_GetHScrollbarScrollPos(5) == 50);
    // Mismatched orientation or scrollbar/sliderbar kind must return 0 without reading/clobbering part 5.
    assert(PE_GetVScrollbarTotalSize(5) == 0);
    assert(PE_GetVScrollbarScrollPos(5) == 0);
    assert(PE_GetHSliderBarTotalSize(5) == 0);
    assert(PE_GetHSliderBarScrollPos(5) == 0);
    assert(PE_GetVSliderBarTotalSize(5) == 0);
    assert(PE_GetHScrollbarTotalSize(5) == 200);
    assert(PE_GetHScrollbarScrollPos(5) == 50);
    parts_scrollbar_free(&g_parts[5]);

    // Sliderbar getters match their own kind/orientation and reject mismatched kinds/orientations.
    PE_SetVSliderBarTotalSize(6, 300);
    PE_SetVSliderBarViewSize(6, 100);
    PE_SetVSliderBarScrollPos(6, 80);
    assert(PE_GetVSliderBarTotalSize(6) == 300);
    assert(PE_GetVSliderBarScrollPos(6) == 80);
    assert(PE_GetHSliderBarTotalSize(6) == 0);
    assert(PE_GetVScrollbarTotalSize(6) == 0);
    assert(PE_GetHScrollbarTotalSize(6) == 0);
    assert(PE_GetVSliderBarTotalSize(6) == 300);
    parts_scrollbar_free(&g_parts[6]);

    // Legacy parts_v2 == false keeps 0-based part IDs working.
    parts_v2 = false;
    PE_SetHScrollbarTotalSize(0, 120);
    assert(g_exists[0] && g_parts[0].scrollbar != NULL);
    assert(PE_GetHScrollbarTotalSize(0) == 120);
    parts_scrollbar_free(&g_parts[0]);

    puts("OK");
    return 0;
}
"""
        out = compile_and_run(
            harness,
            [ROOT / "src/parts/scrollbar.c", *LIBSYS4_CORE],
            [f"-I{ROOT / 'src/parts'}", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections", "-lz", "-lm"],
        )
        self.assertEqual(out.strip(), "OK")


if __name__ == "__main__":
    unittest.main()
