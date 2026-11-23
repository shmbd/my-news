package bd.nib.songbadpatro.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import bd.nib.songbadpatro.MainActivity
import bd.nib.songbadpatro.R
import bd.nib.songbadpatro.domain.model.AlertCandidate
import bd.nib.songbadpatro.domain.model.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts non-intrusive notifications ONLY for newly published Columns, Editorials,
 * Opinions, and Interviews matching user preferences.
 *
 * Strictly NEVER sends breaking-news notifications.
 */
@Singleton
class EditorialAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_EDITORIAL = "mynews_editorial_updates"
        const val CHANNEL_KEYWORD_AUTHOR = "mynews_keyword_author_alerts"
        const val EXTRA_ARTICLE_ID = "extra_article_id"
        private const val MAX_NOTIFICATIONS_PER_RUN = 3
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val editorialChannel = NotificationChannel(
                CHANNEL_EDITORIAL,
                context.getString(R.string.channel_editorial_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_editorial_desc)
            }
            val keywordChannel = NotificationChannel(
                CHANNEL_KEYWORD_AUTHOR,
                context.getString(R.string.channel_keyword_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_keyword_desc)
            }
            manager.createNotificationChannel(editorialChannel)
            manager.createNotificationChannel(keywordChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun postNotifications(
        candidates: List<AlertCandidate>,
        language: AppLanguage
    ): List<AlertCandidate> {
        if (candidates.isEmpty() || !hasNotificationPermission()) return emptyList()
        ensureChannels()

        val manager = NotificationManagerCompat.from(context)
        val delivered = mutableListOf<AlertCandidate>()

        candidates.take(MAX_NOTIFICATIONS_PER_RUN).forEach { candidate ->
            val article = candidate.article
            val channelId = if (candidate.isKeywordOrAuthorAlert) {
                CHANNEL_KEYWORD_AUTHOR
            } else {
                CHANNEL_EDITORIAL
            }
            val title = if (language == AppLanguage.ENGLISH) {
                candidate.matchedReasonEn
            } else {
                candidate.matchedReasonBn
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ARTICLE_ID, article.articleId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                article.articleId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val contributor = article.primaryContributorLabel(language).orEmpty()
            val bigText = if (contributor.isNotBlank()) {
                "${article.title}\n$contributor • ${article.displayPublisherName(language)}"
            } else {
                "${article.title}\n${article.displayPublisherName(language)}"
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(article.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(
                    if (candidate.isKeywordOrAuthorAlert) NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_DEFAULT
                )
                .build()

            try {
                manager.notify(article.articleId.hashCode(), notification)
                delivered.add(candidate)
            } catch (_: SecurityException) {
                // Permission revoked dynamically
            }
        }
        return delivered
    }
}
