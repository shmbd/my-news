package bd.nib.songbadpatro.presentation.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object ShareAndBrowserHelper {

    fun openExternalUrl(context: Context, url: String) {
        val safeUrl = url.trim()
        if (!safeUrl.startsWith("http://") && !safeUrl.startsWith("https://")) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun shareArticle(
        context: Context,
        title: String,
        publisherName: String,
        authorOrPerson: String?,
        url: String
    ) {
        val bylinePart = authorOrPerson?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
        val shareText = "$title$bylinePart ($publisherName)\n$url"
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            val chooser = Intent.createChooser(sendIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
