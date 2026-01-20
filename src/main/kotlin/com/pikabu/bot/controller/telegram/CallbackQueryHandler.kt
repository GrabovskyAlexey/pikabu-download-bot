package com.pikabu.bot.controller.telegram

import com.pikabu.bot.service.queue.QueueService
import com.pikabu.bot.service.telegram.MessageUpdaterService
import com.pikabu.bot.service.telegram.TelegramSenderService
import com.pikabu.bot.service.telegram.VideoSelectionCache
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.generics.TelegramClient

private val logger = KotlinLogging.logger {}

@Component
class CallbackQueryHandler(
    private val telegramClient: TelegramClient,
    private val telegramSenderService: TelegramSenderService,
    private val queueService: QueueService,
    private val messageUpdaterService: MessageUpdaterService,
    private val videoSelectionCache: VideoSelectionCache
) {

    fun handleCallbackQuery(callbackQuery: CallbackQuery) {
        val chatId = callbackQuery.message.chatId
        val data = callbackQuery.data
        val messageId = callbackQuery.message.messageId

        logger.info { "Received callback query from user $chatId: $data" }

        try {
            when {
                data.startsWith("video:") -> handleVideoSelection(chatId, data, messageId, callbackQuery.id)
                else -> {
                    logger.warn { "Unknown callback query data: $data" }
                    answerCallbackQuery(callbackQuery.id, "Неизвестная команда")
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling callback query: $data" }
            answerCallbackQuery(callbackQuery.id, "Произошла ошибка при обработке")
        }
    }

    private fun handleVideoSelection(chatId: Long, data: String, messageId: Int, callbackQueryId: String) {
        // Формат: video:<cache_id>:<index>
        val parts = data.split(":")

        if (parts.size != 3) {
            logger.error { "Invalid callback data format: $data" }
            answerCallbackQuery(callbackQueryId, "Ошибка: неверный формат данных")
            return
        }

        val cacheId = parts[1]
        val index = parts[2].toIntOrNull()

        if (index == null) {
            logger.error { "Invalid video index: ${parts[2]}" }
            answerCallbackQuery(callbackQueryId, "Ошибка: неверный индекс видео")
            return
        }

        // Получаем видео из кэша
        val cacheEntry = videoSelectionCache.get(cacheId)
        if (cacheEntry == null) {
            logger.warn { "Cache entry expired or not found: $cacheId" }
            answerCallbackQuery(callbackQueryId, "Ссылка устарела, отправьте URL заново")
            return
        }

        if (index < 0 || index >= cacheEntry.videos.size) {
            logger.error { "Video index out of range: $index, size: ${cacheEntry.videos.size}" }
            answerCallbackQuery(callbackQueryId, "Ошибка: неверный индекс видео")
            return
        }

        val video = cacheEntry.videos[index]
        logger.info { "User $chatId selected video #$index: ${video.url}" }

        try {
            answerCallbackQuery(callbackQueryId, "Добавляю в очередь...")

            // Удаляем сообщение с кнопками
            telegramSenderService.editMessageText(
                chatId,
                messageId,
                "✅ Видео выбрано, добавляю в очередь..."
            )

            // Добавляем в очередь
            val queueEntity = queueService.addToQueue(
                userId = chatId,
                messageId = messageId,
                videoUrl = video.url,
                videoTitle = video.title
            )

            // Обновляем сообщение с позицией в очереди
            val position = queueEntity.position ?: 1
            val message = if (position == 1) {
                "✅ Видео добавлено в очередь.\n\n⏳ Загрузка начнётся сейчас..."
            } else {
                "✅ Видео добавлено в очередь.\n\n⏳ Позиция в очереди: $position"
            }

            telegramSenderService.editMessageText(chatId, messageId, message)

            logger.info { "Video added to queue from callback for user $chatId: ${video.url}" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to add video to queue from callback for user $chatId" }
            answerCallbackQuery(callbackQueryId, "Ошибка при обработке")
            telegramSenderService.editMessageText(
                chatId,
                messageId,
                "❌ Ошибка при добавлении видео в очередь. Попробуйте позже."
            )
        }
    }

    private fun answerCallbackQuery(callbackQueryId: String, text: String) {
        try {
            val answer = AnswerCallbackQuery(callbackQueryId).apply {
                this.text = text
            }
            telegramClient.execute(answer)
        } catch (e: Exception) {
            logger.error(e) { "Failed to answer callback query: $callbackQueryId" }
        }
    }
}
