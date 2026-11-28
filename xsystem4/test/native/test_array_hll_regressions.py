#!/usr/bin/env python3
"""Host regression tests for the v12/v14 Array HLL argument ABI.

Runs the production Array dispatcher and reference-element insertion helpers
with a small heap fixture; no game files or graphics context are required.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_vm_reign_regressions import extract_function

ROOT = Path(__file__).resolve().parents[2]

FIXTURE = r'''
#include <assert.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "vm.h"
#include "vm/heap.h"
#include "vm/page.h"
#include "vm/v12.h"

static struct ain program = {.version = 14};
struct ain *ain = &program;
static struct vm_pointer slots[64];
struct vm_pointer *heap = slots;
size_t heap_size = 64;
static union vm_value values[128];
union vm_value *stack = values;
int32_t stack_ptr;
static int warnings;

_Noreturn void _vm_error(const char *fmt, ...) { (void)fmt; abort(); }
void sys_warning(const char *fmt, ...) { (void)fmt; warnings++; }
void *_xmalloc(size_t n, const char *f) { (void)f; return malloc(n); }
void *_xcalloc(size_t n, size_t s, const char *f) { (void)f; return calloc(n, s); }
union vm_value stack_pop(void) { assert(stack_ptr > 0); return stack[--stack_ptr]; }
bool page_index_valid(int i) { return i >= 0 && i < 64 && heap[i].ref > 0; }
bool string_index_valid(int i) { (void)i; return false; }
struct page *heap_get_page(int i) { assert(page_index_valid(i)); return heap[i].page; }
void heap_set_page(int i, struct page *p) { assert(page_index_valid(i)); heap[i].page = p; }
void heap_ref(int i) { if (i >= 0) { assert(page_index_valid(i)); heap[i].ref++; } }
void heap_unref(int i) { if (i >= 0) { assert(page_index_valid(i)); heap[i].ref--; } }
struct page *alloc_page(enum page_type t, int index, int n) {
    struct page *p = calloc(1, sizeof(*p) + n * sizeof(union vm_value));
    p->type = t; p->index = index; p->nr_vars = n; return p;
}
void free_page(struct page *p) { free(p); }
int32_t heap_alloc_page(struct page *p) { (void)p; abort(); }
void heap_struct_assign(int dst, int src) { (void)dst; (void)src; abort(); }
int alloc_struct(int t) { (void)t; abort(); }
void init_struct(int t, int slot) { (void)t; (void)slot; abort(); }
void create_struct(int t, union vm_value *v) { (void)t; (void)v; abort(); }
int variable_init_typed(union vm_value *v, const struct ain_type *t, bool a, bool b) {
    (void)a; (void)b;
    assert(t->data == AIN_REF_INT);
    v[0].i = -1; v[1].i = 0; return 2;
}
union vm_value vm_copy(union vm_value v, enum ain_data_type t) {
    assert(t == AIN_REF_INT); heap_ref(v.i); return v;
}
void variable_fini(union vm_value v, enum ain_data_type t, bool dtor) {
    assert(t == AIN_REF_INT); assert(dtor); heap_unref(v.i);
}
void vm_call(int f, int obj) { (void)f; (void)obj; abort(); }
unsigned int SDL_GetTicks(void) { abort(); }
static void alloc_dimensions(int s, const int *d, int n) { (void)s; (void)d; (void)n; abort(); }
static int predicate(int s, int i, union vm_value *cb) { (void)s; (void)i; (void)cb; abort(); }
static bool binary_predicate2(int s, int i, int t, int j, union vm_value *cb) {
    (void)s; (void)i; (void)t; (void)j; (void)cb; abort();
}
static bool binary_predicate(int s, int i, int j, union vm_value *cb) {
    (void)s; (void)i; (void)j; (void)cb; abort();
}
static int compare_element(int s, int i, union vm_value *v) { (void)s; (void)i; (void)v; abort(); }
static bool equal_element(int s, int i, union vm_value *v) { (void)s; (void)i; (void)v; abort(); }
static void insert_element(int s, int i, union vm_value *v) { (void)s; (void)i; (void)v; abort(); }
static void erase_elements_range(int s, int i, int n) { (void)s; (void)i; (void)n; abort(); }
static void erase_element(int s, int i) { (void)s; (void)i; abort(); }
static void sort_array(int s, union vm_value *cb, bool d) { (void)s; (void)cb; (void)d; abort(); }
struct shuffle_random { int unused; };
static void shuffle_random_init(struct shuffle_random *r, unsigned int s) { (void)r; (void)s; abort(); }
static unsigned int shuffle_random_next(struct shuffle_random *r) { (void)r; abort(); }
'''

TEST = r'''
static void widths(void) {
    const int params[] = {1, 2, 3, 0x10001, 0x10002, 0x10003,
                         0x20001, 0x20002, 0x20003};
    const int expected[] = {1, 1, 2, 2, 1, 2, 2, 2, 3};
    for (unsigned int i = 0; i < sizeof(params) / sizeof(*params); i++)
        assert(array_hll_param_width(params[i]) == expected[i]);
    program.version = 12;
    assert(array_hll_param_width(AIN_INT) == 1);
    assert(array_hll_param_width(AIN_STRUCT) == 1);
    assert(array_hll_param_width(AIN_IFACE) == 2);
    program.version = 14;
}

int main(void) {
    widths();
    struct ain_type ref = {.data = AIN_REF_INT};
    struct ain_hll_argument args[] = {
        {.type = {.data = AIN_REF_ARRAY}}, {.type = {.data = AIN_HLL_PARAM}}
    };
    struct ain_hll_function push = {.name = "PushBack", .nr_arguments = 2, .arguments = args};
    struct ain_hll_function numof = {.name = "Numof", .nr_arguments = 1, .arguments = args};
    heap[1] = (struct vm_pointer){.ref = 1, .page = alloc_page(STRUCT_PAGE, 0, 40)};
    heap[2] = (struct vm_pointer){.ref = 1, .page = v12_array_new(&ref, 0, false)};

    stack_push(123); stack_push(456);
    stack_push(2);
    assert(array_call(&numof, 0x10001));
    assert(stack_pop().i == 0 && stack_ptr == 2);

    // Same three-word calls as the status-recovery lists in Evenicle 2.
    for (int i = 0; i < 10; i++) {
        stack_push(2); stack_push(1); stack_push(20 + 2 * i);
        assert(array_call(&push, 0x10001));
        assert(stack_ptr == 2 && warnings == 0);
        assert(v12_array_length(heap[2].page) == i + 1);
        assert(heap[2].page->values[2 * i].i == 1);
        assert(heap[2].page->values[2 * i + 1].i == 20 + 2 * i);
        assert(heap[1].ref == i + 2);
    }
    // An invalid receiver still consumes the complete borrowed reference.
    stack_push(-1); stack_push(1); stack_push(20);
    assert(array_call(&push, 0x10001));
    assert(stack_ptr == 2 && heap[1].ref == 11);
    stack_push(1); stack_push(1); stack_push(20);
    assert(array_call(&push, 0x10001));
    assert(stack_ptr == 2 && warnings == 1 && heap[1].ref == 11);

    resize_array(2, 0);
    assert(heap[1].ref == 1 && v12_array_length(heap[2].page) == 0);
    assert(stack_pop().i == 456 && stack_pop().i == 123 && stack_ptr == 0);
    free_page(heap[2].page); free_page(heap[1].page);
    puts("Array HLL: scalar-reference pairs, stack balance, empty/invalid arrays and ownership: OK");
    return 0;
}
'''


class ArrayHllRegressionTests(unittest.TestCase):
    def test_scalar_reference_arguments(self):
        source = FIXTURE
        for path, names in [
            ("src/page.c", ["ain_type_slot_width", "elem_value_copy", "elem_value_fini", "array_type"]),
            ("src/vm_v12.c", ["type_stride", "element_type", "v12_array_length",
                              "v12_array_new", "resize_array_mode", "resize_array",
                              "set_element", "elem_ref_is_two_slot", "return_element",
                              "array_hll_param_width", "array_hll_ref_ret_is_two_slot",
                              "array_arg_is_owned_value_temp", "type_may_run_destructor",
                              "can_move_array_value_temp", "move_insert_element",
                              "cleanup_array_value_args", "array_call_invalid_slot",
                              "is_hll_func_type", "array_call"]),
        ]:
            source += "\n".join(extract_function(path, name) for name in names)
        source += TEST
        with tempfile.TemporaryDirectory() as tmp:
            src, exe = Path(tmp) / "test.c", Path(tmp) / "test"
            src.write_text(source)
            subprocess.run([
                "cc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2",
                f"-I{ROOT / 'include'}", f"-I{ROOT / 'subprojects/libsys4/include'}",
                str(src), "-o", str(exe),
            ], check=True)
            subprocess.run([str(exe)], check=True)

    def test_delegate_reverse_index_pruning_and_array_temp_move(self):
        harness = r"""
