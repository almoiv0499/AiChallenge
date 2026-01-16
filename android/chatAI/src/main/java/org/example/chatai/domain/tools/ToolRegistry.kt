package org.example.chatai.domain.tools

import org.example.chatai.domain.api.OpenRouterToolDefinition

/**
 * Реестр инструментов AI агента.
 * Управляет регистрацией и поиском инструментов.
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, AgentTool>()
    
    /**
     * Зарегистрировать инструмент
     */
    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }
    
    /**
     * Получить инструмент по имени
     */
    fun getTool(name: String): AgentTool? = tools[name]
    
    /**
     * Получить все инструменты
     */
    fun getAllTools(): List<AgentTool> = tools.values.toList()
    
    /**
     * Получить определения всех инструментов для API
     */
    fun getToolDefinitions(): List<OpenRouterToolDefinition> {
        return tools.values.map { it.getDefinition() }
    }
    
    companion object {
        /**
         * Создать реестр с базовыми инструментами
         */
        fun createDefault(): ToolRegistry {
            return ToolRegistry().apply {
                register(TimeTool())
                register(CalculatorTool())
                register(SearchTool())
                register(RandomNumberTool())
            }
        }
    }
}
