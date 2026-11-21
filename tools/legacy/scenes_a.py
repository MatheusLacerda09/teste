import math
import numpy as np
from pxlib import *

WHITE = [0xFFFFFF, 0xF1FAFF, 0xCFEAF8, 0xA4D2EA, 0x84BADA]


def reflect(cv, tgs, hz, tones, al=0.30, seed=5):
    """Reflexo espelhado de nuvens no mar, com ondulação e listras."""
    cols = [None] + [hexc(c) for c in tones]
    for y in range(hz, GH):
        sy = hz - 1 - (y - hz)
        if sy < 0:
            break
        shift = int(round(math.sin(y * 1.3 + seed) * 1.4))
        a = al * (0.45 if (y % 3 == 2) else 1.0) * (1.0 - (y - hz) / (GH - hz) * 0.6)
        for tg in tgs:
            for x in range(GW):
                sx = x - shift
                if 0 <= sx < GW and tg[sy, sx]:
                    cv.px(x, y, cols[tg[sy, sx]], a)


def day_ocean():
    cv = Cv()
    HZ = 33
    cv.vgrad(0, HZ, [(0, 0x2B8AC4), (0.5, 0x58B0DC), (0.85, 0xA9DEEE), (1, 0xD2F2F6)], bands=13)
    # anel fraco em volta do sol (como na foto)
    for y in range(0, HZ):
        for x in range(GW):
            d = math.hypot((x - 58) * 0.9, (y - HZ) * 1.1)
            if 21 <= d < 23:
                cv.px(x, y, 0xFFFFFF, 0.10)
    # brilho do sol no horizonte
    for i, (w, a) in enumerate([(16, .22), (11, .35), (7, .55), (4, .85)]):
        cv.rect(58 - w, HZ - 1 - i // 2, 58 + w, HZ, 0xFFF4C0, a)
    # mar
    cv.vgrad(HZ, GH, [(0, 0x6CCCEA), (0.10, 0x46B0DE), (0.45, 0x2878BE), (1, 0x163F88)], bands=11)
    rnd = np.random.RandomState(3)
    for y in range(HZ + 1, GH):
        k = (y - HZ) / (GH - HZ)
        for _ in range(int(7 + k * 5)):
            x = rnd.randint(-4, GW)
            ln = int(3 + k * 10 + rnd.randint(0, 5))
            if rnd.rand() < 0.62:
                cv.rect(x, y, x + ln, y + 1, 0x9CE3F6 if k < .5 else 0x62BFE8, 0.55)
            else:
                cv.rect(x, y, x + ln, y + 1, 0x0F3470, 0.40)
    # glitter do sol
    for y in range(HZ + 1, HZ + 20):
        k = (y - HZ) / 20
        for _ in range(3 + int(k * 4)):
            x = int(58 + rnd.randn() * (3 + k * 8))
            cv.rect(x, y, x + int(2 + k * 4), y + 1, 0xFFF6C8, 0.9 - k * 0.5)
    cv.rect(0, HZ, GW, HZ + 1, 0xE0F7FA, 0.9)
    # nuvens
    tgs = []
    tgs.append(cumulus(cv, -10, 38, HZ, 31, 0.28, 21, WHITE, cap=5, inner=1.2, fillfrac=0.55))
    tgs.append(cumulus(cv, 76, 128, HZ, 33, 0.72, 22, WHITE, cap=5, inner=1.2, fillfrac=0.55))
    hz_t = [0xFFFFFF, 0xEAF7FB, 0xD2ECF4, 0xB6DCEA, 0xA6D2E4]
    tgs.append(cumulus(cv, 30, 50, HZ, 8, 0.4, 23, hz_t, cap=3, flat=1))
    tgs.append(cumulus(cv, 64, 82, HZ, 9, 0.55, 24, hz_t, cap=3, flat=1))
    tgs.append(cumulus(cv, 42, 70, HZ, 3, 0.5, 25, [0xF4FBFD, 0xE4F4F8, 0xD4ECF2, 0xC2E2EC, 0xB6DAE6], cap=2, flat=1, minr=1.5))
    reflect(cv, tgs, HZ, WHITE)
    return cv


def blue_sky():
    cv = Cv()
    cv.vgrad(0, GH, [(0, 0x6682EC), (1, 0x7E9AF2)], bands=6)
    # nuvens de fundo (mesmo tom do céu, só mais claras)
    far = [0x93AEF4, 0x8AA6F2, 0x839FF0, 0x7B97EC, 0x738EE8]
    cumulus(cv, -5, 60, 50, 24, 0.55, 31, far, cap=4, inner=0.6, flat=0)
    cumulus(cv, 60, 125, 45, 22, 0.4, 32, far, cap=4, inner=0.6, flat=0)
    mid = [0xE9F0FF, 0xD5E2FC, 0xBBD0F8, 0xA0BBF2, 0x93AEEE]
    cumulus(cv, 8, 100, GH, 44, 0.52, 33, mid, cap=4.5, inner=1.6, fillfrac=0.5, flat=0)
    top = [0xFFFFFF, 0xF2F7FF, 0xDCE8FD, 0xC0D4F8, 0xB0C6F4]
    cumulus(cv, 30, 85, GH, 36, 0.5, 34, top, cap=4.0, inner=1.8, fillfrac=0.55, flat=0)
    cumulus(cv, -12, 36, GH, 18, 0.5, 35, top, cap=3.6, inner=1.4, fillfrac=0.6, flat=0)
    cumulus(cv, 82, 130, GH, 20, 0.5, 36, top, cap=3.6, inner=1.4, fillfrac=0.6, flat=0)
    # traço de avião fininho
    for x in range(60, 100):
        cv.px(x, 8 + (x - 60) // 14, 0x93AEF4, 0.8)
    return cv


def teal_sky():
    cv = Cv()
    cv.vgrad(0, GH, [(0, 0x78C2CC), (1, 0x9AD4DA)], bands=6)
    gap = [0x6FB4C0, 0x66ABB8, 0x5FA3B1, 0x589BAA, 0x5192A2]
    cumulus(cv, -5, 122, 40, 30, 0.5, 41, gap, cap=4.5, inner=1.0, flat=0)
    mid = [0xC8EEF2, 0xB2E2E8, 0x9DD6DE, 0x88C8D2, 0x7CBEC9]
    cumulus(cv, -8, 125, GH, 50, 0.45, 42, mid, cap=5, inner=1.6, fillfrac=0.6, flat=0)
    cumulus(cv, 6, 78, GH, 34, 0.5, 43, [0xE8FAFB, 0xD6F3F6, 0xC2EAEE, 0xAEDFE5, 0xA2D7DE],
            cap=4.2, inner=1.8, fillfrac=0.6, flat=0)
    cumulus(cv, -14, 40, GH, 20, 0.6, 44, [0xF4FEFE, 0xE6FAFB, 0xD4F4F6, 0xC0EBEF, 0xB4E4E9],
            cap=3.4, inner=1.4, fillfrac=0.6, flat=0)
    cumulus(cv, 70, 130, GH, 26, 0.4, 45, [0xF4FEFE, 0xE6FAFB, 0xD4F4F6, 0xC0EBEF, 0xB4E4E9],
            cap=3.6, inner=1.4, fillfrac=0.6, flat=0)
    return cv


def gray_storm():
    cv = Cv()
    cv.vgrad(0, GH, [(0, 0xDAD8CB), (0.7, 0xD2D0C2), (1, 0xCFCDBE)], bands=5)
    # núcleo claro no meio, com sol
    cv.disc(62, 22, 3, 0xF4F0E0)
    cv.disc(62, 22, 2, 0xFFFFF4)
    cumulus(cv, 40, 90, GH, 38, 0.5, 51, [0xF2EEDC, 0xE6E2D0, 0xD6D2C0, 0xC2BFAE, 0xB6B3A3],
            cap=3.8, inner=1.2, fillfrac=0.5, flat=0, xstep=(0.9, 1.2))
    dark_l = [0xA6A89E, 0x84877F, 0x626663, 0x4A4E4E, 0x3A3E3F]
    dark_r = [0xA2A49A, 0x7F837B, 0x5E625F, 0x474B4B, 0x383C3D]
    cumulus(cv, -10, 48, GH, 56, 0.28, 52, dark_l, cap=5, inner=2.0, fillfrac=0.75, flat=0, pw=0.5)
    cumulus(cv, 66, 128, GH, 54, 0.78, 53, dark_r, cap=5, inner=2.0, fillfrac=0.75, flat=0, pw=0.5)
    cumulus(cv, 28, 60, GH, 18, 0.5, 54, [0xCFCCB8, 0xB6B4A2, 0x9A9A8E, 0x7E8079, 0x666A66],
            cap=3.2, inner=1.2, fillfrac=0.7, flat=0)
    cv.disc(62, 22, 2, 0xFFFFF4)
    return cv
