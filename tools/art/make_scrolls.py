#!/usr/bin/env python3
"""Redesenha os dois rolos laterais do mapa em textures/gui/map_sheet.png.

Papel enrolado: cilindro com luz da esquerda, boca de cima com a espiral do papel, voltas diagonais e a curva de baixo
com as voltas aparecendo. Sem pinos, sem fitas, sem pontas soltas: o rolo e so o proprio papel enrolado, encostado
sem fitas, sem pontas soltas: o rolo e so o proprio papel enrolado, encostado na borda da folha. Parte sempre do
original (tools/art/map_sheet_base.png).
"""
import math, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'lasthorizon', 'textures', 'gui', 'map_sheet.png')
BASE = os.path.join(HERE, 'map_sheet_base.png')

OL = (96, 48, 34, 255)         # contorno marrom-avermelhado
PAL = [(255, 244, 208, 255), (249, 227, 171, 255), (240, 208, 142, 255), (222, 176, 110, 255), (190, 134, 84, 255), (146, 92, 58, 255)]
CLEAR = (0, 0, 0, 0)

RW, RH = 20, 214               # largura e altura do rolo
TOP_Y, BOT_Y = 10, 203         # centros das bocas (cima e baixo)
RX_, RY_ = 9.5, 4.8


def band(u):
    """Cilindro de papel: luz vindo da esquerda, sombra e luz refletida na direita. u em -1..1. Faixas limpas, sem pontilhado."""
    nz = math.sqrt(max(0.0, 1 - u * u))
    lam = max(0.0, -0.55 * u + 0.62 * nz)
    rim = 0.18 * max(0.0, u - 0.66) / 0.34
    v = (1.0 - min(1.0, lam + rim)) * 5.4
    return max(0, min(5, int(v)))


def make_roll():
    img = Image.new('RGBA', (RW, RH), CLEAR)
    px = img.load()
    cx = (RW - 1) / 2.0

    def put(x, y, c):
        if 0 <= x < RW and 0 <= y < RH:
            px[x, y] = c

    # corpo: cilindro de ponta a ponta, contorno dos dois lados
    for y in range(TOP_Y, BOT_Y + 1):
        for x in range(RW):
            if x in (0, RW - 1):
                put(x, y, OL)
                continue
            u = (x - cx) / (RW / 2.0 - 1.0)
            put(x, y, PAL[band(u)])
    # voltas do papel: bordas diagonais (luz em cima, sombra embaixo), como as camadas de uma folha enrolada
    for yy, sl in ((46, 3), (101, -3), (150, 3)):
        for x in range(1, RW - 1):
            y = yy + (x // sl if sl > 0 else -(x // -sl))
            u = (x - cx) / (RW / 2.0 - 1.0)
            k = band(u)
            put(x, y - 1, PAL[max(0, k - 1)])
            put(x, y, PAL[min(4, k + 2)])
            put(x, y + 1, PAL[min(5, k + 1)])
    # boca de cima: a ponta do rolo vista de cima, com a espiral do papel
    for y in range(TOP_Y - 6, TOP_Y + 7):
        for x in range(RW):
            ex, ey = (x - cx) / RX_, (y - TOP_Y) / RY_
            d = ex * ex + ey * ey
            if d > 1.0:
                if y < TOP_Y:
                    put(x, y, CLEAR)
                continue
            if d > 0.72:
                put(x, y, OL)
                continue
            r = math.sqrt(d)
            ang = math.atan2(ey, ex)
            tt = (r * 3.0 + ang / (2 * math.pi)) % 1.0
            if r < 0.15:
                c = PAL[5]
            elif tt < 0.17:
                c = PAL[4]
            elif tt < 0.30:
                c = PAL[3]
            else:
                c = PAL[0] if (ex < -0.1 and tt > 0.55) else PAL[1]
            put(x, y, c)
    # fundo: a curva de baixo do rolo, com as voltas do papel aparecendo em arcos
    for y in range(BOT_Y - 1, BOT_Y + 7):
        for x in range(RW):
            ex, ey = (x - cx) / RX_, (y - BOT_Y) / RY_
            d = ex * ex + ey * ey
            if ey < 0 and d <= 1.0:
                continue
            if d > 1.0:
                if ey >= 0:
                    put(x, y, CLEAR)
                continue
            if d > 0.80:
                put(x, y, OL)
            elif ey < 0:
                continue
            elif d > 0.66:
                put(x, y, PAL[3] if x < cx else PAL[4])
            elif d > 0.56:
                put(x, y, PAL[5])
            elif d > 0.44:
                put(x, y, PAL[1] if x < cx else PAL[2])
            elif d > 0.36:
                put(x, y, PAL[4])
            else:
                u = (x - cx) / (RW / 2.0 - 1.0)
                put(x, y, PAL[band(u)])
    return img


def shadow_strip(img, xs, y0, y1, a0):
    """Sombra de contato do rolo sobre a folha (escurece os pixels que ja existem)."""
    for i, x in enumerate(xs):
        a = a0 * (1.0 - i / float(len(xs)))
        for y in range(y0, y1):
            p = img.getpixel((x, y))
            if p[3] == 0:
                continue
            f = 1.0 - a / 255.0
            img.putpixel((x, y), (int(p[0] * f), int(p[1] * f), int(p[2] * f), p[3]))


def main():
    sheet = Image.open(BASE).convert('RGBA')
    roll = make_roll()
    # esquerda: limpa 22..39, rolo em x=23..42
    for x in range(22, 40):
        for y in range(sheet.height):
            sheet.putpixel((x, y), CLEAR)
    shadow_strip(sheet, [43, 44, 45, 46, 47, 48], 12, 205, 60)
    sheet.alpha_composite(roll, (23, 3))
    # direita: limpa 368..391, rolo em x=365..384
    for x in range(368, 392):
        for y in range(sheet.height):
            sheet.putpixel((x, y), CLEAR)
    shadow_strip(sheet, [364, 363, 362, 361, 360, 359], 12, 205, 80)
    sheet.alpha_composite(roll, (365, 3))
    for x in range(385, 389):
        for y in range(14, 220):
            if sheet.getpixel((x, y))[3] == 0:
                sheet.putpixel((x, y), (0, 0, 0, 84 - (x - 385) * 18))
    sheet.save(RES)
    print('ok', sheet.size)


if __name__ == '__main__':
    main()
