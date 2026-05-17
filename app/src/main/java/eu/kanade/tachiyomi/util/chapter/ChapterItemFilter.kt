package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterContext
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterEngine
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterInput
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterLogger
import org.nekomanga.constants.Constants
import org.nekomanga.constants.MdConstants
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.details.MangaDetailsPreferences
import org.nekomanga.domain.library.LibraryPreferences
import org.nekomanga.domain.reader.ReaderPreferences
import org.nekomanga.domain.site.MangaDexPreferences
import org.nekomanga.usecases.chapters.ChapterReadProgress
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ChapterItemFilter(
    val preferences: PreferencesHelper = Injekt.get(),
    val mangaDexPreferences: MangaDexPreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    val readerPreferences: ReaderPreferences = Injekt.get(),
    val mangaDetailsPreferences: MangaDetailsPreferences = Injekt.get(),
    val downloadManager: DownloadManager = Injekt.get(),
    val smartChapterEngine: SmartChapterEngine = SmartChapterEngine(),
) {

    /** filters chapters based on the manga values */
    fun filterChapters(chapters: List<ChapterItem>, manga: Manga): List<ChapterItem> {
        return filterChaptersWithContext(chapters, manga).first
    }

    /**
     * Manga-details list: gate + smart dedup (when enabled) + per-manga display filters (read /
     * download / bookmark / availability). Library unread badges do **not** use this — they use
     * [computeLibraryUnreadCount] via [org.nekomanga.usecases.chapters.CachedMangaUnread.refresh].
     */
    fun filterChaptersWithContext(
        chapters: List<ChapterItem>,
        manga: Manga,
        userReadChapterIds: Set<Long> = emptySet(),
    ): Pair<List<ChapterItem>, SmartChapterContext?> {
        val (afterSmart, smartContext) = processGateAndSmart(chapters, manga, userReadChapterIds)
        return applyDisplayFilters(afterSmart, manga, smartContext) to smartContext
    }

    /**
     * Shared gate + smart-filter pipeline (no manga display filters). Used for library unread
     * counting and as the base for [filterChaptersWithContext].
     */
    internal fun processGateAndSmart(
        chapters: List<ChapterItem>,
        manga: Manga,
        userReadChapterIds: Set<Long> = emptySet(),
    ): Pair<List<ChapterItem>, SmartChapterContext?> {
        val scanlatorFiltered =
            filterChaptersByScanlatorsAndLanguage(
                chapters,
                manga,
                mangaDexPreferences,
                libraryPreferences,
            )

        val smartContext =
            if (manga.smartChapterFilterEnabled(mangaDetailsPreferences)) {
                smartChapterEngine.process(
                    SmartChapterInput(
                        manga = manga,
                        chapters = scanlatorFiltered,
                        downloadManager = downloadManager,
                        filteredScanlators = SmartChapterEngine.filteredScanlatorsFor(manga),
                        rankingFilter =
                            SmartChapterEngine.rankingFilterFor(
                                manga,
                                mangaDexPreferences,
                                libraryPreferences,
                            ),
                        verboseLogging =
                            mangaDetailsPreferences.smartChapterFilterVerboseLog().get(),
                        userReadChapterIds = userReadChapterIds,
                    )
                )
            } else if (scanlatorFiltered.size > 1) {
                SmartChapterLogger.logSkipped(
                    manga,
                    scanlatorFiltered.size,
                    reason = "filter disabled for manga",
                    verboseLogging = mangaDetailsPreferences.smartChapterFilterVerboseLog().get(),
                )
                null
            } else {
                null
            }

        val afterSmart = smartContext?.visibleChapters ?: scanlatorFiltered
        return afterSmart to smartContext
    }

    fun filterFromSmartContext(smartContext: SmartChapterContext, manga: Manga): List<ChapterItem> {
        return applyDisplayFilters(smartContext.visibleChapters, manga, smartContext)
    }

    /**
     * Library badge unread only — delegates to [processGateAndSmart] then counts visible winners.
     * Call only via [org.nekomanga.usecases.chapters.CachedMangaUnread].
     */
    internal fun computeLibraryUnreadCount(chapters: List<ChapterItem>, manga: Manga): Int {
        val userReadChapterIds = chapters.filter { it.chapter.read }.map { it.chapter.id }.toSet()
        val (visible, smartContext) = processGateAndSmart(chapters, manga, userReadChapterIds)
        if (smartContext != null) {
            return smartContext.countUnread()
        }
        return visible.count { chapter -> !chapter.chapter.read && !chapter.chapter.isUnavailable }
    }

    /**
     * Same chapter set as the manga details active list: read markers, smart dedup (when enabled),
     * manga display filters, and manga sort order.
     */
    suspend fun buildMangaActiveChapterList(
        manga: Manga,
        rawChapters: List<ChapterItem>,
        chapterReadProgress: ChapterReadProgress,
        forceAscending: Boolean = false,
    ): Pair<List<ChapterItem>, SmartChapterContext?> {
        val mangaId = manga.id ?: return rawChapters to null
        val withMarkers = chapterReadProgress.applyMarkersToChapterItems(mangaId, rawChapters)
        val userReadChapterIds =
            withMarkers.filter { it.chapter.read }.map { it.chapter.id }.toSet()
        val (_, smartContext) = filterChaptersWithContext(withMarkers, manga, userReadChapterIds)
        val chapterSort =
            ChapterItemSort(
                chapterFilter = this,
                preferences = preferences,
                mangaDetailsPreferences = mangaDetailsPreferences,
            )
        val sorted = chapterSort.getChaptersSorted(manga, withMarkers, smartContext)
        val ordered =
            if (forceAscending) {
                sorted.sortedWith(chapterSort.sortComparator(manga, forceAscending = true))
            } else {
                sorted
            }
        return ordered to smartContext
    }

    private fun applyDisplayFilters(
        chapters: List<ChapterItem>,
        manga: Manga,
        smartContext: SmartChapterContext?,
    ): List<ChapterItem> {
        val readEnabled = manga.readFilter(mangaDetailsPreferences) == Manga.CHAPTER_SHOW_READ
        val unreadEnabled = manga.readFilter(mangaDetailsPreferences) == Manga.CHAPTER_SHOW_UNREAD
        val downloadEnabled =
            manga.downloadedFilter(mangaDetailsPreferences) == Manga.CHAPTER_SHOW_DOWNLOADED
        val notDownloadEnabled =
            manga.downloadedFilter(mangaDetailsPreferences) == Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        val bookmarkEnabled =
            manga.bookmarkedFilter(mangaDetailsPreferences) == Manga.CHAPTER_SHOW_BOOKMARKED
        val notBookmarkEnabled =
            manga.bookmarkedFilter(mangaDetailsPreferences) == Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        val availableEnabled =
            manga.availableFilter(mangaDetailsPreferences) == Manga.CHAPTER_SHOW_AVAILABLE
        val unavailableEnabled =
            manga.availableFilter(mangaDetailsPreferences) == Manga.CHAPTER_SHOW_UNAVAILABLE

        // If none of the display filters are enabled, skip filtering them.
        return if (
            readEnabled ||
                unreadEnabled ||
                downloadEnabled ||
                notDownloadEnabled ||
                bookmarkEnabled ||
                notBookmarkEnabled ||
                unavailableEnabled ||
                availableEnabled
        ) {
            chapters.filter { chapterItem ->
                val chapter = chapterItem.chapter.toDbChapter()
                val effectivelyRead =
                    smartContext?.isEffectivelyRead(chapterItem.chapter.id) ?: chapter.read
                val isDownloaded = downloadManager.isChapterDownloaded(chapter, manga)
                val isAvailable = chapter.isAvailable(isDownloaded)
                return@filter !(readEnabled && !effectivelyRead ||
                    (unreadEnabled && effectivelyRead) ||
                    (bookmarkEnabled && !chapter.bookmark) ||
                    (notBookmarkEnabled && chapter.bookmark) ||
                    (downloadEnabled && !isDownloaded) ||
                    (notDownloadEnabled && isDownloaded) ||
                    (unavailableEnabled && isAvailable) ||
                    (availableEnabled && !isAvailable))
            }
        } else {
            chapters
        }
    }

    /**
     * Builds the reader navigation chapter list (prev/next, chapter picker).
     *
     * When smart chapter filtering is enabled for the manga and [ReaderPreferences.skipFiltered] is
     * on, the list matches the manga details active list ([buildMangaActiveChapterList]) so the
     * reader only steps through chapters the user sees there. Otherwise [skipFiltered] uses
     * [filterChapters] (display filters without smart dedup unless smart is on globally).
     */
    suspend fun filterChaptersForReader(
        chapters: List<ChapterItem>,
        manga: Manga,
        comparator: Comparator<ChapterItem>,
        selectedChapterItem: ChapterItem? = null,
        chapterReadProgress: ChapterReadProgress = Injekt.get(),
    ): List<ChapterItem> {
        val useMangaActiveList =
            readerPreferences.skipFiltered().get() &&
                manga.smartChapterFilterEnabled(mangaDetailsPreferences)

        // Align reader navigation with manga details (dedup + display filters), not legacy
        // reader-only filters.
        if (useMangaActiveList) {
            val (activeList, smartContext) =
                buildMangaActiveChapterList(
                    manga = manga,
                    rawChapters = chapters,
                    chapterReadProgress = chapterReadProgress,
                    forceAscending = true,
                )
            var filteredChapters = activeList

            if (readerPreferences.skipRead().get()) {
                filteredChapters = filteredChapters.filter { item ->
                    val read = smartContext?.isEffectivelyRead(item.chapter.id) ?: item.chapter.read
                    !read
                }
            }

            return ensureSelectedChapterInReaderList(
                filteredChapters,
                selectedChapterItem,
                comparator,
            )
        }

        // 1. Filter by isAvailable
        var filteredChapters = chapters.filter {
            val dbChapter = it.chapter.toDbChapter()
            val isDownloaded = downloadManager.isChapterDownloaded(dbChapter, manga)
            // Uses [Chapter.isAvailable] extension on the DB chapter model.
            dbChapter.isAvailable(isDownloaded)
        }

        // 2. Filter by scanlators/language
        filteredChapters =
            filterChaptersByScanlatorsAndLanguage(
                filteredChapters,
                manga,
                mangaDexPreferences,
                libraryPreferences,
            )

        // 3. Filter out unsupported groups
        filteredChapters = filteredChapters.filter {
            it.chapter.scanlator !in MdConstants.UnsupportedOfficialGroupList
        }

        // 4. Check if reader filters are enabled
        if (
            !readerPreferences.skipRead().get() &&
                !readerPreferences.skipFiltered().get() &&
                !readerPreferences.skipDuplicates().get()
        ) {
            return filteredChapters.sortedWith(comparator)
        }

        // 5. Apply skipRead
        if (readerPreferences.skipRead().get()) {
            filteredChapters = filteredChapters.filter { !it.chapter.read }
        }

        // 6. Apply skipFiltered (manga display filters; smart dedup when enabled)
        if (readerPreferences.skipFiltered().get()) {
            filteredChapters = filterChapters(filteredChapters, manga)
        }

        // 7. Sort
        filteredChapters = filteredChapters.sortedWith(comparator)

        // 8. Apply skipDuplicates (legacy dedup; skipped when smart + skipFiltered above)
        if (readerPreferences.skipDuplicates().get()) {
            filteredChapters =
                filteredChapters.partitionByChapterNumber().mapNotNull { chapterItems ->
                    chapterItems.find { it.chapter.id == selectedChapterItem?.chapter?.id }
                        ?: chapterItems.find {
                            it.chapter.scanlator == selectedChapterItem?.chapter?.scanlator &&
                                it.chapter.uploader == selectedChapterItem?.chapter?.uploader
                        }
                        ?: chapterItems.maxByOrNull {
                            val mainScans =
                                ChapterUtil.getScanlators(it.chapter.scanlator).toMutableSet()
                            if (Constants.NO_GROUP in mainScans)
                                it.chapter.uploader?.let { up -> mainScans.add(up) }

                            val currScans =
                                ChapterUtil.getScanlators(selectedChapterItem?.chapter?.scanlator)
                                    .toMutableSet()
                            if (Constants.NO_GROUP in currScans)
                                selectedChapterItem?.chapter?.uploader?.let { up ->
                                    currScans.add(up)
                                }

                            mainScans.intersect(currScans).size
                        }
                }
        }

        return ensureSelectedChapterInReaderList(filteredChapters, selectedChapterItem, comparator)
    }

    /** Re-add the open chapter when filters would have removed it, then re-sort. */
    private fun ensureSelectedChapterInReaderList(
        filteredChapters: List<ChapterItem>,
        selectedChapterItem: ChapterItem?,
        comparator: Comparator<ChapterItem>,
    ): List<ChapterItem> {
        if (selectedChapterItem?.chapter?.id == null) {
            return filteredChapters
        }
        if (filteredChapters.any { it.chapter.id == selectedChapterItem.chapter.id }) {
            return filteredChapters
        }
        return (filteredChapters + selectedChapterItem).sortedWith(comparator)
    }

    /** filters chapters for scanlators */
    fun filterChaptersByScanlatorsAndLanguage(
        chapters: List<ChapterItem>,
        manga: Manga,
        mangaDexPreferences: MangaDexPreferences,
        libraryPreferences: LibraryPreferences,
    ): List<ChapterItem> {

        val blockedGroups = mangaDexPreferences.blockedGroups().get().toSet()
        val blockedUploaders = mangaDexPreferences.blockedUploaders().get().toSet()

        // Filtered sources, groups and uploaders
        val filteredScanlators = ChapterUtil.getScanlators(manga.filtered_scanlators).toSet()
        val filteredLanguages = ChapterUtil.getLanguages(manga.filtered_language).toSet()

        val sources = SourceManager.mergeSourceNames + MdConstants.name
        val scanlatorMatchAll = libraryPreferences.chapterScanlatorFilterOption().get() == 0

        return chapters.filterNot { chapterItem ->
            val scanlators = ChapterUtil.getScanlators(chapterItem.chapter.scanlator)
            val languages = ChapterUtil.getLanguages(chapterItem.chapter.language)

            val sourceFiltered = sources.any { sourceName ->
                ChapterUtil.filteredBySource(
                    sourceName,
                    scanlators,
                    chapterItem.chapter.isMergedChapter(),
                    chapterItem.chapter.isLocalSource(),
                    filteredScanlators,
                )
            }

            val languageFiltered = ChapterUtil.filterByLanguage(languages, filteredLanguages)

            val blockedScanlator =
                ChapterUtil.filterByScanlator(
                    scanlators,
                    chapterItem.chapter.uploader,
                    false,
                    blockedGroups,
                    blockedUploaders,
                )
            val filteredScanlator =
                ChapterUtil.filterByScanlator(
                    scanlators,
                    chapterItem.chapter.uploader,
                    scanlatorMatchAll,
                    filteredScanlators,
                )
            sourceFiltered || languageFiltered || blockedScanlator || filteredScanlator
        }
    }

    // Adapted from https://stackoverflow.com/a/65465410
    fun List<ChapterItem>.partitionByChapterNumber(): List<List<ChapterItem>> {
        val result = mutableListOf<List<ChapterItem>>()
        var currentList = mutableListOf<ChapterItem>()
        this.forEachIndexed { i, ch ->
            currentList.add(ch)
            if (
                i + 1 >= this.size ||
                    getChapterNum(ch) == null ||
                    getChapterNum(ch) != getChapterNum(this[i + 1]) ||
                    (getVolumeNum(ch) != getVolumeNum(this[i + 1]) &&
                        getVolumeNum(ch) != null &&
                        getVolumeNum(this[i + 1]) != null)
            ) {
                result.add(currentList.toList())
                currentList = mutableListOf()
            }
        }
        return result
    }
}
