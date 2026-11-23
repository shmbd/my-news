package bd.nib.songbadpatro.presentation.screens.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bd.nib.songbadpatro.BuildConfig
import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.ArticleVerificationStatus
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.SyncLogEntry
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.PublisherRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.presentation.util.BanglaFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminMaintenanceUiState(
    val preferences: UserPreferences = UserPreferences(),
    val pendingArticles: List<EditorialArticle> = emptyList(),
    val recentSyncLogs: List<SyncLogEntry> = emptyList(),
    val feedbackMessage: String? = null
)

@HiltViewModel
class AdminMaintenanceViewModel @Inject constructor(
    private val editorialRepository: EditorialRepository,
    publisherRepository: PublisherRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AdminMaintenanceUiState> = combine(
        settingsRepository.preferencesFlow,
        editorialRepository.observePendingVerificationArticles(50),
        publisherRepository.observeRecentSyncLogs(30),
        feedback
    ) { prefs, pending, logs, msg ->
        AdminMaintenanceUiState(
            preferences = prefs,
            pendingArticles = pending,
            recentSyncLogs = logs,
            feedbackMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdminMaintenanceUiState()
    )

    fun updateCutoffIsoDate(newIso: String) {
        val trimmed = newIso.trim()
        if (!PublicationCutoffConfig.isValidIsoCutoffDate(trimmed)) {
            feedback.value = "অবৈধ তারিখ বিন্যাস। অনুগ্রহ করে YYYY-MM-DD (যেমন 2026-11-23) ব্যবহার করুন।"
            return
        }
        val requestedEpoch = PublicationCutoffConfig.cutoffEpochMillisFor(trimmed)
        if (requestedEpoch < PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS) {
            feedback.value = "২৩ নভেম্বর ২০২৬ (2026-11-23)-এর আগের কোনো তারিখ গ্রহণযোগ্য নয়।"
            return
        }
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(publicationCutoffIsoDate = trimmed) }
            feedback.value = "সক্রিয় প্রকাশের কাটঅফ তারিখ হালনাগাদ হয়েছে: $trimmed (Asia/Dhaka)"
        }
    }

    fun resetDefaultCutoff() {
        updateCutoffIsoDate(PublicationCutoffConfig.DEFAULT_CUTOFF_ISO_DATE)
    }

    fun approvePending(articleId: String, category: EditorialCategory) {
        viewModelScope.launch {
            editorialRepository.approvePendingArticle(articleId, category)
        }
    }

    fun rejectPending(articleId: String) {
        viewModelScope.launch {
            editorialRepository.rejectPendingArticle(articleId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMaintenanceScreen(
    state: AdminMaintenanceUiState,
    onBack: () -> Unit,
    onUpdateCutoff: (String) -> Unit,
    onResetCutoff: () -> Unit,
    onApprovePending: (String, EditorialCategory) -> Unit,
    onRejectPending: (String) -> Unit
) {
    val lang = state.preferences.language
    var cutoffInput by remember(state.preferences.publicationCutoffIsoDate) {
        mutableStateOf(state.preferences.publicationCutoffIsoDate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) {
                            "Verification Queue & Sync Diagnostics"
                        } else {
                            "যাচাই কিউ, কাটঅফ ও সিঙ্ক লগ"
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
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "cutoff_admin_card") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (lang == AppLanguage.ENGLISH) {
                                "Centralized Publication Cutoff (Asia/Dhaka)"
                            } else {
                                "কেন্দ্রীয় প্রকাশের তারিখ সীমা (Asia/Dhaka)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = cutoffInput,
                            onValueChange = { cutoffInput = it },
                            singleLine = true,
                            label = { Text("ISO Date (YYYY-MM-DD, >= 2026-11-23)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        state.feedbackMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onUpdateCutoff(cutoffInput) }) {
                                Text(if (lang == AppLanguage.ENGLISH) "Apply Cutoff" else "সংরক্ষণ করুন")
                            }
                            OutlinedButton(onClick = onResetCutoff) {
                                Text("2026-11-23")
                            }
                        }
                    }
                }
            }

            item(key = "pending_header") {
                Text(
                    text = if (lang == AppLanguage.ENGLISH) {
                        "Pending Verification Queue (${state.pendingArticles.size})"
                    } else {
                        "যাচাই অপেক্ষমাণ নিবন্ধ (${BanglaFormatter.formatCount(state.pendingArticles.size, lang)})"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.pendingArticles.isEmpty()) {
                item(key = "pending_empty") {
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) {
                            "No uncertain articles in the pending queue."
                        } else {
                            "বর্তমানে যাচাই অপেক্ষমাণ তালিকায় কোনো অনিশ্চিত নিবন্ধ নেই।"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.pendingArticles, key = { it.articleId }) { pending ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = pending.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${pending.displayPublisherName(lang)} • ${pending.verificationStatus.displayLabel(lang)} • Confidence: ${pending.classificationConfidence}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = pending.classificationReason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (pending.verificationStatus == ArticleVerificationStatus.PENDING_VERIFICATION &&
                                    pending.originalPublicationDate >= PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS
                                ) {
                                    Button(onClick = { onApprovePending(pending.articleId, pending.category) }) {
                                        Text(if (lang == AppLanguage.ENGLISH) "Approve" else "অনুমোদন")
                                    }
                                }
                                OutlinedButton(onClick = { onRejectPending(pending.articleId) }) {
                                    Text(if (lang == AppLanguage.ENGLISH) "Reject" else "বাতিল")
                                }
                            }
                        }
                    }
                }
            }

            item(key = "logs_header") {
                Text(
                    text = if (lang == AppLanguage.ENGLISH) "Recent Synchronization Logs" else "সাম্প্রতিক সিঙ্ক লগ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.recentSyncLogs, key = { it.logId }) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${log.sourceId} • ${if (log.success) "OK" else "ERROR"}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Fetched=${log.fetchedCount}, Published=${log.acceptedCount}, Pending=${log.pendingCount}, PreCutoffRejected=${log.rejectedPreCutoffCount}, NonEditorialRejected=${log.rejectedNonEditorialCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        log.errorMessage?.let { err ->
                            Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

enum class PolicyScreenType {
    PRIVACY,
    CONTENT_COLLECTION,
    ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyAndAboutScreen(
    type: PolicyScreenType,
    language: AppLanguage,
    onBack: () -> Unit
) {
    val (title, paragraphs) = when (type) {
        PolicyScreenType.PRIVACY -> {
            (if (language == AppLanguage.ENGLISH) "Privacy Policy" else "গোপনীয়তা নীতিমালা") to listOf(
                if (language == AppLanguage.ENGLISH) {
                    "My News (আমার খবর) is built with privacy by default: zero advertising SDKs, zero third-party analytics trackers, and zero user profiling."
                } else {
                    "আমার খবর (My News) সম্পূর্ণ বিজ্ঞাপনমুক্ত এবং ট্র্যাকারমুক্ত। এতে কোনো বিজ্ঞাপন SDK, তৃতীয় পক্ষের অ্যানালিটিক্স বা ব্যক্তিগত তথ্য ট্র্যাকিং ব্যবহৃত হয়নি।"
                },
                if (language == AppLanguage.ENGLISH) {
                    "Bookmarks, reading history, preferred authors, and keyword alert rules are stored strictly on your device using Android Room and DataStore."
                } else {
                    "আপনার সংরক্ষিত লেখা (বুকমার্ক), পঠিত তালিকা, পছন্দের লেখক এবং কীওয়ার্ড অ্যালার্ট কেবল আপনার ফোনের স্থানীয় ডেটাবেসে সংরক্ষিত থাকে।"
                },
                if (language == AppLanguage.ENGLISH) {
                    "Network connections exclusively fetch verified public HTTPS feeds and permitted editorial sections from Bangladeshi publishers."
                } else {
                    "ইন্টারনেট সংযোগ শুধুমাত্র বাংলাদেশি সংবাদপত্রের যাচাইকৃত HTTPS ফিড ও অনুমোদিত মতামত পাতা থেকে তথ্য আনার জন্য ব্যবহৃত হয়।"
                }
            )
        }
        PolicyScreenType.CONTENT_COLLECTION -> {
            (if (language == AppLanguage.ENGLISH) "Content Collection & Copyright Policy" else "কনটেন্ট সংগ্রহ ও কপিরাইট নীতিমালা") to listOf(
                if (language == AppLanguage.ENGLISH) {
                    "1. Exclusive Editorial Scope: My News collects ONLY Columns (কলাম), Official Editorials (সম্পাদকীয়), Opinions (মতামত), and Interviews (সাক্ষাৎকার) from Bangladeshi media. Ordinary news, breaking news, crime, sports, entertainment, and press releases are strictly excluded."
                } else {
                    "১. একচেটিয়া সম্পাদকীয় পরিধি: 'আমার খবর' শুধুমাত্র বাংলাদেশি গণমাধ্যমে প্রকাশিত কলাম, সম্পাদকীয়, মতামত এবং সাক্ষাৎকার সংকলন করে। সাধারণ সংবাদ, ব্রেকিং নিউজ, অপরাধ, খেলাধুলা, বিনোদন বা প্রেস বিজ্ঞপ্তি গ্রহণ করা হয় না।"
                },
                if (language == AppLanguage.ENGLISH) {
                    "2. Strict Publication Cutoff (23 November 2026): Only articles originally published on or after 2026-11-23 (Asia/Dhaka) are eligible. Pre-cutoff articles and updated older articles are excluded."
                } else {
                    "২. কঠোর প্রকাশের সীমা (২৩ নভেম্বর ২০২৬): কেবল ২৩ নভেম্বর ২০২৬ বা তার পরে (বাংলাদেশ সময় অনুযায়ী) মূল প্রকাশক কর্তৃক প্রকাশিত লেখাই অন্তর্ভুক্ত হয়।"
                },
                if (language == AppLanguage.ENGLISH) {
                    "3. Copyright & Attribution: My News operates as an ethical discovery index. It respects robots.txt, rate limits, and paywalls, never reproduces complete copyrighted articles without permission, preserves full author and publisher attribution, and directs readers to the original publisher's website."
                } else {
                    "৩. কপিরাইট ও কৃতজ্ঞতা স্বীকার: অ্যাপটি প্রকাশকদের robots.txt ও অ্যাক্সেস নীতিমালা কঠোরভাবে মেনে চলে, কখনোই পে-ওয়াল অতিক্রম করে না এবং সম্পূর্ণ কপিরাইটযুক্ত নিবন্ধ পুনঃপ্রকাশ না করে লেখক ও পত্রিকার পূর্ণ কৃতজ্ঞতাসহ মূল ওয়েবসাইটের সরাসরি লিংক প্রদান করে।"
                }
            )
        }
        PolicyScreenType.ABOUT -> {
            (if (language == AppLanguage.ENGLISH) "About My News (আমার খবর)" else "আমার খবর (My News) সম্পর্কে") to listOf(
                if (language == AppLanguage.ENGLISH) {
                    "My News (আমার খবর) — Version ${BuildConfig.VERSION_NAME} (Package: ${BuildConfig.APPLICATION_ID})"
                } else {
                    "আমার খবর (My News) — সংস্করণ ${BuildConfig.VERSION_NAME} (প্যাকেজ: ${BuildConfig.APPLICATION_ID})"
                },
                if (language == AppLanguage.ENGLISH) {
                    "A dedicated Bangladeshi platform for readers, researchers, journalists, academics, policymakers, and students to discover columns, institutional editorials, opinion journalism, and in-depth interviews."
                } else {
                    "পাঠক, গবেষক, সাংবাদিক, শিক্ষাবিদ, নীতিনির্ধারক ও শিক্ষার্থীদের জন্য বাংলাদেশের সংবাদপত্র ও ডিজিটাল মাধ্যমের বুদ্ধিবৃত্তিক কলাম, সম্পাদকীয় বিশ্লেষণ, মতামত ও সাক্ষাৎকার আবিষ্কারের একটি কেন্দ্রীভূত প্ল্যাটফর্ম।"
                },
                if (language == AppLanguage.ENGLISH) {
                    "Collection Period: On or after ${BuildConfig.DEFAULT_CUTOFF_DATE} (${BuildConfig.DEFAULT_TIMEZONE}) • Ads Disabled: ${!BuildConfig.ADS_ENABLED}"
                } else {
                    "সংগ্রহের সময়কাল: ২৩ নভেম্বর ২০২৬ থেকে চলমান (Asia/Dhaka) • বিজ্ঞাপনমুক্ত সংস্করণ।"
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(paragraphs) { p ->
                Text(text = p, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
