package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.data.database.models.Manga
import java.util.concurrent.ConcurrentHashMap
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.logging.TimberKt

internal object SmartChapterLogger {

    private const val TAG = "SmartChapterFilter"

    /** Last summary fingerprint per manga — avoids duplicate verbose dumps on cache misses. */
    private val lastSummaryKeyByMangaId = ConcurrentHashMap<Long, Int>()

    /** Last skipped-log fingerprint — filter-disabled path has no process cache. */
    private val lastSkippedKeyByMangaId = ConcurrentHashMap<Long, Int>()

    data class Params(
        val manga: Manga,
        val inputChapters: List<ChapterItem>,
        val classified: List<ClassifiedChapter>,
        val context: SmartChapterContext,
        val verboseLogging: Boolean,
        val dedupGroups: Map<LogicalChapterKey, List<ClassifiedChapter>>,
        val droppedNoRankableGroups: List<Pair<LogicalChapterKey, List<ClassifiedChapter>>> =
            emptyList(),
        val fromCache: Boolean = false,
    )

    fun log(params: Params) {
        val mangaId = params.manga.id ?: return
        val inputCount = params.inputChapters.size
        val removed = inputCount - params.context.visibleChapters.size
        val summaryKey = summaryFingerprint(inputCount, removed, params.context)
        val isRepeat = lastSummaryKeyByMangaId.put(mangaId, summaryKey) == summaryKey
        if (isRepeat) return

        // One-line summary whenever smart filter runs (debug builds). Detailed logs need the
        // Advanced setting "Smart chapter filter debug logging".
        val cacheNote = if (params.fromCache) " cached" else ""
        val verboseNote = if (params.verboseLogging) " verbose=true" else " verbose=false"
        val droppedCount = params.droppedNoRankableGroups.size
        val droppedNote = if (droppedCount > 0) " droppedWholeGroups=$droppedCount" else ""
        TimberKt.d {
            "$TAG mangaId=$mangaId title=${params.manga.title}$cacheNote " +
                "input=$inputCount visible=${params.context.visibleChapters.size} " +
                "removed=$removed$droppedNote$verboseNote"
        }

        if (!params.verboseLogging) return

        if (params.fromCache) return

        logSummary(params.manga, inputCount, params.context, removed)
        logClassificationStats(mangaId, params.classified)
        logDroppedNoRankable(mangaId, params.droppedNoRankableGroups)
        logDedupGroups(mangaId, params.dedupGroups, params.context.winnerChapterIds)
        logDecisionsVerbose(mangaId, params.inputChapters, params.context.decisions)
        logFixture(params.manga, params.inputChapters, params.context)
        logReadEquivalence(mangaId, params.context)
    }

    fun logSkipped(manga: Manga, chapterCount: Int, reason: String, verboseLogging: Boolean) {
        if (!verboseLogging) return
        val mangaId = manga.id ?: return
        val skippedKey = 31 * reason.hashCode() + chapterCount
        if (lastSkippedKeyByMangaId.put(mangaId, skippedKey) == skippedKey) return
        TimberKt.d {
            "$TAG mangaId=$mangaId title=${manga.title} skipped ($reason) " +
                "chapters=$chapterCount"
        }
    }

    fun clearDedupState(mangaId: Long? = null) {
        if (mangaId == null) {
            lastSummaryKeyByMangaId.clear()
            lastSkippedKeyByMangaId.clear()
        } else {
            lastSummaryKeyByMangaId.remove(mangaId)
            lastSkippedKeyByMangaId.remove(mangaId)
        }
    }

    private fun summaryFingerprint(
        inputCount: Int,
        removed: Int,
        context: SmartChapterContext,
    ): Int {
        var hash = inputCount
        hash = 31 * hash + removed
        hash = 31 * hash + context.visibleChapters.size
        hash = 31 * hash + context.suppressedChapterIds.hashCode()
        hash = 31 * hash + context.winnerChapterIds.hashCode()
        return hash
    }

    private fun logSummary(
        manga: Manga,
        inputCount: Int,
        context: SmartChapterContext,
        removed: Int,
    ) {
        val splitCount =
            context.decisions.count { it.reason == SuppressReason.SplitWhenWholeExists }
        val dupCount = context.decisions.count { it.reason == SuppressReason.DuplicateWhole }
        TimberKt.d {
            "$TAG mangaId=${manga.id} title=${manga.title} " +
                "input=$inputCount visible=${context.visibleChapters.size} " +
                "removed=$removed suppressed=${context.suppressedChapterIds.size} " +
                "decisions=${context.decisions.size} splits=$splitCount duplicates=$dupCount"
        }
        when {
            removed > 0 ->
                TimberKt.d {
                    "$TAG mangaId=${manga.id} hid $removed of $inputCount chapters " +
                        "($splitCount split groups, $dupCount duplicate groups)"
                }
            inputCount <= 1 ->
                TimberKt.d { "$TAG mangaId=${manga.id} nothing to dedupe (input=$inputCount)" }
            else ->
                TimberKt.d {
                    "$TAG mangaId=${manga.id} no chapters removed " + "(no duplicate/split groups)"
                }
        }
    }

