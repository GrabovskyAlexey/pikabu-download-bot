package com.pikabu.bot.service.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

/**
 * Сервис для отслеживания кастомных метрик приложения
 */
@Service
class MetricsService(private val meterRegistry: MeterRegistry) {

    // Счетчики загрузок
    private val successfulDownloadsCounter: Counter = Counter.builder("bot.downloads.successful")
        .description("Total number of successful video downloads")
        .tag("type", "video")
        .register(meterRegistry)

    private val failedDownloadsCounter: Counter = Counter.builder("bot.downloads.failed")
        .description("Total number of failed video downloads")
        .tag("type", "video")
        .register(meterRegistry)

    // Счетчики кэша
    private val cacheHitsCounter: Counter = Counter.builder("bot.cache.hits")
        .description("Number of cache hits for video file_id")
        .register(meterRegistry)

    private val cacheMissesCounter: Counter = Counter.builder("bot.cache.misses")
        .description("Number of cache misses for video file_id")
        .register(meterRegistry)

    // Счетчик пользователей
    private val uniqueUsersCounter: Counter = Counter.builder("bot.users.unique")
        .description("Total number of unique users who used the bot")
        .register(meterRegistry)

    // Счетчики ошибок по типам
    private val parsingErrorsCounter: Counter = Counter.builder("bot.errors.parsing")
        .description("Number of parsing errors")
        .register(meterRegistry)

    private val downloadErrorsCounter: Counter = Counter.builder("bot.errors.download")
        .description("Number of download errors")
        .register(meterRegistry)

    private val rateLimitErrorsCounter: Counter = Counter.builder("bot.errors.ratelimit")
        .description("Number of rate limit exceeded events")
        .register(meterRegistry)

    // Gauge для текущего размера очереди
    private val currentQueueSize = AtomicInteger(0)
    init {
        Gauge.builder("bot.queue.size", currentQueueSize) { it.get().toDouble() }
            .description("Current number of videos in download queue")
            .register(meterRegistry)
    }

    // Gauge для активных загрузок
    private val activeDownloads = AtomicInteger(0)
    init {
        Gauge.builder("bot.downloads.active", activeDownloads) { it.get().toDouble() }
            .description("Number of currently active downloads")
            .register(meterRegistry)
    }

    // Таймер для времени загрузки
    private val downloadTimer: Timer = Timer.builder("bot.downloads.duration")
        .description("Duration of video downloads")
        .register(meterRegistry)

    // Таймер для времени парсинга
    private val parseTimer: Timer = Timer.builder("bot.parsing.duration")
        .description("Duration of video page parsing")
        .register(meterRegistry)

    /**
     * Записывает успешную загрузку
     */
    fun recordSuccessfulDownload() {
        successfulDownloadsCounter.increment()
    }

    /**
     * Записывает неудачную загрузку
     */
    fun recordFailedDownload() {
        failedDownloadsCounter.increment()
    }

    /**
     * Записывает cache hit
     */
    fun recordCacheHit() {
        cacheHitsCounter.increment()
    }

    /**
     * Записывает cache miss
     */
    fun recordCacheMiss() {
        cacheMissesCounter.increment()
    }

    /**
     * Записывает нового уникального пользователя
     */
    fun recordUniqueUser() {
        uniqueUsersCounter.increment()
    }

    /**
     * Записывает ошибку парсинга
     */
    fun recordParsingError() {
        parsingErrorsCounter.increment()
    }

    /**
     * Записывает ошибку загрузки
     */
    fun recordDownloadError() {
        downloadErrorsCounter.increment()
    }

    /**
     * Записывает превышение rate limit
     */
    fun recordRateLimitExceeded() {
        rateLimitErrorsCounter.increment()
    }

    /**
     * Обновляет размер очереди
     */
    fun updateQueueSize(size: Int) {
        currentQueueSize.set(size)
    }

    /**
     * Увеличивает счетчик активных загрузок
     */
    fun incrementActiveDownloads() {
        activeDownloads.incrementAndGet()
    }

    /**
     * Уменьшает счетчик активных загрузок
     */
    fun decrementActiveDownloads() {
        activeDownloads.decrementAndGet()
    }

    /**
     * Записывает время загрузки видео
     */
    fun recordDownloadDuration(durationMs: Long) {
        downloadTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /**
     * Записывает время парсинга страницы
     */
    fun recordParseDuration(durationMs: Long) {
        parseTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /**
     * Получает текущее количество активных загрузок
     */
    fun getActiveDownloadsCount(): Int = activeDownloads.get()

    /**
     * Получает текущий размер очереди
     */
    fun getCurrentQueueSize(): Int = currentQueueSize.get()
}
