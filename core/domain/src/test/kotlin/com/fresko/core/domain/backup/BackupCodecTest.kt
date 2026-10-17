package com.fresko.core.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val sample = BackupFile(
        exportedAt = 1_792_100_000_000,
        settings = BackupSettings(intervalMinutes = 10, disabledSources = setOf("demonic")),
        works = listOf(
            BackupWork(
                source = "asura", key = "solo-max-level-newbie", title = "Solo Max-Level Newbie",
                url = "https://asurascans.com/comics/solo-max-level-newbie-1bb35e56",
                cover = "https://cdn.asurascans.com/x.webp", trackedAt = 1_792_000_000_000,
                chapter = BackupChapter(281.0, "Chapitre 281", "https://asurascans.com/c/281", 1_792_092_727_887, true),
                readChapterUrl = "https://asurascans.com/c/280", notifyEarlyAccess = false,
            ),
            BackupWork(source = "natomanga", key = "the-taming", title = "The Taming", url = "https://www.natomanga.com/manga/the-taming"),
        ),
    )

    @Test
    fun `aller-retour sans perte`() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample)))
    }

    @Test
    fun `json lisible avec format et version`() {
        val text = BackupCodec.encode(sample)
        assertTrue(text.contains("\"format\": \"fresko-backup\""))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test
    fun `champs inconnus ignores, champs absents par defaut`() {
        val text = """{"format":"fresko-backup","version":1,"exported_at":1,"futur":true,
            "settings":{"interval_minutes":30},
            "works":[{"source":"demonic","key":"k","title":"T","url":"u","autre":1}]}"""
        val b = BackupCodec.decode(text)
        assertEquals(30, b.settings.intervalMinutes)
        assertTrue(b.settings.disabledSources.isEmpty())
        assertTrue(b.works.single().notifyEarlyAccess)
    }

    @Test(expected = InvalidBackupException::class)
    fun `fichier quelconque refuse`() {
        BackupCodec.decode("""{"hello":"world"}""")
    }

    @Test(expected = InvalidBackupException::class)
    fun `version future refusee`() {
        BackupCodec.decode("""{"format":"fresko-backup","version":99,"exported_at":1,"settings":{"interval_minutes":10},"works":[]}""")
    }
}
