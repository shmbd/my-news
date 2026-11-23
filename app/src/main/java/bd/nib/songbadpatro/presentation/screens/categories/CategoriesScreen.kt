package bd.nib.songbadpatro.presentation.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.ArticleSortOption
import bd.nib.songbadpatro.domain.model.ContentLanguage
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.Publisher
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.PublisherRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.domain.usecase.SortAndFilterArticlesUseCase
import bd.nib.songbadpatro.presentation.components.EditorialArticleCard
import bd.nib.songbadpatro.presentation.components.EditorialEmptyState
import bd.nib.songbadpatro.presentation.util.BanglaFormatter
import bd.nib.songbadpatro.presentation.util.ShareAndBrowserHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val selectedCategory: EditorialCategory = EditorialCategory.COLUMN,
    val selectedLanguage: ContentLanguage? = null,
    val selectedPublisherId: String? = null,
    val sortOption: ArticleSortOption = ArticleSortOption.NEWEST_FIRST,
    val categoryCounts: Map<EditorialCategory, Int> = emptyMap(),
    val articles: List<EditorialArticle> = emptyList(),
    val publishers: List<Publisher> = emptyList(),
    val preferences: UserPreferences = UserPreferences()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val editorialRepository: EditorialRepository,
    publisherRepository: PublisherRepository,
    settingsRepository: SettingsRepository,
    private val sorter: SortAndFilterArticlesUseCase
) : ViewModel() {

    private val selectedCategory = MutableStateFlow(EditorialCategory.COLUMN)
    private val selectedLanguage = MutableStateFlow<ContentLanguage?>(null)
    private val selectedPublisherId = MutableStateFlow<String?>(null)
    private val sortOption = MutableStateFlow(ArticleSortOption.NEWEST_FIRST)

    private val categoryArticlesFlow = selectedCategory.flatMapLatest { category ->
        editorialRepository.observeByCategory(category, limit = 120)
    }

    val uiState: StateFlow<CategoriesUiState> = combine(
        combine(selectedCategory, selectedLanguage, selectedPublisherId, sortOption) { c, l, p, s ->
            FilterTuple(c, l, p, s)
        },
        categoryArticlesFlow,
        editorialRepository.observeCategoryCounts(),
        publisherRepository.observePublishers(),
        settingsRepository.preferencesFlow
    ) { filter, rawArticles, counts, pubs, prefs ->
        val filtered = rawArticles.filter { art ->
            (filter.language == null || art.language == filter.language) &&
                (filter.publisherId == null || art.publisherId == filter.publisherId)
        }
        CategoriesUiState(
            selectedCategory = filter.category,
            selectedLanguage = filter.language,
            selectedPublisherId = filter.publisherId,
            sortOption = filter.sort,
            categoryCounts = counts,
            articles = sorter.sort(filtered, filter.sort),
            publishers = pubs.filter { it.enabled },
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState()
    )

    fun selectCategory(category: EditorialCategory) {
        selectedCategory.value = category
    }

    fun selectLanguage(language: ContentLanguage?) {
        selectedLanguage.value = language
    }

    fun selectPublisher(publisherId: String?) {
        selectedPublisherId.value = publisherId
    }

    fun selectSort(option: ArticleSortOption) {
        sortOption.value = option
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch {
            editorialRepository.toggleBookmark(articleId)
        }
    }

    private data class FilterTuple(
        val category: EditorialCategory,
        val language: ContentLanguage?,
        val publisherId: String?,
        val sort: ArticleSortOption
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onSelectCategory: (EditorialCategory) -> Unit,
    onSelectLanguage: (ContentLanguage?) -> Unit,
    onSelectPublisher: (String?) -> Unit,
    onSelectSort: (ArticleSortOption) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onToggleBookmark: (String) -> Unit
) {
    val language = state.preferences.language
    val dataSaver = state.preferences.dataSaverMode
    val context = LocalContext.current
    val categories = EditorialCategory.entries

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (language == AppLanguage.ENGLISH) "Editorial Categories" else "সম্পাদকীয় বিভাগসমূহ",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(state.selectedCategory),
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { cat ->
                    val count = state.categoryCounts[cat] ?: 0
                    val countLabel = BanglaFormatter.formatCount(count, language)
                    Tab(
                        selected = state.selectedCategory == cat,
                        onClick = { onSelectCategory(cat) },
                        text = {
                            Text(
                                text = "${cat.displayTitle(language)} ($countLabel)",
                                fontWeight = if (state.selectedCategory == cat) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("category_tab_${cat.name}")
                    )
                }
            }

            Text(
                text = state.selectedCategory.displayDescription(language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Language & Sort filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedLanguage == null,
                        onClick = { onSelectLanguage(null) },
                        label = { Text(if (language == AppLanguage.ENGLISH) "All Languages" else "সব ভাষা") }
                    )
                }
                items(ContentLanguage.entries) { contentLang ->
                    FilterChip(
                        selected = state.selectedLanguage == contentLang,
                        onClick = {
                            onSelectLanguage(if (state.selectedLanguage == contentLang) null else contentLang)
                        },
                        label = { Text(contentLang.displayLabel(language)) }
                    )
                }
                items(ArticleSortOption.entries) { sort ->
                    FilterChip(
                        selected = state.sortOption == sort,
                        onClick = { onSelectSort(sort) },
                        label = { Text(sort.displayLabel(language)) }
                    )
                }
            }

            if (state.publishers.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedPublisherId == null,
                            onClick = { onSelectPublisher(null) },
                            label = { Text(if (language == AppLanguage.ENGLISH) "All Publishers" else "সব পত্রিকা") }
                        )
                    }
                    items(state.publishers, key = { it.publisherId }) { pub ->
                        FilterChip(
                            selected = state.selectedPublisherId == pub.publisherId,
                            onClick = {
                                onSelectPublisher(if (state.selectedPublisherId == pub.publisherId) null else pub.publisherId)
                            },
                            label = { Text(pub.displayName(language)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (state.articles.isEmpty()) {
                EditorialEmptyState(
                    title = if (language == AppLanguage.ENGLISH) {
                        "No ${state.selectedCategory.titleEn.lowercase()} match current filters"
                    } else {
                        "নির্বাচিত ফিল্টারে কোনো ${state.selectedCategory.titleBn} পাওয়া যায়নি"
                    },
                    subtitle = if (language == AppLanguage.ENGLISH) {
                        "Try clearing language/publisher filters or refreshing from the Home screen."
                    } else {
                        "অন্য পত্রিকা বা ভাষার ফিল্টার নির্বাচন করুন অথবা হোম পাতা থেকে রিফ্রেশ করুন।"
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.articles, key = { it.articleId }) { article ->
                        EditorialArticleCard(
                            article = article,
                            language = language,
                            dataSaver = dataSaver,
                            onClick = { onOpenArticle(article.articleId) },
                            onToggleBookmark = { onToggleBookmark(article.articleId) },
                            onAuthorClick = onOpenAuthor,
                            onOpenOriginalUrl = { url -> ShareAndBrowserHelper.openExternalUrl(context, url) }
                        )
                    }
                }
            }
        }
    }
}
