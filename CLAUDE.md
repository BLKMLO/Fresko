# Fresko — journal de bord (LLM)

Ce fichier est le journal de bord du projet, tenu par l'assistant (Claude Code).
**Il est mis à jour à chaque session.** Règle du dépôt : tout est publié sur `main`,
jamais sur une autre branche.

## Objectif

App Android peu gourmande en batterie qui vérifie toutes les X minutes (10 par
défaut) si un chapitre d'une œuvre suivie est sorti, sur Asura Scans, Demonic Scans et NatoManga.
Depuis la v0.2.0, **la recherche est le seul moyen de trouver une œuvre** (plus de catalogue).

## Architecture (Gradle multi-modules)

```
app/                 UI Compose (écran suivis+recherche, fiche œuvre, réglages), thème, câblage (AppContainer), SourceRegistry
feature/checker/     WorkManager : CheckWorker, CheckScheduler, ChapterNotifier (Android)
core/data/           Room (œuvres suivies, schéma v3) + DataStore (réglages, curseurs de check) (Android)
core/domain/         UpdateChecker : logique du check, pure Kotlin, testée
core/source/         Interface Source + UpdateCursor/RecentUpdates (pure Kotlin)
core/network/        HttpFetcher (interface) + OkHttpFetcher (pure Kotlin)
core/model/          Modèles : WorkKey, WorkInfo, ChapterInfo, TrackedWork… (pure Kotlin)
sources/asura/       Implémentation Asura (API JSON) + tests sur fixtures réelles
sources/demonic/     Implémentation Demonic (HTML/Jsoup) + tests sur fixtures réelles
sources/natomanga/   Implémentation NatoManga (recherche HTML + API chapitres JSON) + tests
tools/icon/          Illustration source de l'icône + scripts de découpage (voir son README)
```

Dépendances : `app → feature:checker, core:data, sources:* → core:domain → core:source → core:model, core:network`.
Les modules `core/*` (hors data) et `sources/*` sont du Kotlin JVM pur : testables sans Android.
Injection de dépendances manuelle (`app/.../AppContainer.kt`), pas de Hilt.

### Ajouter une source

