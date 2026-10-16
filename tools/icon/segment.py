import sys
from collections import deque
import numpy as np
from PIL import Image

src, outdir = sys.argv[1], sys.argv[2]
im = np.asarray(Image.open(src).convert("RGB")).astype(int)
H, W, _ = im.shape
r, g, b = im[..., 0], im[..., 1], im[..., 2]

# 1) Cadre cyan : bornes de la tuile
cyan = (g > 215) & (b > 230) & (r > 120) & (r < 185)
# Bornes mesurées sur les axes centraux (les icônes floues autour contiennent aussi du cyan)
def edges(line):
    idx = np.where(line)[0]
    return idx.min(), idx.max()
mid = H // 2
x0, x1 = edges(cyan[mid, :] | cyan[mid - 40, :] | cyan[mid + 40, :])
y0, y1 = edges(cyan[:, W // 2] | cyan[:, W // 2 - 40] | cyan[:, W // 2 + 40])
print("cadre cyan", x0, x1, y0, y1)
inset = 18
crop = im[y0 + inset:y1 - inset, x0 + inset:x1 - inset]
h, w, _ = crop.shape
print("intérieur", w, h)

# 2) Remplissage depuis les bords : le fond = tout pixel non « blanc autocollant »
cr, cg, cb = crop[..., 0], crop[..., 1], crop[..., 2]
white = (crop.min(axis=2) > 205) | ((cr < 50) & (cg < 50) & (cb < 110))  # blanc ou contour nuit

def dilate(m, r):
    out = m.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dy * dy + dx * dx <= r * r:
                out |= np.roll(np.roll(m, dy, 0), dx, 1)
    return out

R = int(sys.argv[3]) if len(sys.argv) > 3 else 4
white = dilate(white, R)  # bouche les trous du contour blanc
bg = np.zeros((h, w), bool)
q = deque()
for x in range(w):
    for y in (0, h - 1):
        if not white[y, x]:
            bg[y, x] = True; q.append((y, x))
for y in range(h):
    for x in (0, w - 1):
        if not white[y, x] and not bg[y, x]:
            bg[y, x] = True; q.append((y, x))
while q:
    y, x = q.popleft()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ny, nx = y + dy, x + dx
        if 0 <= ny < h and 0 <= nx < w and not bg[ny, nx] and not white[ny, nx]:
            bg[ny, nx] = True; q.append((ny, nx))

# On rend au fond la marge ajoutée par la dilatation
bg = dilate(bg, R)
# 3) Composantes du premier plan ; on garde les grosses
fg = ~bg
label = np.zeros((h, w), int)
sizes = {}
n = 0
for sy in range(h):
    for sx in range(w):
        if fg[sy, sx] and not label[sy, sx]:
            n += 1; label[sy, sx] = n; cnt = 0
            q = deque([(sy, sx)])
            while q:
                y, x = q.popleft(); cnt += 1
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < h and 0 <= nx < w and fg[ny, nx] and not label[ny, nx]:
                        label[ny, nx] = n; q.append((ny, nx))
            sizes[n] = cnt
big = sorted(sizes.items(), key=lambda kv: -kv[1])[:8]
print("composantes (taille)", big)
keep = [k for k, s in big if s > 10000]
mask = np.isin(label, keep)
ys2, xs2 = np.where(mask)
print("bbox premier plan", xs2.min(), xs2.max(), ys2.min(), ys2.max())

np.save(f"{outdir}/mask.npy", mask)
Image.fromarray(crop.astype("uint8")).save(f"{outdir}/interior.png")
rgba = np.dstack([crop, (mask * 255)]).astype("uint8")
Image.fromarray(rgba).save(f"{outdir}/cutout.png")
# Aperçu sur fond magenta pour contrôler le détourage
prev = crop.copy(); prev[~mask] = [255, 0, 255]
Image.fromarray(prev.astype("uint8")).save(f"{outdir}/preview.png")
# Couleurs du fond aux coins (pour le dégradé de l'arrière-plan)
for name, (yy, xx) in {"haut-gauche": (40, 40), "haut-droite": (40, w - 40), "bas-gauche": (h - 40, 40), "bas-droite": (h - 40, w - 40), "centre-haut": (40, w // 2)}.items():
    print(name, crop[yy - 10:yy + 10, xx - 10:xx + 10].reshape(-1, 3).mean(axis=0).astype(int))
