package com.pikabu.bot.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "download_history",
    indexes = [Index(name = "idx_user_id_completed", columnList = "user_id,completed_at")]
)
data class DownloadHistoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "video_url", length = 2048, nullable = false)
    val videoUrl: String,

    @Column(name = "video_title", length = 512)
    val videoTitle: String? = null,

    @Column(name = "status", length = 50, nullable = false)
    val status: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @Column(name = "completed_at", nullable = false)
    val completedAt: LocalDateTime = LocalDateTime.now()
)
