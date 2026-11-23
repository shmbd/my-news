package bd.nib.songbadpatro.presentation.screens.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.presentation.components.EditorialArticleCard
import bd.nib.songbadpatro.presentation.components.EditorialEmptyState
import bd.nib.songbadpatro.presentation.util.BanglaFormatter
import bd.nib.songbadpatro.presentation.util.ShareAndBrowserHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SavedTab {
    BOOKMARKS,
    HISTORY
}

data class SavedUiState(
    val selectedTab: SavedTab = SavedTab.BOOKMARKS,
    val bookmarkedArticles: List<EditorialArticle> = emptyList(),
    val readingHistory: List<EditorialArticle> = emptyList(),
    val preferences: UserPreferences = UserPreferences()
)

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val editorialRepository: EditorialRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val selectedTab = MutableStateFlow(SavedTab.BOOKMARKS)

    val uiState: StateFlow<SavedUiState> = combine(
        selectedTab,
        editorialRepository.observeBookmarkedArticles(),
        editorialRepository.observeReadingHistory(100),
        settingsRepository.preferencesFlow
    ) { tab, saved, history, prefs ->
        SavedUiState(
            selectedTab = tab,
            bookmarkedArticles = saved,
            readingHistory = history,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SavedUiState()
    )

    fun selectTab(tab: SavedTab) {
        selectedTab.value = tab
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch {
            editorialRepository.toggleBookmark(articleId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            editorialRepository.clearReadingHistory()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    state: SavedUiState,
    onSelectTab: (SavedTab) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onClearHistory: () -> Unit
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
                            "Saved & Reading History"
                        } else {
                            "সংরক্ষিত লেখা ও পঠিত ইতিহাস"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (state.selectedTab == SavedTab.HISTORY && state.readingHistory.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = if (language == AppLanguage.ENGLISH) "Clear history" else "ইতিহাস মুছুন"
                            )
                        }
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
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == SavedTab.BOOKMARKS,
                    onClick = { onSelectTab(SavedTab.BOOKMARKS) },
                    text = {
                        val c = BanglaFormatter.formatCount(state.bookmarkedArticles.size, language)
                        Text(
                            if (language == AppLanguage.ENGLISH) "Bookmarks ($c)" else "সংরক্ষিত ($c)"
                        )
                    }
                )
                Tab(
                    selected = state.selectedTab == SavedTab.HISTORY,
                    onClick = { onSelectTab(SavedTab.HISTORY) },
                    text = {
                        val c = BanglaFormatter.formatCount(state.readingHistory.size, language)
                        Text(
                            if (language == AppLanguage.ENGLISH) "History ($c)" else "পঠিত তালিকা ($c)"
                        )
                    }
                )
            }

            val currentList = if (state.selectedTab == SavedTab.BOOKMARKS) {
                state.bookmarkedArticles
            } else {
                state.readingHistory
            }

            if (currentList.isEmpty()) {
                EditorialEmptyState(
                    title = if (state.selectedTab == SavedTab.BOOKMARKS) {
                        if (language == AppLanguage.ENGLISH) "No saved articles yet" else "এখনও কোনো লেখা সংরক্ষিত নেই"
                    } else {
                        if (language == AppLanguage.ENGLISH) "Reading history is empty" else "পঠিত তালিকায় কোনো লেখা নেই"
                    },
                    subtitle = if (state.selectedTab == SavedTab.BOOKMARKS) {
                        if (language == AppLanguage.ENGLISH) {
                            "Tap the bookmark icon on any column, editorial, opinion, or interview to read later offline."
                        } else {
                            "যেকোনো কলাম, সম্পাদকীয়, মতামত বা সাক্ষাৎকারের বুকমার্ক আইকনে ট্যাপ করে অফলাইনে পড়ার জন্য সংরক্ষণ করুন।"
                        }
                    } else {
                        if (language == AppLanguage.ENGLISH) {
                            "Articles you open in Reader Mode will appear here."
                        } else {
                            "পাঠক মোডে পড়া লেখাগুলো স্বয়ংক্রিয়ভাবে এখানে সংরক্ষিত থাকবে।"
                        }
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentList, key = { "${state.selectedTab.name}_${it.articleId}" }) { article ->
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
