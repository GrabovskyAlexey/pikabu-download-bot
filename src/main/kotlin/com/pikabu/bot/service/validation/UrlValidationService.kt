package com.pikabu.bot.service.validation

import com.pikabu.bot.domain.exception.InvalidUrlException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.net.URI

private val logger = KotlinLogging.logger {}

@Service
class UrlValidationService {

    companion object {
        private val ALLOWED_DOMAINS = setOf("pikabu.ru", "www.pikabu.ru")
    }

    fun validateUrl(url: String): String {
        logger.debug { "Validating URL: $url" }

        val trimmedUrl = url.trim()

        if (trimmedUrl.isBlank()) {
            throw InvalidUrlException("URL не может быть пустым")
        }

        val uri = try {
            URI(trimmedUrl)
        } catch (e: Exception) {
            logger.warn(e) { "Invalid URL format: $trimmedUrl" }
            throw InvalidUrlException("Неверный формат URL")
        }

        val host = uri.host?.lowercase()
        if (host == null) {
            throw InvalidUrlException("URL не содержит домена")
        }

        if (host !in ALLOWED_DOMAINS) {
            logger.warn { "URL domain not allowed: $host" }
            throw InvalidUrlException("Поддерживаются только ссылки с pikabu.ru")
        }

        logger.debug { "URL validation passed: $trimmedUrl" }
        return trimmedUrl
    }

    fun isValidPikabuUrl(url: String): Boolean {
        return try {
            validateUrl(url)
            true
        } catch (e: InvalidUrlException) {
            false
        }
    }
}
