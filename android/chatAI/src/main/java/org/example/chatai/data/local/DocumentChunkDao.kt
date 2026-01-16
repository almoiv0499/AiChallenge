package org.example.chatai.data.local

import androidx.room.*
import org.example.chatai.data.model.DocumentChunkEntity

/**
 * DAO для работы с чанками документов RAG индекса.
 */
@Dao
interface DocumentChunkDao {
    
    @Query("SELECT * FROM rag_document_chunks WHERE documentId = :documentId ORDER BY chunkIndex")
    suspend fun getChunksByDocumentId(documentId: String): List<DocumentChunkEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: DocumentChunkEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)
    
    @Query("DELETE FROM rag_document_chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocumentId(documentId: String)
    
    @Query("DELETE FROM rag_document_chunks")
    suspend fun deleteAllChunks()
    
    @Query("SELECT * FROM rag_document_chunks")
    suspend fun getAllChunks(): List<DocumentChunkEntity>
}

