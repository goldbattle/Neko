package org.nekomanga.usecases.chapters

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.util.chapter.ChapterItemFilter
import java.util.concurrent.atomic.AtomicInteger
import org.nekomanga.data.database.repository.ChapterRepository
import org.nekomanga.data.database.repository.MangaRepository
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.toChapterItem
import org.nekomanga.domain.chapter.toSimpleChapter
import org.nekomanga.util.system.mapAsync

/**
 * **Library unread badge only** — single entry point for `manga.cached_unread`.
 *
 * Compute pipeline (all steps; do not reimplement elsewhere for badges):
 * 1. [buildChapterItemsForFilter] — all DB chapters + download state
 * 2. [ChapterReadProgress.applyMarkersToChapterItems] — cross-source read markers
 * 3. [ChapterItemFilter.computeLibraryUnreadCount] → [ChapterItemFilter.processGateAndSmart]
 *    (blocked groups/uploaders, manga scanlator/language, smart dedup) →
 *    [SmartChapterContext.countUnread]
 * 4. [MangaRepository.updateCachedUnread]
 *
 * **Not** used for: manga-details chapter list ([ChapterItemFilter.filterChaptersWithContext] adds
 * display filters), reader navigation, download-unread selection, or SQL `has_read` read totals on
 * [eu.kanade.tachiyomi.data.database.models.LibraryManga.read].
 *
 * After read-state / filter / chapter-list changes, call [refresh] or use [ChapterReadProgress]
 * (which persists via [notifyLibraryUnreadChanged]).
 */
class CachedMangaUnread(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val chapterItemFilter: ChapterItemFilter,
    private val downloadManager: DownloadManager,
    private val chapterReadProgress: ChapterReadProgress,
) {

    suspend fun refresh(mangaId: Long) {
        val manga = mangaRepository.getMangaById(mangaId) ?: return
        refresh(manga)
    }

    suspend fun refresh(manga: Manga) {
        val mangaId = manga.id ?: return
        val dbChapters = chapterRepository.getChaptersForManga(mangaId)
        // Chapter observer can emit mid-sync (after deletes, before inserts); do not zero the
        // badge.
        if (dbChapters.isEmpty()) return
        val count = computeFilteredUnread(manga, dbChapters)
        mangaRepository.updateCachedUnread(mangaId, count)
    }

    /**
     * Recomputes [eu.kanade.tachiyomi.data.database.models.Manga.cached_unread] for every library
     * title.
     */
    suspend fun refreshAllLibrary(onProgress: ((processed: Int, total: Int) -> Unit)? = null): Int {
        val ids = mangaRepository.getFavoriteMangaIds()
        val total = ids.size
        if (total == 0) return 0

        val processed = AtomicInteger(0)
        ids.mapAsync { mangaId ->
            refresh(mangaId)
            onProgress?.invoke(processed.incrementAndGet(), total)
        }
        return total
    }

    suspend fun computeFilteredUnread(manga: Manga): Int {
        val mangaId = manga.id ?: return 0
        val dbChapters = chapterRepository.getChaptersForManga(mangaId)
        return computeFilteredUnread(manga, dbChapters)
    }

    suspend fun computeFilteredUnread(manga: Manga, dbChapters: List<Chapter>): Int {
        val mangaId = manga.id ?: return 0
        if (dbChapters.isEmpty()) return 0

        val chapterItems = buildChapterItemsForFilter(manga, dbChapters)
        val withMarkers = chapterReadProgress.applyMarkersToChapterItems(mangaId, chapterItems)
        return chapterItemFilter.computeLibraryUnreadCount(withMarkers, manga)
    }

    fun buildChapterItemsForFilter(manga: Manga, dbChapters: List<Chapter>): List<ChapterItem> {
        val downloadCount = downloadManager.getDownloadCount(manga)
        return dbChapters.mapNotNull { dbChapter ->
            dbChapter.toSimpleChapter()?.let { chapter ->
                val downloadState =
                    when {
                        downloadCount > 0 &&
                            downloadManager.isChapterDownloaded(dbChapter, manga) ->
                            Download.State.DOWNLOADED
                        else -> Download.State.NOT_DOWNLOADED
                    }
                chapter.toChapterItem(downloadState = downloadState)
            }
        }
    }
}
