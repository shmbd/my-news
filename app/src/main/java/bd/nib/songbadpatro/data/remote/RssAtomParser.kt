package bd.nib.songbadpatro.data.remote

import java.io.StringReader
import java.util.Locale
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class DiscoveredRawItem(
    val title: String,
    val link: String,
    val description: String = "",
    val contentHtml: String? = null,
    val author: String? = null,
    val authorAffiliation: String? = null,
    val authorImageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val originalPublishedRaw: String? = null,
    val updatedRaw: String? = null,
    val categoriesAndTags: List<String> = emptyList(),
    val requiresDetailFetchForDateOrMeta: Boolean = false
)

data class ParsedDiscoveryFeed(
    val feedTitle: String?,
    val items: List<DiscoveredRawItem>,
    val warnings: List<String> = emptyList()
)

fun interface XmlPullParserProvider {
    fun createParser(): XmlPullParser
}

class DefaultXmlPullParserProvider : XmlPullParserProvider {
    override fun createParser(): XmlPullParser {
        return try {
            android.util.Xml.newPullParser()
        } catch (_: Throwable) {
            XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }.newPullParser()
        }
    }
}

/**
 *Maintained Android-compatible RSS 2.0 / Atom 1.0 / Google News Sitemap parser.
 *
 * CRITICAL DESIGN FOR MY NEWS (আমার খবর):
 * - Extracts [DiscoveredRawItem.originalPublishedRaw] (`pubDate`, `dc:date`, `atom:published`,
 *   `news:publication_date`) STRICTLY SEPARATELY from [DiscoveredRawItem.updatedRaw]
 *   (`atom:updated`, `dcterms:modified`, `lastmod`).
 * - NEVER substitutes `System.currentTimeMillis()` when a publication date is absent.
 */
