import React from "react";
import {
  AbsoluteFill,
  Audio,
  continueRender,
  delayRender,
  Easing,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";

/**
 * Промо «Динамический остров для Android» в стиле презентаций Apple, формат TikTok 1080×1920:
 * чёрный фон, крупные заголовки, телефон крупным планом и главы по функциям.
 * Музыка и звуки — public/audio/promo.mp3 (генерируется scripts/make_audio.py, без чужих сэмплов).
 * Заголовки стоят ниже верхней панели TikTok, а важное — выше подписи и кнопок снизу.
 */

const C = {
  green: "#34C759",
  red: "#FF453A",
  orange: "#FF9F0A",
  lavender: "#C6C3F7",
  dim: "rgba(255,255,255,0.55)",
  sub: "#86868B",
};
const GRADIENT = "linear-gradient(90deg,#C6C3F7 0%,#F9A8D4 50%,#93C5FD 100%)";

// Телефон: экран 393 pt шириной
const PHONE_W = 880;
const SCREEN_W = PHONE_W - 44;
const PT = SCREEN_W / 393;

type Shape = { w: number; h: number; r: number };
const S = {
  idle: { w: 126, h: 37, r: 18.5 },
  compact: { w: 212, h: 43, r: 21.5 },
  pill: { w: 224, h: 43, r: 21.5 },
  wide: { w: 371, h: 65, r: 32.5 },
  player: { w: 371, h: 222, r: 44 },
  call: { w: 371, h: 90, r: 42 },
  message: { w: 371, h: 104, r: 36 },
  timer: { w: 371, h: 104, r: 44 },
  torch: { w: 200, h: 224, r: 44 },
} satisfies Record<string, Shape>;

// ——— Шрифт ———
const useInter = () => {
  const [handle] = React.useState(() => delayRender("Шрифт Inter"));
  React.useEffect(() => {
    const faces = [
      new FontFace("Inter", `url(${staticFile("fonts/Inter-Regular.ttf")})`, { weight: "400" }),
      new FontFace("Inter", `url(${staticFile("fonts/Inter-SemiBold.ttf")})`, { weight: "600" }),
      new FontFace("Inter", `url(${staticFile("fonts/Inter-Bold.ttf")})`, { weight: "700" }),
    ];
    Promise.all(faces.map((f) => f.load()))
      .then((loaded) => {
        loaded.forEach((f) => document.fonts.add(f));
        continueRender(handle);
      })
      .catch(() => continueRender(handle));
  }, [handle]);
};

const T: React.CSSProperties = { fontFamily: "Inter", color: "white", fontWeight: 600, whiteSpace: "nowrap" };
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const ease = Easing.bezier(0.25, 0.1, 0.25, 1);

// ——— Мелкие элементы ———
const Icon: React.FC<{ name: string; size: number; color: string; rotate?: number }> = ({ name, size, color, rotate = 0 }) => (
  <div
    style={{
      width: size,
      height: size,
      backgroundColor: color,
      WebkitMaskImage: `url(${staticFile(`icons/${name}.svg`)})`,
      WebkitMaskSize: "contain",
      WebkitMaskRepeat: "no-repeat",
      WebkitMaskPosition: "center",
      transform: `rotate(${rotate}deg)`,
      flexShrink: 0,
    }}
  />
);

const Wave: React.FC<{ color: string; t: number; bars?: number; w: number; h: number }> = ({ color, t, bars = 5, w, h }) => {
  const bw = w / (bars * 2 - 1);
  return (
    <div style={{ display: "flex", alignItems: "center", gap: bw, width: w, height: h }}>
      {Array.from({ length: bars }).map((_, i) => {
        const k = 0.25 + 0.75 * Math.abs(Math.sin(t / (5 + i * 1.3) + i * 1.7));
        return <div key={i} style={{ width: bw, height: h * k, borderRadius: bw, background: color }} />;
      })}
    </div>
  );
};

const Battery: React.FC<{ level: number; color: string; w: number; h: number }> = ({ level, color, w, h }) => (
  <div style={{ display: "flex", alignItems: "center", gap: 2 }}>
    <div style={{ width: w, height: h, borderRadius: h * 0.3, background: `${color}4D`, padding: h * 0.12, boxSizing: "border-box" }}>
      <div style={{ width: `${level}%`, height: "100%", borderRadius: h * 0.2, background: color }} />
    </div>
    <div style={{ width: h * 0.18, height: h * 0.4, borderRadius: 4, background: `${color}80` }} />
  </div>
);

const Art: React.FC<{ size: number }> = ({ size }) => (
  <div style={{ width: size, height: size, borderRadius: size * 0.24, background: "linear-gradient(135deg,#FF8AD8,#A78BFA 50%,#38BDF8)", flexShrink: 0 }} />
);

const Round: React.FC<{ size: number; bg: string; children: React.ReactNode }> = ({ size, bg, children }) => (
  <div style={{ width: size, height: size, borderRadius: "50%", background: bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
    {children}
  </div>
);

/** Свёрнутая капсула: значок у левого края, текст у правого, камера посередине */
const Compact: React.FC<{ left: React.ReactNode; right: React.ReactNode }> = ({ left, right }) => (
  <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "space-between", padding: `0 ${13 * PT}px` }}>
    {left}
    {right}
  </div>
);

const mmss = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;

// ——— Состояния острова (кадры совпадают со звуками в scripts/make_audio.py) ———
type IslandState = { from: number; shape: Shape; content: (t: number) => React.ReactNode };

export const ISLAND: IslandState[] = [
  { from: 0, shape: S.idle, content: () => null },
  {
    from: 205,
    shape: S.wide,
    content: (t) => (
      <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", padding: `0 ${27 * PT}px`, gap: 10 * PT }}>
        <Icon name="flash" size={26 * PT} color={C.green} />
        <span style={{ ...T, fontSize: 17 * PT, flex: 1 }}>Зарядка</span>
        <span style={{ ...T, fontSize: 17 * PT, color: C.green }}>75%</span>
        <Battery level={interpolate(t, [6, 34], [0, 75], { ...clamp, easing: ease })} color={C.green} w={32 * PT} h={15 * PT} />
      </div>
    ),
  },
  {
    from: 275,
    shape: S.compact,
    content: () => (
      <Compact
        left={<Icon name="flash" size={21 * PT} color={C.green} />}
        right={
          <div style={{ display: "flex", alignItems: "center", gap: 5 * PT }}>
            <span style={{ ...T, fontSize: 15 * PT, color: C.green }}>75%</span>
            <Battery level={75} color={C.green} w={26 * PT} h={13 * PT} />
          </div>
        }
      />
    ),
  },
  { from: 355, shape: S.compact, content: (t) => <Compact left={<Art size={26 * PT} />} right={<Wave color={C.lavender} t={t} w={27 * PT} h={19 * PT} />} /> },
  {
    from: 420,
    shape: S.player,
    content: (t) => {
      const lines = ["Пока горит свет в окне", "я пою эту строку прямо сейчас", "и остров поёт вместе со мной"];
      const idx = Math.min(2, Math.floor(t / 34));
      return (
        <div style={{ position: "absolute", inset: 0, padding: `${33 * PT}px ${22 * PT}px 0` }}>
          <div style={{ display: "flex", alignItems: "center", gap: 14 * PT }}>
            <Art size={56 * PT} />
            <div style={{ flex: 1 }}>
              <div style={{ ...T, fontSize: 17 * PT }}>Название трека</div>
              <div style={{ ...T, fontWeight: 400, fontSize: 14 * PT, color: C.dim }}>Исполнитель</div>
            </div>
            <Wave color={C.lavender} t={t} bars={6} w={30 * PT} h={20 * PT} />
          </div>
          <div style={{ marginTop: 12 * PT, height: 52 * PT }}>
            <div style={{ ...T, fontWeight: 400, fontSize: 12 * PT, color: "rgba(255,255,255,0.3)" }}>{lines[idx - 1] ?? " "}</div>
            <div style={{ ...T, fontWeight: 700, fontSize: 17 * PT }}>{lines[idx]}</div>
            <div style={{ ...T, fontWeight: 400, fontSize: 12 * PT, color: "rgba(198,195,247,0.55)" }}>{lines[idx + 1] ?? " "}</div>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 * PT, marginTop: 8 * PT }}>
            <span style={{ ...T, fontWeight: 400, fontSize: 12 * PT, color: C.dim }}>2:50</span>
            <div style={{ flex: 1, height: 6 * PT, borderRadius: 3 * PT, background: "rgba(255,255,255,0.2)" }}>
              <div style={{ width: `${interpolate(t, [0, 110], [70, 80])}%`, height: "100%", borderRadius: 3 * PT, background: "white" }} />
            </div>
            <span style={{ ...T, fontWeight: 400, fontSize: 12 * PT, color: C.dim }}>-0:51</span>
          </div>
          <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: 34 * PT, marginTop: 12 * PT }}>
            <Icon name="play-back" size={28 * PT} color="white" />
            <Icon name="pause" size={34 * PT} color="white" />
            <Icon name="play-forward" size={28 * PT} color="white" />
          </div>
        </div>
      );
    },
  },
  {
    from: 545,
    shape: S.call,
    content: () => (
      <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", padding: `${23 * PT}px ${14 * PT}px ${14 * PT}px`, gap: 12 * PT }}>
        <Round size={52 * PT} bg="linear-gradient(135deg,#FDBA74,#F472B6)">
          <span style={{ ...T, fontSize: 21 * PT }}>А</span>
        </Round>
        <div style={{ flex: 1 }}>
          <div style={{ ...T, fontWeight: 400, fontSize: 13 * PT, color: C.dim }}>Входящий вызов</div>
          <div style={{ ...T, fontSize: 18 * PT }}>Анна</div>
        </div>
        <Round size={52 * PT} bg={C.red}>
          <Icon name="call" size={23 * PT} color="white" rotate={135} />
        </Round>
        <Round size={52 * PT} bg={C.green}>
          <Icon name="call" size={23 * PT} color="white" />
        </Round>
      </div>
    ),
  },
  {
    from: 665,
    shape: S.message,
    content: () => (
      <div style={{ position: "absolute", inset: 0, padding: `${6 * PT}px ${16 * PT}px 0 ${12 * PT}px` }}>
        <div style={{ display: "flex", alignItems: "center", height: 43 * PT, gap: 9 * PT }}>
          <Round size={36 * PT} bg="linear-gradient(135deg,#60A5FA,#A78BFA)">
            <span style={{ ...T, fontSize: 15 * PT }}>М</span>
          </Round>
          <span style={{ ...T, fontSize: 15 * PT, flex: 1 }}>Мама</span>
          <span style={{ ...T, fontWeight: 400, fontSize: 12 * PT, color: "rgba(255,255,255,0.45)" }}>сейчас</span>
        </div>
        <div style={{ ...T, fontWeight: 400, fontSize: 15 * PT, color: "rgba(255,255,255,0.82)", paddingLeft: 4 * PT, marginTop: 4 * PT }}>
          Ты где? Ужин уже на столе 🍝
        </div>
      </div>
    ),
  },
  {
    from: 790,
    shape: S.pill,
    content: (t) => (
      <Compact
        left={<Icon name="notifications-off" size={21 * PT} color={C.red} rotate={t < 45 ? 22 * Math.exp(-t / 9) * Math.sin(t * 1.3) : 0} />}
        right={<span style={{ ...T, fontSize: 16 * PT, color: C.red }}>Без звука</span>}
      />
    ),
  },
  {
    from: 900,
    shape: S.compact,
    content: (t) => (
      <Compact
        left={<Icon name="timer" size={21 * PT} color={C.orange} />}
        right={<span style={{ ...T, fontSize: 17 * PT, color: C.orange, fontVariantNumeric: "tabular-nums" }}>{mmss(299 - Math.floor(t / 30))}</span>}
      />
    ),
  },
  {
    from: 960,
    shape: S.timer,
    content: (t) => (
      <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", padding: `${33 * PT}px ${22 * PT}px ${20 * PT}px`, gap: 12 * PT }}>
        <Round size={50 * PT} bg="rgba(255,159,10,0.25)">
          <Icon name="pause" size={24 * PT} color={C.orange} />
        </Round>
        <Round size={50 * PT} bg="rgba(255,255,255,0.2)">
          <span style={{ ...T, fontSize: 24 * PT, fontWeight: 400 }}>×</span>
        </Round>
        <div style={{ flex: 1 }} />
        <span style={{ ...T, fontWeight: 400, fontSize: 15 * PT, color: C.orange, paddingTop: 14 * PT }}>Таймер</span>
        <span style={{ ...T, fontWeight: 400, fontSize: 44 * PT, color: C.orange, fontVariantNumeric: "tabular-nums" }}>{mmss(297 - Math.floor(t / 30))}</span>
      </div>
    ),
  },
  {
    from: 1080,
    shape: S.torch,
    content: (t) => {
      const level = interpolate(t, [20, 60, 90], [0.55, 1, 0.8], { ...clamp, easing: ease });
      const glow = 0.92 + 0.08 * Math.sin(t / 8);
      return (
        <div style={{ position: "absolute", inset: 0 }}>
          <div
            style={{
              position: "absolute",
              left: 0,
              right: 0,
              top: 20 * PT,
              height: 125 * PT,
              clipPath: `polygon(50% 100%, ${50 - 36 * level}% 0%, ${50 + 36 * level}% 0%)`,
              background: `radial-gradient(70% 100% at 50% 100%, rgba(255,255,255,${0.95 * level * glow}) 0%, rgba(217,212,255,${0.5 * level}) 40%, rgba(142,135,255,0) 100%)`,
            }}
          />
          <div
            style={{
              position: "absolute",
              left: "50%",
              top: 143 * PT,
              width: 90 * PT * level * glow,
              height: 90 * PT * level * glow,
              transform: "translate(-50%,-50%)",
              borderRadius: "50%",
              background: `radial-gradient(circle, rgba(255,255,255,${0.9 * level}) 0%, rgba(185,180,255,0.3) 45%, rgba(0,0,0,0) 70%)`,
            }}
          />
          <div style={{ position: "absolute", left: "50%", top: 143 * PT, transform: "translateX(-50%)", display: "flex", flexDirection: "column", alignItems: "center" }}>
            <div style={{ width: 30 * PT, height: 14 * PT, background: "white", clipPath: "polygon(0 0,100% 0,78% 100%,22% 100%)" }} />
            <div style={{ width: 16 * PT, height: 36 * PT, background: "white", borderRadius: 5 * PT, marginTop: 2 * PT }} />
          </div>
          <div style={{ position: "absolute", right: 26 * PT, top: 40 * PT, ...T, fontSize: 14 * PT, opacity: 0.7 }}>☼</div>
        </div>
      );
    },
  },
  { from: 1205, shape: S.idle, content: () => null },
];

