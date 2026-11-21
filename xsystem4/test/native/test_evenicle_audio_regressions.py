#!/usr/bin/env python3
"""Evenicle EX mixer configuration and native playback lifecycle regressions."""

import struct
import tempfile
import unittest
import wave
from pathlib import Path

from test_rance03_volume_regressions import (
    LIBSYS4_CORE,
    ROOT,
    _ex_block,
    _ex_pstr,
    _ex_soundinfo_table,
    build_extf,
    compile_and_run,
)


def group_table(rows):
    fields = [(b"Group", 1), (b"Voice", 1), (b"Volume", 2)]
    data = bytearray(struct.pack("<I", len(fields)))
    for i, (name, kind) in enumerate(fields):
        data += struct.pack("<I", kind) + _ex_pstr(name)
        data += struct.pack("<II", 0, int(i == 0))
    data += struct.pack("<II", len(fields), len(rows))
    for group, volume in rows:
        data += struct.pack("<IiIiIf", 1, group, 1, -1, 2, volume)
    return _ex_block("Ｅ＿サウンドグループ設定".encode("cp932"), 4, data)


HARNESS = r'''
#include <assert.h>
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <SDL.h>
#include "xsystem4.h"
#include "asset_manager.h"
#include "audio.h"
#include "id_pool.h"
#include "mixer.h"
#include "system4/archive.h"
#include "system4/string.h"
#include "hll/hll.h"

struct config config;
bool game_rance10, game_dohnadohna, game_evenicle2, game_rance03, game_ixseal;
static const char *wave_path;
static Uint32 ticks;
int SDL_InitSubSystem(Uint32 flags) { return 0; }
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
Uint32 SDL_GetTicks(void) { return ticks; }
bool asset_exists_by_name(enum asset_type t, const char *name, int *id) { return false; }
struct archive_data *asset_get(enum asset_type t, int no) { return NULL; }
struct archive_data *asset_get_by_name(enum asset_type t, const char *name, int *id) { return NULL; }
void asset_foreach_file(enum asset_type t, void (*cb)(struct archive_data*, void*), void *u) {}
void bgi_read(const char *path) {}
struct bgi *bgi_get(int no) { return NULL; }
void wai_load(const char *path) {}
struct wai *wai_get(int no) { return NULL; }
const char *display_sjis0(const char *s) { return s; }
char *gamedir_path_icase(const char *name) {
    return strcmp(name, "missing") ? strdup(wave_path) : NULL;
}
void audio_init(void) { mixer_init(); }
void static_library_replace(struct static_library *l, const char *name, void *f) {}

#include "audio_mixer.c"
#include "hll/KiwiSoundEngine_v12.c"

static int slots(void) {
    int total = 0;
    for (int i = 0; i < nr_mixers; i++)
        total += sts_mixer_get_active_voices(&mixers[i].mixer);
    return total;
}

static float render(int chunks) {
    float output[CHUNK_SIZE * 2], peak = 0;
    for (int i = 0; i < chunks; i++) {
        audio_callback(NULL, (Uint8 *)output, sizeof(output));
        for (unsigned j = 0; j < CHUNK_SIZE * 2; j++)
            peak = fmaxf(peak, fabsf(output[j]));
        ticks += 24;
    }
    return peak;
}

static void prepare(int id, const char *name, int group) {
    struct string *s = cstr_to_string(name);
    assert(kiwi_prepare_3(id, s, false));
    assert(kiwi_group(id) == group);
    free_string(s);
}

static void start(int id, const char *name, int group, bool loop) {
    prepare(id, name, group);
    assert(kiwi_loop(id, loop ? 0 : 1));
    assert(kiwi_play(id));
}

static void config_test(bool empty) {
    assert(config.mixer_nr_channels == 14);
    assert(nr_mixers == 14);
    assert(master == &mixers[0]);
    assert(config.mixer_volumes[0] == (empty ? 80 : 50));
    assert(config.mixer_volumes[3] == (empty ? 100 : 80));
    assert(config.mixer_volumes[6] == 53);
    assert(config.mixer_volumes[7] == 59);
    assert(config.mixer_volumes[8] == 0);
    assert(config.mixer_volumes[9] == 100);
    assert(mixers[10].parent == &mixers[3]);
    assert(mixers[11].parent == &mixers[3]);
    assert(mixer_set_volume(10, 53));
    int value = -1;
    assert(mixer_get_volume(10, &value) && value == 53);
    assert(kiwi_mixer_default(10, &value) && value == 80);
    prepare(12, "10001", 11);
    assert(kiwi_play(12));
    assert(render(4) > 0.01f);
    kiwi_stop(12);
    assert(mixer_set_mute(11, 1));
    prepare(12, "10001", 11);
    assert(kiwi_play(12));
    render(4);
    assert(render(4) == 0);
    assert(mixer_set_mute(11, 0));
    prepare(12, "10101", 10);
    assert(kiwi_play(12));
    assert(render(4) > 0.01f);
    assert(mixer_set_volume(10, 0));
    render(4);
    assert(render(4) == 0);
    assert(mixer_set_volume(10, 80));
    assert(render(4) > 0.01f);
    struct string *bad = cstr_to_string("badgroup");
    assert(!kiwi_prepare_3(12, bad, false));
    free_string(bad);
    struct string *missing = cstr_to_string("missing");
    assert(!kiwi_prepare_3(13, missing, false));
    free_string(missing);
    assert(!kiwi_exists(13));
    printf("EX mixers=14; INI preserved=%d; groups 10/11 render, mute, volume and invalid routing OK\n", !empty);
}

static void lifecycle_test(void) {
    int baseline = slots(), peak = baseline;
    struct string *se = cstr_to_string("se");
    kiwi_set_se(5, 33, false);
    for (int scene = 0; scene < 40; scene++) {
        int bgm = 1 + scene % 2;
        start(bgm, "bgm", 1, true);
        kiwi_fade(3 - bgm, 50, 0, true, 0);
        for (int i = 0; i < 3; i++)
            start(3 + i * 2 + scene % 2, "se", 2, true);
        for (int id = 20; id <= 24; id++)
            start(id, "ambient", 12, false);
        for (int message = 0; message < 25; message++) {
            int se_id = 10 + message % 2;
            start(se_id, "se", 2, false);
            kiwi_fade(se_id, 0, 0, false, -1);
            kiwi_fade(se_id, 20, 1, false, 0);
            // SYS_PlayVoice: Prepare(12), Play(12); next message can replace live audio.
            prepare(12, message % 2 ? "10001" : "10101", message % 2 ? 11 : 10);
            struct channel *voice = id_pool_get(&sounds, 12);
            assert(!voice->fade.pending && !voice->fade.fading);
            assert(voice->volume == 100 && !voice->paused);
            assert(kiwi_play(12));
            // BeginBGMFade ducks BGM and looping SE channels, never master or voice.
            kiwi_fade(bgm, 100, 0.6f, false, 5);
            for (int i = 0; i < 3; i++)
                kiwi_fade(3 + i * 2 + scene % 2, 100, 0.6f, false, 5);
            assert(kiwi_play_se(5, se));
            peak = max(peak, slots());
            assert(slots() <= baseline + 20);
            assert(render(4) > 0.01f);
            if (message % 5 == 0) {
                assert(kiwi_pause(12));
                assert(kiwi_stop(12));
            } else if (message % 5 == 1) {
                assert(kiwi_fade(12, 30, 0, true, 0));
                render(1);
            } else if (message % 5 == 2) {
                render(30);
                assert(!kiwi_playing(12));
                assert(sts_mixer_get_active_voices(&mixers[voice->mixer_no].mixer) == 0);
            }
            // UpdateSoundShuffle re-prepares finished ambient handles, not new IDs.
            for (int id = 20; id <= 24; id++)
                if (!kiwi_playing(id)) start(id, "ambient", 12, false);
        }
        // SYS_Sound_Stop stops voices, looping SE and background channels.
        for (int id = 1; id <= 24; id++) kiwi_stop(id);
        kiwi_stop_se(5);
        assert(slots() == baseline);
        assert(kiwi_exists(12));
        assert(kiwi_unprepare(12));
        assert(!kiwi_exists(12));
    }
    free_string(se);
    printf("40 scenes / 1000 voices: baseline slots=%d peak=%d; every scene returns to baseline\n", baseline, peak);
}

static void exhaustion_test(void) {
    int baseline = slots();
    for (int i = 0; i < STS_MIXER_VOICES; i++) start(100 + i, "10001", 11, true);
    prepare(12, "10001", 11);
    assert(!kiwi_play(12));
    assert(!kiwi_playing(12));
    assert(sts_mixer_get_active_voices(&mixers[11].mixer) == STS_MIXER_VOICES);
    assert(kiwi_pause(100));
    assert(kiwi_unprepare(100));
    assert(kiwi_play(12));
    assert(kiwi_stop(12));
    for (int i = 1; i < STS_MIXER_VOICES; i++) kiwi_unprepare(100 + i);
    assert(slots() == baseline);
    // Pending and active stop-fades belong to the channel, not its handle.
    for (int i = 0; i < 50; i++) {
        prepare(12, "10001", 11);
        kiwi_fade(12, 0, 0, false, -1);
        kiwi_fade(12, 100, 1, false, 0);
        assert(((struct channel *)id_pool_get(&sounds, 12))->fade.pending);
        prepare(12, "10001", 11);
        assert(((struct channel *)id_pool_get(&sounds, 12))->volume == 100);
        kiwi_play(12);
        kiwi_fade(12, 20, 0, true, 0);
        prepare(12, "10101", 10);
        kiwi_play(12);
        assert(render(6) > 0.01f);
        assert(kiwi_playing(12));
        kiwi_stop(12);
        assert(slots() == baseline);
    }
    start(12, "10001", 11, true);
    kiwi_fade(12, 20, 0, true, 0);
    render(8);
    assert(!kiwi_playing(12));
    assert(slots() == baseline);
    printf("capacity=%d: overflow returns false; paused close, EOF, fade-stop and replacement release slots\n", STS_MIXER_VOICES);
}

int main(int argc, char **argv) {
    assert(argc == 5);
    wave_path = argv[3];
    config.default_volume = 80;
    if (strcmp(argv[4], "empty")) {
        static const char *const names[] = {"Master", "BGM", "SE", "Voice", "Ambient", "BackVoice"};
        static const int volumes[] = {50, 80, 80, 80, 80, 80};
        set_default_mixers(names, volumes, 6);
    }
    config.ex_path = argv[1];
    kiwi_prelink();
    kiwi_init();
    sound_metas = calloc(1, sizeof(*sound_metas));
    sound_metas[0].ex = ex_read_file(argv[2]);
    assert(sound_metas[0].ex);
    sound_metas[0].info = ex_get_table(sound_metas[0].ex, "SoundInfo");
    nr_sound_metas = 1;
    kiwi_build_sound_row_index();
    if (!strcmp(argv[4], "life")) lifecycle_test();
    else if (!strcmp(argv[4], "full")) exhaustion_test();
    else if (!strcmp(argv[4], "invalid") || !strcmp(argv[4], "absent")) {
        assert(config.mixer_nr_channels == 6);
        printf("absent/invalid EX groups leave configured mixers unchanged\n");
    } else config_test(!strcmp(argv[4], "empty"));
    kiwi_fini();
    assert(slots() == nr_mixers - 1);
    return 0;
}
'''


