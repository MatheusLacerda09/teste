#!/usr/bin/env python3
"""Gera o atlas de animação dos ícones das bandeiras (textures/gui/map_icons_anim.png).

Cada linha do PNG é um ícone de aba e cada coluna é um quadro (FRAMES quadros de 32 x 32). A ordem das linhas é
a da lista ROWS abaixo e tem que bater com MapIcons.ANIM_ROW (Java). O quadro 0 de cada linha é o ícone parado
(igual ao de map_icons32.png); os outros mexem DENTRO do desenho (cabelo ondulando, chama piscando, brilho no ouro...),
nada de subir/descer ou andar de lado.

Os ícones são pixel art de 16 x 16 ampliada 2x. Tudo é editado na grade de 16 x 16 (cada "pixel" lógico = bloco 2 x 2),
assim o jogo, que reduz 32 -> 16 ao desenhar, mostra exatamente o que foi desenhado aqui. Blocos que não mudam
mantêm os pixels originais do atlas de 32 (o ícone de identidade e o dado têm detalhe fino).

Uso: python3 tools/art/make_icon_anim.py [pasta_de_saida] [--preview pasta]
"""
from pathlib import Path
import math
import sys
import numpy as np
from PIL import Image

FRAMES = 8
# linha do atlas -> índice do ícone em map_icons32.png (abas e dado)
ROWS = [0, 1, 2, 3, 25, 26, 5]
HERE = Path(__file__).resolve().parent
SRC = HERE.parent.parent / "src/main/resources/assets/lasthorizon/textures/gui/map_icons32.png"


def load32(i: int) -> np.ndarray:
    a = np.array(Image.open(SRC).convert("RGBA"))
    r, c = divmod(i, 8)
    return a[r * 32:(r + 1) * 32, c * 32:(c + 1) * 32].copy()


def lg(a32: np.ndarray) -> np.ndarray:
    return a32[::2, ::2].copy()


def up(l: np.ndarray) -> np.ndarray:
    return np.repeat(np.repeat(l, 2, 0), 2, 1)


def merge(base32: np.ndarray, base_l: np.ndarray, frame_l: np.ndarray) -> np.ndarray:
    """Sobe o quadro lógico para 32 x 32, mas onde o bloco não mudou volta ao pixel original do atlas."""
    out = up(frame_l)
    for y in range(16):
        for x in range(16):
            if (frame_l[y, x] == base_l[y, x]).all():
                out[y * 2:y * 2 + 2, x * 2:x * 2 + 2] = base32[y * 2:y * 2 + 2, x * 2:x * 2 + 2]
    return out


def mix(c, t, k):
    k = max(0.0, min(1.0, k))
    return tuple(int(round(c[j] + (t[j] - c[j]) * k)) for j in range(3))


def lum(p):
    return 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]


def solid(l, x, y):
    return 0 <= x < 16 and 0 <= y < 16 and l[y, x, 3] > 0


def put(l, x, y, rgb):
    if 0 <= x < 16 and 0 <= y < 16:
        l[y, x] = (rgb[0], rgb[1], rgb[2], 255)


def tint(l, x, y, target, k):
    if solid(l, x, y):
        put(l, x, y, mix(tuple(int(v) for v in l[y, x, :3]), target, k))


def shift_rows(l, dxs):
    """Desloca cada linha lógica y por dxs[y] pixels (sem sair da grade)."""
    out = np.zeros_like(l)
    for y in range(16):
        d = dxs.get(y, 0)
        for x in range(16):
            nx = x + d
            if 0 <= nx < 16 and l[y, x, 3]:
                out[y, nx] = l[y, x]
    return out


def is_gold(p):
    return p[0] > 170 and p[1] > 100 and p[2] < 90 and p[0] - p[2] > 100


def is_red(p):
    return p[0] >= 0x60 and p[1] < 0x34 and p[2] < 0x34


# ---------------------------------------------------------------------------------------------- identidade
def anim_identity(b32):
    b = lg(b32)
    golds = [(x, y) for y in range(16) for x in range(16) if b[y, x, 3] and is_gold(b[y, x])]
    cx = round(sum(x for x, _ in golds) / len(golds))
    cy = round(sum(y for _, y in golds) / len(golds))
    frames = []
    for f in range(FRAMES):
        l = b.copy()
        if 1 <= f <= 6:
            s = 6 + (f - 1) * 4.0                       # faixa diagonal de brilho atravessando o ícone
            for y in range(16):
                for x in range(16):
                    if not solid(l, x, y):
                        continue
                    d = (x + y) - s
                    p = tuple(int(v) for v in l[y, x, :3])
                    if lum(p) < 45:
                        continue                         # contorno escuro não brilha
                    if -1.0 <= d < 1.0:
                        tint(l, x, y, (255, 248, 190) if is_gold(p) else (255, 255, 235), 0.75 if is_gold(p) else 0.5)
                    elif -2.2 <= d < 2.2 and is_gold(p):
                        tint(l, x, y, (255, 236, 140), 0.35)
        star = {3: 1, 4: 2, 5: 1}.get(f, 0)             # estrelinha de 4 pontas no centro do emblema
        if star:
            put(l, cx, cy, (255, 255, 240))
            if star >= 2:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    put(l, cx + dx, cy + dy, (255, 244, 170))
        frames.append(merge(b32, b, l))
    return frames


