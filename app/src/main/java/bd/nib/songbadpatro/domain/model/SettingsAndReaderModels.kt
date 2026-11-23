package bd.nib.songbadpatro.domain.model

enum class AppLanguage(val tag: String, val labelBn: String, val labelEn: String) {
    BANGLA("bn", "বাংলা", "Bangla"),
    ENGLISH("en", "ইংরেজি", "English");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            if (tag?.lowercase()?.startsWith("en") == true) ENGLISH else BANGLA
    }
}

enum class AppThemeMode(val labelBn: String, val labelEn: String) {
    SYSTEM("সিস্টেম ডিফল্ট", "System Default"),
    LIGHT("হালকা (নিউজপ্রিন্ট)", "Light (Newsprint)"),
    DARK("গাঢ় (ডার্ক মোড)", "Dark Mode");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

enum class ReaderTheme(val labelBn: String, val labelEn: String) {
    LIGHT("হালকা", "Light"),
    SEPIA("সেপিয়া", "Sepia"),
    DARK("ডার্ক", "Dark");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

enum class SyncFrequency(val minutes: Long?, val labelBn: String, val labelEn: String) {
    MINUTES_30(30L, "প্রতি ৩০ মিনিট", "Every 30 minutes"),
    HOURS_1(60L, "প্রতি ১ ঘণ্টা", "Every 1 hour"),
    HOURS_3(180L, "প্রতি ৩ ঘণ্টা", "Every 3 hours"),
    HOURS_6(360L, "প্রতি ৬ ঘণ্টা", "Every 6 hours"),
    HOURS_12(720L, "প্রতি ১২ ঘণ্টা", "Every 12 hours"),
    MANUAL(null, "শুধু ম্যানুয়াল রিফ্রেশ", "Manual only");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

enum class NotificationFrequency(val labelBn: String, val labelEn: String) {
    IMMEDIATE("তাৎক্ষণিক (প্রতি সিঙ্কে)", "Immediate (Per Sync)"),
    HOURLY_DIGEST("ঘণ্টায় সর্বোচ্চ ১ বার", "At most once per hour"),
    DAILY_DIGEST("দিনে সর্বোচ্চ ১ বার", "Daily summary"),
    OFF("বিজ্ঞপ্তি বন্ধ", "Disabled");

    fun displayLabel(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) labelEn else labelBn
}

enum class ReaderAccessStatus {
    LIVE_PERMITTED_EXTRACT,
    CACHED_OFFLINE,
    METADATA_AND_SUMMARY_ONLY,
    PAYWALLED_OR_RESTRICTED
}

data class ReaderDocument(
    val article: EditorialArticle,
    val excerptParagraphs: List<String>,
    val accessStatus: ReaderAccessStatus,
    val isCompleteTextAvailable: Boolean,
    val statusBannerBn: String,
    val statusBannerEn: String
) {
    fun displayBanner(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) statusBannerEn else statusBannerBn
}

data class KeywordAlertRule(
    val id: Long = 0L,
    val keyword: String,
    val category: EditorialCategory? = null,
    val publisherId: String? = null,
    val authorNameFilter: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class AlertCandidate(
    val article: EditorialArticle,
    val matchedReasonBn: String,
    val matchedReasonEn: String,
    val isKeywordOrAuthorAlert: Boolean
)

data class SyncSourceReport(
    val sourceId: String,
    val publisherId: String,
    val sourceNameBn: String,
    val success: Boolean,
    val fetchedCount: Int = 0,
    val publishedCount: Int = 0,
    val pendingCount: Int = 0,
    val rejectedPreCutoffCount: Int = 0,
    val rejectedNonEditorialCount: Int = 0,
    val errorMessage: String? = null
)

data class SyncSummary(
    val totalSourcesAttempted: Int,
    val succeededSources: Int,
    val failedSources: Int,
    val newlyPublishedCount: Int,
    val pendingVerificationCount: Int,
    val rejectedPreCutoffCount: Int,
    val rejectedNonEditorialCount: Int,
    val reports: List<SyncSourceReport>
)

data class SyncLogEntry(
    val logId: Long,
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
)

data class CacheStatistics(
    val publishedArticleCount: Int,
    val pendingArticleCount: Int,
    val bookmarkedCount: Int,
    val historyCount: Int,
    val cachedExcerptCount: Int,
    val authorCount: Int
)

data class UserPreferences(
    val language: AppLanguage = AppLanguage.BANGLA,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    val readerFontScale: Float = 1.05f,
    val readerLineSpacing: Float = 1.60f,
    val syncFrequency: SyncFrequency = SyncFrequency.HOURS_1,
    val wifiOnlySync: Boolean = false,
    val dataSaverMode: Boolean = false,
    val enabledCategories: Set<EditorialCategory> = EditorialCategory.entries.toSet(),
    val preferredPublisherIds: Set<String> = emptySet(),
    val preferredAuthorNames: Set<String> = emptySet(),
    val notificationsEnabled: Boolean = false,
    val notifyCategories: Set<EditorialCategory> = EditorialCategory.entries.toSet(),
    val notificationFrequency: NotificationFrequency = NotificationFrequency.IMMEDIATE,
    val quietHoursEnabled: Boolean = true,
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 7,
    val lastNotificationSentAt: Long = 0L,
    val publicationCutoffIsoDate: String = "2026-11-23"
)
