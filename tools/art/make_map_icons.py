#!/usr/bin/env python3
"""Gera o atlas de ícones (map_icons.png 16x16 e map_icons32.png 32x32).

Base: a folha enviada pelo usuário (icons16_reference.png, 1024x640). Ela é pixel art
com pixel de 8x8, ou seja, 128x80 "pixels de verdade" = 8 colunas x 5 linhas de 16x16.

v3 (correção de ícones cortados / misturados):
  * Cada célula é lida na grade nativa (1 pixel da arte = 1 pixel do atlas), sem
    recortar por caixa, sem reamostrar e sem apagar componentes. Nada é cortado e
    nenhum ícone invade a célula do vizinho.
  * Os ícones que na folha vinham quebrados (rosto, estilo de cabelo, acessório, farol,
    balança) e os novos (raposa, barba) são desenhados à mão em OVERRIDES abaixo,
    sempre com pelo menos 1 pixel de margem transparente na célula.
  * Restos soltos de células vizinhas (dado, estrela do sonho) são limpos em CLEAN.

v4 (escolha de raça na criação de personagem):
  * O atlas ganhou uma 6ª linha (ROWS = 6). A folha de referência continua com 5 linhas
    (SHEET_ROWS); tudo da linha 6 em diante é desenhado à mão em OVERRIDES.
  * 40 = punho (card da raça Humana) e 41 = globo (título da aba Raça), cada um com a
    sua própria paleta (PALETTES), no mesmo padrão dos demais: grade 16x16, contorno
    (2, 4, 20), pelo menos 1 pixel de margem em volta, luz vindo de cima à esquerda.
  * Os slots 42 a 47 ficam vazios (reservados).

O atlas de 32x32 é o de 16x16 ampliado 2x (nearest).
"""
from pathlib import Path
import sys
import numpy as np
from PIL import Image

COLS, ROWS = 8, 6          # atlas final: 8 colunas x 6 linhas (48 ícones)
SHEET_ROWS = 5             # a folha de referência só tem 5 linhas
REFERENCE = Path(__file__).with_name("icons16_reference.png")
BACKGROUND = np.array([227, 199, 141], dtype=np.int16)

# ---- paleta dos desenhos à mão (mesmo contorno escuro da folha) ----
PAL = {
    ".": None,
    "K": (2, 4, 20),        # contorno
    "W": (250, 244, 236),   # creme
    "w": (200, 190, 184),   # creme sombra
    "S": (244, 194, 145),   # pele
    "s": (205, 137, 83),    # pele sombra
    "H": (106, 57, 30),     # cabelo / barba marrom
    "h": (150, 90, 52),     # luz do cabelo
    "B": (132, 78, 48),     # barba
    "b": (176, 112, 70),    # luz da barba
    "d": (86, 44, 26),      # sombra da barba
    "G": (240, 180, 48),    # ouro
    "g": (168, 112, 24),    # ouro escuro
    "Y": (255, 232, 120),   # luz amarela
    "R": (200, 40, 44),     # vermelho
    "r": (140, 26, 36),     # vermelho escuro
    "N": (30, 52, 92),      # azul marinho
    "n": (18, 30, 60),      # marinho escuro
    "A": (74, 116, 176),    # azul claro
    "E": (190, 196, 210),   # prata
    "e": (130, 138, 160),   # prata sombra
    "T": (60, 56, 70),      # rocha
    "t": (40, 38, 52),      # rocha escura
    "P": (250, 170, 160),   # rosa (orelha da raposa)
    "M": (90, 40, 20),      # marrom escuro
    "O": (214, 112, 32),    # laranja da raposa
    "L": (244, 154, 62),    # laranja claro
    "D": (160, 76, 24),     # laranja escuro
}

OVERRIDES = {}

OVERRIDES[36] = """
................
...KK......KK...
..KMPK....KPMK..
..KMPPKKKKPPMK..
..KLLOOOOOODDK..
..KLOKOOOOKODK..
..KWLOOOOOODWK..
...KWWWKKWWwK...
....KWWWWWwK....
...KLOWWWWwDK...
...KLOWWWWwDK...
...KLOOWWwODK...
..KLOOOOOOODDK..
.KMOOOOOOOODDWK.
..KKKKKKKKKKKK..
................
"""

