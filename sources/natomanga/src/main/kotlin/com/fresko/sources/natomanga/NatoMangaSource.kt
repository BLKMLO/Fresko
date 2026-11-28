package com.fresko.sources.natomanga

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import com.fresko.core.network.HttpException
import com.fresko.core.network.HttpFetcher
import com.fresko.core.network.HttpResult
import com.fresko.core.network.OkHttpFetcher
import com.fresko.core.source.NoAddressStore
import com.fresko.core.source.RecentUpdates
import com.fresko.core.source.SiteAddressStore
import com.fresko.core.source.Source
import com.fresko.core.source.UpdateCursor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * SaiyanManga (ex-NatoManga / MangaNato) : le site change régulièrement de domaine.
 * - jusqu'au 2026-11-06 : www.natomanga.com ;
 * - 2026-11-06 : www.manganami.com (natomanga.com y redirigeait) ;
 * - 2026-11-21 : manganami → www.natumanga.com → **www.saiyanmanga.com**, nouvelle
 *   interface (Next.js) ; mêmes slugs, même API chapitres.
 *
 * Changement de domaine **suivi automatiquement** : quand une requête est redirigée vers un
 * autre domaine avec le même chemin, ce domaine devient l'adresse utilisée (mémorisée dans
 * [addresses]). Les URL déjà enregistrées sont réécrites à l'ouverture ([openableUrl]).
 *
 * Requêtes (observées le 2026-11-21 sur www.saiyanmanga.com) :
 * - recherche : GET /home/search/json?searchword={alias} (JSON : name, slug, thumb,
 *   chapterLatest) ; repli HTML /search/story/{alias} (ancien format `div.story_item` ou
 *   nouveau format `h3 > a[href^=/manga/]`) ;
 * - chapitres : GET /api/manga/{slug}/chapters?limit=N (JSON, `updated_at` en UTC, au format
 *   ISO « 2026-11-14T11:47:21.000000Z » ou « 2026-11-14 11:47:21 » selon le domaine).
 *
 * Stratégie de check : **une requête par œuvre suivie** (la liste « latest » du site bouge
 * trop vite pour couvrir 10 minutes de façon sûre ; ≈ 250 octets par œuvre via l'API).
 */
class NatoMangaSource(
    private val http: HttpFetcher,
    private val defaultBaseUrl: String = DEFAULT_BASE_URL,
    private val addresses: SiteAddressStore = NoAddressStore,
) : Source {

    override val id = ID
    /**
     * Nom affiché : **SaiyanManga** depuis la v1.1.0 (le site s'appelle ainsi sur son nouveau
     * domaine). L'identifiant interne reste « natomanga » : il est enregistré dans la base, les
     * sauvegardes et les réglages ; le changer les rendrait illisibles.
     */
    override val displayName = "SaiyanManga"

    private val addressKey = "${ID.value}|$defaultBaseUrl"

    /** Adresse actuelle (apprise par redirection, sinon celle par défaut). */
    val baseUrl: String get() = addresses.get(addressKey) ?: defaultBaseUrl

    override val imageReferer: String get() = "$baseUrl/"
    override val currentHost: String? get() = hostOf(baseUrl)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun search(query: String): List<WorkInfo> {
        val alias = searchAlias(query)
        if (alias.isEmpty()) return emptyList()
        // 1) Recherche JSON du nouveau site ; 2) repli HTML (ancien ou nouveau format).
        val fromJson = try {
            parseSearchJson(get("/home/search/json?searchword=$alias", json = true).body)
        } catch (e: HttpException) {
            if (e.code in 500..599 || e.code == 403 || e.code == 429) throw e // site en panne / bloqué
            null // point d'accès absent (ancien site) : repli HTML
        } catch (_: SerializationException) {
            null // page HTML au lieu de JSON : repli HTML
        } catch (_: IllegalArgumentException) {
            null
        }
        return fromJson ?: parseSearch(get("/search/story/$alias").body)
    }

    override suspend fun fetchLatestChapter(key: WorkKey): ChapterInfo? {
        val body = get("/api/manga/${key.key}/chapters?limit=$CHAPTERS_LIMIT", json = true).body
        val chapters = json.decodeFromString(ChaptersResponse.serializer(), body).data?.chapters.orEmpty()
        val latest = chapters.filter { it.chapterNum != null }.maxByOrNull { it.chapterNum!! }
            ?: chapters.firstOrNull()
            ?: return null
        val slug = latest.chapterSlug ?: latest.chapterNum?.let(::chapterSlugOf) ?: return null
        return ChapterInfo(
            number = latest.chapterNum,
            label = latest.chapterName?.replaceFirst("Chapter", "Chapitre") ?: "Chapitre ${latest.chapterNum}",
            url = "$baseUrl/manga/${key.key}/$slug",
            publishedAtMillis = latest.updatedAt?.let(::parseApiDate),
        )
    }

    /** Aucune liste exploitable : le vérificateur interroge chaque œuvre (voir en-tête). */
    override suspend fun fetchRecentUpdates(cursor: UpdateCursor?): RecentUpdates =
        RecentUpdates(emptyMap(), coversCursor = false)

    /** Ancien domaine d'une URL enregistrée → domaine actuel (chemins identiques d'un domaine à l'autre). */
    override fun openableUrl(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        if (uri.host == null || uri.rawPath?.startsWith("/manga/") != true) return url
        val base = URI(baseUrl)
        if (uri.host == base.host) return url
        return baseUrl + uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
    }

    /** GET sur l'adresse actuelle ; une redirection vers un autre domaine (même chemin) est mémorisée. */
    /**
     * @param json réponse JSON attendue : l'en-tête `Accept` permet de reconnaître une page
     *   anti-robot servie à la place des données (voir `CloudflareDetector`).
     */
    private suspend fun get(pathAndQuery: String, json: Boolean = false): HttpResult {
        val base = baseUrl
        val result = http.fetch(base + pathAndQuery, if (json) OkHttpFetcher.JSON_HEADERS else emptyMap())
        movedTo(base + pathAndQuery, result.finalUrl)?.let { addresses.set(addressKey, it) }
        return result
    }

    internal fun parseSearchJson(body: String): List<WorkInfo> {
        val base = baseUrl
        return json.decodeFromString(ListSerializer(SearchItemDto.serializer()), body).mapNotNull { item ->
            val slug = item.slug?.takeIf { it.isNotBlank() }
                ?: item.url?.substringAfter("/manga/", "")?.substringBefore('/')?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val title = item.name?.let { Parser.unescapeEntities(it, false).trim() }?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val number = item.chapterLatest?.let { NUMBER.find(it)?.value?.toDoubleOrNull() }
            WorkInfo(
                key = WorkKey(ID, slug),
                title = title,
                url = "$base/manga/$slug",
                coverUrl = item.thumb?.takeIf { it.startsWith("http") },
                latestChapter = number?.let {
                    ChapterInfo(
                        number = it,
                        label = "Chapitre ${formatNumber(it)}",
                        url = "$base/manga/$slug/${chapterSlugOf(it)}",
                    )
                },
            )
        }.distinctBy { it.key }
    }

    internal fun parseSearch(html: String): List<WorkInfo> {
        val doc = Jsoup.parse(html, baseUrl)
        val oldItems = doc.select("div.story_item")
        return if (oldItems.isNotEmpty()) parseOldSearch(oldItems) else parseNewSearch(doc)
    }

    /** Ancien format (natomanga.com, manganami.com) : `div.story_item`, date « Updated : … ». */
    private fun parseOldSearch(items: List<Element>): List<WorkInfo> = items.mapNotNull { item ->
        val link = item.selectFirst("h3.story_name a[href]") ?: item.selectFirst("a[href*=/manga/]")
            ?: return@mapNotNull null
        val url = link.absUrl("href")
        val slug = url.substringAfter("/manga/", "").substringBefore('/').ifEmpty { return@mapNotNull null }
        val updated = item.select("span").firstOrNull { it.text().startsWith("Updated") }
            ?.text()?.substringAfter(":")?.trim()
        val chapter = item.selectFirst("em.story_chapter a[href]")?.let { a ->
            val label = a.text().trim()
            ChapterInfo(
                number = NUMBER.find(label)?.value?.toDoubleOrNull(),
                label = label.replaceFirst("Chapter", "Chapitre"),
                url = a.absUrl("href"),
                publishedAtMillis = updated?.let(::parseUpdated),
            )
        }
        WorkInfo(
            key = WorkKey(ID, slug),
            title = link.text().trim(),
            url = url,
            coverUrl = item.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotEmpty() },
            latestChapter = chapter,
        )
    }.distinctBy { it.key }

    /** Nouveau format (saiyanmanga.com) : carte `div.group` avec `h3 > a`, image et « Ch N ». */
    private fun parseNewSearch(doc: org.jsoup.nodes.Document): List<WorkInfo> =
        doc.select("h3 a[href*=/manga/]").mapNotNull { link ->
            val url = link.absUrl("href")
            val slug = url.substringAfter("/manga/", "").substringBefore('/').ifEmpty { return@mapNotNull null }
            val card = link.parents().firstOrNull { it.hasClass("group") } ?: link.parent()
            val number = card?.select("span")?.map { it.text().trim() }
                ?.firstOrNull { CHAPTER_BADGE.matches(it) }
                ?.let { NUMBER.find(it)?.value?.toDoubleOrNull() }
            WorkInfo(
                key = WorkKey(ID, slug),
                title = link.attr("title").ifBlank { link.text() }.trim(),
                url = "$baseUrl/manga/$slug",
                coverUrl = card?.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotEmpty() },
                latestChapter = number?.let {
                    ChapterInfo(it, "Chapitre ${formatNumber(it)}", "$baseUrl/manga/$slug/${chapterSlugOf(it)}")
                },
            )
        }.distinctBy { it.key }

    @Serializable
    private data class SearchItemDto(
        val name: String? = null,
        val slug: String? = null,
        val url: String? = null,
        val thumb: String? = null,
        val chapterLatest: String? = null,
    )

    @Serializable
    private data class ChaptersResponse(val data: ChaptersData? = null)

    @Serializable
    private data class ChaptersData(val chapters: List<ChapterDto>? = null)

    @Serializable
    private data class ChapterDto(
        @SerialName("chapter_name") val chapterName: String? = null,
        @SerialName("chapter_slug") val chapterSlug: String? = null,
        @SerialName("chapter_num") val chapterNum: Double? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
    )

    companion object {
        val ID = SourceId("natomanga")
        const val DEFAULT_BASE_URL = "https://www.saiyanmanga.com"
        private const val CHAPTERS_LIMIT = 3
        private val NUMBER = Regex("""\d+(\.\d+)?""")
        private val CHAPTER_BADGE = Regex("""(?i)ch\.?\s*\d+(\.\d+)?""")
        private val SEPARATORS = Regex("[!@%^*()+=<>?/,.:;'\" &#\\[\\]~\\-${'$'}_]")
        private val UPDATED_FORMAT = DateTimeFormatter.ofPattern("MMM-dd-yyyy HH:mm", Locale.ENGLISH)
        private val API_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /**
         * Reproduit `change_alias` du script de recherche du site (fsearch.js) :
         * minuscules, accents retirés, ponctuation et espaces → « _ », « _ » multiples fusionnés.
         */
        fun searchAlias(query: String): String {
            val noAccents = Normalizer.normalize(query.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace('đ', 'd')
            return noAccents
                .replace(SEPARATORS, "_")
                .replace(Regex("_+"), "_")
                .trim('_')
        }

        /** « Oct-14-2026 08:11 » (heure du site = UTC, vérifié contre l'API). */
        internal fun parseUpdated(text: String): Long? = runCatching {
            LocalDateTime.parse(text, UPDATED_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()

        /**
         * `updated_at` de l'API, en UTC : ISO (« 2026-11-14T11:47:21.000000Z », natomanga.com)
         * ou « 2026-11-14 11:47:21 » (saiyanmanga.com) — même chapitre, même instant (vérifié le 2026-11-21).
         */
        internal fun parseApiDate(text: String): Long? =
            runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(text, API_DATE_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()

        /** 285 → « chapter-285 » ; 195.1 → « chapter-195-1 » (convention de l'API, vérifiée). */
        internal fun chapterSlugOf(number: Double): String = "chapter-" + formatNumber(number).replace('.', '-')

        private fun formatNumber(n: Double): String =
            if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

        private fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()

        /**
         * Nouvelle adresse si [requested] a été redirigée vers un **autre domaine** avec le même
         * chemin (déménagement du site) ; null sinon (pas de redirection, ou redirection vers
         * une autre page : parking de domaine, accueil…).
         */
        internal fun movedTo(requested: String, final: String): String? {
            val a = runCatching { URI(requested) }.getOrNull() ?: return null
            val b = runCatching { URI(final) }.getOrNull() ?: return null
            if (b.host == null || a.host == b.host) return null
            if (a.rawPath != b.rawPath) return null
            if (b.scheme != "https") return null
            return "${b.scheme}://${b.host}"
        }
    }
}
