package org.example.chatai.domain.embedding

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "EmbeddingClient"

/**
 * Клиент для генерации эмбеддингов через OpenRouter API.
 * Использует модель text-embedding-ada-002 для генерации векторных представлений текста.
 */
class EmbeddingClient(
    private val apiKey: String,
    private val baseUrl: String = "https://openrouter.ai/api/v1"
) {
    
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }
    
    companion object {
        private const val MODEL = "openai/text-embedding-ada-002"
    }
    
    /**
     * Генерирует эмбеддинг для текста.
     * @return FloatArray - векторное представление текста
     */
    suspend fun generateEmbedding(text: String): FloatArray {
        return try {
            val request = EmbeddingRequest(
                model = MODEL,
                input = text
            )
            
            val response: EmbeddingResponse = client.post("$baseUrl/embeddings") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(request)
            }.body()
            
            if (response.data.isNullOrEmpty()) {
                throw Exception("Пустой ответ от API эмбеддингов")
            }
            
            // Преобразуем List<Double> в FloatArray
            response.data[0].embedding.map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка генерации эмбеддинга", e)
            throw Exception("Ошибка генерации эмбеддинга: ${e.message}", e)
        }
    }
    
    /**
     * Закрывает HTTP клиент
     */
    fun close() {
        client.close()
    }
}

@Serializable
private data class EmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
private data class EmbeddingResponse(
    val data: List<EmbeddingData>? = null,
    val error: EmbeddingError? = null
)

@Serializable
private data class EmbeddingData(
    val embedding: List<Double>,
    val index: Int = 0
)

@Serializable
private data class EmbeddingError(
    val message: String? = null,
    val code: String? = null
)
