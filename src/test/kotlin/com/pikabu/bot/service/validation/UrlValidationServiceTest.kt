package com.pikabu.bot.service.validation

import com.pikabu.bot.domain.exception.InvalidUrlException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UrlValidationServiceTest : FunSpec({

    val service = UrlValidationService()

    context("validateUrl") {
        test("should accept valid pikabu.ru URL") {
            val result = service.validateUrl("https://pikabu.ru/story/test")
            result shouldBe "https://pikabu.ru/story/test"
        }

        test("should accept valid www.pikabu.ru URL") {
            val result = service.validateUrl("https://www.pikabu.ru/story/test")
            result shouldBe "https://www.pikabu.ru/story/test"
        }

        test("should trim whitespace") {
            val result = service.validateUrl("  https://pikabu.ru/story/test  ")
            result shouldBe "https://pikabu.ru/story/test"
        }

        test("should reject empty URL") {
            shouldThrow<InvalidUrlException> {
                service.validateUrl("")
            }
        }

        test("should reject blank URL") {
            shouldThrow<InvalidUrlException> {
                service.validateUrl("   ")
            }
        }

        test("should reject non-pikabu domain") {
            shouldThrow<InvalidUrlException> {
                service.validateUrl("https://youtube.com/watch")
            }
        }

        test("should reject URL without domain") {
            shouldThrow<InvalidUrlException> {
                service.validateUrl("not-a-url")
            }
        }
    }

    context("isValidPikabuUrl") {
        test("should return true for valid pikabu URL") {
            service.isValidPikabuUrl("https://pikabu.ru/story/test") shouldBe true
        }

        test("should return false for invalid URL") {
            service.isValidPikabuUrl("https://youtube.com") shouldBe false
        }

        test("should return false for empty string") {
            service.isValidPikabuUrl("") shouldBe false
        }
    }
})
