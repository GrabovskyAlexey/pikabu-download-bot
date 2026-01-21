package com.pikabu.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "pikabu.auth")
data class PikabuAuthConfig(
    var enabled: Boolean = false,
    var cookies: String = ""
)
