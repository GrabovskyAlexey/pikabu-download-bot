package com.pikabu.bot.service.admin

import com.pikabu.bot.domain.model.ErrorType
import com.pikabu.bot.repository.DownloadHistoryRepository
import com.pikabu.bot.repository.DownloadQueueRepository
import com.pikabu.bot.repository.ErrorLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class DigestSchedulerService(
    private val adminNotificationService: AdminNotificationService,
    private val downloadHistoryRepository: DownloadHistoryRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val downloadQueueRepository: DownloadQueueRepository
) {

    /**
     * Отправляет дневной дайджест каждый день в 9:00
     */
    @Scheduled(cron = "0 0 9 * * *")
    fun sendDailyDigest() {
        logger.info { "Starting daily digest generation" }

        try {
            val stats = collectDailyStats()
            adminNotificationService.sendDailyDigest(stats)
            logger.info { "Daily digest sent successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send daily digest" }
        }
    }

    /**
     * Отправляет недельный дайджест каждый понедельник в 9:00
     */
    @Scheduled(cron = "0 0 9 * * MON")
    fun sendWeeklyDigest() {
        logger.info { "Starting weekly digest generation" }

        try {
            val stats = collectWeeklyStats()
            adminNotificationService.sendWeeklyDigest(stats)
            logger.info { "Weekly digest sent successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send weekly digest" }
        }
    }

    /**
     * Собирает статистику за последние 24 часа
     */
    private fun collectDailyStats(): DailyStats {
        val since = LocalDateTime.now().minusDays(1)

        val successfulDownloads = downloadHistoryRepository.countCompletedSince(since).toInt()

        val parsingErrors = errorLogRepository.countByErrorTypeSince(
            ErrorType.PARSING_ERROR.name,
            since
        ).toInt()

        val downloadErrors = errorLogRepository.countByErrorTypeSince(
            ErrorType.DOWNLOAD_ERROR.name,
            since
        ).toInt()

        val systemErrors = errorLogRepository.countByErrorTypeSince(
            ErrorType.SYSTEM_ERROR.name,
            since
        ).toInt()

        val totalErrors = parsingErrors + downloadErrors + systemErrors

        // Подсчет активных пользователей за последние 24 часа
        val recentHistory = downloadHistoryRepository.findCompletedSince(since)
        val activeUsers = recentHistory.map { it.userId }.distinct().size

        // Текущая очередь
        val queuedRequests = downloadQueueRepository.countByStatus(
            com.pikabu.bot.domain.model.QueueStatus.QUEUED
        ).toInt()

        return DailyStats(
            successfulDownloads = successfulDownloads,
            totalErrors = totalErrors,
            parsingErrors = parsingErrors,
            downloadErrors = downloadErrors,
            systemErrors = systemErrors,
            activeUsers = activeUsers,
            queuedRequests = queuedRequests
        )
    }

    /**
     * Собирает статистику за последние 7 дней
     */
    private fun collectWeeklyStats(): WeeklyStats {
        val since = LocalDateTime.now().minusDays(7)

        val successfulDownloads = downloadHistoryRepository.countCompletedSince(since).toInt()

        val parsingErrors = errorLogRepository.countByErrorTypeSince(
            ErrorType.PARSING_ERROR.name,
            since
        ).toInt()

        val downloadErrors = errorLogRepository.countByErrorTypeSince(
            ErrorType.DOWNLOAD_ERROR.name,
            since
        ).toInt()

        val systemErrors = errorLogRepository.countByErrorTypeSince(
            ErrorType.SYSTEM_ERROR.name,
            since
        ).toInt()

        val totalErrors = parsingErrors + downloadErrors + systemErrors

        // Подсчет активных пользователей за последние 7 дней
        val recentHistory = downloadHistoryRepository.findCompletedSince(since)
        val activeUsers = recentHistory.map { it.userId }.distinct().size

        // Всего уникальных пользователей в системе
        val totalUsers = downloadHistoryRepository.countDistinctUsers().toInt()

        // Текущая очередь
        val queuedRequests = downloadQueueRepository.countByStatus(
            com.pikabu.bot.domain.model.QueueStatus.QUEUED
        ).toInt()

        // Средняя активность в день
        val avgDownloadsPerDay = successfulDownloads / 7.0

        // Топ-5 популярных видео за неделю (только видео с 2+ скачиваниями)
        val topVideosData = downloadHistoryRepository.findTopVideosSince(
            since,
            PageRequest.of(0, 10) // Берем больше, потом отфильтруем
        )

        val topVideos = topVideosData
            .map { row ->
                PopularVideo(
                    videoUrl = row[0] as String,
                    videoTitle = row[1] as String?,
                    downloadCount = (row[2] as Number).toLong()
                )
            }
            .filter { it.downloadCount >= 2 } // Только видео с 2+ скачиваниями
            .take(5) // Топ-5

        return WeeklyStats(
            successfulDownloads = successfulDownloads,
            totalErrors = totalErrors,
            parsingErrors = parsingErrors,
            downloadErrors = downloadErrors,
            systemErrors = systemErrors,
            activeUsers = activeUsers,
            totalUsers = totalUsers,
            queuedRequests = queuedRequests,
            avgDownloadsPerDay = avgDownloadsPerDay,
            topVideos = topVideos
        )
    }
}
