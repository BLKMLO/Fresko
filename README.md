# Fresko

Application Android légère qui vérifie en arrière-plan la sortie de nouveaux
chapitres de webtoons suivis et envoie une notification.

Sources supportées : **Asura Scans** (asurascans.com, ex-asuracomic.net),
**Demonic Scans** (demonicscans.org) et **NatoManga** (natomanga.com).

## Fonctionnalités

- Écran principal : vos œuvres suivies (les plus récemment mises à jour en haut) et
  une barre de recherche en direct sur les sources activées ; « Suivre » sur un résultat.
- Fiche d'une œuvre : dernier chapitre, lecture, page de l'œuvre, arrêt du suivi et,
  pour Asura, alertes accès anticipé activables par œuvre.
- Statut « lu » : sections « À lire » / « À jour » ; « Marquer lu » dans la fiche de l'œuvre.
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

## Clé de signature (releases installables par-dessus)

Sans clé, chaque release est signée avec une clé temporaire de la CI : Android refuse
alors de l'installer par-dessus la précédente. Une fois pour toutes, depuis un
navigateur (aucun logiciel à installer) :

1. Obtenir un fichier `fresko.jks` et son mot de passe (une clé générée pour vous, ou
   `keytool -genkeypair -keystore fresko.jks -storetype PKCS12 -alias fresko -keyalg RSA -keysize 4096 -validity 10950`
   sur une machine ayant Java), puis son contenu en Base64.
2. Sur GitHub : dépôt → **Settings** → **Secrets and variables** → **Actions** →
   **New repository secret**, quatre fois :
   `FRESKO_KEYSTORE_B64` (le Base64), `FRESKO_KEYSTORE_PASSWORD`, `FRESKO_KEY_PASSWORD`
   (le mot de passe, deux fois), `FRESKO_KEY_ALIAS` (`fresko`).
3. Onglet **Actions** → **Release** → **Run workflow** → version (ex. `v0.3.0`) :
   l'APK de la release est reconstruit et signé avec votre clé.
4. Désinstaller une dernière fois l'ancienne version, installer la nouvelle. Les
   suivantes s'installeront par-dessus.

Ne jamais publier la clé ni le mot de passe ; en garder une copie privée (si elle est
perdue, retour à l'étape « désinstaller »).

## Développement

Voir [CLAUDE.md](CLAUDE.md) : architecture, choix techniques, journal de bord.
