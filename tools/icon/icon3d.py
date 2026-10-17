"""Icône « 3D » (source-3d.png, 2026-10-17) : tuile bleue arrondie, cloche + rayons + planches.

1. Détourage : remplissage depuis les bords de l'image sur tout pixel « fond » (transparent
   ou bleu profond, ombres comprises) ; s'arrête sur le jaune, le crème des planches et
   leurs contours sombres. On garde les composantes de taille significative.
2. Fond : dégradé linéaire ajusté (moindres carrés) sur les pixels bleus de la tuile.
3. Calques : fond = dégradé plein cadre ; premier plan = dessin détouré (CONTENT_DP dans
   108 dp, zone visible 72 dp) ; monochrome = cloche, rayons et cadres des planches.

Usage : python3 tools/icon/icon3d.py tools/icon/source-3d.png app/src/main/res [CONTENT_DP]
"""
import os
import sys
from collections import deque

import numpy as np
from PIL import Image, ImageFilter

src, res = sys.argv[1], sys.argv[2]
CONTENT_DP = float(sys.argv[3]) if len(sys.argv) > 3 else 56
im = np.asarray(Image.open(src).convert("RGBA")).astype(int)
H, W, _ = im.shape
r, g, b, a = im[..., 0], im[..., 1], im[..., 2], im[..., 3]

# Tuile : bornes mesurées sur les axes centraux (pixels opaques)
def edges(line):
    idx = np.where(line)[0]
    return idx.min(), idx.max()
