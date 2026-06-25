package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveDao {

    // --- Channels ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ArchiveChannelEntity)

    @Query("SELECT * FROM archive_channels ORDER BY title ASC")
    fun getAllChannels(): Flow<List<ArchiveChannelEntity>>

    @Query("DELETE FROM archive_channels WHERE chat_id = :chatId")
    suspend fun deleteChannel(chatId: Long)

    @Query("SELECT * FROM archive_channels WHERE chat_id = :chatId")
    suspend fun getChannelById(chatId: Long): ArchiveChannelEntity?

    // --- Topics ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<ArchiveTopicEntity>)

    @Query("SELECT * FROM archive_topics WHERE chat_id = :chatId ORDER BY name ASC")
    fun getTopicsByChannel(chatId: Long): Flow<List<ArchiveTopicEntity>>

    @Query("DELETE FROM archive_topics WHERE chat_id = :chatId")
    suspend fun deleteTopicsByChannel(chatId: Long)

    // --- Messages ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<ArchiveMessageEntity>)

    @Query("SELECT * FROM archive_messages WHERE chat_id = :chatId ORDER BY timestamp DESC")
    fun getMessagesByChannel(chatId: Long): Flow<List<ArchiveMessageEntity>>

    // --- Media ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: List<ArchiveMediaEntity>)

    @Query("SELECT * FROM archive_media WHERE message_id IN (SELECT id FROM archive_messages WHERE chat_id = :chatId) ORDER BY file_name ASC")
    fun getMediaByChannel(chatId: Long): Flow<List<ArchiveMediaEntity>>

    @Query("SELECT archive_media.* FROM archive_media JOIN archive_media_fts ON archive_media.rowid = archive_media_fts.rowid WHERE archive_media_fts MATCH :query")
    fun searchMedia(query: String): Flow<List<ArchiveMediaEntity>>

    // --- Download State ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadState(state: ArchiveDownloadStateEntity)

    @Query("SELECT * FROM archive_download_state WHERE file_id = :fileId")
    fun getDownloadState(fileId: Int): Flow<ArchiveDownloadStateEntity?>

    @Query("SELECT * FROM archive_download_state WHERE status IN ('QUEUED', 'DOWNLOADING')")
    fun getActiveDownloads(): Flow<List<ArchiveDownloadStateEntity>>

    // --- Transactions ---
    @Transaction
    suspend fun clearAll() {
        clearAllMedia()
        clearAllMessages()
        clearAllTopics()
        clearAllChannels()
    }

    @Query("DELETE FROM archive_media")
    suspend fun clearAllMedia()

    @Query("DELETE FROM archive_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM archive_topics")
    suspend fun clearAllTopics()

    @Query("DELETE FROM archive_channels")
    suspend fun clearAllChannels()
}
