#!/usr/bin/env python3
"""Host C regression tests for virtual game speed clocks in xsystem4 and xsystem35.

Run: python3 xsystem4/test/native/test_game_speed_regressions.py
"""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

REPO_ROOT = Path(__file__).resolve().parents[3]
SYS4_VM = REPO_ROOT / "xsystem4/src/vm.c"
SYS4_MOD = REPO_ROOT / "xsystem4/src/modifier.c"
SYS35_SYS = REPO_ROOT / "xsystem35/src/system.c"
SYS35_BRIDGE = REPO_ROOT / "xsystem35/src/alicehub_bridge.c"
SYS35_PCM = REPO_ROOT / "xsystem35/src/pcm.sdlmixer.c"


def extract_function(path: Path, name: str) -> str:
    source = path.read_text(encoding="utf-8")
    pattern = re.compile(
        rf"^(?:static\s+)?(?:inline\s+)?[A-Za-z_][A-Za-z0-9_\s\*]*\b{re.escape(name)}\([^;{{}}]*\)\s*\r?\n?\{{",
        re.MULTILINE,
    )
    match = pattern.search(source)
    if not match:
        raise ValueError(f"Function definition not found: {name} in {path}")
    brace = source.index("{", match.start())
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[match.start():end]


SYS4_HARNESS = r"""
#define _POSIX_C_SOURCE 200809L
#include <assert.h>
#include <limits.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

typedef uint32_t Uint32;
#define VM_GAME_SPEED_MIN_PCT     50
#define VM_GAME_SPEED_MAX_PCT     500
#define VM_GAME_SPEED_DEFAULT_PCT 100

static uint64_t g_fake_ticks = 0;
static Uint32 g_last_delay = 0;
static int g_delay_calls = 0;

static uint64_t SDL_GetTicks64(void) { return g_fake_ticks; }
static void gfx_android_delay(Uint32 ms) { g_last_delay = ms; g_delay_calls++; }

static pthread_mutex_t vm_clock_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool vm_clock_initialized = false;
static uint64_t vm_clock_last_real_ms = 0;
static uint64_t vm_clock_virtual_100 = 0;
static int vm_game_speed_pct = VM_GAME_SPEED_DEFAULT_PCT;

__FUNCTIONS__

int main(void)
{
	/* 1. Default 100% tracks real ticks 1:1 */
	g_fake_ticks = 1000;
	assert(vm_get_game_speed_percent() == 100);
	assert(vm_time64() == 1000);
	assert(vm_time() == 1000);
	g_fake_ticks = 1250;
	assert(vm_time64() == 1250);

	/* 2. Switching to 200% mid-run has ZERO discontinuity */
	assert(vm_set_game_speed_percent(200) == 200);
	assert(vm_time64() == 1250);
	g_fake_ticks = 1350; /* +100ms real -> +200ms virtual */
	assert(vm_time64() == 1450);

	/* 3. Fractional accumulation at 150% across 1ms steps loses no precision */
	assert(vm_set_game_speed_percent(150) == 150);
	assert(vm_time64() == 1450);
	for (int i = 0; i < 10; i++) {
		g_fake_ticks += 1;
		(void)vm_time64();
	}
	assert(vm_time64() == 1465); /* +10ms real * 1.5 = +15ms virtual */

	/* 4. Clamping bounds [50, 500] and slow-motion 50% */
	assert(vm_set_game_speed_percent(10) == 50);
	assert(vm_get_game_speed_percent() == 50);
	g_fake_ticks += 200; /* +200ms real -> +100ms virtual */
	assert(vm_time64() == 1565);

	assert(vm_set_game_speed_percent(999) == 500);
	assert(vm_get_game_speed_percent() == 500);
	g_fake_ticks += 100; /* +100ms real -> +500ms virtual */
	assert(vm_time64() == 2065);

	/* 5. Sleep scaling & non-zero floor (prevents 100% CPU spin on Sleep(1)) */
	assert(vm_scale_sleep_ms(0) == 0);
	assert(vm_scale_sleep_ms(-5) == 0);
	assert(vm_scale_sleep_ms(1000) == 200);
	assert(vm_scale_sleep_ms(16) == 3);
	assert(vm_scale_sleep_ms(1) == 1);

	vm_sleep(0);
	assert(g_delay_calls == 0);
	vm_sleep(1000);
	assert(g_delay_calls == 1 && g_last_delay == 200);

	assert(vm_set_game_speed_percent(50) == 50);
	assert(vm_scale_sleep_ms(16) == 32);

	assert(vm_set_game_speed_percent(100) == 100);
	assert(vm_scale_sleep_ms(16) == 16);
	assert(vm_time64() == 2065);
	g_fake_ticks += 40;
	assert(vm_time64() == 2105);

	puts("xsystem4 virtual clock tests PASSED");
	return 0;
}
"""

