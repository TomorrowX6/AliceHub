#!/usr/bin/env python3
"""Dependency-free native regression tests for VM and ReignEngine fixes.

Compiles the actual C implementations from xsystem4/src/vm.c and
xsystem4/src/3d/reign.c against minimal fixtures to verify:
1. struct_page_slot() / get_struct_page() inherit enclosing `this` across
   non-lambda CALLFUNC frames (@2 member initializers & intra-class helpers)
   while stopping at explicit has_struct_page barriers (null-this CALLMETHOD,
   free-function delegates, scenario_call, vm_call(..., -1)).
2. AIN v14 X_A_INIT does not prematurely heap_unref live array slots when the
   compiler reuses a scratch carrier variable across bulk X_ASSIGN sequences.
3. RE_set_projection() returns true (with a finite identity matrix) when
   height == 0.0f instead of failing TapirEngine.SetProjection in Evenicle.

Run: python3 xsystem4/test/native/test_vm_reign_regressions.py
"""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def extract_function(rel_path: str, name: str) -> str:
    source = (ROOT / rel_path).read_text(encoding="utf-8")
    idx = source.index(name + "(")
    start = source.rfind("\n", 0, idx) + 1
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def extract_case_block(rel_path: str, case_label: str) -> str:
    source = (ROOT / rel_path).read_text(encoding="utf-8")
    idx = source.index(f"case {case_label}:")
    brace = source.index("{", idx)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[idx:end]


def compile_and_run(c_source: str) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "test.c"
        exe = Path(tmp) / "test_bin"
        src.write_text(c_source, encoding="utf-8")
        subprocess.run(
            ["cc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2", str(src), "-lm", "-o", str(exe)],
            check=True,
        )
        subprocess.run([str(exe)], check=True)


class VmAndReignRegressionTests(unittest.TestCase):
    def test_struct_page_slot_and_get_struct_page(self) -> None:
        fn_slot = extract_function("src/vm.c", "struct_page_slot")
        fn_get = extract_function("src/vm.c", "get_struct_page")
        c_code = f"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#define unlikely(x) (x)

struct page {{
    int dummy;
}};

struct heap_slot {{
    struct page *page;
    int ref;
}};

struct function_call {{
    int fno;
    int32_t struct_page;
    bool has_struct_page;
}};

struct ain_function {{
    int is_lambda;
}};

struct ain {{
    int nr_functions;
    struct ain_function *functions;
}};

static struct heap_slot heap[128];
static struct function_call call_stack[32];
static int call_stack_ptr = 0;
static struct ain_function funcs[8];
static struct ain ain_obj = {{ .nr_functions = 8, .functions = funcs }};
__attribute__((unused)) static struct ain *ain = &ain_obj;

static bool page_index_valid(int slot) {{
    return slot >= 0 && slot < 128 && heap[slot].ref > 0 && heap[slot].page != NULL;
}}

{fn_slot}
{fn_get}

