package org.example.storage

import java.time.Instant

data class ConversationSummary(
    val id: Long? = null,
    val summary: String,
    val createdAt: Long = Instant.now().epochSecond,
    val messageCount: Int = 0
)

