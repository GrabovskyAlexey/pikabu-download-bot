package com.pikabu.bot.service.metrics

import com.pikabu.bot.repository.DownloadHistoryRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Аудитория бота как метрики: пользователи, хотя бы раз скачавшие видео (всего), и скачавшие за последние сутки и 7 дней.
 * Читаются из истории загрузок при каждом сборе метрик.
 */
@Component
class AudienceMetrics(
    private val history: DownloadHistoryRepository,
) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        Gauge.builder("bot.users") { history.countDistinctUsers().toDouble() }
            .description("Пользователи, скачавшие видео").register(registry)
        Gauge.builder("bot.users.active") { history.countDistinctUsersSince(LocalDateTime.now().minusDays(1)).toDouble() }
            .tag("period", "day").description("Активные пользователи за период").register(registry)
        Gauge.builder("bot.users.active") { history.countDistinctUsersSince(LocalDateTime.now().minusDays(7)).toDouble() }
            .tag("period", "week").description("Активные пользователи за период").register(registry)
    }
}
