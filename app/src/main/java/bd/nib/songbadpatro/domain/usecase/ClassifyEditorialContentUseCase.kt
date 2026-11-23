package bd.nib.songbadpatro.domain.usecase

import bd.nib.songbadpatro.domain.model.ConfidenceTier
import bd.nib.songbadpatro.domain.model.EditorialCategory
import kotlin.math.roundToInt

data class ClassificationInput(
    val title: String,
    val description: String,
    val url: String,
    val publisherNameBn: String,
    val publisherNameEn: String,
    val authorName: String? = null,
    val authorAffiliation: String? = null,
    val feedCategoriesAndTags: List<String> = emptyList(),
    val pageSectionName: String? = null,
    val hasQuestionAnswerStructure: Boolean = false,
    val sourceDefaultCategory: EditorialCategory? = null,
    val isDedicatedEditorialSource: Boolean = false,
    val forceOfficialEditorial: Boolean = false
)

data class ClassificationResult(
    val category: EditorialCategory?,
    val isOfficialEditorial: Boolean,
    val confidence: Double,
    val tier: ConfidenceTier,
    val reasonSummary: String
)

/**
 * Multi-signal rule-based classifier for My News (আমার খবর).
 *
 * Prioritizes precision over recall:
 * - Explicitly excludes breaking news, crime/accident reports, routine political reports,
 *   sports scores, entertainment news, press releases, classifieds, and sponsored content.
 * - Never classifies ordinary news as an opinion simply for containing political/analytical words.
 * - Distinguishes institutional newspaper Editorials (`EDITORIAL`) from independently authored
 *   `COLUMN` and `OPINION` pieces, and identifies `INTERVIEW` features & Q&A conversations.
 */
class ClassifyEditorialContentUseCase {

    private val hardExclusionSignals = listOf(
        "ব্রেকিং", "ব্রেকিং নিউজ", "সদ্যপ্রাপ্ত", "লাইভ আপডেট", "সরাসরি আপডেট",
        "প্রেস বিজ্ঞপ্তি", "সংবাদ বিজ্ঞপ্তি", "নিয়োগ বিজ্ঞপ্তি", "নিয়োগ বিজ্ঞপ্তি",
        "দরপত্র বিজ্ঞপ্তি", "বিজ্ঞাপন", "স্পন্সরড", "প্রচারণা মূলক",
        "breaking news", "live update", "press release", "sponsored content",
        "advertorial", "branded content", "tender notice", "job circular"
    )

    private val newsReporterBylineSignals = listOf(
        "নিজস্ব প্রতিবেদক", "স্টাফ রিপোর্টার", "ডিজিটাল রিপোর্ট", "অনলাইন ডেস্ক",
        "নিউজ ডেস্ক", "খেলা ডেস্ক", "বিনোদন ডেস্ক", "আন্তর্জাতিক ডেস্ক",
        "প্রতিনিধি", "সংবাদদাতা", "বাসস", "ইউএনবি", "রয়টার্স", "এএফপি",
        "staff correspondent", "desk report", "online desk", "news desk",
        "tribune desk", "star digital report", "tbs report", "unb", "bss"
    )

    private val hardNewsUrlSegments = listOf(
        "/crime/", "/accident/", "/sports/", "/cricket/", "/football/",
        "/entertainment/", "/showbiz/", "/lifestyle/", "/jobs/", "/chakri/",
        "/classified", "/video/", "/photo/", "/gallery/", "/weather/"
    )

    private val crimeAccidentHeadlinePatterns = listOf(
        "নিহত", "আহত", "লাশ উদ্ধার", "মরদেহ উদ্ধার", "গ্রেপ্তার", "গ্রেফতার",
        "আটক", "সড়ক দুর্ঘটনা", "সড়ক দুর্ঘটনা", "মুখোমুখি সংঘর্ষে", "বন্দুকযুদ্ধে",
        "মামলা দায়ের", "মামলা দায়ের", "কারাগারে প্রেরণ", "রিমান্ডে",
        "killed in crash", "arrested in", "body recovered", "sent to jail", "remanded"
    )

    private val sportsScorePatterns = listOf(
        "উইকেটে জয়", "উইকেটে জয়", "রানে জয়", "রানে জয়", "গোলে জয়", "গোলে জয়",
        "টসে জিতে", "সেঞ্চুরি করলেন", "পয়েন্ট টেবিল", "পয়েন্ট টেবিল",
        "won by", "wickets", "match highlights", "live score"
    )

