(() => {
  'use strict';

  const REDUCED_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

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

  /* 36 Curated AliceSoft Titles across 3 Opposing Filmstrip Rows (12 × 3) */
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
      'nav.reel': '作品滚轴',
      'nav.overview': '引擎与三端',
      'nav.features': '功能',
      'nav.download': '下载',
      'nav.cta': '下载',
      'hero.tag': 'NATIVE SDL2 · ZERO WINE',
      'hero.sub': '<strong>Android · iOS · Switch</strong> 原生运行 AliceSoft System 1–4 · 60fps · PC 存档互通',
      'hero.cta': '下载最新版',
      'reel.meta': '内置 171+ 目录（含 54+ 汉化）· 支持导入任意 System 1–4 作品 · 点击封面速览',
      'catalog.fAll': '全部 36',
      'catalog.fCn': '汉化',
      'catalog.inspHint': '自动路由至 xsystem4 原生运行 · 三端直跑',
      'overview.kicker': '01 / ARCHITECTURE',
      'overview.title': '三核原生 · 三端直跑',
      'overview.sub': '自动识别目录路由引擎，全平台统一构建。',
      'eng.s3': '早期 DAT 作品原生触控、独立存档与快进。',
      'eng.s35': '完整 ALD/PMS 渲染、多编码、虚拟 CD 音轨与原生 MIDI。',
      'eng.s4': '覆盖 SACT2 至 AIN v14.1、PartsEngine、SealEngine 3D 与 FLAT v15。',
      'hb.tabControls': '操控映射',
      'hb.tabWorkflow': '四步上手',
      'hb.tabStorage': '存储同步',
      'features.kicker': '02 / CAPABILITIES',
      'features.title': '核心功能',
      'features.sub': '从导入、补丁到存档互通与局内工具。',
      'feat.f1.title': 'PC ⇄ 移动端存档互通',
      'feat.f1.desc': '目录隔离与自动备份；按 .ain 结构无损转换 X4SS / .gsave / .rsm 与 <memory>。',
      'feat.f2.title': '全格式直读与合碟',
      'feat.f2.desc': '直读 .zip/.rar/.7z/.iso/.mdf，自动合并多碟、覆盖补丁与修复乱码。',
      'feat.f3.title': '60fps 稳帧与后台恢复',
      'feat.f3.desc': '高优渲染/混音线程、ADPF 提频、16KB 对齐与切后台自动重建纹理/迷宫。',
      'feat.f4.title': '无损 MOD 与补丁',
      'feat.f4.desc': '在线/本地 MOD、汉化与去码补丁；覆盖前自动原子备份，一键还原。',
      'feat.f5.title': '多编码与多轨音频',
      'feat.f5.desc': '兼容 SJIS/GBK/UTF-8 与 CHS.TBL/UIF/word.mai；内置 Opus、MIDI 与 CD 音轨。',
      'feat.f6.title': '流式在线安装',
      'feat.f6.desc': '默认关闭按需开启；HTTP Range 续传、SHA-256 校验与后台保活，免双倍空间。',
      'feat.f7.title': '多实例与目录同步',
      'feat.f7.desc': '《兰斯 10》多实例隔离、AliceStart.CHS 入口切换，支持共享目录与 iCloud。',
      'feat.f8.title': '修改器与崩溃诊断',
      'feat.f8.desc': '离线存档编辑、局内数值锁定与主播模式；崩溃导出 .txt 诊断日志与三语主题。',
      'feat.f9.title': '虚拟手柄与实时字号',
      'feat.f9.desc': '全屏拖拽虚拟手柄与主流外设；PartsEngine 字体 75%–160% 无溢出实时缩放。',
      'feat.f9.preview': '兰斯 10 · 实时字号',
      'download.kicker': '03 / RELEASE · GPL-2.0',
      'download.title': '获取最新版',
      'download.sub': 'GitHub Actions 公开构建 · 不含任何游戏本体。',
      'download.androidReq': 'Android 8.0+ · 直接安装',
      'download.iosReq': 'iOS 15+ · 自签侧载',
      'download.switchReq': '4× NRO → SD /switch/',
      'download.qq': 'QQ 群',
      'download.allReleases': '历史版本',
      'download.ciBuilds': '持续构建',
      'download.source': '开源协议',
      'footer.license': 'GPL-2.0 · 纯开源启动器，不含游戏本体',
      'toast.qqCopied': '已复制 QQ 群号：383917628',
      'toast.pokes': [
        'Alice // 0% Wine · 100% Native SDL2',
        'Alice // 60fps VSync 已锁定',
        'Alice // X4SS ⇄ GD\\x01\\x01 跨端存档就绪',
      ],
    },
    en: {
      'nav.reel': 'Showcase',
      'nav.overview': 'Engines',
      'nav.features': 'Features',
      'nav.download': 'Download',
      'nav.cta': 'Get App',
      'hero.tag': 'NATIVE SDL2 · ZERO WINE',
      'hero.sub': '<strong>Android · iOS · Switch</strong> Native AliceSoft System 1–4 · 60fps · PC Save Interop',
      'hero.cta': 'Download',
      'reel.meta': '171+ catalog (54+ CN) · Unrestricted System 1–4 import · Click any cover to inspect',
      'catalog.fAll': 'All 36',
      'catalog.fCn': 'CN',
      'catalog.inspHint': 'Auto-routed to xsystem4 native kernel · 3 platforms',
      'overview.kicker': '01 / ARCHITECTURE',
      'overview.title': 'Three Kernels · Three Platforms',
      'overview.sub': 'Auto-detects root markers; unified builds across Android, iOS & Switch.',
      'eng.s3': 'Early DAT classics with direct touch, scoped saves & fast skip.',
      'eng.s35': 'ALD/PMS graphics, multi-encoding CJK, virtual CDDA & native MIDI.',
      'eng.s4': 'SACT2 through AIN v14.1, PartsEngine, SealEngine 3D & FLAT v15.',
      'hb.tabControls': 'Controls',
      'hb.tabWorkflow': 'Quick Start',
      'hb.tabStorage': 'Storage',
      'features.kicker': '02 / CAPABILITIES',
      'features.title': 'Core Capabilities',
      'features.sub': 'Import, patches, save interop & handheld tools.',
      'feat.f1.title': 'PC ⇄ Mobile Save Interop',
      'feat.f1.desc': 'Per-game isolation & backups; .ain-guided X4SS / .gsave / .rsm & <memory> export.',
      'feat.f2.title': 'All-Format Import',
      'feat.f2.desc': 'Direct .zip/.rar/.7z/.iso/.mdf extraction, multi-disc merge & CJK filename fix.',
      'feat.f3.title': '60fps & Context Recovery',
      'feat.f3.desc': 'Priority render/audio threads, ADPF boost, 16 KB alignment & EGL rebuild.',
      'feat.f4.title': 'Atomic MODs & Patches',
      'feat.f4.desc': 'Online/local MODs, translations & uncensored patches with one-tap restore.',
      'feat.f5.title': 'CJK Remaps & Audio',
      'feat.f5.desc': 'SJIS/GBK/UTF-8 & CHS.TBL/UIF/word.mai; built-in Opus, MIDI & CDDA.',
      'feat.f6.title': 'Streaming Install',
      'feat.f6.desc': 'Opt-in HTTP Range resume, SHA-256 checks & background tasks without 2× staging.',
      'feat.f7.title': 'Multi-Instance & Sync',
      'feat.f7.desc': 'Rance X multi-folder isolation, AliceStart.CHS entries, shared storage & iCloud.',
      'feat.f8.title': 'Modifiers & Crash Logs',
      'feat.f8.desc': 'Offline save editor, live locks & streamer mode; .txt crash logs & themes.',
      'feat.f9.title': 'Virtual Pad & Font Scale',
      'feat.f9.desc': 'Fullscreen drag pad editor & controllers; 75%–160% PartsEngine live scaling.',
      'feat.f9.preview': 'Rance X · Live Scale',
      'download.kicker': '03 / RELEASE · GPL-2.0',
      'download.title': 'Download Latest',
      'download.sub': 'Built via GitHub Actions · No games bundled.',
      'download.androidReq': 'Android 8.0+ · Direct install',
      'download.iosReq': 'iOS 15+ · Sideload IPA',
      'download.switchReq': '4× NRO → SD /switch/',
      'download.qq': 'QQ Group',
      'download.allReleases': 'History',
      'download.ciBuilds': 'Nightly',
      'download.source': 'License',
      'footer.license': 'GPL-2.0 · Open-source launcher, no games included',
      'toast.qqCopied': 'Copied QQ Group: 383917628',
      'toast.pokes': [
        'Alice // 0% Wine · 100% Native SDL2',
        'Alice // 60fps VSync Locked',
        'Alice // X4SS ⇄ GD\\x01\\x01 Ready',
      ],
    },
    ja: {
      'nav.reel': '作品リール',
      'nav.overview': 'エンジン',
      'nav.features': '機能',
      'nav.download': '入手',
      'nav.cta': '入手',
      'hero.tag': 'NATIVE SDL2 · ZERO WINE',
      'hero.sub': '<strong>Android・iOS・Switch</strong> で System 1–4 をネイティブ実行 · 60fps · PC セーブ互換',
      'hero.cta': '最新版を入手',
      'reel.meta': '171+ 内蔵目録（54+ 翻訳）· 任意の System 1–4 導入可 · クリックで詳細',
      'catalog.fAll': 'すべて 36',
      'catalog.fCn': '翻訳',
      'catalog.inspHint': 'xsystem4 へ自動ルーティング · 3機種対応',
      'overview.kicker': '01 / ARCHITECTURE',
      'overview.title': '3系統コア · 3機種統一',
      'overview.sub': 'フォルダ構成を自動判別し、全機種へ同時配布。',
      'eng.s3': '初期 DAT 作品をネイティブタッチ・分離セーブで実行。',
      'eng.s35': 'ALD/PMS 描画、多文字コード、仮想 CD 音源、MIDI 再生。',
      'eng.s4': 'SACT2 から AIN v14.1・PartsEngine・SealEngine 3D・FLAT v15 対応。',
      'hb.tabControls': '操作対応',
      'hb.tabWorkflow': '導入4手順',
      'hb.tabStorage': '保存と同期',
      'features.kicker': '02 / CAPABILITIES',
      'features.title': '主要機能',
      'features.sub': '導入・MOD・PC セーブ互換・携帯機向けツールを統合。',
      'feat.f1.title': 'PC ⇄ モバイルセーブ互換',
      'feat.f1.desc': '作品別分離＆自動退避。.ain 参照で X4SS / .gsave / .rsm を無劣化変換。',
      'feat.f2.title': '全形式直展開＆合ディスク',
      'feat.f2.desc': '.zip/.rar/.7z/.iso/.mdf を直読し複数ディスク統合＆文字化け修復。',
      'feat.f3.title': '60fps 制御＆復帰再構築',
      'feat.f3.desc': '高優先度描画/音声、ADPF、16KB 整列、EGL テクスチャ/迷宮自動再構築。',
      'feat.f4.title': '非破壊 MOD＆パッチ',
      'feat.f4.desc': 'オンライン/ローカル MOD・翻訳・修正解除に対応。自動退避で即復元。',
      'feat.f5.title': '多文字コード＆音声',
      'feat.f5.desc': 'SJIS/GBK/UTF-8・CHS.TBL/UIF/word.mai。Opus・MIDI・CD 音源内蔵。',
      'feat.f6.title': 'ストリーミング導入',
      'feat.f6.desc': '規約同意後に有効化。HTTP Range 再開・SHA-256 検証・省容量処理。',
      'feat.f7.title': '複数インスタンス＆同期',
      'feat.f7.desc': 'ランス10分離管理、AliceStart.CHS 切替、共有フォルダ＆iCloud 同期。',
      'feat.f8.title': '改造・編集・診断ログ',
      'feat.f8.desc': 'オフライン編集・数値ロック・配信モード。異常終了時は .txt 診断出力。',
      'feat.f9.title': '仮想パッド＆文字拡大',
      'feat.f9.desc': '全画面パッド編集＆コントローラー対応。PartsEngine 文字 75%–160% 拡大。',
      'feat.f9.preview': 'ランス10 · 文字サイズ',
      'download.kicker': '03 / RELEASE · GPL-2.0',
      'download.title': '最新版を入手',
      'download.sub': 'GitHub Actions 公開ビルド · ゲーム本体は非同梱。',
      'download.androidReq': 'Android 8.0+ · 直接導入',
      'download.iosReq': 'iOS 15+ · 署名して導入',
      'download.switchReq': '4× NRO → SD /switch/',
      'download.qq': 'QQ 群',
      'download.allReleases': '履歴',
      'download.ciBuilds': '最新 CI',
      'download.source': 'ライセンス',
      'footer.license': 'GPL-2.0 · オープンソースランチャー（本体非同梱）',
      'toast.qqCopied': 'QQ 番号をコピー：383917628',
      'toast.pokes': [
        'Alice // Wine 不要・100% Native SDL2',
        'Alice // 60fps VSync ロック',
        'Alice // X4SS ⇄ GD\\x01\\x01 準備完了',
      ],
    },
  };

  const PLATFORMS = {
    android: {
      zh: {
        headline: 'Android 原生调度与手柄编辑',
        bullets: [
          '直读文件夹、.zip/.rar/.7z 与 .iso/.mdf 多碟合并',
          '全屏虚拟手柄编辑器 + Xbox / PS / Switch Pro 即插即用',
          '共享目录迁移、单游戏启动入口与 PC 兼容存档导出',
          '前台流式下载、ADPF 提频、16KB 页对齐与 .txt 诊断',
        ],
        cta: '下载 Android APK',
      },
      en: {
        headline: 'Android Native Scheduling & Pad Editor',
        bullets: [
          'Direct folders, .zip/.rar/.7z & .iso/.mdf multi-disc merge',
          'Fullscreen virtual pad editor + Xbox / PS / Switch Pro',
          'Shared storage migration, boot entries & PC save export',
          'Foreground installs, ADPF boost, 16 KB & .txt diagnostics',
        ],
        cta: 'Download Android APK',
      },
      ja: {
        headline: 'Android ネイティブ制御＆パッド編集',
        bullets: [
          'フォルダ・.zip/.rar/.7z・.iso/.mdf 直読＆ディスク統合',
          '全画面仮想パッド編集＋Xbox / PS / Switch Pro 対応',
          '共有領域移行、起動エントリ切替、PC 互換セーブ出力',
          'フォアグラウンド導入、ADPF、16KB 整列、.txt 診断',
        ],
        cta: 'Android APK を入手',
      },
    },
    ios: {
      zh: {
        headline: 'SwiftUI 原生壳体与 iCloud 同步',
        bullets: [
          'AltStore / SideStore 自签未签名 IPA，APFS 零冗余解压',
          '支持「文件」直拷至 AliceHub → Games 及多格式导入',
          'iCloud 云盘任意目录同步存档与 Windows PC 格式导出',
          'iOS 26+ 后台任务、启动入口切换、原生 MIDI 与 MOD 管理',
        ],
        cta: '下载 iOS 未签名 IPA',
      },
      en: {
        headline: 'SwiftUI Shell & iCloud Sync',
        bullets: [
          'Sign IPA via AltStore / SideStore with APFS copy-on-write',
          'Drop into AliceHub → Games via Files or import archives/ISOs',
          'Sync saves over iCloud Drive & export PC-compatible saves',
          'iOS 26+ background tasks, launch entries, MIDI & MODs',
        ],
        cta: 'Download iOS IPA',
      },
      ja: {
        headline: 'SwiftUI ネイティブ＆iCloud 同期',
        bullets: [
          'AltStore 等で IPA 署名導入、APFS 省容量展開に対応',
          '「ファイル」で AliceHub → Games へ直接配置＆直展開',
          'iCloud Drive セーブ同期＆Windows PC 互換セーブ出力',
          'iOS 26+ バックグラウンド継続、起動切替、MIDI、MOD',
        ],
        cta: 'iOS IPA を入手',
      },
    },
    switch: {
      zh: {
        headline: '四 NRO 原生自制 · 掌机底座通用',
        bullets: [
          '4 个 NRO 放入 SD 卡 /switch/，Homebrew 应用模式启动',
          '三引擎统一大库：X 搜索、Y 设置、断点续传与入口切换',
          '直读 SD 卡文件夹/.zip/.iso/.mdf，支持本地/在线 MOD',
          '局内 L+R 呼出截图、快进、修改器、缩放与 PC_SaveData 导出',
        ],
        cta: '下载 Switch ZIP',
      },
      en: {
        headline: '4× Native NROs · Handheld & Docked',
        bullets: [
          'Copy 4 NROs to SD /switch/, launch in Homebrew App Mode',
          'Unified library: X search, Y settings, resume & boot entries',
          'SD folders/.zip/.iso/.mdf plus local & online MOD overlays',
          'L+R overlay for screenshots, skip, modifiers & PC_SaveData',
        ],
        cta: 'Download Switch ZIP',
      },
      ja: {
        headline: '4 NRO 自作アプリ・携帯／ドック対応',
        bullets: [
          '4 NRO を SD /switch/ へ配置しアプリモードで起動',
          '3エンジン統合：X 検索、Y 設定、レジューム、起動切替',
          'SD のフォルダ/.zip/.iso/.mdf＆ローカル／オンライン MOD',
          'L+R でスクショ・スキップ・改造・拡大・PC_SaveData 出力',
        ],
        cta: 'Switch ZIP を入手',
      },
    },
  };

  const HANDBOOK = {
    controls: {
      zh: `<table class="ctrl-table"><thead><tr><th>操作</th><th>触控</th><th>手柄默认</th></tr></thead><tbody>
        <tr><td>左键 / 确认</td><td>单指轻触</td><td><code>A</code> / <code>R3</code>（左摇杆光标）</td></tr>
        <tr><td>右键 / 菜单</td><td>双指轻触 / 轻触黑边</td><td><code>B</code></td></tr>
        <tr><td>快进 Ctrl</td><td>长按 / <code>» Ctrl</code> 锁定</td><td><code>RT</code> 切换</td></tr>
        <tr><td>滚轮回看</td><td>双指上下滑动</td><td><code>LB</code> / <code>RB</code> / 右摇杆</td></tr>
        <tr><td>悬浮菜单</td><td>左上角悬浮图标</td><td><code>Start</code> · Switch <code>L+R</code></td></tr>
      </tbody></table>`,
      en: `<table class="ctrl-table"><thead><tr><th>Action</th><th>Touch</th><th>Controller</th></tr></thead><tbody>
        <tr><td>Left Click</td><td>Single tap</td><td><code>A</code> / <code>R3</code> (L-Stick)</td></tr>
        <tr><td>Right Click</td><td>2-finger / side bars</td><td><code>B</code></td></tr>
        <tr><td>Fast-Forward</td><td>Long press / <code>» Ctrl</code></td><td><code>RT</code> toggle</td></tr>
        <tr><td>Wheel / Log</td><td>2-finger swipe</td><td><code>LB</code> / <code>RB</code> / R-Stick</td></tr>
        <tr><td>Overlay</td><td>Top-left icon</td><td><code>Start</code> · Switch <code>L+R</code></td></tr>
      </tbody></table>`,
      ja: `<table class="ctrl-table"><thead><tr><th>操作</th><th>タッチ</th><th>コントローラー</th></tr></thead><tbody>
        <tr><td>決定 / 左</td><td>1本指タップ</td><td><code>A</code> / <code>R3</code>（左スティック）</td></tr>
        <tr><td>戻る / 右</td><td>2本指 / 黒帯</td><td><code>B</code></td></tr>
        <tr><td>スキップ</td><td>長押し / <code>» Ctrl</code></td><td><code>RT</code> 固定切替</td></tr>
        <tr><td>ログ / 輪</td><td>2本指スワイプ</td><td><code>LB</code> / <code>RB</code> / 右スティック</td></tr>
        <tr><td>メニュー</td><td>左上アイコン</td><td><code>Start</code>・Switch <code>L+R</code></td></tr>
      </tbody></table>`,
    },
    workflow: {
      zh: `<div class="wf-list">
        <div class="wf-item"><span>01</span><div><strong>安装</strong>Android 装 APK；iOS 自签 IPA；Switch 拷 4 NRO 至 <code>/switch/</code>。</div></div>
        <div class="wf-item"><span>02</span><div><strong>导入</strong>直读文件夹、<code>.zip/.rar/.7z</code>、<code>.iso/.mdf</code>，或按需开启在线安装。</div></div>
        <div class="wf-item"><span>03</span><div><strong>配置</strong>按需装无损 MOD / 汉化 / 去码，或切换 <code>AliceStart.CHS</code> 入口。</div></div>
        <div class="wf-item"><span>04</span><div><strong>互通</strong>局内调字号/手柄/修改器；一键同步 iCloud 或导出 PC 存档。</div></div>
      </div>`,
      en: `<div class="wf-list">
        <div class="wf-item"><span>01</span><div><strong>Install</strong>APK on Android, signed IPA on iOS, or 4 NROs in <code>/switch/</code>.</div></div>
        <div class="wf-item"><span>02</span><div><strong>Import</strong>Folders, <code>.zip/.rar/.7z</code>, <code>.iso/.mdf</code>, or opt-in online install.</div></div>
        <div class="wf-item"><span>03</span><div><strong>Customize</strong>Atomic MODs/patches & System 4 <code>Launch entry</code> per game.</div></div>
        <div class="wf-item"><span>04</span><div><strong>Sync</strong>In-game scaling/pad/modifiers; iCloud sync or PC save export.</div></div>
      </div>`,
      ja: `<div class="wf-list">
        <div class="wf-item"><span>01</span><div><strong>導入</strong>Android は APK、iOS は IPA 署名、Switch は <code>/switch/</code> へ配置。</div></div>
        <div class="wf-item"><span>02</span><div><strong>読込</strong>フォルダ・<code>.zip/.rar/.7z</code>・<code>.iso/.mdf</code> 統合、またはオンライン導入。</div></div>
        <div class="wf-item"><span>03</span><div><strong>調整</strong>非破壊 MOD/翻訳や <code>起動エントリ</code> を作品ごとに設定。</div></div>
        <div class="wf-item"><span>04</span><div><strong>連携</strong>文字拡大・仮想パッド・iCloud 同期・PC 互換セーブ出力。</div></div>
      </div>`,
    },
    storage: {
      zh: `<div class="store-list">
        <div class="store-item"><span>ANDROID</span><div>可切换共享目录 <code>内部存储/AliceHub</code>，仅迁移 <code>saves</code>，旧游戏原地直跑。</div></div>
        <div class="store-item"><span>iOS</span><div>「文件」直拷至 <code>AliceHub → Games</code>，APFS 省空间，支持 iCloud 双向同步。</div></div>
        <div class="store-item"><span>SWITCH</span><div>隔离于 <code>/switch/alicehub/&lt;engine&gt;/</code>，更新 NRO 零影响，一键导出 <code>PC_SaveData</code>。</div></div>
      </div>`,
      en: `<div class="store-list">
        <div class="store-item"><span>ANDROID</span><div>Switch to <code>Internal Storage/AliceHub</code>; migrates <code>saves</code> only while games stay in place.</div></div>
        <div class="store-item"><span>iOS</span><div>Drop into <code>AliceHub → Games</code> via Files (APFS); sync saves over iCloud Drive.</div></div>
        <div class="store-item"><span>SWITCH</span><div>Isolated in <code>/switch/alicehub/&lt;engine&gt;/</code>; NRO updates never touch saves.</div></div>
      </div>`,
      ja: `<div class="store-list">
        <div class="store-item"><span>ANDROID</span><div><code>内部ストレージ/AliceHub</code> 切替可。<code>saves</code> のみ安全移行し既存作はそのまま起動。</div></div>
        <div class="store-item"><span>iOS</span><div><code>AliceHub → Games</code> 直接配置（APFS）。iCloud Drive セーブ同期対応。</div></div>
        <div class="store-item"><span>SWITCH</span><div><code>/switch/alicehub/&lt;engine&gt;/</code> で分離管理。NRO 更新で消失せず <code>PC_SaveData</code> 出力可。</div></div>
      </div>`,
    },
  };

  let currentLang = 'zh';
  let currentPlatform = 'android';
  let currentHandbook = 'controls';
  let currentFilter = 'all';
  let selectedSlug = 'rance10';
  let pokeCount = 0;

  /* ========================================================================
     1. FRAMELESS MINI 3D ORBITAL KINETIC ALICE + EYE TRACKING + SPOTLIGHTS
     ======================================================================== */
  function initKineticAlice() {
    const shrine = document.getElementById('alice-shrine');
    const floatWrap = document.getElementById('shrine-card');
    const avatarBox = document.getElementById('alice-avatar-box');
    const eyeM = document.getElementById('alice-eye-m');
    const eyeC = document.getElementById('alice-eye-c');
    const backCanvas = document.getElementById('alice-orbit-back');
    const frontCanvas = document.getElementById('alice-orbit-front');

    document.querySelectorAll('.spotlight-card').forEach((el) => {
      el.addEventListener('pointermove', (e) => {
        const r = el.getBoundingClientRect();
        el.style.setProperty('--sx', `${(e.clientX - r.left).toFixed(0)}px`);
        el.style.setProperty('--sy', `${(e.clientY - r.top).toFixed(0)}px`);
      }, { passive: true });
    });

    if (avatarBox && !REDUCED_MOTION) {
      const scheduleBlink = () => {
        setTimeout(() => {
          if (!avatarBox.classList.contains('is-winking')) {
            avatarBox.classList.add('is-blinking');
            setTimeout(() => avatarBox.classList.remove('is-blinking'), 110);
          }
          scheduleBlink();
        }, 2600 + Math.random() * 3400);
      };
      scheduleBlink();
    }

    if (!shrine || !backCanvas || !frontCanvas) return;
    const ctxB = backCanvas.getContext('2d');
    const ctxF = frontCanvas.getContext('2d');
    if (!ctxB || !ctxF) return;

    let size = 96;
    function resize() {
      const rect = shrine.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      size = Math.max(64, Math.round(rect.width));
      for (const c of [backCanvas, frontCanvas]) {
        c.width = Math.round(size * dpr);
        c.height = Math.round(size * dpr);
      }
      ctxB.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctxF.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    resize();
    window.addEventListener('resize', resize, { passive: true });

    let targetDx = 0, targetDy = 0, curDx = 0, curDy = 0, lastMoveTs = 0;
    let overdrive = 1, bounceScale = 1, bounceVel = 0, visible = true;
    const pulseWaves = [];

    if ('IntersectionObserver' in window) {
      new IntersectionObserver((entries) => {
        visible = entries[0]?.isIntersecting ?? true;
      }, { threshold: 0.02 }).observe(shrine);
    }

    window.addEventListener('pointermove', (e) => {
      lastMoveTs = performance.now();
      if (!avatarBox) return;
      const r = avatarBox.getBoundingClientRect();
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height * 0.7;
      targetDx = Math.max(-1, Math.min(1, (e.clientX - cx) / Math.max(160, window.innerWidth * 0.4)));
      targetDy = Math.max(-1, Math.min(1, (e.clientY - cy) / Math.max(160, window.innerHeight * 0.4)));
    }, { passive: true });

    shrine.addEventListener('click', () => {
      overdrive = 4.8;
      bounceVel = -0.16;
      if (avatarBox) {
        avatarBox.classList.add('is-winking');
        setTimeout(() => avatarBox.classList.remove('is-winking'), 300);
      }
      pulseWaves.push({ r: 0.25, alpha: 0.9, rgb: '225, 75, 134', tx: 1.08, tz: -0.36 });
      pulseWaves.push({ r: 0.2, alpha: 0.85, rgb: '56, 189, 248', tx: 1.12, tz: 0.5 });
      const pokes = I18N[currentLang]['toast.pokes'];
      showToast(pokes[pokeCount % pokes.length]);
      pokeCount++;
    });

    function proj(x0, y0, tx, tz, cx, cy, fov) {
      const y1 = y0 * Math.cos(tx);
      const z1 = y0 * Math.sin(tx);
      const xr = x0 * Math.cos(tz) - y1 * Math.sin(tz);
      const yr = x0 * Math.sin(tz) + y1 * Math.cos(tz);
      const p = fov / (fov - z1);
      return { x: cx + xr * p, y: cy + yr * p, z: z1, p };
    }

    function drawRing(R, tx, tz, rgb, frontA, backA, lw, cx, cy, fov) {
      const segs = 64;
      const step = (Math.PI * 2) / segs;
      for (let i = 0; i < segs; i++) {
        const a0 = i * step;
        const p0 = proj(Math.cos(a0) * R, Math.sin(a0) * R, tx, tz, cx, cy, fov);
        const p1 = proj(Math.cos(a0 + step) * R, Math.sin(a0 + step) * R, tx, tz, cx, cy, fov);
        const midZ = (p0.z + p1.z) * 0.5;
        const ctx = midZ >= 0 ? ctxF : ctxB;
        ctx.strokeStyle = `rgba(${rgb}, ${midZ >= 0 ? frontA : backA})`;
        ctx.lineWidth = lw;
        ctx.beginPath();
        ctx.moveTo(p0.x, p0.y);
        ctx.lineTo(p1.x, p1.y);
        ctx.stroke();
      }
    }

    function drawSatellite(R, ang, dir, tx, tz, rgb, cx, cy, fov) {
      const steps = 16;
      for (let i = 0; i < steps; i++) {
        const t0 = i / steps;
        const t1 = (i + 1) / steps;
        const a0 = ang - dir * 1.1 * (1 - t0);
        const a1 = ang - dir * 1.1 * (1 - t1);
        const p0 = proj(Math.cos(a0) * R, Math.sin(a0) * R, tx, tz, cx, cy, fov);
        const p1 = proj(Math.cos(a1) * R, Math.sin(a1) * R, tx, tz, cx, cy, fov);
        const midZ = (p0.z + p1.z) * 0.5;
        const ctx = midZ >= 0 ? ctxF : ctxB;
        ctx.strokeStyle = `rgba(${rgb}, ${(t1 * t1 * (midZ >= 0 ? 0.85 : 0.4)).toFixed(3)})`;
        ctx.lineWidth = 1.1 + t1 * 1.3;
        ctx.beginPath();
        ctx.moveTo(p0.x, p0.y);
        ctx.lineTo(p1.x, p1.y);
        ctx.stroke();
      }
      const hd = proj(Math.cos(ang) * R, Math.sin(ang) * R, tx, tz, cx, cy, fov);
      const ctx = hd.z >= 0 ? ctxF : ctxB;
      const g = ctx.createRadialGradient(hd.x, hd.y, 0, hd.x, hd.y, 7);
      g.addColorStop(0, `rgba(${rgb}, 0.95)`);
      g.addColorStop(1, 'transparent');
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(hd.x, hd.y, 7, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#fff';
      ctx.beginPath();
      ctx.arc(hd.x, hd.y, 1.9 * hd.p, 0, Math.PI * 2);
      ctx.fill();
    }

    let phase = 0;
    let lastTs = performance.now();

    function frame(now) {
      const dt = Math.min(0.05, (now - lastTs) / 1000);
      lastTs = now;

      if (visible || REDUCED_MOTION) {
        overdrive += (1 - overdrive) * 0.05;
        phase += REDUCED_MOTION ? 0 : dt * overdrive;

        bounceVel = (bounceVel + (1 - bounceScale) * 0.25) * 0.76;
        bounceScale += bounceVel;

        const idle = now - lastMoveTs > 3000;
        const ax = idle ? Math.sin(now * 0.0009) * 0.45 : targetDx;
        const ay = idle ? Math.cos(now * 0.0007) * 0.3 : targetDy;
        curDx += (ax - curDx) * 0.09;
        curDy += (ay - curDy) * 0.09;

        if (floatWrap && !REDUCED_MOTION) {
          const fy = Math.sin(now * 0.002) * 3.2;
          const rz = Math.sin(now * 0.0013) * 2.2;
          floatWrap.style.transform = `translate3d(0, ${fy.toFixed(2)}px, 0) rotateX(${(-curDy * 10).toFixed(1)}deg) rotateY(${(curDx * 12).toFixed(1)}deg) rotateZ(${rz.toFixed(2)}deg) scale(${bounceScale.toFixed(4)})`;
        }
        if (eyeM && eyeC && !REDUCED_MOTION) {
          const ex = (curDx * 1.9).toFixed(2);
          const ey = (curDy * 1.4).toFixed(2);
          eyeM.style.transform = `translate3d(${ex}px, ${ey}px, 0)`;
          eyeC.style.transform = `translate3d(${ex}px, ${ey}px, 0)`;
        }

        ctxB.clearRect(0, 0, size, size);
        ctxF.clearRect(0, 0, size, size);
        const cx = size * 0.5, cy = size * 0.5, fov = size * 1.5;

        const tx1 = 1.08 + curDy * 0.14;
        const tz1 = -0.36 + curDx * 0.12;
        drawRing(size * 0.44, tx1, tz1, '255, 255, 255', 0.24, 0.09, 1, cx, cy, fov);
        drawSatellite(size * 0.44, phase * 1.05 + 0.4, 1, tx1, tz1, '56, 189, 248', cx, cy, fov);

        const tx2 = 1.12 - curDy * 0.12;
        const tz2 = 0.5 + curDx * 0.1;
        drawRing(size * 0.4, tx2, tz2, '225, 75, 134', 0.3, 0.12, 1, cx, cy, fov);
        drawSatellite(size * 0.4, -phase * 0.88 + 2.2, -1, tx2, tz2, '225, 75, 134', cx, cy, fov);

        for (let i = pulseWaves.length - 1; i >= 0; i--) {
          const pw = pulseWaves[i];
          pw.r += dt * 0.55;
          pw.alpha *= 0.91;
          if (pw.alpha < 0.03 || pw.r > 0.52) {
            pulseWaves.splice(i, 1);
            continue;
          }
          drawRing(size * pw.r, pw.tx, pw.tz, pw.rgb, pw.alpha, pw.alpha * 0.4, 1.3, cx, cy, fov);
        }
      }

      if (!REDUCED_MOTION) requestAnimationFrame(frame);
    }
    frame(performance.now());
  }

  /* ========================================================================
     2. SCROLL PROGRESS & REVEALS
     ======================================================================== */
  function initScroll() {
    const nav = document.getElementById('site-nav');
    const hairline = document.getElementById('scroll-hairline-fill');
    const navLinks = Array.from(document.querySelectorAll('.nav-links a[data-sec]'));
    const sections = Array.from(document.querySelectorAll('section[data-section]'));
    const revealAll = Array.from(document.querySelectorAll('.reveal-up'));

    const revealObs = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          e.target.classList.add('is-in');
          revealObs.unobserve(e.target);
        }
      });
    }, { threshold: 0.01, rootMargin: '0px 0px 80px 0px' });
    revealAll.forEach((el) => revealObs.observe(el));

    const secObs = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          const id = e.target.dataset.section;
          navLinks.forEach((a) => a.classList.toggle('is-active', a.dataset.sec === id));
        }
      });
    }, { threshold: 0.3 });
    sections.forEach((s) => secObs.observe(s));

    function onScroll() {
      const y = window.scrollY;
      if (nav) nav.classList.toggle('is-scrolled', y > 16);
      const max = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
      if (hairline) hairline.style.width = `${(Math.min(1, y / max) * 100).toFixed(2)}%`;

      const vh = window.innerHeight;
      const nearBottom = y + vh >= document.documentElement.scrollHeight - 140;
      revealAll.forEach((el) => {
        if (el.classList.contains('is-in')) return;
        if (nearBottom || el.getBoundingClientRect().top < vh + 60) {
          el.classList.add('is-in');
          revealObs.unobserve(el);
        }
      });
    }

    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  /* ========================================================================
     3. 3-ROW INTERACTIVE GAME REEL + FILTER + INSPECTOR
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
    const kernel = g.eng === 'SYS4' ? 'xsystem4' : 'xsystem35';
    if (eng) eng.textContent = kernel;
    if (cn) cn.style.display = g.cn ? 'inline-block' : 'none';
    if (note) {
      note.textContent = currentLang === 'en'
        ? `Auto-routed to ${kernel} native kernel · Android / iOS / Switch`
        : currentLang === 'ja'
          ? `${kernel} へ自動ルーティング · Android / iOS / Switch 対応`
          : `自动路由至 ${kernel} 原生运行 · Android / iOS / Switch 三端直跑`;
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
      return `<div class="game-tile" data-slug="${g.slug}" data-eng="${g.eng}" data-cn="${g.cn ? '1' : '0'}" role="button" tabindex="0">
        <img src="assets/games/${g.slug}.webp" alt="${escapeHtml(title)}" width="120" height="120" />
        <div class="tile-meta">
          <span class="tile-name" title="${escapeHtml(title)}">${escapeHtml(title)}</span>
          <span class="tile-badges">
            <span class="tb-eng">${g.eng}</span>
            ${g.cn ? '<span class="tb-cn">CN</span>' : ''}
          </span>
        </div>
      </div>`;
    };

    const r1 = CATALOG_GAMES.slice(0, 12).map(makeTile).join('');
    const r2 = CATALOG_GAMES.slice(12, 24).map(makeTile).join('');
    const r3 = CATALOG_GAMES.slice(24, 36).map(makeTile).join('');
    stripA.innerHTML = r1 + r1;
    stripB.innerHTML = r2 + r2;
    stripC.innerHTML = r3 + r3;

    applyCatalogFilter(currentFilter);
    updateInspector(selectedSlug);
  }

  /* ========================================================================
     4. PLATFORM SWITCHER, HANDBOOK & MICRO-WIDGETS
     ======================================================================== */
  function renderPlatform(key) {
    currentPlatform = key;
    const copy = PLATFORMS[key][currentLang] || PLATFORMS[key].zh;
    const asset = RELEASE.assets[key];

    document.querySelectorAll('[data-platform]').forEach((btn) => {
      const active = btn.dataset.platform === key;
      btn.classList.toggle('is-active', active);
      btn.setAttribute('aria-selected', String(active));
    });

    const set = (id, text) => {
      const el = document.getElementById(id);
      if (el) el.textContent = text;
    };
    set('plat-headline', copy.headline);
    set('plat-pkg', asset.name);
    set('plat-dl-label', copy.cta);
    set('plat-size-chip', asset.size);

    const dlBtn = document.getElementById('plat-dl-btn');
    if (dlBtn) dlBtn.href = asset.url;

    const ul = document.getElementById('plat-bullets');
    if (ul) ul.innerHTML = copy.bullets.map((b) => `<li>${escapeHtml(b)}</li>`).join('');
  }

  function renderHandbook(tab) {
    currentHandbook = tab;
    document.querySelectorAll('[data-hb]').forEach((btn) => {
      const active = btn.dataset.hb === tab;
      btn.classList.toggle('is-active', active);
      btn.setAttribute('aria-selected', String(active));
    });
    const host = document.getElementById('handbook-content');
    if (host && HANDBOOK[tab]) {
      host.innerHTML = HANDBOOK[tab][currentLang] || HANDBOOK[tab].zh;
    }
  }

  function initWidgets() {
    const hexBadge = document.getElementById('hex-badge');
    if (hexBadge && !REDUCED_MOTION) {
      const labels = ['X4SS ⇄ GD\\x01\\x01', 'RSM v6 ⇄ PC RSM', 'GSAVE ⇄ PC v8', '<memory> ISOLATED'];
      let idx = 0;
      setInterval(() => {
        idx = (idx + 1) % labels.length;
        hexBadge.textContent = labels[idx];
      }, 2600);
    }

    const range = document.getElementById('demo-font-range');
    const valEl = document.getElementById('demo-font-val');
    const preview = document.getElementById('slider-preview');
    if (range && valEl && preview) {
      const sync = () => {
        const v = Number(range.value);
        valEl.textContent = `${v}%`;
        preview.style.fontSize = `${(12.5 * v / 100).toFixed(1)}px`;
      };
      range.addEventListener('input', sync);
      sync();
    }
  }

  /* ========================================================================
     5. LIVE GITHUB LATEST RELEASE SYNC & I18N
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
    renderPlatform(currentPlatform);
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
      if (key === 'hero.sub') el.innerHTML = dict[key];
      else el.textContent = dict[key];
    });

    renderPlatform(currentPlatform);
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
    toastTimer = setTimeout(() => el.classList.remove('is-shown'), 2200);
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-platform]').forEach((btn) => {
      btn.addEventListener('click', () => renderPlatform(btn.dataset.platform));
    });
    document.querySelectorAll('[data-hb]').forEach((btn) => {
      btn.addEventListener('click', () => renderHandbook(btn.dataset.hb));
    });
    document.querySelectorAll('.filter-pill').forEach((btn) => {
      btn.addEventListener('click', () => applyCatalogFilter(btn.dataset.filter));
    });
    document.querySelectorAll('.lang-btn').forEach((btn) => {
      btn.addEventListener('click', () => applyLanguage(btn.dataset.lang));
    });

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
    initKineticAlice();
    initScroll();
    initWidgets();
    fetchLatestRelease();
  });
})();
