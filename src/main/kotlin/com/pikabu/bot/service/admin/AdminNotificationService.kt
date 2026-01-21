package com.pikabu.bot.service.admin

import com.pikabu.bot.config.AdminConfig
import com.pikabu.bot.entity.ErrorLogEntity
import com.pikabu.bot.service.telegram.TelegramSenderService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AdminNotificationService(
    private val adminConfig: AdminConfig,
    private val telegramSenderService: TelegramSenderService
) {

    /**
     * Отправляет уведомление о критических ошибках парсинга
     */
    fun notifyParsingErrors(errors: List<ErrorLogEntity>) {
        if (!adminConfig.enableNotifications || adminConfig.userId == 0L) {
            logger.debug { "Admin notifications disabled or admin ID not configured" }
            return
        }

        if (errors.isEmpty()) return

        val message = buildString {
            append("⚠️ ПРЕДУПРЕЖДЕНИЕ: Обнаружены ошибки парсинга\n\n")
            append("Количество ошибок: ${errors.size} за последние 10 минут\n\n")
            append("Возможно изменилась структура страниц Pikabu.ru\n\n")

            // Показываем последнюю ошибку
            val lastError = errors.firstOrNull()
            if (lastError != null) {
                append("Последняя ошибка:\n")
                append("📄 URL: ${lastError.pageUrl}\n")
                append("💬 Сообщение: ${lastError.errorMessage}\n")
                append("🕐 Время: ${lastError.occurredAt}\n")
            }

            append("\n")
            append("Рекомендуется проверить PikabuHtmlParser и обновить стратегии парсинга.")
        }

        sendNotification(message)
    }

    /**
     * Отправляет уведомление о критических ошибках загрузки
     */
    fun notifyDownloadErrors(errors: List<ErrorLogEntity>) {
        if (!adminConfig.enableNotifications || adminConfig.userId == 0L) {
            logger.debug { "Admin notifications disabled or admin ID not configured" }
            return
        }

        if (errors.isEmpty()) return

        val message = buildString {
            append("⚠️ ПРЕДУПРЕЖДЕНИЕ: Обнаружены ошибки загрузки\n\n")
            append("Количество ошибок: ${errors.size} за последние 15 минут\n\n")
            append("Возможные причины:\n")
            append("• Проблемы с сетью\n")
            append("• Блокировка со стороны Pikabu\n")
            append("• Недоступность видео-серверов\n\n")

            // Показываем последнюю ошибку
            val lastError = errors.firstOrNull()
            if (lastError != null) {
                append("Последняя ошибка:\n")
                append("📄 URL: ${lastError.pageUrl}\n")
                append("💬 Сообщение: ${lastError.errorMessage}\n")
                append("🕐 Время: ${lastError.occurredAt}\n")
            }

            append("\n")
            append("Рекомендуется проверить доступность Pikabu и сетевое подключение.")
        }

        sendNotification(message)
    }

    /**
     * Отправляет уведомление о критической системной ошибке
     */
    fun notifySystemError(error: ErrorLogEntity) {
        if (!adminConfig.enableNotifications || adminConfig.userId == 0L) {
            logger.debug { "Admin notifications disabled or admin ID not configured" }
            return
        }

        val message = buildString {
            append("🚨 КРИТИЧЕСКАЯ ОШИБКА\n\n")
            append("💬 Сообщение: ${error.errorMessage}\n")
            append("🕐 Время: ${error.occurredAt}\n\n")

            if (error.pageUrl != null) {
                append("📄 URL: ${error.pageUrl}\n\n")
            }

            if (error.stackTrace != null && error.stackTrace.length < 500) {
                append("Stack trace:\n```\n${error.stackTrace}\n```\n\n")
            }

            append("Требуется немедленное внимание!")
        }

        sendNotification(message)
    }

    /**
     * Отправляет дневной дайджест статистики
     */
    fun sendDailyDigest(stats: DailyStats) {
        if (!adminConfig.enableDailyDigest || adminConfig.userId == 0L) {
            logger.debug { "Daily digest disabled or admin ID not configured" }
            return
        }

        val message = buildString {
            append("📊 Дневная статистика\n\n")
            append("✅ Загружено видео: ${stats.successfulDownloads}\n")
            append("❌ Ошибок: ${stats.totalErrors}\n")
            append("   • Парсинг: ${stats.parsingErrors}\n")
            append("   • Загрузка: ${stats.downloadErrors}\n")
            append("   • Система: ${stats.systemErrors}\n\n")
            append("👥 Активных пользователей: ${stats.activeUsers}\n")
            append("📦 Всего в очереди: ${stats.queuedRequests}\n")
        }

        sendNotification(message)
    }

    /**
     * Отправляет уведомление об ошибке авторизации
     */
    fun notifyAuthenticationError(statusCode: Int, url: String) {
        if (!adminConfig.enableNotifications || adminConfig.userId == 0L) {
            logger.debug { "Admin notifications disabled or admin ID not configured" }
            return
        }

        val message = buildString {
            append("🔒 ОШИБКА АВТОРИЗАЦИИ\n\n")
            append("HTTP Status: $statusCode\n")
            append("📄 URL: $url\n\n")
            append("Возможные причины:\n")
            when (statusCode) {
                401 -> {
                    append("• Cookies истекли или невалидны\n")
                    append("• Требуется повторная авторизация\n\n")
                    append("Рекомендация: Обновите cookies через /update_auth")
                }
                403 -> {
                    append("• Доступ запрещён\n")
                    append("• Контент может быть приватным\n")
                    append("• Cookies могут быть устаревшими\n\n")
                    append("Рекомендация: Проверьте cookies через /update_auth")
                }
            }
        }

        sendNotification(message)
    }

    /**
     * Отправляет уведомление о протухших cookies
     * Вызывается когда страница загрузилась, но контент требует авторизации
     */
    fun notifyCookiesExpired(url: String) {
        if (!adminConfig.enableNotifications || adminConfig.userId == 0L) {
            logger.debug { "Admin notifications disabled or admin ID not configured" }
            return
        }

        val message = buildString {
            append("🔑 COOKIES ПРОТУХЛИ\n\n")
            append("Обнаружен контент, требующий авторизации:\n")
            append("📄 URL: $url\n\n")
            append("Признаки:\n")
            append("• Страница загрузилась (HTTP 200)\n")
            append("• Но контент показывает призыв авторизоваться\n")
            append("• userID: 0 (неавторизованный пользователь)\n")
            append("• Возможно, это NSFW/18+ контент\n\n")
            append("⚠️ Действие требуется:\n")
            append("Обновите cookies Pikabu через команду /update_auth\n\n")
            append("Как получить cookies:\n")
            append("1. Откройте pikabu.ru в браузере\n")
            append("2. Авторизуйтесь\n")
            append("3. F12 → Application → Cookies\n")
            append("4. Скопируйте PHPSESS\n")
            append("5. Отправьте мне через /update_auth")
        }

        sendNotification(message)
    }

    /**
     * Отправляет произвольное уведомление админу
     */
    fun sendNotification(message: String) {
        if (adminConfig.userId == 0L) {
            logger.warn { "Cannot send notification: admin user ID not configured" }
            return
        }

        try {
            telegramSenderService.sendMessage(adminConfig.userId, message)
            logger.debug { "Admin notification sent successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send admin notification" }
        }
    }
}

data class DailyStats(
    val successfulDownloads: Int,
    val totalErrors: Int,
    val parsingErrors: Int,
    val downloadErrors: Int,
    val systemErrors: Int,
    val activeUsers: Int,
    val queuedRequests: Int
)
