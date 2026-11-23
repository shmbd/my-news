package bd.nib.songbadpatro.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class NetworkFetchResult {
    data class Success(
        val body: String,
        val finalUrl: String,
        val httpCode: Int
    ) : NetworkFetchResult()

    data class Restricted(
        val httpCode: Int,
        val messageBn: String
    ) : NetworkFetchResult()

    data class Error(
        val messageBn: String,
        val cause: Throwable? = null
    ) : NetworkFetchResult()
}

/**
 * Polite, ethical HTTP client for My News (আমার খবর).
 * - Uses HTTPS wherever supported (preserving localhost HTTP only for MockWebServer unit tests).
 * - Enforces per-host polite request spacing so publishers are never aggressively scraped.
 * - Never attempts to bypass HTTP 401/402/403/429, paywalls, or anti-bot challenges.
 */
@Singleton
class FeedNetworkClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val hostLocks = ConcurrentHashMap<String, Mutex>()
    private val lastHostRequestAt = ConcurrentHashMap<String, Long>()

    suspend fun fetchText(
        url: String,
        acceptHeader: String,
        minHostDelayMs: Long = DEFAULT_POLITE_HOST_DELAY_MS
    ): NetworkFetchResult = withContext(Dispatchers.IO) {
        val targetUrl = upgradeToHttpsUnlessLocalhost(url.trim())
        val host = runCatching { URI(targetUrl).host?.lowercase().orEmpty() }.getOrDefault("")

        if (host.isNotBlank() && host != "localhost" && host != "127.0.0.1" && minHostDelayMs > 0L) {
            val mutex = hostLocks.computeIfAbsent(host) { Mutex() }
            mutex.withLock {
                val prev = lastHostRequestAt[host] ?: 0L
                val elapsed = System.currentTimeMillis() - prev
                if (elapsed in 0 until minHostDelayMs) {
                    delay(minHostDelayMs - elapsed)
                }
                lastHostRequestAt[host] = System.currentTimeMillis()
            }
        }

        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", acceptHeader)
            .header("Accept-Language", "bn-BD,bn;q=0.9,en;q=0.8")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val finalUrl = response.request.url.toString()
                if (code == 401 || code == 402 || code == 403 || code == 429 || code == 451) {
                    return@withContext NetworkFetchResult.Restricted(
                        httpCode = code,
                        messageBn = "প্রকাশকের সার্ভার স্বয়ংক্রিয় সংগ্রহে অ্যাক্সেস সীমাবদ্ধ করেছে (HTTP $code)।"
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext NetworkFetchResult.Error(
                        messageBn = "সার্ভার থেকে ত্রুটি পাওয়া গেছে (HTTP $code)।"
                    )
                }
                val bodyObj = response.body
                    ?: return@withContext NetworkFetchResult.Error("সার্ভার থেকে খালি প্রতিক্রিয়া পাওয়া গেছে।")

                val bytes = bodyObj.bytes()
                val charset = bodyObj.contentType()?.charset(Charsets.UTF_8) ?: detectXmlOrHtmlCharset(bytes)
                val bodyText = String(bytes, charset)

                // Never bypass or process anti-bot / CAPTCHA pages
                if (looksLikeBotChallenge(bodyText)) {
                    return@withContext NetworkFetchResult.Restricted(
                        httpCode = 403,
                        messageBn = "প্রকাশকের ওয়েবসাইট স্বয়ংক্রিয় প্রবেশ সীমাবদ্ধ করেছে।"
                    )
                }

                NetworkFetchResult.Success(
                    body = bodyText,
                    finalUrl = finalUrl,
                    httpCode = code
                )
            }
        } catch (ex: UnknownHostException) {
            NetworkFetchResult.Error("ইন্টারনেট সংযোগ বা প্রকাশকের সার্ভার পাওয়া যায়নি।", ex)
        } catch (ex: SocketTimeoutException) {
            NetworkFetchResult.Error("সার্ভারের সংযোগ সময়সীমা অতিক্রম করেছে।", ex)
        } catch (ex: SSLException) {
            NetworkFetchResult.Error("নিরাপদ HTTPS/SSL সনদ যাচাই ব্যর্থ হয়েছে।", ex)
        } catch (ex: IOException) {
            NetworkFetchResult.Error("নেটওয়ার্ক ত্রুটি: ${ex.localizedMessage ?: "সংযোগ ব্যর্থ"}", ex)
        } catch (ex: Exception) {
            NetworkFetchResult.Error("তথ্য সংগ্রহে অপ্রত্যাশিত ত্রুটি ঘটেছে।", ex)
        }
    }

    private fun upgradeToHttpsUnlessLocalhost(url: String): String {
        if (!url.startsWith("http://", ignoreCase = true)) return url
        val lower = url.lowercase()
        if (lower.startsWith("http://localhost") || lower.startsWith("http://127.0.0.1")) {
            return url
        }
        return "https://" + url.substring(7)
    }

    private fun looksLikeBotChallenge(body: String): Boolean {
        if (body.length > 25_000) return false
        val sample = body.take(4096).lowercase()
        return sample.contains("cf-turnstile") ||
            sample.contains("verify you are human") ||
            sample.contains("g-recaptcha") && sample.contains("<title>just a moment")
    }

    private fun detectXmlOrHtmlCharset(bytes: ByteArray): Charset {
        val sample = String(bytes, 0, minOf(bytes.size, 320), Charsets.ISO_8859_1)
        val match = Regex("(?:encoding|charset)=[\"']?([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE).find(sample)
        val name = match?.groupValues?.getOrNull(1)
        return runCatching {
            if (!name.isNullOrBlank()) Charset.forName(name) else Charsets.UTF_8
        }.getOrDefault(Charsets.UTF_8)
    }

    companion object {
        const val USER_AGENT = "MyNewsBangladeshEditorialAggregator/1.0 (Android; +https://github.com/shmbd)"
        const val ACCEPT_FEED = "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8"
        const val ACCEPT_HTML = "text/html, application/xhtml+xml;q=0.9, */*;q=0.8"
        const val DEFAULT_POLITE_HOST_DELAY_MS = 350L
    }
}
