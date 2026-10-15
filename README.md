# Fresko

Application Android légère qui vérifie en arrière-plan la sortie de nouveaux
chapitres de webtoons suivis et envoie une notification.

Sources supportées : **Asura Scans** (asurascans.com, ex-asuracomic.net) et
**Demonic Scans** (demonicscans.org).

## Fonctionnalités

- Écran principal : vos œuvres suivies (les plus récemment mises à jour en haut) et
  une barre de recherche en direct sur les sources activées ; « Suivre » sur un résultat.
- Fiche d'une œuvre : dernier chapitre, lecture, page de l'œuvre, arrêt du suivi et,
  pour Asura, alertes accès anticipé activables par œuvre.
- Tirer l'écran vers le bas : vérification immédiate.
- Réglages : activer/désactiver chaque source, fréquence de vérification
  (10 min par défaut), « Vérifier maintenant ».
- Notification par nouveau chapitre ; un appui ouvre le chapitre dans le navigateur.

## Installer

- Page *Releases* du dépôt : `fresko-vX.Y.Z.apk`.
- Via la CI GitHub : onglet *Actions* → dernier run → artefact `fresko-apk`.
- En local : `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  (signé avec la clé debug, installable directement).

Android 8.0 (API 26) minimum.

## Développement

Voir [CLAUDE.md](CLAUDE.md) : architecture, choix techniques, journal de bord.