SYS35_HARNESS = r"""
#define _POSIX_C_SOURCE 200809L
#include <assert.h>
#include <limits.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

#define SYS_GAME_SPEED_MIN_PCT     50
#define SYS_GAME_SPEED_MAX_PCT     500
#define SYS_GAME_SPEED_DEFAULT_PCT 100

static uint32_t g_fake_ticks32 = 0;
static uint32_t SDL_GetTicks(void) { return g_fake_ticks32; }

static pthread_mutex_t sys_clock_mutex = PTHREAD_MUTEX_INITIALIZER;
#define SYS_CLOCK_LOCK() pthread_mutex_lock(&sys_clock_mutex)
#define SYS_CLOCK_UNLOCK() pthread_mutex_unlock(&sys_clock_mutex)

static bool sys_clock_initialized = false;
static uint32_t sys_clock_last_real_ms = 0;
static uint64_t sys_clock_virtual_100 = 0;
static int sys_game_speed_pct = SYS_GAME_SPEED_DEFAULT_PCT;

__FUNCTIONS__

int main(void)
{
	g_fake_ticks32 = 500;
	assert(sys_get_game_speed_percent() == 100);
	assert(sys_get_ticks() == 500);

	assert(sys_set_game_speed_percent(300) == 300);
	assert(sys_get_ticks() == 500);
	g_fake_ticks32 = 600; /* +100ms real -> +300ms virtual */
	assert(sys_get_ticks() == 800);

	/* Test 32-bit wraparound handling */
	assert(sys_set_game_speed_percent(100) == 100);
	g_fake_ticks32 = 0xFFFFFFF0u;
	uint32_t before_wrap = sys_get_ticks();
	assert(sys_set_game_speed_percent(200) == 200);
	g_fake_ticks32 = 0x00000010u; /* +32ms real across 32-bit wrap -> +64ms virtual */
	uint32_t after_wrap = sys_get_ticks();
	assert((uint32_t)(after_wrap - before_wrap) == 64u);

	/* Sleep scaling */
	assert(sys_scale_sleep_ms(100) == 50);
	assert(sys_scale_sleep_ms(1) == 1);
	assert(sys_scale_sleep_ms(0) == 0);
	assert(sys_set_game_speed_percent(0) == 50);
	assert(sys_scale_sleep_ms(10) == 20);
	assert(sys_set_game_speed_percent(900) == 500);
	assert(sys_scale_sleep_ms(100) == 20);

	puts("xsystem35 virtual clock tests PASSED");
	return 0;
}
"""


class GameSpeedRegressionsTest(unittest.TestCase):
    def _compile_and_run(self, c_source: str, label: str) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / f"{label}.c"
            exe = Path(tmp) / label
            src.write_text(c_source, encoding="utf-8")
            subprocess.run(
                ["gcc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-pthread", str(src), "-o", str(exe)],
                check=True,
            )
            subprocess.run([str(exe)], check=True)

    def test_xsystem4_virtual_clock_monotonic_and_scaled(self):
        funcs = "\n\n".join(
            extract_function(SYS4_VM, name)
            for name in (
                "clamp_game_speed_pct",
                "vm_clock_advance_locked",
                "vm_set_game_speed_percent",
                "vm_get_game_speed_percent",
                "vm_time64",
                "vm_time",
                "vm_scale_sleep_ms",
                "vm_sleep",
            )
        )
        self._compile_and_run(SYS4_HARNESS.replace("__FUNCTIONS__", funcs), "test_sys4_speed")

    def test_xsystem35_virtual_clock_monotonic_and_wrap_safe(self):
        funcs = "\n\n".join(
            extract_function(SYS35_SYS, name)
            for name in (
                "clamp_sys_game_speed_pct",
                "sys_clock_advance_locked",
                "sys_set_game_speed_percent",
                "sys_get_game_speed_percent",
                "sys_scale_sleep_ms",
                "sys_get_ticks",
            )
        )
        self._compile_and_run(SYS35_HARNESS.replace("__FUNCTIONS__", funcs), "test_sys35_speed")

    def test_audio_and_input_clocks_stay_unscaled_and_jni_wired(self):
        pcm_src = SYS35_PCM.read_text(encoding="utf-8")
        self.assertNotIn("sys_get_ticks()", pcm_src)
        self.assertIn("SDL_GetTicks()", pcm_src)

        sys4_mod = SYS4_MOD.read_text(encoding="utf-8")
        self.assertIn("modifier_api_set_game_speed", sys4_mod)
        self.assertIn("modifier_api_get_game_speed", sys4_mod)
        self.assertIn("GameModifierBridge_nativeSetGameSpeed", sys4_mod)
        self.assertIn("GameModifierBridge_nativeGetGameSpeed", sys4_mod)

        sys35_bridge = SYS35_BRIDGE.read_text(encoding="utf-8")
        self.assertIn("GameModifierBridge_nativeSetGameSpeed", sys35_bridge)
        self.assertIn("GameModifierBridge_nativeGetGameSpeed", sys35_bridge)

        # Verify touch/audio/movie subsystems in xsystem4 stay on real SDL_GetTicks
        for rel in ("xsystem4/src/input.c", "xsystem4/src/audio_mixer.c", "xsystem4/src/movie_plmpeg.c"):
            txt = (REPO_ROOT / rel).read_text(encoding="utf-8")
            self.assertNotIn("vm_time(", txt, f"{rel} should keep real wall time")
            self.assertNotIn("vm_time64(", txt, f"{rel} should keep real wall time")


if __name__ == "__main__":
    unittest.main()