class RssAtomParser(
    private val parserProvider: XmlPullParserProvider = DefaultXmlPullParserProvider()
) {

    fun parse(rawXml: String): ParsedDiscoveryFeed {
        val cleaned = sanitizeXmlInput(rawXml)
        if (cleaned.isBlank()) {
            throw IllegalArgumentException("Empty XML feed payload")
        }

        val parser = parserProvider.createParser()
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) }
        parser.setInput(StringReader(cleaned))

        val items = mutableListOf<DiscoveredRawItem>()
        val warnings = mutableListOf<String>()
        var feedTitle: String? = null
        var inHeader = false
        var rootDetected = false

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tag = localTagName(parser.name)
                    when (tag) {
                        "rss", "rdf", "urlset" -> rootDetected = true
                        "channel", "feed" -> {
                            rootDetected = true
                            inHeader = true
                        }
                        "title" -> {
                            if (inHeader && feedTitle == null) {
                                feedTitle = safeReadText(parser).trim().ifBlank { null }
                            }
                        }
                        "item" -> {
                            inHeader = false
                            runCatching { readRssItem(parser) }
                                .onSuccess { if (it != null) items.add(it) }
                                .onFailure { warnings.add("Skipped malformed RSS item: ${it.message}") }
                        }
                        "entry" -> {
                            inHeader = false
                            runCatching { readAtomEntry(parser) }
                                .onSuccess { if (it != null) items.add(it) }
                                .onFailure { warnings.add("Skipped malformed Atom entry: ${it.message}") }
                        }
                        "url" -> {
                            inHeader = false
                            runCatching { readSitemapUrlEntry(parser) }
                                .onSuccess { if (it != null) items.add(it) }
                                .onFailure { warnings.add("Skipped malformed Sitemap URL: ${it.message}") }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (ex: Exception) {
            if (items.isEmpty()) {
                throw IllegalArgumentException("Malformed XML feed: ${ex.message}", ex)
            } else {
                warnings.add("Partially truncated XML feed: ${ex.message}")
            }
        }

        if (!rootDetected && items.isEmpty()) {
            throw IllegalArgumentException("Not a valid RSS, Atom, or Sitemap XML document")
        }

        return ParsedDiscoveryFeed(
            feedTitle = feedTitle,
            items = items,
            warnings = warnings
        )
    }

    private fun readRssItem(parser: XmlPullParser): DiscoveredRawItem? {
        var title = ""
        var link = ""
        var guid = ""
        var descriptionRaw = ""
        var contentEncoded: String? = null
        var author: String? = null
        var imageUrl: String? = null
        var originalPubRaw: String? = null
        var updatedRaw: String? = null
        val categories = mutableListOf<String>()

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val rawName = parser.name.orEmpty().lowercase(Locale.ROOT)
                    val tag = localTagName(rawName)
                    when {
                        tag == "title" && title.isBlank() -> {
                            title = cleanPlainText(safeReadText(parser))
                            depth--
                        }
                        tag == "link" && link.isBlank() -> {
                            val href = parser.getAttributeValue(null, "href")
                            val text = safeReadText(parser).trim()
                            link = href?.takeIf { it.isNotBlank() } ?: text
                            depth--
                        }
                        tag == "guid" && guid.isBlank() -> {
                            guid = safeReadText(parser).trim()
                            depth--
                        }
                        rawName == "content:encoded" || tag == "encoded" -> {
                            contentEncoded = safeReadText(parser).trim().ifBlank { null }
                            depth--
                        }
                        tag == "description" || tag == "summary" -> {
                            val desc = safeReadText(parser).trim()
                            if (desc.isNotBlank()) descriptionRaw = desc
                            depth--
                        }
                        tag == "author" || tag == "creator" -> {
                            val a = cleanPlainText(safeReadText(parser))
                            if (a.isNotBlank() && author == null) author = a
                            depth--
                        }
                        tag == "pubdate" || rawName == "dc:date" || tag == "published" -> {
                            val d = safeReadText(parser).trim()
                            if (d.isNotBlank() && originalPubRaw == null) originalPubRaw = d
                            depth--
                        }
                        tag == "updated" || tag == "modified" || rawName == "atom:updated" || rawName == "dcterms:modified" -> {
                            val u = safeReadText(parser).trim()
                            if (u.isNotBlank() && updatedRaw == null) updatedRaw = u
                            depth--
                        }
                        tag == "category" -> {
                            val cat = cleanPlainText(safeReadText(parser))
                            if (cat.isNotBlank()) categories.add(cat)
                            depth--
                        }
                        tag == "enclosure" -> {
                            val url = parser.getAttributeValue(null, "url")
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            if (!url.isNullOrBlank() && (type.isBlank() || type.startsWith("image"))) {
                                if (imageUrl == null) imageUrl = url.trim()
                            }
                        }
                        rawName == "media:content" || rawName == "media:thumbnail" || tag == "thumbnail" -> {
                            val url = parser.getAttributeValue(null, "url")
                            val medium = parser.getAttributeValue(null, "medium").orEmpty()
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            val isImg = medium.isBlank() || medium.equals("image", true) || type.startsWith("image")
                            if (!url.isNullOrBlank() && isImg && imageUrl == null) {
                                imageUrl = url.trim()
                            }
                        }
                        tag == "image" -> {
                            val textUrl = safeReadText(parser).trim()
                            if (textUrl.startsWith("http") && imageUrl == null) imageUrl = textUrl
                            depth--
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        val resolvedLink = link.ifBlank { guid.takeIf { it.startsWith("http") }.orEmpty() }
        if (title.isBlank() || resolvedLink.isBlank()) return null

        val extractedImage = imageUrl
            ?: extractFirstImage(contentEncoded)
            ?: extractFirstImage(descriptionRaw)

        val cleanedDesc = cleanSummaryText(descriptionRaw.ifBlank { contentEncoded.orEmpty() })

        return DiscoveredRawItem(
            title = title,
            link = resolvedLink,
            description = cleanedDesc,
            contentHtml = contentEncoded ?: descriptionRaw.takeIf { it.contains("<p") },
            author = author,
            thumbnailUrl = normalizeHttpsImage(extractedImage),
            originalPublishedRaw = originalPubRaw,
            updatedRaw = updatedRaw,
            categoriesAndTags = categories,
            requiresDetailFetchForDateOrMeta = originalPubRaw.isNullOrBlank()
        )
    }

    private fun readAtomEntry(parser: XmlPullParser): DiscoveredRawItem? {
        var title = ""
        var link = ""
        var summaryRaw = ""
        var contentHtml: String? = null
        var author: String? = null
        var imageUrl: String? = null
        var publishedRaw: String? = null
        var updatedRaw: String? = null
        val categories = mutableListOf<String>()

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val rawName = parser.name.orEmpty().lowercase(Locale.ROOT)
                    val tag = localTagName(rawName)
                    when {
                        tag == "title" && title.isBlank() -> {
                            title = cleanPlainText(safeReadText(parser))
                            depth--
                        }
                        tag == "link" -> {
                            val rel = parser.getAttributeValue(null, "rel").orEmpty()
                            val href = parser.getAttributeValue(null, "href").orEmpty()
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            if (rel == "enclosure" && type.startsWith("image") && href.isNotBlank() && imageUrl == null) {
                                imageUrl = href.trim()
                            } else if ((rel.isBlank() || rel == "alternate") && href.isNotBlank() && link.isBlank()) {
                                link = href.trim()
                            }
                        }
                        tag == "summary" -> {
                            summaryRaw = safeReadText(parser).trim()
                            depth--
                        }
                        tag == "content" -> {
                            contentHtml = safeReadText(parser).trim().ifBlank { null }
                            depth--
                        }
                        tag == "name" && author == null -> {
                            val a = cleanPlainText(safeReadText(parser))
                            if (a.isNotBlank()) author = a
                            depth--
                        }
                        tag == "published" -> {
                            val p = safeReadText(parser).trim()
                            if (p.isNotBlank() && publishedRaw == null) publishedRaw = p
                            depth--
                        }
                        tag == "updated" -> {
                            val u = safeReadText(parser).trim()
                            if (u.isNotBlank() && updatedRaw == null) updatedRaw = u
                            depth--
                        }
                        tag == "category" -> {
                            val term = parser.getAttributeValue(null, "label")
                                ?: parser.getAttributeValue(null, "term")
                            if (!term.isNullOrBlank()) categories.add(term.trim())
                        }
                        rawName == "media:content" || rawName == "media:thumbnail" -> {
                            val url = parser.getAttributeValue(null, "url")
                            if (!url.isNullOrBlank() && imageUrl == null) imageUrl = url.trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (title.isBlank() || link.isBlank()) return null

        val extractedImage = imageUrl
            ?: extractFirstImage(contentHtml)
            ?: extractFirstImage(summaryRaw)

        return DiscoveredRawItem(
            title = title,
            link = link,
            description = cleanSummaryText(summaryRaw.ifBlank { contentHtml.orEmpty() }),
            contentHtml = contentHtml,
            author = author,
            thumbnailUrl = normalizeHttpsImage(extractedImage),
            originalPublishedRaw = publishedRaw,
            updatedRaw = updatedRaw,
            categoriesAndTags = categories,
            requiresDetailFetchForDateOrMeta = publishedRaw.isNullOrBlank()
        )
    }

    private fun readSitemapUrlEntry(parser: XmlPullParser): DiscoveredRawItem? {
        var loc = ""
        var newsTitle = ""
        var newsPubDate: String? = null
        var lastMod: String? = null
        val keywords = mutableListOf<String>()

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val rawName = parser.name.orEmpty().lowercase(Locale.ROOT)
                    val tag = localTagName(rawName)
                    when {
                        tag == "loc" && loc.isBlank() -> {
                            loc = safeReadText(parser).trim()
                            depth--
                        }
                        rawName == "news:title" || tag == "title" -> {
                            val t = cleanPlainText(safeReadText(parser))
                            if (t.isNotBlank()) newsTitle = t
                            depth--
                        }
                        rawName == "news:publication_date" || tag == "publication_date" -> {
                            val p = safeReadText(parser).trim()
                            if (p.isNotBlank()) newsPubDate = p
                            depth--
                        }
                        tag == "lastmod" -> {
                            val m = safeReadText(parser).trim()
                            if (m.isNotBlank()) lastMod = m
                            depth--
                        }
                        rawName == "news:keywords" || tag == "keywords" -> {
                            safeReadText(parser).split(',')
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .forEach { keywords.add(it) }
                            depth--
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (loc.isBlank()) return null
        return DiscoveredRawItem(
            title = newsTitle.ifBlank { loc.substringAfterLast('/').replace('-', ' ') },
            link = loc,
            originalPublishedRaw = newsPubDate,
            updatedRaw = lastMod,
            categoriesAndTags = keywords,
            requiresDetailFetchForDateOrMeta = newsPubDate.isNullOrBlank() || newsTitle.isBlank()
        )
    }

    private fun safeReadText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text.orEmpty())
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString()
    }

    private fun localTagName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return ""
        val idx = rawName.indexOf(':')
        return (if (idx >= 0) rawName.substring(idx + 1) else rawName).lowercase(Locale.ROOT)
    }

    private fun sanitizeXmlInput(raw: String): String {
        val noBom = raw.removePrefix("\uFEFF").trimStart()
        val startIdx = noBom.indexOf('<')
        if (startIdx < 0) return ""
        val trimmed = noBom.substring(startIdx)
        return buildString(trimmed.length) {
            for (ch in trimmed) {
                val valid = ch == '\u0009' || ch == '\u000A' || ch == '\u000D' ||
                    (ch in '\u0020'..'\uD7FF') || (ch in '\uE000'..'\uFFFD')
                if (valid) append(ch)
            }
        }
    }

    private fun cleanPlainText(htmlOrText: String): String {
        if (htmlOrText.isBlank()) return ""
        return runCatching { Jsoup.parse(htmlOrText).text() }
            .getOrDefault(htmlOrText)
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanSummaryText(htmlOrText: String): String {
        val plain = cleanPlainText(htmlOrText)
            .removeSuffix("বিস্তারিত")
            .removeSuffix("Details")
            .trim()
        return plain.take(400)
    }

    private fun extractFirstImage(html: String?): String? {
        if (html.isNullOrBlank() || !html.contains("<img", ignoreCase = true)) return null
        return runCatching {
            val doc = Jsoup.parseBodyFragment(html)
            val img = doc.selectFirst("img[src], img[data-src]")
            img?.attr("src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun normalizeHttpsImage(rawUrl: String?): String? {
        val url = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (url.startsWith("data:", ignoreCase = true)) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
            url.startsWith("https://", ignoreCase = true) -> url
            else -> null
        }
    }
}
