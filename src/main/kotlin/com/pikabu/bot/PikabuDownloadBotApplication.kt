package com.pikabu.bot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PikabuDownloadBotApplication

fun main(args: Array<String>) {
    runApplication<PikabuDownloadBotApplication>(*args)
}
