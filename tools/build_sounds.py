"""Gera os sons da aba de conquistas do Sea Sovereign (sintetizados do zero, sem samples de terceiros):

  ambient/sea_waves.ogg   mar, estéreo, loop sem emenda de 7,68 s (= 2 ciclos de 3,84 s do mar animado, FRAMES * FRAME_MS)
  ambient/sea_wind.ogg    vento, estéreo, loop sem emenda de 16 s (rajadas + um assobio bem leve)
  bird/song_1..7.ogg      passarinhos (pios, trinados, assobios, gorjeio), mono (o pan acompanha o bando que cruza a tela)

Uso: build_sounds.py <pasta assets/lasthorizon/sounds> [pasta_de_preview]
Precisa de numpy, scipy e ffmpeg (libvorbis). Os loops são ruído gerado e filtrado no domínio da frequência
(FFT circular), então o fim emenda no começo sem estalo.
"""
import os, subprocess, sys, wave
import numpy as np
from scipy import signal

SR = 44100
OUT = sys.argv[1]
PREV = sys.argv[2] if len(sys.argv) > 2 else None
rng = np.random.default_rng(1507)


# ------------------------------------------------------------------ utilidades
def circ_noise(n, lo, hi, tilt=0.0, rolloff=3.0, seed_rng=None):
    """Ruído branco circular filtrado por faixa [lo, hi] Hz, com inclinação espectral `tilt` (0 branco, 1 rosa)."""
    r = seed_rng or rng
    spec = np.fft.rfft(r.standard_normal(n))
    f = np.fft.rfftfreq(n, 1.0 / SR)
    f[0] = 1.0
    g = f ** (-tilt / 2.0)
    g *= 1.0 / (1.0 + (lo / f) ** (2 * rolloff))  # passa-alta
    g *= 1.0 / (1.0 + (f / hi) ** (2 * rolloff))  # passa-baixa
    g[0] = 0.0
    x = np.fft.irfft(spec * g, n)
    return x / (np.std(x) + 1e-12)


def smooth_periodic(n, harmonics, r=None, floor=0.0):
    """Envelope lento periódico (soma de poucos harmônicos de n/1), entre `floor` e 1."""
    r = r or rng
    t = np.arange(n) / n
    e = np.zeros(n)
    for k, a in harmonics:
        e += a * np.sin(2 * np.pi * k * t + r.uniform(0, 2 * np.pi))
    e = (e - e.min()) / (e.max() - e.min() + 1e-12)
    return floor + (1 - floor) * e


def normalize(x, peak=0.89):
    return x * (peak / (np.max(np.abs(x)) + 1e-12))


def write_ogg(name, data, q=4):
    path = os.path.join(OUT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    wav = path[:-4] + ".tmp.wav"
    with wave.open(wav, "wb") as w:
        w.setnchannels(1 if data.ndim == 1 else data.shape[0])
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.asarray(data if data.ndim == 1 else data.T) * 32767).clip(-32767, 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", str(q), path], check=True)
    os.remove(wav)
    if PREV:
        os.makedirs(PREV, exist_ok=True)
        np.save(os.path.join(PREV, os.path.basename(name)[:-4] + ".npy"), np.asarray(data))
    print(f"{name}: {data.shape[-1] / SR:.2f}s, {os.path.getsize(path) / 1024:.0f} KiB")


# ------------------------------------------------------------------ mar
def _loud(x, rms=0.20, peak=0.78):
    """Sobe o volume médio (RMS) e segura os picos com um limitador suave: o som fica 'cheio' sem estourar."""
    x = x / (np.sqrt((x ** 2).mean()) + 1e-12) * rms
    x = np.tanh(x * 1.5) / 1.5
    return x * (peak / (np.max(np.abs(x)) + 1e-12))


def _bubbles(env, count, lo=700, hi=3200):
    """Bolhinhas: pequenos 'blups' (senoide que sobe de tom e morre em ~10 ms) espalhados onde há espuma."""
    n = len(env)
    out = np.zeros(n)
    for _ in range(count):
        c = rng.integers(0, n - 800)
        if rng.random() > env[c] ** 1.2:
            continue
        f0 = rng.uniform(lo, hi)
        dur = int(rng.uniform(0.006, 0.020) * SR)
        tt = np.arange(dur) / SR
        f = f0 * (1.0 + 18.0 * tt)                       # o tom sobe: som típico de bolha
        blip = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / (dur / SR / 4.0))
        out[c:c + dur] += blip * rng.uniform(0.25, 1.0) * env[c]
    return out


