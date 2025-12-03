package org.example.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.config.GigaChatConfig
import org.example.models.GigaChatRequest
import org.example.models.GigaChatResponse
import org.example.models.GigaChatTokenResponse
import org.example.ui.ConsoleUI
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.X509TrustManager

class GigaChatClient(
    private val authorizationKey: String,
    private val scope: String = GigaChatConfig.DEFAULT_SCOPE
) {
    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0
    private val json = createJsonSerializer()
    private val client = createHttpClient()

    suspend fun chat(request: GigaChatRequest): GigaChatResponse {
        val token = getValidToken()
        logRequest()
        val response = sendChatRequest(token, request)
        validateResponse(response)
        return parseResponse(response)
    }

    fun close() = client.close()

    private suspend fun getValidToken(): String {
        val currentTime = System.currentTimeMillis()
        val isTokenExpired = accessToken == null ||
            currentTime >= tokenExpiresAt - GigaChatConfig.TOKEN_REFRESH_MARGIN_MS
        return if (isTokenExpired) fetchAccessToken() else accessToken!!
    }

    private suspend fun fetchAccessToken(): String {
        ConsoleUI.printFetchingToken()
        val response = client.post(GigaChatConfig.AUTH_URL) {
            header(GigaChatConfig.Headers.RQ_UID, UUID.randomUUID().toString())
            header(GigaChatConfig.Headers.AUTHORIZATION, "Basic $authorizationKey")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build { append("scope", scope) }))
        }
        validateResponse(response)
        val tokenResponse: GigaChatTokenResponse = response.body()
        accessToken = tokenResponse.accessToken
        tokenExpiresAt = tokenResponse.expiresAt
        ConsoleUI.printTokenObtained(Date(tokenExpiresAt).toString())
        return tokenResponse.accessToken
    }

    private suspend fun sendChatRequest(token: String, request: GigaChatRequest) =
        client.post("${GigaChatConfig.API_BASE_URL}${GigaChatConfig.CHAT_COMPLETIONS_ENDPOINT}") {
            contentType(ContentType.Application.Json)
            header(GigaChatConfig.Headers.AUTHORIZATION, "Bearer $token")
            setBody(request)
        }

    private suspend fun validateResponse(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Ошибка API: ${response.status} - $errorBody")
        }
    }

    private suspend fun parseResponse(response: HttpResponse): GigaChatResponse {
        val result: GigaChatResponse = response.body()
        logResponse(result)
        return result
    }

    private fun logRequest() = ConsoleUI.printSendingRequest()

    private fun logResponse(response: GigaChatResponse) {
        ConsoleUI.printResponseReceived(
            finishReason = response.choices.firstOrNull()?.finishReason,
            tokensUsed = response.usage?.totalTokens
        )
    }

    private fun createJsonSerializer() = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
    }

    private fun createHttpClient() = HttpClient(CIO) {
        engine {
            https {
                trustManager = createTrustAllManager()
            }
        }
        install(ContentNegotiation) { json(json) }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = ConsoleUI.printHttpLog(message)
            }
            level = LogLevel.INFO
        }
    }

    private fun createTrustAllManager() = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
}