# ---------------------------------------------------------------------------------------------- corpo
def anim_body(b32):
    b = lg(b32)
    chest = [0.0, 0.2, 0.7, 1.0, 1.0, 0.7, 0.2, 0.0]    # respirar: o peito enche e esvazia
    frames = []
    for f in range(FRAMES):
        l = b.copy()
        k = chest[f]                                      # respirar só no sombreado (mexer no contorno deixava os braços tortos)
        for y in range(8, 14):
            for x in range(8, 11):
                if not solid(l, x, y):
                    continue
                p = tuple(int(v) for v in l[y, x, :3])
                if lum(p) < 150:
                    continue
                if y <= 10:
                    tint(l, x, y, (255, 246, 220), 0.75 * k)          # inspira: o peito clareia
                else:
                    tint(l, x, y, (232, 192, 140), 0.6 * (1.0 - k))   # expira: a barriga ganha sombra
        ys = 1 + f * 2                                    # linha de luz descendo pelo corpo
        for y in (ys, ys + 1):
            for x in range(16):
                p = tuple(int(v) for v in l[y, x, :3]) if solid(l, x, y) and 0 <= y < 16 else None
                if p and lum(p) > 100:
                    tint(l, x, y, (255, 255, 250), 0.55 if y == ys else 0.3)
        frames.append(merge(b32, b, l))
    return frames


# ---------------------------------------------------------------------------------------------- cabelo
def anim_hair(b32):
    b = lg(b32)
    frames = []
    for f in range(FRAMES):
        ph = 2 * math.pi * f / FRAMES
        dxs = {}
        for y in range(5, 14):
            amp = (y - 4) / 8.0 * 1.45
            v = amp * math.sin(ph - 0.62 * y)
            dxs[y] = int(round(v)) if abs(v) >= 0.55 else 0
        l = shift_rows(b, dxs)
        s = -2 + f * 4.0                                  # reflexo de luz correndo pela mecha (de cima para baixo)
        for y in range(16):
            for x in range(16):
                if not solid(l, x, y):
                    continue
                p = tuple(int(v) for v in l[y, x, :3])
                if lum(p) < 55:
                    continue
                d = (y * 1.0 + x * 0.55) - s
                if -1.1 <= d < 1.1:
                    tint(l, x, y, (255, 226, 176), 0.62)
                elif -2.4 <= d < 2.4:
                    tint(l, x, y, (255, 200, 140), 0.22)
        frames.append(merge(b32, b, l))
    return frames


# ---------------------------------------------------------------------------------------------- roupa
def anim_outfit(b32):
    b = lg(b32)
    frames = []
    for f in range(FRAMES):
        l = b.copy()
        for y in range(16):
            for x in range(16):
                if not b[y, x, 3]:
                    continue
                p = tuple(int(v) for v in b[y, x, :3])
                if is_red(p):                             # o lenço ondula: luz e sombra passando pelo pano
                    w = math.sin(2 * math.pi * f / FRAMES - (x * 1.1 + y * 0.8))
                    if w > 0.25:
                        tint(l, x, y, (255, 110, 84), 0.42 * w)
                    elif w < -0.25:
                        tint(l, x, y, (40, 4, 8), 0.34 * -w)
                elif is_gold(p):                          # botões e dragonas dourados piscam um de cada vez
                    ph = (x * 3 + y * 5) % FRAMES
                    d = min((f - ph) % FRAMES, (ph - f) % FRAMES)
                    if d == 0:
                        tint(l, x, y, (255, 246, 178), 0.9)
                    elif d == 1:
                        tint(l, x, y, (255, 226, 120), 0.35)
        frames.append(merge(b32, b, l))
    return frames


# ---------------------------------------------------------------------------------------------- alma
def anim_soul(b32):
    b = lg(b32)
    pulse = [0.15, 0.5, 0.95, 0.5, 0.1, -0.75, -0.35, 0.0]       # o miolo pisca: clareia, apaga um instante, volta
    frames = []
    for f in range(FRAMES):
        ph = 2 * math.pi * f / FRAMES
        dxs = {}
        for y in range(1, 13):
            amp = 1.5 * (1.0 - (y - 1) / 12.0) ** 1.4
            v = amp * math.sin(ph + y * 0.55)
            dxs[y] = int(round(v)) if abs(v) >= 0.5 else 0
        l = shift_rows(b, dxs)
        if f in (2, 6):
            l[1, :, 3] = 0                                # a ponta da chama encurta e alonga
        k = pulse[f]
        for y in range(16):
            for x in range(16):
                if not solid(l, x, y):
                    continue
                p = tuple(int(v) for v in l[y, x, :3])
                if p[1] >= 0xC8:                          # miolo claro
                    if k >= 0:
                        tint(l, x, y, (255, 255, 255), 0.75 * k)
                    else:
                        tint(l, x, y, (8, 149, 170), -k * 0.95)
                elif p[1] >= 0x90 and p[0] < 0x60:        # corpo da chama
                    if k >= 0:
                        tint(l, x, y, (140, 245, 240), 0.35 * k)
                    else:
                        tint(l, x, y, (3, 100, 130), -k * 0.6)
        sparks = {                                        # faíscas subindo dos lados e sumindo
            1: [(12, 8, (190, 255, 250))], 2: [(12, 6, (150, 250, 245))], 3: [(13, 4, (110, 230, 235))],
            5: [(3, 6, (190, 255, 250))], 6: [(3, 4, (150, 250, 245))], 7: [(2, 2, (110, 230, 235))],
        }
        for x, y, c in sparks.get(f, []):
            if not solid(l, x, y):
                put(l, x, y, c)
        frames.append(merge(b32, b, l))
    return frames


