"""Render the production MIDI backend without an audio output device."""
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class MidiTsfChecks(unittest.TestCase):
    def test_render_and_controls(self):
        # 120 BPM, 96 ticks per quarter, half-second note and trailing rest.
        events = bytes([0, 0xC0, 0, 0, 0x90, 60, 100,
                        96, 0x80, 60, 0, 96, 0xFF, 0x2F, 0])
        midi = b'MThd' + struct.pack('>IHHH', 6, 0, 1, 96)
        midi += b'MTrk' + struct.pack('>I', len(events)) + events
        backend = (ROOT / 'xsystem35/src/midi.tsf.c').read_text()
        backend = backend[backend.index('#define TSF_IMPLEMENTATION'):]
        harness = r'''
#include <assert.h>
#include <math.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "midi.h"
typedef uint8_t Uint8;
typedef uint16_t Uint16;
typedef int16_t Sint16;
typedef pthread_mutex_t SDL_mutex;
#define AUDIO_S16LSB 0x8010
#define SDL_SwapLE16(x) (x)
#define BGM_VOLVAL_CH 0
#define WARNING(...) do { fprintf(stderr, __VA_ARGS__); fputc('\n', stderr); } while (0)
static struct { int volval[16]; } prv;
static SDL_mutex *SDL_CreateMutex(void) {
    SDL_mutex *m = malloc(sizeof(*m));
    if (m) pthread_mutex_init(m, NULL);
    return m;
}
static void SDL_DestroyMutex(SDL_mutex *m) { pthread_mutex_destroy(m); free(m); }
static void SDL_LockMutex(SDL_mutex *m) { assert(!pthread_mutex_lock(m)); }
static void SDL_UnlockMutex(SDL_mutex *m) { assert(!pthread_mutex_unlock(m)); }
static int device_rate;
static int Mix_QuerySpec(int *rate, Uint16 *format, int *channels) {
    *rate = device_rate; *format = AUDIO_S16LSB; *channels = 2;
    return device_rate != 0;
}
static void (*hook)(void *, Uint8 *, int);
static void Mix_SetPostMix(void (*callback)(void *, Uint8 *, int), void *unused) {
    (void)unused; hook = callback;
}
'''
        harness += backend
        harness += r'''
static void advance(int frames) {
    float buffer[256 * 2];
    while (frames) {
        int n = frames < 256 ? frames : 256;
        render(buffer, n); frames -= n;
    }
}
int main(int argc, char **argv) {
    assert(argc == 3);
    prv.volval[0] = 100;
    unsetenv("XSYSTEM35_SOUNDFONT"); assert(!midi_tsf.init(0));
    setenv("XSYSTEM35_SOUNDFONT", "/missing.sf2", 1); assert(!midi_tsf.init(0));
    setenv("XSYSTEM35_SOUNDFONT", argv[1], 1);
    assert(midi_tsf.init(0)); assert(!hook);  // Engine opens PCM after MIDI init.
    device_rate = 48000; midi_tsf_audio_ready(); assert(hook && rate == 48000);
    FILE *file = fopen(argv[2], "rb"); assert(file);
    uint8_t data[256]; int size = fread(data, 1, sizeof(data), file); fclose(file);
    assert(!midi_tsf.start(1, 1, NULL, 0));
    assert(!midi_tsf.start(1, 1, (uint8_t *)"bad", 3));
    assert(midi_tsf.start(7, 1, data, size));
    Sint16 pcm[4096] = {0}; hook(NULL, (Uint8 *)pcm, sizeof(pcm));
    int nonzero = 0;
    for (unsigned i = 0; i < sizeof(pcm)/sizeof(*pcm); i++) nonzero |= pcm[i];
    assert(nonzero);
    midiplaystate state; midi_tsf.getpos(&state);
    assert(state.in_play && state.play_no == 7 && state.loc_ms > 0);
    midi_tsf.pause(); uint64_t before = frame; advance(1000); assert(frame == before);
    midi_tsf_pause_all(true); midi_tsf.unpause(); advance(1000); assert(frame == before);
    midi_tsf.pause(); midi_tsf_pause_all(false); advance(1000); assert(frame == before);
    midi_tsf.unpause(); advance(1000); assert(frame == before + 1000);
    midi_tsf.stop(); assert(!playing);
    for (unsigned i = 0; i < sizeof(pcm)/sizeof(*pcm); i++) pcm[i] = 123;
    hook(NULL, (Uint8 *)pcm, sizeof(pcm));
    for (unsigned i = 0; i < sizeof(pcm)/sizeof(*pcm); i++) assert(pcm[i] == 123);
    assert(midi_tsf.start(1, 2, data, size)); uint64_t length = duration;
    advance(length + 100); assert(playing && repeats == 1 && frame == 100);
    advance(length); assert(!playing);
    assert(midi_tsf.start(1, 0, data, size)); advance(length * 3 + 100);
    assert(playing && frame == 100);
    midi_tsf.fadestart(100, 25, 0); assert(midi_tsf.fading());
    advance(2400); assert(fabsf(volume - 62.5f) < 0.01f);
    midi_tsf.pause(); before = fade_left; advance(1000); assert(fade_left == before);
    midi_tsf.unpause(); advance(2400);
    assert(volume == 25 && playing && !midi_tsf.fading());
    midi_tsf.fadestart(100, 80, 1); advance(4800);
    assert(volume == 80 && !playing && !midi_tsf.fading());
    assert(midi_tsf.start(1, 0, data, size));
    midi_tsf.fadestart(0, 50, 0); assert(volume == 50);
    prv.volval[0] = 20; midi_tsf.reapply_volume(); assert(fabsf(balance - .2f) < .001f);
    midi_tsf.fadestart(0, 0, 1); assert(!playing);
    midi_tsf.reset(); midi_tsf.exit(); assert(!hook && !synth && !mutex);
    puts("TinySoundFont MIDI: nonzero PCM, negotiated rate, loops, pause, fades, mixing and errors passed");
}
'''
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source, binary = directory / 'check.c', directory / 'check'
            source.write_text(harness)
            (directory / 'test.mid').write_bytes(midi)
            subprocess.run(['cc', '-std=c11', '-Wall', '-Wextra',
                            '-fsanitize=address,undefined', '-g',
                            '-I', str(ROOT / 'xsystem35/src'), str(source),
                            '-o', str(binary)], check=True)
            subprocess.run([str(binary), str(ROOT / 'xsystem35/soundfonts/TimGM6mb.sf2'),
                            str(directory / 'test.mid')], check=True)


if __name__ == '__main__':
    unittest.main()
