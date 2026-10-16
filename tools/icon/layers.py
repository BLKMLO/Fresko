import sys
import numpy as np
from PIL import Image

work, res = sys.argv[1], sys.argv[2]
crop = np.asarray(Image.open(f"{work}/interior.png").convert("RGB"))
mask = np.load(f"{work}/mask.npy")
ys, xs = np.where(mask)
x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
rgba = np.dstack([crop, mask * 255]).astype("uint8")[y0:y1, x0:x1]
art = Image.fromarray(rgba)

# Monochrome : silhouette où les contours sombres deviennent des creux (rendu « trait »)
lum = crop[..., 0] * 0.299 + crop[..., 1] * 0.587 + crop[..., 2] * 0.114
mono_alpha = (mask & (lum > 70)) * 255
mono = np.zeros((*mask.shape, 4), "uint8")
mono[..., :3] = 255
mono[..., 3] = mono_alpha
mono = Image.fromarray(mono)[y0:y1, x0:x1] if False else Image.fromarray(mono).crop((x0, y0, x1, y1))

CONTENT_DP = 54  # plus grande dimension du dessin dans le calque de 108 dp
densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, f in densities.items():
    size = round(108 * f)
    scale = CONTENT_DP * f / max(art.size)
    w, h = round(art.size[0] * scale), round(art.size[1] * scale)
    # Seul le monochrome sert : l'icône couleur est produite par tile.py
    for src_img, fname in ((mono, "ic_launcher_monochrome"),):
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        canvas.paste(src_img.resize((w, h), Image.LANCZOS), ((size - w) // 2, (size - h) // 2))
        import os
        os.makedirs(f"{res}/mipmap-{name}", exist_ok=True)
        canvas.save(f"{res}/mipmap-{name}/{fname}.webp", "WEBP", quality=92, method=6)

# Écran d'accueil vide : illustration détourée, version simplifiée sans fond (512 px max)
os_ = __import__("os"); os_.makedirs(f"{res}/drawable-nodpi", exist_ok=True)
s = 512 / max(art.size)
art.resize((round(art.size[0] * s), round(art.size[1] * s)), Image.LANCZOS).save(
    f"{res}/drawable-nodpi/fresko_mascot.webp", "WEBP", quality=90, method=6)
print("dessin", art.size)
