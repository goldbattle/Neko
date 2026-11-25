package org.nekomanga.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.utils.MdLang
import eu.kanade.tachiyomi.ui.manga.MangaConstants
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import me.saket.swipe.SwipeAction
import org.nekomanga.R
import org.nekomanga.constants.Constants
import org.nekomanga.constants.MdConstants
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.presentation.components.dropdown.SimpleDropDownItem
import org.nekomanga.presentation.components.dropdown.SimpleDropdownMenu
import org.nekomanga.presentation.components.theme.ThemeColorState
import org.nekomanga.presentation.extensions.conditional
import org.nekomanga.presentation.extensions.surfaceColorAtElevationCustomColor
import org.nekomanga.presentation.theme.Size

@Composable
fun ChapterRow(
    themeColor: ThemeColorState,
    chapterItem: ChapterItem,
    shouldHideChapterTitles: Boolean = false,
    swipeRightAction: MangaConstants.ChapterSwipeAction =
        MangaConstants.ChapterSwipeAction.ToggleBookmark,
    swipeLeftAction: MangaConstants.ChapterSwipeAction =
        MangaConstants.ChapterSwipeAction.ToggleRead,
    onClick: (ChapterItem) -> Unit,
    onBookmark: (ChapterItem) -> Unit,
    onRead: (ChapterItem) -> Unit,
    onWebView: (ChapterItem) -> Unit,
    onComment: (String) -> Unit,
    onDownload: (List<ChapterItem>, MangaConstants.DownloadAction) -> Unit,
    blockScanlator: (MangaConstants.BlockType, String) -> Unit,
    markPrevious: (ChapterItem, Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val swipeActionBackgroundColor =
        remember(colorScheme, themeColor.primaryColor) {
            colorScheme.surfaceColorAtElevationCustomColor(themeColor.primaryColor, Size.small)
        }

    val latestItem = rememberUpdatedState(chapterItem)
    val currentOnBookmark by rememberUpdatedState(onBookmark)
    val currentOnRead by rememberUpdatedState(onRead)
    val latestOnDownload = rememberUpdatedState(onDownload)
    val rightSwipeAction =
        remember(
            swipeRightAction,
            chapterItem.chapter.read,
            chapterItem.chapter.bookmark,
            chapterItem.downloadState,
            chapterItem.chapter.isUnavailable,
            chapterItem.chapter.scanlator,
            themeColor,
            swipeActionBackgroundColor,
        ) {
            chapterSwipeAction(
                action = swipeRightAction,
                chapterItem = chapterItem,
                themeColor = themeColor,
                background = swipeActionBackgroundColor,
                onBookmark = { currentOnBookmark(latestItem.value) },
                onRead = { currentOnRead(latestItem.value) },
                onDownload = { _, a -> latestOnDownload.value(listOf(latestItem.value), a) },
            )
        }
    val leftSwipeAction =
        remember(
            swipeLeftAction,
            chapterItem.chapter.read,
            chapterItem.chapter.bookmark,
            chapterItem.downloadState,
            chapterItem.chapter.isUnavailable,
            chapterItem.chapter.scanlator,
            themeColor,
            swipeActionBackgroundColor,
        ) {
            chapterSwipeAction(
                action = swipeLeftAction,
                chapterItem = chapterItem,
                themeColor = themeColor,
                background = swipeActionBackgroundColor,
                onBookmark = { currentOnBookmark(latestItem.value) },
                onRead = { currentOnRead(latestItem.value) },
                onDownload = { _, a -> latestOnDownload.value(listOf(latestItem.value), a) },
            )
        }
    val startSwipeActions = remember(rightSwipeAction) { listOfNotNull(rightSwipeAction) }
    val endSwipeActions = remember(leftSwipeAction) { listOfNotNull(leftSwipeAction) }

    // The app theme forces LTR, so swiping right reveals the start actions.
    ChapterSwipe(startSwipeActions = startSwipeActions, endSwipeActions = endSwipeActions) {
        ChapterRowContent(
            themeColorState = themeColor,
            shouldHideChapterTitles = shouldHideChapterTitles,
            chapterItem = chapterItem,
            latestChapterItem = latestItem,
            latestOnDownload = latestOnDownload,
            onClick = onClick,
            onWebView = onWebView,
            onComment = onComment,
            markPrevious = markPrevious,
            blockScanlator = blockScanlator,
        )
    }
}

/** Swipe action for one side of the row, or null when that side should do nothing. */
private fun chapterSwipeAction(
    action: MangaConstants.ChapterSwipeAction,
    chapterItem: ChapterItem,
    themeColor: ThemeColorState,
    background: Color,
    onBookmark: (ChapterItem) -> Unit,
    onRead: (ChapterItem) -> Unit,
    onDownload: (List<ChapterItem>, MangaConstants.DownloadAction) -> Unit,
): SwipeAction? {
    val (icon, textRes, onSwipe) =
        when (action) {
            MangaConstants.ChapterSwipeAction.Disabled -> return null
            MangaConstants.ChapterSwipeAction.ToggleRead -> {
                val (readIcon, readTextRes) =
                    if (chapterItem.chapter.read)
                        Icons.Default.VisibilityOff to R.string.mark_as_unread
                    else Icons.Default.Visibility to R.string.mark_as_read
                Triple(readIcon, readTextRes) { onRead(chapterItem) }
            }
            MangaConstants.ChapterSwipeAction.ToggleBookmark -> {
                val (bookmarkIcon, bookmarkTextRes) =
                    if (chapterItem.chapter.bookmark)
                        Icons.Default.BookmarkRemove to R.string.remove_bookmark
                    else Icons.Default.BookmarkAdd to R.string.add_bookmark
                Triple(bookmarkIcon, bookmarkTextRes) { onBookmark(chapterItem) }
            }
            MangaConstants.ChapterSwipeAction.Download -> {
                when (chapterItem.downloadState) {
                    Download.State.DOWNLOADED ->
                        Triple(Icons.Default.Delete, R.string.remove) {
                            onDownload(listOf(chapterItem), MangaConstants.DownloadAction.Remove)
                        }
                    Download.State.QUEUE,
                    Download.State.DOWNLOADING,
                    Download.State.ERROR ->
                        Triple(Icons.Default.Cancel, R.string.cancel) {
                            onDownload(listOf(chapterItem), MangaConstants.DownloadAction.Cancel)
                        }
                    Download.State.NOT_DOWNLOADED -> {
                        val isLocked =
                            chapterItem.chapter.isUnavailable ||
                                MdConstants.UnsupportedOfficialGroupList.contains(
                                    chapterItem.chapter.scanlator,
                                )
                        if (isLocked) return null
                        Triple(Icons.Default.Download, R.string.download) {
                            onDownload(listOf(chapterItem), MangaConstants.DownloadAction.Download)
                        }
                    }
                }
            }
        }

    return SwipeAction(
        icon = {
            SwipeIcon(
                icon = icon,
                text = stringResource(textRes),
                contentColor = themeColor.primaryColor,
            )
        },
        background = background,
        onSwipe = onSwipe,
    )
}

@Composable
private fun ChapterRowContent(
    themeColorState: ThemeColorState,
    shouldHideChapterTitles: Boolean,
    chapterItem: ChapterItem,
    latestChapterItem: State<ChapterItem>,
    latestOnDownload: State<(List<ChapterItem>, MangaConstants.DownloadAction) -> Unit>,
    onClick: (ChapterItem) -> Unit,
    onWebView: (ChapterItem) -> Unit,
    onComment: (String) -> Unit,
    markPrevious: (ChapterItem, Boolean) -> Unit,
    blockScanlator: (MangaConstants.BlockType, String) -> Unit,
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val textColor =
        remember(chapterItem.chapter.read) {
            if (chapterItem.chapter.read)
                onSurfaceColor.copy(alpha = NekoColors.disabledAlphaLowContrast)
            else onSurfaceColor
        }
    val secondaryTextColor =
        remember(chapterItem.chapter.read) {
            if (chapterItem.chapter.read)
                onSurfaceColor.copy(alpha = NekoColors.disabledAlphaLowContrast)
            else onSurfaceColor.copy(alpha = NekoColors.mediumAlphaLowContrast)
        }

    if (isDropdownExpanded) {
        val dropdownItems =
            remember(
                chapterItem.chapter.isLocalSource(),
                chapterItem.chapter.isMergedChapter(),
                chapterItem.chapter.scanlator,
                chapterItem.chapter.uploader,
            ) {
                buildChapterDropdownItems(
                    isLocal = chapterItem.chapter.isLocalSource(),
                    isMerged = chapterItem.chapter.isMergedChapter(),
                    scanlator = chapterItem.chapter.scanlator,
                    uploader = chapterItem.chapter.uploader,
                    onWebView = { onWebView(chapterItem) },
                    onComment = { onComment(chapterItem.chapter.mangaDexChapterId) },
                    markPrevious = { read -> markPrevious(chapterItem, read) },
                    blockScanlator = blockScanlator,
                )
            }

        SimpleDropdownMenu(
            expanded = true,
            themeColorState = themeColorState,
            onDismiss = { isDropdownExpanded = false },
            dropDownItems = dropdownItems,
        )
    }

    val latestOnClick by rememberUpdatedState(onClick)
    val onRowClick = remember { { latestOnClick(latestChapterItem.value) } }
    val onIndicatorDownload = remember {
        { action: MangaConstants.DownloadAction ->
            latestOnDownload.value(listOf(latestChapterItem.value), action)
        }
    }
    val startReadingLabel = remember(context) { context.getString(R.string.start_reading) }
    val onRowLongClick =
        remember(haptic) {
            {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isDropdownExpanded = true
            }
        }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .conditional(isDropdownExpanded) {
                    background(themeColorState.rippleColor.copy(alpha = 0.2f))
                }
                .combinedClickable(
                    onClick = onRowClick,
                    onLongClick = onRowLongClick,
                    role = Role.Button,
                    onClickLabel = startReadingLabel,
                )
                .padding(start = Size.small, top = Size.small, bottom = Size.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ChapterTitle(
                shouldHideChapterTitles = shouldHideChapterTitles,
                title = chapterItem.chapter.name,
                chapterNumber = chapterItem.chapter.chapterNumber.toDouble(),
                isBookmarked = chapterItem.chapter.bookmark,
                textColor = textColor,
                themeColor = themeColorState.primaryColor,
            )

            val subtitleText =
                remember(
                    chapterItem.chapter.dateUpload,
                    chapterItem.chapter.read,
                    chapterItem.chapter.lastPageRead,
                    chapterItem.chapter.pagesLeft,
                    chapterItem.chapter.scanlator,
                    chapterItem.chapter.uploader,
                ) {
                    val statuses = mutableListOf<String>()
                    ChapterUtil.relativeDate(chapterItem.chapter.dateUpload)?.let {
                        statuses.add(it)
                    }
                    if (!chapterItem.chapter.read && chapterItem.chapter.lastPageRead > 0) {
                        if (chapterItem.chapter.pagesLeft > 0) {
                            statuses.add(
                                context.resources.getQuantityString(
                                    R.plurals.pages_left,
                                    chapterItem.chapter.pagesLeft,
                                    chapterItem.chapter.pagesLeft,
                                ),
                            )
                        } else {
                            statuses.add(
                                context.getString(
                                    R.string.page_,
                                    chapterItem.chapter.lastPageRead + 1,
                                ),
                            )
                        }
                    }
                    if (chapterItem.chapter.scanlator.isNotBlank()) {
                        if (chapterItem.chapter.scanlator == Constants.NO_GROUP)
                            statuses.add(chapterItem.chapter.uploader)
                        statuses.add(chapterItem.chapter.scanlator)
                        if (
                            chapterItem.chapter.isMergedChapter() &&
                                chapterItem.chapter.uploader.isNotBlank()
                        )
                            statuses.add(chapterItem.chapter.uploader)
                    }
                    statuses.joinToString(Constants.SEPARATOR)
                }

            ChapterSubtitle(
                subtitleText = subtitleText,
                language = chapterItem.chapter.language,
                textColor = secondaryTextColor,
            )
        }
        ChapterDownloadIndicator(
            isUnavailable = chapterItem.chapter.isUnavailable,
            scanlator = chapterItem.chapter.scanlator,
            downloadState = chapterItem.downloadState,
            downloadProgress = chapterItem.downloadProgress,
            onDownload = onIndicatorDownload,
            themeColorState = themeColorState,
        )
    }
}

