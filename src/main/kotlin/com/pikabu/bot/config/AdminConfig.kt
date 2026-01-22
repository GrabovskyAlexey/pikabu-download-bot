package com.pikabu.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.admin")
data class AdminConfig(
    var userId: Long = 0,
    var enableNotifications: Boolean = true,
    var enableDailyDigest: Boolean = false,
    var enableWeeklyDigest: Boolean = false
)
