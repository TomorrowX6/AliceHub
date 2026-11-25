"""Exercise the hub's local import without libnx or commercial game data."""
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
import zipfile


def iso9660(files, *, raw=False, joliet=False):
    def both(number, size):
        return number.to_bytes(size, "little") + number.to_bytes(size, "big")

    def record(name, block, length, flags=0):
        result = bytearray(33 + len(name) + (1 - len(name) % 2))
        result[0] = len(result)
        result[2:10] = both(block, 4)
        result[10:18] = both(length, 4)
        result[25] = flags
        result[28:32] = both(1, 2)
        result[32] = len(name)
        result[33:33 + len(name)] = name
        return result

    sectors = [bytearray(2048) for _ in range(24 + len(files))]
    root = record(b"\0", 20, 2048, 2)
    for i, kind in enumerate([1, 2, 255] if joliet else [1, 255], 16):
        descriptor = sectors[i]
        descriptor[:7] = bytes([kind]) + b"CD001\1"
        descriptor[128:132] = both(2048, 2)
        descriptor[156:190] = root
        if kind == 2:
            descriptor[88:91] = b"%/E"
    directory = root + record(b"\1", 20, 2048, 2)
    for block, (name, contents) in enumerate(files.items(), 24):
        identifier = (name + ";1").encode("utf-16-be" if joliet else "ascii")
        directory += record(identifier, block, len(contents))
        sectors[block][:len(contents)] = contents
    sectors[20][:len(directory)] = directory
    if raw:
        return b"".join(bytes(16) + sector + bytes(2352 - 16 - 2048) for sector in sectors)
    return b"".join(sectors)


class SwitchImportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.tmp.cleanup)
        cls.runner = Path(cls.tmp.name) / "import"
        hub = Path(__file__).resolve().parents[3] / "cmake/switch/hub"
        zlib = ["-lz"]
        devkitpro = os.environ.get("DEVKITPRO")
        if devkitpro and not Path("/usr/include/zlib.h").exists():
            headers = Path(devkitpro) / "portlibs/switch/include"
            zlib = ["-idirafter", str(headers), "-l:libz.so.1"]
        subprocess.run(
            ["cc", "-std=gnu11", "-Wall", "-Wextra", "-Werror", "-I", str(hub),
             *(str(hub / source) for source in
               ("model.c", "install_transaction.c", "local_import.c", "import_zip.c", "import_iso.c")),
             "-x", "c", "-", *zlib, "-o", str(cls.runner)],
            input=r"""
#include "local_import.h"
#include <string.h>

static bool update(void *data, const char *detail, uint64_t current, uint64_t total)
{
    (void)total;
    return !data || !current || strcmp(detail, "Demo.ain");
}

int main(int argc, char **argv)
{
    ImportCallbacks cb = {.data = argc > 3 ? argv[3] : NULL, .update = update};
    HubGame game;
    char error[160];
    if (hub_local_import(argv[1], argv[2], &cb, &game, error)) {
        puts(game.instance_id);
        return 0;
    }
    puts(error);
    return 1;
}
""", text=True, check=True,
        )

    def setUp(self):
        tmp = tempfile.TemporaryDirectory(dir=self.tmp.name)
        self.addCleanup(tmp.cleanup)
        self.sd = Path(tmp.name)
        self.library = self.sd / "library"
        self.library.mkdir()

    def run_import(self, source, *, result=0, cancel=False):
        args = [self.runner, self.library, source]
        if cancel:
            args.append("cancel")
        run = subprocess.run(args, text=True, capture_output=True)
        self.assertEqual(run.returncode, result, run.stdout + run.stderr)
        self.assertFalse((self.library / ".local-import-v1").exists())
        return run.stdout.strip()

    def test_folder_detection_pruning_cancel_and_duplicate(self):
        source = self.sd / "Demo"
        source.mkdir()
        (source / "System40.ini").write_text('CodeName = "Demo.ain"\n')
        (source / "Demo.ain").write_bytes(b"stub")
        (source / "uninstaller.exe").touch()
        (source / "test.DLL").touch()
        (source / ".xsys35rc").write_text("keep")
        (source / "SaveData").mkdir()
        (source / "SaveData/OldSave").touch()
        (source / "link").symlink_to(source / "Demo.ain")
        self.run_import(source, result=1, cancel=True)
        self.assertFalse((self.library / "xsystem4/games/Demo").exists())
        self.assertEqual(self.run_import(source), "local:xsystem4:Demo")
        installed = self.library / "xsystem4/games/Demo"
        self.assertEqual({p.name for p in installed.iterdir()}, {"System40.ini", "Demo.ain", ".xsys35rc"})
        (source / "Demo.ain").write_bytes(b"replacement")
        self.assertIn("already installed", self.run_import(source, result=1))
        self.assertEqual((installed / "Demo.ain").read_bytes(), b"stub")
        self.assertTrue((source / "SaveData/OldSave").exists())

    def test_configured_chs_and_legacy_data(self):
        source = self.sd / "CHS"
        source.mkdir()
        (source / "System40.ini").write_text('CodeName = "data\\中文.chs"\n')
        (source / "中文.chs").write_bytes(b"stub")
        self.assertEqual(self.run_import(source), "local:xsystem4:CHS")
        legacy = self.sd / "Legacy"
        legacy.mkdir()
        (legacy / "ADISK.DAT").write_bytes(b"\1\0\2\0" + bytes(300))
        self.run_import(legacy, result=1)
        (legacy / "ACG0.DAT").write_bytes(b"data")
        self.assertEqual(self.run_import(legacy), "local:system3:Legacy")

    def test_zip_wrapped_game_descriptors_and_zip64(self):
        class StreamingZip:
            def __init__(self, file):
                self.file = file

            def write(self, data):
                return self.file.write(data)

            def flush(self):
                return self.file.flush()

        source = self.sd / "wrapper.zip"
        payload = bytes(range(256)) * 530
        with source.open("wb") as file, zipfile.ZipFile(StreamingZip(file), "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("日本語/System40.ini", "INI")
            with archive.open("日本語/Game.ain", "w", force_zip64=True) as entry:
                entry.write(payload)
            archive.writestr("日本語/DirectX/test", "pruned")
        self.assertEqual(self.run_import(source), "local:xsystem4:日本語")
        installed = self.library / "xsystem4/games/日本語"
        self.assertEqual((installed / "Game.ain").read_bytes(), payload)
        self.assertFalse((installed / "DirectX").exists())

    def test_malformed_zip_is_not_published(self):
        source = self.sd / "bad.zip"
        for bad in ["/absolute", "Game/../escape", r"Game\..\escape", "sdmc:/new", "Game/link"]:
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("Game/System40.ini", "INI")
                archive.writestr("Game/Test.ain", "code")
                if bad.endswith("link"):
                    entry = zipfile.ZipInfo(bad)
                    entry.create_system = 3
                    entry.external_attr = (stat.S_IFLNK | 0o777) << 16
                    archive.writestr(entry, "/")
                else:
                    archive.writestr(bad, "invalid")
            self.run_import(source, result=1)
        with zipfile.ZipFile(source, "w") as archive:
            archive.writestr("System40.ini", "CONFIG")
            archive.writestr("Test.ain", "CODE")
        source.write_bytes(source.read_bytes().replace(b"CODE", b"DOCE", 1))
        self.run_import(source, result=1)
        self.assertFalse((self.library / "xsystem4/games/Game").exists())
        self.assertFalse((self.library / "xsystem4/games/bad").exists())

    def test_iso_and_numbered_images_merge_only_companion_assets(self):
        source = self.sd / "Standalone.iso"
        source.write_bytes(iso9660({"System40.ini": b'CodeName="遊戯.chs"', "遊戯.chs": b"code"}, joliet=True))
        self.assertEqual(self.run_import(source), "local:xsystem4:Standalone")
        images = self.sd / "images"
        images.mkdir()
        first = iso9660({"System40.ini": b"INI", "Rance.ain": b"code", "RanceCG.afa": b"primary"})
        second = iso9660({"rancecg.AFA": b"duplicate", "RanceSound.afa": b"companion", "Other.afa": b"skip"}, raw=True)
        (images / "Rance CD 1.iso").write_bytes(first)
        (images / "Rance CD 2.mdf").write_bytes(second)
        self.assertEqual(self.run_import(images), "local:xsystem4:Rance")
        installed = self.library / "xsystem4/games/Rance"
        self.assertEqual((installed / "RanceCG.afa").read_bytes(), b"primary")
        self.assertEqual((installed / "RanceSound.afa").read_bytes(), b"companion")
        self.assertFalse((installed / "Other.afa").exists())
        malformed = self.sd / "short.iso"
        malformed.write_bytes(first[:-2048])
        self.run_import(malformed, result=1)

    def test_system35_disc_folders_in_zip(self):
        source = self.sd / "Game Set.zip"
        with zipfile.ZipFile(source, "w") as archive:
            archive.writestr("Disc1/GAMEDATA/RanceSA.ALD", "scenario")
            archive.writestr("Disc2/GAMEDATA/RanceSB.ALD", "companion")
            archive.writestr("Disc3/GAMEDATA/OtherSA.ALD", "unrelated")
            archive.writestr("Disc3/GAMEDATA/OtherSB.ALD", "must not merge")
        self.assertEqual(self.run_import(source), "local:xsystem35:Game Set")
        installed = self.library / "xsystem35/games/Game Set"
        self.assertEqual({p.name for p in installed.iterdir()}, {"RanceSA.ALD", "RanceSB.ALD"})
