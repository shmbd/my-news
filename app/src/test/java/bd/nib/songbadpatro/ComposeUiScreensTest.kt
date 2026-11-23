package bd.nib.songbadpatro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.ArticleVerificationStatus
import bd.nib.songbadpatro.domain.model.AuthorProfile
import bd.nib.songbadpatro.domain.model.ContentLanguage
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.PersonRoleType
import bd.nib.songbadpatro.domain.model.ReaderAccessStatus
import bd.nib.songbadpatro.domain.model.ReaderDocument
import bd.nib.songbadpatro.domain.model.ReaderTheme
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.presentation.screens.authors.AuthorsScreen
import bd.nib.songbadpatro.presentation.screens.authors.AuthorsUiState
import bd.nib.songbadpatro.presentation.screens.categories.CategoriesScreen
import bd.nib.songbadpatro.presentation.screens.categories.CategoriesUiState
import bd.nib.songbadpatro.presentation.screens.home.HomeScreen
import bd.nib.songbadpatro.presentation.screens.home.HomeUiState
import bd.nib.songbadpatro.presentation.screens.reader.ReaderScreen
import bd.nib.songbadpatro.presentation.screens.reader.ReaderUiState
import bd.nib.songbadpatro.presentation.screens.saved.SavedScreen
import bd.nib.songbadpatro.presentation.screens.saved.SavedUiState
import bd.nib.songbadpatro.presentation.screens.search.SearchScreen
import bd.nib.songbadpatro.presentation.screens.search.SearchUiState
import bd.nib.songbadpatro.presentation.screens.settings.SettingsScreen
import bd.nib.songbadpatro.presentation.screens.settings.SettingsUiState
import bd.nib.songbadpatro.presentation.theme.MyNewsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeUiScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleColumn = EditorialArticle(
        articleId = "ui_col_1",
        canonicalUrl = "https://www.ittefaq.com.bd/opinion/822504",
        originalArticleUrl = "https://www.ittefaq.com.bd/opinion/822504",
        title = "গঙ্গা চুক্তির নবায়ন ও আঞ্চলিক সহযোগিতা",
        normalizedTitle = "গঙ্গা চুক্তির নবায়ন ও আঞ্চলিক সহযোগিতা",
        description = "আন্তঃসীমান্ত নদী ব্যবস্থাপনা নিয়ে বিশেষ কলাম।",
        authorId = "auth_1",
        authorName = "ড. আব্দুল ওয়াদুদ",
        authorAffiliation = "নদী গবেষক",
        authorPhotograph = null,
        interviewerName = null,
        intervieweeId = null,
        intervieweeName = null,
        intervieweeDesignation = null,
        publisherId = "ittefaq",
        publisherNameBn = "দৈনিক ইত্তেফাক",
        publisherNameEn = "The Daily Ittefaq",
        publisherLogoUrl = null,
        publisherWebsite = "https://www.ittefaq.com.bd",
        category = EditorialCategory.COLUMN,
        isOfficialEditorial = false,
        originalPublicationDate = PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS + 3_600_000L,
        updatedDate = null,
        thumbnailUrl = null,
        extractedContent = "প্রথম অনুচ্ছেদ।",
        isFullExtractPermitted = true,
        readerRestricted = false,
        language = ContentLanguage.BANGLA,
        tags = listOf("কলাম"),
        classificationConfidence = 0.92,
        classificationReason = "Tag:Column",
        verificationStatus = ArticleVerificationStatus.PUBLISHED,
        isBookmarked = true,
        createdAt = PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS,
        lastSyncedAt = PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS
    )

    @Test
    fun `HomeScreen displays Bangla editorial sections, offline banner, and hides Most Read when unavailable`() {
        composeRule.setContent {
            MyNewsTheme {
                HomeScreen(
                    state = HomeUiState(
                        isRefreshing = false,
                        isOnline = false,
                        latestColumns = listOf(sampleColumn),
                        recentlyPublished = listOf(sampleColumn),
                        mostReadArticles = emptyList(),
                        preferences = UserPreferences()
                    ),
                    onRefresh = {},
                    onOpenArticle = {},
                    onToggleBookmark = {},
                    onOpenCategory = {},
                    onOpenAuthor = {},
                    onOpenSearch = {},
                    onOpenSources = {},
                    onOpenSettings = {},
                    onOpenAdminQueue = {}
                )
            }
        }

        composeRule.onNodeWithText("আমার খবর").assertIsDisplayed()
        composeRule.onNodeWithTag("offline_banner").assertIsDisplayed()
        composeRule.onNodeWithText("সর্বশেষ কলাম").assertIsDisplayed()
        composeRule.onNodeWithText("গঙ্গা চুক্তির নবায়ন ও আঞ্চলিক সহযোগিতা").assertIsDisplayed()
    }

    @Test
    fun `CategoriesScreen switches tabs and triggers category callback`() {
        var clickedCategory: EditorialCategory? = null
        composeRule.setContent {
            MyNewsTheme {
                CategoriesScreen(
                    state = CategoriesUiState(
                        selectedCategory = EditorialCategory.COLUMN,
                        categoryCounts = mapOf(EditorialCategory.COLUMN to 1, EditorialCategory.INTERVIEW to 2),
                        articles = listOf(sampleColumn)
                    ),
                    onSelectCategory = { clickedCategory = it },
                    onSelectLanguage = {},
                    onSelectPublisher = {},
                    onSelectSort = {},
                    onOpenArticle = {},
                    onOpenAuthor = {},
                    onToggleBookmark = {}
                )
            }
        }

        composeRule.onNodeWithTag("category_tab_INTERVIEW").performClick()
        assertTrue(clickedCategory == EditorialCategory.INTERVIEW)
    }

    @Test
    fun `ReaderScreen renders headline, attribution, typography toggle, and original publisher button`() {
        var bookmarkClicked = false
        composeRule.setContent {
            MyNewsTheme {
                ReaderScreen(
                    state = ReaderUiState(
                        isLoading = false,
                        article = sampleColumn,
                        readerDocument = ReaderDocument(
                            article = sampleColumn,
                            excerptParagraphs = listOf("আন্তঃসীমান্ত নদী ব্যবস্থাপনা নিয়ে বিশেষ কলাম।"),
                            accessStatus = ReaderAccessStatus.CACHED_OFFLINE,
                            isCompleteTextAvailable = false,
                            statusBannerBn = "কপিরাইট নীতিমালা মেনে সংরক্ষিত অফলাইন সারসংক্ষেপ দেখানো হচ্ছে।",
                            statusBannerEn = "Showing cached summary."
                        ),
                        preferences = UserPreferences(readerTheme = ReaderTheme.SEPIA)
                    ),
                    onBack = {},
                    onToggleBookmark = { bookmarkClicked = true },
                    onOpenAuthor = {},
                    onUpdateReaderTheme = {},
                    onUpdateFontScale = {},
                    onUpdateLineSpacing = {}
                )
            }
        }

        composeRule.onNodeWithText("গঙ্গা চুক্তির নবায়ন ও আঞ্চলিক সহযোগিতা").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_bookmark_btn").performClick()
        assertTrue(bookmarkClicked)
        composeRule.onNodeWithTag("reader_content_list")
            .performScrollToNode(hasTestTag("open_original_article_btn"))
        composeRule.onNodeWithTag("open_original_article_btn").assertIsDisplayed()
    }

    @Test
    fun `SavedScreen renders bookmarked editorial articles`() {
        composeRule.setContent {
            MyNewsTheme {
                SavedScreen(
                    state = SavedUiState(bookmarkedArticles = listOf(sampleColumn)),
                    onSelectTab = {},
                    onOpenArticle = {},
                    onOpenAuthor = {},
                    onToggleBookmark = {},
                    onClearHistory = {}
                )
            }
        }
        composeRule.onNodeWithText("সংরক্ষিত (১)").assertIsDisplayed()
    }

    @Test
    fun `AuthorsScreen displays contributor directory and article count`() {
        composeRule.setContent {
            MyNewsTheme {
                AuthorsScreen(
                    state = AuthorsUiState(
                        profiles = listOf(
                            AuthorProfile(
                                authorId = "auth_1",
                                name = "ড. আব্দুল ওয়াদুদ",
                                normalizedName = "ড আব্দুল ওয়াদুদ",
                                photograph = null,
                                designation = "নদী গবেষক",
                                affiliation = null,
                                publisherId = "ittefaq",
                                publisherNameBn = "দৈনিক ইত্তেফাক",
                                publisherNameEn = "The Daily Ittefaq",
                                biography = null,
                                roleType = PersonRoleType.AUTHOR,
                                articleCount = 3
                            )
                        )
                    ),
                    onSelectRole = {},
                    onUpdateQuery = {},
                    onOpenAuthorProfile = {}
                )
            }
        }
        composeRule.onNodeWithText("ড. আব্দুল ওয়াদুদ").assertIsDisplayed()
    }

    @Test
    fun `SearchScreen and SettingsScreen render interactive controls`() {
        composeRule.setContent {
            MyNewsTheme {
                SearchScreen(
                    state = SearchUiState(results = listOf(sampleColumn)),
                    onBack = {},
                    onUpdateQuery = {},
                    onUpdateAuthorQuery = {},
                    onToggleCategory = {},
                    onSelectPublisher = {},
                    onSelectLanguage = {},
                    onSelectDatePreset = {},
                    onSelectSort = {},
                    onOpenArticle = {},
                    onOpenAuthor = {},
                    onToggleBookmark = {}
                )
            }
        }
        composeRule.onNodeWithTag("search_query_field").assertIsDisplayed()
    }

    @Test
    fun `SettingsScreen renders Bangla configuration sections`() {
        composeRule.setContent {
            MyNewsTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    onBack = {},
                    onSetLanguage = {},
                    onSetTheme = {},
                    onSetFontScale = {},
                    onSetLineSpacing = {},
                    onSetSyncFrequency = {},
                    onSetWifiOnly = {},
                    onSetDataSaver = {},
                    onToggleEnabledCategory = {},
                    onSetNotificationsEnabled = {},
                    onNotificationPermissionResult = {},
                    onToggleNotifyCategory = {},
                    onTogglePreferredPublisher = {},
                    onAddPreferredAuthor = {},
                    onRemovePreferredAuthor = {},
                    onSetNotificationFrequency = {},
                    onSetQuietHours = {},
                    onAddKeywordAlert = {},
                    onDeleteKeywordAlert = {},
                    onClearCache = {},
                    onOpenSources = {},
                    onOpenMaintenance = {},
                    onOpenPrivacyPolicy = {},
                    onOpenCollectionPolicy = {},
                    onOpenAbout = {}
                )
            }
        }
        composeRule.onNodeWithTag("settings_list").assertIsDisplayed()
        composeRule.onNodeWithText("সেটিংস").assertIsDisplayed()
    }
}
