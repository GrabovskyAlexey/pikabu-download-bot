package com.pikabu.bot.service.telegram

import com.pikabu.bot.config.AdminConfig
import com.pikabu.bot.config.TelegramBotConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeChat
import org.telegram.telegrambots.meta.generics.TelegramClient

private val logger = KotlinLogging.logger {}

/**
 * Регистрирует команды бота в Telegram меню при старте приложения
 */
@Component
class BotCommandRegistrar(
    private val telegramClient: TelegramClient,
    private val botConfig: TelegramBotConfig,
    private val adminConfig: AdminConfig
) {

    /**
     * Регистрация команд выполняется после инициализации бина
     */
    @PostConstruct
    fun registerCommands() {
        try {
            // Общие команды для всех пользователей
            registerUserCommands()

            // Админские команды только для админа
            registerAdminCommands()

            logger.info { "Bot commands registered successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to register bot commands" }
        }
    }

    /**
     * Регистрирует общие команды для всех пользователей
     */
    private fun registerUserCommands() {
        val userCommands = listOf(
            BotCommand("start", "Начало работы"),
            BotCommand("help", "Справка по использованию")
        )

        val setMyCommands = SetMyCommands.builder()
            .commands(userCommands)
            .build()

        telegramClient.execute(setMyCommands)
        logger.debug { "Registered ${userCommands.size} user commands" }
    }

    /**
     * Регистрирует команды для админа (видны только админу)
     */
    private fun registerAdminCommands() {
        val adminCommands = listOf(
            // Общие команды
            BotCommand("start", "Начало работы"),
            BotCommand("help", "Справка по использованию"),

            // Админские команды
            BotCommand("stats", "📊 Общая статистика"),
            BotCommand("health", "🏥 Состояние системы"),
            BotCommand("queue", "📋 Очередь загрузок"),
            BotCommand("cache", "💾 Статистика кэша"),
            BotCommand("errors", "❌ Последние ошибки"),
            BotCommand("auth_status", "🔐 Статус авторизации Pikabu"),
            BotCommand("update_auth", "🔑 Обновить cookies авторизации"),
            BotCommand("cancel", "❌ Отменить текущую операцию")
        )

        val setMyCommands = SetMyCommands.builder()
            .commands(adminCommands)
            .scope(BotCommandScopeChat.builder().chatId(adminConfig.userId.toString()).build())
            .build()

        telegramClient.execute(setMyCommands)
        logger.debug { "Registered ${adminCommands.size} admin commands for admin ${adminConfig.userId}" }
    }
}
