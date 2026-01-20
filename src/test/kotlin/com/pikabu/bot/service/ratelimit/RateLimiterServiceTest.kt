package com.pikabu.bot.service.ratelimit

import com.pikabu.bot.config.RateLimiterConfig
import com.pikabu.bot.domain.exception.RateLimitExceededException
import com.pikabu.bot.entity.RateLimitEntity
import com.pikabu.bot.repository.RateLimitRepository
import com.pikabu.bot.service.metrics.MetricsService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.LocalDateTime
import java.util.*

class RateLimiterServiceTest : FunSpec({

    lateinit var repository: RateLimitRepository
    lateinit var config: RateLimiterConfig
    lateinit var service: RateLimiterService
    val metricsService = mockk<MetricsService>(relaxed = true)

    beforeEach {
        repository = mockk(relaxed = true)
        config = RateLimiterConfig(maxRequests = 10, windowHours = 1)
        service = RateLimiterService(repository, config, metricsService)
    }

    afterEach {
        clearAllMocks()
    }

    context("checkRateLimit") {
        test("should allow first request") {
            every { repository.findByUserId(123L) } returns Optional.empty()
            every { repository.save(any()) } returnsArgument 0

            service.checkRateLimit(123L)

            verify { repository.save(match { it.requestCount == 1 }) }
        }

        test("should increment request count") {
            val entity = RateLimitEntity(
                id = 1L,
                userId = 123L,
                requestCount = 5,
                windowStart = LocalDateTime.now(),
                windowEnd = LocalDateTime.now().plusHours(1)
            )

            every { repository.findByUserId(123L) } returns Optional.of(entity)
            every { repository.save(any()) } returnsArgument 0

            service.checkRateLimit(123L)

            verify { repository.save(match { it.requestCount == 6 }) }
        }

        test("should throw exception when limit exceeded") {
            val entity = RateLimitEntity(
                id = 1L,
                userId = 123L,
                requestCount = 10,
                windowStart = LocalDateTime.now(),
                windowEnd = LocalDateTime.now().plusHours(1)
            )

            every { repository.findByUserId(123L) } returns Optional.of(entity)

            shouldThrow<RateLimitExceededException> {
                service.checkRateLimit(123L)
            }
        }

        test("should reset window when expired") {
            val entity = RateLimitEntity(
                id = 1L,
                userId = 123L,
                requestCount = 10,
                windowStart = LocalDateTime.now().minusHours(2),
                windowEnd = LocalDateTime.now().minusHours(1)
            )

            every { repository.findByUserId(123L) } returns Optional.of(entity)
            every { repository.save(any()) } returnsArgument 0

            service.checkRateLimit(123L)

            verify { repository.save(match { it.requestCount == 1 }) }
        }
    }

    context("getRateLimitInfo") {
        test("should return info for existing user") {
            val entity = RateLimitEntity(
                id = 1L,
                userId = 123L,
                requestCount = 5,
                windowStart = LocalDateTime.now(),
                windowEnd = LocalDateTime.now().plusHours(1)
            )

            every { repository.findByUserId(123L) } returns Optional.of(entity)

            val info = service.getRateLimitInfo(123L)

            info.currentCount shouldBe 5
            info.maxRequests shouldBe 10
            info.remainingRequests shouldBe 5
        }

        test("should return default info for new user") {
            every { repository.findByUserId(123L) } returns Optional.empty()

            val info = service.getRateLimitInfo(123L)

            info.currentCount shouldBe 0
            info.remainingRequests shouldBe 10
        }
    }

    context("resetRateLimit") {
        test("should delete rate limit entry") {
            every { repository.deleteByUserId(123L) } just Runs

            service.resetRateLimit(123L)

            verify { repository.deleteByUserId(123L) }
        }
    }
})
