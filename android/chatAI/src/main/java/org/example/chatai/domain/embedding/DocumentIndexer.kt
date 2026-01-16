package org.example.chatai.domain.embedding

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.chatai.data.local.DocumentChunkDao
import org.example.chatai.data.local.DocumentDao
import org.example.chatai.data.model.DocumentChunkEntity
import org.example.chatai.data.model.DocumentEntity
import java.util.UUID

private const val TAG = "DocumentIndexer"
private const val CHUNK_SIZE = 500 // Примерный размер чанка в символах
private const val CHUNK_OVERLAP = 50 // Перекрытие между чанками

/**
 * Сервис для индексации документов.
 * Разбивает документы на чанки, генерирует эмбеддинги и сохраняет в базу данных.
 */
class DocumentIndexer(
    private val embeddingClient: EmbeddingClient,
    private val documentDao: DocumentDao,
    private val documentChunkDao: DocumentChunkDao
) {
    
    /**
     * Индексирует документ.
     * 
     * @param documentId ID документа
     * @param text Текст документа
     * @param source Источник документа (например, путь к файлу)
     * @param title Заголовок документа
     * @param metadata Метаданные документа
     * @return Количество проиндексированных чанков
     */
    suspend fun indexDocument(
        documentId: String,
        text: String,
        source: String,
        title: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Начало индексации документа: $documentId")
            
            // Разбиваем текст на чанки
            val chunks = splitIntoChunks(text)
            
            if (chunks.isEmpty()) {
                Log.w(TAG, "Документ $documentId не содержит текста для индексации")
                return@withContext 0
            }
            
            // Сохраняем документ
            val document = DocumentEntity(
                id = documentId,
                source = source,
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            documentDao.insertDocument(document)
            
            // Удаляем старые чанки документа
            documentChunkDao.deleteChunksByDocumentId(documentId)
            
            // Генерируем эмбеддинги и сохраняем чанки
            val indexedChunks = mutableListOf<DocumentChunkEntity>()
            
            chunks.forEachIndexed { index, chunkText ->
                try {
                    // Генерируем эмбеддинг
                    val embedding = embeddingClient.generateEmbedding(chunkText)
                    
                    // Сериализуем эмбеддинг в ByteArray
                    val embeddingBytes = serializeEmbedding(embedding)
                    
                    // Создаем чанк
                    val chunkId = "${documentId}_chunk_$index"
                    val metadataJson = if (metadata.isEmpty()) {
                        null
                    } else {
                        serializeMetadata(metadata + ("chunk" to index.toString()))
                    }
                    
                    val chunk = DocumentChunkEntity(
                        id = chunkId,
                        documentId = documentId,
                        chunkIndex = index,
                        text = chunkText,
                        embedding = embeddingBytes,
                        embeddingDimension = embedding.size,
                        metadata = metadataJson,
                        createdAt = System.currentTimeMillis()
                    )
                    
                    indexedChunks.add(chunk)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка индексации чанка $index документа $documentId", e)
                }
            }
            
            // Сохраняем все чанки
            if (indexedChunks.isNotEmpty()) {
                documentChunkDao.insertChunks(indexedChunks)
            }
            
            Log.d(TAG, "Индексация документа $documentId завершена: ${indexedChunks.size} чанков")
            indexedChunks.size
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка индексации документа $documentId", e)
            0
        }
    }
    
    /**
     * Разбивает текст на чанки для индексации.
     */
    private fun splitIntoChunks(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        
        val chunks = mutableListOf<String>()
        val lines = text.lines()
        var currentChunk = StringBuilder()
        
        for (line in lines) {
            currentChunk.append(line).append("\n")
            
            // Если текущий чанк достиг размера, сохраняем его
            if (currentChunk.length >= CHUNK_SIZE) {
                chunks.add(currentChunk.toString().trim())
                
                // Начинаем новый чанк с перекрытием (последние CHUNK_OVERLAP символов)
                val chunkText = currentChunk.toString()
                val overlap = if (chunkText.length > CHUNK_OVERLAP) {
                    chunkText.takeLast(CHUNK_OVERLAP)
                } else {
                    chunkText
                }
                currentChunk = StringBuilder(overlap)
            }
        }
        
        // Добавляем последний чанк, если он не пустой
        val lastChunk = currentChunk.toString().trim()
        if (lastChunk.isNotBlank()) {
            chunks.add(lastChunk)
        }
        
        return if (chunks.isEmpty()) listOf(text) else chunks
    }
    
    /**
     * Сериализует эмбеддинг в ByteArray
     */
    private fun serializeEmbedding(embedding: FloatArray): ByteArray {
        val bytes = ByteArray(embedding.size * 4)
        var offset = 0
        for (value in embedding) {
            val bits = java.lang.Float.floatToIntBits(value)
            bytes[offset++] = (bits shr 24).toByte()
            bytes[offset++] = (bits shr 16).toByte()
            bytes[offset++] = (bits shr 8).toByte()
            bytes[offset++] = bits.toByte()
        }
        return bytes
    }
    
    /**
     * Сериализует метаданные в JSON строку
     */
    private fun serializeMetadata(metadata: Map<String, String>): String {
        if (metadata.isEmpty()) return "{}"
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val jsonObject = kotlinx.serialization.json.buildJsonObject {
                metadata.forEach { (key, value) ->
                    put(key, kotlinx.serialization.json.JsonPrimitive(value))
                }
            }
            json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                jsonObject
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сериализации метаданных", e)
            "{}"
        }
    }
}
