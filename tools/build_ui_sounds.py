"""Gera os sons da tela de criação de personagem do Sea Sovereign (sintetizados do zero, sem samples de terceiros):

  ui/tick.ogg    estalo curto de madeira/latão (um traço da alma "assenta")
  ui/pick.ogg    toque macio de papel e madeira (escolher uma opção)
  ui/page.ogg    folha de pergaminho virando (trocar de aba)
  ui/roll.ogg    dados chacoalhando (girar a alma, aleatório)
  ui/chime.ogg   sino de cristal em duas notas (a alma foi revelada)
  ui/seal.ogg    selo de cera batendo no papel (selar o personagem)
  ui/deny.ogg    duas batidas graves e abafadas (falta algo)
  ui/gem.ogg     faísca ascendente (uma aba ficou concluída)
  ui/open.ogg    pergaminho se desenrolando e assentando na mesa (a tela abre)
  ui/flap.ogg    estalo curto de pano (cada bandeira se desenrola)
  ui/hover.ogg   tique bem macio (o mouse passa por cima de uma bandeira)

Uso: build_ui_sounds.py <pasta assets/lasthorizon/sounds> [pasta_de_preview]
Precisa de numpy, scipy e ffmpeg (libvorbis). Tudo mono, 44,1 kHz, volume calibrado para ficar abaixo da música e dos efeitos do jogo.
"""
import os, subprocess, sys, wave
import numpy as np
from scipy import signal

SR = 44100
OUT = sys.argv[1]
PREV = sys.argv[2] if len(sys.argv) > 2 else None
rng = np.random.default_rng(2611)


def tt(d):
    return np.arange(int(SR * d)) / SR


def env_exp(n, attack, decay):
    """Ataque linear curto e queda exponencial (constante `decay` em segundos)."""
    t = np.arange(n) / SR
    a = np.minimum(1.0, t / max(attack, 1e-4))
    return a * np.exp(-t / decay)


def noise(n):
    return rng.standard_normal(n)


def band(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def lowp(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, fc, btype="low", fs=SR, output="sos"), x)


def highp(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, fc, btype="high", fs=SR, output="sos"), x)


def norm(x, peak):
    x = x - np.mean(x)
    return x / (np.max(np.abs(x)) + 1e-12) * peak


def fade(x, ms=4):
    n = int(SR * ms / 1000)
    x = x.copy()
    x[:n] *= np.linspace(0, 1, n)
    x[-n:] *= np.linspace(1, 0, n)
    return x


def bell(freq, dur, decay, partials=((1, 1.0), (2.01, 0.45), (2.76, 0.30), (4.07, 0.16), (5.4, 0.08))):
    t = tt(dur)
    x = np.zeros_like(t)
    for ratio, amp in partials:
        x += amp * np.sin(2 * np.pi * freq * ratio * t + rng.uniform(0, 6.28)) * np.exp(-t / (decay / (0.6 + 0.4 * ratio)))
    x *= np.minimum(1.0, t / 0.002)
    return x


def place(buf, x, at):
    i = int(SR * at)
    n = min(len(x), len(buf) - i)
    if n > 0:
        buf[i:i + n] += x[:n]


# ------------------------------------------------------------------ os sons
def s_tick():
    n = int(SR * 0.09)
    t = np.arange(n) / SR
    body = np.sin(2 * np.pi * 1850 * t) * np.exp(-t / 0.012) + 0.5 * np.sin(2 * np.pi * 3100 * t) * np.exp(-t / 0.006)
    click = band(noise(n), 2500, 7000) * np.exp(-t / 0.003) * 0.7
    return fade(norm(body + click, 0.55))


def s_pick():
    n = int(SR * 0.12)
    t = np.arange(n) / SR
    thump = np.sin(2 * np.pi * (520 - 1500 * t) * t) * np.exp(-t / 0.02)
    paper = band(noise(n), 800, 3800) * np.exp(-t / 0.014) * 0.9
    return fade(norm(thump * 0.8 + paper, 0.5))


def s_page():
    d = 0.42
    n = int(SR * d)
    t = np.arange(n) / SR
    x = band(noise(n), 450, 4200, 2)
    swell = np.sin(np.pi * np.clip(t / d, 0, 1)) ** 1.6                       # sobe e desce, como a folha passando
    flutter = 0.65 + 0.35 * np.sin(2 * np.pi * 23 * t + 1.0) * np.sin(2 * np.pi * 9 * t)
    crackle = (rng.random(n) < 0.0016) * noise(n) * 2.5                       # fibras do papel
    return fade(norm((x * swell * flutter + highp(crackle, 3000) * swell), 0.42), 8)


def s_roll():
    d = 0.72
    buf = np.zeros(int(SR * d))
    at = 0.0
    gap = 0.075
    while at < d - 0.12:
        f = rng.uniform(1300, 3600)
        n = int(SR * 0.05)
        t = np.arange(n) / SR
        hit = (np.sin(2 * np.pi * f * t) * np.exp(-t / 0.006) + band(noise(n), 1800, 6500) * np.exp(-t / 0.003)) * rng.uniform(0.45, 1.0)
        place(buf, hit, at)
        at += gap * rng.uniform(0.7, 1.15)
        gap *= 0.90                                                            # os dados vão assentando
    buf = lowp(buf, 9000)
    return fade(norm(buf, 0.5))


