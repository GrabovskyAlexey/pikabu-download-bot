package com.pikabu.bot.service.ratelimit

import com.pikabu.bot.config.RateLimiterConfig
import com.pikabu.bot.domain.exception.RateLimitExceededException
import com.pikabu.bot.entity.RateLimitEntity
import com.pikabu.bot.repository.RateLimitRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class RateLimiterService(
    private val rateLimitRepository: RateLimitRepository,
    private val rateLimiterConfig: RateLimiterConfig
) {

    /**
     * Проверяет rate limit для пользователя
     * @throws RateLimitExceededException если лимит превышен
     */
    @Transactional
    fun checkRateLimit(userId: Long) {
        val now = LocalDateTime.now()
        val rateLimitEntity = rateLimitRepository.findByUserId(userId).orElse(null)

        if (rateLimitEntity == null) {
            // Первый запрос пользователя - создаем новую запись
            createNewRateLimitEntry(userId, now)
            logger.debug { "Created new rate limit entry for user $userId" }
            return
        }

        // Проверяем, истекло ли окно
        val windowEnd = rateLimitEntity.windowStart.plusHours(rateLimiterConfig.windowHours.toLong())

        if (now.isAfter(windowEnd)) {
            // Окно истекло - сбрасываем счетчик
            resetRateLimitWindow(rateLimitEntity, now)
            logger.debug { "Rate limit window expired for user $userId, resetting" }
            return
        }

        // Окно активно - проверяем лимит
        if (rateLimitEntity.requestCount >= rateLimiterConfig.maxRequests) {
            val remainingTime = calculateRemainingTime(windowEnd, now)
            logger.warn { "Rate limit exceeded for user $userId: ${rateLimitEntity.requestCount}/${rateLimiterConfig.maxRequests}" }
            throw RateLimitExceededException(
                "Превышен лимит запросов (${rateLimiterConfig.maxRequests} в час). " +
                        "Попробуйте через $remainingTime."
            )
        }

        // Увеличиваем счетчик
        rateLimitEntity.requestCount++
        rateLimitEntity.windowEnd = windowEnd
        rateLimitRepository.save(rateLimitEntity)

        logger.debug { "Rate limit check passed for user $userId: ${rateLimitEntity.requestCount}/${rateLimiterConfig.maxRequests}" }
    }

    /**
     * Получает информацию о rate limit для пользователя
     */
    fun getRateLimitInfo(userId: Long): RateLimitInfo {
        val rateLimitEntity = rateLimitRepository.findByUserId(userId).orElse(null)
            ?: return RateLimitInfo(
                currentCount = 0,
                maxRequests = rateLimiterConfig.maxRequests,
                windowStart = LocalDateTime.now(),
                windowEnd = LocalDateTime.now().plusHours(rateLimiterConfig.windowHours.toLong()),
                remainingRequests = rateLimiterConfig.maxRequests
            )

        val windowEnd = rateLimitEntity.windowStart.plusHours(rateLimiterConfig.windowHours.toLong())
        val now = LocalDateTime.now()

        // Если окно истекло, возвращаем сброшенные данные
        if (now.isAfter(windowEnd)) {
            return RateLimitInfo(
                currentCount = 0,
                maxRequests = rateLimiterConfig.maxRequests,
                windowStart = now,
                windowEnd = now.plusHours(rateLimiterConfig.windowHours.toLong()),
                remainingRequests = rateLimiterConfig.maxRequests
            )
        }

        return RateLimitInfo(
            currentCount = rateLimitEntity.requestCount,
            maxRequests = rateLimiterConfig.maxRequests,
            windowStart = rateLimitEntity.windowStart,
            windowEnd = windowEnd,
            remainingRequests = (rateLimiterConfig.maxRequests - rateLimitEntity.requestCount).coerceAtLeast(0)
        )
    }

    /**
     * Сбрасывает rate limit для пользователя (для тестирования/админа)
     */
    @Transactional
    fun resetRateLimit(userId: Long) {
        rateLimitRepository.deleteByUserId(userId)
        logger.info { "Rate limit reset for user $userId" }
    }

    /**
     * Создает новую запись rate limit
     */
    private fun createNewRateLimitEntry(userId: Long, now: LocalDateTime) {
        val entity = RateLimitEntity(
            userId = userId,
            requestCount = 1,
            windowStart = now,
            windowEnd = now.plusHours(rateLimiterConfig.windowHours.toLong())
        )
        rateLimitRepository.save(entity)
    }

    /**
     * Сбрасывает окно rate limit
     */
    private fun resetRateLimitWindow(entity: RateLimitEntity, now: LocalDateTime) {
        entity.requestCount = 1
        entity.windowStart = now
        entity.windowEnd = now.plusHours(rateLimiterConfig.windowHours.toLong())
        rateLimitRepository.save(entity)
    }

    /**
     * Вычисляет оставшееся время до конца окна
     */
    private fun calculateRemainingTime(windowEnd: LocalDateTime, now: LocalDateTime): String {
        val duration = java.time.Duration.between(now, windowEnd)
        val minutes = duration.toMinutes()
        val seconds = duration.seconds % 60

        return when {
            minutes > 60 -> "${minutes / 60} ч ${minutes % 60} мин"
            minutes > 0 -> "$minutes мин $seconds сек"
            else -> "$seconds сек"
        }
    }
}

data class RateLimitInfo(
    val currentCount: Int,
    val maxRequests: Int,
    val windowStart: LocalDateTime,
    val windowEnd: LocalDateTime,
    val remainingRequests: Int
)
