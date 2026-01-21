package com.pikabu.bot.config

import com.pikabu.bot.repository.AuthConfigRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val logger = KotlinLogging.logger {}

@Configuration
class HttpClientConfig {

    companion object {
        private const val COOKIE_CONFIG_KEY = "pikabu_cookies"
    }

    // Храним ссылку на CookiesStorage для возможности обновления в runtime
    private lateinit var cookieStorage: CookiesStorage

    @Bean
    fun cookiesStorage(): CookiesStorage {
        cookieStorage = AcceptAllCookiesStorage()
        return cookieStorage
    }

    @Bean
    fun httpClient(
        authConfig: PikabuAuthConfig,
        cookiesStorage: CookiesStorage,
        authConfigRepository: AuthConfigRepository
    ): HttpClient {
        // Приоритет: БД > .env конфигурация
        val cookiesToLoad = runBlocking {
            try {
                // Пытаемся загрузить из БД
                val cookiesFromDb = authConfigRepository.findByConfigKey(COOKIE_CONFIG_KEY)
                    .map { it.configValue }
                    .orElse(null)

                if (cookiesFromDb != null && cookiesFromDb.isNotBlank()) {
                    logger.info { "Loading Pikabu authentication cookies from database" }
                    cookiesFromDb
                } else if (authConfig.enabled && authConfig.cookies.isNotBlank()) {
                    logger.info { "Loading Pikabu authentication cookies from config file" }
                    authConfig.cookies
                } else {
                    logger.info { "No Pikabu authentication cookies configured" }
                    null
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load cookies from database, falling back to config" }
                if (authConfig.enabled && authConfig.cookies.isNotBlank()) {
                    authConfig.cookies
                } else {
                    null
                }
            }
        }

        // Загружаем cookies если они есть
        if (cookiesToLoad != null) {
            runBlocking {
                loadCookiesFromString(cookiesStorage, cookiesToLoad)
            }
        }

        return HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }

            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }

            install(ContentNegotiation)

            // Поддержка cookies для авторизации
            install(HttpCookies) {
                storage = cookiesStorage
            }

            // Настройки для следования редиректам
            followRedirects = true

            // Настройки для повторных попыток
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 2)
                exponentialDelay()
            }
        }
    }

    /**
     * Парсит cookies из строки формата "name1=value1; name2=value2; ..."
     * и добавляет их в CookiesStorage
     */
    private suspend fun loadCookiesFromString(storage: CookiesStorage, cookieString: String) {
        val domain = "pikabu.ru"
        val url = Url("https://$domain")

        // Парсим cookies
        cookieString.split(";").forEach { cookiePart ->
            val trimmed = cookiePart.trim()
            if (trimmed.isNotEmpty()) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    val name = parts[0].trim()
                    val value = parts[1].trim()

                    // Создаём Cookie объект
                    val cookie = Cookie(
                        name = name,
                        value = value,
                        domain = domain,
                        path = "/"
                    )

                    // Добавляем в storage
                    storage.addCookie(url, cookie)
                }
            }
        }

        logger.debug { "Loaded cookies from string into storage" }
    }
}
