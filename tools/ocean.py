"""Cenario UNICO e continuo da aba de conquistas (v2, 234x113 px, 1 pixel de arte = 1 px de GUI).

Mar + ceu + nuvens em pixel art 16-bit que mudam de cor aos poucos (dia -> ... -> noite -> amanhecer).
O Python gera MASCARAS brancas (alfa) e o Java pinta cada mascara com a cor do momento, entao nada faz
fade entre imagens: o mesmo mar e as mesmas nuvens vao trocando de cor.

O que tem aqui:
  * nuvens em 3 faixas de profundidade (estratos distantes, cumulos medios, bancos grandes no horizonte),
    cada uma com 7 tons (brilho de borda, luz, meio, sombra, nucleo escuro e brilho por baixo). As faixas sao
    tiras de 468 px que se repetem sem emenda e o Java as arrasta devagar (parallax);
  * reflexo das nuvens no mar (espelhado, ondulado no Java);
  * mar em 32 quadros em loop perfeito: 5 tons de profundidade, cristas, sombras, tracos horizontais e espuma;
  * estrelas (poeira + brilhantes), via lactea (3 tons), lua com crateras, sol com nucleo, brilho em aneis;
  * tiras de dither para as transicoes do ceu.

Fonte da verdade das cores: MOODS abaixo (vira SeaMoods.java ao rodar build_backgrounds.py).
"""
import math
import numpy as np
from PIL import Image

W, H = 382, 203          # area interna da janela de conquistas (janela de 400 x 230)
HZ = 140                 # linha do horizonte (px)
SX = W / 234.0           # fator de escala em relacao ao desenho original 234 x 113
SYH = HZ / 78.0          # idem na vertical (acima do horizonte)
SY = H / 113.0
SH = H - HZ              # altura do mar (px)
F = 32                   # quadros por ciclo do mar
TAU = 2 * math.pi
STRIP = 2 * W            # largura das tiras de nuvem (2 telas, repete sem emenda)
CX = W // 2

# ------------------------------------------------------------------------------------------------
# PALETAS
COLOR_NAMES = [
    'sky0', 'sky1', 'sky2', 'sky3',                              # degrade do ceu (topo -> horizonte)
    's0', 's1', 's2', 's3', 's4',                                # mar: do horizonte (claro) ao fundo (escuro)
    'crest', 'shadow', 'foam', 'glitter',                        # mar: crista, sombra, espuma, reflexo
    'c0', 'c1', 'c2', 'c3', 'c4', 'c5', 'c6',                    # nuvens: borda, luz, claro, meio, sombra, nucleo, brilho de baixo
    'st0', 'st1',                                                # nuvens finas (estratos)
    'glow', 'sun', 'core',                                       # sol: aneis, disco, miolo
    'moon', 'crater', 'mshade', 'ring', 'mglow',                 # lua: disco, crateras, sombra, contorno, brilho
    'haze', 'bird',                                              # nevoa do horizonte, passaros
    'neb0', 'neb1', 'neb2',                                      # via lactea
    'star1', 'star2',                                            # estrelas
]
NUM_NAMES = ['glow_a', 'sun_y', 'sun_x', 'disc_a', 'moon_a', 'moon_x', 'moon_y',
             'star_a', 'star_b', 'neb_a', 'glit_a', 'haze_a']


def _m(a, b, f=0.5):
    out = 0
    for sh in (16, 8, 0):
        ca, cb = (a >> sh) & 255, (b >> sh) & 255
        out |= int(round(ca + (cb - ca) * f)) << sh
    return out


def mood(sky, s0, s2, s4, sea_fx, clouds, st, sun, moon, misc, nums, neb=(0, 0, 0), stars=(0xFFFFFF, 0xDDE6FF)):
    """Monta uma paleta completa a partir de poucas cores (o resto sai por mistura)."""
    c0, c1, c3, c5, c6 = clouds
    crest, shadow, foam, glitter = sea_fx
    return dict(c=[
        *sky,
        s0, _m(s0, s2), s2, _m(s2, s4), s4,
        crest, shadow, foam, glitter,
        c0, c1, _m(c1, c3), c3, _m(c3, c5), c5, c6,
        *st,
        *sun,
        *moon,
        *misc,
        *neb,
        *stars,
    ], n=nums)


