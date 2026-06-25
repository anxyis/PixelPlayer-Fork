package com.theveloper.pixelplay.presentation.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.ArchiveChannelEntity
import com.theveloper.pixelplay.data.database.ArchiveDao
import com.theveloper.pixelplay.data.database.ArchiveDownloadStateEntity
import com.theveloper.pixelplay.data.database.ArchiveMediaEntity
import com.theveloper.pixelplay.data.database.ArchiveTopicEntity
import com.theveloper.pixelplay.data.telegram.TelegramDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import android.content.Context

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val archiveDao: ArchiveDao,
    private val downloadManager: TelegramDownloadManager
) : ViewModel() {

    fun getChannels(): Flow<List<ArchiveChannelEntity>> {
        return archiveDao.getAllChannels()
    }

    fun getTopics(channelId: Long): Flow<List<ArchiveTopicEntity>> {
        return archiveDao.getTopicsForChannel(channelId)
    }

    fun getMedia(topicId: Long): Flow<List<ArchiveMediaEntity>> {
        return archiveDao.getMediaForTopic(topicId)
    }

    fun getDownloadState(fileId: Int): Flow<ArchiveDownloadStateEntity?> {
        return archiveDao.getDownloadState(fileId)
    }

    fun getActiveDownloads(): Flow<List<ArchiveDownloadStateEntity>> {
        return archiveDao.getActiveDownloads()
    }

    fun enqueueDownload(context: Context, fileId: Int) {
        downloadManager.enqueueWorkManagerDownload(context, fileId)
    }
}
