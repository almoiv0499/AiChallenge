package org.example.chatai.domain.embedding

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.chatai.data.local.DocumentChunkDao
import org.example.chatai.data.local.DocumentDao
import org.example.chatai.data.model.DocumentChunkEntity
import org.example.chatai.data.model.DocumentEntity
import kotlinx.coroutines.flow.first
import kotlin.math.sqrt

private const val TAG = "RagService"

/**
 * RAG (Retrieval-Augmented Generation) сервис для поиска информации в документации проекта.
 * Использует векторный поиск (cosine similarity) для нахождения релевантных фрагментов.
 */
class RagService(
    private val embeddingClient: EmbeddingClient,
    private val documentDao: DocumentDao,
    private val documentChunkDao: DocumentChunkDao
) {
    
    /**
     * Поиск релевантной информации в документации по текстовому запросу.
     * 
     * @param query Текстовый запрос
     * @param limit Количество результатов
     * @param minSimilarity Минимальный порог сходства (0.0 - 1.0)
     * @return Список найденных фрагментов документации с оценкой сходства
     */
    suspend fun search(
        query: String,
        limit: Int = 10,
        minSimilarity: Double = 0.3
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            // Генерируем эмбеддинг для запроса
            val queryEmbedding = embeddingClient.generateEmbedding(query)
            
            // Получаем все чанки и документы из базы данных
            val allChunks = documentChunkDao.getAllChunks()
            val allDocuments = documentDao.getAllDocumentsSync()
            val documentsMap = allDocuments.associateBy { it.id }
            
            if (allChunks.isEmpty()) {
                Log.d(TAG, "Нет проиндексированных документов для поиска")
                return@withContext emptyList()
            }
            
            // Вычисляем cosine similarity для каждого чанка
            val results = allChunks.mapNotNull { chunk ->
                try {
                    val document = documentsMap[chunk.documentId]
                    val chunkEmbedding = deserializeEmbedding(chunk.embedding)
                    val similarity = cosineSimilarity(queryEmbedding, chunkEmbedding)
                    
                    if (similarity >= minSimilarity) {
                        SearchResult(
                            chunkId = chunk.id,
                            documentId = chunk.documentId,
                            chunkIndex = chunk.chunkIndex,
                            text = chunk.text,
                            source = document?.source ?: "unknown",
                            title = document?.title,
                            similarity = similarity,
                            metadata = parseMetadata(chunk.metadata)
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка обработки чанка ${chunk.id}", e)
                    null
                }
            }
            
            // Сортируем по сходству и берем топ-N
            results.sortedByDescending { it.similarity }.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при поиске в RAG", e)
            emptyList()
        }
    }
    
    /**
     * Проверяет, есть ли проиндексированные документы.
     */
    suspend fun hasDocuments(): Boolean {
        return try {
            documentDao.getDocumentCount() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки наличия документов", e)
            false
        }
    }
    
    /**
     * Получает количество проиндексированных документов.
     */
    suspend fun getDocumentCount(): Int {
        return try {
            documentDao.getDocumentCount()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения количества документов", e)
            0
        }
    }
    
    /**
     * Получает все документы из индекса.
     */
    suspend fun getAllDocuments(): List<DocumentEntity> {
        return try {
            documentDao.getAllDocumentsSync()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения всех документов", e)
            emptyList()
        }
    }
    
    // Вспомогательные методы
    
    private fun deserializeEmbedding(bytes: ByteArray): FloatArray {
        val embedding = FloatArray(bytes.size / 4)
        var offset = 0
        for (i in embedding.indices) {
            val bits = ((bytes[offset++].toInt() and 0xFF) shl 24) or
                      ((bytes[offset++].toInt() and 0xFF) shl 16) or
                      ((bytes[offset++].toInt() and 0xFF) shl 8) or
                      (bytes[offset++].toInt() and 0xFF)
            embedding[i] = java.lang.Float.intBitsToFloat(bits)
        }
        return embedding
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Embeddings must have the same dimension" }
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0.0) 0.0 else dotProduct / denominator
    }
    
    private fun parseMetadata(metadataJson: String?): Map<String, String> {
        if (metadataJson == null || metadataJson.isBlank() || metadataJson == "{}") {
            return emptyMap()
        }
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val jsonObject = json.parseToJsonElement(metadataJson) as kotlinx.serialization.json.JsonObject
            jsonObject.entries.associate { (key, value) ->
                key to value.toString().trim('"')
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка парсинга метаданных", e)
            emptyMap()
        }
    }
}

/**
 * Результат поиска в RAG.
 */
data class SearchResult(
    val chunkId: String,
    val documentId: String,
    val chunkIndex: Int,
    val text: String,
    val source: String,
    val title: String?,
    val similarity: Double,
    val metadata: Map<String, String> = emptyMap()
)