# 32 - barba (nova)
OVERRIDES[32] = """
................
....KKKKKKKK....
...KHHHHHHHHK...
..KHHhHHHHHHHK..
..KHHHHHHHHHHK..
..KHSHHSSHHSHK..
..KSSKSSSSKSSK..
..KBSSSssSSSBK..
..KBBBBBBBBBBK..
..KBBbBddBbBBK..
...KBBBBBBBBK...
...KBbBBBBbBK...
....KBBBBBBK....
.KKSSKBBBBKSSKK.
.KSSSSKKKKSSSSK.
................
"""

# 40 - punho (card da raça Humana). Punho de frente, 4 nós dos dedos com um brilho em cada,
# polegar atravessado e manga azul com debrum dourado. Paleta própria: PAL_FIST.
OVERRIDES[40] = """
................
.....KK.KK......
..KKKW2KW3KKK...
.KW2K23423KW3K..
.K124124124124K.
.K234234234235K.
.K334334334335K.
.K112222223345K.
.K222222222445K.
.K333333334445K.
..K3334444455K..
...KYYGGGGggK...
...KabbcccddK...
...KbccccdddK...
....KKKKKKKK....
................
"""

# 41 - globo (título da aba Raça). Esfera iluminada de cima à esquerda (África e Europa, deserto
# do Saara em areia), um meridiano dourado do lado direito e pedestal. Paleta própria: PAL_GLOBE.
OVERRIDES[41] = """
................
.....KKKKKK.....
...KKpppqYYKK...
..KpWoMMMNNYGK..
..KooMMpMNNYGK..
.KpppDppqNEFGgK.
.KppDDDDEEEsGgK.
.KqpMMMNNNFsGgK.
.KNwwqNNNNstggK.
..KqqqrNNOsGgK..
..KrrrrOOstggK..
...KKssstggKK...
....KKKggKK.....
...KYYYGGggK....
....KKKKKKK.....
................
"""


def helm() -> Image.Image:
    """Leme baseado no ícone de leme do texto das conquistas (font/icons.png): disco bege com 8 manoplas e joia vermelha."""
    tan, light, shade, dark, spot = (215, 154, 74), (239, 194, 122), (179, 116, 48), (74, 44, 20), (74, 44, 20)
    red, cream = (214, 52, 40), (251, 233, 180)
    px = {}
    cx = cy = 7.5
    dist = lambda x, y: ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
    for y in range(16):
        for x in range(16):
            if dist(x, y) <= 4.4:
                px[(x, y)] = light if x + y < 12 else (shade if x + y > 17 else tan)
    handles = [(7, 2), (8, 2), (7, 3), (8, 3), (7, 12), (8, 12), (7, 13), (8, 13),
               (2, 7), (2, 8), (3, 7), (3, 8), (12, 7), (12, 8), (13, 7), (13, 8),
               (3, 3), (4, 4), (12, 3), (11, 4), (3, 12), (4, 11), (12, 12), (11, 11)]
    for q in handles:
        px[q] = light if q[0] + q[1] < 15 else tan
    for q in [(5, 5), (10, 5), (5, 10), (10, 10), (7, 4), (8, 11), (4, 8), (11, 7)]:
        px[q] = spot
    for y in range(16):
        for x in range(16):
            d = dist(x, y)
            if d <= 1.6:
                px[(x, y)] = red
            elif d <= 2.3:
                px[(x, y)] = cream
    px[(7, 7)] = (255, 120, 90)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    edge = {(x + dx, y + dy) for (x, y) in px for dx in (-1, 0, 1) for dy in (-1, 0, 1)
            if (x + dx, y + dy) not in px and 0 <= x + dx < 16 and 0 <= y + dy < 16}
    for q in edge:
        img.putpixel(q, (47, 32, 24, 255))
    for (x, y), c in px.items():
        img.putpixel((x, y), c + (255,))
    return img


