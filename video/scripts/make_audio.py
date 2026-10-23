"""
Музыка и звуковые эффекты для промо-ролика — синтезируются с нуля (без чужих сэмплов,
можно спокойно выкладывать в TikTok). Моменты совпадают с кадрами в src/IslandPromo/index.tsx.

    python3 scripts/make_audio.py   →   public/audio/promo.wav (+ promo.mp3, если есть ffmpeg)
"""
import os
import subprocess
import wave

import numpy as np

SR = 44100
FPS = 30
FRAMES = 1620
DUR = FRAMES / FPS + 0.5
N = int(SR * DUR)
rng = np.random.default_rng(7)

L = np.zeros(N)
R = np.zeros(N)


def t_of(n):
    return np.arange(n) / SR


def add(sig, at, gain=1.0, pan=0.0):
    """Положить звук в момент at (секунды); pan от -1 (лево) до 1 (право)."""
    i = int(at * SR)
    if i >= N:
        return
    sig = sig[: N - i]
    L[i:i + len(sig)] += sig * gain * (1 - max(pan, 0))
    R[i:i + len(sig)] += sig * gain * (1 + min(pan, 0))


def env(n, a=0.005, d=0.2, curve=4.0):
    t = t_of(n)
    e = np.minimum(1, t / max(a, 1e-4)) * np.exp(-curve * np.maximum(0, t - a) / max(d, 1e-4))
    return e


def lowpass(x, cutoff):
    # Простой однополюсный фильтр, cutoff может меняться во времени
    cutoff = np.broadcast_to(cutoff, x.shape)
    a = 1 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


def note(m):
    return 440.0 * 2 ** ((m - 69) / 12)


# ——— Музыка: 112 BPM, C — G — Am — F ———
BPM = 112
BEAT = 60 / BPM
START = 110 / FPS          # телефон появляется — вступают барабаны
DROP_END = 1460 / FPS       # финал — барабаны уходят
CHORDS = [[48, 55, 60, 64, 67], [43, 55, 59, 62, 67], [45, 57, 60, 64, 69], [41, 53, 57, 60, 65]]


def pad(freqs, dur, gain=0.12):
    n = int(dur * SR)
    t = t_of(n)
    s = np.zeros(n)
    for f in freqs:
        for det in (-0.12, 0.0, 0.12):  # лёгкий «хорус»
            ff = f * 2 ** (det / 12)
            s += np.sin(2 * np.pi * ff * t) + 0.3 * np.sin(4 * np.pi * ff * t)
    a = np.minimum(1, t / 0.6) * np.minimum(1, (dur - t) / 0.6)
    return s * a * gain / len(freqs)


def kick():
    n = int(0.35 * SR)
    t = t_of(n)
    f = 45 + 110 * np.exp(-t * 28)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)


def hat(open_=False):
    n = int((0.16 if open_ else 0.05) * SR)
    x = rng.standard_normal(n)
    x = x - lowpass(x, 7000)
    return x * env(n, 0.001, 0.12 if open_ else 0.025)


def clap():
    n = int(0.22 * SR)
    x = rng.standard_normal(n)
    x = lowpass(x, 3500) - lowpass(x, 900)
    e = env(n, 0.002, 0.09)
    for k in (0.011, 0.022):
        e += 0.6 * np.roll(env(n, 0.002, 0.02), int(k * SR))
    return x * e


def bass(m, dur):
    n = int(dur * SR)
    t = t_of(n)
    f = note(m)
    s = np.tanh(2.2 * (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(4 * np.pi * f * t)))
    return lowpass(s, 900) * env(n, 0.01, dur * 0.9, 2.5)


def pluck(m, dur=0.35):
    n = int(dur * SR)
    t = t_of(n)
    f = note(m)
    s = np.sign(np.sin(2 * np.pi * f * t)) * 0.5 + np.sin(4 * np.pi * f * t) * 0.3
    return lowpass(s, 2600 * np.exp(-t * 6) + 400) * env(n, 0.002, 0.18)


bar = BEAT * 4
t0 = 0.0
k = 0
while t0 < DUR - 0.5:
    ch = CHORDS[k % 4]
    in_main = START - 0.01 <= t0 < DROP_END
    add(pad([note(m) for m in ch[1:]], bar + 0.6, 0.55 if in_main else 0.4), t0)
    if in_main:
        for b in range(4):
            tb = t0 + b * BEAT
            add(kick(), tb, 0.9)
            add(hat(), tb + BEAT / 2, 0.22, pan=0.3)
            if b in (1, 3):
                add(clap(), tb, 0.35, pan=-0.1)
            add(bass(ch[0] - 12 + (0 if b % 2 == 0 else 12), BEAT * 0.9), tb, 0.32)
        # Арпеджио восьмыми
        arp = [ch[2] + 12, ch[3] + 12, ch[4] + 12, ch[3] + 12]
        for j in range(8):
            add(pluck(arp[j % 4]), t0 + j * BEAT / 2, 0.11, pan=(-0.4 if j % 2 else 0.4))
    t0 += bar
    k += 1

# ——— Эффекты ———
def whoosh(dur=0.45, up=True):
    n = int(dur * SR)
    t = t_of(n)
    x = rng.standard_normal(n)
    sweep = (300 + 5000 * (t / dur)) if up else (5000 - 4600 * (t / dur))
    y = lowpass(x, sweep)
    a = np.sin(np.pi * np.minimum(1, t / dur)) ** 2
    return y * a


