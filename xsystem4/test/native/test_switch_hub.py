"""Host tests for the Switch hub's SD model, launch requests, and save transfers."""
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest
import zipfile
import zlib

from switch_import_checks import SwitchImportTest
from switch_mod_checks import SwitchModsTest


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
        errno = EIO;
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
#include "save_presets.h"
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
    assert(hub_save_preset_count(false) == 4 && hub_save_preset_count(true) == 1);
    cJSON *preset = hub_save_preset_changes(false, 0, json);
    assert(preset && cJSON_GetObjectItem(preset, "foodTicket")->valueint == 999);
    assert(cJSON_GetObjectItem(preset, "atkPerPhys")->valueint == 999);
    assert(cJSON_GetObjectItem(preset, "statusMaxPer")->valueint == 999);
    cJSON_Delete(preset);
    preset = hub_save_preset_changes(false, 1, json);
    assert(preset && cJSON_GetArraySize(preset) == 3);
    assert(cJSON_GetObjectItem(preset, "perFindTreasure")->valueint == 999);
    assert(cJSON_GetObjectItem(preset, "treasureNotGetCount")->valueint == 99);
    assert(cJSON_GetObjectItem(preset, "rareLuck")->valueint == 999);
    cJSON_Delete(preset);
    preset = hub_save_preset_changes(false, 2, json);
    assert(preset && cJSON_GetArraySize(preset) == 5);
    assert(cJSON_GetObjectItem(preset, "setCardCount")->valueint == 99);
    assert(cJSON_GetObjectItem(preset, "setCharStar")->valueint == 5);
    cJSON_Delete(preset);
    preset = hub_save_preset_changes(false, 3, json);
    assert(preset && cJSON_GetObjectItem(preset, "humanDead")->valueint == 0);
    assert(cJSON_GetObjectItem(preset, "leazasState")->valueint == 0);
    assert(cJSON_GetObjectItem(preset, "subHanny")->valueint == 99999);
    cJSON_Delete(preset);
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
    preset = hub_save_preset_changes(true, 0, json);
    assert(preset && cJSON_GetArraySize(preset) == 22);
    assert(cJSON_GetObjectItem(preset, "gamePlayCount")->valueint == 50);
    assert(cJSON_GetObjectItem(preset, "leaderAtkRatio")->valuedouble == 10);
    assert(cJSON_GetObjectItem(preset, "totalClearPoint")->valueint == 999);
    char *preset_text = cJSON_PrintUnformatted(preset);
    assert(preset_text && modifier_api_write_global_save_mod(global, collection, preset_text));
    free(preset_text);
    cJSON_Delete(preset);
    cJSON_Delete(json);
    json = read_summary(modifier_api_read_global_save_summary(global, collection));
    assert(cJSON_GetObjectItem(json, "totalClearPoint")->valueint == 999);
    assert(cJSON_GetObjectItem(json, "gamePlayCount")->valueint == 50);
    assert(cJSON_GetObjectItem(json, "leaderAtkRatio")->valuedouble == 10);
    cJSON_Delete(json);
    json = cJSON_Parse("{\"hasCommonSystemData\":true}");
    preset = hub_save_preset_changes(true, 0, json);
    assert(preset && cJSON_GetArraySize(preset) == 15);
    assert(!cJSON_GetObjectItem(preset, "gamePlayCount") && !cJSON_GetObjectItem(preset, "leaderAtkRatio"));
    cJSON_Delete(preset);
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
""", extra=["-DMODIFIER_OFFLINE_ONLY", str(HUB / "save_presets.c"), str(ENGINE / "src/modifier.c"),
               str(ENGINE / "src/cJSON.c"), *(str(SYS4 / f"src/{s}.c") for s in sources)],
            libraries=["-lz", "-lm"], setup=assets)


class SwitchHubTest(unittest.TestCase):
    def test_online_manifest_and_resume(self):
        repo = Path(__file__).resolve().parents[3]
        hub = repo / "cmake/switch/hub"
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(
                ["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror",
                 "-I", str(hub), "-I", str(repo / "xsystem4/include"),
                 str(hub / "model.c"), str(hub / "install_transaction.c"), str(hub / "fs_util.c"),
                 str(hub / "online_manifest.c"), str(repo / "xsystem4/src/cJSON.c"),
                 "-x", "c", "-", "-o", str(Path(tmp) / "test")],
                input=r"""
