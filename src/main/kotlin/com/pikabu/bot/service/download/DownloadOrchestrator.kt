package com.pikabu.bot.service.download

import com.pikabu.bot.domain.exception.DownloadException
import com.pikabu.bot.domain.model.QueueStatus
import com.pikabu.bot.entity.DownloadQueueEntity
import com.pikabu.bot.service.cache.VideoCacheService
import com.pikabu.bot.service.queue.QueueService
import com.pikabu.bot.service.telegram.TelegramSenderService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class DownloadOrchestrator(
    private val streamingDownloader: StreamingDownloader,
    private val queueService: QueueService,
    private val telegramSenderService: TelegramSenderService,
    private val videoCacheService: VideoCacheService,
    private val botConfig: com.pikabu.bot.config.TelegramBotConfig,
    private val metricsService: com.pikabu.bot.service.metrics.MetricsService
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
                val caption = buildCachedVideoCaption(queueEntity.videoTitle, cacheEntry?.fileSize)

                success = telegramSenderService.sendVideoByFileId(
                    chatId = queueEntity.userId,
                    fileId = cachedFileId,
                    caption = caption
                )
            } else {
                metricsService.recordCacheMiss()
                // Кэша нет - загружаем и отправляем
                val downloadResult = streamingDownloader.downloadAndSend(
                    videoUrl = queueEntity.videoUrl,
                    chatId = queueEntity.userId,
                    videoTitle = queueEntity.videoTitle,
                    replyToMessageId = null
                )

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

        // Уведомляем пользователя
        val userMessage = buildString {
            append("❌ Не удалось загрузить видео.\n\n")
            append("Причина: $errorMessage\n\n")
            append("Попробуйте позже или отправьте другую ссылку.")
        }

        telegramSenderService.sendMessage(queueEntity.userId, userMessage)
    }

    /**
     * Формирует caption для кэшированного видео
     */
    private fun buildCachedVideoCaption(videoTitle: String?, fileSize: Long?): String {
        return buildString {
            if (videoTitle != null) {
                append("📹 $videoTitle\n\n")
            }
            if (fileSize != null) {
                val sizeMb = fileSize / (1024.0 * 1024.0)
                append("✅ Загружено: %.2f МБ\n\n".format(sizeMb))
            } else {
                append("✅ Видео загружено\n\n")
            }
            append("Спасибо что воспользовались @${botConfig.username}")
        }
    }
}
