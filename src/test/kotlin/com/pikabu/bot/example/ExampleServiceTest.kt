package com.pikabu.bot.example

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk

class ExampleServiceTest : FunSpec({

    val service = ExampleService()

    context("processData") {
        test("should convert input to uppercase") {
            val result = service.processData("hello")
            result shouldBe "HELLO"
        }

        test("should handle empty string") {
            val result = service.processData("")
            result shouldBe ""
        }

        test("should preserve uppercase") {
            val result = service.processData("WORLD")
            result shouldBe "WORLD"
        }
    }

    context("complexOperation") {
        test("should return success when key exists") {
            val data = mapOf("key" to "value123")
            val result = service.complexOperation(data)

            result.isSuccess shouldBe true
            result.getOrNull() shouldBe "value123"
        }

        test("should return failure when key is missing") {
            val data = mapOf("other" to "value")
            val result = service.complexOperation(data)

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldContain "Missing 'key' parameter"
        }

        test("should handle invalid key type") {
            val data = mapOf<String, Any>("key" to 123)
            val result = service.complexOperation(data)

            result.isFailure shouldBe true
        }
    }

    // Пример использования MockK
    context("with mocks") {
        test("example of using MockK") {
            val mockService = mockk<ExampleService>(relaxed = true)
            // mockk конфигурация будет здесь
        }
    }
})
