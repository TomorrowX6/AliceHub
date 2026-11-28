(() => {
  'use strict';

  const REDUCED_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const FINE_POINTER = window.matchMedia('(hover: hover) and (pointer: fine)').matches;

  /* ========================================================================
     RELEASE STATE (live-synced from GitHub API with f0810b6 fallback)
     ======================================================================== */
  const RELEASE = {
    tag: 'f0810b6',
    url: 'https://github.com/TomorrowX6/AliceHub/releases/tag/f0810b6',
    assets: {
      android: {
        name: 'AliceHub-f0810b6-arm64-v8a.apk',
        size: '44.3 MiB',
        url: 'https://github.com/TomorrowX6/AliceHub/releases/download/f0810b6/AliceHub-f0810b6-arm64-v8a.apk',
      },
      ios: {
        name: 'AliceHub-f0810b6-ios-unsigned.ipa',
        size: '48.6 MiB',
        url: 'https://github.com/TomorrowX6/AliceHub/releases/download/f0810b6/AliceHub-f0810b6-ios-unsigned.ipa',
      },
      switch: {
        name: 'AliceHub-f0810b6-switch.zip',
        size: '85.9 MiB',
        url: 'https://github.com/TomorrowX6/AliceHub/releases/download/f0810b6/AliceHub-f0810b6-switch.zip',
      },
    },
  };

  /* ========================================================================
     CURATED 24-TITLE CATALOG SHOWCASE DATA
     ======================================================================== */
  const CATALOG_GAMES = [
    { slug: 'rance10', eng: 'SYS4', cn: true, zh: '兰斯 10 -决战-', en: 'Rance X', ja: 'ランス10 -決戦-' },
    { slug: 'dohnadohna', eng: 'SYS4', cn: true, zh: '多娜多娜 一起干坏事吧', en: 'Dohna Dohna', ja: 'ドーナドーナ' },
    { slug: 'evenicle2', eng: 'SYS4', cn: true, zh: '夏娃年代记 2', en: 'Evenicle 2', ja: 'イブニクル2' },
    { slug: 'evenicle', eng: 'SYS4', cn: true, zh: '夏娃年代记', en: 'Evenicle', ja: 'イブニクル' },
    { slug: 'sengokurance', eng: 'SYS4', cn: true, zh: '战国兰斯', en: 'Sengoku Rance', ja: '戦国ランス' },
    { slug: 'rance09', eng: 'SYS4', cn: true, zh: '兰斯 09 赫尔曼革命', en: 'Rance IX', ja: 'ランス9' },
    { slug: 'rance08', eng: 'SYS4', cn: true, zh: '兰斯 08 Quest Magnum', en: 'Rance Quest', ja: 'ランス・クエスト' },
    { slug: 'rance06', eng: 'SYS4', cn: true, zh: '兰斯 VI 赛斯崩坏', en: 'Rance VI', ja: 'ランスVI' },
    { slug: 'rance03', eng: 'SYS4', cn: true, zh: '兰斯 03 利萨斯陷落', en: 'Rance 03', ja: 'ランス03' },
    { slug: 'rance01', eng: 'SYS4', cn: true, zh: '兰斯 01 寻找小光', en: 'Rance 01', ja: 'ランス01' },
    { slug: 'daiteikoku', eng: 'SYS4', cn: true, zh: '大帝国', en: 'Daiteikoku', ja: '大帝国' },
    { slug: 'escalayer_reboot', eng: 'SYS4', cn: true, zh: '超昂天使 R 重制版', en: 'Escalayer Reboot', ja: '超昂天使R' },
    { slug: 'ixseal', eng: 'SYS4', cn: true, zh: '超昂神骑 Ixseal', en: 'Beat Valkyrie Ixseal', ja: '超昂神騎エクシール' },
    { slug: 'haruka', eng: 'SYS4', cn: true, zh: '超昂闪忍 遥', en: 'Beat Blades Haruka', ja: '超昂閃忍ハルカ' },
    { slug: 'maman', eng: 'SYS4', cn: true, zh: '再婚妈妈', en: 'Heartful Maman', ja: 'は～とふるママン' },
    { slug: 'oyakorankan', eng: 'SYS4', cn: true, zh: '母娘乱馆', en: 'Oyakorankan', ja: '母娘乱館' },
    { slug: 'haharanman', eng: 'SYS4', cn: true, zh: '母烂漫', en: 'Haha Ranman', ja: '母爛漫' },
    { slug: 'tsumamigui3', eng: 'SYS4', cn: true, zh: '妻中蜜 3', en: 'Tsumamigui 3', ja: '妻みぐい3' },
    { slug: 'kichikuou', eng: 'SYS35', cn: true, zh: '鬼畜王兰斯', en: 'Kichikuou Rance', ja: '鬼畜王ランス' },
    { slug: 'daiakuji', eng: 'SYS35', cn: true, zh: '大恶司', en: 'Daiakuji', ja: '大悪司' },
    { slug: 'mamatoto', eng: 'SYS35', cn: true, zh: '魔物娘战记', en: 'Mamatoto', ja: 'ままにょにょ / ママトト' },
    { slug: 'galzoo', eng: 'SYS4', cn: true, zh: 'GALZOO 岛', en: 'Galzoo Island', ja: 'GALZOOアイランド' },
    { slug: 'toushin3', eng: 'SYS4', cn: true, zh: '斗神都市 III', en: 'Toushin Toshi III', ja: '闘神都市III' },
    { slug: 'toushin2', eng: 'SYS35', cn: true, zh: '斗神都市 II', en: 'Toushin Toshi II', ja: '闘神都市II' },
  ];

  /* ========================================================================
     TRI-LINGUAL DICTIONARY (ZH / EN / JA)
     ======================================================================== */
  const I18N = {
    zh: {
      'nav.engines': '三核引擎',
      'nav.platforms': '三端实机',
      'nav.features': '硬核能力',
      'nav.catalog': '作品矩阵',
      'nav.download': '获取安装',
      'nav.cta': '立即下载',
      'hero.badge': 'ZERO WINE · ZERO WINLATOR · 100% NATIVE SDL2',
      'hero.sub': '原生突破 · SYSTEM 1–4',
      'hero.lead': '把 AliceSoft 四十年经典直接运行在 <strong>Android、iOS / iPadOS 与 Nintendo Switch</strong> 原生图形与音频管线上。三套独立原生引擎，告别模拟器卡顿与转译损耗。',
      'hero.ctaPrimary': '获取最新版',
      'hero.ctaSecondary': '检视三核架构',
      'hero.m1': 'Sys 1–3 / 3.5 / 4.x',
      'hero.m2': 'Android · iOS · Switch',
      'hero.m3': '内置目录 · 含 54+ 汉化',
      'hero.m4': '纯原生编译 · 零转译',
      'hero.hint': '✦ 移动光标看瞳孔追踪 · 点击画面任意处引爆色散冲击波',
      'engines.kicker': 'TRIPLE NATIVE KERNEL ARCHITECTURE',
      'engines.title': '三核原生引擎矩阵',
      'engines.sub': '启动器自动扫描游戏根目录标记并路由至对应原生内核——从 1989 年的 DAT 经典到 2020s 的 3D 骨骼与粒子演出，一套入口全部贯通。',
      'engines.markersLabel': '自动识别标记',
      'engines.samplesLabel': '代表作品',
      'platforms.kicker': 'THREE PLATFORMS · ONE UNIFIED EXPERIENCE',
      'platforms.title': '三端原生 · 掌上即战',
      'platforms.sub': 'Android、iOS / iPadOS 与 Nintendo Switch 同步构建、同享 Release 发布页，全平台具备 PC 兼容存档导出、启动入口切换与内置更新检查。',
      'platforms.deckTitle': 'INTERACTIVE CONTROL DECK // 点击测试按键反馈',
      'platforms.deckInitial': '点击下方虚拟按键或悬浮球，体验原生手势与手柄映射反馈。',
      'features.kicker': 'ENGINEERED BEYOND EMULATION',
      'features.title': '六维硬核工程能力',
      'features.sub': '从底层字节码、存档序列化、多格式流式解压到移动端帧率与显存恢复，每一项都是面向真实游玩痛点打磨的原生能力。',
      'feat.f1.title': 'PC ⇄ 移动端双向无损存档互通',
      'feat.f1.desc': '导出 PC 兼容存档时自动读取对应 .ain 结构定义，将移动端 X4SS 存档无损转换并修复为 Windows 原版 GD\\x01\\x01 / .gsave / .rsm 格式，随时无缝跨设备续关。',
      'feat.f2.title': '全格式直读与多碟自动合并',
      'feat.f2.desc': '内置流式解压与 ISO9660 / Joliet 光盘镜像提取器，直读 .zip / .rar / .7z / .iso / .mdf（2048/2352/2448 扇区），自动合并 Disk1/Disk2 并修复文件名乱码。',
      'feat.f3.title': '60fps 稳帧调度与切后台无损恢复',
      'feat.f3.desc': '高优先级渲染/混音线程、Android 12+ ADPF 动态提频、Android 15+ 16KB 页对齐与 EGLContext 丢失自动重建；切回瞬间满帧续绘。',
      'feat.f4.title': '无损 MOD、汉化补丁与编码全适配',
      'feat.f4.desc': '覆盖前自动原子备份原版文件，一键卸载还原；全面支持 Shift-JIS / GBK / CHS.TBL / UIF / word.mai 重映射编码与未汉化系统弹窗智能识别。',
      'feat.f5.title': '单游戏启动入口与灵活存储同步',
      'feat.f5.desc': '支持按作品自由切换 AliceStart.ini / AliceStart.CHS 等启动入口；Android 支持共享目录「内部存储/AliceHub」，iOS 支持 iCloud Drive 存档同步。',
      'feat.f6.title': '全屏虚拟手柄编辑器、字号缩放与局内修改器',
      'feat.f6.desc': '内置 GAL / RPG 布局预设与可视化拖拽编辑器，即插即用 Xbox / PlayStation / Switch Pro 手柄；支持 PartsEngine 字体 75%–160% 实时缩放、滚动条 100%–300% 加宽、离线存档编辑与实时数值锁定。',
      'feat.f6.preview': '兰斯 10 · 局内字体实时无损重排',
      'catalog.kicker': '171+ CATALOG ENTRIES · PLUS ANY CUSTOM IMPORT',
      'catalog.title': '跨越四十年作品矩阵',
      'catalog.sub': '内置 171+ 部作品识别目录（含 54+ 部汉化标识），同时支持自由导入目录之外的任意 AliceSoft System 1–4 作品与多碟镜像。',
      'catalog.notice': 'AliceHub 是纯开源引擎与启动器，安装包不包含任何游戏本体或版权素材。请自行准备您合法拥有使用权的游戏文件。',
      'download.kicker': 'READY FOR DEPLOYMENT // OPEN SOURCE GPL-2.0',
      'download.title': '即刻启动原生引擎',
      'download.sub': '全部三端安装包均直接托管于 GitHub Releases，并由 GitHub Actions 公开透明构建。',
      'download.androidReq': 'Android 8.0+ · arm64-v8a · OpenGL ES 3.0 · 直接安装',
      'download.iosReq': 'iOS 15.0+ · arm64 · 未签名，使用 AltStore / SideStore / Sideloadly 自签',
      'download.switchReq': 'Homebrew Menu 应用模式 · 解压将 4 个 NRO 放入 SD 卡 /switch/',
      'download.copy': '点击复制',
      'download.allReleases': '历史版本',
      'download.ciBuilds': '持续构建',
      'download.source': '开源仓库',
      'footer.tagline': 'AliceSoft System 1–4 多平台原生游戏启动器',
      'footer.license': '基于 GNU General Public License v2.0 (GPL-2.0) 协议开源。不含游戏本体。',
      'footer.credits': 'CORE UPSTREAM CREDITS',
      'toast.qqCopied': '已复制 QQ 群号：383917628',
      'toast.pokes': [
        '[ALICE] 0% Wine · 100% 原生 SDL2！',
        '[ALICE] 60FPS VSync 稳帧锁定中！',
        '[ALICE] X4SS ⇄ GD\\x01\\x01 存档直通！',
        '[ALICE] 异色瞳追踪：已锁定目标！',
      ],
    },
    en: {
      'nav.engines': 'Engines',
      'nav.platforms': 'Platforms',
      'nav.features': 'Arsenal',
      'nav.catalog': 'Catalog',
      'nav.download': 'Download',
      'nav.cta': 'GET APP',
      'hero.badge': 'ZERO WINE · ZERO WINLATOR · 100% NATIVE SDL2',
      'hero.sub': 'NATIVE METAL · SYSTEM 1–4',
      'hero.lead': 'Run four decades of AliceSoft classics natively on <strong>Android, iOS / iPadOS, and Nintendo Switch</strong> via three native SDL2 engines—no Wine, no Winlator, zero x86 translation overhead.',
      'hero.ctaPrimary': 'GET LATEST',
      'hero.ctaSecondary': 'INSPECT 3 KERNELS',
      'hero.m1': 'Sys 1–3 / 3.5 / 4.x',
      'hero.m2': 'Android · iOS · Switch',
      'hero.m3': 'Built-in · 54+ CN Tags',
      'hero.m4': 'Pure Native · Zero x86',
      'hero.hint': '✦ Move cursor to track Alice’s eyes · Click anywhere to fire a chromatic shockwave',
      'engines.kicker': 'TRIPLE NATIVE KERNEL ARCHITECTURE',
      'engines.title': 'Three Native Kernels',
      'engines.sub': 'AliceHub inspects your game root automatically and routes to the right native engine—from 1989 DAT classics to modern 3D skeletal & particle epics.',
      'engines.markersLabel': 'AUTO-DETECT MARKERS',
      'engines.samplesLabel': 'SIGNATURE TITLES',
      'platforms.kicker': 'THREE PLATFORMS · ONE UNIFIED EXPERIENCE',
      'platforms.title': 'Three Platforms. Zero Compromise.',
      'platforms.sub': 'Android, iOS / iPadOS, and Nintendo Switch ship together in GitHub Releases—all featuring PC-compatible save export, per-game launch entries, and in-app update checks.',
      'platforms.deckTitle': 'INTERACTIVE CONTROL DECK // CLICK BUTTONS TO TEST',
      'platforms.deckInitial': 'Press virtual pad buttons or the floating pill below to test native control signals.',
      'features.kicker': 'ENGINEERED BEYOND EMULATION',
      'features.title': 'Six-Axis Native Arsenal',
      'features.sub': 'Engineered from the bytecode, save serializer, archive stream, and GPU frame scheduler up to solve real portable gameplay pain points.',
      'feat.f1.title': 'Bidirectional PC ⇄ Mobile Save Interop',
      'feat.f1.desc': 'PC-compatible export reads the game’s .ain schema to convert mobile X4SS saves and repair GD\\x01\\x01 / .gsave / .rsm files automatically across Android, iOS, and Switch.',
      'feat.f2.title': 'All-Format Streaming Import & Disc Merge',
      'feat.f2.desc': 'Built-in libarchive + ISO9660/Joliet extractors read .zip / .rar / .7z / .iso / .mdf (2048/2352/2448 sectors), auto-merge multi-disc folders, and repair CJK filename mojibake.',
      'feat.f3.title': '60fps Frame Pacing & Context Recovery',
      'feat.f3.desc': 'High-priority render/audio threads, Android 12+ ADPF dynamic CPU boost, Android 15+ 16 KB page alignment, and automatic EGLContext texture/FBO reconstruction.',
      'feat.f4.title': 'Non-Destructive MODs & Remapped CJK',
      'feat.f4.desc': 'Atomic vanilla backups allow one-tap restore; full Shift-JIS, GBK, CHS.TBL, UIF, word.mai remap support, smart untranslated CP932 system popups, and <memory> virtual saves.',
      'feat.f5.title': 'Per-Game Launch Entries & Flexible Storage',
      'feat.f5.desc': 'Pick AliceStart.ini / AliceStart.CHS per title; Android supports shared Internal Storage/AliceHub migration while iOS syncs saves with Files & iCloud Drive.',
      'feat.f6.title': 'Fullscreen Virtual Pad Editor, Scaling & Modifiers',
      'feat.f6.desc': 'GAL & RPG presets, visual drag-and-drop editor, plug-and-play Xbox/PS/Switch Pro controllers, live 75%–160% PartsEngine font scale, 100%–300% scrollbars, and save/live editors.',
      'feat.f6.preview': 'Rance X · Live Non-Overflow PartsEngine Text Scale',
      'catalog.kicker': '171+ CATALOG ENTRIES · PLUS ANY CUSTOM IMPORT',
      'catalog.title': 'Four Decades of Classics',
      'catalog.sub': '171+ recognized catalog entries (54+ with Chinese indicators), plus unrestricted local import for any AliceSoft System 1–4 title or multi-disc image.',
      'catalog.notice': 'AliceHub is strictly an open-source launcher and engine runtime. No games or copyrighted assets are bundled—provide game files you have the right to use.',
      'download.kicker': 'READY FOR DEPLOYMENT // OPEN SOURCE GPL-2.0',
      'download.title': 'Deploy Native Engines Now',
      'download.sub': 'All three platform packages are published directly on GitHub Releases and built transparently by GitHub Actions.',
      'download.androidReq': 'Android 8.0+ · arm64-v8a · OpenGL ES 3.0 · Ready to install',
      'download.iosReq': 'iOS 15.0+ · arm64 · Unsigned; sideload via AltStore / SideStore / Sideloadly',
      'download.switchReq': 'Homebrew Menu (App Mode) · Extract 4 NROs to /switch/ on SD card',
      'download.copy': 'CLICK TO COPY',
      'download.allReleases': 'ALL RELEASES',
      'download.ciBuilds': 'NIGHTLY CI',
      'download.source': 'SOURCE CODE',
      'footer.tagline': 'AliceSoft System 1–4 Native Multi-Platform Launcher',
      'footer.license': 'Open source under GNU General Public License v2.0 (GPL-2.0). No games bundled.',
      'footer.credits': 'CORE UPSTREAM CREDITS',
      'toast.qqCopied': 'Copied QQ Group: 383917628',
      'toast.pokes': [
        '[ALICE] 0% Wine · 100% Native SDL2!',
        '[ALICE] 60FPS VSync Locked!',
        '[ALICE] X4SS ⇄ GD\\x01\\x01 Save Bridge Ready!',
        '[ALICE] Heterochromia Target Locked!',
      ],
    },
    ja: {
      'nav.engines': '3つのエンジン',
      'nav.platforms': '対応機種',
      'nav.features': 'コア機能',
      'nav.catalog': '作品マトリクス',
      'nav.download': 'ダウンロード',
      'nav.cta': '入手する',
      'hero.badge': 'ZERO WINE · ZERO WINLATOR · 100% NATIVE SDL2',
      'hero.sub': 'ネイティブ実行 · SYSTEM 1–4',
      'hero.lead': 'アリスソフト40年の名作を <strong>Android、iOS / iPadOS、Nintendo Switch</strong> のネイティブ描画・音声パイプラインで直接実行。Wine も命令変換も不要。',
      'hero.ctaPrimary': '最新版を入手',
      'hero.ctaSecondary': '3核エンジンを見る',
      'hero.m1': 'Sys 1–3 / 3.5 / 4.x',
      'hero.m2': 'Android · iOS · Switch',
      'hero.m3': '内蔵カタログ · 54+ 翻訳対応',
      'hero.m4': '純ネイティブ · 変換負荷ゼロ',
      'hero.hint': '✦ カーソルでアリスの瞳が追従 · 画面クリックで色収差ショックウェーブ発生',
      'engines.kicker': 'TRIPLE NATIVE KERNEL ARCHITECTURE',
      'engines.title': '3系統ネイティブエンジン',
      'engines.sub': 'ルートフォルダのファイルを自動判別し最適なエンジンへルーティング。1989年の DAT 作品から最新の 3D スケルタル演出まで1つのランチャーで完結。',
      'engines.markersLabel': '自動判別マーカー',
      'engines.samplesLabel': '代表タイトル',
      'platforms.kicker': 'THREE PLATFORMS · ONE UNIFIED EXPERIENCE',
      'platforms.title': '3プラットフォーム完全対応',
      'platforms.sub': 'Android・iOS / iPadOS・Nintendo Switch を GitHub Releases で同時配布。全機種で PC 互換セーブ出力・起動エントリ切替・アップデート確認に対応。',
      'platforms.deckTitle': 'INTERACTIVE CONTROL DECK // ボタンを押して応答テスト',
      'platforms.deckInitial': '下の仮想パッドやフローティングボタンをクリックして入力応答を体験してください。',
      'features.kicker': 'ENGINEERED BEYOND EMULATION',
      'features.title': '6つのネイティブ基盤技術',
      'features.sub': 'バイトコード、セーブシリアライズ、アーカイブ展開、GPU 復旧まで携帯機プレイの課題に合わせて一から最適化。',
      'feat.f1.title': 'PC ⇄ モバイル双方向セーブ互換',
      'feat.f1.desc': 'PC 互換セーブ出力時に作品の .ain 構造定義を参照し、X4SS セーブの変換や GD\\x01\\x01 / .gsave / .rsm の修復を自動実行。3機種すべてで Windows 版とシームレス連携。',
      'feat.f2.title': '全形式直接展開＆複数ディスク統合',
      'feat.f2.desc': 'libarchive + ISO9660/Joliet 展開器を内蔵。.zip / .rar / .7z / .iso / .mdf（2048/2352/2448 セクタ）を直接読み込み、Disk1/Disk2 統合と文字化け修復を自動化。',
      'feat.f3.title': '60fps ペーシング＆復帰時自動再構築',
      'feat.f3.desc': '高優先度描画/音声スレッド、Android 12+ ADPF 動的ブースト、Android 15+ 16KB ページ整列、EGLContext 消失時のテクスチャ/FBO 自動再構築に対応。',
      'feat.f4.title': '非破壊 MOD・パッチ＆多文字コード対応',
      'feat.f4.desc': '上書き前の自動バックアップでワンタップ復元。Shift-JIS / GBK / CHS.TBL / UIF / word.mai 置換テーブルと未翻訳 CP932 ダイアログ判定、<memory> 仮想セーブに対応。',
      'feat.f5.title': '作品別起動エントリ＆柔軟な保存先同期',
      'feat.f5.desc': 'AliceStart.ini / AliceStart.CHS 等の起動エントリを作品別に切替。Android は共有フォルダ「内部ストレージ/AliceHub」、iOS は iCloud Drive 同期に対応。',
      'feat.f6.title': '仮想パッド編集・文字拡大＆補助ツール',
      'feat.f6.desc': 'GAL / RPG プリセット＆フルスクリーン編集、Xbox / PS / Switch Pro 対応、PartsEngine 文字 75%–160% 拡大・スクロールバー 100%–300% 拡大、セーブ編集を搭載。',
      'feat.f6.preview': 'ランス10・PartsEngine 文字サイズをリアルタイム再配置',
      'catalog.kicker': '171+ CATALOG ENTRIES · PLUS ANY CUSTOM IMPORT',
      'catalog.title': '40年を横断する作品マトリクス',
      'catalog.sub': '171 作品以上の内蔵カタログに加え、カタログ外のあらゆる System 1–4 作品や複数ディスクイメージも自由にインポート可能。',
      'catalog.notice': 'AliceHub は純粋なオープンソースのランチャー＆エンジンです。ゲーム本体は一切同梱されません。利用権のあるゲームデータをご用意ください。',
      'download.kicker': 'READY FOR DEPLOYMENT // OPEN SOURCE GPL-2.0',
      'download.title': 'ネイティブエンジンを今すぐ導入',
      'download.sub': 'Android・iOS・Switch 全パッケージを GitHub Releases で直接配布し、GitHub Actions により公開ビルドしています。',
      'download.androidReq': 'Android 8.0+ · arm64-v8a · OpenGL ES 3.0 · そのまま導入可能',
      'download.iosReq': 'iOS 15.0+ · arm64 · 未署名（AltStore / SideStore / Sideloadly で署名）',
      'download.switchReq': 'Homebrew Menu アプリモード · 4つの NRO を SD カード /switch/ へ配置',
      'download.copy': 'クリックでコピー',
      'download.allReleases': 'リリース一覧',
      'download.ciBuilds': '最新ビルド',
      'download.source': 'ソースコード',
      'footer.tagline': 'アリスソフト System 1–4 マルチプラットフォーム・ネイティブランチャー',
      'footer.license': 'GNU General Public License v2.0 (GPL-2.0) に基づき公開。ゲームデータは同梱されません。',
      'footer.credits': 'CORE UPSTREAM CREDITS',
      'toast.qqCopied': 'QQ グループ番号をコピーしました：383917628',
      'toast.pokes': [
        '[ALICE] Wine 不要・100% ネイティブ SDL2！',
        '[ALICE] 60FPS VSync ロック完了！',
        '[ALICE] X4SS ⇄ GD\\x01\\x01 セーブ互換スタンバイ！',
        '[ALICE] オッドアイ追尾システム稼働中！',
      ],
    },
  };

  /* ========================================================================
     ENGINE DETAILS DATA (3 KERNELS × 3 LANGUAGES)
     ======================================================================== */
  const ENGINES = {
    sys3: {
      badge: 'KERNEL // SYSTEM3-SDL2',
      era: '1989 — 1995',
      markers: ['ADISK.DAT', 'ACG.DAT', 'AMUS.DAT', 'ASCE.DAT'],
      termTitle: 'system3-sdl2 // legacy dat runtime',
      games: [
        { slug: 'rance01', label: 'Rance I (DAT)' },
        { slug: 'toushin2', label: 'Early Classics' },
      ],
      copy: {
        zh: {
          title: '早期 System 1–3 原生引擎',
          desc: '面向采用早期 DAT 资源架构的经典作品（如《兰斯 I》《兰斯 II》《兰斯 III》原版），提供原生触控交互、独立存档隔离与轻量快进体验。',
          log: '[BOOT] Mounted ADISK.DAT + ACG.DAT (System 1-3)\n[GFX ] Indexed palette blitter -> SDL2 texture OK\n[CTRL] Direct touch mode active (zero virtual clutter)\n[SAVE] Scoped directory: saves/<game>/ [READY]',
        },
        en: {
          title: 'Early System 1–3 Native Runtime',
          desc: 'Runs classic DAT-archive titles such as the original Rance I, II, and III with direct native touch input, isolated save directories, and instant read-text skip.',
          log: '[BOOT] Mounted ADISK.DAT + ACG.DAT (System 1-3)\n[GFX ] Indexed palette blitter -> SDL2 texture OK\n[CTRL] Direct touch mode active (zero virtual clutter)\n[SAVE] Scoped directory: saves/<game>/ [READY]',
        },
        ja: {
          title: '初期 System 1–3 ネイティブエンジン',
          desc: '『ランス I』『ランス II』『ランス III』オリジナル版など DAT 形式の初期作品を実行。ネイティブタッチ操作、作品別セーブ管理、既読スキップに対応。',
          log: '[BOOT] Mounted ADISK.DAT + ACG.DAT (System 1-3)\n[GFX ] Indexed palette blitter -> SDL2 texture OK\n[CTRL] Direct touch mode active (zero virtual clutter)\n[SAVE] Scoped directory: saves/<game>/ [READY]',
        },
      },
    },
    sys35: {
      badge: 'KERNEL // XSYSTEM35',
      era: '1996 — 2003',
      markers: ['System39.ini', 'System39.ain', '*SA.ALD', 'ADISK.ALD', 'playlist.txt'],
      termTitle: 'xsystem35 // ald + midi synthesizer',
      games: [
        { slug: 'kichikuou', label: '鬼畜王ランス' },
        { slug: 'daiakuji', label: '大悪司' },
        { slug: 'mamatoto', label: 'ママトト' },
        { slug: 'toushin2', label: '闘神都市II' },
      ],
      copy: {
        zh: {
          title: 'System 3.5–3.9 黄金时代内核',
          desc: '完整实现 System 3.5–3.9 字节码与扩展模块（SACT / ShGraph / ShSound / eDemo）、PMS/QNT/VSP/WebP 图像、Shift-JIS/GBK/Unicode 排版、虚拟 CD 音轨与原生 MIDI 合成器。',
          log: '[BOOT] Detected System39.ain + *SA.ALD archive set\n[AUDIO] MIDI synth active + playlist.txt Ogg/MP3 CDDA\n[TEXT] Shift-JIS / GBK / UTF-8 glyph pipeline OK\n[TOOL] Floating overlay + gamepad bridge + live modifier',
        },
        en: {
          title: 'System 3.5–3.9 Golden-Era Kernel',
          desc: 'Full System 3.5–3.9 bytecode & modules (SACT, ShGraph, ShSound, eDemo), PMS/QNT/VSP/WebP graphics, multi-encoding CJK text, virtual CD tracks (playlist.txt), and native MIDI playback.',
          log: '[BOOT] Detected System39.ain + *SA.ALD archive set\n[AUDIO] MIDI synth active + playlist.txt Ogg/MP3 CDDA\n[TEXT] Shift-JIS / GBK / UTF-8 glyph pipeline OK\n[TOOL] Floating overlay + gamepad bridge + live modifier',
        },
        ja: {
          title: 'System 3.5–3.9 黄金期ネイティブコア',
          desc: 'System 3.5–3.9 バイトコード＆拡張モジュール（SACT / ShGraph / ShSound / eDemo）、PMS/QNT/VSP/WebP 描画、Shift-JIS/GBK/Unicode、仮想 CD 音源、ネイティブ MIDI 再生に対応。',
          log: '[BOOT] Detected System39.ain + *SA.ALD archive set\n[AUDIO] MIDI synth active + playlist.txt Ogg/MP3 CDDA\n[TEXT] Shift-JIS / GBK / UTF-8 glyph pipeline OK\n[TOOL] Floating overlay + gamepad bridge + live modifier',
        },
      },
    },
    sys4: {
      badge: 'KERNEL // XSYSTEM4',
      era: '2002 — PRESENT',
      markers: ['AliceStart.ini', 'AliceStart.CHS', 'System40.ini', '*.ain', 'CHS.TBL'],
      termTitle: 'xsystem4 // ain v0-v14.1 + seal3d + flat15',
      games: [
        { slug: 'rance10', label: '兰斯 10' },
        { slug: 'dohnadohna', label: '多娜多娜' },
        { slug: 'evenicle2', label: '夏娃年代记 2' },
        { slug: 'sengokurance', label: '战国兰斯' },
      ],
      copy: {
        zh: {
          title: 'System 4.x 全世代原生内核',
          desc: '覆盖早期 System 4.0（SACT2 / StoatSpriteEngine / DrawDungeon / ReignEngine）至后期旗舰（AIN v12–v14.1、PartsEngine、SealEngine 3D UBO 骨骼、FLAT v15、Ogg Opus、CHS.TBL/UIF 汉化映射与 <memory> 虚拟存档）。',
          log: '[BOOT] Loaded AIN v14.1 · O(deg) Delegate Reverse Index\n[GFX ] GLES 3.0 SealEngine UBO + FLAT v15 · 60.0 FPS\n[CJK ] GBK / SJIS / CHS.TBL + smart CP932 system popup\n[SAVE] X4SS ⇄ GD\\x01\\x01 PC export + <memory> snapshot OK',
        },
        en: {
          title: 'Full-Generation System 4.x Kernel',
          desc: 'Spans early System 4.0 (SACT2, StoatSpriteEngine, DrawDungeon, ReignEngine) through modern flagships (AIN v12–v14.1, PartsEngine, SealEngine 3D UBO bones, FLAT v15, Ogg Opus, CHS.TBL/UIF remaps, and <memory> snapshots).',
          log: '[BOOT] Loaded AIN v14.1 · O(deg) Delegate Reverse Index\n[GFX ] GLES 3.0 SealEngine UBO + FLAT v15 · 60.0 FPS\n[CJK ] GBK / SJIS / CHS.TBL + smart CP932 system popup\n[SAVE] X4SS ⇄ GD\\x01\\x01 PC export + <memory> snapshot OK',
        },
        ja: {
          title: 'System 4.x 全世代ネイティブコア',
          desc: '初期 System 4.0（SACT2 / StoatSpriteEngine / DrawDungeon / ReignEngine）から最新大規模作（AIN v12–v14.1、PartsEngine、SealEngine 3D UBO、FLAT v15、Ogg Opus、CHS.TBL/UIF、<memory> セーブ）まで網羅。',
          log: '[BOOT] Loaded AIN v14.1 · O(deg) Delegate Reverse Index\n[GFX ] GLES 3.0 SealEngine UBO + FLAT v15 · 60.0 FPS\n[CJK ] GBK / SJIS / CHS.TBL + smart CP932 system popup\n[SAVE] X4SS ⇄ GD\\x01\\x01 PC export + <memory> snapshot OK',
        },
      },
    },
  };

  /* ========================================================================
     PLATFORM SPECS DATA (3 PLATFORMS × 3 LANGUAGES)
     ======================================================================== */
  const PLATFORMS = {
    android: {
      pill: 'ANDROID // ARM64-V8A · API 26+',
      deckMode: 'MODE: ANDROID TOUCH + ADPF',
      footer: ['SINGLE TAP → CLICK', '2-FINGER → MENU', 'LONG PRESS → CTRL'],
      copy: {
        zh: {
          headline: '旗舰级 Android 原生调度与全屏手柄编辑器',
          summary: '下载 APK 直接安装，支持前台服务后台流式安装、共享存储「内部存储 → AliceHub」存档迁移、ADPF 动态提频与 Android 15+ 16KB 页对齐。',
          bullets: [
            '直读文件夹、.zip / .rar / .7z 与 .iso / .mdf 光盘镜像，自动合并多盘与修复乱码',
            '可视化全屏虚拟手柄编辑器 + Xbox / PlayStation / Switch Pro 即插即用',
            '支持单游戏 System 4 启动入口切换、原子备份 MOD 管理与 PC 兼容存档导出',
            '结构化崩溃诊断卡片，一键复制或导出 .txt 日志；内置应用更新检查',
          ],
          cta: '下载 Android APK',
        },
        en: {
          headline: 'Flagship Android Scheduling & Visual Gamepad Editor',
          summary: 'Install the APK directly with foreground-service installs, guided shared storage (Internal Storage → AliceHub) save migration, ADPF boost, and Android 15+ 16 KB alignment.',
          bullets: [
            'Direct import from folders, .zip / .rar / .7z archives, and .iso / .mdf disc images',
            'Fullscreen drag-and-drop virtual pad editor + plug-and-play Xbox / PS / Switch Pro controllers',
            'Per-game System 4 launch entries, atomic-backup MOD manager, and PC-compatible save export',
            'Structured crash diagnostic reports with one-tap .txt export and built-in update checks',
          ],
          cta: 'Download Android APK',
        },
        ja: {
          headline: 'Android ネイティブ制御＆仮想パッドエディタ',
          summary: 'APK をそのまま導入可能。フォアグラウンドサービス導入、共有ストレージ「内部ストレージ → AliceHub」移行、ADPF 動的制御、16KB ページ整列に対応。',
          bullets: [
            'フォルダ・.zip/.rar/.7z・.iso/.mdf を直接読み込み、複数ディスク統合と文字化け修復に対応',
            'フルスクリーン仮想パッド編集＋Xbox / PlayStation / Switch Pro 物理コントローラー対応',
            'System 4 作品別起動エントリ切替、非破壊 MOD 管理、PC 互換セーブ出力を搭載',
            '構造化クラッシュ診断ログ出力およびアプリ内アップデート確認に対応',
          ],
          cta: 'Android APK を入手',
        },
      },
    },
    ios: {
      pill: 'iOS / iPadOS // ARM64 · iOS 15+',
      deckMode: 'MODE: iOS TOUCH + iCLOUD SYNC',
      footer: ['FILES → ALICEHUB/GAMES', 'HOLD → SKIP (SYS4)', 'iCLOUD DRIVE SYNC'],
      copy: {
        zh: {
          headline: '纯净 SwiftUI 壳体、iCloud 同步与 iOS 26 后台任务',
          summary: '使用 AltStore、SideStore 或 Sideloadly 自签侧载未签名 IPA；支持「文件」App 直接拖入游戏与 APFS 零冗余克隆安装。',
          bullets: [
            '支持导入文件夹 / 多碟与 .zip / .rar / .7z / .iso / .mdf 镜像，也可直拷至「AliceHub → Games」',
            '可选用「文件」内任意目录（如 iCloud 云盘）同步存档，并支持导出 Windows PC 兼容存档',
            'iOS 26+ 支持系统后台持续处理任务；支持 System 4 启动入口切换与 xsystem35 MIDI 播放',
            '内置虚拟按键/手柄设置、无损 MOD 与去码补丁管理及应用内更新检查',
          ],
          cta: '下载 iOS 未签名 IPA',
        },
        en: {
          headline: 'Native SwiftUI Shell, iCloud Sync & iOS 26 Background Tasks',
          summary: 'Sign and sideload the unsigned IPA with AltStore, SideStore, or Sideloadly; import directly via Files with APFS copy-on-write staging.',
          bullets: [
            'Import folders, multi-disc sets, and .zip / .rar / .7z / .iso / .mdf images—or drop into AliceHub → Games',
            'Sync saves with any folder in Files (including iCloud Drive) and export Windows PC-compatible saves',
            'Continued background processing on iOS 26+, per-game System 4 launch entries, and xsystem35 MIDI',
            'Customizable gamepad controls, non-destructive MOD/patch management, and built-in update checks',
          ],
          cta: 'Download iOS Unsigned IPA',
        },
        ja: {
          headline: 'SwiftUI ネイティブ実装・iCloud 同期・iOS 26 バックグラウンド',
          summary: 'AltStore / SideStore / Sideloadly 等で未署名 IPA に署名して導入。「ファイル」アプリからの直接コピーと APFS 省容量展開に対応。',
          bullets: [
            'フォルダ・複数ディスク・.zip/.rar/.7z/.iso/.mdf の取り込み、および AliceHub → Games 配置に対応',
            'iCloud Drive 等のフォルダとセーブ同期でき、Windows PC 互換セーブ出力にも対応',
            'iOS 26 以降のバックグラウンド継続処理、System 4 起動エントリ切替、MIDI 再生に対応',
            'パッド設定・非破壊 MOD／パッチ管理・アプリ内アップデート確認を内蔵',
          ],
          cta: 'iOS 未署名 IPA を入手',
        },
      },
    },
    switch: {
      pill: 'NINTENDO SWITCH // 4× NRO HOMEBREW',
      deckMode: 'MODE: JOY-CON · L+R OVERLAY',
      footer: ['L + R → IN-GAME MENU', 'X → SEARCH · Y → SETTINGS', '4× NRO → /switch/'],
      copy: {
        zh: {
          headline: '四 NRO 原生自制程序 · 掌机底座全适配',
          summary: '直接从最新 Release 下载 Switch 压缩包，将 AliceHub.nro 与三套引擎 NRO 一并放入 SD 卡 /switch/，以应用模式启动即可。',
          bullets: [
            '三引擎统一大库：支持 X 搜索、Y 设置、单游戏启动入口切换、断点续传下载与更新检查',
            '支持从 SD 卡导入文件夹/.zip/.iso/.mdf，并支持 .zip/.rar/.7z 本地或在线 MOD 与汉化补丁',
            '完整存档管理器支持导入/导出（含 PC_SaveData 转换）、备份恢复、目录同步与兰斯 10 存档编辑',
            '局内按 L+R（横握 SL+SR / 键盘 F1）呼出菜单，支持截图、快进、局内修改与字号/滚动条缩放',
          ],
          cta: '下载 Switch ZIP',
        },
        en: {
          headline: 'Four Native Homebrew NROs · Handheld & Docked',
          summary: 'Grab the Switch ZIP from the latest Release, copy AliceHub.nro and all three engine NROs into /switch/ on your SD card, and launch via Homebrew Menu (App Mode).',
          bullets: [
            'Unified 3-engine library with X search, Y settings, System 4 launch entries, resumable downloads, and update checks',
            'Import local folders/.zip/.iso/.mdf from SD plus .zip/.rar/.7z local or online MOD & translation overlays',
            'Full save manager with import/export (including PC_SaveData), backup/restore, sync, and Rance 10 save editor',
            'Press L+R (SL+SR sideways / F1 keyboard) for in-game screenshots, skip toggle, live modifiers, and scaling',
          ],
          cta: 'Download Switch ZIP',
        },
        ja: {
          headline: '4 NRO ネイティブ Homebrew・携帯／ドック両対応',
          summary: '最新リリースの Switch ZIP を展開し、AliceHub.nro と3つのエンジン NRO を SD カードの /switch/ に配置。Homebrew Menu からアプリモードで起動。',
          bullets: [
            '3エンジン統合ライブラリ：X 検索、Y 設定、起動エントリ切替、レジューム対応ダウンロード、更新確認',
            'SD カードからフォルダ/.zip/.iso/.mdf を導入でき、.zip/.rar/.7z のローカル／オンライン MOD にも対応',
            'セーブ管理でインポート・PC_SaveData 互換出力・バックアップ復元・同期・ランス10セーブ編集が可能',
            'プレイ中 L+R（横持ち SL+SR / F1）でスクショ・スキップ・リアルタイム改造・文字拡大メニューを表示',
          ],
          cta: 'Switch ZIP を入手',
        },
      },
    },
  };

  let currentLang = 'zh';
  let currentEngine = 'sys4';
  let currentPlatform = 'android';
  let ctrlLatched = false;
  let pokeCount = 0;

  /* ========================================================================
     1. BOOT SHUTTER CURTAIN
     ======================================================================== */
  function runBootIntro() {
    const curtain = document.getElementById('boot-curtain');
    const pctEl = document.getElementById('boot-pct');
    const fillEl = document.getElementById('boot-bar-fill');
    if (!curtain || REDUCED_MOTION) {
      document.body.classList.remove('is-booting');
      document.body.classList.add('is-ready');
      if (curtain) curtain.remove();
      return;
    }

    let pct = 0;
    const start = performance.now();
    const duration = 540;

    function tick(now) {
      const t = Math.min(1, (now - start) / duration);
      // Punchy non-linear jump
      pct = Math.round((1 - Math.pow(1 - t, 3)) * 100);
      if (pctEl) pctEl.textContent = String(pct).padStart(2, '0') + '%';
      if (fillEl) fillEl.style.width = pct + '%';

      if (t < 1) {
        requestAnimationFrame(tick);
      } else {
        setTimeout(() => {
          curtain.classList.add('is-open');
          document.body.classList.remove('is-booting');
          document.body.classList.add('is-ready');
          setTimeout(() => {
            curtain.classList.add('is-done');
          }, 720);
        }, 70);
      }
    }
    requestAnimationFrame(tick);
  }

  /* ========================================================================
     2. KINETIC HERO CANVAS (WIREFRAME WARP + SPEEDLINES + SHOCKWAVES)
     ======================================================================== */
  const shockwaves = [];
  const sparks = [];
  const pointer = { x: 0.5, y: 0.5, tx: 0.5, ty: 0.5, vx: 0, vy: 0, px: 0, py: 0 };

  function spawnShockwave(clientX, clientY) {
    const hero = document.getElementById('hero');
    if (!hero) return;
    const rect = hero.getBoundingClientRect();
    const x = clientX - rect.left;
    const y = clientY - rect.top;
    shockwaves.push({ x, y, r: 10, maxR: Math.max(rect.width, rect.height) * 0.65, alpha: 1 });
    for (let i = 0; i < 24; i++) {
      const ang = (Math.PI * 2 * i) / 24 + (Math.random() - 0.5) * 0.25;
      const spd = 4 + Math.random() * 11;
      sparks.push({
        x, y,
        vx: Math.cos(ang) * spd,
        vy: Math.sin(ang) * spd,
        size: 3 + Math.random() * 5,
        color: i % 3 === 0 ? '#ff0080' : i % 3 === 1 ? '#00e5ff' : '#eeff00',
        life: 1,
      });
    }
  }

  function initHeroCanvas() {
    const canvas = document.getElementById('hero-canvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const TOKENS = [
      'AIN v14.1', 'SDL2 NATIVE', 'FLAT v15', 'SEAL3D UBO',
      'GD\\x01\\x01', 'CHS.TBL', 'ADISK.DAT', 'SYSTEM39',
      '60FPS VSYNC', '16KB ALIGN', 'ADPF BOOST', '4x NRO',
    ];

    let w = 0, h = 0, dpr = 1;
    const shards = Array.from({ length: 16 }, (_, i) => ({
      x: Math.random(),
      y: Math.random(),
      z: 0.35 + Math.random() * 0.65,
      vx: (Math.random() - 0.5) * 0.00045,
      vy: -0.0002 - Math.random() * 0.0004,
      label: TOKENS[i % TOKENS.length],
      color: i % 3 === 0 ? '#ff0080' : i % 3 === 1 ? '#00e5ff' : '#eeff00',
    }));

    function resize() {
      const rect = canvas.getBoundingClientRect();
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = Math.max(1, Math.floor(rect.width));
      h = Math.max(1, Math.floor(rect.height));
      canvas.width = Math.floor(w * dpr);
      canvas.height = Math.floor(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    resize();
    window.addEventListener('resize', resize, { passive: true });

    let phase = 0;

    function draw() {
      ctx.clearRect(0, 0, w, h);

      // Smooth pointer interpolation
      pointer.x += (pointer.tx - pointer.x) * 0.09;
      pointer.y += (pointer.ty - pointer.y) * 0.09;
      phase += REDUCED_MOTION ? 0 : 0.018;

      const cx = w * (0.58 + (pointer.x - 0.5) * 0.14);
      const cy = h * (0.48 + (pointer.y - 0.5) * 0.12);

      // 1. Perspective brutalist wireframe horizon (floor + ceiling)
      ctx.save();
      ctx.lineWidth = 1;

      const horizonY = cy;
      // Floor rays
      for (let i = -9; i <= 9; i++) {
        const bx = cx + i * (w * 0.13);
        ctx.strokeStyle = i % 2 === 0 ? 'rgba(0, 229, 255, 0.14)' : 'rgba(255, 0, 128, 0.13)';
        ctx.beginPath();
        ctx.moveTo(cx, horizonY);
        ctx.lineTo(bx, h + 40);
        ctx.stroke();

        ctx.strokeStyle = 'rgba(246, 245, 238, 0.05)';
        ctx.beginPath();
        ctx.moveTo(cx, horizonY);
        ctx.lineTo(bx, -40);
        ctx.stroke();
      }

      // Scrolling horizontal depth lines on floor
      for (let j = 0; j < 10; j++) {
        const p = ((j / 10) + (phase * 0.25)) % 1;
        const y = horizonY + Math.pow(p, 2.2) * (h - horizonY + 30);
        ctx.strokeStyle = `rgba(255, 0, 128, ${(0.04 + p * 0.22).toFixed(3)})`;
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(w, y);
        ctx.stroke();
      }

      // 2. Manga radial speedlines around focus point
      const rayCount = 26;
      for (let r = 0; r < rayCount; r++) {
        const a = (Math.PI * 2 * r) / rayCount + Math.sin(phase * 0.4 + r) * 0.025;
        const inner = Math.min(w, h) * (0.28 + (r % 3) * 0.07);
        const outer = Math.max(w, h) * 0.92;
        ctx.strokeStyle = r % 4 === 0 ? 'rgba(0, 229, 255, 0.12)' : 'rgba(246, 245, 238, 0.045)';
        ctx.lineWidth = r % 5 === 0 ? 1.6 : 0.8;
        ctx.beginPath();
        ctx.moveTo(cx + Math.cos(a) * inner, cy + Math.sin(a) * inner);
        ctx.lineTo(cx + Math.cos(a) * outer, cy + Math.sin(a) * outer);
        ctx.stroke();
      }

      // 3. Floating opcode telemetry shards
      ctx.font = '700 10px "JetBrains Mono", monospace';
      for (const s of shards) {
        if (!REDUCED_MOTION) {
          s.x = (s.x + s.vx + 1) % 1;
          s.y = (s.y + s.vy + 1) % 1;
        }
        const sx = s.x * w + (pointer.x - 0.5) * -42 * s.z;
        const sy = s.y * h + (pointer.y - 0.5) * -32 * s.z;
        ctx.fillStyle = s.color;
        ctx.globalAlpha = 0.26 * s.z;
        ctx.fillRect(sx - 6, sy - 6, 4, 4);
        ctx.fillText(s.label, sx + 4, sy - 1);
      }
      ctx.globalAlpha = 1;

      // 4. Chromatic shockwaves on click
      for (let i = shockwaves.length - 1; i >= 0; i--) {
        const sw = shockwaves[i];
        sw.r += 22;
        sw.alpha *= 0.92;
        if (sw.alpha < 0.03 || sw.r > sw.maxR) {
          shockwaves.splice(i, 1);
          continue;
        }
        ctx.lineWidth = 3 * sw.alpha;
        ctx.strokeStyle = `rgba(255, 0, 128, ${sw.alpha.toFixed(3)})`;
        ctx.beginPath();
        ctx.arc(sw.x - 4, sw.y + 2, sw.r, 0, Math.PI * 2);
        ctx.stroke();

        ctx.strokeStyle = `rgba(0, 229, 255, ${sw.alpha.toFixed(3)})`;
        ctx.beginPath();
        ctx.arc(sw.x + 4, sw.y - 2, sw.r * 0.92, 0, Math.PI * 2);
        ctx.stroke();
      }

      // 5. High-velocity sparks
      for (let i = sparks.length - 1; i >= 0; i--) {
        const p = sparks[i];
        p.x += p.vx;
        p.y += p.vy;
        p.vx *= 0.94;
        p.vy *= 0.94;
        p.life -= 0.032;
        if (p.life <= 0) {
          sparks.splice(i, 1);
          continue;
        }
        ctx.fillStyle = p.color;
        ctx.globalAlpha = p.life;
        ctx.fillRect(p.x - p.size * 0.5, p.y - p.size * 0.5, p.size, p.size);
      }
      ctx.globalAlpha = 1;
      ctx.restore();

      if (!REDUCED_MOTION) requestAnimationFrame(draw);
    }

    draw();

    const hero = document.getElementById('hero');
    if (hero) {
      hero.addEventListener('click', (e) => {
        if (e.target.closest('a, button')) return;
        spawnShockwave(e.clientX, e.clientY);
      });
    }
  }

  /* ========================================================================
     3. LIVE ALICE HETEROCHROMIA EYE TRACKING + 3D SHRINE TILT + POKE
     ======================================================================== */
  function initAliceShrine() {
    const shrine = document.getElementById('alice-shrine');
    const card = document.getElementById('shrine-card');
    const avatarBox = document.getElementById('alice-avatar-box');
    const eyeM = document.getElementById('alice-eye-m');
    const eyeC = document.getElementById('alice-eye-c');
    const ghostM = document.getElementById('alice-ghost-m');
    const ghostC = document.getElementById('alice-ghost-c');
    const chips = shrine ? shrine.querySelectorAll('.float-chip') : [];
    if (!shrine || !card || !avatarBox || !eyeM || !eyeC) return;

    // Natural blinking
    if (!REDUCED_MOTION) {
      const scheduleBlink = () => {
        setTimeout(() => {
          if (!avatarBox.classList.contains('is-winking')) {
            avatarBox.classList.add('is-blinking');
            setTimeout(() => avatarBox.classList.remove('is-blinking'), 130);
          }
          scheduleBlink();
        }, 2600 + Math.random() * 3200);
      };
      scheduleBlink();
    }

    window.addEventListener('pointermove', (e) => {
      const nx = e.clientX / Math.max(1, window.innerWidth);
      const ny = e.clientY / Math.max(1, window.innerHeight);
      pointer.tx = nx;
      pointer.ty = ny;

      if (REDUCED_MOTION) return;

      const rect = avatarBox.getBoundingClientRect();
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height * 0.72;
      const dx = (e.clientX - cx) / Math.max(180, window.innerWidth * 0.45);
      const dy = (e.clientY - cy) / Math.max(180, window.innerHeight * 0.45);
      const clampX = Math.max(-1, Math.min(1, dx));
      const clampY = Math.max(-1, Math.min(1, dy));

      // Live eye translation inside socket
      const ex = clampX * 5.2;
      const ey = clampY * 3.8;
      eyeM.style.transform = `translate3d(${ex.toFixed(2)}px, ${ey.toFixed(2)}px, 0)`;
      eyeC.style.transform = `translate3d(${ex.toFixed(2)}px, ${ey.toFixed(2)}px, 0)`;

      // 3D tilt card
      const rotY = clampX * 13;
      const rotX = -clampY * 11;
      card.style.transform = `rotateX(${rotX.toFixed(2)}deg) rotateY(${rotY.toFixed(2)}deg)`;

      // Dynamic chromatic ghost split
      if (ghostM && ghostC) {
        ghostM.style.transform = `translate3d(${(-6 - clampX * 7).toFixed(1)}px, ${(2 - clampY * 4).toFixed(1)}px, 0)`;
        ghostC.style.transform = `translate3d(${(6 + clampX * 7).toFixed(1)}px, ${(-2 + clampY * 4).toFixed(1)}px, 0)`;
      }

      // Floating parallax badges
      chips.forEach((chip) => {
        const depth = Number(chip.dataset.depth || 20);
        chip.style.transform = `translate3d(${(clampX * depth * -0.55).toFixed(1)}px, ${(clampY * depth * -0.45).toFixed(1)}px, 0)`;
      });
    }, { passive: true });

    // Playful poke interaction
    shrine.addEventListener('click', (e) => {
      const rect = shrine.getBoundingClientRect();
      spawnShockwave(e.clientX || rect.left + rect.width / 2, e.clientY || rect.top + rect.height / 2);

      card.classList.remove('is-poked');
      void card.offsetWidth;
      card.classList.add('is-poked');

      avatarBox.classList.add('is-winking');
      setTimeout(() => avatarBox.classList.remove('is-winking'), 360);

      const pokes = I18N[currentLang]['toast.pokes'];
      showToast(pokes[pokeCount % pokes.length]);
      pokeCount++;
    });
  }

  /* ========================================================================
     4. CUSTOM CROSSHAIR CURSOR
     ======================================================================== */
  function initCursor() {
    if (!FINE_POINTER || REDUCED_MOTION) return;
    const cursor = document.getElementById('custom-cursor');
    const tag = document.getElementById('cursor-tag');
    if (!cursor || !tag) return;

    let mx = -100, my = -100, cx = -100, cy = -100;

    window.addEventListener('pointermove', (e) => {
      mx = e.clientX;
      my = e.clientY;
      cursor.classList.add('is-visible');
      const target = e.target.closest('[data-cursor], a, button');
      if (target) {
        cursor.classList.add('is-active');
        tag.textContent = target.dataset.cursor || '';
      } else {
        cursor.classList.remove('is-active');
        tag.textContent = '';
      }
    }, { passive: true });

    window.addEventListener('pointerdown', () => cursor.classList.add('is-down'));
    window.addEventListener('pointerup', () => cursor.classList.remove('is-down'));
    document.addEventListener('mouseleave', () => cursor.classList.remove('is-visible'));

    function loop() {
      cx += (mx - cx) * 0.28;
      cy += (my - cy) * 0.28;
      cursor.style.transform = `translate3d(${cx.toFixed(1)}px, ${cy.toFixed(1)}px, 0)`;
      requestAnimationFrame(loop);
    }
    loop();
  }

  /* ========================================================================
     5. SCROLL VELOCITY SKEW + MARQUEE BOOST + TELEMETRY RAIL + REVEALS
     ======================================================================== */
  function initScrollChoreography() {
    const nav = document.getElementById('hud-nav');
    const railFill = document.getElementById('rail-fill');
    const railPct = document.getElementById('rail-pct');
    const railSec = document.getElementById('rail-sec');
    const navLinks = Array.from(document.querySelectorAll('.hud-links a[data-sec]'));
    const sections = Array.from(document.querySelectorAll('section[data-section]'));
    const ribbons = Array.from(document.querySelectorAll('[data-marquee]'));

    const secMap = {
      hero: 'SEC.01',
      engines: 'SEC.02',
      platforms: 'SEC.03',
      features: 'SEC.04',
      catalog: 'SEC.05',
      download: 'SEC.06',
    };

    let lastY = window.scrollY;
    let velocity = 0;
    const ribbonOffsets = ribbons.map(() => 0);

    // IntersectionObserver for reveal-up elements
    const revealObs = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-in');
          revealObs.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });

    document.querySelectorAll('.reveal-up').forEach((el) => revealObs.observe(el));

    // Active section observer
    const secObs = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const id = entry.target.dataset.section;
          if (railSec && secMap[id]) railSec.textContent = secMap[id];
          navLinks.forEach((a) => a.classList.toggle('is-active', a.dataset.sec === id));
        }
      });
    }, { threshold: 0.35 });

    sections.forEach((s) => secObs.observe(s));

    function frame() {
      const y = window.scrollY;
      const dy = y - lastY;
      lastY = y;
      velocity += (dy - velocity) * 0.16;

      if (nav) nav.classList.toggle('is-scrolled', y > 32);

      // Scroll progress
      const maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
      const pct = Math.max(0, Math.min(1, y / maxScroll));
      if (railFill) railFill.style.height = (pct * 100).toFixed(1) + '%';
      if (railPct) railPct.textContent = String(Math.round(pct * 100)).padStart(2, '0') + '%';

      // Dynamic scroll-velocity skew & chromatic RGB split
      if (!REDUCED_MOTION) {
        const clampedV = Math.max(-45, Math.min(45, velocity));
        const skew = (clampedV * -0.065).toFixed(2);
        const rgb = Math.min(12, Math.abs(clampedV) * 0.22).toFixed(1);
        document.documentElement.style.setProperty('--scroll-skew', `${skew}deg`);
        document.documentElement.style.setProperty('--scroll-rgb', `${rgb}px`);

        // Kinetic dual ribbons driven by base drift + scroll velocity
        ribbons.forEach((ribbon, idx) => {
          const track = ribbon.querySelector('.ribbon-track');
          if (!track) return;
          const dir = Number(ribbon.dataset.marquee || 1);
          const half = track.scrollWidth / 2 || 1000;
          ribbonOffsets[idx] += dir * (0.95 + Math.abs(clampedV) * 0.16);
          if (ribbonOffsets[idx] <= -half) ribbonOffsets[idx] += half;
          if (ribbonOffsets[idx] >= 0) ribbonOffsets[idx] -= half;
          track.style.transform = `translate3d(${ribbonOffsets[idx].toFixed(1)}px, 0, 0)`;
        });
      }

      requestAnimationFrame(frame);
    }

    if (!REDUCED_MOTION) {
      requestAnimationFrame(frame);
    } else {
      window.addEventListener('scroll', () => {
        if (nav) nav.classList.toggle('is-scrolled', window.scrollY > 32);
      }, { passive: true });
    }
  }

  /* ========================================================================
     6. ENGINE CUT-IN SWITCHER
     ======================================================================== */
  let termTimer = null;

  function renderEngine(key, withSlash) {
    currentEngine = key;
    const data = ENGINES[key];
    const copy = data.copy[currentLang] || data.copy.zh;

    document.querySelectorAll('.engine-tab').forEach((btn) => {
      const active = btn.dataset.engine === key;
      btn.classList.toggle('is-active', active);
      btn.setAttribute('aria-selected', String(active));
    });

    if (withSlash && !REDUCED_MOTION) {
      const wipe = document.getElementById('engine-wipe');
      if (wipe) {
        wipe.classList.remove('is-slashing');
        void wipe.offsetWidth;
        wipe.classList.add('is-slashing');
      }
    }

    const set = (id, text) => {
      const el = document.getElementById(id);
      if (el) el.textContent = text;
    };

    set('eng-badge', data.badge);
    set('eng-era', data.era);
    set('eng-title', copy.title);
    set('eng-desc', copy.desc);
    set('eng-term-title', data.termTitle);

    const markersEl = document.getElementById('eng-markers');
    if (markersEl) {
      markersEl.innerHTML = data.markers
        .map((m) => `<span class="marker-chip">${escapeHtml(m)}</span>`)
        .join('');
    }

    const gamesEl = document.getElementById('eng-games');
    if (gamesEl) {
      gamesEl.innerHTML = data.games
        .map((g) => `<span class="mini-game-chip"><img src="assets/games/${g.slug}.webp" alt="" width="30" height="30" /><span>${escapeHtml(g.label)}</span></span>`)
        .join('');
    }

    // Punchy typewriter terminal log
    const termEl = document.getElementById('eng-terminal');
    if (termEl) {
      if (termTimer) clearInterval(termTimer);
      if (REDUCED_MOTION) {
        termEl.textContent = copy.log;
      } else {
        let idx = 0;
        const full = copy.log;
        termEl.textContent = '';
        termTimer = setInterval(() => {
          idx += 5;
          termEl.textContent = full.slice(0, idx);
          if (idx >= full.length) {
            clearInterval(termTimer);
            termTimer = null;
          }
        }, 16);
      }
    }
  }

  /* ========================================================================
     7. PLATFORM SWITCHER + INTERACTIVE PLAYABLE CONTROL DECK
     ======================================================================== */
  function renderPlatform(key, withSlash) {
    currentPlatform = key;
    const spec = PLATFORMS[key];
    const copy = spec.copy[currentLang] || spec.copy.zh;
    const asset = RELEASE.assets[key];

    document.querySelectorAll('.plat-tab').forEach((btn) => {
      const active = btn.dataset.platform === key;
      btn.classList.toggle('is-active', active);
      btn.setAttribute('aria-selected', String(active));
    });

    if (withSlash && !REDUCED_MOTION) {
      const wipe = document.getElementById('plat-wipe');
      if (wipe) {
        wipe.classList.remove('is-slashing');
        void wipe.offsetWidth;
        wipe.classList.add('is-slashing');
      }
    }

    const set = (id, text) => {
      const el = document.getElementById(id);
      if (el) el.textContent = text;
    };

    set('plat-pill', spec.pill);
    set('plat-pkg', asset.name);
    set('plat-headline', copy.headline);
    set('plat-summary', copy.summary);
    set('plat-dl-label', copy.cta);
    set('plat-size-chip', asset.size);
    set('deck-mode', spec.deckMode);

    const dlBtn = document.getElementById('plat-dl-btn');
    if (dlBtn) dlBtn.href = asset.url;

    const ul = document.getElementById('plat-bullets');
    if (ul) {
      ul.innerHTML = copy.bullets.map((b) => `<li>${escapeHtml(b)}</li>`).join('');
    }

    const footer = document.getElementById('deck-footer');
    if (footer) {
      footer.innerHTML = spec.footer.map((t) => `<span>${escapeHtml(t)}</span>`).join('');
    }
  }

  function initPlayableDeck() {
    const deck = document.getElementById('hardware-deck');
    const ctrlPill = document.getElementById('deck-ctrl-pill');
    const fpsEl = document.getElementById('deck-fps');
    const speakerEl = document.getElementById('deck-speaker');
    const textEl = document.getElementById('deck-text');
    const signalEl = document.getElementById('deck-signal');
    if (!deck) return;

    const RESPONSES = {
      CONFIRM: {
        zh: ['[A / 单指轻触] 确认推进剧情 → SACT2 / PartsEngine 命中目标。', 'EVENT: SDL_BUTTON_LEFT (CONFIRMED)'],
        en: ['[A / TAP] Advance scene → SACT2 / PartsEngine target hit.', 'EVENT: SDL_BUTTON_LEFT (CONFIRMED)'],
        ja: ['[A / タップ] シーン送り → SACT2 / PartsEngine 応答。', 'EVENT: SDL_BUTTON_LEFT (CONFIRMED)'],
      },
      CANCEL: {
        zh: ['[B / 双指轻触] 呼出游戏右键菜单或取消当前选择。', 'EVENT: SDL_BUTTON_RIGHT (BACK/MENU)'],
        en: ['[B / 2-FINGER] Open right-click game menu or cancel.', 'EVENT: SDL_BUTTON_RIGHT (BACK/MENU)'],
        ja: ['[B / 2本指タップ] 右クリックメニュー呼出・キャンセル。', 'EVENT: SDL_BUTTON_RIGHT (BACK/MENU)'],
      },
      LR: {
        zh: ['[L+R / 悬浮菜单] 已呼出局内工具栏：截图 / 字号缩放 / 实时修改器。', 'OVERLAY: IN-GAME TOOLBOX OPEN'],
        en: ['[L+R / OVERLAY] In-game menu open: Screenshot · Font Scale · Live Modifiers.', 'OVERLAY: IN-GAME TOOLBOX OPEN'],
        ja: ['[L+R / メニュー] ゲーム内メニュー起動：スクショ・文字拡大・改造ツール。', 'OVERLAY: IN-GAME TOOLBOX OPEN'],
      },
      MENU: {
        zh: ['[悬浮球] 切换虚拟按键显隐、字号缩放（75%–160%）与修改器。', 'OVERLAY: FLOATING HUB TRIGGERED'],
        en: ['[FLOATING BALL] Toggle virtual pad, font scale (75%–160%), or live modifiers.', 'OVERLAY: FLOATING HUB TRIGGERED'],
        ja: ['[フローティング] 仮想パッド表示切替・文字拡大・補助メニュー。', 'OVERLAY: FLOATING HUB TRIGGERED'],
      },
      SHOT: {
        zh: ['[Y / 一键截图] 已保存无损画面截图至相册 / screenshots 目录！', 'CAPTURE: PNG SAVED (0ms STALL)'],
        en: ['[Y / SCREENSHOT] Instant frame captured to album / screenshots folder!', 'CAPTURE: PNG SAVED (0ms STALL)'],
        ja: ['[Y / スクショ] 現在のフレームを無劣化 PNG で保存しました！', 'CAPTURE: PNG SAVED (0ms STALL)'],
      },
      SKIP: {
        zh: ['[X / 快进] 触发单次高速跳过文本。', 'KEY: SDLK_LCTRL PULSE'],
        en: ['[X / SKIP] Triggering fast text skip pulse.', 'KEY: SDLK_LCTRL PULSE'],
        ja: ['[X / スキップ] 高速テキストスキップを実行。', 'KEY: SDLK_LCTRL PULSE'],
      },
      WHEEL_UP: {
        zh: ['[LB / 双指下滑] 鼠标滚轮向上 → 打开历史对话记录（Backlog）。', 'WHEEL: +1 (OPEN BACKLOG)'],
        en: ['[LB / SWIPE DOWN] Mouse wheel up → Open dialogue backlog.', 'WHEEL: +1 (OPEN BACKLOG)'],
        ja: ['[LB / 下スワイプ] ホイール上 → バックログ表示。', 'WHEEL: +1 (OPEN BACKLOG)'],
      },
      WHEEL_DN: {
        zh: ['[RB / 双指上滑] 鼠标滚轮向下 → 推进下一句台词。', 'WHEEL: -1 (NEXT LINE)'],
        en: ['[RB / SWIPE UP] Mouse wheel down → Advance next line.', 'WHEEL: -1 (NEXT LINE)'],
        ja: ['[RB / 上スワイプ] ホイール下 → 次のメッセージへ。', 'WHEEL: -1 (NEXT LINE)'],
      },
      CURSOR: {
        zh: ['[虚拟光标] 切换左摇杆虚拟鼠标光标锁定状态。', 'CURSOR: VIRTUAL MOUSE TOGGLED'],
        en: ['[CURSOR] Toggled left-stick virtual mouse cursor.', 'CURSOR: VIRTUAL MOUSE TOGGLED'],
        ja: ['[仮想カーソル] 左スティック仮想マウスの切替。', 'CURSOR: VIRTUAL MOUSE TOGGLED'],
      },
    };

    deck.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-pad]');
      if (!btn) return;
      const code = btn.dataset.pad;

      btn.classList.add('is-hit');
      setTimeout(() => btn.classList.remove('is-hit'), 160);

      if (code === 'CTRL') {
        ctrlLatched = !ctrlLatched;
        if (ctrlPill) ctrlPill.classList.toggle('is-latched', ctrlLatched);
        if (fpsEl) fpsEl.textContent = ctrlLatched ? '60.0 FPS · CTRL×' : '60.0 FPS';
        if (speakerEl) speakerEl.textContent = 'SYSTEM // CTRL LATCH';
        if (textEl) {
          textEl.textContent = ctrlLatched
            ? (currentLang === 'en' ? '[⏩ Ctrl LATCHED] Auto fast-forwarding dialogues at locked 60fps!' : currentLang === 'ja' ? '[⏩ Ctrl 固定中] 60fps でテキスト高速スキップ中！' : '[⏩ Ctrl 已锁定] 持续高速快进对话中，再次点击解除锁定。')
            : (currentLang === 'en' ? '[⏩ Ctrl RELEASED] Returned to normal dialogue pacing.' : currentLang === 'ja' ? '[⏩ Ctrl 解除] 通常速度に戻りました。' : '[⏩ Ctrl 已解除] 恢复正常对话速度。');
        }
        if (signalEl) signalEl.textContent = ctrlLatched ? 'LATCHED: SDLK_LCTRL HELD' : 'RELEASED: NORMAL PACING';
        return;
      }

      const pair = RESPONSES[code];
      if (pair) {
        const [msg, sig] = pair[currentLang] || pair.zh;
        if (speakerEl) speakerEl.textContent = `INPUT // ${code}`;
        if (textEl) textEl.textContent = msg;
        if (signalEl) signalEl.textContent = sig;
      } else {
        if (speakerEl) speakerEl.textContent = `D-PAD // ${code}`;
        if (textEl) {
          textEl.textContent = currentLang === 'en'
            ? `Directional input [${code}] mapped to virtual cursor / dungeon step.`
            : currentLang === 'ja'
              ? `方向入力 [${code}] を仮想カーソル／ダンジョン移動へ送信。`
              : `方向键 [${code}] 已映射至虚拟光标 / 3D 迷宫移动。`;
        }
        if (signalEl) signalEl.textContent = `AXIS: ${code} · LATENCY < 1ms`;
      }
    });
  }

  /* ========================================================================
     8. HEX SAVE MORPH & INTERACTIVE FONT SCALE SLIDER
     ======================================================================== */
  function initMicroWidgets() {
    // Save hex morph cycling
    const hexSrc = document.getElementById('hex-src');
    const hexDst = document.getElementById('hex-dst');
    const srcLabel = document.querySelector('.morph-src .morph-label');
    const dstLabel = document.querySelector('.morph-dst .morph-label');
    if (hexSrc && hexDst && !REDUCED_MOTION) {
      const frames = [
        ['MOBILE X4SS', '58 34 53 53 00 1A F2 09', 'WINDOWS GD\\x01\\x01', '47 44 01 01 00 1A F2 09'],
        ['MOBILE X4SS', '58 34 53 53 00 2B 84 C1', 'WINDOWS GD\\x01\\x01', '47 44 01 01 00 2B 84 C1'],
        ['RSM v6 ENC', '52 53 4D 06 7C E9 10 4A', 'PC RSM RAW', '52 53 4D 00 00 14 00 00'],
        ['GSAVE + .CMT', '47 53 56 08 40 00 A2 11', 'PC GSAVE v8', '47 53 56 08 FF 01 A2 11'],
      ];
      let fi = 0;
      setInterval(() => {
        fi = (fi + 1) % frames.length;
        if (srcLabel) srcLabel.textContent = frames[fi][0];
        hexSrc.textContent = frames[fi][1];
        if (dstLabel) dstLabel.textContent = frames[fi][2];
        hexDst.textContent = frames[fi][3];
      }, 2200);
    }

    // Live PartsEngine Font Scale demo slider
    const range = document.getElementById('demo-font-range');
    const valEl = document.getElementById('demo-font-val');
    const preview = document.getElementById('slider-preview');
    if (range && valEl && preview) {
      const syncFont = () => {
        const v = Number(range.value);
        valEl.textContent = `${v}%`;
        preview.style.fontSize = `${(14 * v / 100).toFixed(1)}px`;
      };
      range.addEventListener('input', syncFont);
      syncFont();
    }
  }

  /* ========================================================================
     9. 24-TITLE REAL ARTWORK KINETIC WALL
     ======================================================================== */
  function renderCatalogWall() {
    const stripA = document.getElementById('art-strip-a');
    const stripB = document.getElementById('art-strip-b');
    if (!stripA || !stripB) return;

    const makeTile = (g) => {
      const title = g[currentLang] || g.zh;
      return `<div class="game-tile" data-cursor="${g.eng}">
        <img src="assets/games/${g.slug}.webp" alt="${escapeHtml(title)}" width="148" height="148" />
        <div class="tile-meta">
          <span class="tile-name" title="${escapeHtml(title)}">${escapeHtml(title)}</span>
          <span class="tile-badges">
            <span class="tb-eng">${g.eng}</span>
            ${g.cn ? '<span class="tb-cn">CN</span>' : ''}
          </span>
        </div>
      </div>`;
    };

    const row1 = CATALOG_GAMES.slice(0, 12).map(makeTile).join('');
    const row2 = CATALOG_GAMES.slice(12).map(makeTile).join('');
    stripA.innerHTML = row1 + row1;
    stripB.innerHTML = row2 + row2;
  }

  /* ========================================================================
     10. LIVE GITHUB LATEST RELEASE SYNC
     ======================================================================== */
  function applyReleaseUI() {
    const set = (id, text) => {
      const el = document.getElementById(id);
      if (el) el.textContent = text;
    };
    set('nav-release-tag', RELEASE.tag);
    set('hero-ver-pill', RELEASE.tag);
    set('dl-release-tag', `RELEASE ${RELEASE.tag}`);

    for (const k of ['android', 'ios', 'switch']) {
      const a = RELEASE.assets[k];
      set(`dl-file-${k}`, a.name);
      set(`dl-size-${k}`, a.size);
      const card = document.getElementById(`dl-card-${k}`);
      if (card) card.href = a.url;
    }
    renderPlatform(currentPlatform, false);
  }

  function fetchLatestRelease() {
    fetch('https://api.github.com/repos/TomorrowX6/AliceHub/releases/latest', {
      headers: { Accept: 'application/vnd.github+json' },
    })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (!data || !data.tag_name || !Array.isArray(data.assets)) return;
        RELEASE.tag = data.tag_name;
        RELEASE.url = data.html_url || RELEASE.url;
        const fmtSize = (bytes) => `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;

        for (const asset of data.assets) {
          const n = (asset.name || '').toLowerCase();
          if (n.endsWith('.apk')) {
            RELEASE.assets.android = { name: asset.name, size: fmtSize(asset.size), url: asset.browser_download_url };
          } else if (n.endsWith('.ipa')) {
            RELEASE.assets.ios = { name: asset.name, size: fmtSize(asset.size), url: asset.browser_download_url };
          } else if (n.includes('switch') && n.endsWith('.zip')) {
            RELEASE.assets.switch = { name: asset.name, size: fmtSize(asset.size), url: asset.browser_download_url };
          }
        }
        applyReleaseUI();
      })
      .catch(() => { /* Keep built-in fallback release data */ });
  }

  /* ========================================================================
     11. TRI-LINGUAL SWITCHER & UTILITIES
     ======================================================================== */
  function applyLanguage(lang) {
    if (!I18N[lang]) return;
    currentLang = lang;
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : lang === 'ja' ? 'ja' : 'en';

    document.querySelectorAll('.lang-btn').forEach((btn) => {
      btn.classList.toggle('is-active', btn.dataset.lang === lang);
    });

    const dict = I18N[lang];
    document.querySelectorAll('[data-i18n]').forEach((el) => {
      const key = el.dataset.i18n;
      if (!(key in dict)) return;
      if (key === 'hero.lead') {
        el.innerHTML = dict[key];
      } else {
        el.textContent = dict[key];
      }
    });

    renderEngine(currentEngine, false);
    renderPlatform(currentPlatform, false);
    renderCatalogWall();
  }

  let toastTimer = null;
  function showToast(msg) {
    const el = document.getElementById('toast');
    if (!el) return;
    el.textContent = msg;
    el.classList.add('is-shown');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('is-shown'), 2400);
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  /* ========================================================================
     12. WIRE EVERYTHING UP
     ======================================================================== */
  document.addEventListener('DOMContentLoaded', () => {
    // Engine tabs
    document.querySelectorAll('.engine-tab').forEach((btn) => {
      btn.addEventListener('click', () => renderEngine(btn.dataset.engine, true));
    });

    // Platform tabs
    document.querySelectorAll('.plat-tab').forEach((btn) => {
      btn.addEventListener('click', () => renderPlatform(btn.dataset.platform, true));
    });

    // Language switcher
    document.querySelectorAll('.lang-btn').forEach((btn) => {
      btn.addEventListener('click', () => applyLanguage(btn.dataset.lang));
    });

    // Mobile drawer
    const menuBtn = document.getElementById('mobile-menu-btn');
    const drawer = document.getElementById('mobile-drawer');
    if (menuBtn && drawer) {
      menuBtn.addEventListener('click', () => {
        const open = drawer.hasAttribute('hidden');
        if (open) drawer.removeAttribute('hidden');
        else drawer.setAttribute('hidden', '');
        menuBtn.setAttribute('aria-expanded', String(open));
      });
      drawer.querySelectorAll('a').forEach((a) => {
        a.addEventListener('click', () => {
          drawer.setAttribute('hidden', '');
          menuBtn.setAttribute('aria-expanded', 'false');
        });
      });
    }

    // QQ copy button
    const qqBtn = document.getElementById('copy-qq-btn');
    if (qqBtn) {
      qqBtn.addEventListener('click', () => {
        const qq = qqBtn.dataset.qq || '383917628';
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(qq).catch(() => {});
        }
        showToast(I18N[currentLang]['toast.qqCopied']);
      });
    }

    applyLanguage('zh');
    applyReleaseUI();
    initHeroCanvas();
    initAliceShrine();
    initCursor();
    initScrollChoreography();
    initPlayableDeck();
    initMicroWidgets();
    runBootIntro();
    fetchLatestRelease();
  });
})();
