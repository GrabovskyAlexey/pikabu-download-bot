package com.pikabu.bot.service.metrics

import com.pikabu.bot.repository.DownloadHistoryRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk

class AudienceMetricsTest : FunSpec({

    test("should expose the audience as gauges read from the repository on every scrape") {
        var total = 10L
        val history = mockk<DownloadHistoryRepository> {
            every { countDistinctUsers() } answers { total }
            every { countDistinctUsersSince(any()) } returnsMany listOf(2L, 6L, 3L, 7L)
        }
        val registry = SimpleMeterRegistry()
        AudienceMetrics(history).bindTo(registry)

        registry.get("bot.users").gauge().value() shouldBe 10.0
        total = 12
        registry.get("bot.users").gauge().value() shouldBe 12.0
        registry.get("bot.users.active").tag("period", "day").gauge().value() shouldBe 2.0
        registry.get("bot.users.active").tag("period", "week").gauge().value() shouldBe 6.0
    }
})
