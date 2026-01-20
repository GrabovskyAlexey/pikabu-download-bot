package com.pikabu.bot.service.download

import com.pikabu.bot.domain.exception.DownloadException
import com.pikabu.bot.service.telegram.TelegramSenderService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

private val logger = KotlinLogging.logger {}

@Service
class StreamingDownloader(
    private val videoDownloadService: VideoDownloadService,
    private val telegramSenderService: TelegramSenderService,
    private val botConfig: com.pikabu.bot.config.TelegramBotConfig
) {

    /**
     * Загружает видео и отправляет в Telegram с автоматической очисткой
     */
    data class SendResult(
        val success: Boolean,
        val fileId: String? = null,
        val fileSize: Long? = null
    )

    suspend fun downloadAndSend(
        videoUrl: String,
        chatId: Long,
        videoTitle: String?,
        replyToMessageId: Int? = null
    ): SendResult {
        var tempFile: File? = null

        try {
            // Создаем временный файл
            tempFile = withContext(Dispatchers.IO) {
                Files.createTempFile("pikabu_video_", ".mp4").toFile()
            }

            logger.info { "Created temp file: ${tempFile.absolutePath}" }

            // Загружаем видео
            val downloadResult = videoDownloadService.downloadVideo(videoUrl, tempFile)

            logger.info { "Video downloaded: ${downloadResult.sizeBytes} bytes, sending to user $chatId" }

            // Отправляем в Telegram
            val caption = buildCaption(videoTitle, downloadResult)
            val fileId = telegramSenderService.sendVideo(
                chatId = chatId,
                videoFile = tempFile,
                caption = caption,
                replyToMessageId = replyToMessageId
            )

            if (fileId != null) {
                logger.info { "Video sent successfully to user $chatId, file_id: $fileId" }
                return SendResult(
                    success = true,
                    fileId = fileId,
                    fileSize = downloadResult.sizeBytes
                )
            } else {
                logger.error { "Failed to send video to user $chatId" }
                return SendResult(success = false)
            }

        } catch (e: Exception) {
            logger.error(e) { "Error in downloadAndSend for user $chatId: $videoUrl" }
            return SendResult(success = false)
        } finally {
            // Гарантированно удаляем временный файл
            tempFile?.let { file ->
                try {
                    val deleted = withContext(Dispatchers.IO) {
                        file.toPath().deleteIfExists()
                    }
                    if (deleted) {
                        logger.debug { "Temp file deleted: ${file.absolutePath}" }
                    } else {
                        logger.warn { "Temp file not found for deletion: ${file.absolutePath}" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to delete temp file: ${file.absolutePath}" }
                }
            }
        }
    }

    /**
     * Формирует caption для видео
     */
    private fun buildCaption(videoTitle: String?, downloadResult: com.pikabu.bot.service.download.DownloadResult): String {
        val sizeMb = downloadResult.sizeBytes / (1024.0 * 1024.0)
        val durationSec = downloadResult.durationMs / 1000.0

        return buildString {
            if (videoTitle != null) {
                append("📹 $videoTitle\n\n")
            }
            append("✅ Загружено: %.2f МБ".format(sizeMb))
            if (durationSec > 1) {
                append(" (%.1f сек)".format(durationSec))
            }
            append("\n\nСпасибо что воспользовались @${botConfig.username}")
        }
    }
}
