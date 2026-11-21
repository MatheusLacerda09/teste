"""Cenas ANIMADAS do fundo de conquistas: cúmulos pixel art 16-bit assentados no horizonte do mar.

Cada função de cena recebe t em [0, 1) e devolve um quadro; t = 1.0 é idêntico a t = 0.0 (loop perfeito).
Nuvem = 3 tons duros (branco puro, azul-celeste claro, azul médio), luz de cima, sem anti-aliasing.
Água = faixas que ondulam, marolas que deslizam em velocidades diferentes (perspectiva), reflexo das
nuvens ondulando e brilho do sol/lua piscando. As nuvens balançam de leve.
"""
import math
import numpy as np
from pxlib import *

F = 32                      # quadros por ciclo (build_backgrounds.py usa o mesmo valor)
TAU = 2 * math.pi
TH = (-0.05, -0.50, -0.90)  # limiares das bandas de luz (branco / claro / médio)


def pal(white, light, mid, base=None):
    """Paleta de 3 tons no formato esperado por cumulus() (5 posições)."""
    return [white, light, mid, mid, base if base is not None else mid]


DAY = pal(0xFFFFFF, 0x8ED0F4, 0x4A94DC, 0x3F86CF)

# ---------------------------------------------------------------------------------------------
# cache (o céu e a forma das nuvens não mudam entre quadros)
_sky = {}
_tgc = {}


def static(builder):
    if builder not in _sky:
        _sky[builder] = builder().a.copy()
    cv = Cv()
    cv.a = _sky[builder].copy()
    return cv


def _tg(x0, x1, hz, hgt, peak, seed, tones, kw):
    key = (x0, x1, hz, hgt, peak, seed, tuple(tones), tuple(sorted(kw.items())))
    if key not in _tgc:
        _tgc[key] = cumulus(Cv(), x0, x1, hz, hgt, peak, seed, tones, ret=True, **kw)
    return _tgc[key]


def shifted(tg, dx):
    if dx == 0:
        return tg
    out = np.zeros_like(tg)
    if dx > 0:
        out[:, dx:] = tg[:, :-dx]
    else:
        out[:, :dx] = tg[:, -dx:]
    return out


def comp(cv, tg, tones, al=1.0):
    cols = np.array([[0.0, 0.0, 0.0]] + [hexc(c) for c in tones])
    m = tg > 0
    cv.a[m] = cv.a[m] * (1 - al) + cols[tg[m]] * al


def draw_cloud(cv, t, x0, x1, hz, hgt, peak, seed, tones, amp=1.5, **kw):
    """Desenha um cúmulo balançando devagar (±amp células) e devolve a máscara já deslocada."""
    tg = _tg(x0, x1, hz, hgt, peak, seed, tones, kw)
    dx = int(round(amp * math.sin(TAU * (t + (seed * 0.137) % 1.0))))
    tg = shifted(tg, dx)
    comp(cv, tg, tones)
    return tg


def cloud(cv, t, x0, x1, hz, hgt, peak, seed, tones, **kw):
    """Cúmulo de base reta apoiado no horizonte `hz`, iluminado de cima."""
    kw.setdefault('cap', 4.4)
    kw.setdefault('inner', 1.0)
    kw.setdefault('fillfrac', 0.55)
    kw.setdefault('flat', 1)
    kw.setdefault('light', (0.0, -1.0))
    kw.setdefault('th', TH)
    return draw_cloud(cv, t, x0, x1, hz, hgt, peak, seed, tones, **kw)


def reflect(cv, tgs, hz, tones, t, al=0.30, seed=5):
    """Reflexo espelhado das nuvens no mar, ondulando com o tempo."""
    cols = [None] + [hexc(c) for c in tones]
    for y in range(hz, GH):
        sy = hz - 1 - (y - hz)
        if sy < 0:
            break
        shift = int(round(math.sin(y * 1.3 + seed + TAU * t) * 1.4))
        a = al * (0.45 if (y % 3 == 2) else 1.0) * (1.0 - (y - hz) / (GH - hz) * 0.6)
        for tg in tgs:
            for x in range(GW):
                sx = x - shift
                if 0 <= sx < GW and tg[sy, sx]:
                    cv.px(x, y, cols[tg[sy, sx]], a)


