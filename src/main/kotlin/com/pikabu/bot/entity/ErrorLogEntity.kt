package com.pikabu.bot.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "error_log",
    schema = "pikabu_bot",
    indexes = [
        Index(name = "idx_error_type_occurred", columnList = "error_type,occurred_at"),
        Index(name = "idx_notified_admin", columnList = "notified_admin")
    ]
)
data class ErrorLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "error_type", length = 100, nullable = false)
    val errorType: String,

    @Column(name = "error_message", columnDefinition = "TEXT", nullable = false)
    val errorMessage: String,

    @Column(name = "page_url", length = 2048)
    val pageUrl: String? = null,

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    val stackTrace: String? = null,

    @Column(name = "notified_admin", nullable = false)
    var notifiedAdmin: Boolean = false,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: LocalDateTime = LocalDateTime.now()
)
