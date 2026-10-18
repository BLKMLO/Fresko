<div align="center">

# 🔔 Fresko

**Soyez prévenu dès qu'un chapitre sort — sans vider votre batterie.**

Fresko surveille en arrière-plan les webtoons que vous suivez sur
Asura Scans, Demonic Scans et NatoManga, et vous envoie une notification
dès qu'un nouveau chapitre est publié. Un appui, et vous lisez.

[Télécharger](https://github.com/BLKMLO/Fresko/releases/latest) ·
[Premiers pas](#premiers-pas) ·
[Sources](#sources-prises-en-charge)

</div>

---

```
🔔 Fresko                                                  ⚙

  🔍 Rechercher une œuvre…

  À LIRE  2
  ┌──────────────────────────────────────────────────────┐
  │ La Tour sans fin              Asura   Nouveau        │
  │ Chapitre 142                          Anticipé       │
  ├──────────────────────────────────────────────────────┤
  │ Le Dernier Héritier           NatoManga   Nouveau    │
  │ Chapitre 87                                          │
  └──────────────────────────────────────────────────────┘

  À JOUR  1
  ┌──────────────────────────────────────────────────────┐
  │ Lame d'Écume                  Demonic                │
  │ Chapitre 63                                          │
  └──────────────────────────────────────────────────────┘

  ↓ tirer pour vérifier maintenant
```

*Maquette de l'écran principal ; titres d'œuvres fictifs, à titre d'illustration.*

## Pourquoi Fresko

**Une seule requête par source.** À chaque vérification, Fresko lit la liste
des dernières mises à jour du site plutôt que d'interroger chaque œuvre une à
une. Une source sans œuvre suivie ne fait aucune requête. Les détails et les
mesures sont plus bas, dans [Consommation](#consommation).

**Rien ne vous échappe.** Si la liste des mises à jour ne couvre pas toute la
période depuis la dernière vérification (téléphone éteint, réseau coupé),
Fresko vérifie une à une les œuvres manquantes. La progression n'est
enregistrée que lorsque tout a été vérifié.

**L'accès anticipé, à votre choix.** Sur Asura, chaque œuvre a son propre
réglage : être prévenu dès la sortie en accès anticipé, ou seulement quand le
chapitre devient gratuit.

**Vos œuvres survivent à une désinstallation.** Fresko tient à jour un fichier
de sauvegarde dans le dossier de votre choix. Réinstaller l'app puis rechoisir
ce dossier restaure tout.

## Fonctionnalités

| | |
|---|---|
| 🔍 **Recherche en direct** | Sur toutes les sources activées en même temps ; « Suivre » sur un résultat. |
| 📚 **Œuvres suivies** | Les plus récemment mises à jour en haut, rangées en « À lire » / « À jour ». |
| 📖 **Fiche d'une œuvre** | Dernier chapitre, lecture, page de l'œuvre, « Marquer lu / non lu », arrêt du suivi, alertes accès anticipé (Asura). |
| 🔔 **Notifications** | Une par nouveau chapitre ; un appui ouvre le chapitre dans le navigateur. |
| ↻ **Vérification immédiate** | Tirer l'écran principal vers le bas. |
| 💾 **Sauvegarde** | Fichier hors de l'app, mis à jour automatiquement (Réglages → Sauvegarde). |
| ⚙ **Réglages** | Sources activables une à une, fréquence de vérification, « Vérifier maintenant ». |

Désactiver une source ne supprime rien : ses œuvres sont simplement masquées,
et réapparaissent quand on la réactive.

## Installation

Téléchargez `fresko-vX.Y.Z.apk` depuis la
[dernière release](https://github.com/BLKMLO/Fresko/releases/latest) et
ouvrez-le sur votre téléphone.

| Où | Quoi |
|---|---|
| Page *Releases* | `fresko-vX.Y.Z.apk`, signé : les versions suivantes s'installent par-dessus |
| Onglet *Actions* → dernier run | artefact `fresko-apk` |
| En local | `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (clé debug) |

Android 8.0 (API 26) minimum.

## Premiers pas

**1. Cherchez une œuvre.** Tapez au moins deux caractères dans la barre de
recherche : les résultats arrivent source par source.

**2. Suivez-la.** « Suivre » sur un résultat : son dernier chapitre devient la
référence, considérée comme lue.

**3. Laissez faire.** Fresko vérifie toutes les 10 minutes par défaut. Quand un
chapitre sort, l'œuvre passe dans « À lire » et une notification arrive :

```
🔔 Fresko · asura
   La Tour sans fin
   Chapitre 143 (accès anticipé)
```

*Notification d'exemple, titre fictif.*

## Sources prises en charge

| Source | Site | Accès anticipé | Vérification |
|---|---|---|---|
| **Asura Scans** | asurascans.com (ex-asuracomic.net) | ✓ par œuvre | liste des mises à jour |
| **Demonic Scans** | demonicscans.org | — | liste des mises à jour |
| **NatoManga** | natomanga.com | — | une requête par œuvre suivie |

## Fréquence et batterie

| Réglage | Valeurs proposées |
|---|---|
| Fréquence | 10 min (défaut), 15 min, 30 min, 1 h, 2 h, 6 h, 12 h |
| Conditions | réseau disponible, batterie non faible |

Android impose 15 minutes minimum aux tâches périodiques : en dessous, Fresko
enchaîne des tâches ponctuelles. Quand le téléphone est en veille prolongée
(Doze), Android peut retarder les vérifications — c'est le comportement normal
du système.

### Consommation

Mesurée le 2026-10-15 (corps compressés, hors en-têtes HTTP/TLS) : 18 965 octets
pour une page Asura, 15 919 octets pour une page Demonic.

```
octets/jour = (1440 / intervalle_min) × (18 965 + 15 919)
            = 144 × 34 884 = 5 023 296 octets ≈ 5,0 Mo/jour   (à 10 min)
```

NatoManga (mesuré le 2026-10-16) : 233 à 254 octets par œuvre suivie et par
vérification.

```
octets/jour ≈ (1440 / intervalle_min) × N_œuvres × 254
            = 144 × 10 × 254 = 365 760 octets ≈ 0,37 Mo/jour   (10 œuvres, à 10 min)
```

Valeurs variables selon le contenu des pages.

## Clé de signature

Sans clé, chaque release est signée avec une clé temporaire de la CI : Android
refuse alors de l'installer par-dessus la précédente. À faire une fois pour
toutes, depuis un navigateur, sans aucun logiciel à installer.

**1. Obtenez une clé.** Un fichier `fresko.jks` et son mot de passe (une clé
générée pour vous, ou la commande ci-dessous sur une machine ayant Java), puis
son contenu en Base64.

```bash
keytool -genkeypair -keystore fresko.jks -storetype PKCS12 -alias fresko \
        -keyalg RSA -keysize 4096 -validity 10950
```

**2. Ajoutez quatre secrets.** Dépôt → **Settings** → **Secrets and
variables** → **Actions** → **New repository secret** :

| Secret | Valeur |
|---|---|
| `FRESKO_KEYSTORE_B64` | le contenu Base64 du fichier |
| `FRESKO_KEYSTORE_PASSWORD` | le mot de passe |
| `FRESKO_KEY_PASSWORD` | le mot de passe |
| `FRESKO_KEY_ALIAS` | `fresko` |

**3. Lancez une release.** Onglet **Actions** → **Release** → **Run
workflow** → version (`v0.5.1` ou `0.5.1`). L'APK est reconstruit et signé
avec votre clé. Les secrets sont **permanents** : toutes les releases suivantes
sont signées automatiquement. Sans eux, le workflow échoue volontairement.

**4. Réinstallez une dernière fois.** Désinstallez l'ancienne version,
installez la nouvelle : les suivantes s'installeront par-dessus.

> Ne publiez jamais la clé ni le mot de passe, et gardez-en une copie privée.
> Si elle est perdue, retour à l'étape « désinstaller ».

## Contribuer

```bash
./gradlew test                  # les tests JVM
./gradlew :app:assembleDebug    # un APK de développement
```

Les sources et la logique de vérification sont du Kotlin pur, testées sans
Android sur des pages réelles enregistrées. [`CLAUDE.md`](CLAUDE.md) décrit
l'architecture, les choix techniques et le journal de bord ;
[`tools/icon/README.md`](tools/icon/README.md) l'icône et ses scripts.

Ajouter une source : créer `sources/<nom>/`, implémenter
`com.fresko.core.source.Source`, puis la déclarer dans `settings.gradle.kts`,
`app/build.gradle.kts` et `SourceRegistry.kt`. L'interface, la vérification et
les réglages s'adaptent seuls.
