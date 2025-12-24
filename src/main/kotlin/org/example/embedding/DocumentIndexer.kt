package org.example.embedding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Пайплайн для индексации документов:
 * 1. Разбивка текста на чанки
 * 2. Генерация эмбеддингов для каждого чанка
 * 3. Сохранение в SQLite индекс
 */
class DocumentIndexer(
    private val embeddingClient: EmbeddingClient,
    private val storage: DocumentIndexStorage,
    private val chunker: TextChunker = TextChunker()
) {
    /**
     * Индексирует документ: разбивает на чанки, генерирует эмбеддинги и сохраняет.
     * @param documentId Уникальный идентификатор документа
     * @param text Текст документа
     * @param source Источник документа (например, путь к файлу)
     * @param title Заголовок документа
     * @param metadata Дополнительные метаданные
     * @return Количество проиндексированных чанков
     */
    suspend fun indexDocument(
        documentId: String,
        text: String,
        source: String,
        title: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): Int = withContext(Dispatchers.IO) {
        // Шаг 1: Разбивка на чанки
        val chunks = chunker.chunkText(
            text,
            ChunkMetadata(source = source, additionalMetadata = metadata)
        )
        
        if (chunks.isEmpty()) {
            return@withContext 0
        }
        
        println("📄 Разбито на ${chunks.size} чанков")
        
        // Шаг 2: Генерация эмбеддингов
        println("🔄 Генерация эмбеддингов...")
        val indexedChunks = chunks.mapIndexed { index, chunk ->
            print("  Чанк ${index + 1}/${chunks.size}... ")
            val embedding = embeddingClient.generateEmbedding(chunk.text)
            println("✓")
            IndexedChunk(chunk, embedding)
        }
        
        // Шаг 3: Сохранение в индекс
        println("💾 Сохранение в индекс...")
        val document = Document(
            id = documentId,
            source = source,
            title = title
        )
        
        val success = storage.saveDocument(document, indexedChunks)
        if (success) {
            println("✅ Документ успешно проиндексирован: ${indexedChunks.size} чанков")
            indexedChunks.size
        } else {
            throw RuntimeException("Ошибка сохранения документа в индекс")
        }
    }
    
    /**
     * Поиск похожих документов по текстовому запросу.
     * @param queryText Текст запроса
     * @param limit Количество результатов
     * @param minSimilarity Минимальный порог сходства
     * @return Список найденных чанков с оценкой сходства
     */
    suspend fun search(
        queryText: String,
        limit: Int = 10,
        minSimilarity: Double = 0.7
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        // Генерируем эмбеддинг для запроса
        val queryEmbedding = embeddingClient.generateEmbedding(queryText)
        
        // Ищем похожие чанки
        storage.searchSimilar(queryEmbedding, limit, minSimilarity)
    }
    
    /**
     * Поиск похожих документов по эмбеддингу запроса.
     */
    suspend fun searchByEmbedding(
        queryEmbedding: FloatArray,
        limit: Int = 10,
        minSimilarity: Double = 0.7
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        storage.searchSimilar(queryEmbedding, limit, minSimilarity)
    }
}


