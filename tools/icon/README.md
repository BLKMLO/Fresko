# Icône Fresko

`source.jpg` : illustration fournie (2026-10-16). **On ne retire que le décor flou
autour de la tuile (icônes Play Store)** : le fond illustré (bleu, bulles, planches,
étincelles) fait partie de l'icône. Scripts (Python 3 + Pillow + NumPy) :

```
python3 tools/icon/tile.py tools/icon/source.jpg app/src/main/res 70   # icône couleur
python3 tools/icon/segment.py tools/icon/source.jpg /tmp/icon 3        # détourage
python3 tools/icon/layers.py /tmp/icon app/src/main/res                # monochrome + accueil
```

1. `tile.py` repère le cadre cyan de la tuile, prend l'intérieur (retrait de 42 px pour
   sortir de l'arc arrondi du cadre), le place à 70 dp dans le calque de 108 dp
   (zone visible : 72 dp) avec un débord flouté → `mipmap-*/ic_launcher_background.webp`.
2. `segment.py` détoure cloche + planches (remplissage du fond depuis les bords, arrêté
   par le contour blanc / bleu nuit épaissi de 3 px ; grandes composantes seulement).
3. `layers.py` en tire `mipmap-*/ic_launcher_monochrome.webp` (icônes à thème
   d'Android 13+) et `drawable-nodpi/fresko_mascot.webp` : l'illustration **simplifiée,
   sans fond**, de l'écran d'accueil vide (préférée par l'utilisateur à la tuile entière).

Le premier plan de l'icône adaptative est transparent (`ic_launcher.xml`).
