package bd.nib.songbadpatro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import bd.nib.songbadpatro.data.worker.EditorialAlertNotifier
import bd.nib.songbadpatro.data.worker.SyncWorkScheduler
import bd.nib.songbadpatro.domain.repository.PublisherRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MyNewsApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var publisherRepository: PublisherRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var workScheduler: SyncWorkScheduler

    @Inject
    lateinit var notifier: EditorialAlertNotifier

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        applicationScope.launch {
            publisherRepository.ensureRegistrySeeded()
            val prefs = settingsRepository.getPreferences()
            workScheduler.schedulePeriodicSync(prefs.syncFrequency, prefs.wifiOnlySync)
        }
    }
}
