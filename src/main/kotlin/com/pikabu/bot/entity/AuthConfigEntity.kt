package com.pikabu.bot.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "auth_config",
    schema = "pikabu_bot",
    uniqueConstraints = [UniqueConstraint(name = "uk_config_key", columnNames = ["config_key"])]
)
data class AuthConfigEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "config_key", length = 50, nullable = false, unique = true)
    val configKey: String,

    @Column(name = "config_value", columnDefinition = "TEXT")
    val configValue: String?,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_by", length = 100)
    val updatedBy: String? = null
)
