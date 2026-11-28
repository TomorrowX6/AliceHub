<div align="center">
  <img src="project/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AliceHub Icon" />
  <h1>AliceHub</h1>
  <p><strong>アリスソフトの System 1–4 に対応するマルチプラットフォームのネイティブゲームランチャー</strong></p>
  <p>
    <a href="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml"><img src="https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml/badge.svg" alt="Build Android APK, iOS IPA, and Switch NROs" /></a>
    <a href="xsystem4/COPYING"><img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Android%20%28arm64--v8a%29-3DDC84.svg" alt="Android arm64-v8a" /></a>
    <a href="https://app.fossa.com/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub?ref=badge_shield"><img src="https://app.fossa.com/api/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub.svg?type=shield" alt="FOSSA Status" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-iOS%2015%2B-007AFF.svg" alt="iOS 15+" /></a>
    <a href="https://github.com/TomorrowX6/AliceHub/releases"><img src="https://img.shields.io/badge/Platform-Nintendo%20Switch-E60012.svg" alt="Nintendo Switch homebrew" /></a>
    <img src="https://img.shields.io/badge/Engine-System%201%E2%80%934-FF7043.svg" alt="Engine: System 1–4" />
  </p>
  <p><a href="README.md"><strong>简体中文</strong></a> | <a href="README.en.md"><strong>English</strong></a> | <strong>日本語</strong></p>
</div>

---

**AliceHub**は、アリスソフトの **System 1–4** 作品を **Android、iOS / iPadOS、Nintendo Switch（Homebrew）** でネイティブ実行・管理するオープンソースのランチャーです。SDL2 と3つのネイティブエンジンを使うため、Wine、Winlator、x86/x64 命令変換は不要です。ゲームデータは同梱されません。利用権のあるゲームファイルをご用意ください。

## ダウンロードとクイックスタート

