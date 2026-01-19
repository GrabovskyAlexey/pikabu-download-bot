package com.pikabu.bot.domain.model

enum class QueueStatus {
    QUEUED,       // В очереди
    DOWNLOADING,  // Загружается
    COMPLETED,    // Завершено
    FAILED        // Ошибка
}
