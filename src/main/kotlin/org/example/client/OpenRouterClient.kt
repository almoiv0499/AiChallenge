package org.example.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.example.config.OpenRouterConfig
import org.example.models.OpenRouterRequest
import org.example.models.OpenRouterResponse
import org.example.ui.ConsoleUI

class OpenRouterClient(private val apiKey: String) {
    private val json = createJsonSerializer()
    private val client = createHttpClient()

    suspend fun createResponse(request: OpenRouterRequest): OpenRouterResponse {
        val startTime = System.currentTimeMillis()
        val response = sendRequest(request)
        val responseTimeMs = System.currentTimeMillis() - startTime
        validateResponse(response)
        return parseResponse(request.temperature, response, responseTimeMs)
    }

    fun close() = client.close()

    private suspend fun sendRequest(request: OpenRouterRequest): HttpResponse {
        return client.post(OpenRouterConfig.API_URL) {
            contentType(ContentType.Application.Json)
            header(OpenRouterConfig.Headers.AUTHORIZATION, "Bearer $apiKey")
            setBody(request)
        }
    }

    private suspend fun validateResponse(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            val statusCode = response.status.value
            if (isTokenLimitError(statusCode, errorBody)) {
                throw TokenLimitExceededException(
                    message = "Превышен лимит токенов в запросе",
                    statusCode = statusCode,
                    errorCode = extractErrorCode(errorBody)
                )
            }
            throw RuntimeException("Ошибка API: ${response.status} - $errorBody")
        }
    }

    private suspend fun parseResponse(
        temperature: Double?,
        response: HttpResponse,
        responseTimeMs: Long
    ): OpenRouterResponse {
        val result: OpenRouterResponse = response.body()
        logResponse(temperature, result, responseTimeMs)
        if (result.error != null) {
            val error = result.error
            if (isTokenLimitErrorInResponse(error)) {
                throw TokenLimitExceededException(
                    message = error.message ?: "Превышен лимит токенов",
                    errorCode = error.code
                )
            }
            throw RuntimeException("API Error: ${error.code} - ${error.message}")
        }
        return result
    }

    private fun logResponse(temperature: Double?, response: OpenRouterResponse, responseTimeMs: Long) {
        ConsoleUI.printResponseReceived(
            temperature = temperature,
            finishReason = "completed",
            inputTokens = response.usage?.inputTokens,
            outputTokens = response.usage?.outputTokens,
            totalTokens = response.usage?.totalTokens,
            responseTimeMs = responseTimeMs
        )
    }

    private fun createJsonSerializer() = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        isLenient = true
    }

    private fun createHttpClient() = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        install(ContentNegotiation) { json(json) }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = ConsoleUI.printHttpLog(message)
            }
            level = LogLevel.INFO
        }
    }

    private fun isTokenLimitError(statusCode: Int, errorBody: String): Boolean {
        return statusCode == HttpStatusCode.PayloadTooLarge.value ||
                statusCode == HttpStatusCode.BadRequest.value && containsTokenLimitKeywords(errorBody)
    }

    private fun isTokenLimitErrorInResponse(error: org.example.models.OpenRouterError): Boolean {
        val message = error.message?.lowercase() ?: ""
        val code = error.code?.lowercase() ?: ""
        return containsTokenLimitKeywords(message) || containsTokenLimitKeywords(code)
    }

    private fun containsTokenLimitKeywords(text: String): Boolean {
        val lowerText = text.lowercase()
        return lowerText.contains("token") && (
                lowerText.contains("limit") ||
                lowerText.contains("exceeded") ||
                lowerText.contains("too large") ||
                lowerText.contains("maximum") ||
                lowerText.contains("context_length_exceeded") ||
                lowerText.contains("request_too_large")
        )
    }

    private fun extractErrorCode(errorBody: String): String? {
        return try {
            val json = json.parseToJsonElement(errorBody)
            if (json is JsonObject) {
                json.jsonObject["code"]?.toString()?.trim('"')
            } else null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 120_000L
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val SOCKET_TIMEOUT_MS = 120_000L
    }
}

