package bd.nib.songbadpatro

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.nib.songbadpatro.data.worker.EditorialAlertNotifier
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.presentation.navigation.MyNewsAppShell
import bd.nib.songbadpatro.presentation.theme.MyNewsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var deepLinkArticleId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkArticleId = intent?.getStringExtra(EditorialAlertNotifier.EXTRA_ARTICLE_ID)

        setContent {
            val prefs by settingsRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            MyNewsTheme(themeMode = prefs.themeMode) {
                MyNewsAppShell(
                    language = prefs.language,
                    deepLinkArticleId = deepLinkArticleId
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkArticleId = intent.getStringExtra(EditorialAlertNotifier.EXTRA_ARTICLE_ID)
    }
}
