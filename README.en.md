<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>Native Android Runtime Engine &amp; Unified Hub for System 4.x Applications</strong></p>
  <p>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build APK" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Android%20%28arm64--v8a%29-3DDC84.svg" alt="Platform" /></a>
  </p>
  <p><a href="README.md"><strong>简体中文</strong></a> | <strong>English</strong> | <a href="README.ja.md"><strong>日本語</strong></a></p>
</div>

---

**AliceHub** (`io.github.alicehub`) is a native Android runtime engine and unified asset hub for **System 4.x** titles, built upon [xsystem4](https://github.com/nunuhara/xsystem4) and [xsystem4-android](https://github.com/kichikuou/xsystem4-android).

Written in pure C/C++, SDL2, and OpenGL ES 3.0, AliceHub directly executes AIN bytecode and renders original assets on `arm64-v8a` Android devices without Wine, Winlator, or any x86/x64 translation layer. Beyond classic System 4.0 compatibility, it provides complete implementations and mobile-first optimizations for late-era large-scale projects powered by **AIN v12 – v14.1**, **PartsEngine**, **FLAT v15 skeletal & particle animations**, **SealEngine 3D**, **APEG video streaming**, and **SaveData v26 / v27**.

---

## Key Features

### 1. Unified Home, Dedicated Hubs & Global Settings
- **Clean Home Portal**: Displays supported entries with high-resolution icons and real-time status badges (`Not Installed`, `Installed`, `Installed · MOD`, `Installing…`, `Running`).
- **Dedicated Entry Hubs**: Each entry opens a tailored management view with base package installation, multi-instance directory switching, non-destructive MOD management, isolated save management, modifier tools, and clean uninstallation controls.
- **Global Settings** (top-right gear icon):
  - **Theme**: Instant switching across **Auto**, **Light**, and **Dark** modes.
  - **Language**: Instant UI, notification, and overlay localization across **System**, **简体中文**, **日本語**, and **English**.
  - **Online Install Toggle**: Disabled by default; enabling presents formal Terms & Disclaimer and displays package file sizes across online installation options.
  - **About**: Built-in **Open Source Licenses** viewer and **GitHub** repository link.

### 2. Dual-Mode Installation, Foreground Service & MOD Management
- **Local & Online Package Installation**:
  - **Local Install**: Powered by a native `libarchive` + `liblzma` streaming extractor supporting `.zip` / `.rar` / `.7z` archives (including solid archives and large dictionaries) and external folders with automatic UTF-8 / UTF-16LE / GBK filename detection.
  - **Online Install** *(when enabled in Settings)*: Supports streaming cloud downloads with upfront file-size indicators, HTTP `Range` pause/resume, and per-file SHA-256 verification without double extraction storage overhead.
- **Foreground Download Service**: Continues downloading and extracting at full speed when minimized or with the screen off, providing live progress notifications and pause controls.
- **Non-Destructive MOD Manager**: Supports online release catalogs with file sizes and changelogs, incremental patch updates, automatic base + patch combo installation, local MOD archive/folder imports, and automatic vanilla file backup for one-tap restoration.

### 3. Save Manager & Live Floating Overlay Tools
- **Scoped Save Manager**: Bidirectional desktop save compatibility (`SaveData` v26/v27 `.asd` & `RSave` `.rsd`) with atomic `fsync` writes, archive/folder/single-file import & export, multi-directory switching, and per-file backup, rename, and delete actions.
- **Offline & In-Game Floating Tools**: Draggable in-game overlay icon, latched **`⏩ Ctrl`** fast-forward pill, live **Font Size (`75%–160%`)** scaling with overflow-safe layout adaptation, live **Scrollbar (`100%–300%`)** enlargement, offline save editors, and real-time numeric/state modifiers.

### 4. Native System 4.x Engine & Mobile Performance
- **AIN v12 – v14.1 VM & Full HLL Coverage**: Complete support for AIN v12–v14.1 opcodes (`IFACE_WRAP`, `CALLMETHOD`, `X_*` instructions, delegates `DG_*`, deep struct copy), all 804 `PartsEngine` functions, `SealEngine`, `ADVEngine`, `SaveData`, `RSave`, `APEG`, `SoundEngine`, `HashMap`, and multi-archive `.afa` / `.ex` priority resolution.
- **PartsEngine, FLAT v15 & GLES 3.0 Pipeline**: Offscreen FBO composition, `ELNA` / `FLAT` v15 timelines and particle emitters, 3D Euler perspective transforms with back-face culling, and full `cproc` rendering operations.
- **Frame Pacing & Power Efficiency**: Urgent display (`-8`) and audio (`-16`) thread priorities, Android 12+ **ADPF (`APerformanceHintManager`)** dynamic CPU boost, 60fps VSync pacing on 60Hz/120Hz displays, 16 KB page alignment (Android 15+ ready), and automatic audio suspension when backgrounded.

---

## System Requirements

| Component | Minimum | Recommended |
| :--- | :--- | :--- |
| **Architecture** | `arm64-v8a` (64-bit ARM) | Snapdragon 8 Series / Dimensity 8000+ or equivalent |
| **OS Version** | Android 8.0 (API 26)+ | Android 12.0 (API 31)+ (ADPF & seamless frame pacing) |
| **Graphics** | OpenGL ES 3.0 | OpenGL ES 3.2 |
| **Memory** | 4 GB RAM | 6 GB+ RAM |
| **Storage** | 5 GB+ free space recommended | UFS 3.0+ flash storage |

---

## Quick Start

1. Download the latest signed APK from [**Releases**](https://github.com/TomorrowX6/AliceHub/releases) or [**GitHub Actions**](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml).
2. Open **AliceHub** and select an entry on the home screen:
   - Use **Local Install** to import a `.zip` / `.rar` / `.7z` archive or folder containing original PC files (`AliceStart.ini` / `System40.ini`, `*.ain`, `*.afa`, etc.).
   - Optionally enable **Online Install** in **Settings** after reviewing the Terms & Disclaimer.
3. Tap **Start Game** to launch, and use the top-left floating button anytime to adjust font size, scrollbar scale, fast-forward, or live tools.

---

## Touch Controls

| Action | Touch Gesture |
| :--- | :--- |
| **Left Click / Confirm** | Single-finger tap |
| **Right Click / Cancel / Menu** | Two-finger tap anywhere, or single-finger tap on side letterbox bars |
| **Fast-Forward (`Ctrl`)** | Single-finger press & hold, or tap the floating **`⏩ Ctrl`** pill to latch |
| **Mouse Wheel Up / Down** | Two-finger swipe down / up |
| **Hover / Drag** | Single-finger press and drag |
| **Exit Prompt** | System Back gesture / button |

---

## Building from Source

```bash
sudo apt-get update
sudo apt-get install -y ninja-build bison flex autoconf automake libtool pkg-config cmake git python3 openjdk-17-jdk

git clone https://github.com/TomorrowX6/AliceHub.git
cd AliceHub

export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME=/path/to/android-sdk/ndk/26.1.10909125

./build-shared-libs.sh
cd project && ./gradlew assembleRelease
```

The signed APK is generated at `project/app/build/outputs/apk/release/app-release.apk`.

---

## Credits & License

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — Core System 4 engine and file format parsers.
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Original Android port framework for xsystem4.

Licensed under the [GNU General Public License v2.0 (GPL-2.0)](xsystem4/COPYING).
