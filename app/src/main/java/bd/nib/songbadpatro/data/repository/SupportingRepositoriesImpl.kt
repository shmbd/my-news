package bd.nib.songbadpatro.data.repository

import bd.nib.songbadpatro.data.local.PreferencesDataStore
import bd.nib.songbadpatro.data.local.dao.ArticleDao
import bd.nib.songbadpatro.data.local.dao.AuthorDao
import bd.nib.songbadpatro.data.local.dao.FeedSourceDao
import bd.nib.songbadpatro.data.local.dao.KeywordAlertDao
import bd.nib.songbadpatro.data.local.dao.NotifiedArticleDao
import bd.nib.songbadpatro.data.local.dao.PublisherDao
import bd.nib.songbadpatro.data.local.dao.SyncLogDao
import bd.nib.songbadpatro.data.local.entity.ArticleWithUserStateRow
import bd.nib.songbadpatro.data.local.entity.AuthorEntity
import bd.nib.songbadpatro.data.local.entity.FeedSourceEntity
import bd.nib.songbadpatro.data.local.entity.KeywordAlertEntity
import bd.nib.songbadpatro.data.local.entity.NotifiedArticleEntity
import bd.nib.songbadpatro.data.local.entity.PublisherEntity
import bd.nib.songbadpatro.data.local.entity.SyncLogEntity
import bd.nib.songbadpatro.data.registry.PublisherRegistryLoader
import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.AuthorProfile
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.FeedHealthStatus
import bd.nib.songbadpatro.domain.model.FeedSource
import bd.nib.songbadpatro.domain.model.KeywordAlertRule
import bd.nib.songbadpatro.domain.model.PersonRoleType
import bd.nib.songbadpatro.domain.model.Publisher
import bd.nib.songbadpatro.domain.model.SourceVerificationStatus
import bd.nib.songbadpatro.domain.model.SyncLogEntry
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.AlertRepository
import bd.nib.songbadpatro.domain.repository.AuthorRepository
import bd.nib.songbadpatro.domain.repository.PublisherRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
class PublisherRepositoryImpl @Inject constructor(
    private val publisherDao: PublisherDao,
    private val feedSourceDao: FeedSourceDao,
    private val syncLogDao: SyncLogDao,
    private val registryLoader: PublisherRegistryLoader
) : PublisherRepository {

    override suspend fun ensureRegistrySeeded() {
        val manifest = registryLoader.loadRegistryManifest()
        val (publishers, sources) = registryLoader.toSeedEntities(manifest)
        if (publishers.isNotEmpty()) {
            publisherDao.insertIgnore(publishers)
        }
        if (sources.isNotEmpty()) {
            feedSourceDao.insertIgnore(sources)
        }
    }

    override fun observePublishers(): Flow<List<Publisher>> =
        combine(publisherDao.observeAll(), feedSourceDao.observeAll()) { pubEntities, sourceEntities ->
            val sourcesByPub = sourceEntities.groupBy { it.publisherId }
            pubEntities.map { pub ->
                mapPublisherWithSources(pub, sourcesByPub[pub.publisherId].orEmpty())
            }
        }

    override fun observeRecentlyAddedPublishers(limit: Int): Flow<List<Publisher>> =
        combine(publisherDao.observeRecentlyAdded(limit), feedSourceDao.observeAll()) { pubEntities, sourceEntities ->
            val sourcesByPub = sourceEntities.groupBy { it.publisherId }
            pubEntities.map { pub ->
                mapPublisherWithSources(pub, sourcesByPub[pub.publisherId].orEmpty())
            }
        }

    override fun observeFeedSources(): Flow<List<FeedSource>> =
        feedSourceDao.observeAll().map { list -> list.map(FeedSourceEntity::toDomain) }

    override suspend fun setPublisherEnabled(publisherId: String, enabled: Boolean) {
        val pub = publisherDao.getById(publisherId) ?: return
        if (enabled && pub.verificationStatus != SourceVerificationStatus.VERIFIED) {
            return // Unverified or restricted publishers remain disabled until verified
        }
        publisherDao.updateEnabled(publisherId, enabled)
        feedSourceDao.updatePublisherSourcesEnabled(
            publisherId = publisherId,
            enabled = enabled,
            health = if (enabled) FeedHealthStatus.NEVER_SYNCED else FeedHealthStatus.DISABLED
        )
    }

    override suspend fun setFeedSourceEnabled(sourceId: String, enabled: Boolean) {
        feedSourceDao.updateSourceEnabled(
            sourceId = sourceId,
            enabled = enabled,
            health = if (enabled) FeedHealthStatus.NEVER_SYNCED else FeedHealthStatus.DISABLED
        )
    }

    override fun observeRecentSyncLogs(limit: Int): Flow<List<SyncLogEntry>> =
        syncLogDao.observeRecent(limit).map { logs -> logs.map(SyncLogEntity::toDomain) }

    private fun mapPublisherWithSources(
        pub: PublisherEntity,
        sources: List<FeedSourceEntity>
    ): Publisher {
        val supportedCats = sources.flatMap { it.supportedCategories }.distinct()
        val methods = sources.map { it.collectionMethod }.distinct()
        val lastSuccess = sources.mapNotNull { it.lastSuccessfulSync }.maxOrNull()
        val lastErr = sources.firstOrNull { !it.lastError.isNullOrBlank() }?.lastError
        val totalArticles = sources.sumOf { it.collectedArticleCount.coerceAtLeast(0) }
        val health = when {
            !pub.enabled || pub.verificationStatus != SourceVerificationStatus.VERIFIED -> FeedHealthStatus.DISABLED
            sources.any { it.healthStatus == FeedHealthStatus.HEALTHY } -> FeedHealthStatus.HEALTHY
            sources.any { it.healthStatus == FeedHealthStatus.ERROR } -> FeedHealthStatus.ERROR
            else -> FeedHealthStatus.NEVER_SYNCED
        }

        return Publisher(
            publisherId = pub.publisherId,
            nameBn = pub.nameBn,
            nameEn = pub.nameEn,
            website = pub.website,
            logoUrl = pub.logoUrl,
            language = pub.language,
            enabled = pub.enabled,
            verificationStatus = pub.verificationStatus,
            supportedCategories = supportedCats,
            collectionMethods = methods,
            lastSuccessfulSync = lastSuccess,
            lastError = lastErr,
            healthStatus = health,
            collectedArticleCount = totalArticles,
            addedAt = pub.addedAt
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AuthorRepositoryImpl @Inject constructor(
    private val authorDao: AuthorDao,
    private val articleDao: ArticleDao,
    private val settingsRepository: SettingsRepository
) : AuthorRepository {

    override fun observeAuthors(roleFilter: PersonRoleType?, searchQuery: String): Flow<List<AuthorProfile>> =
        authorDao.observeAuthors(roleFilter, searchQuery.trim())
            .map { list -> list.map(AuthorEntity::toDomain) }

    override fun observeAuthorById(authorId: String): Flow<AuthorProfile?> =
        authorDao.observeById(authorId).map { it?.toDomain() }

    override fun observeArticlesForPerson(authorId: String): Flow<List<EditorialArticle>> =
        settingsRepository.preferencesFlow.flatMapLatest { prefs ->
            val cutoffMs = PublicationCutoffConfig.cutoffEpochMillisFor(prefs.publicationCutoffIsoDate)
            articleDao.observeArticlesForPerson(authorId, cutoffMs)
                .map { rows -> rows.map(ArticleWithUserStateRow::toDomain) }
        }
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: PreferencesDataStore
) : SettingsRepository {
    override val preferencesFlow: Flow<UserPreferences> = dataStore.preferencesFlow
    override suspend fun getPreferences(): UserPreferences = dataStore.getPreferences()
    override suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) =
        dataStore.update(transform)
}

@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val keywordAlertDao: KeywordAlertDao,
    private val notifiedArticleDao: NotifiedArticleDao
) : AlertRepository {

    override fun observeKeywordAlerts(): Flow<List<KeywordAlertRule>> =
        keywordAlertDao.observeAll().map { list -> list.map(KeywordAlertEntity::toDomain) }

    override suspend fun getEnabledKeywordAlerts(): List<KeywordAlertRule> =
        keywordAlertDao.getEnabled().map(KeywordAlertEntity::toDomain)

    override suspend fun addKeywordAlert(
        keyword: String,
        category: EditorialCategory?,
        publisherId: String?,
        authorNameFilter: String?
    ): Long {
        val cleaned = keyword.trim()
        if (cleaned.isBlank()) return -1L
        return keywordAlertDao.insert(
            KeywordAlertEntity(
                keyword = cleaned,
                category = category,
                publisherId = publisherId,
                authorNameFilter = authorNameFilter?.trim()?.ifBlank { null },
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun setKeywordAlertEnabled(id: Long, enabled: Boolean) =
        keywordAlertDao.updateEnabled(id, enabled)

    override suspend fun deleteKeywordAlert(id: Long) =
        keywordAlertDao.deleteById(id)

    override suspend fun getNotifiedArticleIds(): Set<String> =
        notifiedArticleDao.getAllNotifiedArticleIds().toSet()

    override suspend fun markArticlesNotified(articleIds: List<String>, reason: String) {
        val now = System.currentTimeMillis()
        notifiedArticleDao.insertAll(
            articleIds.map { id ->
                NotifiedArticleEntity(articleId = id, reason = reason, notifiedAt = now)
            }
        )
        notifiedArticleDao.pruneOlderThan(now - 30L * 24 * 60 * 60 * 1000L)
    }
}
