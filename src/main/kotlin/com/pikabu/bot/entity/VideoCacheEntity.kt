package com.pikabu.bot.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "video_cache",
    schema = "pikabu_bot",
    indexes = [Index(name = "idx_last_used_at", columnList = "last_used_at")]
)
data class VideoCacheEntity(
    @Id
    @Column(name = "video_url", length = 2048)
    val videoUrl: String,

    @Column(name = "file_id", length = 256, nullable = false)
    val fileId: String,

    @Column(name = "file_size")
    val fileSize: Long? = null,

    @Column(name = "cached_at", nullable = false)
    val cachedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: LocalDateTime = LocalDateTime.now()
)
