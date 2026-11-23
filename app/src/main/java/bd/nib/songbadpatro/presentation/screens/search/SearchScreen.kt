package bd.nib.songbadpatro.presentation.screens.search

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.ArticleSortOption
import bd.nib.songbadpatro.domain.model.ContentLanguage
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.Publisher
import bd.nib.songbadpatro.domain.model.SearchFilter
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.PublisherRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.presentation.components.EditorialArticleCard
import bd.nib.songbadpatro.presentation.components.EditorialEmptyState
import bd.nib.songbadpatro.presentation.util.BanglaFormatter
import bd.nib.songbadpatro.presentation.util.ShareAndBrowserHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DateRangePreset(val labelBn: String, val labelEn: String) {
    SINCE_CUTOFF("২৩ নভেম্বর ২০২৬ থেকে সব", "Since 23 Nov 2026"),
    TODAY("আজ প্রকাশিত", "Published Today"),
    LAST_7_DAYS("গত ৭ দিন", "Last 7 Days");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

data class SearchUiState(
    val filter: SearchFilter = SearchFilter(),
    val datePreset: DateRangePreset = DateRangePreset.SINCE_CUTOFF,
    val results: List<EditorialArticle> = emptyList(),
    val publishers: List<Publisher> = emptyList(),
    val preferences: UserPreferences = UserPreferences()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val editorialRepository: EditorialRepository,
    publisherRepository: PublisherRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(SearchFilter())
    private val datePresetFlow = MutableStateFlow(DateRangePreset.SINCE_CUTOFF)

    private val resultsFlow = filterFlow.flatMapLatest { filter ->
        editorialRepository.searchArticles(filter)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        filterFlow,
        datePresetFlow,
        resultsFlow,
        publisherRepository.observePublishers(),
        settingsRepository.preferencesFlow
    ) { filter, preset, results, pubs, prefs ->
        SearchUiState(
            filter = filter,
            datePreset = preset,
            results = results,
            publishers = pubs.filter { it.enabled },
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun updateQuery(query: String) {
        filterFlow.value = filterFlow.value.copy(query = query)
    }

    fun updateAuthorQuery(authorQuery: String) {
        filterFlow.value = filterFlow.value.copy(authorQuery = authorQuery)
    }

    fun toggleCategory(category: EditorialCategory) {
        val next = filterFlow.value.categories.toMutableSet()
        if (!next.add(category)) next.remove(category)
        filterFlow.value = filterFlow.value.copy(categories = next)
    }

    fun selectPublisher(publisherId: String?) {
        filterFlow.value = filterFlow.value.copy(publisherId = publisherId)
    }

    fun selectLanguage(language: ContentLanguage?) {
        filterFlow.value = filterFlow.value.copy(language = language)
    }

    fun selectSort(sortOption: ArticleSortOption) {
        filterFlow.value = filterFlow.value.copy(sortOption = sortOption)
    }

    fun selectDatePreset(preset: DateRangePreset) {
        datePresetFlow.value = preset
        val todayDhaka = LocalDate.now(PublicationCutoffConfig.DHAKA_ZONE)
        val fromEpoch = when (preset) {
            DateRangePreset.SINCE_CUTOFF -> PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS
            DateRangePreset.TODAY -> maxOf(
                PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS,
                todayDhaka.atStartOfDay(PublicationCutoffConfig.DHAKA_ZONE).toInstant().toEpochMilli()
            )
            DateRangePreset.LAST_7_DAYS -> maxOf(
                PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS,
                todayDhaka.minusDays(7).atStartOfDay(PublicationCutoffConfig.DHAKA_ZONE).toInstant().toEpochMilli()
            )
        }
        filterFlow.value = filterFlow.value.copy(fromDateEpochMs = fromEpoch)
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch {
            editorialRepository.toggleBookmark(articleId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onUpdateQuery: (String) -> Unit,
    onUpdateAuthorQuery: (String) -> Unit,
    onToggleCategory: (EditorialCategory) -> Unit,
    onSelectPublisher: (String?) -> Unit,
    onSelectLanguage: (ContentLanguage?) -> Unit,
    onSelectDatePreset: (DateRangePreset) -> Unit,
    onSelectSort: (ArticleSortOption) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onToggleBookmark: (String) -> Unit
) {
    val language = state.preferences.language
    val dataSaver = state.preferences.dataSaverMode
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (language == AppLanguage.ENGLISH) {
                            "Search Commentary & Interviews"
                        } else {
                            "মতামত, কলাম ও সাক্ষাৎকার অনুসন্ধান"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = onUpdateQuery,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.filter.query.isNotBlank()) {
                            IconButton(onClick = { onUpdateQuery("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = null)
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            if (language == AppLanguage.ENGLISH) {
                                "Headline, topic, interviewee..."
                            } else {
                                "শিরোনাম, বিষয়, কীওয়ার্ড..."
                            }
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_query_field")
                )
            }

            OutlinedTextField(
                value = state.filter.authorQuery,
                onValueChange = onUpdateAuthorQuery,
                singleLine = true,
                placeholder = {
                    Text(
                        if (language == AppLanguage.ENGLISH) {
                            "Filter by columnist or interviewee name..."
                        } else {
                            "নির্দিষ্ট কলামিস্ট বা সাক্ষাৎকারদাতার নাম..."
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Category multi-select chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EditorialCategory.entries) { cat ->
                    FilterChip(
                        selected = cat in state.filter.categories,
                        onClick = { onToggleCategory(cat) },
                        label = { Text(cat.displayTitle(language)) }
                    )
                }
                items(DateRangePreset.entries) { preset ->
                    FilterChip(
                        selected = state.datePreset == preset,
                        onClick = { onSelectDatePreset(preset) },
                        label = { Text(preset.displayLabel(language)) }
                    )
                }
            }

            // Publisher, Language & Sort chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ArticleSortOption.entries) { sort ->
                    FilterChip(
                        selected = state.filter.sortOption == sort,
                        onClick = { onSelectSort(sort) },
                        label = { Text(sort.displayLabel(language)) }
                    )
                }
                items(ContentLanguage.entries) { lang ->
                    FilterChip(
                        selected = state.filter.language == lang,
                        onClick = { onSelectLanguage(if (state.filter.language == lang) null else lang) },
                        label = { Text(lang.displayLabel(language)) }
                    )
                }
                items(state.publishers, key = { it.publisherId }) { pub ->
                    FilterChip(
                        selected = state.filter.publisherId == pub.publisherId,
                        onClick = {
                            onSelectPublisher(if (state.filter.publisherId == pub.publisherId) null else pub.publisherId)
                        },
                        label = { Text(pub.displayName(language)) }
                    )
                }
            }

            Text(
                text = if (language == AppLanguage.ENGLISH) {
                    "${state.results.size} verified results (published >= ${state.preferences.publicationCutoffIsoDate})"
                } else {
                    "ফলাফল: ${BanglaFormatter.formatCount(state.results.size, language)}টি যাচাইকৃত লেখা (${BanglaFormatter.toBanglaDigits(state.preferences.publicationCutoffIsoDate)} থেকে)"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            if (state.results.isEmpty()) {
                EditorialEmptyState(
                    title = if (language == AppLanguage.ENGLISH) "No matching editorial articles" else "মিলে যাওয়া কোনো লেখা পাওয়া যায়নি",
                    subtitle = if (language == AppLanguage.ENGLISH) {
                        "All search results strictly enforce the ${state.preferences.publicationCutoffIsoDate} cutoff and exclude routine general news."
                    } else {
                        "সকল অনুসন্ধান ২৩ নভেম্বর ২০২৬ প্রকাশের সীমা মেনে চলে এবং সাধারণ সংবাদ বাদ দেয়।"
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.results, key = { it.articleId }) { article ->
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