MOODS = [
    # 0 dia de mar aberto
    mood(sky=(0x2B8AC4, 0x4AA4DA, 0x8CD4EC, 0xD2F2F6), s0=0x86DCF0, s2=0x3A98D6, s4=0x1A52A0,
         sea_fx=(0xCFF6FF, 0x103C80, 0xFFFFFF, 0xFFF6C8),
         clouds=(0xFFFFFF, 0xFFFFFF, 0xC4E2F6, 0x6C98D4, 0xA8D4F4), st=(0xE6F6FC, 0xB4DCF0),
         sun=(0xFFF4C0, 0xFFFBE0, 0xFFFFFF), moon=(0xDDEBFF, 0xB4C8E8, 0x9CB4DC, 0x5C78B8, 0x9CB8F0),
         misc=(0xF4FFFF, 0x1C2B4A), nums=[.45, 46, 150, 1, 0, 117, 28, 0, 0, 0, .9, .35],
         neb=(0x2B8AC4, 0x4AA4DA, 0x8CD4EC)),
    # 1 ceu azul
    mood(sky=(0x5870E0, 0x6C8CEC, 0x8CAAF4, 0xB4CCFA), s0=0x7CB8F0, s2=0x4278D0, s4=0x1E3C90,
         sea_fx=(0xBCD8FC, 0x12307A, 0xFFFFFF, 0xDDEBFF),
         clouds=(0xFFFFFF, 0xF4F8FF, 0xBCCEF6, 0x6880D0, 0xA0B8F0), st=(0xD0E0FC, 0x98B4F0),
         sun=(0xDDE8FF, 0xF4F8FF, 0xFFFFFF), moon=(0xDDEBFF, 0xB4C8E8, 0x9CB4DC, 0x5C78B8, 0x9CB8F0),
         misc=(0xE4EEFF, 0x23336A), nums=[.30, 54, 120, .8, 0, 117, 28, 0, 0, 0, .55, .25],
         neb=(0x5870E0, 0x6C8CEC, 0x8CAAF4)),
    # 2 ceu turquesa
    mood(sky=(0x5CB4C8, 0x78C8D2, 0x9AD8DC, 0xBCECE8), s0=0x8ADCE4, s2=0x40A0BA, s4=0x1C5878,
         sea_fx=(0xC0F2F4, 0x123E5C, 0xFFFFFF, 0xE8FFFF),
         clouds=(0xFFFFFF, 0xF2FFFF, 0xB8E0E6, 0x5E9CB4, 0x98D4DC), st=(0xD8F6F4, 0xA0D8DC),
         sun=(0xE0FFFF, 0xF4FFFF, 0xFFFFFF), moon=(0xDDEBFF, 0xB4C8E8, 0x9CB4DC, 0x5C78B8, 0x9CB8F0),
         misc=(0xE8FFFF, 0x1F4048), nums=[.22, 58, 92, .6, 0, 117, 28, 0, 0, 0, .45, .25],
         neb=(0x5CB4C8, 0x78C8D2, 0x9AD8DC)),
    # 3 tempestade cinza
    mood(sky=(0x8C9298, 0xA4A9A6, 0xBFC0B8, 0xD6D4C8), s0=0x94A4AC, s2=0x566672, s4=0x28323E,
         sea_fx=(0xB4C2C8, 0x161E28, 0xEEF2F2, 0xF0F0E0),
         clouds=(0xEEF2F2, 0xD4D8D8, 0x8E98A0, 0x3E4854, 0x6C7884), st=(0xB8BEC0, 0x8C969C),
         sun=(0xF4F0E0, 0xFFFFF4, 0xFFFFFF), moon=(0xDDEBFF, 0xB4C8E8, 0x9CB4DC, 0x5C78B8, 0x9CB8F0),
         misc=(0xD8D8CC, 0x25282A), nums=[.18, 44, 117, .35, 0, 117, 28, 0, 0, 0, .05, .20],
         neb=(0x8C9298, 0xA4A9A6, 0xBFC0B8)),
    # 4 por do sol (nuvens roxas com brilho laranja por baixo)
    mood(sky=(0x7C5C9C, 0xB878A8, 0xF09898, 0xFFB878), s0=0xF4A070, s2=0xB0608A, s4=0x4C3672,
         sea_fx=(0xFFC890, 0x3A2862, 0xFFE6C8, 0xFFD6A0),
         clouds=(0xFFC8A0, 0xC47A98, 0x8E5688, 0x4E2E62, 0xFF9060), st=(0xE8A0A8, 0xB87098),
         sun=(0xFFB070, 0xFFE08A, 0xFFF2B8), moon=(0xDDEBFF, 0xB4C8E8, 0x9CB4DC, 0x5C78B8, 0x9CB8F0),
         misc=(0xFFB080, 0x3A1A3C), nums=[.65, 14, 135, 1, 0, 117, 28, 0, 0, 0, .95, .50],
         neb=(0x7C5C9C, 0xB878A8, 0xF09898)),
    # 5 sol grande no horizonte
    mood(sky=(0x2C3078, 0x7C4488, 0xE86C58, 0xFFA060), s0=0xF89060, s2=0x944E8C, s4=0x402C68,
         sea_fx=(0xFFC080, 0x2E2058, 0xFFEAC0, 0xFFE0A0),
         clouds=(0xFFC070, 0xE27A7C, 0x9A4A80, 0x4A2860, 0xFF8A50), st=(0xE8805C, 0xA8506C),
         sun=(0xFFB868, 0xFFF2A0, 0xFFFFD8), moon=(0xDDEBFF, 0xB4C8E8, 0x9CB4DC, 0x5C78B8, 0x9CB8F0),
         misc=(0xFFA860, 0x1A1024), nums=[.85, 4, 117, 1, 0, 117, 28, 0, 0, 0, 1.0, .60],
         neb=(0x2C3078, 0x7C4488, 0xE86C58)),
    # 6 crepusculo rosa
    mood(sky=(0x8C70B0, 0xC890B8, 0xF4A8A8, 0xFFC8A8), s0=0xF8B0A4, s2=0xAA6EA6, s4=0x52427C,
         sea_fx=(0xFFD0C0, 0x40306C, 0xFFE8EC, 0xFFD2B8),
         clouds=(0xFFE8E0, 0xF8C4C8, 0xB88CB8, 0x6C5890, 0xFFB0A0), st=(0xF4B8C4, 0xC890B8),
         sun=(0xFFC0A0, 0xFFC8A0, 0xFFE0C0), moon=(0xDDEBFF, 0xB4C8E8, 0x9CB4DC, 0x5C78B8, 0x9CB8F0),
         misc=(0xFFC0A8, 0x3A2250), nums=[.40, -14, 117, 0, 0, 117, 28, .12, 0, 0, .25, .45],
         neb=(0x8C70B0, 0xC890B8, 0xF4A8A8)),
    # 7 noite violeta de estrelas brilhantes (nuvens brancas)
    mood(sky=(0x2E2C68, 0x3A3A82, 0x5658A4, 0x7880C0), s0=0x7C8CD0, s2=0x4C58A8, s4=0x242C74,
         sea_fx=(0xA8B8F0, 0x141A52, 0xDCE6FF, 0xDCE6FF),
         clouds=(0xFFFFFF, 0xE8EEF8, 0xA8BCE8, 0x6474BC, 0xB8C8F0), st=(0x6870B8, 0x5258A0),
         sun=(0xFFC0A0, 0xFFF0C0, 0xFFFFFF), moon=(0xDDEBFF, 0xB4C8E8, 0x9CB4DC, 0x5C78B8, 0x9CB8F0),
         misc=(0x8C98D8, 0xB4C2F4), nums=[0, -30, 117, 0, 0, 117, 28, 1, .35, .25, .35, .30],
         neb=(0x3A3A82, 0x5658A4, 0x7880C0), stars=(0xFFFFFF, 0xBCD0FF)),
    # 8 noite de lua cheia (nuvens azuis com borda ciano)
    mood(sky=(0x0A0E26, 0x0E1636, 0x16224C, 0x1E3070), s0=0x1E6EBE, s2=0x0E3F95, s4=0x161C70,
         sea_fx=(0x3C94DC, 0x0A1450, 0xA8E4F4, 0x8CE0F0),
         clouds=(0x58C4EC, 0x2A74BC, 0x0F4496, 0x0C1840, 0x143C84), st=(0x1A2C6C, 0x121C4C),
         sun=(0xFFC0A0, 0xFFF0C0, 0xFFFFFF), moon=(0xA8E8F0, 0x62C0CC, 0x2C8ACC, 0x182C78, 0x2E6AC0),
         misc=(0x1C3C8C, 0x9CB0E8), nums=[0, -30, 117, 0, 1, 117, 30, 1, .45, .15, .8, .25],
         neb=(0x10224E, 0x1C3C84, 0x3C5CB8), stars=(0xFFFFFF, 0xB8D8FF)),
    # 9 via lactea
    mood(sky=(0x000320, 0x00104A, 0x241A70, 0xE08CC0), s0=0xB070B8, s2=0x4A2E84, s4=0x0A0A30,
         sea_fx=(0xF0A8D8, 0x08082A, 0xF4D0F0, 0xFFC8E8),
         clouds=(0xF0A0D8, 0x8450A0, 0x40286C, 0x14103C, 0xD868A8), st=(0x6A3C94, 0x2A1C5C),
         sun=(0xFFC0A0, 0xFFF0C0, 0xFFFFFF), moon=(0xEAEAEE, 0xB4B4BE, 0x8C8C9C, 0x10102C, 0x5A60A0),
         misc=(0xE0A0D0, 0xD8C0F0), nums=[0, -30, 117, 0, 1, 196, 22, 1, 1, 1, .55, .35],
         neb=(0x08206C, 0x2C58C8, 0xB8A0F0), stars=(0xFFFFFF, 0xBCD0FF)),
    # 10 amanhecer
    mood(sky=(0x6C7CC4, 0xB490C8, 0xF4A8B8, 0xFFD0A0), s0=0xFFC8A8, s2=0xC0789C, s4=0x5C4688,
         sea_fx=(0xFFE0C8, 0x4A3470, 0xFFF4E8, 0xFFE8C0),
         clouds=(0xFFF0D0, 0xFFC8B8, 0xC88CB0, 0x786098, 0xFFC890), st=(0xF8C0C8, 0xD098B8),
         sun=(0xFFD8A0, 0xFFE8B0, 0xFFF8E0), moon=(0xDDEBFF, 0xB4C8E8, 0x9CB4DC, 0x5C78B8, 0x9CB8F0),
         misc=(0xFFD8B0, 0x4A2E5A), nums=[.5, -2, 80, .9, 0, 117, 28, 0, 0, 0, .8, .5],
         neb=(0x6C7CC4, 0xB490C8, 0xF4A8B8)),
]
# Posicao do sol e da lua em cada momento (espaco 234 x 78). O Java liga os pontos com uma curva suave, entao o
# sol nasce a esquerda, cruza o ceu, se poe a direita e passa por baixo; a lua nasce a esquerda e se poe a direita.
#            sol (x, y)      lua (x, y)
SKY_PATH = [((75, 42), (-40, -30)),   # 0 dia
            ((117, 56), (-40, -30)),  # 1 ceu azul
            ((160, 48), (-40, -30)),  # 2 turquesa
            ((190, 36), (-40, -30)),  # 3 tempestade
            ((205, 14), (-40, -30)),  # 4 por do sol
            ((212, 3), (-40, -30)),   # 5 sol grande
            ((216, -16), (-40, -30)),  # 6 crepusculo
            ((190, -40), (25, -6)),   # 7 noite violeta
            ((117, -52), (117, 30)),  # 8 lua cheia
            ((50, -40), (196, 22)),   # 9 via lactea
            ((28, 2), (250, -8))]     # 10 amanhecer
