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
    'nav.download': '下载',
    'hero.sub': 'AliceSoft System 多平台原生启动器',
    'hero.cta': '获取版本',
    'ed.about': '说明',
    'ed.statement': '在手机与掌机上直接打开 AliceSoft 作品。<span>不依赖 Wine 或转译层，由三套开源原生内核驱动，支持压缩包与镜像导入、汉化与 MOD 补丁及跨平台存档互通。</span>',
    'spec.k1': '内核',
    'spec.k2': '目录',
    'spec.v2': '内置 171+ 作品识别，支持自由导入任意目录与多碟合并',
    'spec.k3': '输入',
    'spec.v3': '原生触控手势、自由拖拽虚拟按键与主流蓝牙 / USB 手柄',
    'spec.k4': '许可',
    'spec.v4': 'GNU GPL-2.0 开源免费，安装包不含任何游戏本体',
    'sc.t1': '三端实机与操控',
    'sc.t2': '跨端存档互通',
    'sc.t3': '三核架构与谱系',
    'sc.n1': '切换设备形态、缩放局内字体，或在屏幕内直接拖拽虚拟按键。',
    'sc.n2': '点击切换双向传输方向，基于 .ain 结构在掌机与 Windows PC 间无损转换。',
    'sc.n3': '切换不同年代的 AliceSoft 作品谱系，查看自动匹配的原生引擎与特性。',
    'sc.font': '字号',
    'sc.padOn': '虚拟按键（可拖拽）',
    'sc.padOff': '纯触控',
    'sc.nextGame': '切换作品 ↻',
    'sc.toPc': '掌机 → PC 无损转换',
    'sc.toMob': 'PC → 掌机自动导入',
    'sc.flip': '切换传输方向 ⇄',
    'sc.backupOk': '覆盖前自动备份 · 支持一键还原',
    'sc.autoRoute': '自动识别目录结构并匹配原生引擎',
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
    'nav.download': 'Download',
    'hero.sub': 'Native multi-platform launcher for AliceSoft System',
    'hero.cta': 'Download',
    'ed.about': 'ABOUT',
    'ed.statement': 'Open AliceSoft games directly on phone and handheld. <span>No Wine or translation layers—driven by three open-source native engines with archive and disc import, translation & MOD patches, and cross-platform saves.</span>',
    'spec.k1': 'ENGINES',
    'spec.k2': 'CATALOG',
    'spec.v2': '171+ recognized titles built in, plus open import for any game folder or multi-disc set',
    'spec.k3': 'INPUT',
    'spec.v3': 'Native touch gestures, draggable virtual pads, and standard Bluetooth / USB controllers',
    'spec.k4': 'LICENSE',
    'spec.v4': 'GNU GPL-2.0 free & open source — no game files are bundled',
    'sc.t1': 'Hardware & Controls',
    'sc.t2': 'Cross-Save Pipeline',
    'sc.t3': 'Three Native Kernels',
    'sc.n1': 'Morph between Switch, Android & iPad, scale live text, or drag virtual buttons directly on screen.',
    'sc.n2': 'Flip transfer direction to preview .ain-guided lossless conversion between handheld and Windows PC.',
    'sc.n3': 'Explore four decades of AliceSoft engines and automatic archive/patch detection.',
    'sc.font': 'Font',
    'sc.padOn': 'Virtual Pad (Drag)',
    'sc.padOff': 'Touch Only',
    'sc.nextGame': 'Next Title ↻',
    'sc.toPc': 'Handheld → PC Export',
    'sc.toMob': 'PC → Handheld Import',
    'sc.flip': 'Reverse Flow ⇄',
    'sc.backupOk': 'Auto-backup before overwrite · One-tap restore',
    'sc.autoRoute': 'Auto-detects root markers and routes to native kernel',
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
    'nav.download': '入手',
    'hero.sub': 'AliceSoft System マルチプラットフォーム実行環境',
    'hero.cta': 'ダウンロード',
    'ed.about': '概要',
    'ed.statement': 'スマホや携帯機でアリスソフト作品をそのまま起動。<span>Wine や命令変換に頼らず、3つのオープンソース・ネイティブエンジンで動作。アーカイブ読込・MOD／翻訳パッチ・機種間セーブ連携に対応します。</span>',
    'spec.k1': 'エンジン',
    'spec.k2': 'カタログ',
    'spec.v2': '171 作品以上を自動判別。カタログ外のフォルダや複数ディスクも自由に導入可能',
    'spec.k3': '操作',
    'spec.v3': 'ネイティブタッチ・自由配置仮想パッド・各種コントローラー対応',
    'spec.k4': 'ライセンス',
    'spec.v4': 'GNU GPL-2.0 オープンソース（ゲーム本体は含まれません）',
    'sc.t1': '3機種実機と操作',
    'sc.t2': 'PC ⇄ セーブ連携',
    'sc.t3': '3系統コアの系譜',
    'sc.n1': '機種切替・文字拡大・画面内の仮想パッド自由ドラッグをそのまま体験できます。',
    'sc.n2': '転送方向を切り替えて、.ain 参照による PC ⇄ 携帯機の無劣化変換を確認。',
    'sc.n3': '年代ごとのネイティブエンジン自動判別と対応フォーマットを確認できます。',
    'sc.font': '文字',
    'sc.padOn': '仮想パッド（移動可）',
    'sc.padOff': 'タッチのみ',
    'sc.nextGame': '作品切替 ↻',
    'sc.toPc': '携帯機 → PC 無劣化出力',
    'sc.toMob': 'PC → 携帯機 自動導入',
    'sc.flip': '転送方向を反転 ⇄',
    'sc.backupOk': '上書き前自動退避・ワンタップ復元',
    'sc.autoRoute': 'ディレクトリ構造からネイティブコアを自動判別',
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
let activeShowcase = 'device';

