package com.pikabu.bot.entity

import com.pikabu.bot.domain.model.QueueStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "download_queue",
    indexes = [
        Index(name = "idx_status_created", columnList = "status,created_at"),
        Index(name = "idx_user_id", columnList = "user_id")
    ]
)
data class DownloadQueueEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "message_id", nullable = false)
    val messageId: Int,

    @Column(name = "video_url", length = 2048, nullable = false)
    val videoUrl: String,

    @Column(name = "video_title", length = 512)
    val videoTitle: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    var status: QueueStatus = QueueStatus.QUEUED,

    @Column(name = "position")
    var position: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
