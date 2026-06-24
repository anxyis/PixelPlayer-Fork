package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.database.TelegramDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramDownloadManager @Inject constructor(
    private val clientManager: TelegramClientManager,
    private val dao: TelegramDao
) {
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = ConcurrentHashMap<Int, Deferred<String?>>()
    private val downloadSemaphore = Semaphore(4) // Max concurrent downloads

    // Thread-safe map for fileId -> local path
    private val resolvedPathCache = ConcurrentHashMap<Int, String>()

    private val _downloadCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val downloadCompleted: SharedFlow<Int> = _downloadCompleted.asSharedFlow()

    private val _songFileUpdated = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val songFileUpdated = _songFileUpdated.asSharedFlow()

    fun clearMemoryCache() {
        resolvedPathCache.clear()
        Timber.d("TelegramDownloadManager: Memory cache cleared")
    }

    suspend fun downloadFile(fileId: Int, priority: Int = 1): TdApi.File? {
        return try {
            clientManager.sendRequest(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        } catch (e: Exception) {
            Timber.w("Failed to start file download: $fileId")
            null
        }
    }

    suspend fun getFile(fileId: Int): TdApi.File? {
        return try {
            clientManager.sendRequest(TdApi.GetFile(fileId))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun isFileCached(fileId: Int): Boolean {
        if (resolvedPathCache.containsKey(fileId)) return true
        val file = getFile(fileId)
        val isCompleted = file?.local?.isDownloadingCompleted == true && file.local.path.isNotEmpty()
        if (isCompleted) {
            resolvedPathCache[fileId] = file!!.local.path
        }
        return isCompleted
    }

    suspend fun checkPathCache(fileId: Int): String? {
        return resolvedPathCache[fileId]
    }

    fun putPathCache(fileId: Int, path: String) {
        resolvedPathCache[fileId] = path
    }

    suspend fun resolveTelegramUri(uriString: String, getMessageFunc: suspend (Long, Long) -> TdApi.Message?): Pair<Int, Long>? {
        if (!uriString.startsWith("telegram://")) return null

        val parts = uriString.substringAfter("telegram://").split("/")
        if (parts.size != 2) return null

        val chatId = parts[0].toLongOrNull() ?: return null
        val messageId = parts[1].toLongOrNull() ?: return null

        val message = getMessageFunc(chatId, messageId) ?: return null
        val content = message.content

        if (content is TdApi.MessageAudio) {
            return Pair(content.audio.audio.id, content.audio.audio.size)
        } else if (content is TdApi.MessageDocument) {
            return Pair(content.document.document.id, content.document.document.size)
        }
        return null
    }

    fun preResolveTelegramUri(uriString: String, getMessageFunc: suspend (Long, Long) -> TdApi.Message?) {
        downloadScope.launch {
            val resolved = resolveTelegramUri(uriString, getMessageFunc)
            val fileId = resolved?.first ?: return@launch

            if (resolvedPathCache.containsKey(fileId)) return@launch

            val existingFile = getFile(fileId)
            if (existingFile?.local?.isDownloadingCompleted == true && existingFile.local.path.isNotEmpty()) {
                resolvedPathCache[fileId] = existingFile.local.path
            }
        }
    }

    private suspend fun persistSongFilePathIfNeeded(fileId: Int, path: String?) {
        if (path.isNullOrBlank()) return

        val existingSong = dao.getSongByFileId(fileId) ?: return
        if (existingSong.filePath == path) return

        dao.insertSongs(listOf(existingSong.copy(filePath = path)))
        _songFileUpdated.tryEmit(existingSong.id)
    }

    suspend fun downloadFileAwait(fileId: Int, priority: Int = 1): String? {
        resolvedPathCache[fileId]?.let { path ->
            if (java.io.File(path).exists()) return path
            resolvedPathCache.remove(fileId)
        }

        val existingJob = activeDownloads[fileId]
        if (existingJob != null && existingJob.isActive) return existingJob.await()

        val newJob = downloadScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                downloadSemaphore.withPermit {
                    val currentFile = getFile(fileId)
                    if (currentFile?.local?.isDownloadingCompleted == true) {
                        currentFile.local.path.takeIf { it.isNotEmpty() }?.let {
                            resolvedPathCache[fileId] = it
                            persistSongFilePathIfNeeded(fileId, it)
                            _downloadCompleted.tryEmit(fileId)
                            return@withPermit it
                        }
                    }

                    val initialFile = getFile(fileId)
                    val isSmallFile = initialFile?.size == 0L || (initialFile?.size ?: 0) < 1024 * 1024

                    if (isSmallFile) {
                        return@withPermit try {
                            val resultFile = withTimeout(15_000L) {
                                clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, true))
                            }
                            if (resultFile.local.isDownloadingCompleted && resultFile.local.path.isNotEmpty()) {
                                resolvedPathCache[fileId] = resultFile.local.path
                                persistSongFilePathIfNeeded(fileId, resultFile.local.path)
                                _downloadCompleted.tryEmit(fileId)
                                resultFile.local.path
                            } else null
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (e.message?.contains("canceled") != true && e.message?.contains("has failed") != true) {
                                Timber.w("Sync download failed for $fileId: ${e.message}")
                            }
                            null
                        }
                    }

                    try {
                        clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, false))
                    } catch (e: Exception) {
                        Timber.w("Async download request failed for $fileId: ${e.message}")
                        return@withPermit null
                    }

                    val completedPath = withTimeoutOrNull(60_000L) {
                        clientManager.updates
                            .filterIsInstance<TdApi.UpdateFile>()
                            .filter { it.file.id == fileId }
                            .first { update ->
                                val file = update.file
                                when {
                                    file.local.isDownloadingCompleted && file.local.path.isNotEmpty() -> true
                                    !file.local.canBeDownloaded -> throw Exception("File cannot be downloaded")
                                    else -> false
                                }
                            }
                            .file.local.path
                    }

                    if (completedPath != null) {
                        resolvedPathCache[fileId] = completedPath
                        persistSongFilePathIfNeeded(fileId, completedPath)
                        _downloadCompleted.tryEmit(fileId)
                        return@withPermit completedPath
                    }

                    val finalFile = getFile(fileId)
                    return@withPermit if (finalFile?.local?.isDownloadingCompleted == true && finalFile.local.path.isNotEmpty()) {
                        persistSongFilePathIfNeeded(fileId, finalFile.local.path)
                        _downloadCompleted.tryEmit(fileId)
                        finalFile.local.path
                    } else null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("downloadFileAwait error for $fileId: ${e.message}")
                throw e
            } finally {
                activeDownloads.remove(fileId)
            }
        }

        activeDownloads[fileId] = newJob
        return try {
            newJob.start()
            newJob.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            newJob.cancel(e)
            throw e
        }
    }
}
