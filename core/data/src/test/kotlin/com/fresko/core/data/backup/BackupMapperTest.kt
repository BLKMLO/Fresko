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
        groupId = "g-1", readChapterNumber = 280.0,
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
            chapterEarlyAccess = false, readChapterUrl = null, notifyEarlyAccess = true,
            groupId = null, readChapterNumber = null)
        assertEquals(bare, BackupMapper.toEntity(BackupMapper.toBackup(bare)))
    }

    @Test
    fun `sauvegarde v0_6 sans numero lu - deduit si le chapitre connu est lu`() {
        val json = """{"format":"fresko-backup","version":1,"exported_at":1,"settings":{"interval_minutes":10},
            "works":[{"source":"asura","key":"a","title":"A","url":"u",
            "chapter":{"number":12.0,"label":"Chapitre 12","url":"c12"},"read_chapter_url":"c12"}]}"""
        val e = BackupMapper.toEntity(BackupCodec.decode(json).works.single())
        assertEquals(12.0, e.readChapterNumber!!, 0.0)
        assertEquals(null, e.groupId)
    }
}
