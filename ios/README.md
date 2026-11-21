# iOS native libraries

Prerequisites: Xcode with iOS SDKs, and Homebrew `cmake ninja autoconf automake
libtool bison flex` (`xcodegen` will be used by the app project later).

```
ios/build-native.sh iphonesimulator
ios/build-native.sh iphoneos
```

Both builds target arm64, iOS 15.0+, and install separate static archives and
headers in `ios/build/<sdk>/stage/{lib,include}`. Do not combine device and
simulator archives into one fat archive: both use arm64 but different platforms.
Zlib is taken from the selected SDK. FreeType 2.13.3 matches the Android fallback
in `cmake/FindFreetype.cmake`; cglm 0.9.2 is fetched by the engine's CMake project.
Other dependency versions and hashes match the root Android superbuild.
Archive imports use static libarchive 3.8.11 and xz 5.8.4 with SDK zlib.

Host-side library and extraction checks (no simulator required):

```
brew install libarchive xz cmake ninja xcodegen autoconf automake libtool bison flex
bash ios/Tests/run-library-checks.sh
```

The script compiles the C bridge and Swift sources with host `swiftc`, linking
Homebrew libarchive rather than macOS's older system library. Fixtures are created
at runtime and removed afterward: raw Shift-JIS ZIP, traversal ZIP, empty/corrupt
archives, 7z/tar/tar.gz via Homebrew bsdtar, Joliet ISO via `hdiutil makehybrid`,
and synthetic raw-sector MDF images. End-to-end library checks cover archive-root,
subfolder and nested-disc imports, duplicates, invalid games and temporary cleanup.
libffi 3.4.6 has no configure switch to disable AArch64 closures, so the iOS-only
patch disables them in its target header. Static executable trampolines are
disabled in configure. CFI directives are disabled for that dependency because
Xcode's assembler rejects its AArch64 CFI expressions. Exceptions must not unwind
through libffi calls.

## Isolated engines

The native build also stages SDL2_mixer 2.8.1 (minimp3 and Vorbisfile, no MIDI)
and the SDL2 xsystem35 static engine with the debugger disabled. The launcher
supports System 3.x and System 4 and labels each game's engine. System 4 INIs
and non-System39 AINs take precedence over System 3 markers (`*SA.ALD`,
`ADISK.ALD`, `System39.ini`, `System39.ain`). Only regular root files count.

`libxsystem4_engine.a` contains one prelinked object combining xsystem4, sys4
and cglm; `libxsystem35_engine.a` similarly combines xsystem35, src_lib and
modules. The iOS linker localizes everything except each engine's entry point
and the shared `alicehub_ios_*` platform functions. Engines and their
private libraries use `-fno-common` so tentative globals are isolated too. Shared
dependencies remain undefined until the app link. Do not link the raw engine
archives alongside these isolated archives.

`EnginePlatform.m` stays inside the System 4 object so its audio/save lifecycle
hooks resolve to the isolated System 4 implementations by default. System 3
registers its own audio/render-flush/message-skip hook before attaching its
window; the SDL UIKit event pump suspends either engine while inactive.
The app force-loads both isolated archives to exercise their combined link.
After building both SDKs, validate the staged
exports with `python3 ios/Tests/test_engine_isolation.py`.

## App shell

`ios/build-app.sh sim` generates the Xcode project and builds an unsigned
simulator app in `ios/build/app-iphonesimulator/Build/Products/Debug-iphonesimulator/`.
`ios/build-app.sh ipa` builds Release for devices without signing and packages
`ios/build/AliceHub.ipa` for signing by a sideloading tool. The generated Xcode
project is ignored; edit `project.yml` instead. Each app build runs the incremental
native build so edits to engine sources, headers and platform code are linked.

The SwiftUI launcher discovers `Documents/Games`, imports security-scoped
folders or archives/disc images with coordinated reads, and confirms deletion
without removing saves. The archive picker opens files in place; extraction goes
directly into hidden `Games/.import-<UUID>/stage0`, with up to three further stages
for nested archives or game-containing ISO/MDF images. The shallowest game root
wins (path-sorted ties). Its folder name is preserved, or the outer archive's
basename is used for a stage-root game. Duplicate names are rejected, temporary
stages are always cleaned, and the overlay shows extracted MB and the current
entry. Supported archive documents opened from Files can also be imported.
Each launch writes
`Documents/Logs/engine.log` (stdout/stderr) and `crash.log`; logs are replaced on
the next launch. The app delegate forwards scene lifecycle notifications to SDL.
Games with an INI launch by directory; AIN-only folders launch the first root AIN
in filename order when multiple AINs exist.

## App launch contract

### System 3.x

The launcher calls `xsystem35_main` with `-gamedir <root> -savedir <saveDir>`
and adds `-saveformat system39` for System39 INI/AIN markers. The game directory
is the working directory. Engine output and crash reports use `Documents/Logs`.
Bundled `MTLc3m.ttf`/`mincho.ttf` paths are supplied via
`XSYSTEM35_FONT_GOTHIC`/`XSYSTEM35_FONT_MINCHO` as defaults before loading
`.xsys35rc`; profile and CLI font overrides take precedence. Relative profile
font paths resolve against the game directory.

SDL's renderer handles Retina scaling and aspect-preserving letterboxing.
The native System 3 touch handler is retained: one finger is left-click,
two fingers or a tap in the black border is right-click, three fingers opens
the engine menu. There is no additional Android launcher gesture overlay.