for _i, ((_sx, _sy), (_mx, _my)) in enumerate(SKY_PATH):
    _n = MOODS[_i]['n']
    _n[1], _n[2], _n[5], _n[6] = _sy, _sx, _mx, _my
for _m_ in MOODS:
    assert len(_m_['c']) == len(COLOR_NAMES), (len(_m_['c']), len(COLOR_NAMES))
    assert len(_m_['n']) == len(NUM_NAMES)


# ------------------------------------------------------------------------------------------------
# RUIDO PERIODICO (as tiras de nuvem repetem a cada STRIP px)
def noise(h, w, cell, seed, periodic=True):
    rnd = np.random.RandomState(seed)
    gh, gw = max(2, int(round(h / cell))), max(2, int(round(w / cell)))
    g = rnd.rand(gh, gw).astype(np.float32)
    if periodic:
        big = np.tile(g, (1, 3))
        im = Image.fromarray(big, mode='F').resize((w * 3, h), Image.BICUBIC)
        return np.asarray(im)[:, w:2 * w]
    return np.asarray(Image.fromarray(g, mode='F').resize((w, h), Image.BICUBIC))


def hump(u, peak, pw=0.7):
    wd = peak if u < peak else 1.0 - peak
    k = 1.0 - abs(u - peak) / max(wd, 0.2)
    return max(0.0, min(1.0, k)) ** pw


