package bd.nib.songbadpatro.domain.usecase

import java.security.MessageDigest

/**
 * Resolves Author & Interviewee identities while avoiding accidental merging of
 * different people who happen to share similar names across different publishers/affiliations.
 */
object AuthorIdentityResolver {

    private val institutionalLabels = setOf(
        "সম্পাদকীয়", "সম্পাদকীয়", "সম্পাদকীয় পর্ষদ", "সম্পাদকীয় পর্ষদ",
        "editorial", "editorial board", "editorial desk"
    )

    fun isInstitutionalByline(name: String?, publisherNameBn: String, publisherNameEn: String): Boolean {
        if (name.isNullOrBlank()) return true
        val cleaned = name.trim().lowercase()
        return cleaned in institutionalLabels ||
            cleaned == publisherNameBn.trim().lowercase() ||
            cleaned == publisherNameEn.trim().lowercase()
    }

    fun normalizePersonName(rawName: String): String {
        return BanglaTitleNormalizer.normalize(rawName)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Generates a deterministic `authorId`:
     * - If a specific affiliation/designation is known (length >= 5), scopes identity to
     *   `(normalizedName | normalizedAffiliation)` so the same scholar writing in two newspapers
     *   with the same university affiliation matches reliably while another person with a
     *   different affiliation stays separate.
     * - If no affiliation is known, scopes identity to `(normalizedName | publisherId)` so two
     *   different contributors with a common name (e.g., "মোহাম্মদ আলী") at different newspapers
     *   are NOT falsely merged into a single profile.
     */
    fun resolvePersonId(
        name: String,
        affiliationOrDesignation: String?,
        publisherId: String
    ): String {
        val normName = normalizePersonName(name)
        val normAff = affiliationOrDesignation
            ?.let { BanglaTitleNormalizer.normalize(it) }
            ?.takeIf { it.length >= 5 }

        val key = if (normAff != null) {
            "person|$normName|aff:$normAff"
        } else {
            "person|$normName|pub:$publisherId"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }
}
