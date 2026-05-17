package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.util.chapter.ChapterUtil

/**
 * Smart chapter filtering: deduplicate and simplify the chapter list for manga with many sources.
 *
 * **Purpose.** Merged / multi-source manga often have the same chapter from several scanlators,
 * split releases (20.1, 20.2) beside a whole (20), and locked official stubs next to readable fan
 * or merge copies. This engine keeps one visible row per logical whole chapter, hides redundant
 * splits, and builds cross-scanlator read metadata so unread counts and UI stay correct.
 *
 * **When it runs.** Only via
 * [eu.kanade.tachiyomi.util.chapter.ChapterItemFilter.filterChaptersWithContext] (manga details,
 * reader, library update, updates feed). Gated by
 * [eu.kanade.tachiyomi.data.database.models.Manga.smartChapterFilterEnabled] (per-manga flag in
 * `chapter_flags`, or global default from
 * [org.nekomanga.domain.details.MangaDetailsPreferences.smartChapterFilterByDefault]), after
 * scanlator/language/blocked-group filtering. Library unread is persisted as `cached_unread` via
 * [org.nekomanga.usecases.chapters.CachedMangaUnread]. Read persistence across scanlators is
 * handled separately by [org.nekomanga.usecases.chapters.ChapterReadProgress] and
 * [eu.kanade.tachiyomi.util.chapter.ChapterReadMarkerKeys].
 *
 * **Pipeline** ([process]):
 * 1. [ChapterClassifier] — assign [ChapterKind] (Whole / Split / Extra / Unknown) and
 *    [LogicalChapterKey] per row. Recognized number comes from DB or parsed chapter text.
 * 2. [ChapterGrouper.groupAndFilter] — group by family key; if both Whole and Split exist in a
 *    family, suppress all splits ([SuppressReason.SplitWhenWholeExists]).
 * 3. [ChapterGrouper.groupForDeduplication] — bucket remaining rows by volume-insensitive dedup key
 *    (e.g. "Vol.1 Ch.2" and "Ch.2" share one bucket; extras keep volume; unknown rows use chapter
 *    id).
 * 4. Per bucket — [SmartChapterEligibility.isExcludedFromRanking] removes unavailable, locked
 *    official ([org.nekomanga.constants.MdConstants.UnsupportedOfficialGroupList]), blocked
 *    groups/uploaders, and manga-filtered scanlators from competing. Excluded rows are never
 *    winners. If every member is excluded, the whole bucket is dropped (no locked stub promoted as
 *    winner).
 * 5. [ChapterWinnerSelector.pickWinner] — score remaining rankable copies; highest wins. Scoring
 *    (additive): preferred source +100; native MangaDex +150 (dropped from competition when fan or
 *    non–Manga Ball merge exists); merge source +60; Manga Ball aggregator −80; downloaded +10_000
 *    (any downloaded copy wins the group before other source rules); user read on row +30;
 *    locked/unavailable −500; upload date up to +20.
 * 6. [ChapterReadEquivalence.buildMetadata] — for all input chapters (including suppressed),
 *    compute user read, auto-read, and "read via" labels using equivalence keys (volume-insensitive
 *    for wholes, exact number for splits/extras). [SmartChapterContext.isEffectivelyRead] drives
 *    unread counts, next-unread, and read/unread display filters.
 *
 * **Output.** [SmartChapterContext]: [SmartChapterContext.visibleChapters],
 * [SmartChapterContext.suppressedChapterIds], [SmartChapterContext.decisions], and
 * [SmartChapterContext.metadataByChapterId]. Display filters
 * (read/unread/downloaded/bookmark/available) are applied afterward in [ChapterItemFilter].
 *
 * Results are memoized per manga in [SmartChapterProcessCache] when chapter fingerprints and
 * ranking rules are unchanged. Debug logcat: tag `SmartChapterFilter`; verbose logs via
 * [org.nekomanga.domain.details.MangaDetailsPreferences.smartChapterFilterVerboseLog].
 *
 * @see ChapterClassifier
 * @see ChapterGrouper
 * @see ChapterWinnerSelector
 * @see ChapterReadEquivalence
 * @see SmartChapterEligibility
 */
class SmartChapterEngine {