Android、iOS、Nintendo Switch のパッケージはいずれも [**最新リリース**](https://github.com/TomorrowX6/AliceHub/releases/latest) からダウンロードできます。

| プラットフォーム | 動作環境 | パッケージ |
| :--- | :--- | :--- |
| **Android** | Android 8.0（API 26）以降、`arm64-v8a`、OpenGL ES 3.0 | `AliceHub-<バージョン>-arm64-v8a.apk`（そのままインストール可能） |
| **iOS / iPadOS** | 15.0 以降、arm64 | `AliceHub-<バージョン>-ios-unsigned.ipa`（**未署名**、署名してサイドロード） |
| **Nintendo Switch** | Homebrew Menu で NRO を起動できる環境（アプリケーションモードを推奨） | `AliceHub-<バージョン>-switch.zip`（展開して4つの NRO を SD カードの `/switch/` に配置） |

[GitHub Actions → Build](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml) の成功した実行から `AliceHub-arm64-v8a`、`AliceHub-ios-unsigned`、`AliceHub-switch` をダウンロードして最新コミットのビルドを取得することもできます。成果物のダウンロードには GitHub へのログインが必要です。

### 公式セルフホストリポジトリ（F-Droid / SideStore / HBAS）

公式セルフホストストア（[store.000.moe](https://store.000.moe/)）から導入・アップデートすることもできます。

| プラットフォーム | 対応クライアント | リポジトリ URL |
| :--- | :--- | :--- |
| **Android** | F-Droid / Droid-ify / Neo Store | `https://store.000.moe/fdroid/repo`（SHA-256 フィンガープリント：`48620D9F64F02B26196550D138B7014D24B3C22C2C2DC168F0838ACC454ACDFA`） |
| **iOS / iPadOS** | SideStore / AltStore | `https://store.000.moe/altstore/source.json` |
| **Nintendo Switch** | Homebrew App Store (HBAS) | `https://store.000.moe/hbas`（または [`repos.json`](https://store.000.moe/hbas/repos.json) を SD カードの `/switch/appstore/.get/repos.json` へ配置） |

### Android

APK をインストールして **AliceHub** を開き、ゲームを選択するか、右上のメニューから **他のゲームを導入** を選びます。ゲームファイル一式を含むフォルダ、`.zip` / `.rar` / `.7z` アーカイブ、`.iso` / `.mdf` ディスクイメージを読み込めます。導入後は **ゲーム開始** をタップします。

アップデート後の案内では、アプリ専用フォルダを引き続き使うこともでき、既存のゲーム・セーブ・バックアップはその場に残ります。共有ストレージを選んで権限を許可した場合、移行可能なセーブとバックアップのみが「内部ストレージ → AliceHub」配下の `saves` と `save_backups` に移動されます。容量の大きい導入済みゲームは移動されずそのまま起動でき、新しくダウンロード・インポートしたゲームは `AliceHub/games` に保存されます。移行できないデータは元の場所に残ります。設定でアプリ専用フォルダに戻しても移行済みデータは戻りません。旧バージョンは共有フォルダを自動では読み込まないため、ダウングレードが必要な場合は移行前の場所を保持するか、バックアップして旧版側で再インポートしてください。アンインストールするとアプリ専用データが消去されるため、ダウングレードのために先にアンインストールしないでください。

### iOS / iPadOS

1. AltStore、SideStore、Sideloadly などで IPA に署名し、サイドロードします。無料の Apple Account で署名した場合は通常 7 日ごとに更新が必要です。TrollStore は対応する端末と OS バージョンでのみ利用できます。
2. **AliceHub → ゲームをインポート** から **フォルダ / 複数ディスク** または **アーカイブ / ディスクイメージ** を選びます。「ファイル」アプリで端末内の **AliceHub → Games** にゲームフォルダ一式をコピーすることもできます。
3. ライブラリで取り込んだゲームを開き、**プレイ** をタップします。ゲームを通常終了するとライブラリに戻ります。

Android と iOS の**オンラインインストール**は初期状態では無効です。設定で規約に同意して有効にすると、ゲームの項目から利用可能なファイルをダウンロードできます。ゲームファイルとインストール時の一時データに必要な空き容量を確保してください。

### Nintendo Switch（Homebrew）

1. [**最新リリース**](https://github.com/TomorrowX6/AliceHub/releases/latest) から `AliceHub-<バージョン>-switch.zip` をダウンロードして展開します（[GitHub Actions → Build](https://github.com/TomorrowX6/AliceHub/actions/workflows/build.yml) の `AliceHub-switch` も利用可能です）。`AliceHub.nro`、`system3.nro`、`xsystem35.nro`、`xsystem4.nro` の4つを SD カードの `/switch/` にコピーします。
2. Homebrew Menu からアプリケーションモードで **AliceHub** を開きます。ライブラリでは **X** で検索、**Y** で設定を開けます。**A** でゲームの詳細を開き、**Start game** を選ぶと起動します。**Settings → Import local game** から SD カード上のフォルダ、`.zip`、`.iso`、`.mdf` を取り込めます。展開済みのゲームを、作品ごとに個別のフォルダに分けて次の場所に置くこともできます。

| エンジン | ゲームフォルダの例 |
| :--- | :--- |
| `system3.nro`（初期の System 1–3） | `/switch/alicehub/system3/games/<ゲーム>/` |
| `xsystem35.nro`（System 3.5–3.9） | `/switch/alicehub/xsystem35/games/<ゲーム>/` |
| `xsystem4.nro`（System 4） | `/switch/alicehub/xsystem4/games/<ゲーム>/` |

セーブは各エンジンの `saves/<ゲーム>/` に分けて保存されます。ローダーが AliceHub から別の NRO を起動できない場合は、Homebrew Menu から各エンジンの NRO を直接開き、内蔵のゲーム選択画面を使ってください。

#### Switch の機能

- **ライブラリとダウンロード**：3つのエンジンのゲームを1つのライブラリで検索し、名前やエンジンでの並べ替え、コピーや削除、System 4 の作品別**起動エントリ**選択、アプリ内アップデート確認に対応します。規約を確認して同意し、**Online access** を有効にすると、**Online Download** からカタログ内のゲームをインストールできます。中断したダウンロードは再開できます。
- **パッチとセーブ**：対応作品の MOD、翻訳パッチ、単体の修正解除パッチをオンラインまたは SD カードの `.zip` / `.rar` / `.7z` からインストール・削除できます。セーブ管理では SD カード内でのインポート、エクスポート（**PC 互換セーブ** `PC_SaveData` 出力を含む）、バックアップ、復元、フォルダ間の同期が可能です。ランス10はオフラインでセーブを編集できます。
- **操作とゲーム内メニュー**：共通またはゲーム別のコントローラープロファイルと仮想タッチボタンを設定できます。プレイ中に **L + R**（Joy-Con 横持ちの場合は **SL + SR**、キーボードは **F1**）でメニューを開き、スクリーンショット撮影やスキップの切り替えができます。一部の作品ではプレイ中の数値変更ができ、System 4 の PartsEngine 画面では文字とスクロールバーのサイズを調整できます。

## System 1–4 の互換性と機能

Android と iOS のランチャー、および Switch のローカル取り込み機能は、ゲームのルートフォルダ内のファイルから使用するエンジンを判定します。以下は主な判定用ファイルであり、ゲームに必要なすべてのファイルではありません。

| ゲームの種類 | ネイティブエンジン | 主な判定用ファイル |
| :--- | :--- | :--- |
| **初期の System 1–3** | `system3-sdl2` | `ADISK.DAT`、`ACG.DAT` など |
| **System 3.5–3.9** | `xsystem35` | `System39.ini`、`System39.ain`、`*SA.ALD`、`ADISK.ALD` |
| **System 4.x** | `xsystem4` | `AliceStart.ini`、`AliceStart.CHS`、`System40.ini`、`*.ain`（`System39.ain` を除く） |

### Android と iOS の主な機能

- **ライブラリと取り込み**：検索、並び替え、中国語翻訳版と原語版の表示に対応。カタログ外の作品、複数ディスク、同梱パッチも取り込めます。System 4 の作品別**起動エントリ**切替、アプリ内アップデート確認、対応作品の MOD 管理にも対応します。ランス10ではフォルダの新規作成・切り替えができ、セーブと MOD はフォルダごとに管理されます。
- **セーブと補助機能**：セーブのインポート、エクスポート（Windows PC 互換形式への自動変換を含む）、バックアップと復元に対応します。対応作品ではセーブ編集、ゲーム内の改造ツール、配信者モード、表示サイズ調整を利用できます。iOS では「ファイル」で選んだフォルダ（iCloud Drive など）とセーブを同期できます。
- **操作と音声**：System 3.5–3.9 と System 4.x は編集可能な仮想パッドと物理コントローラーの割り当てに対応し、初期の DAT 形式の作品はネイティブのタッチ操作を使います。`xsystem35` の MIDI 音楽は両プラットフォームで再生できます。
- **バックグラウンドインストール**：Android はフォアグラウンドサービスを使用します。iOS 26 以降はシステムタスクで継続できます。それ以前の iOS はバックグラウンド実行時間に制限があり、上限に達すると一時停止します。アプリに戻ると再開できます。

## Android の機能詳細

### 1. 3つのネイティブエンジンとモバイル最適化
- **初期の System 1–3（`system3-sdl2`）**：『ランス I』『ランス II』『ランス III』のオリジナル版など、DAT 形式の作品を実行できます。
- **System 3.5–3.9（`xsystem35`）**：
  - System 3.5〜3.9 のシナリオバイトコードおよび拡張モジュール（`ShGraph`、`ShSound`、`ShString`、`SACT`、`eDemo` 等）、`ALD` / `ALK` / `DRI` アーカイブ、`PMS` / `QNT` / `VSP` / `JPEG` / `WebP` 画像描画、`System 3.9`（`System39.ain`）セーブ形式に対応。
  - **Shift-JIS / GBK / Unicode** マルチエンコーディング描画、`SDL2_mixer` 仮想 CD オーディオ再生（`playlist.txt` による MP3 / Ogg トラック指定）、Android ネイティブ MIDI シンセサイザーを内蔵し、フローティングオーバーレイ・仮想パッド／物理コントローラー・リアルタイム補助機能と完全連携。
- **System 4.x ネイティブエンジン（`xsystem4`）**：
  - 初期 System 4.0（`AIN v0〜v11`、`SACT2`、`StoatSpriteEngine`、`DrawDungeon` / `ReignEngine` 3D ダンジョン＆スキニング描画、`ALD` アーカイブ）から後期大規模タイトル（`AIN v12〜v14.1` 全命令セット、`PartsEngine`、`SealEngine` 3D UBO ボーン変換、`ELNA` / `FLAT v15` スケルタル＆パーティクルアニメーション、`APEG` / `PMPEG` 動画再生、`Ogg Vorbis / Opus` 音声、`SaveData v26/v27`、`<memory>` 仮想セーブ、複数 `.afa` / `.ex` 優先度解決）まで網羅。
  - `GBK`、`Shift-JIS` に加え、`CHS.TBL` / `UIF` / `word.mai` 等の文字置換テーブルと混在 CJK システムダイアログのデコードに対応。
- **グラフィックス復旧・高リフレッシュレート制御・省電力設計**：
  - バックグラウンド移行時の `EGLContext` 消失後にテクスチャ・オフスクリーン FBO・フィルタ・3D ダンジョンリソースを自動再構築し、ウィンドウ復帰時に即座に再描画。
  - 描画（`-8`）およびオーディオ（`-16`）高優先度スレッド制御、Android 12+ **ADPF（`APerformanceHintManager`）** 動的クロック制御、60fps VSync ペーシング、Android 15+ 向け `16 KB` ページアライメント（`max-page-size=16384`）、バックグラウンド時の音声／MIDI 自動一時停止に対応。

### 2. 統合ホーム・専用管理ハブ・グローバル設定
- **ライブラリとインポート**：カタログ内の作品と取り込んだ作品を表示します。PE 実行ファイルからアイコンを抽出し、インストール状態も表示します。
- **エントリ別管理ハブ**：本体導入、複数ディレクトリ切替、System 4 の**起動エントリ**選択、非破壊 MOD・パッチ管理、個別セーブ管理、専用補助ツール、バックグラウンド非同期アンインストールを作品特性に応じて自動構成。
- **設定画面**（ホーム右上の歯車アイコン）：
  - **保存先**：アプリ専用フォルダと共有フォルダ（`内部ストレージ → AliceHub`）の切替・セーブ移行に対応。
  - **テーマ**：`自動`・`ライト`・`ダーク` のリアルタイム切替。
  - **言語**：`システム`・`简体中文`・`日本語`・`English` の即時切替（通知・パッドエディタ・オーバーレイにも反映）。
  - **オンライン導入切替**：デフォルト OFF。有効化時に利用規約・免責事項を表示し、パッケージ容量を明示。
  - **パッド設定**：全体デフォルトおよび作品別の仮想パッド配置・物理コントローラー割り当てを一元管理。
  - **キャッシュ消去＆情報**：通常の一時データとレジューム用分割キャッシュを区別して安全に整理可能。アプリ内**アップデート確認**、**オープンソースライセンス** 表示、**GitHub** リンクを収録。

### 3. マルチフォーマット資産導入・フォアグラウンド通信・非破壊 MOD 管理
- **アーカイブ＆ディスクイメージ直接導入**：
  - `libarchive` + `liblzma` ネイティブストリーミング展開に加え、**ISO9660 / Joliet ディスクイメージ展開エンジン** を内蔵。`.zip` / `.rar` / `.7z` アーカイブ（ソリッド圧縮・大辞書対応）、**`.iso` / `.mdf` ディスクイメージ（2048 / 2352 / 2448 セクタ自動判別）**、または外部フォルダから直接導入可能。
  - 複数ディスクフォルダ（`Disk1` / `Disk2` 等）や分割インストールデータの自動統合、同梱アップデートパッチの自動適用、不要ファイルの整理、および UTF-8 / UTF-16LE / GBK / Shift-JIS ファイル名の自動判別と文字化け修復に対応。
- **ストリーミング導入＆フォアグラウンドサービス**：
  - オンライン導入（設定で有効化時）では容量事前表示・HTTP `Range` レジューム・ファイル単位の SHA-256 検証に対応し、2倍の一時領域を必要とせず直接展開。
  - フォアグラウンドサービスで、画面オフやバックグラウンド移行後もダウンロードと展開を継続できます。進捗や通信速度の表示、一時停止、キャンセルにも対応します。
- **非破壊 MOD・パッチ管理**：
  - 更新履歴・容量付きのオンラインカタログ更新、差分パッチ適用、バージョン自動認識付きのローカル導入に対応し、上書き前の自動アトミックバックアップによりワンタップで初期状態へ復元可能。

### 4. 仮想ゲームパッド・物理コントローラー・ビジュアルレイアウトエディタ
- **複数プリセット＆フルスクリーン編集**：ノベル（GAL / ADV）向け・ダンジョン RPG 向け等のプリセットを収録。フルスクリーンのドラッグ＆ドロップ編集画面からボタン・十字キー・スティックの追加／削除、位置・サイズ・不透明度・形状（円／ピル／四角）・タップ／トグル動作・同時押しコンボ・ショートカット（パッド表示切替、仮想カーソル切替、スクリーンショット、メニュー起動）の調整、グリッドスナップ、アンドゥに対応。
- **物理コントローラー対応**：Bluetooth / USB 接続のコントローラー（Xbox / PlayStation / Switch Pro 等）に対応。ボタンの入れ替え、デッドゾーンやカーソル速度の調整、仮想マウス／ホイール／方向キー／WASD の割り当てができます。

### 5. PC 双方向セーブ互換・フローティングツール・診断レポート
- **セーブ管理＆PC 形式自動変換**：
  - セーブをゲームフォルダごとに分けて管理します。アーカイブ／フォルダ／単一ファイルのインポートとエクスポート、上書き前の自動バックアップと復元に対応します。
  - **PC 互換セーブ**のエクスポート時には、モバイル向け `X4SS` 構造化セーブの変換や `GD\x01\x01` / `.gsave` / `.rsm` の修復（対応 `.ain` の構造定義を参照）を自動で行い、PC ⇄ モバイル間のシームレスな互換性を実現。
- **オフライン編集＆ゲーム内フローティングツール**：
  - **モバイル補助機能**：ドラッグ可能な左上フローティングボタン、**`⏩ Ctrl`** 早送りロックボタン、ワンタップスクリーンショット、および PartsEngine 向けのリアルタイム **文字サイズ変更（`75%〜160%`）**・**スクロールバー拡大（`100%〜300%`）**。
  - **セーブ＆リアルタイム補助**：対応作品ではオフラインでのセーブ編集、全開放セーブの導入と復元、プレイ中の数値調整と固定を利用できます。
- **構造化エラー診断レポート**：実行時エラーやネイティブクラッシュ発生時に構造化診断カードを表示し、ワンタップコピー・`.txt` ログ出力・システム共有に対応。

---

## タッチとコントローラー操作

初期の System 1–3 の DAT 形式の作品はネイティブのタッチ操作を使います。以下は Android の System 3.5–3.9 と System 4.x 向けです。iOS では **設定 → ゲームパッド設定** から操作を変更できます。System 4 では **長押しでスキップ（Ctrl 長押し）** も有効にできます。

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

### Android APK

JDK 17、Android SDK（プラットフォーム API 34）、Android NDK、CMake 3.22 以降、Ninja を用意してください。以下は Ubuntu の例です。`YOUR_NDK_VERSION` はインストール済みの NDK バージョンに置き換えてください。

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

出力先：`project/app/build/outputs/apk/release/app-release.apk`。リリース用の署名を設定していない場合はデバッグ鍵が使われるため、署名の異なるインストール済み APK を上書きできません。

### iOS IPA またはシミュレータ用アプリ

macOS に Xcode と iOS SDK をインストールします（CI は Xcode 26 を使用）。シミュレータ用アプリを使う場合は iOS Simulator ランタイムも必要です。リポジトリのルートで実行します。

```bash
brew install cmake ninja autoconf automake libtool bison flex pkgconf xcodegen
ios/build-app.sh ipa  # ios/build/AliceHub.ipa（未署名）
ios/build-app.sh sim  # arm64 シミュレータ用アプリ
```

ビルドと開発時の確認方法は [iOS 開発者向けドキュメント](ios/README.md) を参照してください。

### Nintendo Switch NRO

Switch 用 portlibs を含む devkitPro / devkitA64 環境を用意します（CI は `devkitpro/devkita64` イメージを使用）。そのイメージ内でホスト側の依存パッケージをインストールし、リポジトリのルートでビルドします。

```bash
apt-get update && apt-get install -y --no-install-recommends bison flex gcc libc6-dev python3 python3-pil zlib1g-dev libarchive-dev pkg-config libpng-dev libjpeg-dev
export PATH="$DEVKITPRO/portlibs/switch/bin:$DEVKITPRO/devkitA64/bin:$DEVKITPRO/tools/bin:$PATH"
aarch64-none-elf-cmake -G Ninja -S cmake/switch -B build-switch -DCMAKE_BUILD_TYPE=Release
cmake --build build-switch --parallel 2
```

4つの NRO は `build-switch/artifacts/` に出力されます。SD カードの `/switch/` にまとめてコピーしてください。詳細は [Switch Hub 開発者向けガイド](cmake/switch/hub/README.md) を参照してください。

---

## 謝辞とライセンス

- [nunuhara/xsystem4](https://github.com/nunuhara/xsystem4) & [nunuhara/libsys4](https://github.com/nunuhara/libsys4) — クロスプラットフォーム System 4.x コアエンジンおよびフォーマットパーサー。
- [kichikuou/system3-sdl2](https://github.com/kichikuou/system3-sdl2) — 初期の System 1–3 作品向けランタイム。
- [kichikuou/xsystem35-sdl2](https://github.com/kichikuou/xsystem35-sdl2) — クロスプラットフォーム System 3.x（`xsystem35`）コアエンジンおよびモジュール実装。
- [kichikuou/xsystem4-android](https://github.com/kichikuou/xsystem4-android) — Android 移植基盤フレームワーク。

本プロジェクトは [GNU General Public License v2.0 (GPL-2.0)](xsystem4/COPYING) のもとで公開されています（あわせて [xsystem35/COPYING](xsystem35/COPYING) も参照してください）。

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FTomorrowX6%2FAliceHub?ref=badge_large)
