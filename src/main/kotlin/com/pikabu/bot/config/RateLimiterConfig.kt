package com.pikabu.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.rate-limit")
data class RateLimiterConfig(
    var maxRequests: Int = 1000,
    var windowHours: Int = 1
)
