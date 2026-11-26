#!/usr/bin/env python3
"""Host C and build-contract regression tests for Ogg Opus voice support (Beat Blades Haruka)."""

import shutil
import struct
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def pkg_cflags(*pkgs: str) -> list[str]:
    return subprocess.check_output(["pkg-config", "--cflags", *pkgs], text=True).split()


def compile_and_run(
    c_src: str,
    extra_sources: list[Path],
    extra_flags: list[str] | None = None,
    args: list[str] | None = None,
    pkgs: tuple[str, ...] = ("sdl2",),
) -> str:
    cc = shutil.which("cc") or shutil.which("gcc")
    assert cc, "C compiler required"
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        src_path = tmp / "harness.c"
        bin_path = tmp / "harness"
        src_path.write_text(c_src, encoding="utf-8")
        cmd = [
            cc,
            "-std=c11",
            "-O2",
            "-Wall",
            "-Wextra",
            "-Wno-unused-parameter",
            "-Wno-unused-function",
            "-Wno-sign-compare",
            "-Wno-type-limits",
            "-D_DEFAULT_SOURCE",
            "-DUSE_GLES",
            f"-I{ROOT / 'include'}",
            f"-I{ROOT / 'src'}",
            f"-I{ROOT / 'subprojects' / 'libsys4' / 'include'}",
            *pkg_cflags(*pkgs),
            str(src_path),
            *[str(p) for p in extra_sources],
            *(extra_flags or []),
            "-o",
            str(bin_path),
        ]
        res_c = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if res_c.returncode != 0:
            raise AssertionError(f"Compile failed ({res_c.returncode}):\n{res_c.stderr}")
        res_r = subprocess.run(
            [str(bin_path), *(args or [])], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
        )
        if res_r.returncode != 0:
            raise AssertionError(
                f"Run failed ({res_r.returncode}):\nstdout:\n{res_r.stdout}\nstderr:\n{res_r.stderr}"
            )
        return res_r.stdout


LIBSYS4_AUDIO_META = [
    ROOT / "subprojects/libsys4/src/system.c",
    ROOT / "subprojects/libsys4/src/file.c",
    ROOT / "subprojects/libsys4/src/utfsjis.c",
    ROOT / "subprojects/libsys4/src/string.c",
    ROOT / "src/audio_meta.c",
]