    private val routinePoliticalBriefingPatterns = listOf(
        "সংবাদ সম্মেলনে বললেন", "মানববন্ধনে বক্তারা", "বিক্ষোভ মিছিল", "স্মারকলিপি প্রদান",
        "কর্মসূচি ঘোষণা", "ফেসবুক পোস্টে", "ভেরিফায়েড ফেসবুক", "শোক প্রকাশ"
    )

    private val officialEditorialAuthors = listOf(
        "সম্পাদকীয়", "সম্পাদকীয়", "সম্পাদকীয় পর্ষদ", "সম্পাদকীয় পর্ষদ",
        "সম্পাদকমণ্ডলী", "editorial", "editorial board", "editorial desk"
    )

    private val columnistCredentialsSignals = listOf(
        "অধ্যাপক", "ড.", "ডক্টর", "গবেষক", "অর্থনীতিবিদ", "কলামিস্ট", "বিশ্লেষক",
        "সাবেক রাষ্ট্রদূত", "আইনজীবী", "প্রাবন্ধিক", "শিক্ষাবিদ", "লেখক",
        "professor", "dr.", "economist", "columnist", "researcher", "analyst", "fellow"
    )

    fun classify(input: ClassificationInput): ClassificationResult {
        val reasons = mutableListOf<String>()
        val normTitle = BanglaTitleNormalizer.normalize(input.title)
        val normDesc = BanglaTitleNormalizer.normalize(input.description)
        val lowerUrl = input.url.lowercase()
        val lowerAuthor = input.authorName?.trim()?.lowercase().orEmpty()
        val lowerAff = input.authorAffiliation?.trim()?.lowercase().orEmpty()
        val allTags = (input.feedCategoriesAndTags + listOfNotNull(input.pageSectionName))
            .map { BanglaTitleNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
        val joinedTags = allTags.joinToString(" | ")

        // 1. Hard Exclusions: Breaking news, press releases, ads, classifieds
        for (signal in hardExclusionSignals) {
            val normSig = BanglaTitleNormalizer.normalize(signal)
            if (normTitle.contains(normSig) || joinedTags.contains(normSig)) {
                return rejected("Excluded non-editorial signal: '$signal'")
            }
        }

        if (hardNewsUrlSegments.any { lowerUrl.contains(it) }) {
            return rejected("Excluded general-news URL path segment")
        }

        val hasInterviewSignal = hasStrongInterviewSignals(normTitle, normDesc, lowerUrl, joinedTags, input.hasQuestionAnswerStructure)

        // Reporter / Desk byline check (unless it is an explicit Interview)
        if (!hasInterviewSignal && lowerAuthor.isNotBlank()) {
            val matchedReporter = newsReporterBylineSignals.firstOrNull { sig ->
                lowerAuthor.contains(sig.lowercase())
            }
            if (matchedReporter != null) {
                return rejected("Excluded news desk/correspondent byline: '$matchedReporter'")
            }
        }

        // Crime/accident & sports match exclusions when not from a dedicated editorial feed
        if (!input.isDedicatedEditorialSource) {
            if (crimeAccidentHeadlinePatterns.any { normTitle.contains(BanglaTitleNormalizer.normalize(it)) }) {
                return rejected("Excluded crime/accident report headline")
            }
            if (sportsScorePatterns.any { normTitle.contains(BanglaTitleNormalizer.normalize(it)) }) {
                return rejected("Excluded sports match report")
            }
            if (!hasInterviewSignal && routinePoliticalBriefingPatterns.any {
                    normTitle.contains(BanglaTitleNormalizer.normalize(it)) ||
                        normDesc.contains(BanglaTitleNormalizer.normalize(it))
                }
            ) {
                return rejected("Excluded routine political/social-media news report")
            }
        }

        // 2. Score the four eligible categories: INTERVIEW, EDITORIAL, COLUMN, OPINION
        var interviewScore = 0.0
        var editorialScore = 0.0
        var columnScore = 0.0
        var opinionScore = 0.0

        // --- A. INTERVIEW SIGNALS ---
        if (lowerUrl.contains("/interview") || lowerUrl.contains("/interviews") || lowerUrl.contains("/sakkhatkar")) {
            interviewScore += 0.65
            reasons.add("URL:/interview")
        }
        if (containsAny(joinedTags, listOf("সাক্ষাৎকার", "মুখোমুখি", "আলাপচারিতা", "কথোপকথন", "interview", "interviews", "q a"))) {
            interviewScore += 0.60
            reasons.add("Tag:Interview")
        }
        if (containsAny(normTitle, listOf("সাক্ষাৎকার", "সাক্ষাৎকারে", "মুখোমুখি", "আলাপচারিতায়", "একান্ত আলাপে", "interview with", "in conversation with", "q a"))) {
            interviewScore += 0.45
            reasons.add("Headline:Interview")
        }
        if (input.hasQuestionAnswerStructure) {
            interviewScore += 0.40
            reasons.add("Body:Q&A-structure")
        }
        if (input.sourceDefaultCategory == EditorialCategory.INTERVIEW && input.isDedicatedEditorialSource) {
            interviewScore += 0.50
            reasons.add("Source:DedicatedInterviewFeed")
        }

        // --- B. OFFICIAL EDITORIAL SIGNALS ---
        val isEditorialAuthor = lowerAuthor.isBlank() ||
            officialEditorialAuthors.any { lowerAuthor == it || lowerAuthor.contains(it) } ||
            lowerAuthor.equals(input.publisherNameBn.trim().lowercase(), ignoreCase = true) ||
            lowerAuthor.equals(input.publisherNameEn.trim().lowercase(), ignoreCase = true)

        val hasIndependentPersonAuthor = lowerAuthor.isNotBlank() && !isEditorialAuthor

        if (input.forceOfficialEditorial) {
            editorialScore += 0.85
            reasons.add("Source:OfficialEditorialFeed")
        }
        if (lowerUrl.contains("/editorial/") || lowerUrl.endsWith("/editorial") || lowerUrl.contains("/sampadakiyo")) {
            if (!hasIndependentPersonAuthor) {
                editorialScore += 0.65
                reasons.add("URL:/editorial+InstitutionalAuthor")
            } else {
                // Sub-editorial or guest column hosted under /editorial/ section (common in BD print dailies)
                columnScore += 0.45
                opinionScore += 0.35
                reasons.add("URL:/editorial+NamedAuthor")
            }
        }
        if (allTags.any { it == "সম্পাদকীয়" || it == "editorial" }) {
            if (!hasIndependentPersonAuthor) {
                editorialScore += 0.65
                reasons.add("Tag:OfficialEditorial")
            } else {
                columnScore += 0.40
                reasons.add("Tag:EditorialSection+NamedAuthor")
            }
        }
        if (officialEditorialAuthors.any { lowerAuthor == it }) {
            editorialScore += 0.55
            reasons.add("Byline:EditorialBoard")
        }
        if (normTitle.startsWith("সম্পাদকীয় ") || normTitle.startsWith("editorial ")) {
            editorialScore += 0.45
            reasons.add("Headline:EditorialPrefix")
        }

        // --- C. COLUMN SIGNALS ---
        if (lowerUrl.contains("/column") || lowerUrl.contains("/columns") ||
            lowerUrl.contains("/sub-editorial") || lowerUrl.contains("/post-editorial") ||
            lowerUrl.contains("/uposampadakiyo")
        ) {
            columnScore += 0.65
            reasons.add("URL:/column-or-subeditorial")
        }
        if (containsAny(joinedTags, listOf("কলাম", "উপসম্পাদকীয়", "উপসম্পাদকীয়", "সুবর্ণরেখা", "সময় ও সমাজ", "column", "columns", "sub editorial"))) {
            columnScore += 0.65
            reasons.add("Tag:Column")
        }
        if (containsAny(normTitle, listOf("কলাম ", "উপসম্পাদকীয় ", "column "))) {
            columnScore += 0.45
            reasons.add("Headline:Column")
        }
        if (hasIndependentPersonAuthor && (
                columnistCredentialsSignals.any { lowerAuthor.contains(it.lowercase()) || lowerAff.contains(it.lowercase()) } ||
                    lowerAff.length >= 6
                )
        ) {
            columnScore += 0.25
            reasons.add("Author:ColumnistCredentials")
        }
        if (input.sourceDefaultCategory == EditorialCategory.COLUMN && input.isDedicatedEditorialSource) {
            columnScore += 0.50
            reasons.add("Source:DedicatedColumnFeed")
        }

        // --- D. OPINION & COMMENTARY SIGNALS ---
        if (lowerUrl.contains("/opinion") || lowerUrl.contains("/op-ed") || lowerUrl.contains("/oped") ||
            lowerUrl.contains("/thoughts") || lowerUrl.contains("/views") || lowerUrl.contains("/perspectives") ||
            lowerUrl.contains("/muktadhara") || lowerUrl.contains("/muktomot") || lowerUrl.contains("/commentary") ||
            lowerUrl.contains("/longform")
        ) {
            opinionScore += 0.58
            reasons.add("URL:/opinion-or-commentary")
        }
        if (containsAny(
                joinedTags,
                listOf("মতামত", "মুক্তমত", "মুক্তধারা", "চিন্তা", "দৃষ্টিকোণ", "মত মতান্তর", "অভিমত", "প্রতিক্রিয়া", "পাঠকের মত", "opinion", "op ed", "thoughts", "views", "commentary", "perspective", "longform")
            )
        ) {
            opinionScore += 0.58
            reasons.add("Tag:Opinion")
        }
        if (containsAny(normTitle, listOf("মতামত ", "মুক্তমত ", "দৃষ্টিকোণ ", "বিশ্লেষণ ", "opinion ", "op ed ", "perspective "))) {
            opinionScore += 0.35
            reasons.add("Headline:OpinionPrefix")
        }
        if (input.isDedicatedEditorialSource) {
            when (input.sourceDefaultCategory) {
                EditorialCategory.OPINION -> {
                    opinionScore += 0.42
                    if (hasIndependentPersonAuthor && lowerAff.isNotBlank()) columnScore += 0.20
                    reasons.add("Source:DedicatedOpinionSource")
                }
                EditorialCategory.EDITORIAL -> {
                    if (!hasIndependentPersonAuthor) {
                        editorialScore += 0.45
                        reasons.add("Source:DedicatedEditorialSource")
                    } else {
                        columnScore += 0.42
                        reasons.add("Source:EditorialPageNamedColumnist")
                    }
                }
                else -> Unit
            }
        }

        // Author bonus when inside an opinion/editorial context
        val maxContextualScore = maxOf(interviewScore, editorialScore, columnScore, opinionScore)
        if (maxContextualScore >= 0.40 && hasIndependentPersonAuthor) {
            if (columnScore >= opinionScore || lowerAff.isNotBlank() ||
                columnistCredentialsSignals.any { lowerAuthor.contains(it.lowercase()) }
            ) {
                columnScore += 0.20
            } else {
                opinionScore += 0.20
            }
        }

        // Determine winning category
        val candidates = listOf(
            EditorialCategory.INTERVIEW to interviewScore,
            EditorialCategory.EDITORIAL to editorialScore,
            EditorialCategory.COLUMN to columnScore,
            EditorialCategory.OPINION to opinionScore
        ).sortedByDescending { it.second }

        val (bestCategory, rawBestScore) = candidates.first()
        val clampedConfidence = ((rawBestScore.coerceIn(0.0, 0.99)) * 100.0).roundToInt() / 100.0

        val tier = when {
            clampedConfidence >= HIGH_CONFIDENCE_THRESHOLD -> ConfidenceTier.HIGH
            clampedConfidence >= MEDIUM_CONFIDENCE_THRESHOLD -> ConfidenceTier.MEDIUM
            else -> ConfidenceTier.LOW
        }

        if (tier == ConfidenceTier.LOW) {
            return ClassificationResult(
                category = null,
                isOfficialEditorial = false,
                confidence = clampedConfidence,
                tier = ConfidenceTier.LOW,
                reasonSummary = "Insufficient editorial signals (score=$clampedConfidence)"
            )
        }

        val officialEditorial = bestCategory == EditorialCategory.EDITORIAL && !hasIndependentPersonAuthor
        return ClassificationResult(
            category = bestCategory,
            isOfficialEditorial = officialEditorial,
            confidence = clampedConfidence,
            tier = tier,
            reasonSummary = reasons.distinct().joinToString(", ").ifBlank { "Matched $bestCategory" }
        )
    }

    private fun hasStrongInterviewSignals(
        normTitle: String,
        normDesc: String,
        lowerUrl: String,
        joinedTags: String,
        hasQaBody: Boolean
    ): Boolean {
        return lowerUrl.contains("/interview") ||
            joinedTags.contains("সাক্ষাৎকার") ||
            joinedTags.contains("interview") ||
            normTitle.contains("সাক্ষাৎকার") ||
            normTitle.contains("মুখোমুখি") ||
            normTitle.contains("interview with") ||
            (hasQaBody && normDesc.contains("প্রশ্ন"))
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean {
        if (haystack.isBlank()) return false
        return needles.any { needle -> haystack.contains(needle) }
    }

    private fun rejected(reason: String) = ClassificationResult(
        category = null,
        isOfficialEditorial = false,
        confidence = 0.05,
        tier = ConfidenceTier.LOW,
        reasonSummary = reason
    )

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.75
        const val MEDIUM_CONFIDENCE_THRESHOLD = 0.45
    }
}
