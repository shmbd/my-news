package bd.nib.songbadpatro.domain.model

import kotlinx.serialization.Serializable

/**
 * Strictly the ONLY four eligible content categories in My News (আমার খবর).
 * General news, breaking news, crime, sports, entertainment, and press releases are excluded.
 */
@Serializable
enum class EditorialCategory(
    val titleBn: String,
    val titleEn: String,
    val descriptionBn: String,
    val descriptionEn: String
) {
    COLUMN(
        titleBn = "কলাম",
        titleEn = "Columns",
        descriptionBn = "সাংবাদিক, গবেষক, শিক্ষাবিদ ও বিশ্লেষকদের নিয়মিত ও বিশেষ কলাম",
        descriptionEn = "Regular and guest columns by journalists, academics, economists, and analysts"
    ),
    EDITORIAL(
        titleBn = "সম্পাদকীয়",
        titleEn = "Editorials",
        descriptionBn = "সংবাদপত্র ও প্রকাশনা প্রতিষ্ঠানের প্রাতিষ্ঠানিক সম্পাদকীয় অবস্থান",
        descriptionEn = "Official institutional editorials representing the newspaper's editorial board"
    ),
    OPINION(
        titleBn = "মতামত",
        titleEn = "Opinions",
        descriptionBn = "মুক্তমত, অতিথি নিবন্ধ, সমসাময়িক ভাষ্য, দৃষ্টিকোণ ও গভীর বিশ্লেষণ",
        descriptionEn = "Opinion pieces, commentary, perspectives, and analytical essays"
    ),
    INTERVIEW(
        titleBn = "সাক্ষাৎকার",
        titleEn = "Interviews",
        descriptionBn = "বিশেষজ্ঞ, নীতিনির্ধারক, শিক্ষাবিদ ও বিশিষ্টজনদের প্রশ্নোত্তর ও দীর্ঘ আলাপচারিতা",
        descriptionEn = "Interviews, Q&A features, and long-form conversations with notable figures"
    );

    fun displayTitle(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) titleEn else titleBn

    fun displayDescription(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) descriptionEn else descriptionBn
}

@Serializable
enum class ArticleVerificationStatus(val labelBn: String, val labelEn: String) {
    PUBLISHED("প্রকাশিত", "Published"),
    PENDING_VERIFICATION("বিভাগ যাচাই অপেক্ষমাণ", "Pending Classification Review"),
    PENDING_DATE_VERIFICATION("তারিখ যাচাই অপেক্ষমাণ", "Pending Date Verification");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

@Serializable
enum class ConfidenceTier {
    HIGH,
    MEDIUM,
    LOW
}

@Serializable
enum class ContentLanguage(val code: String, val labelBn: String, val labelEn: String) {
    BANGLA("bn", "বাংলা", "Bangla"),
    ENGLISH("en", "ইংরেজি", "English");

    fun displayLabel(appLanguage: AppLanguage): String =
        if (appLanguage == AppLanguage.ENGLISH) labelEn else labelBn

