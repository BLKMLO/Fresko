package com.fresko.sources.demonic

import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Parsing HTML de demonicscans.org. Structure observée le 2026-10-15 :
 * - listes (lastupdates.php, translationlist.php) : `div.updates-element`
 *   contenant un lien `/manga/<slug>` et des lignes `.chap-date`
 *   ("Chapter N" + date AAAA-MM-JJ) ;
 * - recherche (search.php?manga=…) : liens `a[href^=/manga/]` avec titre dans un `div` ;
 * - page œuvre (/manga/<slug>) : `#chapters-list li a` ("Chapter N" + `span` date).
 */
internal class DemonicParser(private val baseUrl: String) {

    fun parseUpdatesList(html: String): List<WorkInfo> {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select("div.updates-element").mapNotNull { el ->
            val link = el.selectFirst("a[href^=/manga/]") ?: return@mapNotNull null
            val href = link.attr("href")
            val title = el.selectFirst("h2 a[title]")?.attr("title")?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: link.attr("title").trim().ifEmpty { el.selectFirst("h2")?.text().orEmpty() }
            val cover = el.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotEmpty() }
            val chapters = el.select(".chap-date").mapNotNull { row ->
                val links = row.select("a")
                val first = links.firstOrNull() ?: return@mapNotNull null
                chapterOf(
                    label = first.text(),
                    href = first.attr("href"),
                    date = links.getOrNull(1)?.text(),
                )
            }
            WorkInfo(
                key = keyOf(href),
                title = title,
                url = absolute(href),
                coverUrl = cover,
                latestChapter = chapters.latest(),
            )
        }.distinctBy { it.key }
    }

    fun parseSearch(html: String): List<WorkInfo> {
        val doc = Jsoup.parseBodyFragment(html, baseUrl)
        return doc.select("a[href^=/manga/]").map { a ->
            val href = a.attr("href")
            val title = a.selectFirst("div > div")?.ownText()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: a.text().trim()
            WorkInfo(
                key = keyOf(href),
                title = title,
                url = absolute(href),
                coverUrl = a.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotEmpty() },
            )
        }.distinctBy { it.key }
    }

    fun parseWorkPageLatest(html: String): ChapterInfo? {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select("#chapters-list li a").mapNotNull { a ->
            chapterOf(
                label = a.ownText(),
                href = a.attr("href"),
                date = a.selectFirst("span")?.text(),
            )
        }.latest()
    }

    fun keyOf(href: String): WorkKey = WorkKey(DemonicSource.ID, href.removePrefix("/manga/"))

    fun workUrl(key: WorkKey): String = "$baseUrl/manga/${key.key}"

    private fun absolute(href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("/") -> baseUrl + href
        else -> "$baseUrl/$href"
    }

    private fun chapterOf(label: String, href: String, date: String?): ChapterInfo? {
        if (href.isBlank()) return null
        val clean = label.trim()
        val number = NUMBER.find(clean)?.value?.toDoubleOrNull()
        return ChapterInfo(
            number = number,
            label = clean.replaceFirst("Chapter", "Chapitre"),
            url = absolute(href),
            publishedAtMillis = date?.let(::parseDate),
        )
    }

    private fun List<ChapterInfo>.latest(): ChapterInfo? =
        filter { it.number != null }.maxByOrNull { it.number!! } ?: firstOrNull()

    companion object {
        private val NUMBER = Regex("""\d+(\.\d+)?""")

        /** Date "AAAA-MM-JJ" → epoch millis à minuit UTC. */
        fun parseDate(text: String): Long? = runCatching {
            LocalDate.parse(text.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
