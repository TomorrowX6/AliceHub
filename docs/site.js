import * as THREE from './vendor/three.module.min.js';

const REDUCED_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const RELEASE = {
  tag: 'f0810b6',
  assets: {
    android: {
      size: '44.3 MiB',
      url: 'https://github.com/TomorrowX6/AliceHub/releases/download/f0810b6/AliceHub-f0810b6-arm64-v8a.apk',
    },
    ios: {
      size: '48.6 MiB',
      url: 'https://github.com/TomorrowX6/AliceHub/releases/download/f0810b6/AliceHub-f0810b6-ios-unsigned.ipa',
    },
    switch: {
      size: '85.9 MiB',
      url: 'https://github.com/TomorrowX6/AliceHub/releases/download/f0810b6/AliceHub-f0810b6-switch.zip',
    },
  },
};

/* 35 Painted AliceSoft Game Covers for the Interactive 3D Curved Gallery Wall */
const GAMES = [
  { slug: 'rance10', eng: 'SYS4', cn: true, zh: '兰斯 10 -决战-', en: 'Rance X', ja: 'ランス10 -決戦-' },
  { slug: 'evenicle2', eng: 'SYS4', cn: true, zh: '夏娃年代记 2', en: 'Evenicle 2', ja: 'イブニクル2' },
  { slug: 'sengokurance', eng: 'SYS4', cn: true, zh: '战国兰斯', en: 'Sengoku Rance', ja: '戦国ランス' },
  { slug: 'rance09', eng: 'SYS4', cn: true, zh: '兰斯 09 赫尔曼革命', en: 'Rance IX', ja: 'ランス9' },
  { slug: 'rance08', eng: 'SYS4', cn: true, zh: '兰斯 08 Quest Magnum', en: 'Rance Quest', ja: 'ランス・クエスト' },
  { slug: 'rance06', eng: 'SYS4', cn: true, zh: '兰斯 VI 赛斯崩坏', en: 'Rance VI', ja: 'ランスVI' },
  { slug: 'rance03', eng: 'SYS4', cn: true, zh: '兰斯 03 利萨斯陷落', en: 'Rance 03', ja: 'ランス03' },
  { slug: 'rance01', eng: 'SYS4', cn: true, zh: '兰斯 01 寻找小光', en: 'Rance 01', ja: 'ランス01' },
  { slug: 'evenicle', eng: 'SYS4', cn: true, zh: '夏娃年代记', en: 'Evenicle', ja: 'イブニクル' },
  { slug: 'daiteikoku', eng: 'SYS4', cn: true, zh: '大帝国', en: 'Daiteikoku', ja: '大帝国' },
  { slug: 'escalayer_reboot', eng: 'SYS4', cn: true, zh: '超昂天使 R 重制版', en: 'Escalayer Reboot', ja: '超昂天使R' },

  { slug: 'ixseal', eng: 'SYS4', cn: true, zh: '超昂神骑 Ixseal', en: 'Beat Valkyrie Ixseal', ja: '超昂神騎エクシール' },
  { slug: 'haruka', eng: 'SYS4', cn: true, zh: '超昂闪忍 遥', en: 'Beat Blades Haruka', ja: '超昂閃忍ハルカ' },
  { slug: 'maman', eng: 'SYS4', cn: true, zh: '再婚妈妈', en: 'Heartful Maman', ja: 'は～とふるママン' },
  { slug: 'oyakorankan', eng: 'SYS4', cn: true, zh: '母娘乱馆', en: 'Oyakorankan', ja: '母娘乱館' },
  { slug: 'haharanman', eng: 'SYS4', cn: true, zh: '母烂漫', en: 'Haha Ranman', ja: '母爛漫' },
  { slug: 'tsumamigui3', eng: 'SYS4', cn: true, zh: '妻中蜜 3', en: 'Tsumamigui 3', ja: '妻みぐい3' },
  { slug: 'daibanchou', eng: 'SYS4', cn: true, zh: '大番长', en: 'Daibanchou', ja: '大番長' },
  { slug: 'dorapeko', eng: 'SYS4', cn: true, zh: '多拉佩科', en: 'Dorapeko', ja: 'ドラペコ' },
  { slug: 'bladebriders', eng: 'SYS4', cn: false, zh: 'Blade Briders', en: 'Blade Briders', ja: 'ブレイドブライダーズ' },
  { slug: 'toushin3', eng: 'SYS4', cn: true, zh: '斗神都市 III', en: 'Toushin Toshi III', ja: '闘神都市III' },
  { slug: 'galzoo', eng: 'SYS4', cn: true, zh: 'GALZOO 岛', en: 'Galzoo Island', ja: 'GALZOOアイランド' },
  { slug: 'paschacpp', eng: 'SYS4', cn: true, zh: '帕斯茶 C++', en: 'Pascha C++', ja: 'ぱすちゃC++' },

  { slug: 'kichikuou', eng: 'SYS35', cn: true, zh: '鬼畜王兰斯', en: 'Kichikuou Rance', ja: '鬼畜王ランス' },
  { slug: 'daiakuji', eng: 'SYS35', cn: true, zh: '大恶司', en: 'Daiakuji', ja: '大悪司' },
  { slug: 'mamatoto', eng: 'SYS35', cn: true, zh: '魔物娘战记', en: 'Mamatoto', ja: 'ママトト' },
  { slug: 'toushin2', eng: 'SYS35', cn: true, zh: '斗神都市 II', en: 'Toushin Toshi II', ja: '闘神都市II' },
  { slug: 'atlachnacha', eng: 'SYS35', cn: true, zh: '阿托拉克纳克亚', en: 'Atlach-Nacha', ja: 'アトラク＝ナクア' },
  { slug: 'dalk', eng: 'SYS35', cn: false, zh: 'DALK 外传', en: 'DALK', ja: 'DALK' },
  { slug: 'escalayer', eng: 'SYS35', cn: true, zh: '超昂天使 原版', en: 'Escalayer', ja: '超昂天使エスカレイヤー' },
  { slug: 'rance02', eng: 'SYS4', cn: true, zh: '兰斯 02', en: 'Rance 02', ja: 'ランス02' },
  { slug: 'rance5d', eng: 'SYS35', cn: true, zh: '兰斯 5D', en: 'Rance 5D', ja: 'ランス5D' },
  { slug: 'yorugakuru', eng: 'SYS35', cn: true, zh: '夜来了！', en: 'Yoru ga Kuru!', ja: '夜が来る!' },
  { slug: 'okusamantr', eng: 'SYS4', cn: true, zh: '太太的恢复术', en: 'Okusama no Kaifuku Jutsu', ja: '奥さまの回復術' },
  { slug: 'ikusamiko', eng: 'SYS4', cn: false, zh: '战巫女', en: 'Ikusamiko', ja: '戦巫女' },
];

