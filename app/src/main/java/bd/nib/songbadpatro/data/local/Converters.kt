package bd.nib.songbadpatro.data.local

import androidx.room.TypeConverter
import bd.nib.songbadpatro.domain.model.ArticleVerificationStatus
import bd.nib.songbadpatro.domain.model.CollectionMethod
import bd.nib.songbadpatro.domain.model.ContentLanguage
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.FeedHealthStatus
import bd.nib.songbadpatro.domain.model.PersonRoleType
import bd.nib.songbadpatro.domain.model.SourceParsingRules
import bd.nib.songbadpatro.domain.model.SourceVerificationStatus
import bd.nib.songbadpatro.domain.model.SyndicatedEdition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromEditorialCategory(value: EditorialCategory?): String? = value?.name

    @TypeConverter
    fun toEditorialCategory(value: String?): EditorialCategory? =
        value?.let { runCatching { EditorialCategory.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromCategoryList(value: List<EditorialCategory>): String =
        json.encodeToString(value.map { it.name })

    @TypeConverter
    fun toCategoryList(value: String): List<EditorialCategory> =
        runCatching {
            json.decodeFromString<List<String>>(value)
                .mapNotNull { name -> runCatching { EditorialCategory.valueOf(name) }.getOrNull() }
        }.getOrDefault(emptyList())

    @TypeConverter
    fun fromVerificationStatus(value: ArticleVerificationStatus): String = value.name

    @TypeConverter
    fun toVerificationStatus(value: String): ArticleVerificationStatus =
        runCatching { ArticleVerificationStatus.valueOf(value) }
            .getOrDefault(ArticleVerificationStatus.PENDING_VERIFICATION)

    @TypeConverter
    fun fromContentLanguage(value: ContentLanguage): String = value.name

    @TypeConverter
    fun toContentLanguage(value: String): ContentLanguage =
        runCatching { ContentLanguage.valueOf(value) }.getOrDefault(ContentLanguage.BANGLA)

    @TypeConverter
    fun fromCollectionMethod(value: CollectionMethod): String = value.name

    @TypeConverter
    fun toCollectionMethod(value: String): CollectionMethod =
        runCatching { CollectionMethod.valueOf(value) }.getOrDefault(CollectionMethod.RSS_ATOM)

    @TypeConverter
    fun fromSourceVerification(value: SourceVerificationStatus): String = value.name

    @TypeConverter
    fun toSourceVerification(value: String): SourceVerificationStatus =
        runCatching { SourceVerificationStatus.valueOf(value) }
            .getOrDefault(SourceVerificationStatus.REQUIRES_VERIFICATION)

    @TypeConverter
    fun fromFeedHealth(value: FeedHealthStatus): String = value.name

    @TypeConverter
    fun toFeedHealth(value: String): FeedHealthStatus =
        runCatching { FeedHealthStatus.valueOf(value) }.getOrDefault(FeedHealthStatus.NEVER_SYNCED)

    @TypeConverter
    fun fromPersonRole(value: PersonRoleType): String = value.name

    @TypeConverter
    fun toPersonRole(value: String): PersonRoleType =
        runCatching { PersonRoleType.valueOf(value) }.getOrDefault(PersonRoleType.AUTHOR)

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromSyndicatedEditions(value: List<SyndicatedEdition>): String = json.encodeToString(value)

    @TypeConverter
    fun toSyndicatedEditions(value: String): List<SyndicatedEdition> =
        runCatching { json.decodeFromString<List<SyndicatedEdition>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromParsingRules(value: SourceParsingRules): String = json.encodeToString(value)

    @TypeConverter
    fun toParsingRules(value: String): SourceParsingRules =
        runCatching { json.decodeFromString<SourceParsingRules>(value) }.getOrDefault(SourceParsingRules())
}