def dice() -> Image.Image:
    """Dois dados: um vermelho atrás e um creme na frente, com pontos."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    def die(x0, y0, size, fill, shade, pip, pips):
        for y in range(y0, y0 + size):
            for x in range(x0, x0 + size):
                edge = x in (x0, x0 + size - 1) or y in (y0, y0 + size - 1)
                corner = x in (x0, x0 + size - 1) and y in (y0, y0 + size - 1)
                if corner:
                    continue
                col = PAL["K"] if edge else (shade if (x == x0 + size - 2 or y == y0 + size - 2) else fill)
                img.putpixel((x, y), col + (255,))
        for px_, py_ in pips:
            img.putpixel((x0 + px_, y0 + py_), pip + (255,))
    die(6, 1, 9, PAL["R"], PAL["r"], PAL["W"], [(2, 2), (4, 4), (6, 6)])
    die(1, 6, 9, PAL["W"], PAL["w"], PAL["K"], [(2, 2), (6, 2), (4, 4), (2, 6), (6, 6)])
    return img


# restos soltos de células vizinhas: (x, y) locais ao tile 16x16
CLEAN = {
    5: [(14, 8), (14, 9), (14, 10)],                 # lasca solta à direita do dado
    27: [(3, 11), (4, 11), (3, 12), (4, 12)],        # migalhas da estrela do sonho
}



# ---- completar ícones cortados da folha original, SEM mudar o estilo ----
OUTLINE = (2, 4, 20)
# células da folha que ficaram sem contorno / com buracos: recebem contorno e fechamento
COMPLETE = {0, 1, 2, 3, 5, 11, 33, 34, 35, 37, 38}
# remover pedaços soltos (x, y) e acrescentar pixels (x, y, cor) à mão, por ícone
REMOVE = {
    34: [(1, 3), (2, 3), (1, 4), (2, 4), (1, 5), (2, 5), (1, 6), (2, 6)],   # pedaço solto à esquerda do farol
}
ADD = {
    # estilo de cabelo: liga o enfeite dourado de cima ao cabelo azul-marinho de baixo
    11: [(7, 3, (22, 36, 66)), (8, 3, (22, 36, 66)),
         (6, 4, (24, 36, 68)), (7, 4, (32, 48, 88)), (8, 4, (28, 42, 80)), (9, 4, (24, 36, 68)), (10, 4, (20, 30, 58)),
         (6, 5, (20, 30, 58)), (7, 5, (30, 44, 82)), (10, 5, (24, 36, 68)), (11, 5, (14, 22, 44))],
    # farol: topo vermelho que faltava
    34: [(7, 2, (150, 14, 28)), (8, 2, (170, 20, 34)), (6, 3, (40, 60, 100))],
}


SHIFT = {}
TRIM = {11: 'top', 33: 'right'}      # estes dois tinham 16/15 px: tira 1 px da ponta para sobrar margem      # recentra para sobrar 1 px de margem


def complete(img: Image.Image, index: int) -> Image.Image:
    img = img.copy()
    px = img.load()
    for x, y in REMOVE.get(index, []):
        px[x, y] = (0, 0, 0, 0)
    for x, y, c in ADD.get(index, []):
        px[x, y] = c + (255,)
    # fecha buracos: pixel vazio com 3 ou 4 vizinhos preenchidos recebe a média deles
    for _ in range(2):
        fill = []
        for y in range(1, 15):
            for x in range(1, 15):
                if px[x, y][3] == 0:
                    ns = [px[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if px[x + dx, y + dy][3]]
                    if len(ns) >= 3:
                        fill.append((x, y, tuple(sum(n[i] for n in ns) // len(ns) for i in range(3)) + (255,)))
        for x, y, c in fill:
            px[x, y] = c
    # contorno: pixel claro com vizinho vazio ganha o contorno escuro no vizinho
    edge = []
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a and (r + g + b) > 150:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny][3] == 0:
                        edge.append((nx, ny))
    for x, y in edge:
        px[x, y] = OUTLINE + (255,)
    t = TRIM.get(index)
    if t == 'bottom':
        for x in range(16):
            px[x, 15] = (0, 0, 0, 0)
        for x in range(16):
            px[x, 14] = (0, 0, 0, 0) if px[x, 14][3] and sum(px[x, 14][:3]) < 150 else px[x, 14]
    if t == 'top':
        for x in range(16):
            px[x, 0] = (0, 0, 0, 0)
    if t == 'right':
        for y in range(16):
            px[15, y] = (0, 0, 0, 0)
    dx, dy = SHIFT.get(index, (0, 0))
    if dx or dy:
        moved = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        moved.alpha_composite(img, (dx, dy))
        img = moved
    return img


PAL_BEARD = dict(PAL, H=(96, 44, 20), h=(138, 74, 34), B=(116, 58, 24), b=(158, 92, 46), d=(70, 32, 16), S=(236, 178, 134), s=(206, 140, 100))

# punho (40): pele em 5 tons (1 = brilho ... 5 = sombra da borda), ouro e manga azul
PAL_FIST = {
    ".": None,
    "K": (2, 4, 20),         # contorno
    "1": (255, 240, 206),    # pele: luz
    "2": (255, 208, 150),
    "3": (246, 168, 108),
    "4": (206, 108, 80),     # pele: dobra entre os dedos
    "5": (140, 66, 70),      # pele: sombra da borda direita
    "W": (255, 255, 255),    # brilho nos nós dos dedos
    "Y": (255, 238, 136),    # debrum dourado: luz
    "G": (250, 192, 48),
    "g": (184, 118, 24),
    "a": (140, 192, 255),    # manga azul: da luz à sombra
    "b": (80, 142, 232),
    "c": (48, 100, 196),
    "d": (28, 60, 140),
}

# globo (41): oceano em 6 tons (o = reflexo ... t = fundo), terra verde, deserto em areia, nuvem, ouro
PAL_GLOBE = {
    ".": None,
    "K": (2, 4, 20),         # contorno
    "o": (176, 230, 252),    # oceano: do reflexo à sombra
    "p": (100, 188, 246),
    "q": (54, 138, 222),
    "r": (38, 98, 184),
    "s": (28, 66, 142),
    "t": (22, 46, 108),
    "M": (140, 200, 92),     # vegetação: da luz à sombra
    "N": (84, 152, 78),
    "O": (52, 104, 72),
    "D": (252, 230, 156),    # deserto
    "E": (234, 194, 104),
    "F": (198, 146, 80),
    "W": (255, 255, 255),    # reflexo da luz na esfera
    "w": (226, 244, 255),    # nuvem
    "Y": (255, 236, 130),    # meridiano e pedestal dourados
    "G": (244, 184, 52),
    "g": (176, 116, 28),
}

# paleta própria por ícone (o resto usa PAL)
PALETTES = {32: PAL_BEARD, 40: PAL_FIST, 41: PAL_GLOBE}



# ---- ícones desenhados pelo usuário (32x32), em tools/art/user_icons/NN.png ----
USER_DIR = Path(__file__).with_name("user_icons")


def user_icon32(index: int):
    f = USER_DIR / f"{index:02d}.png"
    return Image.open(f).convert("RGBA") if f.exists() else None


def down16(img32: Image.Image) -> Image.Image:
    """32x32 -> 16x16 por média de blocos 2x2 (com alfa pré-multiplicado) e alfa binário, para manter o pixel art nítido."""
    a = np.array(img32).astype(np.float64)
    alpha = a[:, :, 3:4] / 255.0
    pre = np.concatenate([a[:, :, :3] * alpha, alpha], axis=2).reshape(16, 2, 16, 2, 4).mean(axis=(1, 3))
    out = np.zeros((16, 16, 4), dtype=np.uint8)
    al = pre[:, :, 3]
    solid = al >= 0.5
    rgb = np.where(al[:, :, None] > 0, pre[:, :, :3] / np.maximum(al[:, :, None], 1e-6), 0)
    out[:, :, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    out[:, :, 3] = np.where(solid, 255, 0)
    return Image.fromarray(out, "RGBA")


def parse(art: str, pal=None) -> Image.Image:
    pal = pal or PAL
    rows = [r for r in art.strip("\n").split("\n")]
    assert len(rows) == 16, f"esperava 16 linhas, veio {len(rows)}"
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, f"linha {y} tem {len(row)} colunas: {row!r}"
        for x, ch in enumerate(row):
            col = pal[ch]
            if col:
                img.putpixel((x, y), col + (255,))
    return img


CLEAN_ROWS = {9: range(0, 4)}      # rosto: sobras soltas do topo (vinham da linha de cima da folha)


def from_sheet(index: int) -> Image.Image:
    sheet = np.array(Image.open(REFERENCE).convert("RGB"))[4::8, 4::8]   # 80x128, 1 px = 1 pixel de arte
    row, col = divmod(index, COLS)
    cell = sheet[row * 16:(row + 1) * 16, col * 16:(col + 1) * 16].astype(np.int16)
    fg = np.linalg.norm(cell - BACKGROUND, axis=2) > 18
    rgba = np.dstack([cell.astype("uint8"), (fg * 255).astype("uint8")])
    img = Image.fromarray(rgba, "RGBA")
    for x, y in CLEAN.get(index, []):
        img.putpixel((x, y), (0, 0, 0, 0))
    for y in CLEAN_ROWS.get(index, []):
        for x in range(16):
            img.putpixel((x, y), (0, 0, 0, 0))
    return img


def tile(index: int) -> Image.Image:
    u = user_icon32(index)
    if u is not None:
        return down16(u)
    if index == 4:
        return helm()
    if index in OVERRIDES:
        return parse(OVERRIDES[index], PALETTES.get(index))
    if index >= COLS * SHEET_ROWS:                    # slots reservados, sem desenho (fora da folha de referência)
        return Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    img = from_sheet(index)
    return complete(img, index) if index in COMPLETE else img


def build16() -> Image.Image:
    atlas = Image.new("RGBA", (COLS * 16, ROWS * 16), (0, 0, 0, 0))
    for i in range(COLS * ROWS):
        row, col = divmod(i, COLS)
        atlas.alpha_composite(tile(i), (col * 16, row * 16))
    return atlas


def main() -> None:
    preview_arg = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    args = [Path(a) for a in sys.argv[1:] if not a.startswith("--") and a != preview_arg]
    out_dir = args[0] if args else Path("src/main/resources/assets/lasthorizon/textures/gui")
    out_dir.mkdir(parents=True, exist_ok=True)
    atlas16 = build16()
    atlas32 = atlas16.resize((COLS * 32, ROWS * 32), Image.Resampling.NEAREST)
    for i in range(COLS * ROWS):                      # ícones feitos pelo usuário entram no 32x32 como foram desenhados
        u = user_icon32(i)
        if u is not None:
            r, c = divmod(i, COLS)
            atlas32.paste(Image.new("RGBA", (32, 32), (0, 0, 0, 0)), (c * 32, r * 32))
            atlas32.alpha_composite(u, (c * 32, r * 32))
    atlas32.save(out_dir / "map_icons32.png")
    print("salvo", out_dir / "map_icons32.png", atlas32.size)

    # margem de cada ícone (deve ser >= 1 em todos os lados)
    arr = np.array(atlas16)[:, :, 3] > 0
    for i in range(COLS * ROWS):
        r, c = divmod(i, COLS)
        t = arr[r * 16:(r + 1) * 16, c * 16:(c + 1) * 16]
        ys, xs = np.where(t)
        if not len(xs):                               # slot vazio (reservado)
            continue
        m = (xs.min(), ys.min(), 15 - xs.max(), 15 - ys.max())
        if min(m) < 1:
            print(f"AVISO ícone {i}: margens esq/cima/dir/baixo = {m}")

    if "--preview" in sys.argv:
        preview = Path(sys.argv[sys.argv.index("--preview") + 1])
        preview.mkdir(parents=True, exist_ok=True)
        for label, bg in (("parch", (227, 199, 141, 255)), ("dark", (54, 35, 61, 255))):
            big = atlas16.resize((COLS * 16 * 8, ROWS * 16 * 8), Image.Resampling.NEAREST)
            board = Image.new("RGBA", big.size, bg)
            board.alpha_composite(big)
            board.save(preview / f"icons16_{label}.png")


if __name__ == "__main__":
    main()
