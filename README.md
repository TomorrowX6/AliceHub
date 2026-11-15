<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>面向 System 4.x 架构的 Android 原生运行引擎与聚合管理中心</strong></p>
  <p>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build APK" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Android%20%28arm64--v8a%29-3DDC84.svg" alt="Platform" /></a>
    <img src="https://img.shields.io/badge/QQ%20%E4%BA%A4%E6%B5%81%E7%BE%A4-383917628-12B7F5.svg" alt="QQ 交流群: 383917628" />
  </p>
  <p><strong>简体中文</strong> | <a href="README.en.md"><strong>English</strong></a> | <a href="README.ja.md"><strong>日本語</strong></a></p>
</div>

---

**AliceHub**（`io.github.alicehub`）是基于 [xsystem4](https://github.com/nunuhara/xsystem4) 与 [xsystem4-android](https://github.com/kichikuou/xsystem4-android) 深度定制与扩展的 **System 4.x** Android 原生运行引擎与本地资源管理中心。

项目采用纯 C/C++、SDL2 与 OpenGL ES 3.0 编写，在 `arm64-v8a` Android 设备上直接解释执行 AIN 字节码并硬件加速渲染原版资源，无需借助 Wine、Winlator 或任何 x86/x64 指令转译层。除兼容早期 System 4.0 架构外，重点针对采用 **AIN v12 ~ v14.1**、**PartsEngine**、**FLAT v15 骨骼与粒子动画**、**SealEngine 3D**、**APEG 视频流** 以及 **SaveData v26 / v27** 的后期大型项目与社区扩展补丁进行了完整实现与移动端深度调优。

---

## 核心特性

### 1. 聚合启动主页、独立项目管理与全局设置
- **简洁直观的聚合主页**：卡片式展示内置支持条目、高清图标及实时状态角标（`未安装`、`已安装`、`已安装 · MOD`、`安装中…`、`运行中`）。
- **独立项目中心**：每个条目拥有独立的专属管理页，按项目特性自动适配本体安装、多实例目录切换、无损 MOD 管理、隔离存档目录管理、修改器及右上角资源清理菜单。
- **全局设置页**（主页右上角齿轮图标）：
  - **主题模式**：支持 `自动`、`亮色`、`深色` 三档实时切换。
  - **界面语言**：支持 `跟随系统`、`简体中文`、`日本語`、`English` 四档即时切换，同步覆盖系统通知栏与局内悬浮窗。
  - **在线安装开关**：默认关闭；点击开启时弹出完整七条《免责声明与使用条款》，开启后展示在线安装入口与精确包体大小。
  - **清空缓存**：实时统计临时解压、下载暂存与分享缓存占用，支持一键安全清理且不影响已安装资源与存档。
  - **社区与关于**：内置 **QQ 交流群**、**开源许可证** 与 **GitHub** 仓库入口。

### 2. 双模式资源安装、后台服务与无损扩展管理
- **本地与在线安装**：
  - **本地安装**：内置基于 `libarchive` + `liblzma` 的原生流式解压引擎，支持直接从 `.zip` / `.rar` / `.7z` 压缩包（含固实压缩与大字典）或外部文件夹导入，自动识别 UTF-8 / UTF-16LE / GBK 文件名。
  - **在线安装**（于设置中开启后可用）：支持流式下载、包体体积预展示、HTTP `Range` 断点续传与逐文件 SHA-256 完整性校验，无需双倍解压暂存空间。
- **前台服务后台保活**：支持一键转入后台运行，依托前台服务与唤醒锁在切回桌面或息屏状态下持续满速传输与解压，并在进度弹窗与系统通知栏同步提供实时进度、网速、**暂停**与**取消**控制。
- **无损 MOD 与补丁管理**：
  - 支持在线查看版本列表、包体体积与更新日志，执行一键增量补丁更新、基础包 + 补丁自动组合安装及历史版本切换；本地导入压缩包或文件夹时自动识别并规范化标准版本号，与在线目录无缝对齐。
  - 安装时自动备份被覆盖的原版文件，随时支持一键 **卸载 MOD** 无损还原。
- **现代化诊断报告**：运行异常时展示结构化错误诊断卡片，支持一键复制详情、导出 `.txt` 日志文件及调用系统分享。

### 3. 存档管理与局内实时悬浮工具
- **存档管理**：
  - 与桌面端原版存档格式双向互通（`SaveData` v26/v27 `.asd` 与 `RSave` `.rsd`，关键全局数据 `fsync` 原子落盘）。
  - 支持压缩包、文件夹、单文件多模式导入与导出，支持多存档目录切换及单文件备份、重命名与删除。
- **离线修改与局内悬浮窗**：
  - **通用移动端增强**：游戏内左上角可拖动悬浮图标、可锁定快进状态的 **`⏩ Ctrl`** 悬浮键、**字体大小实时缩放（`75%~160%`，防溢出重排）** 以及 **滚动条加宽放大（`100%~300%`）**。
  - **数值编辑与实时锁定**：支持离线存档编辑、全解锁存档导入还原，以及局内实时数值调节与状态锁定开关。

### 4. 原生 System 4.x 引擎与移动端性能调优
- **AIN v12 ~ v14.1 虚拟机与完整 HLL 库**：完整支持 AIN v12–v14.1 指令集（含 `IFACE_WRAP`、`CALLMETHOD`、`X_*` 系列操作码、委托 `DG_*`、结构体深拷贝）、`PartsEngine` 全 804 个接口、`SealEngine`、`ADVEngine`、`SaveData`、`RSave`、`APEG`、`SoundEngine`、`HashMap` 及多 `.afa` / `.ex` 优先级覆盖解析。
- **PartsEngine、FLAT v15 与 3D 渲染管线**：OpenGL ES 3.0 离屏 FBO 合成、zlib 压缩 `ELNA` / `FLAT` v15 骨骼与粒子动画、3D 欧拉角透视翻转与背面剔除，以及完整 `cproc` 图像滤镜与文本排版工序。
- **高刷帧率节奏与低功耗调度**：渲染（`-8`）与混音（`-16`）高优先级线程调度，接入 Android 12+ **ADPF（`APerformanceHintManager`）** 动态提频与 60fps VSync 对齐，兼容 Android 15+ `16 KB` 页面对齐，切入后台自动挂起音频混音。

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
2. 安装并打开 **AliceHub**，选择对应条目进入管理页：
   - 点击 **本地安装**，从本地 `.zip` / `.rar` / `.7z` 压缩包或文件夹导入完整运行资源（含 `AliceStart.ini` / `System40.ini`、`*.ain`、`*.afa` 等）。
   - 如需使用在线通道，可在主页右上角 **设置** 中阅读并同意《免责声明与使用条款》后开启 **在线安装**。
3. 点击 **开始游戏** 启动运行；运行过程中可随时点击左上角悬浮图标调节字号、滚动条、快进及辅助功能。

---

## 触控操作说明

| 运行时操作 | 触控手势 |
| :--- | :--- |
| **鼠标左键 / 确认** | 单指轻触目标位置 |
| **鼠标右键 / 返回 / 菜单** | 双指轻触屏幕任意位置，或单指轻触左右黑边区域 |
| **对话快进（`Ctrl`）** | 单指长按屏幕，或点击 **`⏩ Ctrl`** 悬浮键锁定快进 |
| **鼠标滚轮上下翻页** | 双指在屏幕上向下 / 向上滑动 |
| **光标悬停 / 拖拽滚动** | 单指按住并滑动 |
| **退出确认弹窗** | 系统返回手势 / 返回键 |

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

# 1. 交叉编译全部 arm64-v8a 原生动态库
./build-shared-libs.sh

# 2. 构建签名 Release APK
cd project
./gradlew assembleRelease
```

构建完成后，安装包输出路径为 `project/app/build/outputs/apk/release/app-release.apk`。

---

## 致谢与开源许可

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — 跨平台 System 4 核心引擎与格式解析库。
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — xsystem4 Android 移植基础框架。

本项目基于 [GNU General Public License v2.0 (GPL-2.0)](xsystem4/COPYING) 协议开源。
