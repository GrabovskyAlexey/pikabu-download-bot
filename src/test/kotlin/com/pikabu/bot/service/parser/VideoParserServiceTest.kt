package com.pikabu.bot.service.parser

import com.pikabu.bot.domain.exception.VideoNotFoundException
import com.pikabu.bot.domain.model.ErrorType
import com.pikabu.bot.domain.model.VideoFormat
import com.pikabu.bot.domain.model.VideoInfo
import com.pikabu.bot.service.admin.ErrorMonitoringService
import com.pikabu.bot.service.metrics.MetricsService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.mockk.*
import kotlinx.serialization.json.Json

class VideoParserServiceTest : FunSpec({

    lateinit var mockParser: PikabuHtmlParser
    lateinit var errorMonitoringService: ErrorMonitoringService
    lateinit var service: VideoParserService
    val metricsService = mockk<MetricsService>(relaxed = true)

    beforeEach {
        mockParser = mockk(relaxed = true)
        errorMonitoringService = mockk(relaxed = true)
    }

    afterEach {
        clearAllMocks()
    }

    context("parseVideos") {
        test("should return videos when parser finds them") {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel("<html><body>Test HTML</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(Json)
                }
            }

            service = VideoParserService(
                httpClient, mockParser, errorMonitoringService, metricsService)

            val expectedVideos = listOf(
                VideoInfo(
                    url = "https://example.com/video.mp4",
                    title = "Test Video",
                    format = VideoFormat.MP4
                )
            )

            every { mockParser.parseVideos(any(), any()) } returns expectedVideos

            val result = service.parseVideos("https://pikabu.ru/story/test")

            result shouldHaveSize 1
            result[0].url shouldBe "https://example.com/video.mp4"
            result[0].title shouldBe "Test Video"

            verify { mockParser.parseVideos(any(), "https://pikabu.ru/story/test") }
            verify(exactly = 0) { errorMonitoringService.logError(any(), any(), any(), any()) }
        }

        test("should throw VideoNotFoundException when no videos found") {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel("<html><body>No video here</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )
            }

            val httpClient = HttpClient(mockEngine)
            service = VideoParserService(httpClient, mockParser, errorMonitoringService, metricsService)

            every { mockParser.parseVideos(any(), any()) } returns emptyList()

            shouldThrow<VideoNotFoundException> {
                service.parseVideos("https://pikabu.ru/story/test")
            }

            verify {
                errorMonitoringService.logError(
                    errorType = ErrorType.PARSING_ERROR,
                    errorMessage = "No videos found on page",
                    pageUrl = "https://pikabu.ru/story/test",
                    stackTrace = null
                )
            }
        }

        test("should throw VideoNotFoundException on HTTP error") {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel("Not Found"),
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }

            val httpClient = HttpClient(mockEngine)
            service = VideoParserService(httpClient, mockParser, errorMonitoringService, metricsService)

            shouldThrow<VideoNotFoundException> {
                service.parseVideos("https://pikabu.ru/story/test")
            }

            verify {
                errorMonitoringService.logError(
                    errorType = ErrorType.PARSING_ERROR,
                    errorMessage = "Failed to fetch page: HTTP 404",
                    pageUrl = "https://pikabu.ru/story/test",
                    stackTrace = null
                )
            }
        }

        test("should handle network errors gracefully") {
            val mockEngine = MockEngine { request ->
                throw Exception("Network error")
            }

            val httpClient = HttpClient(mockEngine)
            service = VideoParserService(httpClient, mockParser, errorMonitoringService, metricsService)

            shouldThrow<VideoNotFoundException> {
                service.parseVideos("https://pikabu.ru/story/test")
            }

            verify {
                errorMonitoringService.logError(
                    errorType = ErrorType.PARSING_ERROR,
                    errorMessage = match { it.contains("Network error") },
                    pageUrl = "https://pikabu.ru/story/test",
                    stackTrace = any()
                )
            }
        }
    }
})