const islandAt = (frame: number) => {
  let i = 0;
  ISLAND.forEach((s, k) => {
    if (frame >= s.from) i = k;
  });
  return i;
};

const Island: React.FC<{ frame: number; fps: number }> = ({ frame, fps }) => {
  const i = islandAt(frame);
  const st = ISLAND[i];
  const prev = ISLAND[Math.max(0, i - 1)].shape;
  const t = frame - st.from;
  // Пружина с отскоком — как стиль «Как Фрукт» в приложении
  const p = i === 0 ? 1 : spring({ frame: t, fps, config: { damping: 12, stiffness: 150, mass: 0.9 } });
  const mix = (a: number, b: number) => a + (b - a) * p;
  const w = mix(prev.w, st.shape.w) * PT;
  const h = mix(prev.h, st.shape.h) * PT;
  const r = mix(prev.r, st.shape.r) * PT;
  const shake = st.shape === S.pill && t < 30 ? Math.sin(t * 1.4) * 3.2 * PT * Math.exp(-t / 8) : 0;
  const o = interpolate(t, [3, 12], [0, 1], clamp);
  const blur = interpolate(t, [3, 12], [10, 0], clamp);
  return (
    <div style={{ position: "absolute", top: 11 * PT, left: (SCREEN_W - w) / 2 + shake, width: w, height: h, borderRadius: r, background: "#000", overflow: "hidden" }}>
      <div style={{ position: "absolute", top: 18.5 * PT - 6 * PT, left: w / 2 - 6 * PT, width: 12 * PT, height: 12 * PT, borderRadius: "50%", background: "#15151C", zIndex: 2 }} />
      <div style={{ position: "absolute", inset: 0, opacity: o, filter: `blur(${blur}px)` }}>{st.content(t)}</div>
    </div>
  );
};

