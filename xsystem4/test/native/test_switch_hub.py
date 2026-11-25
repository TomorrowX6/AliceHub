"""Host tests for the Switch hub's SD model, launch request codec and offline saves."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

from switch_import_checks import SwitchImportTest


REPO = Path(__file__).resolve().parents[3]
HUB = REPO / "cmake/switch/hub"
ENGINE = REPO / "xsystem4"
SYS4 = ENGINE / "subprojects/libsys4"
UNLOCK = REPO / "project/app/src/main/res/raw/full_unlock_saves.zip"


class SwitchSaveTest(unittest.TestCase):
    def run_harness(self, source, extra=(), libraries=(), setup=None):
        with tempfile.TemporaryDirectory() as tmp:
            if setup:
                setup(tmp)
            executable = Path(tmp) / "test"
            subprocess.run(
                ["cc", "-std=gnu11", "-I", str(HUB), "-I", str(ENGINE / "include"),
                 "-I", str(SYS4 / "include"), str(HUB / "save_storage.c"), *extra,
                 "-x", "c", "-", "-o", str(executable), *libraries],
                input=source, text=True, check=True,
            )
            subprocess.run([str(executable)], cwd=tmp, check=True)

    def test_save_storage_unlock_and_fat_fallback(self):
        self.run_harness(r"""
#include "save_storage.h"
#include "savedata.h"
#include <assert.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static bool fail_publish;
int __real_rename(const char *, const char *);
int __wrap_rename(const char *src, const char *dst)
{
    struct stat st;
    if (!stat(dst, &st)) {
        errno = EEXIST;
        return -1;
    }
    if (fail_publish && !strcmp(dst, "saves/game/LocalSave10.asd") && strstr(src, ".hub-tmp")) {
        errno = EIO;
        return -1;
    }
    return __real_rename(src, dst);
}

static void write_text(const char *path, const char *text)
{
    FILE *fp = fopen(path, "wb");
    assert(fp && fputs(text, fp) >= 0 && !fclose(fp));
}

static void expect_text(const char *path, const char *text)
{
    char value[256] = {0};
    FILE *fp = fopen(path, "rb");
    assert(fp && fread(value, 1, sizeof(value) - 1, fp) == strlen(text) && !fclose(fp));
    assert(!strcmp(value, text));
}

int main(void)
{
    assert(!mkdir("game", 0700) && !mkdir("assets", 0700));
    write_text("game/Evenicle.ain", "");
    assert(!hub_save_supported("game"));
    write_text("game/Rance10.AIN", "");
    assert(hub_save_supported("game"));
    const char *files[] = {"GlobalSave.asd", "Collection.asd", "AFCGMode.asd", "AFBGMMode.asd"};
    for (size_t i = 0; i < 4; ++i) {
        HubSavePath file;
        assert(hub_save_join(file, "assets", files[i]));
        write_text(file, files[i]);
    }
    assert(hub_save_install_unlock("saves/fresh", "assets"));
    expect_text("saves/fresh/GlobalSave.asd", "GlobalSave.asd");
    assert(hub_save_restore_set("saves/fresh", HUB_SAVE_UNLOCK_BACKUP));
    assert(access("saves/fresh/GlobalSave.asd", F_OK) && errno == ENOENT);

    assert(!mkdir("saves/game", 0700) && !mkdir("saves/game/SaveData", 0700));
    write_text("saves/game/GlobalSave.asd", "root original");
    write_text("saves/game/SaveData/Collection.asd", "nested original");
    write_text("saves/game/SaveData/LocalSave2.ASD", "two");
    write_text("saves/game/LocalSave10.asd", "ten");
    write_text("saves/game/LocalSaveX.asd", "ignored");
    HubSaveSlots slots;
    assert(hub_save_scan("saves/game", &slots) && slots.count == 2);
    assert(!strcmp(slots.paths[0], "SaveData/LocalSave2.ASD"));
    assert(!strcmp(slots.paths[1], "LocalSave10.asd"));
    free(slots.paths);

    HubSavePath global, collection;
    assert(hub_save_global_paths("saves/game", global, collection));
    assert(!strcmp(global, "saves/game/GlobalSave.asd"));
    assert(!strcmp(collection, "saves/game/SaveData/Collection.asd"));
    assert(hub_save_install_unlock("saves/game", "assets"));
    assert(hub_save_install_unlock("saves/game", "assets"));
    expect_text("saves/game/GlobalSave.asd", "GlobalSave.asd");
    expect_text("saves/game/SaveData/Collection.asd", "Collection.asd");
    assert(hub_save_has_set_backup("saves/game", HUB_SAVE_UNLOCK_BACKUP));
    assert(hub_save_restore_set("saves/game", HUB_SAVE_UNLOCK_BACKUP));
    expect_text("saves/game/GlobalSave.asd", "root original");
    expect_text("saves/game/SaveData/Collection.asd", "nested original");
    assert(access("saves/game/SaveData/AFBGMMode.asd", F_OK) && errno == ENOENT);
    assert(!hub_save_has_set_backup("saves/game", HUB_SAVE_UNLOCK_BACKUP));

    const char *local = "saves/game/LocalSave10.asd";
    char *temp;
    FILE *fp = atomic_save_open(local, "wb", &temp);
    assert(fp && fputs("modified", fp) >= 0);
    fail_publish = true;
    assert(!atomic_save_commit(fp, local, temp, false));
    expect_text(local, "ten");
    fail_publish = false;
    fp = atomic_save_open(local, "wb", &temp);
    assert(fp && fputs("modified", fp) >= 0 && atomic_save_commit(fp, local, temp, false));
    expect_text(local, "modified");
    assert(!rename(local, "saves/game/LocalSave10.asd.hub-old"));
    assert(hub_save_scan("saves/game", &slots) && slots.count == 2);
    assert(!strcmp(slots.paths[1], "LocalSave10.asd"));
    free(slots.paths);
    assert(hub_save_restore(local, HUB_SAVE_EDIT_BACKUP));
    expect_text(local, "ten");
    assert(!hub_save_has_backup(local, HUB_SAVE_EDIT_BACKUP));
}
""", extra=["-Wall", "-Wextra", "-Werror"], libraries=["-Wl,--wrap=rename"])

    def test_offline_codec_round_trip(self):
        with tempfile.TemporaryDirectory() as tmp:
            probe = subprocess.run(
                ["cc", "-x", "c", "-", "-lz", "-o", str(Path(tmp) / "zlib-test")],
                input="#include <zlib.h>\nint main(void) { return zlibVersion() == 0; }",
                text=True, capture_output=True,
            )
        if probe.returncode:
            self.skipTest("host zlib development package required for codec test")

        def assets(tmp):
            with zipfile.ZipFile(UNLOCK) as archive:
                archive.extractall(Path(tmp) / "assets")

        sources = ("savefile", "buffer", "file", "mt19937int", "string", "system", "utfsjis", "zlib")
        self.run_harness(r"""
