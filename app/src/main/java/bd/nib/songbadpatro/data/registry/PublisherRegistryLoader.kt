package bd.nib.songbadpatro.data.registry

import android.content.Context
import bd.nib.songbadpatro.data.local.entity.FeedSourceEntity
import bd.nib.songbadpatro.data.local.entity.PublisherEntity
import bd.nib.songbadpatro.domain.model.CollectionMethod
import bd.nib.songbadpatro.domain.model.ContentLanguage
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.FeedHealthStatus
import bd.nib.songbadpatro.domain.model.SourceParsingRules
import bd.nib.songbadpatro.domain.model.SourceVerificationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RegistryManifestDto(
    val registryVersion: Int = 1,
    val defaultCutoffIsoDate: String = "2026-11-23",
    val timezone: String = "Asia/Dhaka",
    val publishers: List<RegistryPublisherDto> = emptyList()
)

@Serializable
data class RegistryPublisherDto(
    val publisherId: String,
    val nameBn: String,
    val nameEn: String,
    val website: String,
    val logoUrl: String? = null,
    val language: ContentLanguage = ContentLanguage.BANGLA,
    val enabled: Boolean = true,
    val verificationStatus: SourceVerificationStatus = SourceVerificationStatus.VERIFIED,
    val robotsNote: String? = null,
    val sources: List<RegistrySourceDto> = emptyList()
)

@Serializable
data class RegistrySourceDto(
    val sourceId: String,
    val nameBn: String,
    val nameEn: String,
    val feedUrl: String? = null,
    val sectionUrl: String? = null,
    val defaultCategory: EditorialCategory? = null,
    val supportedCategories: List<EditorialCategory> = emptyList(),
    val collectionMethod: CollectionMethod = CollectionMethod.RSS_ATOM,
    val verificationStatus: SourceVerificationStatus = SourceVerificationStatus.VERIFIED,
    val enabled: Boolean = true,
    val parsingRules: SourceParsingRules = SourceParsingRules()
)

@Singleton
class PublisherRegistryLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun loadRegistryManifest(): RegistryManifestDto {
        return runCatching {
            val raw = context.assets.open(ASSET_FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseManifestJson(raw)
        }.getOrElse {
            RegistryManifestDto()
        }
    }

    fun parseManifestJson(rawJson: String): RegistryManifestDto =
        json.decodeFromString<RegistryManifestDto>(rawJson)

    fun toSeedEntities(
        manifest: RegistryManifestDto,
        seedTimestampMs: Long = System.currentTimeMillis()
    ): Pair<List<PublisherEntity>, List<FeedSourceEntity>> {
        val publishers = mutableListOf<PublisherEntity>()
        val sources = mutableListOf<FeedSourceEntity>()

        manifest.publishers.forEachIndexed { idx, pub ->
            val safeEnabled = pub.enabled && pub.verificationStatus == SourceVerificationStatus.VERIFIED
            publishers.add(
                PublisherEntity(
                    publisherId = pub.publisherId,
                    nameBn = pub.nameBn,
                    nameEn = pub.nameEn,
                    website = pub.website,
                    logoUrl = pub.logoUrl,
                    language = pub.language,
                    enabled = safeEnabled,
                    verificationStatus = pub.verificationStatus,
                    addedAt = seedTimestampMs - (idx * 60_000L)
                )
            )
            pub.sources.forEach { src ->
                val srcEnabled = safeEnabled && src.enabled && src.verificationStatus == SourceVerificationStatus.VERIFIED
                sources.add(
                    FeedSourceEntity(
                        sourceId = src.sourceId,
                        publisherId = pub.publisherId,
                        publisherNameBn = pub.nameBn,
                        publisherNameEn = pub.nameEn,
                        nameBn = src.nameBn,
                        nameEn = src.nameEn,
                        feedUrl = src.feedUrl,
                        sectionUrl = src.sectionUrl,
                        defaultCategory = src.defaultCategory,
                        supportedCategories = src.supportedCategories.ifEmpty { EditorialCategory.entries },
                        language = pub.language,
                        collectionMethod = src.collectionMethod,
                        verificationStatus = src.verificationStatus,
                        enabled = srcEnabled,
                        lastSuccessfulSync = null,
                        lastSyncAttempt = null,
                        lastError = if (src.verificationStatus != SourceVerificationStatus.VERIFIED) pub.robotsNote else null,
                        healthStatus = if (srcEnabled) FeedHealthStatus.NEVER_SYNCED else FeedHealthStatus.DISABLED,
                        collectedArticleCount = 0,
                        parsingRules = src.parsingRules
                    )
                )
            }
        }
        return publishers to sources
    }

    companion object {
        const val ASSET_FILE_NAME = "publisher_registry.json"
    }
}
