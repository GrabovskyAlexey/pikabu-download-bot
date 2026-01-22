package com.pikabu.bot.service.admin

import com.pikabu.bot.config.AdminConfig
import com.pikabu.bot.entity.ErrorLogEntity
import com.pikabu.bot.service.telegram.TelegramSenderService
import com.pikabu.bot.service.template.MessageTemplateService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AdminNotificationService(
    private val adminConfig: AdminConfig,
    private val telegramSenderService: TelegramSenderService,
    private val messageTemplateService: MessageTemplateService
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

        val message = messageTemplateService.renderNotification("parsing-errors.ftl", mapOf(
            "errorCount" to errors.size,
            "lastError" to errors.firstOrNull()
        ))

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

        val message = messageTemplateService.renderNotification("download-errors.ftl", mapOf(
            "errorCount" to errors.size,
            "lastError" to errors.firstOrNull()
        ))

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

        val message = messageTemplateService.renderNotification("system-error.ftl", mapOf(
            "error" to error
        ))

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

        val message = messageTemplateService.renderNotification("daily-digest.ftl", mapOf(
            "stats" to stats
        ))

        sendNotification(message)
    }

    /**
     * Отправляет недельный дайджест статистики
     */
    fun sendWeeklyDigest(stats: WeeklyStats) {
        if (!adminConfig.enableWeeklyDigest || adminConfig.userId == 0L) {
            logger.debug { "Weekly digest disabled or admin ID not configured" }
            return
        }

        val message = messageTemplateService.renderNotification("weekly-digest.ftl", mapOf(
            "stats" to stats
        ))

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

        val message = messageTemplateService.renderNotification("authentication-error.ftl", mapOf(
            "statusCode" to statusCode,
            "url" to url
        ))

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

        val message = messageTemplateService.renderNotification("cookies-expired.ftl", mapOf(
            "url" to url
        ))

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

data class WeeklyStats(
    val successfulDownloads: Int,
    val totalErrors: Int,
    val parsingErrors: Int,
    val downloadErrors: Int,
    val systemErrors: Int,
    val activeUsers: Int,
    val totalUsers: Int,
    val queuedRequests: Int,
    val avgDownloadsPerDay: Double,
    val topVideos: List<PopularVideo>
)

data class PopularVideo(
    val videoUrl: String,
    val videoTitle: String?,
    val downloadCount: Long
)
