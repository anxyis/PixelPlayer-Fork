package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = ArchiveMediaEntity::class)
@Entity(tableName = "archive_media_fts")
data class ArchiveMediaFtsEntity(
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "caption") val caption: String?,
    @ColumnInfo(name = "file_extension") val fileExtension: String
)