// ——— Телефон ———
const Phone: React.FC<{ frame: number; fps: number }> = ({ frame, fps }) => {
  const reveal = spring({ frame: frame - 110, fps, config: { damping: 200, stiffness: 60 } });
  const outro = interpolate(frame, [1235, 1290], [0, 1], { ...clamp, easing: ease });
  const y = interpolate(reveal, [0, 1], [700, 0]) + outro * 900;
  const scale = interpolate(reveal, [0, 1], [1.25, 1]) * interpolate(frame, [170, 1230], [1, 1.06]);
  const tilt = interpolate(reveal, [0, 1], [18, 0]);
  return (
    <div
      style={{
        position: "absolute",
        left: (1080 - PHONE_W) / 2,
        top: 760,
        width: PHONE_W,
        height: 1900,
        transform: `translateY(${y}px) perspective(2400px) rotateX(${tilt}deg) scale(${scale})`,
        transformOrigin: "50% 0%",
        opacity: reveal * (1 - outro),
      }}
    >
      <div style={{ position: "absolute", inset: 0, borderRadius: 150, background: "linear-gradient(160deg,#5B5A63,#2A2930 40%,#4A4952)", boxShadow: "0 40px 120px rgba(120,110,255,0.25)" }} />
      <div style={{ position: "absolute", inset: 22, borderRadius: 130, overflow: "hidden", background: "radial-gradient(120% 70% at 50% 0%, #4B4680 0%, #23203A 45%, #0E0D16 100%)" }}>
        <div style={{ position: "absolute", top: 16 * PT, left: 30 * PT, right: 30 * PT, display: "flex", justifyContent: "space-between", alignItems: "center", ...T, fontSize: 16 * PT }}>
          <span>9:41</span>
          <Battery level={80} color="white" w={24 * PT} h={11 * PT} />
        </div>
        <Island frame={frame} fps={fps} />
      </div>
    </div>
  );
};

