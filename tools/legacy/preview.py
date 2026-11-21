import sys
sys.path.insert(0, ''+__import__('os').path.dirname(__file__)+'')
from PIL import Image
import importlib
mods = sys.argv[2:]
items = []
for m in mods:
    mod, fn = m.split('.')
    items.append(getattr(importlib.import_module(mod), fn)().img().resize((117 * 4, 57 * 4), Image.NEAREST))
cols = 2
rows = (len(items) + 1) // 2
sheet = Image.new('RGB', (117 * 4 * cols + 8, (57 * 4 + 8) * rows), (30, 30, 30))
for i, im in enumerate(items):
    sheet.paste(im, ((i % cols) * (117 * 4 + 8), (i // cols) * (57 * 4 + 8)))
sheet.save(sys.argv[1])
