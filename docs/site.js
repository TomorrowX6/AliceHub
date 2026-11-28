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

/* 70 Unique Painted AliceSoft Covers (5 Rows × 14 Cols = 100% Zero Duplicates Anywhere!)
   Interleaved so no two entries from the same series sit adjacent or collide across rows. */
const GAMES = [
  // Row 0 (14 unique)
  { slug: 'rance10', eng: 'SYS4', zh: '兰斯 10 -决战-', en: 'Rance X', ja: 'ランス10 -決戦-' },
  { slug: 'evenicle2', eng: 'SYS4', zh: '夏娃年代记 2', en: 'Evenicle 2', ja: 'イブニクル2' },
  { slug: 'kichikuou', eng: 'SYS35', zh: '鬼畜王兰斯', en: 'Kichikuou Rance', ja: '鬼畜王ランス' },
  { slug: 'ixseal', eng: 'SYS4', zh: '超昂神骑 Ixseal', en: 'Beat Valkyrie Ixseal', ja: '超昂神騎エクシール' },
  { slug: 'maman', eng: 'SYS4', zh: '再婚妈妈', en: 'Heartful Maman', ja: 'は～とふるママン' },
  { slug: 'daiteikoku', eng: 'SYS4', zh: '大帝国', en: 'Daiteikoku', ja: '大帝国' },
  { slug: 'pascha3', eng: 'SYS4', zh: '帕斯茶 3', en: 'Pastel Chime 3', ja: 'ぱすてるチャイム3' },
  { slug: 'oyakorankan', eng: 'SYS4', zh: '母娘乱馆', en: 'Oyakorankan', ja: '母娘乱館' },
  { slug: 'toushin3', eng: 'SYS4', zh: '斗神都市 III', en: 'Toushin Toshi III', ja: '闘神都市III' },
  { slug: 'yorugakuru', eng: 'SYS35', zh: '夜来了！', en: 'Yoru ga Kuru!', ja: '夜が来る!' },
  { slug: 'dorapeko', eng: 'SYS4', zh: '多拉佩科', en: 'Dorapeko', ja: 'ドラペコ' },
  { slug: 'onlyyourecross', eng: 'SYS35', zh: 'Only You Re:Cross', en: 'Only You Re:Cross', ja: 'Only You リ・クルス' },
  { slug: 'ikusamiko', eng: 'SYS4', zh: '战巫女', en: 'Ikusamiko', ja: '戦巫女' },
  { slug: 'tsumamigui3', eng: 'SYS4', zh: '妻中蜜 3', en: 'Tsumamigui 3', ja: '妻みぐい3' },

  // Row 1 (14 unique)
  { slug: 'sengokurance', eng: 'SYS4', zh: '战国兰斯', en: 'Sengoku Rance', ja: '戦国ランス' },
  { slug: 'escalayer_reboot', eng: 'SYS4', zh: '超昂天使 R 重制版', en: 'Escalayer Reboot', ja: '超昂天使R' },
  { slug: 'daiakuji', eng: 'SYS35', zh: '大恶司', en: 'Daiakuji', ja: '大悪司' },
  { slug: 'haharanman', eng: 'SYS4', zh: '母烂漫', en: 'Haha Ranman', ja: '母爛漫' },
  { slug: 'bladebriders', eng: 'SYS4', zh: 'Blade Briders', en: 'Blade Briders', ja: 'ブレイドブライダーズ' },
  { slug: 'pastelchime2', eng: 'SYS4', zh: '帕斯茶 Continue', en: 'Pastel Chime Continue', ja: 'ぱすてるチャイムContinue' },
  { slug: 'rance08', eng: 'SYS4', zh: '兰斯 08 Quest Magnum', en: 'Rance Quest', ja: 'ランス・クエスト' },
  { slug: 'mamatoto', eng: 'SYS35', zh: '魔物娘战记', en: 'Mamatoto', ja: 'ママトト' },
  { slug: 'haruka', eng: 'SYS4', zh: '超昂闪忍 遥', en: 'Beat Blades Haruka', ja: '超昂閃忍ハルカ' },
  { slug: 'galzoo', eng: 'SYS4', zh: 'GALZOO 岛', en: 'Galzoo Island', ja: 'GALZOOアイランド' },
  { slug: 'mugenhouyou', eng: 'SYS35', zh: '梦幻泡影', en: 'Mugen Houyou', ja: '夢幻泡影' },
  { slug: 'okusamantr', eng: 'SYS4', zh: '太太的恢复术', en: 'Okusama no Kaifuku Jutsu', ja: '奥さまの回復術' },
  { slug: 'darcrows', eng: 'SYS35', zh: 'DARCROWS', en: 'DARCROWS', ja: 'DARCROWS' },
  { slug: 'evenicle', eng: 'SYS4', zh: '夏娃年代记', en: 'Evenicle', ja: 'イブニクル' },

  // Row 2 (14 unique)
  { slug: 'rance09', eng: 'SYS4', zh: '兰斯 09 赫尔曼革命', en: 'Rance IX', ja: 'ランス9' },
  { slug: 'daibanchou', eng: 'SYS4', zh: '大番长', en: 'Daibanchou', ja: '大番長' },
  { slug: 'atlachnacha', eng: 'SYS35', zh: '阿托拉克纳克亚', en: 'Atlach-Nacha', ja: 'アトラク＝ナクア' },
  { slug: 'momoiroguardian', eng: 'SYS4', zh: '桃色守护者', en: 'Momoiro Guardian', ja: '桃色ガーディアン' },
  { slug: 'toushin2', eng: 'SYS35', zh: '斗神都市 II', en: 'Toushin Toshi II', ja: '闘神都市II' },
  { slug: 'tsumamigui2', eng: 'SYS4', zh: '妻中蜜 2', en: 'Tsumamigui 2', ja: '妻みぐい2' },
  { slug: 'rance03', eng: 'SYS4', zh: '兰斯 03 利萨斯陷落', en: 'Rance 03', ja: 'ランス03' },
  { slug: 'paschacpp', eng: 'SYS4', zh: '帕斯茶 C++', en: 'Pascha C++', ja: 'ぱすちゃC++' },
  { slug: 'harukavsesca', eng: 'SYS4', zh: '超昂大战', en: 'Haruka vs Escalayer', ja: '超昂閃忍ハルカ vs エスカレイヤー' },
  { slug: 'dungeonsdolls', eng: 'SYS4', zh: 'Dungeons & Dolls', en: 'Dungeons & Dolls', ja: 'ダンジョンズ＆ドールズ' },
  { slug: 'diabolique', eng: 'SYS35', zh: 'DiaboLiQuE', en: 'DiaboLiQuE', ja: 'DiaboLiQuE' },
  { slug: 'otomesenki', eng: 'SYS4', zh: '乙女战记', en: 'Otome Senki', ja: '乙女戦記' },
  { slug: 'kaerunyo', eng: 'SYS35', zh: '青蛙女', en: 'Kaerunyo', ja: 'かえるにょ・ぱにょ～ん' },
  { slug: 'shimaima', eng: 'SYS4', zh: '姐妹', en: 'Shimaima', ja: 'しまいま。' },

  // Row 3 (14 unique)
  { slug: 'rance06', eng: 'SYS4', zh: '兰斯 VI 赛斯崩坏', en: 'Rance VI', ja: 'ランスVI' },
  { slug: 'escalayer', eng: 'SYS35', zh: '超昂天使 原版', en: 'Escalayer', ja: '超昂天使エスカレイヤー' },
  { slug: 'oudouyuusha', eng: 'SYS4', zh: '王道勇者', en: 'Oudou Yuusha', ja: '王道勇者' },
  { slug: 'onlyyou', eng: 'SYS35', zh: 'Only You', en: 'Only You', ja: 'Only You' },
  { slug: 'eveniclegaiden', eng: 'SYS4', zh: '夏娃年代记 外传', en: 'Evenicle Gaiden', ja: 'イブニクル外伝' },
  { slug: 'gakuenking', eng: 'SYS35', zh: '学园之王', en: 'Gakuen King', ja: '学園KING' },
  { slug: 'tsumashibori', eng: 'SYS4', zh: '妻榨', en: 'Tsumashibori', ja: '妻しぼり' },
  { slug: 'rance01', eng: 'SYS4', zh: '兰斯 01 寻找小光', en: 'Rance 01', ja: 'ランス01' },
  { slug: 'littlevampire', eng: 'SYS35', zh: '小吸血鬼', en: 'Little Vampire', ja: 'りっとる・ばいんぱいあ' },
  { slug: 'edoranze', eng: 'SYS4', zh: '江户兰斯', en: 'Edoranze', ja: '江戸ランゼ' },
  { slug: 'maidnosusume', eng: 'SYS35', zh: '女仆的推荐', en: 'Maid no Susume', ja: 'メイドさんのすすめ' },
  { slug: 'cielclaire', eng: 'SYS4', zh: 'Ciel Claire', en: 'Ciel Claire', ja: 'シエルクレール' },
  { slug: 'reishiki', eng: 'SYS35', zh: '零式', en: 'Reishiki', ja: '零式' },
  { slug: 'whiphoney', eng: 'SYS4', zh: 'Whip Honey', en: 'Whip Honey', ja: 'ウィップハニー' },

  // Row 4 (14 unique)
  { slug: 'rance5d', eng: 'SYS35', zh: '兰斯 5D', en: 'Rance 5D', ja: 'ランス5D' },
  { slug: 'pastelchime', eng: 'SYS35', zh: '帕斯茶', en: 'Pastel Chime', ja: 'ぱすてるチャイム' },
  { slug: 'dalk', eng: 'SYS35', zh: 'DALK 外传', en: 'DALK', ja: 'DALK' },
  { slug: 'mamanyonyo', eng: 'SYS4', zh: '妈妈女女', en: 'Mamanyonyo', ja: 'ままにょにょ' },
  { slug: 'rance02', eng: 'SYS4', zh: '兰斯 02', en: 'Rance 02', ja: 'ランス02' },
  { slug: 'tsumamigui', eng: 'SYS35', zh: '妻中蜜', en: 'Tsumamigui', ja: '妻みぐい' },
  { slug: 'vanish', eng: 'SYS4', zh: 'Vanish', en: 'Vanish', ja: 'ヴァニッシュ' },
  { slug: 'toushin1', eng: 'SYS3', zh: '斗神都市', en: 'Toushin Toshi', ja: '闘神都市' },
  { slug: 'sukiirono', eng: 'SYS4', zh: '喜欢色', en: 'Sukiirono', ja: '好き色の…' },
  { slug: 'yakata3', eng: 'SYS35', zh: '馆 3', en: 'Yakata 3', ja: '館3' },
  { slug: 'rance04', eng: 'SYS3', zh: '兰斯 IV 教团的遗产', en: 'Rance IV', ja: 'ランスIV' },
  { slug: 'okusamajun', eng: 'SYS4', zh: '太太纯爱', en: 'Okusama Jun', ja: '奥さま純愛' },
  { slug: 'hushaby', eng: 'SYS35', zh: 'Hushaby Baby', en: 'Hushaby Baby', ja: 'ハシャバイベイビー' },
  { slug: 'seeinao', eng: 'SYS35', zh: 'SeeIn 青', en: 'SeeIn AO', ja: 'SeeIn青' },
];

