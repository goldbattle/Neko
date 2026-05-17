package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_A
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_B
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.LOCKED_OFFICIAL
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.MERGE
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.MERGE_AGGREGATOR
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Triple fan + aggregator + merge on every ch 8–19 (see
 * [SmartChapterEngineDenseLibraryFixtureTest]).
 */
class SmartChapterEngineUserLogFixtureTest {

    private val engine = SmartChapterEngine()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val fixture = SmartChapterEngineExampleDbFixtureTest()
    private val manga =
        MangaImpl().apply {
            id = 58L
            title = "Triple-source fixture"
        }

    @Before
    fun setup() {
        SmartChapterEngine.clearCache()
        every { downloadManager.isChapterDownloaded(any(), any()) } returns false
        every { downloadManager.isChapterDownloaded(any(), any(), any()) } returns false
    }

    @Test
    fun `fan aggregator merge mix shows whole chapters 8 through 19`() {
        val chapters = buildList {
            for (n in 8..19) {
                add(fixture.chapter(n * 10L + 1, n.toFloat(), FAN_A, "Ch.$n"))
                add(fixture.chapter(n * 10L + 2, n.toFloat(), MERGE_AGGREGATOR, "Ch.$n"))
                add(fixture.chapter(n * 10L + 3, n.toFloat(), MERGE, "Ch.$n"))
                if (n in listOf(14, 20)) {
                    add(
                        fixture.chapter(
                            n * 10L + 4,
                            n.toFloat(),
                            LOCKED_OFFICIAL,
                            "Ch.$n",
                            isUnavailable = true,
                        )
                    )
                }
                if (n in listOf(14, 15, 16, 17, 18, 19, 20)) {
                    add(fixture.chapter(n * 10L + 5, n.toFloat(), FAN_B, "Ch.$n"))
                }
            }
            add(fixture.chapter(75L, 7.5f, MERGE, "Ch.7.5"))
            add(fixture.chapter(135L, 13.5f, MERGE_AGGREGATOR, "Ch.13.5"))
            add(fixture.chapter(195L, 19.5f, MERGE_AGGREGATOR, "Ch.19.5"))
        }

        val result =
            engine.process(
                SmartChapterInput(
                    manga = manga,
                    chapters = chapters,
                    downloadManager = downloadManager,
                )
            )

        val visibleWholes =
            result.visibleChapters
                .map { it.chapter.chapterNumber }
                .filter { kotlin.math.abs(it - it.toInt()) < 0.01f && it in 8f..19f }
                .map { it.toInt() }
                .toSet()
        assertTrue("Missing: ${(8..19).filter { it !in visibleWholes }}", visibleWholes.size == 12)
        assertTrue(7.5f in result.visibleChapters.map { it.chapter.chapterNumber })
    }
}
