#!/usr/bin/env python3
"""Native C regression tests for Wave 1 CN/spin-off adaptations:
1. Haharanman (母烂漫): activity_name_eq GBK full-width digit folding (CPetalList.jaf:24).
2. Evenicle Rance (夏娃年代记 兰斯版): GBK heuristic detection + 1280x720 default viewport.
3. Tsumamigui 3 Spin-offs (妻中蜜3 番外 纯爱篇 / 寝取篇):
   - AIN v8 structural & filename detection + 1024x768 default viewport
   - SystemService.GetGameVersionByText() tokens
   - Parts message_type_v8() early AIN v8 (no DoubleClick slot at 5)
   - Audio mixer exclusion from Voice parent capture
   - FNL fallback substitute scales (both spin-offs and Haharanman)
   - vm.c A_SORT/A_FIND early AIN v8 1-slot comparator guard
"""
import unittest

from test_evenicle2_regressions import (
    LIBSYS4_CORE,
    ROOT,
    compile_and_run,
)
from test_font_and_draw_scissor_regressions import extract_function


class Wave1CnNativeTests(unittest.TestCase):
    def test_wave1_hacks_tokens_and_fnl_fallback(self):
        fn_gvt = extract_function("src/hll/SystemService.c", "game_version_text")
        fn_gvbt = extract_function("src/hll/SystemService.c", "SystemService_GetGameVersionByText")
        fn_set_scales = extract_function("src/text.c", "set_fnl_substitute_scales")
        fn_apply_fallback = extract_function("src/text.c", "apply_fallback_fnl_substitute_scale")

        harness = f"""
#include <assert.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "xsystem4.h"
#include "system4/ain.h"
#include "system4/string.h"
#include "system4/utfsjis.h"
#include "vm.h"

void apply_game_specific_hacks(struct ain *ain);
struct config config;
void message_layout_init_rance03(void) {{}}
_Noreturn void _vm_error(const char *fmt, ...) {{ abort(); }}

#define MAX_FNL_FONTS 32
static float fnl_substitute_scales[MAX_FNL_FONTS];
static unsigned fnl_substitute_count = 0;

{fn_gvt}
{fn_gvbt}
{fn_set_scales}
{fn_apply_fallback}

int main(void) {{
    // 1. Evenicle Rance CN: v6 + CBigMap + "56s5d4" + GBK string heuristic ("兰斯") -> GBK + 1280x720.
    struct ain *er = ain_new(6, 0);
    ain_add_struct(er, "CBigMap");
    ain_add_string(er, "56s5d4");
    ain_add_string(er, "\\xc0\\xbc\\xcb\\xb9"); // GBK "兰斯"
    memset(&config, 0, sizeof(config));
    config.game_name = strdup("Evenicle");
    config.view_width = 800;
    config.view_height = 600;
    sys4_set_text_gbk(false);
    apply_game_specific_hacks(er);
    assert(game_evenicle_rance == true);
    assert(game_evenicle == false);
    assert(sys4_text_is_gbk == true);
    assert(config.view_width == 1280 && config.view_height == 720);
    free(config.game_name);
    ain_free(er);

    // 2. Tsumamigui 3 Spin-off Junai: v8 + C_BOOT_WARNING + "-asat;df,zx" -> 1024x768 + exact token + FNL fallback.
    struct ain *jun = ain_new(8, 0);
    ain_add_struct(jun, "C_BOOT_WARNING");
    ain_add_string(jun, "-asat;df,zx");
    memset(&config, 0, sizeof(config));
    config.view_width = 800;
    config.view_height = 600;
    sys4_set_text_gbk(false);
    apply_game_specific_hacks(jun);
    assert(game_tsumamigui3_jun == true);
    assert(game_tsumamigui3_ntr == false && game_maman == false);
    assert(config.view_width == 1024 && config.view_height == 768);
    struct string *v = SystemService_GetGameVersionByText();
    assert(strcmp(v->text, "2[0-as:;lclat;o34;asdf,zxcv@-123") == 0);
    free_string(v);
    fnl_substitute_count = 0;
    apply_fallback_fnl_substitute_scale();
    assert(fnl_substitute_count == 2);
    assert(fabsf(fnl_substitute_scales[0] - (129.0f / 192.0f)) < 1e-4f);
    assert(fabsf(fnl_substitute_scales[1] - (112.0f / 160.0f)) < 1e-4f);
    ain_free(jun);

    // 3. Tsumamigui 3 Spin-off Netorare: v8 + C_BOOT_WARNING + "asd34k8jklh" -> 1024x768 + exact token.
    struct ain *ntr = ain_new(8, 0);
    ain_add_struct(ntr, "C_BOOT_WARNING");
    ain_add_string(ntr, "asd34k8jklh");
    memset(&config, 0, sizeof(config));
    config.view_width = 800;
    config.view_height = 600;
    apply_game_specific_hacks(ntr);
    assert(game_tsumamigui3_ntr == true);
    assert(game_tsumamigui3_jun == false && game_maman == false);
    assert(config.view_width == 1024 && config.view_height == 768);
    v = SystemService_GetGameVersionByText();
    assert(strcmp(v->text, "kljasd890f734kjhasd98jklhasd86f") == 0);
    free_string(v);
    ain_free(ntr);

    // 4. Filename fallback detects both spin-offs on AIN v8.
    struct ain *jun_fn = ain_new(8, 0);
    memset(&config, 0, sizeof(config));
    config.view_width = 800;
    config.view_height = 600;
    config.ain_filename = strdup("/games/tsuma3jun/Tsuma3SO_Junai.ain");
    apply_game_specific_hacks(jun_fn);
    assert(game_tsumamigui3_jun == true && config.view_width == 1024 && config.view_height == 768);
    free(config.ain_filename);
    ain_free(jun_fn);

    struct ain *ntr_fn = ain_new(8, 0);
    memset(&config, 0, sizeof(config));
    config.view_width = 800;
    config.view_height = 600;
    config.ain_filename = strdup("/games/tsuma3ntr/Tsuma3SO_Netorare.ain");
    apply_game_specific_hacks(ntr_fn);
    assert(game_tsumamigui3_ntr == true && config.view_width == 1024 && config.view_height == 768);
    free(config.ain_filename);
    ain_free(ntr_fn);

    // 5. Haharanman FNL fallback ratios (4 faces).
    struct ain *haha = ain_new(14, 0);
    ain_add_string(haha, "83h2mjr");
    memset(&config, 0, sizeof(config));
    config.view_width = 800;
    config.view_height = 600;
    apply_game_specific_hacks(haha);
    assert(game_haharanman == true && config.view_width == 1280 && config.view_height == 720);
    fnl_substitute_count = 0;
    apply_fallback_fnl_substitute_scale();
    assert(fnl_substitute_count == 4);
    assert(fabsf(fnl_substitute_scales[0] - (123.0f / 160.0f)) < 1e-4f);
    assert(fabsf(fnl_substitute_scales[3] - (104.0f / 160.0f)) < 1e-4f);
    ain_free(haha);

    puts("OK");
    return 0;
}}
"""
        out = compile_and_run(
            harness,
            [ROOT / "src/hacks.c", ROOT / "src/escalayer_reboot_cn_ui.c", *LIBSYS4_CORE],
            ["-lz", "-lm"],
        )
        self.assertEqual(out.strip(), "OK")

    def test_haharanman_activity_name_gbk_zenkaku_digit_folding(self):
        fn_eq = extract_function("src/parts/activity.c", "activity_name_eq")
        harness = f"""
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "system4.h"
#include "system4/utfsjis.h"

{fn_eq}

int main(void) {{
    // CPetalList.jaf:24 queries GBK full-width digits (0xA3 0xB0..0xB9) while PACT stores SJIS full-width digits (0x82 0x4F..0x58).
    const char *pact_sjis = "CG_\\x82\\x4f\\x82\\x50";   // "CG_０１" with SJIS full-width digits
    const char *query_gbk = "CG_\\xa3\\xb0\\xa3\\xb1";   // "CG_０１" with GBK full-width digits
    const char *query_gbk_diff = "CG_\\xa3\\xb0\\xa3\\xb2";

    // In SJIS mode, GBK full-width digits must NOT fold.
    sys4_set_text_gbk(false);
    assert(activity_name_eq(pact_sjis, pact_sjis) == true);
    assert(activity_name_eq(pact_sjis, query_gbk) == false);

    // In GBK mode, GBK full-width digits fold cleanly to SJIS and match in either argument order.
    sys4_set_text_gbk(true);
    assert(activity_name_eq(pact_sjis, query_gbk) == true);
    assert(activity_name_eq(query_gbk, pact_sjis) == true);
    assert(activity_name_eq(pact_sjis, query_gbk_diff) == false);
    assert(activity_name_eq(NULL, NULL) == true);
    assert(activity_name_eq(pact_sjis, NULL) == false);

    puts("OK");
    return 0;
}}
"""
        out = compile_and_run(harness, LIBSYS4_CORE, ["-lz", "-lm"])
        self.assertEqual(out.strip(), "OK")

    def test_tsumamigui3_spinoff_early_v8_message_mapping_and_vm_guard(self):
        vm_src = (ROOT / "src/vm.c").read_text(encoding="utf-8", errors="ignore")
        self.assertEqual(
            vm_src.count(
                "ain->version > 8 || (ain->version == 8 && !game_tsumamigui3_jun && !game_tsumamigui3_ntr)"
            ),
            2,
        )

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
#include "parts_internal.h"
#include "vm.h"