// ——— Заголовки глав ———
type Chapter = { from: number; to: number; title: string; sub: string };
export const CHAPTERS: Chapter[] = [
  { from: 175, to: 340, title: "Зарядка.", sub: "Широко — когда важно.\nКомпактно — всегда." },
  { from: 345, to: 530, title: "Музыка.", sub: "Любой плеер. Обложка, волна\nи текст песни в такт." },
  { from: 535, to: 650, title: "Звонки.", sub: "Ответить — одним касанием.\nС любой звонилкой." },
  { from: 655, to: 775, title: "Уведомления.", sub: "Сообщения обтекают камеру —\nкак на iPhone." },
  { from: 780, to: 885, title: "Режим звука.", sub: "Остров вздрагивает.\nКолокольчик качается." },
  { from: 890, to: 1065, title: "Таймер.", sub: "Время — всегда на виду.\nПауза в один жест." },
  { from: 1070, to: 1200, title: "Фонарик.", sub: "Свайп — ярче.\nКасание — выключить." },
];

const Headline: React.FC<{ frame: number; c: Chapter }> = ({ frame, c }) => {
  const t = frame - c.from;
  const len = c.to - c.from;
  const inA = interpolate(t, [0, 18], [0, 1], { ...clamp, easing: ease });
  const outA = interpolate(t, [len - 14, len], [1, 0], clamp);
  const subIn = interpolate(t, [10, 30], [0, 1], { ...clamp, easing: ease });
  return (
    <div style={{ position: "absolute", top: 230, left: 0, right: 0, textAlign: "center", opacity: Math.min(inA, outA) }}>
      <div style={{ ...T, fontWeight: 700, fontSize: 124, letterSpacing: -3, filter: `blur(${(1 - inA) * 14}px)`, transform: `translateY(${(1 - inA) * 30}px)` }}>
        {c.title}
      </div>
      <div style={{ ...T, whiteSpace: "pre-line", fontWeight: 600, fontSize: 46, lineHeight: 1.25, color: C.sub, marginTop: 24, opacity: subIn, transform: `translateY(${(1 - subIn) * 20}px)` }}>
        {c.sub}
      </div>
    </div>
  );
};

