package com.pikabu.bot.example

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ExampleService {

    fun processData(input: String): String {
        logger.info { "Processing data: $input" }

        return try {
            val result = input.uppercase()
            logger.debug { "Successfully processed: $result" }
            result
        } catch (e: Exception) {
            logger.error(e) { "Error processing data: $input" }
            throw e
        }
    }

    fun complexOperation(data: Map<String, Any>): Result<String> {
        logger.trace { "Starting complex operation with data: $data" }

        return runCatching {
            val value = data["key"] as? String
                ?: throw IllegalArgumentException("Missing 'key' parameter")

            logger.info { "Operation successful for key: $value" }
            value
        }.onFailure { error ->
            logger.warn(error) { "Complex operation failed" }
        }
    }
}
