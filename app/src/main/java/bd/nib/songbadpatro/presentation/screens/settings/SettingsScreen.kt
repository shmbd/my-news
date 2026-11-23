package bd.nib.songbadpatro.presentation.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bd.nib.songbadpatro.data.worker.EditorialAlertNotifier
import bd.nib.songbadpatro.data.worker.SyncWorkScheduler
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.AppThemeMode
import bd.nib.songbadpatro.domain.model.CacheStatistics
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.KeywordAlertRule
import bd.nib.songbadpatro.domain.model.NotificationFrequency
import bd.nib.songbadpatro.domain.model.Publisher
import bd.nib.songbadpatro.domain.model.SyncFrequency
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.AlertRepository
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

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val keywordAlerts: List<KeywordAlertRule> = emptyList(),
    val publishers: List<Publisher> = emptyList(),
    val cacheStatistics: CacheStatistics = CacheStatistics(0, 0, 0, 0, 0, 0),
    val hasNotificationPermission: Boolean = true,
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val editorialRepository: EditorialRepository,
    private val alertRepository: AlertRepository,
    publisherRepository: PublisherRepository,
    private val workScheduler: SyncWorkScheduler,
    private val notifier: EditorialAlertNotifier
) : ViewModel() {

    private val cacheStats = MutableStateFlow(CacheStatistics(0, 0, 0, 0, 0, 0))
    private val statusMsg = MutableStateFlow<String?>(null)
    private val notifPerm = MutableStateFlow(notifier.hasNotificationPermission())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.preferencesFlow,
        alertRepository.observeKeywordAlerts(),
        publisherRepository.observePublishers(),
        cacheStats,
        combine(notifPerm, statusMsg) { p, m -> p to m }
    ) { prefs, alerts, pubs, stats, (perm, msg) ->
        SettingsUiState(
            preferences = prefs,
            keywordAlerts = alerts,
            publishers = pubs.filter { it.enabled },
            cacheStatistics = stats,
            hasNotificationPermission = perm,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    init {
        refreshCacheStats()
    }

    fun refreshCacheStats() {
        viewModelScope.launch {
            runCatching { editorialRepository.getCacheStatistics() }
                .onSuccess { cacheStats.value = it }
            notifPerm.value = notifier.hasNotificationPermission()
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(language = language) }
            runCatching {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(themeMode = mode) }
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(readerFontScale = scale) }
        }
    }

    fun setLineSpacing(spacing: Float) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(readerLineSpacing = spacing) }
        }
    }

    fun setSyncFrequency(frequency: SyncFrequency) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(syncFrequency = frequency) }
            val current = settingsRepository.getPreferences()
            workScheduler.schedulePeriodicSync(current.syncFrequency, current.wifiOnlySync)
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(wifiOnlySync = wifiOnly) }
            val current = settingsRepository.getPreferences()
            workScheduler.schedulePeriodicSync(current.syncFrequency, current.wifiOnlySync)
        }
    }

    fun setDataSaver(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(dataSaverMode = enabled) }
        }
    }

    fun toggleEnabledCategory(category: EditorialCategory) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { prefs ->
                val next = prefs.enabledCategories.toMutableSet()
                if (next.size > 1 || category !in next) {
                    if (!next.add(category)) next.remove(category)
                }
                prefs.copy(enabledCategories = next)
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(notificationsEnabled = enabled) }
            notifPerm.value = notifier.hasNotificationPermission()
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        notifPerm.value = granted
        if (granted) {
            setNotificationsEnabled(true)
        }
    }

    fun toggleNotifyCategory(category: EditorialCategory) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { prefs ->
                val next = prefs.notifyCategories.toMutableSet()
                if (!next.add(category)) next.remove(category)
                prefs.copy(notifyCategories = next)
            }
        }
    }

    fun togglePreferredPublisher(publisherId: String) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { prefs ->
                val next = prefs.preferredPublisherIds.toMutableSet()
                if (!next.add(publisherId)) next.remove(publisherId)
                prefs.copy(preferredPublisherIds = next)
            }
        }
    }

    fun addPreferredAuthor(authorName: String) {
        val cleaned = authorName.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            settingsRepository.updatePreferences { prefs ->
                prefs.copy(preferredAuthorNames = prefs.preferredAuthorNames + cleaned)
            }
        }
    }

    fun removePreferredAuthor(authorName: String) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { prefs ->
                prefs.copy(preferredAuthorNames = prefs.preferredAuthorNames - authorName)
            }
        }
    }

    fun setNotificationFrequency(freq: NotificationFrequency) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(notificationFrequency = freq) }
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(quietHoursEnabled = enabled) }
        }
    }

    fun addKeywordAlert(keyword: String) {
        val cleaned = keyword.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            alertRepository.addKeywordAlert(keyword = cleaned)
        }
    }

    fun deleteKeywordAlert(id: Long) {
        viewModelScope.launch {
            alertRepository.deleteKeywordAlert(id)
        }
    }

    fun clearNonBookmarkedCache() {
        viewModelScope.launch {
            val deleted = editorialRepository.clearNonBookmarkedCache()
            statusMsg.value = "$deleted টি অসংরক্ষিত ক্যাশ নিবন্ধ মুছে ফেলা হয়েছে (সংরক্ষিত বুকমার্ক অক্ষুণ্ণ আছে)।"
            refreshCacheStats()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetTheme: (AppThemeMode) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onSetSyncFrequency: (SyncFrequency) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetDataSaver: (Boolean) -> Unit,
    onToggleEnabledCategory: (EditorialCategory) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onNotificationPermissionResult: (Boolean) -> Unit,
    onToggleNotifyCategory: (EditorialCategory) -> Unit,
    onTogglePreferredPublisher: (String) -> Unit,
    onAddPreferredAuthor: (String) -> Unit,
    onRemovePreferredAuthor: (String) -> Unit,
    onSetNotificationFrequency: (NotificationFrequency) -> Unit,
    onSetQuietHours: (Boolean) -> Unit,
    onAddKeywordAlert: (String) -> Unit,
    onDeleteKeywordAlert: (Long) -> Unit,
    onClearCache: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenCollectionPolicy: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val prefs = state.preferences
    val lang = prefs.language
    var newKeyword by remember { mutableStateOf("") }
    var newAuthorPref by remember { mutableStateOf("") }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onNotificationPermissionResult
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Settings" else "সেটিংস",
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("settings_list")
        ) {
            // 1. Language & Theme
            item(key = "sec_appearance") {
                SettingsSectionCard(
                    title = if (lang == AppLanguage.ENGLISH) "Language, Theme & Reading" else "ভাষা, থিম ও পাঠক সেটিংস"
                ) {
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Interface Language" else "অ্যাপের ভাষা",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { appLang ->
                            FilterChip(
                                selected = prefs.language == appLang,
                                onClick = { onSetLanguage(appLang) },
                                label = { Text(if (appLang == AppLanguage.BANGLA) "বাংলা" else "English") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Theme" else "থিম",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { onSetTheme(mode) },
                                label = { Text(mode.displayLabel(lang)) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Reader Font Size" else "পাঠক মোডে অক্ষরের আকার",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = prefs.readerFontScale,
                        onValueChange = onSetFontScale,
                        valueRange = 0.85f..1.65f
                    )
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Reader Line Spacing" else "পাঠক মোডে লাইনের দূরত্ব",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = prefs.readerLineSpacing,
                        onValueChange = onSetLineSpacing,
                        valueRange = 1.30f..2.10f
                    )
                }
            }

            // 2. Enabled Categories & Publishers
            item(key = "sec_scope") {
                SettingsSectionCard(
                    title = if (lang == AppLanguage.ENGLISH) "Content Categories & Publishers" else "প্রদর্শিত বিভাগ ও প্রকাশক"
                ) {
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Enabled Editorial Categories" else "সক্রিয় বিভাগসমূহ",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditorialCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = cat in prefs.enabledCategories,
                                onClick = { onToggleEnabledCategory(cat) },
                                label = { Text(cat.displayTitle(lang)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenSources, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (lang == AppLanguage.ENGLISH) {
                                "Manage Enabled Bangladeshi Publishers"
                            } else {
                                "সক্রিয় পত্রিকা ও উৎস ব্যবস্থাপনা করুন"
                            }
                        )
                    }
                }
            }

            // 3. Synchronization & Data Usage
            item(key = "sec_sync") {
                SettingsSectionCard(
                    title = if (lang == AppLanguage.ENGLISH) "Background Sync & Data Usage" else "স্বয়ংক্রিয় সিঙ্ক ও ডেটা ব্যবহার"
                ) {
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Refresh Frequency" else "রিফ্রেশের সময়কাল",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SyncFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = prefs.syncFrequency == freq,
                                onClick = { onSetSyncFrequency(freq) },
                                label = { Text(freq.displayLabel(lang)) }
                            )
                        }
                    }

                    SwitchRow(
                        title = if (lang == AppLanguage.ENGLISH) "Sync on Wi-Fi only" else "শুধু ওয়াই-ফাইয়ে সিঙ্ক করুন",
                        checked = prefs.wifiOnlySync,
                        onCheckedChange = onSetWifiOnly
                    )
                    SwitchRow(
                        title = if (lang == AppLanguage.ENGLISH) "Data Saver (hide thumbnails)" else "ডেটা সাশ্রয় মোড (থাম্বনেইল লুকান)",
                        checked = prefs.dataSaverMode,
                        onCheckedChange = onSetDataSaver
                    )
                }
            }

            // 4. Notifications, Preferred Authors, Keyword Alerts & Quiet Hours
            item(key = "sec_notifications") {
                SettingsSectionCard(
                    title = if (lang == AppLanguage.ENGLISH) {
                        "Notifications & Keyword Alerts (Editorial Only)"
                    } else {
                        "বিজ্ঞপ্তি, লেখক ও কীওয়ার্ড অ্যালার্ট (ব্রেকিং নিউজ মুক্ত)"
                    }
                ) {
                    SwitchRow(
                        title = if (lang == AppLanguage.ENGLISH) {
                            "Enable editorial notifications"
                        } else {
                            "নতুন কলাম ও সম্পাদকীয় বিজ্ঞপ্তি চালু"
                        },
                        checked = prefs.notificationsEnabled,
                        onCheckedChange = { wantEnabled ->
                            if (wantEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.hasNotificationPermission) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSetNotificationsEnabled(wantEnabled)
                            }
                        }
                    )

                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Notification Frequency" else "বিজ্ঞপ্তির ধরন ও হার",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotificationFrequency.entries.forEach { nf ->
                            FilterChip(
                                selected = prefs.notificationFrequency == nf,
                                onClick = { onSetNotificationFrequency(nf) },
                                label = { Text(nf.displayLabel(lang)) }
                            )
                        }
                    }

                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Alert Categories" else "বিজ্ঞপ্তির বিভাগ",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditorialCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = cat in prefs.notifyCategories,
                                onClick = { onToggleNotifyCategory(cat) },
                                label = { Text(cat.displayTitle(lang)) }
                            )
                        }
                    }

                    if (state.publishers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (lang == AppLanguage.ENGLISH) {
                                "Preferred Publishers (empty = all enabled)"
                            } else {
                                "পছন্দের পত্রিকা (খালি থাকলে সব সক্রিয় পত্রিকা)"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.publishers.take(8).forEach { pub ->
                                FilterChip(
                                    selected = pub.publisherId in prefs.preferredPublisherIds,
                                    onClick = { onTogglePreferredPublisher(pub.publisherId) },
                                    label = { Text(pub.displayName(lang)) }
                                )
                            }
                        }
                    }

                    SwitchRow(
                        title = if (lang == AppLanguage.ENGLISH) {
                            "Quiet Hours (${BanglaFormatter.formatHour24(prefs.quietStartHour, lang)} – ${BanglaFormatter.formatHour24(prefs.quietEndHour, lang)})"
                        } else {
                            "নীরব সময় (${BanglaFormatter.formatHour24(prefs.quietStartHour, lang)} থেকে ${BanglaFormatter.formatHour24(prefs.quietEndHour, lang)})"
                        },
                        checked = prefs.quietHoursEnabled,
                        onCheckedChange = onSetQuietHours
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    // Preferred Authors
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Preferred Authors" else "পছন্দের লেখক তালিকা",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newAuthorPref,
                            onValueChange = { newAuthorPref = it },
                            singleLine = true,
                            placeholder = {
                                Text(if (lang == AppLanguage.ENGLISH) "Add columnist name..." else "কলামিস্টের নাম যোগ করুন...")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            onAddPreferredAuthor(newAuthorPref)
                            newAuthorPref = ""
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add Author")
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        prefs.preferredAuthorNames.forEach { name ->
                            FilterChip(
                                selected = true,
                                onClick = { onRemovePreferredAuthor(name) },
                                label = { Text("$name ✕") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Keyword Alerts
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) "Keyword Alerts" else "কীওয়ার্ড অ্যালার্ট",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newKeyword,
                            onValueChange = { newKeyword = it },
                            singleLine = true,
                            placeholder = {
                                Text(if (lang == AppLanguage.ENGLISH) "e.g. সংবিধান, অর্থনীতি..." else "যেমন: সংবিধান, সংস্কার, বাজেট...")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            onAddKeywordAlert(newKeyword)
                            newKeyword = ""
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add Keyword")
                        }
                    }
                    state.keywordAlerts.forEach { rule ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "• ${rule.keyword}", style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onDeleteKeywordAlert(rule.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }

            // 5. Offline Cache Management & Maintenance
            item(key = "sec_cache") {
                SettingsSectionCard(
                    title = if (lang == AppLanguage.ENGLISH) "Offline Cache & Maintenance" else "অফলাইন ক্যাশ ও রক্ষণাবেক্ষণ"
                ) {
                    val stats = state.cacheStatistics
                    Text(
                        text = if (lang == AppLanguage.ENGLISH) {
                            "Cached articles: ${stats.publishedArticleCount} • Bookmarks: ${stats.bookmarkedCount} • Cached previews: ${stats.cachedExcerptCount} • Pending review: ${stats.pendingArticleCount}"
                        } else {
                            "ক্যাশকৃত লেখা: ${BanglaFormatter.formatCount(stats.publishedArticleCount, lang)} • বুকমার্ক: ${BanglaFormatter.formatCount(stats.bookmarkedCount, lang)} • প্রিভিউ ক্যাশ: ${BanglaFormatter.formatCount(stats.cachedExcerptCount, lang)} • যাচাই অপেক্ষমাণ: ${BanglaFormatter.formatCount(stats.pendingArticleCount, lang)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onClearCache, modifier = Modifier.weight(1f)) {
                            Text(if (lang == AppLanguage.ENGLISH) "Clear Cache" else "ক্যাশ পরিষ্কার")
                        }
                        Button(onClick = onOpenMaintenance, modifier = Modifier.weight(1f)) {
                            Text(if (lang == AppLanguage.ENGLISH) "Admin & Cutoff" else "যাচাই ও কাটঅফ")
                        }
                    }
                }
            }

            // 6. Policies & About
            item(key = "sec_policies") {
                SettingsSectionCard(
                    title = if (lang == AppLanguage.ENGLISH) "About & Policies" else "অ্যাপ পরিচিতি ও নীতিমালা"
                ) {
                    OutlinedButton(onClick = onOpenPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (lang == AppLanguage.ENGLISH) "Privacy Policy" else "গোপনীয়তা নীতিমালা (Privacy Policy)")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = onOpenCollectionPolicy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (lang == AppLanguage.ENGLISH) "Content Collection & Copyright Policy" else "কনটেন্ট সংগ্রহ ও কপিরাইট নীতিমালা")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                        Text(if (lang == AppLanguage.ENGLISH) "About My News (আমার খবর)" else "আমার খবর (My News) সম্পর্কে")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
