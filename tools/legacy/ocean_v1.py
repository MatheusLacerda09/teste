"""Cenário ÚNICO e contínuo da aba de conquistas: mar + nuvens de pixel art (SNES 16-bit) que mudam de cor
aos poucos (dia -> céu azul -> turquesa -> tempestade -> pôr do sol -> sol grande -> crepúsculo -> noite -> dia).

O Python gera MÁSCARAS brancas animadas (32 quadros em loop perfeito) e o Java pinta cada máscara com a cor
do momento, então nada faz fade entre imagens: o mesmo mar e as mesmas nuvens vão trocando de cor.
Fonte da verdade das cores: MOODS abaixo (vira SeaMoods.java ao rodar build_backgrounds.py).
"""
import math
import numpy as np
from PIL import Image
from pxlib import cumulus, Cv, GW, GH

HZ = 36                   # linha do horizonte (em células de 2 px)
F = 32                    # quadros por ciclo
TAU = 2 * math.pi
CX = 58                   # coluna do sol / lua / caminho de brilho

# ------------------------------------------------------------------------------------------------
# Paletas por momento. Ordem das cores = COLOR_NAMES; ordem dos números = NUM_NAMES.
COLOR_NAMES = ['sky_t', 'sky_m', 'sky_b', 's0', 's1', 's2', 's3', 's4', 'crest', 'shadow', 'foam', 'glitter',
               'cw', 'cl', 'cm', 'glow', 'disc', 'haze', 'bird']
NUM_NAMES = ['glow_a', 'sun_y', 'disc_a', 'moon_a', 'star_a', 'glit_a', 'haze_a']

MOODS = [
    # 0 dia de mar aberto
    dict(c=[0x2B8AC4, 0x58B0DC, 0xD2F2F6, 0x86DCF0, 0x58BCE6, 0x3A98D6, 0x2874BE, 0x1A52A0, 0xCFF6FF, 0x103C80,
            0xFFFFFF, 0xFFF6C8, 0xFFFFFF, 0x8ED0F4, 0x4A94DC, 0xFFF4C0, 0xFFFBE0, 0xF4FFFF, 0x1C2B4A],
         n=[.45, 15, 1.0, 0, 0, .90, .35]),
    # 1 céu azul
    dict(c=[0x6682EC, 0x7A96F0, 0x9CB4F6, 0x7CB8F0, 0x5C9AE6, 0x4278D0, 0x2E58B2, 0x1E3C90, 0xBCD8FC, 0x12307A,
            0xFFFFFF, 0xDDEBFF, 0xFFFFFF, 0x8ED0F4, 0x4A94DC, 0xDDE8FF, 0xF4F8FF, 0xE4EEFF, 0x23336A],
         n=[.30, 19, .8, 0, 0, .55, .25]),
    # 2 céu turquesa
    dict(c=[0x78C2CC, 0x90D0D6, 0xB2E4E4, 0x8ADCE4, 0x62C0D0, 0x40A0BA, 0x2C7C9A, 0x1C5878, 0xC0F2F4, 0x123E5C,
            0xFFFFFF, 0xE8FFFF, 0xFFFFFF, 0x8ED0F4, 0x4A94DC, 0xE0FFFF, 0xF4FFFF, 0xE8FFFF, 0x1F4048],
         n=[.22, 22, .6, 0, 0, .45, .25]),
    # 3 tempestade cinza
    dict(c=[0xA4A9A6, 0xBFC0B8, 0xD6D4C8, 0x94A4AC, 0x72828C, 0x566672, 0x3E4C5A, 0x28323E, 0xB4C2C8, 0x161E28,
            0xEEF2F2, 0xF0F0E0, 0xE4E8EA, 0x9AA6B0, 0x5C6A76, 0xF4F0E0, 0xFFFFF4, 0xD8D8CC, 0x25282A],
         n=[.18, 17, .35, 0, 0, .05, .20]),
    # 4 pôr do sol vermelho
    dict(c=[0x4B69A6, 0x7A78AE, 0xC47E8C, 0xF4A070, 0xDC7C7C, 0xB0608A, 0x804C86, 0x4C3672, 0xFFC890, 0x3A2862,
            0xFFE6C8, 0xFFD6A0, 0xFFE6C0, 0xFF9E5C, 0xC25A6C, 0xFFB070, 0xFFE08A, 0xFFB080, 0x3A1A3C],
         n=[.65, 6, 1.0, 0, 0, .95, .50]),
    # 5 sol grande no horizonte
    dict(c=[0x424590, 0x8A4C8C, 0xF0764F, 0xF89060, 0xD06880, 0x944E8C, 0x683E82, 0x402C68, 0xFFC080, 0x2E2058,
            0xFFEAC0, 0xFFE0A0, 0xFFD8A0, 0xFF9A5C, 0xB65072, 0xFFB868, 0xFFF2A0, 0xFFA860, 0x1A1024],
         n=[.85, 2, 1.0, 0, 0, 1.0, .60]),
    # 6 crepúsculo rosa
    dict(c=[0xB48AB6, 0xDB98B6, 0xFFB090, 0xF8B0A4, 0xDC8CA8, 0xAA6EA6, 0x7C5896, 0x52427C, 0xFFD0C0, 0x40306C,
            0xFFE8EC, 0xFFD2B8, 0xFFE8EC, 0xF8A8C0, 0xA878B0, 0xFFC0A0, 0xFFC8A0, 0xFFC0A8, 0x3A2250],
         n=[.40, -5, 0.0, 0, .12, .35, .45]),
    # 7 noite estrelada
    dict(c=[0x2C2E68, 0x34377A, 0x4A52A0, 0x5262B4, 0x3C4A9C, 0x2A3484, 0x1C2468, 0x121646, 0x8C9CE8, 0x0A0C28,
            0xDCE6FF, 0xDCE6FF, 0xE8EEFF, 0x9FB6F0, 0x5568C0, 0xFFC0A0, 0xFFF0C0, 0x6C7CC8, 0xB4C2F4],
         n=[0.0, -14, 0.0, 1, 1.0, .80, .25]),
]
MOON_Y = 24               # altura da lua acima do horizonte (células)
MOON_GLOW = 0xAEC2FF


