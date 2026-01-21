package com.pikabu.bot.service.auth

import com.pikabu.bot.entity.AuthConfigEntity
import com.pikabu.bot.repository.AuthConfigRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import jakarta.transaction.Transactional
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class CookieUpdateService(
    private val cookiesStorage: CookiesStorage,
    private val authConfigRepository: AuthConfigRepository
) {

    companion object {
        private const val COOKIE_CONFIG_KEY = "pikabu_cookies"
    }

    /**
     * Обновляет cookies в HTTP клиенте и сохраняет в БД
     */
    @Transactional
    fun updateCookies(cookieString: String, adminUserId: String? = null) {
        runBlocking {
            try {
                logger.info { "Updating Pikabu authentication cookies" }

                // Загружаем новые cookies в память
                loadCookiesFromString(cookiesStorage, cookieString)

                logger.info { "Cookies updated in memory successfully" }

                // Сохраняем в базу данных
                saveCookiesToDatabase(cookieString, adminUserId)

                logger.info { "Cookies saved to database successfully" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to update cookies" }
                throw e
            }
        }
    }

    /**
     * Сохраняет cookies в базу данных
     */
    private fun saveCookiesToDatabase(cookieString: String, updatedBy: String?) {
        val existingConfig = authConfigRepository.findByConfigKey(COOKIE_CONFIG_KEY)

        val configEntity = if (existingConfig.isPresent) {
            // Обновляем существующую запись
            existingConfig.get().copy(
                configValue = cookieString,
                updatedAt = LocalDateTime.now(),
                updatedBy = updatedBy
            )
        } else {
            // Создаём новую запись
            AuthConfigEntity(
                configKey = COOKIE_CONFIG_KEY,
                configValue = cookieString,
                updatedAt = LocalDateTime.now(),
                updatedBy = updatedBy
            )
        }

        authConfigRepository.save(configEntity)
        logger.debug { "Saved cookies config to database with key: $COOKIE_CONFIG_KEY" }
    }

    /**
     * Парсит cookies из строки формата "name1=value1; name2=value2; ..."
     * и добавляет их в CookiesStorage
     */
    private suspend fun loadCookiesFromString(storage: CookiesStorage, cookieString: String) {
        val domain = "pikabu.ru"
        val url = Url("https://$domain")

        var count = 0

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
                    count++
                }
            }
        }

        logger.info { "Loaded $count cookies into storage" }
    }

    /**
     * Проверяет текущее состояние cookies
     */
    fun getCookieStatus(): String {
        return runBlocking {
            try {
                val domain = "pikabu.ru"
                val url = Url("https://$domain")

                val currentCookies = cookiesStorage.get(url)

                if (currentCookies.isEmpty()) {
                    "❌ Cookies не установлены"
                } else {
                    val cookieNames = currentCookies.joinToString(", ") { it.name }
                    "✅ Установлено ${currentCookies.size} cookie(s): $cookieNames"
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to get cookie status" }
                "❌ Ошибка проверки cookies"
            }
        }
    }
}
