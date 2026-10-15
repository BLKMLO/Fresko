# Fresko

Application Android légère qui vérifie en arrière-plan la sortie de nouveaux
chapitres de webtoons suivis et envoie une notification.

Sources supportées : **Asura Scans** (asurascans.com, ex-asuracomic.net) et
**Demonic Scans** (demonicscans.org).

## Fonctionnalités

- Écran principal : catalogue des sources activées, recherche, filtre par source ;
  ♥ pour suivre / ne plus suivre. Les œuvres suivies remontent en haut de la liste.
- Paramètres : activer/désactiver chaque source, fréquence de vérification
  (10 min par défaut), « Vérifier maintenant ».
- Notification par nouveau chapitre ; un appui ouvre le chapitre dans le navigateur.

## Installer

- Via la CI GitHub : onglet *Actions* → dernier run → artefact `fresko-apk`.
- En local : `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  (signé avec la clé debug, installable directement).

Android 8.0 (API 26) minimum.

## Développement

Voir [CLAUDE.md](CLAUDE.md) : architecture, choix techniques, journal de bord.