#include "save_storage.h"
#include "modifier_api.h"
#include "cJSON.h"
#include "system4/savefile.h"
#include <assert.h>
#include <stdlib.h>
#include <string.h>

static cJSON *read_summary(char *text)
{
    cJSON *json = cJSON_Parse(text);
    free(text);
    assert(json);
    return json;
}

int main(void)
{
    const char *local = "LocalSave1.asd";
    struct gsave *save = gsave_create(5, "test", 1, "local");
    gsave_add_globals_record(save, 1);
    save->globals[0] = (struct gsave_global){AIN_INT, 3, strdup("\x81\x9f\x90\xed\x93\xac\x89\xf1\x90\x94"), 1};
    FILE *fp = fopen(local, "wb");
    assert(fp && gsave_write(save, fp, true, 1) == SAVEFILE_SUCCESS && !fclose(fp));
    gsave_free(save);
    cJSON *json = read_summary(modifier_api_read_local_save_summary(local));
    assert(cJSON_IsTrue(cJSON_GetObjectItem(json, "valid")));
    assert(cJSON_GetObjectItem(json, "battleCount")->valueint == 3);
    cJSON_Delete(json);
    assert(modifier_api_write_local_save_mod(local, "{\"battleCount\":5}"));
    json = read_summary(modifier_api_read_local_save_summary(local));
    assert(cJSON_GetObjectItem(json, "battleCount")->valueint == 5);
    cJSON_Delete(json);
    assert(hub_save_restore(local, HUB_SAVE_EDIT_BACKUP));
    json = read_summary(modifier_api_read_local_save_summary(local));
    assert(cJSON_GetObjectItem(json, "battleCount")->valueint == 3);
    cJSON_Delete(json);

    assert(hub_save_install_unlock("saves/game", "assets"));
    HubSavePath global, collection;
    assert(hub_save_global_paths("saves/game", global, collection));
    json = read_summary(modifier_api_read_global_save_summary(global, collection));
    assert(cJSON_IsTrue(cJSON_GetObjectItem(json, "hasGlobal")));
    assert(cJSON_IsTrue(cJSON_GetObjectItem(json, "hasCollection")));
    cJSON_Delete(json);
    assert(modifier_api_write_global_save_mod(global, collection,
        "{\"gamePlayCount\":20,\"leaderAtkRatio\":2.5,\"OpenMonsterpedia\":1}"));
    json = read_summary(modifier_api_read_global_save_summary(global, collection));
    assert(cJSON_GetObjectItem(json, "gamePlayCount")->valueint == 20);
    assert(cJSON_GetObjectItem(json, "leaderAtkRatio")->valuedouble == 2.5);
    assert(cJSON_GetObjectItem(json, "OpenMonsterpedia")->valueint == 1);
    cJSON_Delete(json);
    assert(hub_save_restore_set("saves/game", HUB_SAVE_EDIT_BACKUP));
    json = read_summary(modifier_api_read_global_save_summary(global, collection));
    assert(cJSON_IsFalse(cJSON_GetObjectItem(json, "hasCommonSystemData")));
    cJSON_Delete(json);
}
""", extra=["-DMODIFIER_OFFLINE_ONLY", str(ENGINE / "src/modifier.c"),
               str(ENGINE / "src/cJSON.c"), *(str(SYS4 / f"src/{s}.c") for s in sources)],
            libraries=["-lz", "-lm"], setup=assets)

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

    def test_support_storage_and_error_export(self):
        hub = Path(__file__).resolve().parents[3] / "cmake/switch/hub"
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(
                ["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror", "-I", str(hub),
                 str(hub / "model.c"), str(hub / "support_files.c"),
                 "-x", "c", "-", "-o", str(Path(tmp) / "support")],
                input=r"""
