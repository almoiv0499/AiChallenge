package org.example.notion

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class NotionClient(private val apiKey: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 30000
        }
    }
    private val baseUrl = "https://api.notion.com/v1"

    suspend fun getPage(pageId: String): NotionPage {
        return withContext(Dispatchers.IO) {
            val formattedPageId = formatPageIdForApi(pageId)
            val response = httpClient.get("$baseUrl/pages/$formattedPageId") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("Notion-Version", "2025-09-03")
            }
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    val errorResponse = json.decodeFromString(NotionErrorResponse.serializer(), errorBody)
                    when (errorResponse.code) {
                        "object_not_found" -> {
                            "Страница не найдена или недоступна для интеграции.\n" +
                            "Запрошенный ID: $formattedPageId\n" +
                            "Убедитесь, что:\n" +
                            "1. Страница существует в вашем workspace\n" +
                            "2. Страница доступна для вашей интеграции (откройте страницу в Notion → три точки → Connections → добавьте вашу интеграцию)\n" +
                            "3. ID страницы указан правильно (проверьте URL страницы)"
                        }
                        "unauthorized" -> "Не авторизован. Проверьте правильность API ключа."
                        "restricted_resource" -> "Доступ к ресурсу ограничен. Убедитесь, что интеграция имеет необходимые права."
                        else -> "${errorResponse.message} (код: ${errorResponse.code})"
                    }
                } catch (e: Exception) {
                    "HTTP ${response.status.value}: $errorBody"
                }
                throw NotionException(errorMessage)
            }
            val responseText = response.bodyAsText()
            json.decodeFromString(NotionPage.serializer(), responseText)
        }
    }

    private fun formatPageIdForApi(pageId: String): String {
        val trimmed = pageId.trim()
        val uuidPattern = Regex("""^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$""", RegexOption.IGNORE_CASE)
        if (uuidPattern.matches(trimmed)) {
            return trimmed
        }
        val cleanPageId = trimmed.replace("-", "").replace("_", "")
        return if (cleanPageId.length == 32) {
            "${cleanPageId.substring(0, 8)}-${cleanPageId.substring(8, 12)}-${cleanPageId.substring(12, 16)}-${cleanPageId.substring(16, 20)}-${cleanPageId.substring(20, 32)}"
        } else {
            trimmed
        }
    }

    fun close() {
        httpClient.close()
    }
}

class NotionException(message: String) : Exception(message)
