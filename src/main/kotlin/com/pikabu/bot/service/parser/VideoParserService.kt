package com.pikabu.bot.service.parser

import com.pikabu.bot.domain.exception.AuthenticationException
import com.pikabu.bot.domain.exception.VideoNotFoundException
import com.pikabu.bot.domain.model.ErrorType
import com.pikabu.bot.domain.model.VideoInfo
import com.pikabu.bot.service.admin.AdminNotificationService
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
    private val errorMonitoringService: ErrorMonitoringService,
    private val adminNotificationService: AdminNotificationService,
    private val metricsService: com.pikabu.bot.service.metrics.MetricsService
) {

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    fun parseVideos(pageUrl: String): List<VideoInfo> {
        val startTime = System.currentTimeMillis()
        logger.debug { "Parsing videos from: $pageUrl" }

        val html = runBlocking {
            fetchHtml(pageUrl)
        }

        val allVideos = pikabuHtmlParser.parseVideos(html, pageUrl)

        // Применяем приоритет: прямые видео > внешние
        val videos = filterByPriority(allVideos)

        if (videos.isEmpty()) {
            // Проверяем, требуется ли авторизация для этой страницы
            val authRequired = pikabuHtmlParser.checkAuthenticationRequired(html)

            if (authRequired) {
                logger.warn { "Authentication required for page: $pageUrl" }

                // Логируем ошибку авторизации
                errorMonitoringService.logError(
                    errorType = ErrorType.AUTHENTICATION_ERROR,
                    errorMessage = "Content requires authentication (cookies expired or missing)",
                    pageUrl = pageUrl
                )

                // Уведомляем админа о протухших cookies
                adminNotificationService.notifyCookiesExpired(pageUrl)

                throw AuthenticationException(
                    "Видео доступно только после авторизации. Попробуйте позже.",
                    403
                )
            }

            logger.warn { "No videos found on page: $pageUrl" }
            metricsService.recordParsingError()

            // Логируем ошибку парсинга
            errorMonitoringService.logError(
                errorType = ErrorType.PARSING_ERROR,
                errorMessage = "No videos found on page",
                pageUrl = pageUrl
            )

            throw VideoNotFoundException("На странице не найдено видео")
        }

        val duration = System.currentTimeMillis() - startTime
        metricsService.recordParseDuration(duration)

        logger.debug { "Found ${videos.size} video(s) on page: $pageUrl" }
        return videos
    }

    private suspend fun fetchHtml(url: String): String {
        return try {
            val response = httpClient.get(url) {
                header("User-Agent", USER_AGENT)
            }

            val statusCode = response.status.value

            // Проверка на ошибки авторизации
            if (statusCode == 401 || statusCode == 403) {
                logger.warn { "Authentication error for: $url, status: $statusCode" }

                // Логируем ошибку авторизации
                errorMonitoringService.logError(
                    errorType = ErrorType.AUTHENTICATION_ERROR,
                    errorMessage = "Authentication failed: HTTP $statusCode",
                    pageUrl = url
                )

                // Уведомляем админа
                adminNotificationService.notifyAuthenticationError(statusCode, url)

                throw AuthenticationException(
                    "Доступ к странице ограничен (HTTP $statusCode)",
                    statusCode
                )
            }

            if (statusCode !in 200..299) {
                logger.error { "Failed to fetch page: $url, status: $statusCode" }
                metricsService.recordParsingError()

                // Логируем ошибку
                errorMonitoringService.logError(
                    errorType = ErrorType.PARSING_ERROR,
                    errorMessage = "Failed to fetch page: HTTP $statusCode",
                    pageUrl = url
                )

                throw VideoNotFoundException("Не удалось загрузить страницу (HTTP $statusCode)")
            }

            response.bodyAsText()
        } catch (e: AuthenticationException) {
            throw e
        } catch (e: VideoNotFoundException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error fetching HTML from: $url" }
            metricsService.recordParsingError()

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

    /**
     * Применяет приоритет: прямые видео > внешние
     * Если есть прямые видео - показываем только их
     * Если нет прямых - показываем внешние
     */
    private fun filterByPriority(videos: List<VideoInfo>): List<VideoInfo> {
        val directVideos = videos.filter { !it.isExternal }
        val externalVideos = videos.filter { it.isExternal }

        return if (directVideos.isNotEmpty()) {
            logger.debug { "Found ${directVideos.size} direct video(s), ignoring ${externalVideos.size} external video(s)" }
            directVideos
        } else {
            logger.debug { "No direct videos found, showing ${externalVideos.size} external video(s)" }
            externalVideos
        }
    }
}