const I18N = {
  zh: {
    'nav.demos': '演示',
    'nav.download': '下载',
    'filter.all': '全部',
    'filter.cn': '汉化',
    'hero.sub': 'AliceSoft System 多平台原生启动器',
    'hero.hint': '拖拽拨动 · 点击起波',
    'hero.explore': '功能演示',
    'hero.cta': '获取版本',
    'ed.about': '说明',
    'ed.statement': '在手机与掌机上直接打开 AliceSoft 作品。<span>不依赖 Wine 或转译层，由三套开源原生内核驱动，支持压缩包与镜像导入、汉化与 MOD 补丁及跨平台存档互通。</span>',
    'spec.k1': '内核',
    'spec.k2': '目录',
    'spec.v2': '内置 171+ 作品识别，支持自由导入任意目录与多碟合并',
    'spec.k3': '输入',
    'spec.v3': '原生触控手势、自定义虚拟按键与主流蓝牙 / USB 手柄',
    'spec.k4': '许可',
    'spec.v4': 'GNU GPL-2.0 开源免费，安装包不含任何游戏本体',
    'demo.kicker': '功能演示',
    'demo.t1': '局内字号与操控',
    'demo.t2': 'PC ⇄ 移动存档转换',
    'demo.t3': '多格式导入与路由',
    'demo.t4': '无损 MOD 与入口',
    'demo.play.title': 'PartsEngine 字号实时重排与虚拟手柄叠加',
    'demo.play.desc': '拖动滑块实时体验字体防溢出缩放、滚动条加宽，切换 GAL / RPG 按键布局或点击 » Ctrl 快进对话。',
    'demo.play.font': '字号缩放',
    'demo.play.bar': '滚动条宽度',
    'demo.play.pOff': '纯触控',
    'demo.save.title': '基于 .ain 结构定义的 PC ⇄ 移动端无损存档互通',
    'demo.save.desc': '导出 PC 存档时按游戏 .ain 重建 Windows 头部与压缩流，导入覆盖前自动生成可还原备份。',
    'demo.save.run': '转换方向 ⇄',
    'demo.router.title': '多格式直读解压与三套原生内核自动识别',
    'demo.router.desc': '点选不同格式的本地游戏包，预览流式解压、多碟合并、编码修复与三核自动路由。',
    'demo.mods.title': '无损原子备份 MOD、去码补丁与启动入口切换',
    'demo.mods.desc': '模拟单作项目中心：自由开关补丁或切换启动入口，原版文件自动隔离备份，随时一键还原。',
    'demo.mods.reset': '一键还原原版',
    'ed.download': '下载',
    'dl.android': 'Android 8.0+ · 直接安装',
    'dl.ios': 'iOS 15+ · 自签侧载',
    'dl.switch': 'Homebrew · 解压至 /switch/',
    'dl.qq': 'QQ 交流群',
    'dl.releases': '历史版本 ↗',
    'dl.ci': '构建记录 ↗',
    'toast.qq': '已复制 QQ 群号：383917628',
  },
  en: {
    'nav.demos': 'Demos',
    'nav.download': 'Download',
    'filter.all': 'All',
    'filter.cn': 'CN',
    'hero.sub': 'Native multi-platform launcher for AliceSoft System',
    'hero.hint': 'Drag to glide · Click to ripple',
    'hero.explore': 'Live Demos',
    'hero.cta': 'Download',
    'ed.about': 'ABOUT',
    'ed.statement': 'Open AliceSoft games directly on phone and handheld. <span>No Wine or translation layers—driven by three open-source native engines with archive and disc import, translation & MOD patches, and cross-platform saves.</span>',
    'spec.k1': 'ENGINES',
    'spec.k2': 'CATALOG',
    'spec.v2': '171+ recognized titles built in, plus open import for any game folder or multi-disc set',
    'spec.k3': 'INPUT',
    'spec.v3': 'Native touch gestures, customizable virtual pad, and standard Bluetooth / USB controllers',
    'spec.k4': 'LICENSE',
    'spec.v4': 'GNU GPL-2.0 free & open source — no game files are bundled',
    'demo.kicker': 'INTERACTIVE',
    'demo.t1': 'Font Scale & Controls',
    'demo.t2': 'PC ⇄ Mobile Saves',
    'demo.t3': 'Archive & Routing',
    'demo.t4': 'Atomic MODs & Entry',
    'demo.play.title': 'Live PartsEngine Font Scaling & Virtual Pad Overlay',
    'demo.play.desc': 'Drag sliders to test non-overflow font scaling and wider scrollbars, switch GAL/RPG pads, or latch » Ctrl.',
    'demo.play.font': 'Font Scale',
    'demo.play.bar': 'Scrollbar',
    'demo.play.pOff': 'Touch Only',
    'demo.save.title': '.ain-Guided Bidirectional PC ⇄ Mobile Save Conversion',
    'demo.save.desc': 'Rebuilds Windows save headers and compressed streams from .ain schemas with automatic pre-overwrite backups.',
    'demo.save.run': 'Flip Direction ⇄',
    'demo.router.title': 'Multi-Format Import & Automatic 3-Kernel Routing',
    'demo.router.desc': 'Pick sample archives or disc images to preview streaming extraction, multi-disc merging, and engine detection.',
    'demo.mods.title': 'Atomic-Backup MODs, Uncensored Patches & Launch Entries',
    'demo.mods.desc': 'Toggle patches or switch launch entries—original game files are backed up atomically for one-tap restore.',
    'demo.mods.reset': 'Restore Vanilla',
    'ed.download': 'DOWNLOAD',
    'dl.android': 'Android 8.0+ · Direct install',
    'dl.ios': 'iOS 15+ · Sideload IPA',
    'dl.switch': 'Homebrew · Extract to /switch/',
    'dl.qq': 'QQ Group',
    'dl.releases': 'All Releases ↗',
    'dl.ci': 'CI Builds ↗',
    'toast.qq': 'Copied QQ Group: 383917628',
  },
  ja: {
    'nav.demos': 'デモ',
    'nav.download': '入手',
    'filter.all': 'すべて',
    'filter.cn': '翻訳',
    'hero.sub': 'AliceSoft System マルチプラットフォーム実行環境',
    'hero.hint': 'ドラッグで移動 · クリックで波紋',
    'hero.explore': '機能デモ',
    'hero.cta': 'ダウンロード',
    'ed.about': '概要',
    'ed.statement': 'スマホや携帯機でアリスソフト作品をそのまま起動。<span>Wine や命令変換に頼らず、3つのオープンソース・ネイティブエンジンで動作。アーカイブ読込・MOD／翻訳パッチ・機種間セーブ連携に対応します。</span>',
    'spec.k1': 'エンジン',
    'spec.k2': 'カタログ',
    'spec.v2': '171 作品以上を自動判別。カタログ外のフォルダや複数ディスクも自由に導入可能',
    'spec.k3': '操作',
    'spec.v3': 'ネイティブタッチ・カスタマイズ可能な仮想パッド・各種コントローラー対応',
    'spec.k4': 'ライセンス',
    'spec.v4': 'GNU GPL-2.0 オープンソース（ゲーム本体は含まれません）',
    'demo.kicker': '機能デモ',
    'demo.t1': '文字拡大と仮想操作',
    'demo.t2': 'PC ⇄ セーブ変換',
    'demo.t3': '自動展開と判別',
    'demo.t4': '非破壊 MOD と起動',
    'demo.play.title': 'PartsEngine リアルタイム文字拡大＆仮想パッド',
    'demo.play.desc': 'スライダーで文字拡大とスクロールバー調整を体験。GAL / RPG 配置切替や » Ctrl 早送りにも対応。',
    'demo.play.font': '文字サイズ',
    'demo.play.bar': 'バー幅',
    'demo.play.pOff': 'タッチのみ',
    'demo.save.title': '.ain 構造参照による PC ⇄ モバイル双方向セーブ変換',
    'demo.save.desc': 'ゲームの .ain を参照し Windows 版ヘッダへ無劣化変換。上書き前の自動バックアップも実行します。',
    'demo.save.run': '変換方向を切替 ⇄',
    'demo.router.title': '全形式直展開＆3系統ネイティブエンジン自動判別',
    'demo.router.desc': '各種アーカイブや ISO を選んで、ストリーミング展開・複数ディスク統合・起動ルーティングを確認。',
    'demo.mods.title': '非破壊バックアップ MOD・修正解除＆起動エントリ切替',
    'demo.mods.desc': 'パッチの ON/OFF や起動エントリを切替。元ファイルは自動退避され、いつでもワンタップで復元できます。',
    'demo.mods.reset': '初期状態へ復元',
    'ed.download': '入手',
    'dl.android': 'Android 8.0+ · 直接導入',
    'dl.ios': 'iOS 15+ · 署名して導入',
    'dl.switch': 'Homebrew · /switch/ へ展開',
    'dl.qq': 'QQ グループ',
    'dl.releases': 'リリース一覧 ↗',
    'dl.ci': 'ビルド履歴 ↗',
    'toast.qq': 'QQ 番号をコピーしました：383917628',
  },
};

