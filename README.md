<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>AliceSoft System 1–4 多平台原生游戏启动器</strong></p>
  <p>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build Android APK, iOS IPA, and Switch NROs" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <a href="https://app.fossa.com/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub?ref=badge_shield"><img src="https://app.fossa.com/api/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub.svg?type=shield" alt="FOSSA Status" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Android%20%28arm64--v8a%29-3DDC84.svg" alt="Android arm64-v8a" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-iOS%2015%2B-007AFF.svg" alt="iOS 15+" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://img.shields.io/badge/Platform-Nintendo%20Switch-E60012.svg" alt="Nintendo Switch homebrew" /></a>
    <img src="https://img.shields.io/badge/Engine-System%201%E2%80%934-FF7043.svg" alt="Engine: System 1–4" />
    <img src="https://img.shields.io/badge/QQ%20%E4%BA%A4%E6%B5%81%E7%BE%A4-383917628-12B7F5.svg" alt="QQ 交流群: 383917628" />
  </p>
  <p><strong>简体中文</strong> | <a href="README.en.md"><strong>English</strong></a> | <a href="README.ja.md"><strong>日本語</strong></a></p>
</div>

---

**AliceHub** 是在 **Android、iOS / iPadOS 和 Nintendo Switch（Homebrew）** 上原生运行 AliceSoft **System 1–4** 游戏的开源启动器与管理工具。三套原生引擎通过 SDL2 运行游戏，无需 Wine、Winlator 或 x86/x64 指令转译。安装包不包含游戏，请自行准备有权使用的游戏资源。

## 下载与快速上手

