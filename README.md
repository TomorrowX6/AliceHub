<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>AliceSoft System 1–4 多平台原生启动器</strong></p>
  <p>
    <a href="https://alice.000.moe/"><img src="https://img.shields.io/badge/Website-alice.000.moe-111116.svg" alt="Website" /></a>
    <a href="https://store.000.moe/"><img src="https://img.shields.io/badge/Store-store.000.moe-E14B86.svg" alt="Self-Hosted Store" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <img src="https://img.shields.io/badge/QQ%20%E4%BA%A4%E6%B5%81%E7%BE%A4-383917628-12B7F5.svg" alt="QQ 交流群: 383917628" />
  </p>
  <p><strong>简体中文</strong> | <a href="README.en.md"><strong>English</strong></a> | <a href="README.ja.md"><strong>日本語</strong></a></p>
</div>

---

**AliceHub** 是在 **Android、iOS / iPadOS 与 Nintendo Switch（Homebrew）** 上原生运行 AliceSoft **System 1–4** 游戏的开源启动器与管理工具。基于 SDL2 原生运行，无需 Wine、Winlator 或 x86 转译。**不包含任何游戏本体**，请自行准备有权使用的游戏文件。

## 下载与自建软件源

前往 [官网](https://alice.000.moe/)、[最新 Release](https://github.com/TomorrowX6/AliceHub/releases/latest) 或 [自建源主页](https://store.000.moe/) 获取安装包（开发构建见 [Actions → Build](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml)）：

| 平台 | 系统要求 | 安装包 | 官方自建源 |
| :--- | :--- | :--- | :--- |
| **Android** | Android 8.0+ · `arm64-v8a` | `*.apk` 直接安装 | [F-Droid](https://store.000.moe/#fdroid)：`https://store.000.moe/fdroid/repo` |
| **iOS / iPadOS** | iOS 15+ · `arm64` | `*.ipa`（未签名，需自签侧载） | [SideStore / AltStore](https://store.000.moe/#sidestore)：`https://store.000.moe/altstore/source.json` |
| **Nintendo Switch** | Homebrew（建议应用模式） | `*.zip`（解压 4 个 NRO 至 `/switch/`） | [HBAS](https://store.000.moe/#hbas)：`https://store.000.moe/hbas`（[`repos.json`](https://store.000.moe/hbas/repos.json)） |

- **F-Droid 源 SHA-256 指纹**：`48620D9F64F02B26196550D138B7014D24B3C22C2C2DC168F0838ACC454ACDFA`
- **Switch HBAS**：将 [`repos.json`](https://store.000.moe/hbas/repos.json) 放至 SD 卡 `/switch/appstore/.get/repos.json`，或在现有配置中加入上述仓库地址。

## 核心特性

- **三引擎原生支持**：内置 `system3-sdl2`（早期 System 1–3）、`xsystem35`（System 3.5–3.9）与 `xsystem4`（System 4.x），根据游戏目录自动识别引擎。
- **多格式本地导入**：支持文件夹、`.zip` / `.rar` / `.7z` 压缩包与 `.iso` / `.mdf` 光盘镜像，支持多碟合并与文件名编码修复。
- **存档互通与同步**：按游戏隔离存档并支持备份/恢复，可自动导出 **Windows PC 兼容存档**；iOS 支持通过「文件」/ iCloud 同步。
- **MOD 与补丁管理**：无损安装汉化补丁与 MOD，覆盖前自动备份原文件并支持一键还原；支持 System 4 单游戏启动入口切换与多实例目录。
- **触控与手柄**：支持自定义虚拟按键布局与蓝牙/USB 实体手柄映射，内置局内菜单（快进、截图、实时修改、PartsEngine 字体与滚动条缩放）。

## 快速上手

- **Android**：安装 APK → 点击右上角菜单导入游戏（或选择目录条目）→ 开始游戏。可在设置中切换应用专用目录或共享目录（`内部存储 → AliceHub`）。
- **iOS / iPadOS**：使用 SideStore、AltStore 或 Sideloadly 签名侧载 IPA → 通过 **导入游戏** 或「文件」App 复制到 `AliceHub → Games` 即可启动。
- **Nintendo Switch**：将 `AliceHub.nro`、`system3.nro`、`xsystem35.nro`、`xsystem4.nro` 一并放入 `/switch/`，以应用模式打开 **AliceHub**，按 **Y** 进入设置导入本地游戏，或手动放至 `/switch/alicehub/<system3|xsystem35|xsystem4>/games/<游戏>/`。游戏中按 **L + R**（横握 Joy-Con 为 **SL + SR**）打开局内菜单。

> 各端**在线下载**功能默认关闭，可在设置中阅读并同意使用条款后开启。

<details>
<summary><strong>从源码构建（Android / iOS / Nintendo Switch）</strong></summary>

### Android APK

需要 JDK 17、Android SDK（API 34）、NDK、CMake 3.22+ 与 Ninja：

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/YOUR_NDK_VERSION"
./build-shared-libs.sh
(cd project && ./gradlew assembleRelease)
# 输出：project/app/build/outputs/apk/release/app-release.apk
```

### iOS IPA / 模拟器

需要 macOS 与 Xcode（CI 使用 Xcode 26），详见 [ios/README.md](ios/README.md)：

```bash
brew install cmake ninja autoconf automake libtool bison flex pkgconf xcodegen
ios/build-app.sh ipa  # ios/build/AliceHub.ipa（未签名）
ios/build-app.sh sim  # arm64 模拟器应用
```

### Nintendo Switch NRO

需要 devkitPro / devkitA64 环境（CI 使用 `devkitpro/devkita64`），详见 [cmake/switch/hub/README.md](cmake/switch/hub/README.md)：

```bash
export PATH="$DEVKITPRO/portlibs/switch/bin:$DEVKITPRO/devkitA64/bin:$DEVKITPRO/tools/bin:$PATH"
aarch64-none-elf-cmake -G Ninja -S cmake/switch -B build-switch -DCMAKE_BUILD_TYPE=Release
cmake --build build-switch --parallel 2
# 输出：build-switch/artifacts/*.nro
```

</details>

## 致谢与许可

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — System 4.x 引擎与格式解析库
- [kichikuou/system3-sdl2](https://github.com/kichikuou/system3-sdl2) — 早期 System 1–3 运行引擎
- [kichikuou/xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) — System 3.x（xsystem35）引擎
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Android 移植基础框架

基于 [GNU GPL-2.0](xsystem4/COPYING) 协议开源（另见 [xsystem35/COPYING](xsystem35/COPYING)）。
