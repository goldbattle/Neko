package org.nekomanga.usecases.chapters

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.util.chapter.ChapterReadMarkerKeys
import eu.kanade.tachiyomi.util.chapter.smartfilter.ChapterClassifier
import eu.kanade.tachiyomi.util.chapter.smartfilter.ChapterReadEquivalence
import eu.kanade.tachiyomi.util.chapter.smartfilter.ClassifiedChapter
import org.nekomanga.data.database.repository.ChapterReadMarkerRepository
import org.nekomanga.data.database.repository.ChapterRepository
import org.nekomanga.data.database.repository.MangaRepository
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.toChapterItem
import org.nekomanga.domain.chapter.toSimpleChapter
import org.nekomanga.domain.details.MangaDetailsPreferences
import uy.kohesive.injekt.injectLazy

/**
 * Persists read progress per logical chapter (across scanlators/sources) and applies it to chapter
 * rows when smart chapter filtering is enabled for the manga.
 */
class ChapterReadProgress(
    private val chapterRepository: ChapterRepository,
    private val markerRepository: ChapterReadMarkerRepository,
    private val mangaRepository: MangaRepository,
    private val mangaDetailsPreferences: MangaDetailsPreferences,
) {

    private val cachedMangaUnread: CachedMangaUnread by injectLazy()

    suspend fun isSmartFilterEnabled(mangaId: Long): Boolean {
        val manga = mangaRepository.getMangaById(mangaId) ?: return false
        return manga.smartChapterFilterEnabled(mangaDetailsPreferences)
    }

    suspend fun applyMarkersToAllChapters(mangaId: Long) {
        if (!isSmartFilterEnabled(mangaId)) return
        val chapters = chapterRepository.getChaptersForManga(mangaId)
        applyMarkers(mangaId, chapters)
    }

    /** Apply persisted markers to chapter items for smart-filter UI (read + "read via" tags). */
    suspend fun applyMarkersToChapterItems(
        mangaId: Long,
        chapters: List<ChapterItem>,
    ): List<ChapterItem> {
        if (!isSmartFilterEnabled(mangaId)) return chapters
        val keys = markerRepository.getKeysForManga(mangaId)
        if (keys.isEmpty()) return chapters

        val classifiedById = chapters.associate { item ->
            item.chapter.id to ChapterClassifier.classify(item)
        }
        val readAnchors = chapters.mapNotNull { item ->
            val matchesMarker = ChapterReadMarkerKeys.matches(item.chapter.toDbChapter(), keys)
            if (item.chapter.read || matchesMarker) {
                classifiedById[item.chapter.id]
            } else {
                null
            }
        }
        if (readAnchors.isEmpty()) return chapters

        return chapters.map { item ->
            if (item.chapter.read) return@map item
            val classified = classifiedById[item.chapter.id] ?: return@map item
            val matchesMarker = ChapterReadMarkerKeys.matches(item.chapter.toDbChapter(), keys)
            val matchesEquivalence = readAnchors.any { anchor ->
                ChapterReadEquivalence.sharesEquivalence(classified, anchor)
            }
            if (matchesMarker || matchesEquivalence) {
                item.copy(chapter = item.chapter.copy(read = true))
            } else {
                item
            }
        }
    }

    /** Apply persisted markers to new chapters before insert (no DB write). */
    suspend fun applyMarkersInMemory(mangaId: Long, chapters: List<Chapter>) {
        if (!isSmartFilterEnabled(mangaId)) return
        val keys = markerRepository.getKeysForManga(mangaId)
        if (keys.isEmpty()) return
        for (chapter in chapters) {
            if (!chapter.read && ChapterReadMarkerKeys.matches(chapter, keys)) {
                chapter.read = true
            }
        }
    }

    suspend fun applyMarkers(mangaId: Long, chapters: List<Chapter>): List<Chapter> {
        if (!isSmartFilterEnabled(mangaId)) return chapters
        val keys = markerRepository.getKeysForManga(mangaId)
        if (keys.isEmpty()) return chapters

        val toUpdate = mutableListOf<Chapter>()
        for (chapter in chapters) {
            if (!chapter.read && ChapterReadMarkerKeys.matches(chapter, keys)) {
                chapter.read = true
                toUpdate.add(chapter)
            }
        }
        if (toUpdate.isNotEmpty()) {
            chapterRepository.updateChaptersProgress(toUpdate)
            persistLibraryUnreadCount(mangaId)
        }
        return chapters
    }

    /** Call before deleting chapter rows so read state survives (e.g. merge source removal). */
    suspend fun preserveReadStateBeforeDelete(mangaId: Long, chaptersBeingDeleted: List<Chapter>) {
        if (!isSmartFilterEnabled(mangaId)) return
        val keys =
            chaptersBeingDeleted
                .filter { it.read }
                .map { ChapterReadMarkerKeys.fromChapter(it) }
                .distinct()
        if (keys.isNotEmpty()) {
            markerRepository.markRead(mangaId, keys, lastPageRead = 0)
        }
    }

    suspend fun syncReadToEquivalentRows(
        mangaId: Long,
        anchorChapters: List<Chapter>,
        read: Boolean,
    ) {
        if (!isSmartFilterEnabled(mangaId)) {
            updateChaptersReadDirectly(mangaId, anchorChapters, read)
            return
        }

        val anchorKeys = anchorChapters.map { ChapterReadMarkerKeys.fromChapter(it) }.distinct()
        val anchorClassified = anchorChapters.mapNotNull { it.toClassified() }

        if (read) {
            val lastPage = anchorChapters.maxOfOrNull { it.last_page_read } ?: 0
            markerRepository.markRead(mangaId, anchorKeys, lastPage)
        } else {
            markerRepository.markUnread(mangaId, anchorKeys)
        }

        val allChapters = chapterRepository.getChaptersForManga(mangaId)
        val toUpdate = allChapters.filter { chapter ->
            val matchesMarker = anchorKeys.any { ChapterReadMarkerKeys.matches(chapter, it) }
            val matchesEquivalence =
                if (anchorClassified.isEmpty()) {
                    false
                } else {
                    val classified = chapter.toClassified() ?: return@filter false
                    anchorClassified.any { anchor ->
                        ChapterReadEquivalence.sharesEquivalence(classified, anchor)
                    }
                }
            (matchesMarker || matchesEquivalence) && chapter.read != read
        }
        for (chapter in toUpdate) {
            chapter.read = read
            if (!read) {
                chapter.last_page_read = 0
                chapter.pages_left = 0
            }
        }
        if (toUpdate.isNotEmpty()) {
            chapterRepository.updateChaptersProgress(toUpdate)
        }
        persistLibraryUnreadCount(mangaId)
    }

    private suspend fun updateChaptersReadDirectly(
        mangaId: Long,
        chapters: List<Chapter>,
        read: Boolean,
    ) {
        if (chapters.isEmpty()) return
        for (chapter in chapters) {
            chapter.read = read
            if (!read) {
                chapter.last_page_read = 0
                chapter.pages_left = 0
            }
        }
        chapterRepository.updateChaptersProgress(chapters)
        persistLibraryUnreadCount(mangaId)
    }

    /**
     * Recompute and persist the library unread badge. Call after any mutation that changes which
     * chapters count as read/unread for filtering purposes.
     */
    suspend fun notifyLibraryUnreadChanged(mangaId: Long) {
        cachedMangaUnread.refresh(mangaId)
    }

    private suspend fun persistLibraryUnreadCount(mangaId: Long) {
        notifyLibraryUnreadChanged(mangaId)
    }

    private fun Chapter.toClassified(): ClassifiedChapter? {
        val simple = toSimpleChapter() ?: return null
        return ChapterClassifier.classify(simple.toChapterItem())
    }
}
