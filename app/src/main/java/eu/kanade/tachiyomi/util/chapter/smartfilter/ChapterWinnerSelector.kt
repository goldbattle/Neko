package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import org.nekomanga.constants.Constants
import org.nekomanga.constants.MdConstants
import org.nekomanga.domain.chapter.ChapterItem

internal class ChapterWinnerSelector(
    private val manga: Manga,
    private val downloadManager: DownloadManager,
    private val filteredScanlators: Set<String>,
) {

    data class PickResult(
        val winner: ClassifiedChapter,
        val losers: List<ClassifiedChapter>,
        val scoreBreakdown: String,
    )

    fun pickWinner(candidates: List<ClassifiedChapter>): PickResult {
        require(candidates.isNotEmpty())
        if (candidates.size == 1) {
            return PickResult(candidates.first(), emptyList(), "single")
        }

        // Top priority: offline copy the user already has (beats source/merge/MangaDex rules
        // below).
        val downloadedOnly = candidates.filter { isDownloaded(it.chapter) }
        val competing =
            when {
                downloadedOnly.isNotEmpty() -> downloadedOnly
                candidates.any { prefersOverNativeMangaDex(it.chapter) } ->
                    candidates.filter {
                        !SmartChapterEligibility.isNativeMangaDexChapter(it.chapter)
                    }
                else -> candidates
            }

        if (downloadedOnly.size == 1 && competing.size == 1) {
            val winner = downloadedOnly.first()
            return PickResult(
                winner = winner,
                losers = candidates.filter { it != winner },
                scoreBreakdown = "downloadedOnly",
            )
        }

        val scored = competing.map { c ->
            val (score, breakdown) = score(c)
            Triple(c, score, breakdown)
        }

        val best = scored.maxBy { it.second }
        val losers = (candidates.filter { it != best.first }).map { it }

        val breakdown =
            buildList {
                    add(best.third)
                    if (competing.size < candidates.size) {
                        when {
                            candidates.any { isDownloaded(it.chapter) } ->
                                add("skippedNotDownloaded")
                            candidates.any { prefersOverNativeMangaDex(it.chapter) } ->
                                add("skippedNativeMangaDex")
                        }
                    }
                }
                .joinToString(",")

        return PickResult(winner = best.first, losers = losers, scoreBreakdown = breakdown)
    }

    private fun score(classified: ClassifiedChapter): Pair<Int, String> {
        val ch = classified.chapter
        var score = 0
        val parts = mutableListOf<String>()

        if (isDownloaded(ch)) {
            // Dominates source/read/upload tie-breakers — they already have this file locally.
            score += 10_000
            parts.add("downloaded+10000")
        }

        val scanlators = ChapterUtil.getScanlators(ch.chapter.scanlator)
        val sourceFiltered =
            scanlators.any { it in filteredScanlators } ||
                (SourceManager.mergeSourceNames + MdConstants.name).any { source ->
                    ChapterUtil.filteredBySource(
                        source,
                        scanlators,
                        ch.chapter.isMergedChapter(),
                        ch.chapter.isLocalSource(),
                        filteredScanlators,
                    )
                }
        if (!sourceFiltered) {
            score += 100
            parts.add("preferredSource+100")
        }

        when {
            SmartChapterEligibility.isNativeMangaDexChapter(ch) -> {
                score += 150
                parts.add("nativeMangaDex+150")
            }
            ch.chapter.isMergedChapter() -> {
                if (isMangaBallAggregator(ch)) {
                    score -= 80
                    parts.add("mangaBallAggregator-80")
                } else {
                    // Weeb Central, Komga, etc. — prefer over fan scanlator dupes (Clean Loli,
                    // Odin)
                    score += 60
                    parts.add("mergeSource+60")
                }
            }
        }

        if (ch.chapter.read) {
            score += 30
            parts.add("userRead+30")
        }

        if (SmartChapterEligibility.isLocked(ch)) {
            score -= 500
            parts.add("lockedOfficial-500")
        } else if (ch.chapter.isUnavailable) {
            score -= 500
            parts.add("unavailable-500")
        }

        score += (ch.chapter.dateUpload / 1_000_000).toInt().coerceAtMost(20)
        parts.add("upload+${(ch.chapter.dateUpload / 1_000_000).toInt().coerceAtMost(20)}")

        return score to parts.joinToString(",")
    }

    private fun isDownloaded(chapter: ChapterItem): Boolean {
        if (chapter.downloadState == Download.State.DOWNLOADED) return true
        return downloadManager.isChapterDownloaded(chapter.chapter.toDbChapter(), manga)
    }

    companion object {
        /** Fan scanlator or non–Manga Ball merge release — should beat a native MangaDex stub. */
        private fun prefersOverNativeMangaDex(chapter: ChapterItem): Boolean {
            if (SmartChapterEligibility.isNativeMangaDexChapter(chapter)) return false
            if (!chapter.chapter.isMergedChapter()) return true
            return !isMangaBallAggregator(chapter)
        }

        private fun isMangaBallAggregator(chapter: ChapterItem): Boolean {
            return chapter.chapter.scanlator.startsWith("Manga Ball")
        }

        fun formatScanlatorLabel(chapter: ChapterItem): String {
            val scanlator = chapter.chapter.scanlator
            if (scanlator.isBlank()) return "Unknown"
            if (scanlator == Constants.NO_GROUP && chapter.chapter.uploader.isNotBlank()) {
                return chapter.chapter.uploader
            }
            return ChapterUtil.getScanlators(scanlator).joinToString(", ")
        }
    }
}
