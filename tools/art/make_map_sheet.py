#!/usr/bin/env python3
"""
Gera textures/gui/map_sheet.png (392 x 224): o mapa da criação de personagem.

Pergaminho rasgado com sombreado em pontilhado (dither de Bayer), dobras, manchas, curvas de nível e costas,
uma rota vermelha pontilhada com X ao longo da dobra do meio, rosa dos ventos, navio, montanhas, árvores,
dois rolos de papel nas laterais (faixas onduladas de tan e marrom, contorno escuro irregular, boca em espiral e nós
redondos nas pontas; sem tiras nem fivelas), e a borda de cima e de baixo rasgada. Tudo é pixel art sem suavização.

Uso: python3 tools/art/make_map_sheet.py [saida.png]
Dependências: numpy, scipy, Pillow.
"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage

W, H = 392, 224
MX, MY, MW, MH = 36, 6, 336, 204          # a folha (os rolos cobrem as pontas)
FOLD = MX + MW // 2                       # 204
ROLL_L, ROLL_R, ROLL_W = 24, 368, 16
ROLL_Y0, ROLL_Y1 = 10, 206                # centro da tampa de cima e da borda de baixo (as varas saem além)
LEFT_PANEL = (46, 12, 194, 207)           # x0, y0, x1, y1 (onde fica texto: decoração mais fraca)
RIGHT_PANEL = (214, 12, 362, 207)

rnd = np.random.default_rng(20261030)


def rgb(v):
    return np.array([(v >> 16) & 255, (v >> 8) & 255, v & 255], dtype=np.float32)


def vnoise(w, h, cell, seed):
    r = np.random.default_rng(seed)
    gw, gh = w // cell + 3, h // cell + 3
    g = r.random((gh, gw))
    ys, xs = np.arange(h) / cell, np.arange(w) / cell
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    sy, sx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a, b, c, d = g[y0][:, x0], g[y0][:, x0 + 1], g[y0 + 1][:, x0], g[y0 + 1][:, x0 + 1]
    top = a + (b - a) * sx[None, :]
    bot = c + (d - c) * sx[None, :]
    return top + (bot - top) * sy[:, None]


def fractal(w, h, seed, cells=(64, 32, 16), weights=(0.58, 0.28, 0.14)):
    f = sum(wt * vnoise(w, h, c, seed + i * 7) for i, (c, wt) in enumerate(zip(cells, weights)))
    return (f - f.min()) / (f.max() - f.min())


def carve(n, amp, tears):
    a = np.zeros(n, int)
    v = int(rnd.integers(0, amp + 1))
    for i in range(n):
        r = rnd.random()
        if r < 0.16:
            v += 1
        elif r < 0.32:
            v -= 1
        v = max(0, min(amp, v))
        a[i] = v
    for _ in range(tears):
        at, ln, dp = int(rnd.integers(10, n - 10)), int(rnd.integers(2, 6)), int(rnd.integers(3, 6))
        for k in range(ln):
            if at + k < n:
                a[at + k] = max(a[at + k], dp - abs(k - ln // 2))
    for i in range(7):                     # cantos arredondados
        a[i] = max(a[i], 7 - i)
        a[n - 1 - i] = max(a[n - 1 - i], 7 - i)
    return a


# ------------------------------------------------------------------------------------------ a folha
carve(MH, 2, 7); carve(MH, 2, 7); carve(MW, 2, 9); carve(MW, 2, 9)   # (só para manter a sequência aleatória do resto do mapa)
erng = np.random.default_rng(4242)


def free_edge(n, tears):
    """Distância da borda para dentro, por posição: serrilhado fino + ondulação larga + poucos rasgos + cantos arredondados."""
    a = np.zeros(n, int)
    v = 1
    for i in range(n):
        v = int(np.clip(v + erng.choice([-1, 0, 0, 1]), 0, 2))
        a[i] = v
    i = np.arange(n)
    ph = erng.uniform(0, 2 * np.pi, 2)
    wave = 1.6 + 1.5 * np.sin(i / n * 2 * np.pi * 2.0 + ph[0]) + 0.8 * np.sin(i / n * 2 * np.pi * 5.0 + ph[1])
    a = np.maximum(a, np.round(wave).astype(int))
    for _ in range(tears):
        at, ln, dp = int(erng.integers(14, n - 14)), int(erng.integers(2, 5)), int(erng.integers(3, 6))
        for k in range(ln):
            a[at + k] = max(a[at + k], dp - abs(k - ln // 2))
    for k in range(6):
        a[k] = max(a[k], 6 - k)
        a[n - 1 - k] = max(a[n - 1 - k], 6 - k)
    return a


EL, ER = free_edge(MH, 0), free_edge(MH, 0)
_ys = np.arange(MH)                       # as laterais ficam sob os rolos: sem rasgos fundos (só os cantos arredondados)
_corner = (_ys < 6) | (_ys >= MH - 6)
EL, ER = (np.where(_corner, a, np.minimum(a, 2)) for a in (EL, ER))
ET, EB = free_edge(MW, 4), free_edge(MW, 4)
mask = np.zeros((MH, MW), bool)
for y in range(MH):
    for x in range(MW):
        mask[y, x] = EL[y] <= x < MW - ER[y] and ET[x] <= y < MH - EB[x]

dist = ndimage.distance_transform_cdt(np.pad(mask, 1), metric="taxicab")[1:-1, 1:-1]

BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0
TONES = [rgb(c) for c in (0xEFD9A2, 0xE9D098, 0xE3C78D, 0xDDBE83, 0xD6B478, 0xCEA96C, 0xC59D60, 0xBA9055)]


def quantize(v, x0=0, y0=0):
    n = len(TONES) - 1
    f = np.clip(v, 0, 1) * n
    base = np.floor(f).astype(int)
    frac = f - base
    yy, xx = np.mgrid[0:v.shape[0], 0:v.shape[1]]
    th = BAYER[(yy + y0) % 4, (xx + x0) % 4]
    idx = np.clip(base + (frac > th).astype(int), 0, n)
    return np.array(TONES)[idx]


yy, xx = np.mgrid[0:MH, 0:MW]
nx, ny = (xx - MW / 2) / (MW / 2), (yy - MH / 2) / (MH / 2)
radial = np.clip(np.sqrt(nx * nx * 0.9 + ny * ny), 0, 1.4) ** 1.7
mottle = fractal(MW, MH, 11, cells=(48, 24, 10), weights=(0.5, 0.32, 0.18))
stain = fractal(MW, MH, 91, cells=(40, 20, 9), weights=(0.55, 0.3, 0.15))
rust = fractal(MW, MH, 191, cells=(44, 22, 9), weights=(0.55, 0.3, 0.15))
edge_dark = np.clip(1 - dist / 15.0, 0, 1) ** 1.5 * 0.60
v = 0.10 + 0.40 * radial + 0.34 * (mottle - 0.5) + edge_dark
v += np.clip((stain - 0.70) / 0.30, 0, 1) * 0.30
sheet = quantize(v)
# ferrugem (avermelhado) e mofo (esverdeado) em manchas pontilhadas
for noise, thr, tint, amt in ((rust, 0.74, 0xB4623A, 0.34), (stain, 0.80, 0x7C8C66, 0.26)):
    m = np.clip((noise - thr) / (1 - thr), 0, 1)
    dm = (m > BAYER[yy % 4, xx % 4] * 0.9) & (m > 0.04)
    sheet[dm] = sheet[dm] * (1 - amt) + rgb(tint) * amt

# sombra das pontas junto dos rolos (o papel entra por baixo deles)
for side in (0, 1):
    for d in range(1, 11):
        col = (ROLL_L + ROLL_W - MX + d - 1) if side == 0 else (ROLL_R - MX - d)
        if 0 <= col < MW:
            sheet[:, col] = sheet[:, col] * (1 - 0.46 * (1 - d / 11.0) ** 1.5)

# bordas: contorno, fio escuro e fio médio (como nos mapas de referência)
OUT, RIM1, RIM2 = rgb(0x4B2D14), rgb(0x80522A), rgb(0xAA7A40)
sheet[dist == 1] = OUT
sheet[dist == 2] = RIM1
r3 = dist == 3
sheet[r3] = RIM2
chk = (dist == 4) & ((xx + yy) % 2 == 0)
sheet[chk] = RIM2


# ------------------------------------------------------------------------------------------ mapa: costas e curvas de nível
land_f = fractal(MW, MH, 5, cells=(70, 34, 17), weights=(0.6, 0.27, 0.13))
SEA = 0.47
strength = np.ones((MH, MW), np.float32)
for (x0, y0, x1, y1) in (LEFT_PANEL, RIGHT_PANEL):
    strength[max(0, y0 - MY):y1 - MY, max(0, x0 - MX):x1 - MX] = 0.26
strength = ndimage.uniform_filter(strength, size=5)
inner = dist >= 5


def blend(px_mask, color, amount):
    m = px_mask & inner
    a = (amount * strength)[m][:, None]
    sheet[m] = sheet[m] * (1 - a) + rgb(color) * a


land = land_f > SEA
sea = ~land
blend(sea, 0xF4E6BE, 0.16)                       # o mar é um pouco mais claro
step = 0.075
level = np.floor((land_f - SEA) / step)
for k in (1, 2, 3, 4):                            # curvas de nível dentro da terra
    ring = (level >= k) & ((np.roll(level, 1, 0) < k) | (np.roll(level, 1, 1) < k))
    blend(ring & land, 0x8C5F30, 0.40)
coast = land & (~np.roll(land, 1, 0) | ~np.roll(land, 1, 1) | ~np.roll(land, -1, 0) | ~np.roll(land, -1, 1))
coast |= sea & (np.roll(land, 1, 0) | np.roll(land, 1, 1))
blend(coast, 0x6B4423, 0.85)
shore = ndimage.binary_dilation(coast, iterations=2) & sea & ~coast       # fio de espuma claro ao lado da costa
blend(shore, 0xFFF3CC, 0.28)
# traços de onda no mar: pequenos "--" em linhas
wave = vnoise(MW, MH, 3, 77)
wv = sea & (wave > 0.58) & ((yy % 7) == 3) & ((xx % 11) < 4)
blend(wv, 0x9C7442, 0.42)

# ------------------------------------------------------------------------------------------ dobras
creases = np.zeros((MH, MW), np.int8)
for y in range(MH):                               # dobra vertical (tracejada, com falhas)
    if rnd.random() < 0.9:
        creases[y, FOLD - MX] = 1
fy = MH // 2
for x in range(MW):
    if rnd.random() < 0.85:
        creases[fy, x] = 2
for y in range(MH):
    if creases[y, FOLD - MX] == 1 and inner[y, FOLD - MX]:
        sheet[y, FOLD - MX] = sheet[y, FOLD - MX] * 0.80 + rgb(0x6B4423) * 0.20
        sheet[y, FOLD - MX + 1] = sheet[y, FOLD - MX + 1] * 0.8 + rgb(0xFFF3CC) * 0.2
        for d in range(1, 6):
            sheet[y, FOLD - MX - d] *= 1 - 0.07 * (1 - d / 6)
for x in range(MW):
    if creases[fy, x] == 2 and inner[fy, x]:
        sheet[fy, x] = sheet[fy, x] * 0.86 + rgb(0x6B4423) * 0.14
        sheet[fy + 1, x] = sheet[fy + 1, x] * 0.88 + rgb(0xFFF3CC) * 0.12

# ------------------------------------------------------------------------------------------ canvas e sprites
canvas = np.zeros((H, W, 4), np.float32)          # RGBA 0..255


def put_sheet():
    m = mask
    canvas[MY:MY + MH, MX:MX + MW, :3][m] = sheet[m]
    canvas[MY:MY + MH, MX:MX + MW, 3][m] = 255


def px(x, y, color, a=255):
    if 0 <= x < W and 0 <= y < H:
        c = rgb(color) if isinstance(color, int) else color
        al = a / 255.0
        old = canvas[y, x]
        oa = old[3] / 255.0
        na = al + oa * (1 - al)
        if na > 0:
            canvas[y, x, :3] = (c * al + old[:3] * oa * (1 - al)) / na
        canvas[y, x, 3] = na * 255


def sprite(rows, x0, y0, pal):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in pal:
                px(x0 + i, y0 + j, pal[ch])


put_sheet()

INK, INK2, INK3 = 0x5E3B1E, 0x8A5F31, 0xB4864C
MOUNT = ["....X....", "...XxX...", "..XxxXX..", ".XxxxXXX.", "XxxxxXXXX"]
MOUNT2 = ["...X...", "..XxX..", ".XxxXX.", "XxxxXXX"]
TREE = ["..X..", ".XXX.", "..X..", ".XXX.", "XXXXX", "..X.."]
PALM = ["X..XXX.X.", ".XXX.XXX.", "...XX....", "....X....", "....X....", "....X...."]
HOUSE = ["...X...", "..XXX..", ".XXXXX.", "XXXXXXX", "X.XXX.X", "XXXXXXX"]
ANCHOR = ["....XXX....", "....X.X....", "....XXX....", ".....X.....", "..XXXXXXX..", ".....X.....",
          "X....X....X", "XX...X...XX", ".XX..X..XX.", "..XXXXXXX..", "....XXX...."]
SHIP = [".....X.......", ".....XX......", "....XXXX.....", "....XXXXX....", "...XXXXXXX...", "...XXXXXXXX..",
        ".....X.......", "XXXXXXXXXXXXX", ".XXXXXXXXXXX.", "..XXXXXXXXX.."]
CHEST = [".XXXXXXX.", "XxxxxxxxX", "XXXXgXXXX", "XxxxxxxxX", "XxxxxxxxX", "XXXXXXXXX"]


def ink(rows, x, y, dark=INK, mid=INK2):
    sprite(rows, x, y, {"X": dark, "x": mid, "g": 0xD9A441})


# rosa dos ventos nítida na dobra (parte de cima)
def compass(cx, cy, r, dark, mid, light, alpha=255, ticks=True):
    yy2, xx2 = np.mgrid[-r:r + 1, -r:r + 1]
    d = np.sqrt(xx2 ** 2 + yy2 ** 2)
    for j in range(2 * r + 1):
        for i in range(2 * r + 1):
            dx, dy, dd = i - r, j - r, d[j, i]
            col = None
            if abs(dd - r) <= 0.9:
                col = mid
            elif ticks and abs(dd - (r - 3)) <= 0.5:
                col = light
            else:
                for k in range(8):
                    card = k % 2 == 0
                    ang = k * np.pi / 4
                    ux, uy = np.cos(ang), np.sin(ang)
                    ln = (r - 2) * (1.0 if card else 0.6)
                    hw = ln * (0.17 if card else 0.13)
                    p, q = dx * ux + dy * uy, -dx * uy + dy * ux
                    if 0 <= p <= ln and abs(q) <= hw * (1 - p / ln) + 0.4:
                        col = dark if q >= 0 else mid
                        break
                if col is None and dd <= 1.6:
                    col = dark
            if col is not None:
                px(cx + dx, cy + dy, col, alpha)


# fio de ouro/tinta: rota vermelha pontilhada pela dobra
def route(points, color=0xB8302A, spacing=5.0):
    pts = np.array(points, float)
    seg = np.sqrt(((pts[1:] - pts[:-1]) ** 2).sum(1))
    total = seg.sum()
    # suaviza com Catmull-Rom
    dense = []
    for i in range(len(pts) - 1):
        p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, len(pts) - 1)]
        for t in np.linspace(0, 1, 24, endpoint=False):
            t2, t3 = t * t, t * t * t
            dense.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    dense.append(pts[-1])
    acc, last = 0.0, dense[0]
    out = [dense[0]]
    for p in dense[1:]:
        acc += np.linalg.norm(p - last)
        last = p
        if acc >= spacing:
            out.append(p)
            acc = 0.0
    for p in out:
        x, y = int(round(p[0])), int(round(p[1]))
        for dx in (0, 1):
            for dy in (0, 1):
                px(x + dx, y + dy, 0x3A1008 if (dx and dy) else color)
    return out


def xmark(cx, cy, size=4, color=0xB8302A):
    for i in range(-size, size + 1):
        for t in (0, 1):
            px(cx + i, cy + i + t, color)
            px(cx + i, cy - i + t, color)
        px(cx + i + 1, cy + i + 2, 0x3A1008, 140)


# coluna decorada da dobra (a faixa livre entre os painéis vai de x = 194 a 214)
ANCHOR7 = ["...X...", "..XXX..", "...X...", ".XXXXX.", "...X...", "X..X..X", "XX.X.XX", ".XXXXX."]
compass(FOLD, 24, 11, INK, INK3, INK3)
ink(MOUNT2, FOLD - 10, 62)
ink(TREE, FOLD - 9, 98)
ink(TREE, FOLD - 4, 103)
ink(HOUSE, FOLD - 10, 130)
ink(ANCHOR7, FOLD - 10, 170)
ink(TREE, FOLD + 5, 80)
ink(MOUNT2, FOLD + 5, 112)
ink(TREE, FOLD + 6, 148)
for i in range(3):
    px(FOLD + 5 + i * 3, 160, INK3)
pts = route([(FOLD, 40), (FOLD + 3, 54), (FOLD - 2, 70), (FOLD + 3, 88), (FOLD - 3, 112), (FOLD + 2, 132), (FOLD - 2, 152),
             (FOLD + 2, 170), (FOLD, 184)])
xmark(FOLD, 191, 4)
for dx in range(-1, 2):
    for dy in range(-1, 2):
        px(FOLD + dx, 38 + dy, 0xB8302A)

# marca d'água grande da rosa dos ventos atrás do painel da direita
compass(288, 110, 58, INK, INK3, INK3, alpha=34)

# ------------------------------------------------------------------------------------------ rolos de papel
# Estilo dos pergaminhos de referência: contorno quase preto e irregular, faixas onduladas de tan e marrom (chapadas, sem
# pontilhado) ao longo do rolo, a boca do rolo com a espiral marrom e um nó redondo em cada ponta. Nada prende o rolo.
RO = [0x1D1918, 0x2C1C12, 0x7A4A2A, 0xA15F39, 0xB17D48, 0xC4965B, 0xCEB177, 0xDDC78F, 0xEBDCAA]
CAP_RY = 3.4
KNOB = ["..KKKK..",
        ".KLccbK.",
        "KLcccbbK",
        "KccbbbaK",
        ".KbbaaK.",
        "..KKKK..",
        "..KaaK..",
        "..KaaK.."]
KNOB_PAL = {"K": RO[0], "L": RO[7], "c": RO[6], "b": RO[4], "a": RO[3]}


def roll(x0, left):
    w = ROLL_W
    xc = x0 + w / 2.0
    rows = ROLL_Y1 - ROLL_Y0 + 10
    streak = ndimage.zoom(rnd.random((rows // 12 + 3, 4)), (12, 5), order=1)[:rows, :w]     # faixas ao longo do rolo
    blot = fractal(w, rows, 77 + x0, cells=(16, 8, 4), weights=(0.5, 0.3, 0.2))
    jit = lambda: ndimage.zoom(rnd.random(rows // 5 + 3), 5, order=1)[:rows]
    eL, eR = (jit() > 0.68).astype(int), (jit() > 0.68).astype(int)                         # silhueta irregular

    def bottom_of(c):                                  # última linha do corpo na coluna c (borda de baixo arredondada)
        ex = ((x0 + c + 0.5) - xc) / (w / 2.0)
        return int(round(ROLL_Y1 + 3.0 * np.sqrt(max(0.0, 1 - ex * ex))))

    for y in range(ROLL_Y0, ROLL_Y1 + 4):
        r = y - ROLL_Y0
        spike_l, spike_r = rnd.random() < 0.025, rnd.random() < 0.025
        for c in range(w):
            if c < eL[r] or c > w - 1 - eR[r] or y > bottom_of(c):
                continue
            u = ((c + 0.5) / w) * 2 - 1
            lam = -0.50 * u + 0.80 * np.sqrt(max(0.0, 1 - u * u))
            v = 0.02 + 0.62 * max(0.0, lam) + (streak[r, c] - 0.5) * 0.36 + (blot[r, c] - 0.5) * 0.32
            col = RO[2 + int(np.clip(v, 0.0, 0.999) * 7)]
            edge_l, edge_r = c - eL[r], (w - 1 - eR[r]) - c
            if y >= bottom_of(c) or edge_l == 0 or edge_r == 0 or (edge_l == 1 and blot[r, c] < 0.30) \
                    or (edge_r == 1 and blot[r, c] < 0.30) or (spike_l and edge_l == 2) or (spike_r and edge_r == 2):
                col = RO[0]
            elif edge_l == 1 or edge_r == 1:
                col = RO[1]
            px(x0 + c, y, col)

    cy, rx, ry = ROLL_Y0, w / 2.0, CAP_RY               # boca do rolo (em cima): o corte do papel em espiral
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for c in range(w):
            dx, dy = (x0 + c + 0.5) - xc, (y + 0.5) - cy
            ex, ey = dx / rx, dy / ry
            d = np.sqrt(ex * ex + ey * ey)
            if d > 1.0:
                continue
            if (dx / (rx - 1.3)) ** 2 + (dy / (ry - 1.0)) ** 2 > 1.0:
                px(x0 + c, y, RO[0])
                continue
            k = d * 2.4 + np.arctan2(ey, ex) / (2 * np.pi)
            px(x0 + c, y, RO[2] if (k % 1.0) < 0.36 else RO[6] if ex < 0.2 else RO[5])
    px(int(xc), cy, RO[2]); px(int(xc) - 1, cy, RO[2])
    sprite(KNOB, int(xc) - 4, cy - 10, KNOB_PAL)        # nó de cima
    fy = max(bottom_of(c) for c in range(w)) + 1        # nó de baixo (o mesmo, de ponta-cabeça)
    for j, row in enumerate(reversed(KNOB)):
        for i, ch in enumerate(row):
            if ch in KNOB_PAL:
                px(int(xc) - 4 + i, fy + j - 1, KNOB_PAL[ch])

    ix = x0 + w if left else x0 - 1                     # fresta escura onde a folha entra por baixo do rolo
    step = 1 if left else -1
    for y in range(ROLL_Y0 + 3, ROLL_Y1):
        for k, a in enumerate((150, 85, 36)):
            x = ix + step * k
            if 0 <= x < W and canvas[y, x, 3] > 200:
                px(x, y, RO[0], a)


roll(ROLL_L, True)
roll(ROLL_R, False)

# ------------------------------------------------------------------------------------------ sombra no chão
alpha_now = canvas[:, :, 3] > 0
shadow = np.zeros((H, W), np.float32)
for (dx, dy, a) in ((3, 4, 0.30), (2, 3, 0.22), (1, 2, 0.16)):
    sh = np.roll(np.roll(alpha_now, dy, 0), dx, 1).astype(np.float32) * a
    shadow = np.maximum(shadow, sh)
shadow[alpha_now] = 0
out = canvas.copy()
sm = shadow > 0
out[sm, :3] = 0
out[sm, 3] = shadow[sm] * 255

img = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")
dst = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/lasthorizon/textures/gui/map_sheet.png"
img.save(dst)
print("salvo", dst, img.size)