tx0, tx1 = edges(a[H // 2] > 250)
ty0, ty1 = edges(a[:, W // 2] > 250)
print("tuile", (tx0, ty0, tx1, ty1))

# 1) Fond = transparent, ou bleu profond (dégradé, biseau, ombres portées)
bg_like = (a < 128) | ((b > 110) & (b - r > 80) & (b - g > 45))
bg = np.zeros((H, W), bool)
q = deque()
for x in range(W):
    for y in (0, H - 1):
        if bg_like[y, x] and not bg[y, x]:
            bg[y, x] = True; q.append((y, x))
for y in range(H):
    for x in (0, W - 1):
        if bg_like[y, x] and not bg[y, x]:
            bg[y, x] = True; q.append((y, x))
while q:
    y, x = q.popleft()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ny, nx = y + dy, x + dx
        if 0 <= ny < H and 0 <= nx < W and not bg[ny, nx] and bg_like[ny, nx]:
            bg[ny, nx] = True; q.append((ny, nx))

fg = ~bg
label = np.zeros((H, W), int)
sizes = {}
n = 0
for sy in range(H):
    for sx in range(W):
        if fg[sy, sx] and not label[sy, sx]:
            n += 1; label[sy, sx] = n; cnt = 0
            q = deque([(sy, sx)])
            while q:
                y, x = q.popleft(); cnt += 1
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < H and 0 <= nx < W and fg[ny, nx] and not label[ny, nx]:
                        label[ny, nx] = n; q.append((ny, nx))
            sizes[n] = cnt
big = sorted(sizes.items(), key=lambda kv: -kv[1])
print("composantes", big[:10])
keep = [k for k, s in big if s > 1500]
mask = np.isin(label, keep)
# bord adouci (anticrénelage)
alpha = np.asarray(Image.fromarray((mask * 255).astype("uint8")).filter(ImageFilter.GaussianBlur(0.8)))
ys, xs = np.where(mask)
x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
print("dessin", (x0, y0, x1, y1))
art = Image.fromarray(np.dstack([im[..., :3], alpha]).astype("uint8")).crop((x0, y0, x1, y1))

# 2) Dégradé du fond : couleur = c0 + cx·u + cy·v, (u, v) ∈ [0,1] dans la tuile
inner = np.zeros((H, W), bool)
m = 60  # loin des coins arrondis et du biseau
inner[ty0 + m:ty1 - m, tx0 + m:tx1 - m] = True
sel = bg & inner & (a > 250)
yy, xx = np.where(sel)
u = (xx - tx0) / (tx1 - tx0); v = (yy - ty0) / (ty1 - ty0)
A = np.column_stack([np.ones_like(u), u, v])
coef = [np.linalg.lstsq(A, im[yy, xx, c], rcond=None)[0] for c in range(3)]
print("dégradé (haut-gauche → bas-droite)",
      [int(round(c[0])) for c in coef], [int(round(c[0] + c[1] + c[2])) for c in coef])

# Monochrome (icônes à thème) : cloche + rayons (jaune/orangé) et cadres crème des
# planches ; les cases illustrées restent vides → rendu « trait » lisible.
yellow = (r > 170) & (g > 110) & (b < 110)
cream = (r > 205) & (g > 200) & (b > 185)
cand = mask & (yellow | cream)

def components(m):
    lab = np.zeros(m.shape, int); out = []
    for sy, sx in zip(*np.where(m)):
        if lab[sy, sx]:
            continue
        k = len(out) + 1; lab[sy, sx] = k; q = deque([(sy, sx)]); pts = []
        while q:
            y, x = q.popleft(); pts.append((y, x))
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < H and 0 <= nx < W and m[ny, nx] and not lab[ny, nx]:
                    lab[ny, nx] = k; q.append((ny, nx))
        out.append(np.array(pts))
    return out

comps = sorted(components(cand), key=len, reverse=True)
bell, frames = comps[0], comps[1:3]                     # cloche, puis les 2 cadres de planches
bell_x1 = bell[:, 1].max()
extras = [c for c in comps[3:] if len(c) > 2000 and (
    c[:, 1].max() < bell[:, 1].min() + 60                # rayons, à gauche de la cloche
    or (c[:, 1].max() <= bell_x1 and c[:, 0].min() > bell[:, 0].max() - 120))]  # battant
mono_px = np.zeros((H, W), bool)
for c in [bell, *frames, *extras]:
    mono_px[c[:, 0], c[:, 1]] = True
# Comble les reflets à l'intérieur de la cloche (pas les cases des planches, qui restent vides)
bell_zone = np.zeros((H, W), bool)
for c in [bell, *[e for e in extras if e[:, 1].min() > bell[:, 1].min()]]:
    bell_zone[c[:, 0], c[:, 1]] = True
by0, bx0 = bell[:, 0].min(), bell[:, 1].min(); by1, bx1 = bell[:, 0].max(), bell[:, 1].max()
sub = bell_zone[by0:by1 + 1, bx0:bx1 + 1]
outside = np.zeros_like(sub); q = deque()
for y in range(sub.shape[0]):
    for x in (0, sub.shape[1] - 1):
        if not sub[y, x] and not outside[y, x]: outside[y, x] = True; q.append((y, x))
for x in range(sub.shape[1]):
    for y in (0, sub.shape[0] - 1):
        if not sub[y, x] and not outside[y, x]: outside[y, x] = True; q.append((y, x))
while q:
    y, x = q.popleft()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ny, nx = y + dy, x + dx
        if 0 <= ny < sub.shape[0] and 0 <= nx < sub.shape[1] and not sub[ny, nx] and not outside[ny, nx]:
            outside[ny, nx] = True; q.append((ny, nx))
mono_px[by0:by1 + 1, bx0:bx1 + 1] |= ~outside
print("monochrome : cloche", len(bell), "cadres", [len(f) for f in frames], "extras", [len(e) for e in extras])
mono_a = np.asarray(Image.fromarray((mono_px * 255).astype("uint8")).filter(ImageFilter.GaussianBlur(0.8)))
mono = Image.fromarray(np.dstack([np.full((H, W, 3), 255), mono_a]).astype("uint8")).crop((x0, y0, x1, y1))

densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, f in densities.items():
    size = round(108 * f)
    gy, gx = np.mgrid[0:size, 0:size] / (size - 1)
    bgimg = np.dstack([np.clip(c[0] + c[1] * gx + c[2] * gy, 0, 255) for c in coef]).astype("uint8")
    os.makedirs(f"{res}/mipmap-{name}", exist_ok=True)
    Image.fromarray(bgimg).save(f"{res}/mipmap-{name}/ic_launcher_background.webp", "WEBP", quality=95, method=6)
    scale = CONTENT_DP * f / max(art.size)
    w, h = round(art.size[0] * scale), round(art.size[1] * scale)
    for img, fname in ((art, "ic_launcher_foreground"), (mono, "ic_launcher_monochrome")):
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        canvas.paste(img.resize((w, h), Image.LANCZOS), ((size - w) // 2, (size - h) // 2))
        canvas.save(f"{res}/mipmap-{name}/{fname}.webp", "WEBP", quality=92, method=6)
