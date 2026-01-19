package com.pikabu.bot.controller.telegram

import com.pikabu.bot.service.queue.QueueService
import com.pikabu.bot.service.telegram.MessageUpdaterService
import com.pikabu.bot.service.telegram.TelegramSenderService
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
    private val messageUpdaterService: MessageUpdaterService
) {

    fun handleCallbackQuery(callbackQuery: CallbackQuery) {
        val chatId = callbackQuery.message.chatId
        val data = callbackQuery.data
        val messageId = callbackQuery.message.messageId

        logger.info { "Received callback query from user $chatId: $data" }

        try {
            when {
                data.startsWith("select_video:") -> handleVideoSelection(chatId, data, messageId)
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

    private fun handleVideoSelection(chatId: Long, data: String, messageId: Int) {
        // Формат: select_video:<page_url>:<video_url>
        val parts = data.split(":", limit = 3)

        if (parts.size != 3) {
            logger.error { "Invalid callback data format: $data" }
            telegramSenderService.sendMessage(chatId, "Ошибка: неверный формат данных")
            return
        }

        val pageUrl = parts[1]
        val videoUrl = parts[2]

        logger.info { "User $chatId selected video: $videoUrl from page: $pageUrl" }

        try {
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
                videoUrl = videoUrl,
                videoTitle = null
            )

            // Обновляем сообщение с позицией в очереди
            val position = queueEntity.position ?: 1
            val message = if (position == 1) {
                "✅ Видео добавлено в очередь.\n\n⏳ Загрузка начнётся сейчас..."
            } else {
                "✅ Видео добавлено в очередь.\n\n⏳ Позиция в очереди: $position"
            }

            telegramSenderService.editMessageText(chatId, messageId, message)

            logger.info { "Video added to queue from callback for user $chatId: $videoUrl" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to add video to queue from callback for user $chatId" }
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
