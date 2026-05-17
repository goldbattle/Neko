package org.nekomanga.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chapter_read_markers",
    foreignKeys =
        [
            ForeignKey(
                entity = MangaEntity::class,
                parentColumns = ["id"],
                childColumns = ["manga_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [Index(value = ["manga_id"]), Index(value = ["manga_id", "equiv_key"], unique = true)],
    primaryKeys = ["manga_id", "equiv_key"],
)
data class ChapterReadMarkerEntity(
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    @ColumnInfo(name = "equiv_key") val equivKey: String,
    @ColumnInfo(name = "read_at") val readAt: Long,
    @ColumnInfo(name = "last_page_read") val lastPageRead: Int,
)
