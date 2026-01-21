package com.pikabu.bot.service.cache

import com.pikabu.bot.entity.VideoCacheEntity
import com.pikabu.bot.repository.VideoCacheRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class VideoCacheService(
    private val videoCacheRepository: VideoCacheRepository
) {

    /**
     * Получает file_id из кэша для указанного URL
     */
    @Transactional
    fun getFileId(videoUrl: String): String? {
        val cached = videoCacheRepository.findById(videoUrl).orElse(null)

        if (cached != null) {
            // Обновляем время последнего использования
            videoCacheRepository.updateLastUsedAt(videoUrl, LocalDateTime.now())
            logger.debug { "Cache HIT for URL: $videoUrl (file_id: ${cached.fileId})" }
            return cached.fileId
        }

        logger.debug { "Cache MISS for URL: $videoUrl" }
        return null
    }

    /**
     * Получает полную запись кэша для указанного URL
     */
    @Transactional
    fun getCacheEntry(videoUrl: String): VideoCacheEntity? {
        val cached = videoCacheRepository.findById(videoUrl).orElse(null)

        if (cached != null) {
            // Обновляем время последнего использования
            videoCacheRepository.updateLastUsedAt(videoUrl, LocalDateTime.now())
            logger.debug { "Cache HIT for URL: $videoUrl" }
        }

        return cached
    }

    /**
     * Сохраняет file_id в кэш
     */
    @Transactional
    fun saveFileId(videoUrl: String, fileId: String, fileSize: Long?) {
        try {
            val entity = VideoCacheEntity(
                videoUrl = videoUrl,
                fileId = fileId,
                fileSize = fileSize
            )
            videoCacheRepository.save(entity)
            logger.debug { "Cached file_id for URL: $videoUrl (size: $fileSize bytes)" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to cache file_id for URL: $videoUrl" }
        }
    }

    /**
     * Получает количество записей в кэше
     */
    fun getCacheSize(): Long {
        return videoCacheRepository.count()
    }

    /**
     * Получает все записи кэша
     */
    fun getAllCacheEntries(): List<VideoCacheEntity> {
        return videoCacheRepository.findAll()
    }

    /**
     * Очищает старые записи кэша (старше 30 дней)
     * Запускается раз в день в 3:00
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupOldCache() {
        try {
            val cutoffDate = LocalDateTime.now().minusDays(30)
            val deleted = videoCacheRepository.deleteOlderThan(cutoffDate)

            if (deleted > 0) {
                logger.info { "Cleaned up $deleted old cache entries (older than 30 days)" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to cleanup old cache entries" }
        }
    }
}
