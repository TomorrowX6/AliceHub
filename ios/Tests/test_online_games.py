#!/usr/bin/env python3
"""Host regression fixture for the iOS online base-game installer."""
import hashlib
import http.server
import io
import json
import pathlib
import re
import subprocess
import tempfile
import threading
import time
import zipfile
from pe_icon_fixture import pe_icon_fixture

ROOT = pathlib.Path(__file__).resolve().parents[2]
ARCHIVE = pathlib.Path(subprocess.check_output(["brew", "--prefix", "libarchive"], text=True).strip())
buffer = io.BytesIO()
with zipfile.ZipFile(buffer, "w") as archive:
    archive.writestr("Manual/说明.txt", "verified archive")
    archive.writestr("overlap.txt", "overlaid")
PAYLOADS = {"game.ain": bytes(range(256)) * 4096, ".xsys35rc": b"hidden config", "overlap.txt": b"original", "manual.zip": buffer.getvalue(), "empty.txt": b""}
PAYLOADS["twin.ain"] = PAYLOADS["game.ain"]
MANIFEST = {
    "gameName": "测试游戏", "baseName": "FixtureGame",
    "files": [{"path": path, "key": "Fixture/" + path, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
               "extract": path.endswith(".zip")} for path, data in PAYLOADS.items()],
}
RANGES = []


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/manifest.json":
            data = json.dumps(MANIFEST).encode()
        else:
            name = self.path.removeprefix("/files/Fixture/")
            if name not in PAYLOADS:
                self.send_error(404)
                return
            data = PAYLOADS[name]
        range_hdr = self.headers.get("Range", "")
        if range_hdr.startswith("bytes="):
            spec = range_hdr[6:]
            start_s, end_s = spec.split("-", 1)
            start = int(start_s)
            end = int(end_s) if end_s else len(data) - 1
            partial = True
        else:
            start = 0
            end = len(data) - 1
            partial = False
        if self.path.endswith("game.ain"):
            RANGES.append(start)
        slice_data = data[start:end + 1] if len(data) > 0 else b""
        self.send_response(206 if partial else 200)
        self.send_header("ETag", '"fixture"')
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(len(slice_data)))
        if partial and len(data) > 0:
            self.send_header("Content-Range", f"bytes {start}-{end}/{len(data)}")
        self.end_headers()
        try:
            for offset in range(0, len(slice_data), 4096):
                self.wfile.write(slice_data[offset:offset + 4096])
                self.wfile.flush()
                time.sleep(0.001)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def log_message(self, *args):
        pass


# Every Android online base-game source must remain discoverable on iOS.
android = (ROOT / "project/app/src/main/java/io/github/kichikuou/xsystem4/OnlineInstaller.kt").read_text()
routes = set(re.findall(r'const val \w+_CDN_URL = "https://alicehub\.000\.moe/([^"/]+)"', android))
ios = (ROOT / "ios/App/OnlineGameCatalog.swift").read_text()
assert routes == set(re.findall(r'Self\(id: "([^"]+)"', ios))
android_root = ROOT / "project/app/src/main/java/io/github/kichikuou/xsystem4"
android_catalog = (android_root / "GameCatalog.kt").read_text()
android_ids = dict(re.findall(r'const val (ID_\w+) = "([^"]+)"', android_catalog))
android_order = re.findall(r'ID_\w+', android_catalog.split("private val BUILT_IN_SELECT_ORDER: List<String> = listOf(")[1].split(")")[0])
android_metadata = {
    key: (date, "nil" if score == "null" else score)
    for key, date, score in re.findall(r'GameCatalog\.(ID_\w+) to GameSortInfo\("([^"]+)", ([\d.]+|null)',
                                       (android_root / "GameSortMetadata.kt").read_text())
}
ios_library = (ROOT / "ios/App/LibraryEntries.swift").read_text()
assert re.findall(r'\("([^"]+)", "(\d{4}-\d{2}-\d{2})", ([\d.]+|nil)\)', ios_library) == [
    (android_ids[key], *android_metadata[key]) for key in android_order
]

with tempfile.TemporaryDirectory() as directory:
    work = pathlib.Path(directory)
    (work / "manifest.json").write_text(json.dumps(MANIFEST))
    (work / "game.ain").write_bytes(PAYLOADS["game.ain"])
    (work / "icon32.exe").write_bytes(pe_icon_fixture())
    (work / "icon64.exe").write_bytes(pe_icon_fixture(pe_plus=True))
    for source in ["ArchiveBridge", "ArchiveExtract"]:
        subprocess.run(["xcrun", "clang", "-I" + str(ARCHIVE / "include"), "-c", str(ROOT / f"ios/App/{source}.c"), "-o", str(work / f"{source}.o")], check=True)
    header = work / "Bridging.h"
    header.write_text('#include "' + str(ROOT / "ios/App/ArchiveBridge.h") + '"\n#include "' + str(ROOT / "ios/App/ArchiveExtract.h") + '"\n')
    sources = ["GameLibrary", "LocalGameImport", "LocalImportCleanup", "GamepadProfile", "GameDisplayScale", "HubLanguage", "ArchiveExtractor", "Iso9660Extractor", "ModArchiveExtractor", "ModCatalog", "ModOverlay", "ModInstaller", "ModManagerModel", "OnlineGameCatalog", "OnlineGameInstaller", "LibraryEntries", "GameExecutableIcon", "BackgroundActivity"]
    subprocess.run(["xcrun", "swiftc", "-swift-version", "5", "-import-objc-header", str(header),
                    *[str(ROOT / f"ios/App/{source}.swift") for source in sources], str(ROOT / "ios/Tests/OnlineGameChecks.swift"),
                    str(work / "ArchiveBridge.o"), str(work / "ArchiveExtract.o"), "-L" + str(ARCHIVE / "lib"), "-larchive", "-liconv", "-o", str(work / "checks")], check=True)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        subprocess.run([str(work / "checks"), str(work), f"http://127.0.0.1:{server.server_port}/"], check=True)
        assert RANGES[0] == 64 and len(RANGES) == 4 and RANGES[1] == 0 and RANGES[2] > 0 and RANGES[3] == 0, RANGES
        print("PASS: Android catalog parity and HTTP Range continuation after cancellation")
        print("PASS: iOS library ordering, installation grouping and PE artwork")
    finally:
        server.shutdown()
