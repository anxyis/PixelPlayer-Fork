package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.database.ArchiveDao
import com.theveloper.pixelplay.data.database.ArchiveChannelEntity
import com.theveloper.pixelplay.data.database.ArchiveTopicEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramArchiveSyncManager @Inject constructor(
    private val chatManager: TelegramChatManager,
    private val clientManager: TelegramClientManager,
    private val archiveDao: ArchiveDao
) {

    suspend fun syncChannel(chatId: Long, isForum: Boolean) = withContext(Dispatchers.IO) {
        Timber.d("Starting archive sync for chat $chatId (isForum=$isForum)")
        try {
            val chat = clientManager.sendRequest<TdApi.Chat>(TdApi.GetChat(chatId))
            val chatType = when (chat.type) {
                is TdApi.ChatTypePrivate -> "private"
                is TdApi.ChatTypeBasicGroup -> "group"
                is TdApi.ChatTypeSupergroup -> "supergroup"
                is TdApi.ChatTypeSecret -> "secret"
                else -> "unknown"
            }

            // 1. Sync Channel Metadata
            val channelEntity = ArchiveChannelEntity(
                chatId = chat.id,
                title = chat.title,
                username = null, // Can resolve later if needed
                type = chatType,
                lastSyncTime = System.currentTimeMillis(),
                photoPath = null // Resolving photo is handled later by DownloadManager
            )
            archiveDao.insertChannel(channelEntity)

            // 2. Sync Topics or Messages
            if (isForum) {
                val topics = chatManager.getForumTopics(chatId)
                val archiveTopics = topics.map {
                    ArchiveTopicEntity(
                        id = it.id,
                        chatId = it.chatId,
                        threadId = it.threadId,
                        name = it.name,
                        lastSyncTime = System.currentTimeMillis(),
                        iconEmoji = it.iconEmoji
                    )
                }
                archiveDao.insertTopics(archiveTopics)

                for (topic in archiveTopics) {
                    syncMessagesForThread(chatId, topic.threadId)
                }
            } else {
                syncMessagesForThread(chatId, null)
            }

            Timber.d("Finished archive sync for chat $chatId")
        } catch (e: Exception) {
            Timber.e(e, "Error syncing chat $chatId")
        }
    }

    private suspend fun syncMessagesForThread(chatId: Long, threadId: Long?) {
        var offsetMessageId = 0L
        val batchSize = 100
        var totalSynced = 0

        while (true) {
            val (messages, nextOffset) = chatManager.getArchiveMessages(
                chatId = chatId,
                threadId = threadId,
                offsetMessageId = offsetMessageId,
                limit = batchSize
            )

            if (messages.isEmpty()) break

            val messageEntities = messages.map { ArchiveMappers.mapToMessageEntity(it) }
            val mediaEntities = messages.mapNotNull { ArchiveMappers.mapToMediaEntity(it) }

            archiveDao.insertMessages(messageEntities)
            if (mediaEntities.isNotEmpty()) {
                archiveDao.insertMedia(mediaEntities)
            }

            totalSynced += messages.size

            if (nextOffset == 0L || messages.size < batchSize) break
            offsetMessageId = nextOffset
        }

        Timber.d("Synced $totalSynced messages for chat $chatId (thread=$threadId)")
    }
}
