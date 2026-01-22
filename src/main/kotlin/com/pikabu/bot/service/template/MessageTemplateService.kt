package com.pikabu.bot.service.template

import freemarker.template.Configuration
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.StringWriter

private val logger = KotlinLogging.logger {}

@Service
class MessageTemplateService(
    private val freemarkerConfig: Configuration
) {

    /**
     * Рендерит шаблон пользовательского сообщения
     */
    fun renderMessage(templateName: String, data: Map<String, Any?> = emptyMap()): String {
        return renderTemplate("messages/$templateName", data)
    }

    /**
     * Рендерит шаблон уведомления администратору
     */
    fun renderNotification(templateName: String, data: Map<String, Any?> = emptyMap()): String {
        return renderTemplate("notifications/$templateName", data)
    }

    /**
     * Рендерит шаблон FreeMarker с заданными данными
     */
    private fun renderTemplate(templatePath: String, data: Map<String, Any?>): String {
        return try {
            val template = freemarkerConfig.getTemplate(templatePath)
            val writer = StringWriter()
            template.process(data, writer)
            writer.toString()
        } catch (e: Exception) {
            logger.error(e) { "Failed to render template: $templatePath" }
            "Ошибка при формировании сообщения"
        }
    }
}
