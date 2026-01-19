package com.pikabu.bot

import com.pikabu.bot.config.TelegramBotConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(TelegramBotConfig::class)
class PikabuDownloadBotApplication

fun main(args: Array<String>) {
    runApplication<PikabuDownloadBotApplication>(*args)
}
