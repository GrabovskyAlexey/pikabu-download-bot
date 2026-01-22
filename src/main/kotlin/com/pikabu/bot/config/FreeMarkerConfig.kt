package com.pikabu.bot.config

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
class FreeMarkerConfig {

    @Bean
    @Primary
    fun freemarkerConfiguration(): Configuration {
        val configuration = Configuration(Configuration.VERSION_2_3_33)
        configuration.setClassForTemplateLoading(this.javaClass, "/templates")
        configuration.defaultEncoding = "UTF-8"
        configuration.templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        configuration.logTemplateExceptions = false
        configuration.wrapUncheckedExceptions = true
        configuration.fallbackOnNullLoopVariable = false
        return configuration
    }
}
