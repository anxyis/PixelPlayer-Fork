package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "archive_messages",
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["thread_id"]),
        Index(value = ["chat_id", "message_id"])
    ]
)
data class ArchiveMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String, // format: "chatId_messageId"
    @ColumnInfo(name = "chat_id") val chatId: Long,
    @ColumnInfo(name = "thread_id") val threadId: Long? = null, // Null for non-forum chats
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
