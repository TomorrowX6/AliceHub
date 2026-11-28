#!/usr/bin/env python3
"""
Build self-hosted F-Droid (Android), SideStore/AltStore (iOS), and HBAS (Nintendo Switch)
repositories for AliceHub from GitHub Release artifacts.

All binary payloads are staged into --out (never committed to git) so they can be uploaded
directly to Cloudflare R2 (e.g. https://store.000.moe).
"""

from __future__ import annotations

import argparse
import base64
import datetime
import hashlib
import json
import os
import plistlib
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BASE_URL = "https://store.000.moe"
DEFAULT_GITHUB_REPO = "TomorrowX6/AliceHub"
DEFAULT_WEBSITE = "https://alice.000.moe"
DEFAULT_PACKAGE_ID = "io.github.alicehub"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def find_aapt2() -> str | None:
    found = shutil.which("aapt2")
    if found:
        return found
    for root in (
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        "/root/.cache/android-sdk",
        "/usr/local/lib/android/sdk",
    ):
        if not root:
            continue
        p = Path(root) / "build-tools"
        if p.is_dir():
            candidates = sorted(p.glob("*/aapt2"), reverse=True)
            if candidates:
                return str(candidates[0])
    return None


def inspect_apk(apk_path: Path) -> dict[str, Any]:
    aapt2 = find_aapt2()
    if not aapt2:
        raise RuntimeError("aapt2 not found in PATH or ANDROID_HOME")
    badging = subprocess.check_output([aapt2, "dump", "badging", str(apk_path)], text=True)

    pkg_m = re.search(
        r"package:\s+name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']+)'",
        badging,
    )
    if not pkg_m:
        raise RuntimeError(f"Could not parse package header from aapt2 output for {apk_path}")

    min_sdk_m = re.search(r"sdkVersion:'(\d+)'", badging)
    target_sdk_m = re.search(r"targetSdkVersion:'(\d+)'", badging)
    native_m = re.search(r"native-code:\s*(.+)", badging)

    permissions: list[dict[str, Any]] = []
    for line in badging.splitlines():
        if line.startswith("uses-permission:"):
            nm = re.search(r"name='([^']+)'", line)
            ms = re.search(r"maxSdkVersion='(\d+)'", line)
            if nm:
                entry: dict[str, Any] = {"name": nm.group(1)}
                if ms:
                    entry["maxSdkVersion"] = int(ms.group(1))
                permissions.append(entry)

    features: list[dict[str, str]] = []
    for line in badging.splitlines():
        if line.startswith("  uses-feature:"):
            fm = re.search(r"name='([^']+)'", line)
            if fm:
                features.append({"name": fm.group(1)})

    nativecode = re.findall(r"'([^']+)'", native_m.group(1)) if native_m else ["arm64-v8a"]

    # Extract APK signing certificate SHA-256 & MD5(hex-encoded DER) for F-Droid v2/v1
    apksigner = shutil.which("apksigner")
    if not apksigner:
        raise RuntimeError("apksigner not found in PATH")
    cert_out = subprocess.check_output(
        [apksigner, "verify", "--print-certs", "--print-certs-pem", str(apk_path)],
        text=True,
    )
    sha256_m = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", cert_out)
    ders: list[bytes] = [
        base64.b64decode(re.sub(r"\s+", "", m.group(1)))
        for m in re.finditer(
            r"-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----",
            cert_out,
            re.S,
        )
    ]
    if not ders:
        raise RuntimeError(f"Could not extract PEM certificate from apksigner output for {apk_path}")

    if sha256_m:
        signer_sha256 = sha256_m.group(1).lower()
        signer_der = next((d for d in ders if hashlib.sha256(d).hexdigest().lower() == signer_sha256), None)
        if signer_der is None:
            raise RuntimeError(f"Could not match DER certificate to digest {signer_sha256}")
    else:
        signer_der = ders[0]
        signer_sha256 = hashlib.sha256(signer_der).hexdigest().lower()

    # Legacy F-Droid v1 `sig` is MD5 of the lowercase hex-encoded DER signing certificate.
    sig_v1 = hashlib.md5(signer_der.hex().encode("ascii")).hexdigest()

    return {
        "packageName": pkg_m.group(1),
        "versionCode": int(pkg_m.group(2)),
        "versionName": pkg_m.group(3),
        "minSdkVersion": int(min_sdk_m.group(1)) if min_sdk_m else 26,
        "targetSdkVersion": int(target_sdk_m.group(1)) if target_sdk_m else 34,
        "nativecode": nativecode,
        "permissions": permissions,
        "features": features,
        "signerSha256": signer_sha256,
        "sigV1": sig_v1,
        "size": apk_path.stat().st_size,
        "sha256": sha256_file(apk_path),
    }