int main(void) {{
    struct page obj_page = {{ .dummy = 42 }};
    heap[86].page = &obj_page;
    heap[86].ref = 1;

    // #0: "0" / main called via vm_call(fno, -1) -> has_struct_page=true, struct_page=-1
    call_stack[0] = (struct function_call){{ .fno = 0, .struct_page = -1, .has_struct_page = true }};
    // #1: CSpriteParts@0 constructor -> has_struct_page=true, struct_page=86
    call_stack[1] = (struct function_call){{ .fno = 1, .struct_page = 86, .has_struct_page = true }};
    // #2: CSpriteParts@2 or CASTimerImp@Reset via CALLFUNC (is_lambda=0, has_struct_page=false)
    funcs[2].is_lambda = 0;
    call_stack[2] = (struct function_call){{ .fno = 2, .struct_page = -1, .has_struct_page = false }};
    call_stack_ptr = 3;

    assert(struct_page_slot() == 86);
    assert(get_struct_page(0) == &obj_page);

    // #3: nested non-lambda helper via CALLFUNC -> still inherits 86
    funcs[3].is_lambda = 0;
    call_stack[3] = (struct function_call){{ .fno = 3, .struct_page = -1, .has_struct_page = false }};
    call_stack_ptr = 4;
    assert(struct_page_slot() == 86);
    assert(get_struct_page(0) == &obj_page);

    // #4: null-this CALLMETHOD / free-function delegate / scenario_call -> barrier!
    call_stack[4] = (struct function_call){{ .fno = 4, .struct_page = -1, .has_struct_page = true }};
    call_stack_ptr = 5;
    assert(struct_page_slot() == -1);
    assert(get_struct_page(0) == NULL);

    // #5: helper called via CALLFUNC from inside null-this method -> stops at #4 (-1), does NOT steal 86!
    call_stack[5] = (struct function_call){{ .fno = 2, .struct_page = -1, .has_struct_page = false }};
    call_stack_ptr = 6;
    assert(struct_page_slot() == -1);
    assert(get_struct_page(0) == NULL);
    // Frame 4 (index 1 from bottom = call_stack[1]) still sees &obj_page
    assert(get_struct_page(4) == &obj_page);

    return 0;
}}
"""
        compile_and_run(c_code)

    def test_x_a_init_reused_carrier_preserves_live_slots(self) -> None:
        case_block = extract_case_block("src/vm.c", "X_A_INIT")
        c_code = f"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

enum ain_data_type {{
    AIN_VOID = 0,
    AIN_INT = 10,
    AIN_ARRAY = 14,
    AIN_WRAP = 100,
    AIN_OPTION = 101,
}};

enum page_type {{
    GLOBAL_PAGE = 0,
    LOCAL_PAGE = 1,
    STRUCT_PAGE = 2,
    ARRAY_PAGE = 3,
}};

struct ain_type {{
    enum ain_data_type data;
    int struc;
    struct ain_type *array_type;
}};

union vm_value {{
    int32_t i;
    float f;
}};

struct page {{
    enum page_type type;
    enum ain_data_type a_type;
    int nr_vars;
    struct {{
        int rank;
        int struct_type;
        struct ain_type element_type;
    }} array;
    union vm_value values[];
}};

struct heap_slot {{
    struct page *page;
    int ref;
}};

static struct heap_slot heap[64];
static int next_slot = 1;
static union vm_value stack[64];
static int stack_ptr = 0;
static unsigned int invalid_var_warn_count = 0;
#define INVALID_VAR_WARN_LIMIT 64
#define WARNING(...) ((void)0)

static void stack_push(int32_t v) {{ stack[stack_ptr++].i = v; }}
static union vm_value stack_pop(void) {{ return stack[--stack_ptr]; }}

static bool page_index_valid(int slot) {{
    return slot >= 0 && slot < 64 && heap[slot].ref > 0 && heap[slot].page != NULL;
}}

static bool heap_index_valid(int slot) {{
    return slot >= 0 && slot < 64 && heap[slot].ref > 0;
}}

static struct page *alloc_page(enum page_type t, int sub, int nr_vars) {{
    struct page *p = calloc(1, sizeof(struct page) + (size_t)nr_vars * sizeof(union vm_value));
    p->type = t;
    p->a_type = (enum ain_data_type)sub;
    p->nr_vars = nr_vars;
    return p;
}}

static int heap_alloc_page(struct page *p) {{
    int s = next_slot++;
    heap[s].page = p;
    heap[s].ref = 1;
    return s;
}}

static void heap_unref(int slot) {{
    assert(heap_index_valid(slot));
    if (--heap[slot].ref == 0) {{
        free(heap[slot].page);
        heap[slot].page = NULL;
    }}
}}

static struct ain_type elem_int = {{ .data = AIN_INT, .struc = -1, .array_type = NULL }};
static struct ain_type arr_int = {{ .data = AIN_ARRAY, .struc = -1, .array_type = &elem_int }};

static const struct ain_type *lookup_expected_var_type(int32_t pageno, int32_t varno) {{
    (void)pageno;
    (void)varno;
    return &arr_int;
}}

static int ain_type_slot_width(const struct ain_type *t) {{ (void)t; return 1; }}
static bool ain_type_is_null_handle(enum ain_data_type dt) {{ (void)dt; return false; }}

static struct page *v12_array_new(const struct ain_type *elem_t, int count, bool init) {{
    (void)init;
    struct page *p = alloc_page(ARRAY_PAGE, AIN_ARRAY, count);
    p->array.rank = 1;
    p->array.struct_type = -1;
    p->array.element_type = elem_t ? *elem_t : elem_int;
    return p;
}}

enum opcode {{ X_A_INIT = 1 }};
static int cur_arg0 = 0;
static int get_argument(int idx) {{ assert(idx == 0); return cur_arg0; }}

static void run_x_a_init(int init_elems) {{
    cur_arg0 = init_elems;
    switch (X_A_INIT) {{
    {case_block}
    }}
}}

int main(void) {{
    // Allocate global page (slot 0) and local scratch page (slot 1)
    heap[0].page = alloc_page(GLOBAL_PAGE, 0, 4);
    heap[0].ref = 1;
    int local_slot = heap_alloc_page(alloc_page(LOCAL_PAGE, 0, 2));
    heap[local_slot].page->values[0].i = -1;

    // 1st X_A_INIT on scratch carrier (local_slot, 0), transferred to global[0] via X_ASSIGN
    stack_push(local_slot);
    stack_push(0);
    stack_push(4);
    run_x_a_init(0);
    int arr1 = stack_pop().i;
    heap[0].page->values[0].i = arr1; // X_ASSIGN raw slot transfer (ref stays 1)

    // 2nd X_A_INIT reusing the same scratch carrier (local_slot, 0)
    stack_push(local_slot);
    stack_push(0);
    stack_push(8);
    run_x_a_init(0);
    int arr2 = stack_pop().i;
    heap[0].page->values[1].i = arr2;

    // Both arr1 and arr2 MUST still be alive with ref == 1 and type == ARRAY_PAGE!
    assert(arr1 != arr2);
    assert(page_index_valid(arr1) && heap[arr1].page->type == ARRAY_PAGE && heap[arr1].page->nr_vars == 4);
    assert(page_index_valid(arr2) && heap[arr2].page->type == ARRAY_PAGE && heap[arr2].page->nr_vars == 8);
    assert(heap[arr1].ref == 1);
    assert(heap[arr2].ref == 1);

    heap_unref(arr1);
    heap_unref(arr2);
    heap_unref(local_slot);
    free(heap[0].page);
    return 0;
}}
"""
        compile_and_run(c_code)

    def test_re_set_projection_zero_height_succeeds(self) -> None:
        fn_proj = extract_function("src/3d/reign.c", "RE_set_projection")
        c_code = f"""
#include <assert.h>
#include <math.h>
#include <stdbool.h>
#include <string.h>

typedef float mat4[4][4];

struct RE_plugin {{
    mat4 proj_transform;
}};

static float glm_rad(float deg) {{
    return deg * 0.017453292519943295f;
}}

static void glm_mat4_identity(mat4 m) {{
    memset(m, 0, sizeof(mat4));
    for (int i = 0; i < 4; i++)
        m[i][i] = 1.0f;
}}

static void glm_perspective(float fovy, float aspect, float nearZ, float farZ, mat4 dest) {{
    memset(dest, 0, sizeof(mat4));
    float f = 1.0f / tanf(fovy * 0.5f);
    dest[0][0] = f / aspect;
    dest[1][1] = f;
    dest[2][2] = (farZ + nearZ) / (nearZ - farZ);
    dest[2][3] = -1.0f;
    dest[3][2] = (2.0f * farZ * nearZ) / (nearZ - farZ);
}}

{fn_proj}

int main(void) {{
    assert(!RE_set_projection(NULL, 1280.0f, 720.0f, 1.0f, 1000.0f, 60.0f));

    struct RE_plugin p;
    memset(&p, 0x7f, sizeof(p));

    // Zero-height initial projection call (Evenicle TapirEngine setup) must succeed cleanly!
    assert(RE_set_projection(&p, 1280.0f, 0.0f, 1.0f, 1000.0f, 60.0f));
    for (int c = 0; c < 4; c++) {{
        for (int r = 0; r < 4; r++) {{
            assert(isfinite(p.proj_transform[c][r]));
            assert(p.proj_transform[c][r] == (c == r ? 1.0f : 0.0f));
        }}
    }}

    // Normal non-zero projection call
    assert(RE_set_projection(&p, 1280.0f, 720.0f, 1.0f, 1000.0f, 60.0f));
    assert(isfinite(p.proj_transform[0][0]) && p.proj_transform[0][0] > 0.0f);
    assert(isfinite(p.proj_transform[1][1]) && p.proj_transform[1][1] > 0.0f);
    // Horizontal FOV convention: [0][0] == f, [1][1] == f * aspect
    float aspect = 1280.0f / 720.0f;
    assert(fabsf(p.proj_transform[1][1] / p.proj_transform[0][0] - aspect) < 1e-5f);
    return 0;
}}
"""
        compile_and_run(c_code)

    def test_rance6_hacks_and_gbk_detection(self) -> None:
        fn_r6 = extract_function("src/hacks.c", "apply_rance6_hacks")
        fn_gbk_str = extract_function("src/hacks.c", "ain_has_gbk_string")
        fn_r03_str = extract_function("src/hacks.c", "ain_has_gbk_r03_strings")
        fn_hacks = extract_function("src/hacks.c", "apply_game_specific_hacks")
        fn_cfg = extract_function("src/system4.c", "config_init")
        c_code = f"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

