package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.database.TelegramTopicEntity
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.SharedFlow
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramChatManager @Inject constructor(
    private val clientManager: TelegramClientManager,
    private val downloadManager: TelegramDownloadManager
) {

    suspend fun searchPublicChat(username: String): TdApi.Chat? {
        return try {
            clientManager.sendRequest(TdApi.SearchPublicChat(username))
        } catch (e: Exception) {
            Timber.e(e, "Error searching public chat: $username")
            null
        }
    }

    suspend fun isForum(chatId: Long): Boolean {
        return try {
            val chat = clientManager.sendRequest<TdApi.Chat>(TdApi.GetChat(chatId))
            val type = chat.type
            if (type !is TdApi.ChatTypeSupergroup) return false
            val supergroup = clientManager.sendRequest<TdApi.Supergroup>(
                TdApi.GetSupergroup(type.supergroupId)
            )
            supergroup.isForum
        } catch (e: Exception) {
            Timber.w(e, "isForum check failed for chatId=$chatId")
            false
        }
    }

    suspend fun getForumTopics(chatId: Long): List<TelegramTopicEntity> {
        val topics = mutableListOf<TelegramTopicEntity>()
        try {
            var offsetDate = 0
            var offsetMessageId = 0L
            var offsetForumTopicId = 0

            while (true) {
                val request = TdApi.GetForumTopics().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.offsetDate = offsetDate
                    this.offsetMessageId = offsetMessageId
                    this.offsetForumTopicId = offsetForumTopicId
                    this.limit = 100
                }
                val result = clientManager.sendRequest<TdApi.ForumTopics>(request)

                if (result.topics.isEmpty()) break

                for (topic in result.topics) {
                    val info = topic.info
                    val emojiId = info.icon.customEmojiId

                    val threadId: Long = run {
                        var resolved = 0L
                        val preferredNames = listOf(
                            "messageThreadId", "message_thread_id",
                            "threadId", "thread_id",
                            "topicId", "topic_id",
                            "forumTopicId", "forum_topic_id"
                        )
                        for (name in preferredNames) {
                            try {
                                val f = info.javaClass.getDeclaredField(name)
                                f.isAccessible = true
                                val v = f.get(info)
                                val candidate = when (v) {
                                    is Long -> v
                                    is Int  -> v.toLong()
                                    else    -> 0L
                                }
                                if (candidate != 0L) {
                                    resolved = candidate
                                    break
                                }
                            } catch (_: NoSuchFieldException) { }
                        }

                        if (resolved == 0L) {
                            for (f in info.javaClass.declaredFields) {
                                if (f.name in setOf("chatId", "chat_id", "creatorUserId", "creator_user_id", "customEmojiId", "custom_emoji_id", "editDate", "edit_date", "date")) continue
                                if (f.type != Long::class.java && f.type != Int::class.java) continue
                                try {
                                    f.isAccessible = true
                                    val candidate = when (val v = f.get(info)) {
                                        is Long -> v
                                        is Int  -> v.toLong()
                                        else    -> 0L
                                    }
                                    if (candidate != 0L) {
                                        resolved = candidate
                                        break
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                        resolved
                    }

                    if (threadId != 0L) {
                        topics.add(
                            TelegramTopicEntity(
                                id = "${chatId}_${threadId}",
                                chatId = chatId,
                                threadId = threadId,
                                name = info.name,
                                iconEmoji = if (emojiId != 0L) emojiId.toString() else null
                            )
                        )
                    }

                    offsetDate = info.creationDate

                    var tmId = 0L
                    try {
                        val tf = topic.javaClass.getDeclaredField("topMessage")
                        tf.isAccessible = true
                        val m = tf.get(topic) as? TdApi.Message
                        if (m != null) tmId = m.id
                    } catch (e: Exception) {}

                    offsetMessageId = tmId
                    offsetForumTopicId = threadId.toInt()
                }

                if (result.topics.size < 100) break
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching forum topics for chat $chatId")
        }
        return topics
    }

    suspend fun getAudioMessagesByTopic(chatId: Long, threadId: Long): List<Song> {
        Timber.d("Fetching audio for topic threadId=$threadId in chat=$chatId")
        try {
            clientManager.sendRequest<TdApi.Ok>(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Timber.w("Failed to open chat: $chatId")
        }

        val allSongs = mutableListOf<Song>()
        var nextFromMessageId = 0L
        val batchSize = 100

        try {
            while (true) {
                val request = TdApi.SearchChatMessages().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.senderId = null
                    this.fromMessageId = nextFromMessageId
                    this.offset = 0
                    this.limit = batchSize
                    this.filter = TdApi.SearchMessagesFilterAudio()

                    var topicSet = false
                    try {
                        val f = this.javaClass.getDeclaredField("topicId")
                        f.isAccessible = true
                        f.set(this, TdApi.MessageTopicForum(threadId.toInt()))
                        topicSet = true
                    } catch (_: NoSuchFieldException) { }

                    if (!topicSet) {
                        try {
                            val f = this.javaClass.getDeclaredField("messageThreadId")
                            f.isAccessible = true
                            f.set(this, threadId)
                            topicSet = true
                        } catch (_: NoSuchFieldException) { }
                    }

                    if (!topicSet) {
                        Timber.e("SearchChatMessages: could not set topic filter")
                    }
                }

                val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)

                if (response.messages.isEmpty()) break

                response.messages.forEach { message ->
                    mapMessageToSong(message)?.let { allSongs.add(it) }
                }

                nextFromMessageId = response.nextFromMessageId
                if (nextFromMessageId == 0L) break
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching audio for topic $threadId in chat $chatId")
        }
        return allSongs
    }

    suspend fun getAudioMessages(chatId: Long): List<Song> {
        Timber.d("Fetching chat history for chat: $chatId")
        try {
            clientManager.sendRequest<TdApi.Ok>(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Timber.w("Failed to open chat: $chatId")
        }

        val allSongs = mutableListOf<Song>()
        var nextFromMessageId = 0L
        val batchSize = 100

        try {
            while (true) {
                val request = TdApi.SearchChatMessages().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.senderId = null
                    this.fromMessageId = nextFromMessageId
                    this.offset = 0
                    this.limit = batchSize
                    this.filter = TdApi.SearchMessagesFilterAudio()
                }

                val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)

                if (response.messages.isEmpty()) break

                response.messages.forEach { message ->
                    mapMessageToSong(message)?.let { allSongs.add(it) }
                }

                nextFromMessageId = response.nextFromMessageId
                if (nextFromMessageId == 0L) break
            }
            return allSongs
        } catch (e: Exception) {
            Timber.e(e, "Error fetching chat history for chat $chatId")
            return allSongs
        }
    }

    suspend fun getArchiveMessages(chatId: Long, threadId: Long? = null, offsetMessageId: Long = 0L, limit: Int = 100): Pair<List<TdApi.Message>, Long> {
        return try {
            val request = TdApi.SearchChatMessages().apply {
                this.chatId = chatId
                this.query = ""
                this.senderId = null
                this.fromMessageId = offsetMessageId
                this.offset = 0
                this.limit = limit
                this.filter = null // NO FILTER: We want all messages

                if (threadId != null) {
                    var topicSet = false
                    try {
                        val f = this.javaClass.getDeclaredField("topicId")
                        f.isAccessible = true
                        f.set(this, TdApi.MessageTopicForum(threadId.toInt()))
                        topicSet = true
                    } catch (_: NoSuchFieldException) { }

                    if (!topicSet) {
                        try {
                            val f = this.javaClass.getDeclaredField("messageThreadId")
                            f.isAccessible = true
                            f.set(this, threadId)
                            topicSet = true
                        } catch (_: NoSuchFieldException) { }
                    }
                }
            }

            val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)
            Pair(response.messages.toList(), response.nextFromMessageId)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching archive messages for chat $chatId (thread=$threadId)")
            Pair(emptyList(), 0L)
        }
    }

    suspend fun getMessage(chatId: Long, messageId: Long): TdApi.Message? {
        return try {
            clientManager.sendRequest<TdApi.Message>(TdApi.GetMessage(chatId, messageId))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun refreshMessage(chatId: Long, messageId: Long): TdApi.Message? {
        return getMessage(chatId, messageId)
    }

    private suspend fun mapMessageToSong(message: TdApi.Message): Song? {
        val content = message.content

        return when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                var albumArtPath: String? = null
                var thumbnail = audio.albumCoverThumbnail

                if (thumbnail == null && audio.externalAlbumCovers?.isNotEmpty() == true) {
                    thumbnail = audio.externalAlbumCovers.maxByOrNull { it.width * it.height }
                }

                if (thumbnail != null) {
                    albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                    if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                        downloadManager.putPathCache(thumbnail.file.id, thumbnail.file.local.path)
                    }
                }

                Song(
                    id = "${message.chatId}_${message.id}",
                    title = audio.title.takeIf { it.isNotEmpty() } ?: audio.fileName.substringBeforeLast('.').ifEmpty { "Unknown Title" },
                    artist = audio.performer.takeIf { it.isNotEmpty() } ?: "Unknown Artist",
                    artistId = -1,
                    album = "Telegram Stream",
                    albumId = -1,
                    path = "",
                    contentUriString = "telegram://${message.chatId}/${message.id}",
                    albumArtUriString = albumArtPath,
                    duration = audio.duration * 1000L,
                    telegramFileId = audio.audio.id,
                    telegramChatId = message.chatId,
                    mimeType = audio.mimeType,
                    bitrate = 0,
                    sampleRate = 0,
                    year = 0,
                    trackNumber = 0,
                    dateAdded = message.date.toLong(),
                    isFavorite = false
                )
            }
            is TdApi.MessageDocument -> {
                val document = content.document
                val isAudioMime = document.mimeType.startsWith("audio/") || document.mimeType == "application/ogg"
                val isAudioExtension = document.fileName.lowercase().run {
                    endsWith(".mp3") || endsWith(".flac") || endsWith(".wav") ||
                    endsWith(".m4a") || endsWith(".ogg") || endsWith(".aac")
                }

                if (isAudioMime || isAudioExtension) {
                    var albumArtPath: String? = null
                    val thumbnail = document.thumbnail
                    if (thumbnail != null) {
                        albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                        if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                            downloadManager.putPathCache(thumbnail.file.id, thumbnail.file.local.path)
                        }
                    }

                    Song(
                        id = "${message.chatId}_${message.id}",
                        title = document.fileName.substringBeforeLast('.').ifEmpty { "Unknown Title" },
                        artist = "Unknown Artist",
                        artistId = -1,
                        album = "Telegram Stream",
                        albumId = -1,
                        path = "",
                        contentUriString = "telegram://${message.chatId}/${message.id}",
                        albumArtUriString = albumArtPath,
                        duration = 0L,
                        telegramFileId = document.document.id,
                        telegramChatId = message.chatId,
                        mimeType = document.mimeType,
                        bitrate = 0,
                        sampleRate = 0,
                        year = 0,
                        trackNumber = 0,
                        dateAdded = message.date.toLong(),
                        isFavorite = false
                    )
                } else null
            }
            else -> null
        }
    }
}
