package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import org.nekomanga.domain.chapter.ChapterItem

data class SmartChapterInput(
    val manga: Manga,
    val chapters: List<ChapterItem>,
    val downloadManager: DownloadManager,
    val filteredScanlators: Set<String> = emptySet(),
    val rankingFilter: SmartChapterEligibility.RankingFilter? = null,
    val verboseLogging: Boolean = false,
    /** Chapter row IDs the user marked read in DB (before marker read overlay). */
    val userReadChapterIds: Set<Long> = emptySet(),
) {
    fun effectiveRankingFilter(): SmartChapterEligibility.RankingFilter =
        rankingFilter
            ?: SmartChapterEligibility.RankingFilter(filteredScanlators = filteredScanlators)
}
