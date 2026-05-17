package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import org.nekomanga.constants.MdConstants
import org.nekomanga.domain.chapter.ChapterItem

/** Chapters that must not participate in smart dedup / winner ranking. */
object SmartChapterEligibility {

    data class RankingFilter(
        val filteredScanlators: Set<String>,
        val blockedGroups: Set<String> = emptySet(),
        val blockedUploaders: Set<String> = emptySet(),
        val scanlatorMatchAll: Boolean = false,
    )

    fun isExcludedFromRanking(chapter: ChapterItem, filter: RankingFilter): Boolean {
        if (chapter.chapter.isUnavailable) return true

        val scanlators = ChapterUtil.getScanlators(chapter.chapter.scanlator)
        if (scanlators.any { it in MdConstants.UnsupportedOfficialGroupList }) {
            return true
        }

        if (
            ChapterUtil.filterByScanlator(
                scanlators,
                chapter.chapter.uploader,
                all = false,
                filteredGroups = filter.blockedGroups,
                filteredUploaders = filter.blockedUploaders,
            )
        ) {
            return true
        }

        if (filter.filteredScanlators.isNotEmpty()) {
            if (
                ChapterUtil.filterByScanlator(
                    scanlators,
                    chapter.chapter.uploader,
                    filter.scanlatorMatchAll,
                    filter.filteredScanlators,
                )
            ) {
                return true
            }
        }

        return false
    }

    fun isNativeMangaDexChapter(chapter: ChapterItem): Boolean {
        if (chapter.chapter.isMergedChapter()) return false
        return ChapterUtil.getScanlators(chapter.chapter.scanlator).contains(MdConstants.name)
    }

    fun isLocked(chapter: ChapterItem): Boolean {
        if (chapter.chapter.isUnavailable) return true
        return ChapterUtil.getScanlators(chapter.chapter.scanlator).any { scanlator ->
            scanlator in MdConstants.UnsupportedOfficialGroupList
        }
    }
}