    private fun logClassificationStats(mangaId: Long, classified: List<ClassifiedChapter>) {
        val byKind = classified.groupingBy { it.kind }.eachCount()
        TimberKt.d {
            "$TAG mangaId=$mangaId classify " +
                byKind.entries.joinToString(" ") { "${it.key}=${it.value}" }
        }
        val multiDedupKeys =
            classified
                .groupBy { ChapterClassifier.deduplicationKey(it) }
                .count { it.value.size > 1 }
        if (multiDedupKeys > 0) {
            TimberKt.d { "$TAG mangaId=$mangaId dedupKeyGroupsWithDuplicates=$multiDedupKeys" }
        }
    }

    private fun logDroppedNoRankable(
        mangaId: Long,
        groups: List<Pair<LogicalChapterKey, List<ClassifiedChapter>>>,
    ) {
        if (groups.isEmpty()) return
        TimberKt.d {
            "$TAG mangaId=$mangaId noRankableFallback groups=${groups.size} " +
                "(all candidates blocked/locked/unavailable — showing best fallback)"
        }
        for ((key, members) in groups) {
            TimberKt.d { "$TAG mangaId=$mangaId noRankableKey=$key count=${members.size}" }
            for (member in members) {
                TimberKt.d {
                    "$TAG mangaId=$mangaId   fallback ${SmartChapterLogLabels.label(member)}"
                }
            }
        }
    }

    private fun logDedupGroups(
        mangaId: Long,
        dedupGroups: Map<LogicalChapterKey, List<ClassifiedChapter>>,
        winnerIds: Set<Long>,
    ) {
        val contested = dedupGroups.filter { it.value.size > 1 }
        if (contested.isEmpty()) return

        TimberKt.d { "$TAG mangaId=$mangaId dedupGroups contested=${contested.size}" }
        for ((key, members) in contested.entries.sortedByDescending { it.value.size }) {
            TimberKt.d { "$TAG mangaId=$mangaId dedupKey=$key count=${members.size}" }
            for (member in members) {
                val won = member.chapter.chapter.id in winnerIds
                val marker = if (won) "WIN" else "LOSE"
                TimberKt.d {
                    "$TAG mangaId=$mangaId   $marker ${SmartChapterLogLabels.label(member)}"
                }
            }
        }
    }

    private fun logDecisionsVerbose(
        mangaId: Long,
        inputChapters: List<ChapterItem>,
        decisions: List<FilterDecision>,
    ) {
        val byId = inputChapters.associateBy { it.chapter.id }
        for (decision in decisions) {
            val winnerLabel =
                decision.winnerChapterId?.let { id ->
                    byId[id]?.let { SmartChapterLogLabels.label(it) }
                } ?: "none"
            val suppressedLabels =
                decision.suppressedChapterIds.mapNotNull { id ->
                    byId[id]?.let { SmartChapterLogLabels.label(it) }
                }
            TimberKt.d {
                "$TAG mangaId=$mangaId decision reason=${decision.reason} " +
                    "kind=${decision.kind} key=${decision.logicalKey} " +
                    "winner=$winnerLabel scores=${decision.scoreBreakdown}"
            }
            for (label in suppressedLabels) {
                TimberKt.d { "$TAG mangaId=$mangaId   suppressed $label" }
            }
        }
    }

    private fun logFixture(
        manga: Manga,
        inputChapters: List<ChapterItem>,
        context: SmartChapterContext,
    ) {
        val snippet =
            SmartChapterLogLabels.formatFixtureSnippet(
                mangaId = manga.id!!,
                mangaTitle = manga.title,
                chapters = inputChapters,
                context = context,
            )
        if (snippet.isBlank()) return

        TimberKt.d { "$TAG FIXTURE_START mangaId=${manga.id}" }
        snippet.lines().forEach { line -> TimberKt.d { "$TAG $line" } }
        TimberKt.d {
            "$TAG FIXTURE_END mangaId=${manga.id} " +
                "(copy lines between START/END into SmartChapterEngineTest)"
        }
    }

    private fun logReadEquivalence(mangaId: Long, context: SmartChapterContext) {
        var logged = 0
        for ((id, meta) in context.metadataByChapterId) {
            if (!meta.readState.autoRead && !meta.readState.userRead) continue
            val via = meta.readState.userReadVia
            TimberKt.d {
                "$TAG mangaId=$mangaId readState id=$id userRead=${meta.readState.userRead} " +
                    "autoRead=${meta.readState.autoRead} viaId=${via?.chapterId} " +
                    "viaScan=${via?.scanlatorLabel} kind=${meta.kind}"
            }
            logged++
            if (logged >= 40) {
                TimberKt.d { "$TAG mangaId=$mangaId readState ... truncated after 40 entries" }
                break
            }
        }
    }
}
