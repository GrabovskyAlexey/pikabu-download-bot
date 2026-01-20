package com.pikabu.bot.service.queue

import com.pikabu.bot.domain.model.QueueStatus
import com.pikabu.bot.entity.DownloadQueueEntity
import com.pikabu.bot.repository.DownloadHistoryRepository
import com.pikabu.bot.repository.DownloadQueueRepository
import com.pikabu.bot.service.metrics.MetricsService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.time.LocalDateTime

class QueueServiceTest : FunSpec({

    lateinit var queueRepository: DownloadQueueRepository
    lateinit var historyRepository: DownloadHistoryRepository
    lateinit var service: QueueService
    val metricsService = mockk<MetricsService>(relaxed = true)

    beforeEach {
        queueRepository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        service = QueueService(queueRepository, historyRepository, metricsService)
    }

    afterEach {
        clearAllMocks()
    }

    context("addToQueue") {
        test("should create queue entity with QUEUED status") {
            val entity = DownloadQueueEntity(
                id = 1L,
                userId = 123L,
                messageId = 456,
                videoUrl = "https://example.com/video.mp4",
                videoTitle = "Test Video",
                status = QueueStatus.QUEUED,
                position = 1,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            every { queueRepository.countByStatus(QueueStatus.QUEUED) } returns 0
            every { queueRepository.save(any()) } returns entity

            val result = service.addToQueue(
                userId = 123L,
                messageId = 456,
                videoUrl = "https://example.com/video.mp4",
                videoTitle = "Test Video"
            )

            result.status shouldBe QueueStatus.QUEUED
            result.position shouldBe 1
            verify { queueRepository.save(any()) }
        }

        test("should calculate correct position when queue not empty") {
            every { queueRepository.countByStatus(QueueStatus.QUEUED) } returns 5
            every { queueRepository.save(any()) } answers {
                firstArg<DownloadQueueEntity>().copy(id = 1L)
            }

            val result = service.addToQueue(
                userId = 123L,
                messageId = 456,
                videoUrl = "https://example.com/video.mp4"
            )

            result.position shouldBe 6
        }
    }

    context("updateStatus") {
        test("should update status and updatedAt") {
            val entity = DownloadQueueEntity(
                id = 1L,
                userId = 123L,
                messageId = 456,
                videoUrl = "https://example.com/video.mp4",
                status = QueueStatus.QUEUED,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            every { queueRepository.findById(1L) } returns java.util.Optional.of(entity)
            every { queueRepository.save(any()) } returnsArgument 0

            service.updateStatus(1L, QueueStatus.DOWNLOADING)

            verify {
                queueRepository.save(match {
                    it.status == QueueStatus.DOWNLOADING
                })
            }
        }

        test("should handle non-existent entity gracefully") {
            every { queueRepository.findById(999L) } returns java.util.Optional.empty()

            service.updateStatus(999L, QueueStatus.DOWNLOADING)

            verify(exactly = 0) { queueRepository.save(any()) }
        }
    }

    context("archiveToHistory") {
        test("should save to history and delete from queue") {
            val entity = DownloadQueueEntity(
                id = 1L,
                userId = 123L,
                messageId = 456,
                videoUrl = "https://example.com/video.mp4",
                videoTitle = "Test",
                status = QueueStatus.COMPLETED,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            every { historyRepository.save(any()) } returnsArgument 0
            every { queueRepository.delete(any()) } just Runs

            service.archiveToHistory(entity)

            verify { historyRepository.save(any()) }
            verify { queueRepository.delete(entity) }
        }
    }

    context("countByStatus") {
        test("should return correct count") {
            every { queueRepository.countByStatus(QueueStatus.QUEUED) } returns 5

            val count = service.countByStatus(QueueStatus.QUEUED)

            count shouldBe 5
        }
    }
})
