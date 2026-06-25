package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "archive_topics",
    indices = [
        Index(value = ["chat_id"])
    ]
)
data class ArchiveTopicEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String, // format: "chatId_threadId"
    @ColumnInfo(name = "chat_id") val chatId: Long,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long = 0,
    @ColumnInfo(name = "icon_emoji") val iconEmoji: String? = null
)
