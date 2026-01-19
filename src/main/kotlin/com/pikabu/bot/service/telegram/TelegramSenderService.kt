package com.pikabu.bot.service.telegram

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.io.File

private val logger = KotlinLogging.logger {}

@Service
class TelegramSenderService(
    private val telegramClient: TelegramClient
) {

    fun sendMessage(chatId: Long, text: String): Int? {
        return try {
            val message = SendMessage(chatId.toString(), text)
            val response = telegramClient.execute(message)
            logger.debug { "Message sent to chat $chatId" }
            response.messageId
        } catch (e: TelegramApiException) {
            logger.error(e) { "Failed to send message to chat $chatId" }
            null
        }
    }

    fun sendMessageWithInlineKeyboard(
        chatId: Long,
        text: String,
        buttons: List<Pair<String, String>>
    ): Int? {
        return try {
            val keyboard = InlineKeyboardMarkup(
                buttons.map { (label, callbackData) ->
                    InlineKeyboardRow(
                        InlineKeyboardButton(label).apply {
                            this.callbackData = callbackData
                        }
                    )
                }
            )

            val message = SendMessage(chatId.toString(), text).apply {
                replyMarkup = keyboard
            }

            val response = telegramClient.execute(message)
            logger.debug { "Message with inline keyboard sent to chat $chatId" }
            response.messageId
        } catch (e: TelegramApiException) {
            logger.error(e) { "Failed to send message with inline keyboard to chat $chatId" }
            null
        }
    }

    fun editMessageText(chatId: Long, messageId: Int, text: String): Boolean {
        return try {
            val editMessage = EditMessageText(text).apply {
                this.chatId = chatId.toString()
                this.messageId = messageId
            }
            telegramClient.execute(editMessage)
            logger.debug { "Message $messageId edited in chat $chatId" }
            true
        } catch (e: TelegramApiException) {
            logger.error(e) { "Failed to edit message $messageId in chat $chatId" }
            false
        }
    }

    fun sendVideo(chatId: Long, videoFile: File, caption: String? = null): Boolean {
        return try {
            val sendVideo = SendVideo(chatId.toString(), InputFile(videoFile)).apply {
                caption?.let { this.caption = it }
            }
            telegramClient.execute(sendVideo)
            logger.info { "Video sent to chat $chatId" }
            true
        } catch (e: TelegramApiException) {
            logger.error(e) { "Failed to send video to chat $chatId" }
            false
        }
    }

    fun sendVideo(chatId: Long, videoFile: File, caption: String? = null, replyToMessageId: Int? = null): Boolean {
        return try {
            val sendVideo = SendVideo(chatId.toString(), InputFile(videoFile)).apply {
                caption?.let { this.caption = it }
                replyToMessageId?.let { this.replyToMessageId = it }
            }
            telegramClient.execute(sendVideo)
            logger.info { "Video sent to chat $chatId" }
            true
        } catch (e: TelegramApiException) {
            logger.error(e) { "Failed to send video to chat $chatId" }
            false
        }
    }
}
