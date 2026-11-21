"""Biblioteca mínima de pixel art para gerar os fundos da aba de conquistas (grade 117x57, célula = 2 px)."""
import math
import numpy as np

GW, GH = 117, 57


def hexc(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], float)


def mixc(a, b, f):
    return a * (1 - f) + b * f


class Cv:
    def __init__(self):
        self.a = np.zeros((GH, GW, 3), float)

    def fill(self, c):
        self.a[:] = hexc(c) if isinstance(c, int) else c

    def px(self, x, y, c, al=1.0):
        if 0 <= x < GW and 0 <= y < GH:
            c = hexc(c) if isinstance(c, int) else c
            self.a[y, x] = self.a[y, x] * (1 - al) + c * al

    def rect(self, x0, y0, x1, y1, c, al=1.0):
        for y in range(max(0, y0), min(GH, y1)):
            for x in range(max(0, x0), min(GW, x1)):
                self.px(x, y, c, al)

    def vgrad(self, y0, y1, stops, bands=10, x0=0, x1=GW):
        """Degradê vertical em faixas. stops = [(t, 0xRRGGBB), ...]."""
        n = max(1, y1 - y0)
        for y in range(y0, y1):
            t = (y - y0) / n
            tq = (math.floor(t * bands) + 0.5) / bands
            self.a[y, x0:x1] = interp(stops, tq)

    def disc(self, cx, cy, r, c, al=1.0, clip=None):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and (clip is None or y < clip):
                    self.px(x, y, c, al)

    def img(self):
        from PIL import Image
        arr = np.clip(self.a.round(), 0, 255).astype(np.uint8)
        return Image.fromarray(arr, "RGB")


def interp(stops, t):
    t = min(1.0, max(0.0, t))
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        if t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return mixc(hexc(c0), hexc(c1), f)
    return hexc(stops[-1][1])


def hump(u, peak, pw=0.6):
    w = peak if u < peak else 1.0 - peak
    k = 1.0 - abs(u - peak) / max(w, 0.2)
    return max(0.0, min(1.0, k)) ** pw


def cumulus(cv, x0, x1, base, hgt, peak, seed, tones, cap=4.0, inner=1.0, minr=2.0, fillfrac=0.5,
            flat=1, xstep=(0.8, 1.1), pw=0.6, topjit=0.18, light=(-0.35, -0.9), th=(0.5, 0.0, -0.5),
            al=1.0, ret=False, ragged=False):
    """Cúmulo de lóbulos redondos com luz de cima/esquerda.
    tones = [brilho, claro, médio, sombra, fundo]; base = linha (y, exclusiva) do fundo da nuvem."""
    rnd = np.random.RandomState(seed)
    w = x1 - x0
    balls = []
    x = 0.0
    while x < w:
        top = max(2.0, hump(x / w, peak, pw) * hgt * (1 - topjit + topjit * rnd.rand()))
        rad = min(top, minr + (cap - minr) * rnd.rand())
        balls.append((x, max(0.0, top - rad), rad))
        x += max(1.6, rad * (xstep[0] + (xstep[1] - xstep[0]) * rnd.rand()))
    for _ in range(int(w * hgt / 16 * inner)):
        bx = 1 + rnd.rand() * (w - 2)
        top = hump(bx / w, peak, pw) * hgt
        rad = minr + 0.4 + (cap - minr) * rnd.rand()
        cy = rad * 0.6 + rnd.rand() * max(0.0, top - rad * 2.2)
        if top < rad * 1.4:
            continue
        balls.append((bx, cy, rad))
    balls.sort(key=lambda b: b[1] + b[2])
    tg = np.zeros((GH, GW), int)
    ys, xs = np.mgrid[0:GH, 0:GW]
    yabove = (base - 1 - ys) + 0.5
    for u in range(GW):
        pass
    # preenchimento base
    for xx in range(x0, min(x1, GW)):
        if xx < 0:
            continue
        hh = hump((xx + 0.5 - x0) / w, peak, pw) * hgt * fillfrac
        for yy in range(GH):
            if 0 <= yabove[yy, xx] < hh:
                tg[yy, xx] = 3
    for (bx, cy, rad) in balls:
        ex = (xs + 0.5 - x0 - bx) / rad
        ey = (cy - yabove) / rad
        m = (ex ** 2 + ey ** 2 <= 1.0) & (yabove > -0.01)
        ld = light[0] * ex + light[1] * ey
        t = np.where(ld > th[0], 1, np.where(ld > th[1], 2, np.where(ld > th[2], 3, 4)))
        tg[m] = t[m]
    for r in range(flat):
        row = base - 1 - r
        if 0 <= row < GH:
            sel = tg[row] > 0
            tg[row][sel] = 5 if r == 0 and len(tones) > 4 else 4
    if ragged:
        for xx in range(GW):
            col = tg[:, xx]
            ys_ = np.nonzero(col)[0]
            if len(ys_) == 0:
                continue
            b = ys_.max()
            cut = max(0, int(round(1.6 + 1.5 * math.sin(xx * 0.55 + seed) + 0.7 * math.sin(xx * 1.3 + seed))))
            for k in range(cut):
                tg[b - k, xx] = 0
            if b - cut >= 0 and tg[b - cut, xx]:
                tg[b - cut, xx] = 5
    if ret:
        return tg
    composite(cv, tg, tones, al)
    return tg


def composite(cv, tg, tones, al=1.0):
    cols = [None] + [hexc(c) for c in tones]
    for y in range(GH):
        for x in range(GW):
            k = tg[y, x]
            if k:
                cv.a[y, x] = cv.a[y, x] * (1 - al) + cols[k] * al


def stars(cv, n, seed, ymax, cols=(0xFFFFFF,), cross=0.12, region=None):
    rnd = np.random.RandomState(seed)
    for _ in range(n):
        x = rnd.randint(1, GW - 1)
        y = rnd.randint(1, ymax)
        if region and not region(x, y):
            continue
        c = cols[rnd.randint(len(cols))]
        cv.px(x, y, c)
        if rnd.rand() < cross:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                cv.px(x + dx, y + dy, c, 0.55)
