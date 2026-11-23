package bd.nib.songbadpatro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bd.nib.songbadpatro.data.local.entity.FeedSourceEntity
import bd.nib.songbadpatro.data.local.entity.KeywordAlertEntity
import bd.nib.songbadpatro.data.local.entity.NotifiedArticleEntity
import bd.nib.songbadpatro.data.local.entity.PublisherEntity
import bd.nib.songbadpatro.data.local.entity.SyncLogEntity
import bd.nib.songbadpatro.domain.model.FeedHealthStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PublisherDao {
    @Query("SELECT * FROM publishers ORDER BY enabled DESC, verificationStatus ASC, nameBn ASC")
    fun observeAll(): Flow<List<PublisherEntity>>

    @Query("SELECT * FROM publishers WHERE enabled = 1 ORDER BY addedAt DESC, nameBn ASC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<PublisherEntity>>

    @Query("SELECT * FROM publishers")
    suspend fun getAll(): List<PublisherEntity>

    @Query("SELECT * FROM publishers WHERE publisherId = :publisherId LIMIT 1")
    suspend fun getById(publisherId: String): PublisherEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(publishers: List<PublisherEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(publisher: PublisherEntity)

    @Query("UPDATE publishers SET enabled = :enabled WHERE publisherId = :publisherId")
    suspend fun updateEnabled(publisherId: String, enabled: Boolean)
}

@Dao
interface FeedSourceDao {
    @Query("SELECT * FROM feed_sources ORDER BY enabled DESC, verificationStatus ASC, publisherNameBn ASC")
    fun observeAll(): Flow<List<FeedSourceEntity>>

    @Query("SELECT * FROM feed_sources")
    suspend fun getAll(): List<FeedSourceEntity>

    @Query("SELECT * FROM feed_sources WHERE enabled = 1 AND verificationStatus = 'VERIFIED'")
    suspend fun getEnabledVerified(): List<FeedSourceEntity>

    @Query("SELECT * FROM feed_sources WHERE publisherId = :publisherId")
    suspend fun getByPublisher(publisherId: String): List<FeedSourceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sources: List<FeedSourceEntity>)

    @Query("UPDATE feed_sources SET enabled = :enabled, healthStatus = :health WHERE sourceId = :sourceId")
    suspend fun updateSourceEnabled(sourceId: String, enabled: Boolean, health: FeedHealthStatus)

    @Query("UPDATE feed_sources SET enabled = :enabled, healthStatus = :health WHERE publisherId = :publisherId AND verificationStatus = 'VERIFIED'")
    suspend fun updatePublisherSourcesEnabled(publisherId: String, enabled: Boolean, health: FeedHealthStatus)

    @Query(
        """
        UPDATE feed_sources
        SET healthStatus = :healthStatus,
            lastSyncAttempt = :attemptAt,
            lastSuccessfulSync = COALESCE(:successAt, lastSuccessfulSync),
            lastError = :error,
            collectedArticleCount = CASE WHEN :collectedCount >= 0 THEN :collectedCount ELSE collectedArticleCount END
        WHERE sourceId = :sourceId
        """
    )
    suspend fun updateSyncStatus(
        sourceId: String,
        healthStatus: FeedHealthStatus,
        attemptAt: Long,
        successAt: Long?,
        error: String?,
        collectedCount: Int
    )
}

@Dao
interface SyncLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncLogEntity)

    @Query("SELECT * FROM sync_logs ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 40): Flow<List<SyncLogEntity>>

    @Query("DELETE FROM sync_logs WHERE startedAt < :olderThanEpochMs")
    suspend fun pruneOlderThan(olderThanEpochMs: Long)
}

@Dao
interface KeywordAlertDao {
    @Query("SELECT * FROM keyword_alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<KeywordAlertEntity>>

    @Query("SELECT * FROM keyword_alerts WHERE enabled = 1")
    suspend fun getEnabled(): List<KeywordAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KeywordAlertEntity): Long

    @Query("UPDATE keyword_alerts SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM keyword_alerts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NotifiedArticleDao {
    @Query("SELECT articleId FROM notified_articles")
    suspend fun getAllNotifiedArticleIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<NotifiedArticleEntity>)

    @Query("DELETE FROM notified_articles WHERE notifiedAt < :olderThanEpochMs")
    suspend fun pruneOlderThan(olderThanEpochMs: Long)
}
