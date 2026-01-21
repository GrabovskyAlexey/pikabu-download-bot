package com.pikabu.bot.domain.exception

class AuthenticationException(
    message: String,
    val statusCode: Int
) : RuntimeException(message)
