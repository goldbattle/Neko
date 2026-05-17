package org.nekomanga.usecases.chapters

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.nekomanga.data.database.repository.ChapterRepository
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.ChapterMarkActions
import org.nekomanga.domain.chapter.SimpleChapter

@OptIn(ExperimentalCoroutinesApi::class)
class MarkChapterUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockChapterRepository: ChapterRepository
    private lateinit var mockChapterReadProgress: ChapterReadProgress
    private lateinit var markChapterUseCase: MarkChapterUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockChapterRepository = mockk(relaxed = true)
        mockChapterReadProgress = mockk(relaxed = true)
        markChapterUseCase = MarkChapterUseCase(mockChapterRepository, mockChapterReadProgress)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given unread action with specific parameters when marking chapters then chapters are updated correctly`() =
        runTest {
            // Arrange
            val expectedLastRead = 5
            val expectedPagesLeft = 10
            val markAction =
                ChapterMarkActions.Unread(
                    lastRead = expectedLastRead,
                    pagesLeft = expectedPagesLeft,
                )

            val simpleChapter =
                SimpleChapter.create()
                    .copy(id = 1L, mangaId = 100L, read = true, lastPageRead = 20, pagesLeft = 0)
            val chapterItem = ChapterItem(chapter = simpleChapter)
            coEvery { mockChapterReadProgress.isSmartFilterEnabled(100L) } returns true

            // Act
            markChapterUseCase(markAction, listOf(chapterItem))

            // Assert
            coVerify(exactly = 1) {
                mockChapterReadProgress.syncReadToEquivalentRows(
                    mangaId = 100L,
                    anchorChapters = any(),
                    read = false,
                )
            }
        }

    @Test
    fun `given read action when smart filter disabled then refreshes library unread cache`() =
        runTest {
            val chapterItem =
                ChapterItem(
                    chapter = SimpleChapter.create().copy(id = 1L, mangaId = 100L, read = false)
                )
            coEvery { mockChapterReadProgress.isSmartFilterEnabled(100L) } returns false

            markChapterUseCase(ChapterMarkActions.Read(), listOf(chapterItem))

            coVerify(exactly = 1) { mockChapterReadProgress.notifyLibraryUnreadChanged(100L) }
            coVerify(exactly = 0) {
                mockChapterReadProgress.syncReadToEquivalentRows(any(), any(), any())
            }
        }
}
