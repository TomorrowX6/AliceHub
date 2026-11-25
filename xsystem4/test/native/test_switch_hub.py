"""Host tests for the Switch hub's SD model and launch request codec."""
from pathlib import Path
import subprocess
import tempfile
import unittest


class SwitchHubTest(unittest.TestCase):
    def test_install_transaction(self):
        hub = Path(__file__).resolve().parents[3] / "cmake/switch/hub"
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(
                ["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror", "-I", str(hub),
                 str(hub / "model.c"), str(hub / "install_transaction.c"),
                 "-x", "c", "-", "-o", str(Path(tmp) / "test")],
                input=r"""
#include "install_transaction.h"
#include <assert.h>
#include <errno.h>
#include <sys/stat.h>
#include <unistd.h>

static void put(const char *path, int value)
{
    FILE *f = fopen(path, "wb");
    assert(f && fputc(value, f) != EOF && !fclose(f));
}

static bool verify(const char *dir, void *context)
{
    char path[HUB_PATH_SIZE];
    assert(snprintf(path, sizeof(path), "%s/data/game.dat", dir) < (int)sizeof(path));
    FILE *f = fopen(path, "rb");
    if (!f)
        return false;
    bool ok = fgetc(f) == *(int *)context;
    fclose(f);
    return ok;
}

int main(void)
{
    HubGame game;
    assert(hub_game_init(&game, HUB_XSYSTEM4, "Alice"));
    assert(hub_install_recover("."));
    HubInstallTransaction *tx = hub_install_begin(".", &game);
    assert(tx);
    assert(!hub_install_begin(".", &game) && errno == EBUSY);
    assert(!hub_install_recover(".") && errno == EBUSY);
    char path[HUB_PATH_SIZE], target[HUB_PATH_SIZE];
    assert(hub_game_path(".", &game, target));
    assert(access(target, F_OK));
    assert(!hub_install_stage_file(tx, "../../outside", path));
    assert(!hub_install_stage_file(tx, "alias. /file", path));
    assert(hub_install_stage_file(tx, "data/game.dat", path));
    assert(!symlink("/tmp", path));
    assert(!hub_install_stage_file(tx, "data/game.dat", path));
    int old = 1, new = 2;
    assert(!hub_install_verify(tx, verify, &old));
    assert(!unlink(path));
    put(path, old);
    assert(!hub_install_commit(tx));
    assert(!hub_install_verify(tx, verify, &new));
    assert(hub_install_verify(tx, verify, &old));
    assert(hub_install_commit(tx) && verify(target, &old));

    assert(!mkdir("xsystem4/saves", 0700) && !mkdir("xsystem4/saves/Alice", 0700));
    put("xsystem4/saves/Alice/save.dat", 3);
    tx = hub_install_begin(".", &game);
    assert(tx && hub_install_stage_file(tx, "data/game.dat", path));
    put(path, new);
    assert(hub_install_verify(tx, verify, &new));
    assert(hub_install_rollback(tx) && verify(target, &old));

    const char *work = "xsystem4/.install-v1/Alice";
    const char *backup = "xsystem4/.install-v1/Alice/backup";
    tx = hub_install_begin(".", &game);
    assert(tx && hub_install_stage_file(tx, "data/game.dat", path));
    put(path, new);
    assert(!mkdir("xsystem4/.install-v1/Alice/backup", 0700));
    put("xsystem4/.install-v1/Alice/backup/occupied", 4);
    assert(hub_install_verify(tx, verify, &new));
    assert(!hub_install_commit(tx));
    assert(hub_install_rollback(tx) && verify(target, &old));

    assert(!mkdir(work, 0700) && !rename(target, backup));
    assert(hub_install_recover(".") && verify(target, &old));
    assert(!mkdir(work, 0700) && !mkdir(backup, 0700));
    assert(hub_install_recover(".") && verify(target, &old) && access(backup, F_OK));
    tx = hub_install_begin(".", &game);
    assert(tx && hub_install_stage_file(tx, "data/game.dat", path));
    put(path, new);
    assert(hub_install_verify(tx, verify, &new));
    assert(hub_install_commit(tx) && verify(target, &new));
    assert(!access("xsystem4/saves/Alice/save.dat", F_OK));
}
""",
                text=True,
                check=True,
            )
            subprocess.run([str(Path(tmp) / "test")], cwd=tmp, check=True)

    def test_model_and_handoff(self):
        hub = Path(__file__).resolve().parents[3] / "cmake/switch/hub"
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(
                ["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror", "-I", str(hub),
                 str(hub / "model.c"), "-x", "c", "-", "-o", str(Path(tmp) / "test")],
                input=r"""
#include "model.h"
#include <assert.h>
#include <string.h>
#include <sys/stat.h>

static bool parse(const char *text, HubHandoff *handoff)
{
    FILE *file = tmpfile();
    assert(file && fputs(text, file) >= 0);
    rewind(file);
    bool ok = hub_handoff_parse(file, handoff);
    assert(!fclose(file));
    return ok;
}

int main(void)
{
    const char *dirs[] = {"system3", "system3/games", "system3/games/ランス 1",
                          "xsystem35", "xsystem35/games", "xsystem35/games/ランス 1",
                          "xsystem4", "xsystem4/games", "xsystem4/games/爱丽丝学园",
                          "xsystem4/games/.partial"};
    for (size_t i = 0; i < sizeof(dirs) / sizeof(*dirs); ++i)
        assert(!mkdir(dirs[i], 0700));
    FILE *not_a_game = fopen("xsystem4/games/file", "wb");
    assert(not_a_game && !fclose(not_a_game));
    HubLibrary library = {0};
    assert(hub_library_scan(&library, ".") && library.count == 3);
    assert(strcmp(library.games[0].instance_id, library.games[1].instance_id));
    HubGame a = library.games[0];
    assert(hub_library_scan(&library, ".") && !strcmp(a.game_id, library.games[0].game_id));
    char path[HUB_PATH_SIZE];
    assert(hub_game_save_path(".", &a, path) && strstr(path, "/saves/ランス 1"));
    hub_library_free(&library);

    HubSettings settings;
    assert(hub_settings_load(".", &settings) && settings.sort == HUB_SORT_NAME);
    settings.sort = HUB_SORT_ENGINE;
    assert(hub_settings_save(".", &settings));
    assert(hub_settings_load(".", &settings) && settings.sort == HUB_SORT_ENGINE);
    assert(!rename("settings", "settings.bak"));
    assert(hub_settings_load(".", &settings) && settings.sort == HUB_SORT_ENGINE);
    FILE *future = fopen("settings", "wb");
    assert(future && fputs("alicehub-settings 9\nname\n", future) >= 0 && !fclose(future));
    assert(!hub_settings_load(".", &settings) && settings.sort == HUB_SORT_NAME);

    HubHandoff request = {.game = a}, read = {0};
    assert(hub_handoff_write(".", false, &request));
    assert(hub_handoff_take(".", false, &read) && !strcmp(read.game.instance_id, a.instance_id));
    assert(!hub_handoff_take(".", false, &read));
    strcpy(request.error, "Missing script\nline two");
    assert(!hub_handoff_write(".", false, &request));
    assert(hub_handoff_write(".", true, &request));
    assert(hub_handoff_take(".", true, &read) && !strcmp(read.error, "Missing script line two"));
    assert(!parse("alicehub-handoff 2\nsystem3\nvalid\n\n", &read));
    assert(!parse("alicehub-handoff 1\ninvalid\nvalid\n\n", &read));
    assert(!parse("alicehub-handoff 1\nsystem3\n../bad\n\n", &read));
    assert(!parse("alicehub-handoff 1\nsystem3\nsdmc:/other\n\n", &read));
    assert(!parse("alicehub-handoff 1\nsystem3\nbad\\path\n\n", &read));
    assert(!parse("alicehub-handoff 1\nsystem3\nvalid\n", &read));
    assert(!parse("alicehub-handoff 1\nsystem3\nvalid\n\ntrailing", &read));
}
""",
                text=True,
                check=True,
            )
            subprocess.run([str(Path(tmp) / "test")], cwd=tmp, check=True)