struct ain *ain = NULL;
bool parts_v2 = true;
bool parts_multi_controller = true;
bool game_tsumamigui3_jun = true;
bool game_tsumamigui3_ntr = false;
_Noreturn void _vm_error(const char *fmt, ...) { abort(); }

int main(void) {
    // Early AIN v8 (Tsumamigui 3 Spin-offs): no DoubleClick slot at 5.
    // Internal IDs 1..25 shift down by 1 to 0..24; SwipeBegin..End map to 25..27.
    struct ain *a8 = ain_new(8, 0);
    ain = a8;

    struct parts dummy_part;
    memset(&dummy_part, 0, sizeof(dummy_part));
    dummy_part.no = 17;
    dummy_part.delegate_index = 3;

    PE_ReleaseMessage();
    assert(PE_GetMessageType() == -1);

    parts_msg_push(&dummy_part, PARTS_MSG_MOUSE_ENTER, "");
    assert(PE_GetMessageType() == 0);
    PE_PopMessage();

    parts_msg_push(&dummy_part, PARTS_MSG_MOUSE_CLICK, "ii", 1, 2);
    assert(PE_GetMessageType() == 4);
    PE_PopMessage();

    parts_msg_push(&dummy_part, PARTS_MSG_MOUSE_ON, "");
    assert(PE_GetMessageType() == 5);
    PE_PopMessage();

    parts_msg_push(&dummy_part, PARTS_MSG_SWIPE_BEGIN, "");
    assert(PE_GetMessageType() == 25);
    PE_PopMessage();

    parts_msg_push(&dummy_part, PARTS_MSG_SWIPE_END, "");
    assert(PE_GetMessageType() == 27);
    PE_PopMessage();

    // If a v8 title DOES have CallDelegateMouseDoubleClick, keep Maman's mapping (MouseOn=6, SwipeBegin=26).
    ain_add_function(a8, "parts::detail::CPartsMessageManager@CallDelegateMouseDoubleClick");
    parts_msg_push(&dummy_part, PARTS_MSG_MOUSE_ON, "");
    assert(PE_GetMessageType() == 6);
    PE_PopMessage();

    parts_msg_push(&dummy_part, PARTS_MSG_SWIPE_BEGIN, "");
    assert(PE_GetMessageType() == 26);
    PE_PopMessage();

    ain_free(a8);
    puts("OK");
    return 0;
}
"""
        out = compile_and_run(
            harness,
            [ROOT / "src/parts/message.c", *LIBSYS4_CORE],
            [f"-I{ROOT / 'src/parts'}", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections", "-lz", "-lm"],
        )
        self.assertEqual(out.strip(), "OK")


if __name__ == "__main__":
    unittest.main()