Android 和 iOS 安装包见 [**最新 Release**](https://github.com/TomorrowX6/AliceHub/releases/latest)；Nintendo Switch 构建见 GitHub Actions：

| 平台 | 系统要求 | 安装包 |
| :--- | :--- | :--- |
| **Android** | Android 8.0（API 26）及以上、`arm64-v8a`、OpenGL ES 3.0 | `AliceHub-<版本>-arm64-v8a.apk`，下载后直接安装 |
| **iOS / iPadOS** | 15.0 及以上、arm64 | `AliceHub-<版本>-ios-unsigned.ipa`，**未签名**，需自行签名并侧载 |
| **Nintendo Switch** | 可通过 Homebrew Menu 运行 NRO，建议使用应用模式 | GitHub Actions 产物 `AliceHub-switch`（启动器和三个引擎） |

也可在 [GitHub Actions → Build](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml) 的成功构建中下载 `AliceHub-arm64-v8a` 或 `AliceHub-ios-unsigned`，解压后获取 APK 或 IPA（下载构建产物需登录 GitHub）。

### Android

安装 APK，打开 **AliceHub**，选择游戏条目，或从右上角菜单选择 **导入其他游戏**。可以导入包含完整游戏资源的文件夹、`.zip` / `.rar` / `.7z` 压缩包或 `.iso` / `.mdf` 光盘镜像。导入后点击 **开始游戏**。

### iOS / iPadOS

1. 使用 AltStore、SideStore 或 Sideloadly 签名并侧载 IPA。使用免费 Apple 账户签名通常需每 7 天续签一次；TrollStore 仅适用于它支持的设备与系统版本。
2. 打开 **AliceHub → 导入游戏**，选择 **文件夹 / 多碟** 或 **压缩包 / 光盘**；也可用「文件」App 将完整游戏文件夹复制到本机 **AliceHub → Games**。
3. 在游戏库打开已导入的条目并点击 **开始游戏**。正常退出游戏会返回游戏库。

Android 和 iOS 的**在线安装**默认关闭。可在设置中阅读并同意使用条款后开启，再从游戏条目下载可用的资源。准备足够空间存放游戏文件及安装时的临时数据。

### Nintendo Switch（Homebrew）

1. 从 [GitHub Actions → Build](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml) 的成功构建中下载并解压 `AliceHub-switch`（需登录 GitHub）。将 `AliceHub.nro`、`system3.nro`、`xsystem35.nro` 和 `xsystem4.nro` 一并复制到 SD 卡的 `/switch/`。
2. 从 Homebrew Menu 以应用模式打开 **AliceHub**。在游戏库按 **X** 搜索，按 **Y** 进入设置，或按 **A** 打开游戏详情并选择 **Start game**。可通过 **Settings → Import local game** 从 SD 卡导入文件夹、`.zip`、`.iso` 或 `.mdf`。也可将解压后的游戏放到下列目录，每个游戏单独一个子目录：

| 引擎 | 游戏目录示例 |
| :--- | :--- |
| `system3.nro`（早期 System 1–3） | `/switch/alicehub/system3/games/<游戏>/` |
| `xsystem35.nro`（System 3.5–3.9） | `/switch/alicehub/xsystem35/games/<游戏>/` |
| `xsystem4.nro`（System 4） | `/switch/alicehub/xsystem4/games/<游戏>/` |

存档单独写入对应引擎的 `saves/<游戏>/`。如果当前加载器不支持从 AliceHub 启动其他 NRO，可以从 Homebrew Menu 打开对应引擎的 NRO，并使用内置游戏选择器。

#### Switch 功能

- **游戏库与下载**：三个引擎共用一个游戏库，支持搜索、按名称或引擎排序，以及复制和删除游戏。在 **Online access** 中阅读并同意条款后启用网络访问，即可从 **Online Download** 安装目录中的游戏，下载支持断点续传。
- **补丁与存档**：为兼容作品安装或移除 MOD、汉化补丁和独立去码补丁。存档管理器支持在 SD 卡上导入、导出、备份、恢复及在文件夹之间同步；《兰斯 10》支持离线存档编辑。
- **操控与局内菜单**：支持全局和单游戏手柄配置及虚拟触控按键。游戏中按 **L + R**（横握 Joy-Con 时为 **SL + SR**，键盘为 **F1**）打开菜单，可截图或快进；部分游戏支持局内修改，System 4 的 PartsEngine 界面可调节文字和滚动条大小。

## System 1–4 兼容性与功能

Android 和 iOS 启动器及 Switch 本地导入功能会根据游戏根目录中的文件识别引擎。下表列出常见的识别标记，不代表完整游戏只需这些文件。

| 游戏类型 | 原生引擎 | 常见识别标记 |
| :--- | :--- | :--- |
| **早期 System 1–3** | `system3-sdl2` | `ADISK.DAT`、`ACG.DAT` 等 |
| **System 3.5–3.9** | `xsystem35` | `System39.ini`、`System39.ain`、`*SA.ALD`、`ADISK.ALD` |
| **System 4.x** | `xsystem4` | `AliceStart.ini`、`System40.ini`、`*.ain`（不含 `System39.ain`） |

### Android 与 iOS 功能概览

- **游戏库与导入**：内置目录支持搜索、排序及「熟肉 / 生肉」标识，并可导入目录外的作品。支持多碟导入、随附补丁及兼容作品的 MOD 管理。兰斯 10 可切换或新建目录，分别保留各版本的存档与 MOD。
- **存档与工具**：支持存档导入、导出与备份；部分游戏支持存档编辑、局内修改器、主播模式或显示缩放。iOS 可选用「文件」中的文件夹（例如 iCloud 云盘）同步存档。
- **操控与音频**：System 3.5–3.9 和 System 4.x 支持可编辑虚拟手柄与物理手柄映射；早期 DAT 版作品使用原生触控。两端均支持 `xsystem35` 的 MIDI 音乐。
- **后台安装**：Android 使用前台服务；iOS 26 及以上可通过系统任务继续后台处理。旧版 iOS 的后台时间有限，超时后会暂停，可回到应用继续。

### 近期游戏兼容性修复

- 修复《兰斯 VI》读取原版迷宫行走数据时的载入失败，并兼容受旧版写入问题影响的行走数据。
- 修复《夏娃年代记 兰斯版》汉化版的宝箱技能提示、读档确认和奖励技能文字乱码，以及《战国兰斯》汉化版的快进按钮和启动署名。
- 修复《妻中蜜 3》和《超昂天使 艾斯卡蕾雅 Reboot》的背景语音音量控制；在《兰斯 02》《战国兰斯》《兰斯 08》《兰斯 09》中使用内置修改器后，受影响的界面数值会刷新。
- 修复《再婚妈妈》的场景选择崩溃，以及该作和《超昂神骑》的青色文字残片；修复《太太的恢复术》的重复立绘和对话框按钮文字错位。

## Android 功能详解

### 1. 三套原生引擎与移动端优化
- **早期 System 1–3（`system3-sdl2`）**：运行采用 DAT 资源的早期作品，例如《兰斯 I》《兰斯 II》《兰斯 III》的原版。
- **System 3.5–3.9（`xsystem35`）**：
  - 支持 System 3.5 ~ 3.9 场景字节码与扩展模块（`ShGraph`、`ShSound`、`ShString`、`SACT`、`eDemo` 等）、`ALD` / `ALK` / `DRI` 资源包、`PMS` / `QNT` / `VSP` / `JPEG` / `WebP` 图像渲染，以及 `System 3.9`（`System39.ain`）存档格式。
  - 原生兼容 **Shift-JIS / GBK / Unicode** 多编码文本排版，内置 `SDL2_mixer` 虚拟 CD 音轨播放（支持 `playlist.txt` 挂载 MP3 / Ogg 音轨）与 Android 原生 MIDI 合成器，完整接入局内悬浮窗、虚拟手柄/外设映射与实时状态修改。
- **System 4.x 原生内核（`xsystem4`）**：
  - 覆盖早期 System 4.0（`AIN v0 ~ v11`、`SACT2`、`StoatSpriteEngine`、`DrawDungeon` / `ReignEngine` 3D 迷宫与蒙皮渲染、`ALD` 资源包）至后期大型项目（`AIN v12 ~ v14.1`、`PartsEngine`、`SealEngine` 3D UBO 骨骼变换、`ELNA` / `FLAT v15` 骨骼与粒子动画、`APEG` / `PMPEG` 视频流、`SaveData v26/v27` 及多 `.afa` / `.ex` 优先级覆盖）。
- **图形恢复与帧率调度**：
  - 支持后台 `EGLContext` 丢失后的纹理、离屏 FBO、图像滤镜与 3D 迷宫资源自动重建，窗口恢复即时重绘。
  - 渲染（`-8`）与混音（`-16`）高优先级线程调度，接入 Android 12+ **ADPF（`APerformanceHintManager`）** 动态提频与 60fps VSync 对齐，全面适配 Android 15+ `16 KB` 页面对齐（`max-page-size=16384`），切入后台自动挂起音频混音与 MIDI 输出。

### 2. 聚合启动主页、独立项目管理与全局设置
- **聚合主页与通用导入**：展示内置与导入的游戏条目，自动从 PE 可执行文件提取图标，并显示安装状态。
- **独立项目中心**：每个条目拥有专属管理页，按引擎与项目特性自动适配本体安装、多实例目录切换、无损 MOD / 汉化补丁管理、隔离存档目录管理、专属修改器及后台异步卸载清理。
- **全局设置页**（主页右上角齿轮图标）：
  - **主题模式**：支持 `自动`、`亮色`、`深色` 三档实时切换。
  - **界面语言**：支持 `跟随系统`、`简体中文`、`日本語`、`English` 四档即时切换，同步覆盖系统通知栏、手柄编辑器与局内悬浮窗。
  - **在线安装开关**：默认关闭；点击开启时弹出完整《免责声明与使用条款》，开启后展示在线安装入口与精确包体大小。
  - **虚拟手柄与外设中心**：统一配置全局默认与单项目独立的虚拟按键布局及物理控制器映射。
  - **清空缓存与关于**：区分常规临时缓存与断点续传分片缓存并一键安全清理；内置 **QQ 交流群**、**开源许可证** 与 **GitHub** 仓库入口。

### 3. 多格式资源导入、前台服务保活与无损扩展管理
- **全格式本地安装与光盘直读**：
  - 内置 `libarchive` + `liblzma` 原生流式解压引擎与 **ISO9660 / Joliet 光盘镜像提取器**，支持直接从 `.zip` / `.rar` / `.7z` 压缩包（含固实压缩与大字典）、**`.iso` / `.mdf` 光盘镜像（自动识别 2048 / 2352 / 2448 扇区布局）** 或外部文件夹导入。
  - 支持多盘目录（`Disk1` / `Disk2` 等）与分盘安装集自动合并、随附更新补丁自动覆盖、冗余安装文件清理，以及 UTF-8 / UTF-16LE / GBK / Shift-JIS 文件名编码识别与乱码自动修复。
- **流式在线安装与前台保活**：
  - 在线安装（于设置中开启后可用）支持流式下载、包体体积预展示、HTTP `Range` 断点续传与逐文件 SHA-256 完整性校验，无需双倍解压暂存空间。
  - 前台服务可在切回桌面或息屏时继续下载与解压，并在进度弹窗与系统通知栏显示进度、网速及暂停和取消操作。
- **无损 MOD 与汉化补丁管理**：
  - 支持在线查看版本列表、包体体积与更新日志，执行一键增量更新、基础包 + 补丁自动组合安装及历史版本切换；本地导入时自动识别并规范化标准版本号。
  - 覆盖前自动原子备份原版文件，随时支持一键 **卸载还原**。

### 4. 虚拟手柄、物理控制器与可视化布局编辑
- **多预设虚拟按键与全屏编辑器**：内置面向视觉小说（GAL / ADV）与迷宫探索（RPG）等多套布局预设；提供全屏可视化拖拽编辑器，支持自由添加/删除按钮、十字键与虚拟摇杆，独立调节坐标、尺寸缩放、不透明度、圆形/胶囊/方形外观、点按/锁定（Toggle）模式、组合键与快捷指令（显隐虚拟键、切换虚拟光标、局内截图、打开菜单），支持网格吸附与撤销操作。
- **物理外设即插即用**：原生适配蓝牙与 USB 有线手柄（Xbox / PlayStation / Switch Pro 等），支持 A/B 与 X/Y 键位互换、摇杆死区与光标速度调节，可将左右摇杆与十字键映射为虚拟鼠标光标、滚轮、方向键或 WASD。

### 5. 桌面双向存档互通、局内悬浮工具与诊断报告
- **存档管理与桌面格式互通**：
  - 按游戏目录隔离存档，支持压缩包、文件夹或单文件导入导出；导入覆盖前自动备份，并支持恢复备份。
  - 针对移动端 `X4SS` 结构化存档，导出时自动转换为桌面端原版 `GD\x01\x01` 序列化格式，实现 PC 与 Android 双向无损互通。
- **离线修改与局内实时悬浮工具**：
  - **通用移动端增强**：左上角可拖动悬浮球、可锁定快进状态的 **`⏩ Ctrl`** 悬浮键、一键局内截图，以及针对 PartsEngine 的 **字体大小实时缩放（`75%~160%`，防溢出重排）** 与 **滚动条加宽放大（`100%~300%`）**。
  - **数值编辑与实时锁定**：部分游戏支持离线存档编辑、全解锁存档导入还原、局内实时数值调节和状态锁定。
- **结构化错误诊断报告**：运行异常或原生崩溃信号触发时展示结构化诊断卡片，支持一键复制详情、导出 `.txt` 日志文件及调用系统分享。

---

## 触控与手柄操作

早期 System 1–3 DAT 版作品使用原生触控。下表适用于 Android 的 System 3.5–3.9 和 System 4.x。iOS 请在 **设置 → 手柄设置** 中配置手柄；在 System 4 游戏中，可另行开启 **长按快进（按住 Ctrl）**。

| 运行时操作 | 触控手势 | 物理手柄默认映射（可自定义） |
| :--- | :--- | :--- |
| **鼠标左键 / 确认** | 单指轻触目标位置 | `A` / `R3`（左摇杆控制虚拟光标） |
| **鼠标右键 / 返回 / 菜单** | 双指轻触屏幕任意位置，或单指轻触左右黑边区域 | `B` |
| **对话快进（`Ctrl`）** | 单指长按屏幕，或点击 **`⏩ Ctrl`** 悬浮键锁定快进 | `RT`（锁定切换） |
| **鼠标滚轮上下翻页** | 双指在屏幕上向下 / 向上滑动 | `LB` / `RB` 或右摇杆上下推动 |
| **光标悬停 / 拖拽滚动** | 单指按住并滑动 | 左摇杆移动光标 |
| **悬浮菜单 / 虚拟按键显隐** | 点击左上角悬浮图标 | `Start`（菜单）/ `Back`（光标）/ `Guide`（虚拟键） |
| **退出确认弹窗** | 系统返回手势 / 返回键 | `LT`（`Esc`）或系统返回键 |

---

## 从源码构建

### Android APK

安装 JDK 17、Android SDK（平台 API 34）、Android NDK、CMake 3.22+ 和 Ninja。以下以 Ubuntu 为例，将 `YOUR_NDK_VERSION` 替换为已安装的 NDK 版本：

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

输出：`project/app/build/outputs/apk/release/app-release.apk`。没有配置 Release 密钥时，本地构建默认使用调试密钥，不能直接覆盖签名不同的已安装版本。

### iOS IPA 或模拟器应用

在 macOS 上安装 Xcode 和 iOS SDK（CI 使用 Xcode 26）；构建模拟器应用时还需安装模拟器运行时。以下命令从仓库根目录运行：

```bash
brew install cmake ninja autoconf automake libtool bison flex pkgconf xcodegen
ios/build-app.sh ipa  # ios/build/AliceHub.ipa（未签名）
ios/build-app.sh sim  # 构建 arm64 模拟器应用
```

构建细节与开发检查见 [iOS 开发文档](ios/README.md)。

### Nintendo Switch NRO

使用带 Switch portlibs 的 devkitPro / devkitA64 环境（CI 使用 `devkitpro/devkita64` 镜像）。在该镜像中安装主机侧依赖，然后从仓库根目录构建：

```bash
apt-get update && apt-get install -y --no-install-recommends bison flex gcc libc6-dev python3 python3-pil zlib1g-dev libarchive-dev pkg-config libpng-dev libjpeg-dev
export PATH="$DEVKITPRO/portlibs/switch/bin:$DEVKITPRO/devkitA64/bin:$DEVKITPRO/tools/bin:$PATH"
aarch64-none-elf-cmake -G Ninja -S cmake/switch -B build-switch -DCMAKE_BUILD_TYPE=Release
cmake --build build-switch --parallel 2
```

四个 NRO 文件输出到 `build-switch/artifacts/`，安装时一并复制到 SD 卡的 `/switch/`。开发说明见 [Switch Hub 开发文档](cmake/switch/hub/README.md)。

---

## 致谢与开源许可

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — 跨平台 System 4.x 核心引擎与格式解析库。
- [kichikuou/system3-sdl2](https://github.com/kichikuou/system3-sdl2) — 早期 System 1–3 游戏的运行引擎。
- [kichikuou/xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) — 跨平台 System 3.x（xsystem35）核心引擎与模块实现。
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Android 移植基础框架。

本项目基于 [GNU General Public License v2.0 (GPL-2.0)](xsystem4/COPYING) 协议开源（同时参见 [xsystem35/COPYING](xsystem35/COPYING)）。

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub?ref=badge_large)
