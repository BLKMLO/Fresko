package com.fresko.sources.natomanga

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import com.fresko.core.network.HttpFetcher
import com.fresko.core.source.RecentUpdates
import com.fresko.core.source.Source
import com.fresko.core.source.UpdateCursor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * NatoManga / MangaNato (www.natomanga.com). Observé le 2026-10-16 :
 * - recherche : GET /search/story/{requête_en_snake_case} (HTML, `div.story_item`) ;
 * - chapitres : GET /api/manga/{slug}/chapters?limit=N (JSON, dates UTC précises).
 *
 * Stratégie de check : **une requête par œuvre suivie**. La liste « latest » du site
 * (24 œuvres par page, sans dates, ≈ 94 000 œuvres au catalogue) bouge trop vite pour
 * couvrir 10 minutes de façon sûre, et une page pèse ≈ 22 Ko compressés contre
 * quelques centaines d'octets pour l'API chapitres d'une œuvre.
 */
class NatoMangaSource(
    private val http: HttpFetcher,
    private val baseUrl: String = "https://www.natomanga.com",
) : Source {

    override val id = ID
    override val displayName = "NatoManga"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun search(query: String): List<WorkInfo> {
        val alias = searchAlias(query)
        if (alias.isEmpty()) return emptyList()
        return parseSearch(http.get("$baseUrl/search/story/$alias"))
    }

    override suspend fun fetchLatestChapter(key: WorkKey): ChapterInfo? {
        val body = http.get("$baseUrl/api/manga/${key.key}/chapters?limit=$CHAPTERS_LIMIT")
        val chapters = json.decodeFromString(ChaptersResponse.serializer(), body).data?.chapters.orEmpty()
        val latest = chapters.filter { it.chapterNum != null }.maxByOrNull { it.chapterNum!! }
            ?: chapters.firstOrNull()
            ?: return null
        return ChapterInfo(
            number = latest.chapterNum,
            label = latest.chapterName?.replaceFirst("Chapter", "Chapitre") ?: "Chapitre ${latest.chapterNum}",
            url = "$baseUrl/manga/${key.key}/${latest.chapterSlug}",
            publishedAtMillis = latest.updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        )
    }

    /** Aucune liste exploitable : le vérificateur interroge chaque œuvre (voir en-tête). */
    override suspend fun fetchRecentUpdates(cursor: UpdateCursor?): RecentUpdates =
        RecentUpdates(emptyMap(), coversCursor = false)

    internal fun parseSearch(html: String): List<WorkInfo> {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select("div.story_item").mapNotNull { item ->
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
    }

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
        private const val CHAPTERS_LIMIT = 3
        private val NUMBER = Regex("""\d+(\.\d+)?""")
        private val SEPARATORS = Regex("[!@%^*()+=<>?/,.:;'\" &#\\[\\]~\\-${'$'}_]")
        private val UPDATED_FORMAT = DateTimeFormatter.ofPattern("MMM-dd-yyyy HH:mm", Locale.ENGLISH)

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
    }
}
