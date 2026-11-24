<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>A native AliceSoft game launcher and library for Android and iOS</strong></p>
  <p>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build Android APK and iOS IPA" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Android%20%28arm64--v8a%29-3DDC84.svg" alt="Android arm64-v8a" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-iOS%2015%2B-007AFF.svg" alt="iOS 15+" /></a>
    <img src="https://img.shields.io/badge/Engine-System%201%E2%80%934-FF7043.svg" alt="Engine: System 1–4" />
  </p>
  <p><a href="README.md"><strong>简体中文</strong></a> | <strong>English</strong> | <a href="README.ja.md"><strong>日本語</strong></a></p>
</div>

---

**AliceHub** (`io.github.alicehub`) is an open-source launcher and library for running AliceSoft games natively on **Android and iOS**. It uses SDL2 and native engines, without Wine, Winlator, or x86/x64 translation. No games are bundled; use game files you have the right to use.

## Download and quick start

Download the package for your device from the [**latest release**](https://github.com/TomorrowX6/AliceHub/releases/latest):

| Platform | Requirements | Package |
| :--- | :--- | :--- |
| **Android** | Android 8.0 (API 26) or later, `arm64-v8a`, OpenGL ES 3.0 | `AliceHub-<version>-arm64-v8a.apk`, ready to install |
| **iOS / iPadOS** | 15.0 or later, arm64 | `AliceHub-<version>-ios-unsigned.ipa`, **unsigned**; sign and sideload it |

You can also download `re-xsystem4-arm64-v8a` or `AliceHub-ios-unsigned` from a successful [GitHub Actions → Build](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml) run and unzip the artifact to get the APK or IPA. GitHub sign-in is required for artifacts.

### Android

Install the APK and open **AliceHub**. Select a game entry, or choose **Import Other Game** from the top-right menu. Import a folder, a `.zip` / `.rar` / `.7z` archive, or an `.iso` / `.mdf` disc image containing the complete game files. Tap **Start Game** after import.

### iOS / iPadOS

1. Sign and sideload the IPA with AltStore, SideStore, or Sideloadly. Signing with a free Apple Account typically needs renewal every 7 days. TrollStore only works on supported devices and OS versions.
2. In **AliceHub → Import Game**, choose **Folder / Multiple Discs** or **Archive / Disc Image**. You can also copy a complete game folder into **AliceHub → Games** on your device using Files.
3. Open the imported entry in the library and tap **Play**. Exiting the game normally returns you to the library.

**Online installation** is off by default on both platforms. Enable it in Settings after accepting the terms, then download available files from the game's entry. Leave enough space for the game files and any temporary installation data.

## Supported games and features

The launcher picks the engine from the files in the game's root folder. These are common detection markers, not a complete list of required game files.

| Game type | Native engine | Common markers |
| :--- | :--- | :--- |
| **Early System 1–3** | `system3-sdl2` | `ADISK.DAT`, `ACG.DAT`, etc. |
| **System 3.5–3.9** | `xsystem35` | `System39.ini`, `System39.ain`, `*SA.ALD`, `ADISK.ALD` |
| **System 4.x** | `xsystem4` | `AliceStart.ini`, `System40.ini`, `*.ain` (except `System39.ain`) |

- **Library and imports:** Search, sort, and identify Chinese-translated and original-language entries. Import games outside the built-in catalog, including multi-disc sets and bundled patches. Manage mods for compatible games. Rance X can create and switch between game folders, keeping saves and mods separate for each version.
- **Saves and tools:** Import, export, and back up saves. Supported titles offer save editing, in-game modifiers, streamer mode, or display scaling. On iOS, you can sync saves with a folder selected in Files, such as one in iCloud Drive.
- **Controls and audio:** System 3.5–3.9 and System 4.x support editable virtual controls and physical controller mappings. Early DAT-based games use native touch controls. Both platforms play `xsystem35` MIDI music.
- **Background installs:** Android uses a foreground service. iOS 26 and later can continue via a system task; older iOS versions have limited background time and pause when it runs out. Return to the app to continue.

## Android features in detail

### 1. Native engines and mobile optimization
- **System 3.x Native Kernel (`xsystem35`)**:
  - Supports System 3.5 – 3.9 scenario bytecode and extension modules (`ShGraph`, `ShSound`, `ShString`, `SACT`, `eDemo`, etc.), `ALD` / `ALK` / `DRI` archives, `PMS` / `QNT` / `VSP` / `JPEG` / `WebP` graphics, and the `System 3.9` (`System39.ain`) save format.
  - Native **Shift-JIS / GBK / Unicode** text rendering, `SDL2_mixer` virtual CD audio playback (MP3 / Ogg tracks via `playlist.txt`), and an Android-native MIDI synthesizer, fully integrated with the in-game floating overlay, virtual/physical gamepad bridge, and live state modifiers.
- **System 4.x Native Engine (`xsystem4`)**:
  - Backward and forward compatibility across early System 4.0 (`AIN v0 – v11`, `SACT2`, `StoatSpriteEngine`, `DrawDungeon` / `ReignEngine` 3D dungeon & skinned rendering, `ALD` archives) and modern titles (`AIN v12 – v14.1` opcodes, `PartsEngine`, `SealEngine` 3D UBO bone transforms, `ELNA` / `FLAT v15` skeletal & particle animations, `APEG` / `PMPEG` video streaming, `SaveData v26/v27`, and multi-archive `.afa` / `.ex` priority resolution).
- **Graphics Resilience, Frame Pacing & Power Efficiency**:
  - Automatic reconstruction of textures, offscreen FBOs, filters, and 3D dungeon resources after background `EGLContext` loss, with immediate frame re-presentation on window restore.
  - High-priority display (`-8`) and audio (`-16`) thread scheduling, Android 12+ **ADPF (`APerformanceHintManager`)** dynamic CPU boost, 60fps VSync pacing, 16 KB page alignment (`max-page-size=16384`, Android 15+ ready), and automatic audio/MIDI suspension when backgrounded.

### 2. Unified Home, Dedicated Hubs & Global Settings
- **Library & Custom Import**: Shows built-in and imported entries with installation status and extracts icons from PE executables.
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
  - A foreground service can continue downloading and extracting with the app minimized or the screen off, with live progress, speed, **Pause**, and **Cancel** controls.
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

## Touch and controller controls

The table below applies to System 3.5–3.9 and System 4.x on Android. On iOS, configure controls in **Settings → Gamepad Settings**. For System 4 games, you can also enable **Long Press to Skip (Hold Ctrl)**.

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

## Building from source

### Android APK

Install JDK 17, the Android SDK (platform API 34), an Android NDK, CMake 3.22+, and Ninja. The commands below use Ubuntu. Replace `YOUR_NDK_VERSION` with the installed NDK version:

```bash
sudo apt-get update
sudo apt-get install -y ninja-build bison flex autoconf automake libtool pkg-config cmake git python3 openjdk-17-jdk

git clone https://github.com/TomorrowX6/AliceHub.git
cd AliceHub

export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/YOUR_NDK_VERSION"
./build-shared-libs.sh
(cd project && ./gradlew assembleRelease)
```

Output: `project/app/build/outputs/apk/release/app-release.apk`. Without release credentials, the local build uses the debug key and cannot update an installed APK signed with a different key.

### iOS IPA or simulator app

Install Xcode and the iOS SDK on macOS (CI uses Xcode 26). Install an iOS Simulator runtime to run the simulator app. From the repository root:

```bash
brew install cmake ninja autoconf automake libtool bison flex pkgconf xcodegen
ios/build-app.sh ipa  # ios/build/AliceHub.ipa (unsigned)
ios/build-app.sh sim  # arm64 simulator app
```

See the [iOS developer documentation](ios/README.md) for build details and development checks.

---

## Credits & License

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — Cross-platform System 4.x core engine and file format parsers.
- [kichikuou/system3-sdl2](https://github.com/kichikuou/system3-sdl2) — Runtime for early System 1–3 games.
- [kichikuou/xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) — Cross-platform System 3.x (`xsystem35`) core engine and module implementations.
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Original Android port framework.

Licensed under the [GNU General Public License v2.0 (GPL-2.0)](xsystem4/COPYING) (see also [xsystem35/COPYING](xsystem35/COPYING)).
