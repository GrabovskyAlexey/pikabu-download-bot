package com.pikabu.bot.service.download

import com.pikabu.bot.domain.exception.DownloadException
import com.pikabu.bot.domain.model.ErrorType
import com.pikabu.bot.service.admin.ErrorMonitoringService
import com.pikabu.bot.service.metrics.MetricsService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import kotlin.math.pow

private val logger = KotlinLogging.logger {}

@Service
class VideoDownloadService(
    private val httpClient: HttpClient,
    private val errorMonitoringService: ErrorMonitoringService,
    private val metricsService: MetricsService,
    @Value("\${app.download.max-size-mb:500}")
    private val maxSizeMb: Int,
    @Value("\${app.download.timeout-minutes:5}")
    private val timeoutMinutes: Int,
    @Value("\${app.download.max-retries:3}")
    private val maxRetries: Int
) {

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val CHUNK_SIZE = 8192
    }

    private val maxSizeBytes = maxSizeMb * 1024L * 1024L

    /**
     * Загружает видео с retry логикой
     */
    suspend fun downloadVideo(videoUrl: String, outputFile: File): DownloadResult {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                logger.debug { "Downloading video (attempt ${attempt + 1}/$maxRetries): $videoUrl" }

                val result = downloadVideoInternal(videoUrl, outputFile)

                logger.debug { "Video downloaded successfully: ${result.sizeBytes} bytes, ${result.durationMs} ms" }
                metricsService.recordDownloadDuration(result.durationMs)
                return result

            } catch (e: DownloadException) {
                lastException = e
                logger.warn(e) { "Download attempt ${attempt + 1} failed: ${e.message}" }

                if (attempt < maxRetries - 1) {
                    val delayMs = calculateBackoffDelay(attempt)
                    logger.debug { "Retrying in ${delayMs}ms..." }
                    delay(delayMs)
                }
            }
        }

        // Все попытки исчерпаны
        val message = "Failed to download video after $maxRetries attempts: $videoUrl"
        logger.error { message }
        metricsService.recordDownloadError()

        // Логируем ошибку
        errorMonitoringService.logError(
            errorType = ErrorType.DOWNLOAD_ERROR,
            errorMessage = "Failed after $maxRetries attempts: ${lastException?.message}",
            pageUrl = videoUrl,
            stackTrace = lastException?.stackTraceToString()?.take(1000)
        )

        throw DownloadException(message, lastException)
    }

    /**
     * Внутренний метод загрузки без retry
     */
    private suspend fun downloadVideoInternal(videoUrl: String, outputFile: File): DownloadResult {
        val startTime = System.currentTimeMillis()
        var downloadedBytes = 0L

        try {
            val response = httpClient.get(videoUrl) {
                header("User-Agent", USER_AGENT)
            }

            if (!response.status.isSuccess()) {
                throw DownloadException("HTTP error: ${response.status.value} ${response.status.description}")
            }

            // Проверяем Content-Length если доступен
            val contentLength = response.contentLength()
            if (contentLength != null && contentLength > maxSizeBytes) {
                throw DownloadException("Video size ($contentLength bytes) exceeds limit ($maxSizeBytes bytes)")
            }

            logger.debug { "Starting streaming download to: ${outputFile.absolutePath}" }

            // Streaming загрузка
            outputFile.outputStream().use { output ->
                val channel: ByteReadChannel = response.bodyAsChannel()

                while (!channel.isClosedForRead) {
                    val buffer = ByteArray(CHUNK_SIZE)
                    val bytesRead = channel.readAvailable(buffer, 0, CHUNK_SIZE)

                    if (bytesRead == -1) break

                    downloadedBytes += bytesRead

                    // Проверяем размер во время загрузки
                    if (downloadedBytes > maxSizeBytes) {
                        throw DownloadException("Downloaded size ($downloadedBytes bytes) exceeds limit ($maxSizeBytes bytes)")
                    }

                    output.write(buffer, 0, bytesRead)
                }
            }

            val duration = System.currentTimeMillis() - startTime
            logger.debug { "Download completed: $downloadedBytes bytes in ${duration}ms" }

            return DownloadResult(
                success = true,
                sizeBytes = downloadedBytes,
                durationMs = duration
            )

        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error during video download: $videoUrl" }
            throw DownloadException("Download failed: ${e.message}", e)
        }
    }

    /**
     * Вычисляет задержку для exponential backoff
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        // 1s, 2s, 4s
        return (1000 * 2.0.pow(attempt)).toLong()
    }
}

data class DownloadResult(
    val success: Boolean,
    val sizeBytes: Long,
    val durationMs: Long
)
