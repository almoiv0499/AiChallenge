package org.example.chatai.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность чанка документа с эмбеддингом в RAG индексе.
 */
@Entity(
    tableName = "rag_document_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["chunkIndex"])
    ]
)
data class DocumentChunkEntity(
    @PrimaryKey
    val id: String,
    val documentId: String,
    val chunkIndex: Int,
    val text: String,
    val embedding: ByteArray,
    val embeddingDimension: Int,
    val metadata: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as DocumentChunkEntity
        
        if (id != other.id) return false
        if (documentId != other.documentId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (embeddingDimension != other.embeddingDimension) return false
        if (metadata != other.metadata) return false
        if (createdAt != other.createdAt) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + embeddingDimension
        result = 31 * result + (metadata?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
