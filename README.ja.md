<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>System 4.x アーキテクチャ向けネイティブ Android ランタイム＆統合管理ハブ</strong></p>
  <p>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build APK" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Android%20%28arm64--v8a%29-3DDC84.svg" alt="Platform" /></a>
  </p>
  <p><a href="README.md"><strong>简体中文</strong></a> | <a href="README.en.md"><strong>English</strong></a> | <strong>日本語</strong></p>
</div>

---

**AliceHub**（`io.github.alicehub`）は、[xsystem4](https://github.com/nunuhara/xsystem4) および [xsystem4-android](https://github.com/kichikuou/xsystem4-android) をベースに独自拡張・最適化された **System 4.x** 向けネイティブ Android ランタイムエンジン＆ローカル資産管理ハブです。

純粋な C/C++、SDL2、OpenGL ES 3.0 で実装されており、Wine や Winlator などの x86/x64 エミュレーション層を介さずに `arm64-v8a` Android デバイス上で AIN バイトコードを直接実行します。従来の System 4.0 互換性に加え、**AIN v12〜v14.1**、**PartsEngine**、**FLAT v15 スケルタル＆パーティクルアニメーション**、**SealEngine 3D**、**APEG 動画ストリーム**、および **SaveData v26 / v27** を採用する大規模プロジェクト向けに完全な実装とモバイル最適化を提供します。

---

## 主な機能

### 1. 統合ホーム・専用管理ハブ・グローバル設定
- **統合ホーム画面**：高解像度アイコンとリアルタイム状態バッジ（`未導入`、`導入済`、`導入済 · MOD`、`導入中…`、`実行中`）を一覧表示。
- **エントリ別管理ハブ**：本体導入、複数ディレクトリ切替、非破壊 MOD 管理、個別セーブ管理、補助ツール、および右上ゴミ箱メニューからのクリーン削除に対応。
- **設定画面**（ホーム右上の歯車アイコン）：
  - **テーマ**：`自動`・`ライト`・`ダーク` のリアルタイム切替。
  - **言語**：`システム`・`简体中文`・`日本語`・`English` の即時切替（通知およびオーバーレイにも反映）。
  - **オンラインインストール切替**：デフォルト OFF。有効化時に利用規約・免責事項を表示し、有効時はパッケージ容量を自動表示。
  - **情報**：**オープンソースライセンス** 表示および **GitHub** リポジトリリンクを収録。

### 2. デュアルモード導入・バックグラウンド通信・MOD 管理
- **ローカル＆オンライン導入**：
  - **ローカルインストール**：`libarchive` + `liblzma` ストリーミング展開により、`.zip` / `.rar` / `.7z` アーカイブまたはフォルダから直接ファイルを導入（UTF-8 / UTF-16LE / GBK ファイル名自動判別）。
  - **オンラインインストール**（設定で有効化時）：パッケージ容量表示・HTTP `Range` レジューム・ファイル単位の SHA-256 検証付きストリーミング導入に対応。
- **フォアグラウンドサービス通知**：バックグラウンド移行や画面オフ時もフルスピードで処理を継続し、通知バーから進捗確認や一時停止が可能。
- **非破壊 MOD 管理**：更新履歴・容量表示付きのオンラインカタログ更新、差分パッチ適用、ローカルアーカイブ／フォルダ導入に対応し、元ファイルの自動バックアップによりワンタップで初期状態へ復元可能。

### 3. セーブ管理＆リアルタイムオーバーレイツール
- **セーブ管理**：PC 版セーブフォーマットとの双方向互換（`SaveData` v26/v27 `.asd` および `RSave` `.rsd`、`fsync` アトミック保存）、インポート・エクスポート、ディレクトリ切替、個別バックアップ・名前変更・削除に対応。
- **オフライン編集＆フローティングオーバーレイ**：ドラッグ可能なフローティングアイコン、**`⏩ Ctrl`** 早送りロックボタン、リアルタイム **文字サイズ変更（`75%〜160%`）**、**スクロールバー拡大（`100%〜300%`）**、セーブ編集、およびリアルタイム数値・ステータス補助機能を搭載。

### 4. ネイティブ System 4.x エンジン＆最適化
- **AIN v12〜v14.1 仮想マシン＆全 HLL 実装**：`IFACE_WRAP`・`CALLMETHOD`・`X_*` 命令・デリゲート `DG_*`、`PartsEngine` 全 804 関数、`SealEngine`、`ADVEngine`、`APEG`、複数 `.afa` / `.ex` 優先度解決を実装。
- **GLES 3.0・FLAT v15・ADPF ペーシング**：オフスクリーン FBO 合成、`ELNA` / `FLAT` v15 スケルタル＆パーティクル描画、3D 透視回転、Android 12+ **ADPF** 動的クロック制御、60fps VSync ペーシング、16 KB ページアライメント（Android 15+ 対応）、バックグラウンド時の音声自動休止に対応。

---

## 動作環境

| 項目 | 最小要件 | 推奨環境 |
| :--- | :--- | :--- |
| **CPU アーキテクチャ** | `arm64-v8a`（64ビット ARM） | Snapdragon 8 シリーズ / Dimensity 8000+ 同等以上 |
| **OS バージョン** | Android 8.0（API 26）以上 | Android 12.0（API 31）以上 |
| **グラフィックス** | OpenGL ES 3.0 | OpenGL ES 3.2 |
| **メモリ** | 4 GB RAM | 6 GB RAM 以上 |
| **ストレージ空き容量** | 5 GB 以上の空き容量を推奨 | UFS 3.0 以上のフラッシュストレージ |

---

## クイックスタート

1. [**Releases**](https://github.com/TomorrowX6/AliceHub/releases) または [**GitHub Actions**](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml) から最新の署名済み APK を取得してインストールします。
2. **AliceHub** を起動し、対象エントリの管理ハブを開きます：
   - 手元の PC 版ファイル一式（`AliceStart.ini` / `System40.ini`、`*.ain`、`*.afa` 等）は **ローカルインストール** からアーカイブまたはフォルダを選択して導入します。
   - オンライン導入を利用する場合は、ホーム右上の **設定** から利用規約に同意して有効化します。
3. **ゲーム開始** をタップして起動し、画面左上のフローティングアイコンから文字サイズ・早送り・各種補助機能をいつでも調整できます。

---

## タッチ操作一覧

| 操作内容 | タッチジェスチャー |
| :--- | :--- |
| **左クリック / 決定** | 1本指でタップ |
| **右クリック / キャンセル / メニュー** | 2本指でタップ、または左右の黒帯エリアを1本指でタップ |
| **テキストスキップ（`Ctrl`）** | 1本指で長押し、または **`⏩ Ctrl`** ボタンをタップして固定 |
| **マウスホイール上下** | 2本指で上下にスワイプ |
| **マウスホバー / ドラッグ** | 1本指で押さえたままスライド |
| **終了確認ダイアログ** | システムの戻る操作 |

---

## ソースからのビルド

```bash
sudo apt-get update
sudo apt-get install -y ninja-build bison flex autoconf automake libtool pkg-config cmake git python3 openjdk-17-jdk

git clone https://github.com/TomorrowX6/AliceHub.git
cd AliceHub

export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME=/path/to/android-sdk/ndk/26.1.10909125

./build-shared-libs.sh
cd project && ./gradlew assembleRelease
```

生成された APK は `project/app/build/outputs/apk/release/app-release.apk` に出力されます。

---

## 謝辞とライセンス

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — System 4 コアエンジンおよびフォーマットパーサー。
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — xsystem4 Android 移植基盤。

本プロジェクトは [GNU General Public License v2.0 (GPL-2.0)](xsystem4/COPYING) のもとで公開されています。
