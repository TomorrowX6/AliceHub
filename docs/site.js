(() => {
  'use strict';

  const REDUCED_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const FINE_POINTER = window.matchMedia('(hover: hover) and (pointer: fine)').matches;

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
    { slug: 'mamatoto', eng: 'SYS35', cn: true, zh: '魔物娘战记', en: 'Mamatoto', ja: 'ママトト' },
    { slug: 'galzoo', eng: 'SYS4', cn: true, zh: 'GALZOO 岛', en: 'Galzoo Island', ja: 'GALZOOアイランド' },
    { slug: 'toushin3', eng: 'SYS4', cn: true, zh: '斗神都市 III', en: 'Toushin Toshi III', ja: '闘神都市III' },
    { slug: 'toushin2', eng: 'SYS35', cn: true, zh: '斗神都市 II', en: 'Toushin Toshi II', ja: '闘神都市II' },
  ];

  const I18N = {
    zh: {
      'nav.engines': '三核引擎',
      'nav.platforms': '三端实机',
      'nav.features': '工程能力',
      'nav.catalog': '作品矩阵',
      'nav.download': '下载',
      'nav.cta': '获取版本',
      'hero.badge': 'ZERO WINE · ZERO WINLATOR · PURE NATIVE SDL2',
      'hero.sub': '原生运行 System 1–4',
      'hero.lead': '在 <strong>Android、iOS / iPadOS 与 Nintendo Switch</strong> 上通过三套原生引擎直接运行 AliceSoft 经典作品，无需模拟转译，保持 60fps 流畅响应与完整存档生态。',
      'hero.ctaPrimary': '下载最新版',
      'hero.ctaSecondary': '探索三核架构',
      'hero.m1': '原生引擎 · Sys 1–4',
      'hero.m2': 'Android · iOS · Switch',
      'hero.m3': '内置目录 · 含 54+ 汉化',
      'hero.m4': '指令转译损耗',
      'hero.hint': '移动光标观察瞳孔追踪与 3D 视差 · 点击触发光波涟漪',
      'engines.kicker': '01 / TRIPLE NATIVE KERNEL',
      'engines.title': '三套独立原生内核',
      'engines.sub': '自动识别游戏根目录标记并路由至对应引擎——从 1989 年的早期 DAT 作品到现代 3D 骨骼与粒子动画，一套启动器全部原生运行。',
      'engines.markersLabel': '自动识别标记',
      'engines.samplesLabel': '代表作品',
      'platforms.kicker': '02 / MULTI-PLATFORM RUNTIME',
      'platforms.title': '三端原生，一致体验',
      'platforms.sub': 'Android、iOS / iPadOS 与 Nintendo Switch 同步构建并统一发布，全平台均提供 PC 兼容存档导出、启动入口切换与应用内更新检查。',
      'platforms.deckTitle': '交互操控预览 · 点击测试按键',
      'platforms.deckInitial': '点击下方按键或 ⏩ Ctrl，测试原生手势与手柄映射反馈。',
      'features.kicker': '03 / CORE ENGINEERING',
      'features.title': '面向实机游玩的底层打磨',
      'features.sub': '围绕跨端存档互通、多格式直读、汉化编码、帧率调度与掌上交互，把桌面经典打磨成真正顺手的掌上原生体验。',
      'feat.f1.title': 'PC ⇄ 移动端双向无损存档互通',
      'feat.f1.desc': '导出 PC 兼容存档时自动读取对应 .ain 结构定义，将移动端 X4SS 存档无损转换并修复为 Windows 原版 GD\\x01\\x01 / .gsave / .rsm 格式，随时跨设备续关。',
      'feat.f2.title': '全格式直读与多碟自动合并',
      'feat.f2.desc': '内置流式解压与 ISO9660 / Joliet 提取器，直读 .zip / .rar / .7z / .iso / .mdf（2048/2352/2448 扇区），自动合并多盘并修复乱码。',
      'feat.f3.title': '60fps 稳帧调度与后台恢复',
      'feat.f3.desc': '高优先级渲染/混音线程、Android 12+ ADPF 提频、16KB 页对齐与 EGLContext 丢失自动重建，切回瞬间无损续绘。',
      'feat.f4.title': '无损 MOD、补丁与编码适配',
      'feat.f4.desc': '覆盖前自动原子备份原版文件，一键还原；完整支持 Shift-JIS / GBK / CHS.TBL / UIF / word.mai 与未汉化 CP932 系统弹窗识别。',
      'feat.f5.title': '单游戏启动入口与灵活存储',
      'feat.f5.desc': '支持按作品切换 AliceStart.ini / AliceStart.CHS 启动入口；Android 支持共享目录「内部存储/AliceHub」，iOS 支持 iCloud Drive 同步。',
      'feat.f6.title': '全屏虚拟手柄编辑器、字号缩放与局内修改器',
      'feat.f6.desc': '内置 GAL / RPG 预设与可视化拖拽编辑，即插即用 Xbox / PlayStation / Switch Pro 手柄；支持 PartsEngine 字体 75%–160% 实时缩放、滚动条 100%–300% 加宽、离线存档编辑与实时数值锁定。',
      'feat.f6.preview': '兰斯 10 · 局内字体实时无损重排',
      'catalog.kicker': '04 / 171+ CATALOG · CUSTOM IMPORT',
      'catalog.title': '内置 171+ 部作品识别目录',
      'catalog.sub': '涵盖 54+ 部汉化标识与全世代代表作，同时支持自由导入目录之外的任意 AliceSoft System 1–4 作品与多碟镜像。',
      'catalog.notice': 'AliceHub 是纯开源引擎与启动器，安装包不包含任何游戏本体或版权素材。请自行准备您合法拥有使用权的游戏文件。',
      'download.kicker': '05 / DEPLOYMENT · GPL-2.0',
      'download.title': '下载最新版本',
      'download.sub': 'Android、iOS 与 Nintendo Switch 安装包均托管于 GitHub Releases，并由 GitHub Actions 公开构建。',
      'download.androidReq': 'Android 8.0+ · arm64-v8a · OpenGL ES 3.0 · 直接安装',
      'download.iosReq': 'iOS 15.0+ · arm64 · 未签名，使用 AltStore / SideStore / Sideloadly 自签',
      'download.switchReq': 'Homebrew Menu 应用模式 · 解压将 4 个 NRO 放入 SD 卡 /switch/',
      'download.copy': '点击复制',
      'download.allReleases': '历史版本',
      'download.ciBuilds': '持续构建',
      'download.source': '开源仓库',
      'footer.license': '基于 GNU General Public License v2.0 (GPL-2.0) 协议开源。不含游戏本体。',
      'footer.credits': 'CORE UPSTREAM CREDITS',
      'toast.qqCopied': '已复制 QQ 群号：383917628',
      'toast.pokes': [
        'Alice // 0% Wine · 100% Native SDL2',
        'Alice // 60fps VSync 已锁定',
        'Alice // X4SS ⇄ GD\\x01\\x01 跨端存档就绪',
      ],
    },
    en: {
      'nav.engines': 'Engines',
      'nav.platforms': 'Platforms',
      'nav.features': 'Engineering',
      'nav.catalog': 'Catalog',
      'nav.download': 'Download',
      'nav.cta': 'Get App',
      'hero.badge': 'ZERO WINE · ZERO WINLATOR · PURE NATIVE SDL2',
      'hero.sub': 'Native System 1–4',
      'hero.lead': 'Run four decades of AliceSoft classics natively on <strong>Android, iOS / iPadOS, and Nintendo Switch</strong> through three native SDL2 engines—zero emulation, smooth 60fps pacing, and full PC save interoperability.',
      'hero.ctaPrimary': 'Download Latest',
      'hero.ctaSecondary': 'Explore 3 Engines',
      'hero.m1': 'Native Engines · Sys 1–4',
      'hero.m2': 'Android · iOS · Switch',
      'hero.m3': 'Built-in · 54+ CN Tags',
      'hero.m4': 'Emulation Overhead',
      'hero.hint': 'Move cursor for eye tracking & 3D tilt · Click anywhere for ripple',
      'engines.kicker': '01 / TRIPLE NATIVE KERNEL',
      'engines.title': 'Three Native Kernels',
      'engines.sub': 'Automatically detects game-root markers and routes to the right native engine—from 1989 DAT releases to modern 3D skeletal & particle epics.',
      'engines.markersLabel': 'AUTO-DETECT MARKERS',
      'engines.samplesLabel': 'REPRESENTATIVE TITLES',
      'platforms.kicker': '02 / MULTI-PLATFORM RUNTIME',
      'platforms.title': 'Three Platforms, One Standard',
      'platforms.sub': 'Android, iOS / iPadOS, and Nintendo Switch ship together in GitHub Releases—all supporting PC-compatible save export, launch entries, and in-app update checks.',
      'platforms.deckTitle': 'Interactive Control Deck · Click to Test',
      'platforms.deckInitial': 'Press virtual buttons or ⏩ Ctrl below to preview native input responses.',
      'features.kicker': '03 / CORE ENGINEERING',
      'features.title': 'Built for Real Portable Play',
      'features.sub': 'From save serialization and streaming disc extraction to CJK remapping and frame pacing, every layer is engineered natively.',
      'feat.f1.title': 'Bidirectional PC ⇄ Mobile Save Interop',
      'feat.f1.desc': 'PC-compatible export uses each title’s .ain schema to convert mobile X4SS saves and repair GD\\x01\\x01 / .gsave / .rsm files across Android, iOS, and Switch.',
      'feat.f2.title': 'Direct Archive & Multi-Disc Extraction',
      'feat.f2.desc': 'Built-in libarchive + ISO9660/Joliet extractors read .zip / .rar / .7z / .iso / .mdf (2048/2352/2448 sectors), merge multi-disc sets, and repair CJK filenames.',
      'feat.f3.title': '60fps Pacing & Context Recovery',
      'feat.f3.desc': 'High-priority render/audio threads, Android 12+ ADPF boost, 16 KB page alignment, and automatic EGLContext texture/FBO reconstruction.',
      'feat.f4.title': 'Non-Destructive MODs & CJK Remaps',
      'feat.f4.desc': 'Atomic vanilla backups allow one-tap restore; full Shift-JIS, GBK, CHS.TBL, UIF, word.mai remap support, smart CP932 system popups, and <memory> saves.',
      'feat.f5.title': 'Launch Entries & Flexible Storage',
      'feat.f5.desc': 'Choose AliceStart.ini / AliceStart.CHS per game; Android supports shared Internal Storage/AliceHub migration while iOS syncs via iCloud Drive.',
      'feat.f6.title': 'Virtual Pad Editor, Text Scaling & Modifiers',
      'feat.f6.desc': 'GAL & RPG presets, visual drag-and-drop editor, plug-and-play Xbox/PS/Switch Pro controllers, 75%–160% PartsEngine font scaling, 100%–300% scrollbars, and save/live editors.',
      'feat.f6.preview': 'Rance X · Live Non-Overflow PartsEngine Text Scale',
      'catalog.kicker': '04 / 171+ CATALOG · CUSTOM IMPORT',
      'catalog.title': '171+ Recognized Titles Built In',
      'catalog.sub': 'Covers 54+ localized entries across four decades, plus unrestricted local import for any AliceSoft System 1–4 title or disc image.',
      'catalog.notice': 'AliceHub is purely an open-source launcher and engine runtime. No games or copyrighted assets are included—provide game files you own.',
      'download.kicker': '05 / DEPLOYMENT · GPL-2.0',
      'download.title': 'Download Latest Release',
      'download.sub': 'Android, iOS, and Nintendo Switch packages are hosted on GitHub Releases and built transparently by GitHub Actions.',
      'download.androidReq': 'Android 8.0+ · arm64-v8a · OpenGL ES 3.0 · Direct install',
      'download.iosReq': 'iOS 15.0+ · arm64 · Unsigned; sign with AltStore / SideStore / Sideloadly',
      'download.switchReq': 'Homebrew Menu (App Mode) · Extract 4 NROs into SD /switch/',
      'download.copy': 'Copy',
      'download.allReleases': 'Releases',
      'download.ciBuilds': 'Nightly CI',
      'download.source': 'Source Code',
      'footer.license': 'Open source under GNU General Public License v2.0 (GPL-2.0). No games bundled.',
      'footer.credits': 'CORE UPSTREAM CREDITS',
      'toast.qqCopied': 'Copied QQ Group: 383917628',
      'toast.pokes': [
        'Alice // 0% Wine · 100% Native SDL2',
        'Alice // 60fps VSync Locked',
        'Alice // X4SS ⇄ GD\\x01\\x01 Save Interop Ready',
      ],
    },
    ja: {
      'nav.engines': '3つのエンジン',
      'nav.platforms': '対応機種',
      'nav.features': 'コア機能',
      'nav.catalog': '作品一覧',
      'nav.download': '入手',
      'nav.cta': 'ダウンロード',
      'hero.badge': 'ZERO WINE · ZERO WINLATOR · PURE NATIVE SDL2',
      'hero.sub': 'System 1–4 ネイティブ実行',
      'hero.lead': '<strong>Android、iOS / iPadOS、Nintendo Switch</strong> でアリスソフトの名作を3つのネイティブエンジンにより直接実行。Wine も命令変換も不要、60fps と PC セーブ互換を両立。',
      'hero.ctaPrimary': '最新版を入手',
      'hero.ctaSecondary': '3核エンジンを見る',
      'hero.m1': 'ネイティブエンジン · Sys 1–4',
      'hero.m2': 'Android · iOS · Switch',
      'hero.m3': '内蔵カタログ · 54+ 翻訳対応',
      'hero.m4': '変換オーバーヘッド',
      'hero.hint': 'カーソルで瞳追従＆3D視差 · クリックで波紋エフェクト',
      'engines.kicker': '01 / TRIPLE NATIVE KERNEL',
      'engines.title': '3系統のネイティブコア',
      'engines.sub': 'ルートフォルダのファイルを自動判別して最適なエンジンへルーティング。1989年の DAT 作品から最新の 3D 演出まで1つで完結します。',
      'engines.markersLabel': '自動判別マーカー',
      'engines.samplesLabel': '代表作品',
      'platforms.kicker': '02 / MULTI-PLATFORM RUNTIME',
      'platforms.title': '3プラットフォーム統一体験',
      'platforms.sub': 'Android・iOS / iPadOS・Nintendo Switch を同時配布。全機種で PC 互換セーブ出力・起動エントリ切替・アップデート確認に対応。',
      'platforms.deckTitle': '操作プレビュー · ボタンをクリック',
      'platforms.deckInitial': '下の仮想キーや ⏩ Ctrl をクリックして入力応答を確認できます。',
      'features.kicker': '03 / CORE ENGINEERING',
      'features.title': '携帯機プレイのための最適化',
      'features.sub': 'セーブ互換、アーカイブ展開、文字コード対応、フレーム制御まで実機での快適さを追求しました。',
      'feat.f1.title': 'PC ⇄ モバイル双方向セーブ互換',
      'feat.f1.desc': 'PC 互換セーブ出力時に .ain 構造定義を参照し、X4SS の変換や GD\\x01\\x01 / .gsave / .rsm の修復を自動実行。3機種すべてで Windows 版と連携。',
      'feat.f2.title': '全形式直接展開＆複数ディスク統合',
      'feat.f2.desc': 'libarchive + ISO9660/Joliet 展開器を内蔵。.zip / .rar / .7z / .iso / .mdf を直接読み込み、複数ディスク統合と文字化け修復に対応。',
      'feat.f3.title': '60fps ペーシング＆復帰時再構築',
      'feat.f3.desc': '高優先度描画/音声スレッド、Android 12+ ADPF 制御、16KB ページ整列、EGLContext 消失時の自動再構築に対応。',
      'feat.f4.title': '非破壊 MOD・パッチ＆多文字コード',
      'feat.f4.desc': '上書き前の自動バックアップでワンタップ復元。Shift-JIS / GBK / CHS.TBL / UIF / word.mai、未翻訳 CP932 ダイアログ判定、<memory> セーブに対応。',
      'feat.f5.title': '作品別起動エントリ＆柔軟な保存先',
      'feat.f5.desc': 'AliceStart.ini / AliceStart.CHS を作品別に切替。Android は共有フォルダ「内部ストレージ/AliceHub」、iOS は iCloud Drive 同期に対応。',
      'feat.f6.title': '仮想パッド編集・文字拡大＆補助ツール',
      'feat.f6.desc': 'GAL / RPG プリセット＆フルスクリーン編集、Xbox / PS / Switch Pro 対応、PartsEngine 文字 75%–160% 拡大、スクロールバー拡大、セーブ編集を搭載。',
      'feat.f6.preview': 'ランス10・PartsEngine 文字サイズをリアルタイム再配置',
      'catalog.kicker': '04 / 171+ CATALOG · CUSTOM IMPORT',
      'catalog.title': '171 作品以上の内蔵カタログ',
      'catalog.sub': '40年分の名作をカバーし、カタログ外のあらゆる System 1–4 作品やディスクイメージも自由にインポート可能。',
      'catalog.notice': 'AliceHub は純粋なオープンソースランチャー＆エンジンです。ゲーム本体は同梱されません。利用権のあるデータをご用意ください。',
      'download.kicker': '05 / DEPLOYMENT · GPL-2.0',
      'download.title': '最新リリースを入手',
      'download.sub': 'Android・iOS・Switch 全パッケージを GitHub Releases で配布し、GitHub Actions で公開ビルドしています。',
      'download.androidReq': 'Android 8.0+ · arm64-v8a · OpenGL ES 3.0 · そのまま導入可能',
      'download.iosReq': 'iOS 15.0+ · arm64 · 未署名（AltStore / SideStore / Sideloadly で署名）',
      'download.switchReq': 'Homebrew Menu アプリモード · 4つの NRO を SD /switch/ へ配置',
      'download.copy': 'コピー',
      'download.allReleases': 'リリース一覧',
      'download.ciBuilds': '最新ビルド',
      'download.source': 'ソースコード',
      'footer.license': 'GNU General Public License v2.0 (GPL-2.0) に基づき公開。ゲームデータは同梱されません。',
      'footer.credits': 'CORE UPSTREAM CREDITS',
      'toast.qqCopied': 'QQ グループ番号をコピーしました：383917628',
      'toast.pokes': [
        'Alice // Wine 不要・100% Native SDL2',
        'Alice // 60fps VSync ロック中',
        'Alice // X4SS ⇄ GD\\x01\\x01 セーブ互換対応',
      ],
    },
  };

  const ENGINES = {
    sys3: {
      badge: 'SYSTEM3-SDL2',
      era: '1989 — 1995',
      markers: ['ADISK.DAT', 'ACG.DAT', 'AMUS.DAT', 'ASCE.DAT'],
      termTitle: 'system3-sdl2 · dat runtime',
      games: [
        { slug: 'rance01', label: 'Rance I (DAT)' },
        { slug: 'toushin2', label: 'Classic DAT' },
      ],
      copy: {
        zh: {
          title: '早期 System 1–3 原生引擎',
          desc: '面向采用早期 DAT 资源架构的经典作品（如《兰斯 I》《兰斯 II》《兰斯 III》原版），提供原生触控交互、独立存档隔离与轻量快进体验。',
          log: '[BOOT] Mounted ADISK.DAT + ACG.DAT (System 1-3)\n[GFX ] Indexed palette blitter -> SDL2 texture OK\n[CTRL] Direct native touch mode active\n[SAVE] Scoped directory: saves/<game>/ [READY]',
        },
        en: {
          title: 'Early System 1–3 Native Runtime',
          desc: 'Runs classic DAT-archive titles such as the original Rance I, II, and III with direct native touch input, isolated saves, and read-text skip.',
          log: '[BOOT] Mounted ADISK.DAT + ACG.DAT (System 1-3)\n[GFX ] Indexed palette blitter -> SDL2 texture OK\n[CTRL] Direct native touch mode active\n[SAVE] Scoped directory: saves/<game>/ [READY]',
        },
        ja: {
          title: '初期 System 1–3 ネイティブエンジン',
          desc: '『ランス I』『ランス II』『ランス III』オリジナル版など DAT 形式の初期作品を実行。ネイティブタッチ操作と作品別セーブ管理に対応。',
          log: '[BOOT] Mounted ADISK.DAT + ACG.DAT (System 1-3)\n[GFX ] Indexed palette blitter -> SDL2 texture OK\n[CTRL] Direct native touch mode active\n[SAVE] Scoped directory: saves/<game>/ [READY]',
        },
      },
    },
    sys35: {
      badge: 'XSYSTEM35',
      era: '1996 — 2003',
      markers: ['System39.ini', 'System39.ain', '*SA.ALD', 'ADISK.ALD', 'playlist.txt'],
      termTitle: 'xsystem35 · ald + midi',
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
          log: '[BOOT] Detected System39.ain + *SA.ALD archive set\n[AUD ] MIDI synth active + playlist.txt Ogg/MP3 CDDA\n[TEXT] Shift-JIS / GBK / UTF-8 glyph pipeline OK\n[TOOL] Overlay + gamepad bridge + live modifier',
        },
        en: {
          title: 'System 3.5–3.9 Golden-Era Kernel',
          desc: 'Full System 3.5–3.9 bytecode & modules (SACT, ShGraph, ShSound, eDemo), PMS/QNT/VSP/WebP graphics, multi-encoding CJK text, virtual CD tracks, and native MIDI playback.',
          log: '[BOOT] Detected System39.ain + *SA.ALD archive set\n[AUD ] MIDI synth active + playlist.txt Ogg/MP3 CDDA\n[TEXT] Shift-JIS / GBK / UTF-8 glyph pipeline OK\n[TOOL] Overlay + gamepad bridge + live modifier',
        },
        ja: {
          title: 'System 3.5–3.9 黄金期ネイティブコア',
          desc: 'System 3.5–3.9 バイトコード＆拡張モジュール、PMS/QNT/VSP/WebP 描画、Shift-JIS/GBK/Unicode、仮想 CD 音源、ネイティブ MIDI 再生に対応。',
          log: '[BOOT] Detected System39.ain + *SA.ALD archive set\n[AUD ] MIDI synth active + playlist.txt Ogg/MP3 CDDA\n[TEXT] Shift-JIS / GBK / UTF-8 glyph pipeline OK\n[TOOL] Overlay + gamepad bridge + live modifier',
        },
      },
    },
    sys4: {
      badge: 'XSYSTEM4',
      era: '2002 — PRESENT',
      markers: ['AliceStart.ini', 'AliceStart.CHS', 'System40.ini', '*.ain', 'CHS.TBL'],
      termTitle: 'xsystem4 · ain v0–v14.1',
      games: [
        { slug: 'rance10', label: '兰斯 10' },
        { slug: 'dohnadohna', label: '多娜多娜' },
        { slug: 'evenicle2', label: '夏娃年代记 2' },
        { slug: 'sengokurance', label: '战国兰斯' },
      ],
      copy: {
        zh: {
          title: 'System 4.x 全世代原生内核',
          desc: '覆盖早期 System 4.0（SACT2 / StoatSpriteEngine / DrawDungeon / ReignEngine）至现代旗舰（AIN v12–v14.1、PartsEngine、SealEngine 3D UBO 骨骼、FLAT v15、Ogg Opus、CHS.TBL/UIF 汉化映射与 <memory> 虚拟存档）。',
          log: '[BOOT] Loaded AIN v14.1 · O(deg) Delegate Reverse Index\n[GFX ] GLES 3.0 SealEngine UBO + FLAT v15 · 60.0 FPS\n[CJK ] GBK / SJIS / CHS.TBL + smart CP932 system popup\n[SAVE] X4SS ⇄ GD\\x01\\x01 PC export + <memory> snapshot OK',
        },
        en: {
          title: 'Full-Generation System 4.x Kernel',
          desc: 'Spans early System 4.0 (SACT2, StoatSpriteEngine, DrawDungeon, ReignEngine) through modern flagships (AIN v12–v14.1, PartsEngine, SealEngine 3D UBO, FLAT v15, Ogg Opus, CHS.TBL/UIF remaps, and <memory> snapshots).',
          log: '[BOOT] Loaded AIN v14.1 · O(deg) Delegate Reverse Index\n[GFX ] GLES 3.0 SealEngine UBO + FLAT v15 · 60.0 FPS\n[CJK ] GBK / SJIS / CHS.TBL + smart CP932 system popup\n[SAVE] X4SS ⇄ GD\\x01\\x01 PC export + <memory> snapshot OK',
        },
        ja: {
          title: 'System 4.x 全世代ネイティブコア',
          desc: '初期 System 4.0 から最新大規模作（AIN v12–v14.1、PartsEngine、SealEngine 3D UBO、FLAT v15、Ogg Opus、CHS.TBL/UIF、<memory> セーブ）まで網羅。',
          log: '[BOOT] Loaded AIN v14.1 · O(deg) Delegate Reverse Index\n[GFX ] GLES 3.0 SealEngine UBO + FLAT v15 · 60.0 FPS\n[CJK ] GBK / SJIS / CHS.TBL + smart CP932 system popup\n[SAVE] X4SS ⇄ GD\\x01\\x01 PC export + <memory> snapshot OK',
        },
      },
    },
  };

  const PLATFORMS = {
    android: {
      pill: 'ANDROID · ARM64-V8A',
      deckMode: 'ANDROID TOUCH + PAD',
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
          headline: 'Android Native Scheduling & Visual Gamepad Editor',
          summary: 'Direct APK install with foreground-service installs, guided shared storage (Internal Storage → AliceHub) save migration, ADPF boost, and Android 15+ 16 KB alignment.',
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
      pill: 'iOS / iPadOS · ARM64',
      deckMode: 'iOS TOUCH + iCLOUD',
      footer: ['FILES → ALICEHUB/GAMES', 'HOLD → SKIP (SYS4)', 'iCLOUD DRIVE SYNC'],
      copy: {
        zh: {
          headline: 'SwiftUI 原生壳体、iCloud 同步与 iOS 26 后台任务',
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
      pill: 'NINTENDO SWITCH · 4× NRO',
      deckMode: 'JOY-CON · L+R OVERLAY',
      footer: ['L + R → MENU', 'X → SEARCH · Y → SETTINGS', '4× NRO → /switch/'],
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
     1. CINEMATIC LASER-SPLIT BOOT CURTAIN
     ======================================================================== */
  function runBootIntro() {
    const curtain = document.getElementById('boot-curtain');
    const laser = document.getElementById('boot-laser');
    const pctEl = document.getElementById('boot-pct');
    if (!curtain || REDUCED_MOTION) {
      document.body.classList.remove('is-booting');
      document.body.classList.add('is-ready');
      if (curtain) curtain.remove();
      return;
    }

    const start = performance.now();
    const duration = 460;

    function tick(now) {
      const t = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      if (pctEl) pctEl.textContent = String(Math.round(eased * 100)).padStart(2, '0') + '%';
      if (laser) laser.style.transform = `scaleX(${eased.toFixed(3)})`;

      if (t < 1) {
        requestAnimationFrame(tick);
      } else {
        setTimeout(() => {
          curtain.classList.add('is-open');
          document.body.classList.remove('is-booting');
          document.body.classList.add('is-ready');
          setTimeout(() => curtain.classList.add('is-done'), 740);
        }, 60);
      }
    }
    requestAnimationFrame(tick);
  }

  /* ========================================================================
     2. CALM ARCHITECTURAL KINETIC LIGHT-FIELD CANVAS
     ======================================================================== */
  const ripples = [];
  const ptr = { x: 0.5, y: 0.5, tx: 0.5, ty: 0.5 };

  function spawnRipple(clientX, clientY) {
    const hero = document.getElementById('hero');
    if (!hero) return;
    const rect = hero.getBoundingClientRect();
    ripples.push({
      x: clientX - rect.left,
      y: clientY - rect.top,
      r: 12,
      maxR: Math.max(rect.width, rect.height) * 0.55,
      alpha: 0.72,
    });
  }

  function initHeroCanvas() {
    const canvas = document.getElementById('hero-canvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let w = 0, h = 0, dpr = 1;
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

    let time = 0;

    function drawWave(color, amp, freq, yRatio, phaseShift, width) {
      ctx.strokeStyle = color;
      ctx.lineWidth = width;
      ctx.beginPath();
      const step = Math.max(12, Math.floor(w / 72));
      const baseY = h * yRatio + (ptr.y - 0.5) * 44;
      for (let x = -step; x <= w + step; x += step) {
        const nx = x / w;
        const dist = Math.exp(-Math.pow((nx - ptr.x) * 2.4, 2));
        const y = baseY
          + Math.sin(nx * freq + time + phaseShift) * (amp + dist * 26)
          + Math.cos(nx * (freq * 0.5) - time * 0.7) * (amp * 0.45);
        if (x === -step) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
    }

    function draw() {
      ctx.clearRect(0, 0, w, h);
      ptr.x += (ptr.tx - ptr.x) * 0.06;
      ptr.y += (ptr.ty - ptr.y) * 0.06;
      time += REDUCED_MOTION ? 0 : 0.011;

      // Subtle architectural grid
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.026)';
      ctx.lineWidth = 1;
      const cell = 72;
      const ox = (ptr.x - 0.5) * -18;
      const oy = (ptr.y - 0.5) * -14;
      ctx.beginPath();
      for (let x = (ox % cell); x < w; x += cell) {
        ctx.moveTo(x, 0);
        ctx.lineTo(x, h);
      }
      for (let y = (oy % cell); y < h; y += cell) {
        ctx.moveTo(0, y);
        ctx.lineTo(w, y);
      }
      ctx.stroke();

      // Soft ambient heterochromia aura anchored behind monolith
      const gx1 = w * (0.72 + (ptr.x - 0.5) * 0.08);
      const gy1 = h * (0.45 + (ptr.y - 0.5) * 0.08);
      const grad1 = ctx.createRadialGradient(gx1 - 60, gy1 - 30, 0, gx1 - 60, gy1 - 30, Math.min(w, h) * 0.46);
      grad1.addColorStop(0, 'rgba(225, 75, 134, 0.11)');
      grad1.addColorStop(1, 'transparent');
      ctx.fillStyle = grad1;
      ctx.fillRect(0, 0, w, h);

      const grad2 = ctx.createRadialGradient(gx1 + 70, gy1 + 40, 0, gx1 + 70, gy1 + 40, Math.min(w, h) * 0.44);
      grad2.addColorStop(0, 'rgba(56, 189, 248, 0.09)');
      grad2.addColorStop(1, 'transparent');
      ctx.fillStyle = grad2;
      ctx.fillRect(0, 0, w, h);

      // Sculptural dual-channel silk waves
      drawWave('rgba(225, 75, 134, 0.22)', 26, 4.2, 0.62, 0, 1.4);
      drawWave('rgba(56, 189, 248, 0.18)', 22, 3.6, 0.65, 1.4, 1.2);
      drawWave('rgba(255, 255, 255, 0.06)', 16, 5.0, 0.58, 2.6, 0.9);

      // Clean click ripples
      for (let i = ripples.length - 1; i >= 0; i--) {
        const rp = ripples[i];
        rp.r += 11;
        rp.alpha *= 0.94;
        if (rp.alpha < 0.015 || rp.r > rp.maxR) {
          ripples.splice(i, 1);
          continue;
        }
        ctx.lineWidth = 1.4;
        ctx.strokeStyle = `rgba(225, 75, 134, ${(rp.alpha * 0.65).toFixed(3)})`;
        ctx.beginPath();
        ctx.arc(rp.x, rp.y, rp.r, 0, Math.PI * 2);
        ctx.stroke();

        ctx.strokeStyle = `rgba(56, 189, 248, ${(rp.alpha * 0.55).toFixed(3)})`;
        ctx.beginPath();
        ctx.arc(rp.x, rp.y, rp.r * 0.84, 0, Math.PI * 2);
        ctx.stroke();
      }

      if (!REDUCED_MOTION) requestAnimationFrame(draw);
    }

    draw();

    const hero = document.getElementById('hero');
    if (hero) {
      hero.addEventListener('click', (e) => {
        if (e.target.closest('a, button')) return;
        spawnRipple(e.clientX, e.clientY);
      });
    }
  }

  /* ========================================================================
     3. ALICE MONOLITH 3D TILT + LIVE EYE TRACKING + SPOTLIGHT CARDS
     ======================================================================== */
  function initAliceAndSpotlights() {
    const shrine = document.getElementById('alice-shrine');
    const card = document.getElementById('shrine-card');
    const avatarBox = document.getElementById('alice-avatar-box');
    const eyeM = document.getElementById('alice-eye-m');
    const eyeC = document.getElementById('alice-eye-c');

    if (avatarBox && !REDUCED_MOTION) {
      const scheduleBlink = () => {
        setTimeout(() => {
          if (!avatarBox.classList.contains('is-winking')) {
            avatarBox.classList.add('is-blinking');
            setTimeout(() => avatarBox.classList.remove('is-blinking'), 120);
          }
          scheduleBlink();
        }, 2800 + Math.random() * 3400);
      };
      scheduleBlink();
    }

    // Specular spotlight on all cards
    document.querySelectorAll('.spotlight-card').forEach((el) => {
      el.addEventListener('pointermove', (e) => {
        const r = el.getBoundingClientRect();
        el.style.setProperty('--sx', `${(e.clientX - r.left).toFixed(0)}px`);
        el.style.setProperty('--sy', `${(e.clientY - r.top).toFixed(0)}px`);
      }, { passive: true });
    });

    window.addEventListener('pointermove', (e) => {
      ptr.tx = e.clientX / Math.max(1, window.innerWidth);
      ptr.ty = e.clientY / Math.max(1, window.innerHeight);

      if (REDUCED_MOTION || !avatarBox || !eyeM || !eyeC || !card) return;

      const rect = avatarBox.getBoundingClientRect();
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height * 0.72;
      const dx = Math.max(-1, Math.min(1, (e.clientX - cx) / Math.max(180, window.innerWidth * 0.45)));
      const dy = Math.max(-1, Math.min(1, (e.clientY - cy) / Math.max(180, window.innerHeight * 0.45)));

      eyeM.style.transform = `translate3d(${(dx * 4.8).toFixed(2)}px, ${(dy * 3.4).toFixed(2)}px, 0)`;
      eyeC.style.transform = `translate3d(${(dx * 4.8).toFixed(2)}px, ${(dy * 3.4).toFixed(2)}px, 0)`;

      card.style.transform = `rotateX(${(-dy * 9).toFixed(2)}deg) rotateY(${(dx * 10).toFixed(2)}deg)`;
      const cr = card.getBoundingClientRect();
      card.style.setProperty('--mx', `${(e.clientX - cr.left).toFixed(0)}px`);
      card.style.setProperty('--my', `${(e.clientY - cr.top).toFixed(0)}px`);
    }, { passive: true });

    if (shrine && card && avatarBox) {
      shrine.addEventListener('click', (e) => {
        const rect = shrine.getBoundingClientRect();
        spawnRipple(e.clientX || rect.left + rect.width / 2, e.clientY || rect.top + rect.height / 2);
        card.classList.remove('is-poked');
        void card.offsetWidth;
        card.classList.add('is-poked');
        avatarBox.classList.add('is-winking');
        setTimeout(() => avatarBox.classList.remove('is-winking'), 320);

        const pokes = I18N[currentLang]['toast.pokes'];
        showToast(pokes[pokeCount % pokes.length]);
        pokeCount++;
      });
    }
  }

  /* ========================================================================
     4. MINIMAL PRECISION CURSOR
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

    document.addEventListener('mouseleave', () => cursor.classList.remove('is-visible'));

    function loop() {
      cx += (mx - cx) * 0.24;
      cy += (my - cy) * 0.24;
      cursor.style.transform = `translate3d(${cx.toFixed(1)}px, ${cy.toFixed(1)}px, 0)`;
      requestAnimationFrame(loop);
    }
    loop();
  }

  /* ========================================================================
     5. SCROLL CHOREOGRAPHY (HAIRLINE + GHOST NUMERAL PARALLAX + QUIET TICKER)
     ======================================================================== */
  function initScroll() {
    const nav = document.getElementById('site-nav');
    const hairline = document.getElementById('scroll-hairline-fill');
    const navLinks = Array.from(document.querySelectorAll('.nav-links a[data-sec]'));
    const sections = Array.from(document.querySelectorAll('section[data-section]'));
    const ghosts = Array.from(document.querySelectorAll('[data-parallax]'));
    const quietTrack = document.getElementById('quiet-track');

    const revealObs = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-in');
          revealObs.unobserve(entry.target);
        }
      });
    }, { threshold: 0.01, rootMargin: '0px 0px 80px 0px' });

    const revealAll = Array.from(document.querySelectorAll('.reveal-up'));
    revealAll.forEach((el) => revealObs.observe(el));

    const sweepVisibleReveals = () => {
      const vh = window.innerHeight;
      const nearBottom = window.scrollY + vh >= document.documentElement.scrollHeight - 160;
      revealAll.forEach((el) => {
        if (el.classList.contains('is-in')) return;
        const r = el.getBoundingClientRect();
        if (nearBottom || r.top < vh + 60) {
          el.classList.add('is-in');
          revealObs.unobserve(el);
        }
      });
    };
    window.addEventListener('scroll', sweepVisibleReveals, { passive: true });

    const secObs = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const id = entry.target.dataset.section;
          navLinks.forEach((a) => a.classList.toggle('is-active', a.dataset.sec === id));
        }
      });
    }, { threshold: 0.32 });

    sections.forEach((s) => secObs.observe(s));

    let lastY = window.scrollY;
    let vel = 0;
    let tickerX = 0;

    function frame() {
      const y = window.scrollY;
      vel += (y - lastY - vel) * 0.14;
      lastY = y;

      if (nav) nav.classList.toggle('is-scrolled', y > 24);
      const maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
      const pct = Math.max(0, Math.min(1, y / maxScroll));
      if (hairline) hairline.style.width = `${(pct * 100).toFixed(2)}%`;

      if (!REDUCED_MOTION) {
        // Parallax on ghosted architectural numerals
        ghosts.forEach((g) => {
          const rect = g.parentElement.getBoundingClientRect();
          const factor = Number(g.dataset.parallax || 0.12);
          const shift = (rect.top - window.innerHeight * 0.45) * factor;
          g.style.transform = `translate3d(0, ${shift.toFixed(1)}px, 0)`;
        });

        // Smooth scroll-responsive quiet ticker
        if (quietTrack) {
          const half = quietTrack.scrollWidth / 2 || 1000;
          tickerX -= 0.55 + Math.min(4, Math.abs(vel) * 0.08);
          if (tickerX <= -half) tickerX += half;
          quietTrack.style.transform = `translate3d(${tickerX.toFixed(1)}px, 0, 0)`;
        }
      }

      requestAnimationFrame(frame);
    }

    if (!REDUCED_MOTION) {
      requestAnimationFrame(frame);
    } else {
      window.addEventListener('scroll', () => {
        if (nav) nav.classList.toggle('is-scrolled', window.scrollY > 24);
      }, { passive: true });
    }
  }

  /* ========================================================================
     6. ENGINE SWITCHER
     ======================================================================== */
  let termTimer = null;

  function renderEngine(key, withWipe) {
    currentEngine = key;
    const data = ENGINES[key];
    const copy = data.copy[currentLang] || data.copy.zh;

    document.querySelectorAll('.engine-btn').forEach((btn) => {
      const active = btn.dataset.engine === key;
      btn.classList.toggle('is-active', active);
      btn.setAttribute('aria-selected', String(active));
    });

    if (withWipe && !REDUCED_MOTION) {
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
      markersEl.innerHTML = data.markers.map((m) => `<span class="marker-chip">${escapeHtml(m)}</span>`).join('');
    }

    const gamesEl = document.getElementById('eng-games');
    if (gamesEl) {
      gamesEl.innerHTML = data.games
        .map((g) => `<span class="sample-chip"><img src="assets/games/${g.slug}.webp" alt="" width="26" height="26" /><span>${escapeHtml(g.label)}</span></span>`)
        .join('');
    }

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
          idx += 6;
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
     7. PLATFORM SWITCHER + TACTILE CONTROL DECK
     ======================================================================== */
  function renderPlatform(key, withWipe) {
    currentPlatform = key;
    const spec = PLATFORMS[key];
    const copy = spec.copy[currentLang] || spec.copy.zh;
    const asset = RELEASE.assets[key];

    document.querySelectorAll('.seg-tab').forEach((btn) => {
      const active = btn.dataset.platform === key;
      btn.classList.toggle('is-active', active);
      btn.setAttribute('aria-selected', String(active));
    });

    if (withWipe && !REDUCED_MOTION) {
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
    if (ul) ul.innerHTML = copy.bullets.map((b) => `<li>${escapeHtml(b)}</li>`).join('');

    const footer = document.getElementById('deck-footer');
    if (footer) footer.innerHTML = spec.footer.map((t) => `<span>${escapeHtml(t)}</span>`).join('');
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
        zh: ['[A / 单指轻触] 确认并推进剧情。', 'SDL_BUTTON_LEFT'],
        en: ['[A / TAP] Confirm and advance scene.', 'SDL_BUTTON_LEFT'],
        ja: ['[A / タップ] 決定・メッセージ送り。', 'SDL_BUTTON_LEFT'],
      },
      CANCEL: {
        zh: ['[B / 双指轻触] 呼出右键菜单或取消操作。', 'SDL_BUTTON_RIGHT'],
        en: ['[B / 2-FINGER] Right-click menu or cancel.', 'SDL_BUTTON_RIGHT'],
        ja: ['[B / 2本指タップ] 右クリックメニュー・戻る。', 'SDL_BUTTON_RIGHT'],
      },
      LR: {
        zh: ['[L+R] 已打开局内菜单：截图 / 字号缩放 / 修改器。', 'OVERLAY MENU OPEN'],
        en: ['[L+R] In-game menu open: Screenshot · Font Scale · Modifiers.', 'OVERLAY MENU OPEN'],
        ja: ['[L+R] ゲーム内メニュー起動：スクショ・文字拡大・改造。', 'OVERLAY MENU OPEN'],
      },
      MENU: {
        zh: ['[悬浮球] 切换虚拟按键显隐、字号缩放与修改器。', 'FLOATING HUB'],
        en: ['[FLOATING BALL] Toggle virtual pad, scaling, or modifiers.', 'FLOATING HUB'],
        ja: ['[フローティング] 仮想パッド・文字拡大・補助メニュー。', 'FLOATING HUB'],
      },
      SHOT: {
        zh: ['[Y] 已保存无损画面截图至 screenshots 目录。', 'PNG SAVED'],
        en: ['[Y] Lossless screenshot saved.', 'PNG SAVED'],
        ja: ['[Y] スクリーンショットを保存しました。', 'PNG SAVED'],
      },
      SKIP: {
        zh: ['[X] 单次快进跳过当前台词。', 'SDLK_LCTRL PULSE'],
        en: ['[X] Fast text skip pulse.', 'SDLK_LCTRL PULSE'],
        ja: ['[X] テキストスキップ。', 'SDLK_LCTRL PULSE'],
      },
      WHEEL_UP: {
        zh: ['[LB / 双指下滑] 鼠标滚轮向上 → 打开历史对话记录。', 'WHEEL +1 (BACKLOG)'],
        en: ['[LB / SWIPE DOWN] Wheel up → Open backlog.', 'WHEEL +1 (BACKLOG)'],
        ja: ['[LB / 下スワイプ] ホイール上 → バックログ表示。', 'WHEEL +1 (BACKLOG)'],
      },
      WHEEL_DN: {
        zh: ['[RB / 双指上滑] 鼠标滚轮向下 → 推进下一句台词。', 'WHEEL -1 (NEXT)'],
        en: ['[RB / SWIPE UP] Wheel down → Next line.', 'WHEEL -1 (NEXT)'],
        ja: ['[RB / 上スワイプ] ホイール下 → 次の台詞へ。', 'WHEEL -1 (NEXT)'],
      },
      CURSOR: {
        zh: ['[虚拟光标] 切换左摇杆虚拟鼠标光标。', 'VIRTUAL CURSOR TOGGLED'],
        en: ['[CURSOR] Virtual mouse cursor toggled.', 'VIRTUAL CURSOR TOGGLED'],
        ja: ['[仮想カーソル] 左スティック仮想マウス切替。', 'VIRTUAL CURSOR TOGGLED'],
      },
    };

    deck.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-pad]');
      if (!btn) return;
      const code = btn.dataset.pad;

      btn.classList.add('is-hit');
      setTimeout(() => btn.classList.remove('is-hit'), 150);

      if (code === 'CTRL') {
        ctrlLatched = !ctrlLatched;
        if (ctrlPill) ctrlPill.classList.toggle('is-latched', ctrlLatched);
        if (fpsEl) fpsEl.textContent = ctrlLatched ? '60.0 FPS · CTRL' : '60.0 FPS';
        if (speakerEl) speakerEl.textContent = 'ALICEHUB // CTRL LATCH';
        if (textEl) {
          textEl.textContent = ctrlLatched
            ? (currentLang === 'en' ? '⏩ Ctrl latched — fast-forwarding smoothly at 60fps.' : currentLang === 'ja' ? '⏩ Ctrl 固定中 — 60fps で高速スキップ中。' : '⏩ Ctrl 已锁定 — 60fps 持续快进对话，再次点击解除。')
            : (currentLang === 'en' ? '⏩ Ctrl released — normal dialogue pacing.' : currentLang === 'ja' ? '⏩ Ctrl 解除 — 通常速度に戻りました。' : '⏩ Ctrl 已解除 — 恢复正常对话速度。');
        }
        if (signalEl) signalEl.textContent = ctrlLatched ? 'LATCHED: SDLK_LCTRL' : 'READY';
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
            ? `Directional input [${code}] mapped to virtual cursor / dungeon movement.`
            : currentLang === 'ja'
              ? `方向入力 [${code}] を仮想カーソル／ダンジョン移動へ送信。`
              : `方向键 [${code}] 已映射至虚拟光标 / 3D 迷宫移动。`;
        }
        if (signalEl) signalEl.textContent = `AXIS ${code}`;
      }
    });
  }

  /* ========================================================================
     8. HEX SAVE MORPH & FONT SCALE SLIDER
     ======================================================================== */
  function initWidgets() {
    const hexSrc = document.getElementById('hex-src');
    const hexDst = document.getElementById('hex-dst');
    const srcLabel = document.getElementById('morph-src-label');
    const dstLabel = document.getElementById('morph-dst-label');
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
      }, 2400);
    }

    const range = document.getElementById('demo-font-range');
    const valEl = document.getElementById('demo-font-val');
    const preview = document.getElementById('slider-preview');
    if (range && valEl && preview) {
      const sync = () => {
        const v = Number(range.value);
        valEl.textContent = `${v}%`;
        preview.style.fontSize = `${(14 * v / 100).toFixed(1)}px`;
      };
      range.addEventListener('input', sync);
      sync();
    }
  }

  /* ========================================================================
     9. CATALOG FILMSTRIP (ALL PRELOADED, ZERO BLANK FRAMES)
     ======================================================================== */
  function renderCatalogWall() {
    const stripA = document.getElementById('art-strip-a');
    const stripB = document.getElementById('art-strip-b');
    if (!stripA || !stripB) return;

    const makeTile = (g) => {
      const title = g[currentLang] || g.zh;
      return `<div class="game-tile" data-cursor="${g.eng}">
        <img src="assets/games/${g.slug}.webp" alt="${escapeHtml(title)}" width="144" height="144" />
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
    set('dl-release-tag', RELEASE.tag);

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
        const fmtSize = (b) => `${(b / (1024 * 1024)).toFixed(1)} MiB`;
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
      .catch(() => {});
  }

  /* ========================================================================
     11. LANGUAGE SWITCHER & HELPERS
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
      if (key === 'hero.lead') el.innerHTML = dict[key];
      else el.textContent = dict[key];
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

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.engine-btn').forEach((btn) => {
      btn.addEventListener('click', () => renderEngine(btn.dataset.engine, true));
    });
    document.querySelectorAll('.seg-tab').forEach((btn) => {
      btn.addEventListener('click', () => renderPlatform(btn.dataset.platform, true));
    });
    document.querySelectorAll('.lang-btn').forEach((btn) => {
      btn.addEventListener('click', () => applyLanguage(btn.dataset.lang));
    });

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
    initAliceAndSpotlights();
    initCursor();
    initScroll();
    initPlayableDeck();
    initWidgets();
    runBootIntro();
    fetchLatestRelease();
  });
})();
