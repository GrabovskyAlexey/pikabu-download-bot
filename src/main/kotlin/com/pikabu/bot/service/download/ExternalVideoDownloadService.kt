package com.pikabu.bot.service.download

import com.pikabu.bot.domain.exception.DownloadException
import com.pikabu.bot.domain.model.ErrorType
import com.pikabu.bot.domain.model.VideoPlatform
import com.pikabu.bot.service.admin.ErrorMonitoringService
import com.pikabu.bot.service.metrics.MetricsService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Service
class ExternalVideoDownloadService(
    private val errorMonitoringService: ErrorMonitoringService,
    private val metricsService: MetricsService,
    @Value("\${app.download.max-size-mb}") private val maxSizeMb: Int,
    @Value("\${app.download.external-timeout-minutes:10}") private val timeoutMinutes: Long
) {

    init {
        // Проверяем доступность yt-dlp при старте
        checkYtDlpAvailability()
    }

    /**
     * Проверяет доступность yt-dlp
     */
    private fun checkYtDlpAvailability() {
        try {
            val process = ProcessBuilder("yt-dlp", "--version")
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(5, TimeUnit.SECONDS)

            if (completed && process.exitValue() == 0) {
                val version = process.inputStream.bufferedReader().readText().trim()
                logger.info { "yt-dlp is available: version $version" }
            } else {
                logger.warn { "yt-dlp check failed or timed out" }
            }
        } catch (e: Exception) {
            logger.warn { "yt-dlp is not available: ${e.message}. External video downloads will fail." }
            logger.warn { "To enable external video support, install yt-dlp: https://github.com/yt-dlp/yt-dlp" }
        }
    }

    companion object {
        // Разрешённые платформы
        val ALLOWED_PLATFORMS = setOf(
            "youtube.com", "youtu.be",
            "rutube.ru",
            "vk.com", "vk.ru"
        )

        // Целевое качество: 720p
        const val TARGET_HEIGHT = 720
    }

    /**
     * Скачивает внешнее видео через yt-dlp
     */
    suspend fun downloadExternalVideo(
        videoUrl: String,
        outputFile: File,
        platform: VideoPlatform
    ): ExternalDownloadResult = withContext(Dispatchers.IO) {

        val startTime = System.currentTimeMillis()
        logger.info { "Starting external video download from $platform: $videoUrl" }

        try {
            // Валидация платформы
            if (!isAllowedPlatform(videoUrl)) {
                throw DownloadException("Platform not supported: $videoUrl")
            }

            // Команда yt-dlp
            val command = buildYtDlpCommand(videoUrl, outputFile)

            logger.debug { "Executing yt-dlp command: ${command.joinToString(" ")}" }

            // Запуск процесса с таймаутом
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // Читаем вывод в реальном времени, чтобы избежать переполнения буфера
            val outputBuilder = StringBuilder()
            val reader = process.inputStream.bufferedReader()

            // Запускаем чтение вывода в отдельной корутине
            val readJob = launch(Dispatchers.IO) {
                try {
                    reader.lineSequence().forEach { line ->
                        outputBuilder.appendLine(line)
                        // Логируем важные строки для отладки
                        if (line.contains("Downloading") || line.contains("ETA") ||
                            line.contains("Downloaded") || line.contains("Merging")) {
                            logger.debug { "yt-dlp: $line" }
                        }
                    }
                } catch (e: Exception) {
                    logger.debug { "Output reader stopped: ${e.message}" }
                }
            }

            val completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

            if (!completed) {
                process.destroyForcibly()
                readJob.cancel()
                throw DownloadException("Download timeout after $timeoutMinutes minutes")
            }

            // Ждем завершения чтения вывода
            readJob.join()

            val output = outputBuilder.toString()
            val exitCode = process.exitValue()

            logger.debug { "yt-dlp completed with exit code: $exitCode" }

            if (exitCode != 0) {
                logger.error { "yt-dlp failed with exit code $exitCode: $output" }

                // Специальные сообщения для разных ошибок
                val errorMessage = when {
                    output.contains("HTTP Error 404") -> {
                        if (videoUrl.contains("vk.com")) {
                            "Видео ВКонтакте недоступно. Возможно оно удалено или доступно только авторизованным пользователям."
                        } else {
                            "Видео не найдено (404)"
                        }
                    }
                    output.contains("HTTP Error 403") -> "Доступ к видео запрещен"
                    output.contains("Private video") -> "Это приватное видео"
                    output.contains("age-restricted") -> "Видео имеет возрастные ограничения"
                    else -> "Не удалось загрузить видео"
                }

                throw DownloadException(errorMessage)
            }

            // Проверка существования и размера файла
            if (!outputFile.exists()) {
                logger.error { "yt-dlp completed successfully but file was not created. Output: $output" }
                throw DownloadException("Video file was not created by yt-dlp")
            }

            val fileSize = outputFile.length()

            if (fileSize == 0L) {
                logger.error { "yt-dlp completed successfully but file is empty. Output: $output" }
                outputFile.delete()
                throw DownloadException("Downloaded video file is empty")
            }

            val maxSizeBytes = maxSizeMb * 1024L * 1024L
            if (fileSize > maxSizeBytes) {
                outputFile.delete()
                throw DownloadException("Video exceeds size limit: ${fileSize / 1024 / 1024} MB > $maxSizeMb MB")
            }

            val duration = System.currentTimeMillis() - startTime

            logger.info {
                "External video downloaded successfully: " +
                "size=${fileSize / 1024 / 1024}MB, duration=${duration}ms, platform=$platform"
            }

            ExternalDownloadResult(
                success = true,
                sizeBytes = fileSize,
                durationMs = duration
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to download external video: $videoUrl" }

            errorMonitoringService.logError(
                errorType = ErrorType.DOWNLOAD_ERROR,
                errorMessage = "External video download failed: ${e.message}",
                pageUrl = videoUrl,
                stackTrace = e.stackTraceToString()
            )

            throw DownloadException("External video download failed: ${e.message}", e)
        }
    }

    /**
     * Строит команду для yt-dlp
     */
    private fun buildYtDlpCommand(videoUrl: String, outputFile: File): List<String> {
        val isYouTube = videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")
        val isVK = videoUrl.contains("vk.com") || videoUrl.contains("vk.ru")

        val formatString = when {
            isYouTube -> {
                // Для YouTube: приоритет на 720p высокого качества
                // 136 = 720p H.264 видео (высокое качество), 140 = M4A аудио 128kbps
                // Fallback на любое доступное 720p или ниже
                "136+140/bestvideo[height<=720]+bestaudio/best[height<=720]/best"
            }
            isVK -> {
                // Для VK: строгое ограничение на 720p
                // Сортировка: предпочитаем 720p, затем ниже (но не выше)
                "bestvideo[height<=720][height>=480]+bestaudio/bestvideo[height<=720]+bestaudio/best[height<=720]/best"
            }
            else -> {
                // Для остальных платформ (Rutube)
                "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
            }
        }

        val command = mutableListOf(
            "yt-dlp",

            // Формат видео
            "--format", formatString,

            // Объединить в mp4
            "--merge-output-format", "mp4",

            // Путь к выходному файлу
            "--output", outputFile.absolutePath,

            // Не создавать .part файлы
            "--no-part",

            // Всегда перезаписывать
            "--force-overwrites",

            // Множественные попытки при ошибках
            "--retries", "3",
            "--fragment-retries", "3",

            // Показывать прогресс (для логов)
            "--newline",
            "--progress"
        )

        // Специальные настройки для VK
        if (isVK) {
            command.addAll(listOf(
                // Сортировка форматов: приоритет на меньший размер при одинаковом качестве
                "--format-sort", "res:720,+size,+br",

                // User-Agent браузера для VK
                "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",

                // Добавить referer
                "--add-header", "Referer:https://vk.com/"
            ))
        }

        // URL видео
        command.add(videoUrl)

        return command
    }

    /**
     * Проверяет разрешена ли платформа
     */
    private fun isAllowedPlatform(url: String): Boolean {
        return ALLOWED_PLATFORMS.any { url.contains(it) }
    }
}

/**
 * Результат загрузки внешнего видео
 */
data class ExternalDownloadResult(
    val success: Boolean,
    val sizeBytes: Long,
    val durationMs: Long
)
