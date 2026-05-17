package org.nekomanga.data.database.repository

interface ChapterReadMarkerRepository {
    suspend fun getKeysForManga(mangaId: Long): Set<String>

    suspend fun markRead(mangaId: Long, equivKeys: Collection<String>, lastPageRead: Int)

    suspend fun markUnread(mangaId: Long, equivKeys: Collection<String>)
}
