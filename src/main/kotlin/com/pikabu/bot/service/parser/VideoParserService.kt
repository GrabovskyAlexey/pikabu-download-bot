package com.pikabu.bot.service.parser

import com.pikabu.bot.domain.exception.VideoNotFoundException
import com.pikabu.bot.domain.model.ErrorType
import com.pikabu.bot.domain.model.VideoInfo
import com.pikabu.bot.service.admin.ErrorMonitoringService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class VideoParserService(
    private val httpClient: HttpClient,
    private val pikabuHtmlParser: PikabuHtmlParser,
    private val errorMonitoringService: ErrorMonitoringService
) {

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    fun parseVideos(pageUrl: String): List<VideoInfo> {
        logger.info { "Parsing videos from: $pageUrl" }

        val html = runBlocking {
            fetchHtml(pageUrl)
        }

        val videos = pikabuHtmlParser.parseVideos(html, pageUrl)

        if (videos.isEmpty()) {
            logger.warn { "No videos found on page: $pageUrl" }

            // Логируем ошибку парсинга
            errorMonitoringService.logError(
                errorType = ErrorType.PARSING_ERROR,
                errorMessage = "No videos found on page",
                pageUrl = pageUrl
            )

            throw VideoNotFoundException("На странице не найдено видео")
        }

        logger.info { "Found ${videos.size} video(s) on page: $pageUrl" }
        return videos
    }

    private suspend fun fetchHtml(url: String): String {
        return try {
            val response = httpClient.get(url) {
                header("User-Agent", USER_AGENT)
            }

            if (response.status.value !in 200..299) {
                logger.error { "Failed to fetch page: $url, status: ${response.status}" }

                // Логируем ошибку
                errorMonitoringService.logError(
                    errorType = ErrorType.PARSING_ERROR,
                    errorMessage = "Failed to fetch page: HTTP ${response.status.value}",
                    pageUrl = url
                )

                throw VideoNotFoundException("Не удалось загрузить страницу (HTTP ${response.status.value})")
            }

            response.bodyAsText()
        } catch (e: VideoNotFoundException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error fetching HTML from: $url" }

            // Логируем ошибку
            errorMonitoringService.logError(
                errorType = ErrorType.PARSING_ERROR,
                errorMessage = "Error fetching HTML: ${e.message}",
                pageUrl = url,
                stackTrace = e.stackTraceToString().take(1000)
            )

            throw VideoNotFoundException("Ошибка при загрузке страницы: ${e.message}")
        }
    }
}
