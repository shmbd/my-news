package bd.nib.songbadpatro

import bd.nib.songbadpatro.domain.model.ArticleSortOption
import bd.nib.songbadpatro.domain.model.ArticleVerificationStatus
import bd.nib.songbadpatro.domain.model.ConfidenceTier
import bd.nib.songbadpatro.domain.model.ContentLanguage
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.KeywordAlertRule
import bd.nib.songbadpatro.domain.model.NotificationFrequency
import bd.nib.songbadpatro.domain.model.SearchFilter
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.usecase.AuthorIdentityResolver
import bd.nib.songbadpatro.domain.usecase.BanglaTitleNormalizer
import bd.nib.songbadpatro.domain.usecase.ClassificationInput
import bd.nib.songbadpatro.domain.usecase.ClassifyEditorialContentUseCase
import bd.nib.songbadpatro.domain.usecase.DateValidationUseCase
import bd.nib.songbadpatro.domain.usecase.DeduplicateAndSyndicateUseCase
import bd.nib.songbadpatro.domain.usecase.EditorialAlertMatcher
import bd.nib.songbadpatro.domain.usecase.SortAndFilterArticlesUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierDeduplicationAndSearchTest {

    private val classifier = ClassifyEditorialContentUseCase()
    private val deduplicator = DeduplicateAndSyndicateUseCase()
    private val sorterAndFilter = SortAndFilterArticlesUseCase()
    private val alertMatcher = EditorialAlertMatcher()

    @Test
    fun `classifies official newspaper editorial distinctly from named guest column`() {
        val officialEd = classifier.classify(
            ClassificationInput(
                title = "সম্ভাবনা আছে; কিন্তু কর্মসংস্থান কোথায়?",
                description = "শিক্ষিত তরুণদের কর্মসংস্থান বিষয়ে দৈনিক ইত্তেফাকের সম্পাদকীয়।",
                url = "https://www.ittefaq.com.bd/editorial/822510",
                publisherNameBn = "দৈনিক ইত্তেফাক",
                publisherNameEn = "The Daily Ittefaq",
                authorName = "সম্পাদকীয়",
                feedCategoriesAndTags = listOf("মতামত", "সম্পাদকীয়"),
                isDedicatedEditorialSource = true,
                forceOfficialEditorial = true
            )
        )
        assertEquals(EditorialCategory.EDITORIAL, officialEd.category)
        assertTrue(officialEd.isOfficialEditorial)
        assertEquals(ConfidenceTier.HIGH, officialEd.tier)

        val namedColumn = classifier.classify(
            ClassificationInput(
                title = "গঙ্গা চুক্তির নবায়ন: ন্যায্যতা ও পারস্পরিক সহযোগিতা",
                description = "আন্তঃসীমান্ত নদী ব্যবস্থাপনা নিয়ে বিশেষ কলাম।",
                url = "https://www.ittefaq.com.bd/opinion/column/822504",
                publisherNameBn = "দৈনিক ইত্তেফাক",
                publisherNameEn = "The Daily Ittefaq",
                authorName = "ড. আব্দুল ওয়াদুদ",
                authorAffiliation = "অধ্যাপক, নদী গবেষণা ইনস্টিটিউট",
                feedCategoriesAndTags = listOf("মতামত", "কলাম"),
                isDedicatedEditorialSource = true
            )
        )
        assertEquals(EditorialCategory.COLUMN, namedColumn.category)
        assertFalse(namedColumn.isOfficialEditorial)
        assertEquals(ConfidenceTier.HIGH, namedColumn.tier)
    }

    @Test
    fun `classifies interview and opinion accurately and rejects general crime breaking and political news`() {
        val interviewRes = classifier.classify(
            ClassificationInput(
                title = "সাক্ষাৎকারে অধ্যাপক রেহমান সোবহান: বৈষম্য নিরসনে প্রাতিষ্ঠানিক সংস্কার",
                description = "প্রশ্ন: অর্থনীতির মূল চ্যালেঞ্জ কী? উত্তর: সুশাসন প্রতিষ্ঠা।",
                url = "https://www.prothomalo.com/opinion/interview/xyz99",
                publisherNameBn = "প্রথম আলো",
                publisherNameEn = "Prothom Alo",
                authorName = "বিশেষ প্রতিনিধি",
                feedCategoriesAndTags = listOf("সাক্ষাৎকার"),
                hasQuestionAnswerStructure = true
            )
        )
        assertEquals(EditorialCategory.INTERVIEW, interviewRes.category)
        assertEquals(ConfidenceTier.HIGH, interviewRes.tier)

        val opinionRes = classifier.classify(
            ClassificationInput(
                title = "উচ্চশিক্ষায় গবেষণার বরাদ্দ বাড়ানো জরুরি কেন",
                description = "বিশ্ববিদ্যালয়ে মৌলিক গবেষণার সংকট ও করণীয়।",
                url = "https://samakal.com/opinion/article/2611999",
                publisherNameBn = "সমকাল",
                publisherNameEn = "Samakal",
                authorName = "ফারহানা রহমান",
                feedCategoriesAndTags = listOf("মতামত", "মুক্তমত"),
                isDedicatedEditorialSource = true,
                sourceDefaultCategory = EditorialCategory.OPINION
            )
        )
        assertEquals(EditorialCategory.OPINION, opinionRes.category)
        assertEquals(ConfidenceTier.HIGH, opinionRes.tier)

        // Reject breaking news
        val breakingRes = classifier.classify(
            ClassificationInput(
                title = "ব্রেকিং: রাজধানীতে সড়ক অবরোধ",
                description = "তীব্র যানজট সৃষ্টি হয়েছে।",
                url = "https://www.prothomalo.com/bangladesh/capital/123",
                publisherNameBn = "প্রথম আলো",
                publisherNameEn = "Prothom Alo"
            )
        )
        assertNull(breakingRes.category)
        assertEquals(ConfidenceTier.LOW, breakingRes.tier)

        // Reject routine political desk report even if it mentions political terms
        val politicalReport = classifier.classify(
            ClassificationInput(
                title = "বিএনপিতে ফেরার গুঞ্জন, নিজের অবস্থান জানালেন রুমিন ফারহানা",
                description = "ফেসবুক পোস্টে নিজের অবস্থান স্পষ্ট করেছেন তিনি।",
                url = "https://www.ittefaq.com.bd/politics/822691",
                publisherNameBn = "দৈনিক ইত্তেফাক",
                publisherNameEn = "The Daily Ittefaq",
                authorName = "ইত্তেফাক ডিজিটাল রিপোর্ট",
                feedCategoriesAndTags = listOf("রাজনীতি"),
                isDedicatedEditorialSource = false
            )
        )
        assertNull(politicalReport.category)
        assertEquals(ConfidenceTier.LOW, politicalReport.tier)

        // Reject accident report
        val accidentReport = classifier.classify(
            ClassificationInput(
                title = "পিরোজপুরে মোটরসাইকেল-ট্রলির মুখোমুখি সংঘর্ষে তিন যুবক নিহত, আহত ২",
                description = "পুলিশ ঘটনাস্থলে পৌঁছেছে।",
                url = "https://www.ittefaq.com.bd/country/822690",
                publisherNameBn = "দৈনিক ইত্তেফাক",
                publisherNameEn = "The Daily Ittefaq"
            )
        )
        assertNull(accidentReport.category)
    }

    @Test
    fun `deduplicates same publisher feed items while preserving syndicated cross-publisher editions`() {
        val pubTime = DateValidationUseCase.parseTimestampInDhaka("2026-11-23T10:00:00+06:00")!!
        val a1 = sampleArticle(
            id = "1",
            url = "https://www.thedailystar.net/opinion/views/news/climate-finance-101",
            title = "Climate finance justice for Bangladesh",
            publisherId = "daily_star_en",
            publisherNameBn = "দ্য ডেইলি স্টার",
            publishedAt = pubTime
        )
        // Same publisher duplicate via another feed
        val a2SamePub = sampleArticle(
            id = "2",
            url = "https://www.thedailystar.net/opinion/rss-mirror/climate-finance-101",
            title = "Climate finance justice for Bangladesh!",
            publisherId = "daily_star_en",
            publisherNameBn = "দ্য ডেইলি স্টার",
            publishedAt = pubTime + 1_000L
        )
        // Syndicated column in a different publisher -> MUST preserve separate record!
        val a3SyndicatedOtherPub = sampleArticle(
            id = "3",
            url = "https://www.dhakatribune.com/opinion/op-ed/999/climate-finance-justice-for-bangladesh",
            title = "Climate finance justice for Bangladesh",
            publisherId = "dhaka_tribune",
            publisherNameBn = "ঢাকা ট্রিবিউন",
            publishedAt = pubTime + 3_600_000L
        )

        val reconciled = deduplicator.reconcile(emptyList(), listOf(a1, a2SamePub, a3SyndicatedOtherPub))
        assertEquals(2, reconciled.size)
        val starEdition = reconciled.first { it.publisherId == "daily_star_en" }
        val tribuneEdition = reconciled.first { it.publisherId == "dhaka_tribune" }
        assertEquals(1, starEdition.syndicatedEditions.size)
        assertEquals("dhaka_tribune", starEdition.syndicatedEditions.first().publisherId)
        assertEquals("daily_star_en", tribuneEdition.syndicatedEditions.first().publisherId)
    }

    @Test
    fun `author identity resolver avoids merging two different authors with same name at different publishers`() {
        val idPubA = AuthorIdentityResolver.resolvePersonId(
            name = "মোহাম্মদ আলী",
            affiliationOrDesignation = null,
            publisherId = "prothom_alo"
        )
        val idPubB = AuthorIdentityResolver.resolvePersonId(
            name = "মোহাম্মদ আলী",
            affiliationOrDesignation = null,
            publisherId = "samakal"
        )
        assertNotEquals(idPubA, idPubB)

        val idSameScholar1 = AuthorIdentityResolver.resolvePersonId(
            name = "ড. সেলিম রায়হান",
            affiliationOrDesignation = "অধ্যাপক, অর্থনীতি বিভাগ, ঢাকা বিশ্ববিদ্যালয়",
            publisherId = "prothom_alo"
        )
        val idSameScholar2 = AuthorIdentityResolver.resolvePersonId(
            name = "ড. সেলিম রায়হান",
            affiliationOrDesignation = "অধ্যাপক, অর্থনীতি বিভাগ, ঢাকা বিশ্ববিদ্যালয়",
            publisherId = "tbs_bangla"
        )
        assertEquals(idSameScholar1, idSameScholar2)
    }

    @Test
    fun `search and sorting strictly enforce 2026-11-23 cutoff and sort orders`() {
        val preCutoff = DateValidationUseCase.parseTimestampInDhaka("2026-11-20T12:00:00+06:00")!!
        val nov23 = DateValidationUseCase.parseTimestampInDhaka("2026-11-23T08:00:00+06:00")!!
        val nov24 = DateValidationUseCase.parseTimestampInDhaka("2026-11-24T08:00:00+06:00")!!

        val oldItem = sampleArticle("old", "https://ex.bd/1", "পুরোনো মতামত", "ittefaq", "দৈনিক ইত্তেফাক", preCutoff)
        val newerA = sampleArticle("new1", "https://ex.bd/2", "অর্থনৈতিক সংস্কার", "samakal", "সমকাল", nov23, author = "আনিসুল হক")
        val newerB = sampleArticle("new2", "https://ex.bd/3", "ব্যাংকিং খাতে সুশাসন", "ittefaq", "দৈনিক ইত্তেফাক", nov24, author = "ড. দেবপ্রিয়")

        val results = sorterAndFilter.applyFilterAndSort(
            articles = listOf(oldItem, newerA, newerB),
            filter = SearchFilter(sortOption = ArticleSortOption.OLDEST_FIRST)
        )
        assertEquals(2, results.size)
        assertEquals("new1", results[0].articleId)
        assertEquals("new2", results[1].articleId)
    }

    @Test
    fun `notification matcher deduplicates respects quiet hours and never notifies pre-cutoff articles`() {
        val nov23 = DateValidationUseCase.parseTimestampInDhaka("2026-11-23T15:00:00+06:00")!!
        val article = sampleArticle(
            id = "art_notify_1",
            url = "https://samakal.com/opinion/1",
            title = "সংবিধান ও নির্বাচন কমিশন সংস্কার",
            publisherId = "samakal",
            publisherNameBn = "সমকাল",
            publishedAt = nov23
        )
        val prefs = UserPreferences(
            notificationsEnabled = true,
            notificationFrequency = NotificationFrequency.IMMEDIATE,
            quietHoursEnabled = true,
            quietStartHour = 23,
            quietEndHour = 7
        )
        val rules = listOf(KeywordAlertRule(id = 1L, keyword = "সংবিধান"))

        // Quiet hour (2 AM) -> empty
        val duringQuiet = alertMatcher.findNotificationCandidates(
            newlyPublished = listOf(article),
            keywordRules = rules,
            alreadyNotifiedIds = emptySet(),
            preferences = prefs,
            currentHour24 = 2
        )
        assertTrue(duringQuiet.isEmpty())

        // Daytime (14:00) -> matches once
        val daytime = alertMatcher.findNotificationCandidates(
            newlyPublished = listOf(article),
            keywordRules = rules,
            alreadyNotifiedIds = emptySet(),
            preferences = prefs,
            currentHour24 = 14
        )
        assertEquals(1, daytime.size)

        // Already notified -> deduplicated
        val duplicateCheck = alertMatcher.findNotificationCandidates(
            newlyPublished = listOf(article),
            keywordRules = rules,
            alreadyNotifiedIds = setOf("art_notify_1"),
            preferences = prefs,
            currentHour24 = 14
        )
        assertTrue(duplicateCheck.isEmpty())
    }

    private fun sampleArticle(
        id: String,
        url: String,
        title: String,
        publisherId: String,
        publisherNameBn: String,
        publishedAt: Long,
        author: String? = "ড. কামাল আহমেদ"
    ): EditorialArticle = EditorialArticle(
        articleId = id,
        canonicalUrl = url,
        originalArticleUrl = url,
        title = title,
        normalizedTitle = BanglaTitleNormalizer.normalize(title),
        description = "বিশ্লেষণধর্মী মতামত নিবন্ধ",
        authorId = "auth_$id",
        authorName = author,
        authorAffiliation = null,
        authorPhotograph = null,
        interviewerName = null,
        intervieweeId = null,
        intervieweeName = null,
        intervieweeDesignation = null,
        publisherId = publisherId,
        publisherNameBn = publisherNameBn,
        publisherNameEn = publisherId,
        publisherLogoUrl = null,
        publisherWebsite = "https://example.bd",
        category = EditorialCategory.OPINION,
        isOfficialEditorial = false,
        originalPublicationDate = publishedAt,
        updatedDate = null,
        thumbnailUrl = null,
        extractedContent = null,
        isFullExtractPermitted = false,
        readerRestricted = false,
        language = ContentLanguage.BANGLA,
        tags = listOf("মতামত"),
        classificationConfidence = 0.88,
        classificationReason = "Test",
        verificationStatus = ArticleVerificationStatus.PUBLISHED,
        createdAt = publishedAt,
        lastSyncedAt = publishedAt
    )
}
