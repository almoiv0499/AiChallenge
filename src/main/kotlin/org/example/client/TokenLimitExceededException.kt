package org.example.client

class TokenLimitExceededException(
    message: String,
    val statusCode: Int? = null,
    val errorCode: String? = null
) : RuntimeException(message)

