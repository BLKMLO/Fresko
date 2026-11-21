package com.fresko.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fresko.core.data.db.FreskoDatabase
import com.fresko.core.data.db.WorkEntity
import com.fresko.core.model.ChapterInfo
import com.fresko.core.model.SourceId
import com.fresko.core.model.WorkInfo
import com.fresko.core.model.WorkKey
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Requêtes Room réelles (base SQLite en mémoire) : groupes, restauration, « lu ». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkRepositoryTest {

    private lateinit var db: FreskoDatabase
    private lateinit var repo: WorkRepository
    private val asura = SourceId("asura")
    private val nato = SourceId("natomanga")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FreskoDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = WorkRepository(db.workDao())
    }

    @After
    fun tearDown() = db.close()

    private fun ch(n: Double, src: SourceId) = ChapterInfo(n, "Chapitre $n", "https://${src.value}.example/manga/x/$n")
    private fun info(src: SourceId, key: String, n: Double?) =
        WorkInfo(WorkKey(src, key), "Solo", "https://${src.value}.example/manga/$key", null, n?.let { ch(it, src) })

    @Test
    fun `groupe - lier, marquer lu pour tout le groupe, retirer une source dissout le groupe`() = runTest {
        repo.track(info(asura, "a", 10.0))
        repo.track(info(nato, "b", 10.0), groupWith = WorkKey(asura, "a"))
        val a = repo.get(WorkKey(asura, "a"))!!
        val b = repo.get(WorkKey(nato, "b"))!!
        assertTrue(a.groupId != null && a.groupId == b.groupId)
        assertEquals(10.0, repo.groupBestNumber(a.groupId!!, a.key)!!, 0.0)

        repo.saveLatestChapter(a.key, ch(11.0, asura), 0)
        repo.markRead(b.key) // marque tout le groupe
        assertEquals(11.0, repo.get(a.key)!!.readChapterNumber!!, 0.0)

        repo.untrack(a.key)
        assertNull(repo.get(b.key)!!.groupId) // groupe d'une seule source dissous
    }

    @Test
    fun `source ajoutee en avance sur la lecture - reste a lire`() = runTest {
        repo.track(info(asura, "a", 10.0))
        repo.track(info(nato, "b", 12.0), groupWith = WorkKey(asura, "a"))
        val b = repo.get(WorkKey(nato, "b"))!!
        assertNull(b.readChapterNumber) // 12 > 10 lu ailleurs : pas marqué lu
        repo.track(info(SourceId("demonic"), "c", 9.0), groupWith = WorkKey(asura, "a"))
        assertEquals(9.0, repo.get(WorkKey(SourceId("demonic"), "c"))!!.readChapterNumber!!, 0.0)
    }

    @Test
    fun `restauration - ajoute les oeuvres absentes, conserve celles deja suivies`() = runTest {
        repo.track(info(asura, "a", 20.0))
        val old = WorkEntity(
            sourceId = "asura", workKey = "a", title = "Ancien", url = "u", coverUrl = null, tracked = true,
            chapterNumber = 5.0, chapterLabel = "Chapitre 5", chapterUrl = "c5",
        )
        val fresh = old.copy(workKey = "z", title = "Nouvelle")
        val added = repo.restore(listOf(old, fresh))
        assertEquals(1, added)
        assertEquals(20.0, repo.get(WorkKey(asura, "a"))!!.chapterNumber!!, 0.0) // pas de recul
        assertEquals("Nouvelle", repo.get(WorkKey(asura, "z"))!!.title)
    }

    @Test
    fun `restauration - groupe d'une seule oeuvre dissous`() = runTest {
        val lone = WorkEntity(
            sourceId = "asura", workKey = "a", title = "A", url = "u", coverUrl = null, tracked = true, groupId = "g-perdu",
        )
        repo.restore(listOf(lone))
        assertNull(repo.get(WorkKey(asura, "a"))!!.groupId)
    }

    @Test
    fun `marquer lu depuis une notification - seulement si le chapitre est toujours le dernier`() = runTest {
        repo.track(info(nato, "b", 10.0))
        repo.saveLatestChapter(WorkKey(nato, "b"), ch(11.0, nato), 0)
        // Notification du 11 touchée, mais le 12 est sorti entre-temps : pas marqué lu.
        repo.saveLatestChapter(WorkKey(nato, "b"), ch(12.0, nato), 0)
        assertFalse(repo.markReadIfCurrent(WorkKey(nato, "b"), ch(11.0, nato).url, 11.0))
        assertTrue(repo.get(WorkKey(nato, "b"))!!.isUnread)
        // Notification du 12, URL sur un ancien domaine du site : reconnue.
        assertTrue(repo.markReadIfCurrent(WorkKey(nato, "b"), "https://ancien.example/manga/x/12.0", null))
        assertFalse(repo.get(WorkKey(nato, "b"))!!.isUnread)
    }
}
