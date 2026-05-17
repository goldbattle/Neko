package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.chapter.ExampleDbGroups.BLOCKED
import eu.kanade.tachiyomi.util.chapter.ExampleDbGroups.GROUP_11
import eu.kanade.tachiyomi.util.chapter.ExampleDbGroups.GROUP_3
import eu.kanade.tachiyomi.util.chapter.ExampleDbGroups.GROUP_6
import eu.kanade.tachiyomi.util.chapter.ExampleDbGroups.MANGA_DEX
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterEngine
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterEngineExampleDbFixtureTest
import io.mockk.every
import io.mockk.mockk
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nekomanga.domain.details.MangaDetailsPreferences
import org.nekomanga.domain.library.LibraryPreferences
import org.nekomanga.domain.site.MangaDexPreferences

/**
 * End-to-end: [ChapterItemFilter] + [ChapterItemSort] with [ExampleDbChapterData] (manga id=58).
 */
class ChapterItemFilterSmartChapterTest {

    private val exampleDbFixture = SmartChapterEngineExampleDbFixtureTest()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val preferences = mockk<PreferencesHelper>(relaxed = true)
    private val mangaDexPreferences = mockk<MangaDexPreferences>(relaxed = true)
    private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
    private val mangaDetailsPreferences = mockk<MangaDetailsPreferences>(relaxed = true)
    private lateinit var chapterFilter: ChapterItemFilter
    private lateinit var chapterSort: ChapterItemSort

    private val manga =
        MangaImpl().apply {
            id = 58L
            title = "Example DB manga"
            chapter_flags = Manga.CHAPTER_SMART_DEDUP_ENABLED or Manga.CHAPTER_FILTER_LOCAL
        }

    @Before
    fun setup() {
        SmartChapterEngine.clearCache()
        every { downloadManager.isChapterDownloaded(any(), any(), any<Boolean>()) } returns false
        every { mangaDexPreferences.blockedGroups().get() } returns emptySet()
        every { mangaDexPreferences.blockedUploaders().get() } returns emptySet()
        every { libraryPreferences.chapterScanlatorFilterOption().get() } returns 0
        every { mangaDetailsPreferences.smartChapterFilterVerboseLog().get() } returns false
        every { mangaDetailsPreferences.smartChapterFilterByDefault().get() } returns true
        every { mangaDetailsPreferences.filterChapterByRead().get() } returns Manga.SHOW_ALL
        every { mangaDetailsPreferences.filterChapterByDownloaded().get() } returns Manga.SHOW_ALL
        every { mangaDetailsPreferences.filterChapterByBookmarked().get() } returns Manga.SHOW_ALL
        every { mangaDetailsPreferences.filterChapterByAvailable().get() } returns Manga.SHOW_ALL
        every { mangaDetailsPreferences.chaptersDescAsDefault().get() } returns false
        every { mangaDetailsPreferences.sortChapterOrder().get() } returns
            Manga.CHAPTER_SORTING_SMART

        chapterFilter =
            ChapterItemFilter(
                preferences = preferences,
                mangaDexPreferences = mangaDexPreferences,
                libraryPreferences = libraryPreferences,
                readerPreferences = mockk(relaxed = true),
                mangaDetailsPreferences = mangaDetailsPreferences,
                downloadManager = downloadManager,
                smartChapterEngine = SmartChapterEngine(),
            )
        chapterSort =
            ChapterItemSort(
                chapterFilter = chapterFilter,
                preferences = preferences,
                mangaDetailsPreferences = mangaDetailsPreferences,
            )
    }

    @Test
    fun `full example db active list includes wholes 8 through 19`() {
        val allChapters = ExampleDbChapterData.allChapters()

        val (_, smartContext) =
            chapterFilter.filterChaptersWithContext(
                chapters = allChapters,
                manga = manga,
                userReadChapterIds = emptySet(),
            )
        requireNotNull(smartContext) { "Smart filter should run when per-manga flag is set" }

        val active =
            chapterSort.getChaptersSorted(
                manga = manga,
                rawChapters = allChapters,
                smartContext = smartContext,
            )

        val wholeNumbers =
            active
                .map { it.chapter.chapterNumber }
                .filter { n -> abs(n - n.toInt()) < 0.01f && n in 8f..19f }
                .map { it.toInt() }
                .toSet()
        val missing = (8..19).filter { it !in wholeNumbers }
        assertTrue("Missing wholes 8-19 in full DB pipeline: $missing", missing.isEmpty())
    }

    @Test
    fun `example db active chapter list includes wholes 8 through 20`() {
        val allChapters = exampleDbFixture.exampleDbChapters7Through21()
        val userReadChapterIds = setOf(1501L) // ch7 fan row read

        val (_, smartContext) =
            chapterFilter.filterChaptersWithContext(
                chapters = allChapters,
                manga = manga,
                userReadChapterIds = userReadChapterIds,
            )
        val active =
            chapterSort.getChaptersSorted(
                manga = manga,
                rawChapters = allChapters,
                smartContext = smartContext,
            )

        val visibleNumbers = active.map { it.chapter.chapterNumber }.toSet()
        val missingWholes = (8..20).map { it.toFloat() }.toSet() - visibleNumbers
        assertTrue("Missing wholes in active list: $missingWholes", missingWholes.isEmpty())
        assertTrue(7.5f in visibleNumbers)
    }