/* ==========================================================================
   1. THREE.JS INTERACTIVE 3D CURVED COVER WALL (TRUE VIBRANT SRGB)
   ========================================================================== */
function initThreeWall() {
  const canvas = document.getElementById('webgl-stage');
  if (!canvas) return { triggerWave: () => {} };

  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      alpha: false,
      powerPreference: 'high-performance',
    });
  } catch {
    return { triggerWave: () => {} };
  }

  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(window.innerWidth, window.innerHeight, false);
  renderer.setClearColor(0x070709, 1);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, window.innerWidth / window.innerHeight, 0.1, 80);
  camera.position.set(0, 0, 12.8);

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

  const wallGroup = new THREE.Group();
  scene.add(wallGroup);

  const texLoader = new THREE.TextureLoader();
  const tileGeo = new THREE.PlaneGeometry(1.9, 1.9, 1, 1);

  const tileFrag = `
    precision highp float;
    uniform sampler2D uMap;
    uniform float uHover;
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

      vec2 shift = vec2(uFlash * 0.011, 0.0);
      vec3 col = vec3(
        texture2D(uMap, clamp(vUv + shift, 0.002, 0.998)).r,
        texture2D(uMap, vUv).g,
        texture2D(uMap, clamp(vUv - shift, 0.002, 0.998)).b
      );

      float bright = mix(0.95, 1.12, clamp(uHover + uProx * 0.2, 0.0, 1.0));
      col *= bright;

      float spec = smoothstep(0.36, 0.96, 1.0 - abs(dot(p, normalize(uLightDir)) - 0.08));
      col += vec3(0.9, 0.96, 1.0) * spec * (0.03 + uProx * 0.07 + uHover * 0.11 + uFlash * 0.16);

      float rim = smoothstep(-0.02, -0.004, d) * mask;
      vec3 rimCol = mix(vec3(1.0), vec3(0.22, 0.74, 0.97), clamp(uHover + uFlash + uProx * 0.35, 0.0, 1.0));
      col += rimCol * rim * (0.14 + uProx * 0.14 + uHover * 0.52 + uFlash * 0.56);

      gl_FragColor = vec4(col, mask);
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
      tex.colorSpace = THREE.NoColorSpace;
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
          uFlash: { value: 0 },
          uProx: { value: 0 },
          uLightDir: { value: new THREE.Vector2(0.7, 0.7) },
        },
        vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
        fragmentShader: tileFrag,
      });

      const mesh = new THREE.Mesh(tileGeo, mat);
      const xInit = -HALF_WIDTH + c * SPACING_X + rowShift;
      mesh.position.set(xInit, yBase, 0);
      mesh.userData = { game };
      wallGroup.add(mesh);
      meshes.push(mesh);

      tiles.push({ mesh, mat, game, row: r, x: xInit, yBase, hover: 0, phase: Math.random() * Math.PI * 2 });
    }
  }

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
      attribute float aScale; attribute vec3 aColor; varying vec3 vColor; uniform float uPixelRatio;
      void main() {
        vColor = aColor;
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_PointSize = aScale * uPixelRatio * (12.0 / max(2.0, -mv.z));
        gl_Position = projectionMatrix * mv;
      }
    `,
    fragmentShader: `
      precision highp float; varying vec3 vColor;
      void main() {
        float d = length(gl_PointCoord - 0.5);
        if (d > 0.5) discard;
        gl_FragColor = vec4(vColor, pow(1.0 - d * 2.0, 2.4) * 0.28);
      }
    `,
  });
  const particles = new THREE.Points(pGeo, pMat);
  scene.add(particles);

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

      const baseZ = window.innerWidth < 720 ? 15.2 : 12.8;
      camera.position.x = mouse.x * 1.0;
      camera.position.y = mouse.y * 0.55 - scrollProgress * 0.6;
      camera.position.z = baseZ + scrollProgress * 2.2;
      camera.lookAt(mouse.x * 0.16, mouse.y * 0.1 - scrollProgress * 0.4, 0);

      wallGroup.rotation.y = -mouse.x * 0.055;
      wallGroup.rotation.x = mouse.y * 0.04 + scrollProgress * 0.12;

      cursorProbe.position.x += (mouse.x * 7.2 - cursorProbe.position.x) * 0.12;
      cursorProbe.position.y += (mouse.y * 4.2 - cursorProbe.position.y) * 0.12;
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
          if (hoveredMesh && hoveredMesh.userData.game) updateHudFocus(hoveredMesh.userData.game);
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
        tile.mesh.position.z = curveZ + tile.hover * 0.72 + prox * 0.14 + waveZ;
        tile.mesh.rotation.y = -tile.x * 0.056 + waveZ * 0.09 + dragVel * 0.014;
        tile.mesh.rotation.x = waveZ * 0.055;

        const s = 1 + tile.hover * 0.055 + prox * 0.012;
        tile.mesh.scale.set(s, s, 1);

        tile.mat.uniforms.uHover.value = tile.hover;
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
  return { triggerWave };
}