let currentLang = 'zh';
let focusedGame = GAMES[0];

/* ==========================================================================
   1. THREE.JS INTERACTIVE 3D CURVED COVER WALL (TRUE SRGB BRIGHTNESS FIXED!)
   ========================================================================== */
function initThreeWall() {
  const canvas = document.getElementById('webgl-stage');
  if (!canvas) return { triggerWave: () => {}, setFilter: () => {} };

  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      alpha: false,
      powerPreference: 'high-performance',
    });
  } catch {
    return { triggerWave: () => {}, setFilter: () => {} };
  }

  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(window.innerWidth, window.innerHeight, false);
  renderer.setClearColor(0x070709, 1);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, window.innerWidth / window.innerHeight, 0.1, 80);
  camera.position.set(0, 0, 12.8);

  /* Deep Obsidian Atmospheric Aurora Backdrop */
  const auroraMat = new THREE.ShaderMaterial({
    depthWrite: false,
    uniforms: {
      uTime: { value: 0 },
      uMouse: { value: new THREE.Vector2(0, 0) },
    },
    vertexShader: `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      precision highp float;
      varying vec2 vUv;
      uniform float uTime;
      uniform vec2 uMouse;
      void main() {
        vec2 p = vUv - 0.5;
        vec2 c1 = vec2(-0.26 + sin(uTime * 0.2) * 0.08 + uMouse.x * 0.07, 0.18 + cos(uTime * 0.17) * 0.05);
        vec2 c2 = vec2(0.28 + cos(uTime * 0.18) * 0.08 - uMouse.x * 0.06, -0.14 + sin(uTime * 0.22) * 0.06);
        float g1 = exp(-dot(p - c1, p - c1) * 5.0);
        float g2 = exp(-dot(p - c2, p - c2) * 4.8);
        vec3 col = vec3(0.026, 0.026, 0.038)
          + vec3(0.88, 0.29, 0.52) * g1 * 0.13
          + vec3(0.22, 0.74, 0.97) * g2 * 0.12;
        gl_FragColor = vec4(col, 1.0);
      }
    `,
  });
  const auroraPlane = new THREE.Mesh(new THREE.PlaneGeometry(62, 38), auroraMat);
  auroraPlane.position.set(0, 0, -14);
  scene.add(auroraPlane);

  /* 3D Cursor Volumetric Light Probe + Click Shockwave Torus Rings */
  const probeMat = new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    uniforms: { uIntensity: { value: 0.18 } },
    vertexShader: `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      precision highp float;
      varying vec2 vUv;
      uniform float uIntensity;
      void main() {
        vec2 p = vUv - 0.5;
        float d = length(p);
        if (d > 0.5) discard;
        float glow = exp(-d * d * 18.0);
        vec3 col = mix(vec3(0.22, 0.74, 0.97), vec3(0.88, 0.29, 0.52), smoothstep(-0.35, 0.35, p.x));
        gl_FragColor = vec4(col, glow * uIntensity);
      }
    `,
  });
  const cursorProbe = new THREE.Mesh(new THREE.PlaneGeometry(5.6, 5.6), probeMat);
  cursorProbe.position.set(0, 0, 1.5);
  scene.add(cursorProbe);

  const shockRings = [];
  const shockGeo = new THREE.TorusGeometry(1.0, 0.018, 8, 96);

  /* 5-Row Panoramic Curved 3D Cover Wall (True sRGB faithful brightness!) */
  const wallGroup = new THREE.Group();
  scene.add(wallGroup);

  const texLoader = new THREE.TextureLoader();
  const tileGeo = new THREE.PlaneGeometry(1.9, 1.9, 1, 1);

  const tileVert = `
    varying vec2 vUv;
    void main() {
      vUv = uv;
      gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    }
  `;

  const tileFrag = `
    precision highp float;
    uniform sampler2D uMap;
    uniform float uHover;
    uniform float uDim;
    uniform float uFlash;
    uniform float uProx;
    uniform vec2 uLightDir;
    varying vec2 vUv;

    float sdRoundRect(vec2 p, vec2 b, float r) {
      vec2 q = abs(p) - b + r;
      return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    void main() {
      vec2 p = vUv - 0.5;
      float d = sdRoundRect(p, vec2(0.495), 0.072);
      float mask = 1.0 - smoothstep(-0.008, 0.008, d);
      if (mask < 0.01) discard;

      // Sample faithful sRGB directly (NoColorSpace prevents double-gamma darkening)
      vec2 shift = vec2(uFlash * 0.011, 0.0);
      vec3 tex = vec3(
        texture2D(uMap, clamp(vUv + shift, 0.002, 0.998)).r,
        texture2D(uMap, vUv).g,
        texture2D(uMap, clamp(vUv - shift, 0.002, 0.998)).b
      );

      float luma = dot(tex, vec3(0.299, 0.587, 0.114));
      vec3 col = mix(tex, vec3(luma * 0.42), uDim * 0.85);

      // Vibrant true-color resting brightness (0.94) + gentle hover/proximity lift
      float bright = mix(0.94, 1.12, clamp(uHover + uProx * 0.2, 0.0, 1.0)) * (1.0 - uDim * 0.65);
      col *= bright;

      // Subtle diagonal specular glass sheen
      float spec = smoothstep(0.36, 0.96, 1.0 - abs(dot(p, normalize(uLightDir)) - 0.08));
      col += vec3(0.9, 0.96, 1.0) * spec * (0.03 + uProx * 0.07 + uHover * 0.11 + uFlash * 0.16);

      // Crisp luminous glass edge highlight
      float rim = smoothstep(-0.02, -0.004, d) * mask;
      vec3 rimCol = mix(vec3(1.0), vec3(0.22, 0.74, 0.97), clamp(uHover + uFlash + uProx * 0.35, 0.0, 1.0));
      col += rimCol * rim * (0.14 + uProx * 0.14 + uHover * 0.52 + uFlash * 0.56);

      gl_FragColor = vec4(col, mask * (1.0 - uDim * 0.52));
    }
  `;

  const ROWS = 5;
  const COLS = 14;
  const SPACING_X = 2.16;
  const SPACING_Y = 2.14;
  const LOOP_WIDTH = COLS * SPACING_X;
  const HALF_WIDTH = LOOP_WIDTH * 0.5;
  const ROW_SPEEDS = [-0.46, 0.38, -0.42, 0.45, -0.36];
  const ROW_OFFSETS = [0, 11, 22, 6, 17];

  const textures = new Map();
  function getTexture(slug) {
    if (!textures.has(slug)) {
      const tex = texLoader.load(`assets/games/${slug}.webp`);
      tex.colorSpace = THREE.NoColorSpace; // Raw sRGB for custom ShaderMaterial (no dark gamma crush!)
      tex.minFilter = THREE.LinearMipmapLinearFilter;
      tex.generateMipmaps = true;
      textures.set(slug, tex);
    }
    return textures.get(slug);
  }

  const tiles = [];
  const meshes = [];

  for (let r = 0; r < ROWS; r++) {
    const yBase = (2 - r) * SPACING_Y + 0.55;
    const rowShift = (r % 2) * (SPACING_X * 0.5);
    for (let c = 0; c < COLS; c++) {
      const game = GAMES[(ROW_OFFSETS[r] + c) % GAMES.length];
      const mat = new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: true,
        uniforms: {
          uMap: { value: getTexture(game.slug) },
          uHover: { value: 0 },
          uDim: { value: 0 },
          uFlash: { value: 0 },
          uProx: { value: 0 },
          uLightDir: { value: new THREE.Vector2(0.7, 0.7) },
        },
        vertexShader: tileVert,
        fragmentShader: tileFrag,
      });

      const mesh = new THREE.Mesh(tileGeo, mat);
      const xInit = -HALF_WIDTH + c * SPACING_X + rowShift;
      mesh.position.set(xInit, yBase, 0);
      mesh.userData = { game };
      wallGroup.add(mesh);
      meshes.push(mesh);

      tiles.push({
        mesh,
        mat,
        game,
        row: r,
        x: xInit,
        yBase,
        hover: 0,
        dim: 0,
        dimTarget: 0,
        phase: Math.random() * Math.PI * 2,
      });
    }
  }

  /* Floating 3D Volumetric Bokeh Dust */
  const PARTICLE_COUNT = 120;
  const pPos = new Float32Array(PARTICLE_COUNT * 3);
  const pScale = new Float32Array(PARTICLE_COUNT);
  const pColor = new Float32Array(PARTICLE_COUNT * 3);
  const pSpeed = new Float32Array(PARTICLE_COUNT);
  const palette = [new THREE.Color('#e14b86'), new THREE.Color('#38bdf8'), new THREE.Color('#e4e6f0')];

  for (let i = 0; i < PARTICLE_COUNT; i++) {
    pPos[i * 3] = (Math.random() - 0.5) * 26;
    pPos[i * 3 + 1] = (Math.random() - 0.5) * 14;
    pPos[i * 3 + 2] = -3.5 + Math.random() * 9.5;
    pScale[i] = 8 + Math.random() * 18;
    pSpeed[i] = 0.12 + Math.random() * 0.25;
    const c = palette[i % palette.length];
    pColor[i * 3] = c.r;
    pColor[i * 3 + 1] = c.g;
    pColor[i * 3 + 2] = c.b;
  }

  const pGeo = new THREE.BufferGeometry();
  pGeo.setAttribute('position', new THREE.BufferAttribute(pPos, 3));
  pGeo.setAttribute('aScale', new THREE.BufferAttribute(pScale, 1));
  pGeo.setAttribute('aColor', new THREE.BufferAttribute(pColor, 3));

  const pMat = new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    uniforms: { uPixelRatio: { value: Math.min(window.devicePixelRatio || 1, 2) } },
    vertexShader: `
      attribute float aScale;
      attribute vec3 aColor;
      varying vec3 vColor;
      uniform float uPixelRatio;
      void main() {
        vColor = aColor;
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_PointSize = aScale * uPixelRatio * (12.0 / max(2.0, -mv.z));
        gl_Position = projectionMatrix * mv;
      }
    `,
    fragmentShader: `
      precision highp float;
      varying vec3 vColor;
      void main() {
        float d = length(gl_PointCoord - 0.5);
        if (d > 0.5) discard;
        gl_FragColor = vec4(vColor, pow(1.0 - d * 2.0, 2.4) * 0.28);
      }
    `,
  });
  const particles = new THREE.Points(pGeo, pMat);
  scene.add(particles);

  /* Interaction: Raycast Hover, Inertia Drag, Click Ripple & Scroll Pause */
  const raycaster = new THREE.Raycaster();
  const pointerNdc = new THREE.Vector2(99, 99);
  const mouse = { x: 0, y: 0, tx: 0, ty: 0 };
  let hoveredMesh = null;
  let scrollProgress = 0;

  let dragging = false;
  let dragMoved = 0;
  let lastDragX = 0;
  let dragVel = 0;
  const waves = [];

  function triggerWave(wx, wy) {
    waves.push({ x: wx, y: wy, t: 0 });
    if (waves.length > 4) waves.shift();

    const rMat = new THREE.MeshBasicMaterial({
      color: waves.length % 2 === 0 ? 0x38bdf8 : 0xe14b86,
      transparent: true,
      opacity: 0.52,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });
    const rMesh = new THREE.Mesh(shockGeo, rMat);
    rMesh.position.set(wx, wy, 1.4);
    rMesh.scale.setScalar(0.35);
    wallGroup.add(rMesh);
    shockRings.push({ mesh: rMesh, mat: rMat, t: 0 });
  }

  function setFilter(filter) {
    for (const tile of tiles) {
      const match =
        filter === 'all' ||
        (filter === 'cn' && tile.game.cn) ||
        tile.game.eng === filter;
      tile.dimTarget = match ? 0 : 1;
    }
  }

  window.addEventListener('pointerdown', (e) => {
    if (scrollProgress > 0.75) return;
    if (e.target.closest('a, button, .editorial-sheet, .stage-bottom, .site-nav')) return;
    dragging = true;
    dragMoved = 0;
    lastDragX = e.clientX;
    canvas.classList.add('is-dragging');
  });

  window.addEventListener('pointermove', (e) => {
    const nx = (e.clientX / Math.max(1, window.innerWidth)) * 2 - 1;
    const ny = -(e.clientY / Math.max(1, window.innerHeight)) * 2 + 1;
    mouse.tx = nx;
    mouse.ty = ny;
    pointerNdc.set(nx, ny);

    if (dragging) {
      const dx = e.clientX - lastDragX;
      lastDragX = e.clientX;
      dragMoved += Math.abs(dx);
      dragVel = Math.max(-14, Math.min(14, dx * 0.048));
    }
  }, { passive: true });

  window.addEventListener('pointerup', (e) => {
    if (!dragging) return;
    dragging = false;
    canvas.classList.remove('is-dragging');

    if (dragMoved < 8) {
      const nx = (e.clientX / Math.max(1, window.innerWidth)) * 2 - 1;
      const ny = -(e.clientY / Math.max(1, window.innerHeight)) * 2 + 1;
      raycaster.setFromCamera(new THREE.Vector2(nx, ny), camera);
      const hits = raycaster.intersectObjects(meshes, false);
      if (hits.length > 0) {
        triggerWave(hits[0].point.x, hits[0].point.y);
        if (hits[0].object.userData.game) updateHudFocus(hits[0].object.userData.game);
      } else {
        triggerWave(nx * 6, ny * 3.5);
      }
    }
  });

  window.addEventListener('pointerleave', () => {
    pointerNdc.set(99, 99);
    hoveredMesh = null;
    dragging = false;
    canvas.classList.remove('is-dragging');
  });

  window.addEventListener('scroll', () => {
    const y = window.scrollY;
    scrollProgress = Math.min(1, y / Math.max(400, window.innerHeight));
    const nav = document.getElementById('site-nav');
    if (nav) nav.classList.toggle('is-scrolled', y > 24);
  }, { passive: true });

  window.addEventListener('resize', () => {
    const w = window.innerWidth;
    const h = window.innerHeight;
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    renderer.setPixelRatio(dpr);
    renderer.setSize(w, h, false);
    pMat.uniforms.uPixelRatio.value = dpr;
  }, { passive: true });

  let lastTime = performance.now();
  let elapsed = 0;
  let rayTick = 0;

  function animate(now) {
    const dt = Math.min(0.05, (now - lastTime) / 1000);
    lastTime = now;

    if (scrollProgress < 0.99 || REDUCED_MOTION) {
      elapsed += REDUCED_MOTION ? 0 : dt;
      mouse.x += (mouse.tx - mouse.x) * 0.055;
      mouse.y += (mouse.ty - mouse.y) * 0.055;
      if (!dragging) dragVel *= 0.93;

      const isNarrow = window.innerWidth < 720;
      const baseZ = isNarrow ? 15.2 : 12.8;
      camera.position.x = mouse.x * 1.0;
      camera.position.y = mouse.y * 0.55 - scrollProgress * 0.6;
      camera.position.z = baseZ + scrollProgress * 2.2;
      camera.lookAt(mouse.x * 0.16, mouse.y * 0.1 - scrollProgress * 0.4, 0);

      wallGroup.rotation.y = -mouse.x * 0.055;
      wallGroup.rotation.x = mouse.y * 0.04 + scrollProgress * 0.12;

      const probeX = mouse.x * 7.2;
      const probeY = mouse.y * 4.2;
      cursorProbe.position.x += (probeX - cursorProbe.position.x) * 0.12;
      cursorProbe.position.y += (probeY - cursorProbe.position.y) * 0.12;
      cursorProbe.position.z = 1.4 + cursorProbe.position.x * cursorProbe.position.x * 0.028;

      auroraMat.uniforms.uTime.value = elapsed;
      auroraMat.uniforms.uMouse.value.set(mouse.x, mouse.y);

      for (let i = shockRings.length - 1; i >= 0; i--) {
        const sr = shockRings[i];
        sr.t += dt;
        sr.mesh.scale.setScalar(0.35 + sr.t * 5.8);
        sr.mat.opacity = Math.max(0, 0.52 * Math.exp(-sr.t * 2.1));
        if (sr.t > 1.8) {
          wallGroup.remove(sr.mesh);
          sr.mat.dispose();
          shockRings.splice(i, 1);
        }
      }

      for (let i = waves.length - 1; i >= 0; i--) {
        waves[i].t += dt;
        if (waves[i].t > 2.6) waves.splice(i, 1);
      }

      rayTick++;
      if (!dragging && pointerNdc.x < 2 && rayTick % 2 === 0) {
        raycaster.setFromCamera(pointerNdc, camera);
        const hits = raycaster.intersectObjects(meshes, false);
        const nextHover = hits.length > 0 ? hits[0].object : null;
        if (nextHover !== hoveredMesh) {
          hoveredMesh = nextHover;
          if (hoveredMesh && hoveredMesh.userData.game) {
            updateHudFocus(hoveredMesh.userData.game);
          }
        }
      }

      for (const tile of tiles) {
        if (!REDUCED_MOTION) {
          const rowDir = tile.row % 2 === 0 ? 1 : -0.75;
          tile.x += (ROW_SPEEDS[tile.row] + dragVel * rowDir * 2.2) * dt;
          if (tile.x > HALF_WIDTH) tile.x -= LOOP_WIDTH;
          else if (tile.x < -HALF_WIDTH) tile.x += LOOP_WIDTH;
        }

        const isHovered = tile.mesh === hoveredMesh;
        tile.hover += ((isHovered ? 1 : 0) - tile.hover) * 0.14;
        tile.dim += (tile.dimTarget - tile.dim) * 0.1;

        const pDist = Math.hypot(tile.x - cursorProbe.position.x, tile.yBase - cursorProbe.position.y);
        const prox = Math.exp(-pDist * pDist * 0.085);
        const curveZ = tile.x * tile.x * 0.032;
        const idleFloat = REDUCED_MOTION ? 0 : Math.sin(elapsed * 0.9 + tile.phase) * 0.045;

        let waveZ = 0;
        let waveFlash = 0;
        for (const w of waves) {
          const dist = Math.hypot(tile.x - w.x, tile.yBase - w.y);
          const front = w.t * 7.4 - dist;
          if (front > 0 && front < 6.283) {
            const env = Math.sin(front) * Math.exp(-dist * 0.21) * Math.exp(-w.t * 1.15);
            waveZ += env * 1.25;
            waveFlash = Math.max(waveFlash, Math.abs(env) * 1.35);
          }
        }

        tile.mesh.position.x = tile.x;
        tile.mesh.position.y = tile.yBase + idleFloat;
        tile.mesh.position.z = curveZ + tile.hover * 0.72 + prox * 0.14 - tile.dim * 1.05 + waveZ;
        tile.mesh.rotation.y = -tile.x * 0.056 + waveZ * 0.09 + dragVel * 0.014;
        tile.mesh.rotation.x = waveZ * 0.055;

        const s = 1 + tile.hover * 0.055 + prox * 0.012;
        tile.mesh.scale.set(s, s, 1);

        tile.mat.uniforms.uHover.value = tile.hover;
        tile.mat.uniforms.uDim.value = tile.dim;
        tile.mat.uniforms.uFlash.value = waveFlash;
        tile.mat.uniforms.uProx.value = prox;
        tile.mat.uniforms.uLightDir.value.set(0.65 + mouse.x * 0.4, 0.72 + mouse.y * 0.4);
      }

      if (!REDUCED_MOTION) {
        const posAttr = pGeo.attributes.position;
        for (let i = 0; i < PARTICLE_COUNT; i++) {
          let py = posAttr.getY(i) + pSpeed[i] * dt;
          if (py > 7.2) py = -7.2;
          posAttr.setY(i, py);
        }
        posAttr.needsUpdate = true;
        particles.rotation.y = mouse.x * 0.05;
      }

      renderer.render(scene, camera);
    }

    if (!REDUCED_MOTION) requestAnimationFrame(animate);
  }

  animate(performance.now());
  return { triggerWave, setFilter };
}

