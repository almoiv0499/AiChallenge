package org.example.embedding

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.math.sqrt

/**
 * Хранилище для индекса документов с эмбеддингами в SQLite.
 * Поддерживает сохранение и поиск по векторному сходству (cosine similarity).
 */
class DocumentIndexStorage(private val dbPath: String = "document_index.db") {
    init {
        loadDriver()
        initializeDatabase()
    }
    
    private fun loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("SQLite JDBC driver not found in classpath", e)
        }
    }
    
    private fun getConnection(): Connection {
        val url = "jdbc:sqlite:$dbPath"
        return try {
            DriverManager.getConnection(url)
        } catch (e: Exception) {
            throw RuntimeException("Failed to connect to database: ${e.message}", e)
        }
    }
    
    private fun initializeDatabase() {
        try {
            getConnection().use { connection ->
                // Таблица для документов
                connection.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS documents (
                        id TEXT PRIMARY KEY,
                        source TEXT NOT NULL,
                        title TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Таблица для чанков с эмбеддингами
                connection.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS document_chunks (
                        id TEXT PRIMARY KEY,
                        document_id TEXT NOT NULL,
                        chunk_index INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        embedding BLOB NOT NULL,
                        embedding_dimension INTEGER NOT NULL,
                        metadata TEXT,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Индексы для быстрого поиска
                connection.createStatement().execute("""
                    CREATE INDEX IF NOT EXISTS idx_chunks_document_id 
                    ON document_chunks(document_id)
                """.trimIndent())
                
                connection.createStatement().execute("""
                    CREATE INDEX IF NOT EXISTS idx_chunks_source 
                    ON documents(source)
                """.trimIndent())
            }
        } catch (e: SQLException) {
            throw RuntimeException("Failed to initialize database", e)
        }
    }
    
    /**
     * Сохраняет документ и его чанки с эмбеддингами.
     */
    fun saveDocument(document: Document, chunks: List<IndexedChunk>): Boolean {
        return try {
            getConnection().use { connection ->
                connection.autoCommit = false
                try {
                    val timestamp = System.currentTimeMillis() / 1000
                    
                    // Сохраняем документ
                    val docSql = """
                        INSERT INTO documents (id, source, title, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            source = excluded.source,
                            title = excluded.title,
                            updated_at = excluded.updated_at
                    """.trimIndent()
                    
                    val docStatement = connection.prepareStatement(docSql)
                    docStatement.setString(1, document.id)
                    docStatement.setString(2, document.source)
                    docStatement.setString(3, document.title)
                    docStatement.setLong(4, timestamp)
                    docStatement.setLong(5, timestamp)
                    docStatement.executeUpdate()
                    
                    // Удаляем старые чанки документа
                    val deleteSql = "DELETE FROM document_chunks WHERE document_id = ?"
                    val deleteStatement = connection.prepareStatement(deleteSql)
                    deleteStatement.setString(1, document.id)
                    deleteStatement.executeUpdate()
                    
                    // Сохраняем новые чанки
                    val chunkSql = """
                        INSERT INTO document_chunks 
                        (id, document_id, chunk_index, text, embedding, embedding_dimension, metadata, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                    
                    val chunkStatement = connection.prepareStatement(chunkSql)
                    
                    for (chunk in chunks) {
                        val chunkId = "${document.id}_chunk_${chunk.chunk.index}"
                        val embeddingBytes = serializeEmbedding(chunk.embedding)
                        
                        chunkStatement.setString(1, chunkId)
                        chunkStatement.setString(2, document.id)
                        chunkStatement.setInt(3, chunk.chunk.index)
                        chunkStatement.setString(4, chunk.chunk.text)
                        chunkStatement.setBytes(5, embeddingBytes)
                        chunkStatement.setInt(6, chunk.embedding.size)
                        chunkStatement.setString(7, JsonSerializer.serializeMetadata(chunk.chunk.metadata))
                        chunkStatement.setLong(8, timestamp)
                        chunkStatement.addBatch()
                    }
                    
                    chunkStatement.executeBatch()
                    connection.commit()
                    true
                } catch (e: Exception) {
                    connection.rollback()
                    false
                } finally {
                    connection.autoCommit = true
                }
            }
        } catch (e: SQLException) {
            false
        }
    }
    
    /**
     * Поиск похожих чанков по векторному сходству (cosine similarity).
     * @param queryEmbedding Эмбеддинг запроса
     * @param limit Количество результатов
     * @param minSimilarity Минимальный порог сходства (0.0 - 1.0)
     * @return Список найденных чанков с оценкой сходства
     */
    fun searchSimilar(
        queryEmbedding: FloatArray,
        limit: Int = 10,
        minSimilarity: Double = 0.0
    ): List<SearchResult> {
        return try {
            getConnection().use { connection ->
                val sql = """
                    SELECT 
                        dc.id,
                        dc.document_id,
                        dc.chunk_index,
                        dc.text,
                        dc.embedding,
                        dc.embedding_dimension,
                        dc.metadata,
                        d.source,
                        d.title
                    FROM document_chunks dc
                    JOIN documents d ON dc.document_id = d.id
                """.trimIndent()
                
                val statement = connection.prepareStatement(sql)
                val resultSet = statement.executeQuery()
                
                val results = mutableListOf<SearchResult>()
                
                while (resultSet.next()) {
                    val embeddingBytes = resultSet.getBytes("embedding")
                    val storedEmbedding = deserializeEmbedding(embeddingBytes)
                    val similarity = cosineSimilarity(queryEmbedding, storedEmbedding)
                    
                    if (similarity >= minSimilarity) {
                        results.add(
                            SearchResult(
                                chunkId = resultSet.getString("id"),
                                documentId = resultSet.getString("document_id"),
                                chunkIndex = resultSet.getInt("chunk_index"),
                                text = resultSet.getString("text"),
                                source = resultSet.getString("source"),
                                title = resultSet.getString("title"),
                                similarity = similarity,
                                metadata = JsonSerializer.deserializeMetadata(
                                    resultSet.getString("metadata") ?: "{}"
                                )
                            )
                        )
                    }
                }
                
                // Сортируем по сходству и берем топ-N
                results.sortedByDescending { it.similarity }.take(limit)
            }
        } catch (e: SQLException) {
            emptyList()
        }
    }
    
    /**
     * Получает все чанки документа.
     */
    fun getDocumentChunks(documentId: String): List<IndexedChunk> {
        return try {
            getConnection().use { connection ->
                val sql = """
                    SELECT 
                        dc.chunk_index,
                        dc.text,
                        dc.embedding,
                        dc.metadata
                    FROM document_chunks dc
                    WHERE dc.document_id = ?
                    ORDER BY dc.chunk_index
                """.trimIndent()
                
                val statement = connection.prepareStatement(sql)
                statement.setString(1, documentId)
                val resultSet = statement.executeQuery()
                
                val chunks = mutableListOf<IndexedChunk>()
                
                while (resultSet.next()) {
                    val embeddingBytes = resultSet.getBytes("embedding")
                    val embedding = deserializeEmbedding(embeddingBytes)
                    val chunk = TextChunk(
                        text = resultSet.getString("text"),
                        index = resultSet.getInt("chunk_index"),
                        source = documentId,
                        metadata = JsonSerializer.deserializeMetadata(
                            resultSet.getString("metadata") ?: "{}"
                        )
                    )
                    chunks.add(IndexedChunk(chunk, embedding))
                }
                
                chunks
            }
        } catch (e: SQLException) {
            emptyList()
        }
    }
    
    /**
     * Удаляет документ и все его чанки.
     */
    fun deleteDocument(documentId: String): Boolean {
        return try {
            getConnection().use { connection ->
                val sql = "DELETE FROM documents WHERE id = ?"
                val statement = connection.prepareStatement(sql)
                statement.setString(1, documentId)
                statement.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            false
        }
    }
    
    /**
     * Получает список всех документов.
     */
    fun getAllDocuments(): List<Document> {
        return try {
            getConnection().use { connection ->
                val sql = "SELECT id, source, title FROM documents"
                val statement = connection.prepareStatement(sql)
                val resultSet = statement.executeQuery()
                
                val documents = mutableListOf<Document>()
                while (resultSet.next()) {
                    documents.add(
                        Document(
                            id = resultSet.getString("id"),
                            source = resultSet.getString("source"),
                            title = resultSet.getString("title")
                        )
                    )
                }
                documents
            }
        } catch (e: SQLException) {
            emptyList()
        }
    }
    
    // Вспомогательные методы для работы с эмбеддингами
    
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
}

/**
 * Представляет документ в индексе.
 */
data class Document(
    val id: String,
    val source: String,
    val title: String? = null
)

/**
 * Чанк с эмбеддингом.
 */
data class IndexedChunk(
    val chunk: TextChunk,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as IndexedChunk
        
        if (chunk != other.chunk) return false
        if (!embedding.contentEquals(other.embedding)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = chunk.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

/**
 * Результат поиска.
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

/**
 * Утилита для сериализации метаданных в JSON.
 */
private object JsonSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    fun serializeMetadata(metadata: Map<String, String>): String {
        if (metadata.isEmpty()) return "{}"
        val jsonObject = buildJsonObject {
            metadata.forEach { (key, value) ->
                put(key, value)
            }
        }
        return jsonObject.toString()
    }
    
    fun deserializeMetadata(jsonString: String): Map<String, String> {
        if (jsonString.isBlank() || jsonString == "{}") return emptyMap()
        return try {
            val jsonObject = json.parseToJsonElement(jsonString) as JsonObject
            jsonObject.entries.associate { (key, value) ->
                key to value.toString().trim('"')
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

