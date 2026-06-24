package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.database.ArchiveMediaEntity
import com.theveloper.pixelplay.data.database.ArchiveMessageEntity
import org.drinkless.tdlib.TdApi

object ArchiveMappers {

    fun mapToMessageEntity(message: TdApi.Message): ArchiveMessageEntity {
        // Resolve thread ID dynamically using the fallback logic similar to topic parsing
        var threadId: Long? = null
        try {
            // First try newer API messageThreadId
            val threadIdField = message.javaClass.getDeclaredField("messageThreadId")
            threadIdField.isAccessible = true
            val threadVal = threadIdField.get(message) as? Long
            if (threadVal != null && threadVal != 0L) {
                threadId = threadVal
            }
        } catch (e: Exception) {
            // Fallback for very old TDLib builds where messageThreadId isn't on Message.
        }

        return ArchiveMessageEntity(
            id = "${message.chatId}_${message.id}",
            chatId = message.chatId,
            threadId = threadId,
            messageId = message.id,
            timestamp = message.date.toLong()
        )
    }

    fun mapToMediaEntity(message: TdApi.Message): ArchiveMediaEntity? {
        val content = message.content
        val messageId = "${message.chatId}_${message.id}"

        return when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                ArchiveMediaEntity(
                    id = "${message.chatId}_${audio.audio.id}",
                    messageId = messageId,
                    fileId = audio.audio.id,
                    fileName = audio.fileName.ifEmpty { "Unknown Audio" },
                    caption = getCaption(content),
                    mimeType = audio.mimeType.ifEmpty { "audio/mpeg" },
                    fileExtension = getExtension(audio.fileName, "mp3"),
                    size = audio.audio.size.toLong()
                )
            }
            is TdApi.MessageDocument -> {
                val doc = content.document
                ArchiveMediaEntity(
                    id = "${message.chatId}_${doc.document.id}",
                    messageId = messageId,
                    fileId = doc.document.id,
                    fileName = doc.fileName.ifEmpty { "Unknown Document" },
                    caption = getCaption(content),
                    mimeType = doc.mimeType.ifEmpty { "application/octet-stream" },
                    fileExtension = getExtension(doc.fileName, "bin"),
                    size = doc.document.size.toLong()
                )
            }
            is TdApi.MessageVideo -> {
                val video = content.video
                ArchiveMediaEntity(
                    id = "${message.chatId}_${video.video.id}",
                    messageId = messageId,
                    fileId = video.video.id,
                    fileName = video.fileName.ifEmpty { "Video_${message.id}.mp4" },
                    caption = getCaption(content),
                    mimeType = video.mimeType.ifEmpty { "video/mp4" },
                    fileExtension = getExtension(video.fileName, "mp4"),
                    size = video.video.size.toLong()
                )
            }
            is TdApi.MessagePhoto -> {
                val photo = content.photo
                val bestSize = photo.sizes.maxByOrNull { it.width * it.height }
                if (bestSize == null) null
                else ArchiveMediaEntity(
                    id = "${message.chatId}_${bestSize.photo.id}",
                    messageId = messageId,
                    fileId = bestSize.photo.id,
                    fileName = "Photo_${message.id}.jpg",
                    caption = getCaption(content),
                    mimeType = "image/jpeg",
                    fileExtension = "jpg",
                    size = bestSize.photo.size.toLong()
                )
            }
            is TdApi.MessageAnimation -> {
                val anim = content.animation
                ArchiveMediaEntity(
                    id = "${message.chatId}_${anim.animation.id}",
                    messageId = messageId,
                    fileId = anim.animation.id,
                    fileName = anim.fileName.ifEmpty { "Animation_${message.id}.mp4" },
                    caption = getCaption(content),
                    mimeType = anim.mimeType.ifEmpty { "video/mp4" },
                    fileExtension = getExtension(anim.fileName, "mp4"),
                    size = anim.animation.size.toLong()
                )
            }
            is TdApi.MessageVoiceNote -> {
                val voice = content.voiceNote
                ArchiveMediaEntity(
                    id = "${message.chatId}_${voice.voice.id}",
                    messageId = messageId,
                    fileId = voice.voice.id,
                    fileName = "VoiceNote_${message.id}.ogg",
                    caption = getCaption(content),
                    mimeType = voice.mimeType.ifEmpty { "audio/ogg" },
                    fileExtension = "ogg",
                    size = voice.voice.size.toLong()
                )
            }
            is TdApi.MessageSticker -> {
                val sticker = content.sticker
                ArchiveMediaEntity(
                    id = "${message.chatId}_${sticker.sticker.id}",
                    messageId = messageId,
                    fileId = sticker.sticker.id,
                    fileName = "Sticker_${message.id}.webp",
                    caption = getCaption(content),
                    mimeType = "image/webp", // Might be tgs/webm too
                    fileExtension = "webp",
                    size = sticker.sticker.size.toLong()
                )
            }
            else -> null
        }
    }

    private fun getCaption(content: TdApi.MessageContent): String? {
        val captionObj = try {
            val f = content.javaClass.getDeclaredField("caption")
            f.isAccessible = true
            f.get(content) as? TdApi.FormattedText
        } catch (e: Exception) { null }
        return captionObj?.text?.takeIf { it.isNotBlank() }
    }

    private fun getExtension(fileName: String, default: String): String {
        return fileName.substringAfterLast('.', default).lowercase()
    }
}