/** Текст появляется из размытия и снизу, как в роликах Apple */
const Reveal: React.FC<{ frame: number; at: number; children: React.ReactNode; style?: React.CSSProperties }> = ({ frame, at, children, style }) => {
  const k = interpolate(frame - at, [0, 20], [0, 1], { ...clamp, easing: ease });
  return <div style={{ opacity: k, filter: `blur(${(1 - k) * 16}px)`, transform: `translateY(${(1 - k) * 40}px)`, ...style }}>{children}</div>;
};

const GradientText: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <span style={{ background: GRADIENT, WebkitBackgroundClip: "text", backgroundClip: "text", color: "transparent" }}>{children}</span>
);

const Title: React.FC = () => (
  <GradientText>
    Динамический
    <br />
    остров.
  </GradientText>
);

const Intro: React.FC<{ frame: number }> = ({ frame }) => (
  <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity: interpolate(frame, [96, 112], [1, 0], clamp) }}>
    <Reveal frame={frame} at={6} style={{ ...T, fontSize: 64, color: C.sub }}>
      Знакомьтесь.
    </Reveal>
    <Reveal frame={frame} at={30} style={{ ...T, fontSize: 132, fontWeight: 700, letterSpacing: -4, marginTop: 20, textAlign: "center", whiteSpace: "normal", lineHeight: 1.05 }}>
      <Title />
    </Reveal>
    <Reveal frame={frame} at={58} style={{ ...T, fontSize: 64, marginTop: 28 }}>
      Для Android.
    </Reveal>
  </AbsoluteFill>
);

