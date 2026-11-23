package bd.nib.songbadpatro.presentation.screens.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.FeedHealthStatus
import bd.nib.songbadpatro.domain.model.Publisher
import bd.nib.songbadpatro.domain.model.SourceVerificationStatus
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.PublisherRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.presentation.components.PublisherMonogram
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

data class SourcesUiState(
    val publishers: List<Publisher> = emptyList(),
    val syncingPublisherId: String? = null,
    val preferences: UserPreferences = UserPreferences()
)

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val publisherRepository: PublisherRepository,
    private val editorialRepository: EditorialRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val syncingPublisherId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SourcesUiState> = combine(
        publisherRepository.observePublishers(),
        syncingPublisherId,
        settingsRepository.preferencesFlow
    ) { pubs, syncingId, prefs ->
        SourcesUiState(
            publishers = pubs,
            syncingPublisherId = syncingId,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SourcesUiState()
    )

    fun setPublisherEnabled(publisherId: String, enabled: Boolean) {
        viewModelScope.launch {
            publisherRepository.setPublisherEnabled(publisherId, enabled)
        }
    }

    fun syncPublisherNow(publisherId: String) {
        if (syncingPublisherId.value != null) return
        viewModelScope.launch {
            syncingPublisherId.value = publisherId
            runCatching { editorialRepository.syncPublisher(publisherId) }
            syncingPublisherId.value = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SourcesScreen(
    state: SourcesUiState,
    onBack: () -> Unit,
    onTogglePublisher: (String, Boolean) -> Unit,
    onSyncPublisher: (String) -> Unit
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
                            "Publishers & Source Registry"
                        } else {
                            "পত্রিকা ও উৎস ব্যবস্থাপনা"
                        },
                        style = MaterialTheme.typography.titleLarge,
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
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "registry_intro") {
                Text(
                    text = if (language == AppLanguage.ENGLISH) {
                        "Only live-verified Bangladeshi publishers permitting RSS or public section collection are enabled. Sources returning HTTP 403 or TLS errors remain disabled until verified."
                    } else {
                        "কেবলমাত্র যাচাইকৃত ও অনুমোদিত বাংলাদেশি পত্রিকার সম্পাদকীয় ও মতামত উৎসগুলো সক্রিয় রাখা হয়েছে। যেসব ওয়েবসাইটে স্বয়ংক্রিয় অ্যাক্সেস সীমাবদ্ধ (যেমন HTTP 403), সেগুলো স্বচ্ছতার সাথে নিষ্ক্রিয় রাখা হয়েছে।"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(state.publishers, key = { it.publisherId }) { pub ->
                val isVerified = pub.verificationStatus == SourceVerificationStatus.VERIFIED
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PublisherMonogram(
                                name = pub.displayName(language),
                                logoUrl = pub.logoUrl,
                                dataSaver = dataSaver,
                                sizeDp = 38
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pub.displayName(language),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = pub.website,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Switch(
                                checked = pub.enabled,
                                enabled = isVerified,
                                onCheckedChange = { checked -> onTogglePublisher(pub.publisherId, checked) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPill(
                                text = pub.verificationStatus.displayLabel(language),
                                isPositive = isVerified
                            )
                            StatusPill(
                                text = pub.healthStatus.displayLabel(language),
                                isPositive = pub.healthStatus == FeedHealthStatus.HEALTHY
                            )
                            pub.collectionMethods.forEach { method ->
                                StatusPill(
                                    text = method.displayLabel(language),
                                    isPositive = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (language == AppLanguage.ENGLISH) {
                                "Categories: " + pub.supportedCategories.joinToString(", ") { it.titleEn }
                            } else {
                                "সমর্থিত বিভাগ: " + pub.supportedCategories.joinToString(" • ") { it.titleBn }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val syncTimeText = pub.lastSuccessfulSync?.let {
                            BanglaFormatter.formatDhakaDate(it, language, includeTime = true)
                        } ?: if (language == AppLanguage.ENGLISH) "Not synced yet" else "এখনও সিঙ্ক হয়নি"

                        Text(
                            text = if (language == AppLanguage.ENGLISH) {
                                "Last successful sync: $syncTimeText • Collected: ${pub.collectedArticleCount}"
                            } else {
                                "সর্বশেষ সফল সিঙ্ক: $syncTimeText • সংগৃহীত লেখা: ${BanglaFormatter.formatCount(pub.collectedArticleCount, language)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!pub.lastError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pub.lastError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { ShareAndBrowserHelper.openExternalUrl(context, pub.website) }
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (language == AppLanguage.ENGLISH) "Website" else "ওয়েবসাইট")
                            }
                            if (isVerified && pub.enabled) {
                                OutlinedButton(
                                    onClick = { onSyncPublisher(pub.publisherId) },
                                    enabled = state.syncingPublisherId == null
                                ) {
                                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (state.syncingPublisherId == pub.publisherId) {
                                            if (language == AppLanguage.ENGLISH) "Syncing..." else "সিঙ্ক হচ্ছে..."
                                        } else {
                                            if (language == AppLanguage.ENGLISH) "Sync Now" else "এখনই সিঙ্ক"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, isPositive: Boolean) {
    Surface(
        color = if (isPositive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isPositive) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
