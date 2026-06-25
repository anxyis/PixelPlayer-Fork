package com.theveloper.pixelplay.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.database.ArchiveDao
import com.theveloper.pixelplay.data.database.ArchiveDownloadStateEntity
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.os.Environment
import com.theveloper.pixelplay.data.telegram.TelegramClientManager
import kotlinx.coroutines.launch

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val telegramRepository: TelegramRepository,
    private val clientManager: TelegramClientManager,
    private val archiveDao: ArchiveDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fileId = inputData.getInt(KEY_FILE_ID, -1)
        if (fileId == -1) {
            return Result.failure()
        }

        try {
            // Update state to DOWNLOADING
            archiveDao.insertDownloadState(
                ArchiveDownloadStateEntity(
                    fileId = fileId,
                    status = "DOWNLOADING"
                )
            )

            // Start a job to track progress
            val progressJob = applicationContext.let { ctx ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    clientManager.updates
                        .filterIsInstance<TdApi.UpdateFile>()
                        .filter { it.file.id == fileId }
                        .collect { update ->
                            archiveDao.insertDownloadState(
                                ArchiveDownloadStateEntity(
                                    fileId = fileId,
                                    status = "DOWNLOADING",
                                    downloadedBytes = update.file.local.downloadedSize.toLong(),
                                    totalBytes = update.file.expectedSize.toLong().takeIf { it > 0 } ?: 0L
                                )
                            )
                        }
                }
            }

            // Await the download completion via the existing manager
            val path = telegramRepository.downloadFileAwait(fileId, priority = 16)
            progressJob.cancel()

            if (path == null) {
                Timber.e("Download failed for fileId=$fileId: path is null")
                archiveDao.insertDownloadState(
                    ArchiveDownloadStateEntity(
                        fileId = fileId,
                        status = "FAILED"
                    )
                )
                return Result.retry() // Or failure, depending on your retry policy
            }

            // Copy the file to public Downloads directory
            val sourceFile = File(path)
            if (sourceFile.exists()) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val archiveDir = File(downloadsDir, "TelegramArchive")
                if (!archiveDir.exists()) archiveDir.mkdirs()

                // Try to get original file name if available via TdApi.GetFile, but we just use sourceFile.name for now
                val destFile = File(archiveDir, sourceFile.name)
                
                if (!destFile.exists()) {
                    FileInputStream(sourceFile).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                Timber.d("Copied file $fileId to ${destFile.absolutePath}")
            }

            archiveDao.insertDownloadState(
                ArchiveDownloadStateEntity(
                    fileId = fileId,
                    status = "DONE",
                    downloadedBytes = sourceFile.length(),
                    totalBytes = sourceFile.length()
                )
            )
            return Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Error downloading fileId=$fileId")
            archiveDao.insertDownloadState(
                ArchiveDownloadStateEntity(
                    fileId = fileId,
                    status = "FAILED"
                )
            )
            return Result.retry()
        }
    }

    companion object {
        const val KEY_FILE_ID = "file_id"
    }
}
