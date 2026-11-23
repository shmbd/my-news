package bd.nib.songbadpatro.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.AppThemeMode
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.NotificationFrequency
import bd.nib.songbadpatro.domain.model.ReaderTheme
import bd.nib.songbadpatro.domain.model.SyncFrequency
import bd.nib.songbadpatro.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.myNewsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "my_news_preferences"
)

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT_SCALE = floatPreferencesKey("reader_font_scale")
        val READER_LINE_SPACING = floatPreferencesKey("reader_line_spacing")
        val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
        val DATA_SAVER_MODE = booleanPreferencesKey("data_saver_mode")
        val ENABLED_CATEGORIES = stringSetPreferencesKey("enabled_categories")
        val PREFERRED_PUBLISHERS = stringSetPreferencesKey("preferred_publishers")
        val PREFERRED_AUTHORS = stringSetPreferencesKey("preferred_authors")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_CATEGORIES = stringSetPreferencesKey("notify_categories")
        val NOTIFICATION_FREQUENCY = stringPreferencesKey("notification_frequency")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val LAST_NOTIFICATION_SENT_AT = longPreferencesKey("last_notification_sent_at")
        val PUBLICATION_CUTOFF_ISO = stringPreferencesKey("publication_cutoff_iso")
    }

    val preferencesFlow: Flow<UserPreferences> = context.myNewsDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs -> prefs.toUserPreferences() }

    suspend fun getPreferences(): UserPreferences = preferencesFlow.first()

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        context.myNewsDataStore.edit { mutablePrefs ->
            val current = mutablePrefs.toUserPreferences()
            val updated = transform(current)
            val validatedCutoff = if (PublicationCutoffConfig.isValidIsoCutoffDate(updated.publicationCutoffIsoDate)) {
                updated.publicationCutoffIsoDate.trim()
            } else {
                PublicationCutoffConfig.DEFAULT_CUTOFF_ISO_DATE
            }

            mutablePrefs[Keys.LANGUAGE] = updated.language.tag
            mutablePrefs[Keys.THEME_MODE] = updated.themeMode.name
            mutablePrefs[Keys.READER_THEME] = updated.readerTheme.name
            mutablePrefs[Keys.READER_FONT_SCALE] = updated.readerFontScale.coerceIn(0.85f, 1.75f)
            mutablePrefs[Keys.READER_LINE_SPACING] = updated.readerLineSpacing.coerceIn(1.25f, 2.20f)
            mutablePrefs[Keys.SYNC_FREQUENCY] = updated.syncFrequency.name
            mutablePrefs[Keys.WIFI_ONLY_SYNC] = updated.wifiOnlySync
            mutablePrefs[Keys.DATA_SAVER_MODE] = updated.dataSaverMode
            mutablePrefs[Keys.ENABLED_CATEGORIES] = updated.enabledCategories
                .ifEmpty { EditorialCategory.entries.toSet() }
                .map { it.name }
                .toSet()
            mutablePrefs[Keys.PREFERRED_PUBLISHERS] = updated.preferredPublisherIds
            mutablePrefs[Keys.PREFERRED_AUTHORS] = updated.preferredAuthorNames
            mutablePrefs[Keys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
            mutablePrefs[Keys.NOTIFY_CATEGORIES] = updated.notifyCategories.map { it.name }.toSet()
            mutablePrefs[Keys.NOTIFICATION_FREQUENCY] = updated.notificationFrequency.name
            mutablePrefs[Keys.QUIET_HOURS_ENABLED] = updated.quietHoursEnabled
            mutablePrefs[Keys.QUIET_START_HOUR] = updated.quietStartHour.coerceIn(0, 23)
            mutablePrefs[Keys.QUIET_END_HOUR] = updated.quietEndHour.coerceIn(0, 23)
            mutablePrefs[Keys.LAST_NOTIFICATION_SENT_AT] = updated.lastNotificationSentAt
            mutablePrefs[Keys.PUBLICATION_CUTOFF_ISO] = validatedCutoff
        }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val allCats = EditorialCategory.entries.toSet()
        val storedCats = this[Keys.ENABLED_CATEGORIES]
            ?.mapNotNull { runCatching { EditorialCategory.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: allCats
        val storedNotifyCats = this[Keys.NOTIFY_CATEGORIES]
            ?.mapNotNull { runCatching { EditorialCategory.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: allCats
        val rawCutoff = this[Keys.PUBLICATION_CUTOFF_ISO]
        val safeCutoff = if (PublicationCutoffConfig.isValidIsoCutoffDate(rawCutoff)) {
            rawCutoff!!.trim()
        } else {
            PublicationCutoffConfig.DEFAULT_CUTOFF_ISO_DATE
        }

        return UserPreferences(
            language = AppLanguage.fromTag(this[Keys.LANGUAGE]),
            themeMode = runCatching {
                AppThemeMode.valueOf(this[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name)
            }.getOrDefault(AppThemeMode.SYSTEM),
            readerTheme = runCatching {
                ReaderTheme.valueOf(this[Keys.READER_THEME] ?: ReaderTheme.LIGHT.name)
            }.getOrDefault(ReaderTheme.LIGHT),
            readerFontScale = (this[Keys.READER_FONT_SCALE] ?: 1.05f).coerceIn(0.85f, 1.75f),
            readerLineSpacing = (this[Keys.READER_LINE_SPACING] ?: 1.60f).coerceIn(1.25f, 2.20f),
            syncFrequency = runCatching {
                SyncFrequency.valueOf(this[Keys.SYNC_FREQUENCY] ?: SyncFrequency.HOURS_1.name)
            }.getOrDefault(SyncFrequency.HOURS_1),
            wifiOnlySync = this[Keys.WIFI_ONLY_SYNC] ?: false,
            dataSaverMode = this[Keys.DATA_SAVER_MODE] ?: false,
            enabledCategories = storedCats,
            preferredPublisherIds = this[Keys.PREFERRED_PUBLISHERS] ?: emptySet(),
            preferredAuthorNames = this[Keys.PREFERRED_AUTHORS] ?: emptySet(),
            notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: false,
            notifyCategories = storedNotifyCats,
            notificationFrequency = runCatching {
                NotificationFrequency.valueOf(this[Keys.NOTIFICATION_FREQUENCY] ?: NotificationFrequency.IMMEDIATE.name)
            }.getOrDefault(NotificationFrequency.IMMEDIATE),
            quietHoursEnabled = this[Keys.QUIET_HOURS_ENABLED] ?: true,
            quietStartHour = (this[Keys.QUIET_START_HOUR] ?: 23).coerceIn(0, 23),
            quietEndHour = (this[Keys.QUIET_END_HOUR] ?: 7).coerceIn(0, 23),
            lastNotificationSentAt = this[Keys.LAST_NOTIFICATION_SENT_AT] ?: 0L,
            publicationCutoffIsoDate = safeCutoff
        )
    }
}
