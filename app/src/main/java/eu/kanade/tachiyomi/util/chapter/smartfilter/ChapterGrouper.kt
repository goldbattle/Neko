package eu.kanade.tachiyomi.util.chapter.smartfilter

internal object ChapterGrouper {

    data class GroupResult(
        val visible: List<ClassifiedChapter>,
        val suppressed: List<ClassifiedChapter>,
        val decisions: MutableList<FilterDecision>,
    )

    fun groupAndFilter(classified: List<ClassifiedChapter>): GroupResult {
        val decisions = mutableListOf<FilterDecision>()
        val byFamily = classified.groupBy { ChapterClassifier.familyKey(it.key) }

        val visible = mutableListOf<ClassifiedChapter>()
        val suppressed = mutableListOf<ClassifiedChapter>()

        for ((familyKey, members) in byFamily) {
            val wholes = members.filter { it.kind == ChapterKind.Whole }
            val splits = members.filter { it.kind == ChapterKind.Split }
            val extras = members.filter { it.kind == ChapterKind.Extra }
            val unknown = members.filter { it.kind == ChapterKind.Unknown }

            if (wholes.isNotEmpty() && splits.isNotEmpty()) {
                decisions.add(
                    FilterDecision(
                        logicalKey = familyKey,
                        kind = ChapterKind.Split,
                        winnerChapterId = null,
                        suppressedChapterIds = splits.map { it.chapter.chapter.id },
                        reason = SuppressReason.SplitWhenWholeExists,
                    )
                )
                suppressed.addAll(splits)
                visible.addAll(wholes)
                visible.addAll(extras)
                visible.addAll(unknown)
            } else {
                visible.addAll(members)
            }
        }

        return GroupResult(visible, suppressed, decisions)
    }

    /** Group visible chapters for duplicate whole selection (volume-insensitive). */
    fun groupForDeduplication(
        visible: List<ClassifiedChapter>
    ): Map<LogicalChapterKey, List<ClassifiedChapter>> {
        return visible.groupBy { ChapterClassifier.deduplicationKey(it) }
    }
}
