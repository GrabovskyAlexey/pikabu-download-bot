package com.pikabu.bot.service.admin

import com.pikabu.bot.config.AdminConfig
import com.pikabu.bot.domain.model.ErrorType
import com.pikabu.bot.entity.ErrorLogEntity
import com.pikabu.bot.repository.ErrorLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class ErrorMonitoringService(
    private val errorLogRepository: ErrorLogRepository,
    private val adminNotificationService: AdminNotificationService,
    private val adminConfig: AdminConfig,
    @Value("\${app.error-monitoring.parsing-error-threshold:5}")
    private val parsingErrorThreshold: Int,
    @Value("\${app.error-monitoring.parsing-error-window-minutes:10}")
    private val parsingErrorWindowMinutes: Int,
    @Value("\${app.error-monitoring.download-error-threshold:10}")
    private val downloadErrorThreshold: Int,
    @Value("\${app.error-monitoring.download-error-window-minutes:15}")
    private val downloadErrorWindowMinutes: Int
) {

    /**
     * Периодически проверяет ошибки и отправляет уведомления админу
     */
    @Scheduled(fixedDelayString = "\${app.error-monitoring.check-interval-minutes:5}000", initialDelay = 60000)
    @Transactional
    fun monitorErrors() {
        if (!adminConfig.enableNotifications) {
            logger.debug { "Error monitoring disabled" }
            return
        }

        try {
            logger.debug { "Running error monitoring check" }

            // Проверка ошибок парсинга
            checkParsingErrors()

            // Проверка ошибок загрузки
            checkDownloadErrors()

            logger.debug { "Error monitoring check completed" }

        } catch (e: Exception) {
            logger.error(e) { "Error in error monitoring service" }
        }
    }

    /**
     * Проверяет ошибки парсинга
     */
    private fun checkParsingErrors() {
        val since = LocalDateTime.now().minusMinutes(parsingErrorWindowMinutes.toLong())
        val errors = errorLogRepository.findRecentErrorsByType(
            errorType = ErrorType.PARSING_ERROR.name,
            since = since
        )

        val unnotifiedErrors = errors.filter { !it.notifiedAdmin }

        if (unnotifiedErrors.size >= parsingErrorThreshold) {
            logger.warn { "Parsing error threshold exceeded: ${unnotifiedErrors.size}/$parsingErrorThreshold" }

            // Отправляем уведомление
            adminNotificationService.notifyParsingErrors(unnotifiedErrors)

            // Помечаем как notified
            markAsNotified(unnotifiedErrors)
        }
    }

    /**
     * Проверяет ошибки загрузки
     */
    private fun checkDownloadErrors() {
        val since = LocalDateTime.now().minusMinutes(downloadErrorWindowMinutes.toLong())
        val errors = errorLogRepository.findRecentErrorsByType(
            errorType = ErrorType.DOWNLOAD_ERROR.name,
            since = since
        )

        val unnotifiedErrors = errors.filter { !it.notifiedAdmin }

        if (unnotifiedErrors.size >= downloadErrorThreshold) {
            logger.warn { "Download error threshold exceeded: ${unnotifiedErrors.size}/$downloadErrorThreshold" }

            // Отправляем уведомление
            adminNotificationService.notifyDownloadErrors(unnotifiedErrors)

            // Помечаем как notified
            markAsNotified(unnotifiedErrors)
        }
    }

    /**
     * Логирует ошибку в базу данных
     */
    @Transactional
    fun logError(errorType: ErrorType, errorMessage: String, pageUrl: String? = null, stackTrace: String? = null) {
        try {
            val errorEntity = ErrorLogEntity(
                errorType = errorType.name,
                errorMessage = errorMessage,
                pageUrl = pageUrl,
                stackTrace = stackTrace,
                notifiedAdmin = false,
                occurredAt = LocalDateTime.now()
            )

            errorLogRepository.save(errorEntity)

            logger.debug { "Error logged: type=$errorType, message=$errorMessage" }

            // Для системных ошибок отправляем уведомление немедленно
            if (errorType == ErrorType.SYSTEM_ERROR && adminConfig.enableNotifications) {
                adminNotificationService.notifySystemError(errorEntity)
                errorEntity.notifiedAdmin = true
                errorLogRepository.save(errorEntity)
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to log error to database: $errorMessage" }
        }
    }

    /**
     * Помечает ошибки как уведомленные
     */
    private fun markAsNotified(errors: List<ErrorLogEntity>) {
        errors.forEach { error ->
            error.notifiedAdmin = true
        }
        errorLogRepository.saveAll(errors)
        logger.debug { "Marked ${errors.size} errors as notified" }
    }

    /**
     * Получает статистику ошибок
     */
    fun getErrorStatistics(hours: Int = 24): ErrorStatistics {
        val since = LocalDateTime.now().minusHours(hours.toLong())

        val parsingErrors = errorLogRepository.countByErrorTypeSince(ErrorType.PARSING_ERROR.name, since)
        val downloadErrors = errorLogRepository.countByErrorTypeSince(ErrorType.DOWNLOAD_ERROR.name, since)
        val systemErrors = errorLogRepository.countByErrorTypeSince(ErrorType.SYSTEM_ERROR.name, since)

        return ErrorStatistics(
            parsingErrors = parsingErrors.toInt(),
            downloadErrors = downloadErrors.toInt(),
            systemErrors = systemErrors.toInt(),
            totalErrors = (parsingErrors + downloadErrors + systemErrors).toInt(),
            periodHours = hours
        )
    }
}

data class ErrorStatistics(
    val parsingErrors: Int,
    val downloadErrors: Int,
    val systemErrors: Int,
    val totalErrors: Int,
    val periodHours: Int
)
