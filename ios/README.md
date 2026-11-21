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
libffi 3.4.6 has no configure switch to disable AArch64 closures, so the iOS-only
patch disables them in its target header. Static executable trampolines are
disabled in configure. CFI directives are disabled for that dependency because
Xcode's assembler rejects its AArch64 CFI expressions. Exceptions must not unwind
through libffi calls.

## App shell

`ios/build-app.sh sim` generates the Xcode project and builds an unsigned
simulator app in `ios/build/app-iphonesimulator/Build/Products/Debug-iphonesimulator/`.
`ios/build-app.sh ipa` builds Release for devices without signing and packages
`ios/build/AliceHub.ipa` for signing by a sideloading tool. The generated Xcode
project is ignored; edit `project.yml` instead. Each app build runs the incremental
native build so edits to engine sources, headers and platform code are linked.

The SwiftUI launcher discovers `Documents/Games`, imports security-scoped
folders with coordinated reads, and confirms deletion without removing saves.
Import uses a hidden temporary directory and moves it into place after copying.
Duplicate folder names are rejected rather than overwritten. Each launch writes
`Documents/Logs/engine.log` (stdout/stderr) and `crash.log`; logs are replaced on
the next launch. The app delegate forwards scene lifecycle notifications to SDL.
Games with an INI launch by directory; AIN-only folders launch the first root AIN
in filename order when multiple AINs exist.

## App launch contract

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
  Link the staged dependencies (including sys4 and cglm), SDK zlib/iconv and
  SDL's required Apple frameworks; do not link SDL2main as an app entry point.
  Static compilation alone does not validate final app linking or lifecycle.
* The non-Android shutdown path still calls `sys_exit`/process exit; returning
  cleanly to the launcher and repeat sessions remain future work.
  iOS crash reports omit the Linux-only
  register dump. Error dialogs use the scene-aware SDL UIKit alert window.

## Game runtime

The iOS platform bridge attaches SDL's UIKit window to the active SwiftUI scene
and requests landscape orientation. Rendering binds SDL's actual EAGL drawable
framebuffer instead of framebuffer zero. The viewport remains in window points
for mouse/touch mapping and is scaled to drawable pixels only for `glViewport`.
SDL converts single-finger touches to mouse events. Android's window, GL and
input paths are unchanged.
Before a scene deactivates, the bridge finishes pending GPU work, pauses audio,
and flushes tracked saves. SDL's event pump runs UIKit without returning to the
engine while the app is inactive, resuming only once it becomes active again.

Debug builds accept `-autolaunch <folder>` to launch a discovered game after the
scene becomes active, for example:

```
xcrun simctl launch booted io.github.alicehub -autolaunch dohnadohna
python3 ios/Tests/test_viewport.py
```

Dohna Dohna's title screen and first Chinese message have been exercised on the
iPhone 17 simulator. Game files belong outside the repository, then under the
app container's `Documents/Games/<folder>`; no game data is bundled. The test
host has no audio output devices (`system_profiler SPAudioDataType` lists none),
and SDL reports AudioQueue error -66680 (`kAudioQueueErr_InvalidDevice`). The
engine continues silently; audible output still needs verification on a host
with an audio device or a physical iPhone.

## In-game modifier

The shared native modifier (`xsystem4/src/modifier.c`) is reached from Swift through
`xsystem4/include/modifier_api.h`; Android's JNI exports call the same functions.

- **In game:** a draggable wand button on the engine window opens the modifier panel: MOD cheat-code
  entry with the Android presets (Rance X), live locks (Rance X, Dohna Dohna, Evenicle), one-tap common
  cheats (Dohna Dohna, Evenicle) and the live value readout. Locks persist in
  `Application Support/modifier_config.json`.
- **Launcher:** swipe a Rance X game (folder with `Rance10.ain`) and choose “修改存档” to edit the writable fields of its `LocalSaveN.asd` files.
  Each write first copies the file to `<save>.mod.bak`.

Checks: `swiftc -parse-as-library ios/App/ModifierBridge.swift ios/Tests/ModifierChecks.swift -o /tmp/modchecks && /tmp/modchecks`.
