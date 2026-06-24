package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.database.TelegramSongEntity
import com.theveloper.pixelplay.data.database.TelegramTopicEntity
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramRepository @Inject constructor(
    private val clientManager: TelegramClientManager,
    private val authManager: TelegramAuthManager,
    private val chatManager: TelegramChatManager,
    private val downloadManager: TelegramDownloadManager,
    private val playlistManager: TelegramPlaylistManager
) {
    val authorizationState: Flow<TdApi.AuthorizationState?> = authManager.authorizationState
    val authErrors: SharedFlow<TdApi.Error> = authManager.authErrors
    val downloadCompleted: SharedFlow<Int> = downloadManager.downloadCompleted
    val songFileUpdated: SharedFlow<String> = downloadManager.songFileUpdated

    fun clearMemoryCache() {
        downloadManager.clearMemoryCache()
    }

    fun isReady(): Boolean = clientManager.isReady()

    suspend fun awaitReady(timeoutMs: Long = 30_000L): Boolean = clientManager.awaitReady(timeoutMs)

    fun sendPhoneNumber(phoneNumber: String) = authManager.sendPhoneNumber(phoneNumber)

    suspend fun sendPhoneNumberAwait(phoneNumber: String, timeoutMs: Long = 20_000L): Result<Unit> =
        authManager.sendPhoneNumberAwait(phoneNumber, timeoutMs)

    fun checkAuthenticationCode(code: String) = authManager.checkAuthenticationCode(code)

    suspend fun checkAuthenticationCodeAwait(code: String, timeoutMs: Long = 20_000L): Result<Unit> =
        authManager.checkAuthenticationCodeAwait(code, timeoutMs)

    fun checkAuthenticationPassword(password: String) = authManager.checkAuthenticationPassword(password)

    suspend fun checkAuthenticationPasswordAwait(password: String, timeoutMs: Long = 20_000L): Result<Unit> =
        authManager.checkAuthenticationPasswordAwait(password, timeoutMs)

    fun logout() = authManager.logout()

    suspend fun searchPublicChat(username: String): TdApi.Chat? = chatManager.searchPublicChat(username)

    suspend fun isForum(chatId: Long): Boolean = chatManager.isForum(chatId)

    suspend fun getForumTopics(chatId: Long): List<TelegramTopicEntity> = chatManager.getForumTopics(chatId)

    suspend fun getAudioMessagesByTopic(chatId: Long, threadId: Long): List<Song> = chatManager.getAudioMessagesByTopic(chatId, threadId)

    suspend fun getAudioMessages(chatId: Long): List<Song> = chatManager.getAudioMessages(chatId)

    suspend fun downloadFile(fileId: Int, priority: Int = 1): TdApi.File? = downloadManager.downloadFile(fileId, priority)

    suspend fun getFile(fileId: Int): TdApi.File? = downloadManager.getFile(fileId)

    suspend fun getMessage(chatId: Long, messageId: Long): TdApi.Message? = chatManager.getMessage(chatId, messageId)

    suspend fun isFileCached(fileId: Int): Boolean = downloadManager.isFileCached(fileId)

    suspend fun resolveTelegramUri(uriString: String): Pair<Int, Long>? = downloadManager.resolveTelegramUri(uriString) { c, m -> getMessage(c, m) }

    fun preResolveTelegramUri(uriString: String) = downloadManager.preResolveTelegramUri(uriString) { c, m -> getMessage(c, m) }

    suspend fun refreshMessage(chatId: Long, messageId: Long): TdApi.Message? = chatManager.refreshMessage(chatId, messageId)

    fun warmUpArtworkForSongs(entities: List<TelegramSongEntity>, maxSongs: Int = 24) {
        // Obsolete in archive app, stubbed to prevent breaking PlayerViewModel
    }

    suspend fun downloadFileAwait(fileId: Int, priority: Int = 1): String? = downloadManager.downloadFileAwait(fileId, priority)

    suspend fun updateAppPlaylistForTelegramChannel(chatId: Long, channelTitle: String, telegramEntities: List<TelegramSongEntity>) =
        playlistManager.updateAppPlaylistForTelegramChannel(chatId, channelTitle, telegramEntities)

    suspend fun updateAppPlaylistForTopic(chatId: Long, threadId: Long, topicName: String, telegramEntities: List<TelegramSongEntity>) =
        playlistManager.updateAppPlaylistForTopic(chatId, threadId, topicName, telegramEntities)

    suspend fun deleteAppPlaylistForTelegramChannel(chatId: Long) = playlistManager.deleteAppPlaylistForTelegramChannel(chatId)

    suspend fun deleteAppPlaylistForTopic(chatId: Long, threadId: Long) = playlistManager.deleteAppPlaylistForTopic(chatId, threadId)

    suspend fun deleteAllTopicPlaylistsForChannel(chatId: Long) = playlistManager.deleteAllTopicPlaylistsForChannel(chatId)
}