    companion object {
        fun fromCode(code: String?): ContentLanguage =
            if (code?.lowercase()?.startsWith("en") == true) ENGLISH else BANGLA
    }
}

@Serializable
enum class CollectionMethod(val labelBn: String, val labelEn: String) {
    RSS_ATOM("অফিসিয়াল RSS/Atom ফিড", "Official RSS/Atom Feed"),
    SECTION_HTML_JSONLD("অনুমোদিত বিভাগীয় পাতা (JSON-LD/HTML)", "Permitted Section Page (JSON-LD/HTML)"),
    SITEMAP("প্রকাশক সাইটম্যাপ", "Publisher Sitemap");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

@Serializable
enum class SourceVerificationStatus(val labelBn: String, val labelEn: String) {
    VERIFIED("যাচাইকৃত ও অনুমোদিত", "Verified & Permitted"),
    REQUIRES_VERIFICATION("যাচাই প্রয়োজন (নিষ্ক্রিয়)", "Requires Verification (Disabled)"),
    DISABLED_BY_POLICY("প্রকাশক নীতিমালায় নিষ্ক্রিয়", "Disabled by Publisher Policy");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

@Serializable
enum class FeedHealthStatus(val labelBn: String, val labelEn: String) {
    HEALTHY("সচল", "Healthy"),
    ERROR("ত্রুটি", "Error"),
    NEVER_SYNCED("সিঙ্ক হয়নি", "Not Synced Yet"),
    DISABLED("নিষ্ক্রিয়", "Disabled");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

@Serializable
enum class PersonRoleType(val labelBn: String, val labelEn: String) {
    AUTHOR("কলামিস্ট ও লেখক", "Columnist & Author"),
    INTERVIEWEE("সাক্ষাৎকারদাতা", "Interviewee"),
    BOTH("লেখক ও সাক্ষাৎকারদাতা", "Author & Interviewee");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

@Serializable
data class SourceParsingRules(
    val articleLinkPattern: String? = null,
    val excludeLinkPattern: String? = null,
    val authorSelector: String? = null,
    val dateSelector: String? = null,
    val bodySelector: String? = null,
    val isDedicatedEditorialFeed: Boolean = false,
    val forceOfficialEditorial: Boolean = false
)

@Serializable
data class SyndicatedEdition(
    val publisherId: String,
    val publisherNameBn: String,
    val publisherNameEn: String,
    val url: String,
    val publishedAt: Long
)

data class EditorialArticle(
    val articleId: String,
    val canonicalUrl: String,
    val originalArticleUrl: String,
    val title: String,
    val normalizedTitle: String,
    val description: String,
    val authorId: String?,
    val authorName: String?,
    val authorAffiliation: String?,
    val authorPhotograph: String?,
    val interviewerName: String?,
    val intervieweeId: String?,
    val intervieweeName: String?,
    val intervieweeDesignation: String?,
    val publisherId: String,
    val publisherNameBn: String,
    val publisherNameEn: String,
    val publisherLogoUrl: String?,
    val publisherWebsite: String,
    val category: EditorialCategory,
    val isOfficialEditorial: Boolean,
    val originalPublicationDate: Long,
    val updatedDate: Long?,
    val thumbnailUrl: String?,
    val extractedContent: String?,
    val isFullExtractPermitted: Boolean,
    val readerRestricted: Boolean,
    val language: ContentLanguage,
    val tags: List<String>,
    val syndicatedEditions: List<SyndicatedEdition> = emptyList(),
    val classificationConfidence: Double,
    val classificationReason: String,
    val verificationStatus: ArticleVerificationStatus,
    val popularityScore: Int? = null,
    val isBookmarked: Boolean = false,
    val bookmarkedAt: Long? = null,
    val lastViewedAt: Long? = null,
    val createdAt: Long,
    val lastSyncedAt: Long
) {
    fun displayPublisherName(appLanguage: AppLanguage): String =
        if (appLanguage == AppLanguage.ENGLISH) publisherNameEn else publisherNameBn

    fun primaryContributorLabel(appLanguage: AppLanguage): String? {
        return when (category) {
            EditorialCategory.EDITORIAL -> {
                if (isOfficialEditorial || authorName.isNullOrBlank()) {
                    if (appLanguage == AppLanguage.ENGLISH) {
                        "Editorial Board • $publisherNameEn"
                    } else {
                        "সম্পাদকীয় পর্ষদ • $publisherNameBn"
                    }
                } else {
                    authorName
                }
            }
            EditorialCategory.INTERVIEW -> {
                intervieweeName?.takeIf { it.isNotBlank() } ?: authorName
            }
            EditorialCategory.COLUMN, EditorialCategory.OPINION -> {
                authorName?.takeIf { it.isNotBlank() }
            }
        }
    }
}

data class Publisher(
    val publisherId: String,
    val nameBn: String,
    val nameEn: String,
    val website: String,
    val logoUrl: String?,
    val language: ContentLanguage,
    val enabled: Boolean,
    val verificationStatus: SourceVerificationStatus,
    val supportedCategories: List<EditorialCategory>,
    val collectionMethods: List<CollectionMethod>,
    val lastSuccessfulSync: Long?,
    val lastError: String?,
    val healthStatus: FeedHealthStatus,
    val collectedArticleCount: Int,
    val addedAt: Long
) {
    fun displayName(appLanguage: AppLanguage): String =
        if (appLanguage == AppLanguage.ENGLISH) nameEn else nameBn
}

data class FeedSource(
    val sourceId: String,
    val publisherId: String,
    val publisherNameBn: String,
    val publisherNameEn: String,
    val nameBn: String,
    val nameEn: String,
    val feedUrl: String?,
    val sectionUrl: String?,
    val defaultCategory: EditorialCategory?,
    val supportedCategories: List<EditorialCategory>,
    val language: ContentLanguage,
    val collectionMethod: CollectionMethod,
    val verificationStatus: SourceVerificationStatus,
    val enabled: Boolean,
    val lastSuccessfulSync: Long?,
    val lastSyncAttempt: Long?,
    val lastError: String?,
    val healthStatus: FeedHealthStatus,
    val collectedArticleCount: Int,
    val parsingRules: SourceParsingRules = SourceParsingRules()
) {
    val primaryEndpointUrl: String
        get() = feedUrl?.takeIf { it.isNotBlank() } ?: sectionUrl.orEmpty()
}

data class AuthorProfile(
    val authorId: String,
    val name: String,
    val normalizedName: String,
    val photograph: String?,
    val designation: String?,
    val affiliation: String?,
    val publisherId: String?,
    val publisherNameBn: String?,
    val publisherNameEn: String?,
    val biography: String?,
    val roleType: PersonRoleType,
    val articleCount: Int
) {
    fun displayPublisher(appLanguage: AppLanguage): String? =
        if (appLanguage == AppLanguage.ENGLISH) publisherNameEn ?: publisherNameBn else publisherNameBn ?: publisherNameEn
}

enum class ArticleSortOption(val labelBn: String, val labelEn: String) {
    NEWEST_FIRST("নতুন থেকে পুরোনো", "Newest First"),
    OLDEST_FIRST("পুরোনো থেকে নতুন", "Oldest First"),
    PUBLISHER("প্রকাশক অনুযায়ী", "By Publisher"),
    AUTHOR("লেখক অনুযায়ী", "By Author");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

data class SearchFilter(
    val query: String = "",
    val categories: Set<EditorialCategory> = emptySet(),
    val publisherId: String? = null,
    val authorQuery: String = "",
    val fromDateEpochMs: Long? = null,
    val toDateEpochMs: Long? = null,
    val language: ContentLanguage? = null,
    val sortOption: ArticleSortOption = ArticleSortOption.NEWEST_FIRST
)
