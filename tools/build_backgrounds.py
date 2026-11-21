"""Gera o fundo animado da aba de conquistas (v2, 234x113).

Saidas (assets/lasthorizon/textures/gui/seabg/):
  layer_sea_<nome>.png   folhas de 32 quadros (4x8 de 382x63): s0..s4, shadow, crest, foam
  layer_n1..n7.png       nuvens do horizonte (tira 764x140, repete sem emenda), um PNG por tom
  layer_m1..m7.png       cumulos medios (tira 764x140)
  layer_f0/f1.png        nuvens finas distantes (tira 764x140)
  layer_rn_l/rn_d.png    reflexo das nuvens do horizonte no mar (764x63) - claro / escuro
  layer_rm_l/rm_d.png    reflexo dos cumulos medios (764x63)
  layer_stars1_0..2 / stars2_0..2   estrelas brilhantes e poeira, em 3 faixas de brilho (382x140)
  layer_rays0/1.png      raios de luz do sol e da lua (353x353)
  layer_neb0..2.png      via lactea (382x140)
  props.png              lua, sol, brilho em aneis, tiras de dither (512x256)
  src/main/java/.../client/SeaMoods.java  paletas de cada momento (gerado)
Uso: python3 tools/build_backgrounds.py   (requer numpy e Pillow)
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from PIL import Image
import ocean as O

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'src/main/resources/assets/lasthorizon/textures/gui/seabg')
JAVA = os.path.join(ROOT, 'src/main/java/br/com/lasthorizon/akuma/client/SeaMoods.java')
COLS, ROWS = 4, O.F // 4


def java_moods():
    lines = ['package br.com.lasthorizon.akuma.client;', '',
             '/** GERADO por tools/build_backgrounds.py (paletas em tools/ocean.py, MOODS). Nao edite a mao. */',
             'final class SeaMoods {', '    private SeaMoods() {}', '',
             f'    static final int COUNT = {len(O.MOODS)}, NC = {len(O.COLOR_NAMES)}, NN = {len(O.NUM_NAMES)};']
    for i, n in enumerate(O.COLOR_NAMES):
        lines.append(f'    static final int {n.upper()} = {i};')
    for i, n in enumerate(O.NUM_NAMES):
        lines.append(f'    static final int {n.upper()} = {i};')
    lines.append('')
    lines.append('    static final int[][] COLORS = {')
    for m in O.MOODS:
        lines.append('            {' + ', '.join(f'0x{c:06X}' for c in m['c']) + '},')
    lines.append('    };')
    lines.append('    static final double[][] NUMS = {')
    scale = {'sun_x': O.SX, 'moon_x': O.SX, 'sun_y': O.SYH, 'moon_y': O.SYH}   # moods sao escritos no espaco 234 x 113
    for m in O.MOODS:
        vals = [x * scale.get(n, 1.0) for n, x in zip(O.NUM_NAMES, m['n'])]
        lines.append('            {' + ', '.join(repr(round(float(x), 3)) for x in vals) + '},')
    lines.append('    };')
    lines.append('}')
    return '\n'.join(lines) + '\n'


def save(name, alpha):
    O.to_rgba(alpha).save(os.path.join(OUT, f'layer_{name}.png'), optimize=True)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        if old.startswith(('scene_', 'layer_')) or old == 'props.png':
            os.remove(os.path.join(OUT, old))
    # mar
    sheets = {n: Image.new('RGBA', (O.W * COLS, O.SH * ROWS), (0, 0, 0, 0)) for n in O.SEA_LAYERS}
    for f in range(O.F):
        L = O.sea_frame(f)
        for n in O.SEA_LAYERS:
            sheets[n].paste(O.to_rgba(L[n].astype(float)), ((f % COLS) * O.W, (f // COLS) * O.SH))
    for n, im in sheets.items():
        im.save(os.path.join(OUT, f'layer_sea_{n}.png'), optimize=True)
    # nuvens
    S = O.strips()
    for k in range(1, 8):
        save(f'n{k}', (S['near'] == k).astype(float))
        save(f'm{k}', (S['mid'] == k).astype(float))
    save('f0', np.isin(S['far'], (2, 3)).astype(float))
    save('f1', np.isin(S['far'], (4, 5, 6)).astype(float))
    for key, nm in (('near', 'rn'), ('mid', 'rm')):
        r = O.refl_of(S[key])
        save(f'{nm}_l', np.isin(r, (1, 2, 3)).astype(float))
        save(f'{nm}_d', np.isin(r, (4, 5, 6, 7)).astype(float))
    # estrelas e via lactea
    t1, t2 = O.make_stars()
    for i in range(3):
        save(f'stars1_{i}', t1[i])
        save(f'stars2_{i}', t2[i])
    for i, ry in enumerate(O.make_rays()):
        save(f'rays{i}', ry)
    layers, _ = O.make_nebula()
    for i, m in enumerate(layers):
        save(f'neb{i}', m.astype(float))
    O.make_props().save(os.path.join(OUT, 'props.png'), optimize=True)
    with open(JAVA, 'w', encoding='utf-8') as fh:
        fh.write(java_moods())
    total = sum(os.path.getsize(os.path.join(OUT, x)) for x in os.listdir(OUT))
    print('ok', len(os.listdir(OUT)), 'arquivos,', total // 1024, 'KB')
