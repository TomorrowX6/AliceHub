#!/usr/bin/env python3
"""
Upload a staged self-hosted store directory (built by scripts/build_stores.py)
to a Cloudflare R2 bucket via the Cloudflare REST API.

Authentication (via environment variables):
  - CF_ACCOUNT_ID (required)
  - CF_API_TOKEN (preferred, R2 Object Write permission)
    OR CF_AUTH_EMAIL + CF_AUTH_KEY (Global API Key)
"""

from __future__ import annotations

import argparse
import http.client
import json
import os
import sys
import time
import urllib.parse
from pathlib import Path


CONTENT_TYPES = {
    ".json": "application/json; charset=utf-8",
    ".html": "text/html; charset=utf-8",
    ".jar": "application/java-archive",
    ".apk": "application/vnd.android.package-archive",
    ".ipa": "application/octet-stream",
    ".zip": "application/zip",
    ".png": "image/png",
    ".webp": "image/webp",
    ".install": "text/plain; charset=utf-8",
    ".txt": "text/plain; charset=utf-8",
}

SHORT_CACHE_EXTS = {".json", ".jar", ".html", ".install"}


def content_type_for(path: Path) -> str:
    return CONTENT_TYPES.get(path.suffix.lower(), "application/octet-stream")


def cache_control_for(path: Path) -> str:
    if path.suffix.lower() in SHORT_CACHE_EXTS:
        return "public, max-age=60, must-revalidate"
    return "public, max-age=86400"


def auth_headers() -> dict[str, str]:
    token = os.environ.get("CF_API_TOKEN", "").strip()
    if token:
        return {"Authorization": f"Bearer {token}"}
    email = os.environ.get("CF_AUTH_EMAIL", "").strip()
    key = os.environ.get("CF_AUTH_KEY", "").strip()
    if email and key:
        return {"X-Auth-Email": email, "X-Auth-Key": key}
    raise SystemExit(
        "Missing Cloudflare credentials: set CF_API_TOKEN or (CF_AUTH_EMAIL + CF_AUTH_KEY)."
    )


def upload_object(
    account_id: str,
    bucket: str,
    key: str,
    file_path: Path,
    base_headers: dict[str, str],
    retries: int = 3,
) -> None:
    size = file_path.stat().st_size
    encoded_key = urllib.parse.quote(key, safe="/")
    api_path = f"/client/v4/accounts/{account_id}/r2/buckets/{bucket}/objects/{encoded_key}"
    headers = {
        **base_headers,
        "Content-Type": content_type_for(file_path),
        "Cache-Control": cache_control_for(file_path),
        "Content-Length": str(size),
    }

    last_err = None
    for attempt in range(1, retries + 1):
        conn = http.client.HTTPSConnection("api.cloudflare.com", timeout=300)
        try:
            conn.putrequest("PUT", api_path)
            for hk, hv in headers.items():
                conn.putheader(hk, hv)
            conn.endheaders()
            with file_path.open("rb") as f:
                while True:
                    chunk = f.read(1024 * 1024)
                    if not chunk:
                        break
                    conn.send(chunk)
            resp = conn.getresponse()
            body = resp.read().decode("utf-8", errors="replace")
            if 200 <= resp.status < 300:
                try:
                    parsed = json.loads(body)
                    if parsed.get("success", True):
                        return
                except json.JSONDecodeError:
                    return
            last_err = f"HTTP {resp.status}: {body[:300]}"
        except Exception as exc:
            last_err = repr(exc)
        finally:
            conn.close()
        if attempt < retries:
            time.sleep(2 * attempt)

    raise RuntimeError(f"Failed to upload {key} ({size} bytes): {last_err}")


def main() -> int:
    ap = argparse.ArgumentParser(description="Upload staged store directory to Cloudflare R2")
    ap.add_argument("--dir", required=True, type=Path, help="Staged store directory")
    ap.add_argument("--bucket", default=os.environ.get("CF_R2_BUCKET", "alicehub-store"))
    ap.add_argument("--account-id", default=os.environ.get("CF_ACCOUNT_ID", ""))
    ap.add_argument("--skip-summary", action="store_true", help="Skip uploading build-summary.json")
    args = ap.parse_args()

    if not args.account_id:
        raise SystemExit("Missing --account-id / CF_ACCOUNT_ID")
    if not args.dir.is_dir():
        raise SystemExit(f"Not a directory: {args.dir}")

    headers = auth_headers()
    files = sorted(p for p in args.dir.rglob("*") if p.is_file())
    # Upload payloads (APK/IPA/ZIP/icons) BEFORE indexes (.json/.jar) so clients never see a dangling index!
    payloads = [p for p in files if p.suffix.lower() not in SHORT_CACHE_EXTS]
    indexes = [p for p in files if p.suffix.lower() in SHORT_CACHE_EXTS]
    ordered = payloads + indexes

    uploaded = 0
    for fpath in ordered:
        rel_key = fpath.relative_to(args.dir).as_posix()
        if args.skip_summary and rel_key == "build-summary.json":
            continue
        t0 = time.time()
        upload_object(args.account_id, args.bucket, rel_key, fpath, headers)
        dt = time.time() - t0
        mb = fpath.stat().st_size / (1024 * 1024)
        print(f"OK  {rel_key:<54} {mb:6.2f} MiB  ({dt:.1f}s)", flush=True)
        uploaded += 1

    print(f"Uploaded {uploaded} objects to R2 bucket '{args.bucket}'.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
