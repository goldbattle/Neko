package eu.kanade.tachiyomi.util.chapter.smartfilter

import org.junit.Assert.assertTrue
import org.junit.Test
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.SimpleChapter

class SmartChapterLogLabelsTest {

    @Test
    fun `fixture snippet includes involved chapters only`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 6f, scanlator = "A"),
                chapter(id = 2L, number = 6f, scanlator = "B"),
                chapter(id = 3L, number = 7f, scanlator = "C"),
            )
        val context =
            SmartChapterContext(
                visibleChapters = listOf(chapters[0]),
                metadataByChapterId = emptyMap(),
                decisions =
                    listOf(
                        FilterDecision(
                            logicalKey =
                                LogicalChapterKey(
                                    volume = null,
                                    chapterNumber = 6f,
                                    baseChapter = 6,
                                    isExtra = false,
                                ),
                            kind = ChapterKind.Whole,
                            winnerChapterId = 1L,
                            suppressedChapterIds = listOf(2L),
                            reason = SuppressReason.DuplicateWhole,
                            scoreBreakdown = "preferredSource+100",
                        )
                    ),
                winnerChapterIds = setOf(1L),
                suppressedChapterIds = setOf(2L),
                visibleIdSet = setOf(1L),
            )

        val snippet =
            SmartChapterLogLabels.formatFixtureSnippet(
                mangaId = 99L,
                mangaTitle = "Test Manga",
                chapters = chapters,
                context = context,
            )

        assertTrue(snippet.contains("id = 1L"))
        assertTrue(snippet.contains("id = 2L"))
        assertTrue(!snippet.contains("id = 3L"))
        assertTrue(snippet.contains("input=2 visible=1 suppressed=1"))
    }

    private fun chapter(id: Long, number: Float, scanlator: String): ChapterItem {
        return ChapterItem(
            chapter =
                SimpleChapter(
                    id = id,
                    mangaId = 1L,
                    read = false,
                    bookmark = false,
                    lastPageRead = 0,
                    dateFetch = 0L,
                    sourceOrder = 0,
                    smartOrder = 0,
                    url = "url/$id",
                    name = "Chapter $number",
                    dateUpload = 0L,
                    chapterNumber = number,
                    pagesLeft = 0,
                    volume = "",
                    chapterText = "Ch. $number",
                    chapterTitle = "",
                    language = "",
                    mangaDexChapterId = "",
                    oldMangaDexChapterId = null,
                    scanlator = scanlator,
                    uploader = "",
                    isUnavailable = false,
                )
        )
    }
}