# ---------------------------------------------------------------------------------------------- passado
def anim_past(b32):
    b = lg(b32)
    sand = [(x, y) for y in range(5, 11) for x in range(6, 10)
            if b[y, x, 3] and 0x70 <= b[y, x, 0] <= 0xB0 and b[y, x, 1] < 0x70 and b[y, x, 2] < 0x30]
    frames = []
    for f in range(FRAMES):
        l = b.copy()
        for x, y in sand:                                 # a areia desce: um brilho percorre a ampulheta de cima para baixo
            d = y - (4 + f)
            if d == 0:
                tint(l, x, y, (255, 226, 140), 1.0)
            elif d == -1:
                tint(l, x, y, (248, 196, 100), 0.65)
            elif d == -2:
                tint(l, x, y, (230, 160, 70), 0.3)
        s = -3 + f * 4.0                                  # luz suave deslizando pelo pergaminho
        for y in range(16):
            for x in range(16):
                if not solid(l, x, y):
                    continue
                p = tuple(int(v) for v in l[y, x, :3])
                if lum(p) > 170:
                    d = (x + y) - s
                    if -1.2 <= d < 1.2:
                        tint(l, x, y, (255, 250, 225), 0.6)
        frames.append(merge(b32, b, l))
    return frames


# ---------------------------------------------------------------------------------------------- dado
def anim_dice(b32):
    """O dado tem detalhe fino (não é 2x2): só recolore e põe uma estrelinha (em blocos 2 x 2 alinhados)."""
    star_cx, star_cy = 26, 6
    plus = {1: 0, 2: 1, 3: 2, 4: 1, 5: 0}               # tamanho do braço da estrelinha por quadro
    frames = []
    for f in range(FRAMES):
        o = b32.copy()
        k = 0.5 + 0.5 * math.sin(2 * math.pi * f / FRAMES)
        for y in range(32):
            for x in range(32):
                p = tuple(int(v) for v in o[y, x, :3])
                if o[y, x, 3] and is_red(p):              # os pontos vermelhos pulsam
                    o[y, x, :3] = mix(p, (255, 86, 66), 0.55 * k)
        if f in plus:
            arm = plus[f]
            col = (255, 250, 205)
            blocks = [(0, 0)] + [(dx * i, dy * i) for i in range(1, arm + 1) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            for bx, by in blocks:
                x0, y0 = star_cx + bx * 2, star_cy + by * 2
                if 0 <= x0 < 31 and 0 <= y0 < 31:
                    c = (255, 255, 245) if (bx, by) == (0, 0) else col
                    o[y0:y0 + 2, x0:x0 + 2] = (c[0], c[1], c[2], 255)
        frames.append(o)
    return frames


BUILDERS = [anim_identity, anim_body, anim_hair, anim_outfit, anim_soul, anim_past, anim_dice]


def build() -> Image.Image:
    atlas = Image.new("RGBA", (FRAMES * 32, len(ROWS) * 32), (0, 0, 0, 0))
    for r, (icon, fn) in enumerate(zip(ROWS, BUILDERS)):
        base = load32(icon)
        frames = fn(base)
        assert len(frames) == FRAMES
        frames[0] = base if r != 6 else frames[0]         # quadro 0 = ícone parado original (o do dado já sai igual, sem estrela)
        for f, fr in enumerate(frames):
            atlas.alpha_composite(Image.fromarray(fr.astype(np.uint8), "RGBA"), (f * 32, r * 32))
    return atlas


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    prev = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    if prev:
        args = [a for a in args if a != prev]
    out = Path(args[0]) if args else HERE.parent.parent / "src/main/resources/assets/lasthorizon/textures/gui"
    out.mkdir(parents=True, exist_ok=True)
    atlas = build()
    atlas.save(out / "map_icons_anim.png")
    print("salvo", out / "map_icons_anim.png", atlas.size)
    if prev:
        p = Path(prev)
        p.mkdir(parents=True, exist_ok=True)
        z = 4
        for name, bg in (("parch", (227, 199, 141, 255)),):
            board = Image.new("RGBA", (atlas.width * z, atlas.height * z), bg)
            board.alpha_composite(atlas.resize((atlas.width * z, atlas.height * z), Image.Resampling.NEAREST))
            board.save(p / f"anim_{name}.png")


if __name__ == "__main__":
    main()