class EvenicleAudioTests(unittest.TestCase):
    def run_probe(self, mode, invalid=False):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            groups = directory / "groups.ex"
            metadata = directory / "timemap.ex"
            audio = directory / "audio.wav"
            volumes = [0.8, 0.8, 0.8, 1.0, 0.8, 0.8, 0.53, 0.59, -0.2, 1.5, 0.8, 0.8, 0.8, 0.8]
            rows = [(-1, 0.8), (1024, 0.8)] if invalid else list(enumerate(volumes))
            groups.write_bytes(build_extf([] if mode == "absent" else [group_table(rows)]))
            metadata.write_bytes(build_extf([_ex_soundinfo_table(
                [(b"10001", 11), (b"10101", 10), (b"bgm", 1),
                 (b"se", 2), (b"ambient", 12), (b"badgroup", 100)],
                [(b"Name", 3), (b"Group", 1)],
            )]))
            with wave.open(str(audio), "wb") as stream:
                stream.setparams((1, 2, 44100, 22050, "NONE", "not compressed"))
                stream.writeframes(struct.pack("<h", 8192) * 22050)
            output = compile_and_run(
                HARNESS,
                [*LIBSYS4_CORE, ROOT / "src/id_pool.c"],
                ["-lz", "-lm", "-lsndfile"],
                [str(groups), str(metadata), str(audio), mode],
            )
            print(output, end="", flush=True)

    def test_ex_extends_ini_and_voice_volume_routing(self):
        self.run_probe("groups")

    def test_ex_without_ini(self):
        self.run_probe("empty")

    def test_invalid_ex_groups(self):
        self.run_probe("invalid", invalid=True)

    def test_absent_ex_groups(self):
        self.run_probe("absent")

    def test_realistic_message_and_scene_lifecycle(self):
        self.run_probe("life")

    def test_capacity_and_replaced_fades(self):
        self.run_probe("full")


if __name__ == "__main__":
    unittest.main()
