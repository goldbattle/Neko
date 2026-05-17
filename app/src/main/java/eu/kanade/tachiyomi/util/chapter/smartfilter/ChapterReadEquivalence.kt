package eu.kanade.tachiyomi.util.chapter.smartfilter

import org.nekomanga.domain.chapter.ChapterItem

internal object ChapterReadEquivalence {

    fun buildMetadata(
        allChapters: List<ChapterItem>,
        classifiedById: Map<Long, ClassifiedChapter>,
        /** DB-backed user read rows before marker overlay (for read-via tags). */
        userReadChapterIds: Set<Long> = emptySet(),
    ): Map<Long, ChapterDisplayMetadata> {
        val userReadByEquivalence = mutableMapOf<EquivalenceKey, ChapterItem>()
        val useExplicitUserRead = userReadChapterIds.isNotEmpty()

        for (chapter in allChapters) {
            val isUserRead =
                if (useExplicitUserRead) {
                    chapter.chapter.id in userReadChapterIds
                } else {
                    chapter.chapter.read
                }
            if (!isUserRead) continue
            val classified = classifiedById[chapter.chapter.id] ?: continue
            val key = equivalenceKey(classified)
            userReadByEquivalence.putIfAbsent(key, chapter)
        }

        return allChapters.associate { chapter ->
            val classified =
                classifiedById[chapter.chapter.id]
                    ?: ClassifiedChapter(
                        chapter,
                        ChapterClassifier.classifyKind(chapter),
                        ChapterClassifier.logicalKey(
                            chapter,
                            ChapterClassifier.classifyKind(chapter),
                        ),
                    )
            val eqKey = equivalenceKey(classified)
            val userRead =
                if (useExplicitUserRead) {
                    chapter.chapter.id in userReadChapterIds
                } else {
                    chapter.chapter.read
                }
            val via = userReadByEquivalence[eqKey]
            val autoRead = !userRead && via != null && via.chapter.id != chapter.chapter.id
            val userReadVia =
                if (autoRead) {
                    UserReadVia(
                        chapterId = via.chapter.id,
                        scanlatorLabel = ChapterWinnerSelector.formatScanlatorLabel(via),
                    )
                } else {
                    null
                }
            chapter.chapter.id to
                ChapterDisplayMetadata(
                    readState =
                        ChapterReadState(
                            userRead = userRead,
                            autoRead = autoRead,
                            userReadVia = userReadVia,
                        ),
                    kind = classified.kind,
                )
        }
    }

    private data class EquivalenceKey(
        val volume: String?,
        val chapterNumber: Float?,
        val isExtra: Boolean,
    )

    private fun equivalenceKey(classified: ClassifiedChapter): EquivalenceKey {
        val key = classified.key
        return when (classified.kind) {
            ChapterKind.Split,
            ChapterKind.Extra -> EquivalenceKey(key.volume, key.chapterNumber, key.isExtra)
            ChapterKind.Whole,
            ChapterKind.Unknown ->
                EquivalenceKey(
                    volume = null,
                    chapterNumber = key.baseChapter?.toFloat() ?: key.chapterNumber ?: -1f,
                    isExtra = key.isExtra,
                )
        }
    }

    fun sharesEquivalence(a: ClassifiedChapter, b: ClassifiedChapter): Boolean {
        return equivalenceKey(a) == equivalenceKey(b)
    }
}