const I18N = {
  zh: {
    'nav.download': '下载',
    'hero.sub': 'AliceSoft System 多平台原生启动器',
    'hero.qq': 'QQ 群',
    'hero.cta': '获取版本',
    'qq.hint': '手机 QQ 扫码加入交流群',
    'qq.copy': '复制群号',
    'dl.title': '下载',
    'dl.android': 'Android 8.0+ · 直接安装',
    'dl.ios': 'iOS 15+ · 自签侧载',
    'dl.switch': 'Homebrew · 解压至 /switch/',
    'dl.qq': 'QQ 交流群',
    'toast.qq': '已复制 QQ 群号：383917628',
  },
  en: {
    'nav.download': 'Download',
    'hero.sub': 'Native multi-platform launcher for AliceSoft System',
    'hero.qq': 'QQ Group',
    'hero.cta': 'Download',
    'qq.hint': 'Scan with QQ mobile to join',
    'qq.copy': 'Copy ID',
    'dl.title': 'DOWNLOAD',
    'dl.android': 'Android 8.0+ · Direct install',
    'dl.ios': 'iOS 15+ · Sideload IPA',
    'dl.switch': 'Homebrew · Extract to /switch/',
    'dl.qq': 'QQ Group',
    'toast.qq': 'Copied QQ Group: 383917628',
  },
  ja: {
    'nav.download': '入手',
    'hero.sub': 'AliceSoft System マルチプラットフォーム実行環境',
    'hero.qq': 'QQ 群',
    'hero.cta': 'ダウンロード',
    'qq.hint': 'QQ アプリでスキャンして参加',
    'qq.copy': '番号コピー',
    'dl.title': 'ダウンロード',
    'dl.android': 'Android 8.0+ · 直接導入',
    'dl.ios': 'iOS 15+ · 署名して導入',
    'dl.switch': 'Homebrew · /switch/ へ展開',
    'dl.qq': 'QQ グループ',
    'toast.qq': 'QQ 番号をコピーしました：383917628',
  },
};

