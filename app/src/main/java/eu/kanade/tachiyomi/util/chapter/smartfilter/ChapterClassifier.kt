package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.util.chapter.getChapterNum
import kotlin.math.abs
import kotlin.math.floor
import org.nekomanga.domain.chapter.ChapterItem

internal object ChapterClassifier {

    private const val WHOLE_EPSILON = 0.01f

    fun classify(chapter: ChapterItem): ClassifiedChapter {
        val kind = classifyKind(chapter)
        val key = logicalKey(chapter, kind)
        return ClassifiedChapter(chapter, kind, key)
    }

    /** DB chapter_number, or number parsed from chapter text (e.g. "Ch. 8"). */
    fun recognizedNumber(chapter: ChapterItem): Float? {
        if (chapter.chapter.isRecognizedNumber) return chapter.chapter.chapterNumber
        return getChapterNum(chapter)?.takeIf { it >= 0f }
    }

    fun classifyKind(chapter: ChapterItem): ChapterKind {
        val num = recognizedNumber(chapter) ?: return classifyFromTitle(chapter)
        val fractional = num - floor(num)
        // Tolerate float drift (e.g. 7.999999 stored as chapter 8).
        if (fractional < WHOLE_EPSILON || fractional > 1f - WHOLE_EPSILON) return ChapterKind.Whole
        if (abs(fractional - 0.5f) < WHOLE_EPSILON) return ChapterKind.Extra
        if (fractional >= 0.97f) return ChapterKind.Extra
        if (fractional > WHOLE_EPSILON) return ChapterKind.Split
        return ChapterKind.Unknown
    }

    private fun classifyFromTitle(chapter: ChapterItem): ChapterKind {
        val name = chapter.chapter.name.lowercase()
        return when {
            name.contains("extra") ||
                name.contains("omake") ||
                name.contains("special") ||
                name.contains("bonus") -> ChapterKind.Extra
            else -> ChapterKind.Unknown
        }
    }

    fun logicalKey(chapter: ChapterItem, kind: ChapterKind): LogicalChapterKey {
        val vol = chapter.chapter.volume.takeIf { it.isNotBlank() }
        val num = recognizedNumber(chapter)
        return when (kind) {
            ChapterKind.Extra ->
                LogicalChapterKey(
                    volume = vol,
                    chapterNumber = num,
                    baseChapter = null,
                    isExtra = true,
                )
            else -> {
                val base = num?.let { floor(it).toInt() }
                LogicalChapterKey(
                    volume = vol,
                    chapterNumber = num,
                    baseChapter = base,
                    isExtra = false,
                )
            }
        }
    }

    /** Keys that share the same whole-chapter family (for split suppression). */
    fun familyKey(key: LogicalChapterKey): LogicalChapterKey {
        if (key.isExtra) return key
        // Volume-insensitive: merged hosts tag splits with vol "1" while wholes may have none.
        return key.copy(volume = null, chapterNumber = null, isExtra = false)
    }

    /**
     * Key for deduplicating the same chapter across scanlators/volumes (e.g. "Vol.1 Ch.2" vs
     * "Ch.2"). Extras keep volume + fractional number so distinct bonus chapters stay separate.
     */
    fun deduplicationKey(classified: ClassifiedChapter): LogicalChapterKey {
        return when (classified.kind) {
            ChapterKind.Extra -> classified.key
            ChapterKind.Unknown ->
                // Do not collapse every unnumbered chapter into one dedup bucket (key=-1).
                LogicalChapterKey(
                    volume = classified.key.volume,
                    chapterNumber = classified.chapter.chapter.id.toFloat(),
                    baseChapter = null,
                    isExtra = false,
                )
            else ->
                LogicalChapterKey(
                    volume = null,
                    chapterNumber = classified.key.baseChapter?.toFloat(),
                    baseChapter = classified.key.baseChapter,
                    isExtra = classified.key.isExtra,
                )
        }
    }
}
