package bd.nib.songbadpatro.presentation.screens.authors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.AuthorProfile
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.PersonRoleType
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.AuthorRepository
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.presentation.components.EditorialArticleCard
import bd.nib.songbadpatro.presentation.components.EditorialEmptyState
import bd.nib.songbadpatro.presentation.util.BanglaFormatter
import bd.nib.songbadpatro.presentation.util.ShareAndBrowserHelper
import coil.compose.AsyncImage
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

data class AuthorsUiState(
    val selectedRole: PersonRoleType = PersonRoleType.AUTHOR,
    val searchQuery: String = "",
    val profiles: List<AuthorProfile> = emptyList(),
    val preferences: UserPreferences = UserPreferences()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AuthorsViewModel @Inject constructor(
    authorRepository: AuthorRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val selectedRole = MutableStateFlow(PersonRoleType.AUTHOR)
    private val searchQuery = MutableStateFlow("")

    private val profilesFlow = combine(selectedRole, searchQuery) { role, q -> role to q }
        .flatMapLatest { (role, q) ->
            authorRepository.observeAuthors(roleFilter = role, searchQuery = q)
        }

    val uiState: StateFlow<AuthorsUiState> = combine(
        selectedRole,
        searchQuery,
        profilesFlow,
        settingsRepository.preferencesFlow
    ) { role, q, list, prefs ->
        AuthorsUiState(
            selectedRole = role,
            searchQuery = q,
            profiles = list,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthorsUiState()
    )

    fun selectRole(role: PersonRoleType) {
        selectedRole.value = role
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }
}

data class AuthorProfileUiState(
    val profile: AuthorProfile? = null,
    val articles: List<EditorialArticle> = emptyList(),
    val isPreferredForAlerts: Boolean = false,
    val preferences: UserPreferences = UserPreferences()
)

@HiltViewModel
class AuthorProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authorRepository: AuthorRepository,
    private val editorialRepository: EditorialRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val authorId: String = savedStateHandle["authorId"] ?: ""

    val uiState: StateFlow<AuthorProfileUiState> = combine(
        authorRepository.observeAuthorById(authorId),
        authorRepository.observeArticlesForPerson(authorId),
        settingsRepository.preferencesFlow
    ) { profile, articles, prefs ->
        val preferred = profile?.name != null && prefs.preferredAuthorNames.any {
            it.equals(profile.name, ignoreCase = true)
        }
        AuthorProfileUiState(
            profile = profile,
            articles = articles,
            isPreferredForAlerts = preferred,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthorProfileUiState()
    )

    fun togglePreferredAuthorAlert() {
        val name = uiState.value.profile?.name ?: return
        viewModelScope.launch {
            settingsRepository.updatePreferences { current ->
                val mutable = current.preferredAuthorNames.toMutableSet()
                val existing = mutable.firstOrNull { it.equals(name, ignoreCase = true) }
                if (existing != null) mutable.remove(existing) else mutable.add(name)
                current.copy(preferredAuthorNames = mutable)
            }
        }
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch {
            editorialRepository.toggleBookmark(articleId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorsScreen(
    state: AuthorsUiState,
    onSelectRole: (PersonRoleType) -> Unit,
    onUpdateQuery: (String) -> Unit,
    onOpenAuthorProfile: (String) -> Unit
) {
    val language = state.preferences.language
    val dataSaver = state.preferences.dataSaverMode

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (language == AppLanguage.ENGLISH) {
                            "Columnists & Interviewees"
                        } else {
                            "কলামিস্ট, লেখক ও সাক্ষাৎকারদাতা"
                        },
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
            TabRow(
                selectedTabIndex = if (state.selectedRole == PersonRoleType.AUTHOR) 0 else 1
            ) {
                Tab(
                    selected = state.selectedRole == PersonRoleType.AUTHOR,
                    onClick = { onSelectRole(PersonRoleType.AUTHOR) },
                    text = {
                        Text(if (language == AppLanguage.ENGLISH) "Columnists & Authors" else "কলামিস্ট ও লেখক")
                    }
                )
                Tab(
                    selected = state.selectedRole == PersonRoleType.INTERVIEWEE,
                    onClick = { onSelectRole(PersonRoleType.INTERVIEWEE) },
                    text = {
                        Text(if (language == AppLanguage.ENGLISH) "Interviewees" else "সাক্ষাৎকারদাতা")
                    }
                )
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onUpdateQuery,
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onUpdateQuery("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = null)
                        }
                    }
                },
                placeholder = {
                    Text(
                        if (language == AppLanguage.ENGLISH) {
                            "Search columnist, scholar, or affiliation..."
                        } else {
                            "লেখক, গবেষক বা প্রতিষ্ঠানের নাম খুঁজুন..."
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("author_search_input")
            )

            if (state.profiles.isEmpty()) {
                EditorialEmptyState(
                    title = if (language == AppLanguage.ENGLISH) "No contributors found" else "কোনো লেখক বা সাক্ষাৎকারদাতা পাওয়া যায়নি",
                    subtitle = if (language == AppLanguage.ENGLISH) {
                        "Contributors appear automatically as verified columns, opinions, and interviews are indexed."
                    } else {
                        "যাচাইকৃত কলাম, মতামত ও সাক্ষাৎকার সংগৃহীত হলে লেখক ও সাক্ষাৎকারদাতাদের তালিকা স্বয়ংক্রিয়ভাবে তৈরি হয়।"
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.profiles, key = { it.authorId }) { person ->
                        AuthorDirectoryCard(
                            person = person,
                            language = language,
                            dataSaver = dataSaver,
                            onClick = { onOpenAuthorProfile(person.authorId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorDirectoryCard(
    person: AuthorProfile,
    language: AppLanguage,
    dataSaver: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            PersonAvatar(
                name = person.name,
                photoUrl = person.photograph,
                dataSaver = dataSaver,
                sizeDp = 48
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val subtitleParts = listOfNotNull(
                    person.designation?.takeIf { it.isNotBlank() },
                    person.affiliation?.takeIf { it.isNotBlank() && it != person.designation },
                    person.displayPublisher(language)
                )
                if (subtitleParts.isNotEmpty()) {
                    Text(
                        text = subtitleParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (language == AppLanguage.ENGLISH) {
                    "${person.articleCount} articles"
                } else {
                    "${BanglaFormatter.formatCount(person.articleCount, language)}টি লেখা"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorProfileScreen(
    state: AuthorProfileUiState,
    onBack: () -> Unit,
    onTogglePreferredAlert: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onToggleBookmark: (String) -> Unit
) {
    val language = state.preferences.language
    val dataSaver = state.preferences.dataSaverMode
    val context = LocalContext.current
    val profile = state.profile

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = profile?.name ?: if (language == AppLanguage.ENGLISH) "Author Profile" else "লেখক পরিচিতি",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (profile != null) {
                item(key = "author_header") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PersonAvatar(
                                    name = profile.name,
                                    photoUrl = profile.photograph,
                                    dataSaver = dataSaver,
                                    sizeDp = 64
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    profile.designation?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    profile.affiliation?.takeIf { it.isNotBlank() && it != profile.designation }?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    profile.displayPublisher(language)?.let { pubName ->
                                        Text(
                                            text = pubName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (language == AppLanguage.ENGLISH) {
                                        "Published in aggregator: ${profile.articleCount}"
                                    } else {
                                        "সংগৃহীত প্রকাশিত লেখা: ${BanglaFormatter.formatCount(profile.articleCount, language)}টি"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                                FilterChip(
                                    selected = state.isPreferredForAlerts,
                                    onClick = onTogglePreferredAlert,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (state.isPreferredForAlerts) {
                                                Icons.Filled.NotificationsActive
                                            } else {
                                                Icons.Outlined.NotificationsNone
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            if (language == AppLanguage.ENGLISH) {
                                                if (state.isPreferredForAlerts) "Following Alerts" else "Follow Alerts"
                                            } else {
                                                if (state.isPreferredForAlerts) "বিজ্ঞপ্তি চালু" else "লেখকের অ্যালার্ট"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            items(state.articles, key = { it.articleId }) { article ->
                EditorialArticleCard(
                    article = article,
                    language = language,
                    dataSaver = dataSaver,
                    onClick = { onOpenArticle(article.articleId) },
                    onToggleBookmark = { onToggleBookmark(article.articleId) },
                    onOpenOriginalUrl = { url -> ShareAndBrowserHelper.openExternalUrl(context, url) }
                )
            }
        }
    }
}

@Composable
private fun PersonAvatar(
    name: String,
    photoUrl: String?,
    dataSaver: Boolean,
    sizeDp: Int
) {
    if (!photoUrl.isNullOrBlank() && !dataSaver) {
        AsyncImage(
            model = photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        val initial = name.trim().firstOrNull()?.toString() ?: "ল"
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
