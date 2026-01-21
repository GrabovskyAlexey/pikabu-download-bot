package com.pikabu.bot.domain.model

enum class ErrorType {
    PARSING_ERROR,          // Ошибки парсинга HTML
    DOWNLOAD_ERROR,         // Ошибки загрузки видео
    AUTHENTICATION_ERROR,   // Ошибки авторизации (401/403)
    SYSTEM_ERROR            // Критические системные ошибки
}
