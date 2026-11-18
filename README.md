<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>面向 System 3.x / System 4.x 架构的 Android 双内核原生运行引擎与聚合管理中心</strong></p>
  <p>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build APK" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Android%20%28arm64--v8a%29-3DDC84.svg" alt="Platform" /></a>
    <img src="https://img.shields.io/badge/Engine-System%203.x%20%26%204.x-FF7043.svg" alt="Engine: System 3.x & 4.x" />
    <img src="https://img.shields.io/badge/QQ%20%E4%BA%A4%E6%B5%81%E7%BE%A4-383917628-12B7F5.svg" alt="QQ 交流群: 383917628" />
  </p>
  <p><strong>简体中文</strong> | <a href="README.en.md"><strong>English</strong></a> | <a href="README.ja.md"><strong>日本語</strong></a></p>
</div>

---

**AliceHub**（`io.github.alicehub`）是基于 [xsystem4](https://github.com/nunuhara/xsystem4)、[xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) 与 [xsystem4-android](https://github.com/kichikuou/xsystem4-android) 深度定制与扩展的 **System 3.x & System 4.x** Android 双内核原生运行引擎与本地资源聚合管理中心。

项目采用纯 C/C++、SDL2 与 OpenGL ES 3.0 编写，在 `arm64-v8a` Android 设备上直接解释执行原版脚本字节码并硬件加速渲染封包资源，无需借助 Wine、Winlator 或任何 x86/x64 指令转译层。启动器可根据资源目录自动识别 **System 3.x**（`System39.ini`、`*SA.ALD`、`ADISK.ALD`）与 **System 4.x**（`AliceStart.ini`、`System40.ini`、`*.ain`）架构并透明调度对应原生内核（`libxsystem35.so` / `libxsystem4.so`），完整覆盖从经典 **System 3.5 ~ 3.9**、早期 **System 4.0** 到采用 **AIN v12 ~ v14.1**、**PartsEngine**、**FLAT v15**、**SealEngine / ReignEngine 3D** 的历代作品及社区汉化与扩展补丁。

---

## 核心特性

### 1. System 3.x + System 4.x 双原生内核与移动端深度调优
- **System 3.x 原生内核（`xsystem35`）**：
  - 完整支持 System 3.5 ~ 3.9 场景字节码与扩展模块（`ShGraph`、`ShSound`、`ShString`、`SACT`、`eDemo` 等）、`ALD` / `ALK` / `DRI` 资源包、`PMS` / `QNT` / `VSP` / `JPEG` / `WebP` 图像渲染，以及 `System 3.9`（`System39.ain`）存档格式。
  - 原生兼容 **Shift-JIS / GBK / Unicode** 多编码文本排版，内置 `SDL2_mixer` 虚拟 CD 音轨播放（支持 `playlist.txt` 挂载 MP3 / Ogg 音轨）与 Android 原生 MIDI 合成器，完整接入局内悬浮窗、虚拟手柄/外设映射与实时状态修改。
- **System 4.x 全代际内核（`xsystem4`）**：
  - 向上与向下兼容早期 System 4.0（`AIN v0 ~ v11`、`SACT2`、`StoatSpriteEngine`、`DrawDungeon` / `ReignEngine` 3D 迷宫与蒙皮渲染、`ALD` 资源包）至后期大型项目（`AIN v12 ~ v14.1` 完整指令集、`PartsEngine` 全 804 个接口、`SealEngine` 3D UBO 骨骼变换、`ELNA` / `FLAT v15` 骨骼与粒子动画、`APEG` / `PMPEG` 视频流、`SaveData v26/v27` 及多 `.afa` / `.ex` 优先级覆盖）。
- **图形弹性恢复与高刷低功耗调度**：
  - 支持后台 `EGLContext` 丢失后的纹理、离屏 FBO、图像滤镜与 3D 迷宫资源自动重建，窗口恢复即时重绘。
  - 渲染（`-8`）与混音（`-16`）高优先级线程调度，接入 Android 12+ **ADPF（`APerformanceHintManager`）** 动态提频与 60fps VSync 对齐，全面适配 Android 15+ `16 KB` 页面对齐（`max-page-size=16384`），切入后台自动挂起音频混音与 MIDI 输出。

### 2. 聚合启动主页、独立项目管理与全局设置
- **聚合主页与通用导入**：卡片式展示多款深度适配预设条目，并支持通过 **导入其他游戏** 添加任意 System 3.x / System 4.x 外部项目；自动从 PE 可执行文件提取高清图标，实时展示状态角标（`未安装`、`已安装`、`已安装 · MOD`、`已安装 · 汉化`、`安装中…`、`运行中`）。
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
  - 依托前台服务与唤醒锁在切回桌面或息屏状态下持续满速传输与解压，并在进度弹窗与系统通知栏同步提供实时进度、网速、**暂停**与**取消**控制。
- **无损 MOD 与汉化补丁管理**：
  - 支持在线查看版本列表、包体体积与更新日志，执行一键增量更新、基础包 + 补丁自动组合安装及历史版本切换；本地导入时自动识别并规范化标准版本号。
  - 覆盖前自动原子备份原版文件，随时支持一键 **卸载还原**。

### 4. 虚拟手柄、物理控制器与可视化布局编辑
- **多预设虚拟按键与全屏编辑器**：内置面向视觉小说（GAL / ADV）与迷宫探索（RPG）等多套布局预设；提供全屏可视化拖拽编辑器，支持自由添加/删除按钮、十字键与虚拟摇杆，独立调节坐标、尺寸缩放、不透明度、圆形/胶囊/方形外观、点按/锁定（Toggle）模式、组合键与快捷指令（显隐虚拟键、切换虚拟光标、局内截图、打开菜单），支持网格吸附与撤销操作。
- **物理外设即插即用**：原生适配蓝牙与 USB 有线手柄（Xbox / PlayStation / Switch Pro 等），支持 A/B 与 X/Y 键位互换、摇杆死区与光标速度调节，可将左右摇杆与十字键灵活映射为虚拟鼠标光标、滚轮翻页、方向键或 WASD，无缝驱动 System 3.x 与 System 4.x 双引擎。

### 5. 桌面双向存档互通、局内悬浮工具与诊断报告
- **存档管理与桌面格式互通**：
  - 支持 System 3.x（`.asd`）与 System 4.x（`SaveData` v26/v27 `.asd`、`RSave` `.rsd`、`.sav`）多目录隔离管理与压缩包/文件夹/单文件导入导出，关键全局数据采用 `fsync` 原子落盘；导入覆盖前自动备份原存档并支持一键恢复备份。
  - 针对移动端 `X4SS` 结构化存档，导出时自动转换为桌面端原版 `GD\x01\x01` 序列化格式，实现 PC 与 Android 双向无损互通。
- **离线修改与局内实时悬浮工具**：
  - **通用移动端增强**：左上角可拖动悬浮球、可锁定快进状态的 **`⏩ Ctrl`** 悬浮键、一键局内截图，以及针对 PartsEngine 的 **字体大小实时缩放（`75%~160%`，防溢出重排）** 与 **滚动条加宽放大（`100%~300%`）**。
  - **数值编辑与实时锁定**：支持离线存档编辑、全解锁存档导入还原，以及覆盖多款 System 3.x / System 4.x 内置条目的局内实时数值调节与状态锁定开关。
- **结构化错误诊断报告**：运行异常或原生崩溃信号触发时展示结构化诊断卡片，支持一键复制详情、导出 `.txt` 日志文件及调用系统分享。

---

## 系统与硬件要求

| 项目 | 最低要求 | 推荐配置 |
| :--- | :--- | :--- |
| **处理器架构** | `arm64-v8a`（64 位 ARM） | 骁龙 8 系列 / 天玑 8000+ 或同级芯片 |
| **操作系统** | Android 8.0（API 26）及以上 | Android 12.0（API 31）及以上（支持 ADPF 与无缝帧率） |
| **图形接口** | OpenGL ES 3.0 | OpenGL ES 3.2 |
| **运行内存** | 4 GB RAM | 6 GB RAM 及以上 |
| **存储空间** | 建议预留 5 GB 以上可用空间 | UFS 3.0+ 闪存 |

---

## 快速上手

1. 前往 [**Releases**](https://github.com/TomorrowX6/AliceHub/releases) 下载最新签名安装包，或在 [**GitHub Actions**](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml) 获取最新自动构建产物。
2. 安装并打开 **AliceHub**，选择预设条目进入管理页，或点击主页右上角菜单中的 **导入其他游戏**：
   - **本地安装**：从本地 `.zip` / `.rar` / `.7z` 压缩包、`.iso` / `.mdf` 光盘镜像或文件夹导入完整运行资源：
     - **System 3.x 资源**：包含 `*SA.ALD` / `ADISK.ALD`、`*.ALD`、`System39.ini` / `System39.ain` 等（如需挂载 CD 音轨，可在游戏根目录放置 `playlist.txt` 指向 MP3 / Ogg 曲目）。
     - **System 4.x 资源**：包含 `AliceStart.ini` / `System40.ini`、`*.ain`、`*.afa` / `*.ald` 等。
   - **在线安装**：可在主页右上角 **设置** 中阅读并同意《免责声明与使用条款》后开启 **在线安装**。
3. 点击 **开始游戏** 启动运行（应用将自动识别架构并调用 `xsystem35` 或 `xsystem4` 内核）；运行中可随时点击左上角悬浮图标调节快进、虚拟手柄、截图、字号缩放及辅助功能。

---

## 触控与手柄操作说明

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

### 1. 环境准备
推荐使用 Linux（Ubuntu 22.04 / 24.04）或 macOS，安装 **JDK 17**、**Android SDK（API 34+）**、**Android NDK（r26d–r29）**、**CMake 3.22+** 与 **Ninja**：

```bash
sudo apt-get update
sudo apt-get install -y ninja-build bison flex autoconf automake libtool pkg-config cmake git python3 openjdk-17-jdk
```

### 2. 编译与打包

```bash
git clone https://github.com/TomorrowX6/AliceHub.git
cd AliceHub

export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME=/path/to/android-sdk/ndk/26.1.10909125

# 1. 交叉编译全部 arm64-v8a 原生动态库（libxsystem4.so / libxsystem35.so / libSDL2.so / libarchive_jni.so 等）
./build-shared-libs.sh

# 2. 构建签名 Release APK
cd project
./gradlew assembleRelease
```

构建完成后，安装包输出路径为 `project/app/build/outputs/apk/release/app-release.apk`。

---

## 致谢与开源许可

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — 跨平台 System 4.x 核心引擎与格式解析库。
- [kichikuou/xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) — 跨平台 System 3.x（xsystem35）核心引擎与模块实现。
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Android 移植基础框架。

本项目基于 [GNU General Public License v2.0 (GPL-2.0)](xsystem4/COPYING) 协议开源（同时参见 [xsystem35/COPYING](xsystem35/COPYING)）。
