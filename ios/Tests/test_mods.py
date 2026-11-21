#!/usr/bin/env python3
"""Compile and exercise the MOD layer on macOS (brew install libarchive)."""
import http.server
import io
import pathlib
import subprocess
import tempfile
import threading
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
ARCHIVE = pathlib.Path(subprocess.check_output(["brew", "--prefix", "libarchive"], text=True).strip())
archive = io.BytesIO()
with zipfile.ZipFile(archive, "w") as output:
    output.writestr("Rance10.ain", b"online mod")
DATA = archive.getvalue()
RANGES = []
RETRY = []


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        start = int(self.headers.get("Range", "bytes=0-")[6:-1])
        RANGES.append(start)
        payload = DATA
        if "?retry" in self.path:
            RETRY.append(start)
            if len(RETRY) == 1:
                payload = bytes([DATA[0] ^ 1]) + DATA[1:]
        self.send_response(206 if start else 200)
        self.send_header("ETag", '"fixture"')
        self.send_header("Content-Length", str(len(DATA) - start))
        if start:
            self.send_header("Content-Range", f"bytes {start}-{len(DATA)-1}/{len(DATA)}")
        self.end_headers()
        self.wfile.write(payload[start:])

    def log_message(self, *args):
        pass


with tempfile.TemporaryDirectory() as temporary:
    work = pathlib.Path(temporary)
    (work / "online.zip").write_bytes(DATA)
    subprocess.run(["xcrun", "clang", "-I" + str(ARCHIVE / "include"), "-c", str(ROOT / "ios/App/ArchiveBridge.c"), "-o", str(work / "bridge.o")], check=True)
    sources = ["ios/App/ModArchiveExtractor.swift", "ios/App/ModCatalog.swift", "ios/App/ModOverlay.swift", "ios/App/ModInstaller.swift", "ios/App/ModManagerModel.swift", "ios/Tests/ModChecks.swift"]
    subprocess.run(["xcrun", "swiftc", "-swift-version", "5", "-import-objc-header", str(ROOT / "ios/App/ArchiveBridge.h"),
                    *[str(ROOT / source) for source in sources], str(work / "bridge.o"), "-L" + str(ARCHIVE / "lib"), "-larchive", "-liconv", "-o", str(work / "checks")], check=True)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        subprocess.run([str(work / "checks"), str(work), f"http://127.0.0.1:{server.server_port}/online.zip"], check=True)
        assert RANGES == [32, 0, 0], RANGES
        assert RETRY == [0, 0], RETRY
        print("PASS: HTTP fixture received Range bytes=32-; corrupt response retried cleanly")
    finally:
        server.shutdown()