#define _POSIX_C_SOURCE 200809L
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "system4.h"
#include "system4/ain.h"
#include "system4/string.h"
#include "vm.h"
#include "vm/heap.h"
#include "vm/page.h"

struct ain *ain = NULL;
size_t instr_ptr = 0;

static union vm_value test_stack[256];
union vm_value *stack = test_stack;
int32_t stack_ptr = 0;
union vm_value stack_pop(void) { return stack[--stack_ptr]; }

static int vm_copy_page_calls = 0;
static int env_dtor_calls = 0;

int vm_copy_page(int source) {
    vm_copy_page_calls++;
    struct page *page = heap_get_page(source);
    int slot = heap_alloc_slot(VM_PAGE);
    heap_set_page(slot, copy_page(page));
    heap_copy_delegate_targets(source, slot);
    return slot;
}

union vm_value vm_copy(union vm_value v, enum ain_data_type type) {
    switch (type) {
    case AIN_STRUCT:
    case AIN_DELEGATE:
    case AIN_ARRAY:
    case AIN_ARRAY_TYPE:
        return (union vm_value){ .i = vm_copy_page(v.i) };
    case AIN_STRING:
        heap_ref(v.i);
        return v;
    case AIN_REF_TYPE:
    case AIN_IFACE:
    case AIN_IFACE_WRAP:
    case AIN_REF_OPTION:
        heap_ref(v.i);
        return v;
    default:
        return v;
    }
}

