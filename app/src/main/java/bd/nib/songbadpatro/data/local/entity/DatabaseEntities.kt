package bd.nib.songbadpatro.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import bd.nib.songbadpatro.domain.model.ArticleVerificationStatus
import bd.nib.songbadpatro.domain.model.AuthorProfile
import bd.nib.songbadpatro.domain.model.CollectionMethod
import bd.nib.songbadpatro.domain.model.ContentLanguage
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.FeedHealthStatus
import bd.nib.songbadpatro.domain.model.FeedSource
import bd.nib.songbadpatro.domain.model.KeywordAlertRule
import bd.nib.songbadpatro.domain.model.PersonRoleType
import bd.nib.songbadpatro.domain.model.SourceParsingRules
import bd.nib.songbadpatro.domain.model.SourceVerificationStatus
import bd.nib.songbadpatro.domain.model.SyncLogEntry
import bd.nib.songbadpatro.domain.model.SyndicatedEdition

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["canonicalUrl"], unique = true),
        Index(value = ["originalPublicationDate"]),
        Index(value = ["category"]),
        Index(value = ["publisherId"]),
        Index(value = ["authorId"]),
        Index(value = ["intervieweeId"]),
        Index(value = ["verificationStatus"]),
        Index(value = ["normalizedTitle"])
    ]
)
data class ArticleEntity(
    @PrimaryKey
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
    val syndicatedEditions: List<SyndicatedEdition>,
    val classificationConfidence: Double,
    val classificationReason: String,
    val verificationStatus: ArticleVerificationStatus,
    val popularityScore: Int?,
    val createdAt: Long,
    val lastSyncedAt: Long
) {
    companion object {
        fun fromDomain(article: EditorialArticle): ArticleEntity = ArticleEntity(
            articleId = article.articleId,
            canonicalUrl = article.canonicalUrl,
            originalArticleUrl = article.originalArticleUrl,
            title = article.title,
            normalizedTitle = article.normalizedTitle,
            description = article.description,
            authorId = article.authorId,
            authorName = article.authorName,
            authorAffiliation = article.authorAffiliation,
            authorPhotograph = article.authorPhotograph,
            interviewerName = article.interviewerName,
            intervieweeId = article.intervieweeId,
            intervieweeName = article.intervieweeName,
            intervieweeDesignation = article.intervieweeDesignation,
            publisherId = article.publisherId,
            publisherNameBn = article.publisherNameBn,
            publisherNameEn = article.publisherNameEn,
            publisherLogoUrl = article.publisherLogoUrl,
            publisherWebsite = article.publisherWebsite,
            category = article.category,
            isOfficialEditorial = article.isOfficialEditorial,
            originalPublicationDate = article.originalPublicationDate,
            updatedDate = article.updatedDate,
            thumbnailUrl = article.thumbnailUrl,
            extractedContent = article.extractedContent,
            isFullExtractPermitted = article.isFullExtractPermitted,
            readerRestricted = article.readerRestricted,
            language = article.language,
            tags = article.tags,
            syndicatedEditions = article.syndicatedEditions,
            classificationConfidence = article.classificationConfidence,
            classificationReason = article.classificationReason,
            verificationStatus = article.verificationStatus,
            popularityScore = article.popularityScore,
            createdAt = article.createdAt,
            lastSyncedAt = article.lastSyncedAt
        )
    }
}

data class ArticleWithUserStateRow(
    @Embedded val article: ArticleEntity,
    val savedAt: Long?,
    val viewedAt: Long?
) {
    fun toDomain(): EditorialArticle = EditorialArticle(
        articleId = article.articleId,
        canonicalUrl = article.canonicalUrl,
        originalArticleUrl = article.originalArticleUrl,
        title = article.title,
        normalizedTitle = article.normalizedTitle,
        description = article.description,
        authorId = article.authorId,
        authorName = article.authorName,
        authorAffiliation = article.authorAffiliation,
        authorPhotograph = article.authorPhotograph,
        interviewerName = article.interviewerName,
        intervieweeId = article.intervieweeId,
        intervieweeName = article.intervieweeName,
        intervieweeDesignation = article.intervieweeDesignation,
        publisherId = article.publisherId,
        publisherNameBn = article.publisherNameBn,
        publisherNameEn = article.publisherNameEn,
        publisherLogoUrl = article.publisherLogoUrl,
        publisherWebsite = article.publisherWebsite,
        category = article.category,
        isOfficialEditorial = article.isOfficialEditorial,
        originalPublicationDate = article.originalPublicationDate,
        updatedDate = article.updatedDate,
        thumbnailUrl = article.thumbnailUrl,
        extractedContent = article.extractedContent,
        isFullExtractPermitted = article.isFullExtractPermitted,
        readerRestricted = article.readerRestricted,
        language = article.language,
        tags = article.tags,
        syndicatedEditions = article.syndicatedEditions,
        classificationConfidence = article.classificationConfidence,
        classificationReason = article.classificationReason,
        verificationStatus = article.verificationStatus,
        popularityScore = article.popularityScore,
        isBookmarked = savedAt != null,
        bookmarkedAt = savedAt,
        lastViewedAt = viewedAt,
        createdAt = article.createdAt,
        lastSyncedAt = article.lastSyncedAt
    )
}

