package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "archive_channels")
data class ArchiveChannelEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id") val chatId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "username") val username: String? = null,
    @ColumnInfo(name = "type") val type: String, // e.g., "private", "channel", "group", "supergroup"
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long = 0,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null // Local path to cached profile photo
)
