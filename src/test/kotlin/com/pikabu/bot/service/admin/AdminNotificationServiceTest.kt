package com.pikabu.bot.service.admin

import com.pikabu.bot.config.AdminConfig
import com.pikabu.bot.domain.model.ErrorType
import com.pikabu.bot.entity.ErrorLogEntity
import com.pikabu.bot.service.telegram.TelegramSenderService
import com.pikabu.bot.service.template.MessageTemplateService
import io.kotest.core.spec.style.FunSpec
import io.mockk.*
import java.time.LocalDateTime

class AdminNotificationServiceTest : FunSpec({

    lateinit var adminConfig: AdminConfig
    lateinit var telegramSenderService: TelegramSenderService
    lateinit var messageTemplateService: MessageTemplateService
    lateinit var service: AdminNotificationService

    beforeEach {
        adminConfig = AdminConfig(
            userId = 12345L,
            enableNotifications = true,
            enableDailyDigest = true,
            enableWeeklyDigest = true
        )
        telegramSenderService = mockk(relaxed = true)
        messageTemplateService = mockk(relaxed = true)

        // Mock template rendering
        every { messageTemplateService.renderNotification(any(), any()) } answers {
            val templateName = firstArg<String>()
            val data = secondArg<Map<String, Any?>>()

            when {
                templateName.contains("parsing-errors") -> {
                    val lastError = data["lastError"] as? ErrorLogEntity
                    "ПРЕДУПРЕЖДЕНИЕ: Обнаружены ошибки парсинга\nКоличество ошибок: ${data["errorCount"]}\n${lastError?.pageUrl ?: ""}"
                }
                templateName.contains("download-errors") -> "ПРЕДУПРЕЖДЕНИЕ: Обнаружены ошибки загрузки\nКоличество ошибок: ${data["errorCount"]}\n${(data["lastError"] as? ErrorLogEntity)?.errorMessage}"
                templateName.contains("system-error") -> {
                    val error = data["error"] as? ErrorLogEntity
                    val trace = if (error?.stackTrace != null && error.stackTrace.length < 500) error.stackTrace else ""
                    "КРИТИЧЕСКАЯ ОШИБКА\n${error?.errorMessage}\n$trace\nТребуется немедленное внимание!"
                }
                templateName.contains("daily-digest") -> {
                    val stats = data["stats"] as DailyStats
                    "Дневная статистика\nЗагружено видео: ${stats.successfulDownloads}\nОшибок: ${stats.totalErrors}\nАктивных пользователей: ${stats.activeUsers}"
                }
                templateName.contains("weekly-digest") -> {
                    val stats = data["stats"] as WeeklyStats
                    "Недельная статистика\nЗагружено видео: ${stats.successfulDownloads}\nАктивных за неделю: ${stats.activeUsers}"
                }
                else -> "Test message"
            }
        }

        service = AdminNotificationService(adminConfig, telegramSenderService, messageTemplateService)
    }

    afterEach {
        clearAllMocks()
    }

    context("notifyParsingErrors") {
        test("should send notification with error details") {
            val errors = listOf(
                ErrorLogEntity(
                    id = 1L,
                    errorType = ErrorType.PARSING_ERROR.name,
                    errorMessage = "No videos found",
                    pageUrl = "https://pikabu.ru/story/test",
                    occurredAt = LocalDateTime.now(),
                    notifiedAdmin = false
                )
            )

            service.notifyParsingErrors(errors)

            verify {
                telegramSenderService.sendMessage(
                    chatId = 12345L,
                    text = match {
                        it.contains("ПРЕДУПРЕЖДЕНИЕ: Обнаружены ошибки парсинга") &&
                        it.contains("Количество ошибок: 1") &&
                        it.contains("https://pikabu.ru/story/test")
                    }
                )
            }
        }

        test("should not send notification when notifications disabled") {
            adminConfig = AdminConfig(userId = 12345L, enableNotifications = false, enableDailyDigest = false, enableWeeklyDigest = false)
            service = AdminNotificationService(adminConfig, telegramSenderService, messageTemplateService)

            val errors = listOf(
                ErrorLogEntity(
                    id = 1L,
                    errorType = ErrorType.PARSING_ERROR.name,
                    errorMessage = "Test error",
                    occurredAt = LocalDateTime.now(),
                    notifiedAdmin = false
                )
            )

            service.notifyParsingErrors(errors)

            verify(exactly = 0) { telegramSenderService.sendMessage(any(), any()) }
        }

        test("should not send notification when admin ID not configured") {
            adminConfig = AdminConfig(userId = 0L, enableNotifications = true, enableDailyDigest = false, enableWeeklyDigest = false)
            service = AdminNotificationService(adminConfig, telegramSenderService, messageTemplateService)

            val errors = listOf(
                ErrorLogEntity(
                    id = 1L,
                    errorType = ErrorType.PARSING_ERROR.name,
                    errorMessage = "Test error",
                    occurredAt = LocalDateTime.now(),
                    notifiedAdmin = false
                )
            )

            service.notifyParsingErrors(errors)

            verify(exactly = 0) { telegramSenderService.sendMessage(any(), any()) }
        }

        test("should not send notification when error list is empty") {
            service.notifyParsingErrors(emptyList())

            verify(exactly = 0) { telegramSenderService.sendMessage(any(), any()) }
        }
    }

    context("notifyDownloadErrors") {
        test("should send notification with download error details") {
            val errors = listOf(
                ErrorLogEntity(
                    id = 1L,
                    errorType = ErrorType.DOWNLOAD_ERROR.name,
                    errorMessage = "Connection timeout",
                    pageUrl = "https://example.com/video.mp4",
                    occurredAt = LocalDateTime.now(),
                    notifiedAdmin = false
                )
            )

            service.notifyDownloadErrors(errors)

            verify {
                telegramSenderService.sendMessage(
                    chatId = 12345L,
                    text = match {
                        it.contains("ПРЕДУПРЕЖДЕНИЕ: Обнаружены ошибки загрузки") &&
                        it.contains("Количество ошибок: 1") &&
                        it.contains("Connection timeout")
                    }
                )
            }
        }
    }

    context("notifySystemError") {
        test("should send critical error notification") {
            val error = ErrorLogEntity(
                id = 1L,
                errorType = ErrorType.SYSTEM_ERROR.name,
                errorMessage = "Database connection lost",
                occurredAt = LocalDateTime.now(),
                notifiedAdmin = false,
                stackTrace = "java.sql.SQLException: Connection refused"
            )

            service.notifySystemError(error)

            verify {
                telegramSenderService.sendMessage(
                    chatId = 12345L,
                    text = match {
                        it.contains("КРИТИЧЕСКАЯ ОШИБКА") &&
                        it.contains("Database connection lost") &&
                        it.contains("Требуется немедленное внимание!")
                    }
                )
            }
        }

        test("should include stack trace for small errors") {
            val error = ErrorLogEntity(
                id = 1L,
                errorType = ErrorType.SYSTEM_ERROR.name,
                errorMessage = "NPE",
                occurredAt = LocalDateTime.now(),
                notifiedAdmin = false,
                stackTrace = "Short trace"
            )

            service.notifySystemError(error)

            verify {
                telegramSenderService.sendMessage(
                    chatId = 12345L,
                    text = match { it.contains("Short trace") }
                )
            }
        }
    }

    context("sendDailyDigest") {
        test("should send daily statistics") {
            val stats = DailyStats(
                successfulDownloads = 100,
                totalErrors = 5,
                parsingErrors = 2,
                downloadErrors = 3,
                systemErrors = 0,
                activeUsers = 25,
                queuedRequests = 10
            )

            service.sendDailyDigest(stats)

            verify {
                telegramSenderService.sendMessage(
                    chatId = 12345L,
                    text = match {
                        it.contains("Дневная статистика") &&
                        it.contains("Загружено видео: 100") &&
                        it.contains("Ошибок: 5") &&
                        it.contains("Активных пользователей: 25")
                    }
                )
            }
        }

        test("should not send digest when disabled") {
            adminConfig = AdminConfig(userId = 12345L, enableNotifications = true, enableDailyDigest = false, enableWeeklyDigest = false)
            service = AdminNotificationService(adminConfig, telegramSenderService, messageTemplateService)

            val stats = DailyStats(
                successfulDownloads = 100,
                totalErrors = 5,
                parsingErrors = 2,
                downloadErrors = 3,
                systemErrors = 0,
                activeUsers = 25,
                queuedRequests = 10
            )

            service.sendDailyDigest(stats)

            verify(exactly = 0) { telegramSenderService.sendMessage(any(), any()) }
        }
    }

    context("sendNotification") {
        test("should send arbitrary notification") {
            service.sendNotification("Test notification")

            verify {
                telegramSenderService.sendMessage(12345L, "Test notification")
            }
        }

        test("should handle telegram send failure gracefully") {
            every { telegramSenderService.sendMessage(any(), any()) } throws Exception("Telegram API error")

            // Should not throw
            service.sendNotification("Test notification")

            verify { telegramSenderService.sendMessage(12345L, "Test notification") }
        }
    }
})
