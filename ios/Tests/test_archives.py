#!/usr/bin/env python3
"""Host Swift/C regression check (brew install libarchive)."""
import pathlib
import io
import struct
import subprocess
import tempfile
import tarfile
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ARCHIVE = pathlib.Path(subprocess.check_output(["brew", "--prefix", "libarchive"], text=True).strip())


def zip_bytes(entries):
    local = bytearray()
    central = bytearray()
    for name, data in entries:
        crc = zlib.crc32(data)
        central += struct.pack("<4s6H3I5H2I", b"PK\x01\x02", 20, 20, 0, 0, 0, 0,
                               crc, len(data), len(data), len(name), 0, 0, 0, 0, 0, len(local)) + name
        local += struct.pack("<4s5H3I2H", b"PK\x03\x04", 20, 0, 0, 0, 0,
                             crc, len(data), len(data), len(name), 0) + name + data
    return local + central + struct.pack("<4s4H2IH", b"PK\x05\x06", 0, 0, len(entries), len(entries), len(central), len(local), 0)


with tempfile.TemporaryDirectory() as directory:
    work = pathlib.Path(directory)
    fixtures = {
        "names": [("中文游戏.txt".encode("gbk"), b"a"), ("ソフト.txt".encode("shift_jis"), b"b"),
                  (b".xsys35rc", b"c"), (b"empty.txt", b""), (b"SaveData/a", b"x"),
                  (b"custom.txt", b"x"), (b"__MACOSX/a", b"x"), (b"x.dll", b"x")],
        "empty": [], "traversal": [(b"../escape", b"x")],
        "absolute": [(b"/escape", b"x")], "drive": [(b"C:\\escape", b"x")],
        "symlink": [(b"link/escape", b"x")],
    }
    for name, entries in fixtures.items():
        (work / (name + ".zip")).write_bytes(zip_bytes(entries))
    (work / "corrupt.zip").write_bytes(b"not an archive")
    for valid in [True, False]:
        with tarfile.open(work / ("links.tar" if valid else "bad-links.tar"), "w") as archive:
            entry = tarfile.TarInfo("source.txt")
            entry.size = 4
            archive.addfile(entry, io.BytesIO(b"data"))
            entry = tarfile.TarInfo("copy.txt")
            entry.type = tarfile.LNKTYPE
            entry.linkname = "source.txt" if valid else "../outside"
            archive.addfile(entry)
    subprocess.run(["xcrun", "clang", "-I" + str(ARCHIVE / "include"), "-c", str(ROOT / "ios/App/ArchiveBridge.c"),
                    "-o", str(work / "bridge.o")], check=True)
    subprocess.run(["xcrun", "swiftc", "-import-objc-header", str(ROOT / "ios/App/ArchiveBridge.h"),
                    str(ROOT / "ios/App/ArchiveExtractor.swift"), str(ROOT / "ios/Tests/ArchiveChecks.swift"),
                    str(work / "bridge.o"), "-L" + str(ARCHIVE / "lib"), "-larchive", "-liconv", "-o", str(work / "checks")], check=True)
    subprocess.run([str(work / "checks"), str(work)], check=True)
