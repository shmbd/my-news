package bd.nib.songbadpatro.domain.usecase

import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.AlertCandidate
import bd.nib.songbadpatro.domain.model.ArticleSortOption
import bd.nib.songbadpatro.domain.model.ArticleVerificationStatus
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.KeywordAlertRule
import bd.nib.songbadpatro.domain.model.NotificationFrequency
import bd.nib.songbadpatro.domain.model.SearchFilter
import bd.nib.songbadpatro.domain.model.UserPreferences

class SortAndFilterArticlesUseCase {

    fun applyFilterAndSort(
        articles: List<EditorialArticle>,
        filter: SearchFilter,
        cutoffIsoDate: String = PublicationCutoffConfig.DEFAULT_CUTOFF_ISO_DATE
    ): List<EditorialArticle> {
        val cutoffEpochMs = PublicationCutoffConfig.cutoffEpochMillisFor(cutoffIsoDate)
        val effectiveFrom = maxOf(cutoffEpochMs, filter.fromDateEpochMs ?: cutoffEpochMs)
        val normQuery = BanglaTitleNormalizer.normalize(filter.query)
        val normAuthorQuery = BanglaTitleNormalizer.normalize(filter.authorQuery)

        val filtered = articles.filter { article ->
            article.verificationStatus == ArticleVerificationStatus.PUBLISHED &&
                article.originalPublicationDate >= effectiveFrom &&
                (filter.toDateEpochMs == null || article.originalPublicationDate <= filter.toDateEpochMs) &&
                (filter.categories.isEmpty() || article.category in filter.categories) &&
                (filter.publisherId == null || article.publisherId == filter.publisherId) &&
                (filter.language == null || article.language == filter.language) &&
                (normAuthorQuery.isBlank() ||
                    BanglaTitleNormalizer.normalize(article.authorName.orEmpty()).contains(normAuthorQuery) ||
                    BanglaTitleNormalizer.normalize(article.intervieweeName.orEmpty()).contains(normAuthorQuery)) &&
                (normQuery.isBlank() || matchesQuery(article, normQuery, filter.query.trim()))
        }

        return sort(filtered, filter.sortOption)
    }

    fun sort(articles: List<EditorialArticle>, sortOption: ArticleSortOption): List<EditorialArticle> {
        return when (sortOption) {
            ArticleSortOption.NEWEST_FIRST -> articles.sortedByDescending { it.originalPublicationDate }
            ArticleSortOption.OLDEST_FIRST -> articles.sortedBy { it.originalPublicationDate }
            ArticleSortOption.PUBLISHER -> articles.sortedWith(
                compareBy<EditorialArticle> { it.publisherNameBn }
                    .thenByDescending { it.originalPublicationDate }
            )
            ArticleSortOption.AUTHOR -> articles.sortedWith(
                compareBy<EditorialArticle> {
                    it.authorName?.takeIf { a -> a.isNotBlank() }
                        ?: it.intervieweeName?.takeIf { p -> p.isNotBlank() }
                        ?: "zzz"
                }.thenByDescending { it.originalPublicationDate }
            )
        }
    }

    private fun matchesQuery(article: EditorialArticle, normQuery: String, rawQuery: String): Boolean {
        val lowerRaw = rawQuery.lowercase()
        return article.normalizedTitle.contains(normQuery) ||
            article.title.lowercase().contains(lowerRaw) ||
            article.description.lowercase().contains(lowerRaw) ||
            article.authorName?.lowercase()?.contains(lowerRaw) == true ||
            article.intervieweeName?.lowercase()?.contains(lowerRaw) == true ||
            article.publisherNameBn.lowercase().contains(lowerRaw) ||
            article.publisherNameEn.lowercase().contains(lowerRaw) ||
            article.tags.any { it.lowercase().contains(lowerRaw) } ||
            PublicationCutoffConfig.formatIsoDateInDhaka(article.originalPublicationDate).contains(lowerRaw)
    }
}

/**
 * Evaluates newly published editorial articles against user notification preferences
 * (categories, preferred publishers, preferred authors, keyword alerts, quiet hours,
 * frequency limits, and strict notification deduplication).
 *
 * NOTE: Never sends breaking-news notifications (out of scope for My News).
 */
class EditorialAlertMatcher {