# ------------------------------------------------------------------------------------------------
# NUVENS: lobos redondos pintados de tras pra frente, cada um com luz de cima (cel shading)
# tons da grade: 0 vazio, 1 borda, 2 luz, 3 claro, 4 meio, 5 sombra, 6 nucleo, 7 brilho de baixo
LIGHT = np.array([-0.38, -0.925])


def make_cloud(spec, seed, horizon, stratus=False):
    cx, base, w, h, peak, rmin, rmax = spec
    rnd = np.random.RandomState(seed)
    ys, xs = np.mgrid[0:HZ, 0:STRIP]
    cm = np.zeros((HZ, STRIP), bool)
    cld = np.zeros((HZ, STRIP), float)
    x0 = cx - w / 2.0
    balls = []
    x = 0.0
    while x < w:
        top = hump(x / w, peak) * h * (0.62 + 0.38 * rnd.rand())
        if top < 2.5:
            x += 1.5
            continue
        r = max(2.0, min(rmin + (rmax - rmin) * rnd.rand(), top * 0.72))
        balls.append((x0 + x, base - top + r, r))
        x += r * (0.75 + 0.55 * rnd.rand())
    if not stratus:
        for _ in range(int(w * h / (rmax * rmax * 1.6))):
            bx = rnd.rand() * w
            top = hump(bx / w, peak) * h
            if top < 7:
                continue
            r = max(2.5, min(rmin + (rmax - rmin) * rnd.rand() * 1.15, top * 0.5))
            lo, hi = base - top + r, base - r * 0.55
            if hi <= lo:
                continue
            balls.append((x0 + bx, lo + (hi - lo) * rnd.rand() ** 1.3, r))
        if not horizon:
            x = 0.0
            while x < w:
                top = hump(x / w, peak) * h
                r = max(2.0, min(rmin * 0.95, top * 0.5))
                if top > 5:
                    balls.append((x0 + x, base - r * 0.9, r))
                x += r * (1.0 + 0.5 * rnd.rand())
    balls.sort(key=lambda b: b[1])
    # miolo: preenche o corpo para nao sobrar buracos
    for xx in range(int(x0), int(x0 + w) + 1):
        hh = hump((xx - x0) / w, peak) * h * (0.45 if not stratus else 0.35)
        col = xx % STRIP
        sel = (ys[:, 0] >= base - hh) & (ys[:, 0] < base)
        cm[sel, col] = True
        cld[sel, col] = -0.25
    for (bx, by, r) in balls:
        xr = np.arange(int(math.floor(bx - r - 1)), int(math.ceil(bx + r + 2)))
        yr = np.arange(max(0, int(math.floor(by - r - 1))), min(HZ, int(math.ceil(by + r + 2))))
        if len(yr) == 0:
            continue
        dx = (xr[None, :] + 0.5 - bx)
        dy = (yr[:, None] + 0.5 - by)
        th = np.arctan2(dy, dx)
        rr = r * (1 + 0.07 * np.sin(3 * th + bx) + 0.04 * np.sin(5 * th + by))
        inside = np.hypot(dx, dy) <= rr
        ld = np.clip((dx * LIGHT[0] + dy * LIGHT[1]) / r, -1, 1)
        cols = xr % STRIP
        sub = np.ix_(yr, cols)
        cm[sub] = cm[sub] | inside
        cld[sub] = np.where(inside, ld, cld[sub])
    cm &= ys < base
    if not horizon:
        # barriga irregular
        nz = noise(1, STRIP, 3, seed + 99)[0]
        for xx in range(STRIP):
            ys_ = np.nonzero(cm[:, xx])[0]
            if len(ys_):
                cut = int(round(nz[xx] * 2.6 - 0.4))
                b = ys_.max()
                for k in range(max(0, cut)):
                    if b - k >= 0:
                        cm[b - k, xx] = False
    # profundidade dentro da nuvem
    top = np.where(cm.any(0), cm.argmax(0), 0)
    bot = np.where(cm.any(0), HZ - 1 - cm[::-1].argmax(0), 0)
    dfrac = (ys - top[None, :]) / np.maximum(1.0, (bot - top + 1)[None, :])
    jit = (noise(HZ, STRIP, 6, seed + 7) - 0.5) * 0.14
    s = cld * 0.78 - (dfrac - 0.30) * 0.95 + jit
    tg = np.zeros((HZ, STRIP), np.int8)
    tg[cm] = 6
    tg[cm & (s > -0.55)] = 5
    tg[cm & (s > -0.10)] = 4
    tg[cm & (s > 0.36)] = 3
    tg[cm & (s > 0.76)] = 2
    if not stratus:
        up = np.zeros_like(cm); up[1:] = cm[:-1]
        left = np.roll(cm, 1, axis=1)
        down = np.zeros_like(cm); down[:-1] = cm[1:]
        rim = cm & (~up | (~left & (cld > 0.25)))
        tg[rim] = 1
        if not horizon:
            und = cm & ~down & ~rim
            und &= noise(HZ, STRIP, 3, seed + 31) > 0.30
            tg[und] = 7
            # segunda linha de brilho em alguns pontos
            und2 = np.zeros_like(cm); und2[1:] = und[:-1]
            und2 &= cm & (tg > 2) & (noise(HZ, STRIP, 5, seed + 55) > 0.62)
            tg[und2] = 7
    return tg