def waves():
    """Mar: fundo contínuo de água + 3 ondas de tamanhos e intervalos diferentes (não parece loop), cada uma subindo
    rápido, quebrando em espuma com bolhas e recuando com chiado. Loop circular de 7,68 s (2 x 3,84 s do mar animado)."""
    L = 7.68
    n = int(round(L * SR))
    t = np.arange(n) / SR
    # (centro s, subida s, descida s, força)
    surges = [(1.20, 0.80, 1.80, 1.00), (3.85, 0.65, 1.55, 0.72), (6.15, 0.90, 1.45, 0.90)]

    def env_for(shift):
        e = np.zeros(n)
        for c, rise, fall, a in surges:
            d = ((t - c - shift + L / 2) % L) - L / 2
            e += a * np.where(d < 0, np.exp(-(d / rise) ** 2), np.exp(-(np.abs(d) / fall) ** 1.4))
        return np.clip(e, 0, 1.15)

    chans = []
    for side in (0, 1):
        shift = 0.06 if side else -0.06                 # uma onda chega um pouco antes num lado: largura estéreo
        e = env_for(shift)
        foam = np.roll(e, int(0.28 * SR)) ** 1.3        # a espuma vem depois do impacto
        body = circ_noise(n, 70, 1300, tilt=1.1, rolloff=2.0)
        wash = circ_noise(n, 550, 6500, tilt=0.6, rolloff=2.0)
        sizzle = circ_noise(n, 3500, 12000, tilt=0.0, rolloff=2.0)
        rumble = circ_noise(n, 25, 150, tilt=1.2, rolloff=3.0)
        slosh = circ_noise(n, 140, 650, tilt=0.8, rolloff=2.0) * (0.55 + 0.45 * circ_noise(n, 0.4, 2.2, rolloff=2.0))
        bed = 0.20 + 0.80 * np.clip(e, 0, 1)
        x = (0.95 * body * (0.18 + 0.95 * e) + 0.60 * wash * foam + 0.18 * sizzle * foam ** 1.6
             + 0.35 * rumble * (0.35 + 0.65 * e) + 0.40 * slosh * bed)
        x *= 0.88 + 0.12 * circ_noise(n, 0.3, 3.0, rolloff=2.0)
        bub = _bubbles(foam, 700) + 0.6 * _bubbles(foam, 500, 1500, 5200)
        x += 1.3 * bub
        chans.append(x)
    x = np.stack(chans)
    x -= x.mean(axis=1, keepdims=True)
    return _loud(x, rms=0.16)


