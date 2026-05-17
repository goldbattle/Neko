package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.ALLOWED
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.BLOCKED
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_A
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_B
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.LOCKED_OFFICIAL
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.MERGE
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.MERGE_AGGREGATOR
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nekomanga.constants.MdConstants
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.SimpleChapter

class SmartChapterEngineTest {

    private val engine = SmartChapterEngine()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val manga =
        MangaImpl().apply {
            id = 1L
            title = "Test"
        }

    @Before
    fun clearCache() {
        SmartChapterEngine.clearCache()
        // Relaxed mock defaults booleans to true; would mark every chapter downloaded (+10_000).
        every { downloadManager.isChapterDownloaded(any(), any()) } returns false
        every { downloadManager.isChapterDownloaded(any(), any(), any()) } returns false
    }

    @Test
    fun `caches result when chapter input unchanged`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 6f, scanlator = "MangaDex"),
                chapter(id = 2L, number = 6f, scanlator = "Other Group"),
            )

        val first = process(chapters)
        val second = process(chapters)

        assertTrue(first === second)
    }

    @Test
    fun `duplicate whole chapters keep one winner`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 6f, scanlator = "MangaDex", read = false),
                chapter(id = 2L, number = 6f, scanlator = "Other Group", read = false),
            )

        val result = process(chapters)

        assertEquals(1, result.visibleChapters.size)
        assertTrue(result.visibleChapters.first().chapter.id in listOf(1L, 2L))
        assertEquals(1, result.suppressedChapterIds.size)
    }

    @Test
    fun `split chapters hidden when whole exists`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 6f, scanlator = "A"),
                chapter(id = 2L, number = 6.1f, scanlator = "A"),
                chapter(id = 3L, number = 6.2f, scanlator = "A"),
            )

        val result = process(chapters)

        assertEquals(1, result.visibleChapters.size)
        assertEquals(6f, result.visibleChapters.first().chapter.chapterNumber)
        assertTrue(result.suppressedChapterIds.contains(2L))
        assertTrue(result.suppressedChapterIds.contains(3L))
    }

    @Test
    fun `auto read when equivalent chapter user read`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 3f, scanlator = "Group A", read = true),
                chapter(id = 2L, number = 3f, scanlator = "Group B", read = false),
            )

        val result = process(chapters, userReadChapterIds = setOf(1L))

        val metaB = result.metadataByChapterId[2L]!!
        assertFalse(metaB.readState.userRead)
        assertTrue(metaB.readState.autoRead)
        assertEquals("Group A", metaB.readState.userReadVia?.scanlatorLabel)
        assertTrue(result.isEffectivelyRead(2L))
    }

    /**
     * User read ch7 on group A with smart filter off, then enabled smart filter: winner row may get
     * read=true from marker overlay but should still show "Read via" for the original group.
     */
    @Test
    fun `read via tag when winner only marked read in memory`() {
        val chapters =
            listOf(
                chapter(id = 100L, number = 7f, scanlator = "Group A", read = true),
                chapter(id = 200L, number = 7f, scanlator = FAN_A, read = false),
            )
        val withMarkerOverlay = chapters.map { item ->
            if (item.chapter.id == 200L) {
                item.copy(chapter = item.chapter.copy(read = true))
            } else {
                item
            }
        }

        val result = process(withMarkerOverlay, userReadChapterIds = setOf(100L))

        val metaWinner = result.metadataByChapterId[200L]!!
        assertFalse(metaWinner.readState.userRead)
        assertTrue(metaWinner.readState.autoRead)
        assertEquals("Group A", metaWinner.readState.userReadVia?.scanlatorLabel)
    }

    @Test
    fun `merge source visible when sole rankable for chapter 8`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 8f, scanlator = LOCKED_OFFICIAL, isUnavailable = true),
                chapter(id = 1762L, number = 8f, scanlator = MERGE),
            )

        val result = process(chapters)

        assertEquals(1762L, result.visibleChapters.single().chapter.id)
    }

    @Test
    fun `near integer chapter number classified as whole`() {
        val chapters = listOf(chapter(id = 1L, number = 7.999999f, scanlator = "A"))

        val result = process(chapters)

        assertEquals(ChapterKind.Whole, result.metadataByChapterId[1L]!!.kind)
    }

    @Test
    fun `whole chapters with different volumes dedupe to one winner`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 2f, scanlator = "Group A", volume = "Vol.1"),
                chapter(id = 2L, number = 2f, scanlator = "Group B", volume = ""),
            )

        val result = process(chapters)

        assertEquals(1, result.visibleChapters.size)
        assertEquals(1, result.suppressedChapterIds.size)
    }

    /**
     * From logcat (example DB manga id=59): ch.29 had separate DuplicateWhole groups for volume=5,
     * Vol.5, and null before volume-insensitive dedup. After fix, one group wins id=31.
     */
    @Test
    fun `whole chapter volume variants 5 Vol5 and empty dedupe to one`() {
        val chapters =
            listOf(
                chapter(id = 31L, number = 29f, scanlator = "Winner Group", volume = "5"),
                chapter(id = 32L, number = 29f, scanlator = "Other A", volume = "5"),
                chapter(id = 150L, number = 29f, scanlator = "Other B", volume = "Vol.5"),
                chapter(id = 153L, number = 29f, scanlator = "Other C", volume = "Vol.5"),
                chapter(id = 151L, number = 29f, scanlator = "Other D", volume = ""),
                chapter(id = 152L, number = 29f, scanlator = "Other E", volume = ""),
                chapter(id = 309L, number = 29f, scanlator = "Other F", volume = ""),
            )

        val result = process(chapters)

        assertEquals(1, result.visibleChapters.size)
        assertEquals(31L, result.visibleChapters.single().chapter.id)
        assertEquals(setOf(32L, 150L, 153L, 151L, 152L, 309L), result.suppressedChapterIds)
    }

    /** Logcat: 29.5 extra stays separate from 29.0 whole; extras can still dedupe per volume. */
    @Test
    fun `extra 29_5 visible alongside whole 29`() {
        val chapters =
            listOf(
                chapter(id = 31L, number = 29f, scanlator = "A", volume = "5"),
                chapter(id = 29L, number = 29.5f, scanlator = "A", volume = "5"),
                chapter(id = 30L, number = 29.5f, scanlator = "B", volume = "5"),
            )

        val result = process(chapters)

        assertEquals(2, result.visibleChapters.size)
        assertTrue(result.suppressedChapterIds.contains(30L))
        assertTrue(result.visibleChapters.any { it.chapter.chapterNumber == 29.5f })
    }

    /** ChapterSourceSync pattern: Odin 20.1/20.2 splits beside fan whole 20 (J-Novel excluded). */
    /**
     * Merged manga often has a whole "Ch.N" from one host and split "Ch.N.1–N.k" from another;
     * volume metadata may differ (empty vs "1" / "Vol.1"). Split suppression must be
     * volume-insensitive like deduplication, or a fan/official split (e.g. Comikey Ch.1.3) can win
     * over the whole Ch.1.
     */
    @Test
    fun `split parts hidden when whole exists even with mismatched volume labels`() {
        val chapters =
            listOf(
                chapter(id = 304L, number = 1f, scanlator = MERGE_AGGREGATOR, volume = ""),
                chapter(id = 305L, number = 1f, scanlator = MERGE_AGGREGATOR, volume = ""),
                chapter(id = 293L, number = 1.3f, scanlator = "Comikey", volume = "1"),
                chapter(id = 294L, number = 1.3f, scanlator = MERGE_AGGREGATOR, volume = "1"),
                chapter(id = 295L, number = 1.3f, scanlator = MERGE_AGGREGATOR, volume = "1"),
                chapter(id = 296L, number = 1.2f, scanlator = "Comikey", volume = "1"),
                chapter(id = 297L, number = 1.2f, scanlator = MERGE_AGGREGATOR, volume = "1"),
                chapter(id = 300L, number = 1.1f, scanlator = "Comikey", volume = "1"),
                chapter(id = 301L, number = 1.1f, scanlator = MERGE_AGGREGATOR, volume = "1"),
            )

        val result = process(chapters)

        assertEquals(1, result.visibleChapters.size)
        assertEquals(1f, result.visibleChapters.single().chapter.chapterNumber)
        assertTrue(
            "Expected a whole Ch.1, got ${result.visibleChapters.single().chapter.chapterText}",
            result.visibleChapters.single().chapter.id in listOf(304L, 305L),
        )
        assertTrue(
            result.suppressedChapterIds.containsAll(
                listOf(293L, 294L, 295L, 296L, 297L, 300L, 301L)
            )
        )
    }

    @Test
    fun `split parts hidden when whole chapter exists`() {
        val chapters =
            listOf(
                chapter(id = 100L, number = 20f, scanlator = LOCKED_OFFICIAL, volume = "4"),
                chapter(id = 103L, number = 20f, scanlator = FAN_B, volume = "4"),
                chapter(id = 101L, number = 20.1f, scanlator = FAN_B, volume = "4"),
                chapter(id = 102L, number = 20.2f, scanlator = FAN_B, volume = "4"),
            )

        val result = process(chapters)

        assertEquals(1, result.visibleChapters.size)
        assertEquals(103L, result.visibleChapters.single().chapter.id)
        assertEquals(20f, result.visibleChapters.single().chapter.chapterNumber)
        assertTrue(result.suppressedChapterIds.containsAll(listOf(100L, 101L, 102L)))
    }

    @Test
    fun `unlocked chapter preferred over locked official`() {
        val lockedScanlator = MdConstants.UnsupportedOfficialGroupList.first()
        val chapters =
            listOf(
                chapter(id = 1L, number = 5f, scanlator = lockedScanlator, isUnavailable = true),
                chapter(id = 2L, number = 5f, scanlator = "Fan Group"),
            )

        val result = process(chapters)

        assertEquals(2L, result.visibleChapters.single().chapter.id)
        assertTrue(result.suppressedChapterIds.contains(1L))
    }

    @Test
    fun `locked only candidate omitted from visible list`() {
        val lockedScanlator = MdConstants.UnsupportedOfficialGroupList.first()
        val chapters =
            listOf(chapter(id = 1L, number = 5f, scanlator = lockedScanlator, isUnavailable = true))

        val result = process(chapters)

        assertTrue(result.visibleChapters.isEmpty())
    }

    @Test
    fun `chapter number parsed from text when db number missing`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = -1f, scanlator = "Fan Group", chapterText = "Ch. 8"),
                chapter(id = 2L, number = -1f, scanlator = "Other", chapterText = "Ch. 8"),
            )

        val result = process(chapters)

        assertEquals(1, result.visibleChapters.size)
        assertEquals(
            ChapterKind.Whole,
            result.metadataByChapterId[result.visibleChapters.single().chapter.id]!!.kind,
        )
        assertEquals(1, result.suppressedChapterIds.size)
    }

    /**
     * User report (example DB export): only 7.5 / 13.5 / 19.5 visible when locked wholes were
     * wrongly promoted as winners then removed by display filters. Locked wholes must stay hidden;
     * extras remain in separate families.
     */
    @Test
    fun `locked only wholes hidden extras still visible`() {
        val lockedScanlator = MdConstants.UnsupportedOfficialGroupList.first()
        val chapters = buildList {
            for (n in 8..20) {
                add(
                    chapter(
                        id = n.toLong(),
                        number = n.toFloat(),
                        scanlator = lockedScanlator,
                        isUnavailable = true,
                    )
                )
            }
            add(chapter(id = 75L, number = 7.5f, scanlator = MERGE))
            add(chapter(id = 135L, number = 13.5f, scanlator = MERGE))
            add(chapter(id = 195L, number = 19.5f, scanlator = MERGE))
        }

        val result = process(chapters)
        val visibleNumbers = result.visibleChapters.map { it.chapter.chapterNumber }.toSet()

        assertTrue((8..20).map { it.toFloat() }.none { it in visibleNumbers })
        assertTrue(7.5f in visibleNumbers)
        assertTrue(13.5f in visibleNumbers)
        assertTrue(19.5f in visibleNumbers)
    }

    @Test
    fun `dedup group omitted when every member excluded from ranking`() {
        val lockedScanlator = MdConstants.UnsupportedOfficialGroupList.first()
        val chapters =
            listOf(
                chapter(id = 1L, number = 8f, scanlator = lockedScanlator, isUnavailable = true),
                chapter(id = 2L, number = 8f, scanlator = lockedScanlator, isUnavailable = true),
            )

        val result = process(chapters)

        assertTrue(result.visibleChapters.isEmpty())
    }

    @Test
    fun `unnumbered chapters are not collapsed into one dedup group`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = -1f, scanlator = "A", name = "Prologue"),
                chapter(id = 2L, number = -1f, scanlator = "A", name = "Epilogue"),
            )

        val result = process(chapters)

        assertEquals(2, result.visibleChapters.size)
    }

    @Test
    fun `native mangadex preferred over merged aggregator when sole release`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 6f, scanlator = "MangaDex"),
                chapter(id = 2L, number = 6f, scanlator = MERGE_AGGREGATOR),
            )

        val result = process(chapters)

        assertEquals(1L, result.visibleChapters.single().chapter.id)
        assertTrue(result.suppressedChapterIds.contains(2L))
    }

    @Test
    fun `fan release wins over native mangadex when both exist`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 8f, scanlator = "MangaDex"),
                chapter(id = 2L, number = 8f, scanlator = MERGE),
                chapter(id = 3L, number = 8f, scanlator = FAN_A),
            )

        val result = process(chapters)

        val winner = result.visibleChapters.single()
        assertTrue(result.suppressedChapterIds.contains(1L))
        assertTrue(winner.chapter.id in listOf(2L, 3L))
        assertTrue(winner.chapter.scanlator != "MangaDex")
    }

    @Test
    fun `merge source wins over fan group for same chapter number`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 9f, scanlator = FAN_A),
                chapter(id = 2L, number = 9f, scanlator = MERGE),
            )

        val result = process(chapters)

        assertEquals(2L, result.visibleChapters.single().chapter.id)
    }

    @Test
    fun `downloaded chapter wins over merge source`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 9f, scanlator = FAN_A),
                chapter(id = 2L, number = 9f, scanlator = MERGE),
            )

        val result = process(chapters, downloadedChapterIds = setOf(1L))

        assertEquals(1L, result.visibleChapters.single().chapter.id)
        assertTrue(result.suppressedChapterIds.contains(2L))
    }

    @Test
    fun `fan group loses to merge source for same chapter`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 15f, scanlator = FAN_B),
                chapter(id = 2L, number = 15f, scanlator = MERGE),
            )

        val result = process(chapters)

        assertEquals(2L, result.visibleChapters.single().chapter.id)
    }

    @Test
    fun `fan whole visible when locked copies exist in dedup group`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 8f, scanlator = LOCKED_OFFICIAL, isUnavailable = true),
                chapter(id = 2L, number = 8f, scanlator = MERGE),
            )

        val result = process(chapters)

        assertEquals(2L, result.visibleChapters.single().chapter.id)
    }

    @Test
    fun `blocked group excluded from ranking`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 7f, scanlator = BLOCKED),
                chapter(id = 2L, number = 7f, scanlator = ALLOWED),
            )

        val result =
            process(
                chapters,
                rankingFilter =
                    SmartChapterEligibility.RankingFilter(
                        filteredScanlators = emptySet(),
                        blockedGroups = setOf(BLOCKED),
                    ),
            )

        assertEquals(2L, result.visibleChapters.single().chapter.id)
        assertTrue(result.suppressedChapterIds.contains(1L))
    }

    @Test
    fun `extra chapter kind classification`() {
        val chapters = listOf(chapter(id = 1L, number = 6.5f, scanlator = "A"))

        val result = process(chapters)

        assertEquals(ChapterKind.Extra, result.metadataByChapterId[1L]!!.kind)
    }

    @Test
    fun `countUnread ignores auto read duplicates`() {
        val chapters =
            listOf(
                chapter(id = 1L, number = 3f, scanlator = "A", read = true),
                chapter(id = 2L, number = 3f, scanlator = "B", read = false),
                chapter(id = 3L, number = 4f, scanlator = "A", read = false),
            )

        val result = process(chapters)

        assertEquals(1, result.countUnread())
    }

    private fun process(
        chapters: List<ChapterItem>,
        rankingFilter: SmartChapterEligibility.RankingFilter? = null,
        userReadChapterIds: Set<Long> = emptySet(),
        downloadedChapterIds: Set<Long> = emptySet(),
    ): SmartChapterContext {
        if (downloadedChapterIds.isNotEmpty()) {
            every { downloadManager.isChapterDownloaded(any(), any()) } answers
                {
                    val chapter = invocation.args[0] as Chapter
                    chapter.id in downloadedChapterIds
                }
        }
        return engine.process(
            SmartChapterInput(
                manga = manga,
                chapters = chapters,
                downloadManager = downloadManager,
                rankingFilter = rankingFilter,
                userReadChapterIds = userReadChapterIds,
            )
        )
    }

    private fun chapter(
        id: Long,
        number: Float,
        scanlator: String,
        read: Boolean = false,
        volume: String = "",
        isUnavailable: Boolean = false,
        chapterText: String? = null,
        name: String? = null,
    ): ChapterItem {
        return ChapterItem(
            chapter =
                SimpleChapter(
                    id = id,
                    mangaId = 1L,
                    read = read,
                    bookmark = false,
                    lastPageRead = 0,
                    dateFetch = 0L,
                    sourceOrder = 0,
                    smartOrder = 0,
                    url = "url/$id",
                    name = name ?: "Chapter $number",
                    dateUpload = 1000L,
                    chapterNumber = number,
                    pagesLeft = 0,
                    volume = volume,
                    chapterText = chapterText ?: "Ch. $number",
                    chapterTitle = "",
                    language = "",
                    mangaDexChapterId = "",
                    oldMangaDexChapterId = null,
                    scanlator = scanlator,
                    uploader = "",
                    isUnavailable = isUnavailable,
                )
        )
    }
}