# ------------------------------------------------------------------------------------------------
# NUVENS (3 tons duros, luz de cima) — formas fixas, só balançam
CLOUD_SPEC = [(-12, 32, 18, 0.35), (80, 130, 20, 0.70), (26, 46, 9, 0.50), (68, 90, 10, 0.50), (40, 78, 4, 0.50)]
_tgc = {}


def _cloud_tg(i):
    if i not in _tgc:
        x0, x1, h, pk = CLOUD_SPEC[i]
        _tgc[i] = cumulus(Cv(), x0, x1, HZ, h, pk, 21 + i, [0] * 5, ret=True, cap=min(4.6, 2.0 + h / 6),
                          inner=1.0, fillfrac=0.55, flat=1, light=(0.0, -1.0), th=(-0.05, -0.50, -0.90))
    return _tgc[i]


def _shift(tg, dx):
    if dx == 0:
        return tg
    out = np.zeros_like(tg)
    if dx > 0:
        out[:, dx:] = tg[:, :-dx]
    else:
        out[:, :dx] = tg[:, -dx:]
    return out


def clouds_tg(t):
    comb = np.zeros((GH, GW), int)
    for i in range(len(CLOUD_SPEC)):
        seed = 21 + i
        dx = int(round(1.5 * math.sin(TAU * (t + (seed * 0.137) % 1.0))))
        tg = _shift(_cloud_tg(i), dx)
        comb[tg > 0] = tg[tg > 0]
    return comb


def cloud_class(tg):
    """1 = branco, 2 = azul claro, 3..5 = azul médio."""
    return {'cw': tg == 1, 'cl': tg == 2, 'cm': tg >= 3}


# ------------------------------------------------------------------------------------------------
# MAR
_R = np.random.RandomState(7).rand(5, GH, GW)
_YS, _XS = np.mgrid[HZ:GH, 0:GW]


