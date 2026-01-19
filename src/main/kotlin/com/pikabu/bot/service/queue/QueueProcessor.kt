package com.pikabu.bot.service.queue

import com.pikabu.bot.domain.model.QueueStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class QueueProcessor(
    private val queueService: QueueService,
    @Value("\${app.max-concurrent-downloads:5}")
    private val maxConcurrentDownloads: Int
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Обрабатывает очередь каждые 5 секунд
     */
    @Scheduled(fixedDelay = 5000)
    fun processQueue() {
        try {
            val downloadingCount = queueService.countByStatus(QueueStatus.DOWNLOADING)
            val availableSlots = maxConcurrentDownloads - downloadingCount.toInt()

            if (availableSlots <= 0) {
                logger.debug { "No available slots (downloading: $downloadingCount/$maxConcurrentDownloads)" }
                return
            }

            val pendingRequests = queueService.getNextPendingRequests(availableSlots)

            if (pendingRequests.isEmpty()) {
                logger.debug { "No pending requests in queue" }
                return
            }

            logger.info { "Processing ${pendingRequests.size} pending requests (available slots: $availableSlots)" }

            // Запускаем загрузку для каждого запроса в корутинах
            pendingRequests.forEach { queueEntity ->
                scope.launch {
                    try {
                        logger.info { "Starting download for queue ID: ${queueEntity.id}" }

                        // Обновляем статус на DOWNLOADING
                        queueEntity.id?.let { queueService.updateStatus(it, QueueStatus.DOWNLOADING) }

                        // TODO: Phase 6 - implement actual download logic
                        // downloadOrchestrator.download(queueEntity)

                        // Временная заглушка - симуляция загрузки
                        logger.warn { "Download system not implemented yet (Phase 6)" }
                        delay(1000) // Имитация загрузки

                        // Пока просто помечаем как завершенное и архивируем
                        queueEntity.id?.let { queueService.updateStatus(it, QueueStatus.COMPLETED) }
                        queueService.getById(queueEntity.id!!)?.let { updated ->
                            queueService.archiveToHistory(updated)
                        }

                        logger.info { "Download completed for queue ID: ${queueEntity.id}" }

                    } catch (e: Exception) {
                        logger.error(e) { "Error processing queue ID: ${queueEntity.id}" }

                        // Помечаем как failed
                        queueEntity.id?.let { queueService.updateStatus(it, QueueStatus.FAILED) }
                        queueService.getById(queueEntity.id!!)?.let { failed ->
                            queueService.archiveToHistory(failed)
                        }
                    }
                }
            }

            // Перерасчитываем позиции после обработки
            queueService.recalculatePositions()

        } catch (e: Exception) {
            logger.error(e) { "Error in queue processor" }
        }
    }

    /**
     * Получает текущую статистику очереди
     */
    fun getQueueStats(): QueueStats {
        return QueueStats(
            queued = queueService.countByStatus(QueueStatus.QUEUED),
            downloading = queueService.countByStatus(QueueStatus.DOWNLOADING),
            maxConcurrent = maxConcurrentDownloads
        )
    }
}

data class QueueStats(
    val queued: Long,
    val downloading: Long,
    val maxConcurrent: Int
)