/* ==========================================================================
   2. INTERACTIVE CORE FEATURE DEMOS WORKBENCH
   ========================================================================== */
function initInteractiveDemos() {
  /* --- Tab Switching --- */
  const tabs = Array.from(document.querySelectorAll('.demo-tab'));
  const panes = Array.from(document.querySelectorAll('.demo-pane'));
  tabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      const target = tab.dataset.demo;
      tabs.forEach((t) => {
        const active = t === tab;
        t.classList.toggle('is-active', active);
        t.setAttribute('aria-selected', String(active));
      });
      panes.forEach((p) => p.classList.toggle('is-active', p.dataset.pane === target));
    });
  });

  /* --- DEMO 01: Live In-Game Font Scale, Scrollbar & Virtual Pad --- */
  const LINES = {
    zh: [
      ['兰斯', '「哼，人类灭亡危机？那就由本大爷在掌机上亲自解决吧。」'],
      ['希露', '「兰斯大人，拖动下方滑块即可实时缩放 PartsEngine 字体且不截断哦。」'],
      ['兰斯', '「噢！连右侧滚动条都能加宽，还有 GAL / RPG 虚拟手柄随时切换。」'],
      ['AliceHub', '点击 » Ctrl 可锁定 60fps 持续快进，双指轻触或按 B 呼出右键菜单。'],
    ],
    en: [
      ['Rance', '"Humanity in crisis? Fine, I will clear it natively on handheld."'],
      ['Sill', '"Drag the slider below to scale PartsEngine text cleanly without clipping."'],
      ['Rance', '"Nice! Even the scrollbar widens, plus instant GAL & RPG pad layouts."'],
      ['AliceHub', 'Latch » Ctrl for smooth 60fps fast-forward, or tap B / 2-finger for menu.'],
    ],
    ja: [
      ['ランス', '「人類滅亡の危機だと？ 俺様が携帯機でネイティブ解決してやるぜ。」'],
      ['シィル', '「下のスライダーで PartsEngine の文字サイズを崩れず拡大できますよ。」'],
      ['ランス', '「おおっ、スクロールバーも太くなるし、GAL / RPG パッドも即切替だな。」'],
      ['AliceHub', '» Ctrl で 60fps 早送り固定、B または 2 本指タップで右クリックメニュー。'],
    ],
  };

  let lineIdx = 0;
  let ctrlTimer = null;
  const simSpeaker = document.getElementById('sim-speaker');
  const simLine = document.getElementById('sim-line');
  const simStatus = document.getElementById('sim-status');
  const simCtrl = document.getElementById('sim-ctrl');
  const simVpad = document.getElementById('sim-vpad');
  const fontRange = document.getElementById('sim-font-range');
  const fontVal = document.getElementById('sim-font-val');
  const barRange = document.getElementById('sim-bar-range');
  const barVal = document.getElementById('sim-bar-val');
  const simThumb = document.getElementById('sim-thumb');

  function renderSimLine() {
    const bank = LINES[currentLang] || LINES.zh;
    const [spk, txt] = bank[lineIdx % bank.length];
    if (simSpeaker) simSpeaker.textContent = spk;
    if (simLine) simLine.textContent = txt;
  }

  function advanceLine(delta) {
    const bank = LINES[currentLang] || LINES.zh;
    lineIdx = (lineIdx + delta + bank.length) % bank.length;
    renderSimLine();
  }

  if (fontRange && fontVal && simLine) {
    const syncFont = () => {
      const v = Number(fontRange.value);
      fontVal.textContent = `${v}%`;
      simLine.style.fontSize = `${(14.5 * v / 100).toFixed(1)}px`;
    };
    fontRange.addEventListener('input', syncFont);
    syncFont();
  }

  if (barRange && barVal && simThumb) {
    const syncBar = () => {
      const v = Number(barRange.value);
      barVal.textContent = `${v}%`;
      simThumb.style.width = `${Math.round(4 + (v - 100) * 0.04)}px`;
    };
    barRange.addEventListener('input', syncBar);
    syncBar();
  }

  document.querySelectorAll('[data-preset]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const preset = btn.dataset.preset;
      document.querySelectorAll('[data-preset]').forEach((b) => b.classList.toggle('is-active', b === btn));
      if (simVpad) simVpad.dataset.preset = preset;
    });
  });

  document.querySelectorAll('[data-sim-act]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const act = btn.dataset.simAct;
      if (act === 'prev') advanceLine(-1);
      else if (act === 'next' || act === 'skip') advanceLine(1);
      else if (act === 'shot' && simStatus) simStatus.textContent = 'PNG SCREENSHOT SAVED';
      else if (act === 'menu' && simStatus) simStatus.textContent = 'RIGHT-CLICK MENU';
      else if (simStatus) simStatus.textContent = `D-PAD ${act.toUpperCase()}`;
    });
  });

  if (simCtrl) {
    simCtrl.addEventListener('click', () => {
      const on = simCtrl.classList.toggle('is-on');
      if (ctrlTimer) {
        clearInterval(ctrlTimer);
        ctrlTimer = null;
      }
      if (on) {
        if (simStatus) simStatus.textContent = '» CTRL LATCHED · 60fps';
        ctrlTimer = setInterval(() => advanceLine(1), 950);
      } else if (simStatus) {
        simStatus.textContent = 'xsystem4 · 60fps';
      }
    });
  }

  /* --- DEMO 02: Bidirectional PC <-> Mobile Save Converter --- */
  const SAVE_SAMPLES = {
    rance10: {
      schema: 'Rance10.ain',
      mKind: 'MOBILE X4SS',
      pKind: 'WINDOWS GD\\x01\\x01',
      mFile: 'saves/rance10/0001.save',
      pFile: 'PC_SaveData/0001.save',
      mHex: '58 34 53 53 00 1A F2 09 78 9C E3 04',
      pHex: '47 44 01 01 00 1A F2 09 78 9C E3 04',
      logOut: ['[BACKUP] saves/rance10 → save_backups/auto_20261128.zip', '[SCHEMA] Verified 148 struct tables via Rance10.ain · CRC OK'],
      logIn: ['[BACKUP] Pre-overwrite snapshot saved · one-tap restore ready', '[IMPORT] Converted Windows GD\\x01\\x01 → native X4SS · 0 errors'],
    },
    evenicle2: {
      schema: 'Evenicle2.ain',
      mKind: 'MOBILE GSAVE',
      pKind: 'PC GSAVE v8 + .CMT',
      mFile: 'saves/evenicle2/0004.gsave',
      pFile: 'PC_SaveData/0004.gsave',
      mHex: '47 53 56 08 40 00 A2 11 19 88 C0 02',
      pHex: '47 53 56 08 FF 01 A2 11 19 88 C0 02',
      logOut: ['[BACKUP] saves/evenicle2 → save_backups/auto_20261128.zip', '[EXPORT] Repaired .gsave + thumbnail .cmt metadata for Windows'],
      logIn: ['[BACKUP] Isolated existing mobile saves before import', '[IMPORT] Mounted PC .gsave + .cmt into Evenicle 2 slot #04'],
    },
    escalayer_reboot: {
      schema: 'EscalayerR.ain',
      mKind: 'MOBILE <memory>',
      pKind: 'PC RSM RAW',
      mFile: 'saves/escalayer_r/SaveData.rsm',
      pFile: 'PC_SaveData/SaveData.rsm',
      mHex: '52 53 4D 06 7C E9 10 4A 00 3F B4 12',
      pHex: '52 53 4D 00 00 14 00 00 00 3F B4 12',
      logOut: ['[MEMORY] Snapshotted virtual <memory> slot cleanly', '[EXPORT] Decoded RSM v6 header for Windows PC compatibility'],
      logIn: ['[BACKUP] Archived previous <memory> state automatically', '[IMPORT] Restored Windows SaveData.rsm into isolated directory'],
    },
  };

  let curSaveKey = 'rance10';
  let saveToPc = true;

  function renderSaveDemo() {
    const d = SAVE_SAMPLES[curSaveKey];
    const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
    set('save-src-kind', saveToPc ? d.mKind : d.pKind);
    set('save-src-file', saveToPc ? d.mFile : d.pFile);
    set('save-src-hex', saveToPc ? d.mHex : d.pHex);
    set('save-dst-kind', saveToPc ? d.pKind : d.mKind);
    set('save-dst-file', saveToPc ? d.pFile : d.mFile);
    set('save-dst-hex', saveToPc ? d.pHex : d.mHex);
    set('save-schema', d.schema);
    const log = document.getElementById('save-log');
    if (log) {
      const lines = saveToPc ? d.logOut : d.logIn;
      log.innerHTML = lines.map((l) => `<span>${l}</span>`).join('');
    }
  }

  document.querySelectorAll('[data-save-game]').forEach((btn) => {
    btn.addEventListener('click', () => {
      curSaveKey = btn.dataset.saveGame;
      document.querySelectorAll('[data-save-game]').forEach((b) => b.classList.toggle('is-active', b === btn));
      renderSaveDemo();
    });
  });

  const flipSaveBtn = document.getElementById('run-save-convert');
  if (flipSaveBtn) {
    flipSaveBtn.addEventListener('click', () => {
      saveToPc = !saveToPc;
      renderSaveDemo();
    });
  }
  renderSaveDemo();

  /* --- DEMO 03: Multi-Format Archive / ISO Auto-Router --- */
  const ROUTES = {
    oyakorankan: {
      zh: ['libarchive 7z 固实流式直读', '免双倍暂存空间，自动修复压缩包内 Shift-JIS / GBK 路径乱码。', 'xsystem4 · System 4.x', '检测到 AliceStart.CHS 与 CHS.TBL，自动绑定中文启动入口。', 'CHS.TBL 重映射 + CP932 弹窗识别', '自动区分汉化文本与未汉化系统提示框，支持虚拟手柄与 PC 存档。'],
      en: ['Streaming 7z Solid Extraction', 'Zero 2× staging space; repairs garbled Shift-JIS / GBK paths automatically.', 'xsystem4 · System 4.x', 'Detected AliceStart.CHS + CHS.TBL and bound the localized boot entry.', 'CHS.TBL Remap + CP932 Popups', 'Separates remapped CJK dialogue from raw CP932 system dialogs cleanly.'],
      ja: ['7z ソリッド・ストリーミング展開', '2倍の一時領域不要。Shift-JIS / GBK の文字化けパスを自動修復。', 'xsystem4 · System 4.x', 'AliceStart.CHS と CHS.TBL を検出し専用エントリへ自動接続。', 'CHS.TBL 置換＆CP932 判定', '翻訳テキストと未翻訳システムダイアログを正確に判別表示します。'],
    },
    kichikuou: {
      zh: ['ISO9660 / Joliet 双碟自动合并', '直读 2048/2352/2448 扇区镜像，自动合并 Disk1/Disk2 音频与脚本。', 'xsystem35 · System 3.5–3.9', '检测到 System39.ain 与 *SA.ALD，路由至 xsystem35 原生内核。', '原生 MIDI 合成 + 虚拟 CD 音轨', '内置 MIDI 合成器与 playlist.txt CDDA 音轨，切后台自动挂起音频。'],
      en: ['ISO9660 / Joliet Multi-Disc Merge', 'Reads 2048/2352/2448 sector disc images and merges multi-disc assets.', 'xsystem35 · System 3.5–3.9', 'Detected System39.ain + *SA.ALD; routed to native xsystem35 kernel.', 'Native MIDI Synth + Virtual CDDA', 'Built-in MIDI synthesizer and playlist.txt CD tracks with background pause.'],
      ja: ['ISO9660 / Joliet 複数ディスク統合', '2048/2352/2448 セクタの ISO/MDF を直読し Disk1/2 を自動統合。', 'xsystem35 · System 3.5–3.9', 'System39.ain と *SA.ALD を検出し xsystem35 ネイティブ起動。', 'ネイティブ MIDI＆仮想 CD 音源', '内蔵 MIDI シンセと playlist.txt 音源に対応、バックグラウンド自動停止。'],
    },
    rance10: {
      zh: ['RAR5 流式直读 + 增量补丁覆盖', '直接流式解压大字典 RAR 归档，自动按目录隔离多版本实例。', 'xsystem4 · AIN v12–v14.1', '检测到 Rance10.ain + PartsEngine，启用多实例存档与高优调度。', '离线存档编辑 + 60fps 稳帧', '支持多实例独立存档/MOD、实时字号缩放、修改器与 PC 互通导出。'],
      en: ['Streaming RAR5 + Patch Overlay', 'Extracts large-dictionary RAR archives cleanly with multi-instance folders.', 'xsystem4 · AIN v12–v14.1', 'Detected Rance10.ain + PartsEngine; enabled multi-instance save scoping.', 'Save Editor + 60fps Scheduling', 'Independent saves & MODs per folder, live font scale, modifiers & PC export.'],
      ja: ['RAR5 直展開＆差分パッチ統合', '大容量 RAR を省容量展開し、複数バージョンのフォルダを分離管理。', 'xsystem4 · AIN v12–v14.1', 'Rance10.ain と PartsEngine を検出、独立インスタンス環境を構成。', 'セーブ編集＆60fps 制御', 'フォルダ別セーブ/MOD 管理、文字拡大、PC 互換出力を標準搭載。'],
    },
    rance1: {
      zh: ['ZIP 轻量直读与编码校验', '秒级提取早期 DAT 数据包，保留原始目录结构并建立独立存档槽。', 'system3-sdl2 · System 1–3', '检测到 ADISK.DAT + ACG.DAT（1989 架构），路由至 system3-sdl2。', '原生触控 + 轻量快进', '完整兼容早期调色板绘制、直觉触控点击与独立作品存档管理。'],
      en: ['Instant ZIP Extract & Check', 'Extracts classic 1989 DAT packages cleanly with scoped save storage.', 'system3-sdl2 · System 1–3', 'Detected ADISK.DAT + ACG.DAT; routed automatically to system3-sdl2.', 'Direct Touch + Fast Skip', 'Faithful indexed palette rendering with native touch input and fast skip.'],
      ja: ['ZIP 軽量展開＆構成チェック', '初期 DAT パッケージを即座に展開し作品別のセーブ領域を用意。', 'system3-sdl2 · System 1–3', 'ADISK.DAT + ACG.DAT を検出し system3-sdl2 へ自動ルーティング。', 'ネイティブタッチ＆スキップ', '初期パレット描画を忠実に再現し、タッチ操作と高速スキップに対応。'],
    },
  };

  let curRouteKey = 'oyakorankan';
  function renderRouteDemo() {
    const arr = (ROUTES[curRouteKey][currentLang] || ROUTES[curRouteKey].zh);
    const ids = ['rt-extract-title', 'rt-extract-desc', 'rt-engine-title', 'rt-engine-desc', 'rt-caps-title', 'rt-caps-desc'];
    ids.forEach((id, i) => {
      const el = document.getElementById(id);
      if (el) el.textContent = arr[i];
    });
  }

  document.querySelectorAll('[data-route]').forEach((btn) => {
    btn.addEventListener('click', () => {
      curRouteKey = btn.dataset.route;
      document.querySelectorAll('[data-route]').forEach((b) => b.classList.toggle('is-active', b === btn));
      renderRouteDemo();
    });
  });
  renderRouteDemo();

  /* --- DEMO 04: Atomic-Backup MODs & Launch Entry --- */
  const entryReadout = document.getElementById('mod-entry-readout');
  const backupState = document.getElementById('mod-backup-state');
  const checks = Array.from(document.querySelectorAll('.mod-check'));

  function syncModState() {
    const activeCount = checks.filter((c) => c.checked).length;
    if (!backupState) return;
    const files = activeCount === 0 ? 0 : activeCount + 1;
    backupState.textContent = currentLang === 'en'
      ? (activeCount ? `Atomic backup protects ${files} vanilla files · one-tap restore ready` : 'Vanilla state active · 0 files modified')
      : currentLang === 'ja'
        ? (activeCount ? `元ファイル ${files} 件を自動退避済み・ワンタップ復元可能` : '初期状態（変更ファイル 0 件）')
        : (activeCount ? `原子备份已保护 ${files} 个原版文件 · 卸载后自动无损还原` : '当前为纯净原版状态 · 未修改任何原版文件');
  }

  document.querySelectorAll('[data-entry]').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-entry]').forEach((b) => b.classList.toggle('is-active', b === btn));
      if (entryReadout) entryReadout.textContent = `ENTRY: ${btn.dataset.entry}`;
    });
  });

  checks.forEach((c) => c.addEventListener('change', syncModState));

  const resetBtn = document.getElementById('mod-reset-btn');
  if (resetBtn) {
    resetBtn.addEventListener('click', () => {
      checks.forEach((c) => { c.checked = false; });
      syncModState();
    });
  }

  return {
    refreshLang: () => {
      renderSimLine();
      renderRouteDemo();
      syncModState();
    },
  };
}

