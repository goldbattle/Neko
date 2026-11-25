package org.nekomanga.presentation.screens.feed.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size as CoilSize
import eu.kanade.tachiyomi.data.coil.dynamicCover
import java.util.Date
import jp.wasabeef.gap.Gap
import kotlinx.coroutines.flow.distinctUntilChanged
import org.nekomanga.R
import org.nekomanga.domain.chapter.SimpleChapter
import org.nekomanga.domain.feed.FeedHistoryGroup
import org.nekomanga.presentation.components.UiText
import org.nekomanga.presentation.screens.EmptyScreen
import org.nekomanga.presentation.screens.feed.FeedManga
import org.nekomanga.presentation.screens.feed.FeedScreenActions
import org.nekomanga.presentation.theme.Size

@Composable
fun FeedHistoryPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    feedHistoryMangaList: List<FeedManga> = listOf(),
    outlineCovers: Boolean,
    dynamicCovers: Boolean,
    outlineCards: Boolean,
    hasMoreResults: Boolean,
    loadingResults: Boolean,
    feedScreenActions: FeedScreenActions,
    loadNextPage: () -> Unit,
    historyGrouping: FeedHistoryGroup,
) {
    if (feedHistoryMangaList.isEmpty()) {
        if (!loadingResults) {
            EmptyScreen(message = UiText.StringResource(R.string.no_results_found))
        }
        return
    }
    val scrollState = rememberLazyListState()

    // Optimize: Observe scroll state via snapshotFlow instead of attaching
    // LaunchedEffect to individual LazyColumn items. This avoids unnecessary
    // composition overhead and redundant pagination triggers.
    if (hasMoreResults && !loadingResults) {
        LaunchedEffect(scrollState) {
            snapshotFlow {
                val layoutInfo = scrollState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItemIndex >= (totalItems - 5)
            }
                .collect { isAtEnd ->
                    if (isAtEnd) {
                        loadNextPage()
                    }
                }
        }
    }

    val now = remember(feedHistoryMangaList) { Date().time }

    val context = LocalContext.current
    val artworks = remember(feedHistoryMangaList) { feedHistoryMangaList.map { it.artwork } }
    val currentArtworks by rememberUpdatedState(artworks)
    val lastPrefetchedHolder = remember(scrollState, dynamicCovers) { intArrayOf(-1) }
    LaunchedEffect(scrollState, dynamicCovers) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (last < 0) return@collect
                val arts = currentArtworks
                val end = minOf(last + 8, arts.lastIndex)
                for (i in (lastPrefetchedHolder[0] + 1)..end) {
                    context.imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(arts[i])
                            .dynamicCover(dynamicCovers)
                            .precision(Precision.INEXACT)
                            .size(CoilSize.ORIGINAL)
                            .build(),
                    )
                }
                if (end > lastPrefetchedHolder[0]) lastPrefetchedHolder[0] = end
            }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = scrollState,
        contentPadding = contentPadding,
    ) {
        var timeSpan = ""
        feedHistoryMangaList.forEachIndexed { index, feedManga ->
            val dateString =
                getDateString(feedManga.chapters.first().chapter.lastRead, now, historyGrouping)
            if (dateString.isNotEmpty() && timeSpan != dateString) {
                timeSpan = dateString

                item(key = "header-$dateString", contentType = "header") {
                    Text(
                        text = dateString,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().padding(Size.small),
                    )
                }
            } else {
                item(key = "gap-$index", contentType = "gap") { Gap(Size.small) }
            }

            item(key = "$index-${feedManga.mangaId}", contentType = "history") {
                HistoryCard(
                    feedManga = feedManga,
                    outlineCover = outlineCovers,
                    dynamicCover = dynamicCovers,
                    outlineCard = outlineCards,
                    groupedBySeries = historyGrouping == FeedHistoryGroup.Series,
                    mangaClick =
                        remember(feedManga, feedScreenActions) {
                            { feedScreenActions.mangaClick(feedManga.mangaId) }
                        },
                    chapterClick =
                        remember(feedManga, feedScreenActions) {
                            { chapterId: Long ->
                                feedScreenActions.chapterClick(feedManga.mangaId, chapterId)
                            }
                        },
                    deleteAllHistoryClick =
                        remember(feedManga, feedScreenActions) {
                            { feedScreenActions.deleteAllHistoryClick(feedManga) }
                        },
                    deleteHistoryClick =
                        remember(feedManga, feedScreenActions) {
                            { chp: SimpleChapter ->
                                feedScreenActions.deleteHistoryClick(feedManga, chp)
                            }
                        },
                )
                Gap(Size.tiny)
            }
        }
    }
}

private fun getDateString(
    date: Long,
    currentDate: Long,
    historyGroupType: FeedHistoryGroup = FeedHistoryGroup.Day,
): String {
    if (historyGroupType == FeedHistoryGroup.No || historyGroupType == FeedHistoryGroup.Series) {
        return ""
    }

    val dateType =
        if (historyGroupType == FeedHistoryGroup.Day) {
            DateUtils.DAY_IN_MILLIS
        } else {
            DateUtils.WEEK_IN_MILLIS
        }

    val dateString = DateUtils.getRelativeTimeSpanString(date, currentDate, dateType).toString()
    return if (dateString == "0 weeks ago") {
        "This week"
    } else {
        dateString
    }
}
