package com.pikabu.bot.domain.model

/**
 * Платформа видео
 */
enum class VideoPlatform {
    /**
     * Прямое видео с Pikabu (mp4/webm файлы)
     */
    PIKABU,

    /**
     * YouTube видео
     */
    YOUTUBE,

    /**
     * RuTube видео
     */
    RUTUBE,

    /**
     * VK видео
     */
    VKVIDEO
}