/* ==========================================================================
   3. FLOATING ALICE EMBLEM EYES + BLINK + CLICK PULSE
   ========================================================================== */
function initAliceEmblem(triggerWave) {
  const btn = document.getElementById('alice-emblem');
  const box = document.getElementById('alice-avatar-box');
  const eyeM = document.getElementById('alice-eye-m');
  const eyeC = document.getElementById('alice-eye-c');
  if (!btn || !box) return;

  if (!REDUCED_MOTION) {
    const scheduleBlink = () => {
      setTimeout(() => {
        if (!box.classList.contains('is-winking')) {
          box.classList.add('is-blinking');
          setTimeout(() => box.classList.remove('is-blinking'), 110);
        }
        scheduleBlink();
      }, 2800 + Math.random() * 3400);
    };
    scheduleBlink();

    window.addEventListener('pointermove', (e) => {
      if (!eyeM || !eyeC) return;
      const r = box.getBoundingClientRect();
      const dx = Math.max(-1, Math.min(1, (e.clientX - (r.left + r.width / 2)) / 240));
      const dy = Math.max(-1, Math.min(1, (e.clientY - (r.top + r.height * 0.7)) / 240));
      const tr = `translate3d(${(dx * 1.8).toFixed(2)}px, ${(dy * 1.3).toFixed(2)}px, 0)`;
      eyeM.style.transform = tr;
      eyeC.style.transform = tr;
    }, { passive: true });
  }

  btn.addEventListener('click', () => {
    box.classList.add('is-winking');
    setTimeout(() => box.classList.remove('is-winking'), 320);
    triggerWave(0, 0.5);
  });
}