struct string {{
    char *text;
}};

struct ain {{
    int version;
    int nr_strings;
    struct string **strings;
    const char *const *funcs;
    const char *const *globals;
    const char *const *structs;
    const char *const *libs;
}};

struct config {{
    char *game_name;
    char *ain_filename;
    char *save_dir;
    char *home_dir;
    int view_width;
    int view_height;
}} config;

bool game_daibanchou_en = false;
bool game_rance01 = false;
bool game_rance02 = false;
bool game_rance02_mg = false;
bool game_rance6 = false;
bool game_rance6_mg = false;
bool game_rance7_mg = false;
bool game_sengokurance = false;
bool game_rance8 = false;
bool game_rance8_mg = false;
bool game_rance9 = false;
bool game_rance10 = false;
bool game_dohnadohna = false;
bool game_evenicle = false;
bool game_evenicle2 = false;
bool game_rance03 = false;
bool game_dungeons_and_dolls = false;
bool game_escalayer_reboot = false;
bool game_galzoo = false;
bool game_ixseal = false;
bool game_tsumashibori = false;

static bool sys4_text_is_gbk = false;
static bool gbk_at_get_save_path = false;
static void sys4_set_text_gbk(bool enabled) {{ sys4_text_is_gbk = enabled; }}
static char *xstrdup(const char *s) {{
    if (!s) s = "";
    size_t n = strlen(s) + 1;
    char *p = malloc(n);
    memcpy(p, s, n);
    return p;
}}
static char *get_xsystem4_home(void) {{ return xstrdup("/tmp/xsys4_home"); }}
static char *get_save_path(const char *dir) {{
    gbk_at_get_save_path = sys4_text_is_gbk;
    return xstrdup(dir ? dir : "SaveData");
}}
static char *sjis2utf(const char *s, size_t len) {{
    (void)len;
    if (!s) return xstrdup("");
    if (!strcmp(s, "\\x83\\x89\\x83\\x93\\x83\\x58\\x82\\x55")) return xstrdup("ランス６");
    return xstrdup(s);
}}

