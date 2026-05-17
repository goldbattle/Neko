package eu.kanade.tachiyomi.util.chapter.smartfilter

import org.nekomanga.domain.chapter.ChapterItem

enum class ChapterKind {
    Whole,
    Split,
    Extra,
    Unknown,
}

/** Stable key for grouping / equivalence within a manga. */
data class LogicalChapterKey(
    val volume: String?,
    val chapterNumber: Float?,
    val baseChapter: Int?,
    val isExtra: Boolean,
) {
    companion object {
        const val NO_VOLUME = ""
    }
}

data class UserReadVia(val chapterId: Long, val scanlatorLabel: String)

data class ChapterReadState(
    val userRead: Boolean,
    val autoRead: Boolean,
    val userReadVia: UserReadVia? = null,
) {
    val effectivelyRead: Boolean
        get() = userRead || autoRead
}

data class ChapterDisplayMetadata(val readState: ChapterReadState, val kind: ChapterKind)

enum class SuppressReason {
    DuplicateWhole,
    SplitWhenWholeExists,
    NotWinner,
}

data class FilterDecision(
    val logicalKey: LogicalChapterKey,
    val kind: ChapterKind,
    val winnerChapterId: Long?,
    val suppressedChapterIds: List<Long>,
    val reason: SuppressReason?,
    val scoreBreakdown: String? = null,
)

data class ClassifiedChapter(
    val chapter: ChapterItem,
    val kind: ChapterKind,
    val key: LogicalChapterKey,
)
