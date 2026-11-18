<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>System 3.x / System 4.x アーキテクチャ向けデュアルカーネル・ネイティブ Android ランタイム＆統合管理ハブ</strong></p>
  <p>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build APK" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Android%20%28arm64--v8a%29-3DDC84.svg" alt="Platform" /></a>
    <img src="https://img.shields.io/badge/Engine-System%203.x%20%26%204.x-FF7043.svg" alt="Engine: System 3.x & 4.x" />
  </p>
  <p><a href="README.md"><strong>简体中文</strong></a> | <a href="README.en.md"><strong>English</strong></a> | <strong>日本語</strong></p>
</div>

---

**AliceHub**（`io.github.alicehub`）は、[xsystem4](https://github.com/nunuhara/xsystem4)、[xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2)、および [xsystem4-android](https://github.com/kichikuou/xsystem4-android) をベースに独自拡張・最適化された **System 3.x & System 4.x** 向けデュアルカーネル・ネイティブ Android ランタイムエンジン＆ローカル資産管理ハブです。

純粋な C/C++、SDL2、OpenGL ES 3.0 で実装されており、Wine や Winlator などの x86/x64 エミュレーション層を介さずに `arm64-v8a` Android デバイス上で原本シナリオバイトコードの直接実行とハードウェアアクセラレーション描画を行います。ディレクトリ構成から **System 3.x**（`System39.ini`、`*SA.ALD`、`ADISK.ALD`）と **System 4.x**（`AliceStart.ini`、`System40.ini`、`*.ain`）を自動判別して対応するネイティブエンジン（`libxsystem35.so` / `libxsystem4.so`）を起動し、クラシックな **System 3.5〜3.9**、初期 **System 4.0** から **AIN v12〜v14.1**、**PartsEngine**、**FLAT v15**、**SealEngine / ReignEngine 3D** を採用する後期大規模タイトルおよび拡張パッチまで幅広く対応します。

---

## 主な機能

### 1. System 3.x + System 4.x デュアルネイティブエンジン＆モバイル最適化
- **System 3.x ネイティブカーネル（`xsystem35`）**：
  - System 3.5〜3.9 のシナリオバイトコードおよび拡張モジュール（`ShGraph`、`ShSound`、`ShString`、`SACT`、`eDemo` 等）、`ALD` / `ALK` / `DRI` アーカイブ、`PMS` / `QNT` / `VSP` / `JPEG` / `WebP` 画像描画、`System 3.9`（`System39.ain`）セーブ形式を完全サポート。
  - **Shift-JIS / GBK / Unicode** マルチエンコーディング描画、`SDL2_mixer` 仮想 CD オーディオ再生（`playlist.txt` による MP3 / Ogg トラック指定）、Android ネイティブ MIDI シンセサイザーを内蔵し、フローティングオーバーレイ・仮想パッド／物理コントローラー・リアルタイム補助機能と完全連携。
- **全世代対応 System 4.x カーネル（`xsystem4`）**：
  - 初期 System 4.0（`AIN v0〜v11`、`SACT2`、`StoatSpriteEngine`、`DrawDungeon` / `ReignEngine` 3D ダンジョン＆スキニング描画、`ALD` アーカイブ）から後期大規模タイトル（`AIN v12〜v14.1` 全命令セット、`PartsEngine` 全 804 関数、`SealEngine` 3D UBO ボーン変換、`ELNA` / `FLAT v15` スケルタル＆パーティクルアニメーション、`APEG` / `PMPEG` 動画再生、`SaveData v26/v27`、複数 `.afa` / `.ex` 優先度解決）まで網羅。
- **グラフィックス復旧・高リフレッシュレート制御・省電力設計**：
  - バックグラウンド移行時の `EGLContext` 消失後にテクスチャ・オフスクリーン FBO・フィルタ・3D ダンジョンリソースを自動再構築し、ウィンドウ復帰時に即座に再描画。
  - 描画（`-8`）およびオーディオ（`-16`）高優先度スレッド制御、Android 12+ **ADPF（`APerformanceHintManager`）** 動的クロック制御、60fps VSync ペーシング、Android 15+ 向け `16 KB` ページアライメント（`max-page-size=16384`）、バックグラウンド時の音声／MIDI 自動一時停止に対応。

### 2. 統合ホーム・専用管理ハブ・グローバル設定
- **統合ホーム＆汎用インポート**：プリセットタイトルに加え、**他のゲームを導入** から任意の System 3.x / System 4.x 作品を自由に追加可能。PE 実行ファイルから高解像度アイコンを自動抽出し、リアルタイム状態バッジ（`未導入`、`導入済`、`導入済 · MOD`、`導入中…`、`実行中`）を表示。
- **エントリ別管理ハブ**：本体導入、複数ディレクトリ切替、非破壊 MOD・パッチ管理、個別セーブ管理、専用補助ツール、バックグラウンド非同期アンインストールを作品特性に応じて自動構成。
- **設定画面**（ホーム右上の歯車アイコン）：
  - **テーマ**：`自動`・`ライト`・`ダーク` のリアルタイム切替。
  - **言語**：`システム`・`简体中文`・`日本語`・`English` の即時切替（通知・パッドエディタ・オーバーレイにも反映）。
  - **オンライン導入切替**：デフォルト OFF。有効化時に利用規約・免責事項を表示し、パッケージ容量を明示。
  - **パッド設定**：全体デフォルトおよび作品別の仮想パッド配置・物理コントローラー割り当てを一元管理。
  - **キャッシュ消去＆情報**：通常の一時データとレジューム用分割キャッシュを区別して安全に整理可能。**オープンソースライセンス** 表示および **GitHub** リンクを収録。

### 3. マルチフォーマット資産導入・フォアグラウンド通信・非破壊 MOD 管理
- **アーカイブ＆ディスクイメージ直接導入**：
  - `libarchive` + `liblzma` ネイティブストリーミング展開に加え、**ISO9660 / Joliet ディスクイメージ展開エンジン** を内蔵。`.zip` / `.rar` / `.7z` アーカイブ（ソリッド圧縮・大辞書対応）、**`.iso` / `.mdf` ディスクイメージ（2048 / 2352 / 2448 セクタ自動判別）**、または外部フォルダから直接導入可能。
  - 複数ディスクフォルダ（`Disk1` / `Disk2` 等）や分割インストールデータの自動統合、同梱アップデートパッチの自動適用、不要ファイルの整理、および UTF-8 / UTF-16LE / GBK / Shift-JIS ファイル名の自動判別と文字化け修復に対応。
- **ストリーミング導入＆フォアグラウンドサービス**：
  - オンライン導入（設定で有効化時）では容量事前表示・HTTP `Range` レジューム・ファイル単位の SHA-256 検証に対応し、2倍の一時領域を必要とせず直接展開。
  - フォアグラウンドサービスと WakeLock により画面オフやバックグラウンド移行時もフルスピードで継続し、進捗・通信速度表示および **一時停止**・**キャンセル** 操作を提供。
- **非破壊 MOD・パッチ管理**：
  - 更新履歴・容量付きのオンラインカタログ更新、差分パッチ適用、バージョン自動認識付きのローカル導入に対応し、上書き前の自動アトミックバックアップによりワンタップで初期状態へ復元可能。

### 4. 仮想ゲームパッド・物理コントローラー・ビジュアルレイアウトエディタ
- **複数プリセット＆フルスクリーン編集**：ノベル（GAL / ADV）向け・ダンジョン RPG 向け等のプリセットを収録。フルスクリーンのドラッグ＆ドロップ編集画面からボタン・十字キー・スティックの追加／削除、位置・サイズ・不透明度・形状（円／ピル／四角）・タップ／トグル動作・同時押しコンボ・ショートカット（パッド表示切替、仮想カーソル切替、スクリーンショット、メニュー起動）の調整、グリッドスナップ、アンドゥに対応。
- **物理コントローラー対応**：Bluetooth / USB 接続の各種コントローラー（Xbox / PlayStation / Switch Pro 等）にネイティブ対応。A/B・X/Y 入れ替え、デッドゾーン・カーソル速度調整、左右スティックや十字キーへの仮想マウスカーソル／ホイール／方向キー／WASD 割り当てに対応し、System 3.x / System 4.x 両エンジンで共通動作。

### 5. PC 双方向セーブ互換・フローティングツール・診断レポート
- **セーブ管理＆PC 形式自動変換**：
  - System 3.x（`.asd`）および System 4.x（`SaveData` v26/v27 `.asd`、`RSave` `.rsd`、`.sav`）のディレクトリ分離管理、アーカイブ／フォルダ／単一ファイルのインポート・エクスポート、`fsync` アトミック保存、上書き前の自動バックアップとワンタップ復元に対応。
  - モバイル向け `X4SS` 構造化セーブデータはエクスポート時に Windows PC 原版の `GD\x01\x01` 形式へ自動変換され、PC ⇄ Android 間の双方向互換を実現。
- **オフライン編集＆ゲーム内フローティングツール**：
  - **モバイル補助機能**：ドラッグ可能な左上フローティングボタン、**`⏩ Ctrl`** 早送りロックボタン、ワンタップスクリーンショット、および PartsEngine 向けのリアルタイム **文字サイズ変更（`75%〜160%`）**・**スクロールバー拡大（`100%〜300%`）**。
  - **セーブ＆リアルタイム補助**：オフラインセーブ編集、全開放セーブ導入／復元、および対応する System 3.x / System 4.x タイトル向けのリアルタイム数値調整・ステータス固定スイッチを搭載。
- **構造化エラー診断レポート**：実行時エラーやネイティブクラッシュ発生時に構造化診断カードを表示し、ワンタップコピー・`.txt` ログ出力・システム共有に対応。

---

## 動作環境

| 項目 | 最小要件 | 推奨環境 |
| :--- | :--- | :--- |
| **CPU アーキテクチャ** | `arm64-v8a`（64ビット ARM） | Snapdragon 8 シリーズ / Dimensity 8000+ 同等以上 |
| **OS バージョン** | Android 8.0（API 26）以上 | Android 12.0（API 31）以上（ADPF・シームレスフレーム制御対応） |
| **グラフィックス** | OpenGL ES 3.0 | OpenGL ES 3.2 |
| **メモリ** | 4 GB RAM | 6 GB RAM 以上 |
| **ストレージ空き容量** | 5 GB 以上の空き容量を推奨 | UFS 3.0 以上のフラッシュストレージ |

---

## クイックスタート

1. [**Releases**](https://github.com/TomorrowX6/AliceHub/releases) または [**GitHub Actions**](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml) から最新の署名済み APK を取得してインストールします。
2. **AliceHub** を起動し、対象エントリの管理ハブを開くか、右上メニューの **他のゲームを導入** を選択します：
   - **ローカル導入**：`.zip` / `.rar` / `.7z` アーカイブ、`.iso` / `.mdf` ディスクイメージ、またはフォルダから PC 版ファイル一式を導入します：
     - **System 3.x 資産**：`*SA.ALD` / `ADISK.ALD`、`*.ALD`、`System39.ini` / `System39.ain` 等（CD-DA 音源を再生する場合はゲームルートに MP3 / Ogg を指定した `playlist.txt` を配置）。
     - **System 4.x 資産**：`AliceStart.ini` / `System40.ini`、`*.ain`、`*.afa` / `*.ald` 等。
   - **オンライン導入**：利用する場合はホーム右上の **設定** から利用規約に同意して有効化します。
3. **ゲーム開始** をタップして起動します（構成に応じて `xsystem35` または `xsystem4` が自動選択されます）。実行中は画面左上のフローティングアイコンから早送り・仮想パッド・スクリーンショット・文字サイズ・各種補助機能をいつでも調整できます。

---

## タッチ＆コントローラー操作一覧

| 操作内容 | タッチジェスチャー | 物理コントローラー初期割り当て（変更可） |
| :--- | :--- | :--- |
| **左クリック / 決定** | 1本指でタップ | `A` / `R3`（左スティックで仮想カーソル移動） |
| **右クリック / キャンセル / メニュー** | 2本指でタップ、または左右の黒帯エリアを1本指でタップ | `B` |
| **テキストスキップ（`Ctrl`）** | 1本指で長押し、または **`⏩ Ctrl`** ボタンをタップして固定 | `RT`（トグル固定） |
| **マウスホイール上下** | 2本指で上下にスワイプ | `LB` / `RB` または右スティック上下 |
| **マウスホバー / ドラッグ** | 1本指で押さえたままスライド | 左スティックでカーソル移動 |
| **オーバーレイメニュー / 仮想パッド** | 左上のフローティングアイコンをタップ | `Start`（メニュー）/ `Back`（カーソル）/ `Guide`（仮想パッド） |
| **終了確認ダイアログ** | システムの戻る操作 | `LT`（`Esc`）またはシステムの戻るボタン |

---

## ソースからのビルド

```bash
sudo apt-get update
sudo apt-get install -y ninja-build bison flex autoconf automake libtool pkg-config cmake git python3 openjdk-17-jdk

git clone https://github.com/TomorrowX6/AliceHub.git
cd AliceHub

export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME=/path/to/android-sdk/ndk/26.1.10909125

# 1. arm64-v8a ネイティブ共有ライブラリ一式をクロスビルド（libxsystem4.so / libxsystem35.so / libSDL2.so / libarchive_jni.so 等）
./build-shared-libs.sh

# 2. 署名済み Release APK をビルド
cd project && ./gradlew assembleRelease
```

生成された APK は `project/app/build/outputs/apk/release/app-release.apk` に出力されます。

---

## 謝辞とライセンス

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — クロスプラットフォーム System 4.x コアエンジンおよびフォーマットパーサー。
- [kichikuou/xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) — クロスプラットフォーム System 3.x（`xsystem35`）コアエンジンおよびモジュール実装。
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Android 移植基盤フレームワーク。

本プロジェクトは [GNU General Public License v2.0 (GPL-2.0)](xsystem4/COPYING) のもとで公開されています（あわせて [xsystem35/COPYING](xsystem35/COPYING) も参照してください）。
