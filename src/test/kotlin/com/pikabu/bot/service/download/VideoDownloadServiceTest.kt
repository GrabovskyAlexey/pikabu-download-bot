package com.pikabu.bot.service.download

import com.pikabu.bot.domain.exception.DownloadException
import com.pikabu.bot.domain.model.ErrorType
import com.pikabu.bot.service.admin.ErrorMonitoringService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.io.File

class VideoDownloadServiceTest : FunSpec({

    lateinit var errorMonitoringService: ErrorMonitoringService

    beforeEach {
        errorMonitoringService = mockk(relaxed = true)
    }

    afterEach {
        clearAllMocks()
    }

    context("downloadVideo") {
        test("should successfully download small video") {
            val videoContent = ByteArray(1024) { it.toByte() } // 1KB test file

            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel(videoContent),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("video/mp4"),
                        HttpHeaders.ContentLength to listOf("1024")
                    )
                )
            }

            val httpClient = HttpClient(mockEngine)
            val service = VideoDownloadService(
                httpClient = httpClient,
                errorMonitoringService = errorMonitoringService,
                maxSizeMb = 500,
                timeoutMinutes = 5,
                maxRetries = 3
            )

            val tempFile = File.createTempFile("test_video", ".mp4")
            try {
                val result = runBlocking {
                    service.downloadVideo("https://example.com/video.mp4", tempFile)
                }

                result.success shouldBe true
                result.sizeBytes shouldBe 1024L
                result.durationMs shouldBeGreaterThan 0L

                tempFile.exists() shouldBe true
                tempFile.length() shouldBe 1024L

                verify(exactly = 0) { errorMonitoringService.logError(any(), any(), any(), any()) }
            } finally {
                tempFile.delete()
            }
        }

        test("should reject video exceeding size limit via Content-Length") {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("video/mp4"),
                        HttpHeaders.ContentLength to listOf("${600L * 1024 * 1024}") // 600MB
                    )
                )
            }

            val httpClient = HttpClient(mockEngine)
            val service = VideoDownloadService(
                httpClient = httpClient,
                errorMonitoringService = errorMonitoringService,
                maxSizeMb = 500,
                timeoutMinutes = 5,
                maxRetries = 3
            )

            val tempFile = File.createTempFile("test_video", ".mp4")
            try {
                shouldThrow<DownloadException> {
                    runBlocking {
                        service.downloadVideo("https://example.com/video.mp4", tempFile)
                    }
                }

                // Проверяем, что была хотя бы одна попытка залогировать ошибку
                verify(atLeast = 1) {
                    errorMonitoringService.logError(
                        errorType = any(),
                        errorMessage = any(),
                        pageUrl = any(),
                        stackTrace = any()
                    )
                }
            } finally {
                tempFile.delete()
            }
        }

        test("should retry on failure and eventually succeed") {
            var attemptCount = 0

            val mockEngine = MockEngine { request ->
                attemptCount++
                if (attemptCount < 3) {
                    throw Exception("Network timeout")
                } else {
                    respond(
                        content = ByteReadChannel(ByteArray(100)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType to listOf("video/mp4"))
                    )
                }
            }

            val httpClient = HttpClient(mockEngine)
            val service = VideoDownloadService(
                httpClient = httpClient,
                errorMonitoringService = errorMonitoringService,
                maxSizeMb = 500,
                timeoutMinutes = 5,
                maxRetries = 3
            )

            val tempFile = File.createTempFile("test_video", ".mp4")
            try {
                val result = runBlocking {
                    service.downloadVideo("https://example.com/video.mp4", tempFile)
                }

                result.success shouldBe true
                attemptCount shouldBe 3 // 2 failures + 1 success
            } finally {
                tempFile.delete()
            }
        }

        test("should fail after max retries") {
            val mockEngine = MockEngine { request ->
                throw Exception("Persistent network error")
            }

            val httpClient = HttpClient(mockEngine)
            val service = VideoDownloadService(
                httpClient = httpClient,
                errorMonitoringService = errorMonitoringService,
                maxSizeMb = 500,
                timeoutMinutes = 5,
                maxRetries = 3
            )

            val tempFile = File.createTempFile("test_video", ".mp4")
            try {
                shouldThrow<DownloadException> {
                    runBlocking {
                        service.downloadVideo("https://example.com/video.mp4", tempFile)
                    }
                }

                verify {
                    errorMonitoringService.logError(
                        errorType = ErrorType.DOWNLOAD_ERROR,
                        errorMessage = match { it.contains("Failed after 3 attempts") },
                        pageUrl = "https://example.com/video.mp4",
                        stackTrace = any()
                    )
                }
            } finally {
                tempFile.delete()
            }
        }

        test("should handle HTTP error status codes") {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel("Forbidden"),
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType to listOf("text/plain"))
                )
            }

            val httpClient = HttpClient(mockEngine)
            val service = VideoDownloadService(
                httpClient = httpClient,
                errorMonitoringService = errorMonitoringService,
                maxSizeMb = 500,
                timeoutMinutes = 5,
                maxRetries = 3
            )

            val tempFile = File.createTempFile("test_video", ".mp4")
            try {
                shouldThrow<DownloadException> {
                    runBlocking {
                        service.downloadVideo("https://example.com/video.mp4", tempFile)
                    }
                }
            } finally {
                tempFile.delete()
            }
        }
    }
})
