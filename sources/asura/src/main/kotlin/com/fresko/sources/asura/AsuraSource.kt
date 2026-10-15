package com.fresko.sources.asura

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import com.fresko.core.network.HttpFetcher
import com.fresko.core.source.RecentUpdates
import com.fresko.core.source.Source
import com.fresko.core.source.UpdateCursor
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Asura Scans (asuracomic.net redirige vers asurascans.com depuis 2026).
 * Le site expose une API JSON publique : on l'utilise plutôt que de parser du HTML.
 *
 * - Catalogue : GET /api/series?sort=latest&order=desc&limit=20&offset=N (20 max par page)
 * - Check : la 1re page triée par dernière sortie contient `latest_chapters`
 *   pour chaque série → une seule requête suffit dans la majorité des cas.
 * - Fallback : GET /api/series/{slug}/chapters
 */
class AsuraSource(
    private val http: HttpFetcher,
    private val apiBase: String = "https://api.asurascans.com",
    private val siteBase: String = "https://asurascans.com",
) : Source {

    override val id = ID
    override val displayName = "Asura Scans"
    // Catalogue complet (≈ 350 séries) : la recherche distante est inutile pour l'UI,
    // mais [search] reste disponible.
    override val supportsRemoteSearch = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetchCatalog(onProgress: (Int) -> Unit): List<WorkInfo> {
        val result = mutableListOf<WorkInfo>()
        var offset = 0
        repeat(MAX_CATALOG_PAGES) {
            val page = fetchPage(offset)
            val items = page.data.orEmpty()
            result += items.map { it.toWorkInfo() }
            onProgress(result.size)
            if (items.isEmpty() || page.meta?.hasMore != true) return result.distinctBy { it.key }
            offset += items.size
        }
        return result.distinctBy { it.key }
    }

    override suspend fun search(query: String): List<WorkInfo> {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val body = http.get("$apiBase/api/series?search=$q&limit=$PAGE_SIZE&offset=0")
        return parsePage(body).data.orEmpty().map { it.toWorkInfo() }
    }

    override suspend fun fetchLatestChapter(key: WorkKey): ChapterInfo? {
        val body = http.get("$apiBase/api/series/${key.key}/chapters")
        val chapters = json.decodeFromString(ChapterListDto.serializer(), body).data.orEmpty()
        val latest = chapters.filter { it.number != null }.maxByOrNull { it.number!! }
            ?: return null
        // L'endpoint chapitres ne renvoie pas public_url ; /comics/{slug} redirige
        // (302) vers l'URL publique, vérifié le 2026-10-15.
        return latest.toChapterInfo(seriesUrl = null, slug = key.key)
    }

    override suspend fun fetchRecentUpdates(cursor: UpdateCursor?): RecentUpdates {
        val latest = mutableMapOf<WorkKey, ChapterInfo>()
        var covered = false
        var offset = 0
        for (pageIndex in 0 until MAX_UPDATE_PAGES) {
            val items = fetchPage(offset).data.orEmpty()
            if (items.isEmpty()) {
                covered = true // fin du catalogue : tout est couvert
                break
            }
            for (s in items) {
                val info = s.toWorkInfo()
                info.latestChapter?.let { latest.putIfAbsent(info.key, it) }
            }
            if (cursor == null) break
            // Liste triée par last_chapter_at décroissant (horodatage précis) :
            // si la dernière entrée est antérieure au curseur, la période est couverte.
            val oldest = items.last().lastChapterAt?.let(::parseInstant) ?: break
            if (oldest <= cursor.sinceMillis) {
                covered = true
                break
            }
            offset += items.size
        }
        return RecentUpdates(latest, covered, latest.values.map { it.url }.toSet())
    }

    private suspend fun fetchPage(offset: Int): SeriesPageDto =
        parsePage(http.get("$apiBase/api/series?sort=latest&order=desc&limit=$PAGE_SIZE&offset=$offset"))

    internal fun parsePage(body: String): SeriesPageDto =
        json.decodeFromString(SeriesPageDto.serializer(), body)

    private fun SeriesDto.seriesUrl(): String =
        publicUrl?.let { siteBase + it } ?: "$siteBase/comics/$slug"

    private fun SeriesDto.toWorkInfo(): WorkInfo {
        val url = seriesUrl()
        return WorkInfo(
            key = WorkKey(ID, slug),
            title = title,
            url = url,
            coverUrl = cover,
            latestChapter = latestChapters.orEmpty()
                .filter { it.number != null }
                .maxByOrNull { it.number!! }
                ?.toChapterInfo(url, slug),
        )
    }

    private fun ChapterDto.toChapterInfo(seriesUrl: String?, slug: String): ChapterInfo {
        val n = number
        val numberText = n?.let(::formatNumber) ?: "?"
        val base = seriesUrl ?: "$siteBase/comics/$slug"
        val now = System.currentTimeMillis()
        val earlyUntil = earlyAccessUntil?.let(::parseInstant)
        return ChapterInfo(
            number = n,
            label = buildString {
                append("Chapitre ").append(numberText)
                if (!title.isNullOrBlank()) append(" – ").append(title)
            },
            url = "$base/chapter/$numberText",
            publishedAtMillis = publishedAt?.let(::parseInstant),
            isEarlyAccess = isPremium || (earlyUntil != null && earlyUntil > now),
        )
    }

    companion object {
        val ID = SourceId("asura")
        private const val PAGE_SIZE = 20
        private const val MAX_CATALOG_PAGES = 200
        private const val MAX_UPDATE_PAGES = 5

        internal fun formatNumber(n: Double): String =
            if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

        internal fun parseInstant(text: String): Long? =
            runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
    }
}
