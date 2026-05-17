package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_A
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_B
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.LOCKED_OFFICIAL
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.MERGE
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.NO_UPLOADER
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.SCAN_A
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.SCAN_B
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.SCAN_C
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.SCAN_D
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.SCAN_E
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.SPLIT_GROUP
import io.mockk.every
import io.mockk.mockk
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nekomanga.domain.chapter.ChapterItem

/**
 * Dense multi-scanlator library (126 chapters): one merge source across most numbers, [FAN_A] only
 * on lower chapters, [FAN_B] on mid/high, extras/splits mixed in. Mirrors a real merged-manga shape
 * without tying assertions to production group names.
 */
class SmartChapterEngineDenseLibraryFixtureTest {

    private val engine = SmartChapterEngine()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val fixture = SmartChapterEngineExampleDbFixtureTest()
    private val manga =
        MangaImpl().apply {
            id = 58L
            title = "Dense library fixture"
        }

    @Before
    fun setup() {
        SmartChapterEngine.clearCache()
        every { downloadManager.isChapterDownloaded(any(), any()) } returns false
        every { downloadManager.isChapterDownloaded(any(), any(), any()) } returns false
    }

    @Test
    fun `dense library matches expected smart filter summary counts`() {
        val chapters = denseLibraryChapters()
        assertEquals(126, chapters.size)

        val result = process(chapters)
        val removed = chapters.size - result.visibleChapters.size

        assertTrue(
            "visible=${result.visibleChapters.size} removed=$removed",
            result.visibleChapters.size in 56..58,
        )
        assertEquals(126, result.visibleChapters.size + removed)
    }

    @Test
    fun `dense library keeps whole chapters 8 through 19 visible`() {
        val result = process(denseLibraryChapters())

        val visibleWholes =
            result.visibleChapters
                .map { it.chapter.chapterNumber }
                .filter { isWhole(it) && it.toInt() in 8..19 }
                .map { it.toInt() }
                .toSet()

        val missing = (8..19).filter { it !in visibleWholes }
        assertTrue("Missing wholes: $missing", missing.isEmpty())
    }

    @Test
    fun `fan group a only competes through chapter 13`() {
        val chapters = denseLibraryChapters()
        val fanANumbers =
            chapters
                .filter { it.chapter.scanlator == FAN_A }
                .map { it.chapter.chapterNumber }
                .filter { isWhole(it) }
                .map { it.toInt() }
                .toSet()

        assertEquals(setOf(2, 3, 4, 7, 8, 9, 10, 11, 12, 13), fanANumbers)
        assertFalse(14 in fanANumbers)
    }

    @Test
    fun `merge wins over fan a on chapter 8 with locked official excluded`() {
        val chapters =
            listOf(
                fixture.chapter(801L, 8f, LOCKED_OFFICIAL, "Ch.8"),
                fixture.chapter(802L, 8f, FAN_A, "Ch.8"),
                fixture.chapter(803L, 8f, MERGE, "Ch.8"),
            )

        val result = process(chapters)
        val winner = result.visibleChapters.single()

        assertEquals(803L, winner.chapter.id)
        assertEquals(MERGE, winner.chapter.scanlator)
        assertTrue(result.suppressedChapterIds.contains(801L))
        assertTrue(result.suppressedChapterIds.contains(802L))
    }

    @Test
    fun `chapter 14 dedup group has fan b and merge only not fan a`() {
        val inputs =
            denseLibraryChapters().filter {
                isWhole(it.chapter.chapterNumber) && it.chapter.chapterNumber.toInt() == 14
            }
        assertEquals(
            setOf(LOCKED_OFFICIAL, FAN_B, MERGE),
            inputs.map { it.chapter.scanlator }.toSet(),
        )
        assertFalse(FAN_A in inputs.map { it.chapter.scanlator })

        val result = process(denseLibraryChapters())
        val ch14 =
            result.visibleChapters.single {
                isWhole(it.chapter.chapterNumber) && it.chapter.chapterNumber.toInt() == 14
            }
        assertEquals(MERGE, ch14.chapter.scanlator)
    }

    @Test
    fun `chapters 15 through 19 show merge winner over fan b each`() {
        val result = process(denseLibraryChapters())

        for (n in 15..19) {
            val visible =
                result.visibleChapters.single {
                    isWhole(it.chapter.chapterNumber) && it.chapter.chapterNumber.toInt() == n
                }
            assertEquals(MERGE, visible.chapter.scanlator)
        }
    }

    @Test
    fun `dot5 extras visible and odin splits hidden when whole exists`() {
        val result = process(denseLibraryChapters())
        val visibleNumbers = result.visibleChapters.map { it.chapter.chapterNumber }.toSet()

        assertTrue(7.5f in visibleNumbers)
        assertTrue(47.5f in visibleNumbers)
        assertFalse(20.1f in visibleNumbers)
        assertFalse(20.2f in visibleNumbers)
        assertTrue(20f in visibleNumbers)
    }

