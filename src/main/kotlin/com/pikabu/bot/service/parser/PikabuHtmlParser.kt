package com.pikabu.bot.service.parser

import com.pikabu.bot.domain.model.VideoFormat
import com.pikabu.bot.domain.model.VideoInfo
import com.pikabu.bot.domain.model.VideoPlatform
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

        // Стратегия 4: Iframe и embed теги (внешние видео)
        videos.addAll(parseIframeVideos(document))

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
     * Проверяет требуется ли авторизация для просмотра контента на странице
     * Возвращает true если обнаружены признаки требования авторизации
     */
    fun checkAuthenticationRequired(html: String): Boolean {
        logger.debug { "Checking if authentication is required" }

        // Признак 1: Текст с призывом авторизоваться
        val authPhrases = listOf(
            "Авторизуйтесь или зарегистрируйтесь",
            "Авторизуйтесь",
            "Войти / Зарегистрироваться",
            "Требуется авторизация"
        )
        val hasAuthPrompt = authPhrases.any { phrase ->
            html.contains(phrase, ignoreCase = true)
        }

        // Признак 2: userID: 0 (неавторизованный пользователь)
        val hasUnauthorizedUser = html.contains(""""userID":0""") ||
                                   html.contains(""""userID": 0""")

        // Признак 3: Маркеры NSFW/18+ контента
        val hasNsfwContent = html.contains("NSFW-контента (18+)", ignoreCase = true) ||
                             html.contains("[18+]", ignoreCase = false) ||
                             html.contains("adult-content", ignoreCase = true)

        val isAuthRequired = (hasAuthPrompt && hasUnauthorizedUser) ||
                             (hasNsfwContent && hasUnauthorizedUser)

        if (isAuthRequired) {
            logger.info {
                "Authentication required detected: " +
                "authPrompt=$hasAuthPrompt, " +
                "unauthorizedUser=$hasUnauthorizedUser, " +
                "nsfwContent=$hasNsfwContent"
            }
        }

        return isAuthRequired
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

    /**
     * Стратегия 4: Поиск внешних видео (vue-video-player с data-source)
     */
    private fun parseIframeVideos(document: Document): List<VideoInfo> {
        val videos = mutableListOf<VideoInfo>()

        logger.debug { "Strategy 4: Parsing vue-video-player with data-source" }

        // Pikabu использует Vue.js с lazy loading - iframe создаётся при клике на play
        // В исходном HTML есть div.vue-video-player с data-source
        document.select("div.vue-video-player[data-source]").forEach { player ->
            val dataSource = player.attr("data-source")

            extractExternalVideoUrl(dataSource)?.let { externalUrl ->
                videos.add(
                    VideoInfo(
                        url = externalUrl,
                        title = null,
                        isExternal = true,
                        platform = detectPlatform(externalUrl),
                        format = VideoFormat.UNKNOWN
                    )
                )
                logger.debug { "Found external video in vue-video-player: $externalUrl (platform: ${detectPlatform(externalUrl)})" }
            }
        }

        // Также ищем старый формат iframe (на всякий случай)
        document.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src")
            val title = iframe.attr("title")

            extractExternalVideoUrl(src)?.let { externalUrl ->
                videos.add(
                    VideoInfo(
                        url = externalUrl,
                        title = title.ifBlank { null },
                        isExternal = true,
                        platform = detectPlatform(externalUrl),
                        format = VideoFormat.UNKNOWN
                    )
                )
                logger.debug { "Found external video in iframe: $externalUrl (platform: ${detectPlatform(externalUrl)})" }
            }
        }

        logger.debug { "Strategy 4 found ${videos.size} external video(s)" }
        return videos
    }

    /**
     * Извлекает URL внешнего видео из embed URL
     * Конвертирует embed URL в watch URL
     */
    private fun extractExternalVideoUrl(embedUrl: String): String? {
        logger.debug { "Extracting external video URL from: $embedUrl" }

        return when {
            // YouTube: youtube.com/embed/VIDEO_ID -> youtube.com/watch?v=VIDEO_ID
            embedUrl.contains("youtube.com/embed/") -> {
                val videoId = embedUrl.substringAfter("youtube.com/embed/")
                    .substringBefore("?")
                    .substringBefore("/")
                if (videoId.isNotBlank()) {
                    "https://www.youtube.com/watch?v=$videoId"
                } else null
            }

            // YouTube короткий формат: youtu.be/VIDEO_ID
            embedUrl.contains("youtu.be/") -> {
                val videoId = embedUrl.substringAfter("youtu.be/")
                    .substringBefore("?")
                    .substringBefore("/")
                if (videoId.isNotBlank()) {
                    "https://www.youtube.com/watch?v=$videoId"
                } else null
            }

            // RuTube: rutube.ru/play/embed/VIDEO_ID -> rutube.ru/video/VIDEO_ID
            embedUrl.contains("rutube.ru/play/embed/") -> {
                val videoId = embedUrl.substringAfter("rutube.ru/play/embed/")
                    .substringBefore("?")
                    .substringBefore("/")
                if (videoId.isNotBlank()) {
                    "https://rutube.ru/video/$videoId/"
                } else null
            }

            // RuTube уже в правильном формате: rutube.ru/video/VIDEO_ID
            embedUrl.contains("rutube.ru/video/") -> {
                val videoId = embedUrl.substringAfter("rutube.ru/video/")
                    .substringBefore("?")
                    .substringBefore("/")
                if (videoId.isNotBlank()) {
                    "https://rutube.ru/video/$videoId/"
                } else null
            }

            // VK: vk.com/video_ext.php?oid=X&id=Y -> vk.com/video-X_Y
            embedUrl.contains("vk.com/video_ext.php") || embedUrl.contains("vk.ru/video_ext.php") -> {
                parseVkVideoUrl(embedUrl)
            }

            // VK уже в правильном формате: vk.com/video-X_Y
            embedUrl.contains("vk.com/video") || embedUrl.contains("vk.ru/video") -> {
                val result = if (embedUrl.startsWith("http://") || embedUrl.startsWith("https://")) {
                    embedUrl.substringBefore("?") // Убираем query параметры
                } else {
                    "https://$embedUrl".substringBefore("?")
                }
                logger.info { "VK video URL already in correct format: $result" }
                result
            }

            else -> {
                logger.debug { "Unknown external video URL format: $embedUrl" }
                null
            }
        }
    }

    /**
     * Парсит VK video_ext.php URL в формат videoOID_ID
     */
    private fun parseVkVideoUrl(url: String): String? {
        return try {
            // Используем URI для правильного парсинга query параметров
            val uri = URI(url)
            val params = uri.query?.split("&")
                ?.mapNotNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                ?.toMap() ?: emptyMap()

            val oid = params["oid"]
            val id = params["id"]

            logger.debug { "Parsing VK URL: oid='$oid', id='$id' from: $url" }

            if (!oid.isNullOrBlank() && !id.isNullOrBlank()) {
                val result = "https://vk.com/video${oid}_$id"
                logger.info { "Parsed VK video URL: $result (from embed: $url)" }
                result
            } else {
                logger.warn { "Failed to parse VK URL: oid or id is blank. oid='$oid', id='$id'" }
                null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse VK video URL: $url" }
            null
        }
    }

    /**
     * Определяет платформу видео по URL
     */
    private fun detectPlatform(url: String): VideoPlatform {
        return when {
            url.contains("youtube.com") || url.contains("youtu.be") -> VideoPlatform.YOUTUBE
            url.contains("rutube.ru") -> VideoPlatform.RUTUBE
            url.contains("vk.com") || url.contains("vk.ru") -> VideoPlatform.VKVIDEO
            else -> VideoPlatform.PIKABU
        }
    }
}
