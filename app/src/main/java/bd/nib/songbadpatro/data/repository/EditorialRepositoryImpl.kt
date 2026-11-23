package bd.nib.songbadpatro.data.repository

import android.util.Log
import bd.nib.songbadpatro.data.local.dao.ArticleDao
import bd.nib.songbadpatro.data.local.dao.AuthorDao
import bd.nib.songbadpatro.data.local.dao.BookmarkDao
import bd.nib.songbadpatro.data.local.dao.FeedSourceDao
import bd.nib.songbadpatro.data.local.dao.PublisherDao
import bd.nib.songbadpatro.data.local.dao.ReadingHistoryDao
import bd.nib.songbadpatro.data.local.dao.SyncLogDao
import bd.nib.songbadpatro.data.local.entity.ArticleEntity
import bd.nib.songbadpatro.data.local.entity.ArticleWithUserStateRow
import bd.nib.songbadpatro.data.local.entity.AuthorEntity
import bd.nib.songbadpatro.data.local.entity.BookmarkEntity
import bd.nib.songbadpatro.data.local.entity.FeedSourceEntity
import bd.nib.songbadpatro.data.local.entity.PublisherEntity
import bd.nib.songbadpatro.data.local.entity.ReadingHistoryEntity
import bd.nib.songbadpatro.data.local.entity.SyncLogEntity
import bd.nib.songbadpatro.data.remote.DiscoveredRawItem
import bd.nib.songbadpatro.data.remote.ExtractedPageMetadata
import bd.nib.songbadpatro.data.remote.FeedNetworkClient
import bd.nib.songbadpatro.data.remote.NetworkFetchResult
import bd.nib.songbadpatro.data.remote.RobotsTxtPolicyChecker
import bd.nib.songbadpatro.data.remote.RssAtomParser
import bd.nib.songbadpatro.data.remote.SectionAndMetadataExtractor
import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.ArticleVerificationStatus
import bd.nib.songbadpatro.domain.model.CacheStatistics
import bd.nib.songbadpatro.domain.model.CollectionMethod
import bd.nib.songbadpatro.domain.model.ConfidenceTier
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.FeedHealthStatus
import bd.nib.songbadpatro.domain.model.PersonRoleType
import bd.nib.songbadpatro.domain.model.ReaderAccessStatus
import bd.nib.songbadpatro.domain.model.ReaderDocument
import bd.nib.songbadpatro.domain.model.SearchFilter
import bd.nib.songbadpatro.domain.model.SyncSourceReport
import bd.nib.songbadpatro.domain.model.SyncSummary
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.PublisherRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.domain.usecase.AuthorIdentityResolver
import bd.nib.songbadpatro.domain.usecase.BanglaTitleNormalizer
import bd.nib.songbadpatro.domain.usecase.ClassificationInput
import bd.nib.songbadpatro.domain.usecase.ClassifyEditorialContentUseCase
import bd.nib.songbadpatro.domain.usecase.DateValidationOutcome
import bd.nib.songbadpatro.domain.usecase.DateValidationUseCase
import bd.nib.songbadpatro.domain.usecase.DeduplicateAndSyndicateUseCase
import bd.nib.songbadpatro.domain.usecase.SortAndFilterArticlesUseCase
import bd.nib.songbadpatro.domain.usecase.UrlCanonicalizer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EditorialRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao,
    private val bookmarkDao: BookmarkDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val authorDao: AuthorDao,
    private val publisherDao: PublisherDao,
    private val feedSourceDao: FeedSourceDao,
    private val syncLogDao: SyncLogDao,
    private val publisherRepository: PublisherRepository,
    private val settingsRepository: SettingsRepository,
    private val networkClient: FeedNetworkClient,
    private val robotsChecker: RobotsTxtPolicyChecker,
    private val rssAtomParser: RssAtomParser,
    private val metadataExtractor: SectionAndMetadataExtractor,
    private val dateValidator: DateValidationUseCase,
    private val classifier: ClassifyEditorialContentUseCase,
    private val deduplicator: DeduplicateAndSyndicateUseCase,
    private val sorterAndFilter: SortAndFilterArticlesUseCase
) : EditorialRepository {

    companion object {
        private const val TAG = "EditorialRepository"
        private const val MAX_CONCURRENT_SOURCES = 4
        private const val MAX_DETAIL_FETCHES_PER_SOURCE = 5
        private const val MAX_CACHED_ARTICLES = 600
    }

    private val activeCutoffMsFlow: Flow<Long> = settingsRepository.preferencesFlow
        .map { prefs -> PublicationCutoffConfig.cutoffEpochMillisFor(prefs.publicationCutoffIsoDate) }
        .distinctUntilChanged()

    override fun observeRecentPublished(limit: Int): Flow<List<EditorialArticle>> =
        settingsRepository.preferencesFlow.flatMapLatest { prefs ->
            val cutoffMs = PublicationCutoffConfig.cutoffEpochMillisFor(prefs.publicationCutoffIsoDate)
            articleDao.observeRecentPublished(cutoffMs, limit).map { rows ->
                rows.map(ArticleWithUserStateRow::toDomain)
                    .filter { it.category in prefs.enabledCategories }
            }
        }

    override fun observeFeaturedCommentary(limit: Int): Flow<List<EditorialArticle>> =
        settingsRepository.preferencesFlow.flatMapLatest { prefs ->
            val cutoffMs = PublicationCutoffConfig.cutoffEpochMillisFor(prefs.publicationCutoffIsoDate)
            articleDao.observeFeaturedCommentary(cutoffMs, limit).map { rows ->
                rows.map(ArticleWithUserStateRow::toDomain)
                    .filter { it.category in prefs.enabledCategories }
            }
        }

    override fun observeByCategory(category: EditorialCategory, limit: Int): Flow<List<EditorialArticle>> =
        activeCutoffMsFlow.flatMapLatest { cutoffMs ->
            articleDao.observeByCategory(category, cutoffMs, limit)
                .map { rows -> rows.map(ArticleWithUserStateRow::toDomain) }
        }

    override fun observeMostReadIfAvailable(limit: Int): Flow<List<EditorialArticle>> =
        activeCutoffMsFlow.flatMapLatest { cutoffMs ->
            articleDao.observeMostRead(cutoffMs, limit)
                .map { rows -> rows.map(ArticleWithUserStateRow::toDomain) }
        }

    override fun observeCategoryCounts(): Flow<Map<EditorialCategory, Int>> =
        activeCutoffMsFlow.flatMapLatest { cutoffMs ->
            articleDao.observeCategoryCounts(cutoffMs).map { list ->
                val base = EditorialCategory.entries.associateWith { 0 }.toMutableMap()
                list.forEach { base[it.category] = it.count }
                base
            }
        }

    override fun observeArticleById(articleId: String): Flow<EditorialArticle?> =
        articleDao.observeById(articleId).map { row ->
            row?.toDomain()
        }

    override fun observePendingVerificationArticles(limit: Int): Flow<List<EditorialArticle>> =
        articleDao.observePendingArticles(limit).map { rows -> rows.map(ArticleWithUserStateRow::toDomain) }

    override fun searchArticles(filter: SearchFilter): Flow<List<EditorialArticle>> =
        settingsRepository.preferencesFlow.flatMapLatest { prefs ->
            val cutoffMs = PublicationCutoffConfig.cutoffEpochMillisFor(prefs.publicationCutoffIsoDate)
            val effectiveFrom = maxOf(cutoffMs, filter.fromDateEpochMs ?: cutoffMs)
            articleDao.searchPublished(
                query = filter.query.trim(),
                publisherId = filter.publisherId,
                authorQuery = filter.authorQuery.trim(),
                effectiveFromMs = effectiveFrom,
                toEpochMs = filter.toDateEpochMs,
                language = filter.language
            ).map { rows ->
                val domain = rows.map(ArticleWithUserStateRow::toDomain)
                sorterAndFilter.applyFilterAndSort(domain, filter, prefs.publicationCutoffIsoDate)
            }
        }

    override fun observeBookmarkedArticles(): Flow<List<EditorialArticle>> =
        activeCutoffMsFlow.flatMapLatest { cutoffMs ->
            articleDao.observeBookmarked(cutoffMs).map { rows -> rows.map(ArticleWithUserStateRow::toDomain) }
        }

    override fun observeReadingHistory(limit: Int): Flow<List<EditorialArticle>> =
        activeCutoffMsFlow.flatMapLatest { cutoffMs ->
            articleDao.observeReadingHistory(cutoffMs, limit).map { rows -> rows.map(ArticleWithUserStateRow::toDomain) }
        }

    override suspend fun toggleBookmark(articleId: String) {
        val currentlySaved = bookmarkDao.isBookmarked(articleId)
        setBookmarked(articleId, !currentlySaved)
    }

    override suspend fun setBookmarked(articleId: String, bookmarked: Boolean) {
        if (bookmarked) {
            bookmarkDao.upsertBookmark(
                BookmarkEntity(articleId = articleId, savedAt = System.currentTimeMillis())
            )
        } else {
            bookmarkDao.deleteByArticleId(articleId)
        }
    }

    override suspend fun recordReadingHistory(articleId: String) {
        readingHistoryDao.upsertHistory(
            ReadingHistoryEntity(articleId = articleId, viewedAt = System.currentTimeMillis())
        )
    }

    override suspend fun clearReadingHistory() {
        readingHistoryDao.clearAll()
    }

    override suspend fun loadReaderDocument(articleId: String, forceRefresh: Boolean): ReaderDocument {
        val row = articleDao.getRowById(articleId)
            ?: throw IllegalArgumentException("Article not found: $articleId")
        val article = row.toDomain()

        // Use cached permitted excerpt immediately when available and not forcing refresh
        if (!forceRefresh && !article.extractedContent.isNullOrBlank()) {
            val cachedParagraphs = article.extractedContent
                .split("\n\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (cachedParagraphs.isNotEmpty()) {
                return ReaderDocument(
                    article = article,
                    excerptParagraphs = cachedParagraphs,
                    accessStatus = ReaderAccessStatus.CACHED_OFFLINE,
                    isCompleteTextAvailable = false,
                    statusBannerBn = "কপিরাইট নীতিমালা মেনে সংরক্ষিত অফলাইন সারসংক্ষেপ ও নির্বাচিত অংশ দেখানো হচ্ছে। সম্পূর্ণ নিবন্ধটি পড়তে মূল প্রকাশকের ওয়েবসাইটে যান।",
                    statusBannerEn = "Showing cached offline summary & permitted preview in accordance with copyright policy. Open the original publisher to read the complete article."
                )
            }
        }

        // Verify robots.txt before attempting live HTML metadata/excerpt extraction
        val allowedByRobots = robotsChecker.isUrlAllowed(article.originalArticleUrl)
        if (!allowedByRobots) {
            return ReaderDocument(
                article = article,
                excerptParagraphs = listOfNotNull(article.description.takeIf { it.isNotBlank() }),
                accessStatus = ReaderAccessStatus.PAYWALLED_OR_RESTRICTED,
                isCompleteTextAvailable = false,
                statusBannerBn = "প্রকাশকের robots.txt নীতিমালা অনুযায়ী শুধু সংক্ষিপ্ত বিবরণ দেখানো হচ্ছে। সম্পূর্ণ লেখাটি মূল ওয়েবসাইটে পড়ুন।",
                statusBannerEn = "Showing publisher summary only per robots.txt policy. Read the full article on the publisher's website."
            )
        }

        return when (val net = networkClient.fetchText(article.originalArticleUrl, FeedNetworkClient.ACCEPT_HTML)) {
            is NetworkFetchResult.Success -> {
                val meta = metadataExtractor.extractArticleMetadata(net.body, net.finalUrl)
                val now = System.currentTimeMillis()
                when (meta.readerAccessStatus) {
                    ReaderAccessStatus.LIVE_PERMITTED_EXTRACT -> {
                        val excerptText = meta.permittedExcerptParagraphs.joinToString("\n\n")
                        articleDao.updateExtractedReaderCache(
                            articleId = article.articleId,
                            extractedContent = excerptText,
                            permitted = true,
                            restricted = false,
                            updatedAt = now
                        )
                        ReaderDocument(
                            article = article.copy(extractedContent = excerptText, isFullExtractPermitted = true),
                            excerptParagraphs = meta.permittedExcerptParagraphs,
                            accessStatus = ReaderAccessStatus.LIVE_PERMITTED_EXTRACT,
                            isCompleteTextAvailable = false,
                            statusBannerBn = "প্রকাশকের কপিরাইট ও কৃতজ্ঞতা অক্ষুণ্ণ রেখে প্রারম্ভিক অংশ ও সারসংক্ষেপ দেখানো হচ্ছে। সম্পূর্ণ নিবন্ধটি মূল প্রকাশকের ওয়েবসাইটে পড়ুন।",
                            statusBannerEn = "Showing permitted preview & summary with full attribution. Visit the original publisher to read the complete work."
                        )
                    }
                    ReaderAccessStatus.PAYWALLED_OR_RESTRICTED -> {
                        articleDao.updateExtractedReaderCache(
                            articleId = article.articleId,
                            extractedContent = null,
                            permitted = false,
                            restricted = true,
                            updatedAt = now
                        )
                        ReaderDocument(
                            article = article.copy(readerRestricted = true),
                            excerptParagraphs = listOfNotNull(article.description.takeIf { it.isNotBlank() }),
                            accessStatus = ReaderAccessStatus.PAYWALLED_OR_RESTRICTED,
                            isCompleteTextAvailable = false,
                            statusBannerBn = "এই লেখাটি প্রকাশকের সীমাবদ্ধ অ্যাক্সেস বা সাবস্ক্রিপশনের আওতাভুক্ত। অনুগ্রহ করে মূল ওয়েবসাইটে পড়ুন।",
                            statusBannerEn = "This article is subject to publisher access restrictions or subscription. Please read it on the original website."
                        )
                    }
                    else -> {
                        ReaderDocument(
                            article = article,
                            excerptParagraphs = listOfNotNull(article.description.takeIf { it.isNotBlank() }),
                            accessStatus = ReaderAccessStatus.METADATA_AND_SUMMARY_ONLY,
                            isCompleteTextAvailable = false,
                            statusBannerBn = "শুধু শিরোনাম ও প্রকাশকের প্রদত্ত সংক্ষিপ্ত বিবরণ উপলব্ধ। সম্পূর্ণ লেখাটি মূল ওয়েবসাইটে পড়ুন।",
                            statusBannerEn = "Showing headline and publisher description. Open the original link for the full article."
                        )
                    }
                }
            }
            is NetworkFetchResult.Restricted -> {
                articleDao.updateExtractedReaderCache(
                    articleId = article.articleId,
                    extractedContent = null,
                    permitted = false,
                    restricted = true,
                    updatedAt = System.currentTimeMillis()
                )
                ReaderDocument(
                    article = article.copy(readerRestricted = true),
                    excerptParagraphs = listOfNotNull(article.description.takeIf { it.isNotBlank() }),
                    accessStatus = ReaderAccessStatus.PAYWALLED_OR_RESTRICTED,
                    isCompleteTextAvailable = false,
                    statusBannerBn = "${net.messageBn} মূল প্রকাশকের ওয়েবসাইটে সম্পূর্ণ লেখাটি পড়ুন।",
                    statusBannerEn = "Publisher restricted direct extraction (HTTP ${net.httpCode}). Open the original article link below."
                )
            }
            is NetworkFetchResult.Error -> {
                val fallbackParagraphs = article.extractedContent
                    ?.split("\n\n")
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(article.description.takeIf { it.isNotBlank() })
                ReaderDocument(
                    article = article,
                    excerptParagraphs = fallbackParagraphs,
                    accessStatus = if (!article.extractedContent.isNullOrBlank()) {
                        ReaderAccessStatus.CACHED_OFFLINE
                    } else {
                        ReaderAccessStatus.METADATA_AND_SUMMARY_ONLY
                    },
                    isCompleteTextAvailable = false,
                    statusBannerBn = "${net.messageBn} সংরক্ষিত বিবরণ দেখানো হচ্ছে।",
                    statusBannerEn = "Offline or network unavailable — showing cached metadata & summary."
                )
            }
        }
    }

    override suspend fun syncAllEnabledSources(): SyncSummary {
        publisherRepository.ensureRegistrySeeded()
        val enabledPublishers = publisherDao.getAll().filter { it.enabled }.associateBy { it.publisherId }
        val activeSources = feedSourceDao.getEnabledVerified()
            .filter { it.publisherId in enabledPublishers.keys }
        return executeSyncPipeline(activeSources, enabledPublishers)
    }

    override suspend fun syncPublisher(publisherId: String): SyncSummary {
        publisherRepository.ensureRegistrySeeded()
        val pub = publisherDao.getById(publisherId)
            ?: return SyncSummary(0, 0, 0, 0, 0, 0, 0, emptyList())
        val sources = feedSourceDao.getByPublisher(publisherId)
            .filter { it.verificationStatus.name == "VERIFIED" }
        return executeSyncPipeline(sources, mapOf(pub.publisherId to pub))
    }

    private suspend fun executeSyncPipeline(
        sources: List<FeedSourceEntity>,
        publishersById: Map<String, PublisherEntity>
    ): SyncSummary = coroutineScope {
        val prefs = settingsRepository.getPreferences()
        val cutoffIso = prefs.publicationCutoffIsoDate
        val cutoffMs = PublicationCutoffConfig.cutoffEpochMillisFor(cutoffIso)

        // Purge any pre-cutoff records if cutoff date was moved forward administratively
        articleDao.deleteBeforeCutoff(cutoffMs)

        if (sources.isEmpty()) {
            return@coroutineScope SyncSummary(0, 0, 0, 0, 0, 0, 0, emptyList())
        }

        val existingEntities = articleDao.getRecentEntitiesSnapshot(MAX_CACHED_ARTICLES)
        val existingDomain = existingEntities.map {
            ArticleWithUserStateRow(it, savedAt = null, viewedAt = null).toDomain()
        }
        val existingCanonicals = existingDomain.map { it.canonicalUrl }.toSet()

        val semaphore = Semaphore(MAX_CONCURRENT_SOURCES)
        val perSourceOutputs = sources.map { source ->
            async {
                semaphore.withPermit {
                    val pub = publishersById[source.publisherId]
                    syncSingleFeedSource(source, pub, existingCanonicals, cutoffIso)
                }
            }
        }.awaitAll()

        val reports = perSourceOutputs.map { it.first }
        val discoveredArticles = perSourceOutputs.flatMap { it.second }

        var newlyPublished = 0
        if (discoveredArticles.isNotEmpty()) {
            val reconciled = deduplicator.reconcile(existingDomain, discoveredArticles, cutoffIso)
            val validToPersist = reconciled.filter {
                it.verificationStatus == ArticleVerificationStatus.PENDING_DATE_VERIFICATION ||
                    it.originalPublicationDate >= cutoffMs
            }
            articleDao.upsertArticles(validToPersist.map(ArticleEntity::fromDomain))
            articleDao.pruneExcessArticles(MAX_CACHED_ARTICLES)
            rebuildAuthorIndex(cutoffMs)

            newlyPublished = validToPersist.count {
                it.verificationStatus == ArticleVerificationStatus.PUBLISHED &&
                    it.canonicalUrl !in existingCanonicals
            }
        }

        // Prune sync logs older than 14 days
        syncLogDao.pruneOlderThan(System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000L)

        SyncSummary(
            totalSourcesAttempted = sources.size,
            succeededSources = reports.count { it.success },
            failedSources = reports.count { !it.success },
            newlyPublishedCount = newlyPublished,
            pendingVerificationCount = reports.sumOf { it.pendingCount },
            rejectedPreCutoffCount = reports.sumOf { it.rejectedPreCutoffCount },
            rejectedNonEditorialCount = reports.sumOf { it.rejectedNonEditorialCount },
            reports = reports
        )
    }

    private suspend fun syncSingleFeedSource(
        source: FeedSourceEntity,
        publisher: PublisherEntity?,
        existingCanonicals: Set<String>,
        cutoffIso: String
    ): Pair<SyncSourceReport, List<EditorialArticle>> {
        val startedAt = System.currentTimeMillis()
        val endpoint = when (source.collectionMethod) {
            CollectionMethod.RSS_ATOM, CollectionMethod.SITEMAP -> source.feedUrl ?: source.sectionUrl
            CollectionMethod.SECTION_HTML_JSONLD -> source.sectionUrl ?: source.feedUrl
        }.orEmpty()

        if (endpoint.isBlank()) {
            return recordSourceFailure(source, startedAt, "উৎসের ঠিকানা কনফিগার করা নেই।")
        }

        // For section HTML discovery, verify robots.txt permission first
        if (source.collectionMethod == CollectionMethod.SECTION_HTML_JSONLD) {
            if (!robotsChecker.isUrlAllowed(endpoint)) {
                return recordSourceFailure(
                    source,
                    startedAt,
                    "প্রকাশকের robots.txt এই পাতায় স্বয়ংক্রিয় সংগ্রহের অনুমতি দেয় না।"
                )
            }
        }

        val acceptHeader = if (source.collectionMethod == CollectionMethod.SECTION_HTML_JSONLD) {
            FeedNetworkClient.ACCEPT_HTML
        } else {
            FeedNetworkClient.ACCEPT_FEED
        }

        return when (val net = networkClient.fetchText(endpoint, acceptHeader)) {
            is NetworkFetchResult.Restricted -> recordSourceFailure(source, startedAt, net.messageBn)
            is NetworkFetchResult.Error -> recordSourceFailure(source, startedAt, net.messageBn)
            is NetworkFetchResult.Success -> {
                try {
                    val rawItems: List<DiscoveredRawItem> = when (source.collectionMethod) {
                        CollectionMethod.RSS_ATOM, CollectionMethod.SITEMAP -> {
                            rssAtomParser.parse(net.body).items
                        }
                        CollectionMethod.SECTION_HTML_JSONLD -> {
                            metadataExtractor.discoverFromSectionHtml(
                                html = net.body,
                                sectionUrl = net.finalUrl,
                                rules = source.parsingRules
                            )
                        }
                    }

                    val accepted = mutableListOf<EditorialArticle>()
                    var rejectedOld = 0
                    var rejectedNonEd = 0
                    var pendingCount = 0
                    var detailFetchCount = 0

                    for (raw in rawItems) {
                        val initialCanonical = UrlCanonicalizer.canonicalize(raw.link)
                        if (initialCanonical.isBlank()) continue

                        // Enrich via Stage 2 HTML/JSON-LD extraction when needed (and permitted)
                        var enrichedMeta: ExtractedPageMetadata? = null
                        val needsEnrichment = raw.requiresDetailFetchForDateOrMeta &&
                            initialCanonical !in existingCanonicals &&
                            detailFetchCount < MAX_DETAIL_FETCHES_PER_SOURCE

                        if (needsEnrichment && robotsChecker.isUrlAllowed(raw.link)) {
                            detailFetchCount++
                            val pageRes = networkClient.fetchText(raw.link, FeedNetworkClient.ACCEPT_HTML)
                            if (pageRes is NetworkFetchResult.Success) {
                                enrichedMeta = metadataExtractor.extractArticleMetadata(pageRes.body, pageRes.finalUrl)
                            }
                        }

                        val resolvedCanonical = UrlCanonicalizer.canonicalize(enrichedMeta?.canonicalUrl ?: raw.link)
                        val resolvedTitle = enrichedMeta?.headline?.takeIf { it.isNotBlank() } ?: raw.title
                        val resolvedDesc = enrichedMeta?.description?.takeIf { it.isNotBlank() } ?: raw.description
                        val resolvedAuthor = enrichedMeta?.authorName ?: raw.author
                        val resolvedAff = enrichedMeta?.authorAffiliation ?: raw.authorAffiliation
                        val resolvedTags = (raw.categoriesAndTags + enrichedMeta?.tags.orEmpty()).distinct()
                        val resolvedOriginalPub = raw.originalPublishedRaw ?: enrichedMeta?.originalPublishedRaw
                        val resolvedUpdated = raw.updatedRaw ?: enrichedMeta?.updatedRaw

                        // Stage 3: Classification BEFORE publication
                        val classResult = classifier.classify(
                            ClassificationInput(
                                title = resolvedTitle,
                                description = resolvedDesc,
                                url = raw.link,
                                publisherNameBn = source.publisherNameBn,
                                publisherNameEn = source.publisherNameEn,
                                authorName = resolvedAuthor,
                                authorAffiliation = resolvedAff,
                                feedCategoriesAndTags = resolvedTags,
                                pageSectionName = enrichedMeta?.sectionName,
                                hasQuestionAnswerStructure = enrichedMeta?.hasQuestionAnswerStructure == true,
                                sourceDefaultCategory = source.defaultCategory,
                                isDedicatedEditorialSource = source.parsingRules.isDedicatedEditorialFeed,
                                forceOfficialEditorial = source.parsingRules.forceOfficialEditorial
                            )
                        )

                        if (classResult.tier == ConfidenceTier.LOW || classResult.category == null) {
                            rejectedNonEd++
                            continue
                        }

                        // Strict Date Validation BEFORE database insertion
                        val dateOutcome = dateValidator.validate(
                            originalPublishedRaw = resolvedOriginalPub,
                            updatedRaw = resolvedUpdated,
                            cutoffIsoDate = cutoffIso,
                            isExplicitlyNewlyPublished = enrichedMeta?.isExplicitlyNewlyPublished == true,
                            allowPendingQueueWhenMissing = true
                        )

                        val (pubEpochMs, updatedEpochMs, verificationStatus) = when (dateOutcome) {
                            is DateValidationOutcome.RejectedBeforeCutoff,
                            is DateValidationOutcome.RejectedInvalidOrMissing -> {
                                rejectedOld++
                                continue
                            }
                            is DateValidationOutcome.PendingDateVerification -> {
                                pendingCount++
                                Triple(0L, dateOutcome.updatedEpochMs, ArticleVerificationStatus.PENDING_DATE_VERIFICATION)
                            }
                            is DateValidationOutcome.Eligible -> {
                                val verStatus = if (classResult.tier == ConfidenceTier.HIGH) {
                                    ArticleVerificationStatus.PUBLISHED
                                } else {
                                    pendingCount++
                                    ArticleVerificationStatus.PENDING_VERIFICATION
                                }
                                Triple(dateOutcome.originalPublicationEpochMs, dateOutcome.updatedEpochMs, verStatus)
                            }
                        }

                        val cleanAuthor = resolvedAuthor?.takeIf {
                            !AuthorIdentityResolver.isInstitutionalByline(it, source.publisherNameBn, source.publisherNameEn)
                        }
                        val authorId = cleanAuthor?.let {
                            AuthorIdentityResolver.resolvePersonId(it, resolvedAff, source.publisherId)
                        }
                        val intervieweeName = enrichedMeta?.intervieweeName
                        val intervieweeId = intervieweeName?.let {
                            AuthorIdentityResolver.resolvePersonId(it, enrichedMeta?.intervieweeDesignation, source.publisherId)
                        }

                        val now = System.currentTimeMillis()
                        accepted.add(
                            EditorialArticle(
                                articleId = stableArticleId(resolvedCanonical),
                                canonicalUrl = resolvedCanonical,
                                originalArticleUrl = UrlCanonicalizer.normalizeDisplayUrl(raw.link),
                                title = resolvedTitle,
                                normalizedTitle = BanglaTitleNormalizer.normalize(resolvedTitle),
                                description = resolvedDesc,
                                authorId = authorId,
                                authorName = cleanAuthor,
                                authorAffiliation = resolvedAff,
                                authorPhotograph = enrichedMeta?.authorImageUrl ?: raw.authorImageUrl,
                                interviewerName = enrichedMeta?.interviewerName,
                                intervieweeId = intervieweeId,
                                intervieweeName = intervieweeName,
                                intervieweeDesignation = enrichedMeta?.intervieweeDesignation,
                                publisherId = source.publisherId,
                                publisherNameBn = source.publisherNameBn,
                                publisherNameEn = source.publisherNameEn,
                                publisherLogoUrl = publisher?.logoUrl,
                                publisherWebsite = publisher?.website ?: source.sectionUrl.orEmpty(),
                                category = classResult.category,
                                isOfficialEditorial = classResult.isOfficialEditorial,
                                originalPublicationDate = pubEpochMs,
                                updatedDate = updatedEpochMs,
                                thumbnailUrl = enrichedMeta?.thumbnailUrl ?: raw.thumbnailUrl,
                                extractedContent = enrichedMeta?.permittedExcerptParagraphs
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.joinToString("\n\n"),
                                isFullExtractPermitted = enrichedMeta?.isFullExtractPermitted == true,
                                readerRestricted = enrichedMeta?.readerAccessStatus == ReaderAccessStatus.PAYWALLED_OR_RESTRICTED,
                                language = source.language,
                                tags = resolvedTags,
                                classificationConfidence = classResult.confidence,
                                classificationReason = classResult.reasonSummary,
                                verificationStatus = verificationStatus,
                                createdAt = now,
                                lastSyncedAt = now
                            )
                        )
                    }

                    val publishedFromSource = accepted.count { it.verificationStatus == ArticleVerificationStatus.PUBLISHED }
                    val completedAt = System.currentTimeMillis()

                    feedSourceDao.updateSyncStatus(
                        sourceId = source.sourceId,
                        healthStatus = FeedHealthStatus.HEALTHY,
                        attemptAt = completedAt,
                        successAt = completedAt,
                        error = null,
                        collectedCount = publishedFromSource
                    )
                    syncLogDao.insert(
                        SyncLogEntity(
                            sourceId = source.sourceId,
                            publisherId = source.publisherId,
                            startedAt = startedAt,
                            completedAt = completedAt,
                            success = true,
                            fetchedCount = rawItems.size,
                            acceptedCount = publishedFromSource,
                            pendingCount = pendingCount,
                            rejectedPreCutoffCount = rejectedOld,
                            rejectedNonEditorialCount = rejectedNonEd,
                            errorMessage = null
                        )
                    )

                    val report = SyncSourceReport(
                        sourceId = source.sourceId,
                        publisherId = source.publisherId,
                        sourceNameBn = source.nameBn,
                        success = true,
                        fetchedCount = rawItems.size,
                        publishedCount = publishedFromSource,
                        pendingCount = pendingCount,
                        rejectedPreCutoffCount = rejectedOld,
                        rejectedNonEditorialCount = rejectedNonEd
                    )
                    report to accepted
                } catch (ex: Exception) {
                    Log.w(TAG, "Source sync parse failure (${source.sourceId}): ${ex.message}")
                    recordSourceFailure(
                        source,
                        startedAt,
                        "উৎস প্রক্রিয়াকরণে ত্রুটি: ${ex.message ?: "অবৈধ বিন্যাস"}"
                    )
                }
            }
        }
    }

    private suspend fun recordSourceFailure(
        source: FeedSourceEntity,
        startedAt: Long,
        messageBn: String
    ): Pair<SyncSourceReport, List<EditorialArticle>> {
        val completedAt = System.currentTimeMillis()
        feedSourceDao.updateSyncStatus(
            sourceId = source.sourceId,
            healthStatus = FeedHealthStatus.ERROR,
            attemptAt = completedAt,
            successAt = null,
            error = messageBn,
            collectedCount = -1
        )
        syncLogDao.insert(
            SyncLogEntity(
                sourceId = source.sourceId,
                publisherId = source.publisherId,
                startedAt = startedAt,
                completedAt = completedAt,
                success = false,
                fetchedCount = 0,
                acceptedCount = 0,
                pendingCount = 0,
                rejectedPreCutoffCount = 0,
                rejectedNonEditorialCount = 0,
                errorMessage = messageBn
            )
        )
        return SyncSourceReport(
            sourceId = source.sourceId,
            publisherId = source.publisherId,
            sourceNameBn = source.nameBn,
            success = false,
            errorMessage = messageBn
        ) to emptyList()
    }

    private suspend fun rebuildAuthorIndex(cutoffEpochMs: Long) {
        val published = articleDao.getRecentEntitiesSnapshot(MAX_CACHED_ARTICLES)
            .filter {
                it.verificationStatus == ArticleVerificationStatus.PUBLISHED &&
                    it.originalPublicationDate >= cutoffEpochMs
            }

        data class MutablePersonAcc(
            val id: String,
            val name: String,
            val normalizedName: String,
            var photo: String?,
            var designation: String?,
            var affiliation: String?,
            val publisherId: String,
            val publisherNameBn: String,
            val publisherNameEn: String,
            var hasAuthorRole: Boolean = false,
            var hasIntervieweeRole: Boolean = false,
            var count: Int = 0
        )

        val map = LinkedHashMap<String, MutablePersonAcc>()

        for (art in published) {
            if (!art.authorId.isNullOrBlank() && !art.authorName.isNullOrBlank()) {
                val acc = map.getOrPut(art.authorId) {
                    MutablePersonAcc(
                        id = art.authorId,
                        name = art.authorName,
                        normalizedName = AuthorIdentityResolver.normalizePersonName(art.authorName),
                        photo = art.authorPhotograph,
                        designation = null,
                        affiliation = art.authorAffiliation,
                        publisherId = art.publisherId,
                        publisherNameBn = art.publisherNameBn,
                        publisherNameEn = art.publisherNameEn
                    )
                }
                acc.hasAuthorRole = true
                acc.count++
                if (acc.photo == null) acc.photo = art.authorPhotograph
                if (acc.affiliation == null) acc.affiliation = art.authorAffiliation
            }

            if (!art.intervieweeId.isNullOrBlank() && !art.intervieweeName.isNullOrBlank()) {
                val acc = map.getOrPut(art.intervieweeId) {
                    MutablePersonAcc(
                        id = art.intervieweeId,
                        name = art.intervieweeName,
                        normalizedName = AuthorIdentityResolver.normalizePersonName(art.intervieweeName),
                        photo = null,
                        designation = art.intervieweeDesignation,
                        affiliation = art.intervieweeDesignation,
                        publisherId = art.publisherId,
                        publisherNameBn = art.publisherNameBn,
                        publisherNameEn = art.publisherNameEn
                    )
                }
                acc.hasIntervieweeRole = true
                if (art.intervieweeId != art.authorId) acc.count++
                if (acc.designation == null) acc.designation = art.intervieweeDesignation
            }
        }

        val authorEntities = map.values.map { acc ->
            val role = when {
                acc.hasAuthorRole && acc.hasIntervieweeRole -> PersonRoleType.BOTH
                acc.hasIntervieweeRole -> PersonRoleType.INTERVIEWEE
                else -> PersonRoleType.AUTHOR
            }
            AuthorEntity(
                authorId = acc.id,
                name = acc.name,
                normalizedName = acc.normalizedName,
                photograph = acc.photo,
                designation = acc.designation,
                affiliation = acc.affiliation,
                publisherId = acc.publisherId,
                publisherNameBn = acc.publisherNameBn,
                publisherNameEn = acc.publisherNameEn,
                biography = null,
                roleType = role,
                articleCount = acc.count
            )
        }
        if (authorEntities.isNotEmpty()) {
            authorDao.upsertAll(authorEntities)
        }
    }

    override suspend fun approvePendingArticle(articleId: String, category: EditorialCategory) {
        val entity = articleDao.getEntityById(articleId) ?: return
        val cutoffMs = PublicationCutoffConfig.cutoffEpochMillisFor(
            settingsRepository.getPreferences().publicationCutoffIsoDate
        )
        // Strict rule: never approve an article without a verified date >= cutoff
        if (entity.originalPublicationDate < cutoffMs) return
        articleDao.updateVerificationStatus(
            articleId = articleId,
            status = ArticleVerificationStatus.PUBLISHED,
            category = category
        )
        rebuildAuthorIndex(cutoffMs)
    }

    override suspend fun rejectPendingArticle(articleId: String) {
        articleDao.deleteById(articleId)
    }

    override suspend fun clearNonBookmarkedCache(): Int = articleDao.deleteNonBookmarked()

    override suspend fun getCacheStatistics(): CacheStatistics {
        val cutoffMs = PublicationCutoffConfig.cutoffEpochMillisFor(
            settingsRepository.getPreferences().publicationCutoffIsoDate
        )
        return CacheStatistics(
            publishedArticleCount = articleDao.countPublished(cutoffMs),
            pendingArticleCount = articleDao.countPending(),
            bookmarkedCount = bookmarkDao.countBookmarks(),
            historyCount = readingHistoryDao.countHistory(),
            cachedExcerptCount = articleDao.countCachedExcerpts(),
            authorCount = authorDao.countActiveAuthors()
        )
    }

    private fun stableArticleId(canonicalUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalUrl.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