def sea_fields(t):
    d = (_YS - HZ).astype(float)
    A = 0.25 + 0.06 * d
    k1 = 0.85 / (1 + 0.11 * d)
    k2 = 1.9 / (1 + 0.09 * d)
    s1 = np.sin(k1 * _XS + TAU * t + d * 0.35)
    s2 = np.sin(k2 * _XS - 2 * TAU * t + d * 0.60)
    yeff = d + A * (0.75 * s1 + 0.45 * s2)
    q = np.zeros(d.shape, int)
    for b in (3.4, 7.2, 11.4, 15.8):
        q += (yeff >= b)
    qp1 = np.vstack([np.zeros((1, GW), int), q[:-1]])
    qp2 = np.vstack([np.zeros((2, GW), int), q[:-2]])
    crest = q > qp1
    crest[0] = False
    shadow = (qp1 > qp2) & (q == qp1)
    shadow[:2] = False
    foam = crest & (s1 > 0.5) & (_R[0][HZ:GH] < 0.55)
    blink = np.sin(TAU * (1 + (_R[2][HZ:GH] > 0.5)) * t + TAU * _R[3][HZ:GH])
    foam |= crest & (_R[1][HZ:GH] < 0.08) & (blink > 0.6)
    w = 1.5 + d * 0.55
    path = (np.abs(_XS - CX) <= w) & (d >= 1)
    glit = path & ((crest | shadow) & (blink > -0.3) | (blink > 0.78) & (_R[4][HZ:GH] < 0.30))
    return q, crest, shadow, foam, glit


def reflection(tg, t):
    """Reflexo espelhado das nuvens com ondulação: devolve alfas por classe."""
    cls = np.zeros((GH, GW), int)
    for y in range(HZ, GH):
        sy = HZ - 1 - (y - HZ)
        if sy < 0:
            break
        shift = int(round(math.sin(y * 1.3 + 55 + TAU * t) * 1.4))
        a = 0.30 * (0.45 if (y % 3 == 2) else 1.0) * (1.0 - (y - HZ) / (GH - HZ) * 0.6)
        for x in range(GW):
            sx = x - shift
            if 0 <= sx < GW and tg[sy, sx]:
                cls[y, x] = tg[sy, sx]
                alpha_r[y, x] = a
    return cls


alpha_r = np.zeros((GH, GW))


def frame_layers(f):
    """Todas as máscaras (alfa 0..1, shape GHxGW) do quadro f."""
    t = f / F
    L = {}
    q, crest, shadow, foam, glit = sea_fields(t)
    full = lambda a: np.vstack([np.zeros((HZ, GW)), a.astype(float)])
    for i in range(5):
        L[f's{i}'] = full(q == i)
    L['shadow'], L['crest'], L['foam'], L['glitter'] = full(shadow), full(crest), full(foam), full(glit)
    tg = clouds_tg(t)
    L.update({k: v.astype(float) for k, v in cloud_class(tg).items()})
    alpha_r[:] = 0
    rc = reflection(tg, t)
    ra = alpha_r.copy()
    for name, sel in cloud_class(rc).items():
        L['r' + name[1]] = ra * sel
    return L


LAYER_ORDER = ['s0', 's1', 's2', 's3', 's4', 'shadow', 'crest', 'foam', 'glitter', 'rw', 'rl', 'rm', 'cw', 'cl', 'cm']

# ------------------------------------------------------------------------------------------------
# PROPS: estrelas, brilho do sol (anéis), disco do sol, lua — sprite sheet 128x128
STAR = (0, 0, 117, 57)
GLOW = (0, 60, 65, 65)
SUN = (70, 64, 13, 13)
MOON = (90, 64, 11, 11)


