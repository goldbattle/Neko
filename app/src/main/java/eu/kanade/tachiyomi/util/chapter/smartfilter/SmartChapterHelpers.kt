package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.chapter.ChapterItemFilter
import org.nekomanga.data.database.repository.ChapterRepository
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.toChapterItem
import org.nekomanga.domain.chapter.toSimpleChapter
import org.nekomanga.domain.details.MangaDetailsPreferences
import org.nekomanga.usecases.chapters.CachedMangaUnread
import org.nekomanga.usecases.chapters.ChapterReadProgress

/**
 * Updates feed / library-update helpers. Filtered unread for library badges is persisted via
 * [CachedMangaUnread] on the manga row (`cached_unread`).
 */
object SmartChapterHelpers {

    suspend fun buildContextCache(
        mangas: List<Manga>,
        chapterRepository: ChapterRepository,
        chapterItemFilter: ChapterItemFilter,
        mangaDetailsPreferences: MangaDetailsPreferences,
        chapterReadProgress: ChapterReadProgress,
        cachedMangaUnread: CachedMangaUnread,
    ): Map<Long, SmartChapterContext?> {
        val smartMangas = mangas.filter { it.smartChapterFilterEnabled(mangaDetailsPreferences) }
        if (smartMangas.isEmpty()) return emptyMap()

        val mangaIds = smartMangas.mapNotNull { it.id }
        val chaptersByMangaId =
            chapterRepository.getChaptersForMangaIds(mangaIds).groupBy { it.manga_id }

        return smartMangas.associate { manga ->
            val mangaId = manga.id!!
            mangaId to
                buildSmartContextForManga(
                    manga = manga,
                    dbChapters = chaptersByMangaId[mangaId].orEmpty(),
                    chapterItemFilter = chapterItemFilter,
                    mangaDetailsPreferences = mangaDetailsPreferences,
                    chapterReadProgress = chapterReadProgress,
                    cachedMangaUnread = cachedMangaUnread,
                )
        }
    }

    suspend fun buildSmartContextForManga(
        manga: Manga,
        dbChapters: List<Chapter>,
        chapterItemFilter: ChapterItemFilter,
        mangaDetailsPreferences: MangaDetailsPreferences,
        chapterReadProgress: ChapterReadProgress,
        cachedMangaUnread: CachedMangaUnread,
    ): SmartChapterContext? {
        if (!manga.smartChapterFilterEnabled(mangaDetailsPreferences)) return null

        val mangaId = manga.id ?: return null
        val chapterItems = cachedMangaUnread.buildChapterItemsForFilter(manga, dbChapters)
        if (chapterItems.isEmpty()) return null

        val withMarkers = chapterReadProgress.applyMarkersToChapterItems(mangaId, chapterItems)
        val userReadChapterIds =
            withMarkers.filter { it.chapter.read }.map { it.chapter.id }.toSet()

        return chapterItemFilter
            .filterChaptersWithContext(withMarkers, manga, userReadChapterIds)
            .second
    }

    suspend fun refreshCachedUnreadForManga(manga: Manga, cachedMangaUnread: CachedMangaUnread) {
        cachedMangaUnread.refresh(manga)
    }

    /** New chapters that should appear in the library-update notification / Updates feed. */
    suspend fun filterNewChaptersForNotification(
        manga: Manga,
        newChapters: List<Chapter>,
        chapterRepository: ChapterRepository,
        chapterItemFilter: ChapterItemFilter,
        mangaDetailsPreferences: MangaDetailsPreferences,
        chapterReadProgress: ChapterReadProgress,
        cachedMangaUnread: CachedMangaUnread,
    ): List<Chapter> {
        val sorted = newChapters.sortedBy { it.chapter_number }
        if (!manga.smartChapterFilterEnabled(mangaDetailsPreferences)) {
            return sorted
        }

        val mangaId = manga.id ?: return sorted
        val context =
            buildSmartContextForManga(
                manga = manga,
                dbChapters = chapterRepository.getChaptersForManga(mangaId),
                chapterItemFilter = chapterItemFilter,
                mangaDetailsPreferences = mangaDetailsPreferences,
                chapterReadProgress = chapterReadProgress,
                cachedMangaUnread = cachedMangaUnread,
            ) ?: return sorted

        return sorted.filter { chapter ->
            val item = chapter.toSimpleChapter()?.toChapterItem() ?: return@filter true
            shouldShowInUpdates(item, context)
        }
    }

    fun shouldShowInUpdates(chapterItem: ChapterItem, context: SmartChapterContext?): Boolean {
        if (context == null) return true
        return context.shouldShowInUpdates(chapterItem)
    }
}
