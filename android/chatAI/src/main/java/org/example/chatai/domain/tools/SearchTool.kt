package org.example.chatai.domain.tools

import org.example.chatai.domain.api.OpenRouterPropertyDefinition
import org.example.chatai.domain.api.OpenRouterToolDefinition
import org.example.chatai.domain.api.OpenRouterToolParameters

/**
 * Инструмент для поиска информации (эмуляция)
 */
class SearchTool : AgentTool {
    override val name = "search"
    override val description = "Поиск информации"
    
    override fun getDefinition() = OpenRouterToolDefinition(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "query" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Поисковый запрос"
                )
            ),
            required = listOf("query")
        )
    )
    
    override fun execute(arguments: Map<String, String>): String {
        val query = arguments["query"] ?: return "Ошибка: не указан поисковый запрос"
        return """
            Результаты поиска по запросу "$query":
            1. Найдена статья: "$query - основные сведения"
            2. Найдена документация: "Руководство по $query"
            3. Найдено обсуждение: "FAQ по теме $query"
        """.trimIndent()
    }
}
