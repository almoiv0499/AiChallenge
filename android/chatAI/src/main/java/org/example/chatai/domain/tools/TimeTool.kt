package org.example.chatai.domain.tools

import org.example.chatai.domain.api.OpenRouterPropertyDefinition
import org.example.chatai.domain.api.OpenRouterToolDefinition
import org.example.chatai.domain.api.OpenRouterToolParameters
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Инструмент для получения текущего времени
 */
class TimeTool : AgentTool {
    override val name = "get_current_time"
    override val description = "Получить текущее время"
    
    override fun getDefinition() = OpenRouterToolDefinition(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "timezone" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Часовой пояс (например, 'UTC', 'Europe/Moscow')"
                )
            )
        )
    )
    
    override fun execute(arguments: Map<String, String>): String {
        val formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)
        return "Текущее время: ${LocalDateTime.now().format(formatter)}"
    }
    
    companion object {
        private const val DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
    }
}
