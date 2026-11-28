# Fresko — journal de bord (LLM)

Ce fichier est le journal de bord du projet, tenu par l'assistant (Claude Code).
**Il est mis à jour à chaque session.** Règle du dépôt : tout est publié sur `main`,
jamais sur une autre branche.

## Objectif

App Android peu gourmande en batterie qui vérifie toutes les X minutes (10 par
défaut) si un chapitre d'une œuvre suivie est sorti, sur Asura Scans, Demonic Scans et SaiyanManga
(ex-NatoManga : nom affiché depuis 2026-11-28 ; identifiant interne, module et classe restent
`natomanga` / `NatoMangaSource`, car l'identifiant est stocké en base, sauvegardes et réglages).
Depuis la v0.2.0, **la recherche est le seul moyen de trouver une œuvre** (plus de catalogue).

## Architecture (Gradle multi-modules)

```
app/                 UI Compose (écran suivis+recherche, fiche œuvre, réglages), thème, câblage (AppContainer), SourceRegistry
feature/checker/     WorkManager : CheckWorker, CheckScheduler, ChapterNotifier (Android)
core/data/           Room (œuvres suivies, schéma v5) + DataStore (réglages, curseurs de check) (Android)
core/domain/         UpdateChecker : logique du check, pure Kotlin, testée
core/source/         Interface Source + UpdateCursor/RecentUpdates (pure Kotlin)
core/network/        HttpFetcher (interface) + OkHttpFetcher (pure Kotlin)
core/model/          Modèles : WorkKey, WorkInfo, ChapterInfo, TrackedWork… (pure Kotlin)
sources/asura/       Implémentation Asura (API JSON) + tests sur fixtures réelles
sources/demonic/     Implémentation Demonic (HTML/Jsoup) + tests sur fixtures réelles
sources/natomanga/   Implémentation NatoManga (recherche JSON/HTML + API chapitres JSON, domaine suivi automatiquement) + tests
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
   - Option **Wi-Fi uniquement** (v1.0.0, DataStore `wifi_only`) : contrainte `UNMETERED` pour
     les checks planifiés ; le check manuel accepte tout réseau. État du check manuel
     (`ManualCheck` : IDLE / WAITING / RUNNING) : au-delà de 2 s en file, l'accueil affiche
     « Pas de connexion » (annulable) au lieu d'un spinner infini.
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
   **v1.0.0** : `UpdateChecker.check()` est sérialisé (`Mutex`) — un check manuel pendant un check
   planifié notifiait deux fois. Une erreur « tout le site » pendant les requêtes par œuvre
   (`CheckErrors.isSiteWide` : réseau, délai, 5xx, 403/429, défi Cloudflare) interrompt la
   source (erreur de source) au lieu d'attendre chaque œuvre (limite de 10 min de WorkManager) ;
   400/404 restent des erreurs d'œuvre. Même chapitre passé d'anticipé à gratuit : mise à jour
   silencieuse (badge). `isNewerThan` sans numéro compare les URL **au domaine près**
   (`sameLocation`).
6. **Statut « lu »** (v0.3.0, se règle dans la fiche de l'œuvre uniquement) : colonne `read_chapter_url` ; une œuvre est « à lire »
   si `chapter_url ≠ read_chapter_url`. Le premier chapitre connu d'une œuvre
   (référence au suivi ou initialisation par le check) est considéré comme lu
   (`CASE WHEN chapter_url IS NULL` dans `WorkDao.updateChapter`, évalué sur l'ancienne valeur).
7. **Sources désactivées** : rien n'est supprimé ; le check les ignore et l'UI masque
   leurs œuvres (compteur « masquées » sur l'accueil, « conservées » dans les réglages).
   Depuis la v0.5.0, DataStore mémorise les sources **désactivées** (`disabled_sources`) ;
   l'ancienne clé `enabled_sources` est reprise par `SourceSelection` (seules asura et
   demonic pouvaient y être désactivées) puis supprimée. Bug corrigé : une source ajoutée
   après un premier réglage restait désactivée (NatoManga « absente » de la recherche).
9. **Anti-robot Cloudflare** (v0.6.0, `app/net/`) :
   - `OkHttpFetcher` lève `CloudflareChallengeException` (code 403/429/503 + en-tête
     `cf-mitigated: challenge`, ou serveur cloudflare + marqueurs de page de défi ;
     `CloudflareDetector`, 4 tests).
   - Cookies partagés OkHttp ↔ WebView (`WebViewCookieJar` sur `CookieManager`) et même
     User-Agent partout (`WebUserAgent` : UA de la WebView sans « ; wv » ni « Version/x ») :
     `cf_clearance` est lié au User-Agent.
   - `ChallengeAwareFetcher` : sur défi, résolution invisible (`CloudflareSolver`, WebView hors
     écran, attente d'un **nouveau** `cf_clearance`, 30 s max, 1 tentative / 30 min / site),
     puis un nouvel essai ; sinon `ChallengeRegistry` note le site « en attente »
     (SharedPreferences `challenges`) et l'erreur remonte. Toute requête réussie l'efface.
   - UI : bandeau « Vérification requise » sur l'accueil, bouton dans la section de recherche,
     notification depuis le worker (1 / 6 h / site). `ChallengeActivity` affiche le site et
     se ferme dès qu'un nouveau `cf_clearance` apparaît (puis recherche + check relancés).
   - Tests Robolectric `ChallengeAwareFetcherTest` (6) ; `robolectric.dependency.repo.url`
     pointe désormais toujours vers le miroir Google de Maven Central.
   - **2026-11-28** (« l'anti-bot n'est parfois pas détecté et bloque recherche et
     rafraîchissement ») — défis manqués avant : `cf-mitigated` hors 403/429/503 ; page de défi
     en **200** ; **HTML à la place du JSON** (les API répondent `application/json`, vérifié ;
     l'erreur finissait en « réponse inattendue » / recherche vide). `CloudflareDetector` :
     `cf-mitigated` quel que soit le code ; 200 + marqueurs forts seulement (`_cf_chl_opt`,
     `<title>Just a moment`, `orchestrate/chl_page` — pas `challenge-platform`, injecté aussi dans
     des pages normales) ; `Accept: application/json` (`OkHttpFetcher.JSON_HEADERS`, envoyé par la
     source) + HTML Cloudflare en 2xx/403/429/503 = défi (un 404 HTML = route inconnue, et une
     œuvre inconnue renvoie désormais **404 JSON** « Comic not found », plus 400).
   - **Repli WebView** (`WebViewPageFetcher`) : si le client HTTP reste bloqué après un cookie
     obtenu (l'autorisation peut dépendre de l'empreinte du navigateur), la page est chargée
     dans une WebView invisible (images coupées) et lue par JS (`PageSnapshot` : JSON brut du
     `<pre>` ou HTML). Échec → pas de nouvel essai avant 10 min ; résolution invisible en échec
     → pas de repli (évite ~1 min d'attente de la recherche). Non testable ici (pas de vraie
     WebView) : logique testée avec des faux, lecture de page testée sous Robolectric.
   - La vérification ouvre désormais **la page bloquée** (avant : l'accueil, qui peut ne pas
     être protégé) ; bouton **« Terminé »** dans l'écran de vérification (s'il ne se ferme pas
     seul : défi déjà passé, protection non Cloudflare) → recherche et check relancés.
10. **Erreurs de check** (v0.7.0) : un échec de la requête dédiée à
   une œuvre (exception, ou source qui ne renvoie aucun chapitre) est enregistré sur l'œuvre
   (`check_error`, `check_error_at`, Room v4) ; l'œuvre est réinterrogée à chaque check tant
   que l'erreur persiste, et l'erreur s'efface dès qu'elle est revue. Échec global d'une source
   (liste des mises à jour) : DataStore `check_error_<source>`, effacé par `saveCursor`.
   Messages : `CheckErrors` (core/domain). Affichage : ligne rouge sur la carte, bloc dans la
   fiche, bandeaux sur l'accueil, ligne dans Réglages > Sources. **Avant la v0.7.0, ces erreurs
   n'étaient que journalisées (Log.w), sauf les défis Cloudflare.** Pas de notification d'erreur.
   Au suivi d'une œuvre, l'échec de la requête de référence est aussi enregistré.
11. **Même œuvre sur plusieurs sources** (v0.7.0, option A choisie par l'utilisateur :
   regroupement manuel). Colonne `group_id` (UUID, Room v5) : chaque source reste une ligne
   avec son propre dernier chapitre et son propre check. `UpdateChecker` ne notifie pas un
   chapitre dont le numéro ≤ meilleur numéro connu des **autres** sources du groupe
   (`groupBestNumber`) : la première source qui publie gagne ; sans numéro → notifié.
   Statut « lu » d'un groupe **par numéro** (`read_chapter_number`, migration : = numéro du
   chapitre si lu) : à lire si max(chapitres) > max(numéros lus), repli sur l'URL si un numéro
   manque. Marquer lu / non lu et « Ne plus suivre » portent sur tout le groupe ; « × » retire
   une source (groupe réduit à 1 → dissous). Ajout : fiche > « Ajouter une source » → mode
   liaison (`linkTarget`) : recherche pré-remplie avec le titre, sources déjà présentes exclues,
   bouton « Lier » (une œuvre déjà suivie ailleurs rejoint le groupe, groupes fusionnés).
   Référence d'une source ajoutée : lue, **sauf** si elle est en avance sur le numéro lu des
   autres sources (`markReferenceRead`) → l'œuvre passe « à lire », sans notification.
   UI : une carte par groupe (source en tête = chapitre le plus avancé, puis le plus ancien
   publié), toutes les pastilles, « Ch. N · Source ». Sauvegarde : champs optionnels `group`
   et `read_chapter_number` (format v1 inchangé, lisible par les anciennes versions).
   Suppressions (« Ne plus suivre » et « × » d'une source) confirmées par un dialogue (v0.7.1).
   **Limite** : numérotations différentes entre sites non gérées (pas de décalage par source) ;
   les numéros de chaque source sont visibles dans la fiche.
12. **Domaine suivi automatiquement** (v1.0.0, NatoManga) : `HttpFetcher.fetch` renvoie l'URL
   finale après redirections (`OkHttpFetcher` : `response.request.url`, testé sur deux serveurs
   locaux). Redirection vers un **autre hôte avec le même chemin** (`movedTo`, https seulement)
   → nouvelle adresse mémorisée (`SiteAddressStore`, SharedPreferences `site_addresses`, clé =
   source + adresse par défaut de la version : une adresse apprise est oubliée si la valeur par
   défaut change à la mise à jour). `Source.openableUrl` réécrit les URL enregistrées
   (`/manga/…`) vers le domaine actuel (UI, notifications) ; `Source.currentHost` affiché dans
   Réglages > Sources. Hôtes obsolètes purgés de `ChallengeRegistry`.
13. **Notifications** (v1.0.0) : `ChapterActions` (fourni par l'app : `AppChapterActions`).
   Toucher → `OpenChapterActivity` (invisible) marque lu puis ouvre le navigateur ; bouton
   « Marquer lu » → `MarkReadReceiver`. `markReadIfCurrent` : seulement si le chapitre notifié
   est encore le dernier connu (URL au domaine près, ou même numéro). Nom affiché de la source
   en sous-texte. `ChapterNotifier.notificationsAllowed` (permission, app, canal) → bandeau
   « Notifications désactivées » sur l'accueil (revérifié à chaque retour).
8. **Sauvegarde** (v0.5.0) : `BackupController` (app) écrit `fresko-sauvegarde.json`
   (format `BackupFile` v1 dans `core/domain/backup`, JSON lisible) dans un dossier choisi
   via le sélecteur Android (accès persistant), à chaque changement (anti-rebond 3 s,
   pas d'écriture si contenu identique). Choisir un dossier contenant déjà une sauvegarde
   alors que l'app ne suit rien la restaure. Restauration manuelle : Réglages ou accueil
   vide. Les curseurs de check ne sont pas sauvegardés (premier check = vérification par
   œuvre). L'Auto Backup Google d'Android (`allowBackup`) reste actif en complément, mais
   ne restaure que si la signature est la même.
   **v1.0.0** : restaurer **ajoute seulement les œuvres absentes** (`WorkRepository.restore`),
   les œuvres déjà suivies ne sont plus écrasées (recul des chapitres → alertes en double) ;
   groupes d'une seule œuvre dissous. Choisir un dossier contenant une sauvegarde l'importe
   **toujours** avant de réécrire le fichier (avant : si l'app suivait déjà une œuvre, la
   sauvegarde existante était écrasée — perte de données) ; réglages repris seulement si l'app
   était vide ; fichier illisible jamais écrasé. `BackupSettings.wifi_only` (optionnel).

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
| NatoManga | recherche (2026-11-21) | `https://www.saiyanmanga.com/home/search/json?searchword={alias}` (JSON : `name`, `slug`, `thumb`, `chapterLatest` « Chapter 285 », pas de date ; 20 max) ; alias = `change_alias` de l'ancien `/js/fsearch.js` |
| NatoManga | recherche (repli) | `/search/story/{alias}` : ancien format `div.story_item` (natomanga/manganami, « Updated : Oct-14-2026 08:11 » UTC) ou nouveau (saiyanmanga, Next.js : `h3 > a[href=/manga/slug]`, image, « Ch 285 ») |
| NatoManga | dernier chapitre | `/api/manga/{slug}/chapters?limit=3` (JSON, `chapter_num`, `chapter_slug`, `updated_at` UTC — « 2026-11-14T11:47:21.000000Z » sur natomanga.com, « 2026-11-14 11:47:21 » sur saiyanmanga.com, même instant ; slug inconnu → 400) |
| NatoManga | URL chapitre | `/manga/{slug}/{chapter_slug}` ; `chapter_slug` = « chapter-285 », décimal « chapter-195-1 » (vérifié) |

- asuracomic.net → 301 vers asurascans.com.
- Demonic agrège ≈ 250 pages × 56 œuvres sur `advanced.php` (page 240 pleine, 260 vide).
- Recherche dans l'app : à partir de 2 caractères, anti-rebond 350 ms, toutes les sources
  activées en parallèle ; résultats en mémoire (seules les œuvres suivies sont en base).
- La recherche Demonic ne fournit pas de dernier chapitre ; ses URL de vignettes
  contiennent des espaces bruts (encodés en `%20` par le parser).
- **2026-11-21 : www.manganami.com → 301 www.natumanga.com → 301 www.saiyanmanga.com**
  (nouvelle interface Next.js, mêmes slugs et API). www.natomanga.com répond de nouveau 200
  avec l'**ancienne** interface (même base de données : chapitre 285, mêmes vues) mais sans
  `/home/search/json` → adresse par défaut = saiyanmanga.com. Test réel : partie de manganami,
  la source a appris saiyanmanga seule (2 redirections), recherche JSON + API OK, pages
  chapitre (dont `chapter-195-1`) et couvertures en 200.
- **2026-11-06 : www.natomanga.com → 301 vers www.manganami.com** (mêmes slugs, mêmes API,
  vérifié en réel : recherche + `/api/manga/{slug}/chapters`). L'app appelle directement
  manganami (sinon `cf_clearance`, déposé sur le domaine final, n'était pas retrouvé :
  l'écran de vérification ne se fermait pas). Migration Room 3→4 et restauration de
  sauvegarde (`LegacyUrls`) réécrivent les URL ; vérifications en attente sur l'ancien hôte purgées.
  `ClearanceWatch` surveille le cookie sur tous les domaines traversés par la WebView.
- natomanga.com → 301 vers www.natomanga.com ; couvertures (`storage.waitst.com`,
  `img-r*.2xstorage.com`) accessibles sans Referer (vérifié le 2026-10-16, et de nouveau le 2026-11-06 depuis le
  serveur). Depuis v0.7.0, Coil utilise le client OkHttp de l'app (cookies WebView + même
  User-Agent) et envoie `Source.imageReferer` (NatoManga : le site).
- Asura : 353 séries chargées par l'app lors du test réel du 2026-10-15 (`meta.total` annonçait 354).
- Chapitres Asura `is_premium` / `early_access_until` : notifiés avec la mention « accès anticipé ».

## Consommation réseau (mesurée le 2026-10-15)

Taille transférée (gzip, corps seuls, hors en-têtes HTTP/TLS) d'une requête de check :
- Asura, page 1 : 18 965 octets (60 058 non compressés)
- Demonic, page 1 : 15 919 octets (114 516 non compressés)

Formule, cas courant (une page par source, les deux sources ont des œuvres suivies) :
`octets/jour = (1440 / intervalle_min) × (18 965 + 15 919)` → à 10 min : 144 × 34 884 = 5 023 296 octets ≈ 5,0 Mo/jour.
Une source sans œuvre suivie ne fait aucune requête. Valeurs variables selon le contenu des pages.

NatoManga (mesuré le 2026-10-16 sur natomanga.com ; 221 à 231 octets le 2026-11-21 sur
saiyanmanga.com, 3 œuvres, sous la borne haute) : 233 à 254 octets compressés par œuvre suivie et par check,
soit `octets/jour ≈ (1440 / intervalle_min) × N_œuvres_NatoManga × 254` (borne haute mesurée)
→ à 10 min avec 10 œuvres : 144 × 10 × 254 = 365 760 octets ≈ 0,37 Mo/jour.

## Build

- `./gradlew test` (tests JVM), `./gradlew :app:assembleDebug|assembleRelease`.
- AGP 8.7.3, Kotlin 2.1.0, Gradle 8.14.3, compileSdk/targetSdk 35, minSdk 26, JDK 17+.
- **Branche** : un conteneur neuf peut s'ouvrir sur une branche `claude/…` au lieu de `main`
  (2026-11-21). Vérifier `git branch` ; pousser avec `git push origin HEAD:main`. Ce jour-là,
  un `git push origin main` a poussé un `main` local périmé et la release v1.0.0 est partie
  sur le code v0.7.1 : run annulé avant publication (aucun tag créé), puis relancé.
- Environnement Claude Code cloud : le conteneur peut être recréé entre deux sessions (SDK et
  script d'init perdus le 2026-11-21) → réinstaller : script ci-dessous, puis
  `commandlinetools-linux-11076708_latest.zip` (dl.google.com) dans
  `/opt/android-sdk/cmdline-tools/latest`, `sdkmanager "platforms;android-35" "build-tools;35.0.0"
  "platform-tools"`, `local.properties`. Premier build en ligne (pas `--offline`).
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
  **2026-10-17 : secrets ajoutés par l'utilisateur, signature vérifiée** (APK de la release
  « 0.5.0 » signé CN=Fresko, SHA-256 f481…733e). Depuis v0.5.1 le workflow **échoue** sans
  clé et normalise la version saisie (`0.5.1` → `v0.5.1`). Une release parasite « 0.5.0 »
  (sans « v », saisie manuelle) existe : sans outil de suppression ici, à supprimer à la main.
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
  rendu des écrans (captures Robolectric), migrations Room 1→2 et 2→3 (rejouées sur SQLite),
  requêtes Room des groupes / restauration / « lu » (`WorkRepositoryTest`, Robolectric, v1.0.0).
- Le scraping dépend de la structure des sites : si un site change, seuls les
  fichiers de `sources/<site>/` sont à adapter (fixtures de test à recapturer).
- NatoManga est derrière Cloudflare. Depuis le serveur de dev, jamais de défi ; sur le
  téléphone de l'utilisateur, défi confirmé (2026-10-23) → mécanisme anti-robot v0.6.0
  (ci-dessous). Non testable ici de bout en bout (pas de WebView réelle sous Robolectric).
  Limite : un **blocage** Cloudflare (page « Sorry, you have been blocked », WAF) n'est pas
  un défi et ne se résout pas ; l'écran de vérification l'affichera tel quel.
- Le SAF ne permet pas de choisir la racine de « Téléchargements » ni du stockage
  (Android 11+) : il faut un sous-dossier.
- Groupes multi-sources : pas de décalage de numérotation par source ; si le check initialise
  une source ajoutée (requête de référence en échec au moment du lien), sa référence est
  marquée lue même si elle est en avance (cas limite non traité).
- Suivi de domaine : seulement NatoManga ; un domaine qui ne redirige pas (simple changement
  annoncé sans 301) reste à corriger à la main (`DEFAULT_BASE_URL`). Non testable ici : les
  actions de notification (activité / récepteur) sur appareil réel.
- KSP : à la création d'un nouveau schéma Room, les variantes debug/release peuvent se
  concurrencer (« Empty schema file ») → lancer d'abord `:core:data:kspDebugKotlin` seul.
- Repli WebView : une requête par œuvre SaiyanManga passe alors par la WebView à chaque check
  tant que le client HTTP est bloqué (plus lent et plus gourmand ; non mesuré).
- Pistes : écran « historique des chapitres », dernier chapitre dans les résultats de
  recherche Demonic, décalage de numérotation par source dans un groupe.

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

### 2026-10-17 — Session 5 : v0.5.0
- Demandes : redonner la procédure et les secrets (fichiers envoyés non trouvés par
  l'utilisateur) ; « la recherche NatoManga ne marche pas » ; sauvegarde qui survit à
  une désinstallation.
- NatoManga : site joignable depuis le serveur (20 résultats pour « solo »). Cause trouvée
  dans le code : sources activées stockées en liste explicite → NatoManga désactivée chez
  les utilisateurs ayant touché un interrupteur avant la v0.3.0. Passage à
  `disabled_sources` + reprise (4 tests). Messages d'erreur de recherche explicites
  (blocage 403/429/503, réseau, délai).
- Sauvegarde : format `BackupFile` (5 tests), `BackupMapper` (aller-retour sans perte,
  2 tests), `BackupController` (dossier SAF, écriture auto, restauration auto au choix du
  dossier si l'app est vide, export / import manuels), carte « Sauvegarde » dans les
  réglages, bouton « Restaurer une sauvegarde » sur l'accueil vide.
- 42 tests unitaires distincts. Non testé sur appareil : le sélecteur de fichiers Android
  (SAF) ne fonctionne pas sous Robolectric.
- Secrets de signature redonnés en clair dans la conversation (non versionnés).

### 2026-10-17 — Session 6 : v0.5.1
- Constat : l'utilisateur a relancé la release avec « 0.5.0 » (sans « v ») → release et tag
  séparés « 0.5.0 », APK **bien signé** avec sa clé ; « v0.5.0 » restée signée « Android Debug ».
  Workflow corrigé : version normalisée, échec si la clé manque.
- Nouvelle icône fournie (style 3D, fond transparent avec traces bleues floues au-dessus) :
  `tools/icon/icon3d.py` (détourage, dégradé de fond mesuré, monochrome « trait »).
  L'illustration de l'accueil vide reste l'ancienne (non demandé).
- Réponse : les secrets GitHub sont permanents, rien à redonner à l'assistant.

### 2026-10-18 — Session 7 : README
- Demande : README esthétique « de la même manière que BLKMLO/Tulipe ». Reprise de sa
  structure : en-tête centré (emoji, accroche en gras, liens), maquette en bloc de code,
  « Pourquoi Fresko » à phrases-clés en gras, tableaux, étapes numérotées, « Contribuer ».
  Maquettes avec titres d'œuvres fictifs, signalés comme tels. Contenu factuel inchangé
  (formules de consommation reprises de ce journal).

### 2026-10-23 — Session 8 : v0.6.0
- Demande : NatoManga inutilisable sur le téléphone à cause du filtre anti-robot ; idée de
  l'utilisateur : afficher le site une fois pour faire le captcha et garder les infos.
- Fait : détection du défi Cloudflare, résolution invisible automatique puis vérification
  manuelle une fois (cookie conservé, même User-Agent partout), bandeau / bouton /
  notification. 52 tests unitaires distincts (dont 6 Robolectric pour le mécanisme).
- Non vérifiable ici : comportement réel de Cloudflare sur le téléphone (le serveur de dev
  n'est jamais challengé) et durée de validité du cookie (fixée par le site).

### 2026-11-06 — Session 9 : erreurs de check, NatoManga (domaine, couvertures)
- Demandes : plusieurs sources pour une même œuvre (propositions demandées, pas
  d'implémentation) ; couvertures NatoManga absentes ; confirmer qu'une erreur s'affiche si la
  requête dédiée n'arrive pas à récupérer l'œuvre.
- Constat : **aucune erreur n'était affichée** (seulement `Log.w`, et notification pour les
  défis Cloudflare). Ajout de l'affichage (voir « Erreurs de check ») ; 4 tests domaine.
- Constat : natomanga.com redirige vers manganami.com → `baseUrl` changée, migration Room v4,
  détection du cookie anti-robot sur le domaine final (3 tests `ClearanceWatch`), purge des
  vérifications en attente de l'ancien hôte (1 test), sauvegardes anciennes normalisées (1 test).
- Couvertures : non reproductible depuis le serveur (images en 200, WebP valides). Correctif
  appliqué sur l'hypothèse la plus probable : Coil utilisait son propre client (UA « okhttp »,
  sans cookie) ; il passe par le client de l'app + Referer du site. **Non vérifié sur appareil.**
- 60 tests unitaires distincts, 0 échec ; `assembleDebug` OK ; migration 3→4 rejouée sur SQLite
  (schéma identique à 4.json) ; test de fumée réel NatoManga (20 résultats, chapitre via l'API).

### 2026-11-06 — Session 10 : même œuvre sur plusieurs sources
- Choix de l'utilisateur : **option A** (regroupement manuel), parmi A (manuel), B (automatique
  par titre), C (source principale + secours), D (statu quo). Question des numérotations
  différentes laissée sans réponse → non traitée, numéros visibles par source dans la fiche.
- Fait : voir « Même œuvre sur plusieurs sources ». Room v5 (migration 4→5 rejouée sur SQLite,
  schéma identique à 5.json). 3 tests de groupe dans `UpdateCheckerTest`, 1 test de sauvegarde
  (numéro lu déduit d'une sauvegarde antérieure). 64 tests unitaires distincts, 0 échec ;
  `assembleDebug` et `assembleRelease` OK ; captures 9 (carte groupée), 10 (fiche), 11 (liaison).
- Release v0.7.0 demandée par l'utilisateur (versionCode 8) ; non testé sur appareil.

### 2026-11-07 — Session 11 : v0.7.1
- Retour utilisateur sur la v0.7.0 : « pas de souci apparent ». Demande : confirmation avant
  « Ne plus suivre », puis release mineure.
- Fait : dialogue de confirmation (`WorkSheet`, `PendingRemoval`) pour « Ne plus suivre » et,
  par cohérence, pour le retrait d'une source (« × »). Capture 12 (le test de capture accepte
  désormais une interaction `interact` avant la prise). 64 tests, 0 échec ; `assembleRelease` OK.
- Release v0.7.1 (versionCode 9).

### 2026-11-21 — Session 12 : v1.0.0 (domaine NatoManga, passe de bugs, améliorations)
- Demandes : NatoManga a encore changé de domaine ; passe de recherche de bugs et de
  problèmes « si l'utilisateur sort des clous » ; idées d'amélioration, à faire aussi ;
  release majeure et mise à jour des .md.
- Domaine : manganami → natumanga → saiyanmanga, **nouvelle interface** (recherche HTML
  illisible par l'ancien parser → recherche vide ; dates API dans un autre format). Fait :
  recherche JSON + repli HTML double format, dates deux formats, suivi automatique du domaine
  (voir point 12), URL enregistrées réécrites à l'ouverture.
- Bugs corrigés : double notification (checks simultanés) ; site en panne → une attente par
  œuvre (limite 10 min) ; **perte de sauvegarde** au choix du dossier après réinstallation ;
  restauration qui écrasait les œuvres suivies ; spinner infini hors connexion ; badge
  « Anticipé » figé ; retour Android qui quittait pendant une recherche ; recherche sans source
  activée = écran vide ; lien sans navigateur = rien ; sous-texte de notification « natomanga ».
- Améliorations : marquer lu depuis la notification (toucher / bouton), bandeau notifications
  coupées, Wi-Fi uniquement, « Rechercher l'œuvre à nouveau », adresse du site dans les
  réglages, état de « Vérifier maintenant », référence au suivi remplacée par la réponse
  dédiée à numéro égal (URL et date exactes).
- Conteneur recréé : SDK Android et script de miroir réinstallés (voir Build).
- Tests : **85 tests unitaires distincts, 0 échec** (64 en v0.7.1) — dont NatoManga 15,
  UpdateChecker 24, `WorkRepositoryTest` 5 (Room réelle sous Robolectric), `OkHttpFetcherTest` 1
  (redirection entre deux serveurs locaux) ; `assembleRelease` (R8) OK. Captures 13 (bandeaux)
  et 14 (sans source) ; test de fumée réel du suivi de domaine.
- README mis à jour. Release v1.0.0 (versionCode 10). Non testé sur appareil.

### 2026-11-28 — Session 13 : SaiyanManga, anti-robot
- Demandes : renommer de façon permanente NatoManga en « saiyenmanga » (nom du site :
  **SaiyanManga**, retenu) en gardant le suivi des changements de domaine ; résoudre l'anti-bot
  « parfois pas détecté » qui bloque recherche et rafraîchissement.
- Domaine inchangé depuis le 2026-11-21 (manganami → natumanga → saiyanmanga, vérifié).
- Fait : nom affiché SaiyanManga (identifiant interne inchangé, voir Objectif) ; détection
  élargie, repli WebView, page bloquée ouverte, bouton « Terminé » (voir point 9). Test réel :
  aucune réponse normale du site prise pour un défi (recherche, API, 404 d'œuvre inconnue).
- Cause exacte du blocage sur le téléphone **non observée** (le serveur de dev n'est jamais
  challengé) : correctifs sur les cas plausibles ci-dessus, à confirmer sur appareil.
- 96 tests unitaires distincts, 0 échec ; `assembleRelease` OK. Pas de release (non demandée).