    fun findNotificationCandidates(
        newlyPublished: List<EditorialArticle>,
        keywordRules: List<KeywordAlertRule>,
        alreadyNotifiedIds: Set<String>,
        preferences: UserPreferences,
        currentHour24: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): List<AlertCandidate> {
        if (!preferences.notificationsEnabled) return emptyList()
        if (preferences.notificationFrequency == NotificationFrequency.OFF) return emptyList()
        if (preferences.quietHoursEnabled && isWithinQuietHours(currentHour24, preferences.quietStartHour, preferences.quietEndHour)) {
            return emptyList()
        }
        if (!isFrequencyWindowOpen(preferences.notificationFrequency, preferences.lastNotificationSentAt, nowMillis)) {
            return emptyList()
        }

        val cutoffMs = PublicationCutoffConfig.cutoffEpochMillisFor(preferences.publicationCutoffIsoDate)
        val activeKeywordRules = keywordRules.filter { it.enabled && it.keyword.isNotBlank() }
        val results = mutableListOf<AlertCandidate>()

        for (article in newlyPublished) {
            if (article.articleId in alreadyNotifiedIds) continue
            if (article.verificationStatus != ArticleVerificationStatus.PUBLISHED) continue
            if (article.originalPublicationDate < cutoffMs) continue

            // 1. Check specific keyword / author rules first
            val matchedRule = activeKeywordRules.firstOrNull { rule -> matchesRule(article, rule) }
            if (matchedRule != null) {
                results.add(
                    AlertCandidate(
                        article = article,
                        matchedReasonBn = "কীওয়ার্ড অ্যালার্ট: ${matchedRule.keyword}",
                        matchedReasonEn = "Keyword Alert: ${matchedRule.keyword}",
                        isKeywordOrAuthorAlert = true
                    )
                )
                continue
            }

            // 2. Check preferred authors
            val matchedAuthor = preferences.preferredAuthorNames.firstOrNull { prefAuthor ->
                prefAuthor.isNotBlank() && (
                    article.authorName?.contains(prefAuthor, ignoreCase = true) == true ||
                        article.intervieweeName?.contains(prefAuthor, ignoreCase = true) == true
                    )
            }
            if (matchedAuthor != null) {
                results.add(
                    AlertCandidate(
                        article = article,
                        matchedReasonBn = "পছন্দের লেখক: $matchedAuthor",
                        matchedReasonEn = "Preferred Author: $matchedAuthor",
                        isKeywordOrAuthorAlert = true
                    )
                )
                continue
            }

            // 3. Category & preferred publisher notifications
            val categoryAllowed = article.category in preferences.notifyCategories
            val publisherAllowed = preferences.preferredPublisherIds.isEmpty() ||
                article.publisherId in preferences.preferredPublisherIds
            if (categoryAllowed && publisherAllowed) {
                results.add(
                    AlertCandidate(
                        article = article,
                        matchedReasonBn = "নতুন ${article.category.titleBn} • ${article.publisherNameBn}",
                        matchedReasonEn = "New ${article.category.titleEn} • ${article.publisherNameEn}",
                        isKeywordOrAuthorAlert = false
                    )
                )
            }
        }

        return results
    }

    private fun matchesRule(article: EditorialArticle, rule: KeywordAlertRule): Boolean {
        if (rule.category != null && article.category != rule.category) return false
        if (!rule.publisherId.isNullOrBlank() && article.publisherId != rule.publisherId) return false
        if (!rule.authorNameFilter.isNullOrBlank()) {
            val af = rule.authorNameFilter.trim()
            val matchesAuthor = article.authorName?.contains(af, ignoreCase = true) == true ||
                article.intervieweeName?.contains(af, ignoreCase = true) == true
            if (!matchesAuthor) return false
        }
        val kw = rule.keyword.trim().lowercase()
        return article.title.lowercase().contains(kw) ||
            article.description.lowercase().contains(kw) ||
            article.tags.any { it.lowercase().contains(kw) } ||
            article.authorName?.lowercase()?.contains(kw) == true ||
            article.intervieweeName?.lowercase()?.contains(kw) == true
    }

    companion object {
        fun isWithinQuietHours(currentHour: Int, startHour: Int, endHour: Int): Boolean {
            val h = currentHour.coerceIn(0, 23)
            val s = startHour.coerceIn(0, 23)
            val e = endHour.coerceIn(0, 23)
            if (s == e) return false
            return if (s < e) {
                h in s until e
            } else {
                h >= s || h < e
            }
        }

        fun isFrequencyWindowOpen(
            frequency: NotificationFrequency,
            lastSentAtMs: Long,
            nowMillis: Long
        ): Boolean {
            if (lastSentAtMs <= 0L) return true
            val elapsed = nowMillis - lastSentAtMs
            return when (frequency) {
                NotificationFrequency.IMMEDIATE -> true
                NotificationFrequency.HOURLY_DIGEST -> elapsed >= 60L * 60L * 1000L
                NotificationFrequency.DAILY_DIGEST -> elapsed >= 24L * 60L * 60L * 1000L
                NotificationFrequency.OFF -> false
            }
        }
    }
}
