package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "archive_download_state")
data class ArchiveDownloadStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_id") val fileId: Int,
    @ColumnInfo(name = "status") val status: String, // QUEUED, DOWNLOADING, DONE, FAILED
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long = 0,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long = 0
)
