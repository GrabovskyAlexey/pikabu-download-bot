package com.pikabu.bot.domain.model

data class VideoInfo(
    val url: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val format: VideoFormat = VideoFormat.UNKNOWN,
    val isExternal: Boolean = false,
    val platform: VideoPlatform = VideoPlatform.PIKABU
)

enum class VideoFormat {
    MP4,
    WEBM,
    MOV,
    AVI,
    UNKNOWN
}

fun String.toVideoFormat(): VideoFormat {
    return when (this.lowercase()) {
        "mp4" -> VideoFormat.MP4
        "webm" -> VideoFormat.WEBM
        "mov" -> VideoFormat.MOV
        "avi" -> VideoFormat.AVI
        else -> VideoFormat.UNKNOWN
    }
}
