import math
import numpy as np
from pxlib import *

LIT = [0xFFD6B2, 0xFF9E5C, 0xDA666A, 0xA65072, 0x7E4670]
RED = [0xF2906A, 0xD2685E, 0xB4585F, 0x9A4E64, 0x84465F]
DRK = [0xC9645F, 0xB0565F, 0x964C66, 0x824468, 0x6E3E63]


def red_sunset():
    cv = Cv()
    cv.vgrad(0, GH, [(0, 0x4B69A6), (1, 0x6A7EB0)], bands=6)
    cumulus(cv, -10, 52, 28, 26, 0.35, 61, DRK, cap=4.2, inner=1.4, fillfrac=0.7, flat=0, ragged=True)
    cumulus(cv, 60, 130, 24, 24, 0.7, 62, DRK, cap=4.2, inner=1.4, fillfrac=0.7, flat=0, ragged=True)
    cumulus(cv, -12, 128, GH, 30, 0.5, 63, RED, cap=4.6, inner=1.6, fillfrac=0.7, flat=0)
    cumulus(cv, 30, 92, GH, 56, 0.45, 64, LIT, cap=4.4, inner=1.8, fillfrac=0.6, flat=0)
    cumulus(cv, 78, 130, GH, 34, 0.55, 65, LIT, cap=4.0, inner=1.6, fillfrac=0.6, flat=0)
    cumulus(cv, -14, 34, GH, 22, 0.6, 66, LIT, cap=3.6, inner=1.4, fillfrac=0.6, flat=0)
    # rastro de avião
    for x in range(34, 76):
        cv.px(x, 15 + (x - 34) // 22, 0xC9566A, 0.9)
    return cv


def sunset_sun():
    cv = Cv()
    W, H = GW, GH
    cx, cy = 58, 32
    # céu azul-arroxeado nos cantos
    cv.vgrad(0, H, [(0, 0x424590), (1, 0x5A5496)], bands=6)
    # anéis concentricos do sol (cada anel = 3 células)
    ring = [0xFFF08C, 0xFFE27C, 0xFFCB6C, 0xFFB45E, 0xFF9C56, 0xF98852, 0xF0764F, 0xE46A52,
            0xD8625A, 0xC85A66, 0xB65672, 0xA25282]
    for y in range(H):
        for x in range(W):
            d = math.hypot((x - cx) * 1.0, (y - cy) * 1.0)
            k = int((d - 15.0) // 3.2) + 1
            if d <= 15.0:
                cv.px(x, y, ring[0])
            elif k < len(ring):
                cv.px(x, y, ring[k])
    # miolo do sol com brilho
    cv.disc(cx - 3, cy - 3, 9, 0xFFF6A8, 0.65)
    cv.disc(cx - 5, cy - 5, 4, 0xFFFBD0, 0.7)
    # anéis recebem pontinhos (poeira de luz)
    rnd = np.random.RandomState(8)
    for _ in range(90):
        x, y = rnd.randint(0, W), rnd.randint(0, 46)
        d = math.hypot(x - cx, y - cy)
        if 15 < d < 42:
            cv.px(x, y, 0xFFE6B0, 0.7)
    # céu de cima, fora do halo, com estrelas
    stars(cv, 70, 9, 26, cols=(0xFFD0A8, 0xFFE8C8, 0xE8B8C8),
          region=lambda x, y: math.hypot(x - cx, y - cy) > 41)
    # cúpula de nuvens ao redor do sol (borda interna iluminada)
    dome_tones = [0xFFB468, 0xE9805E, 0xB5586E, 0x8A4C78, 0x6C4474]
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - cx, (y - cy) * 1.0)
            ang = math.atan2(y - cy, x - cx)
            edge = 40.5 + 2.2 * abs(math.sin(ang * 9 + 0.7)) + 1.4 * math.sin(ang * 23)
            if d > edge and y < 36:
                t = d - edge
                k = 0 if t < 1.6 else 1 if t < 3.4 else 2 if t < 6.5 else 3
                cv.px(x, y, dome_tones[k])
    # nuvens baixas escuras dos lados do sol (contra-luz, com borda alaranjada)
    sil = [0xFFAE6A, 0xC9627A, 0x93507E, 0x7A4A80, 0x62427A]
    cumulus(cv, -8, 46, 32, 9, 0.5, 71, sil, cap=3.4, inner=0.8, fillfrac=0.5, flat=1, light=(0.0, -1.0))
    cumulus(cv, 68, 126, 33, 10, 0.5, 72, sil, cap=3.4, inner=0.8, fillfrac=0.5, flat=1, light=(0.0, -1.0))
    cumulus(cv, 26, 52, 29, 5, 0.4, 73, sil, cap=2.4, inner=0.5, fillfrac=0.5, flat=1, light=(0.0, -1.0))
    # raios: finos, saindo do sol para cima
    for ang_deg, ln in ((-29, 30), (0, 33), (31, 34)):
        a = math.radians(ang_deg)
        for r in range(15, 15 + ln):
            x = int(round(cx + math.sin(a) * r))
            y = int(round(cy - math.cos(a) * r))
            cv.px(x, y, 0xFFE0A0, 0.65 * (1 - (r - 15) / ln))
    for r in range(16, 48):          # feixe largo, vertical
        for dx in (-1, 0, 1):
            cv.px(cx + dx, cy - r, 0xFFD890, 0.28)
    # faixa do mar e montanha
    SEA = 47
    cv.vgrad(SEA, 51, [(0, 0xB65A78), (1, 0x6C4C86)], bands=4)
    cv.rect(0, SEA, W, SEA + 1, 0xFF9E62, 0.9)
    for x in range(30, 88):
        k = abs(x - 58) / 30
        cv.px(x, 49, 0xFFC27A, max(0.0, 0.8 - k))
        if x % 3: cv.px(x, 50, 0xFFB070, max(0.0, 0.6 - k))
    # montanha distante (triângulo suave)
    for x in range(14, 78):
        top = int(round(46 - 8.5 * max(0.0, 1 - abs(x - 44) / 30) ** 1.2))
        for y in range(top, SEA + 1):
            cv.px(x, y, 0x82467C if y > top else 0xB45E7E)
    # nuvens grandes da frente (laranja iluminadas)
    cumulus(cv, -16, 52, 53, 16, 0.55, 74, [0xFFC890, 0xFFA060, 0xE46E5E, 0xB65072, 0x8C4672],
            cap=4.0, inner=1.3, fillfrac=0.6, flat=0)
    cumulus(cv, 66, 134, 53, 19, 0.45, 75, [0xFFC890, 0xFFA060, 0xE46E5E, 0xB65072, 0x8C4672],
            cap=4.2, inner=1.3, fillfrac=0.6, flat=0)
    cumulus(cv, 38, 80, 52, 6, 0.5, 76, [0xFFB27A, 0xFF9060, 0xE2705E, 0xB65572, 0x94496E],
            cap=2.6, inner=0.6, fillfrac=0.6, flat=0)
    # raios largos descendo (translúcidos)
    for ang_deg in (-48, -20, 20, 48):
        a = math.radians(ang_deg)
        for r in range(8, 70):
            for w in range(-2, 3):
                x = int(round(cx + math.sin(a) * r + w * math.cos(a)))
                y = int(round(cy + math.cos(a) * r))
                if y >= 34:
                    cv.px(x, y, 0xFFB070, 0.10)
    # silhueta de mata na frente
    rnd = np.random.RandomState(12)
    h = 3.0
    for x in range(W):
        h = max(2.0, min(7.0, h + rnd.randn() * 1.3))
        top = H - int(h)
        for y in range(top, H):
            cv.px(x, y, 0x2C2036 if y > top else 0x3E2A44)
    return cv


def pink_dusk():
    cv = Cv()
    cv.vgrad(0, GH, [(0, 0xB48AB6), (0.30, 0xDB98B6), (0.55, 0xF9A8B4), (0.8, 0xFFB090), (1, 0xFFA07A)], bands=11)
    # nuvenzinhas laranja nas frestas
    cumulus(cv, 10, 62, 46, 14, 0.4, 81, [0xFFC8A4, 0xFFAE88, 0xFF9876, 0xF08672, 0xE07C70], cap=3.6, inner=0.8, flat=0)
    cumulus(cv, 62, 112, 44, 14, 0.5, 82, [0xFFC8A4, 0xFFAE88, 0xFF9876, 0xF08672, 0xE07C70], cap=3.6, inner=0.8, flat=0)
    # nuvem rosa clara de fundo
    cumulus(cv, 6, 90, 36, 10, 0.5, 83, [0xFFD6D8, 0xFFC4CC, 0xFFB4C0, 0xF8A4B4, 0xF0A0B0], cap=3, inner=0.5, flat=0, fillfrac=0.6, ragged=True)
    P = [0xD8A0C4, 0xB57AAE, 0x9A6498, 0x835488, 0x6E4678]
    cumulus(cv, -18, 54, 34, 30, 0.45, 84, P, cap=4.4, inner=1.5, fillfrac=0.7, flat=0, ragged=True)   # esquerda-cima
    cumulus(cv, 6, 36, 22, 12, 0.3, 85, P, cap=3.4, inner=1.0, fillfrac=0.7, flat=0, ragged=True)
    cumulus(cv, 76, 134, 34, 26, 0.55, 86, P, cap=4.4, inner=1.5, fillfrac=0.7, flat=0, ragged=True)   # direita
    cumulus(cv, 46, 84, 14, 10, 0.5, 87, P, cap=3.2, inner=0.8, fillfrac=0.7, flat=0, ragged=True)
    cumulus(cv, 14, 98, GH + 4, 26, 0.5, 88, P, cap=4.6, inner=1.8, fillfrac=0.7, flat=0)  # baixo
    cumulus(cv, -10, 40, GH + 2, 18, 0.7, 89, P, cap=4.0, inner=1.2, fillfrac=0.7, flat=0)
    cumulus(cv, 84, 130, GH + 2, 22, 0.4, 90, P, cap=4.0, inner=1.2, fillfrac=0.7, flat=0)
    return cv


def night():
    cv = Cv()
    cv.vgrad(0, GH, [(0, 0x2C2E68), (0.5, 0x34377A), (1, 0x4A52A0)], bands=9)
    # camadas de neblina em faixas, como na foto
    bands = [(18, 0x383C86, .55), (24, 0x41479A, .55), (30, 0x4A52A8, .6), (36, 0x5560B2, .7)]
    rnd = np.random.RandomState(14)
    for (yy, c, a) in bands:
        for x in range(GW):
            h = int(2 + 2 * math.sin(x * 0.11 + yy) + 1.5 * math.sin(x * 0.27 + yy * 2))
            for y in range(yy - h, yy + 6):
                cv.px(x, y, c, a)
    # morro de nuvem ao fundo
    cumulus(cv, 30, 70, 40, 10, 0.5, 91, [0x7E88CE, 0x7078C4, 0x646EBC, 0x5A64B4, 0x525CAC], cap=3, inner=0.5, flat=0)
    # mar de nuvens iluminado embaixo
    T = [0xF2F7FF, 0xD2E1FA, 0xAEC4EE, 0x8BA3DE, 0x7088CC]
    cumulus(cv, -10, 70, GH, 24, 0.12, 92, T, cap=4.2, inner=1.6, fillfrac=0.75, flat=0, pw=0.5)
    cumulus(cv, 50, 130, GH, 26, 0.55, 93, T, cap=4.2, inner=1.6, fillfrac=0.75, flat=0, pw=0.5)
    cumulus(cv, 20, 100, GH, 14, 0.5, 94, T, cap=3.6, inner=1.2, fillfrac=0.8, flat=0, pw=0.5)
    # estrelas: pontos, cruzes e brilhos
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
    return cv