let currentLang = 'zh';
let focusedGame = GAMES[0];
let modalOpen = false;

/* ==========================================================================
   1. THREE.JS INTERACTIVE 3D CURVED COVER WALL (TRUE VIBRANT SRGB)
   ========================================================================== */
function initThreeWall() {
  const canvas = document.getElementById('webgl-stage');
  if (!canvas) return;

  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      alpha: false,
      powerPreference: 'high-performance',
    });
  } catch {
    return;
  }

  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(window.innerWidth, window.innerHeight, false);
  renderer.setClearColor(0x070709, 1);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, window.innerWidth / window.innerHeight, 0.1, 80);
  camera.position.set(0, 0, window.innerWidth < 720 ? 15.2 : 12.8);

  const auroraMat = new THREE.ShaderMaterial({
    depthWrite: false,
    uniforms: {
      uTime: { value: 0 },
      uMouse: { value: new THREE.Vector2(0, 0) },
    },
    vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
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
        vec3 col = vec3(0.022, 0.022, 0.032)
          + vec3(0.88, 0.29, 0.52) * g1 * 0.07
          + vec3(0.22, 0.74, 0.97) * g2 * 0.065;
        gl_FragColor = vec4(col, 1.0);
      }
    `,
  });
  const auroraPlane = new THREE.Mesh(new THREE.PlaneGeometry(62, 38), auroraMat);
  auroraPlane.position.set(0, 0, -14);
  scene.add(auroraPlane);

  const wallGroup = new THREE.Group();
  scene.add(wallGroup);

  const texLoader = new THREE.TextureLoader();
  const tileGeo = new THREE.PlaneGeometry(1.9, 1.9, 1, 1);

  const tileFrag = `
    precision highp float;
    uniform sampler2D uMap;
    uniform float uHover;
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

      vec3 col = texture2D(uMap, vUv).rgb;

      float bright = mix(0.76, 0.97, clamp(uHover + uProx * 0.1, 0.0, 1.0));
      col *= bright;

      float spec = smoothstep(0.64, 0.98, 1.0 - abs(dot(p, normalize(uLightDir)) - 0.08));
      col += vec3(0.92, 0.96, 1.0) * spec * (0.01 + uProx * 0.016 + uHover * 0.03);

      float rim = smoothstep(-0.02, -0.004, d) * mask;
      vec3 rimCol = mix(vec3(1.0), vec3(0.22, 0.74, 0.97), clamp(uHover + uProx * 0.25, 0.0, 1.0));
      col += rimCol * rim * (0.07 + uProx * 0.04 + uHover * 0.22);

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
      // Strictly disjoint 1-to-1 mapping: all 70 wall slots are unique titles (zero collisions ever!)
      const game = GAMES[r * COLS + c];
      const mat = new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: true,
        uniforms: {
          uMap: { value: getTexture(game.slug) },
          uHover: { value: 0 },
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
        gl_FragColor = vec4(vColor, pow(1.0 - d * 2.0, 2.4) * 0.14);
      }
    `,
  });
  const particles = new THREE.Points(pGeo, pMat);
  scene.add(particles);

  const raycaster = new THREE.Raycaster();
  const pointerNdc = new THREE.Vector2(99, 99);
  const mouse = { x: 0, y: 0, tx: 0, ty: 0 };
  let hoveredMesh = null;

  let dragging = false;
  let dragMoved = 0;
  let lastDragX = 0;
  let dragVel = 0;
  const probePos = { x: 0, y: 0 };

  window.addEventListener('pointerdown', (e) => {
    if (modalOpen) return;
    if (e.target.closest('a, button, .stage-bottom, .site-nav, .dl-modal')) return;
    dragging = true;
    dragMoved = 0;
    lastDragX = e.clientX;
    canvas.classList.add('is-dragging');
  });

  window.addEventListener('pointermove', (e) => {
    if (modalOpen) return;
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
      if (hits.length > 0 && hits[0].object.userData.game) {
        updateHudFocus(hits[0].object.userData.game);
      }
    }
  });

  window.addEventListener('pointerleave', () => {
    pointerNdc.set(99, 99);
    hoveredMesh = null;
    dragging = false;
    canvas.classList.remove('is-dragging');
  });

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
    elapsed += REDUCED_MOTION ? 0 : dt;

    mouse.x += (mouse.tx - mouse.x) * 0.055;
    mouse.y += (mouse.ty - mouse.y) * 0.055;
    if (!dragging) dragVel *= 0.93;

    const baseZ = window.innerWidth < 720 ? 15.2 : 12.8;
    camera.position.x = mouse.x * 1.0;
    camera.position.y = mouse.y * 0.55;
    camera.position.z = baseZ;
    camera.lookAt(mouse.x * 0.16, mouse.y * 0.1, 0);

    wallGroup.rotation.y = -mouse.x * 0.055;
    wallGroup.rotation.x = mouse.y * 0.04;

    probePos.x += (mouse.x * 7.2 - probePos.x) * 0.12;
    probePos.y += (mouse.y * 4.2 - probePos.y) * 0.12;

    auroraMat.uniforms.uTime.value = elapsed;
    auroraMat.uniforms.uMouse.value.set(mouse.x, mouse.y);

    rayTick++;
    if (!modalOpen && !dragging && pointerNdc.x < 2 && rayTick % 2 === 0) {
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

      const pDist = Math.hypot(tile.x - probePos.x, tile.yBase - probePos.y);
      const prox = Math.exp(-pDist * pDist * 0.085);
      const curveZ = tile.x * tile.x * 0.032;
      const idleFloat = REDUCED_MOTION ? 0 : Math.sin(elapsed * 0.9 + tile.phase) * 0.045;

      tile.mesh.position.x = tile.x;
      tile.mesh.position.y = tile.yBase + idleFloat;
      tile.mesh.position.z = curveZ + tile.hover * 0.65 + prox * 0.08;
      tile.mesh.rotation.y = -tile.x * 0.056 + dragVel * 0.014;
      tile.mesh.rotation.x = 0;

      const s = 1 + tile.hover * 0.048 + prox * 0.008;
      tile.mesh.scale.set(s, s, 1);

      tile.mat.uniforms.uHover.value = tile.hover;
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
    if (!REDUCED_MOTION) requestAnimationFrame(animate);
  }

  animate(performance.now());
}

/* ==========================================================================
   2. ALICE EMBLEM EYES + BLINK + WINK
   ========================================================================== */
function initAliceEmblem() {
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
      if (modalOpen || !eyeM || !eyeC) return;
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
  });
}

/* ==========================================================================
   3. RELEASE MODAL, HUD, I18N & LIVE GITHUB RELEASE SYNC
   ========================================================================== */
function initModal() {
  const dlModal = document.getElementById('dl-modal');
  const qqModal = document.getElementById('qq-modal');

  const closeAll = () => {
    modalOpen = false;
    if (dlModal) dlModal.setAttribute('hidden', '');
    if (qqModal) qqModal.setAttribute('hidden', '');
  };

  const openModal = (el) => {
    if (!el) return;
    closeAll();
    modalOpen = true;
    el.removeAttribute('hidden');
  };

  document.querySelectorAll('[data-open-dl]').forEach((b) => b.addEventListener('click', () => openModal(dlModal)));
  document.querySelectorAll('[data-close-dl]').forEach((b) => b.addEventListener('click', closeAll));
  document.querySelectorAll('[data-open-qq]').forEach((b) => b.addEventListener('click', () => openModal(qqModal)));
  document.querySelectorAll('[data-close-qq]').forEach((b) => b.addEventListener('click', closeAll));

  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && modalOpen) closeAll();
  });
}

function updateHudFocus(game) {
  focusedGame = game;
  const titleEl = document.getElementById('hud-title');
  const engEl = document.getElementById('hud-eng');
  if (titleEl) titleEl.textContent = game[currentLang] || game.zh;
  if (engEl) engEl.textContent = game.eng === 'SYS3' ? 'system3-sdl2' : game.eng === 'SYS35' ? 'xsystem35' : 'xsystem4';
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
    if (k in dict) el.textContent = dict[k];
  });

  updateHudFocus(focusedGame);
}

/* ==========================================================================
   BOOT
   ========================================================================== */
initThreeWall();
initAliceEmblem();
initModal();
applyReleaseUI();
applyLanguage('zh');
fetchLatestRelease();

document.querySelectorAll('.lang-btn').forEach((b) => {
  b.addEventListener('click', () => applyLanguage(b.dataset.lang));
});

document.querySelectorAll('[data-copy-qq]').forEach((qqBtn) => {
  qqBtn.addEventListener('click', () => {
    const qq = qqBtn.dataset.qq || '383917628';
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(qq).catch(() => {});
    }
    showToast(I18N[currentLang]['toast.qq']);
  });
});
