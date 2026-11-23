package bd.nib.songbadpatro.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.SyncFrequency
import bd.nib.songbadpatro.domain.repository.AlertRepository
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.domain.usecase.EditorialAlertMatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@HiltWorker
class EditorialSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val editorialRepository: EditorialRepository,
    private val alertRepository: AlertRepository,
    private val settingsRepository: SettingsRepository,
    private val alertMatcher: EditorialAlertMatcher,
    private val notifier: EditorialAlertNotifier
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val UNIQUE_PERIODIC_SYNC_WORK = "my_news_periodic_editorial_sync"
        private const val TAG = "EditorialSyncWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val summary = editorialRepository.syncAllEnabledSources()
            Log.d(
                TAG,
                "Sync finished: ok=${summary.succeededSources}, fail=${summary.failedSources}, newPublished=${summary.newlyPublishedCount}"
            )

            if (summary.newlyPublishedCount > 0) {
                val prefs = settingsRepository.getPreferences()
                val recentArticles = editorialRepository.observeRecentPublished(limit = 40).first()
                val keywordRules = alertRepository.getEnabledKeywordAlerts()
                val alreadyNotified = alertRepository.getNotifiedArticleIds()
                val dhakaHour = ZonedDateTime.now(PublicationCutoffConfig.DHAKA_ZONE).hour
                val now = System.currentTimeMillis()

                val candidates = alertMatcher.findNotificationCandidates(
                    newlyPublished = recentArticles,
                    keywordRules = keywordRules,
                    alreadyNotifiedIds = alreadyNotified,
                    preferences = prefs,
                    currentHour24 = dhakaHour,
                    nowMillis = now
                )
                val delivered = notifier.postNotifications(candidates, prefs.language)
                if (delivered.isNotEmpty()) {
                    alertRepository.markArticlesNotified(
                        articleIds = delivered.map { it.article.articleId },
                        reason = delivered.first().matchedReasonEn
                    )
                    settingsRepository.updatePreferences {
                        it.copy(lastNotificationSentAt = now)
                    }
                }
            }

            if (summary.succeededSources == 0 && summary.totalSourcesAttempted > 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Background editorial sync failed", ex)
            Result.retry()
        }
    }
}

@Singleton
class SyncWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePeriodicSync(frequency: SyncFrequency, wifiOnly: Boolean) {
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val intervalMinutes = frequency.minutes
        if (intervalMinutes == null) {
            workManager.cancelUniqueWork(EditorialSyncWorker.UNIQUE_PERIODIC_SYNC_WORK)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<EditorialSyncWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            EditorialSyncWorker.UNIQUE_PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
