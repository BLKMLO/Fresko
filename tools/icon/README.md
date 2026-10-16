# Icône Fresko

`source.jpg` : illustration fournie (2026-10-16). **On ne retire que le décor flou
autour de la tuile (icônes Play Store)** : le fond illustré (bleu, bulles, planches,
étincelles) fait partie de l'icône. Scripts (Python 3 + Pillow + NumPy) :

```
python3 tools/icon/tile.py tools/icon/source.jpg app/src/main/res 70   # icône + illustration
python3 tools/icon/segment.py tools/icon/source.jpg /tmp/icon 3        # détourage (monochrome)
python3 tools/icon/layers.py /tmp/icon app/src/main/res                # puis supprimer les
#   mipmap-*/ic_launcher_foreground.webp produits : seuls les monochromes servent
```

1. `tile.py` repère le cadre cyan de la tuile, prend l'intérieur (retrait de 42 px pour
   sortir de l'arc arrondi du cadre), le place à 70 dp dans le calque de 108 dp
   (zone visible : 72 dp) avec un débord flouté → `mipmap-*/ic_launcher_background.webp`.
   Il produit aussi `drawable-nodpi/fresko_mascot.webp` : la tuile entière, cadre
   lumineux compris, pour l'écran vide de l'app.
2. `segment.py` + `layers.py` : détourage cloche + planches, utilisé seulement pour
   `ic_launcher_monochrome.webp` (icônes à thème d'Android 13+).

Le premier plan de l'icône adaptative est transparent (`ic_launcher.xml`).