/* ==========================================================================
   4. HUD, REVEALS, I18N & LIVE GITHUB RELEASE SYNC
   ========================================================================== */
function updateHudFocus(game) {
  focusedGame = game;
  const titleEl = document.getElementById('hud-title');
  const engEl = document.getElementById('hud-eng');
  if (titleEl) titleEl.textContent = game[currentLang] || game.zh;
  if (engEl) engEl.textContent = game.eng === 'SYS4' ? 'xsystem4' : 'xsystem35';
}

function initReveals() {
  const els = Array.from(document.querySelectorAll('.reveal'));
  if (!('IntersectionObserver' in window) || REDUCED_MOTION) {
    els.forEach((el) => el.classList.add('is-in'));
    return;
  }
  const io = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-in');
        io.unobserve(entry.target);
      }
    });
  }, { threshold: 0.05, rootMargin: '0px 0px 40px 0px' });
  els.forEach((el) => io.observe(el));
}

function applyReleaseUI() {
  const hv = document.getElementById('hero-ver');
  const dv = document.getElementById('dl-release-tag');
  if (hv) hv.textContent = RELEASE.tag;
  if (dv) dv.textContent = RELEASE.tag;
  for (const k of ['android', 'ios', 'switch']) {
    const a = RELEASE.assets[k];
    const row = document.getElementById(`dl-card-${k}`);
    const size = document.getElementById(`dl-size-${k}`);
    if (row) row.href = a.url;
    if (size) size.textContent = a.size;
  }
}

