#!/usr/bin/env python3
"""Native C regression test for loading resume saves with VM stack sizes > 4096 (e.g. Evenicle 4102)."""
import unittest

from test_evenicle2_regressions import LIBSYS4_CORE, ROOT, compile_and_run
from test_vm_reign_regressions import extract_function


class ResumeVmStackRegressionTest(unittest.TestCase):
    def test_load_rsave_and_json_stack_dynamic_growth_and_bounds(self):
        fn_ensure = extract_function("src/vm.c", "void vm_stack_ensure")
        fn_load_json = extract_function("src/resume.c", "static void load_json_stack")
        fn_load_rsave = extract_function("src/resume.c", "static void load_rsave_stack")

        harness = f"""
#include <assert.h>
#include <setjmp.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "system4.h"
#include "system4/savefile.h"
#include "vm.h"

#define INITIAL_STACK_SIZE 65536
#define MAX_RESUME_VM_STACK_SIZE (1024 * 1024)

static jmp_buf err_jmp;
static int err_count = 0;
#undef VM_ERROR
#define VM_ERROR(...) do {{ err_count++; longjmp(err_jmp, 1); }} while (0)
#define invalid_save_data(...) do {{ err_count++; longjmp(err_jmp, 1); }} while (0)

static cJSON *type_check(int type, cJSON *item) {{
    if (!item || (item->type & 0xff) != type)
        invalid_save_data("bad json type");
    return item;
}}

union vm_value *stack = NULL;
int32_t stack_ptr = 0;
static size_t stack_size = 0;

{fn_ensure}
{fn_load_json}
{fn_load_rsave}

int main(void) {{
    // 1. Evenicle crash reproduction: rsave with stack_size = 4102 (> legacy 4096 cap).
    int32_t *buf1 = xcalloc(4102, sizeof(int32_t));
    for (int i = 0; i < 4102; i++) buf1[i] = i * 3 - 7;
    struct rsave rs = {{ .stack_size = 4102, .stack = buf1 }};
    load_rsave_stack(&rs);
    assert(stack_ptr == 4102);
    assert(stack_size == INITIAL_STACK_SIZE);
    assert(stack[0].i == -7 && stack[4095].i == 4095 * 3 - 7 && stack[4101].i == 4101 * 3 - 7);
    free(buf1);

    // 2. Dynamic growth beyond INITIAL_STACK_SIZE (65536 -> 131072).
    int32_t *buf2 = xcalloc(70000, sizeof(int32_t));
    for (int i = 0; i < 70000; i++) buf2[i] = i ^ 0x5a5a;
    rs.stack_size = 70000;
    rs.stack = buf2;
    load_rsave_stack(&rs);
    assert(stack_ptr == 70000);
    assert(stack_size >= 131072);
    assert(stack[0].i == 0x5a5a && stack[69999].i == (69999 ^ 0x5a5a));
    free(buf2);

    // 3. JSON resume stack with 4102 items also loads cleanly.
    cJSON *arr = cJSON_CreateArray();
    for (int i = 0; i < 4102; i++)
        cJSON_AddItemToArray(arr, cJSON_CreateNumber(i + 100));
    load_json_stack(arr);
    assert(stack_ptr == 4102);
    assert(stack[0].i == 100 && stack[4101].i == 4201);
    cJSON_Delete(arr);

    // 4. Reject negative, missing-buffer, and oversized stack_size cleanly.
    int before = err_count;
    rs.stack_size = -1; rs.stack = NULL;
    if (setjmp(err_jmp) == 0) {{ load_rsave_stack(&rs); assert(0); }}
    assert(err_count == before + 1);

    rs.stack_size = 10; rs.stack = NULL;
    if (setjmp(err_jmp) == 0) {{ load_rsave_stack(&rs); assert(0); }}
    assert(err_count == before + 2);

    rs.stack_size = MAX_RESUME_VM_STACK_SIZE + 1; rs.stack = NULL;
    if (setjmp(err_jmp) == 0) {{ load_rsave_stack(&rs); assert(0); }}
    assert(err_count == before + 3);

    free(stack);
    puts("OK");
    return 0;
}}
"""
        out = compile_and_run(
            harness,
            [*LIBSYS4_CORE, ROOT / "src/cJSON.c"],
            ["-lz", "-lm"],
        )
        self.assertEqual(out.strip(), "OK")


if __name__ == "__main__":
    unittest.main()
