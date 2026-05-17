package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterContext
import org.nekomanga.constants.MdConstants
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.details.MangaDetailsPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ChapterItemSort(
    val chapterFilter: ChapterItemFilter = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    val mangaDetailsPreferences: MangaDetailsPreferences = Injekt.get(),
) {

    fun getChaptersSorted(
        manga: Manga,
        rawChapters: List<ChapterItem>,
        smartContext: SmartChapterContext? = null,
    ): List<ChapterItem> {
        val filtered =
            if (smartContext != null) {
                chapterFilter.filterFromSmartContext(smartContext, manga)
            } else {
                chapterFilter.filterChapters(rawChapters, manga)
            }
        return filtered.sortedWith(sortComparator(manga))
    }

    fun getNextUnreadChapter(
        manga: Manga,
        rawChapters: List<ChapterItem>,
        andFiltered: Boolean = true,
        smartContext: SmartChapterContext? = null,
    ): ChapterItem? {
        val chapters =
            when {
                andFiltered -> {
                    val filtered =
                        if (smartContext != null) {
                            chapterFilter.filterFromSmartContext(smartContext, manga)
                        } else {
                            chapterFilter.filterChapters(rawChapters, manga)
                        }
                    filtered.filter { chapter ->
                        val read =
                            smartContext?.isEffectivelyRead(chapter.chapter.id)
                                ?: chapter.chapter.read
                        !read && !chapter.chapter.isUnavailable
                    }
                }
                else ->
                    rawChapters.filter {
                        val read = smartContext?.isEffectivelyRead(it.chapter.id) ?: it.chapter.read
                        !read && !it.chapter.isUnavailable
                    }
            }.filter { it.chapter.scanlator !in MdConstants.UnsupportedOfficialGroupList }
        return chapters.sortedWith(sortComparator(manga, true)).firstOrNull()
    }

    fun sortComparator(manga: Manga, forceAscending: Boolean = false): Comparator<ChapterItem> {
        // arrow down in UI is ASC.  The below sorts them all that way.
        val sortAsc =
            if (forceAscending) {
                true
            } else {
                manga.sortDescending(mangaDetailsPreferences) // this really is Asc
            }

        return when (manga.chapterOrder(mangaDetailsPreferences)) {
            Manga.CHAPTER_SORTING_SOURCE ->
                when (sortAsc) {
                    true -> compareBy { it.chapter.sourceOrder }
                    false -> compareByDescending { it.chapter.sourceOrder }
                }

            Manga.CHAPTER_SORTING_UPLOAD_DATE ->
                when (sortAsc) {
                    true ->
                        compareBy<ChapterItem> { it.chapter.dateUpload }
                            .thenByDescending { it.chapter.smartOrder }
                    false ->
                        compareByDescending<ChapterItem> { it.chapter.dateUpload }
                            .thenBy { it.chapter.smartOrder }
                }
            else -> { // default is Smart Sort
                when (sortAsc) {
                    true -> compareByDescending { it.chapter.smartOrder }
                    false -> compareBy { it.chapter.smartOrder }
                }
            }
        }
    }
}
