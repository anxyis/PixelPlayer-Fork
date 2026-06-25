package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "archive_media",
    indices = [
        Index(value = ["message_id"]),
        Index(value = ["file_id"])
    ]
)
data class ArchiveMediaEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String, // Typically the same as message_id or file_id
    @ColumnInfo(name = "message_id") val messageId: String, // format: "chatId_messageId" (FK to ArchiveMessageEntity)
    @ColumnInfo(name = "file_id") val fileId: Int,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "caption") val caption: String? = null,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "file_extension") val fileExtension: String,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "local_path") val localPath: String? = null
)