static int list_find(const char *const *list, const char *name) {{
    if (!list || !name) return -1;
    for (int i = 0; list[i]; i++) {{
        if (!strcmp(list[i], name)) return i;
    }}
    return -1;
}}
static int ain_get_function(struct ain *ain, const char *name) {{ return list_find(ain->funcs, name); }}
static int ain_get_global(struct ain *ain, const char *name) {{ return list_find(ain->globals, name); }}
static int ain_get_struct(struct ain *ain, const char *name) {{ return list_find(ain->structs, name); }}
static int ain_get_library(struct ain *ain, const char *name) {{ return list_find(ain->libs, name); }}

static void apply_daibanchou_hacks(struct ain *ain) {{ (void)ain; }}
static void apply_rance02_hacks(struct ain *ain) {{ (void)ain; }}
static void apply_rance9_hacks(struct ain *ain) {{ (void)ain; }}
static void apply_rance03_message_layout(struct ain *ain) {{ (void)ain; }}
static void apply_rance03_card_caption_layout(struct ain *ain) {{ (void)ain; }}
static void apply_rance03_effect_sound_paths(struct ain *ain) {{ (void)ain; }}
static void apply_rance03_skill_cooldown_layout(struct ain *ain) {{ (void)ain; }}
static void escalayer_reboot_replace_cn_ui_text(struct string **strings, int nr_strings) {{ (void)strings; (void)nr_strings; }}

{fn_r6}
{fn_gbk_str}
{fn_r03_str}
{fn_hacks}
{fn_cfg}

static void reset_config_for_test(const char *game_name, const char *ain_name) {{
    free(config.game_name);
    free(config.ain_filename);
    free(config.save_dir);
    free(config.home_dir);
    config.game_name = game_name ? xstrdup(game_name) : NULL;
    config.ain_filename = xstrdup(ain_name);
    config.save_dir = xstrdup("SaveData");
    config.home_dir = NULL;
    config.view_width = 800;
    config.view_height = 600;
    sys4_text_is_gbk = false;
    gbk_at_get_save_path = false;
}}