#include "online_manifest.h"
#include <assert.h>
#include <string.h>

#define SHA "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
#define ENTRY(PATH, BYTES) "{\"path\":\"" PATH "\",\"key\":\"Test/日本 語.ald\"," \
                           "\"sha256\":\"" SHA "\",\"size\":" BYTES "}"
#define MANIFEST(ENTRIES) "{\"baseName\":\"Test Game\",\"files\":[" ENTRIES "]}"

int main(void)
{
    OnlineManifest doc = {0};
    assert(online_manifest_parse(MANIFEST(ENTRY("GAMEsa.ALD", "4294967295") ","
                                         ENTRY("data\\\\sample", "0")), &doc));
    assert(doc.count == 2 && doc.total == ONLINE_FILE_LIMIT);
    assert(!strcmp(doc.folder, "Test Game") && !strcmp(doc.files[0].path, "data/sample"));
    assert(!strcmp(doc.files[1].sha256,
                   "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
    assert(online_boot_marker(doc.files[1].path) == HUB_XSYSTEM35);
    assert(online_boot_marker("ADISK.DAT") == HUB_SYSTEM3);
    assert(online_boot_marker("AliceStart.ini") == HUB_XSYSTEM4);
    assert(online_boot_marker("nested/ADISK.DAT") == HUB_ENGINE_COUNT);
    online_manifest_free(&doc);
    assert(!online_manifest_parse(MANIFEST(ENTRY("GAMEsa.ALD", "4294967296")), &doc));
    assert(!online_manifest_parse(MANIFEST(ENTRY("GAMEsa.ALD", "23.25")), &doc));
    assert(!online_manifest_parse(MANIFEST(ENTRY("GAMEsa.ALD", "-1")), &doc));
    assert(!online_manifest_parse(MANIFEST(ENTRY("../file", "1")), &doc));
    assert(!online_manifest_parse(MANIFEST(ENTRY("AA", "1") "," ENTRY("aa/b", "1")), &doc));
    assert(!online_manifest_parse(MANIFEST(ENTRY("FILE", "1") "," ENTRY("file", "1")), &doc));
    assert(!online_manifest_parse(MANIFEST(ENTRY(".AliceHub-online-v1", "1")), &doc));
    assert(!online_manifest_parse(MANIFEST(ENTRY("n\\u0000ul", "1")), &doc));
    char url[ONLINE_URL_SIZE];
    assert(online_payload_url("rance10", NULL, url) &&
           !strcmp(url, "https://alicehub.000.moe/rance10/manifest.json"));
    assert(online_payload_url("test", "Test/日本 語.ald", url) &&
           !strcmp(url, "https://alicehub.000.moe/test/files/Test/%e6%97%a5%e6%9c%ac%20%e8%aa%9e.ald"));
    assert(!online_payload_url("test", "../file", url));
    assert(!online_payload_url("https://bad.invalid", "file", url));
    assert(online_resume_matches("bytes 2-3/4", "\"abc\"", "\"abc\"", 2, 4));
    assert(!online_resume_matches("bytes 2-3/4", "\"def\"", "\"abc\"", 2, 4));
    assert(!online_resume_matches("bytes 2-3/4", "W/\"abc\"", "\"abc\"", 2, 4));
    assert(!online_resume_matches("bytes 0-3/4", "\"abc\"", "\"abc\"", 2, 4));
    assert(!online_content_range("bytes 2-3/5", 2, 4));
    assert(!online_content_range("bytes 2-2/4", 2, 4));
    assert(!online_content_range("bytes 18446744073709551616-3/4", 2, 4));
    assert(!online_content_range("bytes 2-3/4 trailing", 2, 4));
}
""",
                text=True,
                check=True,
            )
            subprocess.run([str(Path(tmp) / "test")], cwd=tmp, check=True)

    def test_install_transaction(self):
        hub = Path(__file__).resolve().parents[3] / "cmake/switch/hub"
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(
                ["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror", "-I", str(hub),
                 str(hub / "model.c"), str(hub / "install_transaction.c"), str(hub / "fs_util.c"),
                 "-x", "c", "-", "-Wl,--wrap=rename", "-Wl,--wrap=fopen",
                 "-o", str(Path(tmp) / "test")],
                input=r"""
#include "install_transaction.h"
#include <assert.h>
#include <errno.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#define TARGET "./xsystem4/games/Alice"
#define WORK "./xsystem4/.install-v1/Alice"
static bool no_rename;
static unsigned copy_attempts, fail_at, crash_at;

int __real_rename(const char *, const char *);
int __wrap_rename(const char *source, const char *destination)
{
    struct stat st;
    if (no_rename && !lstat(source, &st) && S_ISDIR(st.st_mode)) {
        errno = ENOSYS;
        return -1;
    }
    return __real_rename(source, destination);
}

FILE *__real_fopen(const char *, const char *);
FILE *__wrap_fopen(const char *path, const char *mode)
{
    if (!strncmp(path, TARGET "/", sizeof(TARGET)) && !strcmp(mode, "wbx")) {
        ++copy_attempts;
        if (copy_attempts == crash_at) _exit(42);
        if (copy_attempts == fail_at) {
            errno = EIO;
            return NULL;
        }
    }
    return __real_fopen(path, mode);
}

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

static HubInstallTransaction *stage_overlay(const HubGame *game, int *value)
{
    HubInstallTransaction *tx = hub_install_begin(".", game);
    char path[HUB_PATH_SIZE];
    assert(tx && hub_install_stage_file(tx, "data/game.dat", path));
    put(path, *value);
    assert(hub_install_stage_file(tx, "added.dat", path));
    put(path, 9);
    assert(hub_install_verify(tx, verify, value));
    return tx;
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

    no_rename = true;
    tx = stage_overlay(&game, &old);
    assert(hub_install_commit(tx) && verify(target, &old));
    assert(!access(TARGET "/added.dat", F_OK) && access(WORK, F_OK));
    assert(!unlink(TARGET "/added.dat"));
    put(TARGET "/original.dat", 7);

    tx = stage_overlay(&game, &new);
    copy_attempts = 0;
    fail_at = 2;
    assert(!hub_install_commit(tx) && copy_attempts == 2);
    assert(!access(WORK "/replacing", F_OK) && !access(WORK "/backup/original.dat", F_OK));
    assert(hub_install_rollback(tx) && verify(target, &old));
    assert(!access(TARGET "/original.dat", F_OK) && access(TARGET "/added.dat", F_OK));

    pid_t child = fork();
    assert(child >= 0);
    if (child == 0) {
        copy_attempts = fail_at = 0;
        crash_at = 2;
        tx = stage_overlay(&game, &new);
        hub_install_commit(tx);
        _exit(99);
    }
    int status;
    assert(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 42);
    assert(!access(WORK "/replacing", F_OK) && !access(WORK "/backup/original.dat", F_OK));
    copy_attempts = 0;
    assert(!hub_install_recover("."));
    assert(!access(WORK "/replacing", F_OK) && !access(WORK "/backup/original.dat", F_OK));
    fail_at = 0;
    assert(hub_install_recover(".") && verify(target, &old));
    assert(!access(TARGET "/original.dat", F_OK) && access(TARGET "/added.dat", F_OK) && access(WORK, F_OK));
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

    def test_controller_profile_inheritance_and_layout(self):
        hub = Path(__file__).resolve().parents[3] / "cmake/switch/hub"
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(
                ["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror", "-I", str(hub),
                 str(hub / "model.c"), str(hub / "controller_profile.c"),
                 "-x", "c", "-", "-o", str(Path(tmp) / "test")],
                input=r"""
#include "controller_profile.h"
#include <assert.h>

int main(void)
{
    HubGame system3, system4;
    assert(hub_game_init(&system3, HUB_SYSTEM3, "ランス"));
    assert(hub_game_init(&system4, HUB_XSYSTEM4, "ランス"));
    ControllerProfile profile;
    assert(controller_profile_load(".", &system3, &profile) == CP_DEFAULTS);
    assert(!profile.touch_enabled);
    assert(controller_profile_map(&profile, 1u << CP_A) == (1u << CP_A));
    assert(controller_profile_hit(&profile, .9f, .7f) == -1);

    profile.binding[CP_A] = CP_B;
    profile.binding[CP_B] = CP_B;
    profile.binding[CP_Y] = CP_BUTTONS;
    profile.touch_enabled = true;
    assert(controller_profile_save(".", NULL, &profile));
    assert(controller_profile_load(".", &system3, &profile) == CP_GLOBAL);
    assert(controller_profile_map(&profile, (1u << CP_A) | (1u << CP_B) | (1u << CP_Y)) == (1u << CP_B));
    assert(controller_profile_hit(&profile, .9f, .7f) == 4);
    assert(controller_profile_hit(&profile, 1.0f, .7f) == -1);

    assert(controller_profile_move(&profile, 4, profile.touch[5].column, profile.touch[5].row));
    assert(controller_profile_hit(&profile, .9f, .7f) == 5);
    assert(!controller_profile_move(&profile, 4, CP_COLUMNS, 0));
    profile.binding[CP_A] = CP_X;
    assert(controller_profile_save(".", &system3, &profile));
    assert(controller_profile_load(".", &system4, &profile) == CP_GLOBAL);
    assert(profile.binding[CP_A] == CP_B);
    assert(controller_profile_load(".", &system3, &profile) == CP_GAME);
    assert(profile.binding[CP_A] == CP_X && controller_profile_hit(&profile, .9f, .7f) == 5);

    FILE *bad = fopen("controller-profiles/system3/ランス/profile", "wb");
    assert(bad && fputs("alicehub-controller 1\n2\n", bad) >= 0 && !fclose(bad));
    assert(controller_profile_load(".", &system3, &profile) == CP_UNREADABLE);
    assert(profile.binding[CP_A] == CP_B);
    assert(controller_profile_reset(".", &system3));
    assert(controller_profile_load(".", &system3, &profile) == CP_GLOBAL);
    assert(!rename("controller-profile", "controller-profile.bak"));
    assert(controller_profile_load(".", &system3, &profile) == CP_GLOBAL);
    assert(controller_profile_reset(".", NULL));
    assert(controller_profile_load(".", &system3, &profile) == CP_DEFAULTS);
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

    def test_instance_copy_and_delete(self):
        hub = Path(__file__).resolve().parents[3] / "cmake/switch/hub"
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "xsystem4/games/Rance10"
            nested = source / "patches"
            nested.mkdir(parents=True)
            (nested / "data.bin").write_bytes(b"\0AliceHub\xff")
            original_saves = root / "xsystem4/saves/Rance10"
            original_saves.mkdir(parents=True)
            (original_saves / "slot.rsm").write_text("original")
            retained = original_saves.with_name("Rance10-1")
            retained.mkdir()
            (retained / "slot.rsm").write_text("retained")
            sibling = root / "system3/games/Rance10-2"
            sibling.mkdir(parents=True)
            (sibling / "data.bin").write_text("different engine")
            (source / "external").symlink_to(sibling, target_is_directory=True)
            stale = root / "xsystem4/.hub-copy-1"
            stale.mkdir()
            (stale / "partial.bin").write_text("interrupted copy")
            subprocess.run(
                ["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror", "-I", str(hub),
                 str(hub / "model.c"), str(hub / "instances.c"), str(hub / "fs_util.c"),
                 "-Wl,--wrap=rename",
                 "-x", "c", "-", "-o", str(root / "test")],
                input=r"""
#include "instances.h"
#include <assert.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>

static unsigned poll_count;
static bool force_fallback;
static unsigned rejected_renames;

int __real_rename(const char *source, const char *destination);

int __wrap_rename(const char *source, const char *destination)
{
    if (!force_fallback)
        return __real_rename(source, destination);
    ++rejected_renames;
    errno = ENOSYS;
    return -1;
}

static bool cancel_in_progress(void)
{
    return ++poll_count == 3;
}

int main(void)
{
    HubGame game, clone, other;
    char suggestion[HUB_NAME_SIZE], path[HUB_PATH_SIZE];
    assert(hub_game_init(&game, HUB_XSYSTEM4, "Rance10"));
    assert(hub_game_init(&clone, HUB_XSYSTEM4, "Rance10-2"));
    assert(hub_game_init(&other, HUB_SYSTEM3, "Rance10-2"));
    assert(hub_instance_suggest(".", &game, suggestion) && !strcmp(suggestion, clone.name));
    assert(hub_instance_copy(".", &game, "../escape", NULL) == HUB_INSTANCE_INVALID_NAME);
    assert(hub_instance_copy(".", &game, "wrong?", NULL) == HUB_INSTANCE_INVALID_NAME);
    assert(hub_instance_copy(".", &game, "rance10", NULL) == HUB_INSTANCE_NAME_TAKEN);
    assert(hub_instance_copy(".", &game, "rance10-1", NULL) == HUB_INSTANCE_NAME_TAKEN);
    assert(hub_instance_copy(".", &game, clone.name, NULL) == HUB_INSTANCE_COPY_FAILED);
    assert(hub_game_path(".", &clone, path) && access(path, F_OK));
    assert(!unlink("xsystem4/games/Rance10/external"));

    assert(hub_instance_copy(".", &game, clone.name, cancel_in_progress) == HUB_INSTANCE_COPY_FAILED);
    assert(poll_count >= 3 && access(path, F_OK));
    force_fallback = true;
    assert(hub_instance_copy(".", &game, clone.name, NULL) == HUB_INSTANCE_OK);
    assert(rejected_renames == 1);
    force_fallback = false;
    assert(!access(path, F_OK));
    FILE *copied = fopen("xsystem4/games/Rance10-2/patches/data.bin", "rb");
    unsigned char data[10];
    assert(copied && fread(data, 1, sizeof(data), copied) == sizeof(data));
    assert(data[0] == 0 && !memcmp(data + 1, "AliceHub", 8) && data[9] == 255 && !fclose(copied));
    assert(hub_game_save_path(".", &clone, path) && !access(path, F_OK));
    assert(access("xsystem4/saves/Rance10-2/slot.rsm", F_OK));
    assert(hub_instance_copy(".", &game, "Rance10-3", NULL) == HUB_INSTANCE_OK);

    HubLibrary library = {0};
    assert(hub_library_scan(&library, ".") && library.count == 4);
    assert(strcmp(clone.instance_id, game.instance_id));
    assert(strcmp(clone.instance_id, other.instance_id));
    hub_library_free(&library);

    FILE *save = fopen("xsystem4/saves/Rance10-2/slot.rsm", "wb");
    assert(save && fputs("copy save", save) >= 0 && !fclose(save));
    assert(!symlink("../../../system3/games/Rance10-2", "xsystem4/games/Rance10-2/external"));
    assert(hub_instance_delete(".", &clone, false) == HUB_INSTANCE_OK);
    assert(!access("xsystem4/saves/Rance10-2/slot.rsm", F_OK));
    assert(access("xsystem4/games/Rance10-2", F_OK));
    assert(hub_library_scan(&library, ".") && library.count == 3);
    hub_library_free(&library);
    assert(!access("system3/games/Rance10-2/data.bin", F_OK));
    assert(!access("xsystem4/games/Rance10-3/patches/data.bin", F_OK));
    assert(hub_instance_delete(".", &game, true) == HUB_INSTANCE_OK);
    assert(access("xsystem4/games/Rance10", F_OK));
    assert(access("xsystem4/saves/Rance10", F_OK));
    assert(!access("xsystem4/saves/Rance10-1/slot.rsm", F_OK));
    assert(!access("xsystem4/saves/Rance10-2/slot.rsm", F_OK));
}
""",
                text=True,
                check=True,
            )
            subprocess.run([str(root / "test")], cwd=root, check=True)
            self.assertEqual(list((root / "xsystem4").glob(".hub-copy-*")), [stale])
            self.assertEqual((stale / "partial.bin").read_text(), "interrupted copy")

    def test_save_transfers_and_pc_codec(self):
        repo = Path(__file__).resolve().parents[3]
        hub = repo / "cmake/switch/hub"
        engine = repo / "xsystem4"
        libsys4 = engine / "subprojects/libsys4"
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            exe = root / "test"
            subprocess.run(
                ["cc", "-std=gnu11", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections",
                 "-Datomic_save_open=hub_codec_atomic_save_open",
                 "-Datomic_save_commit=hub_codec_atomic_save_commit",
                 "-Datomic_save_abort=hub_codec_atomic_save_abort",
                 "-I", str(hub), "-I", str(engine / "include"), "-I", str(libsys4 / "include"),
                 *(str(hub / name) for name in ("model.c", "save_store.c", "save_codec.c")),
                 str(engine / "src/savedata.c"), str(libsys4 / "src/file.c"),
                 str(libsys4 / "src/system.c"), "-x", "c", "-", "-lz", "-o", str(exe)],
                input=r"""
#include "save_store.h"
#include <assert.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define BASE "save-manager/xsystem4/ランス"
#define LIVE "xsystem4/saves/ランス"

static void put(const char *path, const char *content)
{
    FILE *out = fopen(path, "wb");
    assert(out && fputs(content, out) >= 0 && !fclose(out));
}

static void expect(const char *path, const char *content)
{
    FILE *in = fopen(path, "rb");
    assert(in);
    char bytes[64];
    size_t n = fread(bytes, 1, sizeof(bytes), in);
    assert(n == strlen(content) && !memcmp(bytes, content, n) && !fclose(in));
}

int main(void)
{
    HubGame game;
    HubSaveResult result;
    atomic_bool cancelled = false;
    assert(hub_game_init(&game, HUB_XSYSTEM4, "ランス"));
    assert(hub_save_run(".", &game, HUB_SAVE_PREPARE, &cancelled, &result));
    assert(!mkdir(BASE "/Backups", 0700));
    assert(!mkdir(BASE "/Backups/.save-stage-0000", 0700));
    put(BASE "/Backups/.save-stage-0000/previous", "interrupted stage");
    assert(!mkdir(BASE "/Import/SaveData", 0700));
    put(BASE "/Import/SaveData/main.asd", "initial");
    put(BASE "/Import/SaveData/.hidden", "not imported");
    assert(hub_save_run(".", &game, HUB_SAVE_IMPORT, &cancelled, &result) && result.copied == 1);
    expect(LIVE "/main.asd", "initial");
    assert(access(LIVE "/.hidden", F_OK));

    put(LIVE "/.internal", "hidden data");
    assert(hub_save_run(".", &game, HUB_SAVE_BACKUP, &cancelled, &result) && result.copied == 2);
    expect(BASE "/Backups/.save-stage-0000/previous", "interrupted stage");
    assert(!unlink(BASE "/Backups/.save-stage-0000/previous"));
    assert(!rmdir(BASE "/Backups/.save-stage-0000"));
    put(BASE "/Import/SaveData/main.asd", "changed");
    put(BASE "/Import/SaveData/extra.asd", "extra");
    assert(hub_save_run(".", &game, HUB_SAVE_IMPORT, &cancelled, &result) && result.copied == 2);
    expect(LIVE "/main.asd", "changed");
    assert(hub_save_run(".", &game, HUB_SAVE_RESTORE, &cancelled, &result) && result.copied == 2);
    expect(LIVE "/main.asd", "initial");
    expect(LIVE "/.internal", "hidden data");
    assert(access(LIVE "/extra.asd", F_OK));

    put(BASE "/Sync/main.asd", "external");
    put(BASE "/Sync/external.asd", "added");
    put(LIVE "/local.asd", "local");
    assert(hub_save_run(".", &game, HUB_SAVE_SYNC, &cancelled, &result));
    assert(result.copied == 2 && result.conflicts == 1);
    expect(LIVE "/external.asd", "added");
    expect(LIVE "/main.asd", "initial");
    expect(BASE "/Sync/main.asd", "external");
    expect(BASE "/Sync/local.asd", "local");
    assert(hub_save_run(".", &game, HUB_SAVE_PULL, &cancelled, &result));
    expect(LIVE "/main.asd", "external");
    put(LIVE "/main.asd", "local edit");
    assert(hub_save_run(".", &game, HUB_SAVE_PUSH, &cancelled, &result));
    expect(BASE "/Sync/main.asd", "local edit");

    put(LIVE "/ignored.bak", "backup");
    assert(!rename("fixture.asd", LIVE "/struct.asd"));
    assert(hub_save_run(".", &game, HUB_SAVE_EXPORT, &cancelled, &result));
    expect(BASE "/SaveData/ignored.bak", "backup");
    assert(hub_save_run(".", &game, HUB_SAVE_EXPORT_PC, &cancelled, &result));
    assert(result.converted == 1);
    expect(BASE "/PC_SaveData/main.asd", "local edit");
    assert(access(BASE "/PC_SaveData/ignored.bak", F_OK));

    put(LIVE "/main.asd", "not exported");
    assert(!rename("broken.asd", LIVE "/broken.asd"));
    assert(!hub_save_run(".", &game, HUB_SAVE_EXPORT_PC, &cancelled, &result));
    assert(strstr(result.error, "Could not convert broken.asd"));
    expect(BASE "/PC_SaveData/main.asd", "local edit");
    assert(!symlink("main.asd", BASE "/Import/SaveData/link.asd"));
    assert(!hub_save_run(".", &game, HUB_SAVE_IMPORT, &cancelled, &result));
    expect(LIVE "/main.asd", "not exported");
    atomic_store(&cancelled, true);
    assert(!hub_save_run(".", &game, HUB_SAVE_RESTORE, &cancelled, &result));
    assert(!strcmp(result.error, "Cancelled."));
    expect(LIVE "/main.asd", "not exported");
}
""",
                text=True,
                check=True,
            )
            magic = b"X4SS\x01\0\0\0"
            comment = b"Save comment"
            raw = (struct.pack("<III", 1, 1, 1) + b"\x01\x04\0\0\0Hero" +
                   b"\x01\0\0\0\x02\0\0\0HP\x01" + struct.pack("<I", 42))
            compressed = zlib.compress(raw)
            (root / "fixture.asd").write_bytes(
                magic + struct.pack("<I", len(comment)) + comment +
                struct.pack("<II", len(raw), len(compressed)) + compressed
            )
            (root / "broken.asd").write_bytes(magic)
            subprocess.run([str(exe)], cwd=root, check=True)
            export_dir = root / "save-manager/xsystem4/ランス"
            pc = (export_dir / "PC_SaveData/struct.asd").read_bytes()
            self.assertEqual(pc[:4], b"GD\x01\x01")
            unpacked = zlib.decompress(pc[8:])
            self.assertEqual(len(unpacked), struct.unpack("<I", pc[4:8])[0])
            self.assertTrue(unpacked.startswith(b"serialize_struct\0"))
            self.assertTrue(unpacked.endswith(comment))
            self.assertIn(b"Hero\0", unpacked)
            self.assertEqual((export_dir / "SaveData/struct.asd").read_bytes()[:8], magic)
            self.assertFalse(any(root.rglob(".save-stage-*")))
