package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import kotlin.math.floor

/**
 * Stable keys for cross-source chapter read state. Survives removal of merge sources and chapter
 * row replacement during sync.
 */
object ChapterReadMarkerKeys {

    private const val PREFIX_WHOLE = "w:"
    private const val PREFIX_NUMBERED = "n:"
    private const val PREFIX_EXTRA = "e:"

    fun fromChapter(chapter: Chapter): String {
        if (!chapter.isRecognizedNumber) {
            return "${PREFIX_NUMBERED}${chapter.url}"
        }
        val num = chapter.chapter_number
        val fractional = num - floor(num)
        return when {
            fractional == 0f -> "${PREFIX_WHOLE}${floor(num).toInt()}"
            fractional == 0.5f || fractional >= 0.97f -> {
                val vol = chapter.vol?.takeIf { it.isNotBlank() } ?: ""
                "${PREFIX_EXTRA}$vol:$num"
            }
            else -> "${PREFIX_EXTRA}${chapter.vol.orEmpty()}:$num"
        }
    }

    fun matches(chapter: Chapter, markerKey: String): Boolean = fromChapter(chapter) == markerKey

    fun matches(chapter: Chapter, markerKeys: Set<String>): Boolean = markerKeys.any {
        matches(chapter, it)
    }
}
