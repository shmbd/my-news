package bd.nib.songbadpatro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.domain.model.EditorialArticle
import bd.nib.songbadpatro.domain.model.EditorialCategory
import bd.nib.songbadpatro.domain.model.Publisher
import bd.nib.songbadpatro.presentation.theme.BadgeColumnBg
import bd.nib.songbadpatro.presentation.theme.BadgeEditorialBg
import bd.nib.songbadpatro.presentation.theme.BadgeInterviewBg
import bd.nib.songbadpatro.presentation.theme.BadgeOpinionBg
import bd.nib.songbadpatro.presentation.util.BanglaFormatter
import coil.compose.AsyncImage

@Composable
fun CategoryBadge(
    category: EditorialCategory,
    isOfficialEditorial: Boolean = false,
    language: AppLanguage = AppLanguage.BANGLA,
    modifier: Modifier = Modifier
) {
    val bgColor = when (category) {
        EditorialCategory.COLUMN -> BadgeColumnBg
        EditorialCategory.EDITORIAL -> BadgeEditorialBg
        EditorialCategory.OPINION -> BadgeOpinionBg
        EditorialCategory.INTERVIEW -> BadgeInterviewBg
    }
    val text = if (category == EditorialCategory.EDITORIAL && isOfficialEditorial) {
        if (language == AppLanguage.ENGLISH) "Official Editorial" else "প্রাতিষ্ঠানিক সম্পাদকীয়"
    } else {
        category.displayTitle(language)
    }

    Surface(
        color = bgColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun PublisherMonogram(
    name: String,
    logoUrl: String?,
    dataSaver: Boolean,
    sizeDp: Int = 26
) {
    if (!logoUrl.isNullOrBlank() && !dataSaver) {
        AsyncImage(
            model = logoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        val initial = name.trim().firstOrNull()?.toString() ?: "খ"
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorialArticleCard(
    article: EditorialArticle,
    language: AppLanguage,
    dataSaver: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAuthorClick: ((String) -> Unit)? = null,
    onOpenOriginalUrl: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("article_card_${article.articleId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CategoryBadge(
                        category = article.category,
                        isOfficialEditorial = article.isOfficialEditorial,
                        language = language
                    )
                    Text(
                        text = article.displayPublisherName(language),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("bookmark_btn_${article.articleId}")
                ) {
                    Icon(
                        imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (language == AppLanguage.ENGLISH) "Bookmark" else "সংরক্ষণ",
                        tint = if (article.isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    val contributor = article.primaryContributorLabel(language)
                    if (!contributor.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val personIdToOpen = article.intervieweeId ?: article.authorId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (personIdToOpen != null && onAuthorClick != null) {
                                Modifier.clickable { onAuthorClick(personIdToOpen) }
                            } else {
                                Modifier
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val rolePrefix = if (article.category == EditorialCategory.INTERVIEW && !article.intervieweeName.isNullOrBlank()) {
                                if (language == AppLanguage.ENGLISH) "Interviewee: " else "সাক্ষাৎকারদাতা: "
                            } else {
                                ""
                            }
                            val affSuffix = (article.intervieweeDesignation ?: article.authorAffiliation)
                                ?.takeIf { it.isNotBlank() }
                                ?.let { " ($it)" }
                                .orEmpty()
                            Text(
                                text = "$rolePrefix$contributor$affSuffix",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (article.category == EditorialCategory.INTERVIEW && !article.interviewerName.isNullOrBlank()) {
                        Text(
                            text = if (language == AppLanguage.ENGLISH) {
                                "Interviewer: ${article.interviewerName}"
                            } else {
                                "সাক্ষাৎকার গ্রহণ: ${article.interviewerName}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!article.thumbnailUrl.isNullOrBlank() && !dataSaver) {
                    AsyncImage(
                        model = article.thumbnailUrl,
                        contentDescription = article.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 92.dp, height = 74.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            if (article.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = article.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PublisherMonogram(
                        name = article.displayPublisherName(language),
                        logoUrl = article.publisherLogoUrl,
                        dataSaver = dataSaver,
                        sizeDp = 20
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = BanglaFormatter.formatDhakaDate(article.originalPublicationDate, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onOpenOriginalUrl != null) {
                    TextButton(
                        onClick = { onOpenOriginalUrl(article.originalArticleUrl) },
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == AppLanguage.ENGLISH) "Original" else "মূল সূত্র",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactHorizontalEditorialCard(
    article: EditorialArticle,
    language: AppLanguage,
    dataSaver: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.width(276.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                CategoryBadge(
                    category = article.category,
                    isOfficialEditorial = article.isOfficialEditorial,
                    language = language
                )
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = if (article.isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!article.thumbnailUrl.isNullOrBlank() && !dataSaver) {
                Spacer(modifier = Modifier.height(6.dp))
                AsyncImage(
                    model = article.thumbnailUrl,
                    contentDescription = article.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            article.primaryContributorLabel(language)?.let { contributor ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = contributor,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${article.displayPublisherName(language)} • ${BanglaFormatter.formatDhakaDate(article.originalPublicationDate, language)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PublisherChipCard(
    publisher: Publisher,
    language: AppLanguage,
    dataSaver: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            PublisherMonogram(
                name = publisher.displayName(language),
                logoUrl = publisher.logoUrl,
                dataSaver = dataSaver,
                sizeDp = 24
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = publisher.displayName(language),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
                Text(
                    text = publisher.supportedCategories.joinToString(" • ") { it.displayTitle(language) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun OfflineBanner(language: AppLanguage) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (language == AppLanguage.ENGLISH) {
                    "Offline mode — displaying cached editorial articles."
                } else {
                    "আপনি অফলাইনে আছেন — সংরক্ষিত কলাম, সম্পাদকীয় ও মতামত দেখানো হচ্ছে।"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun EditorialLoadingState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EditorialEmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}
