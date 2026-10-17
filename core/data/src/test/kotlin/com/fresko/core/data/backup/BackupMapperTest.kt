package com.fresko.core.data.backup

import com.fresko.core.data.db.WorkEntity
import com.fresko.core.domain.backup.BackupCodec
import com.fresko.core.domain.backup.BackupFile
import com.fresko.core.domain.backup.BackupSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMapperTest {

    private val full = WorkEntity(
        sourceId = "asura", workKey = "solo", title = "Solo Max-Level Newbie", url = "https://a/solo",
        coverUrl = "https://a/c.webp", tracked = true, trackedAt = 10,
        chapterNumber = 281.0, chapterLabel = "Chapitre 281", chapterUrl = "https://a/281",
        chapterPublishedAt = 20, chapterEarlyAccess = true, lastCheckedAt = null,
        notifyEarlyAccess = false, earlyPending = false, readChapterUrl = "https://a/280",
    )

    @Test
    fun `aller-retour complet par le fichier JSON`() {
        val file = BackupFile(exportedAt = 1, settings = BackupSettings(10), works = listOf(BackupMapper.toBackup(full)))
        val back = BackupMapper.toEntity(BackupCodec.decode(BackupCodec.encode(file)).works.single())
        assertEquals(full, back)
    }

    @Test
    fun `oeuvre sans chapitre`() {
        val bare = full.copy(chapterNumber = null, chapterLabel = null, chapterUrl = null, chapterPublishedAt = null,
            chapterEarlyAccess = false, readChapterUrl = null, notifyEarlyAccess = true)
        assertEquals(bare, BackupMapper.toEntity(BackupMapper.toBackup(bare)))
    }
}
