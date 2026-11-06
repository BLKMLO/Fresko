package com.fresko.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {

    /** Œuvres suivies, les plus récemment mises à jour d'abord. */
    @Query(
        """SELECT * FROM works WHERE tracked = 1
           ORDER BY COALESCE(chapter_published_at, tracked_at) DESC, title COLLATE NOCASE ASC"""
    )
    fun observeTracked(): Flow<List<WorkEntity>>

    @Query("SELECT * FROM works WHERE source_id = :sourceId AND tracked = 1")
    suspend fun tracked(sourceId: String): List<WorkEntity>

    @Query("SELECT * FROM works WHERE source_id = :sourceId AND work_key = :key")
    suspend fun get(sourceId: String, key: String): WorkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WorkEntity)

    @Query("SELECT * FROM works WHERE tracked = 1 ORDER BY title COLLATE NOCASE")
    suspend fun allTracked(): List<WorkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<WorkEntity>)

    @Query("DELETE FROM works WHERE source_id = :sourceId AND work_key = :key")
    suspend fun delete(sourceId: String, key: String)

    /** Le tout premier chapitre connu d'une œuvre (référence) est considéré comme lu. */
    @Query(
        """UPDATE works SET
           read_chapter_url = CASE WHEN chapter_url IS NULL THEN :url ELSE read_chapter_url END,
           read_chapter_number = CASE WHEN chapter_url IS NULL THEN :number ELSE read_chapter_number END,
           chapter_number = :number, chapter_label = :label, chapter_url = :url,
           chapter_published_at = :publishedAt, chapter_early_access = :earlyAccess,
           last_checked_at = :checkedAt
           WHERE source_id = :sourceId AND work_key = :key"""
    )
    suspend fun updateChapter(
        sourceId: String, key: String, number: Double?, label: String?, url: String?,
        publishedAt: Long?, earlyAccess: Boolean, checkedAt: Long,
    )

    @Query("UPDATE works SET notify_early_access = :notify, early_pending = 0 WHERE source_id = :sourceId AND work_key = :key")
    suspend fun setNotifyEarlyAccess(sourceId: String, key: String, notify: Boolean)

    @Query(
        """UPDATE works SET read_chapter_url = chapter_url, read_chapter_number = chapter_number
           WHERE source_id = :sourceId AND work_key = :key"""
    )
    suspend fun markRead(sourceId: String, key: String)

    @Query(
        """UPDATE works SET read_chapter_url = NULL, read_chapter_number = NULL
           WHERE source_id = :sourceId AND work_key = :key"""
    )
    suspend fun markUnread(sourceId: String, key: String)

    // --- Groupes : même œuvre suivie sur plusieurs sources ---

    @Query("SELECT * FROM works WHERE tracked = 1 AND group_id = :groupId")
    suspend fun groupMembers(groupId: String): List<WorkEntity>

    @Query(
        """SELECT MAX(chapter_number) FROM works WHERE tracked = 1 AND group_id = :groupId
           AND NOT (source_id = :sourceId AND work_key = :key)"""
    )
    suspend fun groupBestNumber(groupId: String, sourceId: String, key: String): Double?

    @Query(
        """SELECT MAX(read_chapter_number) FROM works WHERE tracked = 1 AND group_id = :groupId
           AND NOT (source_id = :sourceId AND work_key = :key)"""
    )
    suspend fun groupReadNumber(groupId: String, sourceId: String, key: String): Double?

    @Query("UPDATE works SET group_id = :groupId WHERE source_id = :sourceId AND work_key = :key")
    suspend fun setGroup(sourceId: String, key: String, groupId: String?)

    @Query("UPDATE works SET group_id = :to WHERE group_id = :from")
    suspend fun moveGroup(from: String, to: String)

    @Query("UPDATE works SET read_chapter_url = chapter_url, read_chapter_number = chapter_number WHERE group_id = :groupId")
    suspend fun markGroupRead(groupId: String)

    @Query("UPDATE works SET read_chapter_url = NULL, read_chapter_number = NULL WHERE group_id = :groupId")
    suspend fun markGroupUnread(groupId: String)

    @Query("DELETE FROM works WHERE group_id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("UPDATE works SET early_pending = :pending WHERE source_id = :sourceId AND work_key = :key")
    suspend fun setEarlyPending(sourceId: String, key: String, pending: Boolean)

    @Query("UPDATE works SET check_error = :message, check_error_at = :at WHERE source_id = :sourceId AND work_key = :key")
    suspend fun setCheckError(sourceId: String, key: String, message: String?, at: Long?)
}