int main(void) {{
    const char *r6_libs[] = {{ "SACT2", "DrawGraph", "ACXLoader", "DrawDungeon", "PlayMovie", NULL }};
    const char *r6_structs[] = {{ "CDungeon", "CScreenBattle", NULL }};
    const char *jp_funcs[] = {{ "main", NULL }};
    const char *mg_funcs[] = {{ "main", "GetTextWidth", NULL }};

    // 1. JP Rance VI: Shift-JIS MS Gothic ("\\x82\\x6c\\x82\\x72 \\x83\\x53\\x83\\x56\\x83\\x62\\x83\\x4e"), GameName = "Rance6"
    struct string jp_s0 = {{ "\\x82\\x6c\\x82\\x72 \\x83\\x53\\x83\\x56\\x83\\x62\\x83\\x4e" }};
    struct string *jp_strs[] = {{ &jp_s0 }};
    struct ain jp_ain = {{
        .version = 4, .nr_strings = 1, .strings = jp_strs,
        .funcs = jp_funcs, .globals = NULL, .structs = r6_structs, .libs = r6_libs
    }};
    reset_config_for_test("Rance6", "Rance6.ain");
    config_init();
    assert(!gbk_at_get_save_path);
    assert(!sys4_text_is_gbk);
    apply_game_specific_hacks(&jp_ain);
    assert(game_rance6 && !game_rance6_mg && !sys4_text_is_gbk);
    assert(config.view_width == 640 && config.view_height == 480);

    // 2. MangaGamer English Rance VI (even with customized GameName!)
    struct ain mg_ain = jp_ain;
    mg_ain.funcs = mg_funcs;
    reset_config_for_test("Rance VI - The Collapse of Zeth", "Rance6.ain");
    config_init();
    apply_game_specific_hacks(&mg_ain);
    assert(game_rance6 && game_rance6_mg && !sys4_text_is_gbk);

    // 3. CN Rance VI v1.10 Simplified System40.ini:
    // GameName = "Rance6中文版 v1.10（哈尼喵汉化组×爱丽丝汉化组 制作）" in GBK
    struct string cn_s0 = {{ "\\xba\\xda\\xcc\\xe5" }}; // 黑体
    struct string *cn_strs[] = {{ &cn_s0 }};
    struct ain cn_ain = {{
        .version = 4, .nr_strings = 1, .strings = cn_strs,
        .funcs = jp_funcs, .globals = NULL, .structs = r6_structs, .libs = r6_libs
    }};
    reset_config_for_test("Rance6\\xd6\\xd0\\xce\\xc4\\xb0\\xe6 v1.10\\xa3\\xa8\\xba\\xba\\xbb\\xaf\\xd7\\xe9\\xa3\\xa9", "Rance6.ain");
    config_init();
    assert(gbk_at_get_save_path && sys4_text_is_gbk);
    apply_game_specific_hacks(&cn_ain);
    assert(game_rance6 && !game_rance6_mg && sys4_text_is_gbk);

    // 4. CN Rance VI v1.10 Traditional System40Tcn.ini ("中文版" + "漢化組")
    reset_config_for_test("Rance6\\xd6\\xd0\\xce\\xc4\\xb0\\xe6 v1.10\\xa3\\xa8\\x9e\\x61\\xbb\\xaf\\xbf\\x82\\xa3\\xa9", "Rance6.ain");
    config_init();
    assert(gbk_at_get_save_path && sys4_text_is_gbk);
    apply_game_specific_hacks(&cn_ain);
    assert(game_rance6 && !game_rance6_mg && sys4_text_is_gbk);

    // 5. CN Rance VI with unpatched JP GameName = "Rance6" (both v1.10 "黑体" and v0.90 GBK "ＭＳ ゴシック")
    reset_config_for_test("Rance6", "Rance6.ain");
    config_init();
    apply_game_specific_hacks(&cn_ain);
    assert(game_rance6 && !game_rance6_mg && sys4_text_is_gbk);

    struct string cn_v090_s0 = {{ "\\xa3\\xcd\\xa3\\xd3\\x20\\xa5\\xb4\\xa5\\xb7\\xa5\\xc3\\xa5\\xaf" }};
    struct string *cn_v090_strs[] = {{ &cn_v090_s0 }};
    struct ain cn_v090_ain = cn_ain;
    cn_v090_ain.strings = cn_v090_strs;
    reset_config_for_test("Rance6", "Rance6.ain");
    config_init();
    apply_game_specific_hacks(&cn_v090_ain);
    assert(game_rance6 && !game_rance6_mg && sys4_text_is_gbk);

    free(config.game_name);
    free(config.ain_filename);
    free(config.save_dir);
    free(config.home_dir);
    return 0;
}}
"""
        compile_and_run(c_code)


if __name__ == "__main__":
    unittest.main()