    @Test
    fun `locked official on chapter 20 does not drop whole when merge exists`() {
        val result = process(denseLibraryChapters())
        val ch20 =
            result.visibleChapters.single {
                isWhole(it.chapter.chapterNumber) && it.chapter.chapterNumber.toInt() == 20
            }
        assertEquals(MERGE, ch20.chapter.scanlator)
    }

    private fun process(chapters: List<ChapterItem>) =
        engine.process(
            SmartChapterInput(manga = manga, chapters = chapters, downloadManager = downloadManager)
        )

    private fun isWhole(number: Float): Boolean {
        val fractional = number - kotlin.math.floor(number)
        return fractional < 0.01f || fractional > 0.99f
    }

    private fun denseLibraryChapters(): List<ChapterItem> {
        val rows = denseLibraryRows()
        assertEquals(126, rows.size)

        return rows.mapIndexed { index, (number, scanlator) ->
            fixture.chapter(
                id = 58000L + index,
                number = number,
                scanlator = scanlator,
                chapterText = "Ch.${formatChapterText(number)}",
                isUnavailable = scanlator == LOCKED_OFFICIAL,
            )
        }
    }

    private fun denseLibraryRows(): List<Pair<Float, String>> =
        listOf(
            49f to SCAN_D,
            49f to MERGE,
            48f to SCAN_D,
            48f to MERGE,
            47.5f to SCAN_A,
            47.5f to MERGE,
            47f to SCAN_A,
            47f to MERGE,
            46f to SCAN_A,
            46f to MERGE,
            45f to SCAN_A,
            45f to MERGE,
            44f to SCAN_A,
            44f to MERGE,
            43.5f to SCAN_B,
            43f to SCAN_B,
            43f to MERGE,
            42f to SCAN_B,
            42f to MERGE,
            41f to SCAN_B,
            41f to MERGE,
            40f to SCAN_B,
            40f to MERGE,
            39.5f to SCAN_B,
            39f to SCAN_B,
            39f to MERGE,
            38f to SCAN_B,
            38f to MERGE,
            37f to SCAN_B,
            37f to MERGE,
            36f to SCAN_B,
            36f to MERGE,
            35f to SCAN_B,
            35f to MERGE,
            34.5f to SCAN_B,
            34f to SCAN_B,
            34f to MERGE,
            33f to SCAN_B,
            33f to MERGE,
            32f to SCAN_B,
            32f to MERGE,
            31f to SCAN_B,
            31f to MERGE,
            30f to SCAN_B,
            30f to MERGE,
            29.5f to SCAN_A,
            29.5f to SCAN_C,
            29f to SCAN_A,
            29f to SCAN_C,
            29f to MERGE,
            28f to SCAN_C,
            28f to SCAN_A,
            28f to MERGE,
            27f to SCAN_C,
            27f to SCAN_A,
            27f to MERGE,
            26f to SCAN_A,
            26f to FAN_B,
            26.7f to SPLIT_GROUP,
            25f to FAN_B,
            25f to SCAN_A,
            25f to MERGE,
            24.6f to FAN_B,
            24.5f to FAN_B,
            24f to FAN_B,
            23f to FAN_B,
            22f to FAN_B,
            21f to FAN_B,
            20.2f to FAN_B,
            20.1f to FAN_B,
            20f to LOCKED_OFFICIAL,
            19.6f to SPLIT_GROUP,
            19f to FAN_B,
            18f to FAN_B,
            17f to FAN_B,
            16f to FAN_B,
            15f to FAN_B,
            14f to LOCKED_OFFICIAL,
            14f to FAN_B,
            13.6f to SPLIT_GROUP,
            13f to FAN_A,
            12f to FAN_A,
            11f to FAN_A,
            10f to FAN_A,
            9f to FAN_A,
            8f to LOCKED_OFFICIAL,
            8f to FAN_A,
            50f to SCAN_D,
            50f to MERGE,
            7.6f to SPLIT_GROUP,
            7.5f to MERGE,
            7f to FAN_A,
            6f to FAN_B,
            6f to MERGE,
            5f to FAN_B,
            5f to MERGE,
            4f to FAN_A,
            4f to MERGE,
            3f to FAN_A,
            3f to MERGE,
            2f to FAN_A,
            2f to NO_UPLOADER,
            2f to MERGE,
            1f to LOCKED_OFFICIAL,
            1f to NO_UPLOADER,
            1f to SCAN_E,
            1f to MERGE,
            7f to MERGE,
            13f to MERGE,
            12f to MERGE,
            11f to MERGE,
            10f to MERGE,
            9f to MERGE,
            8f to MERGE,
            20f to MERGE,
            19f to MERGE,
            18f to MERGE,
            17f to MERGE,
            16f to MERGE,
            15f to MERGE,
            14f to MERGE,
            23f to MERGE,
            22f to MERGE,
            21f to MERGE,
            24f to MERGE,
            26f to MERGE,
        )

    private fun formatChapterText(number: Float): String {
        val whole = number.toInt()
        if (abs(number - whole) < 0.01f) return whole.toString()
        return number.toString().trimEnd('0').trimEnd('.')
    }
}
