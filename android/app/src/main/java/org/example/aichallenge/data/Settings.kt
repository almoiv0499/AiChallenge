package org.example.aichallenge.data

/**
 * Модель настроек приложения.
 * Хранит состояния включения/выключения MCP серверов и RAG.
 */
data class Settings(
    val notionMcpEnabled: Boolean = false,
    val weatherMcpEnabled: Boolean = false,
    val gitMcpEnabled: Boolean = false,
    val teamMcpEnabled: Boolean = false,
    val ragEnabled: Boolean = false,
    val ragIndexedCount: Int = 0
)