int vm_capture_environment(int fno) { (void)fno; return -1; }
void vm_call(int fno, int struct_page) {
    if (fno == 1) env_dtor_calls++;
    (void)struct_page;
}
const char *display_sjis0(const char *s) { return s ? s : ""; }
#include <stdarg.h>
void _vm_error(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    fprintf(stderr, "\n");
    va_end(ap);
    abort();
}
void _sys_warning(const char *fmt, ...) { (void)fmt; }
bool vm_string_equal_gbk(const struct string *a, const struct string *b) {
    return strcmp(a->text, b->text) == 0;
}
void vm_v12_init(void) {}
void vm_v12_fini(void) {}
struct page *v12_array_new(const struct ain_type *elem_type, int count, bool prealloc) {
    (void)elem_type; (void)count; (void)prealloc;
    return alloc_page(ARRAY_PAGE, AIN_ARRAY, 0);
}

#include "heap.c"
#include "page.c"

// Include Array.Add / Array.Insert move path helpers directly from vm_v12.c
static struct ain_type element_type(struct page *p) { return p->array.element_type; }
static int type_stride(const struct ain_type *t) { return ain_type_slot_width(t); }
int v12_array_length(struct page *p) { return p->nr_vars / type_stride(&p->array.element_type); }
static void resize_array_mode(int slot, int count, bool prealloc) {
    struct page *p = heap_get_page(slot);
    struct ain_type t = element_type(p);
    int w = type_stride(&t), old = v12_array_length(p);
    struct page *np = alloc_page(ARRAY_PAGE, AIN_ARRAY, count * w);
    np->array = p->array;
    int keep = old < count ? old : count;
    if (keep > 0)
        memcpy(np->values, p->values, (size_t)(keep * w) * sizeof(*p->values));
    for (int i = keep; i < count; i++)
        variable_init_typed(&np->values[i * w], &t, prealloc, false);
    free_page(p);
    heap_set_page(slot, np);
}
""" + "\n".join(
            extract_function("src/vm_v12.c", name)
            for name in (
                "array_arg_is_owned_value_temp",
                "type_may_run_destructor",
                "can_move_array_value_temp",
                "move_insert_element",
                "cleanup_array_value_args",
            )
        ) + r"""