def inspect_ipa(ipa_path: Path) -> dict[str, Any]:
    with zipfile.ZipFile(ipa_path) as zf:
        plist_entry = next(
            (n for n in zf.namelist() if re.match(r"^Payload/[^/]+\.app/Info\.plist$", n)),
            None,
        )
        if not plist_entry:
            raise RuntimeError(f"Could not locate Payload/*.app/Info.plist inside {ipa_path}")
        pl = plistlib.loads(zf.read(plist_entry))
    return {
        "bundleIdentifier": str(pl.get("CFBundleIdentifier", DEFAULT_PACKAGE_ID)),
        "displayName": str(pl.get("CFBundleDisplayName") or pl.get("CFBundleName") or "AliceHub"),
        "version": str(pl.get("CFBundleShortVersionString", "0.1.0")),
        "buildVersion": str(pl.get("CFBundleVersion", "1")),
        "minOSVersion": str(pl.get("MinimumOSVersion", "15.0")),
        "gitCommit": str(pl.get("AliceHubGitCommit", "")),
        "size": ipa_path.stat().st_size,
        "sha256": sha256_file(ipa_path),
    }


def export_keystore_fingerprint(keystore: Path, alias: str, storepass: str) -> str:
    der = subprocess.check_output([
        "keytool", "-exportcert",
        "-keystore", str(keystore),
        "-storetype", "PKCS12",
        "-alias", alias,
        "-storepass", storepass,
    ])
    return hashlib.sha256(der).hexdigest().upper()