class HarukaOpusAudioRegressionsTest(unittest.TestCase):
    def test_haruka_ogg_opus_voice_decoding_and_mixing(self):
        # MangaGamer's Beat Blades Haruka (and its CN translation) stores 24,047 character voices
        # in harukaWB.ald as 48 kHz mono Ogg Opus while keeping BGM/SFX as 44.1 kHz Ogg Vorbis.
        # Verify xsystem4's real audio_mixer.c + audio.c + audio_meta.c pipeline decodes, resamples
        # (48 kHz -> 44.1 kHz), routes via XI2 v3 WAI, and mixes Ogg Opus voices alongside Vorbis SFX.
        harness = r"""
#include <assert.h>
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sndfile.h>
#include <SDL.h>
#include "xsystem4.h"
#include "asset_manager.h"
#include "audio.h"
#include "id_pool.h"
#include "mixer.h"
#include "system4/archive.h"
#include "system4/file.h"
#include "system4/string.h"
#include "system4/utfsjis.h"

struct config config;
bool game_rance10, game_dohnadohna, game_evenicle2, game_rance03, game_ixseal,
     game_okusama, game_haharanman, game_night_remaster, game_tsumamigui3_jun,
     game_tsumamigui3_ntr, game_escalayer_reboot, game_tsumamigui3;

static uint8_t *g_sfx_buf;
static size_t g_sfx_len;
static uint8_t *g_voice_buf;
static size_t g_voice_len;

SDL_AudioDeviceID SDL_OpenAudioDevice(const char *dev, int cap, const SDL_AudioSpec *want,
                                     SDL_AudioSpec *have, int allowed) {
    *have = *want;
    return 2;
}
void SDL_PauseAudioDevice(SDL_AudioDeviceID dev, int pause) {}
void SDL_CloseAudioDevice(SDL_AudioDeviceID dev) {}
void SDL_LockAudioDevice(SDL_AudioDeviceID dev) {}
void SDL_UnlockAudioDevice(SDL_AudioDeviceID dev) {}
const char *SDL_GetError(void) { return ""; }
const char *display_utf0(const char *s) { return s; }

static void mem_free_data(struct archive_data *d) {
    free(d->data);
    free(d);
}
static struct archive_ops mem_ar_ops = { .free_data = mem_free_data };
static struct archive mem_ar = { .ops = &mem_ar_ops };

bool asset_exists(enum asset_type t, int no) {
    return t == ASSET_SOUND && (no == 1 || no == 10003);
}

struct archive_data *asset_get(enum asset_type t, int no) {
    if (!asset_exists(t, no)) return NULL;
    const uint8_t *src = (no == 1) ? g_sfx_buf : g_voice_buf;
    size_t len = (no == 1) ? g_sfx_len : g_voice_len;
    struct archive_data *d = calloc(1, sizeof(*d));
    d->archive = &mem_ar;
    d->no = no;
    d->size = len;
    d->data = malloc(len);
    memcpy(d->data, src, len);
    return d;
}

#include "audio_mixer.c"
#include "audio.c"

static void write_tone(const char *path, int format, int samplerate, float freq_hz, int frames) {
    SF_INFO info = {
        .samplerate = samplerate,
        .channels = 1,
        .format = format,
    };
    assert(sf_format_check(&info));
    SNDFILE *sf = sf_open(path, SFM_WRITE, &info);
    assert(sf != NULL);
    float *pcm = malloc((size_t)frames * sizeof(float));
    for (int i = 0; i < frames; i++) {
        pcm[i] = 0.5f * sinf(2.0f * 3.14159265f * freq_hz * (float)i / (float)samplerate);
    }
    assert(sf_writef_float(sf, pcm, frames) == frames);
    free(pcm);
    assert(sf_close(sf) == 0);
}

static float mix_chunks(int chunks) {
    float buf[CHUNK_SIZE * 2];
    float peak = 0.0f;
    for (int i = 0; i < chunks; i++) {
        audio_callback(NULL, (Uint8 *)buf, sizeof(buf));
        for (int j = 0; j < CHUNK_SIZE * 2; j++) {
            float v = fabsf(buf[j]);
            if (v > peak) peak = v;
        }
    }
    return peak;
}

int main(int argc, char **argv) {
    assert(argc == 4);
    const char *sfx_path = argv[1];
    const char *voice_path = argv[2];
    const char *wai_path = argv[3];

    write_tone(sfx_path, SF_FORMAT_OGG | SF_FORMAT_VORBIS, 44100, 440.0f, 44100 / 2);
    write_tone(voice_path, SF_FORMAT_OGG | SF_FORMAT_OPUS, 48000, 660.0f, 48000 / 2);

    g_sfx_buf = file_read(sfx_path, &g_sfx_len);
    g_voice_buf = file_read(voice_path, &g_voice_len);
    assert(g_sfx_buf && g_sfx_len > 32 && !memcmp(g_sfx_buf, "OggS", 4));
    assert(g_voice_buf && g_voice_len > 32 && !memcmp(g_voice_buf, "OggS", 4));

    char *ch_names[] = { "Music", "Sound Effects", "Haruka", "Narika", "Subaru", "Others" };
    int ch_vols[] = { 100, 100, 100, 100, 100, 100 };
    config.mixer_nr_channels = 6;
    config.mixer_channels = ch_names;
    config.mixer_volumes = ch_vols;
    config.default_volume = 100;
    config.wai_path = (char *)wai_path;

    audio_init();

    // 1. Ogg Opus voice (ID 10003 -> WAI channel 2 "Haruka") decodes, resamples 48k -> 44.1k, and mixes.
    assert(wav_get_group_num_from_data_num(10003) == 2);
    assert(wav_prepare(0, 10003) == 1);
    assert(wav_get_group_num(0) == 2);
    int voice_ms = wav_get_time_length(0);
    assert(voice_ms >= 450 && voice_ms <= 550);
    assert(wav_play(0) == 1);
    float voice_peak = mix_chunks(6);
    assert(voice_peak > 0.1f);

    // 2. Character volume slider (channel 2) independently silences the Opus voice.
    assert(mixer_set_volume(2, 0) == 1);
    mix_chunks(4);
    assert(mix_chunks(4) == 0.0f);
    assert(mixer_set_volume(2, 100) == 1);
    assert(wav_unprepare(0) == 1);

    // 3. Ogg Vorbis SFX (ID 1 -> WAI channel 1) and Ogg Opus voice (ID 10003 -> channel 2) mix simultaneously.
    assert(wav_prepare(1, 1) == 1);
    assert(wav_prepare(2, 10003) == 1);
    assert(wav_play(1) == 1 && wav_play(2) == 1);
    assert(mix_chunks(6) > 0.1f);
    assert(wav_unprepare(1) == 1 && wav_unprepare(2) == 1);

    free(g_sfx_buf);
    free(g_voice_buf);
    puts("OK");
    return 0;
}
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            tmp = Path(tmpdir)
            wai_path = tmp / "HarukaWA.wai"
            nr_waves = 10005
            wai_buf = bytearray(36 + nr_waves * 12)
            wai_buf[0:4] = b"XI2\x00"
            struct.pack_into("<III", wai_buf, 4, 1, nr_waves + 1, 3)
            struct.pack_into("<III", wai_buf, 36 + 0 * 12, 1, 0, 1)  # ID 1 -> Sound Effects
            struct.pack_into("<III", wai_buf, 36 + 10002 * 12, 10003, 0, 2)  # ID 10003 -> Haruka
            wai_path.write_bytes(bytes(wai_buf))

            sndfile_libs = subprocess.check_output(
                ["pkg-config", "--libs", "sndfile"], text=True
            ).split()
            out = compile_and_run(
                harness,
                [
                    *LIBSYS4_AUDIO_META,
                    ROOT / "src/id_pool.c",
                    ROOT / "subprojects/libsys4/src/archive.c",
                ],
                [*sndfile_libs, "-lm"],
                [str(tmp / "sfx.ogg"), str(tmp / "voice.opus"), str(wai_path)],
                pkgs=("sdl2", "sndfile"),
            )
            self.assertEqual(out.strip(), "OK")

    def test_cross_platform_opus_build_contract(self):
        repo = ROOT.parent
        patch_script = repo / "cmake/patch-libsndfile-opus.cmake"
        self.assertTrue(patch_script.is_file())

        # Verify patch-libsndfile-opus.cmake patches kichikuou/libsndfile idempotently.
        with tempfile.TemporaryDirectory() as tmpdir:
            src = Path(tmpdir)
            (src / "cmake").mkdir()
            (src / "CMakeLists.txt").write_text(
                "if (ENABLE_EXTERNAL_LIBS AND NOT (Ogg_FOUND AND Vorbis_FOUND))\n"
                "\tset (ENABLE_EXTERNAL_LIBS OFF)\n"
                "endif()\n"
                "set (HAVE_EXTERNAL_XIPH_LIBS ${ENABLE_EXTERNAL_LIBS})\n"
                "set (HAVE_FLAC 0)\n"
                "set (HAVE_OPUS 0)\n"
                "set (EXTERNAL_XIPH_REQUIRE \"ogg vorbis vorbisenc\")\n"
                "\t\t$<$<BOOL:${HAVE_EXTERNAL_XIPH_LIBS}>:Ogg::ogg>\n"
                "\t\t$<$<BOOL:${HAVE_EXTERNAL_XIPH_LIBS}>:Vorbis::vorbisenc>\n"
                "\tif (ENABLE_EXTERNAL_LIBS)\n"
                "\t\tset (SndFile_WITH_EXTERNAL_LIBS 1)\n"
                "\t\tlist (APPEND FIND_MODULES_INSTALL_LIST\n"
                "\t\t\t${CMAKE_CURRENT_LIST_DIR}/cmake/FindOgg.cmake\n"
                "\t\t\t${CMAKE_CURRENT_LIST_DIR}/cmake/FindVorbis.cmake)\n"
                "\telse ()\n"
                "\t\tset (SndFile_WITH_EXTERNAL_LIBS 0)\n"
                "\tendif ()\n",
                encoding="utf-8",
            )
            (src / "cmake/SndFileChecks.cmake").write_text(
                "if (CMAKE_FIND_PACKAGE_PREFER_CONFIG)\n"
                "\tfind_package (Ogg 1.3 CONFIG)\n"
                "\tfind_package (Vorbis CONFIG COMPONENTS Enc)\n"
                "\n"
                "\tinclude (FindPackageHandleStandardArgs)\n"
                "\tfind_package_handle_standard_args (Ogg CONFIG_MODE)\n"
                "\tfind_package_handle_standard_args (Vorbis CONFIG_MODE)\n"
                "else ()\n"
                "\tfind_package (Ogg 1.3)\n"
                "\tfind_package (Vorbis COMPONENTS Enc)\n"
                "endif ()\n"
                "if (Ogg_FOUND AND Vorbis_FOUND)\n"
                "\tset (HAVE_EXTERNAL_XIPH_LIBS 1)\n"
                "endif ()\n",
                encoding="utf-8",
            )
            (src / "cmake/SndFileConfig.cmake.in").write_text(
                "set (SndFile_WITH_EXTERNAL_LIBS @SndFile_WITH_EXTERNAL_LIBS@)\n"
                "if (SndFile_WITH_EXTERNAL_LIBS AND NOT @BUILD_SHARED_LIBS@)\n"
                "\tfind_dependency (Ogg 1.3)\n"
                "\tfind_dependency (Vorbis)\n"
                "endif ()\n",
                encoding="utf-8",
            )
            for _ in range(2):
                subprocess.run(
                    ["cmake", f"-DSOURCE_DIR={src}", "-P", str(patch_script)],
                    check=True,
                    capture_output=True,
                    text=True,
                )
            cm = (src / "CMakeLists.txt").read_text(encoding="utf-8")
            self.assertIn("Ogg_FOUND AND Vorbis_FOUND AND Opus_FOUND", cm)
            self.assertIn("set (HAVE_OPUS ${ENABLE_EXTERNAL_LIBS})", cm)
            self.assertIn("set (HAVE_FLAC 0)", cm)
            self.assertIn("$<$<BOOL:${HAVE_OPUS}>:Opus::opus>", cm)
            self.assertIn("set (SndFile_WITH_OPUS 1)", cm)
            self.assertIn("${CMAKE_CURRENT_LIST_DIR}/cmake/FindOpus.cmake", cm)
            checks = (src / "cmake/SndFileChecks.cmake").read_text(encoding="utf-8")
            self.assertIn("find_package (Opus CONFIG)", checks)
            self.assertIn("find_package (Opus)", checks)
            cfg = (src / "cmake/SndFileConfig.cmake.in").read_text(encoding="utf-8")
            self.assertIn("set (SndFile_WITH_OPUS @SndFile_WITH_OPUS@)", cfg)
            self.assertIn("find_dependency (Opus)", cfg)

        # Verify all three target platforms + engine guard + license collection wire up libopus.
        android_cm = (repo / "CMakeLists.txt").read_text(encoding="utf-8")
        self.assertIn("opus-1.5.2.tar.gz", android_cm)
        self.assertIn("patch-libsndfile-opus.cmake", android_cm)
        self.assertIn(
            "ExternalProject_Add_StepDependencies(libsndfile configure libvorbis libopus)",
            android_cm,
        )

        ios_cm = (repo / "ios/CMakeLists.txt").read_text(encoding="utf-8")
        self.assertIn("dependency(libopus", ios_cm)
        self.assertIn("patch-libsndfile-opus.cmake", ios_cm)
        self.assertIn(
            "ExternalProject_Add_StepDependencies(libsndfile configure libvorbis libopus)",
            ios_cm,
        )
        ios_proj = (repo / "ios/project.yml").read_text(encoding="utf-8")
        self.assertIn("-lsndfile -lvorbisfile -lvorbisenc -lvorbis -lopus -logg", ios_proj)

        sw_cm = (repo / "cmake/switch/CMakeLists.txt").read_text(encoding="utf-8")
        self.assertIn("ExternalProject_Add(libopus", sw_cm)
        self.assertIn("patch-libsndfile-opus.cmake", sw_cm)
        self.assertIn("ExternalProject_Add_StepDependencies(libsndfile configure libopus)", sw_cm)
        self.assertIn("add_dependencies(xsystem4 libffi libopus libsndfile)", sw_cm)
        sw_inc = (repo / "cmake/switch/ConfigureLibsndfile.cmake").read_text(encoding="utf-8")
        self.assertIn("CMAKE_FIND_ROOT_PATH", sw_inc)

        xs4_cm = (ROOT / "CMakeLists.txt").read_text(encoding="utf-8")
        self.assertIn("NOT SndFile_WITH_OPUS", xs4_cm)

        licenses_sh = (repo / "collect-licenses.sh").read_text(encoding="utf-8")
        self.assertIn("libopus-prefix/src/libopus/COPYING", licenses_sh)
        licenses_kt = (
            repo / "project/app/src/main/java/io/github/kichikuou/xsystem4/LicensesMenuActivity.kt"
        ).read_text(encoding="utf-8")
        self.assertIn('Entry("libopus"', licenses_kt)
        self.assertIn("dkp_set_target_file(libopus_license", sw_cm)
        sw_hub_cm = (repo / "cmake/switch/hub/CMakeLists.txt").read_text(encoding="utf-8")
        self.assertIn("dkp_install_assets(AliceHub_romfs DESTINATION support TARGETS libopus_license)", sw_hub_cm)
        sw_support_ui = (repo / "cmake/switch/hub/support_ui.c").read_text(encoding="utf-8")
        self.assertIn('"libopus"', sw_support_ui)
        self.assertIn('"romfs:/support/libopus.txt"', sw_support_ui)


if __name__ == "__main__":
    unittest.main()
