package bd.nib.songbadpatro.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.presentation.components.CompactHorizontalEditorialCard
import bd.nib.songbadpatro.presentation.components.EditorialArticleCard
import bd.nib.songbadpatro.presentation.components.EditorialEmptyState
import bd.nib.songbadpatro.presentation.components.EditorialLoadingState
import bd.nib.songbadpatro.presentation.components.OfflineBanner
import bd.nib.songbadpatro.presentation.components.PublisherChipCard
import bd.nib.songbadpatro.presentation.util.BanglaFormatter
import bd.nib.songbadpatro.presentation.util.ShareAndBrowserHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onOpenCategory: (EditorialCategory) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdminQueue: () -> Unit
) {
    val language = state.preferences.language
    val dataSaver = state.preferences.dataSaverMode
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (language == AppLanguage.ENGLISH) "My News (আমার খবর)" else "আমার খবর",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (language == AppLanguage.ENGLISH) {
                                "Columns • Editorials • Opinions • Interviews"
                            } else {
                                "কলাম • সম্পাদকীয় • মতামত • সাক্ষাৎকার"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.testTag("home_search_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = if (language == AppLanguage.ENGLISH) "Search" else "অনুসন্ধান"
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("home_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = if (language == AppLanguage.ENGLISH) "Refresh" else "রিফ্রেশ"
                        )
                    }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("home_profile_menu_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = if (language == AppLanguage.ENGLISH) "Menu & Settings" else "মেনু ও সেটিংস"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (language == AppLanguage.ENGLISH) "Settings" else "সেটিংস") },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (language == AppLanguage.ENGLISH) "Manage Publishers" else "প্রকাশক ও উৎস ব্যবস্থাপনা") },
                            onClick = {
                                menuExpanded = false
                                onOpenSources()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (language == AppLanguage.ENGLISH) "Verification & Sync Logs" else "যাচাই কিউ ও সিঙ্ক লগ") },
                            onClick = {
                                menuExpanded = false
                                onOpenAdminQueue()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_feed_list")
            ) {
                if (!state.isOnline) {
                    item(key = "offline_banner") {
                        OfflineBanner(language = language)
                    }
                }

                // Active Scope & Cutoff Banner
                item(key = "cutoff_scope_banner") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Newspaper,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (language == AppLanguage.ENGLISH) {
                                    "Strictly editorial commentary & interviews published on or after ${state.preferences.publicationCutoffIsoDate} (Asia/Dhaka)."
                                } else {
                                    "${BanglaFormatter.toBanglaDigits(state.preferences.publicationCutoffIsoDate)} তারিখ বা তার পরে প্রকাশিত কলাম, সম্পাদকীয়, মতামত ও সাক্ষাৎকার।"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                if (state.recentlyPublished.isEmpty() && state.isRefreshing) {
                    item(key = "loading_state") {
                        EditorialLoadingState(
                            message = if (language == AppLanguage.ENGLISH) {
                                "Collecting verified Bangladeshi columns, editorials & interviews..."
                            } else {
                                "বাংলাদেশি পত্রিকার যাচাইকৃত কলাম, সম্পাদকীয়, মতামত ও সাক্ষাৎকার সংগৃহীত হচ্ছে..."
                            }
                        )
                    }
                } else if (state.recentlyPublished.isEmpty()) {
                    item(key = "empty_state") {
                        EditorialEmptyState(
                            title = if (language == AppLanguage.ENGLISH) {
                                "No editorial articles available yet"
                            } else {
                                "এখনও কোনো সম্পাদকীয় বা কলাম পাওয়া যায়নি"
                            },
                            subtitle = if (language == AppLanguage.ENGLISH) {
                                "Only verified columns, editorials, opinions, and interviews published on or after ${state.preferences.publicationCutoffIsoDate} are shown."
                            } else {
                                "কেবল ২৩ নভেম্বর ২০২৬ বা তার পরে প্রকাশিত যাচাইকৃত কলাম, সম্পাদকীয়, মতামত ও সাক্ষাৎকার এখানে প্রদর্শিত হয়।"
                            },
                            actionLabel = if (language == AppLanguage.ENGLISH) "Sync Publishers" else "পুনরায় সিঙ্ক করুন",
                            onAction = onRefresh
                        )
                    }
                } else {
                    // 1. Featured Commentary
                    if (state.featuredCommentary.isNotEmpty()) {
                        item(key = "featured_section") {
                            HorizontalArticleSection(
                                title = if (language == AppLanguage.ENGLISH) "Featured Commentary" else "বিশেষ মন্তব্য ও বিশ্লেষণ",
                                articles = state.featuredCommentary,
                                language = language,
                                dataSaver = dataSaver,
                                onSeeAll = null,
                                onOpenArticle = onOpenArticle,
                                onToggleBookmark = onToggleBookmark
                            )
                        }
                    }

                    // 2. Latest Columns (কলাম)
                    if (state.latestColumns.isNotEmpty()) {
                        item(key = "columns_section") {
                            HorizontalArticleSection(
                                title = if (language == AppLanguage.ENGLISH) "Latest Columns" else "সর্বশেষ কলাম",
                                articles = state.latestColumns,
                                language = language,
                                dataSaver = dataSaver,
                                onSeeAll = { onOpenCategory(EditorialCategory.COLUMN) },
                                onOpenArticle = onOpenArticle,
                                onToggleBookmark = onToggleBookmark
                            )
                        }
                    }

                    // 3. Latest Editorials (সম্পাদকীয়)
                    if (state.latestEditorials.isNotEmpty()) {
                        item(key = "editorials_section") {
                            HorizontalArticleSection(
                                title = if (language == AppLanguage.ENGLISH) "Latest Editorials" else "সর্বশেষ সম্পাদকীয়",
                                articles = state.latestEditorials,
                                language = language,
                                dataSaver = dataSaver,
                                onSeeAll = { onOpenCategory(EditorialCategory.EDITORIAL) },
                                onOpenArticle = onOpenArticle,
                                onToggleBookmark = onToggleBookmark
                            )
                        }
                    }

                    // 4. Latest Opinions (মতামত)
                    if (state.latestOpinions.isNotEmpty()) {
                        item(key = "opinions_section") {
                            HorizontalArticleSection(
                                title = if (language == AppLanguage.ENGLISH) "Latest Opinions" else "সর্বশেষ মতামত",
                                articles = state.latestOpinions,
                                language = language,
                                dataSaver = dataSaver,
                                onSeeAll = { onOpenCategory(EditorialCategory.OPINION) },
                                onOpenArticle = onOpenArticle,
                                onToggleBookmark = onToggleBookmark
                            )
                        }
                    }

                    // 5. Latest Interviews (সাক্ষাৎকার)
                    if (state.latestInterviews.isNotEmpty()) {
                        item(key = "interviews_section") {
                            HorizontalArticleSection(
                                title = if (language == AppLanguage.ENGLISH) "Latest Interviews" else "সর্বশেষ সাক্ষাৎকার",
                                articles = state.latestInterviews,
                                language = language,
                                dataSaver = dataSaver,
                                onSeeAll = { onOpenCategory(EditorialCategory.INTERVIEW) },
                                onOpenArticle = onOpenArticle,
                                onToggleBookmark = onToggleBookmark
                            )
                        }
                    }

                    // 6. Most Read (shown ONLY when reliable popularity metadata exists)
                    if (state.mostReadArticles.isNotEmpty()) {
                        item(key = "most_read_section") {
                            HorizontalArticleSection(
                                title = if (language == AppLanguage.ENGLISH) "Most Read" else "সর্বাধিক পঠিত",
                                articles = state.mostReadArticles,
                                language = language,
                                dataSaver = dataSaver,
                                onSeeAll = null,
                                onOpenArticle = onOpenArticle,
                                onToggleBookmark = onToggleBookmark
                            )
                        }
                    }

                    // 7. Recently Added Publishers
                    if (state.recentlyAddedPublishers.isNotEmpty()) {
                        item(key = "publishers_strip") {
                            Column {
                                SectionHeaderRow(
                                    title = if (language == AppLanguage.ENGLISH) "Recently Added Publishers" else "নতুন যুক্ত প্রকাশক",
                                    seeAllLabel = if (language == AppLanguage.ENGLISH) "Manage" else "ব্যবস্থাপনা",
                                    onSeeAll = onOpenSources
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(state.recentlyAddedPublishers, key = { it.publisherId }) { pub ->
                                        PublisherChipCard(
                                            publisher = pub,
                                            language = language,
                                            dataSaver = dataSaver,
                                            onClick = onOpenSources
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 8. Recently Published Vertical Feed
                    item(key = "recent_header") {
                        SectionHeaderRow(
                            title = if (language == AppLanguage.ENGLISH) "Recently Published" else "সম্প্রতি প্রকাশিত",
                            seeAllLabel = null,
                            onSeeAll = null
                        )
                    }

                    items(state.recentlyPublished, key = { "recent_${it.articleId}" }) { article ->
                        EditorialArticleCard(
                            article = article,
                            language = language,
                            dataSaver = dataSaver,
                            onClick = { onOpenArticle(article.articleId) },
                            onToggleBookmark = { onToggleBookmark(article.articleId) },
                            onAuthorClick = onOpenAuthor,
                            onOpenOriginalUrl = { url -> ShareAndBrowserHelper.openExternalUrl(context, url) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalArticleSection(
    title: String,
    articles: List<EditorialArticle>,
    language: AppLanguage,
    dataSaver: Boolean,
    onSeeAll: (() -> Unit)?,
    onOpenArticle: (String) -> Unit,
    onToggleBookmark: (String) -> Unit
) {
    Column {
        SectionHeaderRow(
            title = title,
            seeAllLabel = if (onSeeAll != null) {
                if (language == AppLanguage.ENGLISH) "See all" else "সব দেখুন"
            } else {
                null
            },
            onSeeAll = onSeeAll
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(articles, key = { it.articleId }) { article ->
                CompactHorizontalEditorialCard(
                    article = article,
                    language = language,
                    dataSaver = dataSaver,
                    onClick = { onOpenArticle(article.articleId) },
                    onToggleBookmark = { onToggleBookmark(article.articleId) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    seeAllLabel: String?,
    onSeeAll: (() -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (seeAllLabel != null && onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text(text = seeAllLabel)
            }
        }
    }
}
