package bd.nib.songbadpatro.domain.repository

import bd.nib.songbadpatro.domain.model.AuthorProfile
import bd.nib.songbadpatro.domain.model.CacheStatistics
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.FeedSource
import bd.nib.songbadpatro.domain.model.KeywordAlertRule
import bd.nib.songbadpatro.domain.model.PersonRoleType
import bd.nib.songbadpatro.domain.model.Publisher
import bd.nib.songbadpatro.domain.model.ReaderDocument
import bd.nib.songbadpatro.domain.model.SearchFilter
import bd.nib.songbadpatro.domain.model.SyncLogEntry
import bd.nib.songbadpatro.domain.model.SyncSummary
import bd.nib.songbadpatro.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface EditorialRepository {
    fun observeRecentPublished(limit: Int = 40): Flow<List<EditorialArticle>>
    fun observeFeaturedCommentary(limit: Int = 8): Flow<List<EditorialArticle>>
    fun observeByCategory(category: EditorialCategory, limit: Int = 60): Flow<List<EditorialArticle>>
    fun observeMostReadIfAvailable(limit: Int = 10): Flow<List<EditorialArticle>>
    fun observeCategoryCounts(): Flow<Map<EditorialCategory, Int>>
    fun observeArticleById(articleId: String): Flow<EditorialArticle?>
    fun observePendingVerificationArticles(limit: Int = 50): Flow<List<EditorialArticle>>
    fun searchArticles(filter: SearchFilter): Flow<List<EditorialArticle>>

    fun observeBookmarkedArticles(): Flow<List<EditorialArticle>>
    fun observeReadingHistory(limit: Int = 100): Flow<List<EditorialArticle>>
    suspend fun toggleBookmark(articleId: String)
    suspend fun setBookmarked(articleId: String, bookmarked: Boolean)
    suspend fun recordReadingHistory(articleId: String)
    suspend fun clearReadingHistory()

    suspend fun loadReaderDocument(articleId: String, forceRefresh: Boolean = false): ReaderDocument
    suspend fun syncAllEnabledSources(): SyncSummary
    suspend fun syncPublisher(publisherId: String): SyncSummary

    suspend fun approvePendingArticle(articleId: String, category: EditorialCategory)
    suspend fun rejectPendingArticle(articleId: String)

    suspend fun clearNonBookmarkedCache(): Int
    suspend fun getCacheStatistics(): CacheStatistics
}

interface PublisherRepository {
    suspend fun ensureRegistrySeeded()
    fun observePublishers(): Flow<List<Publisher>>
    fun observeRecentlyAddedPublishers(limit: Int = 10): Flow<List<Publisher>>
    fun observeFeedSources(): Flow<List<FeedSource>>
    suspend fun setPublisherEnabled(publisherId: String, enabled: Boolean)
    suspend fun setFeedSourceEnabled(sourceId: String, enabled: Boolean)
    fun observeRecentSyncLogs(limit: Int = 40): Flow<List<SyncLogEntry>>
}

interface AuthorRepository {
    fun observeAuthors(roleFilter: PersonRoleType? = null, searchQuery: String = ""): Flow<List<AuthorProfile>>
    fun observeAuthorById(authorId: String): Flow<AuthorProfile?>
    fun observeArticlesForPerson(authorId: String): Flow<List<EditorialArticle>>
}

interface SettingsRepository {
    val preferencesFlow: Flow<UserPreferences>
    suspend fun getPreferences(): UserPreferences
    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences)
}

interface AlertRepository {
    fun observeKeywordAlerts(): Flow<List<KeywordAlertRule>>
    suspend fun getEnabledKeywordAlerts(): List<KeywordAlertRule>
    suspend fun addKeywordAlert(
        keyword: String,
        category: EditorialCategory? = null,
        publisherId: String? = null,
        authorNameFilter: String? = null
    ): Long
    suspend fun setKeywordAlertEnabled(id: Long, enabled: Boolean)
    suspend fun deleteKeywordAlert(id: Long)
    suspend fun getNotifiedArticleIds(): Set<String>
    suspend fun markArticlesNotified(articleIds: List<String>, reason: String)
}