function fetchLatestRelease() {
  fetch('https://api.github.com/repos/TomorrowX6/AliceHub/releases/latest', {
    headers: { Accept: 'application/vnd.github+json' },
  })
    .then((r) => (r.ok ? r.json() : null))
    .then((data) => {
      if (!data || !data.tag_name || !Array.isArray(data.assets)) return;
      RELEASE.tag = data.tag_name;
      const fmt = (b) => `${(b / (1024 * 1024)).toFixed(1)} MiB`;
      for (const a of data.assets) {
        const n = (a.name || '').toLowerCase();
        if (n.endsWith('.apk')) RELEASE.assets.android = { size: fmt(a.size), url: a.browser_download_url };
        else if (n.endsWith('.ipa')) RELEASE.assets.ios = { size: fmt(a.size), url: a.browser_download_url };
        else if (n.includes('switch') && n.endsWith('.zip')) RELEASE.assets.switch = { size: fmt(a.size), url: a.browser_download_url };
      }
      applyReleaseUI();
    })
    .catch(() => {});
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

/* ==========================================================================
   BOOT
   ========================================================================== */
const wall = initThreeWall();
const demos = initInteractiveDemos();
initAliceEmblem(wall.triggerWave);
initReveals();
applyReleaseUI();
fetchLatestRelease();

function applyLanguage(lang) {
  if (!I18N[lang]) return;
  currentLang = lang;
  document.documentElement.lang = lang === 'zh' ? 'zh-CN' : lang === 'ja' ? 'ja' : 'en';

  document.querySelectorAll('.lang-btn').forEach((b) => {
    b.classList.toggle('is-active', b.dataset.lang === lang);
  });

  const dict = I18N[lang];
  document.querySelectorAll('[data-i18n]').forEach((el) => {
    const k = el.dataset.i18n;
    if (!(k in dict)) return;
    if (k === 'ed.statement') el.innerHTML = dict[k];
    else el.textContent = dict[k];
  });

  updateHudFocus(focusedGame);
  demos.refreshLang();
}

applyLanguage('zh');

document.querySelectorAll('.lang-btn').forEach((b) => {
  b.addEventListener('click', () => applyLanguage(b.dataset.lang));
});

document.querySelectorAll('.wf-btn').forEach((b) => {
  b.addEventListener('click', () => {
    document.querySelectorAll('.wf-btn').forEach((x) => x.classList.toggle('is-active', x === b));
    wall.setFilter(b.dataset.filter);
    wall.triggerWave(0, 0.5);
  });
});

const qqBtn = document.getElementById('copy-qq-btn');
if (qqBtn) {
  qqBtn.addEventListener('click', () => {
    const qq = qqBtn.dataset.qq || '383917628';
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(qq).catch(() => {});
    }
    showToast(I18N[currentLang]['toast.qq']);
  });
}