@Composable
private fun ChapterTitle(
    shouldHideChapterTitles: Boolean,
    title: String,
    chapterNumber: Double,
    isBookmarked: Boolean,
    textColor: Color,
    themeColor: Color,
) {
    val context = LocalContext.current
    val displayTitle =
        remember(shouldHideChapterTitles, title, chapterNumber, context) {
            if (shouldHideChapterTitles)
                context.getString(R.string.chapter_, decimalFormat.format(chapterNumber))
            else title
        }
    val baseStyle = MaterialTheme.typography.bodyLarge
    val titleStyle =
        remember(baseStyle, textColor) {
            baseStyle.copy(color = textColor, fontWeight = FontWeight.Medium)
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isBookmarked) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                modifier = Modifier.padding(end = Size.tiny).size(Size.medium),
                tint = themeColor,
            )
        }
        Text(
            text = displayTitle,
            style = titleStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChapterSubtitle(subtitleText: String, language: String, textColor: Color) {
    val baseStyle = MaterialTheme.typography.bodyMedium
    val style =
        remember(baseStyle, textColor) {
            baseStyle.copy(color = textColor, fontWeight = FontWeight.Medium)
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (language.isNotBlank()) {
            LanguageIcon(language = language, textColor = textColor)
        }
        Text(
            text = subtitleText,
            style = style,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LanguageIcon(language: String, textColor: Color) {
    if (language.equals(MdLang.ENGLISH.lang, true)) return

    val mdLang = remember(language) { MdLang.fromIsoCode(language) }
    val baseStyle = MaterialTheme.typography.bodyMedium
    val style =
        remember(baseStyle, textColor) {
            baseStyle.copy(color = textColor, fontWeight = FontWeight.Medium)
        }

    if (mdLang?.iconResId != null) {
        Image(
            painter = painterResource(id = mdLang.iconResId),
            modifier =
                Modifier.padding(end = Size.tiny)
                    .height(Size.medium)
                    .clip(RoundedCornerShape(Size.tiny)),
            contentDescription = mdLang.prettyPrint,
        )
    } else {
        Text(
            text = "$language?",
            style = style,
            modifier = Modifier.padding(end = Size.tiny),
        )
    }
}

@Composable
private fun ChapterDownloadIndicator(
    isUnavailable: Boolean,
    scanlator: String,
    downloadState: Download.State,
    downloadProgress: Int,
    onDownload: (MangaConstants.DownloadAction) -> Unit,
    themeColorState: ThemeColorState,
) {
    val isUnsupportedGroup =
        remember(scanlator) { MdConstants.UnsupportedOfficialGroupList.contains(scanlator) }
    val isLocked = isUnavailable || isUnsupportedGroup
    val isDownloaded = downloadState == Download.State.DOWNLOADED

    when {
        isLocked && !isDownloaded -> {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = stringResource(id = R.string.unavailable),
                modifier = Modifier.padding(Size.extraTiny).size(Size.extraLarge),
                tint = themeColorState.primaryColor,
            )
        }
        else -> {
            DownloadButton(
                themeColorState = themeColorState,
                modifier = Modifier,
                downloadState = downloadState,
                downloadProgress = downloadProgress,
                onClick = onDownload,
            )
        }
    }
}

private fun buildChapterDropdownItems(
    isLocal: Boolean,
    isMerged: Boolean,
    scanlator: String,
    uploader: String,
    onWebView: () -> Unit,
    onComment: () -> Unit,
    markPrevious: (Boolean) -> Unit,
    blockScanlator: (MangaConstants.BlockType, String) -> Unit,
): List<SimpleDropDownItem> {
    return buildList {
        if (!isLocal) {
            add(
                SimpleDropDownItem.Action(
                    text = UiText.StringResource(R.string.open_in_webview),
                    onClick = onWebView,
                ),
            )
        }

        add(
            SimpleDropDownItem.Parent(
                text = UiText.StringResource(R.string.mark_previous_as),
                children =
                    listOf(
                        SimpleDropDownItem.Action(
                            text = UiText.StringResource(R.string.read),
                            onClick = { markPrevious(true) },
                        ),
                        SimpleDropDownItem.Action(
                            text = UiText.StringResource(R.string.unread),
                            onClick = { markPrevious(false) },
                        ),
                    ),
            ),
        )

        val scanlatorItems =
            ChapterUtil.getScanlators(scanlator)
                .mapNotNull { name ->
                    if (name == Constants.NO_GROUP) uploader.takeIf { it.isNotBlank() } else name
                }
                .map { name ->
                    SimpleDropDownItem.Action(
                        text = UiText.String(name),
                        onClick = {
                            val type =
                                if (name == uploader) MangaConstants.BlockType.Uploader
                                else MangaConstants.BlockType.Group
                            blockScanlator(type, name)
                        },
                    )
                }

        if (scanlatorItems.isNotEmpty() && !isLocal) {
            add(
                SimpleDropDownItem.Parent(
                    text = UiText.StringResource(R.string.block_scanlator),
                    children = scanlatorItems,
                ),
            )
        }

        if (!isMerged && !isLocal) {
            add(
                SimpleDropDownItem.Action(
                    text = UiText.StringResource(R.string.comments),
                    onClick = onComment,
                ),
            )
        }
    }
        .toList()
}

@Composable
private fun SwipeIcon(icon: ImageVector, text: String, contentColor: Color) {
    Box(Modifier.padding(horizontal = Size.medium)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Text(text = text, textAlign = TextAlign.Center, color = contentColor)
        }
    }
}

private val decimalFormat =
    DecimalFormat("#.###", DecimalFormatSymbols().apply { decimalSeparator = '.' })