    @Test
    fun `manga hidden scanlators removed before smart dedup`() {
        val mangaWithHiddenGroups =
            MangaImpl().apply {
                id = 58L
                title = "Example DB manga"
                chapter_flags = Manga.CHAPTER_SMART_DEDUP_ENABLED or Manga.CHAPTER_FILTER_LOCAL
                filtered_scanlators = ChapterUtil.getScanlatorString(setOf(GROUP_3, GROUP_6))
            }
        val chapters =
            listOf(
                exampleDbFixture.chapter(1498L, 8f, GROUP_3, "Ch.8"),
                exampleDbFixture.chapter(1682L, 8f, GROUP_6, "Ch.8"),
                exampleDbFixture.chapter(1762L, 8f, GROUP_11, "Ch.8"),
            )

        val (_, smartContext) =
            chapterFilter.filterChaptersWithContext(
                chapters = chapters,
                manga = mangaWithHiddenGroups,
                userReadChapterIds = emptySet(),
            )
        requireNotNull(smartContext)

        val active =
            chapterSort.getChaptersSorted(
                manga = mangaWithHiddenGroups,
                rawChapters = chapters,
                smartContext = smartContext,
            )

        assertEquals(1, active.size)
        assertEquals(1762L, active.single().chapter.id)
        assertFalse(smartContext.visibleChapters.any { it.chapter.id == 1498L })
        assertFalse(smartContext.visibleChapters.any { it.chapter.id == 1682L })
    }

    @Test
    fun `blocked group removed before smart dedup never wins`() {
        every { mangaDexPreferences.blockedGroups().get() } returns setOf(BLOCKED)
        val chapters =
            listOf(
                exampleDbFixture.chapter(1L, 8f, BLOCKED, "Ch.8"),
                exampleDbFixture.chapter(1762L, 8f, GROUP_11, "Ch.8"),
            )

        val (_, smartContext) =
            chapterFilter.filterChaptersWithContext(
                chapters = chapters,
                manga = manga,
                userReadChapterIds = emptySet(),
            )
        requireNotNull(smartContext)

        assertEquals(1762L, smartContext.visibleChapters.single().chapter.id)
    }

    @Test
    fun `chapter omitted when only blocked group copies remain after pre filter`() {
        every { mangaDexPreferences.blockedGroups().get() } returns setOf(BLOCKED)
        val chapters = listOf(exampleDbFixture.chapter(1L, 8f, BLOCKED, "Ch.8"))

        val (active, smartContext) =
            chapterFilter.filterChaptersWithContext(
                chapters = chapters,
                manga = manga,
                userReadChapterIds = emptySet(),
            )
        requireNotNull(smartContext)

        assertTrue(active.isEmpty())
        assertTrue(smartContext.visibleChapters.isEmpty())
    }

    @Test
    fun `native mangadex and fan releases show wholes 8 through 12 without source filter`() {
        val chapters = buildList {
            for (n in 8..12) {
                add(exampleDbFixture.chapter((n * 100).toLong(), n.toFloat(), MANGA_DEX, "Ch.$n"))
                add(exampleDbFixture.chapter((n * 100 + 1).toLong(), n.toFloat(), GROUP_3, "Ch.$n"))
                add(
                    exampleDbFixture.chapter((n * 100 + 2).toLong(), n.toFloat(), GROUP_11, "Ch.$n")
                )
            }
        }

        val (active, smartContext) =
            chapterFilter.filterChaptersWithContext(
                chapters = chapters,
                manga = manga,
                userReadChapterIds = emptySet(),
            )
        requireNotNull(smartContext)

        val wholes =
            active
                .map { it.chapter.chapterNumber }
                .filter { n -> abs(n - n.toInt()) < 0.01f && n in 8f..12f }
                .map { it.toInt() }
                .toSet()
        assertTrue(
            "Missing wholes 8-12 when MD and fan both exist: ${(8..12) - wholes}",
            wholes.size == 5,
        )
        assertTrue(
            "Native MangaDex stub should not be the visible row",
            active.none { it.chapter.scanlator == MANGA_DEX },
        )
    }

    @Test
    fun `read via metadata on visible winner when user read suppressed duplicate`() {
        val chapters =
            listOf(
                exampleDbFixture.chapter(
                    id = 1501L,
                    number = 7f,
                    scanlator = GROUP_3,
                    chapterText = "Ch.7",
                ),
                exampleDbFixture.chapter(
                    id = 1696L,
                    number = 7f,
                    scanlator = GROUP_6,
                    chapterText = "Ch.7",
                ),
            )
        val userReadChapterIds = setOf(1501L)

        val (_, smartContext) =
            chapterFilter.filterChaptersWithContext(
                chapters = chapters,
                manga = manga,
                userReadChapterIds = userReadChapterIds,
            )
        requireNotNull(smartContext)
        val winnerId = smartContext.visibleChapters.single().chapter.id
        val meta = smartContext.metadataByChapterId[winnerId]!!
        if (winnerId == 1501L) {
            assertTrue(meta.readState.userRead)
        } else {
            assertFalse(meta.readState.userRead)
            assertTrue(meta.readState.autoRead)
            assertEquals(GROUP_3, meta.readState.userReadVia?.scanlatorLabel)
        }
    }
}
