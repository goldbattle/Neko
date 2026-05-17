package org.nekomanga.usecases.chapters

import org.nekomanga.data.database.repository.ChapterRepository
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.ChapterMarkActions

/**
 * Use Case to mark chapter read, unread, previously read, previously unread, bookmarked,
 * unbookmarked
 */
class MarkChapterUseCase(
    private val chapterRepository: ChapterRepository,
    private val chapterReadProgress: ChapterReadProgress,
) {

    suspend operator fun invoke(markAction: ChapterMarkActions, chapterItems: List<ChapterItem>) {
        if (chapterItems.isEmpty()) return
        val mangaId = chapterItems.first().chapter.mangaId
        val affectsUnread =
            markAction is ChapterMarkActions.Read ||
                markAction is ChapterMarkActions.PreviousRead ||
                markAction is ChapterMarkActions.Unread ||
                markAction is ChapterMarkActions.PreviousUnread

        when (markAction) {
            is ChapterMarkActions.Read,
            is ChapterMarkActions.PreviousRead -> {
                if (chapterReadProgress.isSmartFilterEnabled(mangaId)) {
                    chapterReadProgress.syncReadToEquivalentRows(
                        mangaId,
                        chapterItems.map { it.chapter.toDbChapter() },
                        read = true,
                    )
                } else {
                    markReadDirectly(chapterItems, read = true)
                }
            }
            is ChapterMarkActions.Unread -> {
                val anchors = chapterItems.map { item ->
                    item.chapter
                        .copy(
                            read = false,
                            lastPageRead = markAction.lastRead ?: 0,
                            pagesLeft = markAction.pagesLeft ?: 0,
                        )
                        .toDbChapter()
                }
                if (chapterReadProgress.isSmartFilterEnabled(mangaId)) {
                    chapterReadProgress.syncReadToEquivalentRows(mangaId, anchors, read = false)
                } else {
                    markReadDirectly(
                        chapterItems,
                        read = false,
                        markAction.lastRead,
                        markAction.pagesLeft,
                    )
                }
            }
            is ChapterMarkActions.PreviousUnread -> {
                if (chapterReadProgress.isSmartFilterEnabled(mangaId)) {
                    chapterReadProgress.syncReadToEquivalentRows(
                        mangaId,
                        chapterItems.map {
                            it.chapter
                                .copy(read = false, lastPageRead = 0, pagesLeft = 0)
                                .toDbChapter()
                        },
                        read = false,
                    )
                } else {
                    markReadDirectly(chapterItems, read = false)
                }
            }
            is ChapterMarkActions.Bookmark,
            is ChapterMarkActions.UnBookmark -> {
                val dbChapters = chapterItems.map { chapterItem ->
                    when (markAction) {
                        is ChapterMarkActions.Bookmark -> chapterItem.chapter.copy(bookmark = true)
                        else -> chapterItem.chapter.copy(bookmark = false)
                    }.toDbChapter()
                }
                chapterRepository.updateChaptersProgress(dbChapters)
            }
        }

        // Smart-filter path refreshes inside [ChapterReadProgress.syncReadToEquivalentRows].
        if (affectsUnread && !chapterReadProgress.isSmartFilterEnabled(mangaId)) {
            chapterReadProgress.notifyLibraryUnreadChanged(mangaId)
        }
    }

    private suspend fun markReadDirectly(
        chapterItems: List<ChapterItem>,
        read: Boolean,
        lastPageRead: Int? = null,
        pagesLeft: Int? = null,
    ) {
        val dbChapters = chapterItems.map { item ->
            item.chapter
                .copy(
                    read = read,
                    lastPageRead = if (read) item.chapter.lastPageRead else lastPageRead ?: 0,
                    pagesLeft = if (read) item.chapter.pagesLeft else pagesLeft ?: 0,
                )
                .toDbChapter()
        }
        chapterRepository.updateChaptersProgress(dbChapters)
    }
}
