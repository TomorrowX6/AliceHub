<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>Dual-Kernel Native Android Runtime Engine &amp; Unified Hub for System 3.x / System 4.x Applications</strong></p>
  <p>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build APK" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Android%20%28arm64--v8a%29-3DDC84.svg" alt="Platform" /></a>
    <img src="https://img.shields.io/badge/Engine-System%203.x%20%26%204.x-FF7043.svg" alt="Engine: System 3.x & 4.x" />
  </p>
  <p><a href="README.md"><strong>简体中文</strong></a> | <strong>English</strong> | <a href="README.ja.md"><strong>日本語</strong></a></p>
</div>

---

**AliceHub** (`io.github.alicehub`) is a dual-kernel native Android runtime engine and unified local asset hub for **System 3.x & System 4.x** titles, built upon [xsystem4](https://github.com/nunuhara/xsystem4), [xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2), and [xsystem4-android](https://github.com/kichikuou/xsystem4-android).

Written in pure C/C++, SDL2, and OpenGL ES 3.0, AliceHub directly executes original scenario bytecode and hardware-accelerates asset rendering on `arm64-v8a` Android devices without Wine, Winlator, or any x86/x64 translation layer. The launcher automatically detects **System 3.x** (`System39.ini`, `*SA.ALD`, `ADISK.ALD`) and **System 4.x** (`AliceStart.ini`, `System40.ini`, `*.ain`) directories and dispatches them to the corresponding native engine (`libxsystem35.so` / `libxsystem4.so`), spanning classic **System 3.5 – 3.9**, early **System 4.0**, and late-era large-scale projects powered by **AIN v12 – v14.1**, **PartsEngine**, **FLAT v15**, and **SealEngine / ReignEngine 3D**, along with community localization and expansion patches.

---

## Key Features

### 1. Dual Native Engines (System 3.x + System 4.x) & Mobile Optimization
- **System 3.x Native Kernel (`xsystem35`)**:
  - Full support for System 3.5 – 3.9 scenario bytecode and extension modules (`ShGraph`, `ShSound`, `ShString`, `SACT`, `eDemo`, etc.), `ALD` / `ALK` / `DRI` archives, `PMS` / `QNT` / `VSP` / `JPEG` / `WebP` graphics, and the `System 3.9` (`System39.ain`) save format.
  - Native **Shift-JIS / GBK / Unicode** text rendering, `SDL2_mixer` virtual CD audio playback (MP3 / Ogg tracks via `playlist.txt`), and an Android-native MIDI synthesizer, fully integrated with the in-game floating overlay, virtual/physical gamepad bridge, and live state modifiers.
- **Full-Generation System 4.x Kernel (`xsystem4`)**:
  - Backward and forward compatibility across early System 4.0 (`AIN v0 – v11`, `SACT2`, `StoatSpriteEngine`, `DrawDungeon` / `ReignEngine` 3D dungeon & skinned rendering, `ALD` archives) and modern titles (`AIN v12 – v14.1` opcodes, all 804 `PartsEngine` functions, `SealEngine` 3D UBO bone transforms, `ELNA` / `FLAT v15` skeletal & particle animations, `APEG` / `PMPEG` video streaming, `SaveData v26/v27`, and multi-archive `.afa` / `.ex` priority resolution).
- **Graphics Resilience, Frame Pacing & Power Efficiency**:
  - Automatic reconstruction of textures, offscreen FBOs, filters, and 3D dungeon resources after background `EGLContext` loss, with immediate frame re-presentation on window restore.
  - High-priority display (`-8`) and audio (`-16`) thread scheduling, Android 12+ **ADPF (`APerformanceHintManager`)** dynamic CPU boost, 60fps VSync pacing, 16 KB page alignment (`max-page-size=16384`, Android 15+ ready), and automatic audio/MIDI suspension when backgrounded.

### 2. Unified Home, Dedicated Hubs & Global Settings
- **Clean Home Portal & Custom Import**: Displays preset entries alongside arbitrary external System 3.x / System 4.x projects imported via **Import Other Game**, automatically extracting high-resolution icons from PE executables and showing live status badges (`Not Installed`, `Installed`, `Installed · MOD`, `Installing…`, `Running`).
- **Dedicated Entry Hubs**: Each entry provides a tailored management view with base package installation, multi-instance directory switching, non-destructive MOD / localization patch management, isolated save management, modifier tools, and background asynchronous uninstallation.
- **Global Settings** (top-right gear icon):
  - **Theme**: Instant switching across **Auto**, **Light**, and **Dark** modes.
  - **Language**: Instant localization across **System**, **简体中文**, **日本語**, and **English** (covering UI, notifications, gamepad editor, and in-game overlays).
  - **Online Install Toggle**: Disabled by default; enabling presents formal Terms & Disclaimer and displays package file sizes across online installation options.
  - **Gamepad**: Centralized configuration for global default and per-entry virtual gamepad layouts and physical controller mappings.
  - **Clear Cache & About**: Distinguishes routine temporary cache from resumable download chunks for safe cleanup; includes **Open Source Licenses** and **GitHub** links.

### 3. Multi-Format Asset Import, Foreground Service & Non-Destructive MODs
- **Archive & Disc Image Local Import**:
  - Powered by a native `libarchive` + `liblzma` streaming extractor and a built-in **ISO9660 / Joliet disc image extractor**, supporting direct imports from `.zip` / `.rar` / `.7z` archives (including solid archives and large dictionaries), **`.iso` / `.mdf` disc images (auto-detecting 2048 / 2352 / 2448 sector layouts)**, or external folders.
  - Automatically merges multi-disc directories (`Disk1` / `Disk2`, etc.) and split install sets, applies bundled update patches, prunes redundant installer bloat, and repairs UTF-8 / UTF-16LE / GBK / Shift-JIS filename mojibake.
- **Streaming Online Install & Foreground Service**:
  - Online Install *(when enabled in Settings)* supports upfront file-size indicators, HTTP `Range` pause/resume, and per-file SHA-256 verification without double extraction storage overhead.
  - Continues downloading and extracting at full speed when minimized or with the screen off via a Foreground Service and wake lock, providing live progress, speed, **Pause**, and **Cancel** controls.
- **Non-Destructive MOD & Patch Manager**:
  - Supports online release catalogs with file sizes and changelogs, incremental patch updates, automatic base + patch combo installation, and local imports with canonical version recognition; atomically backs up vanilla files for one-tap restoration.

### 4. Virtual Gamepad, Physical Controllers & Visual Layout Editor
- **Multi-Preset Virtual Controls & Fullscreen Editor**: Includes built-in presets for visual novels (GAL / ADV) and dungeon crawlers (RPG), plus a fullscreen drag-and-drop visual editor to add/remove buttons, D-Pads, and sticks, customize position, scale, opacity, circle/pill/square shapes, tap/toggle modes, key combos, and emulator shortcuts (toggle overlay, toggle virtual cursor, screenshot, open menu) with grid snapping and multi-step undo.
- **Plug-and-Play Physical Controllers**: Native support for Bluetooth and USB controllers (Xbox, PlayStation, Switch Pro, etc.) with A/B & X/Y swap, stick deadzone and cursor speed tuning, and flexible mapping of sticks and D-Pad to virtual mouse cursor, scroll wheel, arrow keys, or WASD across both System 3.x and System 4.x engines.

### 5. Bidirectional Desktop Save Compatibility, Floating Tools & Diagnostics
- **Scoped Save Manager & Desktop Format Conversion**:
  - Manages System 3.x (`.asd`) and System 4.x (`SaveData` v26/v27 `.asd`, `RSave` `.rsd`, `.sav`) saves with atomic `fsync` writes, archive/folder/single-file import & export, and automatic pre-overwrite backup with one-tap restore.
  - Automatically converts mobile `X4SS` structured saves back into native Windows PC `GD\x01\x01` format upon export for seamless PC ↔ Android interoperability.
- **Offline & In-Game Floating Tools**:
  - **Mobile Enhancements**: Draggable in-game floating button, latched **`⏩ Ctrl`** fast-forward pill, one-tap screenshot capture, live **Font Size (`75%–160%`)** scaling, and live **Scrollbar (`100%–300%`)** enlargement (PartsEngine).
  - **Save & Live Modifiers**: Offline save editing, full-unlock save import/restore, and real-time numeric adjustments and state locks across supported System 3.x and System 4.x entries.
- **Structured Diagnostic Reports**: Captures runtime errors and native crash signals into structured diagnostic cards supporting one-tap copy, `.txt` log export, and system share integration.

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
2. Open **AliceHub** and select a preset entry, or choose **Import Other Game** from the top-right menu:
   - **Local Install**: Import a `.zip` / `.rar` / `.7z` archive, `.iso` / `.mdf` disc image, or folder containing complete PC assets:
     - **System 3.x assets**: `*SA.ALD` / `ADISK.ALD`, `*.ALD`, `System39.ini` / `System39.ain`, etc. (place a `playlist.txt` in the game root to mount ripped MP3 / Ogg CD tracks).
     - **System 4.x assets**: `AliceStart.ini` / `System40.ini`, `*.ain`, `*.afa` / `*.ald`, etc.
   - **Online Install**: Optionally enable **Online Install** in **Settings** after reviewing the Terms & Disclaimer.
3. Tap **Start Game** to launch (AliceHub automatically dispatches `xsystem35` or `xsystem4`), and use the top-left floating button anytime to access fast-forward, virtual gamepad controls, screenshots, font scaling, or modifiers.

---

## Touch & Controller Controls

| Action | Touch Gesture | Default Physical Controller Mapping (Customizable) |
| :--- | :--- | :--- |
| **Left Click / Confirm** | Single-finger tap | `A` / `R3` (Left Stick moves virtual cursor) |
| **Right Click / Cancel / Menu** | Two-finger tap anywhere, or single-finger tap on side letterbox bars | `B` |
| **Fast-Forward (`Ctrl`)** | Single-finger press & hold, or tap the floating **`⏩ Ctrl`** pill to latch | `RT` (latched toggle) |
| **Mouse Wheel Up / Down** | Two-finger swipe down / up | `LB` / `RB` or Right Stick up / down |
| **Hover / Drag** | Single-finger press and drag | Left Stick cursor movement |
| **Overlay Menu / Virtual Pad** | Tap top-left floating icon | `Start` (menu) / `Back` (cursor) / `Guide` (virtual pad) |
| **Exit Prompt** | System Back gesture / button | `LT` (`Esc`) or System Back |

---

## Building from Source

```bash
sudo apt-get update
sudo apt-get install -y ninja-build bison flex autoconf automake libtool pkg-config cmake git python3 openjdk-17-jdk

git clone https://github.com/TomorrowX6/AliceHub.git
cd AliceHub

export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME=/path/to/android-sdk/ndk/26.1.10909125

# 1. Cross-compile all arm64-v8a native shared libraries (libxsystem4.so, libxsystem35.so, libSDL2.so, libarchive_jni.so, etc.)
./build-shared-libs.sh

# 2. Build signed Release APK
cd project && ./gradlew assembleRelease
```

The signed APK is generated at `project/app/build/outputs/apk/release/app-release.apk`.

---

## Credits & License

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — Cross-platform System 4.x core engine and file format parsers.
- [kichikuou/xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) — Cross-platform System 3.x (`xsystem35`) core engine and module implementations.
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Original Android port framework.

Licensed under the [GNU General Public License v2.0 (GPL-2.0)](xsystem4/COPYING) (see also [xsystem35/COPYING](xsystem35/COPYING)).
