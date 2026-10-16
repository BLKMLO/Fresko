"""Icône = l'illustration complète (intérieur de la tuile), sans le décor flou autour.

Produit mipmap-*/ic_launcher_background.webp (l'écran d'accueil utilise layers.py)."""
import os, sys
import numpy as np
from PIL import Image, ImageFilter

src, res = sys.argv[1], sys.argv[2]
ART_DP = float(sys.argv[3]) if len(sys.argv) > 3 else 68
im = Image.open(src).convert("RGB")
a = np.asarray(im).astype(int)
H, W, _ = a.shape
r, g, b = a[..., 0], a[..., 1], a[..., 2]
cyan = (g > 215) & (b > 230) & (r > 120) & (r < 185)
def edges(line):
    idx = np.where(line)[0]; return idx.min(), idx.max()
mid = H // 2
x0, x1 = edges(cyan[mid] | cyan[mid - 40] | cyan[mid + 40])
y0, y1 = edges(cyan[:, W // 2] | cyan[:, W // 2 - 40] | cyan[:, W // 2 + 40])
inset = 42  # au-dela de l arc arrondi du cadre lumineux
interior = im.crop((x0 + inset, y0 + inset, x1 - inset, y1 - inset))
print("tuile", (x0, y0, x1, y1), "intérieur", interior.size)

densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, f in densities.items():
    size = round(108 * f)
    art = round(ART_DP * f)
    tile = interior.resize((art, art), Image.LANCZOS)
    # Débord (≈ 1 dp visible) : prolongement des bords de l'illustration, flouté
    pad = (size - art) // 2 + 2
    padded = np.pad(np.asarray(tile), ((pad, pad), (pad, pad), (0, 0)), mode="edge")
    bleed = Image.fromarray(padded).filter(ImageFilter.GaussianBlur(radius=3 * f))
    o = (bleed.size[0] - size) // 2
    canvas = bleed.crop((o, o, o + size, o + size))
    canvas.paste(tile, ((size - art) // 2, (size - art) // 2))
    os.makedirs(f"{res}/mipmap-{name}", exist_ok=True)
    canvas.save(f"{res}/mipmap-{name}/ic_launcher_background.webp", "WEBP", quality=92, method=6)
