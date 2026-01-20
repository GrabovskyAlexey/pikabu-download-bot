package com.pikabu.bot.service.queue

import com.pikabu.bot.domain.model.QueueStatus
import com.pikabu.bot.entity.DownloadHistoryEntity
import com.pikabu.bot.entity.DownloadQueueEntity
import com.pikabu.bot.repository.DownloadHistoryRepository
import com.pikabu.bot.repository.DownloadQueueRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class QueueService(
    private val downloadQueueRepository: DownloadQueueRepository,
    private val downloadHistoryRepository: DownloadHistoryRepository,
    private val metricsService: com.pikabu.bot.service.metrics.MetricsService
) {

    /**
     * Добавляет запрос в очередь
     */
    @Transactional
    fun addToQueue(
        userId: Long,
        messageId: Int,
        videoUrl: String,
        videoTitle: String? = null
    ): DownloadQueueEntity {
        val queueEntity = DownloadQueueEntity(
            userId = userId,
            messageId = messageId,
            videoUrl = videoUrl,
            videoTitle = videoTitle,
            status = QueueStatus.QUEUED,
            position = calculateNextPosition(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val saved = downloadQueueRepository.save(queueEntity)
        logger.info { "Added to queue: user=$userId, video=$videoUrl, position=${saved.position}" }

        // Обновляем метрику размера очереди
        updateQueueSizeMetric()

        return saved
    }

    /**
     * Обновляет статус запроса
     */
    @Transactional
    fun updateStatus(queueId: Long, newStatus: QueueStatus) {
        val entity = downloadQueueRepository.findById(queueId).orElse(null)
        if (entity != null) {
            entity.status = newStatus
            entity.updatedAt = LocalDateTime.now()
            downloadQueueRepository.save(entity)
            logger.debug { "Updated status: queueId=$queueId, status=$newStatus" }
        } else {
            logger.warn { "Queue entity not found: $queueId" }
        }
    }

    /**
     * Получает текущую позицию в очереди
     */
    fun getQueuePosition(queueId: Long): Int? {
        val entity = downloadQueueRepository.findById(queueId).orElse(null)
        return entity?.position
    }

    /**
     * Получает следующие запросы в очереди
     */
    fun getNextPendingRequests(limit: Int): List<DownloadQueueEntity> {
        val allQueued = downloadQueueRepository.findByStatusOrderByCreatedAtAsc(QueueStatus.QUEUED)
        return allQueued.take(limit)
    }

    /**
     * Получает все запросы в статусе QUEUED
     */
    fun getAllQueuedRequests(): List<DownloadQueueEntity> {
        return downloadQueueRepository.findByStatusOrderByCreatedAtAsc(QueueStatus.QUEUED)
    }

    /**
     * Получает все запросы в статусе DOWNLOADING
     */
    fun getDownloadingRequests(): List<DownloadQueueEntity> {
        return downloadQueueRepository.findByStatus(QueueStatus.DOWNLOADING)
    }

    /**
     * Получает количество запросов в статусе
     */
    fun countByStatus(status: QueueStatus): Long {
        return downloadQueueRepository.countByStatus(status)
    }

    /**
     * Архивирует завершенный запрос в историю
     */
    @Transactional
    fun archiveToHistory(queueEntity: DownloadQueueEntity) {
        val historyEntity = DownloadHistoryEntity(
            userId = queueEntity.userId,
            videoUrl = queueEntity.videoUrl,
            videoTitle = queueEntity.videoTitle,
            status = queueEntity.status.name,
            createdAt = queueEntity.createdAt,
            completedAt = LocalDateTime.now()
        )

        downloadHistoryRepository.save(historyEntity)
        downloadQueueRepository.delete(queueEntity)

        logger.debug { "Archived to history: user=${queueEntity.userId}, video=${queueEntity.videoUrl}, status=${queueEntity.status}" }

        // Обновляем метрику размера очереди
        updateQueueSizeMetric()
    }

    /**
     * Перерасчитывает позиции в очереди
     */
    @Transactional
    fun recalculatePositions() {
        val queuedRequests = downloadQueueRepository.findByStatusOrderByCreatedAtAsc(QueueStatus.QUEUED)

        queuedRequests.forEachIndexed { index, entity ->
            entity.position = index + 1
            entity.updatedAt = LocalDateTime.now()
        }

        downloadQueueRepository.saveAll(queuedRequests)
        logger.debug { "Recalculated positions for ${queuedRequests.size} queued requests" }
    }

    /**
     * Вычисляет следующую позицию в очереди
     */
    private fun calculateNextPosition(): Int {
        val queuedCount = downloadQueueRepository.countByStatus(QueueStatus.QUEUED)
        return (queuedCount + 1).toInt()
    }

    /**
     * Удаляет запрос из очереди
     */
    @Transactional
    fun removeFromQueue(queueId: Long) {
        downloadQueueRepository.deleteById(queueId)
        logger.debug { "Removed from queue: $queueId" }
        recalculatePositions()
    }

    /**
     * Получает запрос по ID
     */
    fun getById(queueId: Long): DownloadQueueEntity? {
        return downloadQueueRepository.findById(queueId).orElse(null)
    }

    /**
     * Обновляет метрику размера очереди
     */
    private fun updateQueueSizeMetric() {
        val queueSize = downloadQueueRepository.countByStatus(QueueStatus.QUEUED).toInt()
        metricsService.updateQueueSize(queueSize)
    }
}
