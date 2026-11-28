<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>AliceSoft System 1–4 マルチプラットフォーム実行環境</strong></p>
  <p>
    <a href="https://alice.000.moe/"><img src="https://img.shields.io/badge/Website-alice.000.moe-111116.svg" alt="Website" /></a>
    <a href="https://store.000.moe/"><img src="https://img.shields.io/badge/Store-store.000.moe-E14B86.svg" alt="Self-Hosted Store" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
  </p>
  <p><a href="README.md"><strong>简体中文</strong></a> | <a href="README.en.md"><strong>English</strong></a> | <strong>日本語</strong></p>
</div>

---

**AliceHub** は、アリスソフトの **System 1–4** 作品を **Android・iOS / iPadOS・Nintendo Switch（Homebrew）** でネイティブ実行・管理するオープンソースランチャーです。SDL2 でネイティブ動作し、Wine・Winlator・x86 変換は不要です。**ゲームデータは含まれません**。利用権のあるゲームファイルをご用意ください。

## ダウンロード・公式セルフホストストア

[公式サイト](https://alice.000.moe/)・[最新リリース](https://github.com/TomorrowX6/AliceHub/releases/latest)・[セルフホストストア](https://store.000.moe/) から入手できます（最新コミットのビルドは [Actions → Build](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml)）：

| プラットフォーム | 動作環境 | 直接ダウンロード | 公式セルフホスト源 |
| :--- | :--- | :--- | :--- |
| **Android** | Android 8.0+ · `arm64-v8a` | `*.apk`（そのまま導入） | [F-Droid](https://store.000.moe/#fdroid)：`https://store.000.moe/fdroid/repo` |
| **iOS / iPadOS** | iOS 15+ · `arm64` | `*.ipa`（未署名・要サイドロード） | [SideStore / AltStore](https://store.000.moe/#sidestore)：`https://store.000.moe/altstore/source.json` |
| **Nintendo Switch** | Homebrew（アプリモード推奨） | `*.zip`（4つの NRO を `/switch/` へ） | [HBAS](https://store.000.moe/#hbas)：`https://store.000.moe/hbas`（[`repos.json`](https://store.000.moe/hbas/repos.json)） |

- **F-Droid SHA-256 フィンガープリント**：`48620D9F64F02B26196550D138B7014D24B3C22C2C2DC168F0838ACC454ACDFA`
- **Switch HBAS**：[`repos.json`](https://store.000.moe/hbas/repos.json) を SD カードの `/switch/appstore/.get/repos.json` へ配置するか、既存ファイルへリポジトリを追加してください。

## 主な機能

- **3つのネイティブエンジン**：`system3-sdl2`（System 1–3）・`xsystem35`（System 3.5–3.9）・`xsystem4`（System 4.x）を内蔵し、ゲーム構成から自動判別。
- **多形式ローカル導入**：フォルダ、`.zip` / `.rar` / `.7z`、`.iso` / `.mdf` ディスクイメージに対応（複数ディスク統合・文字化け修復対応）。
- **PC 互換セーブ管理**：作品別セーブ管理・バックアップ復元に加え、**Windows PC 互換形式**への自動変換出力に対応。iOS は「ファイル」/ iCloud 同期に対応。
- **非破壊 MOD・パッチ管理**：自動バックアップ付きでパッチや MOD を導入・復元可能。System 4 の作品別起動エントリ切替・複数ディレクトリ管理に対応。
- **タッチ＆コントローラー**：仮想パッド編集・Bluetooth/USB コントローラー割り当て、ゲーム内メニュー（スキップ、スクリーンショット、文字・スクロールバー拡大）を搭載。

## クイックスタート

- **Android**：APK を導入し、右上メニューからゲームを取り込んで起動します。設定でアプリ専用／共有フォルダ（`内部ストレージ → AliceHub`）を切り替えられます。
- **iOS / iPadOS**：SideStore・AltStore・Sideloadly 等で署名してサイドロードし、アプリ内インポートまたは「ファイル」アプリで `AliceHub → Games` へ配置して起動します。
- **Nintendo Switch**：`AliceHub.nro`・`system3.nro`・`xsystem35.nro`・`xsystem4.nro` を SD カードの `/switch/` に配置し、アプリモードで **AliceHub** を起動。**Y** で設定からローカル取り込み、または `/switch/alicehub/<system3|xsystem35|xsystem4>/games/<ゲーム>/` へ配置します。プレイ中は **L + R**（Joy-Con 横持ち時は **SL + SR**）でメニューを開けます。

> 各プラットフォームとも**オンラインダウンロード**は初期状態で無効です。設定で規約に同意してから有効化できます。

<details>
<summary><strong>ソースからのビルド（Android / iOS / Nintendo Switch）</strong></summary>

### Android APK

JDK 17・Android SDK（API 34）・NDK・CMake 3.22+・Ninja が必要です：

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/YOUR_NDK_VERSION"
./build-shared-libs.sh
(cd project && ./gradlew assembleRelease)
# 出力：project/app/build/outputs/apk/release/app-release.apk
```

### iOS IPA / シミュレータ

macOS と Xcode（CI は Xcode 26）が必要です。詳細は [ios/README.md](ios/README.md)：

```bash
brew install cmake ninja autoconf automake libtool bison flex pkgconf xcodegen
ios/build-app.sh ipa  # ios/build/AliceHub.ipa（未署名）
ios/build-app.sh sim  # arm64 シミュレータ用アプリ
```

### Nintendo Switch NRO

devkitPro / devkitA64 環境（CI は `devkitpro/devkita64`）が必要です。詳細は [cmake/switch/hub/README.md](cmake/switch/hub/README.md)：

```bash
export PATH="$DEVKITPRO/portlibs/switch/bin:$DEVKITPRO/devkitA64/bin:$DEVKITPRO/tools/bin:$PATH"
aarch64-none-elf-cmake -G Ninja -S cmake/switch -B build-switch -DCMAKE_BUILD_TYPE=Release
cmake --build build-switch --parallel 2
# 出力：build-switch/artifacts/*.nro
```

</details>

## 謝辞とライセンス

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — System 4.x コアエンジン・パーサー
- [kichikuou/system3-sdl2](https://github.com/kichikuou/system3-sdl2) — 初期 System 1–3 ランタイム
- [kichikuou/xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) — System 3.x（`xsystem35`）エンジン
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Android 移植基盤フレームワーク

[GNU GPL-2.0](xsystem4/COPYING) のもとで公開されています（あわせて [xsystem35/COPYING](xsystem35/COPYING) も参照）。

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub?ref=badge_large)