const MORE = ["Текст песен", "Наушники и заряд", "Wi‑Fi и режим полёта", "Загрузки", "Запись экрана", "Секундомер", "Свои цвета и жесты"];
export const MORE_AT = (i: number) => 1310 + i * 12;

const More: React.FC<{ frame: number }> = ({ frame }) => (
  <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity: interpolate(frame, [1440, 1460], [1, 0], clamp) }}>
    <Reveal frame={frame} at={1290} style={{ ...T, fontSize: 112, fontWeight: 700, letterSpacing: -3 }}>
      И ещё.
    </Reveal>
    <div style={{ marginTop: 50, display: "flex", flexDirection: "column", alignItems: "center", gap: 18 }}>
      {MORE.map((s, i) => (
        <Reveal key={s} frame={frame} at={MORE_AT(i)} style={{ ...T, fontSize: 58, color: i % 2 ? "white" : C.lavender }}>
          {s}
        </Reveal>
      ))}
    </div>
  </AbsoluteFill>
);

const Outro: React.FC<{ frame: number }> = ({ frame }) => (
  <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", paddingBottom: 200 }}>
    <Reveal frame={frame} at={1468} style={{ ...T, fontSize: 132, fontWeight: 700, letterSpacing: -4, textAlign: "center", whiteSpace: "normal", lineHeight: 1.05 }}>
      <Title />
    </Reveal>
    <Reveal frame={frame} at={1490} style={{ ...T, fontSize: 52, color: C.sub, marginTop: 30 }}>
      Бесплатно. Для Android.
    </Reveal>
    <Reveal frame={frame} at={1515} style={{ marginTop: 70 }}>
      <div style={{ ...T, fontSize: 40, color: "#000", background: "white", borderRadius: 999, padding: "26px 56px" }}>Скачать на GitHub</div>
    </Reveal>
    <Reveal frame={frame} at={1530} style={{ ...T, fontWeight: 400, fontSize: 34, color: C.sub, marginTop: 34 }}>
      github.com/Akira9021/Dynamic-Island-
    </Reveal>
  </AbsoluteFill>
);

export const PROMO_FRAMES = 1620;

export const IslandPromo: React.FC = () => {
  useInter();
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return (
    <AbsoluteFill style={{ background: "#000" }}>
      <Audio src={staticFile("audio/promo.mp3")} />
      <AbsoluteFill
        style={{
          background: "radial-gradient(60% 40% at 50% 70%, rgba(120,110,255,0.22) 0%, rgba(0,0,0,0) 70%)",
          opacity: interpolate(frame, [110, 170, 1230, 1280], [0, 1, 1, 0], clamp),
        }}
      />
      {frame < 115 ? <Intro frame={frame} /> : null}
      {frame >= 100 && frame < 1300 ? <Phone frame={frame} fps={fps} /> : null}
      {CHAPTERS.map((c) => (frame >= c.from && frame < c.to ? <Headline key={c.title} frame={frame} c={c} /> : null))}
      {frame >= 1280 && frame < 1465 ? <More frame={frame} /> : null}
      {frame >= 1460 ? <Outro frame={frame} /> : null}
    </AbsoluteFill>
  );
};
