package com.pikabu.bot.domain.model

enum class ErrorType {
    PARSING_ERROR,      // Ошибки парсинга HTML
    DOWNLOAD_ERROR,     // Ошибки загрузки видео
    SYSTEM_ERROR        // Критические системные ошибки
}
