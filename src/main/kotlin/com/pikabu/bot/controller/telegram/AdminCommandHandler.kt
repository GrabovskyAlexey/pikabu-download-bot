package com.pikabu.bot.controller.telegram

import com.pikabu.bot.config.AdminConfig
import com.pikabu.bot.domain.model.QueueStatus
import com.pikabu.bot.repository.DownloadHistoryRepository
import com.pikabu.bot.repository.DownloadQueueRepository
import com.pikabu.bot.repository.ErrorLogRepository
import com.pikabu.bot.service.auth.CookieUpdateService
import com.pikabu.bot.service.cache.VideoCacheService
import com.pikabu.bot.service.telegram.AdminState
import com.pikabu.bot.service.telegram.AdminStateService
import com.pikabu.bot.service.telegram.TelegramSenderService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Component
class AdminCommandHandler(
    private val adminConfig: AdminConfig,
    private val telegramSenderService: TelegramSenderService,
    private val downloadHistoryRepository: DownloadHistoryRepository,
    private val downloadQueueRepository: DownloadQueueRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val videoCacheService: VideoCacheService,
    private val cookieUpdateService: CookieUpdateService,
    private val adminStateService: AdminStateService
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

    /**
     * Проверяет является ли пользователь админом
     */
    fun isAdmin(userId: Long): Boolean = userId == adminConfig.userId

    /**
     * Обрабатывает админ-команду
     * Возвращает true если команда была обработана
     */
    fun handleAdminCommand(chatId: Long, command: String): Boolean {
        if (!isAdmin(chatId)) {
            return false
        }

        logger.debug { "Processing admin command: $command" }

        when {
            command.startsWith("/stats") -> handleStatsCommand(chatId)
            command.startsWith("/health") -> handleHealthCommand(chatId)
            command.startsWith("/queue") -> handleQueueCommand(chatId)
            command.startsWith("/cache") -> handleCacheCommand(chatId)
            command.startsWith("/errors") -> handleErrorsCommand(chatId, command)
            command.startsWith("/update_auth") -> handleUpdateAuthCommand(chatId, command)
            command.startsWith("/auth_status") -> handleAuthStatusCommand(chatId)
            command.startsWith("/cancel") -> handleCancelCommand(chatId)
            else -> return false
        }

        return true
    }

    /**
     * /stats - Общая статистика
     */
    private fun handleStatsCommand(chatId: Long) {
        val now = LocalDateTime.now()
        val last24h = now.minusHours(24)
        val last7d = now.minusDays(7)

        // Статистика загрузок
        val totalDownloads = downloadHistoryRepository.count()
        val downloadsLast24h = downloadHistoryRepository.countByCreatedAtAfter(last24h)
        val downloadsLast7d = downloadHistoryRepository.countByCreatedAtAfter(last7d)
        val successfulDownloads = downloadHistoryRepository.countByStatus("COMPLETED")
        val failedDownloads = downloadHistoryRepository.countByStatus("FAILED")

        // Статистика очереди
        val queueSize = downloadQueueRepository.countByStatus(QueueStatus.QUEUED)
        val processingCount = downloadQueueRepository.countByStatus(QueueStatus.DOWNLOADING)

        // Статистика кэша
        val cacheSize = videoCacheService.getCacheSize()

        // Уникальные пользователи
        val uniqueUsers = downloadHistoryRepository.countDistinctUsers()

        val message = """
            📊 **Статистика бота**

            **Загрузки:**
            • Всего: $totalDownloads
            • За 24 часа: $downloadsLast24h
            • За 7 дней: $downloadsLast7d
            • Успешных: $successfulDownloads
            • Ошибок: $failedDownloads
            • Success rate: ${if (totalDownloads > 0) "%.1f%%".format(successfulDownloads * 100.0 / totalDownloads) else "N/A"}

            **Очередь:**
            • В очереди: $queueSize
            • В обработке: $processingCount

            **Кэш:**
            • Видео в кэше: $cacheSize

            **Пользователи:**
            • Уникальных пользователей: $uniqueUsers

            _Обновлено: ${now.format(dateFormatter)}_
        """.trimIndent()

        telegramSenderService.sendMessage(chatId, message, parseMode = "Markdown")
    }

    /**
     * /health - Состояние системы
     */
    private fun handleHealthCommand(chatId: Long) {
        val now = LocalDateTime.now()

        // Проверяем активность системы
        val lastDownloadTime = downloadHistoryRepository.findTopByOrderByCreatedAtDesc()?.createdAt
        val lastErrorTime = errorLogRepository.findTopByOrderByOccurredAtDesc()?.occurredAt

        val queuedCount = downloadQueueRepository.countByStatus(QueueStatus.QUEUED)
        val processingCount = downloadQueueRepository.countByStatus(QueueStatus.DOWNLOADING)

        // Проверяем зависшие задачи (в обработке больше 30 минут)
        val stuckTasks = downloadQueueRepository.findByStatus(QueueStatus.DOWNLOADING)
            .filter { it.updatedAt?.isBefore(now.minusMinutes(30)) == true }
            .size

        val healthStatus = when {
            stuckTasks > 0 -> "⚠️ ПРЕДУПРЕЖДЕНИЕ"
            processingCount > 0 -> "✅ РАБОТАЕТ"
            queuedCount > 0 -> "💤 ОЖИДАНИЕ"
            else -> "✅ ЗДОРОВ"
        }

        val message = """
            🏥 **Состояние системы**

            **Статус:** $healthStatus

            **Очередь:**
            • В очереди: $queuedCount
            • В обработке: $processingCount
            • Зависших задач: $stuckTasks

            **Активность:**
            • Последняя загрузка: ${lastDownloadTime?.let { formatTimeAgo(it, now) } ?: "Никогда"}
            • Последняя ошибка: ${lastErrorTime?.let { formatTimeAgo(it, now) } ?: "Никогда"}

            _Проверено: ${now.format(dateFormatter)}_
        """.trimIndent()

        telegramSenderService.sendMessage(chatId, message, parseMode = "Markdown")
    }

    /**
     * /queue - Состояние очереди
     */
    private fun handleQueueCommand(chatId: Long) {
        val queuedTasks = downloadQueueRepository.findByStatusOrderByCreatedAtAsc(QueueStatus.QUEUED)
        val processingTasks = downloadQueueRepository.findByStatus(QueueStatus.DOWNLOADING)

        if (queuedTasks.isEmpty() && processingTasks.isEmpty()) {
            telegramSenderService.sendMessage(chatId, "✅ Очередь пуста")
            return
        }

        val message = buildString {
            append("📋 **Очередь загрузок**\n\n")

            if (processingTasks.isNotEmpty()) {
                append("**В обработке (${processingTasks.size}):**\n")
                processingTasks.take(5).forEach { task ->
                    val title = task.videoTitle?.take(40) ?: "Без названия"
                    val duration = task.updatedAt?.let { Duration.between(it, LocalDateTime.now()) }
                    val durationStr = duration?.let { " (${formatDuration(it)})" } ?: ""
                    append("• $title$durationStr\n")
                }
                if (processingTasks.size > 5) {
                    append("  ...и ещё ${processingTasks.size - 5}\n")
                }
                append("\n")
            }

            if (queuedTasks.isNotEmpty()) {
                append("**В очереди (${queuedTasks.size}):**\n")
                queuedTasks.take(10).forEach { task ->
                    val title = task.videoTitle?.take(40) ?: "Без названия"
                    val waitTime = Duration.between(task.createdAt, LocalDateTime.now())
                    append("• ${task.position}. $title (ждёт ${formatDuration(waitTime)})\n")
                }
                if (queuedTasks.size > 10) {
                    append("  ...и ещё ${queuedTasks.size - 10}\n")
                }
            }
        }

        telegramSenderService.sendMessage(chatId, message, parseMode = "Markdown")
    }

    /**
     * /cache - Статистика кэша
     */
    private fun handleCacheCommand(chatId: Long) {
        val cacheSize = videoCacheService.getCacheSize()
        val allCacheEntries = videoCacheService.getAllCacheEntries()

        val totalSizeBytes = allCacheEntries.sumOf { it.fileSize ?: 0L }
        val totalSizeMb = totalSizeBytes / (1024.0 * 1024.0)

        val avgSizeMb = if (cacheSize > 0) totalSizeMb / cacheSize else 0.0

        val message = """
            💾 **Кэш видео**

            **Размер:**
            • Всего видео: $cacheSize
            • Общий размер: %.2f МБ
            • Средний размер: %.2f МБ

            **Последние закэшированные:**
        """.trimIndent().format(totalSizeMb, avgSizeMb)

        val recentEntries = allCacheEntries
            .sortedByDescending { it.cachedAt }
            .take(10)

        val fullMessage = buildString {
            append(message)
            if (recentEntries.isNotEmpty()) {
                append("\n")
                recentEntries.forEach { entry ->
                    val sizeMb = (entry.fileSize ?: 0L) / (1024.0 * 1024.0)
                    val timeAgo = formatTimeAgo(entry.cachedAt, LocalDateTime.now())
                    append("• %.1f МБ - $timeAgo\n".format(sizeMb))
                }
            } else {
                append("\nКэш пуст")
            }
        }

        telegramSenderService.sendMessage(chatId, fullMessage, parseMode = "Markdown")
    }

    /**
     * /errors [limit] - Последние ошибки (по умолчанию 10)
     */
    private fun handleErrorsCommand(chatId: Long, command: String) {
        val limit = command.split(" ").getOrNull(1)?.toIntOrNull() ?: 10
        val errors = errorLogRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit.coerceIn(1, 50)))

        if (errors.isEmpty()) {
            telegramSenderService.sendMessage(chatId, "✅ Ошибок не найдено")
            return
        }

        val message = buildString {
            append("❌ **Последние ошибки (${errors.size}):**\n\n")

            errors.forEach { error ->
                val timeAgo = formatTimeAgo(error.occurredAt, LocalDateTime.now())
                val errorMsg = error.errorMessage.take(100)
                val pageUrl = error.pageUrl?.take(50) ?: "N/A"

                append("**${error.errorType}** ($timeAgo)\n")
                append("• $errorMsg\n")
                append("• URL: $pageUrl\n")
                append("\n")
            }
        }

        // Разбиваем на части если сообщение слишком длинное
        if (message.length > 4000) {
            val parts = message.chunked(4000)
            parts.forEach { part ->
                telegramSenderService.sendMessage(chatId, part, parseMode = "Markdown")
            }
        } else {
            telegramSenderService.sendMessage(chatId, message, parseMode = "Markdown")
        }
    }

    /**
     * Форматирует время "X назад"
     */
    private fun formatTimeAgo(time: LocalDateTime, now: LocalDateTime): String {
        val duration = Duration.between(time, now)
        return when {
            duration.toMinutes() < 1 -> "только что"
            duration.toMinutes() < 60 -> "${duration.toMinutes()} мин назад"
            duration.toHours() < 24 -> "${duration.toHours()} ч назад"
            duration.toDays() < 7 -> "${duration.toDays()} дн назад"
            else -> time.format(dateFormatter)
        }
    }

    /**
     * Форматирует длительность
     */
    private fun formatDuration(duration: Duration): String {
        return when {
            duration.toMinutes() < 1 -> "${duration.seconds} сек"
            duration.toHours() < 1 -> "${duration.toMinutes()} мин"
            duration.toDays() < 1 -> "${duration.toHours()} ч ${duration.toMinutesPart()} мин"
            else -> "${duration.toDays()} дн ${duration.toHoursPart()} ч"
        }
    }

    /**
     * /update_auth - Обновление cookies для авторизации на Pikabu (диалоговый режим)
     */
    private fun handleUpdateAuthCommand(chatId: Long, command: String) {
        // Устанавливаем состояние ожидания cookies
        adminStateService.setState(chatId, AdminState.WAITING_FOR_COOKIES)

        val message = """
            🔑 **Обновление авторизации Pikabu**

            **Как получить cookies:**
            1. Откройте pikabu.ru в браузере
            2. Авторизуйтесь на сайте
            3. Нажмите F12 → Application → Cookies → https://pikabu.ru
            4. Найдите cookie `PHPSESS` (главная для авторизации)
            5. Скопируйте значение

            **Теперь отправьте мне cookies в следующем сообщении:**

            Формат: `PHPSESS=значение`
            или несколько: `PHPSESS=abc123; other=xyz456`

            **Важно:** Основная cookie - это `PHPSESS`

            Отправьте /cancel для отмены.
        """.trimIndent()

        telegramSenderService.sendMessage(chatId, message, parseMode = "Markdown")
        logger.debug { "Admin $chatId entered cookie update mode" }
    }

    /**
     * Обрабатывает ввод cookies от админа
     */
    fun handleCookieInput(chatId: Long, cookieString: String) {
        try {
            logger.info { "Admin $chatId is updating Pikabu cookies" }

            // Обновляем cookies в HTTP клиенте и сохраняем в БД
            cookieUpdateService.updateCookies(cookieString, adminUserId = chatId.toString())

            val message = """
                ✅ **Cookies успешно обновлены!**

                Авторизация на Pikabu активна.
                Cookies сохранены в базе данных.
                Теперь можно скачивать защищённые видео.

                Используйте `/auth_status` для проверки.
            """.trimIndent()

            telegramSenderService.sendMessage(chatId, message, parseMode = "Markdown")

            logger.info { "Pikabu cookies updated successfully by admin $chatId" }

            // Очищаем состояние
            adminStateService.clearState(chatId)

        } catch (e: Exception) {
            logger.error(e) { "Failed to update cookies for admin $chatId" }

            val errorMessage = """
                ❌ **Ошибка обновления cookies**

                ${e.message}

                Попробуйте ещё раз или отправьте /cancel для отмены.
            """.trimIndent()

            telegramSenderService.sendMessage(chatId, errorMessage, parseMode = "Markdown")
        }
    }

    /**
     * /auth_status - Проверка статуса авторизации
     */
    private fun handleAuthStatusCommand(chatId: Long) {
        try {
            val status = cookieUpdateService.getCookieStatus()

            val message = """
                🔐 **Статус авторизации Pikabu**

                $status

                Используйте `/update_auth` для обновления cookies.
            """.trimIndent()

            telegramSenderService.sendMessage(chatId, message, parseMode = "Markdown")

        } catch (e: Exception) {
            logger.error(e) { "Failed to check auth status for admin $chatId" }

            val errorMessage = """
                ❌ **Ошибка проверки статуса**

                ${e.message}
            """.trimIndent()

            telegramSenderService.sendMessage(chatId, errorMessage, parseMode = "Markdown")
        }
    }

    /**
     * /cancel - Отмена текущей операции
     */
    private fun handleCancelCommand(chatId: Long) {
        if (adminStateService.hasState(chatId)) {
            adminStateService.clearState(chatId)
            telegramSenderService.sendMessage(chatId, "❌ Операция отменена")
            logger.debug { "Admin $chatId cancelled operation" }
        } else {
            telegramSenderService.sendMessage(chatId, "Нет активных операций для отмены")
        }
    }
}