1. Créer `sources/<nom>/` (copier `build.gradle.kts` d'une source existante).
2. Implémenter `com.fresko.core.source.Source`.
3. L'ajouter dans `settings.gradle.kts`, `app/build.gradle.kts` et `SourceRegistry.kt`.
Rien d'autre à toucher (UI, check, réglages sont génériques).

## Fonctionnement du check

1. `CheckScheduler` planifie `CheckWorker` (contraintes : réseau + batterie non faible).
   - Intervalle ≥ 15 min : `PeriodicWorkRequest` (minimum imposé par Android : 15 min).
   - Intervalle < 15 min (10 par défaut) : chaîne de `OneTimeWorkRequest` avec délai ;
     chaque maillon planifie le suivant (`APPEND_OR_REPLACE`, car `REPLACE`
     annulerait le worker en cours).
   - Doze peut retarder les exécutions quand le téléphone est inactif (comportement Android normal).
2. `UpdateChecker` : pour chaque source activée ayant des œuvres suivies, **une** requête
   « mises à jour récentes ». Si elle ne couvre pas toute la période depuis le dernier
   check réussi (`coversCursor = false`), requête dédiée par œuvre suivie absente.
3. Chapitre plus récent (numéro supérieur, sinon URL différente) → notification.
   Œuvre sans chapitre connu → initialisation silencieuse.
   Les mises à jour récentes donnent **plusieurs** chapitres par œuvre (`recentByKey`,
   du plus récent au plus ancien) : utile pour l'accès anticipé.
4. **Accès anticipé par œuvre** (`notify_early_access`, sources avec `hasEarlyAccess`,
   aujourd'hui Asura) : si désactivé, on prend le plus récent chapitre *non* anticipé.
   Un chapitre anticipé ignoré met l'œuvre en attente (`early_pending`) : comme le
   passage en gratuit ne fait pas remonter la série dans la liste du site, l'œuvre est
   alors vérifiée par requête dédiée à chaque check jusqu'à l'alerte (l'endpoint
   chapitres d'Asura ne renvoie que les chapitres gratuits).
5. Le curseur (`UpdateCursor` : instant + ancres) n'est enregistré que si tout a été vérifié.
6. **Statut « lu »** (v0.3.0, se règle dans la fiche de l'œuvre uniquement) : colonne `read_chapter_url` ; une œuvre est « à lire »
   si `chapter_url ≠ read_chapter_url`. Le premier chapitre connu d'une œuvre
   (référence au suivi ou initialisation par le check) est considéré comme lu
   (`CASE WHEN chapter_url IS NULL` dans `WorkDao.updateChapter`, évalué sur l'ancienne valeur).
7. **Sources désactivées** : rien n'est supprimé ; le check les ignore et l'UI masque
   leurs œuvres (compteur « masquées » sur l'accueil, « conservées » dans les réglages).

### Couverture de la période

- **Asura** : horodatages précis (`last_chapter_at`). Couvert si la dernière série de
  la page est antérieure au curseur.
- **Demonic** : dates au jour près, fuseau inconnu → mécanisme d'**ancres** :
  la liste étant triée par mise à jour, si une entrée (œuvre + même URL de chapitre)
  déjà vue au check précédent est encore là, tout ce qui a bougé depuis est au-dessus.
  Repli sur les dates : couvert si `minuit UTC(jour le plus ancien) + 24 h + 14 h ≤ curseur`.
  Au plus 3 pages.
- **NatoManga** : jamais couvert (`coversCursor = false`, aucune requête de liste) →
  une requête API par œuvre suivie à chaque check. La liste `manga-list/latest-manga`
  (24 œuvres/page, sans date, ≈ 3 910 pages le 2026-10-16) bouge trop pour couvrir
  10 min et pèse 22 003 octets compressés, contre 233 à 254 octets par œuvre via l'API.

## Endpoints observés (2026-10-15)

| Source | Usage | URL |
|---|---|---|
| Asura | check | `https://api.asurascans.com/api/series?sort=latest&order=desc&limit=20&offset=N` (20 max/page, `meta.has_more`) |
| Asura | dernier chapitre | `https://api.asurascans.com/api/series/{slug}/chapters` (n'inclut pas les chapitres premium/accès anticipé) |
| Asura | recherche | `.../api/series?search=…` |
| Asura | URL chapitre | `https://asurascans.com{public_url}/chapter/{n}` ; `/comics/{slug}` redirige (302) |
| Demonic | check | `https://demonicscans.org/lastupdates.php?list=N` (40 œuvres/page, `div.updates-element`) |
| Demonic | (plus utilisé depuis v0.2.0) | `translationlist.php?list=N` (leurs traductions ; 525 œuvres le 2026-10-15) |
| Demonic | recherche | `search.php?manga=…` |
| Demonic | dernier chapitre | `/manga/{slug}` (`#chapters-list li a`) |
| NatoManga | recherche | `https://www.natomanga.com/search/story/{alias}` (20 `div.story_item`, « Updated : Oct-14-2026 08:11 » en UTC) ; alias = `change_alias` de `/js/fsearch.js` |
| NatoManga | dernier chapitre | `/api/manga/{slug}/chapters?limit=3` (JSON, `chapter_num`, `chapter_slug`, `updated_at` UTC ; slug inconnu → 400) |
| NatoManga | URL chapitre | `/manga/{slug}/{chapter_slug}` |

- asuracomic.net → 301 vers asurascans.com.
- Demonic agrège ≈ 250 pages × 56 œuvres sur `advanced.php` (page 240 pleine, 260 vide).
- Recherche dans l'app : à partir de 2 caractères, anti-rebond 350 ms, toutes les sources
  activées en parallèle ; résultats en mémoire (seules les œuvres suivies sont en base).
- La recherche Demonic ne fournit pas de dernier chapitre ; ses URL de vignettes
  contiennent des espaces bruts (encodés en `%20` par le parser).
- natomanga.com → 301 vers www.natomanga.com ; couvertures (`storage.waitst.com`,
  `img-r*.2xstorage.com`) accessibles sans Referer (vérifié le 2026-10-16).
- Asura : 353 séries chargées par l'app lors du test réel du 2026-10-15 (`meta.total` annonçait 354).
- Chapitres Asura `is_premium` / `early_access_until` : notifiés avec la mention « accès anticipé ».

## Consommation réseau (mesurée le 2026-10-15)

Taille transférée (gzip, corps seuls, hors en-têtes HTTP/TLS) d'une requête de check :
- Asura, page 1 : 18 965 octets (60 058 non compressés)
- Demonic, page 1 : 15 919 octets (114 516 non compressés)

Formule, cas courant (une page par source, les deux sources ont des œuvres suivies) :
`octets/jour = (1440 / intervalle_min) × (18 965 + 15 919)` → à 10 min : 144 × 34 884 = 5 023 296 octets ≈ 5,0 Mo/jour.
Une source sans œuvre suivie ne fait aucune requête. Valeurs variables selon le contenu des pages.

NatoManga (mesuré le 2026-10-16) : 233 à 254 octets compressés par œuvre suivie et par check,
soit `octets/jour ≈ (1440 / intervalle_min) × N_œuvres_NatoManga × 254` (borne haute mesurée)
→ à 10 min avec 10 œuvres : 144 × 10 × 254 = 365 760 octets ≈ 0,37 Mo/jour.

## Build

- `./gradlew test` (tests JVM), `./gradlew :app:assembleDebug|assembleRelease`.
- AGP 8.7.3, Kotlin 2.1.0, Gradle 8.14.3, compileSdk/targetSdk 35, minSdk 26, JDK 17+.
- Environnement Claude Code cloud : Maven Central répond 429 via le proxy → script
  d'init **hors dépôt** `~/.gradle/init.d/central-mirror.gradle.kts` qui remplace
  Maven Central par `https://maven-central.storage-download.googleapis.com/maven2/`.
  SDK Android installé dans `/opt/android-sdk` (`local.properties`, non versionné).
- CI : `.github/workflows/android.yml` (tests + APK release en artefact).
- **Release** : `.github/workflows/release.yml` sur tag `v*` → tests, APK, release GitHub
  avec `fresko-vX.Y.Z.apk` et les notes `.github/release-notes/vX.Y.Z.md` (à écrire
  avant de taguer). Procédure : version dans `app/build.gradle.kts` (versionCode +1),
  notes, commit sur `main`, puis lancer le workflow « Release » à la main avec
  `version = vX.Y.Z` (il crée le tag). Dans l'environnement Claude Code cloud, le
  proxy git refuse de pousser un tag (403) : déclenchement via l'API GitHub.
- **Signature** : le dépôt est public, aucune clé n'est versionnée. Si les secrets
  `FRESKO_KEYSTORE_B64`, `FRESKO_KEYSTORE_PASSWORD`, `FRESKO_KEY_ALIAS`, `FRESKO_KEY_PASSWORD`
  existent, la release est signée avec ; sinon clé debug de la CI (différente à chaque
  run → mise à jour impossible par-dessus, il faut désinstaller). Procédure utilisateur
  dans le README. Le 2026-10-16, une clé a été générée pour l'utilisateur (PKCS12, RSA 4096,
  alias `fresko`, CN=Fresko, SHA-256 du certificat
  `F4:81:33:2A:BD:A1:0B:F9:F6:0B:6A:65:8F:4F:4F:C6:63:10:A0:39:39:2E:06:4F:CF:C7:50:45:2F:0C:73:3E`)
  et vérifiée par une signature locale ; elle n'est **pas** dans le dépôt. Relancer le
  workflow Release sur une version existante remplace l'APK de la release.
- **Captures d'écran** (pas d'appareil ici) : `./gradlew :app:testDebugUnitTest -Pscreenshots`
  → `app/build/screenshots/*.png` (Robolectric, rendu natif, données d'exemple, sans
  couvertures). Test `app/src/test/.../screenshots/ScreenshotTest.kt`, exclu par défaut.
  Les écrans sont sans état (`LibraryContent`, `SettingsContent`) pour cela.
  Ne pas combiner avec `test` : la variante *release* n'a pas l'activité de test
  (`ui-test-manifest` en `debugImplementation`) → échec des captures en release.

## Limites connues / pistes

- Pas d'émulateur dans l'environnement de dev (pas de KVM) : UI et WorkManager
  **non testés sur appareil** à ce jour. Testés : parsers (fixtures réelles), logique
  de check (unitaires), appels réels aux deux sites (test de fumée JVM, non versionné),
  rendu des écrans (captures Robolectric), migrations Room 1→2 et 2→3 (rejouées sur SQLite).
- Le scraping dépend de la structure des sites : si un site change, seuls les
  fichiers de `sources/<site>/` sont à adapter (fixtures de test à recapturer).
- Toucher une notification ouvre le chapitre mais ne le marque pas comme lu.
- Pistes : écran « historique des chapitres », option Wi-Fi uniquement, export/import
  des œuvres suivies, dernier chapitre dans les résultats de recherche Demonic,
  marquer lu depuis la notification.

## Journal

### 2026-10-15 — Session 1 : création du projet
- Reconnaissance des sites (endpoints ci-dessus).
- Squelette multi-modules, sources Asura/Demonic, UpdateChecker, Room/DataStore,
  WorkManager (chaîne < 15 min / périodique ≥ 15 min), notifications, UI Compose
  (bibliothèque + paramètres), CI GitHub Actions.
- Remplacement de la couverture « par date » de Demonic par les ancres : sans elles,
  un check chargeait 2 pages la plupart du temps (observé en test réel).
- Au suivi d'une œuvre : référence = max(chapitre du catalogue, requête dédiée),
  car l'endpoint chapitres d'Asura omet les chapitres en accès anticipé.
- Correctif : un maillon de chaîne interrompu ne replanifie pas (sinon chaîne doublée).
- 22 tests unitaires verts ; `assembleDebug` et `assembleRelease` (R8) OK.

### 2026-10-15 — Session 2 : v0.2.0
- Demandes : recherche = seul moyen de trouver une œuvre ; accès anticipé Asura
  paramétrable par œuvre ; application « la plus esthétique possible » ; release v0.2.0.
- Suppression du catalogue (`Source.fetchCatalog` retiré, `search` obligatoire).
  Base Room v2 : `notify_early_access`, `early_pending`, suppression des œuvres non suivies
  (migration rejouée sur SQLite : schéma identique à celui exporté par Room).
- `RecentUpdates.recentByKey` (liste de chapitres) + logique d'accès anticipé dans
  `UpdateChecker` (4 nouveaux tests ; 26 tests au total).
- Nouvelle UI : thème de marque (encre/corail/lavande, clair et sombre), en-tête en
  dégradé, recherche en direct par source avec squelettes de chargement, cartes avec
  pastilles source / « Nouveau » / « Anticipé », fiche œuvre (feuille modale), tirer pour
  vérifier, réglages en cartes, nouvelle icône adaptative, transitions de navigation.
- Captures Robolectric pour valider le rendu (corrigé : badge « Anticipé » qui se
  coupait, dates forcées en français).
- Release : workflow sur tag ou manuel (crée le tag), notes dans `.github/release-notes/`.
  Push de tag refusé par le proxy (403) → release v0.2.0 lancée par déclenchement manuel.

### 2026-10-16 — Session 3 : v0.3.0
- Demandes : marquer une œuvre comme lue ; source NatoManga ; nouvelle icône (image
  fournie) ; sources désactivables sans perdre les œuvres ; procédure de clé sans
  logiciel ; release v0.3.0.
- Statut « lu » : Room v3 (`read_chapter_url`, migration : déjà connu = lu, rejouée
  sur SQLite), sections « À lire » / « À jour », bouton ✓, « Marquer lu / non lu »
  dans la fiche, lecture = lu.
- NatoManga : module `sources/natomanga` (5 tests sur fixtures réelles), check par
  requête API dédiée par œuvre ; chaîne recherche → dernier chapitre → détection
  validée en réel. Couleur de source : ambre.
- Icône : 1re version détourée (fond retiré) **refusée par l'utilisateur** : seul le décor
  flou Play Store devait partir, le fond illustré fait le charme. Version finale :
  illustration complète en fond (`tools/icon/tile.py`), premier plan transparent,
  monochrome détouré ; tuile entière (cadre lumineux) dans l'écran vide (remplacée en v0.4.0).
- Bouton ✓ sur les cartes **retiré à la demande** : marquer lu uniquement depuis la fiche
  de l'œuvre (« Marquer lu / non lu », ou « Lire le chapitre »).
- Sources désactivées : déjà conservées depuis la v0.1 ; ajout des compteurs
  (« N œuvres conservées », « N œuvres masquées »).
- Release v0.3.0 publiée une 1re fois avec l'icône détourée et le bouton ✓, puis
  reconstruite (même tag) après les deux corrections demandées.
- Clé de signature générée pour l'utilisateur (hors dépôt), signature vérifiée localement
  avec `apksigner` ; procédure web dans le README.

### 2026-10-16 — Session 4 : v0.4.0
- Demande : l'icône convient ; l'illustration de l'écran d'accueil (vide) était mieux
  avant → retour à la version **détourée, sans fond** (cloche + planches).
- Scripts `tools/icon` réorganisés : `tile.py` = icône couleur seulement ; `layers.py` =
  monochrome + illustration d'accueil (plus de premier plan généré). Régénération
  vérifiée : monochrome identique octet pour octet.
- Release mineure v0.4.0 (versionCode 4), choix laissé à l'assistant (« autonome »).