    /**
     * Run the smart-filter pipeline for [input.chapters] and return visibility + read metadata.
     * Returns [SmartChapterContext.passthrough] when the chapter list is empty.
     */
    fun process(input: SmartChapterInput): SmartChapterContext {
        if (input.chapters.isEmpty()) {
            return SmartChapterContext.passthrough(emptyList())
        }

        val mangaId = input.manga.id
        val cacheKey = SmartChapterProcessCache.keyFor(input)
        if (mangaId != null) {
            SmartChapterProcessCache.get(mangaId, cacheKey)?.let { cached ->
                SmartChapterLogger.log(
                    SmartChapterLogger.Params(
                        manga = input.manga,
                        inputChapters = input.chapters,
                        classified = emptyList(),
                        context = cached,
                        verboseLogging = input.verboseLogging,
                        dedupGroups = emptyMap(),
                        fromCache = true,
                    )
                )
                return cached
            }
        }

        val classified = input.chapters.map { ChapterClassifier.classify(it) }
        val classifiedById = classified.associateBy { it.chapter.chapter.id }

        val groupResult = ChapterGrouper.groupAndFilter(classified)
        val allDecisions = groupResult.decisions.toMutableList()
        val suppressedIds = groupResult.suppressed.map { it.chapter.chapter.id }.toMutableSet()

        val rankingFilter = input.effectiveRankingFilter()
        val selector =
            ChapterWinnerSelector(
                manga = input.manga,
                downloadManager = input.downloadManager,
                filteredScanlators = input.filteredScanlators,
            )

        val finalVisible = mutableListOf<ClassifiedChapter>()
        val winnerIds = mutableSetOf<Long>()
        val droppedNoRankable = mutableListOf<Pair<LogicalChapterKey, List<ClassifiedChapter>>>()

        val byKey = ChapterGrouper.groupForDeduplication(groupResult.visible)
        val contestedDedupGroups = mutableMapOf<LogicalChapterKey, List<ClassifiedChapter>>()
        for ((key, members) in byKey) {
            if (members.size > 1) {
                contestedDedupGroups[key] = members
            }

            val rankable = members.filter {
                !SmartChapterEligibility.isExcludedFromRanking(it.chapter, rankingFilter)
            }
            val excludedFromRanking = members.filter {
                SmartChapterEligibility.isExcludedFromRanking(it.chapter, rankingFilter)
            }
            suppressedIds.addAll(excludedFromRanking.map { it.chapter.chapter.id })

            if (rankable.isEmpty()) {
                // Every copy is excluded from ranking (locked official, blocked group, unavailable,
                // manga-hidden scanlator, etc.). Do not promote an excluded chapter as the visible
                // winner — that caused whole chapters to vanish after display filters.
                if (members.isNotEmpty()) {
                    droppedNoRankable.add(key to members)
                    suppressedIds.addAll(members.map { it.chapter.chapter.id })
                }
                continue
            }

            if (rankable.size == 1) {
                val winner = rankable.first()
                finalVisible.add(winner)
                winnerIds.add(winner.chapter.chapter.id)
                if (excludedFromRanking.isNotEmpty()) {
                    allDecisions.add(
                        FilterDecision(
                            logicalKey = key,
                            kind = winner.kind,
                            winnerChapterId = winner.chapter.chapter.id,
                            suppressedChapterIds =
                                excludedFromRanking.map { it.chapter.chapter.id },
                            reason = SuppressReason.DuplicateWhole,
                            scoreBreakdown = "soleRankable",
                        )
                    )
                }
                continue
            }

            val pick = selector.pickWinner(rankable)
            finalVisible.add(pick.winner)
            winnerIds.add(pick.winner.chapter.chapter.id)

            val loserIds =
                (pick.losers.map { it.chapter.chapter.id } +
                        excludedFromRanking.map { it.chapter.chapter.id })
                    .distinct()
            if (loserIds.isNotEmpty()) {
                suppressedIds.addAll(loserIds)
                allDecisions.add(
                    FilterDecision(
                        logicalKey = key,
                        kind = pick.winner.kind,
                        winnerChapterId = pick.winner.chapter.chapter.id,
                        suppressedChapterIds = loserIds,
                        reason = SuppressReason.DuplicateWhole,
                        scoreBreakdown = pick.scoreBreakdown,
                    )
                )
            }
        }

        val visibleItems = finalVisible.map { it.chapter }
        val metadata =
            ChapterReadEquivalence.buildMetadata(
                allChapters = input.chapters,
                classifiedById = classifiedById,
                userReadChapterIds = input.userReadChapterIds,
            )

        val visibleIdSet = visibleItems.map { it.chapter.id }.toSet()
        val context =
            SmartChapterContext(
                visibleChapters = visibleItems,
                metadataByChapterId = metadata,
                decisions = allDecisions,
                winnerChapterIds = winnerIds,
                suppressedChapterIds = suppressedIds,
                visibleIdSet = visibleIdSet,
            )

        if (mangaId != null) {
            SmartChapterProcessCache.put(mangaId, cacheKey, context)
            SmartChapterLogger.clearDedupState(mangaId)
        }

        SmartChapterLogger.log(
            SmartChapterLogger.Params(
                manga = input.manga,
                inputChapters = input.chapters,
                classified = classified,
                context = context,
                verboseLogging = input.verboseLogging,
                dedupGroups = contestedDedupGroups,
                droppedNoRankableGroups = droppedNoRankable,
            )
        )
        return context
    }

    companion object {

        /** Clears cached results (e.g. unit tests). */
        fun clearCache(mangaId: Long? = null) {
            SmartChapterProcessCache.clear(mangaId)
            SmartChapterLogger.clearDedupState(mangaId)
        }

        fun filteredScanlatorsFor(
            manga: eu.kanade.tachiyomi.data.database.models.Manga
        ): Set<String> {
            return ChapterUtil.getScanlators(manga.filtered_scanlators).toSet()
        }

        fun rankingFilterFor(
            manga: eu.kanade.tachiyomi.data.database.models.Manga,
            mangaDexPreferences: org.nekomanga.domain.site.MangaDexPreferences,
            libraryPreferences: org.nekomanga.domain.library.LibraryPreferences,
        ): SmartChapterEligibility.RankingFilter {
            return SmartChapterEligibility.RankingFilter(
                filteredScanlators = filteredScanlatorsFor(manga),
                blockedGroups = mangaDexPreferences.blockedGroups().get(),
                blockedUploaders = mangaDexPreferences.blockedUploaders().get(),
                scanlatorMatchAll = libraryPreferences.chapterScanlatorFilterOption().get() == 0,
            )
        }
    }
}
