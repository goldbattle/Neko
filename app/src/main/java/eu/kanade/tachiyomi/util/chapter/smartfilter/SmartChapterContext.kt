package eu.kanade.tachiyomi.util.chapter.smartfilter

import org.nekomanga.domain.chapter.ChapterItem

data class SmartChapterContext(
    val visibleChapters: List<ChapterItem>,
    val metadataByChapterId: Map<Long, ChapterDisplayMetadata>,
    val decisions: List<FilterDecision>,
    val winnerChapterIds: Set<Long>,
    val suppressedChapterIds: Set<Long>,
    private val visibleIdSet: Set<Long>,
) {
    fun isEffectivelyRead(chapterId: Long): Boolean {
        return metadataByChapterId[chapterId]?.readState?.effectivelyRead == true
    }

    fun metadata(chapterId: Long): ChapterDisplayMetadata? = metadataByChapterId[chapterId]

    fun isVisible(chapterId: Long): Boolean = chapterId in visibleIdSet

    fun shouldShowInUpdates(chapterItem: ChapterItem): Boolean {
        val id = chapterItem.chapter.id
        if (!isVisible(id)) return false
        if (isEffectivelyRead(id)) return false
        return true
    }

    fun countUnread(): Int {
        return visibleChapters.count { chapter ->
            !isEffectivelyRead(chapter.chapter.id) && !chapter.chapter.isUnavailable
        }
    }

    companion object {
        fun passthrough(
            chapters: List<ChapterItem>,
            metadata: Map<Long, ChapterDisplayMetadata> = emptyMap(),
        ): SmartChapterContext {
            val ids = chapters.map { it.chapter.id }.toSet()
            return SmartChapterContext(
                visibleChapters = chapters,
                metadataByChapterId = metadata,
                decisions = emptyList(),
                winnerChapterIds = ids,
                suppressedChapterIds = emptySet(),
                visibleIdSet = ids,
            )
        }
    }
}
