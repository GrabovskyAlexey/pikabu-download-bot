package com.pikabu.bot.service.parser

import com.pikabu.bot.domain.model.VideoInfo
import com.pikabu.bot.domain.model.toVideoFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.net.URI

private val logger = KotlinLogging.logger {}

@Component
class PikabuHtmlParser {

    companion object {
        private val VIDEO_URL_REGEX = Regex("""(https?://[^\s"']+\.(mp4|webm|mov|avi))""", RegexOption.IGNORE_CASE)
        private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "avi")
    }

    fun parseVideos(html: String, pageUrl: String): List<VideoInfo> {
        val document = Jsoup.parse(html)
        val videos = mutableSetOf<VideoInfo>()

        logger.debug { "Starting to parse videos from HTML (${html.length} chars)" }

        // Стратегия 1: HTML5 <video> и <source> теги
        videos.addAll(parseVideoTags(document, pageUrl))

        // Стратегия 2: Data-атрибуты
        videos.addAll(parseDataAttributes(document, pageUrl))

        // Стратегия 3: Regex поиск в inline JavaScript
        videos.addAll(parseInlineScripts(html, pageUrl))

        // Удаляем дубликаты по URL и группируем по базовому имени файла
        val uniqueVideos = videos
            .distinctBy { it.url } // Убираем полные дубликаты
            .groupBy { getBaseFileName(it.url) } // Группируем по имени без расширения
            .mapNotNull { (_, group) ->
                // Если есть несколько форматов, предпочитаем MP4, затем WEBM
                group.sortedByDescending {
                    when (it.format) {
                        com.pikabu.bot.domain.model.VideoFormat.MP4 -> 2
                        com.pikabu.bot.domain.model.VideoFormat.WEBM -> 1
                        else -> 0
                    }
                }.firstOrNull()
            }

        logger.debug { "Parsed ${videos.size} raw video(s), filtered to ${uniqueVideos.size} unique" }
        uniqueVideos.forEachIndexed { index, video ->
            logger.debug { "Video #${index + 1}: ${video.url} (format: ${video.format}, title: ${video.title})" }
        }
        return uniqueVideos
    }

    /**
     * Стратегия 1: Поиск HTML5 <video> и <source> тегов
     */
    private fun parseVideoTags(document: Document, pageUrl: String): List<VideoInfo> {
        val videos = mutableListOf<VideoInfo>()

        logger.debug { "Strategy 1: Parsing <video> tags" }

        // Поиск <video src="...">
        document.select("video[src]").forEach { videoElement ->
            val src = videoElement.attr("src")
            if (isValidVideoUrl(src)) {
                val normalizedUrl = normalizeUrl(src, pageUrl)
                videos.add(
                    VideoInfo(
                        url = normalizedUrl,
                        title = videoElement.attr("title").takeIf { it.isNotBlank() },
                        thumbnailUrl = videoElement.attr("poster").takeIf { it.isNotBlank() }
                            ?.let { normalizeUrl(it, pageUrl) },
                        format = getVideoFormat(normalizedUrl)
                    )
                )
                logger.debug { "Found video in <video> tag: $normalizedUrl" }
            }
        }

        // Поиск <source src="..."> внутри <video>
        document.select("video source[src]").forEach { sourceElement ->
            val src = sourceElement.attr("src")
            if (isValidVideoUrl(src)) {
                val normalizedUrl = normalizeUrl(src, pageUrl)
                val videoElement = sourceElement.parent()
                videos.add(
                    VideoInfo(
                        url = normalizedUrl,
                        title = videoElement?.attr("title")?.takeIf { it.isNotBlank() },
                        thumbnailUrl = videoElement?.attr("poster")?.takeIf { it.isNotBlank() }
                            ?.let { normalizeUrl(it, pageUrl) },
                        format = getVideoFormat(normalizedUrl)
                    )
                )
                logger.debug { "Found video in <source> tag: $normalizedUrl" }
            }
        }

        logger.debug { "Strategy 1 found ${videos.size} video(s)" }
        return videos
    }

    /**
     * Стратегия 2: Поиск data-атрибутов (data-video-url, data-src, etc.)
     */
    private fun parseDataAttributes(document: Document, pageUrl: String): List<VideoInfo> {
        val videos = mutableListOf<VideoInfo>()

        logger.debug { "Strategy 2: Parsing data attributes" }

        val dataAttributes = listOf(
            "data-video-url",
            "data-src",
            "data-video",
            "data-url",
            "data-video-src"
        )

        dataAttributes.forEach { attr ->
            document.select("[$attr]").forEach { element ->
                val url = element.attr(attr)
                if (isValidVideoUrl(url)) {
                    val normalizedUrl = normalizeUrl(url, pageUrl)
                    videos.add(
                        VideoInfo(
                            url = normalizedUrl,
                            title = element.attr("data-title").takeIf { it.isNotBlank() }
                                ?: element.attr("title").takeIf { it.isNotBlank() },
                            format = getVideoFormat(normalizedUrl)
                        )
                    )
                    logger.debug { "Found video in $attr: $normalizedUrl" }
                }
            }
        }

        logger.debug { "Strategy 2 found ${videos.size} video(s)" }
        return videos
    }

    /**
     * Стратегия 3: Regex поиск URL в inline JavaScript
     */
    private fun parseInlineScripts(html: String, pageUrl: String): List<VideoInfo> {
        val videos = mutableListOf<VideoInfo>()

        logger.debug { "Strategy 3: Parsing inline scripts with regex" }

        val matches = VIDEO_URL_REGEX.findAll(html)
        matches.forEach { match ->
            val url = match.groupValues[1]
            if (isValidVideoUrl(url)) {
                val normalizedUrl = normalizeUrl(url, pageUrl)
                videos.add(
                    VideoInfo(
                        url = normalizedUrl,
                        format = getVideoFormat(normalizedUrl)
                    )
                )
                logger.debug { "Found video in JavaScript: $normalizedUrl" }
            }
        }

        logger.debug { "Strategy 3 found ${videos.size} video(s)" }
        return videos
    }

    /**
     * Проверяет, является ли URL валидным видео URL
     */
    private fun isValidVideoUrl(url: String): Boolean {
        if (url.isBlank()) return false

        val extension = url.substringAfterLast('.', "").lowercase()
            .substringBefore('?') // Убираем query параметры
            .substringBefore('#') // Убираем fragment

        return extension in VIDEO_EXTENSIONS
    }

    /**
     * Нормализует относительные URL в абсолютные
     */
    private fun normalizeUrl(url: String, baseUrl: String): String {
        return try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else if (url.startsWith("//")) {
                "https:$url"
            } else {
                val base = URI(baseUrl)
                base.resolve(url).toString()
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to normalize URL: $url with base: $baseUrl" }
            url
        }
    }

    /**
     * Определяет формат видео по расширению
     */
    private fun getVideoFormat(url: String): com.pikabu.bot.domain.model.VideoFormat {
        val extension = url.substringAfterLast('.', "").lowercase()
            .substringBefore('?')
            .substringBefore('#')

        return extension.toVideoFormat()
    }

    /**
     * Получает базовое имя файла без расширения (для группировки разных форматов)
     */
    private fun getBaseFileName(url: String): String {
        return url
            .substringAfterLast('/')
            .substringBeforeLast('.')
    }
}
