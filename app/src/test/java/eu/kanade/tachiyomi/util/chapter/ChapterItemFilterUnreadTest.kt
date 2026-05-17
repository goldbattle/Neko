package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterEngine
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_A
import eu.kanade.tachiyomi.util.chapter.smartfilter.SmartChapterFilterTestGroups.FAN_B
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.SimpleChapter
import org.nekomanga.domain.details.MangaDetailsPreferences
import org.nekomanga.domain.library.LibraryPreferences
import org.nekomanga.domain.reader.ReaderPreferences
import org.nekomanga.domain.site.MangaDexPreferences

/**
 * Ensures library unread counting and manga-details gate+smart share
 * [ChapterItemFilter.processGateAndSmart].
 */
class ChapterItemFilterUnreadTest {

    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val mangaDexPreferences = mockk<MangaDexPreferences>(relaxed = true)
    private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
    private val mangaDetailsPreferences = mockk<MangaDetailsPreferences>(relaxed = true)
    private lateinit var chapterFilter: ChapterItemFilter

    private val manga =
        MangaImpl().apply {
            id = 1L
            chapter_flags = Manga.CHAPTER_SMART_DEDUP_ENABLED
        }

    @Before
    fun setup() {
        SmartChapterEngine.clearCache()
        every { downloadManager.isChapterDownloaded(any(), any()) } returns false
        every { downloadManager.isChapterDownloaded(any(), any(), any()) } returns false
        every { mangaDexPreferences.blockedGroups().get() } returns emptySet()
        every { mangaDexPreferences.blockedUploaders().get() } returns emptySet()
        every { libraryPreferences.chapterScanlatorFilterOption().get() } returns 0
        every { mangaDetailsPreferences.smartChapterFilterVerboseLog().get() } returns false
        every { mangaDetailsPreferences.smartChapterFilterByDefault().get() } returns true

        chapterFilter =
            ChapterItemFilter(
                preferences = mockk<PreferencesHelper>(relaxed = true),
                mangaDexPreferences = mangaDexPreferences,
                libraryPreferences = libraryPreferences,
                readerPreferences = mockk<ReaderPreferences>(relaxed = true),
                mangaDetailsPreferences = mangaDetailsPreferences,
                downloadManager = downloadManager,
                smartChapterEngine = SmartChapterEngine(),
            )
    }

    @Test
    fun `computeLibraryUnreadCount matches smart context countUnread on same input`() {
        val chapters =
            listOf(
                chapterItem(id = 1L, number = 6f, scanlator = FAN_A, read = false),
                chapterItem(id = 2L, number = 6f, scanlator = FAN_B, read = false),
                chapterItem(id = 3L, number = 7f, scanlator = FAN_A, read = true),
            )

        val badgeCount = chapterFilter.computeLibraryUnreadCount(chapters, manga)
        val (_, smartContext) = chapterFilter.processGateAndSmart(chapters, manga)
        val contextCount = smartContext!!.countUnread()

        assertEquals(contextCount, badgeCount)
        assertEquals(1, badgeCount)
    }

    private fun chapterItem(
        id: Long,
        number: Float,
        scanlator: String,
        read: Boolean,
    ): ChapterItem =
        ChapterItem(
            chapter =
                SimpleChapter.create()
                    .copy(
                        id = id,
                        mangaId = 1L,
                        chapterNumber = number,
                        scanlator = scanlator,
                        chapterText = "Ch. $number",
                        read = read,
                    )
        )
}
