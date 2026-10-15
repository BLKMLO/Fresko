package com.fresko.sources.demonic

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import com.fresko.core.network.HttpFetcher
import com.fresko.core.source.RecentUpdates
import com.fresko.core.source.Source
import com.fresko.core.source.UpdateCursor
import java.net.URLEncoder

/**
 * Demonic Scans (demonicscans.org). Pas d'API : parsing HTML via [DemonicParser].
 *
 * Les œuvres se trouvent par la recherche du site (search.php).
 */
class DemonicSource(
    private val http: HttpFetcher,
    private val baseUrl: String = "https://demonicscans.org",
) : Source {

    override val id = ID
    override val displayName = "Demonic Scans"
    private val parser = DemonicParser(baseUrl)

    override suspend fun search(query: String): List<WorkInfo> {
        val q = URLEncoder.encode(query, "UTF-8")
        return parser.parseSearch(http.get("$baseUrl/search.php?manga=$q"))
    }

    override suspend fun fetchLatestChapter(key: WorkKey): ChapterInfo? =
        parser.parseWorkPageLatest(http.get(parser.workUrl(key)))

    override suspend fun fetchRecentUpdates(cursor: UpdateCursor?): RecentUpdates {
        val recent = mutableMapOf<WorkKey, List<ChapterInfo>>()
        val anchors = mutableSetOf<String>()
        var covered = false
        for (page in 1..MAX_UPDATE_PAGES) {
            val items = parser.parseUpdatesList(http.get("$baseUrl/lastupdates.php?list=$page"))
            if (items.isEmpty()) {
                covered = true
                break
            }
            for (w in items) {
                if (w.recentChapters.isEmpty()) continue
                recent.putIfAbsent(w.key, w.recentChapters)
                anchors += w.recentChapters.first().url
            }
            if (cursor == null) break
            covered = anchors.any { it in cursor.anchors } || run {
                val oldestDay = items.mapNotNull { it.recentChapters.firstOrNull()?.publishedAtMillis }.minOrNull()
                oldestDay != null && coverageStart(oldestDay) <= cursor.sinceMillis
            }
            if (covered) break
        }
        return RecentUpdates(recent, covered, anchors)
    }

    companion object {
        val ID = SourceId("demonic")
        private const val MAX_UPDATE_PAGES = 3
        private const val DAY = 24 * 3600 * 1000L
        private const val MAX_TZ_OFFSET = 14 * 3600 * 1000L

        /**
         * Repli si aucune ancre n'est retrouvée. Les dates du site sont au jour près, fuseau inconnu. Si la page la plus
         * ancienne affiche le jour J, les sorties du jour J peuvent être tronquées :
         * on ne garantit la couverture qu'après la fin de J, élargie du décalage
         * horaire maximal possible (UTC+14).
         * Formule : couverture = minuit UTC(J) + 1 jour + 14 h.
         */
        internal fun coverageStart(oldestDayUtcMidnight: Long): Long =
            oldestDayUtcMidnight + DAY + MAX_TZ_OFFSET
    }
}
