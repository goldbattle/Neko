package org.nekomanga.usecases.chapters

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.chapter.ChapterItemFilter
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterEngine
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_A
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_B
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.nekomanga.data.database.repository.ChapterReadMarkerRepository
import org.nekomanga.data.database.repository.ChapterRepository
import org.nekomanga.data.database.repository.MangaRepository
import org.nekomanga.domain.chapter.SimpleChapter
import org.nekomanga.domain.details.MangaDetailsPreferences
import org.nekomanga.domain.library.LibraryPreferences
import org.nekomanga.domain.reader.ReaderPreferences
import org.nekomanga.domain.site.MangaDexPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class CachedMangaUnreadTest {

    private val mangaId = 10L
    private val mangaRepository = mockk<MangaRepository>()
    private val chapterRepository = mockk<ChapterRepository>()
    private val markerRepository = mockk<ChapterReadMarkerRepository>()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val mangaDexPreferences = mockk<MangaDexPreferences>(relaxed = true)
    private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
    private val mangaDetailsPreferences = mockk<MangaDetailsPreferences>(relaxed = true)

    private val manga =
        MangaImpl().apply {
            id = mangaId
            title = "Test"
            chapter_flags = Manga.CHAPTER_SMART_DEDUP_ENABLED
        }

    private lateinit var cachedMangaUnread: CachedMangaUnread

    @Before
    fun setup() {
        SmartChapterEngine.clearCache()
        every { downloadManager.getDownloadCount(manga) } returns 0
        every { downloadManager.isChapterDownloaded(any<Chapter>(), any()) } returns false
        every { mangaDexPreferences.blockedGroups().get() } returns emptySet()
        every { mangaDexPreferences.blockedUploaders().get() } returns emptySet()
        every { libraryPreferences.chapterScanlatorFilterOption().get() } returns 0
        every { mangaDetailsPreferences.smartChapterFilterVerboseLog().get() } returns false
        every { mangaDetailsPreferences.smartChapterFilterByDefault().get() } returns true
        coEvery { markerRepository.getKeysForManga(mangaId) } returns emptySet()
        coEvery { mangaRepository.getMangaById(mangaId) } returns manga

        val chapterFilter =
            ChapterItemFilter(
                preferences = mockk<PreferencesHelper>(relaxed = true),
                mangaDexPreferences = mangaDexPreferences,
                libraryPreferences = libraryPreferences,
                readerPreferences = mockk<ReaderPreferences>(relaxed = true),
                mangaDetailsPreferences = mangaDetailsPreferences,
                downloadManager = downloadManager,
                smartChapterEngine = SmartChapterEngine(),
            )
        val chapterReadProgress =
            ChapterReadProgress(
                chapterRepository = chapterRepository,
                markerRepository = markerRepository,
                mangaRepository = mangaRepository,
                mangaDetailsPreferences = mangaDetailsPreferences,
            )
        cachedMangaUnread =
            CachedMangaUnread(
                mangaRepository = mangaRepository,
                chapterRepository = chapterRepository,
                chapterItemFilter = chapterFilter,
                downloadManager = downloadManager,
                chapterReadProgress = chapterReadProgress,
            )
    }

    @Test
    fun `computeFilteredUnread dedupes duplicate wholes to one unread`() = runTest {
        coEvery { chapterRepository.getChaptersForManga(mangaId) } returns
            listOf(
                dbChapter(id = 1L, number = 6f, scanlator = FAN_A),
                dbChapter(id = 2L, number = 6f, scanlator = FAN_B),
                dbChapter(id = 3L, number = 7f, scanlator = FAN_A),
            )

        assertEquals(2, cachedMangaUnread.computeFilteredUnread(manga))
    }

    @Test
    fun `computeFilteredUnread excludes read chapters`() = runTest {
        coEvery { chapterRepository.getChaptersForManga(mangaId) } returns
            listOf(
                dbChapter(id = 1L, number = 5f, scanlator = FAN_A, read = true),
                dbChapter(id = 2L, number = 6f, scanlator = FAN_A, read = false),
            )

        assertEquals(1, cachedMangaUnread.computeFilteredUnread(manga))
    }

    @Test
    fun `computeFilteredUnread honors read markers when db row is unread`() = runTest {
        coEvery { chapterRepository.getChaptersForManga(mangaId) } returns
            listOf(
                dbChapter(id = 1L, number = 5f, scanlator = FAN_A, read = false),
                dbChapter(id = 2L, number = 6f, scanlator = FAN_A, read = false),
            )
        coEvery { markerRepository.getKeysForManga(mangaId) } returns setOf("w:5")

        assertEquals(1, cachedMangaUnread.computeFilteredUnread(manga))
    }

    @Test
    fun `refresh persists filtered unread to manga row`() = runTest {
        coEvery { chapterRepository.getChaptersForManga(mangaId) } returns
            listOf(
                dbChapter(id = 1L, number = 6f, scanlator = FAN_A),
                dbChapter(id = 2L, number = 6f, scanlator = FAN_B),
                dbChapter(id = 3L, number = 7f, scanlator = FAN_A),
            )
        coEvery { mangaRepository.updateCachedUnread(mangaId, 2) } returns Unit

        cachedMangaUnread.refresh(manga)

        coVerify(exactly = 1) { mangaRepository.updateCachedUnread(mangaId, 2) }
    }

    @Test
    fun `refresh does not clear badge when chapter table is empty`() = runTest {
        coEvery { chapterRepository.getChaptersForManga(mangaId) } returns emptyList()

        cachedMangaUnread.refresh(manga)

        coVerify(exactly = 0) { mangaRepository.updateCachedUnread(any(), any()) }
    }

    private fun dbChapter(
        id: Long,
        number: Float,
        scanlator: String,
        read: Boolean = false,
    ): Chapter =
        SimpleChapter.create()
            .copy(
                id = id,
                mangaId = mangaId,
                chapterNumber = number,
                scanlator = scanlator,
                chapterText = "Ch. $number",
                read = read,
            )
            .toDbChapter()
}
