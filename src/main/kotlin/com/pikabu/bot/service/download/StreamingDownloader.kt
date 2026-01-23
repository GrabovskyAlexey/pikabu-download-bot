package com.pikabu.bot.service.download

import com.pikabu.bot.config.TelegramBotConfig
import com.pikabu.bot.domain.exception.DownloadException
import com.pikabu.bot.service.telegram.TelegramSenderService
import com.pikabu.bot.service.template.MessageTemplateService
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
    private val messageTemplateService: MessageTemplateService,
    private val botConfig: TelegramBotConfig
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

            logger.debug { "Created temp file: ${tempFile.absolutePath}" }

            // Загружаем видео
            val downloadResult = videoDownloadService.downloadVideo(videoUrl, tempFile)

            logger.debug { "Video downloaded: ${downloadResult.sizeBytes} bytes, sending to user $chatId" }

            // Отправляем в Telegram
            val caption = buildCaption(videoTitle, downloadResult)
            val fileId = telegramSenderService.sendVideo(
                chatId = chatId,
                videoFile = tempFile,
                caption = caption,
                replyToMessageId = replyToMessageId
            )

            if (fileId != null) {
                logger.info { "Video sent to user $chatId (${downloadResult.sizeBytes} bytes)" }
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
    private fun buildCaption(videoTitle: String?, downloadResult: DownloadResult): String {
        return messageTemplateService.renderMessage("cached-video-caption.ftl", mapOf(
            "videoTitle" to videoTitle,
            "fileSize" to downloadResult.sizeBytes,
            "botUsername" to botConfig.username
        ))
    }
}
