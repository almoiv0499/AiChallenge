package org.example.chatai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сущность документа в RAG индексе.
 */
@Entity(tableName = "rag_documents")
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val source: String,
    val title: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
