package bd.nib.songbadpatro.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.Publisher
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.PublisherRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.presentation.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val syncStatusMessage: String? = null,
    val featuredCommentary: List<EditorialArticle> = emptyList(),
    val latestColumns: List<EditorialArticle> = emptyList(),
    val latestEditorials: List<EditorialArticle> = emptyList(),
    val latestOpinions: List<EditorialArticle> = emptyList(),
    val latestInterviews: List<EditorialArticle> = emptyList(),
    val recentlyPublished: List<EditorialArticle> = emptyList(),
    val mostReadArticles: List<EditorialArticle> = emptyList(),
    val recentlyAddedPublishers: List<Publisher> = emptyList(),
    val preferences: UserPreferences = UserPreferences()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val editorialRepository: EditorialRepository,
    private val publisherRepository: PublisherRepository,
    settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val syncMessage = MutableStateFlow<String?>(null)

    private val categoryStreams = combine(
        editorialRepository.observeByCategory(EditorialCategory.COLUMN, 12),
        editorialRepository.observeByCategory(EditorialCategory.EDITORIAL, 12),
        editorialRepository.observeByCategory(EditorialCategory.OPINION, 12),
        editorialRepository.observeByCategory(EditorialCategory.INTERVIEW, 12)
    ) { cols, eds, ops, ints ->
        CategoryBuckets(cols, eds, ops, ints)
    }

    private val discoveryStreams = combine(
        editorialRepository.observeFeaturedCommentary(6),
        editorialRepository.observeRecentPublished(30),
        editorialRepository.observeMostReadIfAvailable(8),
        publisherRepository.observeRecentlyAddedPublishers(12)
    ) { featured, recent, mostRead, publishers ->
        DiscoveryBuckets(featured, recent, mostRead, publishers)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        categoryStreams,
        discoveryStreams,
        settingsRepository.preferencesFlow,
        networkMonitor.isOnlineFlow,
        combine(isRefreshing, syncMessage) { r, m -> r to m }
    ) { cats, disc, prefs, online, (refreshing, msg) ->
        HomeUiState(
            isRefreshing = refreshing,
            isOnline = online,
            syncStatusMessage = msg,
            featuredCommentary = disc.featured,
            latestColumns = cats.columns,
            latestEditorials = cats.editorials,
            latestOpinions = cats.opinions,
            latestInterviews = cats.interviews,
            recentlyPublished = disc.recent,
            mostReadArticles = disc.mostRead,
            recentlyAddedPublishers = disc.publishers,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isRefreshing = true)
    )

    init {
        viewModelScope.launch {
            publisherRepository.ensureRegistrySeeded()
            refreshFeed()
        }
    }

    fun refreshFeed() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            syncMessage.value = null
            runCatching { editorialRepository.syncAllEnabledSources() }
                .onSuccess { summary ->
                    syncMessage.value = if (summary.succeededSources == 0 && summary.totalSourcesAttempted > 0) {
                        "প্রকাশকের সার্ভার থেকে নতুন লেখা আনা যায়নি। সংরক্ষিত লেখা দেখানো হচ্ছে।"
                    } else if (summary.newlyPublishedCount > 0) {
                        "${summary.newlyPublishedCount}টি নতুন সম্পাদকীয় ও মতামত সংযোজিত হয়েছে।"
                    } else {
                        null
                    }
                }
                .onFailure {
                    syncMessage.value = "সিঙ্ক করার সময় সমস্যা হয়েছে।"
                }
            isRefreshing.value = false
        }
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch {
            editorialRepository.toggleBookmark(articleId)
        }
    }

    fun dismissSyncMessage() {
        syncMessage.value = null
    }

    private data class CategoryBuckets(
        val columns: List<EditorialArticle>,
        val editorials: List<EditorialArticle>,
        val opinions: List<EditorialArticle>,
        val interviews: List<EditorialArticle>
    )

    private data class DiscoveryBuckets(
        val featured: List<EditorialArticle>,
        val recent: List<EditorialArticle>,
        val mostRead: List<EditorialArticle>,
        val publishers: List<Publisher>
    )
}
