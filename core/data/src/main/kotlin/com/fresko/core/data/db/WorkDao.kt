package com.fresko.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {

    /** Œuvres suivies en premier (les plus récemment ajoutées d'abord), puis le reste par titre. */
    @Query(
        """SELECT * FROM works
           ORDER BY tracked DESC,
                    CASE WHEN tracked THEN tracked_at END DESC,
                    title COLLATE NOCASE ASC"""
    )
    fun observeAll(): Flow<List<WorkEntity>>

    @Query("SELECT * FROM works WHERE source_id = :sourceId AND tracked = 1")
    suspend fun tracked(sourceId: String): List<WorkEntity>

    @Query("SELECT * FROM works WHERE source_id = :sourceId AND work_key = :key")
    suspend fun get(sourceId: String, key: String): WorkEntity?

    @Query("SELECT COUNT(*) FROM works WHERE source_id = :sourceId")
    suspend fun count(sourceId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: WorkEntity): Long

    @Query("UPDATE works SET title = :title, url = :url, cover_url = :coverUrl WHERE source_id = :sourceId AND work_key = :key")
    suspend fun updateMeta(sourceId: String, key: String, title: String, url: String, coverUrl: String?)

    @Query(
        """UPDATE works SET chapter_number = :number, chapter_label = :label, chapter_url = :url,
           chapter_published_at = :publishedAt, chapter_early_access = :earlyAccess
           WHERE source_id = :sourceId AND work_key = :key AND tracked = 0"""
    )
    suspend fun updateCatalogChapter(
        sourceId: String, key: String, number: Double?, label: String?, url: String?,
        publishedAt: Long?, earlyAccess: Boolean,
    )

    @Query(
        """UPDATE works SET chapter_number = :number, chapter_label = :label, chapter_url = :url,
           chapter_published_at = :publishedAt, chapter_early_access = :earlyAccess,
           last_checked_at = :checkedAt
           WHERE source_id = :sourceId AND work_key = :key"""
    )
    suspend fun updateChapter(
        sourceId: String, key: String, number: Double?, label: String?, url: String?,
        publishedAt: Long?, earlyAccess: Boolean, checkedAt: Long,
    )

    @Query("UPDATE works SET tracked = :tracked, tracked_at = :at WHERE source_id = :sourceId AND work_key = :key")
    suspend fun setTracked(sourceId: String, key: String, tracked: Boolean, at: Long?)

    /** Insère les nouvelles œuvres et met à jour les existantes sans toucher au suivi. */
    @Transaction
    suspend fun mergeCatalog(items: List<WorkEntity>) {
        for (item in items) {
            if (insertIgnore(item) == -1L) {
                updateMeta(item.sourceId, item.workKey, item.title, item.url, item.coverUrl)
                // Une recherche ne fournit pas toujours de chapitre : on garde l'existant.
                if (item.chapterUrl != null) updateCatalogChapter(
                    item.sourceId, item.workKey, item.chapterNumber, item.chapterLabel,
                    item.chapterUrl, item.chapterPublishedAt, item.chapterEarlyAccess,
                )
            }
        }
    }
}
