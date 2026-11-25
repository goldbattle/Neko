package org.nekomanga.presentation.screens.feed.updates

import android.text.format.DateUtils
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size as CoilSize
import eu.kanade.tachiyomi.data.coil.dynamicCover
import eu.kanade.tachiyomi.ui.manga.MangaConstants
import java.util.Date
import jp.wasabeef.gap.Gap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.nekomanga.R
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.presentation.components.UiText
import org.nekomanga.presentation.components.listcard.ExpressiveListCard
import org.nekomanga.presentation.components.listcard.ListCardType
import org.nekomanga.presentation.screens.EmptyScreen
import org.nekomanga.presentation.screens.feed.FeedManga
import org.nekomanga.presentation.screens.feed.FeedScreenActions
import org.nekomanga.presentation.theme.Size

@Composable
fun FeedUpdatesPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    feedUpdatesMangaList: List<FeedManga> = listOf(),
    outlineCovers: Boolean,
    dynamicCovers: Boolean,
    useVividColorHeaders: Boolean,
    hasMoreResults: Boolean,
    loadingResults: Boolean,
    groupedBySeries: Boolean,
    updatesFetchSort: Boolean,
    feedScreenActions: FeedScreenActions,
    loadNextPage: () -> Unit,
) {
    if (feedUpdatesMangaList.isEmpty()) {
        if (!loadingResults) {
            EmptyScreen(message = UiText.StringResource(R.string.no_results_found))
        }
        return
    }
    val headerColor =
        when (useVividColorHeaders) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.onSurface
        }

    when (groupedBySeries) {
        true -> {
            Grouped(
                modifier = modifier,
                contentPadding = contentPadding,
                feedUpdatesMangaList = feedUpdatesMangaList,
                outlineCovers = outlineCovers,
                dynamicCovers = dynamicCovers,
                headerColor = headerColor,
                hasMoreResults = hasMoreResults,
                loadingResults = loadingResults,
                updatesFetchSort = updatesFetchSort,
                feedScreenActions = feedScreenActions,
                loadNextPage = loadNextPage,
            )
        }
        false -> {
            Ungrouped(
                modifier = modifier,
                contentPadding = contentPadding,
                feedUpdatesMangaList = feedUpdatesMangaList,
                outlineCovers = outlineCovers,
                dynamicCovers = dynamicCovers,
                headerColor = headerColor,
                hasMoreResults = hasMoreResults,
                loadingResults = loadingResults,
                updatesFetchSort = updatesFetchSort,
                feedScreenActions = feedScreenActions,
                loadNextPage = loadNextPage,
            )
        }
    }
}