def make_strip(specs, seed0, horizon=False, stratus=False):
    out = np.zeros((HZ, STRIP), np.int8)
    for i, sp in enumerate(sorted(specs, key=lambda s: s[1])):
        tg = make_cloud(sp, seed0 + i * 13, horizon, stratus)
        out[tg > 0] = tg[tg > 0]
    return out


# (centro x, base y, largura, altura, pico, raio min, raio max) no espaco 234 x 78; _sc() escala para o tamanho real
NEAR = [(36, 78, 150, 44, .38, 6, 15), (205, 78, 92, 20, .50, 4, 9), (318, 78, 170, 48, .62, 6, 16),
        (440, 78, 110, 26, .45, 4, 10)]
MID = [(70, 38, 84, 22, .45, 4.5, 9.5), (178, 24, 100, 24, .60, 4.5, 10), (268, 56, 74, 15, .50, 3.5, 7),
       (352, 35, 92, 26, .40, 4.5, 10), (430, 62, 60, 12, .50, 3.5, 6.5), (30, 13, 56, 11, .50, 3, 6),
       (240, 10, 64, 10, .5, 3, 6)]
FAR = [(52, 46, 130, 6, .50, 2, 3.2), (214, 30, 150, 5, .45, 2, 3.0), (338, 61, 110, 6, .55, 2, 3.2),
       (440, 18, 90, 5, .5, 2, 3.0), (150, 66, 90, 4, .5, 2, 2.8)]

