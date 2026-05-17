package org.nekomanga.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.nekomanga.data.database.entity.ChapterReadMarkerEntity

@Dao
interface ChapterReadMarkerDao {

    @Query("SELECT * FROM chapter_read_markers WHERE manga_id = :mangaId")
    suspend fun getForManga(mangaId: Long): List<ChapterReadMarkerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(markers: List<ChapterReadMarkerEntity>)

    @Query("DELETE FROM chapter_read_markers WHERE manga_id = :mangaId AND equiv_key IN (:keys)")
    suspend fun deleteKeys(mangaId: Long, keys: List<String>)

    @Query("DELETE FROM chapter_read_markers WHERE manga_id = :mangaId")
    suspend fun deleteForManga(mangaId: Long)
}
