package bd.nib.songbadpatro.domain.usecase

import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.SyndicatedEdition
import kotlin.math.abs

/**
 * Deduplication & Syndication Engine for My News (আমার খবর).
 *
 * Rules (Section 9):
 * 1. Same publisher (`a.publisherId == b.publisherId`):
 *    - Exact `canonicalUrl` match OR (`normalizedTitle` match / similarity >= 0.85 within 36 hours)
 *      collapses into ONE stored record (preserving bookmarks, reading history, and richer metadata).
 * 2. Different publishers (`a.publisherId != b.publisherId`):
 *    - Syndicated articles across distinct publishers are NEVER collapsed into one record.
 *    - Each publisher retains its own article record, attribution, and canonical source link,
 *      while cross-referencing the other edition in `syndicatedEditions`.
 */
class DeduplicateAndSyndicateUseCase {

    companion object {
        private const val SAME_PUBLISHER_SIMILARITY = 0.85
        private const val SYNDICATION_SIMILARITY = 0.88
        private const val SAME_PUBLISHER_WINDOW_MS = 36L * 60L * 60L * 1000L
        private const val SYNDICATION_WINDOW_MS = 48L * 60L * 60L * 1000L
    }

    fun reconcile(
        existing: List<EditorialArticle>,
        incoming: List<EditorialArticle>,
        cutoffIsoDate: String = PublicationCutoffConfig.DEFAULT_CUTOFF_ISO_DATE
    ): List<EditorialArticle> {
        val cutoffMs = PublicationCutoffConfig.cutoffEpochMillisFor(cutoffIsoDate)
        val byCanonical = LinkedHashMap<String, EditorialArticle>()

        // 1. Seed existing valid records
        for (art in existing) {
            if (art.originalPublicationDate >= cutoffMs || art.verificationStatus.name == "PENDING_DATE_VERIFICATION") {
                byCanonical[art.canonicalUrl] = art
            }
        }

        // 2. Merge incoming records respecting same-publisher deduplication
        for (candidate in incoming) {
            if (candidate.verificationStatus.name != "PENDING_DATE_VERIFICATION" &&
                candidate.originalPublicationDate < cutoffMs
            ) {
                continue
            }

            val exact = byCanonical[candidate.canonicalUrl]
            if (exact != null) {
                byCanonical[candidate.canonicalUrl] = mergeSamePublisherRecord(exact, candidate)
                continue
            }

            val samePublisherDupKey = byCanonical.entries.firstOrNull { (_, current) ->
                isSamePublisherDuplicate(current, candidate)
            }?.key

            if (samePublisherDupKey != null) {
                val primary = byCanonical.getValue(samePublisherDupKey)
                byCanonical[samePublisherDupKey] = mergeSamePublisherRecord(primary, candidate)
            } else {
                byCanonical[candidate.canonicalUrl] = candidate
            }
        }

        // 3. Cross-link syndicated editions across DIFFERENT publishers without merging them
        val list = byCanonical.values.toMutableList()
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                if (a.publisherId != b.publisherId && isSyndicatedMatch(a, b)) {
                    list[i] = attachSyndicatedLink(a, b)
                    list[j] = attachSyndicatedLink(b, a)
                }
            }
        }

        return list.sortedByDescending { it.originalPublicationDate }
    }

    private fun isSamePublisherDuplicate(a: EditorialArticle, b: EditorialArticle): Boolean {
        if (a.publisherId != b.publisherId) return false
        if (a.normalizedTitle.isBlank() || b.normalizedTitle.isBlank()) return false
        if (a.normalizedTitle == b.normalizedTitle &&
            abs(a.originalPublicationDate - b.originalPublicationDate) <= SAME_PUBLISHER_WINDOW_MS
        ) {
            return true
        }
        if (abs(a.originalPublicationDate - b.originalPublicationDate) > SAME_PUBLISHER_WINDOW_MS) {
            return false
        }
        return BanglaTitleNormalizer.similarity(a.normalizedTitle, b.normalizedTitle) >= SAME_PUBLISHER_SIMILARITY
    }

    private fun isSyndicatedMatch(a: EditorialArticle, b: EditorialArticle): Boolean {
        if (a.normalizedTitle.isBlank() || b.normalizedTitle.isBlank()) return false
        if (abs(a.originalPublicationDate - b.originalPublicationDate) > SYNDICATION_WINDOW_MS) return false
        if (a.normalizedTitle == b.normalizedTitle) return true
        return BanglaTitleNormalizer.similarity(a.normalizedTitle, b.normalizedTitle) >= SYNDICATION_SIMILARITY
    }

    private fun mergeSamePublisherRecord(primary: EditorialArticle, incoming: EditorialArticle): EditorialArticle {
        return primary.copy(
            description = if (incoming.description.length > primary.description.length) incoming.description else primary.description,
            authorId = primary.authorId ?: incoming.authorId,
            authorName = primary.authorName ?: incoming.authorName,
            authorAffiliation = primary.authorAffiliation ?: incoming.authorAffiliation,
            authorPhotograph = primary.authorPhotograph ?: incoming.authorPhotograph,
            intervieweeId = primary.intervieweeId ?: incoming.intervieweeId,
            intervieweeName = primary.intervieweeName ?: incoming.intervieweeName,
            intervieweeDesignation = primary.intervieweeDesignation ?: incoming.intervieweeDesignation,
            thumbnailUrl = primary.thumbnailUrl ?: incoming.thumbnailUrl,
            updatedDate = incoming.updatedDate ?: primary.updatedDate,
            tags = (primary.tags + incoming.tags).distinct(),
            classificationConfidence = maxOf(primary.classificationConfidence, incoming.classificationConfidence),
            lastSyncedAt = maxOf(primary.lastSyncedAt, incoming.lastSyncedAt)
        )
    }

    private fun attachSyndicatedLink(target: EditorialArticle, other: EditorialArticle): EditorialArticle {
        if (target.syndicatedEditions.any { it.publisherId == other.publisherId }) return target
        val updatedLinks = target.syndicatedEditions + SyndicatedEdition(
            publisherId = other.publisherId,
            publisherNameBn = other.publisherNameBn,
            publisherNameEn = other.publisherNameEn,
            url = other.originalArticleUrl,
            publishedAt = other.originalPublicationDate
        )
        return target.copy(syndicatedEditions = updatedLinks)
    }
}
