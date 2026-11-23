package bd.nib.songbadpatro.domain.usecase

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object UrlCanonicalizer {

    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "mc_cid", "mc_eid", "ref", "ref_src", "source",
        "share", "s", "si", "igshid", "yclid", "_ga", "amp", "_rsc"
    )

    fun canonicalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""

        return try {
            val withScheme = when {
                trimmed.startsWith("//") -> "https:$trimmed"
                !trimmed.contains("://") -> "https://$trimmed"
                else -> trimmed
            }
            val uri = URI(withScheme)
            val rawHost = uri.host ?: return trimmed.lowercase().trimEnd('/')
            val host = rawHost.lowercase()
                .removePrefix("www.")
                .removePrefix("m.")
            val scheme = if (host == "localhost" || host == "127.0.0.1") {
                uri.scheme?.lowercase() ?: "http"
            } else {
                "https"
            }
            val rawPath = (uri.rawPath ?: "/")
                .replace(Regex("/+"), "/")
                .let { p -> if (p.length > 1) p.trimEnd('/') else p }

            val cleanedQuery = uri.rawQuery
                ?.split("&")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    val key = if (idx >= 0) pair.substring(0, idx) else pair
                    val decoded = runCatching {
                        URLDecoder.decode(key, StandardCharsets.UTF_8.name())
                    }.getOrDefault(key).lowercase()
                    if (decoded in trackingParams || decoded.startsWith("utm_")) null else pair
                }
                ?.sorted()
                ?.joinToString("&")
                ?.takeIf { it.isNotBlank() }

            val portPart = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            buildString {
                append(scheme).append("://").append(host).append(portPart).append(rawPath)
                if (cleanedQuery != null) append("?").append(cleanedQuery)
            }
        } catch (_: Exception) {
            trimmed.lowercase().trimEnd('/')
        }
    }

    fun normalizeDisplayUrl(rawUrl: String): String {
        val t = rawUrl.trim()
        return when {
            t.startsWith("http://localhost", ignoreCase = true) ||
                t.startsWith("http://127.0.0.1", ignoreCase = true) -> t
            t.startsWith("http://", ignoreCase = true) -> "https://" + t.substring(7)
            t.startsWith("//") -> "https:$t"
            else -> t
        }
    }
}