def _sc(specs):
    return [(cx * SX, (HZ if b == 78 else b * SYH), w * SX, h * SX, p, r0 * SX, r1 * SX)
            for (cx, b, w, h, p, r0, r1) in specs]


NEAR, MID, FAR = _sc(NEAR), _sc(MID), _sc(FAR)
_cache = {}


def strips():
    if not _cache:
        _cache['near'] = make_strip(NEAR, 101, horizon=True)
        _cache['mid'] = make_strip(MID, 211, horizon=False)
        _cache['far'] = make_strip(FAR, 307, horizon=False, stratus=True)
    return _cache


def refl_of(tg):
    """Linhas do mar (0 = logo abaixo do horizonte) = espelho das linhas do ceu (HZ-1 pra cima)."""
    return tg[HZ - 1::-1][:SH]


# ------------------------------------------------------------------------------------------------
# MAR (32 quadros em loop)
SEA_LAYERS = ['s0', 's1', 's2', 's3', 's4', 'shadow', 'crest', 'foam']
_RS = np.random.RandomState(7)
_R = _RS.rand(6, SH, W)
_YS, _XS = np.mgrid[0:SH, 0:W]


def _dashes():
    rnd = np.random.RandomState(1234)
    out = []
    for d in range(1, SH):
        dn = d / SY
        for _ in range(int((2 + dn * 0.20) * SX * 1.1)):
            out.append(dict(d=d, xc=rnd.rand() * W, L=int((4 + dn * 0.8 * (0.5 + rnd.rand())) * SX),
                            light=rnd.rand() < 0.58, ph=rnd.rand() * TAU, amp=(1.5 + rnd.rand() * 3.2) * SX,
                            m=1 + int(rnd.rand() * 2), tall=(dn > 20 and rnd.rand() < 0.5)))
    return out


_DASH = _dashes()


def sea_frame(f):
    t = f / F
    d = _YS.astype(float)
    dn = d / SY                      # linha normalizada (espaco 113 px)
    xn = _XS / SX
    A = (0.35 + 0.075 * dn) * SY
    s1 = np.sin(0.43 / (1 + 0.05 * dn) * xn + TAU * t + dn * 0.35)
    s2 = np.sin(0.95 / (1 + 0.045 * dn) * xn - 2 * TAU * t + dn * 0.60)
    yeff = d + A * (0.75 * s1 + 0.45 * s2)
    q = np.zeros(d.shape, int)
    for b in (3.2, 7.4, 13.2, 21.0):
        q += (yeff >= b * SY)
    qp1 = np.vstack([np.zeros((1, W), int), q[:-1]])
    qp2 = np.vstack([np.zeros((2, W), int), q[:-2]])
    crest = q > qp1
    crest[0] = False
    shadow = (qp1 > qp2) & (q == qp1)
    shadow[:2] = False
    foam = crest & (s1 > 0.60) & (_R[0] < 0.30)
    blink = np.sin(TAU * (1 + (_R[2] > 0.5)) * t + TAU * _R[3])
    foam |= crest & (_R[1] < 0.04) & (blink > 0.6)
    foam |= (_R[4] < 0.0025) & (blink > 0.75) & (_YS > 0)
    for ds in _DASH:
        off = int(round(ds['amp'] * math.sin(TAU * ds['m'] * t + ds['ph'])))
        x1 = int(ds['xc'] + off - ds['L'] / 2)
        xs = np.arange(x1, x1 + ds['L']) % W
        tgt = crest if ds['light'] else shadow
        tgt[ds['d'], xs] = True
        if ds['tall'] and ds['d'] + 1 < SH:
            tgt[ds['d'] + 1, xs] = True
    L = {f's{i}': (q == i) for i in range(5)}
    L['shadow'], L['crest'], L['foam'] = shadow, crest, foam
    return L


