# Icône Fresko

## Icône actuelle (v0.5.1) : `source-3d.png`

Illustration « 3D » fournie le 2026-10-17 : tuile bleue arrondie, cloche + rayons + planches.

```
python3 tools/icon/icon3d.py tools/icon/source-3d.png app/src/main/res 56
```

- **Détourage** : remplissage depuis les bords sur tout pixel « fond » (transparent ou bleu
  profond, ombres comprises), arrêté par le jaune, le crème et les contours sombres ;
  composantes > 1 500 px conservées (dessin + 3 rayons).
- **Fond** : dégradé linéaire ajusté par moindres carrés sur les pixels bleus de la tuile
  (mesuré : (60, 97, 218) en haut à gauche → (26, 40, 138) en bas à droite).
- **Calques** (`mipmap-*/`) : `ic_launcher_background.webp` (dégradé),
  `ic_launcher_foreground.webp` (dessin, 56 dp dans 108 dp ; zone visible 72 dp),
  `ic_launcher_monochrome.webp` (cloche, battant, rayons, cadres des planches ; reflets
  de la cloche comblés, cases illustrées vides).

## Illustration précédente (v0.3.0 → v0.5.0) : `source.jpg`

Toujours utilisée pour l'**écran d'accueil vide** (`drawable-nodpi/fresko_mascot.webp`,
version détourée sans fond, préférée par l'utilisateur) :

```
python3 tools/icon/tile.py tools/icon/source.jpg app/src/main/res 70   # icône couleur
python3 tools/icon/segment.py tools/icon/source.jpg /tmp/icon 3        # détourage
python3 tools/icon/layers.py /tmp/icon app/src/main/res                # monochrome + accueil
```

Ancienne icône (plus utilisée) : `tile.py` repère le cadre cyan de la tuile, prend l'intérieur (retrait de 42 px pour
   sortir de l'arc arrondi du cadre), le place à 70 dp dans le calque de 108 dp
   (zone visible : 72 dp) avec un débord flouté → `mipmap-*/ic_launcher_background.webp`.
2. `segment.py` détoure cloche + planches (remplissage du fond depuis les bords, arrêté
   par le contour blanc / bleu nuit épaissi de 3 px ; grandes composantes seulement).
3. `layers.py` en tire `mipmap-*/ic_launcher_monochrome.webp` (icônes à thème
   d'Android 13+) et `drawable-nodpi/fresko_mascot.webp` : l'illustration **simplifiée,
   sans fond**, de l'écran d'accueil vide (préférée par l'utilisateur à la tuile entière).
