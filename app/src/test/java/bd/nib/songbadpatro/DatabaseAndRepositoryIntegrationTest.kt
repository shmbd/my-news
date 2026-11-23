package bd.nib.songbadpatro

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import bd.nib.songbadpatro.data.local.MyNewsDatabase
import bd.nib.songbadpatro.data.local.entity.ArticleEntity
import bd.nib.songbadpatro.data.local.entity.BookmarkEntity
import bd.nib.songbadpatro.data.local.entity.FeedSourceEntity
import bd.nib.songbadpatro.data.local.entity.PublisherEntity
import bd.nib.songbadpatro.data.registry.PublisherRegistryLoader
import bd.nib.songbadpatro.data.remote.FeedNetworkClient
import bd.nib.songbadpatro.data.remote.RobotsTxtPolicyChecker
import bd.nib.songbadpatro.data.remote.RssAtomParser
import bd.nib.songbadpatro.data.remote.SectionAndMetadataExtractor
import bd.nib.songbadpatro.data.repository.EditorialRepositoryImpl
import bd.nib.songbadpatro.data.repository.PublisherRepositoryImpl
import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.ArticleVerificationStatus
import bd.nib.songbadpatro.domain.model.CollectionMethod
import bd.nib.songbadpatro.domain.model.ContentLanguage
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.FeedHealthStatus
import bd.nib.songbadpatro.domain.model.SourceParsingRules
import bd.nib.songbadpatro.domain.model.SourceVerificationStatus
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.domain.usecase.ClassifyEditorialContentUseCase
import bd.nib.songbadpatro.domain.usecase.DateValidationUseCase
import bd.nib.songbadpatro.domain.usecase.DeduplicateAndSyndicateUseCase
import bd.nib.songbadpatro.domain.usecase.SortAndFilterArticlesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseAndRepositoryIntegrationTest {

    private lateinit var db: MyNewsDatabase
    private lateinit var mockWebServer: MockWebServer

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(UserPreferences())
        override val preferencesFlow: Flow<UserPreferences> = state
        override suspend fun getPreferences(): UserPreferences = state.value
        override suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
            state.value = transform(state.value)
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MyNewsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        db.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `Room DAO enforces 2026-11-23 cutoff, bookmark persistence, reading history, and migration SQL`() = runBlocking {
        val cutoff = PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS
        val oldArticle = testEntity("old_1", "https://ex.bd/old", cutoff - 86_400_000L, ArticleVerificationStatus.PUBLISHED)
        val pendingArticle = testEntity("pend_1", "https://ex.bd/pend", cutoff + 3_600_000L, ArticleVerificationStatus.PENDING_VERIFICATION)
        val validCol = testEntity("col_1", "https://ex.bd/col1", cutoff + 7_200_000L, ArticleVerificationStatus.PUBLISHED)

        db.articleDao().upsertArticles(listOf(oldArticle, pendingArticle, validCol))

        // Public feed query returns ONLY validCol (>= cutoff and PUBLISHED)
        val published = db.articleDao().observeRecentPublished(cutoff, 20).first()
        assertEquals(1, published.size)
        assertEquals("col_1", published.first().article.articleId)

        // Bookmark persistence & cache clear protection
        db.bookmarkDao().upsertBookmark(BookmarkEntity(articleId = "col_1", savedAt = System.currentTimeMillis()))
        val bookmarkedRows = db.articleDao().observeBookmarked(cutoff).first()
        assertEquals(1, bookmarkedRows.size)
        assertTrue(bookmarkedRows.first().toDomain().isBookmarked)

        db.articleDao().deleteNonBookmarked()
        assertEquals(1, db.articleDao().countPublished(cutoff))

        // Reading history
        db.readingHistoryDao().upsertHistory(
            bd.nib.songbadpatro.data.local.entity.ReadingHistoryEntity(articleId = "col_1", viewedAt = 1000L)
        )
        assertEquals(1, db.readingHistoryDao().countHistory())
        db.readingHistoryDao().clearAll()
        assertEquals(0, db.readingHistoryDao().countHistory())

        // Execute MIGRATION_1_2 SQL idempotently against SupportSQLiteDatabase
        val sqliteDb: SupportSQLiteDatabase = db.openHelper.writableDatabase
        MyNewsDatabase.MIGRATION_1_2.migrate(sqliteDb)
    }

    @Test
    fun `Repository sync isolates failing publisher, rejects pre-cutoff and general news, and publishes valid editorials`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsRepo = FakeSettingsRepository()
        val registryLoader = PublisherRegistryLoader(context)
        val pubRepo = PublisherRepositoryImpl(db.publisherDao(), db.feedSourceDao(), db.syncLogDao(), registryLoader)

        val healthyFeedUrl = mockWebServer.url("/healthy-opinion.xml").toString()
        val failingFeedUrl = mockWebServer.url("/failing-403.xml").toString()

        // Seed 1 healthy publisher & 1 failing (403) publisher to verify failure isolation
        db.publisherDao().upsert(
            PublisherEntity("pub_ok", "সমকাল", "Samakal", "https://samakal.com", null, ContentLanguage.BANGLA, true, SourceVerificationStatus.VERIFIED, 1000L)
        )
        db.publisherDao().upsert(
            PublisherEntity("pub_fail", "যুগান্তর", "Jugantor", "https://jugantor.com", null, ContentLanguage.BANGLA, true, SourceVerificationStatus.VERIFIED, 900L)
        )
        db.feedSourceDao().insertIgnore(
            listOf(
                FeedSourceEntity(
                    sourceId = "src_ok",
                    publisherId = "pub_ok",
                    publisherNameBn = "সমকাল",
                    publisherNameEn = "Samakal",
                    nameBn = "সমকাল মতামত",
                    nameEn = "Samakal Opinion",
                    feedUrl = healthyFeedUrl,
                    sectionUrl = null,
                    defaultCategory = EditorialCategory.OPINION,
                    supportedCategories = listOf(EditorialCategory.COLUMN, EditorialCategory.EDITORIAL, EditorialCategory.OPINION),
                    language = ContentLanguage.BANGLA,
                    collectionMethod = CollectionMethod.RSS_ATOM,
                    verificationStatus = SourceVerificationStatus.VERIFIED,
                    enabled = true,
                    lastSuccessfulSync = null,
                    lastSyncAttempt = null,
                    lastError = null,
                    healthStatus = FeedHealthStatus.NEVER_SYNCED,
                    collectedArticleCount = 0,
                    parsingRules = SourceParsingRules(isDedicatedEditorialFeed = true)
                ),
                FeedSourceEntity(
                    sourceId = "src_fail",
                    publisherId = "pub_fail",
                    publisherNameBn = "যুগান্তর",
                    publisherNameEn = "Jugantor",
                    nameBn = "যুগান্তর ফিড",
                    nameEn = "Jugantor Feed",
                    feedUrl = failingFeedUrl,
                    sectionUrl = null,
                    defaultCategory = EditorialCategory.EDITORIAL,
                    supportedCategories = listOf(EditorialCategory.EDITORIAL),
                    language = ContentLanguage.BANGLA,
                    collectionMethod = CollectionMethod.RSS_ATOM,
                    verificationStatus = SourceVerificationStatus.VERIFIED,
                    enabled = true,
                    lastSuccessfulSync = null,
                    lastSyncAttempt = null,
                    lastError = null,
                    healthStatus = FeedHealthStatus.NEVER_SYNCED,
                    collectedArticleCount = 0,
                    parsingRules = SourceParsingRules(isDedicatedEditorialFeed = true)
                )
            )
        )

        val xmlPayload = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>সমকাল মতামত</title>
                <item>
                  <title>স্থানীয় সরকার শক্তিশালীকরণের রূপরেখা</title>
                  <link>https://samakal.com/opinion/article/9001</link>
                  <pubDate>Mon, 23 Nov 2026 11:00:00 +0600</pubDate>
                  <dc:creator>ড. তোফায়েল আহমেদ</dc:creator>
                  <category>কলাম</category>
                  <description>স্থানীয় শাসন ব্যবস্থা ও বিকেন্দ্রীকরণ নিয়ে বিশ্লেষণ।</description>
                </item>
                <item>
                  <title>পুরোনো নিবন্ধ যা কাটঅফের আগে প্রকাশিত</title>
                  <link>https://samakal.com/opinion/article/8000</link>
                  <pubDate>Sun, 22 Nov 2026 18:00:00 +0600</pubDate>
                  <dc:creator>ড. তোফায়েল আহমেদ</dc:creator>
                  <category>মতামত</category>
                  <description>২২ নভেম্বরের নিবন্ধ।</description>
                </item>
                <item>
                  <title>ব্রেকিং: সড়ক দুর্ঘটনায় আহত ৫</title>
                  <link>https://samakal.com/crime/9002</link>
                  <pubDate>Mon, 23 Nov 2026 12:00:00 +0600</pubDate>
                  <dc:creator>নিজস্ব প্রতিবেদক</dc:creator>
                  <description>সাধারণ দুর্ঘটনার সংবাদ।</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                return when {
                    request.path?.startsWith("/healthy-opinion.xml") == true ->
                        MockResponse().setResponseCode(200).setBody(xmlPayload)
                    request.path?.startsWith("/failing-403.xml") == true ->
                        MockResponse().setResponseCode(403).setBody("Forbidden")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val networkClient = FeedNetworkClient(OkHttpClient())
        val repo = EditorialRepositoryImpl(
            articleDao = db.articleDao(),
            bookmarkDao = db.bookmarkDao(),
            readingHistoryDao = db.readingHistoryDao(),
            authorDao = db.authorDao(),
            publisherDao = db.publisherDao(),
            feedSourceDao = db.feedSourceDao(),
            syncLogDao = db.syncLogDao(),
            publisherRepository = pubRepo,
            settingsRepository = settingsRepo,
            networkClient = networkClient,
            robotsChecker = RobotsTxtPolicyChecker(networkClient),
            rssAtomParser = RssAtomParser(),
            metadataExtractor = SectionAndMetadataExtractor(),
            dateValidator = DateValidationUseCase(),
            classifier = ClassifyEditorialContentUseCase(),
            deduplicator = DeduplicateAndSyndicateUseCase(),
            sorterAndFilter = SortAndFilterArticlesUseCase()
        )

        val summary = repo.syncPublisher("pub_ok")
        assertEquals(1, summary.succeededSources)
        assertEquals(1, summary.newlyPublishedCount)
        assertEquals(1, summary.rejectedPreCutoffCount)
        assertEquals(1, summary.rejectedNonEditorialCount)

        val failSummary = repo.syncPublisher("pub_fail")
        assertEquals(1, failSummary.failedSources)

        // Healthy publisher's eligible column remains intact despite failing publisher!
        val liveFeed = repo.observeRecentPublished(10).first()
        assertEquals(1, liveFeed.size)
        assertEquals("স্থানীয় সরকার শক্তিশালীকরণের রূপরেখা", liveFeed.first().title)
        assertEquals(EditorialCategory.COLUMN, liveFeed.first().category)
        assertFalse(liveFeed.any { it.title.contains("পুরোনো") })
    }

    private fun testEntity(
        id: String,
        url: String,
        pubDate: Long,
        status: ArticleVerificationStatus
    ) = ArticleEntity(
        articleId = id,
        canonicalUrl = url,
        originalArticleUrl = url,
        title = "টেস্ট কলাম $id",
        normalizedTitle = "টেস্ট কলাম $id",
        description = "বিবরণ",
        authorId = "a1",
        authorName = "ড. আনিসুজ্জামান",
        authorAffiliation = "ঢাকা বিশ্ববিদ্যালয়",
        authorPhotograph = null,
        interviewerName = null,
        intervieweeId = null,
        intervieweeName = null,
        intervieweeDesignation = null,
        publisherId = "prothom_alo",
        publisherNameBn = "প্রথম আলো",
        publisherNameEn = "Prothom Alo",
        publisherLogoUrl = null,
        publisherWebsite = "https://www.prothomalo.com",
        category = EditorialCategory.COLUMN,
        isOfficialEditorial = false,
        originalPublicationDate = pubDate,
        updatedDate = null,
        thumbnailUrl = null,
        extractedContent = null,
        isFullExtractPermitted = false,
        readerRestricted = false,
        language = ContentLanguage.BANGLA,
        tags = listOf("কলাম"),
        syndicatedEditions = emptyList(),
        classificationConfidence = 0.90,
        classificationReason = "Test",
        verificationStatus = status,
        popularityScore = null,
        createdAt = pubDate,
        lastSyncedAt = pubDate
    )
}
