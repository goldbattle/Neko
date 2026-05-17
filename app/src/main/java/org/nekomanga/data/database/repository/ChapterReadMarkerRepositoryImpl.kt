package org.nekomanga.data.database.repository

import org.nekomanga.data.database.dao.ChapterReadMarkerDao
import org.nekomanga.data.database.entity.ChapterReadMarkerEntity

class ChapterReadMarkerRepositoryImpl(private val dao: ChapterReadMarkerDao) :
    ChapterReadMarkerRepository {

    override suspend fun getKeysForManga(mangaId: Long): Set<String> {
        return dao.getForManga(mangaId).map { it.equivKey }.toSet()
    }

    override suspend fun markRead(mangaId: Long, equivKeys: Collection<String>, lastPageRead: Int) {
        if (equivKeys.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertAll(
            equivKeys.distinct().map { key ->
                ChapterReadMarkerEntity(
                    mangaId = mangaId,
                    equivKey = key,
                    readAt = now,
                    lastPageRead = lastPageRead,
                )
            }
        )
    }

    override suspend fun markUnread(mangaId: Long, equivKeys: Collection<String>) {
        if (equivKeys.isEmpty()) return
        dao.deleteKeys(mangaId, equivKeys.distinct())
    }
}
