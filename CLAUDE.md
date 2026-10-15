# Fresko — journal de bord (LLM)

Ce fichier est le journal de bord du projet, tenu par l'assistant (Claude Code).
**Il est mis à jour à chaque session.** Règle du dépôt : tout est publié sur `main`,
jamais sur une autre branche.

## Objectif

App Android peu gourmande en batterie qui vérifie toutes les X minutes (10 par
défaut) si un chapitre d'une œuvre suivie est sorti, sur Asura Scans et Demonic Scans.
Depuis la v0.2.0, **la recherche est le seul moyen de trouver une œuvre** (plus de catalogue).

## Architecture (Gradle multi-modules)

```
app/                 UI Compose (écran suivis+recherche, fiche œuvre, réglages), thème, câblage (AppContainer), SourceRegistry
feature/checker/     WorkManager : CheckWorker, CheckScheduler, ChapterNotifier (Android)
core/data/           Room (œuvres suivies, schéma v2) + DataStore (réglages, curseurs de check) (Android)
core/domain/         UpdateChecker : logique du check, pure Kotlin, testée
core/source/         Interface Source + UpdateCursor/RecentUpdates (pure Kotlin)
core/network/        HttpFetcher (interface) + OkHttpFetcher (pure Kotlin)
core/model/          Modèles : WorkKey, WorkInfo, ChapterInfo, TrackedWork… (pure Kotlin)
sources/asura/       Implémentation Asura (API JSON) + tests sur fixtures réelles
sources/demonic/     Implémentation Demonic (HTML/Jsoup) + tests sur fixtures réelles
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

### Couverture de la période

- **Asura** : horodatages précis (`last_chapter_at`). Couvert si la dernière série de
  la page est antérieure au curseur.
- **Demonic** : dates au jour près, fuseau inconnu → mécanisme d'**ancres** :
  la liste étant triée par mise à jour, si une entrée (œuvre + même URL de chapitre)
  déjà vue au check précédent est encore là, tout ce qui a bougé depuis est au-dessus.
  Repli sur les dates : couvert si `minuit UTC(jour le plus ancien) + 24 h + 14 h ≤ curseur`.
  Au plus 3 pages.

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

- asuracomic.net → 301 vers asurascans.com.
- Demonic agrège ≈ 250 pages × 56 œuvres sur `advanced.php` (page 240 pleine, 260 vide).
- Recherche dans l'app : à partir de 2 caractères, anti-rebond 350 ms, toutes les sources
  activées en parallèle ; résultats en mémoire (seules les œuvres suivies sont en base).
- La recherche Demonic ne fournit pas de dernier chapitre ; ses URL de vignettes
  contiennent des espaces bruts (encodés en `%20` par le parser).
- Asura : 353 séries chargées par l'app lors du test réel du 2026-10-15 (`meta.total` annonçait 354).
- Chapitres Asura `is_premium` / `early_access_until` : notifiés avec la mention « accès anticipé ».

## Consommation réseau (mesurée le 2026-10-15)

Taille transférée (gzip, corps seuls, hors en-têtes HTTP/TLS) d'une requête de check :
- Asura, page 1 : 18 965 octets (60 058 non compressés)
- Demonic, page 1 : 15 919 octets (114 516 non compressés)

Formule, cas courant (une page par source, les deux sources ont des œuvres suivies) :
`octets/jour = (1440 / intervalle_min) × (18 965 + 15 919)` → à 10 min : 144 × 34 884 = 5 023 296 octets ≈ 5,0 Mo/jour.
Une source sans œuvre suivie ne fait aucune requête. Valeurs variables selon le contenu des pages.

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
  notes, commit sur `main`, `git tag vX.Y.Z && git push origin vX.Y.Z`.
- **Signature** : le dépôt est public, aucune clé n'est versionnée. Si les secrets
  `FRESKO_KEYSTORE_B64`, `FRESKO_KEYSTORE_PASSWORD`, `FRESKO_KEY_ALIAS`, `FRESKO_KEY_PASSWORD`
  existent, la release est signée avec ; sinon clé debug de la CI (différente à chaque
  run → mise à jour impossible par-dessus, il faut désinstaller).
- **Captures d'écran** (pas d'appareil ici) : `./gradlew :app:testDebugUnitTest -Pscreenshots`
  → `app/build/screenshots/*.png` (Robolectric, rendu natif, données d'exemple, sans
  couvertures). Test `app/src/test/.../screenshots/ScreenshotTest.kt`, exclu par défaut.
  Les écrans sont sans état (`LibraryContent`, `SettingsContent`) pour cela.

## Limites connues / pistes

- Pas d'émulateur dans l'environnement de dev (pas de KVM) : UI et WorkManager
  **non testés sur appareil** à ce jour. Testés : parsers (fixtures réelles), logique
  de check (unitaires), appels réels aux deux sites (test de fumée JVM, non versionné),
  rendu des écrans (captures Robolectric), migration Room 1→2 (rejouée sur SQLite).
- Le scraping dépend de la structure des sites : si un site change, seuls les
  fichiers de `sources/<site>/` sont à adapter (fixtures de test à recapturer).
- Pistes : écran « historique des chapitres », option Wi-Fi uniquement, export/import
  des œuvres suivies, dernier chapitre dans les résultats de recherche Demonic.

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
- Release : workflow sur tag, notes dans `.github/release-notes/`.
