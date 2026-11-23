package bd.nib.songbadpatro.data.remote

import bd.nib.songbadpatro.domain.model.ReaderAccessStatus
import bd.nib.songbadpatro.domain.model.SourceParsingRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

data class ExtractedPageMetadata(
    val headline: String?,
    val canonicalUrl: String?,
    val authorName: String?,
    val authorAffiliation: String?,
    val authorImageUrl: String?,
    val originalPublishedRaw: String?,
    val updatedRaw: String?,
    val isExplicitlyNewlyPublished: Boolean,
    val description: String?,
    val thumbnailUrl: String?,
    val sectionName: String?,
    val tags: List<String>,
    val interviewerName: String?,
    val intervieweeName: String?,
    val intervieweeDesignation: String?,
    val hasQuestionAnswerStructure: Boolean,
    val permittedExcerptParagraphs: List<String>,
    val isFullExtractPermitted: Boolean,
    val readerAccessStatus: ReaderAccessStatus
)

/**
 * Multi-stage HTML/JSON-LD discovery and metadata extraction engine:
 * - Stage 1: Discovers article entries from permitted publisher section pages using JSON-LD
 *   (`ItemList`, `CollectionPage`, `NewsArticle`, `OpinionNewsArticle`) and section links.
 * - Stage 2: Extracts structured article metadata preferring JSON-LD -> Open Graph -> HTML meta,
 *   while strictly respecting `robots` (`noarchive`/`nosnippet`), paywalls, and copyright.
 */
class SectionAndMetadataExtractor {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val paywallSelectors = listOf(
        ".paywall", "#paywall", "[data-paywall]", ".subscription-required",
        ".subscriber-only", ".locked-content", ".premium-wall", ".metered-paywall"
    )

    private val articleBodySelectors = listOf(
        "article .story-element-text",
        ".story-content",
        ".news-details",
        ".article-content",
        ".details-body",
        ".content-details",
        ".post-content",
        ".entry-content",
        "[itemprop=articleBody]",
        "article"
    )

    private val noiseSelectors = listOf(
        "script", "style", "noscript", "iframe", "form", "nav", "header", "footer", "aside",
        ".advertisement", ".ad-container", ".ads", ".related-news", ".share-buttons",
        ".social-share", ".comments", "#comments", ".breadcrumb", ".newsletter"
    )

