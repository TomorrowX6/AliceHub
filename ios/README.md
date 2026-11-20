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
project is ignored; edit `project.yml` instead. Missing native staging triggers
the corresponding native build automatically.

The SwiftUI launcher discovers `Documents/Games`, imports security-scoped
folders with coordinated reads, and confirms deletion without removing saves.
Import uses a hidden temporary directory and moves it into place after copying.
Duplicate folder names are rejected rather than overwritten. Each launch writes
`Documents/Logs/engine.log` (stdout/stderr) and `crash.log`; logs are replaced on
the next launch. The app delegate forwards scene lifecycle notifications to SDL.

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
  cleanly to the launcher, repeat sessions, touch controls and sandbox/runtime
  behavior remain app-integration work. iOS crash reports omit the Linux-only
  register dump. Error dialogs currently follow the non-Android desktop gate.

Actual game execution remains to be verified in the game-integration step.
