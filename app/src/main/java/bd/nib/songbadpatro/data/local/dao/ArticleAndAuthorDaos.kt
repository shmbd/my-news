package bd.nib.songbadpatro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bd.nib.songbadpatro.data.local.entity.ArticleEntity
import bd.nib.songbadpatro.data.local.entity.ArticleWithUserStateRow
import bd.nib.songbadpatro.data.local.entity.AuthorEntity
import bd.nib.songbadpatro.data.local.entity.BookmarkEntity
import bd.nib.songbadpatro.data.local.entity.ReadingHistoryEntity
import bd.nib.songbadpatro.domain.model.ArticleVerificationStatus
import bd.nib.songbadpatro.domain.model.ContentLanguage
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.PersonRoleType
import kotlinx.coroutines.flow.Flow

data class CategoryCountTuple(
    val category: EditorialCategory,
    val count: Int
)

@Dao
interface ArticleDao {

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.verificationStatus = 'PUBLISHED'
          AND a.originalPublicationDate >= :cutoffEpochMs
        ORDER BY a.originalPublicationDate DESC
        LIMIT :limit
        """
    )
    fun observeRecentPublished(cutoffEpochMs: Long, limit: Int): Flow<List<ArticleWithUserStateRow>>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.verificationStatus = 'PUBLISHED'
          AND a.originalPublicationDate >= :cutoffEpochMs
        ORDER BY (CASE WHEN a.thumbnailUrl IS NOT NULL THEN 1 ELSE 0 END) DESC,
                 a.classificationConfidence DESC,
                 a.originalPublicationDate DESC
        LIMIT :limit
        """
    )
    fun observeFeaturedCommentary(cutoffEpochMs: Long, limit: Int): Flow<List<ArticleWithUserStateRow>>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.verificationStatus = 'PUBLISHED'
          AND a.category = :category
          AND a.originalPublicationDate >= :cutoffEpochMs
        ORDER BY a.originalPublicationDate DESC
        LIMIT :limit
        """
    )
    fun observeByCategory(
        category: EditorialCategory,
        cutoffEpochMs: Long,
        limit: Int
    ): Flow<List<ArticleWithUserStateRow>>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.verificationStatus = 'PUBLISHED'
          AND a.originalPublicationDate >= :cutoffEpochMs
          AND a.popularityScore IS NOT NULL
          AND a.popularityScore > 0
        ORDER BY a.popularityScore DESC, a.originalPublicationDate DESC
        LIMIT :limit
        """
    )
    fun observeMostRead(cutoffEpochMs: Long, limit: Int): Flow<List<ArticleWithUserStateRow>>

    @Query(
        """
        SELECT category, COUNT(*) AS count
        FROM articles
        WHERE verificationStatus = 'PUBLISHED'
          AND originalPublicationDate >= :cutoffEpochMs
        GROUP BY category
        """
    )
    fun observeCategoryCounts(cutoffEpochMs: Long): Flow<List<CategoryCountTuple>>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.articleId = :articleId
        LIMIT 1
        """
    )
    fun observeById(articleId: String): Flow<ArticleWithUserStateRow?>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.articleId = :articleId
        LIMIT 1
        """
    )
    suspend fun getRowById(articleId: String): ArticleWithUserStateRow?

    @Query("SELECT * FROM articles WHERE articleId = :articleId LIMIT 1")
    suspend fun getEntityById(articleId: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE canonicalUrl = :canonicalUrl LIMIT 1")
    suspend fun getByCanonicalUrl(canonicalUrl: String): ArticleEntity?

    @Query("SELECT * FROM articles ORDER BY originalPublicationDate DESC LIMIT :limit")
    suspend fun getRecentEntitiesSnapshot(limit: Int = 600): List<ArticleEntity>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.verificationStatus != 'PUBLISHED'
        ORDER BY a.createdAt DESC
        LIMIT :limit
        """
    )
    fun observePendingArticles(limit: Int): Flow<List<ArticleWithUserStateRow>>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.verificationStatus = 'PUBLISHED'
          AND a.originalPublicationDate >= :effectiveFromMs
          AND (:toEpochMs IS NULL OR a.originalPublicationDate <= :toEpochMs)
          AND (:publisherId IS NULL OR a.publisherId = :publisherId)
          AND (:language IS NULL OR a.language = :language)
          AND (
              :authorQuery = ''
              OR COALESCE(a.authorName, '') LIKE '%' || :authorQuery || '%'
              OR COALESCE(a.intervieweeName, '') LIKE '%' || :authorQuery || '%'
          )
          AND (
              :query = ''
              OR a.title LIKE '%' || :query || '%'
              OR a.description LIKE '%' || :query || '%'
              OR COALESCE(a.authorName, '') LIKE '%' || :query || '%'
              OR COALESCE(a.intervieweeName, '') LIKE '%' || :query || '%'
              OR a.publisherNameBn LIKE '%' || :query || '%'
              OR a.publisherNameEn LIKE '%' || :query || '%'
              OR a.tags LIKE '%' || :query || '%'
          )
        ORDER BY a.originalPublicationDate DESC
        LIMIT :limit
        """
    )
    fun searchPublished(
        query: String,
        publisherId: String?,
        authorQuery: String,
        effectiveFromMs: Long,
        toEpochMs: Long?,
        language: ContentLanguage?,
        limit: Int = 300
    ): Flow<List<ArticleWithUserStateRow>>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        INNER JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.originalPublicationDate >= :cutoffEpochMs
        ORDER BY b.savedAt DESC
        """
    )
    fun observeBookmarked(cutoffEpochMs: Long): Flow<List<ArticleWithUserStateRow>>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        INNER JOIN reading_history rh ON rh.articleId = a.articleId
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        WHERE a.originalPublicationDate >= :cutoffEpochMs
        ORDER BY rh.viewedAt DESC
        LIMIT :limit
        """
    )
    fun observeReadingHistory(cutoffEpochMs: Long, limit: Int): Flow<List<ArticleWithUserStateRow>>

    @Query(
        """
        SELECT a.*, b.savedAt AS savedAt, rh.viewedAt AS viewedAt
        FROM articles a
        LEFT JOIN bookmarks b ON b.articleId = a.articleId
        LEFT JOIN reading_history rh ON rh.articleId = a.articleId
        WHERE a.verificationStatus = 'PUBLISHED'
          AND a.originalPublicationDate >= :cutoffEpochMs
          AND (a.authorId = :personId OR a.intervieweeId = :personId)
        ORDER BY a.originalPublicationDate DESC
        """
    )
    fun observeArticlesForPerson(personId: String, cutoffEpochMs: Long): Flow<List<ArticleWithUserStateRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticles(articles: List<ArticleEntity>)

    @Query(
        """
        UPDATE articles
        SET extractedContent = :extractedContent,
            isFullExtractPermitted = :permitted,
            readerRestricted = :restricted,
            lastSyncedAt = :updatedAt
        WHERE articleId = :articleId
        """
    )
    suspend fun updateExtractedReaderCache(
        articleId: String,
        extractedContent: String?,
        permitted: Boolean,
        restricted: Boolean,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE articles
        SET verificationStatus = :status,
            category = :category,
            classificationConfidence = 0.90
        WHERE articleId = :articleId
        """
    )
    suspend fun updateVerificationStatus(
        articleId: String,
        status: ArticleVerificationStatus,
        category: EditorialCategory
    )

    @Query("DELETE FROM articles WHERE articleId = :articleId")
    suspend fun deleteById(articleId: String)

    @Query("DELETE FROM articles WHERE originalPublicationDate < :cutoffEpochMs")
    suspend fun deleteBeforeCutoff(cutoffEpochMs: Long): Int

    @Query(
        """
        DELETE FROM articles
        WHERE articleId NOT IN (SELECT articleId FROM bookmarks)
        """
    )
    suspend fun deleteNonBookmarked(): Int

    @Query(
        """
        DELETE FROM articles
        WHERE articleId NOT IN (SELECT articleId FROM bookmarks)
          AND articleId NOT IN (
              SELECT articleId FROM articles
              ORDER BY originalPublicationDate DESC
              LIMIT :keepLatest
          )
        """
    )
    suspend fun pruneExcessArticles(keepLatest: Int = 500): Int

    @Query("SELECT COUNT(*) FROM articles WHERE verificationStatus = 'PUBLISHED' AND originalPublicationDate >= :cutoffEpochMs")
    suspend fun countPublished(cutoffEpochMs: Long): Int

    @Query("SELECT COUNT(*) FROM articles WHERE verificationStatus != 'PUBLISHED'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM articles WHERE extractedContent IS NOT NULL AND extractedContent != ''")
    suspend fun countCachedExcerpts(): Int
}

@Dao
interface BookmarkDao {
    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE articleId = :articleId)")
    suspend fun isBookmarked(articleId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: String)

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun countBookmarks(): Int
}

@Dao
interface ReadingHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM reading_history")
    suspend fun countHistory(): Int
}

@Dao
interface AuthorDao {
    @Query(
        """
        SELECT * FROM authors
        WHERE articleCount > 0
          AND (:roleFilter IS NULL OR roleType = :roleFilter OR roleType = 'BOTH')
          AND (
              :searchQuery = ''
              OR name LIKE '%' || :searchQuery || '%'
              OR normalizedName LIKE '%' || :searchQuery || '%'
              OR COALESCE(affiliation, '') LIKE '%' || :searchQuery || '%'
              OR COALESCE(designation, '') LIKE '%' || :searchQuery || '%'
              OR COALESCE(publisherNameBn, '') LIKE '%' || :searchQuery || '%'
              OR COALESCE(publisherNameEn, '') LIKE '%' || :searchQuery || '%'
          )
        ORDER BY articleCount DESC, name ASC
        """
    )
    fun observeAuthors(roleFilter: PersonRoleType?, searchQuery: String): Flow<List<AuthorEntity>>

    @Query("SELECT * FROM authors WHERE authorId = :authorId LIMIT 1")
    fun observeById(authorId: String): Flow<AuthorEntity?>

    @Query("SELECT * FROM authors WHERE authorId = :authorId LIMIT 1")
    suspend fun getById(authorId: String): AuthorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(authors: List<AuthorEntity>)

    @Query("SELECT COUNT(*) FROM authors WHERE articleCount > 0")
    suspend fun countActiveAuthors(): Int
}