def s_chime():
    d = 1.5
    buf = np.zeros(int(SR * d))
    place(buf, bell(659.3, 1.2, 0.55) * 0.8, 0.0)           # mi
    place(buf, bell(987.8, 1.35, 0.7) * 0.9, 0.16)          # si
    place(buf, bell(1318.5, 1.1, 0.35) * 0.35, 0.16)        # mi oitava (brilho)
    return fade(norm(buf, 0.5), 8)


def s_seal():
    d = 0.7
    n = int(SR * d)
    t = np.arange(n) / SR
    thud = np.sin(2 * np.pi * (170 * np.exp(-t * 9) + 52) * t) * np.exp(-t / 0.11)
    squelch = lowp(noise(n), 1400) * np.exp(-t / 0.045) * 0.6                 # cera cedendo
    paper = band(noise(n), 700, 2600) * np.exp(-t / 0.02) * 0.35
    buf = thud * 1.0 + squelch + paper
    sparkle = bell(1760, 0.6, 0.25) * 0.14
    place(buf, sparkle, 0.12)
    return fade(norm(buf, 0.62), 6)


def s_deny():
    d = 0.3
    buf = np.zeros(int(SR * d))
    for k, at in enumerate((0.0, 0.12)):
        n = int(SR * 0.1)
        t = np.arange(n) / SR
        f = 165 - 18 * k
        x = (np.sign(np.sin(2 * np.pi * f * t)) * 0.5 + np.sin(2 * np.pi * f * 2 * t) * 0.3) * np.exp(-t / 0.035)
        place(buf, lowp(x, 900), at)
    return fade(norm(buf, 0.42))


def s_gem():
    d = 0.5
    n = int(SR * d)
    t = np.arange(n) / SR
    f = 700 * (2.0 ** (t / 0.18))                                              # sobe uma oitava em 0,18 s
    f = np.minimum(f, 1900)
    phase = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(phase) * env_exp(n, 0.004, 0.09)
    sp = bell(2093, 0.4, 0.14) * 0.35
    buf = x
    place(buf, sp, 0.07)
    return fade(norm(buf, 0.4))


def s_open():
    d = 0.75
    n = int(SR * d)
    t = np.arange(n) / SR
    x = band(noise(n), 350, 3600, 2)
    swell = np.sin(np.pi * np.clip(t / 0.6, 0, 1)) ** 1.3 * (t < 0.6)                  # o rolo gira e vai abrindo
    flutter = 0.6 + 0.4 * np.sin(2 * np.pi * 31 * t) * np.sin(2 * np.pi * 7 * t + 0.6)
    crackle = (rng.random(n) < 0.0022) * noise(n) * 2.2
    buf = (x * swell * flutter + highp(crackle, 2800) * swell) * 0.8
    k = int(SR * 0.16)
    tk = np.arange(k) / SR
    thump = (np.sin(2 * np.pi * (140 * np.exp(-tk * 14) + 70) * tk) * np.exp(-tk / 0.05) + lowp(noise(k), 900) * np.exp(-tk / 0.02) * 0.4)
    place(buf, thump * 1.1, 0.58)                                                        # assenta na mesa
    return fade(norm(buf, 0.45), 8)


def s_flap():
    n = int(SR * 0.16)
    t = np.arange(n) / SR
    x = band(noise(n), 500, 3200, 2) * np.exp(-t / 0.035)
    snap = highp(noise(n), 2200) * np.exp(-t / 0.004) * 0.8
    body = np.sin(2 * np.pi * (260 - 400 * t) * t) * np.exp(-t / 0.03) * 0.5
    return fade(norm(x + snap + body, 0.34))


def s_hover():
    n = int(SR * 0.05)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * 2300 * t) * np.exp(-t / 0.006) + band(noise(n), 3000, 8000) * np.exp(-t / 0.002) * 0.4
    return fade(norm(x, 0.16), 2)


SOUNDS = {"tick": s_tick, "pick": s_pick, "page": s_page, "roll": s_roll, "chime": s_chime, "seal": s_seal,
          "deny": s_deny, "gem": s_gem, "open": s_open, "flap": s_flap, "hover": s_hover}


def write_ogg(name, data, q=4):
    d = os.path.join(OUT, "ui")
    os.makedirs(d, exist_ok=True)
    wav = os.path.join(d, name + ".wav")
    pcm = (np.clip(data, -1, 1) * 32767).astype("<i2")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    ogg = os.path.join(d, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", str(q), ogg], check=True)
    if PREV:
        os.makedirs(PREV, exist_ok=True)
        os.replace(wav, os.path.join(PREV, name + ".wav"))
    else:
        os.remove(wav)
    return ogg


if __name__ == "__main__":
    for name, fn in SOUNDS.items():
        x = fn()
        path = write_ogg(name, x)
        print(f"{name}: {len(x) / SR:.2f}s pico={np.max(np.abs(x)):.2f} rms={np.sqrt(np.mean(x * x)):.3f} -> {path} ({os.path.getsize(path)} B)")
