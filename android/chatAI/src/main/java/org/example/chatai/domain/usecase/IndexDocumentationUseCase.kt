package org.example.chatai.domain.usecase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.chatai.domain.embedding.DocumentIndexer
import java.util.UUID

private const val TAG = "IndexDocumentationUseCase"

/**
 * Use case для индексации документации проекта.
 * Индексирует текстовые документы для использования в RAG поиске.
 */
class IndexDocumentationUseCase(
    private val documentIndexer: DocumentIndexer
) {
    
    /**
     * Индексирует текст документа.
     * 
     * @param text Текст документа для индексации
     * @param source Источник документа (например, путь к файлу или название)
     * @param title Заголовок документа
     * @return Количество проиндексированных чанков
     */
    suspend fun indexDocument(
        text: String,
        source: String,
        title: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            val documentId = UUID.randomUUID().toString()
            
            val chunkCount = documentIndexer.indexDocument(
                documentId = documentId,
                text = text,
                source = source,
                title = title
            )
            
            Log.d(TAG, "Документ индексирован: $source ($chunkCount чанков)")
            Result.success(chunkCount)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка индексации документа: $source", e)
            Result.failure(e)
        }
    }
    
    /**
     * Индексирует несколько текстовых документов.
     * 
     * @param documents Список документов для индексации (source, text, title)
     * @return Общее количество проиндексированных чанков
     */
    suspend fun indexDocuments(
        documents: List<Triple<String, String, String?>> // (source, text, title)
    ): Result<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            var totalChunks = 0
            
            documents.forEach { (source, text, title) ->
                val result = indexDocument(text, source, title)
                result.onSuccess { chunkCount ->
                    totalChunks += chunkCount
                }.onFailure { e ->
                    Log.e(TAG, "Ошибка индексации документа: $source", e)
                }
            }
            
            Log.d(TAG, "Индексация завершена: ${documents.size} документов, $totalChunks чанков")
            Result.success(totalChunks)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка индексации документов", e)
            Result.failure(e)
        }
    }
}