def blip(f=1300, dur=0.09):
    n = int(dur * SR)
    t = t_of(n)
    return np.sin(2 * np.pi * f * t * (1 + 0.15 * np.exp(-t * 40))) * env(n, 0.001, 0.05)


def chime(ms, dur=1.2, step=0.09):
    out = np.zeros(int((dur + step * len(ms)) * SR))
    for i, m in enumerate(ms):
        n = int(dur * SR)
        t = t_of(n)
        f = note(m)
        s = (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * 2.76 * f * t) * np.exp(-t * 6)) * env(n, 0.002, 0.5, 3)
        j = int(i * step * SR)
        out[j:j + n] += s
    return out


def impact():
    n = int(1.6 * SR)
    t = t_of(n)
    sub = np.sin(2 * np.pi * np.cumsum(30 + 60 * np.exp(-t * 6)) / SR) * np.exp(-t * 2.2)
    noise = lowpass(rng.standard_normal(n), 1800) * np.exp(-t * 7)
    return sub + 0.5 * noise


def riser(dur=2.6):
    n = int(dur * SR)
    t = t_of(n)
    x = lowpass(rng.standard_normal(n), 200 + 6000 * (t / dur) ** 2)
    tone = np.sin(2 * np.pi * np.cumsum(200 + 900 * (t / dur) ** 2) / SR)
    return (x * 0.7 + tone * 0.25) * (t / dur) ** 2


def ring():
    # Двухтональный звонок, как входящий вызов
    out = np.zeros(int(1.6 * SR))
    for r in range(2):
        n = int(0.5 * SR)
        t = t_of(n)
        s = (np.sin(2 * np.pi * 1320 * t) + np.sin(2 * np.pi * 1660 * t)) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 20 * t)))
        s *= np.minimum(1, t / 0.01) * np.minimum(1, (0.5 - t) / 0.05)
        j = int(r * 0.7 * SR)
        out[j:j + n] += s
    return lowpass(out, 3000)


def shimmer(dur=1.8):
    n = int(dur * SR)
    t = t_of(n)
    s = np.zeros(n)
    for i, m in enumerate([84, 88, 91, 96]):
        s += np.sin(2 * np.pi * note(m) * t + i) * (0.5 + 0.5 * np.sin(2 * np.pi * (3 + i) * t))
    return s * np.minimum(1, t / 0.3) * np.exp(-t * 1.6)


f = lambda frame: frame / FPS  # noqa: E731

# Заставка: тихий колокольчик, подъём и удар, когда появляется телефон
add(chime([72, 76, 79, 84], 1.6, 0.12), f(6), 0.18)
add(riser(), f(110) - 2.6, 0.22)
add(impact(), f(110), 0.55)

# Смены острова (кадры из ISLAND)
for fr in [205, 275, 355, 420, 545, 665, 790, 900, 960, 1080, 1205]:
    add(whoosh(0.35), f(fr) - 0.08, 0.10, pan=0.15)
    add(blip(1500 if fr in (275, 355, 900) else 1100), f(fr) + 0.02, 0.10)

# Заголовки глав — мягкий «вжух»
for fr in [175, 345, 535, 655, 780, 890, 1070]:
    add(whoosh(0.5), f(fr), 0.07, pan=-0.2)

add(blip(900, 0.25), f(205) + 0.4, 0.06)            # батарейка заполняется
add(ring(), f(545) + 0.1, 0.10)                     # звонок
add(chime([79, 84, 88], 1.0, 0.11), f(665) + 0.05, 0.16)  # сообщение
for i in range(4):                                  # режим звука: колокольчик дрожит
    add(blip(2200 - i * 150, 0.04), f(790) + 0.05 + i * 0.07, 0.07)
for i in range(6):                                  # таймер тикает
    add(blip(2600, 0.02), f(960) + i * 1.0, 0.05)
add(shimmer(), f(1080) + 0.05, 0.12)                # фонарик
for i in range(7):                                  # «И ещё» — пункты списка
    add(blip(1200 + i * 90, 0.06), f(1310 + i * 12), 0.08)
add(whoosh(0.7, up=False), f(1235), 0.12)           # телефон уезжает
add(impact(), f(1468), 0.35)                        # финальный экран
add(chime([60, 64, 67, 72, 76], 2.6, 0.14), f(1468), 0.2)

# Сведение: общая громкость, нарастание в начале и затухание в конце
mix = np.stack([L, R])
tt = t_of(N)
fade = np.minimum(1, tt / 0.3) * np.clip((DUR - tt) / 2.5, 0, 1)
mix *= fade
peak = np.max(np.abs(mix))
mix = np.tanh(mix / peak * 1.4) / np.tanh(1.4) * 0.89

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "..", "public", "audio", "promo.wav")
data = (mix.T * 32767).astype("<i2")
with wave.open(out, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(data.tobytes())
print("ok", out, f"{DUR:.1f} c")
mp3 = out[:-4] + ".mp3"
try:
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", out, "-b:a", "192k", mp3], check=True)
    print("ok", mp3)
except Exception as e:  # noqa: BLE001
    print("mp3 не сделан:", e)