# ------------------------------------------------------------------ vento
def wind():
    L = 16.0
    n = int(L * SR)
    chans = []
    gust = smooth_periodic(n, [(1, 1.0), (2, 0.7), (3, 0.5), (5, 0.25)], floor=0.28)
    for side in (0, 1):
        g = np.roll(gust, int((0.45 if side else 0.0) * SR))   # a rajada chega num ouvido e depois no outro
        low = circ_noise(n, 70, 700, tilt=1.4, rolloff=2.0)
        mid = circ_noise(n, 300, 2600, tilt=1.0, rolloff=2.0)
        air = circ_noise(n, 1800, 7000, tilt=0.4, rolloff=2.0)
        flutter = 0.8 + 0.2 * circ_noise(n, 2.0, 9.0, tilt=0.0, rolloff=2.0)   # tremulação nas rajadas fortes
        flutter = 1.0 + (flutter - 1.0) * g
        x = 0.9 * low * (0.35 + 0.65 * g) + 0.75 * mid * g ** 1.3 * flutter + 0.28 * air * g ** 2.2
        chans.append(x)
    # assobio: dois tons que passeiam de frequência bem devagar (periódicos) e só aparecem nas rajadas fortes
    t = np.arange(n) / SR
    whistle = np.zeros(n)
    for f0, d, ph in ((438.0, 38.0, 0.7), (687.0, 55.0, 2.1)):
        f0 = round(f0 * L) / L                        # ciclos inteiros em L -> a fase fecha certinho no laço
        inst = f0 + d * np.sin(2 * np.pi * t / L * 2 + ph)
        phi = 2 * np.pi * np.cumsum(inst) / SR
        phi -= (phi[-1] - 2 * np.pi * round(phi[-1] / (2 * np.pi))) * np.arange(n) / n   # fecha o erro de fase
        whistle += np.sin(phi) * (0.5 + 0.5 * circ_noise(n, 0.5, 5.0))
    whistle *= np.clip(gust - 0.55, 0, 1) ** 1.2
    chans[0] += 0.30 * whistle
    chans[1] += 0.30 * whistle * 0.7
    x = np.stack(chans)
    x -= x.mean(axis=1, keepdims=True)
    return _loud(x, rms=0.16)


# ------------------------------------------------------------------ passarinhos
# Passarinhos de floresta/costa: sons curtos e agudos (2 a 7 kHz) de tom quase puro, com poucos harmônicos.
# Cada arquivo é uma "frase" de 1 a 3 s: pios, trinados, assobios melódicos e gorjeios.

def _tone(f, amp):
    """Tom de passarinho a partir de uma curva de frequência (Hz) e de amplitude: seno + harmônicos fracos."""
    phi = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(phi) + 0.16 * np.sin(2 * phi + 0.5) + 0.05 * np.sin(3 * phi + 1.1)
    return x * amp


def _shape(n, a=0.12, r=0.35):
    """Envelope suave: sobe em `a` e desce em `r` (frações da nota), sem corte seco."""
    x = np.linspace(0, 1, n)
    e = np.minimum(x / max(a, 1e-3), 1.0) * np.minimum((1 - x) / max(r, 1e-3), 1.0)
    return np.sin(np.clip(e, 0, 1) * np.pi / 2) ** 1.5


def _pip(dur, pts, vol=1.0, a=0.12, r=0.35, vib=(0.0, 0.0)):
    """Um pio: a frequência passa pelos pontos `pts` (Hz), em escala logarítmica (varredura natural)."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    lf = np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(pts)), np.log(np.asarray(pts, float)))
    f = np.exp(lf) * (1.0 + vib[1] * np.sin(2 * np.pi * vib[0] * t))
    return _tone(f, _shape(n, a, r) * vol)


def _trill(count, gap, dur, f0, f1, vol=1.0, swell=True):
    """Trinado: várias notinhas rápidas e iguais (cada uma varre de f0 a f1), crescendo e diminuindo."""
    parts = []
    for i in range(count):
        k = i / max(1, count - 1)
        g = (np.sin(np.pi * k) ** 0.7 * 0.7 + 0.3) if swell else 1.0
        parts.append((i * gap, _pip(dur, [f0 * (1 + 0.02 * np.sin(i)), f1], vol * g, 0.2, 0.5)))
    return parts


def _warble(dur, base, dev, rate, vol=1.0):
    """Gorjeio: tom que oscila depressa e de forma irregular (FM com ruído lento), como passarinho cantando sem parar."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    wob = signal.sosfilt(signal.butter(2, rate, "lp", fs=SR, output="sos"), rng.standard_normal(n))
    wob /= np.std(wob) + 1e-12
    f = base * (1.0 + dev * (0.6 * wob + 0.4 * np.sin(2 * np.pi * rate * 0.8 * t)))
    am = 0.55 + 0.45 * np.clip(signal.sosfilt(signal.butter(2, rate * 1.3, "lp", fs=SR, output="sos"),
                                              rng.standard_normal(n)) * 2.0, -1, 1)
    return _tone(np.clip(f, 1500, 7500), _shape(n, 0.1, 0.25) * am * vol)


