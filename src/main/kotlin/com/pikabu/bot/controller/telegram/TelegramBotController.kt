package com.pikabu.bot.controller.telegram

import com.pikabu.bot.config.TelegramBotConfig
import com.pikabu.bot.domain.exception.InvalidUrlException
import com.pikabu.bot.domain.exception.RateLimitExceededException
import com.pikabu.bot.domain.exception.VideoNotFoundException
import com.pikabu.bot.service.parser.VideoParserService
import com.pikabu.bot.service.cache.VideoCacheService
import com.pikabu.bot.service.queue.QueueService
import com.pikabu.bot.service.ratelimit.RateLimiterService
import com.pikabu.bot.service.telegram.MessageUpdaterService
import com.pikabu.bot.service.telegram.TelegramSenderService
import com.pikabu.bot.service.telegram.VideoSelectionCache
import com.pikabu.bot.service.validation.UrlValidationService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.objects.Update

private val logger = KotlinLogging.logger {}

@Component
class TelegramBotController(
    private val botConfig: TelegramBotConfig,
    private val telegramSenderService: TelegramSenderService,
    private val callbackQueryHandler: CallbackQueryHandler,
    private val urlValidationService: UrlValidationService,
    private val videoParserService: VideoParserService,
    private val queueService: QueueService,
    private val messageUpdaterService: MessageUpdaterService,
    private val rateLimiterService: RateLimiterService,
    private val videoSelectionCache: VideoSelectionCache,
    private val videoCacheService: VideoCacheService
) : LongPollingSingleThreadUpdateConsumer {

    override fun consume(update: Update) {
        try {
            when {
                update.hasMessage() && update.message.hasText() -> handleTextMessage(update)
                update.hasCallbackQuery() -> callbackQueryHandler.handleCallbackQuery(update.callbackQuery)
                else -> logger.debug { "Received unsupported update type" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing update: ${update.updateId}" }
        }
    }

    private fun handleTextMessage(update: Update) {
        val message = update.message
        val chatId = message.chatId
        val text = message.text

        logger.info { "Received message from user $chatId: $text" }

        when {
            text.startsWith("/start") -> handleStartCommand(chatId)
            text.startsWith("/help") -> handleHelpCommand(chatId)
            text.startsWith("http") -> handleUrlMessage(chatId, text, message.messageId)
            else -> handleUnknownMessage(chatId)
        }
    }

    private fun handleStartCommand(chatId: Long) {
        val welcomeMessage = """
            Привет! Я бот для скачивания видео с Pikabu.ru

            Просто отправь мне ссылку на пост с Pikabu, и я скачаю видео для тебя.

            Используй /help для получения дополнительной информации.
        """.trimIndent()

        telegramSenderService.sendMessage(chatId, welcomeMessage)
    }

    private fun handleHelpCommand(chatId: Long) {
        val helpMessage = """
            Как пользоваться ботом:

            1. Отправь мне ссылку на пост с Pikabu.ru
            2. Если на странице несколько видео, выбери нужное
            3. Дождись загрузки видео

            Ограничения:
            - Работаю только с pikabu.ru
            - Максимальный размер видео: 500 МБ
            - Лимит запросов: 1000 в час

            Команды:
            /start - начало работы
            /help - справка
        """.trimIndent()

        telegramSenderService.sendMessage(chatId, helpMessage)
    }

    private fun handleUrlMessage(chatId: Long, url: String, messageId: Int) {
        logger.info { "Processing URL from user $chatId: $url" }

        try {
            // Проверка rate limit
            rateLimiterService.checkRateLimit(chatId)

            // Валидация URL
            val validatedUrl = urlValidationService.validateUrl(url)

            // Парсинг видео
            val videos = videoParserService.parseVideos(validatedUrl)

            when {
                videos.isEmpty() -> {
                    telegramSenderService.sendMessage(chatId, "На странице не найдено видео")
                }
                videos.size == 1 -> {
                    // Одно видео - проверяем кэш
                    val video = videos[0]
                    val cachedFileId = videoCacheService.getFileId(video.url)

                    if (cachedFileId != null) {
                        // Видео уже в кэше - отправляем мгновенно
                        logger.info { "Sending cached video to user $chatId (file_id: $cachedFileId)" }

                        // Получаем размер из кэша для caption
                        val cacheEntry = videoCacheService.getCacheEntry(video.url)
                        val caption = buildCachedCaption(video.title, cacheEntry?.fileSize)

                        val success = telegramSenderService.sendVideoByFileId(
                            chatId = chatId,
                            fileId = cachedFileId,
                            caption = caption
                        )
                        if (!success) {
                            telegramSenderService.sendMessage(chatId, "❌ Ошибка отправки. Попробуйте еще раз.")
                        }
                    } else {
                        // Нет в кэше - добавляем в очередь
                        addVideoToQueue(chatId, messageId, video.url, video.title)
                    }
                }
                else -> {
                    // Несколько видео - сохраняем в кэш и показываем inline кнопки
                    val cacheId = videoSelectionCache.store(videos, validatedUrl)
                    val buttons = videos.mapIndexed { index, video ->
                        val title = video.title ?: "Видео ${index + 1}"
                        title to "video:$cacheId:$index"
                    }
                    telegramSenderService.sendMessageWithInlineKeyboard(
                        chatId,
                        "Найдено ${videos.size} видео. Выберите нужное:",
                        buttons
                    )
                }
            }
        } catch (e: RateLimitExceededException) {
            logger.warn { "Rate limit exceeded for user $chatId: ${e.message}" }
            telegramSenderService.sendMessage(chatId, "⏱️ ${e.message}")
        } catch (e: InvalidUrlException) {
            logger.warn { "Invalid URL from user $chatId: ${e.message}" }
            telegramSenderService.sendMessage(chatId, "❌ ${e.message}")
        } catch (e: VideoNotFoundException) {
            logger.warn { "No videos found for user $chatId: ${e.message}" }
            telegramSenderService.sendMessage(chatId, "❌ ${e.message}")
        } catch (e: Exception) {
            logger.error(e) { "Error processing URL for user $chatId" }
            telegramSenderService.sendMessage(
                chatId,
                "❌ Произошла ошибка при обработке ссылки. Попробуйте позже."
            )
        }
    }

    private fun handleUnknownMessage(chatId: Long) {
        telegramSenderService.sendMessage(
            chatId,
            "Я не понимаю это сообщение. Отправь мне ссылку на Pikabu или используй /help для справки."
        )
    }

    /**
     * Формирует caption для кэшированного видео
     */
    private fun buildCachedCaption(videoTitle: String?, fileSize: Long?): String {
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

    /**
     * Добавляет видео в очередь загрузки
     */
    private fun addVideoToQueue(chatId: Long, messageId: Int, videoUrl: String, videoTitle: String?) {
        try {
            // Отправляем сообщение о добавлении в очередь и получаем его ID
            val position = 1 // Временно, будет пересчитано
            val statusMessageId = messageUpdaterService.sendQueueAddedMessage(chatId, position)

            if (statusMessageId == null) {
                logger.error { "Failed to send queue status message" }
                telegramSenderService.sendMessage(chatId, "❌ Ошибка при добавлении в очередь")
                return
            }

            // Добавляем в очередь с ID сообщения о статусе (для обновлений)
            val queueEntity = queueService.addToQueue(
                userId = chatId,
                messageId = statusMessageId,
                videoUrl = videoUrl,
                videoTitle = videoTitle
            )

            // Обновляем сообщение с правильной позицией
            val actualPosition = queueEntity.position ?: 1
            if (actualPosition != position) {
                val updatedMessage = if (actualPosition == 1) {
                    "✅ Видео добавлено в очередь.\n\n⏳ Загрузка начнётся сейчас..."
                } else {
                    "✅ Видео добавлено в очередь.\n\n⏳ Позиция в очереди: $actualPosition"
                }
                telegramSenderService.editMessageText(chatId, statusMessageId, updatedMessage)
            }

            logger.info { "Video added to queue for user $chatId, position: $actualPosition" }

            logger.info { "Video added to queue for user $chatId: $videoUrl" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to add video to queue for user $chatId" }
            telegramSenderService.sendMessage(
                chatId,
                "❌ Ошибка при добавлении видео в очередь. Попробуйте позже."
            )
        }
    }
}