#include "support_files.h"
#include <assert.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static void write_file(const char *path, const char *text)
{
    FILE *file = fopen(path, "wb");
    assert(file && fputs(text, file) >= 0 && !fclose(file));
}

int main(void)
{
    const char *dirs[] = {"system3", "system3/games", "system3/saves", "cache",
                          "cache/nested", ".downloads"};
    for (size_t i = 0; i < sizeof(dirs) / sizeof(*dirs); i++)
        assert(!mkdir(dirs[i], 0700));
    write_file("system3/games/data", "game");
    write_file("system3/saves/data", "save!");
    write_file(".downloads/data", "resume");
    write_file("cache/nested/data", "tmp");
    assert(!symlink("../../system3/games", "cache/nested/linked-game"));
    HubStorage storage = hub_support_storage(".");
    assert(storage.sizes_ok && storage.volume_ok && storage.capacity > 0);
    assert(storage.games == 4 && storage.saves == 5 && storage.cache == 3);
    assert(hub_support_clear_cache("."));
    assert(access("cache/nested", F_OK) && !access(".downloads/data", F_OK));
    storage = hub_support_storage(".");
    assert(storage.sizes_ok && storage.cache == 0 && storage.games == 4 && storage.saves == 5);
    assert(!rmdir("cache") && !symlink("system3/games", "cache"));
    assert(!hub_support_clear_cache(".") && !access("system3/games/data", F_OK));
    assert(!hub_support_export_error("."));
    HubGame game;
    assert(hub_game_init(&game, HUB_SYSTEM3, "Rance"));
    assert(hub_support_record_error(".", &game, "Missing scenario file"));
    assert(hub_support_export_error("."));
    assert(hub_support_record_error(".", &game, "Missing fonts"));
    assert(hub_support_export_error("."));
    FILE *report = fopen(HUB_SUPPORT_REPORT, "rb");
    char data[256] = {0};
    assert(report && fread(data, 1, sizeof(data) - 1, report) && !fclose(report));
    assert(strstr(data, "Nintendo Switch") && strstr(data, "Game: Rance"));
    assert(strstr(data, "Missing fonts") && !strstr(data, "Missing scenario file"));
}
""",
                text=True,
                check=True,
            )
            subprocess.run([str(Path(tmp) / "support")], cwd=tmp, check=True)

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

static void test_library_view(void)
{
    HubGame games[4];
    assert(hub_game_init(&games[0], HUB_XSYSTEM4, "爱丽丝学园"));
    assert(hub_game_init(&games[1], HUB_XSYSTEM35, "King II"));
    assert(hub_game_init(&games[2], HUB_SYSTEM3, "King II"));
    assert(hub_game_init(&games[3], HUB_SYSTEM3, "zeta"));
    assert(hub_game_matches(&games[0], "丽丝"));
    assert(!hub_game_matches(&games[0], "麗絲"));
    assert(hub_game_matches(&games[1], "kInG ii"));
    assert(!hub_game_matches(&games[1], "King III"));
    assert(hub_game_matches(&games[1], ""));

    HubLibrary library = {games, 4};
    hub_library_sort(&library, HUB_SORT_NAME);
    assert(!strcmp(library.games[0].name, "King II"));
    assert(library.games[0].engine == HUB_SYSTEM3 && library.games[1].engine == HUB_XSYSTEM35);
    assert(!strcmp(library.games[2].name, "zeta"));
    hub_library_sort(&library, HUB_SORT_ENGINE);
    assert(!strcmp(library.games[0].instance_id, "local:system3:King II"));
    assert(library.games[1].engine == HUB_SYSTEM3 && !strcmp(library.games[1].name, "zeta"));
    assert(library.games[2].engine == HUB_XSYSTEM35 && library.games[3].engine == HUB_XSYSTEM4);
}

int main(void)
{
    test_library_view();
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
