package org.example.chatai.domain.tools

import org.example.chatai.domain.api.OpenRouterToolDefinition

/**
 * Интерфейс для инструментов AI агента.
 * Каждый инструмент может быть вызван AI для выполнения действий.
 */
interface AgentTool {
    val name: String
    val description: String
    
    /**
     * Получить определение инструмента для API
     */
    fun getDefinition(): OpenRouterToolDefinition
    
    /**
     * Выполнить инструмент с переданными аргументами
     * 
     * @param arguments Аргументы инструмента
     * @return Результат выполнения
     */
    fun execute(arguments: Map<String, String>): String
}
