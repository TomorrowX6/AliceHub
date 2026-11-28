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

  /* 36 Curated Iconic Titles across 3 Opposing Filmstrip Rows (12 × 3) */
  const CATALOG_GAMES = [
    // Row 1: Flagship System 4.x
    { slug: 'rance10', eng: 'SYS4', cn: true, zh: '兰斯 10 -决战-', en: 'Rance X', ja: 'ランス10 -決戦-', caps: ['PC 兼容存档', '多实例目录', '离线存档编辑', 'MOD 管理'] },
    { slug: 'dohnadohna', eng: 'SYS4', cn: true, zh: '多娜多娜 一起干坏事吧', en: 'Dohna Dohna', ja: 'ドーナドーナ', caps: ['AIN v14.1', 'PC 兼容存档', 'Parts 字号缩放', 'MOD 管理'] },
    { slug: 'evenicle2', eng: 'SYS4', cn: true, zh: '夏娃年代记 2', en: 'Evenicle 2', ja: 'イブニクル2', caps: ['SealEngine 3D', 'PC 兼容存档', '局内修改器', '启动入口'] },
    { slug: 'evenicle', eng: 'SYS4', cn: true, zh: '夏娃年代记', en: 'Evenicle', ja: 'イブニクル', caps: ['3D 地图渲染', 'PC 兼容存档', '局内修改器', '虚拟手柄'] },
    { slug: 'sengokurance', eng: 'SYS4', cn: true, zh: '战国兰斯', en: 'Sengoku Rance', ja: '戦国ランス', caps: ['SACT2', '局内修改器', '汉化补丁', 'PC 兼容存档'] },
    { slug: 'rance09', eng: 'SYS4', cn: true, zh: '兰斯 09 赫尔曼革命', en: 'Rance IX', ja: 'ランス9', caps: ['PartsEngine', '局内修改器', '字号缩放', 'PC 兼容存档'] },
    { slug: 'rance08', eng: 'SYS4', cn: true, zh: '兰斯 08 Quest Magnum', en: 'Rance Quest', ja: 'ランス・クエスト', caps: ['局内修改器', '多碟合并', 'PC 兼容存档', '虚拟手柄'] },
    { slug: 'rance06', eng: 'SYS4', cn: true, zh: '兰斯 VI 赛斯崩坏', en: 'Rance VI', ja: 'ランスVI', caps: ['3D 迷宫渲染', '原版迷宫兼容', '虚拟摇杆', 'PC 兼容存档'] },
    { slug: 'rance03', eng: 'SYS4', cn: true, zh: '兰斯 03 利萨斯陷落', en: 'Rance 03', ja: 'ランス03', caps: ['PartsEngine', '高清重排字体', 'MOD 补丁', 'PC 兼容存档'] },
    { slug: 'rance01', eng: 'SYS4', cn: true, zh: '兰斯 01 寻找小光', en: 'Rance 01', ja: 'ランス01', caps: ['PartsEngine', '字号缩放', 'PC 兼容存档', '虚拟手柄'] },
    { slug: 'daiteikoku', eng: 'SYS4', cn: true, zh: '大帝国', en: 'Daiteikoku', ja: '大帝国', caps: ['SACT2', '战略触控/手柄', '汉化补丁', 'PC 兼容存档'] },
    { slug: 'escalayer_reboot', eng: 'SYS4', cn: true, zh: '超昂天使 R 重制版', en: 'Escalayer Reboot', ja: '超昂天使R', caps: ['<memory> 存档', '多编码弹窗', '字号缩放', 'PC 兼容存档'] },
    // Row 2: System 4.x Fan Favorites & Localization Classics
    { slug: 'ixseal', eng: 'SYS4', cn: true, zh: '超昂神骑 Ixseal', en: 'Beat Valkyrie Ixseal', ja: '超昂神騎エクシール', caps: ['局内修改器', 'PartsEngine', '汉化适配', 'PC 兼容存档'] },
    { slug: 'haruka', eng: 'SYS4', cn: true, zh: '超昂闪忍 遥', en: 'Beat Blades Haruka', ja: '超昂閃忍ハルカ', caps: ['Ogg Opus 语音', '局内修改器', '汉化补丁', 'PC 兼容存档'] },
    { slug: 'maman', eng: 'SYS4', cn: true, zh: '再婚妈妈', en: 'Heartful Maman', ja: 'は～とふるママン', caps: ['精准字宽排版', '存档导入', '汉化适配', '虚拟手柄'] },
    { slug: 'oyakorankan', eng: 'SYS4', cn: true, zh: '母娘乱馆', en: 'Oyakorankan', ja: '母娘乱館', caps: ['CHS.TBL 映射', 'CP932 弹窗识别', '启动入口', '虚拟手柄'] },
    { slug: 'haharanman', eng: 'SYS4', cn: true, zh: '母烂漫', en: 'Haha Ranman', ja: '母爛漫', caps: ['ADV 立绘渲染', '存档导入', '汉化补丁', '虚拟手柄'] },
    { slug: 'tsumamigui3', eng: 'SYS4', cn: true, zh: '妻中蜜 3', en: 'Tsumamigui 3', ja: '妻みぐい3', caps: ['背景语音音量', '启动入口', '汉化适配', 'PC 兼容存档'] },
    { slug: 'daibanchou', eng: 'SYS4', cn: true, zh: '大番长', en: 'Daibanchou', ja: '大番長', caps: ['SACT2', '战略 SLG', '汉化补丁', 'PC 兼容存档'] },
    { slug: 'dorapeko', eng: 'SYS4', cn: true, zh: '多拉佩科', en: 'Dorapeko', ja: 'ドラペコ', caps: ['PartsEngine', '局内修改器', '汉化适配', 'PC 兼容存档'] },
    { slug: 'bladebriders', eng: 'SYS4', cn: false, zh: 'Blade Briders', en: 'Blade Briders', ja: 'ブレイドブライダーズ', caps: ['xsystem4', '3D 战斗', '虚拟手柄', 'PC 兼容存档'] },
    { slug: 'toushin3', eng: 'SYS4', cn: true, zh: '斗神都市 III', en: 'Toushin Toshi III', ja: '闘神都市III', caps: ['ReignEngine 3D', '迷宫摇杆映射', '汉化适配', 'PC 兼容存档'] },
    { slug: 'galzoo', eng: 'SYS4', cn: true, zh: 'GALZOO 岛', en: 'Galzoo Island', ja: 'GALZOOアイランド', caps: ['迷宫 RPG', '虚拟手柄', '汉化适配', 'PC 兼容存档'] },
    { slug: 'paschacpp', eng: 'SYS4', cn: true, zh: '帕斯茶 C++', en: 'Pascha C++', ja: 'ぱすちゃC++', caps: ['SACT2', '多编码排版', '虚拟手柄', '存档备份'] },
    // Row 3: System 3.5–3.9 & Classic Era Icons
    { slug: 'kichikuou', eng: 'SYS35', cn: true, zh: '鬼畜王兰斯', en: 'Kichikuou Rance', ja: '鬼畜王ランス', caps: ['xsystem35', '原生 MIDI', '虚拟 CD 音轨', '局内修改器'] },
    { slug: 'daiakuji', eng: 'SYS35', cn: true, zh: '大恶司', en: 'Daiakuji', ja: '大悪司', caps: ['xsystem35', '原生 MIDI', 'SLG 触控', '存档管理'] },
    { slug: 'mamatoto', eng: 'SYS35', cn: true, zh: '魔物娘战记', en: 'Mamatoto', ja: 'ママトト', caps: ['xsystem35', 'ALD/PMS 渲染', '虚拟手柄', '存档管理'] },
    { slug: 'toushin2', eng: 'SYS35', cn: true, zh: '斗神都市 II', en: 'Toushin Toshi II', ja: '闘神都市II', caps: ['xsystem35', '原生 MIDI', '经典 RPG', '虚拟手柄'] },
    { slug: 'atlachnacha', eng: 'SYS35', cn: true, zh: '阿托拉克纳克亚', en: 'Atlach-Nacha', ja: 'アトラク＝ナクア', caps: ['xsystem35', '原生 MIDI', '汉化适配', '局内悬浮窗'] },
    { slug: 'dalk', eng: 'SYS35', cn: false, zh: 'DALK 外传 / 达克', en: 'DALK', ja: 'DALK', caps: ['xsystem35', '经典战棋', 'MIDI 合成', '虚拟手柄'] },
    { slug: 'escalayer', eng: 'SYS35', cn: true, zh: '超昂天使 原版', en: 'Escalayer', ja: '超昂天使エスカレイヤー', caps: ['xsystem35', 'System 3.9', '虚拟 CD 音轨', '存档备份'] },
    { slug: 'rance02', eng: 'SYS4', cn: true, zh: '兰斯 02 反叛的少女们', en: 'Rance 02', ja: 'ランス02', caps: ['xsystem4', '局内修改器', '汉化补丁', 'PC 兼容存档'] },
    { slug: 'rance5d', eng: 'SYS35', cn: true, zh: '兰斯 5D 孤独的少女', en: 'Rance 5D', ja: 'ランス5D', caps: ['xsystem35', 'System 3.9', '汉化适配', '虚拟手柄'] },
    { slug: 'yorugakuru', eng: 'SYS35', cn: true, zh: '夜来了！', en: 'Yoru ga Kuru!', ja: '夜が来る!', caps: ['xsystem35', '原生 MIDI', '汉化适配', '局内悬浮窗'] },
    { slug: 'okusamantr', eng: 'SYS4', cn: true, zh: '太太的恢复术', en: 'Okusama no Kaifuku Jutsu', ja: '奥さまの回復術', caps: ['xsystem4', '立绘排版修复', '汉化适配', '虚拟手柄'] },
    { slug: 'ikusamiko', eng: 'SYS4', cn: false, zh: '战巫女', en: 'Ikusamiko', ja: '戦巫女', caps: ['xsystem4', 'SACT2', '虚拟手柄', '存档管理'] },
  ];

  const I18N = {
    zh: {
      'nav.engines': '三核引擎',
      'nav.platforms': '三端实机',
      'nav.features': '功能',
      'nav.catalog': '作品滚轴',
      'nav.download': '下载',
      'nav.cta': '获取版本',
      'hero.badge': 'NATIVE SDL2 · ZERO WINE',
      'hero.sub': '原生运行 System 1–4',
      'hero.lead': '<strong>Android、iOS 与 Switch</strong> 三端直跑 AliceSoft 经典作品，零转译 60fps，存档全互通。',
      'hero.ctaPrimary': '下载最新版',
      'hero.ctaSecondary': '功能一览',
      'hero.m1': '原生引擎',
      'hero.m2': '三端直跑',
      'hero.m3': '内置目录',
      'hero.m4': '转译损耗',
      'hero.hint': '移动光标追踪视差 · 点击触发脉冲',
      'engines.kicker': '01 / TRIPLE NATIVE KERNEL',
      'engines.title': '三套原生内核',
      'engines.sub': '自动识别目录标记并路由引擎，覆盖 1989 至今全世代作品。',
      'engines.markersLabel': '识别标记',
      'engines.samplesLabel': '代表作品',
      'platforms.kicker': '02 / MULTI-PLATFORM',
      'platforms.title': '三端原生，一致体验',
      'platforms.sub': '同步构建发布，均支持 PC 存档导出、启动入口切换与更新检查。',
      'platforms.deckTitle': '交互操控测试',
      'platforms.deckInitial': '点击按键或 » Ctrl，预览原生手势与手柄反馈。',
      'hb.tabControls': '操控映射',
      'hb.tabWorkflow': '四步上手',
      'hb.tabStorage': '存储与同步',
      'features.kicker': '03 / FEATURES',
      'features.title': '完整实机功能体系',
      'features.sub': '导入、MOD、编码、存档互通与局内工具，一站配齐。',
      'feat.f1.title': 'PC ⇄ 移动端双向存档互通',
      'feat.f1.desc': '按目录隔离并自动备份；按 .ain 结构将 X4SS 无损转为 PC 原版 GD\\x01\\x01 / .gsave / .rsm，支持 <memory> 隔离。',
      'feat.f2.title': '全格式直读与多碟合并',
      'feat.f2.desc': '内置流式解压与 ISO9660，直读 .zip/.rar/.7z/.iso/.mdf，自动合碟与修复乱码。',
      'feat.f3.title': '60fps 稳帧与后台恢复',
      'feat.f3.desc': '高优渲染/混音线程、ADPF 提频、16KB 页对齐与 EGL 纹理/3D 迷宫自动重建。',
      'feat.f4.title': '无损 MOD 与补丁管理',
      'feat.f4.desc': '支持在线/本地 MOD、汉化与去码补丁；覆盖前自动原子备份，一键还原原版。',
      'feat.f5.title': '多编码映射与多轨音频',
      'feat.f5.desc': '兼容 SJIS/GBK/UTF-8 与 CHS.TBL/UIF/word.mai；内置 Opus、原生 MIDI 与 CD 音轨。',
      'feat.f6.title': '流式在线安装与续传',
      'feat.f6.desc': '默认关闭按需开启；HTTP Range 断点续传、SHA-256 校验与后台保活，免双倍空间。',
      'feat.f7.title': '多实例、入口切换与同步',
      'feat.f7.desc': '《兰斯 10》多实例隔离、AliceStart.CHS 入口切换，支持共享目录与 iCloud 同步。',
      'feat.f8.title': '修改器、存档编辑与诊断',
      'feat.f8.desc': '离线存档编辑、局内数值锁定与主播模式；崩溃导出 .txt 诊断日志，三语亮暗主题。',
      'feat.f9.title': '虚拟手柄编辑器与实时字号缩放',
      'feat.f9.desc': 'GAL/RPG 预设与全屏拖拽编辑，支持主流手柄；PartsEngine 字体 75%–160% 无溢出缩放与滚动条加宽。',
      'feat.f9.preview': '兰斯 10 · 字体实时无损重排',
      'catalog.kicker': '04 / 171+ CATALOG · 36 ON REEL',
      'catalog.title': '作品滚轴',
      'catalog.sub': '内置 171+ 目录（含 54+ 汉化），自由导入任意 System 1–4 作品。悬停暂停，点击查看特性。',
      'catalog.fAll': '全部 36',
      'catalog.fCn': '汉化标识',
      'catalog.inspHint': '点击上方封面，查看引擎路由与支持特性。',
      'catalog.notice': '纯开源引擎与启动器，不含任何游戏本体，请自行准备合法游戏文件。',
      'download.kicker': '05 / DEPLOYMENT · GPL-2.0',
      'download.title': '下载最新版',
      'download.sub': '三端安装包由 GitHub Actions 公开构建并托管于 Releases。',
      'download.androidReq': 'Android 8.0+ · arm64-v8a · 直接安装',
      'download.iosReq': 'iOS 15+ · arm64 · AltStore / SideStore 自签',
      'download.switchReq': '应用模式 · 4 个 NRO 解压至 SD /switch/',
      'download.copy': '点击复制',
      'download.allReleases': '历史版本',
      'download.ciBuilds': '持续构建',
      'download.source': '开源仓库',
      'footer.license': 'GPL-2.0 协议开源 · 不含游戏本体',
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
      'nav.features': 'Features',
      'nav.catalog': 'Showcase',
      'nav.download': 'Download',
      'nav.cta': 'Get App',
      'hero.badge': 'NATIVE SDL2 · ZERO WINE',
      'hero.sub': 'Native System 1–4',
      'hero.lead': 'Run AliceSoft classics natively on <strong>Android, iOS & Switch</strong>—zero Wine, smooth 60fps, full PC save interop.',
      'hero.ctaPrimary': 'Download',
      'hero.ctaSecondary': 'Features',
      'hero.m1': 'Engines',
      'hero.m2': 'Platforms',
      'hero.m3': 'Catalog',
      'hero.m4': 'Overhead',
      'hero.hint': 'Move to track · Click for kinetic pulse',
      'engines.kicker': '01 / TRIPLE NATIVE KERNEL',
      'engines.title': 'Three Native Kernels',
      'engines.sub': 'Auto-detects root markers and routes every AliceSoft era to its native engine.',
      'engines.markersLabel': 'MARKERS',
      'engines.samplesLabel': 'HIGHLIGHTS',
      'platforms.kicker': '02 / MULTI-PLATFORM',
      'platforms.title': 'Three Platforms, One Standard',
      'platforms.sub': 'Built together on GitHub Releases with PC save export, boot entries & update checks.',
      'platforms.deckTitle': 'Interactive Input Deck',
      'platforms.deckInitial': 'Tap buttons or » Ctrl to preview native input events.',
      'hb.tabControls': 'Controls',
      'hb.tabWorkflow': 'Quick Start',
      'hb.tabStorage': 'Storage & Sync',
      'features.kicker': '03 / FEATURES',
      'features.title': 'Built for Real Handheld Play',
      'features.sub': 'Import, MODs, CJK remaps, PC saves & in-game tools in one launcher.',
      'feat.f1.title': 'PC ⇄ Mobile Save Interop',
      'feat.f1.desc': 'Per-game save isolation & auto backups; .ain-guided X4SS conversion to Windows GD\\x01\\x01 / .gsave / .rsm plus <memory> isolation.',
      'feat.f2.title': 'All-Format Import & Multi-Disc',
      'feat.f2.desc': 'Streaming libarchive + ISO9660 reads .zip/.rar/.7z/.iso/.mdf, merges multi-disc sets & fixes CJK filenames.',
      'feat.f3.title': '60fps Pacing & Recovery',
      'feat.f3.desc': 'High-priority render/audio threads, ADPF boost, 16 KB alignment & automatic EGL texture/3D dungeon rebuild.',
      'feat.f4.title': 'Atomic MODs & Patches',
      'feat.f4.desc': 'Online & local MODs, translations, and uncensored patches with automatic vanilla backup & one-tap restore.',
      'feat.f5.title': 'CJK Remaps & Multi-Track Audio',
      'feat.f5.desc': 'SJIS/GBK/UTF-8 & CHS.TBL/UIF/word.mai remaps; built-in Ogg Opus voices, native MIDI & virtual CDDA.',
      'feat.f6.title': 'Streaming Online Install',
      'feat.f6.desc': 'Off by default; HTTP Range resume, SHA-256 checks & background tasks without 2× staging space.',
      'feat.f7.title': 'Multi-Instance & Sync',
      'feat.f7.desc': 'Multi-folder Rance X isolation, AliceStart.CHS launch entries, Android shared storage & iOS iCloud sync.',
      'feat.f8.title': 'Modifiers, Save Editor & Logs',
      'feat.f8.desc': 'Offline save editor, live locks & streamer mode; crash cards export .txt logs; tri-lingual & Auto themes.',
      'feat.f9.title': 'Virtual Pad Editor & Live Font Scaling',
      'feat.f9.desc': 'GAL/RPG presets, drag-and-drop editor & Xbox/PS/Switch Pro controllers; 75%–160% PartsEngine text scaling.',
      'feat.f9.preview': 'Rance X · Live Non-Overflow Text Scale',
      'catalog.kicker': '04 / 171+ CATALOG · 36 ON REEL',
      'catalog.title': 'Game Reel',
      'catalog.sub': '171+ catalog entries (54+ CN), plus unrestricted System 1–4 import. Hover to pause; click any cover.',
      'catalog.fAll': 'All 36',
      'catalog.fCn': 'CN Tagged',
      'catalog.inspHint': 'Click any cover above to inspect engine & supported features.',
      'catalog.notice': 'Pure open-source launcher & engines. No games bundled—provide your own files.',
      'download.kicker': '05 / DEPLOYMENT · GPL-2.0',
      'download.title': 'Download Latest',
      'download.sub': 'Publicly built by GitHub Actions and hosted on GitHub Releases.',
      'download.androidReq': 'Android 8.0+ · arm64-v8a · Direct install',
      'download.iosReq': 'iOS 15+ · arm64 · Sign via AltStore / SideStore',
      'download.switchReq': 'App Mode · Copy 4 NROs to SD /switch/',
      'download.copy': 'Copy',
      'download.allReleases': 'Releases',
      'download.ciBuilds': 'CI Builds',
      'download.source': 'Source',
      'footer.license': 'GPL-2.0 Open Source · No games bundled',
      'footer.credits': 'CORE UPSTREAM CREDITS',
      'toast.qqCopied': 'Copied QQ Group: 383917628',
      'toast.pokes': [
        'Alice // 0% Wine · 100% Native SDL2',
        'Alice // 60fps VSync Locked',
        'Alice // X4SS ⇄ GD\\x01\\x01 Ready',
      ],
    },
    ja: {
      'nav.engines': 'エンジン',
      'nav.platforms': '対応機種',
      'nav.features': '機能',
      'nav.catalog': '作品リール',
      'nav.download': '入手',
      'nav.cta': '入手する',
      'hero.badge': 'NATIVE SDL2 · ZERO WINE',
      'hero.sub': 'System 1–4 ネイティブ実行',
      'hero.lead': '<strong>Android・iOS・Switch</strong> でアリスソフト名作を直実行。Wine 不要の 60fps、PC セーブ完全互換。',
      'hero.ctaPrimary': '最新版を入手',
      'hero.ctaSecondary': '機能一覧',
      'hero.m1': 'エンジン',
      'hero.m2': '対応機種',
      'hero.m3': '内蔵目録',
      'hero.m4': '変換負荷',
      'hero.hint': 'カーソル追従 · クリックでパルス',
      'engines.kicker': '01 / TRIPLE NATIVE KERNEL',
      'engines.title': '3系統ネイティブコア',
      'engines.sub': 'ルート構成を自動判別し、1989年から最新作まで最適エンジンへルーティング。',
      'engines.markersLabel': '判別マーカー',
      'engines.samplesLabel': '代表作',
      'platforms.kicker': '02 / MULTI-PLATFORM',
      'platforms.title': '3プラットフォーム統一体験',
      'platforms.sub': '同時配布。全機種で PC 互換セーブ出力・起動エントリ切替・更新確認に対応。',
      'platforms.deckTitle': '操作テスト',
      'platforms.deckInitial': 'ボタンまたは » Ctrl を押して入力応答を確認。',
      'hb.tabControls': '操作対応表',
      'hb.tabWorkflow': '導入4手順',
      'hb.tabStorage': '保存と同期',
      'features.kicker': '03 / FEATURES',
      'features.title': '実機プレイ向け完全機能',
      'features.sub': '導入・MOD・文字コード・PC セーブ互換・補助ツールを統合。',
      'feat.f1.title': 'PC ⇄ モバイル双方向セーブ互換',
      'feat.f1.desc': '作品別分離＆自動バックアップ。.ain 参照で X4SS を PC 版 GD\\x01\\x01 / .gsave / .rsm へ無劣化変換。',
      'feat.f2.title': '全形式直展開＆複数ディスク統合',
      'feat.f2.desc': 'libarchive + ISO9660 内蔵。.zip/.rar/.7z/.iso/.mdf を直読し統合＆文字化け修復。',
      'feat.f3.title': '60fps 制御＆復帰再構築',
      'feat.f3.desc': '高優先度描画/音声、ADPF、16KB 整列、EGL テクスチャ＆3D 迷宮の自動再構築。',
      'feat.f4.title': '非破壊 MOD＆パッチ管理',
      'feat.f4.desc': 'オンライン/ローカル MOD・翻訳・修正解除に対応。上書き前自動退避で即復元。',
      'feat.f5.title': '多文字コード＆マルチ音声',
      'feat.f5.desc': 'SJIS/GBK/UTF-8・CHS.TBL/UIF/word.mai 対応。Opus 音声・MIDI・仮想 CD 音源内蔵。',
      'feat.f6.title': 'ストリーミング導入＆レジューム',
      'feat.f6.desc': '同意後に有効化。HTTP Range 再開・SHA-256 検証・バックグラウンド継続で省容量。',
      'feat.f7.title': '複数インスタンス＆同期',
      'feat.f7.desc': 'ランス10分離管理、AliceStart.CHS 切替、Android 共有領域＆iOS iCloud 同期対応。',
      'feat.f8.title': '改造・セーブ編集・診断ログ',
      'feat.f8.desc': 'オフライン編集・数値ロック・配信モード搭載。異常終了時は .txt 診断出力。',
      'feat.f9.title': '仮想パッド編集＆リアルタイム文字拡大',
      'feat.f9.desc': 'GAL/RPG プリセット＆ドラッグ編集、主要コントローラー対応。PartsEngine 文字 75%–160% 拡大。',
      'feat.f9.preview': 'ランス10・リアルタイム文字拡大',
      'catalog.kicker': '04 / 171+ CATALOG · 36 ON REEL',
      'catalog.title': '作品リール',
      'catalog.sub': '171+ 内蔵目録に加え任意の System 1–4 作品を導入可能。ホバーで停止、クリックで詳細。',
      'catalog.fAll': 'すべて 36',
      'catalog.fCn': '中国語対応',
      'catalog.inspHint': '上のジャケットをクリックすると対応エンジンと機能を表示します。',
      'catalog.notice': '純粋なオープンソースランチャーです。ゲーム本体は各自ご用意ください。',
      'download.kicker': '05 / DEPLOYMENT · GPL-2.0',
      'download.title': '最新版を入手',
      'download.sub': 'GitHub Actions で公開ビルドし、Releases にて配布中。',
      'download.androidReq': 'Android 8.0+ · arm64-v8a · 直接導入',
      'download.iosReq': 'iOS 15+ · arm64 · AltStore / SideStore 署名',
      'download.switchReq': 'アプリモード · 4 NRO を SD /switch/ へ配置',
      'download.copy': 'コピー',
      'download.allReleases': 'リリース',
      'download.ciBuilds': 'CI ビルド',
      'download.source': 'ソース',
      'footer.license': 'GPL-2.0 オープンソース · ゲームデータ非同梱',
      'footer.credits': 'CORE UPSTREAM CREDITS',
      'toast.qqCopied': 'QQ 番号をコピー：383917628',
      'toast.pokes': [
        'Alice // Wine 不要・100% Native SDL2',
        'Alice // 60fps VSync ロック',
        'Alice // X4SS ⇄ GD\\x01\\x01 準備完了',
      ],
    },
  };

  /* Concise Practical Handbook Content */
  const HANDBOOK = {
    controls: {
      zh: `<table class="ctrl-table"><thead><tr><th>操作</th><th>触控手势</th><th>手柄默认映射</th></tr></thead><tbody>
        <tr><td>左键 / 确认</td><td>单指轻触</td><td><code>A</code> / <code>R3</code>（左摇杆光标）</td></tr>
        <tr><td>右键 / 菜单</td><td>双指轻触 / 轻触黑边</td><td><code>B</code></td></tr>
        <tr><td>快进（Ctrl）</td><td>长按屏幕 / 点 <code>» Ctrl</code> 锁定</td><td><code>RT</code> 锁定切换</td></tr>
        <tr><td>滚轮 / 回看</td><td>双指上下滑动</td><td><code>LB</code> / <code>RB</code> 或右摇杆</td></tr>
        <tr><td>悬停 / 拖拽</td><td>单指按住滑动</td><td>左摇杆（可调速度）</td></tr>
        <tr><td>局内悬浮菜单</td><td>点左上角悬浮图标</td><td><code>Start</code> · <code>Back</code> · <code>Guide</code> · Switch <code>L+R</code></td></tr>
        <tr><td>退出确认</td><td>系统返回手势</td><td><code>LT</code>（<code>Esc</code>）</td></tr>
      </tbody></table>`,
      en: `<table class="ctrl-table"><thead><tr><th>Action</th><th>Touch Gesture</th><th>Default Controller</th></tr></thead><tbody>
        <tr><td>Left Click</td><td>Single tap</td><td><code>A</code> / <code>R3</code> (Left Stick cursor)</td></tr>
        <tr><td>Right Click / Menu</td><td>Two-finger tap / side bars</td><td><code>B</code></td></tr>
        <tr><td>Fast-Forward</td><td>Long press / tap <code>» Ctrl</code></td><td><code>RT</code> toggle</td></tr>
        <tr><td>Wheel / Backlog</td><td>Two-finger swipe</td><td><code>LB</code> / <code>RB</code> or Right Stick</td></tr>
        <tr><td>Hover / Drag</td><td>Press and drag</td><td>Left Stick (tunable)</td></tr>
        <tr><td>Overlay Menu</td><td>Top-left floating icon</td><td><code>Start</code> · <code>Back</code> · <code>Guide</code> · Switch <code>L+R</code></td></tr>
        <tr><td>Exit Prompt</td><td>System Back</td><td><code>LT</code> (<code>Esc</code>)</td></tr>
      </tbody></table>`,
      ja: `<table class="ctrl-table"><thead><tr><th>操作</th><th>タッチ</th><th>コントローラー初期設定</th></tr></thead><tbody>
        <tr><td>左クリック / 決定</td><td>1本指タップ</td><td><code>A</code> / <code>R3</code>（左スティック）</td></tr>
        <tr><td>右クリック / 戻る</td><td>2本指タップ / 黒帯タップ</td><td><code>B</code></td></tr>
        <tr><td>スキップ（Ctrl）</td><td>長押し / <code>» Ctrl</code> 固定</td><td><code>RT</code> トグル</td></tr>
        <tr><td>ホイール / ログ</td><td>2本指上下スワイプ</td><td><code>LB</code> / <code>RB</code>・右スティック</td></tr>
        <tr><td>ホバー / ドラッグ</td><td>1本指スライド</td><td>左スティック（感度調整可）</td></tr>
        <tr><td>オーバーレイ</td><td>左上アイコン</td><td><code>Start</code>・<code>Back</code>・<code>Guide</code>・Switch <code>L+R</code></td></tr>
        <tr><td>終了確認</td><td>戻る操作</td><td><code>LT</code>（<code>Esc</code>）</td></tr>
      </tbody></table>`,
    },
    workflow: {
      zh: `<div class="workflow-steps">
        <div class="wf-step"><span class="wf-num">01</span><strong>安装启动器</strong><p>Android 装 APK；iOS 自签 IPA；Switch 复制 4 个 NRO 至 <code>/switch/</code>。</p></div>
        <div class="wf-step"><span class="wf-num">02</span><strong>导入游戏</strong><p>支持文件夹、<code>.zip/.rar/.7z</code>、<code>.iso/.mdf</code> 多碟合并，或按需开启在线安装。</p></div>
        <div class="wf-step"><span class="wf-num">03</span><strong>补丁与入口</strong><p>管理无损 MOD / 汉化 / 去码补丁，按需切换 <code>AliceStart.CHS</code> 等入口。</p></div>
        <div class="wf-step"><span class="wf-num">04</span><strong>游玩与互通</strong><p>局内调字号/手柄/修改器；随时备份、同步 iCloud 或导出 PC 存档。</p></div>
      </div>`,
      en: `<div class="workflow-steps">
        <div class="wf-step"><span class="wf-num">01</span><strong>Install App</strong><p>Install APK on Android, sideload IPA on iOS, or copy 4 NROs to <code>/switch/</code>.</p></div>
        <div class="wf-step"><span class="wf-num">02</span><strong>Import Games</strong><p>Folders, <code>.zip/.rar/.7z</code>, <code>.iso/.mdf</code> multi-disc sets, or opt-in Online Install.</p></div>
        <div class="wf-step"><span class="wf-num">03</span><strong>MODs & Entry</strong><p>Install atomic-backup MODs/patches and switch System 4 <code>Launch entry</code> anytime.</p></div>
        <div class="wf-step"><span class="wf-num">04</span><strong>Play & Sync</strong><p>Use virtual pad, font scale & modifiers; sync via iCloud or export PC saves.</p></div>
      </div>`,
      ja: `<div class="workflow-steps">
        <div class="wf-step"><span class="wf-num">01</span><strong>アプリ導入</strong><p>Android は APK、iOS は IPA 署名、Switch は 4 NRO を <code>/switch/</code> へ配置。</p></div>
        <div class="wf-step"><span class="wf-num">02</span><strong>データ読込</strong><p>フォルダ・<code>.zip/.rar/.7z</code>・<code>.iso/.mdf</code> 統合対応。規約同意でオンライン導入可。</p></div>
        <div class="wf-step"><span class="wf-num">03</span><strong>MOD・起動設定</strong><p>非破壊 MOD/パッチ管理や <code>起動エントリ</code> 切替を作品ごとに設定。</p></div>
        <div class="wf-step"><span class="wf-num">04</span><strong>プレイ＆連携</strong><p>文字拡大・仮想パッド対応。iCloud 同期や PC 互換セーブ出力もワンタップ。</p></div>
      </div>`,
    },
    storage: {
      zh: `<div class="storage-grid">
        <div class="storage-col"><strong>ANDROID // 共享存储</strong><p>可切换至 <code>内部存储/AliceHub</code>。迁移仅移动 <code>saves</code> 与备份，旧游戏原地直跑，新游戏存入 <code>AliceHub/games</code>。</p></div>
        <div class="storage-col"><strong>iOS // 文件与 iCloud</strong><p>通过「文件」直拷至 <code>AliceHub → Games</code>，APFS 省空间解压；支持 iCloud 云盘同步与 PC 存档导出。</p></div>
        <div class="storage-col"><strong>SWITCH // SD 卡隔离</strong><p>按 <code>/switch/alicehub/&lt;engine&gt;/</code> 隔离游戏与存档，更新 NRO 零影响，一键导出 <code>PC_SaveData</code>。</p></div>
      </div>`,
      en: `<div class="storage-grid">
        <div class="storage-col"><strong>ANDROID // SHARED</strong><p>Switch to <code>Internal Storage/AliceHub</code>. Migration moves <code>saves</code> only; existing games stay in place.</p></div>
        <div class="storage-col"><strong>iOS // FILES & iCLOUD</strong><p>Drop folders into <code>AliceHub → Games</code> with APFS copy-on-write; sync saves via iCloud & export PC archives.</p></div>
        <div class="storage-col"><strong>SWITCH // SD LAYOUT</strong><p>Isolated under <code>/switch/alicehub/&lt;engine&gt;/</code>. Updating NROs keeps saves intact; one-tap <code>PC_SaveData</code> export.</p></div>
      </div>`,
      ja: `<div class="storage-grid">
        <div class="storage-col"><strong>ANDROID // 共有領域</strong><p><code>内部ストレージ/AliceHub</code> へ切替可。移行時は <code>saves</code> のみ安全移動し既存ゲームはそのまま起動。</p></div>
        <div class="storage-col"><strong>iOS // ファイル＆iCloud</strong><p><code>AliceHub → Games</code> へ直接配置、APFS で省容量展開。iCloud 同期＆PC 互換出力対応。</p></div>
        <div class="storage-col"><strong>SWITCH // SD 分離</strong><p><code>/switch/alicehub/&lt;engine&gt;/</code> で独立保存。NRO 更新で消失せず <code>PC_SaveData</code> 出力可。</p></div>
      </div>`,
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
          title: '早期 System 1–3 内核',
          desc: '面向 1989–1995 年 DAT 经典作品，提供原生触控、独立存档与轻量快进。',
          log: '[BOOT] Mounted ADISK.DAT + ACG.DAT (System 1-3)\n[GFX ] Indexed palette blitter -> SDL2 texture OK\n[CTRL] Direct native touch mode active\n[SAVE] Scoped directory: saves/<game>/ [READY]',
        },
        en: {
          title: 'Early System 1–3 Kernel',
          desc: 'Runs 1989–1995 DAT classics with native touch, isolated saves & fast skip.',
          log: '[BOOT] Mounted ADISK.DAT + ACG.DAT (System 1-3)\n[GFX ] Indexed palette blitter -> SDL2 texture OK\n[CTRL] Direct native touch mode active\n[SAVE] Scoped directory: saves/<game>/ [READY]',
        },
        ja: {
          title: '初期 System 1–3 コア',
          desc: '1989–1995 年の DAT 初期作をネイティブ実行。タッチ操作・作品別セーブ対応。',
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
          title: 'System 3.5–3.9 内核',
          desc: '完整支持 ALD/PMS/QNT 渲染、多编码文本、虚拟 CD 音轨与原生 MIDI 合成。',
          log: '[BOOT] Detected System39.ain + *SA.ALD archive set\n[AUD ] MIDI synth active + playlist.txt Ogg/MP3 CDDA\n[TEXT] Shift-JIS / GBK / UTF-8 glyph pipeline OK\n[TOOL] Overlay + gamepad bridge + live modifier',
        },
        en: {
          title: 'System 3.5–3.9 Kernel',
          desc: 'Full ALD/PMS/QNT rendering, multi-encoding CJK text, virtual CDDA & native MIDI.',
          log: '[BOOT] Detected System39.ain + *SA.ALD archive set\n[AUD ] MIDI synth active + playlist.txt Ogg/MP3 CDDA\n[TEXT] Shift-JIS / GBK / UTF-8 glyph pipeline OK\n[TOOL] Overlay + gamepad bridge + live modifier',
        },
        ja: {
          title: 'System 3.5–3.9 コア',
          desc: 'ALD/PMS/QNT 描画、多文字コード、仮想 CD 音源、ネイティブ MIDI 再生に対応。',
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
          title: 'System 4.x 全世代内核',
          desc: '覆盖 SACT2 至 AIN v14.1、PartsEngine、SealEngine 3D、FLAT v15 与 CHS.TBL 汉化映射。',
          log: '[BOOT] Loaded AIN v14.1 · O(deg) Delegate Reverse Index\n[GFX ] GLES 3.0 SealEngine UBO + FLAT v15 · 60.0 FPS\n[CJK ] GBK / SJIS / CHS.TBL + smart CP932 system popup\n[SAVE] X4SS ⇄ GD\\x01\\x01 PC export + <memory> snapshot OK',
        },
        en: {
          title: 'System 4.x Full-Gen Kernel',
          desc: 'Covers SACT2 through AIN v14.1, PartsEngine, SealEngine 3D, FLAT v15 & CHS.TBL remaps.',
          log: '[BOOT] Loaded AIN v14.1 · O(deg) Delegate Reverse Index\n[GFX ] GLES 3.0 SealEngine UBO + FLAT v15 · 60.0 FPS\n[CJK ] GBK / SJIS / CHS.TBL + smart CP932 system popup\n[SAVE] X4SS ⇄ GD\\x01\\x01 PC export + <memory> snapshot OK',
        },
        ja: {
          title: 'System 4.x 全世代コア',
          desc: 'SACT2 から AIN v14.1・PartsEngine・SealEngine 3D・FLAT v15・CHS.TBL まで対応。',
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
          headline: 'Android 原生调度与手柄编辑',
          summary: 'APK 直接安装，支持前台流式下载、共享存储迁移与 16KB 页对齐。',
          bullets: [
            '直读文件夹、.zip/.rar/.7z 与 .iso/.mdf 多碟自动合并',
            '全屏虚拟手柄编辑器 + Xbox / PS / Switch Pro 即插即用',
            '单游戏启动入口切换、无损 MOD 与 PC 兼容存档导出',
            '崩溃诊断卡片导出 .txt 日志，内置更新检查',
          ],
          cta: '下载 Android APK',
        },
        en: {
          headline: 'Android Native Scheduling & Pad Editor',
          summary: 'Direct APK install with foreground downloads, shared storage & 16 KB alignment.',
          bullets: [
            'Direct folders, .zip/.rar/.7z archives & .iso/.mdf multi-disc merge',
            'Fullscreen drag-and-drop pad editor + Xbox / PS / Switch Pro support',
            'Per-game launch entries, atomic MODs & PC save export',
            'Crash diagnostics with .txt log export & in-app update checks',
          ],
          cta: 'Download Android APK',
        },
        ja: {
          headline: 'Android ネイティブ制御＆パッド編集',
          summary: 'APK 直接導入。フォアグラウンド導入、共有ストレージ移行、16KB 整列対応。',
          bullets: [
            'フォルダ・.zip/.rar/.7z・.iso/.mdf 直読＆複数ディスク統合',
            '全画面仮想パッド編集＋Xbox / PS / Switch Pro 即時対応',
            '起動エントリ切替、非破壊 MOD 管理、PC 互換セーブ出力',
            'クラッシュ診断 .txt 出力・アプリ内アップデート確認',
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
          headline: 'SwiftUI 原生壳体与 iCloud 同步',
          summary: '自签未签名 IPA，支持「文件」App 拖入与 APFS 零冗余安装。',
          bullets: [
            '支持文件夹、多碟与 .zip/.rar/.7z/.iso/.mdf，直拷至 Games',
            '支持 iCloud 云盘任意目录同步与 PC 兼容存档导出',
            'iOS 26+ 后台持续任务、启动入口切换与原生 MIDI',
            '手柄自定义、无损 MOD / 去码补丁与更新检查',
          ],
          cta: '下载 iOS 未签名 IPA',
        },
        en: {
          headline: 'SwiftUI Shell & iCloud Save Sync',
          summary: 'Sideload unsigned IPA via AltStore/SideStore; APFS copy-on-write via Files.',
          bullets: [
            'Import folders, multi-disc & .zip/.rar/.7z/.iso/.mdf into Games',
            'Sync saves via iCloud Drive & export Windows PC archives',
            'iOS 26+ background tasks, launch entry switch & native MIDI',
            'Custom gamepad controls, atomic MODs & update checks',
          ],
          cta: 'Download iOS IPA',
        },
        ja: {
          headline: 'SwiftUI ネイティブ＆iCloud 同期',
          summary: '未署名 IPA を署名導入。「ファイル」直配置と APFS 省容量展開に対応。',
          bullets: [
            'フォルダ・複数ディスク・.zip/.rar/.7z/.iso/.mdf を Games へ導入',
            'iCloud Drive セーブ同期＆Windows PC 互換セーブ出力',
            'iOS 26+ バックグラウンド継続、起動エントリ切替、MIDI 再生',
            'パッド設定・非破壊 MOD／パッチ管理・更新確認',
          ],
          cta: 'iOS IPA を入手',
        },
      },
    },
    switch: {
      pill: 'NINTENDO SWITCH · 4× NRO',
      deckMode: 'JOY-CON · L+R OVERLAY',
      footer: ['L + R → MENU', 'X → SEARCH · Y → SETTINGS', '4× NRO → /switch/'],
      copy: {
        zh: {
          headline: '四 NRO 原生自制 · 掌机底座通用',
          summary: '将 4 个 NRO 放入 SD 卡 /switch/，应用模式直接启动。',
          bullets: [
            '三引擎统一大库：X 搜索、Y 设置、断点续传与入口切换',
            '直读 SD 卡文件夹/.zip/.iso/.mdf 及本地/在线 MOD',
            '存档备份恢复、目录同步、PC_SaveData 导出与兰斯 10 编辑',
            '局内 L+R（SL+SR / F1）呼出截图、快进、修改器与缩放',
          ],
          cta: '下载 Switch ZIP',
        },
        en: {
          headline: '4× Native NROs · Handheld & Docked',
          summary: 'Copy 4 NROs to SD /switch/ and launch in Homebrew Menu App Mode.',
          bullets: [
            'Unified 3-engine library: X search, Y settings, resume & boot entries',
            'Import SD folders/.zip/.iso/.mdf & local/online MOD overlays',
            'Save backup/restore, sync, PC_SaveData export & Rance X editor',
            'Press L+R (SL+SR / F1) for screenshots, skip, modifiers & scaling',
          ],
          cta: 'Download Switch ZIP',
        },
        ja: {
          headline: '4 NRO 自作アプリ・携帯／ドック対応',
          summary: '4つの NRO を SD /switch/ へ配置しアプリモードで起動。',
          bullets: [
            '3エンジン統合：X 検索、Y 設定、レジューム導入、起動エントリ切替',
            'SD のフォルダ/.zip/.iso/.mdf およびローカル／オンライン MOD 対応',
            'バックアップ復元、同期、PC_SaveData 出力、ランス10セーブ編集',
            'プレイ中 L+R（SL+SR / F1）でスクショ・スキップ・改造・文字拡大',
          ],
          cta: 'Switch ZIP を入手',
        },
      },
    },
  };

  let currentLang = 'zh';
  let currentEngine = 'sys4';
  let currentPlatform = 'android';
  let currentHandbook = 'controls';
  let currentFilter = 'all';
  let selectedSlug = 'rance10';
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

      drawWave('rgba(225, 75, 134, 0.22)', 26, 4.2, 0.62, 0, 1.4);
      drawWave('rgba(56, 189, 248, 0.18)', 22, 3.6, 0.65, 1.4, 1.2);
      drawWave('rgba(255, 255, 255, 0.06)', 16, 5.0, 0.58, 2.6, 0.9);

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
     3. FRAMELESS 3D ORBITAL KINETIC ALICE + LIVE EYES + SPOTLIGHT CARDS
     ======================================================================== */
  function initAliceAndSpotlights() {
    const shrine = document.getElementById('alice-shrine');
    const floatWrap = document.getElementById('shrine-card');
    const avatarBox = document.getElementById('alice-avatar-box');
    const eyeM = document.getElementById('alice-eye-m');
    const eyeC = document.getElementById('alice-eye-c');
    const backCanvas = document.getElementById('alice-orbit-back');
    const frontCanvas = document.getElementById('alice-orbit-front');

    if (avatarBox && !REDUCED_MOTION) {
      const scheduleBlink = () => {
        setTimeout(() => {
          if (!avatarBox.classList.contains('is-winking')) {
            avatarBox.classList.add('is-blinking');
            setTimeout(() => avatarBox.classList.remove('is-blinking'), 115);
          }
          scheduleBlink();
        }, 2600 + Math.random() * 3200);
      };
      scheduleBlink();
    }

    document.querySelectorAll('.spotlight-card').forEach((el) => {
      el.addEventListener('pointermove', (e) => {
        const r = el.getBoundingClientRect();
        el.style.setProperty('--sx', `${(e.clientX - r.left).toFixed(0)}px`);
        el.style.setProperty('--sy', `${(e.clientY - r.top).toFixed(0)}px`);
      }, { passive: true });
    });

    // Pointer state with spring-damped tracking & idle glance
    let targetDx = 0;
    let targetDy = 0;
    let curDx = 0;
    let curDy = 0;
    let lastMoveTs = 0;
    let overdrive = 1;
    let bounceScale = 1;
    let bounceVel = 0;
    let orbitVisible = true;

    const pulseWaves = [];
    const sparks = [];

    window.addEventListener('pointermove', (e) => {
      ptr.tx = e.clientX / Math.max(1, window.innerWidth);
      ptr.ty = e.clientY / Math.max(1, window.innerHeight);
      lastMoveTs = performance.now();

      if (!avatarBox) return;
      const rect = avatarBox.getBoundingClientRect();
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height * 0.7;
      targetDx = Math.max(-1, Math.min(1, (e.clientX - cx) / Math.max(180, window.innerWidth * 0.42)));
      targetDy = Math.max(-1, Math.min(1, (e.clientY - cy) / Math.max(180, window.innerHeight * 0.42)));
    }, { passive: true });

    if (shrine && avatarBox) {
      shrine.addEventListener('click', (e) => {
        const rect = shrine.getBoundingClientRect();
        spawnRipple(e.clientX || rect.left + rect.width / 2, e.clientY || rect.top + rect.height / 2);

        // Overdrive orbital rings + spring elastic bounce + wink
        overdrive = 4.6;
        bounceVel = -0.14;
        avatarBox.classList.add('is-winking');
        setTimeout(() => avatarBox.classList.remove('is-winking'), 320);

        // Spawn 2 concentric 3D pulse shockwaves + 20 radial sparks
        pulseWaves.push({ r: 0.24, alpha: 0.9, color: '225, 75, 134', tiltX: 1.06, tiltZ: -0.34 });
        pulseWaves.push({ r: 0.18, alpha: 0.85, color: '56, 189, 248', tiltX: 1.12, tiltZ: 0.52 });
        for (let i = 0; i < 20; i++) {
          const ang = (Math.PI * 2 * i) / 20 + (Math.random() - 0.5) * 0.22;
          const spd = 1.6 + Math.random() * 2.6;
          sparks.push({
            x: Math.cos(ang) * 0.12,
            y: Math.sin(ang) * 0.12,
            vx: Math.cos(ang) * spd * 0.0045,
            vy: Math.sin(ang) * spd * 0.0045,
            life: 1,
            size: 1.4 + Math.random() * 1.6,
            color: i % 2 === 0 ? '225, 75, 134' : '56, 189, 248',
          });
        }

        const pokes = I18N[currentLang]['toast.pokes'];
        showToast(pokes[pokeCount % pokes.length]);
        pokeCount++;
      });
    }

    if (!shrine || !backCanvas || !frontCanvas) return;
    const ctxB = backCanvas.getContext('2d');
    const ctxF = frontCanvas.getContext('2d');
    if (!ctxB || !ctxF) return;

    let size = 400;
    function resizeOrbits() {
      const rect = shrine.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      size = Math.max(220, Math.round(rect.width));
      for (const c of [backCanvas, frontCanvas]) {
        c.width = Math.round(size * dpr);
        c.height = Math.round(size * dpr);
      }
      ctxB.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctxF.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    resizeOrbits();
    window.addEventListener('resize', resizeOrbits, { passive: true });

    if ('IntersectionObserver' in window) {
      const io = new IntersectionObserver((entries) => {
        orbitVisible = entries[0]?.isIntersecting ?? true;
      }, { threshold: 0.02 });
      io.observe(shrine);
    }

    // 16 subtle depth-sorted orbital stardust motes
    const motes = Array.from({ length: 16 }, (_, i) => ({
      radius: 0.30 + (i % 5) * 0.036,
      angle: (Math.PI * 2 * i) / 16,
      speed: (i % 2 === 0 ? 1 : -1) * (0.28 + (i % 4) * 0.11),
      tiltX: 0.85 + (i % 3) * 0.28,
      tiltZ: -0.7 + (i % 5) * 0.35,
      size: 1.1 + (i % 3) * 0.55,
      color: i % 3 === 0 ? '225, 75, 134' : i % 3 === 1 ? '56, 189, 248' : '255, 255, 255',
    }));

    function project3D(x0, y0, tiltX, tiltZ, cx, cy, fov) {
      // Rotate around X (tilts circle into 3D ellipse), then Z (inclines ring)
      const y1 = y0 * Math.cos(tiltX);
      const z1 = y0 * Math.sin(tiltX);
      const xr = x0 * Math.cos(tiltZ) - y1 * Math.sin(tiltZ);
      const yr = x0 * Math.sin(tiltZ) + y1 * Math.cos(tiltZ);
      const p = fov / (fov - z1);
      return { x: cx + xr * p, y: cy + yr * p, z: z1, p };
    }

    function drawOrbitalRing(R, tiltX, tiltZ, opts, cx, cy, fov) {
      const segs = 96;
      const step = (Math.PI * 2) / segs;
      for (let i = 0; i < segs; i++) {
        if (opts.dashed && i % 3 === 0) continue;
        const a0 = i * step;
        const a1 = a0 + step;
        const p0 = project3D(Math.cos(a0) * R, Math.sin(a0) * R, tiltX, tiltZ, cx, cy, fov);
        const p1 = project3D(Math.cos(a1) * R, Math.sin(a1) * R, tiltX, tiltZ, cx, cy, fov);
        const midZ = (p0.z + p1.z) * 0.5;
        const ctx = midZ >= 0 ? ctxF : ctxB;
        const alpha = midZ >= 0 ? opts.frontAlpha : opts.backAlpha;
        ctx.strokeStyle = `rgba(${opts.rgb}, ${alpha})`;
        ctx.lineWidth = midZ >= 0 ? opts.lineW : opts.lineW * 0.78;
        ctx.beginPath();
        ctx.moveTo(p0.x, p0.y);
        ctx.lineTo(p1.x, p1.y);
        ctx.stroke();

        // Precision degree ticks on primary gyro ring
        if (opts.ticks && i % 4 === 0) {
          const pOut = project3D(Math.cos(a0) * (R + 4.5), Math.sin(a0) * (R + 4.5), tiltX, tiltZ, cx, cy, fov);
          ctx.strokeStyle = `rgba(255, 255, 255, ${midZ >= 0 ? 0.24 : 0.09})`;
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(p0.x, p0.y);
          ctx.lineTo(pOut.x, pOut.y);
          ctx.stroke();
        }
      }
    }

    function drawComet(R, headAng, tailSpan, dir, tiltX, tiltZ, rgb, cx, cy, fov) {
      const steps = 26;
      for (let i = 0; i < steps; i++) {
        const t0 = i / steps;
        const t1 = (i + 1) / steps;
        const a0 = headAng - dir * tailSpan * (1 - t0);
        const a1 = headAng - dir * tailSpan * (1 - t1);
        const p0 = project3D(Math.cos(a0) * R, Math.sin(a0) * R, tiltX, tiltZ, cx, cy, fov);
        const p1 = project3D(Math.cos(a1) * R, Math.sin(a1) * R, tiltX, tiltZ, cx, cy, fov);
        const midZ = (p0.z + p1.z) * 0.5;
        const ctx = midZ >= 0 ? ctxF : ctxB;
        const depthMul = midZ >= 0 ? 1 : 0.48;
        ctx.strokeStyle = `rgba(${rgb}, ${(Math.pow(t1, 1.8) * 0.82 * depthMul).toFixed(3)})`;
        ctx.lineWidth = (1.0 + t1 * 2.1) * p1.p;
        ctx.beginPath();
        ctx.moveTo(p0.x, p0.y);
        ctx.lineTo(p1.x, p1.y);
        ctx.stroke();
      }

      // Luminous orbiting satellite head
      const head = project3D(Math.cos(headAng) * R, Math.sin(headAng) * R, tiltX, tiltZ, cx, cy, fov);
      const ctx = head.z >= 0 ? ctxF : ctxB;
      const glowR = (head.z >= 0 ? 16 : 11) * head.p;
      const g = ctx.createRadialGradient(head.x, head.y, 0, head.x, head.y, glowR);
      g.addColorStop(0, `rgba(${rgb}, ${head.z >= 0 ? 0.95 : 0.55})`);
      g.addColorStop(0.35, `rgba(${rgb}, ${head.z >= 0 ? 0.38 : 0.18})`);
      g.addColorStop(1, 'transparent');
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(head.x, head.y, glowR, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.arc(head.x, head.y, (head.z >= 0 ? 3.2 : 2.2) * head.p, 0, Math.PI * 2);
      ctx.fill();
    }

    let phase = 0;
    let lastTs = performance.now();

    function renderKinetic(now) {
      const dt = Math.min(0.05, (now - lastTs) / 1000);
      lastTs = now;

      if (orbitVisible || REDUCED_MOTION) {
        overdrive += (1 - overdrive) * 0.045;
        phase += REDUCED_MOTION ? 0 : dt * overdrive;

        // Spring physics for click bounce
        const springForce = (1 - bounceScale) * 0.24;
        bounceVel = (bounceVel + springForce) * 0.78;
        bounceScale += bounceVel;

        // Autonomous gentle idle eye glance when pointer is still
        const idle = now - lastMoveTs > 3200;
        const aimX = idle ? Math.sin(now * 0.00085) * 0.42 : targetDx;
        const aimY = idle ? Math.cos(now * 0.00062) * 0.28 : targetDy;
        curDx += (aimX - curDx) * 0.08;
        curDy += (aimY - curDy) * 0.08;

        // Zero-gravity floating Alice + smooth 3D pointer tilt
        if (floatWrap && !REDUCED_MOTION) {
          const floatY = Math.sin(now * 0.0017) * 9.5;
          const swayZ = Math.sin(now * 0.0011) * 1.8;
          const rotX = -curDy * 11 + Math.cos(now * 0.0013) * 2.2;
          const rotY = curDx * 14 + Math.sin(now * 0.00095) * 5.5;
          floatWrap.style.transform =
            `translate3d(0, ${floatY.toFixed(2)}px, 0) rotateX(${rotX.toFixed(2)}deg) rotateY(${rotY.toFixed(2)}deg) rotateZ(${swayZ.toFixed(2)}deg) scale(${bounceScale.toFixed(4)})`;
        }

        if (eyeM && eyeC && !REDUCED_MOTION) {
          const ex = (curDx * 4.4).toFixed(2);
          const ey = (curDy * 3.1).toFixed(2);
          eyeM.style.transform = `translate3d(${ex}px, ${ey}px, 0)`;
          eyeC.style.transform = `translate3d(${ex}px, ${ey}px, 0)`;
        }

        ctxB.clearRect(0, 0, size, size);
        ctxF.clearRect(0, 0, size, size);

        const cx = size * 0.5;
        const cy = size * 0.5;
        const fov = size * 1.45;
        const ptrTiltX = curDy * 0.14;
        const ptrTiltZ = curDx * 0.12;

        // Ring 3: Subtle equatorial harmonic halo behind Alice
        const r3 = size * (0.31 + Math.sin(now * 0.0016) * 0.008);
        ctxB.strokeStyle = 'rgba(255, 255, 255, 0.075)';
        ctxB.lineWidth = 1;
        ctxB.beginPath();
        ctxB.arc(cx, cy, r3, 0, Math.PI * 2);
        ctxB.stroke();

        // Ring 1: Primary cyan-white calibrated 3D gyro orbit
        const t1X = 1.08 + ptrTiltX + Math.sin(now * 0.0007) * 0.05;
        const t1Z = -0.34 + ptrTiltZ;
        const r1 = size * 0.43;
        drawOrbitalRing(r1, t1X, t1Z, { rgb: '255, 255, 255', frontAlpha: 0.22, backAlpha: 0.09, lineW: 1.15, ticks: true }, cx, cy, fov);
        drawComet(r1, phase * 0.85 + 0.5, 1.05, 1, t1X, t1Z, '56, 189, 248', cx, cy, fov);

        // Ring 2: Counter-tilted rose elliptical orbit
        const t2X = 1.12 - ptrTiltX * 0.8 + Math.cos(now * 0.0006) * 0.05;
        const t2Z = 0.52 + ptrTiltZ * 0.9;
        const r2 = size * 0.39;
        drawOrbitalRing(r2, t2X, t2Z, { rgb: '225, 75, 134', frontAlpha: 0.28, backAlpha: 0.12, lineW: 1.1, dashed: true }, cx, cy, fov);
        drawComet(r2, -phase * 0.72 + 2.4, 0.95, -1, t2X, t2Z, '225, 75, 134', cx, cy, fov);

        // Depth-sorted stardust motes
        for (const m of motes) {
          const ang = m.angle + phase * m.speed;
          const R = size * m.radius;
          const pt = project3D(Math.cos(ang) * R, Math.sin(ang) * R, m.tiltX + ptrTiltX * 0.5, m.tiltZ + ptrTiltZ * 0.5, cx, cy, fov);
          const ctx = pt.z >= 0 ? ctxF : ctxB;
          ctx.fillStyle = `rgba(${m.color}, ${pt.z >= 0 ? 0.55 : 0.24})`;
          ctx.beginPath();
          ctx.arc(pt.x, pt.y, m.size * pt.p, 0, Math.PI * 2);
          ctx.fill();
        }

        // Click 3D shockwave rings
        for (let i = pulseWaves.length - 1; i >= 0; i--) {
          const pw = pulseWaves[i];
          pw.r += dt * 0.42;
          pw.alpha *= 0.93;
          if (pw.alpha < 0.02 || pw.r > 0.52) {
            pulseWaves.splice(i, 1);
            continue;
          }
          drawOrbitalRing(size * pw.r, pw.tiltX, pw.tiltZ, {
            rgb: pw.color,
            frontAlpha: pw.alpha,
            backAlpha: pw.alpha * 0.45,
            lineW: 1.6,
          }, cx, cy, fov);
        }

        // Click radial sparks
        for (let i = sparks.length - 1; i >= 0; i--) {
          const sp = sparks[i];
          sp.x += sp.vx;
          sp.y += sp.vy;
          sp.vx *= 0.95;
          sp.vy *= 0.95;
          sp.life -= dt * 1.45;
          if (sp.life <= 0) {
            sparks.splice(i, 1);
            continue;
          }
          ctxF.fillStyle = `rgba(${sp.color}, ${(sp.life * 0.9).toFixed(3)})`;
          ctxF.beginPath();
          ctxF.arc(cx + sp.x * size, cy + sp.y * size, sp.size * sp.life, 0, Math.PI * 2);
          ctxF.fill();
        }
      }

      if (!REDUCED_MOTION) requestAnimationFrame(renderKinetic);
    }

    renderKinetic(performance.now());
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
     5. SCROLL CHOREOGRAPHY
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
    }, { threshold: 0.28 });

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
        ghosts.forEach((g) => {
          const rect = g.parentElement.getBoundingClientRect();
          const factor = Number(g.dataset.parallax || 0.12);
          const shift = (rect.top - window.innerHeight * 0.45) * factor;
          g.style.transform = `translate3d(0, ${shift.toFixed(1)}px, 0)`;
        });

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
     6. ENGINE SWITCHER & HANDBOOK TABS
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

  function renderHandbook(tab) {
    currentHandbook = tab;
    document.querySelectorAll('.hb-tab').forEach((btn) => {
      const active = btn.dataset.hb === tab;
      btn.classList.toggle('is-active', active);
      btn.setAttribute('aria-selected', String(active));
    });
    const host = document.getElementById('handbook-content');
    if (host && HANDBOOK[tab]) {
      host.innerHTML = HANDBOOK[tab][currentLang] || HANDBOOK[tab].zh;
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
            ? (currentLang === 'en' ? '» Ctrl latched — fast-forwarding smoothly at 60fps.' : currentLang === 'ja' ? '» Ctrl 固定中 — 60fps で高速スキップ中。' : '» Ctrl 已锁定 — 60fps 持续快进对话，再次点击解除。')
            : (currentLang === 'en' ? '» Ctrl released — normal dialogue pacing.' : currentLang === 'ja' ? '» Ctrl 解除 — 通常速度に戻りました。' : '» Ctrl 已解除 — 恢复正常对话速度。');
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
     9. 3-ROW INTERACTIVE CATALOG FILMSTRIP + FILTER + QUICK INSPECTOR
     ======================================================================== */
  function updateInspector(slug) {
    const g = CATALOG_GAMES.find((x) => x.slug === slug) || CATALOG_GAMES[0];
    selectedSlug = g.slug;

    const cover = document.getElementById('insp-cover');
    const title = document.getElementById('insp-title');
    const eng = document.getElementById('insp-eng');
    const cn = document.getElementById('insp-cn');
    const note = document.getElementById('insp-note');
    const caps = document.getElementById('insp-caps');

    if (cover) cover.src = `assets/games/${g.slug}.webp`;
    if (title) title.textContent = g[currentLang] || g.zh;
    if (eng) eng.textContent = g.eng === 'SYS4' ? 'xsystem4' : 'xsystem35';
    if (cn) cn.style.display = g.cn ? 'inline-block' : 'none';
    if (note) {
      note.textContent = currentLang === 'en'
        ? `Auto-routed to ${g.eng === 'SYS4' ? 'xsystem4' : 'xsystem35'} native kernel · Android / iOS / Switch.`
        : currentLang === 'ja'
          ? `${g.eng === 'SYS4' ? 'xsystem4' : 'xsystem35'} へ自動ルーティング · Android / iOS / Switch 対応。`
          : `自动路由至 ${g.eng === 'SYS4' ? 'xsystem4' : 'xsystem35'} 原生运行 · 三端直跑。`;
    }
    if (caps) {
      caps.innerHTML = g.caps.map((c) => `<span>${escapeHtml(c)}</span>`).join('');
    }

    document.querySelectorAll('.game-tile').forEach((el) => {
      el.classList.toggle('is-selected', el.dataset.slug === g.slug);
    });
  }

  function applyCatalogFilter(filter) {
    currentFilter = filter;
    document.querySelectorAll('.filter-pill').forEach((btn) => {
      btn.classList.toggle('is-active', btn.dataset.filter === filter);
    });
    document.querySelectorAll('.game-tile').forEach((tile) => {
      const match = filter === 'all'
        || (filter === 'cn' && tile.dataset.cn === '1')
        || tile.dataset.eng === filter;
      tile.classList.toggle('is-dim', !match);
    });
  }

  function renderCatalogWall() {
    const stripA = document.getElementById('art-strip-a');
    const stripB = document.getElementById('art-strip-b');
    const stripC = document.getElementById('art-strip-c');
    if (!stripA || !stripB || !stripC) return;

    const makeTile = (g) => {
      const title = g[currentLang] || g.zh;
      return `<div class="game-tile" data-slug="${g.slug}" data-eng="${g.eng}" data-cn="${g.cn ? '1' : '0'}" data-cursor="${g.eng}" role="button" tabindex="0">
        <img src="assets/games/${g.slug}.webp" alt="${escapeHtml(title)}" width="142" height="142" />
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
    const row2 = CATALOG_GAMES.slice(12, 24).map(makeTile).join('');
    const row3 = CATALOG_GAMES.slice(24, 36).map(makeTile).join('');
    stripA.innerHTML = row1 + row1;
    stripB.innerHTML = row2 + row2;
    stripC.innerHTML = row3 + row3;

    applyCatalogFilter(currentFilter);
    updateInspector(selectedSlug);
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
    renderHandbook(currentHandbook);
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
    document.querySelectorAll('.hb-tab').forEach((btn) => {
      btn.addEventListener('click', () => renderHandbook(btn.dataset.hb));
    });
    document.querySelectorAll('.filter-pill').forEach((btn) => {
      btn.addEventListener('click', () => applyCatalogFilter(btn.dataset.filter));
    });
    document.querySelectorAll('.lang-btn').forEach((btn) => {
      btn.addEventListener('click', () => applyLanguage(btn.dataset.lang));
    });

    // Filmstrip click-to-inspect delegation
    const filmMask = document.getElementById('filmstrip-mask');
    if (filmMask) {
      filmMask.addEventListener('click', (e) => {
        const tile = e.target.closest('.game-tile');
        if (tile && tile.dataset.slug) updateInspector(tile.dataset.slug);
      });
      filmMask.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          const tile = e.target.closest('.game-tile');
          if (tile && tile.dataset.slug) {
            e.preventDefault();
            updateInspector(tile.dataset.slug);
          }
        }
      });
    }

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
