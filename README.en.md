<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>Native cross-platform launcher for AliceSoft System 1–4</strong></p>
  <p>
    <a href="https://alice.000.moe/"><img src="https://img.shields.io/badge/Website-alice.000.moe-111116.svg" alt="Website" /></a>
    <a href="https://store.000.moe/"><img src="https://img.shields.io/badge/Store-store.000.moe-E14B86.svg" alt="Self-Hosted Store" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
  </p>
  <p><a href="README.md"><strong>简体中文</strong></a> | <strong>English</strong> | <a href="README.ja.md"><strong>日本語</strong></a></p>
</div>

---

**AliceHub** is an open-source launcher and game manager for running AliceSoft **System 1–4** titles natively on **Android, iOS / iPadOS, and Nintendo Switch (homebrew)** via SDL2 — no Wine, Winlator, or x86 translation. **No game files are included**; use files you have the right to use.

## Download & Self-Hosted Repositories

Download from the [website](https://alice.000.moe/), [latest release](https://github.com/TomorrowX6/AliceHub/releases/latest), or [self-hosted store hub](https://store.000.moe/) (nightly artifacts in [Actions → Build](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml)):

| Platform | Requirements | Direct Download | Self-Hosted Store |
| :--- | :--- | :--- | :--- |
| **Android** | Android 8.0+ · `arm64-v8a` | `*.apk` (ready to install) | [F-Droid](https://store.000.moe/#fdroid): `https://store.000.moe/fdroid/repo` |
| **iOS / iPadOS** | iOS 15+ · `arm64` | `*.ipa` (unsigned; sign & sideload) | [SideStore / AltStore](https://store.000.moe/#sidestore): `https://store.000.moe/altstore/source.json` |
| **Nintendo Switch** | Homebrew (app mode recommended) | `*.zip` (extract 4 NROs to `/switch/`) | [HBAS](https://store.000.moe/#hbas): `https://store.000.moe/hbas` ([`repos.json`](https://store.000.moe/hbas/repos.json)) |

- **F-Droid SHA-256 fingerprint**: `48620D9F64F02B26196550D138B7014D24B3C22C2C2DC168F0838ACC454ACDFA`
- **Switch HBAS**: Place [`repos.json`](https://store.000.moe/hbas/repos.json) at `/switch/appstore/.get/repos.json` on your SD card, or add the repo URL to your existing file.

## Highlights

- **Three native engines**: Bundles `system3-sdl2` (early System 1–3), `xsystem35` (System 3.5–3.9), and `xsystem4` (System 4.x) with automatic engine detection.
- **Multi-format local import**: Import folders, `.zip` / `.rar` / `.7z` archives, and `.iso` / `.mdf` disc images with multi-disc merging and filename encoding repair.
- **PC-compatible saves**: Isolated per-game saves with backup/restore and automatic export to **Windows PC-compatible formats**; iOS supports Files / iCloud folder sync.
- **Non-destructive MODs & patches**: Install and cleanly revert mods or translation patches with automatic backups, plus per-game System 4 launch entries and multi-instance folders.
- **Touch & controller support**: Visual virtual-gamepad editor, Bluetooth/USB controller mapping, and an in-game menu (fast-forward, screenshots, modifiers, PartsEngine text/scrollbar scaling).

## Quick Start

- **Android**: Install the APK, import a game from the top-right menu (or pick a catalog entry), and tap **Start Game**. Storage can be switched between app-private and shared (`Internal Storage → AliceHub`) in Settings.
- **iOS / iPadOS**: Sideload the IPA with SideStore, AltStore, or Sideloadly, then import games in-app or copy game folders into `AliceHub → Games` via Files.
- **Nintendo Switch**: Copy `AliceHub.nro`, `system3.nro`, `xsystem35.nro`, and `xsystem4.nro` to `/switch/`, launch **AliceHub** in application mode, and press **Y** to import from SD card (or place games under `/switch/alicehub/<system3|xsystem35|xsystem4>/games/<game>/`). Press **L + R** in-game (**SL + SR** on a sideways Joy-Con) for the menu.

> **Online download** is disabled by default on all platforms and can be enabled in Settings after accepting the terms.

<details>
<summary><strong>Build from Source (Android / iOS / Nintendo Switch)</strong></summary>

### Android APK

Requires JDK 17, Android SDK (API 34), Android NDK, CMake 3.22+, and Ninja:

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/YOUR_NDK_VERSION"
./build-shared-libs.sh
(cd project && ./gradlew assembleRelease)
# Output: project/app/build/outputs/apk/release/app-release.apk
```

### iOS IPA / Simulator

Requires macOS and Xcode (CI uses Xcode 26); see [ios/README.md](ios/README.md):

```bash
brew install cmake ninja autoconf automake libtool bison flex pkgconf xcodegen
ios/build-app.sh ipa  # ios/build/AliceHub.ipa (unsigned)
ios/build-app.sh sim  # arm64 simulator app
```

### Nintendo Switch NRO

Requires devkitPro / devkitA64 (CI uses `devkitpro/devkita64`); see [cmake/switch/hub/README.md](cmake/switch/hub/README.md):

```bash
export PATH="$DEVKITPRO/portlibs/switch/bin:$DEVKITPRO/devkitA64/bin:$DEVKITPRO/tools/bin:$PATH"
aarch64-none-elf-cmake -G Ninja -S cmake/switch -B build-switch -DCMAKE_BUILD_TYPE=Release
cmake --build build-switch --parallel 2
# Output: build-switch/artifacts/*.nro
```

</details>

## Credits & License

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — System 4.x core engine and format parsers
- [kichikuou/system3-sdl2](https://github.com/kichikuou/system3-sdl2) — Early System 1–3 runtime
- [kichikuou/xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) — System 3.x (`xsystem35`) engine
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Original Android port framework

Licensed under [GNU GPL-2.0](xsystem4/COPYING) (see also [xsystem35/COPYING](xsystem35/COPYING)).

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub?ref=badge_large)
