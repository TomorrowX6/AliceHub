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
        start = int(self.headers.get("Range", "bytes=0-")[6:-1])
        if self.path.endswith("game.ain"):
            RANGES.append(start)
        self.send_response(206 if start else 200)
        self.send_header("ETag", '"fixture"')
        self.send_header("Content-Length", str(len(data) - start))
        if start:
            self.send_header("Content-Range", f"bytes {start}-{len(data)-1}/{len(data)}")
        self.end_headers()
        try:
            for offset in range(start, len(data), 4096):
                self.wfile.write(data[offset:offset + 4096])
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

with tempfile.TemporaryDirectory() as directory:
    work = pathlib.Path(directory)
    (work / "manifest.json").write_text(json.dumps(MANIFEST))
    (work / "game.ain").write_bytes(PAYLOADS["game.ain"])
    for source in ["ArchiveBridge", "ArchiveExtract"]:
        subprocess.run(["xcrun", "clang", "-I" + str(ARCHIVE / "include"), "-c", str(ROOT / f"ios/App/{source}.c"), "-o", str(work / f"{source}.o")], check=True)
    header = work / "Bridging.h"
    header.write_text('#include "' + str(ROOT / "ios/App/ArchiveBridge.h") + '"\n#include "' + str(ROOT / "ios/App/ArchiveExtract.h") + '"\n')
    sources = ["GameLibrary", "GamepadProfile", "ArchiveExtractor", "Iso9660Extractor", "ModArchiveExtractor", "ModCatalog", "ModOverlay", "ModInstaller", "ModManagerModel", "OnlineGameCatalog", "OnlineGameInstaller"]
    subprocess.run(["xcrun", "swiftc", "-swift-version", "5", "-import-objc-header", str(header),
                    *[str(ROOT / f"ios/App/{source}.swift") for source in sources], str(ROOT / "ios/Tests/OnlineGameChecks.swift"),
                    str(work / "ArchiveBridge.o"), str(work / "ArchiveExtract.o"), "-L" + str(ARCHIVE / "lib"), "-larchive", "-liconv", "-o", str(work / "checks")], check=True)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        subprocess.run([str(work / "checks"), str(work), f"http://127.0.0.1:{server.server_port}/"], check=True)
        assert RANGES[0] == 64 and len(RANGES) == 3 and RANGES[-1] > 0, RANGES
        print("PASS: Android catalog parity and HTTP Range continuation after cancellation")
    finally:
        server.shutdown()