def make_props():
    a = np.zeros((128, 128), float)
    rnd = np.random.RandomState(15)
    for _ in range(80):
        x, y = rnd.randint(1, 116), rnd.randint(1, 27)
        a[y, x] = 1.0
    for (x, y) in ((24, 12), (75, 8), (102, 20), (11, 25), (56, 17), (90, 6), (38, 5), (17, 6), (97, 11)):
        a[y, x] = 1.0
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            a[y + dy, x + dx] = 0.85
    ys, xs = np.mgrid[0:65, 0:65]
    r = np.hypot(xs - 32, ys - 32)
    ring = np.minimum((r // 3).astype(int), 11)
    gl = np.where(r <= 32, 0.92 * (1 - ring / 11.0) ** 1.7, 0)
    a[60:125, 0:65] = gl
    ys, xs = np.mgrid[0:13, 0:13]
    a[64:77, 70:83] = (np.hypot(xs - 6, ys - 6) <= 6.2)
    ys, xs = np.mgrid[0:11, 0:11]
    moon = (np.hypot(xs - 5, ys - 5) <= 5.2).astype(float)
    for (x, y) in ((3, 3), (6, 5), (4, 7), (7, 3)):
        moon[y, x] = 0.6
    a[64:75, 90:101] = moon
    img = np.zeros((128, 128, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA'), a


def to_rgba(alpha):
    img = np.zeros(alpha.shape + (4,), np.uint8)
    img[..., :3] = 255
    img[..., 3] = np.clip(np.round(alpha * 255), 0, 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')


# ------------------------------------------------------------------------------------------------
# preview em Python (mesma conta do SeaBackground.java), em células de 2 px
def _mix(a, b, f):
    a = np.array([(a >> 16) & 255, (a >> 8) & 255, a & 255], float)
    b = np.array([(b >> 16) & 255, (b >> 8) & 255, b & 255], float)
    return a * (1 - f) + b * f


def mood_at(k, f):
    a, b = MOODS[k % len(MOODS)], MOODS[(k + 1) % len(MOODS)]
    c = [_mix(x, y, f) for x, y in zip(a['c'], b['c'])]
    n = [x + (y - x) * f for x, y in zip(a['n'], b['n'])]
    return c, n


def _blend(img, mask, col, a):
    m = (mask * a)[..., None]
    img[:] = img * (1 - m) + np.array(col) * m


def compose(frame, k, f, props_a):
    C, N = mood_at(k, f)
    ci = {n: C[i] for i, n in enumerate(COLOR_NAMES)}
    ni = {n: N[i] for i, n in enumerate(NUM_NAMES)}
    img = np.zeros((GH, GW, 3))
    for y in range(HZ):
        fr = ((y // 3) + 0.5) / 12
        img[y] = _mix_f(ci['sky_t'], ci['sky_m'], fr / .55) if fr < .55 else _mix_f(ci['sky_m'], ci['sky_b'], (fr - .55) / .45)
    st = np.zeros((GH, GW)); st[:57, :117] = props_a[0:57, 0:117]
    _blend(img, st, (255, 255, 255), ni['star_a'])

    def paste(box, cx, cy, col, a):
        u, v, w, h = box
        m = np.zeros((GH, GW))
        x0, y0 = cx - w // 2, cy - h // 2
        for yy in range(h):
            for xx in range(w):
                X, Y = x0 + xx, y0 + yy
                if 0 <= X < GW and 0 <= Y < GH:
                    m[Y, X] = props_a[v + yy, u + xx]
        _blend(img, m, col, a)
    paste(GLOW, CX, HZ - MOON_Y, ((MOON_GLOW >> 16) & 255, (MOON_GLOW >> 8) & 255, MOON_GLOW & 255), ni['moon_a'] * .30)
    paste(MOON, CX, HZ - MOON_Y, (242, 246, 255), ni['moon_a'])
    sy = int(HZ - ni['sun_y'])
    paste(GLOW, CX, sy, ci['glow'], ni['glow_a'])
    paste(SUN, CX, sy, ci['disc'], ni['disc_a'])
    for j, al in enumerate((.75, .5, .3, .15)):
        row = np.zeros((GH, GW)); row[HZ - 1 - j] = 1
        _blend(img, row, ci['haze'], ni['haze_a'] * al)
    L = frame_layers(frame)
    for i in range(5):
        img[L[f's{i}'] > 0] = ci[f's{i}']
    _blend(img, L['shadow'], ci['shadow'], .40)
    _blend(img, L["crest"], ci["crest"], .50)
    _blend(img, L['foam'], ci['foam'], .95)
    _blend(img, L['glitter'], ci['glitter'], ni['glit_a'])
    row = np.zeros((GH, GW)); row[HZ] = 1
    _blend(img, row, ci['haze'], .6)
    for n, c in (('rw', 'cw'), ('rl', 'cl'), ('rm', 'cm')):
        _blend(img, L[n], ci[c], 1.0)
    for n in ('cw', 'cl', 'cm'):
        img[L[n] > 0] = ci[n]
    return Image.fromarray(np.clip(img.round(), 0, 255).astype(np.uint8), 'RGB')


def _mix_f(a, b, f):
    f = min(1, max(0, f))
    return a * (1 - f) + b * f
