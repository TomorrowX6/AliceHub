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
SEG_DATA = bytes((i * 37 + 13) & 0xFF for i in range(256 * 1024))
RANGES = []
RETRY = []
SEG_INFLIGHT = 0
SEG_PEAK = 0
SEG_RANGES = []
SEG_DROP_SEEN = 0
SEG_DROP_RANGES = []
SEG_LOCK = threading.Lock()


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        global SEG_INFLIGHT, SEG_PEAK, SEG_DROP_SEEN
        if self.path.startswith("/redirect"):
            self.send_response(302)
            self.send_header("Location", "/online.zip")
            self.end_headers()
            return
        if self.path.startswith("/segmented-drop.bin"):
            range_hdr = self.headers.get("Range", "")
            with SEG_LOCK:
                SEG_DROP_SEEN += 1
                attempt = SEG_DROP_SEEN
                SEG_DROP_RANGES.append(range_hdr)
            if attempt == 1:
                # Simulate pre-header TCP disconnect: close socket without writing headers!
                self.close_connection = True
                return
            s_str, e_str = range_hdr[6:].split("-", 1)
            start = int(s_str)
            end = int(e_str) if e_str else len(SEG_DATA) - 1
            chunk = SEG_DATA[start:end + 1]
            self.send_response(206)
            self.send_header("ETag", '"seg-fixture"')
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Range", f"bytes {start}-{end}/{len(SEG_DATA)}")
            self.send_header("Content-Length", str(len(chunk)))
            self.end_headers()
            self.wfile.write(chunk)
            return
        if self.path.startswith("/segmented.bin") or self.path.startswith("/segmented-200.bin"):
            is_no_range = self.path.startswith("/segmented-200.bin")
            range_hdr = self.headers.get("Range", "")
            with SEG_LOCK:
                SEG_INFLIGHT += 1
                if SEG_INFLIGHT > SEG_PEAK:
                    SEG_PEAK = SEG_INFLIGHT
            try:
                if not is_no_range and range_hdr.startswith("bytes="):
                    s_str, e_str = range_hdr[6:].split("-", 1)
                    start = int(s_str)
                    end = int(e_str) if e_str else len(SEG_DATA) - 1
                    with SEG_LOCK:
                        SEG_RANGES.append((start, end))
                    # Brief delay so concurrent segments overlap in-flight
                    import time
                    time.sleep(0.05)
                    chunk = SEG_DATA[start:end + 1]
                    self.send_response(206)
                    self.send_header("ETag", '"seg-fixture"')
                    self.send_header("Accept-Ranges", "bytes")
                    self.send_header("Content-Range", f"bytes {start}-{end}/{len(SEG_DATA)}")
                    self.send_header("Content-Length", str(len(chunk)))
                    self.end_headers()
                    self.wfile.write(chunk)
                else:
                    self.send_response(200)
                    self.send_header("ETag", '"seg-fixture"')
                    self.send_header("Content-Length", str(len(SEG_DATA)))
                    self.end_headers()
                    self.wfile.write(SEG_DATA)
            finally:
                with SEG_LOCK:
                    SEG_INFLIGHT -= 1
            return
        start = int(self.headers.get("Range", "bytes=0-")[6:-1])
        if_range = self.headers.get("If-Range")
        if if_range and if_range != '"fixture"':
            start = 0
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
    (work / "segmented.bin").write_bytes(SEG_DATA)
    subprocess.run(["xcrun", "clang", "-I" + str(ARCHIVE / "include"), "-c", str(ROOT / "ios/App/ArchiveBridge.c"), "-o", str(work / "bridge.o")], check=True)
    sources = ["ios/App/HubLanguage.swift", "ios/App/ModArchiveExtractor.swift", "ios/App/ModCatalog.swift", "ios/App/ModOverlay.swift", "ios/App/ModInstaller.swift", "ios/App/BackgroundActivity.swift", "ios/App/ModManagerModel.swift", "ios/Tests/ModChecks.swift"]
    subprocess.run(["xcrun", "swiftc", "-swift-version", "5", "-import-objc-header", str(ROOT / "ios/App/ArchiveBridge.h"),
                    *[str(ROOT / source) for source in sources], str(work / "bridge.o"), "-L" + str(ARCHIVE / "lib"), "-larchive", "-liconv", "-o", str(work / "checks")], check=True)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        subprocess.run([str(work / "checks"), str(work), f"http://127.0.0.1:{server.server_port}/online.zip"], check=True)
        assert RANGES == [32, 16, 0, 0], RANGES
        assert RETRY == [0, 0], RETRY
        assert SEG_PEAK >= 4, f"Expected >=4 concurrent segments, got peak={SEG_PEAK}"
        assert len(SEG_RANGES) >= 8, f"Expected >=8 range segments, got {len(SEG_RANGES)}"
        half = len(SEG_DATA) // 2
        expected_drop_range = f"bytes={half}-{len(SEG_DATA) - 1}"
        assert SEG_DROP_RANGES == [expected_drop_range, expected_drop_range], f"Pre-header disconnect should retry range without restarting from 0: {SEG_DROP_RANGES}"
        print(f"PASS: HTTP fixture received Range bytes=32-, 302 redirect preserved Range bytes=16-; segmented download peak={SEG_PEAK} ranges={len(SEG_RANGES)} drop_retries={SEG_DROP_RANGES}")
    finally:
        server.shutdown()