# ------------------------------------------------------------------------------------------------
# ESTRELAS e VIA LACTEA (estaticas)
SKY_PX = HZ - int(8 * SYH)   # estrelas somem perto do horizonte


def make_stars():
    rnd = np.random.RandomState(15)
    a1 = np.zeros((HZ, W), float)   # brilhantes (esparsas)
    for _ in range(int(34 * SX * SYH)):
        x, y = rnd.randint(1, W - 1), rnd.randint(1, SKY_PX - 8)
        a1[y, x] = 0.65 + 0.35 * rnd.rand()
    a2 = np.zeros((HZ, W), float)   # poeira densa
    _, neb = make_nebula()
    dens = 0.35 + 0.65 * neb
    for _ in range(int(330 * SX * SYH)):
        x, y = rnd.randint(0, W), rnd.randint(0, SKY_PX)
        if rnd.rand() < dens[y, x] * 0.8:
            a2[y, x] = max(a2[y, x], 0.35 + 0.65 * rnd.rand())
    tiers1 = [np.where((a1 > lo) & (a1 <= hi), 1.0, 0.0) * a1 for lo, hi in ((0, .77), (.77, .89), (.89, 1.01))]
    tiers2 = [np.where((a2 > lo) & (a2 <= hi), 1.0, 0.0) * a2 for lo, hi in ((0, .57), (.57, .79), (.79, 1.01))]
    return tiers1, tiers2


_neb = {}


def make_nebula():
    """Faixa diagonal da via lactea: 3 tons em degraus + mapa de densidade."""
    if 'n' in _neb:
        return _neb['n']
    ys, xs = np.mgrid[0:HZ, 0:W].astype(float)
    # linha da faixa: de (10, 4) ate (205, 76)
    x0, y0, x1, y1 = 4.0 * SX, 2.0 * SYH, 215.0 * SX, 74.0 * SYH
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy)
    dist = ((xs - x0) * dy - (ys - y0) * dx) / ln          # distancia perpendicular (px)
    along = ((xs - x0) * dx + (ys - y0) * dy) / ln
    n1 = noise(HZ, W, 34 * SX, 5, periodic=False)
    n2 = noise(HZ, W, 9 * SX, 6, periodic=False)
    n3 = noise(HZ, W, 3 * SX, 8, periodic=False)
    warp = ((n1 - 0.5) * 20 + (n2 - 0.5) * 7) * SX
    widen = (26 + 10 * np.sin(along / ln * math.pi)) * SX
    core = np.clip(1.0 - np.abs(dist + warp) / widen, 0, 1) ** 0.85
    glow = 0.72 + 0.45 * np.clip(along / ln, 0, 1)          # mais forte perto do horizonte
    dens = core * glow * (0.62 + 0.80 * n2) + (n3 - 0.5) * 0.10
    dens = np.clip(dens, 0, 1.2)
    layers = [(dens > 0.16), (dens > 0.42), (dens > 0.78)]
    _neb['n'] = (layers, np.clip(dens, 0, 1))
    return _neb['n']


# ------------------------------------------------------------------------------------------------
# PROPS (atlas 512x256): lua, sol, brilho em aneis, tiras de dither
P_RING, P_MOON, P_CRATER, P_MSHADE = (0, 0, 37, 37), (38, 0, 37, 37), (76, 0, 37, 37), (114, 0, 37, 37)
P_SUN, P_CORE = (152, 0, 29, 29), (182, 0, 19, 19)
P_GLOW = (0, 40, 193, 193)
P_DITH_A, P_DITH_B = (0, 240, W, 1), (0, 242, W, 1)
PS = 1.5   # escala dos sprites da lua e do sol em relacao ao desenho de 25 px


_BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def _dither(v, levels):
    """Quantiza uma intensidade 0..1 em `levels` degraus com trama (Bayer 4x4): degrade em pixel art, sem aneis duros."""
    h, w = v.shape
    thr = np.tile(_BAYER, (h // 4 + 1, w // 4 + 1))[:h, :w]
    return np.clip(np.floor(v * levels + thr), 0, levels) / levels


RAYS = 353   # tamanho do sprite de raios (raio de 176 px)


def make_rays():
    """Raios de luz suaves (alfa continuo, sem trama): duas folhas (raios pares e impares) que o Java faz
    brilhar em alternancia. Cada raio e um leque que abre com a distancia e some antes da borda do sprite."""
    n, c = RAYS, RAYS // 2
    ys, xs = np.mgrid[0:n, 0:n].astype(float)
    dx, dy = xs - c, ys - c
    r = np.hypot(dx, dy)
    th = np.arctan2(dy, dx)
    rnd = np.random.RandomState(21)
    cnt = 14
    out = [np.zeros((n, n)), np.zeros((n, n))]
    for i in range(cnt):
        ang = TAU * (i + 0.5) / cnt + (rnd.rand() - 0.5) * 0.18
        hw = 0.05 + 0.05 * rnd.rand()
        length = c * (0.62 + 0.36 * rnd.rand())
        d = np.abs((th - ang + math.pi) % TAU - math.pi)
        sigma = hw * (0.55 + 0.9 * np.clip(r / c, 0, 1))
        wedge = np.exp(-(d / sigma) ** 2)
        t0 = np.clip((r - 22) / 40.0, 0, 1)
        fade = (t0 * t0 * (3 - 2 * t0)) * np.clip(1 - r / length, 0, 1) ** 1.6
        out[i % 2] = np.maximum(out[i % 2], wedge * fade)
    return [np.clip(o * 0.75, 0, 1) for o in out]


def make_props():
    a = np.zeros((256, 512), float)

    def put(box, arr):
        u, v, w, h = box
        a[v:v + h, u:u + w] = arr

    n = 37
    ys, xs = np.mgrid[0:n, 0:n].astype(float)
    c = 18.0
    r = np.hypot(xs - c, ys - c)
    disc = r <= 10.3 * PS
    put(P_RING, ((r <= 11.9 * PS) & ~disc).astype(float))
    put(P_MOON, disc.astype(float))
    cr = np.zeros((n, n), bool)
    for (cx_, cy_, rx, ry) in ((14, 7, 3.6, 2.6), (9, 12, 2.4, 3.4), (16, 14, 2.6, 2.2), (12, 18, 3.0, 1.8),
                               (7, 6, 1.8, 1.4), (17, 10, 1.6, 2.0), (11, 5, 1.6, 1.2), (15, 18, 1.8, 1.4),
                               (6, 15, 1.4, 1.8)):
        cr |= (((xs - (c + (cx_ - 12) * PS)) / (rx * PS)) ** 2 + ((ys - (c + (cy_ - 12) * PS)) / (ry * PS)) ** 2) <= 1.0
    cr &= noise(n, n, 3, 3, periodic=False) > 0.18
    put(P_CRATER, (cr & disc).astype(float))
    lit = np.hypot(xs - (c + 1.8 * PS), ys - (c - 1.8 * PS)) <= 9.6 * PS
    put(P_MSHADE, (disc & ~lit).astype(float))

    ys, xs = np.mgrid[0:29, 0:29].astype(float)
    put(P_SUN, (np.hypot(xs - 14, ys - 14) <= 14.2).astype(float))
    ys, xs = np.mgrid[0:19, 0:19].astype(float)
    put(P_CORE, (np.hypot(xs - 9, ys - 9) <= 9.4).astype(float))

    ys, xs = np.mgrid[0:193, 0:193].astype(float)
    r = np.hypot(xs - 96, ys - 96)
    inten = np.clip(1 - r / 96.0, 0, 1) ** 2.4 * 0.95
    put(P_GLOW, inten)
    ck = ((np.arange(W) % 2) == 0).astype(float)
    put(P_DITH_A, ck[None, :])
    put(P_DITH_B, (1 - ck)[None, :])
    img = np.zeros((256, 512, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')


def to_rgba(alpha):
    img = np.zeros(alpha.shape + (4,), np.uint8)
    img[..., :3] = 255
    img[..., 3] = np.clip(np.round(alpha * 255), 0, 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')
