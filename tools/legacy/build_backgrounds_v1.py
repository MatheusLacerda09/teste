"""Gera o fundo animado da aba de conquistas.

Saídas:
  assets/lasthorizon/textures/gui/seabg/layer_<nome>.png  - folhas de 32 quadros (4x8 de 117x57) com máscaras
  assets/lasthorizon/textures/gui/seabg/props.png         - estrelas, brilho/disco do sol e lua (128x128)
  src/main/java/.../client/SeaMoods.java                  - paletas de cada momento (gerado, não edite)
Uso: python3 tools/build_backgrounds.py   (requer numpy e Pillow)
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image
import ocean as O

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'src/main/resources/assets/lasthorizon/textures/gui/seabg')
JAVA = os.path.join(ROOT, 'src/main/java/br/com/lasthorizon/akuma/client/SeaMoods.java')
COLS, ROWS = 4, O.F // 4


def java_moods():
    cn = ', '.join(f'{n.upper()}' for n in O.COLOR_NAMES)
    lines = ['package br.com.lasthorizon.akuma.client;', '',
             '/** GERADO por tools/build_backgrounds.py (paletas em tools/ocean.py, MOODS). Não edite à mão. */',
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
    for m in O.MOODS:
        lines.append('            {' + ', '.join(repr(float(x)) for x in m['n']) + '},')
    lines.append('    };')
    lines.append('}')
    return '\n'.join(lines) + '\n'


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        if old.startswith('scene_'):
            os.remove(os.path.join(OUT, old))
    sheets = {n: Image.new('RGBA', (117 * COLS, 57 * ROWS), (0, 0, 0, 0)) for n in O.LAYER_ORDER}
    for f in range(O.F):
        L = O.frame_layers(f)
        for n in O.LAYER_ORDER:
            sheets[n].paste(O.to_rgba(L[n]), ((f % COLS) * 117, (f // COLS) * 57))
    for n, im in sheets.items():
        p = os.path.join(OUT, f'layer_{n}.png')
        im.save(p, optimize=True)
    props, _ = O.make_props()
    props.save(os.path.join(OUT, 'props.png'), optimize=True)
    with open(JAVA, 'w', encoding='utf-8') as fh:
        fh.write(java_moods())
    total = sum(os.path.getsize(os.path.join(OUT, x)) for x in os.listdir(OUT))
    print('ok', len(sheets), 'camadas,', total // 1024, 'KB')