@Composable
private fun Grouped(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    feedUpdatesMangaList: List<FeedManga> = listOf(),
    headerColor: Color,
    outlineCovers: Boolean = false,
    dynamicCovers: Boolean = false,
    hasMoreResults: Boolean = false,
    loadingResults: Boolean = false,
    updatesFetchSort: Boolean,
    feedScreenActions: FeedScreenActions,
    loadNextPage: () -> Unit,
) {
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

    val groupedState = remember { mutableStateOf(computeGroupedBySeries(feedUpdatesMangaList)) }
    LaunchedEffect(feedUpdatesMangaList) {
        if (groupedState.value.source === feedUpdatesMangaList) return@LaunchedEffect
        val result =
            withContext(Dispatchers.Default) { computeGroupedBySeries(feedUpdatesMangaList) }
        groupedState.value = result
    }
    val groupedBySeries = groupedState.value.series
    val renderedGroups = groupedState.value.rendered

    PrefetchCovers(
        scrollState,
        remember(renderedGroups) {
            buildList<Any?> {
                renderedGroups.forEach { (dateString, list) ->
                    if (dateString.isNotEmpty()) add(null)
                    list.forEach { add(it.artwork) }
                }
            }
        },
        dynamicCovers,
    )

    LazyColumn(modifier = modifier, state = scrollState, contentPadding = contentPadding) {
        var timeSpan = ""
        // 2. Iterate through the date groups
        renderedGroups.forEach { (dateString, seriesListForDate) ->

            // 3. Date Header Logic
            if (dateString.isNotEmpty()) {
                // Find the global index of the first item in this date group
                // to correctly trigger the header display once per date.
                val firstItem = seriesListForDate.first()

                // The 'timeSpan' state needs to track the date string globally to prevent duplicate
                // headers
                if (firstItem == groupedBySeries.first())
                    timeSpan = "" // Reset tracker at the start

                if (timeSpan != dateString) {
                    timeSpan = dateString

                    val prefix =
                        when (updatesFetchSort) {
                            true -> R.string.fetched_
                            false -> R.string.updated_
                        }

                    item(key = dateString, contentType = "header") {
                        Text(
                            text = stringResource(id = prefix, dateString),
                            color = headerColor,
                            style = MaterialTheme.typography.titleLarge,
                            modifier =
                                Modifier.padding(
                                    start = Size.small,
                                    top = Size.small,
                                    end = Size.small,
                                ),
                        )
                    }
                }
            }

            // 4. Iterate through the series within the current date group
            seriesListForDate.forEachIndexed { groupIndex, feedManga ->
                val latestChapter = feedManga.chapters.first()

                val listCardType =
                    when {
                        groupIndex == 0 && seriesListForDate.size > 1 -> ListCardType.Top
                        groupIndex == seriesListForDate.size - 1 && seriesListForDate.size > 1 ->
                            ListCardType.Bottom
                        seriesListForDate.size == 1 -> ListCardType.Single
                        else -> ListCardType.Center
                    }

                item(
                    key = "${groupIndex}-${feedManga.mangaId}-${latestChapter.chapter.id}",
                    contentType = "row",
                ) {
                    // 7. Wrap UpdatesCard with ExpressiveListCard
                    ExpressiveListCard(
                        modifier = Modifier.padding(horizontal = Size.small),
                        listCardType = listCardType, // Pass the calculated shape
                    ) {
                        UpdatesCard(
                            chapterItem = latestChapter,
                            numberOfChapters = feedManga.chapters.size,
                            isGrouped = true,
                            mangaTitle = feedManga.mangaTitle,
                            artwork = feedManga.artwork,
                            outlineCovers = outlineCovers,
                            dynamicCovers = dynamicCovers,
                            mangaClick =
                                remember<() -> Unit>(feedManga, feedScreenActions) {
                                    { feedScreenActions.mangaClick(feedManga.mangaId) }
                                },
                            chapterClick =
                                remember<(Long) -> Unit>(feedManga, feedScreenActions) {
                                    { chapterId ->
                                        feedScreenActions.chapterClick(feedManga.mangaId, chapterId)
                                    }
                                },
                            chapterSwipe =
                                remember<(ChapterItem) -> Unit>(latestChapter, feedScreenActions) {
                                    { _ -> feedScreenActions.chapterSwipe(latestChapter) }
                                },
                            downloadClick =
                                remember<(MangaConstants.DownloadAction) -> Unit>(
                                    latestChapter,
                                    feedManga,
                                    feedScreenActions,
                                ) {
                                    { action ->
                                        feedScreenActions.downloadClick(
                                            latestChapter,
                                            feedManga,
                                            action,
                                        )
                                    }
                                },
                        )
                    }

                    // Add Gap only if it's not the last card in the date group
                    if (groupIndex != seriesListForDate.size - 1) {
                        Gap(Size.tiny)
                    }
                }
            }
        }
    }
}