static int make_frame_with_self_delegate(int env_slot) {
    int st = heap_alloc_slot(VM_PAGE);
    struct page *sp = alloc_page(STRUCT_PAGE, 0, 1);
    heap_set_page(st, sp);

    int dg = heap_alloc_slot(VM_PAGE);
    struct page *dp = alloc_page(DELEGATE_PAGE, 0, 0);
    dp = delegate_append_captured(dp, st, 1, env_slot);
    dp = delegate_append_captured(dp, st, 2, env_slot);
    heap_set_page(dg, dp);
    sp->values[0].i = dg;
    return st;
}

int main(void) {
    ain = ain_new(14, 1);
    // Struct 0: { delegate m_dg } (no destructor)
    ain->nr_structures = 3;
    ain->structures = xcalloc(3, sizeof(struct ain_struct));
    ain->structures[0].name = strdup("FrameInfo");
    ain->structures[0].nr_members = 1;
    ain->structures[0].members = xcalloc(1, sizeof(struct ain_variable));
    ain->structures[0].members[0].name = strdup("m_dg");
    ain->structures[0].members[0].type = (struct ain_type){ .data = AIN_DELEGATE, .struc = -1 };
    // Struct 1: env marker struct with destructor (fn index 1)
    ain->structures[1].name = strdup("EnvOwner");
    ain->structures[1].nr_members = 0;
    ain->structures[1].constructor = -1;
    ain->structures[1].destructor = 1;
    // Struct 2: wrapper struct with no own destructor, but owns value EnvOwner member
    ain->structures[2].name = strdup("WrapperWithDtorMember");
    ain->structures[2].nr_members = 1;
    ain->structures[2].members = xcalloc(1, sizeof(struct ain_variable));
    ain->structures[2].members[0].name = strdup("m_owned");
    ain->structures[2].members[0].type = (struct ain_type){ .data = AIN_STRUCT, .struc = 1 };
    ain->structures[2].constructor = -1;
    ain->structures[2].destructor = -1;
    ain->structures[0].constructor = -1;
    ain->structures[0].destructor = -1;
    ain->nr_functions = 2;
    ain->functions = xcalloc(2, sizeof(struct ain_function));
    ain->functions[0].name = strdup("NULL");
    ain->functions[1].name = strdup("EnvOwner@1");

    heap_init();
    heap_set_page(0, alloc_page(GLOBAL_PAGE, 0, 0));

    // 1. Seed 40,000 empty DELEGATE_PAGEs (simulating mid-dungeon Dohna Dohna heap)
    //    and verify none bloat reverse indexes.
    for (int i = 0; i < 40000; i++) {
        int slot = heap_alloc_slot(VM_PAGE);
        heap_set_page(slot, alloc_page(DELEGATE_PAGE, 0, 0));
    }

    // 2. Create a cached FrameInfo (like FrameInfoCache's idle motion) with a captured env.
    int env = heap_alloc_slot(VM_PAGE);
    heap_set_page(env, alloc_page(STRUCT_PAGE, 1, 0));
    int cached = make_frame_with_self_delegate(env);
    int cached_dg = heap[cached].page->values[0].i;
    assert(heap[cached].dg_refs && heap[cached].dg_refs->nr == 1);

    // 3. Repeatedly deep-copy and free cached FrameInfo 200 times (simulating CopySubFrames across turns).
    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    for (int iter = 0; iter < 200; iter++) {
        int c1 = vm_copy_page(cached);
        int c2 = vm_copy_page(c1);
        int c2_dg = heap[c2].page->values[0].i;
        // Verify value-copy delegate inheritance: c2's delegate contains live entries for c2.
        assert(delegate_contains(heap[c2_dg].page, c2, 1));
        assert(delegate_contains(heap[c2_dg].page, c2, 2));
        heap_unref(c1);
        heap_unref(c2);
    }
    clock_gettime(CLOCK_MONOTONIC, &t1);
    double elapsed_ms = (t1.tv_sec - t0.tv_sec) * 1000.0 + (t1.tv_nsec - t0.tv_nsec) / 1e6;
    assert(elapsed_ms < 500.0); // O(deg) takes ~1ms; old O(40k) scan would take hundreds of ms.

    // 4. Dead entries in cached_dg and stale dg_refs on cached MUST be bounded (no unbounded leak).
    int final_copy = vm_copy_page(cached);
    int live_in_cached = heap[cached_dg].page->nr_vars / delegate_stride();
    // Only cached (2) + final_copy (2) = 4 entries; dead entries from the 200 freed iterations were pruned!
    assert(live_in_cached == 4);
    assert(heap[cached].dg_refs && heap[cached].dg_refs->nr <= 2);
    heap_unref(final_copy);

    // 5. Copy-page dead-entry pruning: copying a delegate page with dead entries strips them cleanly.
    int dg_copy = vm_copy_page(cached_dg);
    assert(heap[dg_copy].page->nr_vars / delegate_stride() == 2); // only cached's 2 live entries
    heap_unref(dg_copy);

    // 6. Recycled delegate slot safety: erase target, free delegate, recycle slot as VM_STRING,
    //    then copy target -> must NOT crash or treat recycled string slot as DELEGATE_PAGE!
    int extra_dg = heap_alloc_slot(VM_PAGE);
    struct page *edp = delegate_append_captured(alloc_page(DELEGATE_PAGE, 0, 0), cached, 3, -1);
    heap_set_page(extra_dg, edp);
    delegate_erase(heap[extra_dg].page, cached, 3);
    heap_unref(extra_dg);
    int recycled = heap_alloc_string(make_string("recycled", 8));
    assert(recycled == extra_dg);
    int after_recycle = vm_copy_page(cached);
    assert(after_recycle >= 0);
    heap_unref(after_recycle);
    heap_unref(recycled);

    // 7. DG_CALL cursor/end adjustment when prune_dead_delegate_entries removes an entry before cursor.
    int victim = heap_alloc_slot(VM_PAGE);
    struct page *vp = alloc_page(STRUCT_PAGE, 0, 1);
    vp->values[0].i = -1;
    heap_set_page(victim, vp);
    int active_dg = heap_alloc_slot(VM_PAGE);
    struct page *adp = alloc_page(DELEGATE_PAGE, 0, 0);
    adp = delegate_append_captured(adp, victim, 10, -1); // index 0 (will die)
    adp = delegate_append_captured(adp, cached, 11, -1); // index 1 (stays live)
    adp->delegate.cursor = 1;
    adp->delegate.end = 2;
    heap_set_page(active_dg, adp);
    heap_unref(victim);
    int after_cursor = vm_copy_page(cached);
    assert(heap[active_dg].page->delegate.cursor == 0);
    assert(heap[active_dg].page->delegate.end == 1);
    heap_unref(after_cursor);
    heap_unref(active_dg);

    // 8. heap_refresh_delegate_targets rebuilds reverse indexes cleanly (used after save load).
    heap_clear_delegate_refs(cached);
    assert(heap[cached].dg_refs == NULL);
    heap_refresh_delegate_targets();
    assert(heap[cached].dg_refs && heap[cached].dg_refs->nr >= 1);
    int after_refresh = vm_copy_page(cached);
    assert(delegate_contains(heap[cached_dg].page, after_refresh, 1));
    heap_unref(after_refresh);

    // 9. Deferred env destructor safety: freeing env-owning dead entries runs destructor after loop.
    int temp_env = heap_alloc_slot(VM_PAGE);
    heap_set_page(temp_env, alloc_page(STRUCT_PAGE, 1, 0));
    int temp_owner = heap_alloc_slot(VM_PAGE);
    struct page *top = alloc_page(STRUCT_PAGE, 0, 1);
    top->values[0].i = -1;
    heap_set_page(temp_owner, top);
    struct page *cdp = delegate_append_captured(heap[cached_dg].page, temp_owner, 99, temp_env);
    heap_set_page(cached_dg, cdp);
    heap_unref(temp_env);   // now only cached_dg holds temp_env
    heap_unref(temp_owner); // entry becomes dead
    int before_dtor = env_dtor_calls;
    int trigger = vm_copy_page(cached);
    assert(env_dtor_calls == before_dtor + 1);
    heap_unref(trigger);

    // 10. Array.Add / Array.Insert temporary move optimization (AIN v14 hi=0, lo=2):
    //     ref==1 temporary transfers ownership directly; ref>=2 or borrowed hi=1 or self-add does NOT move.
    struct ain_type st_type = { .data = AIN_STRUCT, .struc = 0 };
    struct page *arr_p = alloc_page(ARRAY_PAGE, AIN_ARRAY, 0);
    arr_p->array.rank = 1;
    arr_p->array.struct_type = 0;
    arr_p->array.element_type = st_type;
    int arr_slot = heap_alloc_page(arr_p);

    struct ain_hll_function add_fn = {0};
    struct ain_hll_argument add_args[2] = {
        { .type = { .data = AIN_REF_ARRAY } },
        { .type = { .data = AIN_HLL_PARAM } },
    };
    add_fn.nr_arguments = 2;
    add_fn.arguments = add_args;

    struct ain_hll_function ins_fn = {0};
    struct ain_hll_argument ins_args[3] = {
        { .type = { .data = AIN_REF_ARRAY } },
        { .type = { .data = AIN_INT } },
        { .type = { .data = AIN_HLL_PARAM } },
    };
    ins_fn.nr_arguments = 3;
    ins_fn.arguments = ins_args;

    int temp_v1 = vm_copy_page(cached); // ref == 1
    assert(heap[temp_v1].ref == 1);
    assert(can_move_array_value_temp(&add_fn, 1, 2, arr_slot, &st_type, temp_v1));
    // Borrowed ref parameter (hi=1, lo=2) must NOT move!
    assert(!can_move_array_value_temp(&add_fn, 1, (1 << 16) | 2, arr_slot, &st_type, temp_v1));
    // Shared refcount (ref >= 2) must NOT move!
    heap_ref(temp_v1);
    assert(!can_move_array_value_temp(&add_fn, 1, 2, arr_slot, &st_type, temp_v1));
    heap_unref(temp_v1);
    // Self-move into same array must NOT move!
    struct ain_type arr_elem_t = { .data = AIN_ARRAY, .struc = -1 };
    assert(!can_move_array_value_temp(&add_fn, 1, 2, arr_slot, &arr_elem_t, arr_slot));
    // Struct with own destructor must NOT move (preserves temp destructor side effects)!
    struct ain_type dtor_st_type = { .data = AIN_STRUCT, .struc = 1 };
    int dtor_temp = heap_alloc_slot(VM_PAGE);
    heap_set_page(dtor_temp, alloc_page(STRUCT_PAGE, 1, 0));
    assert(!can_move_array_value_temp(&add_fn, 1, 2, arr_slot, &dtor_st_type, dtor_temp));
    heap_unref(dtor_temp);
    // Struct transitively owning a destructible value struct member must NOT move!
    struct ain_type wrap_st_type = { .data = AIN_STRUCT, .struc = 2 };
    int wrap_temp = heap_alloc_slot(VM_PAGE);
    struct page *wp = alloc_page(STRUCT_PAGE, 2, 1);
    wp->values[0].i = -1;
    heap_set_page(wrap_temp, wp);
    assert(!can_move_array_value_temp(&add_fn, 1, 2, arr_slot, &wrap_st_type, wrap_temp));
    heap_unref(wrap_temp);
    // Array whose element type is destructible must NOT move, while array of non-destructible structs CAN move!
    struct page *dtor_arr_p = alloc_page(ARRAY_PAGE, AIN_ARRAY, 0);
    dtor_arr_p->array.rank = 1;
    dtor_arr_p->array.struct_type = 1;
    dtor_arr_p->array.element_type = dtor_st_type;
    int dtor_arr_slot = heap_alloc_page(dtor_arr_p);
    assert(!can_move_array_value_temp(&add_fn, 1, 2, arr_slot, &arr_elem_t, dtor_arr_slot));
    heap_unref(dtor_arr_slot);
    struct page *plain_arr_p = alloc_page(ARRAY_PAGE, AIN_ARRAY, 0);
    plain_arr_p->array.rank = 1;
    plain_arr_p->array.struct_type = 0;
    plain_arr_p->array.element_type = st_type;
    int plain_arr_slot = heap_alloc_page(plain_arr_p);
    assert(can_move_array_value_temp(&add_fn, 1, 2, arr_slot, &arr_elem_t, plain_arr_slot));
    heap_unref(plain_arr_slot);

    // Move temp_v1 into index 0 via move_insert_element -> zero extra vm_copy_page calls!
    int copies_before = vm_copy_page_calls;
    move_insert_element(arr_slot, 0, temp_v1);
    assert(vm_copy_page_calls == copies_before);
    assert(v12_array_length(heap[arr_slot].page) == 1);
    assert(heap[arr_slot].page->values[0].i == temp_v1);
    assert(heap[temp_v1].ref == 1);

    // Ownership transfer with cleanup_array_value_args: args[2][0].i = -1 prevents double-unref.
    union vm_value call_args[8][3] = {0};
    call_args[2][0].i = -1;
    cleanup_array_value_args(&ins_fn, 2, call_args);
    assert(page_index_valid(temp_v1) && heap[temp_v1].ref == 1);

    heap_unref(arr_slot);
    assert(!page_index_valid(temp_v1));
    heap_unref(cached);
    heap_unref(env);

    puts("OK");
    return 0;
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            src, exe = Path(tmp) / "test_dg.c", Path(tmp) / "test_dg"
            src.write_text(harness)
            subprocess.run([
                "cc", "-std=gnu11", "-Wall", "-Wextra", "-O2",
                f"-I{ROOT / 'include'}", f"-I{ROOT / 'src'}",
                f"-I{ROOT / 'subprojects/libsys4/include'}",
                str(src),
                str(ROOT / "subprojects/libsys4/src/ain.c"),
                str(ROOT / "subprojects/libsys4/src/buffer.c"),
                str(ROOT / "subprojects/libsys4/src/file.c"),
                str(ROOT / "subprojects/libsys4/src/hashtable.c"),
                str(ROOT / "subprojects/libsys4/src/instructions.c"),
                str(ROOT / "subprojects/libsys4/src/mt19937int.c"),
                str(ROOT / "subprojects/libsys4/src/string.c"),
                str(ROOT / "subprojects/libsys4/src/system.c"),
                str(ROOT / "subprojects/libsys4/src/utfsjis.c"),
                str(ROOT / "subprojects/libsys4/src/zlib.c"),
                "-lz", "-lm", "-o", str(exe),
            ], check=True)
            out = subprocess.run([str(exe)], check=True, capture_output=True, text=True).stdout
            self.assertEqual(out.strip(), "OK")


if __name__ == "__main__":
    unittest.main()