def banks(cv, hz, tones, seed, spec, t, al=0.30):
    """Bancos de nuvens no horizonte + reflexo. spec = [(x0, x1, altura, pico), ...]"""
    tgs = [cloud(cv, t, x0, x1, hz, h, pk, seed + i, tones, cap=min(4.6, 2.0 + h / 6))
           for i, (x0, x1, h, pk) in enumerate(spec)]
    reflect(cv, tgs, hz, tones, t, al=al, seed=seed)
    return tgs


# ---------------------------------------------------------------------------------------------
_dash = {}


def _dash_layers(hz, seed):
    """Marolas por linha: duas camadas periódicas (p divide d*F, então o loop fecha sem emenda)."""
    key = (hz, seed)
    if key in _dash:
        return _dash[key]
    out = []
    n = max(1, GH - hz)
    for y in range(hz + 1, GH):
        k = (y - hz) / n
        rnd = np.random.RandomState(seed * 1009 + y * 7)
        far = k >= 0.5
        layers = ((32, 2 if far else 1, 2), (96 if far else 32, -3 if far else -1, 2))
        for li, (p, d, nseg) in enumerate(layers):
            segs = []
            for _ in range(nseg):
                ln = int(min(p // 2, 3 + k * 9 + rnd.randint(0, 5)))
                segs.append((rnd.randint(0, p), ln, rnd.rand() < 0.6))
            out.append((y, p, d, segs, 0.46 if li == 0 else 0.30))
    _dash[key] = out
    return out


def sea(cv, hz, stops, seed, hi, lo, t, bands=9, glitter=None, wob=0.8):
    """Mar animado: faixas ondulantes, marolas deslizando e brilho piscando."""
    n = GH - hz
    ys, xs = np.mgrid[hz:GH, 0:GW]
    yeff = (ys - hz) + wob * np.sin(TAU * t + xs * 0.13 + ys * 0.55)
    q = np.clip(np.floor(yeff / n * bands).astype(int), 0, bands - 1)
    cols = np.array([interp(stops, (k + 0.5) / bands) for k in range(bands)])
    cv.a[hz:GH] = cols[q]
    k = int(round(t * F)) % F
    hic, loc = hexc(hi), hexc(lo)
    for (y, p, d, segs, al) in _dash_layers(hz, seed):
        for x in range(GW):
            u = (x - d * k) % p
            for (s, ln, is_hi) in segs:
                if (u - s) % p < ln:
                    cv.px(x, y, hic if is_hi else loc, al)
                    break
    if glitter:
        gx, gc = glitter
        rnd = np.random.RandomState(seed + 99)
        for _ in range(46):
            kk = rnd.rand()
            y = hz + 1 + int(kk ** 0.8 * (n - 2))
            x = int(gx + rnd.randn() * (2 + kk * 7))
            ln = int(2 + kk * 4)
            phi, m = rnd.rand(), rnd.randint(1, 3)
            b = math.sin(TAU * (m * t + phi))
            if b > 0.25:
                cv.rect(x, y, x + ln, y + 1, gc, min(1.0, 0.95 * b) * (1 - 0.5 * kk))


# ---------------------------------------------------------------------------------------------
def _sky_day():
    cv = Cv()
    HZ = 34
    cv.vgrad(0, HZ, [(0, 0x2B8AC4), (0.5, 0x58B0DC), (0.85, 0xA9DEEE), (1, 0xD2F2F6)], bands=13)
    for y in range(0, HZ):
        for x in range(GW):
            d = math.hypot((x - 58) * 0.9, (y - HZ) * 1.1)
            if 21 <= d < 23:
                cv.px(x, y, 0xFFFFFF, 0.10)
    for i, (w, a) in enumerate([(16, .22), (11, .35), (7, .55), (4, .85)]):
        cv.rect(58 - w, HZ - 1 - i // 2, 58 + w, HZ, 0xFFF4C0, a)
    return cv


def day_ocean(t=0.0):
    cv = static(_sky_day)
    HZ = 34
    sea(cv, HZ, [(0, 0x6CCCEA), (0.10, 0x46B0DE), (0.45, 0x2878BE), (1, 0x163F88)], 3,
        0x9CE3F6, 0x0F3470, t, bands=11, glitter=(58, 0xFFF6C8))
    cv.rect(0, HZ, GW, HZ + 1, 0xE0F7FA, 0.9)
    banks(cv, HZ, DAY, 21, [(-10, 36, 26, 0.3), (78, 128, 28, 0.7), (30, 52, 10, 0.5), (64, 84, 11, 0.5),
                            (44, 72, 4, 0.5)], t)
    return cv


def _sky_blue():
    cv = Cv()
    HZ = 36
    cv.vgrad(0, HZ, [(0, 0x6682EC), (1, 0x8EA8F4)], bands=7)
    far = pal(0xA6BCF8, 0x93AEF4, 0x839FF0)
    cumulus(cv, -5, 60, 26, 14, 0.55, 31, far, cap=3.4, inner=0.6, flat=0)
    cumulus(cv, 60, 125, 22, 12, 0.4, 32, far, cap=3.4, inner=0.6, flat=0)
    for x in range(60, 100):
        cv.px(x, 8 + (x - 60) // 14, 0xA6BCF8, 0.8)
    return cv


def blue_sky(t=0.0):
    cv = static(_sky_blue)
    HZ = 36
    sea(cv, HZ, [(0, 0x5AA8E4), (0.15, 0x3E86D2), (0.6, 0x2A62B6), (1, 0x1B4290)], 4, 0x8CCCF6, 0x14377C, t)
    cv.rect(0, HZ, GW, HZ + 1, 0xDDEBFF, 0.8)
    banks(cv, HZ, DAY, 33, [(8, 62, 30, 0.5), (50, 112, 26, 0.45), (-14, 22, 14, 0.6), (96, 134, 16, 0.5),
                            (34, 58, 9, 0.5)], t)
    return cv


def _sky_teal():
    cv = Cv()
    HZ = 36
    cv.vgrad(0, HZ, [(0, 0x78C2CC), (1, 0xA6DCE0)], bands=7)
    cumulus(cv, -5, 122, 24, 14, 0.5, 41, pal(0x9AD6DC, 0x88C8D2, 0x76B8C4), cap=3.6, inner=0.6, flat=0)
    return cv


def teal_sky(t=0.0):
    cv = static(_sky_teal)
    HZ = 36
    sea(cv, HZ, [(0, 0x58B6C6), (0.15, 0x3A98B2), (0.6, 0x28708E), (1, 0x1A4C6E)], 5, 0x8EDCE6, 0x123C58, t)
    cv.rect(0, HZ, GW, HZ + 1, 0xE2F8F8, 0.8)
    banks(cv, HZ, DAY, 43, [(-6, 50, 28, 0.45), (44, 108, 24, 0.5), (84, 130, 18, 0.5), (32, 60, 9, 0.5)], t)
    return cv


STORM = pal(0xF2F4F4, 0xA6B2BC, 0x66747F, 0x56626E)
DARK = pal(0xB8C0C6, 0x76828E, 0x434E5A, 0x38424E)


def _sky_storm():
    cv = Cv()
    HZ = 38
    cv.vgrad(0, HZ, [(0, 0xBEC0B8), (0.7, 0xD0CEC2), (1, 0xDAD8CB)], bands=8)
    cv.disc(62, 20, 3, 0xF4F0E0)
    cv.disc(62, 20, 2, 0xFFFFF4)
    return cv


def gray_storm(t=0.0):
    cv = static(_sky_storm)
    HZ = 38
    sea(cv, HZ, [(0, 0x6E7E88), (0.2, 0x4E5E6C), (0.7, 0x35424F), (1, 0x232E3A)], 6, 0x9AA8B2, 0x161E28, t,
        wob=1.0)
    cv.rect(0, HZ, GW, HZ + 1, 0xC8D0D2, 0.7)
    cloud(cv, t, -10, 52, HZ, 34, 0.3, 52, DARK, cap=5, inner=1.6, fillfrac=0.7)
    cloud(cv, t, 62, 128, HZ, 32, 0.75, 53, DARK, cap=5, inner=1.6, fillfrac=0.7)
    banks(cv, HZ, STORM, 55, [(36, 84, 22, 0.5), (4, 40, 12, 0.5), (84, 120, 13, 0.5)], t, al=0.32)
    return cv


RED = pal(0xFFE6C0, 0xFF9E5C, 0xC25A6C, 0x9A4E68)


def _sky_red():
    cv = Cv()
    HZ = 36
    cv.vgrad(0, HZ, [(0, 0x4B69A6), (0.6, 0x7A78AE), (1, 0xC47E8C)], bands=9)
    hi = pal(0xD07468, 0xB4585F, 0x944C64)
    cumulus(cv, -10, 52, 22, 14, 0.35, 61, hi, cap=3.6, inner=0.8, flat=0, ragged=True)
    cumulus(cv, 60, 130, 20, 13, 0.7, 62, hi, cap=3.6, inner=0.8, flat=0, ragged=True)
    for x in range(34, 76):
        cv.px(x, 12 + (x - 34) // 22, 0xC9566A, 0.9)
    return cv


def red_sunset(t=0.0):
    cv = static(_sky_red)
    HZ = 36
    sea(cv, HZ, [(0, 0xE88A6A), (0.15, 0xC46474), (0.6, 0x7E4A82), (1, 0x4A3470)], 7, 0xFFC08A, 0x3A2862, t,
        glitter=(58, 0xFFD6A0))
    cv.rect(0, HZ, GW, HZ + 1, 0xFFC890, 0.8)
    banks(cv, HZ, RED, 64, [(26, 90, 32, 0.45), (76, 130, 24, 0.55), (-14, 34, 18, 0.6), (50, 70, 6, 0.5)], t)
    return cv


SEA5 = 45


def _sky_sun():
    cv = Cv()
    W, H = GW, GH
    cx, cy = 58, 32
    cv.vgrad(0, H, [(0, 0x424590), (1, 0x5A5496)], bands=6)
    ring = [0xFFF08C, 0xFFE27C, 0xFFCB6C, 0xFFB45E, 0xFF9C56, 0xF98852, 0xF0764F, 0xE46A52,
            0xD8625A, 0xC85A66, 0xB65672, 0xA25282]
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - cx, y - cy)
            k = int((d - 15.0) // 3.2) + 1
            if d <= 15.0:
                cv.px(x, y, ring[0])
            elif k < len(ring):
                cv.px(x, y, ring[k])
    cv.disc(cx - 3, cy - 3, 9, 0xFFF6A8, 0.65)
    cv.disc(cx - 5, cy - 5, 4, 0xFFFBD0, 0.7)
    rnd = np.random.RandomState(8)
    for _ in range(90):
        x, y = rnd.randint(0, W), rnd.randint(0, 46)
        d = math.hypot(x - cx, y - cy)
        if 15 < d < 42:
            cv.px(x, y, 0xFFE6B0, 0.7)
    stars(cv, 70, 9, 26, cols=(0xFFD0A8, 0xFFE8C8, 0xE8B8C8),
          region=lambda x, y: math.hypot(x - cx, y - cy) > 41)
    dome_tones = [0xFFB468, 0xE9805E, 0xB5586E, 0x8A4C78, 0x6C4474]
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - cx, y - cy)
            ang = math.atan2(y - cy, x - cx)
            edge = 40.5 + 2.2 * abs(math.sin(ang * 9 + 0.7)) + 1.4 * math.sin(ang * 23)
            if d > edge and y < 36:
                tt = d - edge
                k = 0 if tt < 1.6 else 1 if tt < 3.4 else 2 if tt < 6.5 else 3
                cv.px(x, y, dome_tones[k])
    for ang_deg, ln in ((-29, 30), (0, 33), (31, 34)):
        a = math.radians(ang_deg)
        for r in range(15, 15 + ln):
            x = int(round(cx + math.sin(a) * r))
            y = int(round(cy - math.cos(a) * r))
            cv.px(x, y, 0xFFE0A0, 0.65 * (1 - (r - 15) / ln))
    for r in range(16, 48):
        for dx in (-1, 0, 1):
            cv.px(cx + dx, cy - r, 0xFFD890, 0.28)
    return cv


def sunset_sun(t=0.0):
    cv = static(_sky_sun)
    W, H = GW, GH
    SEA = SEA5
    sea(cv, SEA, [(0, 0xE87A60), (0.15, 0xB65A78), (0.6, 0x7A4A84), (1, 0x4A3470)], 9, 0xFFB070, 0x3A2862, t,
        bands=6, glitter=(58, 0xFFD090))
    cv.rect(0, SEA, W, SEA + 1, 0xFF9E62, 0.9)
    for x in range(14, 78):
        top = int(round(SEA - 1 - 8.5 * max(0.0, 1 - abs(x - 44) / 30) ** 1.2))
        for y in range(top, SEA + 1):
            cv.px(x, y, 0x82467C if y > top else 0xB45E7E)
    sil = pal(0xFFAE6A, 0xC9627A, 0x7A4A80, 0x62427A)
    cloud(cv, t, -8, 46, 32, 9, 0.5, 71, sil, cap=3.4, inner=0.8, fillfrac=0.5)
    cloud(cv, t, 68, 126, 33, 10, 0.5, 72, sil, cap=3.4, inner=0.8, fillfrac=0.5)
    front = pal(0xFFD8A0, 0xFF9A5C, 0xB65072, 0x8C4672)
    banks(cv, SEA, front, 74, [(-16, 40, 16, 0.55), (70, 134, 18, 0.45), (40, 76, 5, 0.5)], t, al=0.28)
    rnd = np.random.RandomState(12)
    h = 2.0
    for x in range(W):
        h = max(1.0, min(4.0, h + rnd.randn() * 0.9))
        top = H - int(h)
        for y in range(top, H):
            cv.px(x, y, 0x2C2036 if y > top else 0x3E2A44)
    return cv


PINK = pal(0xFFE8EC, 0xF8A8C0, 0xA878B0, 0x8A6498)


def _sky_pink():
    cv = Cv()
    HZ = 37
    cv.vgrad(0, HZ, [(0, 0xB48AB6), (0.35, 0xDB98B6), (0.7, 0xF9A8B4), (1, 0xFFB090)], bands=10)
    o = pal(0xFFC8A4, 0xFFAE88, 0xF08672)
    cumulus(cv, 10, 62, 28, 10, 0.4, 81, o, cap=3.2, inner=0.6, flat=0)
    cumulus(cv, 62, 112, 26, 10, 0.5, 82, o, cap=3.2, inner=0.6, flat=0)
    return cv


def pink_dusk(t=0.0):
    cv = static(_sky_pink)
    HZ = 37
    sea(cv, HZ, [(0, 0xFFA48C), (0.15, 0xD684A0), (0.6, 0x8A5C94), (1, 0x5A4682)], 10, 0xFFC4B0, 0x40306C, t,
        glitter=(58, 0xFFD2B8))
    cv.rect(0, HZ, GW, HZ + 1, 0xFFD2C0, 0.8)
    banks(cv, HZ, PINK, 84, [(-8, 44, 26, 0.5), (36, 88, 30, 0.5), (74, 130, 24, 0.5), (50, 66, 7, 0.5)], t)
    return cv


NIGHT = pal(0xFFFFFF, 0x9FB6F0, 0x5568C0, 0x4656AE)


def _sky_night():
    cv = Cv()
    HZ = 38
    cv.vgrad(0, HZ, [(0, 0x2C2E68), (0.5, 0x34377A), (1, 0x4A52A0)], bands=9)
    stars(cv, 80, 15, 30, cols=(0xFFFFFF, 0xBFD0FF, 0x8F9CE0, 0xFFFFFF), cross=0.0)
    for (x, y) in ((24, 12), (75, 8), (102, 20), (11, 25), (56, 17), (90, 6), (38, 5)):
        cv.px(x, y, 0xFFFFFF)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cv.px(x + dx, y + dy, 0xBFD0FF, 0.9)
    for (x, y) in ((17, 6), (97, 11)):
        cv.px(x, y, 0xFFFFFF)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cv.px(x + dx, y + dy, 0xFFFFFF, 0.9)
        for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            cv.px(x + dx, y + dy, 0x9FB4F0, 0.7)
    cumulus(cv, 30, 70, 32, 8, 0.5, 91, pal(0x7E88CE, 0x646EBC, 0x525CAC), cap=3, inner=0.5, flat=0)
    return cv


def night(t=0.0):
    cv = static(_sky_night)
    HZ = 38
    sea(cv, HZ, [(0, 0x4A56A8), (0.15, 0x333C8A), (0.6, 0x1E2468), (1, 0x12163E)], 11, 0x7C8CE0, 0x0A0C28, t,
        glitter=(70, 0xDCE6FF))
    cv.rect(0, HZ, GW, HZ + 1, 0xB4C4F8, 0.7)
    banks(cv, HZ, NIGHT, 93, [(-10, 48, 22, 0.3), (60, 130, 24, 0.6), (36, 74, 12, 0.5)], t, al=0.28)
    return cv
