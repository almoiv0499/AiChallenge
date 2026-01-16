package org.example.chatai.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.example.chatai.data.model.DocumentChunkEntity
import org.example.chatai.data.model.DocumentEntity

/**
 * DAO для работы с документами RAG индекса.
 */
@Dao
interface DocumentDao {
    
    @Query("SELECT * FROM rag_documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM rag_documents")
    suspend fun getAllDocumentsSync(): List<DocumentEntity>
    
    @Query("SELECT COUNT(*) FROM rag_documents")
    suspend fun getDocumentCount(): Int
    
    @Query("SELECT * FROM rag_documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: String): DocumentEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)
    
    @Delete
    suspend fun deleteDocument(document: DocumentEntity)
    
    @Query("DELETE FROM rag_documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: String)
    
    @Query("DELETE FROM rag_documents")
    suspend fun deleteAllDocuments()
}
