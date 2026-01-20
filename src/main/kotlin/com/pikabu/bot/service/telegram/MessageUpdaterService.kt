package com.pikabu.bot.service.telegram

import com.pikabu.bot.domain.model.QueueStatus
import com.pikabu.bot.service.queue.QueueService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class MessageUpdaterService(
    private val queueService: QueueService,
    private val telegramSenderService: TelegramSenderService
) {

    /**
     * Обновляет сообщения о статусе в очереди каждые 7 секунд
     */
    @Scheduled(fixedDelay = 7000)
    fun updateQueueMessages() {
        try {
            // Обновляем сообщения для QUEUED запросов
            updateQueuedMessages()

            // Обновляем сообщения для DOWNLOADING запросов
            updateDownloadingMessages()

        } catch (e: Exception) {
            logger.error(e) { "Error updating queue messages" }
        }
    }

    /**
     * Обновляет сообщения для запросов в очереди
     */
    private fun updateQueuedMessages() {
        val queuedRequests = queueService.getAllQueuedRequests()

        queuedRequests.forEach { entity ->
            try {
                val position = entity.position ?: 0
                val message = buildQueuePositionMessage(position)

                val success = telegramSenderService.editMessageText(
                    chatId = entity.userId,
                    messageId = entity.messageId,
                    text = message
                )

                if (success) {
                    logger.debug { "Updated queue message for user ${entity.userId}, position $position" }
                } else {
                    logger.debug { "Failed to update message for user ${entity.userId} (may be already deleted)" }
                }

            } catch (e: Exception) {
                logger.error(e) { "Error updating message for queue ID: ${entity.id}" }
            }
        }

        if (queuedRequests.isNotEmpty()) {
            logger.debug { "Updated ${queuedRequests.size} queued messages" }
        }
    }

    /**
     * Обновляет сообщения для загружающихся видео
     */
    private fun updateDownloadingMessages() {
        val downloadingRequests = queueService.getDownloadingRequests()

        downloadingRequests.forEach { entity ->
            try {
                val message = buildDownloadingMessage(entity.videoTitle)

                val success = telegramSenderService.editMessageText(
                    chatId = entity.userId,
                    messageId = entity.messageId,
                    text = message
                )

                if (success) {
                    logger.debug { "Updated downloading message for user ${entity.userId}" }
                }

            } catch (e: Exception) {
                logger.error(e) { "Error updating downloading message for queue ID: ${entity.id}" }
            }
        }

        if (downloadingRequests.isNotEmpty()) {
            logger.debug { "Updated ${downloadingRequests.size} downloading messages" }
        }
    }

    /**
     * Формирует сообщение о позиции в очереди
     */
    private fun buildQueuePositionMessage(position: Int): String {
        return when {
            position <= 1 -> "⏳ Ваш запрос следующий в очереди..."
            position <= 3 -> "⏳ Ваш запрос в очереди, позиция: $position\n\nСкоро начнётся загрузка."
            else -> "⏳ Ваш запрос в очереди, позиция: $position"
        }
    }

    /**
     * Формирует сообщение о загрузке
     */
    private fun buildDownloadingMessage(videoTitle: String?): String {
        return if (videoTitle != null) {
            "⬇️ Загружается видео: $videoTitle\n\nПожалуйста, подождите..."
        } else {
            "⬇️ Загружается видео...\n\nПожалуйста, подождите..."
        }
    }

    /**
     * Отправляет начальное сообщение о добавлении в очередь
     */
    fun sendQueueAddedMessage(userId: Long, position: Int): Int? {
        val message = if (position == 1) {
            "✅ Видео добавлено в очередь.\n\n⏳ Загрузка начнётся сейчас..."
        } else {
            "✅ Видео добавлено в очередь.\n\n⏳ Позиция в очереди: $position"
        }

        return telegramSenderService.sendMessage(userId, message)
    }
}
