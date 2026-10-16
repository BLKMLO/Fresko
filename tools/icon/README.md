# Icône Fresko

`source.jpg` : illustration fournie (2026-10-16). Les calques de l'icône adaptative
en sont tirés par deux scripts (Python 3 + Pillow + NumPy) :

```
python3 tools/icon/segment.py tools/icon/source.jpg /tmp/icon 3   # détourage
python3 tools/icon/layers.py /tmp/icon app/src/main/res             # calques par densité
```

1. `segment.py` repère le cadre cyan, prend l'intérieur, puis remplit le fond depuis
   les bords en s'arrêtant sur le contour blanc / bleu nuit des dessins (épaissi de
   3 px pour boucher ses trous) ; ne garde que les grandes composantes (cloche + cartes).
2. `layers.py` produit `mipmap-*/ic_launcher_foreground.webp` (dessin de 54 dp dans
   un calque de 108 dp, visible en entier avec un masque rond),
   `ic_launcher_monochrome.webp` (icônes thématiques Android 13+, contours en creux)
   et `drawable-nodpi/fresko_mascot.webp` (illustration de l'écran vide).

L'arrière-plan est vectoriel : `app/src/main/res/drawable/ic_launcher_background.xml`
(dégradé repris de l'illustration + étincelles).
