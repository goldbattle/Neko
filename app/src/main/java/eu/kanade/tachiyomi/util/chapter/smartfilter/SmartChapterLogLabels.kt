package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import org.nekomanga.domain.chapter.ChapterItem

/** Human-readable labels for logcat and fixture export. */
internal object SmartChapterLogLabels {

    fun label(chapter: ChapterItem): String {
        val c = chapter.chapter
        val vol = c.volume.takeIf { it.isNotBlank() }?.let { v -> "vol=$v " } ?: ""
        val num = if (c.isRecognizedNumber) "ch=${c.chapterNumber}" else "ch=?"
        val scan = ChapterUtil.getScanlators(c.scanlator).joinToString("+").ifBlank { "?" }
        val flags = buildList {
            if (c.isUnavailable) add("unavail")
            if (isLocked(chapter)) add("locked")
            if (c.read) add("read")
        }
        val flagSuffix = if (flags.isEmpty()) "" else " [${flags.joinToString(",")}]"
        return "id=${c.id} $vol$num scan=$scan$flagSuffix"
    }

    fun label(classified: ClassifiedChapter): String =
        "${label(classified.chapter)} kind=${classified.kind} key=${classified.key}"

    fun isLocked(chapter: ChapterItem): Boolean = SmartChapterEligibility.isLocked(chapter)

    /** Kotlin snippet for [SmartChapterEngineTest] from real manga data. */
    fun formatFixtureSnippet(
        mangaId: Long,
        mangaTitle: String,
        chapters: List<ChapterItem>,
        context: SmartChapterContext,
    ): String {
        if (chapters.isEmpty()) return ""

        val involvedIds = buildSet {
            addAll(context.winnerChapterIds)
            addAll(context.suppressedChapterIds)
        }
        val fixtureChapters =
            chapters
                .filter { it.chapter.id in involvedIds }
                .sortedWith(
                    compareBy<ChapterItem> { it.chapter.chapterNumber }.thenBy { it.chapter.id }
                )

        val visible = context.visibleChapters.size
        val suppressed = context.suppressedChapterIds.size
        val input = visible + suppressed

        val sb = StringBuilder()
        sb.appendLine("// SmartChapter fixture mangaId=$mangaId")
        sb.appendLine("// $mangaTitle")
        sb.appendLine("// input=$input visible=$visible suppressed=$suppressed")
        sb.appendLine("private fun fixtureChapters() = listOf(")
        for (item in fixtureChapters) {
            sb.appendLine("    ${chapterConstructor(item)},")
        }
        sb.appendLine(")")
        return sb.toString()
    }

    private fun chapterConstructor(item: ChapterItem): String {
        val c = item.chapter
        val volumeArg =
            c.volume.takeIf { it.isNotBlank() }?.let { ", volume = \"${escape(it)}\"" } ?: ""
        val readArg = if (c.read) ", read = true" else ""
        val unavailArg = if (c.isUnavailable) ", isUnavailable = true" else ""
        val scanlator = escape(c.scanlator)
        return "chapter(id = ${c.id}L, number = ${c.chapterNumber}f, scanlator = \"$scanlator\"$volumeArg$readArg$unavailArg)"
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
