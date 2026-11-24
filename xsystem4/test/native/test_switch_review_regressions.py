"""Exercise Switch controller and save handling without the Switch SDK."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_vm_reign_regressions import extract_function


class SwitchRegressionTest(unittest.TestCase):
    def run_c(self, source):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "test.c"
            exe = Path(tmp) / "test"
            path.write_text(source)
            subprocess.run(["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror",
                            str(path), "-o", str(exe)], check=True)
            subprocess.run([str(exe)], cwd=tmp, check=True)

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


if __name__ == "__main__":
    unittest.main()