@Entity(
    tableName = "publishers",
    indices = [
        Index(value = ["enabled"]),
        Index(value = ["verificationStatus"]),
        Index(value = ["addedAt"])
    ]
)
data class PublisherEntity(
    @PrimaryKey
    val publisherId: String,
    val nameBn: String,
    val nameEn: String,
    val website: String,
    val logoUrl: String?,
    val language: ContentLanguage,
    val enabled: Boolean,
    val verificationStatus: SourceVerificationStatus,
    val addedAt: Long
)

@Entity(
    tableName = "feed_sources",
    indices = [
        Index(value = ["publisherId"]),
        Index(value = ["enabled"]),
        Index(value = ["verificationStatus"])
    ]
)
data class FeedSourceEntity(
    @PrimaryKey
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
    val parsingRules: SourceParsingRules
) {
    fun toDomain(): FeedSource = FeedSource(
        sourceId = sourceId,
        publisherId = publisherId,
        publisherNameBn = publisherNameBn,
        publisherNameEn = publisherNameEn,
        nameBn = nameBn,
        nameEn = nameEn,
        feedUrl = feedUrl,
        sectionUrl = sectionUrl,
        defaultCategory = defaultCategory,
        supportedCategories = supportedCategories,
        language = language,
        collectionMethod = collectionMethod,
        verificationStatus = verificationStatus,
        enabled = enabled,
        lastSuccessfulSync = lastSuccessfulSync,
        lastSyncAttempt = lastSyncAttempt,
        lastError = lastError,
        healthStatus = healthStatus,
        collectedArticleCount = collectedArticleCount,
        parsingRules = parsingRules
    )
}

@Entity(
    tableName = "authors",
    indices = [
        Index(value = ["normalizedName"]),
        Index(value = ["roleType"]),
        Index(value = ["publisherId"])
    ]
)
data class AuthorEntity(
    @PrimaryKey
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
    fun toDomain(): AuthorProfile = AuthorProfile(
        authorId = authorId,
        name = name,
        normalizedName = normalizedName,
        photograph = photograph,
        designation = designation,
        affiliation = affiliation,
        publisherId = publisherId,
        publisherNameBn = publisherNameBn,
        publisherNameEn = publisherNameEn,
        biography = biography,
        roleType = roleType,
        articleCount = articleCount
    )
}

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["articleId"], unique = true),
        Index(value = ["savedAt"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val bookmarkId: Long = 0L,
    val articleId: String,
    val savedAt: Long
)

@Entity(
    tableName = "reading_history",
    indices = [
        Index(value = ["articleId"], unique = true),
        Index(value = ["viewedAt"])
    ]
)
data class ReadingHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val historyId: Long = 0L,
    val articleId: String,
    val viewedAt: Long
)

@Entity(
    tableName = "sync_logs",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["publisherId"])
    ]
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0L,
    val sourceId: String,
    val publisherId: String,
    val startedAt: Long,
    val completedAt: Long,
    val success: Boolean,
    val fetchedCount: Int,
    val acceptedCount: Int,
    val pendingCount: Int,
    val rejectedPreCutoffCount: Int,
    val rejectedNonEditorialCount: Int,
    val errorMessage: String?
) {
    fun toDomain(): SyncLogEntry = SyncLogEntry(
        logId = logId,
        sourceId = sourceId,
        publisherId = publisherId,
        startedAt = startedAt,
        completedAt = completedAt,
        success = success,
        fetchedCount = fetchedCount,
        acceptedCount = acceptedCount,
        pendingCount = pendingCount,
        rejectedPreCutoffCount = rejectedPreCutoffCount,
        rejectedNonEditorialCount = rejectedNonEditorialCount,
        errorMessage = errorMessage
    )
}

@Entity(
    tableName = "keyword_alerts",
    indices = [Index(value = ["enabled"])]
)
data class KeywordAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val keyword: String,
    val category: EditorialCategory?,
    val publisherId: String?,
    val authorNameFilter: String?,
    val enabled: Boolean,
    val createdAt: Long
) {
    fun toDomain(): KeywordAlertRule = KeywordAlertRule(
        id = id,
        keyword = keyword,
        category = category,
        publisherId = publisherId,
        authorNameFilter = authorNameFilter,
        enabled = enabled,
        createdAt = createdAt
    )
}

@Entity(
    tableName = "notified_articles",
    indices = [Index(value = ["notifiedAt"])]
)
data class NotifiedArticleEntity(
    @PrimaryKey
    val articleId: String,
    val reason: String,
    val notifiedAt: Long
)
