package org.example.embedding

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Клиент для генерации эмбеддингов через OpenRouter API.
 * Использует модель text-embedding-ada-002 или аналогичную.
 */
class EmbeddingClient(private val apiKey: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000L
            connectTimeoutMillis = 30_000L
        }
        install(ContentNegotiation) { json(json) }
    }
    
    companion object {
        private const val API_URL = "https://openrouter.ai/api/v1/embeddings"
        // Используем модель OpenAI для эмбеддингов через OpenRouter
        private const val MODEL = "openai/text-embedding-ada-002"
    }
    
    /**
     * Генерирует эмбеддинг для одного текста.
     */
    suspend fun generateEmbedding(text: String): FloatArray {
        val response = client.post(API_URL) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            header("HTTP-Referer", "https://github.com/mobile-next/mobile-mcp")
            header("X-Title", "Mobile MCP Document Indexer")
            setBody(EmbeddingRequest(
                model = MODEL,
                input = text
            ))
        }
        
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Ошибка генерации эмбеддинга: ${response.status} - $errorBody")
        }
        
        val result: EmbeddingResponse = response.body()
        if (result.data.isEmpty()) {
            throw RuntimeException("Пустой ответ от API эмбеддингов")
        }
        
        // Преобразуем List<Double> в FloatArray
        return result.data[0].embedding.map { it.toFloat() }.toFloatArray()
    }
    
    /**
     * Генерирует эмбеддинги для списка текстов.
     * OpenRouter поддерживает batch запросы.
     */
    suspend fun generateEmbeddings(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        
        // OpenRouter может обрабатывать batch, но для надежности делаем по одному
        // или можно попробовать batch, если API поддерживает
        return texts.map { text ->
            generateEmbedding(text)
        }
    }
    
    fun close() = client.close()
}

@Serializable
private data class EmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
private data class EmbeddingResponse(
    val data: List<EmbeddingData>,
    val model: String? = null
)

@Serializable
private data class EmbeddingData(
    val embedding: List<Double>,
    val index: Int? = null
)

