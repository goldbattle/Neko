package eu.kanade.tachiyomi.util.chapter.smartfilter

import org.nekomanga.domain.chapter.ChapterItem

/**
 * Avoids re-running [SmartChapterEngine] when chapter data and ranking rules are unchanged (e.g.
 * manga details combine re-firing on history/track updates).
 */
internal object SmartChapterProcessCache {

    private const val MAX_ENTRIES = 64

    private data class Entry(val key: CacheKey, val context: SmartChapterContext)

    data class CacheKey(
        val chapterFingerprint: Long,
        val rankingFingerprint: Int,
        val mangaFilteredScanlatorsHash: Int,
    )

    private val cache = LinkedHashMap<Long, Entry>(16, 0.75f, true)

    fun keyFor(input: SmartChapterInput): CacheKey {
        val rf = input.effectiveRankingFilter()
        return CacheKey(
            chapterFingerprint = fingerprintChapters(input.chapters),
            rankingFingerprint = fingerprintRanking(rf),
            mangaFilteredScanlatorsHash = input.filteredScanlators.hashCode(),
        )
    }

    @Synchronized
    fun get(mangaId: Long, key: CacheKey): SmartChapterContext? {
        val entry = cache[mangaId] ?: return null
        return entry.context.takeIf { entry.key == key }
    }

    @Synchronized
    fun put(mangaId: Long, key: CacheKey, context: SmartChapterContext) {
        while (cache.size >= MAX_ENTRIES && cache.isNotEmpty()) {
            val eldest = cache.keys.first()
            cache.remove(eldest)
        }
        cache[mangaId] = Entry(key, context)
    }

    @Synchronized
    fun clear(mangaId: Long? = null) {
        if (mangaId == null) {
            cache.clear()
        } else {
            cache.remove(mangaId)
        }
    }

    private fun fingerprintChapters(chapters: List<ChapterItem>): Long {
        var hash = 0L
        for (item in chapters.sortedBy { it.chapter.id }) {
            val chapter = item.chapter
            hash = 31 * hash + chapter.id
            hash = 31 * hash + chapter.chapterNumber.hashCode()
            hash = 31 * hash + chapter.volume.hashCode()
            hash = 31 * hash + chapter.scanlator.hashCode()
            hash = 31 * hash + chapter.uploader.hashCode()
            hash = 31 * hash + chapter.read.hashCode()
            hash = 31 * hash + chapter.dateUpload
            hash = 31 * hash + chapter.smartOrder
            hash = 31 * hash + chapter.sourceOrder
            hash = 31 * hash + item.downloadState.ordinal
        }
        return hash
    }

    private fun fingerprintRanking(filter: SmartChapterEligibility.RankingFilter): Int {
        var hash = filter.filteredScanlators.hashCode()
        hash = 31 * hash + filter.blockedGroups.hashCode()
        hash = 31 * hash + filter.blockedUploaders.hashCode()
        hash = 31 * hash + filter.scanlatorMatchAll.hashCode()
        return hash
    }
}
