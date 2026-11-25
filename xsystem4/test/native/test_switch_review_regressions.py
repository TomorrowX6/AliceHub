"""Exercise Switch regressions without the Switch SDK."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_vm_reign_regressions import extract_function


class SwitchRegressionTest(unittest.TestCase):
    def run_c(self, source, *libs):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "test.c"
            exe = Path(tmp) / "test"
            path.write_text(source)
            subprocess.run(["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror",
                            str(path), "-o", str(exe), *libs], check=True)
            subprocess.run([str(exe)], cwd=tmp, check=True)

    def test_picker_draws_unicode_codepoints_and_respects_stride(self):
        renderer = extract_function("../cmake/switch/launcher.c", "picker_text")
        self.run_c(r"""
#include <assert.h>
#include <locale.h>
#include <stdint.h>
#include <sys/types.h>
#include <uchar.h>
typedef uint8_t u8;
typedef uint16_t u16;
typedef uint32_t u32;
enum { PICKER_WIDTH = 1280, PICKER_HEIGHT = 720, FT_PIXEL_MODE_GRAY = 2 };
#define RGB565_FROM_RGB8(r, g, b) (((r) >> 3) | (((g) >> 2) << 5) | (((b) >> 3) << 11))
typedef struct {
    unsigned rows, width;
    int pitch, pixel_mode;
    const u8 *buffer;
} FT_Bitmap;
typedef struct {
    int bitmap_top, bitmap_left;
    FT_Bitmap bitmap;
    struct { int x; } advance;
} Glyph, *FT_GlyphSlot;
typedef struct {
    u16 *pixels;
    u32 stride;
} Picker;
static ssize_t decode_utf8(u32 *out, const u8 *text)
{
    char32_t cp;
    size_t n = mbrtoc32(&cp, (const char *)text, 4, NULL);
    if (n == (size_t)-1 || n == (size_t)-2)
        return -1;
    *out = cp;
    return (ssize_t)n;
}
static u32 drawn[12];
static int count;
static FT_GlyphSlot picker_glyph(Picker *picker, u32 cp)
{
    (void)picker;
    static const u8 bitmap[] = { 255 };
    static Glyph glyph = { 1, 0, { 1, 1, 1, FT_PIXEL_MODE_GRAY, bitmap }, { 2 * 64 } };
    drawn[count++] = cp;
    return &glyph;
}
""" + renderer + r"""
int main(void)
{
    assert(setlocale(LC_CTYPE, "C.UTF-8"));
    static u16 pixels[2][PICKER_WIDTH + 16];
    Picker picker = { pixels[0], PICKER_WIDTH + 16 };
    picker_text(&picker, 56, 2, "ランス1 中文 한글");
    const u32 expected[] = { 0x30e9, 0x30f3, 0x30b9, '1', ' ', 0x4e2d, 0x6587, ' ', 0xd55c, 0xae00 };
    assert(count == (int)(sizeof(expected) / sizeof(*expected)));
    for (int i = 0; i < count; i++) {
        assert(drawn[i] == expected[i]);
        assert(pixels[1][56 + 2 * i] == 0xffff);
        assert(pixels[0][56 + 2 * i] == 0);
    }
    picker_text(&picker, 56, 0, "字");
    assert(drawn[count - 1] == 0x5b57 && pixels[0][56] == 0);
}
""")

    def test_picker_uses_bundled_and_shared_fonts_per_glyph(self):
        launcher = "../cmake/switch/launcher.c"
        fonts = "\n".join(extract_function(launcher, name)
                          for name in ("picker_load_fonts", "picker_glyph"))
        self.run_c(r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <zlib.h>
#define ALICEHUB_PICKER_COMPRESSED_FONT_SIZE 4
#define FT_LOAD_RENDER 1
#define R_SUCCEEDED(r) ((r) == 0)
enum { PlServiceType_User, PlSharedFontType_Total = 6 };
typedef uint32_t u32;
typedef int FT_Library;
typedef struct { u32 codepoint; } Glyph, *FT_GlyphSlot;
typedef struct { FT_GlyphSlot glyph; u32 first, last; } Face, *FT_Face;
typedef struct { const unsigned char *address; int size; } PlFontData;
typedef struct {
    FT_Library library;
    FT_Face bundled[2], shared[PlSharedFontType_Total];
    unsigned char *fallback_data;
    bool shared_attempted, shared_open;
} Picker;
static Glyph glyph;
static Face primary = { &glyph, 1, 0 };
static Face bundled = { &glyph, 0x4e00, 0x9fff };
static Face shared[PlSharedFontType_Total];
static int FT_Init_FreeType(FT_Library *library) { *library = 1; return 0; }
static int FT_Set_Pixel_Sizes(FT_Face face, int width, int height)
{
    assert(face && !width && height == 24);
    return 0;
}
static int FT_New_Face(FT_Library library, const char *path, int index, FT_Face *face)
{
    assert(library == 1 && index == 0);
    assert(!strcmp(path, "romfs:/fonts/MTLc3m.ttf"));
    *face = &primary;
    return 0;
}
static int FT_New_Memory_Face(FT_Library library, const unsigned char *data, int size,
                              int index, FT_Face *face)
{
    assert(library == 1 && index == 0);
    if (size == 4) {
        assert(!memcmp(data, "Hana", 4));
        *face = &bundled;
    } else {
        assert(size == 1 && *data < PlSharedFontType_Total);
        shared[*data] = (Face){ &glyph, 0x10000 + *data, 0x10000 + *data };
        *face = &shared[*data];
    }
    return 0;
}
static int FT_Get_Char_Index(FT_Face face, u32 cp)
{
    return (face->first <= cp && cp <= face->last) || cp == '?';
}
static int FT_Load_Char(FT_Face face, u32 cp, int flags)
{
    assert(flags == FT_LOAD_RENDER && FT_Get_Char_Index(face, cp));
    face->glyph->codepoint = cp;
    return 0;
}
static bool shared_available;
static int shared_attempts, shared_loaded;
static int plInitialize(int service)
{
    assert(service == PlServiceType_User);
    ++shared_attempts;
    return !shared_available;
}
static int plGetSharedFontByType(PlFontData *font, int type)
{
    static const unsigned char ids[] = { 0, 1, 2, 3, 4, 5 };
    assert(type == shared_loaded++);
    *font = (PlFontData){ &ids[type], 1 };
    return 0;
}
""" + fonts + r"""
int main(void)
{
    assert(!mkdir("romfs:", 0700) && !mkdir("romfs:/fonts", 0700));
    gzFile file = gzopen("romfs:/fonts/HanaMinA.ttf.gz", "wb");
    assert(file && gzwrite(file, "Hana", 4) == 4 && gzclose(file) == Z_OK);
    Picker keyless = {0};
    assert(picker_load_fonts(&keyless));
    const u32 title[] = { 0x7231, 0x4e3d, 0x4e1d, 0x5b66, 0x56ed };
    for (size_t i = 0; i < sizeof(title) / sizeof(*title); ++i)
        assert(picker_glyph(&keyless, title[i])->codepoint == title[i]);
    assert(shared_attempts == 0);
    assert(picker_glyph(&keyless, 0xd55c)->codepoint == '?');
    assert(picker_glyph(&keyless, 0xae00)->codepoint == '?');
    assert(shared_attempts == 1 && shared_loaded == 0);

    shared_available = true;
    Picker console = {0};
    assert(picker_load_fonts(&console));
    for (u32 i = 0; i < PlSharedFontType_Total; ++i)
        assert(picker_glyph(&console, 0x10000 + i)->codepoint == 0x10000 + i);
    assert(shared_attempts == 2 && shared_loaded == PlSharedFontType_Total);
    free(keyless.fallback_data);
    free(console.fallback_data);
}
""", "-lz")

    def test_picker_presents_cached_frame_without_input(self):
        launcher = "../cmake/switch/launcher.c"
        loop = "\n".join(extract_function(launcher, name)
                         for name in ("picker_begin", "compare_names", "validate_game", "choose_game"))
        self.run_c(r"""
#include <assert.h>
#include <dirent.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
typedef uint16_t u16;
typedef uint32_t u32;
typedef uint64_t u64;
typedef int PadState;
typedef struct { int fb; u16 *pixels; u32 stride; } Picker;
#define GAMES "games"
#define ALICEHUB_ENGINE "xsystem4"
#define ALICEHUB_SWITCH_XSYSTEM4
enum { PICKER_HEIGHT = 1, PICKER_ROWS = 18, PICKER_LINE = 31 };
enum { HidNpadStyleSet_NpadStandard, HidNpadButton_A = 1, HidNpadButton_B = 2,
       HidNpadButton_Plus = 4, HidNpadButton_Up = 8, HidNpadButton_Down = 16 };
static u16 pixel;
static int ticks, draws, submitted;
static const u64 *keys;
static bool saw_name, saw_invalid, saw_startup_error;
static bool appletMainLoop(void) { return ticks < 4; }
static void padConfigureInput(int count, int style) { (void)count; (void)style; }
static void padInitializeDefault(PadState *pad) { *pad = 0; }
static void padUpdate(PadState *pad) { *pad = ++ticks; }
static u64 padGetButtonsDown(PadState *pad)
{
    return keys ? keys[*pad - 1] : (*pad == 4 ? HidNpadButton_B : 0);
}
static void svcSleepThread(int ns) { assert(ns == 16000000); }
static u16 *framebufferBegin(int *fb, u32 *stride)
{
    (void)fb;
    *stride = sizeof(pixel);
    return &pixel;
}
static void picker_text(Picker *picker, int x, int y, const char *text)
{
    (void)x; (void)y; (void)text;
    *picker->pixels = ++draws;
    saw_name |= y == 674 && !strcmp(text, "bad");
    saw_invalid |= y == 704 && strstr(text, "System40.ini");
    saw_startup_error |= y == 704 && !strcmp(text, "Broken AIN");
}
static void framebufferEnd(int *fb)
{
    (void)fb;
    if (!keys)
        assert(pixel == 4);
    ++submitted;
}
""" + loop + r"""
int main(void)
{
    Picker picker = {0};
    char game[512];
    assert(!choose_game(&picker, game, sizeof(game), NULL));
    assert(draws == 4 && submitted == 3);

    assert(!mkdir("games", 0700) && !mkdir("games/bad", 0700) && !mkdir("games/good", 0700));
    assert(!mkdir("games/bad/System40.ini", 0700));
    FILE *ini = fopen("games/good/SYSTEM40.INI", "w");
    assert(ini && fputs("CodeName = \"Test.ain\"\n", ini) >= 0 && !fclose(ini));
    const u64 attempt[] = { 0, HidNpadButton_A, HidNpadButton_Down, HidNpadButton_A };
    ticks = 0;
    keys = attempt;
    assert(choose_game(&picker, game, sizeof(game), NULL));
    assert(!strcmp(game, "games/good") && saw_name && saw_invalid);

    const u64 retry[] = { 0, HidNpadButton_B };
    ticks = 0;
    keys = retry;
    strcpy(game, "games/bad");
    assert(!choose_game(&picker, game, sizeof(game), "Broken AIN"));
    assert(saw_startup_error);
}
""")

    def test_system3_engines_require_a_readable_scenario_source(self):
        validator = extract_function("../cmake/switch/launcher.c", "validate_game")
        for engine in ("", "#define ALICEHUB_SWITCH_XSYSTEM35"):
            self.run_c(engine + r"""
#include <assert.h>
#include <ctype.h>
#include <dirent.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
""" + validator + r"""
int main(void)
{
    assert(!mkdir("game", 0700));
    FILE *other = fopen("game/GraphicsGA.ald", "wb");
    assert(other && fputs("not a scenario", other) >= 0 && !fclose(other));
    assert(validate_game("game"));
#ifdef ALICEHUB_SWITCH_XSYSTEM35
    FILE *scenario = fopen("game/TestSA_chs.ALD", "wb");
    assert(scenario && fputs("scenario", scenario) >= 0 && !fclose(scenario));
#else
    FILE *scenario = fopen("game/adisk.dat", "wb");
    assert(scenario && fputc(1, scenario) != EOF && !fclose(scenario));
    assert(validate_game("game"));
    scenario = fopen("game/adisk.dat", "wb");
    const unsigned char header[] = { 2, 0, 3, 0 };
    assert(scenario && fwrite(header, 1, sizeof(header), scenario) == sizeof(header));
    assert(!fseek(scenario, 512, SEEK_SET) && fputc(0, scenario) != EOF && !fclose(scenario));
#endif
    assert(!validate_game("game"));
}
""")

    def test_face_buttons_keep_sideways_mapping(self):
        helper = extract_function("../cmake/switch/controller.h",
                                  "alicehub_switch_face_button")
        self.run_c(r"""
#include <assert.h>
typedef int SDL_JoystickID;
typedef int SDL_GameControllerButton;
typedef int HidNpadIdType;
enum { SDL_CONTROLLER_BUTTON_A, SDL_CONTROLLER_BUTTON_B, SDL_CONTROLLER_BUTTON_X };
enum { HidNpadIdType_No1 = 0, HidNpadIdType_No8 = 7, HidNpadStyleTag_NpadJoyDual = 4 };
enum { HidDeviceTypeBits_JoyLeft = 16, HidDeviceTypeBits_JoyRight = 32 };
static int style[8], type[8];
static int hidGetNpadStyleSet(int id) { return style[id]; }
static int hidGetNpadDeviceType(int id) { return type[id]; }
""" + helper + r"""
int main(void)
{
    style[1] = HidNpadStyleTag_NpadJoyDual;
    type[1] = HidDeviceTypeBits_JoyLeft | HidDeviceTypeBits_JoyRight;
    type[2] = HidDeviceTypeBits_JoyLeft;
    type[3] = HidDeviceTypeBits_JoyRight;
    for (int i = 0; i < 4; ++i) {
        assert(alicehub_switch_face_button(i, SDL_CONTROLLER_BUTTON_A) ==
            (i < 2 ? SDL_CONTROLLER_BUTTON_B : SDL_CONTROLLER_BUTTON_A));
        assert(alicehub_switch_face_button(i, SDL_CONTROLLER_BUTTON_B) ==
            (i < 2 ? SDL_CONTROLLER_BUTTON_A : SDL_CONTROLLER_BUTTON_B));
        assert(alicehub_switch_face_button(i, SDL_CONTROLLER_BUTTON_X) == SDL_CONTROLLER_BUTTON_X);
    }
}
""")

    def test_save_replace_and_rollback(self):
        helper = extract_function("src/savedata.c", "commit_switch_save")
        self.run_c(r"""
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#define xmalloc malloc
#define WARNING(...) ((void)0)
static int fail_promotion;
static int switch_rename(const char *src, const char *dst)
{
    if (access(dst, F_OK) == 0)
        return errno = EEXIST, -1;
    if (fail_promotion && !strcmp(src, "tmp"))
        return errno = EIO, -1;
    return rename(src, dst);
}
#define rename switch_rename
""" + helper + r"""
static void write_file(const char *path, char content)
{
    FILE *fp = fopen(path, "w");
    assert(fp && fputc(content, fp) == content && fclose(fp) == 0);
}
static int read_file(const char *path)
{
    FILE *fp = fopen(path, "r");
    assert(fp);
    int content = fgetc(fp);
    assert(fclose(fp) == 0);
    return content;
}
int main(void)
{
    write_file("tmp", '1');
    assert(commit_switch_save("tmp", "slot") == 0 && read_file("slot") == '1');
    write_file("tmp", '2');
    assert(commit_switch_save("tmp", "slot") == 0 && read_file("slot") == '2');
    assert(access("slot.switch-old", F_OK) != 0);
    write_file("tmp", '3');
    fail_promotion = 1;
    assert(commit_switch_save("tmp", "slot") == -1 && errno == EIO);
    assert(read_file("slot") == '2' && read_file("tmp") == '3');
    assert(access("slot.switch-old", F_OK) != 0);
}
""")

    def test_descriptor_save_names_are_under_launcher_folder(self):
        helper = extract_function("../xsystem35/src/gameresource.c", "descriptor_save_path")
        self.run_c(r"""
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define NOMEMERR abort
""" + helper + r"""
int main(void)
{
    const char *dir = "sdmc:/switch/alicehub/xsystem35/saves/game";
    const char *names[] = {"KICHIKUSA.ASD", "../KICHIKUSA.ASD",
                           "sdmc:/old/KICHIKUSA.ASD", "C:\\old\\KICHIKUSA.ASD"};
    for (unsigned i = 0; i < sizeof(names) / sizeof(*names); ++i) {
        char *path = descriptor_save_path(dir, names[i]);
        assert(path && !strcmp(path, "sdmc:/switch/alicehub/xsystem35/saves/game/KICHIKUSA.ASD"));
        free(path);
    }
    assert(!descriptor_save_path(dir, "../"));
    assert(!descriptor_save_path(dir, ".."));
}
""")

    def test_descriptor_slots_with_the_same_basename_remain_separate(self):
        parser = "\n".join(
            extract_function("../xsystem35/src/gameresource.c", name)
            for name in ("storeDataName", "storeSaveName", "trimRight", "descriptor_save_path",
                         "descriptor_save_path_used", "disambiguate_descriptor_saves",
                         "initGameResourceFromFile")
        )
        self.run_c(r"""
#include <assert.h>
#include <ctype.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#define NOMEMERR abort
#define SYSERROR(...) fprintf(stderr, __VA_ARGS__)
#define max(a, b) ((a) > (b) ? (a) : (b))
enum { DRIFILE_SCO, DRIFILE_CG, DRIFILE_WAVE, DRIFILE_MIDI, DRIFILE_DATA, DRIFILE_RSC, DRIFILE_BGM };
enum { DRIFILEMAX = 255, SAVE_MAXNUMBER = 26 };
typedef struct {
    const char *game_fname[7][DRIFILEMAX], *save_fname[SAVE_MAXNUMBER];
    int cnt[7];
    const char *ain, *wai, *bgi, *sact01, *init, *alk[10], *msgskip, *save_path, *gr_fname;
} GameResource;
""" + parser + r"""
int main(void)
{
    assert(mkdir("saves", 0700) == 0);
    FILE *descriptor = tmpfile();
    assert(descriptor);
    fputs("SaveB second/SLOT.ASD\nSaveA first/slot.asd\nSaveC save-B-1.asd\n"
          "SaveD old/unique.asd\nMsgSkip old/skip.dat\n", descriptor);
    rewind(descriptor);
    GameResource gr;
    assert(initGameResourceFromFile(&gr, descriptor, "xsystem35.gr", "saves"));
    assert(!strcmp(gr.save_fname[0], "saves/slot.asd"));
    assert(!strcmp(gr.save_fname[1], "saves/save-B-2.asd"));
    assert(!strcmp(gr.save_fname[2], "saves/save-B-1.asd"));
    assert(!strcmp(gr.save_fname[3], "saves/unique.asd"));
    assert(!strcmp(gr.msgskip, "saves/skip.dat"));
    assert(!strcmp(gr.save_path, "saves"));
    for (int i = 0; i < 2; i++) {
        FILE *slot = fopen(gr.save_fname[i], "wb");
        assert(slot && fputc('A' + i, slot) == 'A' + i && fclose(slot) == 0);
    }
    for (int i = 0; i < 2; i++) {
        FILE *slot = fopen(gr.save_fname[i], "rb");
        assert(slot && fgetc(slot) == 'A' + i && fclose(slot) == 0);
    }
    fclose(descriptor);
}
""")


if __name__ == "__main__":
    unittest.main()
