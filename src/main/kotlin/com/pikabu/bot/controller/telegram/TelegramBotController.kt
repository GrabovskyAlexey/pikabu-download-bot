package com.pikabu.bot.controller.telegram

import com.pikabu.bot.config.TelegramBotConfig
import com.pikabu.bot.domain.exception.AuthenticationException
import com.pikabu.bot.domain.exception.InvalidUrlException
import com.pikabu.bot.domain.exception.RateLimitExceededException
import com.pikabu.bot.domain.exception.VideoNotFoundException
import com.pikabu.bot.service.parser.VideoParserService
import com.pikabu.bot.service.cache.VideoCacheService
import com.pikabu.bot.service.queue.QueueService
import com.pikabu.bot.service.ratelimit.RateLimiterService
import com.pikabu.bot.service.telegram.AdminState
import com.pikabu.bot.service.telegram.AdminStateService
import com.pikabu.bot.service.telegram.MessageUpdaterService
import com.pikabu.bot.service.telegram.TelegramSenderService
import com.pikabu.bot.service.telegram.VideoSelectionCache
import com.pikabu.bot.service.template.MessageTemplateService
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
    private val adminCommandHandler: AdminCommandHandler,
    private val adminStateService: AdminStateService,
    private val urlValidationService: UrlValidationService,
    private val videoParserService: VideoParserService,
    private val queueService: QueueService,
    private val messageUpdaterService: MessageUpdaterService,
    private val rateLimiterService: RateLimiterService,
    private val videoSelectionCache: VideoSelectionCache,
    private val videoCacheService: VideoCacheService,
    private val messageTemplateService: MessageTemplateService
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

        logger.debug { "Received message from user $chatId: $text" }

        // Проверяем состояние админа (диалоговый режим)
        // Если это команда (начинается с "/") - обрабатываем как команду, иначе как ввод данных
        if (adminCommandHandler.isAdmin(chatId) && adminStateService.hasState(chatId) && !text.startsWith("/")) {
            handleAdminStateMessage(chatId, text)
            return
        }

        when {
            text.startsWith("/start") -> handleStartCommand(chatId)
            text.startsWith("/help") -> handleHelpCommand(chatId)
            text.startsWith("http") -> handleUrlMessage(chatId, text, message.messageId)
            text.startsWith("/") -> {
                // Пытаемся обработать как админ-команду
                val handled = adminCommandHandler.handleAdminCommand(chatId, text)
                if (!handled) {
                    handleUnknownMessage(chatId)
                }
            }
            else -> handleUnknownMessage(chatId)
        }
    }

    /**
     * Обрабатывает сообщения от админа когда он находится в диалоговом режиме
     */
    private fun handleAdminStateMessage(chatId: Long, text: String) {
        when (val state = adminStateService.getState(chatId)) {
            AdminState.WAITING_FOR_COOKIES -> {
                // Админ отправил cookies
                adminCommandHandler.handleCookieInput(chatId, text)
            }
            null -> {
                // Состояние было очищено в другом месте
                logger.debug { "Admin $chatId had no state, processing as normal message" }
                handleUnknownMessage(chatId)
            }
        }
    }

    private fun handleStartCommand(chatId: Long) {
        val welcomeMessage = messageTemplateService.renderMessage("start.ftl")
        telegramSenderService.sendMessage(chatId, welcomeMessage)
    }

    private fun handleHelpCommand(chatId: Long) {
        val helpMessage = messageTemplateService.renderMessage("help.ftl", mapOf(
            "isAdmin" to adminCommandHandler.isAdmin(chatId)
        ))

        telegramSenderService.sendMessage(chatId, helpMessage)
    }

    private fun handleUrlMessage(chatId: Long, url: String, messageId: Int) {
        logger.debug { "Processing URL from user $chatId: $url" }

        try {
            // Проверка rate limit
            rateLimiterService.checkRateLimit(chatId)

            // Валидация URL
            val validatedUrl = urlValidationService.validateUrl(url)

            // Парсинг видео
            val videos = videoParserService.parseVideos(validatedUrl)

            when {
                videos.isEmpty() -> {
                    val message = messageTemplateService.renderMessage("no-video-found.ftl")
                    telegramSenderService.sendMessage(chatId, message)
                }
                videos.size == 1 -> {
                    // Одно видео - проверяем кэш
                    val video = videos[0]
                    val cachedFileId = videoCacheService.getFileId(video.url)

                    if (cachedFileId != null) {
                        // Видео уже в кэше - отправляем мгновенно
                        logger.debug { "Sending cached video to user $chatId" }

                        // Получаем размер из кэша для caption
                        val cacheEntry = videoCacheService.getCacheEntry(video.url)
                        val caption = messageTemplateService.renderMessage("cached-video-caption.ftl", mapOf(
                            "videoTitle" to video.title,
                            "fileSize" to cacheEntry?.fileSize,
                            "botUsername" to botConfig.username
                        ))

                        val success = telegramSenderService.sendVideoByFileId(
                            chatId = chatId,
                            fileId = cachedFileId,
                            caption = caption
                        )
                        if (success) {
                            // Записываем в историю для статистики популярности
                            queueService.recordCachedDownload(chatId, video.url, video.title)
                        } else {
                            val errorMsg = messageTemplateService.renderMessage("error-send-video.ftl")
                            telegramSenderService.sendMessage(chatId, errorMsg)
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
                    val message = messageTemplateService.renderMessage("select-video.ftl", mapOf(
                        "videoCount" to videos.size
                    ))
                    telegramSenderService.sendMessageWithInlineKeyboard(chatId, message, buttons)
                }
            }
        } catch (e: RateLimitExceededException) {
            logger.warn { "Rate limit exceeded for user $chatId: ${e.message}" }
            val message = messageTemplateService.renderMessage("error-rate-limit.ftl", mapOf("message" to e.message))
            telegramSenderService.sendMessage(chatId, message)
        } catch (e: AuthenticationException) {
            logger.warn { "Authentication error for user $chatId: ${e.message}" }
            val message = messageTemplateService.renderMessage("error-auth-required.ftl")
            telegramSenderService.sendMessage(chatId, message)
        } catch (e: InvalidUrlException) {
            logger.warn { "Invalid URL from user $chatId: ${e.message}" }
            val message = messageTemplateService.renderMessage("error-invalid-url.ftl", mapOf("message" to e.message))
            telegramSenderService.sendMessage(chatId, message)
        } catch (e: VideoNotFoundException) {
            logger.warn { "No videos found for user $chatId: ${e.message}" }
            val message = messageTemplateService.renderMessage("error-video-not-found.ftl", mapOf("message" to e.message))
            telegramSenderService.sendMessage(chatId, message)
        } catch (e: Exception) {
            logger.error(e) { "Error processing URL for user $chatId" }
            val message = messageTemplateService.renderMessage("error-general.ftl")
            telegramSenderService.sendMessage(chatId, message)
        }
    }

    private fun handleUnknownMessage(chatId: Long) {
        val message = messageTemplateService.renderMessage("unknown-command.ftl")
        telegramSenderService.sendMessage(chatId, message)
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
                val errorMsg = messageTemplateService.renderMessage("error-queue-add.ftl")
                telegramSenderService.sendMessage(chatId, errorMsg)
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
                    messageTemplateService.renderMessage("queue-added-start.ftl")
                } else {
                    messageTemplateService.renderMessage("queue-added-position.ftl", mapOf("position" to actualPosition))
                }
                telegramSenderService.editMessageText(chatId, statusMessageId, updatedMessage)
            }

            logger.info { "Video added to queue for user $chatId (position: $actualPosition)" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to add video to queue for user $chatId" }
            val errorMsg = messageTemplateService.renderMessage("error-queue-add.ftl")
            telegramSenderService.sendMessage(chatId, errorMsg)
        }
    }
}
