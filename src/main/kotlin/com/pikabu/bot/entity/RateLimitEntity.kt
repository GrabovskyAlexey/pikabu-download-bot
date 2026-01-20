package com.pikabu.bot.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "rate_limits",
    schema = "pikabu_bot",
    indexes = [Index(name = "idx_user_id_rate", columnList = "user_id")]
)
data class RateLimitEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", unique = true, nullable = false)
    val userId: Long,

    @Column(name = "request_count", nullable = false)
    var requestCount: Int = 0,

    @Column(name = "window_start", nullable = false)
    var windowStart: LocalDateTime = LocalDateTime.now(),

    @Column(name = "window_end")
    var windowEnd: LocalDateTime? = null
)