def _place(parts, total):
    out = np.zeros(int(total * SR))
    for start, x in parts:
        i = int(start * SR)
        out[i:i + len(x)] += x[:len(out) - i]
    return out


def _air(x, wet=0.12, tail=0.5):
    """Um pouquinho de ar livre (eco curto e difuso), para o passarinho não soar dentro de um quarto."""
    n = int(tail * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.14 * SR))
    ir = signal.sosfilt(signal.butter(2, 6000, "lp", fs=SR, output="sos"), ir)
    ir[0] = 0
    wetx = signal.fftconvolve(x, ir)
    out = np.concatenate([x, np.zeros(n)])
    out[: len(wetx)] += wet * wetx / (np.max(np.abs(wetx)) + 1e-12) * np.max(np.abs(x))
    return out


def birds():
    clips = []
    # 1) "pi-pi-pi": três pios curtos subindo
    clips.append(_place([(0.05 + i * 0.17, _pip(0.09, [3400, 5200], 0.8 + 0.1 * i, 0.15, 0.4)) for i in range(3)], 0.75))
    # 2) "tuii-tuii": dois pios longos de varredura para cima e para baixo
    clips.append(_place([(0.05, _pip(0.26, [3000, 5600, 4300], 1.0, 0.2, 0.4)),
                         (0.40, _pip(0.30, [3100, 5800, 4000], 0.9, 0.2, 0.45))], 0.8))
    # 3) trinado rápido "ti-ti-ti-ti-ti-ti"
    clips.append(_place(_trill(11, 0.052, 0.040, 4300, 5600, 0.9), 0.75))
    # 4) assobio melódico "tiu-li-lu-liu" (notas de tom puro com vibrato leve)
    clips.append(_place([
        (0.05, _pip(0.22, [3300, 4700], 1.0, 0.15, 0.4, (7, 0.012))),
        (0.32, _pip(0.12, [4600, 3900], 0.85, 0.15, 0.4)),
        (0.48, _pip(0.12, [3600, 4300], 0.85, 0.15, 0.4)),
        (0.66, _pip(0.34, [4400, 5400, 3600], 1.0, 0.2, 0.5, (8, 0.015)))], 1.1))
    # 5) gorjeio contínuo
    clips.append(_place([(0.05, _warble(1.2, 4200, 0.11, 9.0, 0.9))], 1.3))
    # 6) pio duplo + trinado: "pip-pip... tititititi"
    clips.append(_place([(0.04, _pip(0.08, [3600, 5000], 0.85)), (0.20, _pip(0.08, [3600, 5100], 0.9))]
                        + [(s0 + 0.55, x) for s0, x in _trill(9, 0.048, 0.036, 4700, 3900, 0.8)], 1.15))
    # 7) frase maior de passarinho cantando: assobio, pausa e gorjeio curto
    clips.append(_place([
        (0.05, _pip(0.28, [3000, 5200, 4700], 1.0, 0.2, 0.4, (6, 0.01))),
        (0.46, _pip(0.20, [4800, 3600], 0.9, 0.12, 0.4)),
        (0.78, _warble(0.55, 4600, 0.09, 8.0, 0.8)),
        (1.50, _pip(0.16, [3800, 5600], 0.85, 0.15, 0.4))], 1.8))
    out = []
    for c in clips:
        c = _air(c)
        c = c - np.mean(c)
        c = c / (np.sqrt((c ** 2).mean()) + 1e-12) * 0.15      # mesmo volume médio em todas as frases
        c = np.tanh(c * 1.4) / 1.4
        out.append(normalize(c, 0.80))
    return out


# ------------------------------------------------------------------ main
if __name__ == "__main__":
    write_ogg("ambient/sea_waves.ogg", waves(), q=4)
    write_ogg("ambient/sea_wind.ogg", wind(), q=4)
    for i, g in enumerate(birds(), 1):
        write_ogg(f"bird/song_{i}.ogg", g, q=5)
