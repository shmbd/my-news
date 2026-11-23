package bd.nib.songbadpatro.presentation.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.ReaderAccessStatus
import bd.nib.songbadpatro.domain.model.ReaderDocument
import bd.nib.songbadpatro.domain.model.ReaderTheme
import bd.nib.songbadpatro.domain.model.UserPreferences
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.presentation.components.CategoryBadge
import bd.nib.songbadpatro.presentation.components.EditorialLoadingState
import bd.nib.songbadpatro.presentation.theme.DarkBackground
import bd.nib.songbadpatro.presentation.theme.DarkInk
import bd.nib.songbadpatro.presentation.theme.DarkSurface
import bd.nib.songbadpatro.presentation.theme.EditorialInk
import bd.nib.songbadpatro.presentation.theme.NewsprintBackground
import bd.nib.songbadpatro.presentation.theme.NewsprintSurface
import bd.nib.songbadpatro.presentation.theme.SepiaReaderBackground
import bd.nib.songbadpatro.presentation.theme.SepiaReaderInk
import bd.nib.songbadpatro.presentation.theme.SepiaReaderSurface
import bd.nib.songbadpatro.presentation.util.BanglaFormatter
import bd.nib.songbadpatro.presentation.util.ShareAndBrowserHelper
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = true,
    val article: EditorialArticle? = null,
    val readerDocument: ReaderDocument? = null,
    val preferences: UserPreferences = UserPreferences()
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val editorialRepository: EditorialRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val articleId: String = savedStateHandle["articleId"] ?: ""
    private val isLoading = MutableStateFlow(true)
    private val readerDoc = MutableStateFlow<ReaderDocument?>(null)

    val uiState: StateFlow<ReaderUiState> = combine(
        isLoading,
        editorialRepository.observeArticleById(articleId),
        readerDoc,
        settingsRepository.preferencesFlow
    ) { loading, art, doc, prefs ->
        ReaderUiState(
            isLoading = loading,
            article = art,
            readerDocument = doc,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderUiState()
    )

    init {
        if (articleId.isNotBlank()) {
            viewModelScope.launch {
                editorialRepository.recordReadingHistory(articleId)
                loadReader(forceRefresh = false)
            }
        }
    }

    fun loadReader(forceRefresh: Boolean) {
        if (articleId.isBlank()) return
        viewModelScope.launch {
            isLoading.value = true
            runCatching { editorialRepository.loadReaderDocument(articleId, forceRefresh) }
                .onSuccess { readerDoc.value = it }
            isLoading.value = false
        }
    }

    fun toggleBookmark() {
        if (articleId.isBlank()) return
        viewModelScope.launch {
            editorialRepository.toggleBookmark(articleId)
        }
    }

    fun updateReaderTheme(theme: ReaderTheme) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(readerTheme = theme) }
        }
    }

    fun updateFontScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(readerFontScale = scale) }
        }
    }

    fun updateLineSpacing(spacing: Float) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { it.copy(readerLineSpacing = spacing) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onUpdateReaderTheme: (ReaderTheme) -> Unit,
    onUpdateFontScale: (Float) -> Unit,
    onUpdateLineSpacing: (Float) -> Unit
) {
    val language = state.preferences.language
    val context = LocalContext.current
    var showTypographySheet by remember { mutableStateOf(false) }

    val (bgColor, surfaceColor, inkColor) = when (state.preferences.readerTheme) {
        ReaderTheme.LIGHT -> Triple(NewsprintBackground, NewsprintSurface, EditorialInk)
        ReaderTheme.SEPIA -> Triple(SepiaReaderBackground, SepiaReaderSurface, SepiaReaderInk)
        ReaderTheme.DARK -> Triple(DarkBackground, DarkSurface, DarkInk)
    }

    val article = state.article

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = article?.displayPublisherName(language)
                            ?: if (language == AppLanguage.ENGLISH) "Reader" else "পাঠক মোড",
                        style = MaterialTheme.typography.titleMedium,
                        color = inkColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = inkColor)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTypographySheet = !showTypographySheet },
                        modifier = Modifier.testTag("reader_typography_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatSize,
                            contentDescription = if (language == AppLanguage.ENGLISH) "Typography" else "অক্ষর ও থিম",
                            tint = inkColor
                        )
                    }
                    if (article != null) {
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.testTag("reader_bookmark_btn")
                        ) {
                            Icon(
                                imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = inkColor
                            )
                        }
                        IconButton(
                            onClick = {
                                ShareAndBrowserHelper.shareArticle(
                                    context = context,
                                    title = article.title,
                                    publisherName = article.displayPublisherName(language),
                                    authorOrPerson = article.primaryContributorLabel(language),
                                    url = article.originalArticleUrl
                                )
                            }
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share", tint = inkColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        }
    ) { innerPadding ->
        if (article == null && state.isLoading) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EditorialLoadingState(
                    message = if (language == AppLanguage.ENGLISH) "Loading article..." else "লেখা লোড হচ্ছে..."
                )
            }
            return@Scaffold
        }

        if (article == null) {
            Column(modifier = Modifier.padding(innerPadding).padding(24.dp)) {
                Text(
                    text = if (language == AppLanguage.ENGLISH) "Article not found." else "লেখাটি খুঁজে পাওয়া যায়নি।",
                    color = inkColor
                )
            }
            return@Scaffold
        }

        val baseFontSp = 17f * state.preferences.readerFontScale
        val lineHeightSp = baseFontSp * state.preferences.readerLineSpacing
        val paragraphs = state.readerDocument?.excerptParagraphs
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(article.description.takeIf { it.isNotBlank() })

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bgColor)
                .testTag("reader_content_list")
        ) {
            if (showTypographySheet) {
                item(key = "typography_controls") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = if (language == AppLanguage.ENGLISH) "Reading Appearance" else "পড়ার থিম ও অক্ষরের আকার",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = inkColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReaderTheme.entries.forEach { rTheme ->
                                    FilterChip(
                                        selected = state.preferences.readerTheme == rTheme,
                                        onClick = { onUpdateReaderTheme(rTheme) },
                                        label = { Text(rTheme.displayLabel(language)) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (language == AppLanguage.ENGLISH) "Font Size" else "অক্ষরের আকার",
                                style = MaterialTheme.typography.labelMedium,
                                color = inkColor
                            )
                            Slider(
                                value = state.preferences.readerFontScale,
                                onValueChange = onUpdateFontScale,
                                valueRange = 0.85f..1.65f
                            )
                            Text(
                                text = if (language == AppLanguage.ENGLISH) "Line Spacing" else "লাইনের দূরত্ব",
                                style = MaterialTheme.typography.labelMedium,
                                color = inkColor
                            )
                            Slider(
                                value = state.preferences.readerLineSpacing,
                                onValueChange = onUpdateLineSpacing,
                                valueRange = 1.30f..2.10f
                            )
                        }
                    }
                }
            }

            // Category, Live vs Cached Badge & Date
            item(key = "article_meta_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CategoryBadge(
                            category = article.category,
                            isOfficialEditorial = article.isOfficialEditorial,
                            language = language
                        )
                        val cacheLabel = when (state.readerDocument?.accessStatus) {
                            ReaderAccessStatus.CACHED_OFFLINE -> {
                                if (language == AppLanguage.ENGLISH) "Cached Copy" else "সংরক্ষিত অফলাইন কপি"
                            }
                            ReaderAccessStatus.LIVE_PERMITTED_EXTRACT -> {
                                if (language == AppLanguage.ENGLISH) "Live Preview" else "লাইভ প্রিভিউ"
                            }
                            ReaderAccessStatus.PAYWALLED_OR_RESTRICTED -> {
                                if (language == AppLanguage.ENGLISH) "Publisher Restricted" else "সীমাবদ্ধ অ্যাক্সেস"
                            }
                            else -> {
                                if (language == AppLanguage.ENGLISH) "Publisher Summary" else "প্রকাশকের সারসংক্ষেপ"
                            }
                        }
                        Surface(
                            color = surfaceColor,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = cacheLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = inkColor.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = article.title,
                        fontSize = (23f * state.preferences.readerFontScale).sp,
                        lineHeight = (33f * state.preferences.readerFontScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = inkColor
                    )

                    val contributor = article.primaryContributorLabel(language)
                    if (!contributor.isNullOrBlank()) {
                        val personId = article.intervieweeId ?: article.authorId
                        Column {
                            Text(
                                text = contributor,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = inkColor
                            )
                            (article.intervieweeDesignation ?: article.authorAffiliation)?.takeIf { it.isNotBlank() }?.let { aff ->
                                Text(
                                    text = aff,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = inkColor.copy(alpha = 0.78f)
                                )
                            }
                            if (article.category == EditorialCategory.INTERVIEW && !article.interviewerName.isNullOrBlank()) {
                                Text(
                                    text = if (language == AppLanguage.ENGLISH) {
                                        "Interviewed by: ${article.interviewerName}"
                                    } else {
                                        "সাক্ষাৎকার গ্রহণ: ${article.interviewerName}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = inkColor.copy(alpha = 0.78f)
                                )
                            }
                            if (personId != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = { onOpenAuthor(personId) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (language == AppLanguage.ENGLISH) {
                                            "More from this contributor"
                                        } else {
                                            "এই লেখকের সব লেখা দেখুন"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "${article.displayPublisherName(language)} • ${
                            BanglaFormatter.formatDhakaDate(
                                article.originalPublicationDate,
                                language,
                                includeTime = true
                            )
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = inkColor.copy(alpha = 0.75f)
                    )
                }
            }

            if (!article.thumbnailUrl.isNullOrBlank() && !state.preferences.dataSaverMode) {
                item(key = "lead_image") {
                    AsyncImage(
                        model = article.thumbnailUrl,
                        contentDescription = article.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            item(key = "divider_top") {
                HorizontalDivider(color = inkColor.copy(alpha = 0.18f))
            }

            // Permitted preview / description paragraphs (never masquerading incomplete text as complete)
            items(paragraphs) { paragraph ->
                Text(
                    text = paragraph,
                    fontSize = baseFontSp.sp,
                    lineHeight = lineHeightSp.sp,
                    color = inkColor
                )
            }

            // Attribution & Copyright Notice + Prominent Original Article Link
            item(key = "attribution_and_original_cta") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val notice = state.readerDocument?.displayBanner(language)
                            ?: if (language == AppLanguage.ENGLISH) {
                                "Summary & attribution provided for discovery. Please visit ${article.publisherNameEn} to read the complete copyrighted article."
                            } else {
                                "কপিরাইট নীতিমালা মেনে শুধুমাত্র সারসংক্ষেপ ও অনুমোদিত প্রারম্ভিক অংশ প্রদর্শিত হয়েছে। সম্পূর্ণ নিবন্ধটি পড়তে মূল প্রকাশকের ওয়েবসাইটে যান।"
                            }
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = inkColor.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { ShareAndBrowserHelper.openExternalUrl(context, article.originalArticleUrl) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_original_article_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (language == AppLanguage.ENGLISH) {
                                    "Read Full Article on ${article.publisherNameEn}"
                                } else {
                                    "${article.publisherNameBn}-এর মূল ওয়েবসাইটে সম্পূর্ণ লেখাটি পড়ুন"
                                }
                            )
                        }
                    }
                }
            }

            // Syndicated Editions across other publishers (if any)
            if (article.syndicatedEditions.isNotEmpty()) {
                item(key = "syndicated_editions") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (language == AppLanguage.ENGLISH) {
                                "Also published in:"
                            } else {
                                "অন্যান্য প্রকাশনায় প্রকাশিত সংস্করণ:"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = inkColor
                        )
                        article.syndicatedEditions.forEach { syn ->
                            OutlinedButton(
                                onClick = { ShareAndBrowserHelper.openExternalUrl(context, syn.url) }
                            ) {
                                Text(
                                    text = if (language == AppLanguage.ENGLISH) syn.publisherNameEn else syn.publisherNameBn
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