/* ==========================================================================
   2. VISUAL INTERACTIVE SHOWCASE (HARDWARE + OPTICAL SAVE + 3D ERA DECK)
   ========================================================================== */
function initShowcase() {
  /* --- Chapter Tabs --- */
  const scTabs = Array.from(document.querySelectorAll('.sc-tab'));
  const scPanels = Array.from(document.querySelectorAll('.sc-panel'));
  const scNote = document.getElementById('sc-note');

  function syncShowcaseNote() {
    if (!scNote) return;
    const key = activeShowcase === 'device' ? 'sc.n1' : activeShowcase === 'save' ? 'sc.n2' : 'sc.n3';
    scNote.textContent = I18N[currentLang][key];
  }

  scTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      activeShowcase = tab.dataset.sc;
      scTabs.forEach((t) => {
        const on = t === tab;
        t.classList.toggle('is-active', on);
        t.setAttribute('aria-selected', String(on));
      });
      scPanels.forEach((p) => p.classList.toggle('is-active', p.dataset.scPanel === activeShowcase));
      syncShowcaseNote();
      if (activeShowcase === 'save') resizeOpticalCanvas();
    });
  });

  /* --- STAGE 01: Morphing Hardware Device + Ambient Screen Canvas + Draggable Virtual Pads --- */
  const hwShell = document.getElementById('hw-shell');
  const hwScreen = document.getElementById('hw-screen');
  const vnBadge = document.getElementById('vn-badge');
  const vnPortrait = document.getElementById('vn-portrait');
  const vnSpk = document.getElementById('vn-spk');
  const vnText = document.getElementById('vn-text');
  const vnCtrl = document.getElementById('vn-ctrl');
  const vnFontRange = document.getElementById('vn-font-range');
  const vnFontVal = document.getElementById('vn-font-val');
  const vnCanvas = document.getElementById('hw-scene-canvas');

  const VN_SCENES = [
    {
      slug: 'rance10',
      eng: 'xsystem4',
      zh: ['兰斯', '「拖动屏幕里的虚拟按键试试——放哪儿都能直接按，字号也能随时放大。」'],
      en: ['Rance', '"Try dragging the virtual pads right inside this screen, or scaling the dialogue live."'],
      ja: ['ランス', '「画面内の仮想キーはそのままドラッグ移動できるし、文字サイズも即調整だ。」'],
    },
    {
      slug: 'evenicle2',
      eng: 'xsystem4',
      zh: ['亚历克斯', '「在 Switch、Android 与 iPad 之间切换设备形态，三端原生布局都会自动贴合。」'],
      en: ['Alex', '"Morph between Switch, Android, and iPad below—native rendering adapts cleanly across all three."'],
      ja: ['アレックス', '「下のボタンで Switch・Android・iPad を切り替えると、各機種に最適な画面比率へ変形します。」'],
    },
    {
      slug: 'kichikuou',
      eng: 'xsystem35',
      zh: ['鬼畜王兰斯', '「早期 System 3.5 作品同样原生运行，内置 MIDI 合成与虚拟 CD 音轨。」'],
      en: ['Kichikuou', '"Classic System 3.5 titles run natively too, complete with built-in MIDI and virtual CD audio."'],
      ja: ['鬼畜王ランス', '「初期の System 3.5 名作も MIDI シンセと仮想 CD 音源付きでネイティブ動作する。」'],
    },
  ];

  let vnIdx = 0;
  let hwMode = 'switch';
  let typeTimer = null;
  let skipTimer = null;

  function updateVnBadge() {
    if (!vnBadge) return;
    const dev = hwMode === 'switch' ? 'SWITCH' : hwMode === 'ipad' ? 'iPadOS' : 'ANDROID';
    vnBadge.textContent = `${dev} · ${VN_SCENES[vnIdx].eng}`;
  }

  function playVnScene() {
    const s = VN_SCENES[vnIdx];
    const [spk, line] = s[currentLang] || s.zh;
    if (vnPortrait) vnPortrait.src = `assets/games/${s.slug}.webp`;
    if (vnSpk) vnSpk.textContent = spk;
    updateVnBadge();

    if (typeTimer) clearInterval(typeTimer);
    if (!vnText) return;
    if (REDUCED_MOTION) {
      vnText.textContent = line;
      return;
    }
    let pos = 0;
    vnText.textContent = '';
    typeTimer = setInterval(() => {
      pos += 2;
      vnText.textContent = line.slice(0, pos);
      if (pos >= line.length) {
        clearInterval(typeTimer);
        typeTimer = null;
      }
    }, 22);
  }

  document.querySelectorAll('[data-hw-btn]').forEach((btn) => {
    btn.addEventListener('click', () => {
      hwMode = btn.dataset.hwBtn;
      document.querySelectorAll('[data-hw-btn]').forEach((b) => b.classList.toggle('is-active', b === btn));
      if (hwShell) hwShell.dataset.hw = hwMode;
      updateVnBadge();
    });
  });

  document.querySelectorAll('[data-vpad-mode]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const hide = btn.dataset.vpadMode === 'hide';
      document.querySelectorAll('[data-vpad-mode]').forEach((b) => b.classList.toggle('is-active', b === btn));
      if (hwScreen) hwScreen.classList.toggle('is-pad-hidden', hide);
    });
  });

  if (vnFontRange && vnFontVal && vnText) {
    const syncF = () => {
      const v = Number(vnFontRange.value);
      vnFontVal.textContent = `${v}%`;
      vnText.style.fontSize = `${(14 * v / 100).toFixed(1)}px`;
    };
    vnFontRange.addEventListener('input', syncF);
    syncF();
  }

  const nextGameBtn = document.getElementById('vn-cycle-game');
  if (nextGameBtn) {
    nextGameBtn.addEventListener('click', () => {
      vnIdx = (vnIdx + 1) % VN_SCENES.length;
      playVnScene();
    });
  }

  if (vnCtrl) {
    vnCtrl.addEventListener('click', () => {
      const on = vnCtrl.classList.toggle('is-on');
      if (skipTimer) { clearInterval(skipTimer); skipTimer = null; }
      if (on) {
        skipTimer = setInterval(() => {
          vnIdx = (vnIdx + 1) % VN_SCENES.length;
          playVnScene();
        }, 1250);
      }
    });
  }

  // Real drag-anywhere virtual pad editor inside the hardware screen!
  document.querySelectorAll('[data-drag-pad]').forEach((pad) => {
    let activeDrag = false;
    let moved = 0;
    let ox = 0, oy = 0;

    pad.addEventListener('pointerdown', (e) => {
      activeDrag = true;
      moved = 0;
      const pr = pad.getBoundingClientRect();
      ox = e.clientX - pr.left;
      oy = e.clientY - pr.top;
      pad.setPointerCapture(e.pointerId);
      pad.classList.add('is-dragging');
      if (hwScreen) hwScreen.classList.add('is-editing');
    });

    pad.addEventListener('pointermove', (e) => {
      if (!activeDrag || !hwScreen) return;
      const sr = hwScreen.getBoundingClientRect();
      let nx = e.clientX - sr.left - ox;
      let ny = e.clientY - sr.top - oy;
      moved += Math.abs(e.movementX) + Math.abs(e.movementY);
      nx = Math.max(8, Math.min(sr.width - pad.offsetWidth - 8, nx));
      ny = Math.max(44, Math.min(sr.height - pad.offsetHeight - 88, ny));
      // 10px subtle magnetic snap
      nx = Math.round(nx / 10) * 10;
      ny = Math.round(ny / 10) * 10;
      pad.style.left = `${nx}px`;
      pad.style.right = 'auto';
      pad.style.top = `${ny}px`;
    });

    const endDrag = (e) => {
      if (!activeDrag) return;
      activeDrag = false;
      pad.classList.remove('is-dragging');
      if (hwScreen) hwScreen.classList.remove('is-editing');
      // If user tapped a pad button without dragging, trigger action!
      if (moved < 6) {
        const btn = e.target.closest('[data-vn-act]');
        if (btn) {
          const act = btn.dataset.vnAct;
          if (act === 'next' || act === 'skip') { vnIdx = (vnIdx + 1) % VN_SCENES.length; playVnScene(); }
          else if (act === 'prev') { vnIdx = (vnIdx - 1 + VN_SCENES.length) % VN_SCENES.length; playVnScene(); }
          else if (act === 'shot') showToast('PNG Screenshot Saved');
          else if (act === 'menu') showToast('Right-Click Menu');
        }
      }
    };
    pad.addEventListener('pointerup', endDrag);
    pad.addEventListener('pointercancel', endDrag);
  });

  // Ambient 60fps subtle light field inside the hardware screen
  if (vnCanvas && !REDUCED_MOTION) {
    const ctx = vnCanvas.getContext('2d');
    const dots = Array.from({ length: 24 }, (_, i) => ({
      x: Math.random(),
      y: Math.random(),
      r: 1 + (i % 3) * 0.8,
      vy: 0.04 + Math.random() * 0.08,
      c: i % 2 === 0 ? '56, 189, 248' : '225, 75, 134',
    }));

    function drawHwBg() {
      if (activeShowcase === 'device' && ctx && hwScreen) {
        const r = hwScreen.getBoundingClientRect();
        const w = Math.max(200, Math.round(r.width));
        const h = Math.max(140, Math.round(r.height));
        if (vnCanvas.width !== w || vnCanvas.height !== h) {
          vnCanvas.width = w;
          vnCanvas.height = h;
        }
        ctx.clearRect(0, 0, w, h);
        const g = ctx.createRadialGradient(w * 0.5, h * 0.42, 10, w * 0.5, h * 0.42, w * 0.6);
        g.addColorStop(0, 'rgba(56, 189, 248, 0.14)');
        g.addColorStop(0.55, 'rgba(225, 75, 134, 0.06)');
        g.addColorStop(1, 'transparent');
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, w, h);

        for (const d of dots) {
          d.y -= d.vy * 0.016;
          if (d.y < -0.05) { d.y = 1.05; d.x = Math.random(); }
          ctx.fillStyle = `rgba(${d.c}, 0.45)`;
          ctx.beginPath();
          ctx.arc(d.x * w, d.y * h, d.r, 0, Math.PI * 2);
          ctx.fill();
        }
      }
      requestAnimationFrame(drawHwBg);
    }
    requestAnimationFrame(drawHwBg);
  }
  playVnScene();

  /* --- STAGE 02: 60fps Optical Fiber Save Pipeline Canvas --- */
  const SAVE_DATA = {
    rance10: {
      ain: 'Rance10.ain',
      lTag: 'HANDHELD · X4SS',
      rTag: 'WINDOWS · GD\\x01\\x01',
      lFile: 'saves/rance10/0001.save',
      rFile: 'PC_SaveData/0001.save',
      lHex: '58 34 53 53 00 1A F2 09',
      rHex: '47 44 01 01 00 1A F2 09',
    },
    evenicle2: {
      ain: 'Evenicle2.ain',
      lTag: 'HANDHELD · GSAVE',
      rTag: 'WINDOWS · GSAVE v8',
      lFile: 'saves/evenicle2/0004.gsave',
      rFile: 'PC_SaveData/0004.gsave',
      lHex: '47 53 56 08 40 00 A2 11',
      rHex: '47 53 56 08 FF 01 A2 11',
    },
    escalayer_reboot: {
      ain: 'EscalayerR.ain',
      lTag: 'HANDHELD · <memory>',
      rTag: 'WINDOWS · RSM RAW',
      lFile: 'saves/escalayer_r/Save.rsm',
      rFile: 'PC_SaveData/Save.rsm',
      lHex: '52 53 4D 06 7C E9 10 4A',
      rHex: '52 53 4D 00 00 14 00 00',
    },
  };

  let optGame = 'rance10';
  let optDir = 1; // 1 = Handheld -> PC, -1 = PC -> Handheld
  let optPulse = 0;

  function renderOptLabels() {
    const d = SAVE_DATA[optGame];
    const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
    set('opt-left-tag', d.lTag);
    set('opt-left-file', d.lFile);
    set('opt-left-hex', d.lHex);
    set('opt-right-tag', d.rTag);
    set('opt-right-file', d.rFile);
    set('opt-right-hex', d.rHex);
    set('opt-ain', d.ain);
    set('opt-dir-label', I18N[currentLang][optDir > 0 ? 'sc.toPc' : 'sc.toMob']);
  }

  document.querySelectorAll('[data-opt-game]').forEach((btn) => {
    btn.addEventListener('click', () => {
      optGame = btn.dataset.optGame;
      optPulse = 1;
      document.querySelectorAll('[data-opt-game]').forEach((b) => b.classList.toggle('is-active', b === btn));
      renderOptLabels();
    });
  });

  const flipBtn = document.getElementById('opt-flip-btn');
  if (flipBtn) {
    flipBtn.addEventListener('click', () => {
      optDir *= -1;
      optPulse = 1;
      renderOptLabels();
    });
  }
  renderOptLabels();

  const fiberCanvas = document.getElementById('save-fiber-canvas');
  let fW = 800, fH = 320;
  function resizeOpticalCanvas() {
    if (!fiberCanvas) return;
    const r = fiberCanvas.parentElement.getBoundingClientRect();
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    fW = Math.max(280, Math.round(r.width));
    fH = Math.max(240, Math.round(r.height));
    fiberCanvas.width = Math.round(fW * dpr);
    fiberCanvas.height = Math.round(fH * dpr);
    const ctx = fiberCanvas.getContext('2d');
    if (ctx) ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  window.addEventListener('resize', resizeOpticalCanvas, { passive: true });

  if (fiberCanvas) {
    const fCtx = fiberCanvas.getContext('2d');
    const packets = Array.from({ length: 28 }, (_, i) => ({
      t: i / 28,
      lane: (i % 5) - 2,
      speed: 0.24 + (i % 3) * 0.08,
    }));

    function drawFibers() {
      if (activeShowcase === 'save' && fCtx) {
        if (fiberCanvas.width === 0) resizeOpticalCanvas();
        optPulse *= 0.93;
        fCtx.clearRect(0, 0, fW, fH);

        const x0 = fW * 0.14;
        const x1 = fW * 0.86;
        const cy = fH * 0.5;

        // Draw 5 curved optical glass fiber tracks
        for (let l = -2; l <= 2; l++) {
          const bend = l * 28;
          fCtx.strokeStyle = l === 0 ? 'rgba(255, 255, 255, 0.14)' : 'rgba(255, 255, 255, 0.06)';
          fCtx.lineWidth = l === 0 ? 1.4 : 1;
          fCtx.beginPath();
          fCtx.moveTo(x0, cy + bend * 0.35);
          fCtx.quadraticCurveTo(fW * 0.5, cy + bend, x1, cy + bend * 0.35);
          fCtx.stroke();
        }

        // Expanding refractive pulse ring from center .ain prism
        if (optPulse > 0.02) {
          fCtx.strokeStyle = `rgba(56, 189, 248, ${(optPulse * 0.55).toFixed(3)})`;
          fCtx.lineWidth = 1.5;
          fCtx.beginPath();
          fCtx.arc(fW * 0.5, cy, 42 + (1 - optPulse) * 140, 0, Math.PI * 2);
          fCtx.stroke();
        }

        // Animated luminous data packets along fibers
        for (const p of packets) {
          if (!REDUCED_MOTION) {
            p.t += p.speed * 0.016 * optDir;
            if (p.t > 1) p.t -= 1;
            if (p.t < 0) p.t += 1;
          }
          const t = p.t;
          const bend = p.lane * 28;
          const px = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * (fW * 0.5) + t * t * x1;
          const py = (1 - t) * (1 - t) * (cy + bend * 0.35) + 2 * (1 - t) * t * (cy + bend) + t * t * (cy + bend * 0.35);
          const rgb = t < 0.5 ? '225, 75, 134' : '56, 189, 248';
          const grad = fCtx.createRadialGradient(px, py, 0, px, py, 10);
          grad.addColorStop(0, `rgba(${rgb}, 0.95)`);
          grad.addColorStop(1, 'transparent');
          fCtx.fillStyle = grad;
          fCtx.beginPath();
          fCtx.arc(px, py, 10, 0, Math.PI * 2);
          fCtx.fill();
        }
      }
      if (!REDUCED_MOTION) requestAnimationFrame(drawFibers);
    }
    requestAnimationFrame(drawFibers);
  }

  /* --- STAGE 03: 3D Fanned Era Artwork Deck --- */
  const ERAS = {
    sys3: {
      kernel: 'system3-sdl2',
      years: '1989 — 1995',
      covers: ['rance01', 'rance02', 'toushin2', 'dalk', 'rance03'],
      zh: ['System 1–3 早期经典内核', '面向采用早期 DAT 资源结构的作品，还原索引调色板渲染，提供原生触控与独立存档管理。', ['ADISK.DAT / ACG.DAT', '.ZIP 直读', '原生触控', '独立存档']],
      en: ['Early System 1–3 Kernel', 'Faithfully renders 1989–1995 DAT-archive releases with direct native touch and isolated saves.', ['ADISK.DAT / ACG.DAT', 'ZIP Import', 'Native Touch', 'Scoped Saves']],
      ja: ['初期 System 1–3 コア', '1989〜1995 年の DAT 形式作品をネイティブ描画。タッチ操作と作品別セーブに対応。', ['ADISK.DAT / ACG.DAT', 'ZIP 直接展開', 'タッチ操作', '分離セーブ']],
    },
    sys35: {
      kernel: 'xsystem35',
      years: '1996 — 2003',
      covers: ['kichikuou', 'daiakuji', 'mamatoto', 'toushin2', 'yorugakuru'],
      zh: ['System 3.5–3.9 黄金期内核', '完整实现 ALD/PMS/QNT 图像、Shift-JIS/GBK/UTF-8 排版、内置 MIDI 合成器与虚拟 CD 音轨。', ['System39.ain / *SA.ALD', '原生 MIDI 合成', '虚拟 CD 音轨', 'ISO 多碟合并']],
      en: ['System 3.5–3.9 Golden-Era Kernel', 'Full ALD/PMS/QNT graphics, multi-encoding CJK text, built-in MIDI synthesis, and virtual CDDA.', ['System39.ain / *SA.ALD', 'Native MIDI', 'Virtual CDDA', 'Multi-Disc ISO']],
      ja: ['System 3.5–3.9 黄金期コア', 'ALD/PMS/QNT 描画、多文字コード、内蔵 MIDI シンセ、playlist.txt 仮想 CD 音源に対応。', ['System39.ain / *SA.ALD', 'ネイティブ MIDI', '仮想 CD 音源', '複数ディスク統合']],
    },
    sys4: {
      kernel: 'xsystem4',
      years: '2002 — PRESENT',
      covers: ['rance10', 'evenicle2', 'sengokurance', 'oyakorankan', 'escalayer_reboot'],
      zh: ['System 4.x 全世代原生内核', '覆盖 AIN v0–v14.1、PartsEngine 字体重排、SealEngine 3D、FLAT v15、CHS.TBL 汉化映射及无损 MOD 与入口切换。', ['.ZIP/.RAR/.7Z/.ISO/.MDF', 'CHS.TBL / UIF', '原子备份 MOD', 'AliceStart.CHS']],
      en: ['Full-Generation System 4.x Kernel', 'Spans AIN v0–v14.1, PartsEngine scaling, SealEngine 3D, FLAT v15, CHS.TBL remaps, atomic MODs & boot entries.', ['.ZIP/.RAR/.7Z/.ISO/.MDF', 'CHS.TBL / UIF', 'Atomic MODs', 'AliceStart.CHS']],
      ja: ['System 4.x 全世代コア', 'AIN v0–v14.1・PartsEngine・SealEngine 3D・FLAT v15・CHS.TBL 置換・非破壊 MOD・起動切替に対応。', ['.ZIP/.RAR/.7Z/.ISO/.MDF', 'CHS.TBL / UIF', '非破壊 MOD', 'AliceStart.CHS']],
    },
  };

  let curEra = 'sys4';
  function renderEra() {
    const d = ERAS[curEra];
    const [title, desc, specs] = d[currentLang] || d.zh;
    const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
    set('era-kernel', d.kernel);
    set('era-years', d.years);
    set('era-title', title);
    set('era-desc', desc);

    const spEl = document.getElementById('era-specs');
    if (spEl) spEl.innerHTML = specs.map((s) => `<span>${s}</span>`).join('');

    const deck = document.getElementById('era-deck');
    if (deck) {
      const layout = [
        { tx: -124, ty: 18, tz: -40, rot: -14, z: 1 },
        { tx: -64, ty: 5, tz: -16, rot: -7, z: 2 },
        { tx: 0, ty: -6, tz: 18, rot: 0, z: 5 },
        { tx: 64, ty: 5, tz: -16, rot: 7, z: 2 },
        { tx: 124, ty: 18, tz: -40, rot: 14, z: 1 },
      ];
      deck.innerHTML = d.covers.map((slug, i) => {
        const L = layout[i];
        return `<div class="era-card" style="--tx:${L.tx}px;--ty:${L.ty}px;--tz:${L.tz}px;--rot:${L.rot}deg;z-index:${L.z}"><img src="assets/games/${slug}.webp" alt="" width="148" height="148" /></div>`;
      }).join('');
    }
  }

  document.querySelectorAll('[data-era]').forEach((btn) => {
    btn.addEventListener('click', () => {
      curEra = btn.dataset.era;
      document.querySelectorAll('[data-era]').forEach((b) => b.classList.toggle('is-active', b === btn));
      renderEra();
    });
  });
  renderEra();

  return {
    refreshLang: () => {
      syncShowcaseNote();
      playVnScene();
      renderOptLabels();
      renderEra();
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
const showcase = initShowcase();
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
  showcase.refreshLang();
}

applyLanguage('zh');

document.querySelectorAll('.lang-btn').forEach((b) => {
  b.addEventListener('click', () => applyLanguage(b.dataset.lang));
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