**MIDI synthesis is unavailable on iOS.** MIDI-only music stays silent without
an initialization error. Recorded MP3/OGG/WAV BGM and PCM remain supported;
CD tracks require the game's `playlist.txt` (or `_inmm.ini`) and audio files.

### System 4

* Include `xsystem4_main.h` and call `xsystem4_main(argc, argv)` with an argv[0]
  such as `xsystem4`, followed by `--save-folder`, an absolute writable sandbox
  directory, `--save-format=rsm`, and the absolute game directory (or INI/AIN).
  This matches `XSystem4Activity.getArguments()` for System 4 games. argv must
  be writable and NULL-terminated as for a normal C main.
* Package `xsystem4/shaders/` and `xsystem4/fonts/` in the bundle, preserving
  those directory names. Set cwd to that resource root before launch. Shader
  loading tries relative `shaders/...` first; the compiled desktop fallback
  `/usr/local/share/xsystem4` is not usable on iOS. Default fonts similarly try
  relative `fonts/...`; explicit absolute `--font-mincho` and `--font-gothic`
  paths are also supported. Unlike Android's SDL asset reader, FreeType on iOS
  reads actual filesystem paths. The engine does not change cwd itself.
* Supply an absolute game path so the resource cwd and game directory remain
  independent. Import/extract game files into an accessible sandbox directory
  before launch; document-provider access is the app's responsibility.
* Set `XSYSTEM4_HOME` to writable Application Support storage for engine config
  and default save location (otherwise XDG_DATA_HOME, HOME/.xsystem4, then cwd).
  The explicit save-folder is preferred. Optionally set
  `XSYSTEM4_CRASH_LOG_PATH` to an absolute writable log path. No Android audio
  environment variables or JNI modifier/gamepad initialization apply on iOS.
* Integrate SDL's UIKit lifecycle and call `SDL_SetMainReady()` before entering
  the engine; the engine uses `SDL_MAIN_HANDLED` and exports no main/SDL_main.
  Link the isolated engine archives and shared dependencies, SDK zlib/iconv and
  SDL's required Apple frameworks; do not link SDL2main as an app entry point.
  Static compilation alone does not validate final app linking or lifecycle.
* The non-Android shutdown path still calls `sys_exit`/process exit; returning
  cleanly to the launcher and repeat sessions remain future work.
  iOS crash reports omit the Linux-only
  register dump. Error dialogs use the scene-aware SDL UIKit alert window.

## Standalone checks

`python3 ios/Tests/test_save_manager.py` compiles the Foundation-only save manager
with `swiftc -import-objc-header ios/Tests/SaveChecks-Bridging-Header.h
ios/App/SaveZip.swift ios/App/SaveManager.swift ios/Tests/SaveManagerChecks.swift
-lz -o <checks>`, runs save-operation checks, and verifies ZIP interoperability
with Python and `unzip`. The Python driver supplies the fixture directory argument
to the executable. No simulator or game data is required.

The launcher offers 存档管理 on each game's swipe actions. Saves remain under
`Documents/Saves/<game>`; automatic backups retain five ZIPs under Application
Support `save_backups/<game>`. Import merges files; restoring a backup replaces
visible saves while retaining hidden settings. PC export uses the engine's native
X4SS converter and does not modify the original saves.

## Game runtime

The iOS platform bridge attaches SDL's UIKit window to the active SwiftUI scene
and requests landscape orientation. Rendering binds SDL's actual EAGL drawable
framebuffer instead of framebuffer zero. The viewport remains in window points
for mouse/touch mapping and is scaled to drawable pixels only for `glViewport`.
SDL converts single-finger touches to mouse events. Android's window, GL and
input paths are unchanged.
Tap the gamepad icon in the launcher to edit the global layout and controller
settings. Swipe right on a game to configure an override for that game. The
layout editor supports dragging, per-control size, opacity and visibility, and
the Android Visual Novel, RPG and empty presets. In a game, the gamepad icon
opens quick toggles; touches outside the controls still go to the game.
Bluetooth and MFi controllers use SDL's GameController support and the same
default mappings as Android. Enable physical controls in the gamepad settings;
auto-hide is optional and only applies while a controller is connected.
Before a scene deactivates, the bridge finishes pending GPU work, pauses audio,
and flushes tracked saves. SDL's event pump runs UIKit without returning to the
engine while the app is inactive, resuming only once it becomes active again.

Debug builds accept `-autolaunch <folder>` to launch a discovered game after the
scene becomes active, for example:

```
xcrun simctl launch booted io.github.alicehub -autolaunch dohnadohna
python3 ios/Tests/test_viewport.py
xcrun swiftc ios/App/{GameLibrary,GamepadProfile}.swift ios/Tests/LibraryChecks.swift -o /tmp/alicehub-library-checks
/tmp/alicehub-library-checks
```

Dohna Dohna's title screen and first Chinese message have been exercised on the
iPhone 17 simulator. Game files belong outside the repository, then under the
app container's `Documents/Games/<folder>`; no game data is bundled. The test
host has no audio output devices (`system_profiler SPAudioDataType` lists none),
and SDL reports AudioQueue error -66680 (`kAudioQueueErr_InvalidDevice`). The
engine continues silently; audible output still needs verification on a host
with an audio device or a physical iPhone.
