package bd.nib.songbadpatro.data.remote

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight robots.txt parser and policy cache ensuring automated section-page or
 * article metadata requests strictly respect publisher robots.txt Disallow rules.
 */
@Singleton
class RobotsTxtPolicyChecker @Inject constructor(
    private val networkClient: FeedNetworkClient
) {
    private data class CachedRobotsRules(
        val disallowedPrefixes: List<String>,
        val allowedPrefixes: List<String>,
        val fetchedAtMs: Long
    )

    private val cache = ConcurrentHashMap<String, CachedRobotsRules>()

    suspend fun isUrlAllowed(targetUrl: String): Boolean {
        val uri = runCatching { URI(targetUrl.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host == "127.0.0.1") return true

        val origin = "${uri.scheme ?: "https"}://$host${if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"
        val now = System.currentTimeMillis()
        val existing = cache[origin]
        val rules = if (existing != null && now - existing.fetchedAtMs < CACHE_TTL_MS) {
            existing
        } else {
            val fetched = fetchRobotsForOrigin(origin, now)
            cache[origin] = fetched
            fetched
        }

        val pathAndQuery = buildString {
            append(uri.rawPath?.ifBlank { "/" } ?: "/")
            if (!uri.rawQuery.isNullOrBlank()) append("?").append(uri.rawQuery)
        }
        return evaluatePath(pathAndQuery, rules.allowedPrefixes, rules.disallowedPrefixes)
    }

    private suspend fun fetchRobotsForOrigin(origin: String, now: Long): CachedRobotsRules {
        val res = networkClient.fetchText("$origin/robots.txt", "text/plain, */*;q=0.5", minHostDelayMs = 0L)
        return when (res) {
            is NetworkFetchResult.Success -> {
                val parsed = parseRobotsTxt(res.body)
                CachedRobotsRules(parsed.second, parsed.first, now)
            }
            is NetworkFetchResult.Restricted -> {
                // If robots.txt itself returns 403/401, be conservative for HTML scraping
                CachedRobotsRules(disallowedPrefixes = listOf("/"), allowedPrefixes = emptyList(), fetchedAtMs = now)
            }
            is NetworkFetchResult.Error -> {
                // Standard RFC 9309: unreachable/404 robots.txt permits access
                CachedRobotsRules(disallowedPrefixes = emptyList(), allowedPrefixes = listOf("/"), fetchedAtMs = now)
            }
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L // 6 hours

        /**
         * Parses `User-agent: *` rules into `(allowedPrefixes, disallowedPrefixes)`.
         */
        fun parseRobotsTxt(robotsContent: String): Pair<List<String>, List<String>> {
            val allowed = mutableListOf<String>()
            val disallowed = mutableListOf<String>()
            var appliesToWildcard = false
            var seenDirectiveInGroup = false

            robotsContent.lineSequence().forEach { rawLine ->
                val line = rawLine.substringBefore('#').trim()
                if (line.isBlank()) return@forEach
                val colonIdx = line.indexOf(':')
                if (colonIdx <= 0) return@forEach

                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()

                when (key) {
                    "user-agent" -> {
                        if (seenDirectiveInGroup) {
                            appliesToWildcard = false
                            seenDirectiveInGroup = false
                        }
                        if (value == "*") {
                            appliesToWildcard = true
                        }
                    }
                    "allow" -> {
                        seenDirectiveInGroup = true
                        if (appliesToWildcard && value.isNotBlank()) {
                            allowed.add(cleanPattern(value))
                        }
                    }
                    "disallow" -> {
                        seenDirectiveInGroup = true
                        if (appliesToWildcard && value.isNotBlank()) {
                            disallowed.add(cleanPattern(value))
                        }
                    }
                }
            }
            return allowed to disallowed
        }

        fun evaluatePath(
            path: String,
            allowedPrefixes: List<String>,
            disallowedPrefixes: List<String>
        ): Boolean {
            var longestAllow = -1
            for (allow in allowedPrefixes) {
                if (matchesRobotsPrefix(path, allow)) {
                    longestAllow = maxOf(longestAllow, allow.length)
                }
            }
            var longestDisallow = -1
            for (disallow in disallowedPrefixes) {
                if (matchesRobotsPrefix(path, disallow)) {
                    longestDisallow = maxOf(longestDisallow, disallow.length)
                }
            }
            if (longestDisallow < 0) return true
            return longestAllow >= longestDisallow
        }

        private fun cleanPattern(pattern: String): String =
            pattern.trim().removeSuffix("$")

        private fun matchesRobotsPrefix(path: String, pattern: String): Boolean {
            if (pattern.isEmpty()) return false
            if (!pattern.contains('*')) {
                return path.startsWith(pattern)
            }
            val regexStr = "^" + pattern.split('*').joinToString(".*") { Regex.escape(it) }
            return Regex(regexStr).containsMatchIn(path)
        }
    }
}
