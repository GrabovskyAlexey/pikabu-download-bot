package com.pikabu.bot.service.download

import com.pikabu.bot.config.TelegramBotConfig
import com.pikabu.bot.domain.exception.DownloadException
import com.pikabu.bot.domain.model.QueueStatus
import com.pikabu.bot.domain.model.VideoPlatform
import com.pikabu.bot.domain.model.VideoPlatform.PIKABU
import com.pikabu.bot.domain.model.VideoPlatform.RUTUBE
import com.pikabu.bot.domain.model.VideoPlatform.VKVIDEO
import com.pikabu.bot.domain.model.VideoPlatform.YOUTUBE
import com.pikabu.bot.entity.DownloadQueueEntity
import com.pikabu.bot.service.cache.VideoCacheService
import com.pikabu.bot.service.metrics.MetricsService
import com.pikabu.bot.service.parser.VideoParserService
import com.pikabu.bot.service.queue.QueueService
import com.pikabu.bot.service.telegram.TelegramSenderService
import com.pikabu.bot.service.template.MessageTemplateService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class DownloadOrchestrator(
    private val streamingDownloader: StreamingDownloader,
    private val externalVideoDownloadService: ExternalVideoDownloadService,
    private val videoParserService: VideoParserService,
    private val queueService: QueueService,
    private val telegramSenderService: TelegramSenderService,
    private val videoCacheService: VideoCacheService,
    private val botConfig: TelegramBotConfig,
    private val metricsService: MetricsService,
    private val messageTemplateService: MessageTemplateService,
) {

    /**
     * Координирует процесс загрузки видео из очереди
     */
    suspend fun processDownload(queueEntity: DownloadQueueEntity) {
        val queueId = queueEntity.id ?: run {
            logger.error { "Queue entity has no ID" }
            return
        }

        try {
            logger.debug { "Starting download process for queue ID: $queueId, user: ${queueEntity.userId}" }

            // Увеличиваем счетчик активных загрузок
            metricsService.incrementActiveDownloads()

            // Обновляем статус на DOWNLOADING
            queueService.updateStatus(queueId, QueueStatus.DOWNLOADING)

            // Проверяем кэш еще раз (вдруг кто-то скачал пока мы ждали)
            val cachedFileId = videoCacheService.getFileId(queueEntity.videoUrl)
            val success: Boolean

            if (cachedFileId != null) {
                // Отправляем по кэшированному file_id
                logger.debug { "Using cached file_id for queue $queueId" }
                metricsService.recordCacheHit()

                // Формируем caption с размером из кэша
                val cacheEntry = videoCacheService.getCacheEntry(queueEntity.videoUrl)
                val caption = buildVideoCaption(queueEntity.videoTitle, cacheEntry?.fileSize)

                success = telegramSenderService.sendVideoByFileId(
                    chatId = queueEntity.userId,
                    fileId = cachedFileId,
                    caption = caption
                )
            } else {
                metricsService.recordCacheMiss()

                // Определяем тип видео по URL (внешнее или прямое)
                val isExternal = isExternalVideoUrl(queueEntity.videoUrl)
                val platform = detectPlatformFromUrl(queueEntity.videoUrl)

                val downloadResult = if (isExternal) {
                    // Внешнее видео - используем yt-dlp
                    logger.debug { "Downloading external video from $platform: ${queueEntity.videoUrl}" }

                    downloadExternalVideo(
                        videoUrl = queueEntity.videoUrl,
                        chatId = queueEntity.userId,
                        videoTitle = queueEntity.videoTitle,
                        platform = platform
                    )
                } else {
                    // Прямое видео - используем streaming downloader
                    logger.debug { "Downloading direct video: ${queueEntity.videoUrl}" }

                    streamingDownloader.downloadAndSend(
                        videoUrl = queueEntity.videoUrl,
                        chatId = queueEntity.userId,
                        videoTitle = queueEntity.videoTitle,
                        replyToMessageId = null
                    )
                }

                success = downloadResult.success

                // Сохраняем file_id в кэш если загрузка успешна
                if (downloadResult.success && downloadResult.fileId != null) {
                    videoCacheService.saveFileId(
                        videoUrl = queueEntity.videoUrl,
                        fileId = downloadResult.fileId,
                        fileSize = downloadResult.fileSize
                    )
                }
            }

            if (success) {
                // Записываем успешную загрузку в метрики
                metricsService.recordSuccessfulDownload()

                // Видео отправлено успешно - удаляем статусное сообщение
                telegramSenderService.deleteMessage(
                    chatId = queueEntity.userId,
                    messageId = queueEntity.messageId
                )

                // Успех - обновляем статус и архивируем
                queueService.updateStatus(queueId, QueueStatus.COMPLETED)

                // Получаем обновленную сущность для архивации
                val updatedEntity = queueService.getById(queueId)
                if (updatedEntity != null) {
                    queueService.archiveToHistory(updatedEntity)
                    logger.debug { "Download archived for queue ID: $queueId" }
                } else {
                    logger.warn { "Could not find queue entity $queueId for archiving" }
                }
            } else {
                handleDownloadFailure(queueEntity, "Failed to send video to Telegram")
            }

        } catch (e: DownloadException) {
            handleDownloadFailure(queueEntity, e.message ?: "Unknown download error")
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error in download orchestrator for queue ID: $queueId" }
            handleDownloadFailure(queueEntity, "Unexpected error: ${e.message}")
        } finally {
            // Уменьшаем счетчик активных загрузок
            metricsService.decrementActiveDownloads()
        }
    }

    /**
     * Обрабатывает ошибку загрузки
     */
    private fun handleDownloadFailure(queueEntity: DownloadQueueEntity, errorMessage: String) {
        val queueId = queueEntity.id ?: return

        logger.error { "Download failed for queue ID $queueId: $errorMessage" }
        metricsService.recordFailedDownload()

        // Обновляем статус на FAILED
        queueService.updateStatus(queueId, QueueStatus.FAILED)

        // Архивируем с ошибкой
        val failedEntity = queueService.getById(queueId)
        if (failedEntity != null) {
            queueService.archiveToHistory(failedEntity)
        }

        // Уведомляем пользователя понятным сообщением (без технических деталей)
        val userFriendlyMessage = translateErrorToUserMessage(errorMessage)
        telegramSenderService.sendMessage(queueEntity.userId, userFriendlyMessage)
    }

    /**
     * Переводит техническую ошибку в понятное пользователю сообщение
     */
    private fun translateErrorToUserMessage(technicalError: String): String {
        return when {
            // Ошибки размера
            technicalError.contains("exceeds size limit", ignoreCase = true) ||
            technicalError.contains("too large", ignoreCase = true) -> {
                "❌ Видео слишком большое (лимит 500 МБ).\n\n" +
                "Попробуйте другое видео."
            }

            // Таймаут
            technicalError.contains("timeout", ignoreCase = true) ||
            technicalError.contains("timed out", ignoreCase = true) -> {
                "❌ Превышено время ожидания загрузки.\n\n" +
                "Видео слишком долго загружается. Попробуйте позже."
            }

            // Недоступность видео
            technicalError.contains("not available", ignoreCase = true) ||
            technicalError.contains("unavailable", ignoreCase = true) ||
            technicalError.contains("removed", ignoreCase = true) ||
            technicalError.contains("deleted", ignoreCase = true) -> {
                "❌ Видео недоступно.\n\n" +
                "Возможно, оно было удалено или ограничено автором."
            }

            // Ошибки сети
            technicalError.contains("network", ignoreCase = true) ||
            technicalError.contains("connection", ignoreCase = true) ||
            technicalError.contains("failed to fetch", ignoreCase = true) -> {
                "❌ Ошибка сети при загрузке видео.\n\n" +
                "Попробуйте ещё раз через несколько минут."
            }

            // Ограничения платформы (geo-block, private, etc)
            technicalError.contains("geo", ignoreCase = true) ||
            technicalError.contains("region", ignoreCase = true) ||
            technicalError.contains("private", ignoreCase = true) ||
            technicalError.contains("restricted", ignoreCase = true) -> {
                "❌ Видео недоступно для загрузки.\n\n" +
                "Возможно, оно приватное или ограничено по региону."
            }

            // Ошибки отправки в Telegram
            technicalError.contains("failed to send", ignoreCase = true) ||
            technicalError.contains("telegram", ignoreCase = true) -> {
                "❌ Не удалось отправить видео в Telegram.\n\n" +
                "Попробуйте позже или отправьте другую ссылку."
            }

            // Общая ошибка (без технических деталей)
            else -> {
                "❌ Не удалось загрузить видео.\n\n" +
                "Попробуйте позже или отправьте другую ссылку."
            }
        }
    }

    /**
     * Проверяет является ли URL внешним видео
     */
    private fun isExternalVideoUrl(url: String): Boolean {
        return url.contains("youtube.com") ||
               url.contains("youtu.be") ||
               url.contains("rutube.ru") ||
               url.contains("vk.com") ||
               url.contains("vk.ru")
    }

    /**
     * Определяет платформу видео по URL
     */
    private fun detectPlatformFromUrl(url: String): VideoPlatform {
        return when {
            url.contains("youtube.com") || url.contains("youtu.be") ->
                YOUTUBE
            url.contains("rutube.ru") ->
                RUTUBE
            url.contains("vk.com") || url.contains("vk.ru") ->
                VKVIDEO
            else ->
                PIKABU
        }
    }

    /**
     * Загружает внешнее видео через yt-dlp и отправляет в Telegram
     */
    private suspend fun downloadExternalVideo(
        videoUrl: String,
        chatId: Long,
        videoTitle: String?,
        platform: VideoPlatform
    ): StreamingDownloader.SendResult {
        val tempFile = kotlin.io.path.createTempFile("pikabu_external", ".mp4").toFile()

        return try {
            // Скачиваем видео через yt-dlp
            externalVideoDownloadService.downloadExternalVideo(videoUrl, tempFile, platform)

            // Формируем caption
            val caption = buildVideoCaption(videoTitle, tempFile.length())

            // Отправляем в Telegram
            val fileId = telegramSenderService.sendVideo(
                chatId = chatId,
                videoFile = tempFile,
                caption = caption,
                replyToMessageId = null
            )

            val success = fileId != null

            StreamingDownloader.SendResult(
                success = success,
                fileId = fileId,
                fileSize = tempFile.length()
            )
        } finally {
            // Удаляем временный файл
            if (tempFile.exists()) {
                tempFile.delete()
                logger.debug { "Deleted temporary file: ${tempFile.absolutePath}" }
            }
        }
    }

    /**
     * Формирует caption для видео
     */
    private fun buildVideoCaption(videoTitle: String?, fileSize: Long?): String {
        val nonNullableSize = fileSize ?: 0
        return messageTemplateService.renderMessage("cached-video-caption.ftl", mapOf(
            "videoTitle" to videoTitle,
            "fileSize" to nonNullableSize,
            "botUsername" to botConfig.username
        ))
    }
}