def make_signed_jar(
    json_filename: str,
    json_bytes: bytes,
    out_jar: Path,
    keystore: Path,
    alias: str,
    storepass: str,
    keypass: str,
) -> None:
    if out_jar.exists():
        out_jar.unlink()
    with zipfile.ZipFile(out_jar, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zi = zipfile.ZipInfo(json_filename, date_time=(2026, 1, 1, 0, 0, 0))
        zi.compress_type = zipfile.ZIP_DEFLATED
        zf.writestr(zi, json_bytes)
    subprocess.run(
        [
            "jarsigner",
            "-keystore", str(keystore),
            "-storetype", "PKCS12",
            "-storepass", storepass,
            "-keypass", keypass,
            "-digestalg", "SHA-256",
            "-sigalg", "SHA256withRSA",
            str(out_jar),
            alias,
        ],
        check=True,
        stdout=subprocess.DEVNULL,
    )


def verify_fdroid_jar(jar_path: Path, expected_json_name: str, expected_fp_upper: str) -> None:
    """Strictly verify signed JAR against F-Droid's JarIndexVerifier + EntryVerifier rules."""
    subprocess.run(
        ["jarsigner", "-verify", str(jar_path)],
        check=True,
        stdout=subprocess.DEVNULL,
    )
    with zipfile.ZipFile(jar_path) as zf:
        names = set(zf.namelist())
        if expected_json_name not in names:
            raise RuntimeError(f"{jar_path} missing {expected_json_name}")
        manifest = zf.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
        if "SHA1-Digest" in manifest or "MD5-Digest" in manifest:
            raise RuntimeError(f"{jar_path} MANIFEST.MF contains forbidden digest algorithm")
        if "SHA-256-Digest" not in manifest:
            raise RuntimeError(f"{jar_path} MANIFEST.MF missing SHA-256-Digest")

    # Verify with Java JarFile(file, true) to mirror Android's JarIndexVerifier exactly.
    verifier_java = r'''
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.jar.*;

public class VerifyFdroidJar {
    public static void main(String[] args) throws Exception {
        File f = new File(args[0]);
        String entryName = args[1];
        String expectedFp = args[2].toLowerCase();
        try (JarFile jf = new JarFile(f, true)) {
            JarEntry je = jf.getJarEntry(entryName);
            if (je == null) throw new RuntimeException("Missing entry: " + entryName);
            Attributes attrs = je.getAttributes();
            if (attrs == null) throw new RuntimeException("Missing attributes for " + entryName);
            for (Object k : attrs.keySet()) {
                String ks = k.toString();
                if ("MD5-Digest".equals(ks) || "SHA1-Digest".equals(ks))
                    throw new RuntimeException("Forbidden digest: " + ks);
            }
            byte[] buf = new byte[8192];
            try (InputStream is = jf.getInputStream(je)) {
                while (is.read(buf) != -1) {}
            }
            CodeSigner[] signers = je.getCodeSigners();
            if (signers == null || signers.length != 1)
                throw new RuntimeException("Expected 1 codeSigner, got " + (signers == null ? 0 : signers.length));
            java.util.List<? extends java.security.cert.Certificate> certs = signers[0].getSignerCertPath().getCertificates();
            if (certs.size() != 1)
                throw new RuntimeException("Expected 1 cert, got " + certs.size());
            X509Certificate cert = (X509Certificate) certs.get(0);
            byte[] enc = cert.getEncoded();
            if (enc.length < 256) throw new RuntimeException("Certificate too short: " + enc.length);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(enc);
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b));
            String fp = sb.toString();
            if (!expectedFp.equals(fp))
                throw new RuntimeException("Fingerprint mismatch: " + fp + " != " + expectedFp);
            System.out.println("VERIFIED " + f.getName() + " (" + entryName + ") fp=" + fp.toUpperCase());
        }
    }
}
'''
    with tempfile.TemporaryDirectory() as td:
        src = Path(td) / "VerifyFdroidJar.java"
        src.write_text(verifier_java, encoding="utf-8")
        subprocess.run(["javac", str(src)], check=True)
        subprocess.run(
            ["java", "-cp", td, "VerifyFdroidJar", str(jar_path), expected_json_name, expected_fp_upper],
            check=True,
        )


def ensure_square_icon(src_icon: Path, dst_icon: Path, size: int = 512) -> None:
    dst_icon.parent.mkdir(parents=True, exist_ok=True)
    try:
        from PIL import Image

        with Image.open(src_icon).convert("RGBA") as im:
            im = im.resize((size, size), Image.Resampling.LANCZOS)
            im.save(dst_icon, format="PNG", optimize=True)
    except ImportError:
        shutil.copy2(src_icon, dst_icon)


def generate_hbas_banner(src_icon: Path, dst_icon: Path) -> None:
    """Create a crisp 256x150 HBAS package banner (standard hb-appstore card ratio)."""
    dst_icon.parent.mkdir(parents=True, exist_ok=True)
    from PIL import Image, ImageDraw, ImageFont

    w, h = 256, 150
    bg = Image.new("RGBA", (w, h), (10, 10, 15, 255))
    draw = ImageDraw.Draw(bg)

    # Subtle top-left rose and bottom-right cyan atmospheric accents
    for y in range(h):
        for x in range(w):
            dx1, dy1 = (x - 42) / 140.0, (y - 34) / 110.0
            dx2, dy2 = (x - 216) / 140.0, (y - 122) / 110.0
            g1 = max(0.0, 1.0 - (dx1 * dx1 + dy1 * dy1)) ** 2
            g2 = max(0.0, 1.0 - (dx2 * dx2 + dy2 * dy2)) ** 2
            r = int(min(255, 11 + g1 * 48 + g2 * 8))
            g = int(min(255, 11 + g1 * 14 + g2 * 36))
            b = int(min(255, 17 + g1 * 28 + g2 * 52))
            bg.putpixel((x, y), (r, g, b, 255))

    # Subtle inner border
    draw.rectangle([0, 0, w - 1, h - 1], outline=(255, 255, 255, 34), width=1)

    with Image.open(src_icon).convert("RGBA") as icon:
        icon_resized = icon.resize((78, 78), Image.Resampling.LANCZOS)
        bg.alpha_composite(icon_resized, (24, (h - 78) // 2))

    font_title = None
    font_sub = None
    for cand in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    ):
        if os.path.exists(cand):
            font_title = ImageFont.truetype(cand, 26)
            break
    for cand in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if os.path.exists(cand):
            font_sub = ImageFont.truetype(cand, 12)
            break
    if font_title is None:
        font_title = ImageFont.load_default()
    if font_sub is None:
        font_sub = ImageFont.load_default()

    draw.text((116, 48), "AliceHub", fill=(244, 244, 248, 255), font=font_title)
    draw.text((117, 82), "AliceSoft Sys 1-4", fill=(168, 178, 194, 255), font=font_sub)
    draw.text((117, 99), "Switch Homebrew", fill=(56, 189, 248, 255), font=font_sub)

    bg.convert("RGB").save(dst_icon, format="PNG", optimize=True)


def build_fdroid_repo(
    out_root: Path,
    base_url: str,
    github_repo: str,
    website_url: str,
    tag: str,
    published_ms: int,
    release_notes: str,
    apk_src: Path,
    icon_src: Path,
    keystore: Path,
    alias: str,
    storepass: str,
    keypass: str,
) -> dict[str, Any]:
    repo_dir = out_root / "fdroid" / "repo"
    repo_dir.mkdir(parents=True, exist_ok=True)
    repo_address = f"{base_url.rstrip('/')}/fdroid/repo"

    apk_info = inspect_apk(apk_src)
    pkg_id = apk_info["packageName"]
    apk_filename = apk_src.name
    shutil.copy2(apk_src, repo_dir / apk_filename)

    # Icons for both v2 (/icons/...) and legacy v1 (/icons-<dpi>/...)
    repo_icon_rel = "/icons/repo-icon.png"
    app_icon_rel = f"/icons/{pkg_id}.png"
    ensure_square_icon(icon_src, repo_dir / "icons" / "repo-icon.png", 512)
    ensure_square_icon(icon_src, repo_dir / "icons" / f"{pkg_id}.png", 512)
    for dpi, px in ((640, 256), (480, 192), (320, 128), (240, 96), (160, 64), (120, 48)):
        ensure_square_icon(icon_src, repo_dir / f"icons-{dpi}" / "repo-icon.png", px)
        ensure_square_icon(icon_src, repo_dir / f"icons-{dpi}" / f"{pkg_id}.png", px)
    repo_icon_sha = sha256_file(repo_dir / "icons" / "repo-icon.png")
    repo_icon_size = (repo_dir / "icons" / "repo-icon.png").stat().st_size
    app_icon_sha = sha256_file(repo_dir / "icons" / f"{pkg_id}.png")
    app_icon_size = (repo_dir / "icons" / f"{pkg_id}.png").stat().st_size

    now_ms = max(int(time.time() * 1000), published_ms)
    summary_zh = "AliceSoft System 1–4 多平台原生启动器"
    summary_en = "Native multi-platform launcher for AliceSoft System 1–4 games"
    summary_ja = "AliceSoft System 1–4 マルチプラットフォーム実行環境"
    desc_zh = (
        "AliceHub 是在 Android 上原生运行 AliceSoft System 1–4 游戏的开源启动器与管理工具，"
        "内置 system3-sdl2、xsystem35 与 xsystem4 原生引擎，支持游戏导入、存档管理、MOD/补丁与手柄配置。"
        "安装包不含游戏本体，请自行准备有权使用的游戏资源。"
    )
    desc_en = (
        "AliceHub is an open-source native launcher and manager for AliceSoft System 1–4 games "
        "on Android, bundling system3-sdl2, xsystem35, and xsystem4 with local import, save management, "
        "MOD/patch support, and customizable virtual/physical controllers. No game data is included."
    )
    desc_ja = (
        "AliceHub は Android で AliceSoft System 1–4 作品をネイティブ実行するオープンソースランチャーです。"
        "system3-sdl2・xsystem35・xsystem4 を内蔵し、ゲーム導入、セーブ管理、コントローラー設定に対応します。"
        "ゲーム本体は含まれません。"
    )
    whats_new_text = release_notes.strip() or f"Release {tag} ({apk_info['versionName']})"

    # F-Droid Index v2 (https://f-droid.org/docs/Index_V2/)
    index_v2 = {
        "repo": {
            "name": {
                "en-US": "AliceHub F-Droid Repo",
                "zh-CN": "AliceHub 官方 F-Droid 源",
                "ja-JP": "AliceHub 公式 F-Droid リポジトリ",
            },
            "description": {
                "en-US": "Official self-hosted F-Droid repository for AliceHub (AliceSoft System 1–4 native launcher).",
                "zh-CN": "AliceHub 官方自建 F-Droid 仓库，提供 Android 原生 APK 更新。",
                "ja-JP": "AliceHub 公式セルフホスト F-Droid リポジトリ。",
            },
            "icon": {
                "en-US": {
                    "name": repo_icon_rel,
                    "sha256": repo_icon_sha,
                    "size": repo_icon_size,
                }
            },
            "address": repo_address,
            "webBaseUrl": website_url,
            "timestamp": now_ms,
            "categories": {
                "Games": {
                    "name": {
                        "en-US": "Games",
                        "zh-CN": "游戏",
                        "ja-JP": "ゲーム",
                    }
                }
            },
        },
        "packages": {
            pkg_id: {
                "metadata": {
                    "added": published_ms,
                    "lastUpdated": published_ms,
                    "categories": ["Games"],
                    "license": "GPL-2.0-only",
                    "authorName": "TomorrowX6",
                    "authorWebSite": f"https://github.com/{github_repo.split('/')[0]}",
                    "webSite": website_url,
                    "sourceCode": f"https://github.com/{github_repo}",
                    "issueTracker": f"https://github.com/{github_repo}/issues",
                    "changelog": f"https://github.com/{github_repo}/releases",
                    "preferredSigner": apk_info["signerSha256"],
                    "name": {
                        "en-US": "AliceHub",
                        "zh-CN": "AliceHub",
                        "ja-JP": "AliceHub",
                    },
                    "summary": {
                        "en-US": summary_en,
                        "zh-CN": summary_zh,
                        "ja-JP": summary_ja,
                    },
                    "description": {
                        "en-US": desc_en,
                        "zh-CN": desc_zh,
                        "ja-JP": desc_ja,
                    },
                    "icon": {
                        "en-US": {
                            "name": app_icon_rel,
                            "sha256": app_icon_sha,
                            "size": app_icon_size,
                        },
                        "zh-CN": {
                            "name": app_icon_rel,
                            "sha256": app_icon_sha,
                            "size": app_icon_size,
                        },
                        "ja-JP": {
                            "name": app_icon_rel,
                            "sha256": app_icon_sha,
                            "size": app_icon_size,
                        },
                    },
                },
                "versions": {
                    apk_info["sha256"]: {
                        "added": published_ms,
                        "file": {
                            "name": f"/{apk_filename}",
                            "sha256": apk_info["sha256"],
                            "size": apk_info["size"],
                        },
                        "manifest": {
                            "versionName": apk_info["versionName"],
                            "versionCode": apk_info["versionCode"],
                            "usesSdk": {
                                "minSdkVersion": apk_info["minSdkVersion"],
                                "targetSdkVersion": apk_info["targetSdkVersion"],
                            },
                            "signer": {
                                "sha256": [apk_info["signerSha256"]],
                            },
                            "usesPermission": apk_info["permissions"],
                            "nativecode": apk_info["nativecode"],
                            "features": apk_info["features"],
                        },
                        "whatsNew": {
                            "en-US": whats_new_text,
                            "zh-CN": whats_new_text,
                        },
                    }
                },
            }
        },
    }

    index_v2_bytes = json.dumps(index_v2, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    (repo_dir / "index-v2.json").write_bytes(index_v2_bytes)

    entry_obj = {
        "timestamp": now_ms,
        "version": 20002,
        "maxAge": 14,
        "index": {
            "name": "/index-v2.json",
            "sha256": sha256_bytes(index_v2_bytes),
            "size": len(index_v2_bytes),
            "numPackages": 1,
        },
        "diffs": {},
    }
    entry_bytes = json.dumps(entry_obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    (repo_dir / "entry.json").write_bytes(entry_bytes)

    # Legacy F-Droid Index v1 (index-v1.jar) for older clients / Neo Store / Droid-ify fallback
    v1_perms = [
        [p["name"], p.get("maxSdkVersion")]
        for p in apk_info["permissions"]
    ]
    index_v1 = {
        "repo": {
            "timestamp": now_ms,
            "version": 20001,
            "maxage": 14,
            "name": "AliceHub F-Droid Repo",
            "icon": "repo-icon.png",
            "address": repo_address,
            "description": "Official self-hosted F-Droid repository for AliceHub.",
        },
        "requests": {"install": [], "uninstall": []},
        "apps": [
            {
                "categories": ["Games"],
                "suggestedVersionName": apk_info["versionName"],
                "suggestedVersionCode": str(apk_info["versionCode"]),
                "description": desc_en,
                "summary": summary_en,
                "issueTracker": f"https://github.com/{github_repo}/issues",
                "license": "GPL-2.0-only",
                "name": "AliceHub",
                "sourceCode": f"https://github.com/{github_repo}",
                "webSite": website_url,
                "changelog": f"https://github.com/{github_repo}/releases",
                "authorName": "TomorrowX6",
                "added": published_ms,
                "lastUpdated": published_ms,
                "packageName": pkg_id,
                "icon": f"{pkg_id}.png",
                "localized": {
                    "en-US": {
                        "name": "AliceHub",
                        "summary": summary_en,
                        "description": desc_en,
                        "whatsNew": whats_new_text,
                    },
                    "zh-CN": {
                        "name": "AliceHub",
                        "summary": summary_zh,
                        "description": desc_zh,
                        "whatsNew": whats_new_text,
                    },
                    "ja-JP": {
                        "name": "AliceHub",
                        "summary": summary_ja,
                        "description": desc_ja,
                    },
                },
            }
        ],
        "packages": {
            pkg_id: [
                {
                    "added": published_ms,
                    "apkName": apk_filename,
                    "hash": apk_info["sha256"],
                    "hashType": "sha256",
                    "minSdkVersion": apk_info["minSdkVersion"],
                    "targetSdkVersion": apk_info["targetSdkVersion"],
                    "nativecode": apk_info["nativecode"],
                    "packageName": pkg_id,
                    "sig": apk_info["sigV1"],
                    "signer": apk_info["signerSha256"],
                    "size": apk_info["size"],
                    "uses-permission": v1_perms,
                    "features": [f["name"] for f in apk_info["features"]],
                    "versionCode": apk_info["versionCode"],
                    "versionName": apk_info["versionName"],
                }
            ]
        },
    }
    index_v1_bytes = json.dumps(index_v1, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    (repo_dir / "index-v1.json").write_bytes(index_v1_bytes)

    fingerprint = export_keystore_fingerprint(keystore, alias, storepass)
    make_signed_jar("entry.json", entry_bytes, repo_dir / "entry.jar", keystore, alias, storepass, keypass)
    make_signed_jar("index-v1.json", index_v1_bytes, repo_dir / "index-v1.jar", keystore, alias, storepass, keypass)

    verify_fdroid_jar(repo_dir / "entry.jar", "entry.json", fingerprint)
    verify_fdroid_jar(repo_dir / "index-v1.jar", "index-v1.json", fingerprint)

    return {
        "repoUrl": repo_address,
        "fingerprint": fingerprint,
        "deepLink": f"fdroidrepos://{repo_address.removeprefix('https://')}?fingerprint={fingerprint}",
        "apk": apk_info,
    }


def build_altstore_repo(
    out_root: Path,
    base_url: str,
    github_repo: str,
    website_url: str,
    tag: str,
    published_iso: str,
    release_notes: str,
    ipa_src: Path,
    icon_src: Path,
    docs_mirror_path: Path | None = None,
) -> dict[str, Any]:
    alt_dir = out_root / "altstore"
    alt_dir.mkdir(parents=True, exist_ok=True)
    ipa_info = inspect_ipa(ipa_src)

    ipa_filename = ipa_src.name
    shutil.copy2(ipa_src, alt_dir / ipa_filename)
    ensure_square_icon(icon_src, alt_dir / "icon.png", 512)

    base = base_url.rstrip("/")
    source_url = f"{base}/altstore/source.json"
    icon_url = f"{base}/altstore/icon.png"
    ipa_url = f"{base}/altstore/{ipa_filename}"
    ver_desc = release_notes.strip() or f"AliceHub {tag} (Build {ipa_info['buildVersion']})"

    source = {
        "name": "AliceHub",
        "identifier": f"{ipa_info['bundleIdentifier']}.source",
        "subtitle": "AliceSoft System 1–4 原生启动器",
        "description": "AliceHub 官方自建 SideStore / AltStore 源，提供 iOS / iPadOS 原生版更新（不含游戏本体）。",
        "iconURL": icon_url,
        "website": website_url,
        "tintColor": "#E14B86",
        "sourceURL": source_url,
        "featuredApps": [ipa_info["bundleIdentifier"]],
        "apps": [
            {
                "name": "AliceHub",
                "bundleIdentifier": ipa_info["bundleIdentifier"],
                "developerName": "TomorrowX6",
                "subtitle": "AliceSoft System 1–4 原生启动器",
                "localizedDescription": (
                    "AliceHub 是在 iOS / iPadOS 上原生运行 AliceSoft System 1–4 游戏的开源启动器与管理工具，"
                    "内置 system3-sdl2、xsystem35 与 xsystem4 三套原生引擎，支持本地导入、存档同步（iCloud/文件）、"
                    "MOD 与汉化补丁管理及自定义虚拟/实体手柄。\n\n"
                    "纯开源启动器（GNU GPL-2.0），不含任何游戏本体。"
                ),
                "iconURL": icon_url,
                "tintColor": "#E14B86",
                "category": "games",
                "beta": False,
                "version": ipa_info["version"],
                "versionDate": published_iso,
                "versionDescription": ver_desc,
                "downloadURL": ipa_url,
                "size": ipa_info["size"],
                "appPermissions": {
                    "entitlements": [],
                    "privacy": {},
                },
                "versions": [
                    {
                        "version": ipa_info["version"],
                        "buildVersion": ipa_info["buildVersion"],
                        "date": published_iso,
                        "localizedDescription": ver_desc,
                        "downloadURL": ipa_url,
                        "size": ipa_info["size"],
                        "minOSVersion": ipa_info["minOSVersion"],
                        "sha256": ipa_info["sha256"],
                    }
                ],
            }
        ],
        "news": [],
    }

    payload = json.dumps(source, ensure_ascii=False, indent=2) + "\n"
    (alt_dir / "source.json").write_text(payload, encoding="utf-8")
    if docs_mirror_path is not None:
        docs_mirror_path.parent.mkdir(parents=True, exist_ok=True)
        docs_mirror_path.write_text(payload, encoding="utf-8")

    return {
        "sourceUrl": source_url,
        "sidestoreLink": f"sidestore://source?url={urllib.parse.quote(source_url, safe='')}",
        "altstoreLink": f"altstore://source?url={urllib.parse.quote(source_url, safe='')}",
        "ipa": ipa_info,
    }


def build_hbas_repo(
    out_root: Path,
    base_url: str,
    github_repo: str,
    tag: str,
    published_dt: datetime.datetime,
    release_notes: str,
    switch_zip_src: Path,
    icon_src: Path,
) -> dict[str, Any]:
    hbas_dir = out_root / "hbas"
    pkg_dir = hbas_dir / "packages" / "alicehub"
    zips_dir = hbas_dir / "zips"
    pkg_dir.mkdir(parents=True, exist_ok=True)
    zips_dir.mkdir(parents=True, exist_ok=True)

    pkg_name = "alicehub"
    title = "AliceHub"
    author = "TomorrowX6"
    category = "emu"
    short_desc = "AliceSoft System 1-4 native launcher (system3 / xsystem35 / xsystem4)"
    long_desc = (
        "AliceHub is an open-source native launcher and game manager for AliceSoft System 1-4 "
        "titles on Nintendo Switch Homebrew.\\n\\n"
        "Bundles AliceHub.nro, system3.nro, xsystem35.nro, and xsystem4.nro into /switch/. "
        "Includes save manager, MOD/patch manager, controller mapping, and in-game menu. "
        "Open-source (GPL-2.0); no game files are included."
    )
    updated_str = published_dt.strftime("%d/%m/%Y")
    raw_notes = release_notes.strip()
    if raw_notes and all(ord(ch) < 128 for ch in raw_notes):
        changelog = raw_notes.replace("\r\n", "\n").replace("\n", "\\n")
    else:
        changelog = (
            f"Release {tag} ({updated_str})\\n\\n"
            f"Full release notes: https://github.com/{github_repo}/releases/tag/{tag}"
        )

    # Collect .nro payloads from release switch zip and repack into HBAS SD-root layout:
    #   manifest.install
    #   info.json
    #   switch/AliceHub.nro, switch/system3.nro, switch/xsystem35.nro, switch/xsystem4.nro
    nro_entries: list[tuple[str, bytes]] = []
    extracted_bytes = 0
    with zipfile.ZipFile(switch_zip_src) as src_zf:
        for info in sorted(src_zf.infolist(), key=lambda i: i.filename.lower()):
            if info.is_dir():
                continue
            bn = Path(info.filename).name
            if not bn.lower().endswith(".nro"):
                continue
            data = src_zf.read(info.filename)
            rel_sd_path = f"switch/{bn}"
            nro_entries.append((rel_sd_path, data))
            extracted_bytes += len(data)

    if not nro_entries:
        raise RuntimeError(f"No .nro files found inside {switch_zip_src}")

    manifest_lines = [f"U: {rel}" for rel, _ in nro_entries]
    manifest_text = "\n".join(manifest_lines) + "\n"

    info_obj = {
        "title": title,
        "author": author,
        "category": category,
        "version": tag,
        "url": f"https://github.com/{github_repo}",
        "license": "GPLv2",
        "description": short_desc,
        "details": long_desc,
        "changelog": changelog,
    }
    info_text = json.dumps(info_obj, ensure_ascii=False, indent=2) + "\n"

    # Write package zip with manifest.install + info.json + switch/*.nro
    out_zip = zips_dir / f"{pkg_name}.zip"
    with zipfile.ZipFile(out_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as dst_zf:
        dst_zf.writestr("manifest.install", manifest_text.encode("utf-8"))
        dst_zf.writestr("info.json", info_text.encode("utf-8"))
        for rel, data in nro_entries:
            dst_zf.writestr(rel, data)

    zip_bytes = out_zip.stat().st_size
    (pkg_dir / "manifest.install").write_text(manifest_text, encoding="utf-8")
    (pkg_dir / "info.json").write_text(info_text, encoding="utf-8")
    generate_hbas_banner(icon_src, pkg_dir / "icon.png")

    repo_url = f"{base_url.rstrip('/')}/hbas"
    repo_json = {
        "packages": [
            {
                "name": pkg_name,
                "title": title,
                "author": author,
                "description": short_desc,
                "details": long_desc,
                "version": tag,
                "license": "GPLv2",
                "changelog": changelog,
                "url": f"https://github.com/{github_repo}",
                "updated": updated_str,
                "app_dls": 0,
                "web_dls": 0,
                "extracted": max(1, (extracted_bytes + 999) // 1000),
                "filesize": max(1, (zip_bytes + 999) // 1000),
                "category": category,
                "binary": "/switch/AliceHub.nro",
                "screens": 0,
            }
        ]
    }
    (hbas_dir / "repo.json").write_text(
        json.dumps(repo_json, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    # Ready-to-drop repos.json template for sdmc:/switch/appstore/.get/repos.json
    repos_template = {
        "repos": [
            {
                "name": "4TU Switch Repo",
                "url": "https://switch.cdn.fortheusers.org",
                "enabled": True,
            },
            {
                "name": "AliceHub",
                "url": repo_url,
                "enabled": True,
            },
        ]
    }
    (hbas_dir / "repos.json").write_text(
        json.dumps(repos_template, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    return {
        "repoUrl": repo_url,
        "reposJsonUrl": f"{repo_url}/repos.json",
        "version": tag,
        "zipBytes": zip_bytes,
        "extractedBytes": extracted_bytes,
        "nroFiles": [rel for rel, _ in nro_entries],
    }


def write_store_landing(out_root: Path, base_url: str, website_url: str, summary: dict[str, Any]) -> None:
    fd = summary["fdroid"]
    alt = summary["altstore"]
    hb = summary["hbas"]
    html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>AliceHub 自建源 · F-Droid / SideStore / HBAS</title>
  <style>
    :root {{ color-scheme: dark; }}
    *, *::before, *::after {{ box-sizing: border-box; }}
    body {{
      margin: 0; background: #070709; color: #f3f3f5;
      font-family: system-ui, -apple-system, 'Noto Sans SC', sans-serif;
      display: grid; place-items: center; min-height: 100dvh; padding: 18px;
    }}
    main {{
      width: min(680px, 100%); padding: 24px 22px; border-radius: 18px;
      background: rgba(14, 14, 20, 0.94); border: 1px solid rgba(255,255,255,0.14);
    }}
    h1 {{ margin: 0 0 6px; font-size: 24px; }}
    p.sub {{ margin: 0 0 22px; color: rgba(243,243,245,0.65); font-size: 14px; }}
    .item {{ padding: 16px 0; border-top: 1px solid rgba(255,255,255,0.1); }}
    .item h2 {{ margin: 0 0 6px; font-size: 16px; }}
    code {{
      display: block; padding: 10px 12px; border-radius: 10px; margin: 8px 0;
      background: rgba(255,255,255,0.06); font-family: ui-monospace, monospace;
      font-size: 12.5px; word-break: break-all; color: #e2e8f0;
    }}
    a.btn {{
      display: inline-block; margin-top: 10px; margin-right: 10px;
      padding: 7px 14px; border-radius: 999px; font-size: 13px; text-decoration: none;
      background: #f3f3f5; color: #070709; font-weight: 600;
    }}
    a.btn.ghost {{
      background: transparent; color: #f3f3f5; border: 1px solid rgba(255,255,255,0.22);
    }}
    small {{ color: rgba(243,243,245,0.58); font-size: 12px; display: block; margin-top: 6px; line-height: 1.6; overflow-wrap: anywhere; }}
    small code {{ display: inline; padding: 1px 6px; margin: 0 2px; border-radius: 5px; font-size: 11.5px; }}
  </style>
</head>
<body>
  <main>
    <h1>AliceHub 官方自建软件源</h1>
    <p class="sub">Android (F-Droid) · iOS / iPadOS (SideStore / AltStore) · Nintendo Switch (HBAS)</p>

    <section class="item" id="fdroid">
      <h2>Android · F-Droid / Droid-ify / Neo Store</h2>
      <code>{fd['repoUrl']}</code>
      <small>指纹 (SHA-256)：{fd['fingerprint']}</small>
      <a class="btn" href="{fd['deepLink']}">一键添加到 F-Droid ↗</a>
    </section>

    <section class="item" id="sidestore">
      <h2>iOS / iPadOS · SideStore / AltStore</h2>
      <code>{alt['sourceUrl']}</code>
      <a class="btn" href="{alt['sidestoreLink']}">添加到 SideStore ↗</a>
      <a class="btn ghost" href="{alt['altstoreLink']}">添加到 AltStore ↗</a>
    </section>

    <section class="item" id="hbas">
      <h2>Nintendo Switch · HBAS (Homebrew App Store)</h2>
      <code>{hb['repoUrl']}</code>
      <small>将仓库加入 SD 卡 <code>/switch/appstore/.get/repos.json</code>，或直接下载现成配置文件。</small>
      <a class="btn ghost" href="{hb['reposJsonUrl']}">下载 repos.json ↗</a>
    </section>

    <section class="item">
      <a class="btn ghost" href="{website_url}">返回 AliceHub 官网 ↗</a>
    </section>
  </main>
</body>
</html>
"""
    (out_root / "index.html").write_text(html, encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description="Build F-Droid, SideStore, and HBAS stores for AliceHub")
    ap.add_argument("--tag", required=True, help="Release tag (e.g. f0810b6)")
    ap.add_argument("--published", required=True, help="Release ISO-8601 UTC timestamp (e.g. 2026-11-28T05:36:46Z)")
    ap.add_argument("--apk", required=True, type=Path, help="Path to signed arm64-v8a APK")
    ap.add_argument("--ipa", required=True, type=Path, help="Path to unsigned iOS IPA")
    ap.add_argument("--switch-zip", required=True, type=Path, help="Path to Switch NRO zip")
    ap.add_argument("--out", required=True, type=Path, help="Output staging directory")
    ap.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Public HTTPS base URL of the store")
    ap.add_argument("--github-repo", default=DEFAULT_GITHUB_REPO)
    ap.add_argument("--website-url", default=DEFAULT_WEBSITE)
    ap.add_argument("--release-notes", default="")
    ap.add_argument("--icon", type=Path, default=REPO_ROOT / "project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
    ap.add_argument("--fdroid-keystore", required=True, type=Path)
    ap.add_argument("--fdroid-alias", default=os.environ.get("FDROID_KEY_ALIAS", "alicehub-fdroid"))
    ap.add_argument("--fdroid-storepass", default=os.environ.get("FDROID_KEYSTORE_PASSWORD", ""))
    ap.add_argument("--fdroid-keypass", default=os.environ.get("FDROID_KEY_PASSWORD", ""))
    ap.add_argument("--mirror-altstore-json", type=Path, default=None, help="Optional local path to mirror altstore source.json")
    args = ap.parse_args()

    if not args.fdroid_storepass:
        raise SystemExit("Missing --fdroid-storepass / FDROID_KEYSTORE_PASSWORD")
    keypass = args.fdroid_keypass or args.fdroid_storepass

    published_dt = datetime.datetime.fromisoformat(args.published.replace("Z", "+00:00"))
    published_ms = int(published_dt.timestamp() * 1000)

    if args.out.exists():
        shutil.rmtree(args.out)
    args.out.mkdir(parents=True, exist_ok=True)

    summary = {
        "tag": args.tag,
        "published": args.published,
        "baseUrl": args.base_url.rstrip("/"),
        "fdroid": build_fdroid_repo(
            out_root=args.out,
            base_url=args.base_url,
            github_repo=args.github_repo,
            website_url=args.website_url,
            tag=args.tag,
            published_ms=published_ms,
            release_notes=args.release_notes,
            apk_src=args.apk,
            icon_src=args.icon,
            keystore=args.fdroid_keystore,
            alias=args.fdroid_alias,
            storepass=args.fdroid_storepass,
            keypass=keypass,
        ),
        "altstore": build_altstore_repo(
            out_root=args.out,
            base_url=args.base_url,
            github_repo=args.github_repo,
            website_url=args.website_url,
            tag=args.tag,
            published_iso=args.published,
            release_notes=args.release_notes,
            ipa_src=args.ipa,
            icon_src=args.icon,
            docs_mirror_path=args.mirror_altstore_json,
        ),
        "hbas": build_hbas_repo(
            out_root=args.out,
            base_url=args.base_url,
            github_repo=args.github_repo,
            tag=args.tag,
            published_dt=published_dt,
            release_notes=args.release_notes,
            switch_zip_src=args.switch_zip,
            icon_src=args.icon,
        ),
    }

    write_store_landing(args.out, args.base_url, args.website_url, summary)
    (args.out / "build-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