@Composable
private fun Ungrouped(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    feedUpdatesMangaList: List<FeedManga> = listOf(),
    headerColor: Color,
    outlineCovers: Boolean = false,
    dynamicCovers: Boolean,
    hasMoreResults: Boolean = false,
    loadingResults: Boolean = false,
    updatesFetchSort: Boolean,
    feedScreenActions: FeedScreenActions,
    loadNextPage: () -> Unit,
) {
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

    val ungroupedState = remember { mutableStateOf(computeUngrouped(feedUpdatesMangaList)) }
    LaunchedEffect(feedUpdatesMangaList) {
        if (ungroupedState.value.source === feedUpdatesMangaList) return@LaunchedEffect
        val result = withContext(Dispatchers.Default) { computeUngrouped(feedUpdatesMangaList) }
        ungroupedState.value = result
    }
    val grouped = ungroupedState.value.grouped

    PrefetchCovers(
        scrollState,
        remember(grouped) {
            buildList<Any?> {
                grouped.forEach { (dateString, list) ->
                    if (dateString.isNotEmpty()) add(null)
                    list.forEach { fm -> fm.chapters.forEach { _ -> add(fm.artwork) } }
                }
            }
        },
        dynamicCovers,
    )

    LazyColumn(modifier = modifier, state = scrollState, contentPadding = contentPadding) {
        var timeSpan = ""
        grouped.forEach { (dateString, mangaListForDate) ->
            if (dateString.isNotEmpty() && timeSpan != dateString) {
                timeSpan = dateString

                val prefix =
                    when (updatesFetchSort) {
                        true -> R.string.fetched_
                        false -> R.string.updated_
                    }

                item(key = dateString, contentType = "header") {
                    Text(
                        text = stringResource(id = prefix, dateString),
                        color = headerColor,
                        style = MaterialTheme.typography.titleLarge,
                        modifier =
                            Modifier.padding(
                                start = Size.small,
                                top = Size.small,
                                end = Size.small,
                            ),
                    )
                }
            }

            val chaptersForDate = mangaListForDate.flatMap { feedManga ->
                feedManga.chapters.map { chapter -> Pair(feedManga, chapter) }
            }

            chaptersForDate.forEachIndexed { chapterIndex, (feedManga, chapterItem) ->
                val listCardType =
                    when {
                        chapterIndex == 0 && chaptersForDate.size > 1 -> ListCardType.Top
                        chapterIndex == chaptersForDate.size - 1 && chaptersForDate.size > 1 ->
                            ListCardType.Bottom
                        chaptersForDate.size == 1 -> ListCardType.Single
                        else -> ListCardType.Center
                    }

                item(
                    key =
                        "$dateString-$chapterIndex-${feedManga.mangaId}-${chapterItem.chapter.id}",
                    contentType = "row",
                ) {
                    ExpressiveListCard(
                        modifier = Modifier.padding(horizontal = Size.small),
                        listCardType = listCardType, // Pass the correct type
                    ) {
                        UpdatesCard(
                            chapterItem = chapterItem,
                            mangaTitle = feedManga.mangaTitle,
                            artwork = feedManga.artwork,
                            outlineCovers = outlineCovers,
                            dynamicCovers = dynamicCovers,
                            mangaClick =
                                remember<() -> Unit>(feedManga, feedScreenActions) {
                                    { feedScreenActions.mangaClick(feedManga.mangaId) }
                                },
                            chapterClick =
                                remember<(Long) -> Unit>(feedManga, feedScreenActions) {
                                    { chapterId ->
                                        feedScreenActions.chapterClick(feedManga.mangaId, chapterId)
                                    }
                                },
                            chapterSwipe =
                                remember<(ChapterItem) -> Unit>(feedScreenActions) {
                                    { item -> feedScreenActions.chapterSwipe(item) }
                                },
                            downloadClick =
                                remember<(MangaConstants.DownloadAction) -> Unit>(
                                    chapterItem,
                                    feedManga,
                                    feedScreenActions,
                                ) {
                                    { action ->
                                        feedScreenActions.downloadClick(
                                            chapterItem,
                                            feedManga,
                                            action,
                                        )
                                    }
                                },
                        )
                    }
                    if (chapterIndex != chaptersForDate.size - 1) {
                        Gap(Size.tiny)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefetchCovers(
    listState: androidx.compose.foundation.lazy.LazyListState,
    artworks: List<Any?>,
    dynamicCovers: Boolean,
) {
    val context = LocalContext.current
    val currentArtworks by rememberUpdatedState(artworks)
    val lastPrefetchedHolder = remember(listState, dynamicCovers) { intArrayOf(-1) }
    LaunchedEffect(listState, dynamicCovers) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (last < 0) return@collect
                val artworks = currentArtworks
                val end = minOf(last + 8, artworks.lastIndex)
                for (i in (lastPrefetchedHolder[0] + 1)..end) {
                    val data = artworks[i] ?: continue
                    context.imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(data)
                            .dynamicCover(dynamicCovers)
                            .precision(Precision.INEXACT)
                            .size(CoilSize.ORIGINAL)
                            .build(),
                    )
                }
                if (end > lastPrefetchedHolder[0]) lastPrefetchedHolder[0] = end
            }
    }
}

private fun getDateString(date: Long, currentDate: Long): String {
    return DateUtils.getRelativeTimeSpanString(date, currentDate, DateUtils.DAY_IN_MILLIS)
        .toString()
}

private class GroupedResult(
    val source: List<FeedManga>,
    val series: List<FeedManga>,
    val rendered: Map<String, List<FeedManga>>,
)

private class UngroupedResult(
    val source: List<FeedManga>,
    val grouped: Map<String, List<FeedManga>>,
)

// Each call uses its own DateStringCache (a plain HashMap), so it is safe on any thread.
private fun computeGroupedBySeries(list: List<FeedManga>): GroupedResult {
    val dateCache = DateStringCache(Date().time)
    val series =
        list
            .groupBy { dateCache.get(it.date) }
            .map {
                it.value
                    .groupBy { it.mangaId }
                    .map {
                        val (read, unread) =
                            it.value.flatMap { it.chapters }.partition { it.chapter.read }
                        val chapters = (unread.reversed() + read).toList()
                        it.value.first().copy(chapters = chapters)
                    }
            }
            .flatten()
    val rendered = series.groupBy { dateCache.get(it.date) }
    return GroupedResult(list, series, rendered)
}

private fun computeUngrouped(list: List<FeedManga>): UngroupedResult {
    val dateCache = DateStringCache(Date().time)
    return UngroupedResult(list, list.groupBy { dateCache.get(it.date) })
}

private class DateStringCache(val now: Long) {
    private val cache = HashMap<Long, String>()

    fun get(date: Long): String {
        val tz = java.util.TimeZone.getDefault()
        val day = Math.floorDiv(date + tz.getOffset(date), DateUtils.DAY_IN_MILLIS)
        return cache.getOrPut(day) { getDateString(date, now) }
    }
}