    fun discoverFromSectionHtml(
        html: String,
        sectionUrl: String,
        rules: SourceParsingRules = SourceParsingRules(),
        maxItems: Int = 18
    ): List<DiscoveredRawItem> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, sectionUrl)
        val includeRegex = rules.articleLinkPattern?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
        val excludeRegex = rules.excludeLinkPattern?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }

        val discoveredByUrl = LinkedHashMap<String, DiscoveredRawItem>()

        // 1. Prefer JSON-LD ItemList / NewsArticle objects embedded in section pages
        val jsonLdNodes = parseJsonLdObjects(doc)
        for (obj in jsonLdNodes) {
            extractItemsFromJsonLdNode(obj, sectionUrl).forEach { candidate ->
                if (matchesLinkRules(candidate.link, sectionUrl, includeRegex, excludeRegex)) {
                    discoveredByUrl.putIfAbsent(candidate.link, candidate)
                }
            }
        }

        // 2. Discover semantic article cards / links on the public section page
        val anchors = doc.select("article a[href], h1 a[href], h2 a[href], h3 a[href], h4 a[href], a[href]")
        for (anchor in anchors) {
            if (discoveredByUrl.size >= maxItems) break
            val absHref = anchor.absUrl("href").trim().ifBlank { anchor.attr("href").trim() }
            if (!absHref.startsWith("http")) continue
            if (!matchesLinkRules(absHref, sectionUrl, includeRegex, excludeRegex)) continue
            if (discoveredByUrl.containsKey(absHref)) continue

            val cardRoot = anchor.closest("article, li, .card, .news-item, .story-card, div")
            val rawHeadline = anchor.selectFirst("h1, h2, h3, h4")?.text()?.trim()
                ?: anchor.attr("title").trim().takeIf { it.length >= 10 }
                ?: anchor.text().trim()
            if (rawHeadline.length < 10) continue

            val cardTime = cardRoot?.selectFirst("time[datetime]")?.attr("datetime")?.trim()
                ?.takeIf { it.isNotBlank() }
            val cardAuthor = rules.authorSelector?.let { cardRoot?.selectFirst(it)?.text()?.trim() }
                ?: cardRoot?.selectFirst(".author, .writer, .byline, [rel=author]")?.text()?.trim()
            val cardImg = cardRoot?.selectFirst("img[src], img[data-src]")?.let { img ->
                img.absUrl("src").ifBlank { img.absUrl("data-src") }
            }?.takeIf { it.startsWith("http") }

            discoveredByUrl[absHref] = DiscoveredRawItem(
                title = cleanText(rawHeadline),
                link = absHref,
                author = cardAuthor?.takeIf { it.isNotBlank() },
                thumbnailUrl = cardImg?.replaceFirst("http://", "https://"),
                originalPublishedRaw = cardTime,
                requiresDetailFetchForDateOrMeta = cardTime.isNullOrBlank()
            )
        }

        return discoveredByUrl.values.take(maxItems)
    }

    fun extractArticleMetadata(html: String, pageUrl: String): ExtractedPageMetadata {
        val doc = Jsoup.parse(html, pageUrl)
        val jsonLdObjects = parseJsonLdObjects(doc)

        // Locate primary Article / NewsArticle / OpinionNewsArticle JSON-LD object
        val articleJson = jsonLdObjects.firstOrNull { obj ->
            val typeStr = jsonString(obj["@type"])?.lowercase().orEmpty()
            typeStr.contains("article") || typeStr.contains("news") || typeStr.contains("opinion")
        }

        val jsonHeadline = articleJson?.let { jsonString(it["headline"]) ?: jsonString(it["name"]) }
        val jsonDatePublished = articleJson?.let { jsonString(it["datePublished"]) ?: jsonString(it["dateCreated"]) }
        val jsonDateModified = articleJson?.let { jsonString(it["dateModified"]) }
        val jsonSection = articleJson?.let { jsonString(it["articleSection"]) }
        val jsonDescription = articleJson?.let { jsonString(it["description"]) }
        val jsonImage = articleJson?.let { extractImageFromJson(it["image"]) }
        val (jsonAuthorName, jsonAuthorJob, jsonAuthorImg) = extractAuthorFromJson(articleJson?.get("author"))
        val jsonKeywords = extractKeywordsFromJson(articleJson?.get("keywords"))

        val ogTitle = metaContent(doc, "meta[property=og:title], meta[name=twitter:title]")
        val ogDesc = metaContent(doc, "meta[property=og:description], meta[name=description], meta[name=twitter:description]")
        val ogImage = metaContent(doc, "meta[property=og:image], meta[name=twitter:image]")
        val ogPublished = metaContent(
            doc,
            "meta[property=article:published_time], meta[name=pubdate], meta[name=publish-date], meta[itemprop=datePublished]"
        )
        val ogModified = metaContent(
            doc,
            "meta[property=article:modified_time], meta[property=og:updated_time], meta[itemprop=dateModified]"
        )
        val ogSection = metaContent(doc, "meta[property=article:section], meta[name=section]")
        val htmlTimePublished = doc.selectFirst("time[itemprop=datePublished], article time[datetime], time[datetime]")
            ?.attr("datetime")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val canonical = doc.selectFirst("link[rel=canonical]")?.attr("href")?.trim()?.takeIf { it.startsWith("http") }
            ?: metaContent(doc, "meta[property=og:url]")?.takeIf { it.startsWith("http") }

        val htmlAuthor = metaContent(doc, "meta[name=author]")
            ?: doc.selectFirst("[itemprop=author] [itemprop=name], [rel=author], .author-name, .contributor-name, .writer-name, .byline")
                ?.text()
                ?.trim()
        val htmlAffiliation = doc.selectFirst(".author-designation, .author-bio, .writer-designation, .contributor-bio")
            ?.text()
            ?.trim()

        val metaTags = doc.select("meta[property=article:tag], meta[name=keywords]")
            .flatMap { el -> el.attr("content").split(',') }
            .map { cleanText(it) }
            .filter { it.length in 2..60 }

        val resolvedHeadline = cleanText(
            jsonHeadline
                ?: doc.selectFirst("article h1, h1")?.text()
                ?: ogTitle
                ?: ""
        ).takeIf { it.isNotBlank() }

        val resolvedAuthorRaw = cleanAuthorByline(jsonAuthorName ?: htmlAuthor)
        val (parsedAuthorName, parsedInlineAffiliation) = splitAuthorAndAffiliation(resolvedAuthorRaw)

        // Respect robots meta directives & paywalls strictly
        val robotsMeta = doc.select("meta[name=robots], meta[name=googlebot]")
            .joinToString(" ") { it.attr("content") }
            .lowercase()
        val noArchiveOrSnippet = robotsMeta.contains("noarchive") || robotsMeta.contains("nosnippet")
        val paywalled = isPaywalled(doc, html, articleJson)

        noiseSelectors.forEach { sel -> doc.select(sel).remove() }
        val bodyParagraphs = if (noArchiveOrSnippet || paywalled) {
            emptyList()
        } else {
            extractCleanParagraphs(doc)
        }

        val qaDetected = detectQuestionAnswerStructure(bodyParagraphs)
        val (interviewee, intervieweeRole, interviewer) = extractInterviewParticipants(
            headline = resolvedHeadline.orEmpty(),
            description = cleanText(jsonDescription ?: ogDesc ?: ""),
            paragraphs = bodyParagraphs,
            authorName = parsedAuthorName
        )

        // Copyright-respecting policy: store concise, sanitized preview paragraphs (max 4 paragraphs)
        // and clearly direct readers to the original publisher for the full copyrighted work.
        val permittedExcerpt = bodyParagraphs.take(MAX_PERMITTED_PREVIEW_PARAGRAPHS)
        val accessStatus = when {
            paywalled || noArchiveOrSnippet -> ReaderAccessStatus.PAYWALLED_OR_RESTRICTED
            permittedExcerpt.isNotEmpty() -> ReaderAccessStatus.LIVE_PERMITTED_EXTRACT
            else -> ReaderAccessStatus.METADATA_AND_SUMMARY_ONLY
        }

        val resolvedOriginalPub = jsonDatePublished ?: ogPublished ?: htmlTimePublished
        val resolvedUpdated = jsonDateModified ?: ogModified

        return ExtractedPageMetadata(
            headline = resolvedHeadline,
            canonicalUrl = canonical,
            authorName = parsedAuthorName,
            authorAffiliation = jsonAuthorJob ?: htmlAffiliation?.take(140) ?: parsedInlineAffiliation,
            authorImageUrl = normalizeHttps(jsonAuthorImg),
            originalPublishedRaw = resolvedOriginalPub,
            updatedRaw = resolvedUpdated,
            isExplicitlyNewlyPublished = html.contains("পুনঃপ্রকাশিত নয়", ignoreCase = true) ||
                html.contains("newly published edition", ignoreCase = true),
            description = cleanText(jsonDescription ?: ogDesc ?: "").take(420).ifBlank { null },
            thumbnailUrl = normalizeHttps(jsonImage ?: ogImage),
            sectionName = cleanText(jsonSection ?: ogSection ?: "").ifBlank { null },
            tags = (jsonKeywords + metaTags).distinct(),
            interviewerName = interviewer,
            intervieweeName = interviewee,
            intervieweeDesignation = intervieweeRole,
            hasQuestionAnswerStructure = qaDetected,
            permittedExcerptParagraphs = permittedExcerpt,
            isFullExtractPermitted = !paywalled && !noArchiveOrSnippet && permittedExcerpt.isNotEmpty(),
            readerAccessStatus = accessStatus
        )
    }

    fun sanitizePlainText(rawHtml: String): String =
        Jsoup.clean(rawHtml, Safelist.none()).replace(Regex("\\s+"), " ").trim()

    private fun parseJsonLdObjects(doc: Document): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        for (script in doc.select("script[type=application/ld+json]")) {
            val raw = script.data().trim()
            if (raw.isBlank()) continue
            runCatching {
                val root = json.parseToJsonElement(raw)
                collectJsonObjects(root, result)
            }
        }
        return result
    }

    private fun collectJsonObjects(element: JsonElement, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                out.add(element)
                element["@graph"]?.let { collectJsonObjects(it, out) }
            }
            is JsonArray -> element.forEach { collectJsonObjects(it, out) }
            else -> Unit
        }
    }

    private fun extractItemsFromJsonLdNode(obj: JsonObject, baseUrl: String): List<DiscoveredRawItem> {
        val items = mutableListOf<DiscoveredRawItem>()
        val typeStr = jsonString(obj["@type"])?.lowercase().orEmpty()
        if (typeStr == "itemlist" || obj.containsKey("itemListElement")) {
            val arr = obj["itemListElement"] as? JsonArray ?: return emptyList()
            for (el in arr) {
                val itemObj = (el as? JsonObject)?.let { wrapper ->
                    (wrapper["item"] as? JsonObject) ?: wrapper
                } ?: continue
                val url = jsonString(itemObj["url"]) ?: jsonString(itemObj["@id"]) ?: continue
                val title = jsonString(itemObj["headline"]) ?: jsonString(itemObj["name"]) ?: continue
                val pubDate = jsonString(itemObj["datePublished"])
                val modDate = jsonString(itemObj["dateModified"])
                val (author, _, _) = extractAuthorFromJson(itemObj["author"])
                val img = extractImageFromJson(itemObj["image"])
                if (url.startsWith("http") && title.length >= 8) {
                    items.add(
                        DiscoveredRawItem(
                            title = cleanText(title),
                            link = url,
                            description = cleanText(jsonString(itemObj["description"]).orEmpty()).take(360),
                            author = author,
                            thumbnailUrl = normalizeHttps(img),
                            originalPublishedRaw = pubDate,
                            updatedRaw = modDate,
                            requiresDetailFetchForDateOrMeta = pubDate.isNullOrBlank()
                        )
                    )
                }
            }
        } else if (typeStr.contains("article") || typeStr.contains("opinion")) {
            val url = jsonString(obj["url"]) ?: jsonString(obj["mainEntityOfPage"])
            val title = jsonString(obj["headline"]) ?: jsonString(obj["name"])
            if (!url.isNullOrBlank() && url != baseUrl && !title.isNullOrBlank()) {
                val pubDate = jsonString(obj["datePublished"])
                val (author, _, _) = extractAuthorFromJson(obj["author"])
                items.add(
                    DiscoveredRawItem(
                        title = cleanText(title),
                        link = url,
                        description = cleanText(jsonString(obj["description"]).orEmpty()).take(360),
                        author = author,
                        thumbnailUrl = normalizeHttps(extractImageFromJson(obj["image"])),
                        originalPublishedRaw = pubDate,
                        updatedRaw = jsonString(obj["dateModified"]),
                        requiresDetailFetchForDateOrMeta = pubDate.isNullOrBlank()
                    )
                )
            }
        }
        return items
    }

    private fun matchesLinkRules(
        url: String,
        sectionUrl: String,
        includeRegex: Regex?,
        excludeRegex: Regex?
    ): Boolean {
        val clean = url.substringBefore('#').trimEnd('/')
        if (clean.equals(sectionUrl.trimEnd('/'), ignoreCase = true)) return false
        if (excludeRegex != null && excludeRegex.containsMatchIn(url)) return false
        if (includeRegex != null) return includeRegex.containsMatchIn(url)
        val lower = clean.lowercase()
        if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".pdf")) return false
        return lower.contains("/opinion") || lower.contains("/editorial") ||
            lower.contains("/sub-editorial") || lower.contains("/op-ed") ||
            lower.contains("/column") || lower.contains("/interview") ||
            Regex("/\\d{5,}").containsMatchIn(lower)
    }

    private fun isPaywalled(doc: Document, rawHtml: String, articleJson: JsonObject?): Boolean {
        if (paywallSelectors.any { doc.selectFirst(it) != null }) return true
        val freeField = articleJson?.let { jsonString(it["isAccessibleForFree"]) }?.lowercase()
        if (freeField == "false") return true
        val lower = rawHtml.lowercase()
        return lower.contains("\"isaccessibleforfree\":false") ||
            lower.contains("\"isaccessibleforfree\": false")
    }

    private fun extractCleanParagraphs(doc: Document): List<String> {
        for (selector in articleBodySelectors) {
            val container = doc.select(selector)
            if (container.isEmpty()) continue
            val paragraphs = container.select("p")
                .map { sanitizePlainText(it.html()) }
                .filter { it.length >= 30 && !isBoilerplate(it) }
                .distinct()
            if (paragraphs.size >= 2 || paragraphs.sumOf { it.length } >= 200) {
                return paragraphs
            }
        }
        return emptyList()
    }

    private fun detectQuestionAnswerStructure(paragraphs: List<String>): Boolean {
        if (paragraphs.size < 2) return false
        val qaHits = paragraphs.count { p ->
            p.startsWith("প্রশ্ন:") || p.startsWith("প্রশ্ন :") ||
                p.startsWith("উত্তর:") || p.startsWith("উত্তর :") ||
                p.startsWith("প্রথম আলো:") || p.startsWith("সমকাল:") ||
                p.startsWith("ইত্তেফাক:") || p.startsWith("ডেইলি স্টার:") ||
                p.startsWith("Q:") || p.startsWith("A:") ||
                (p.contains("?") && p.length < 180)
        }
        return qaHits >= 2
    }

    private fun extractInterviewParticipants(
        headline: String,
        description: String,
        paragraphs: List<String>,
        authorName: String?
    ): Triple<String?, String?, String?> {
        var interviewee: String? = null
        var designation: String? = null
        var interviewer: String? = null

        // Bangla headline patterns e.g. "সাক্ষাৎকারে ড. দেবপ্রিয় ভট্টাচার্য: ..." or "... মুখোমুখি অধ্যাপক রেহমান সোবহান"
        val bnMatch = Regex("(?:সাক্ষাৎকারে|মুখোমুখি|আলাপচারিতায়|সাক্ষাৎকার\\s*[:—|-])\\s*([^:|—\\-।\n]{4,55})")
            .find(headline)
        if (bnMatch != null) {
            interviewee = cleanText(bnMatch.groupValues[1])
        }
        if (interviewee == null) {
            val enMatch = Regex("(?:Interview with|In conversation with|Q&A with)\\s+([^:|—\\-\n]{4,55})", RegexOption.IGNORE_CASE)
                .find(headline)
            if (enMatch != null) {
                interviewee = cleanText(enMatch.groupValues[1])
            }
        }

        val combinedIntro = (listOf(description) + paragraphs.take(2)).joinToString(" ")
        val interviewerMatch = Regex("সাক্ষাৎকার নিয়েছেন\\s+([^।,\n]{3,45})").find(combinedIntro)
            ?: Regex("Interview by\\s+([^.,\n]{3,45})", RegexOption.IGNORE_CASE).find(combinedIntro)
        if (interviewerMatch != null) {
            interviewer = cleanText(interviewerMatch.groupValues[1])
        } else if (interviewee != null && !authorName.isNullOrBlank() && authorName != interviewee) {
            interviewer = authorName
        }

        if (interviewee != null) {
            val roleMatch = Regex("${Regex.escape(interviewee)}\\s*[,—-]\\s*([^।.\n]{5,80})").find(combinedIntro)
            if (roleMatch != null) {
                designation = cleanText(roleMatch.groupValues[1])
            }
        }

        return Triple(interviewee, designation, interviewer)
    }

    private fun extractAuthorFromJson(element: JsonElement?): Triple<String?, String?, String?> {
        if (element == null) return Triple(null, null, null)
        val obj: JsonObject? = when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull() as? JsonObject
            is JsonPrimitive -> return Triple(element.contentOrNull, null, null)
        }
        if (obj == null) return Triple(null, null, null)
        val name = jsonString(obj["name"])
        val jobTitle = jsonString(obj["jobTitle"]) ?: jsonString(obj["description"])
        val img = extractImageFromJson(obj["image"])
        return Triple(name, jobTitle, img)
    }

    private fun extractImageFromJson(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> jsonString(element["url"]) ?: jsonString(element["contentUrl"])
        is JsonArray -> element.firstOrNull()?.let { extractImageFromJson(it) }
        null -> null
    }

    private fun extractKeywordsFromJson(element: JsonElement?): List<String> = when (element) {
        is JsonPrimitive -> element.contentOrNull
            ?.split(',')
            ?.map { cleanText(it) }
            ?.filter { it.length in 2..60 }
            .orEmpty()
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::cleanText) }
            .filter { it.length in 2..60 }
        else -> emptyList()
    }

    private fun jsonString(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private fun metaContent(doc: Document, cssQuery: String): String? =
        doc.selectFirst(cssQuery)?.attr("content")?.trim()?.takeIf { it.isNotBlank() }

    private fun cleanAuthorByline(raw: String?): String? {
        val cleaned = raw?.let(::cleanText)
            ?.removePrefix("লেখক:")
            ?.removePrefix("লেখক :")
            ?.removePrefix("কলামিস্ট:")
            ?.removePrefix("By ")
            ?.removePrefix("by ")
            ?.trim()
        return cleaned?.takeIf { it.length in 2..100 }
    }

    private fun splitAuthorAndAffiliation(rawAuthor: String?): Pair<String?, String?> {
        if (rawAuthor.isNullOrBlank()) return null to null
        val commaIdx = rawAuthor.indexOf(',')
        if (commaIdx > 2 && commaIdx < rawAuthor.length - 3) {
            val name = rawAuthor.substring(0, commaIdx).trim()
            val aff = rawAuthor.substring(commaIdx + 1).trim()
            return name to aff
        }
        return rawAuthor to null
    }

    private fun cleanText(text: String): String =
        text.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

    private fun normalizeHttps(url: String?): String? {
        val u = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            u.startsWith("//") -> "https:$u"
            u.startsWith("http://", ignoreCase = true) -> "https://" + u.substring(7)
            u.startsWith("https://", ignoreCase = true) -> u
            else -> null
        }
    }

    private fun isBoilerplate(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("আরও পড়ুন") ||
            lower.startsWith("read more") ||
            lower.contains("all rights reserved") ||
            lower.contains("সর্বস্বত্ব সংরক্ষিত") ||
            lower.contains("গুগল নিউজে ফলো করুন")
    }

    companion object {
        const val MAX_PERMITTED_PREVIEW_PARAGRAPHS = 4
    }
}
