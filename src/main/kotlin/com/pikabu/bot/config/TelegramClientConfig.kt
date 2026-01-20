package com.pikabu.bot.config

import com.pikabu.bot.controller.telegram.TelegramBotController
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.meta.generics.TelegramClient
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient as TelegramClientImpl

private val logger = KotlinLogging.logger {}

@Configuration
class TelegramClientConfig(
    private val botConfig: TelegramBotConfig,
    @Lazy private val telegramBotController: TelegramBotController
) {

    @Bean
    fun telegramClient(): TelegramClient {
        logger.info { "Creating TelegramClient with token: ${botConfig.token.take(10)}..." }
        return TelegramClientImpl(botConfig.token)
    }

    @PostConstruct
    fun registerBot() {
        try {
            logger.info { "Registering Telegram bot: ${botConfig.username}" }

            val botsApplication = TelegramBotsLongPollingApplication()
            botsApplication.registerBot(botConfig.token, telegramBotController)

            logger.info { "Telegram bot successfully registered and started" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to register Telegram bot" }
            throw e
        }
    }
}
