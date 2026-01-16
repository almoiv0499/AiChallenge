package org.example.chatai.domain.tools

import org.example.chatai.domain.api.OpenRouterPropertyDefinition
import org.example.chatai.domain.api.OpenRouterToolDefinition
import org.example.chatai.domain.api.OpenRouterToolParameters

/**
 * Инструмент для генерации случайного числа
 */
class RandomNumberTool : AgentTool {
    override val name = "random_number"
    override val description = "Случайное число"
    
    override fun getDefinition() = OpenRouterToolDefinition(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "min" to OpenRouterPropertyDefinition(
                    type = "integer",
                    description = "Минимальное значение (по умолчанию 1)"
                ),
                "max" to OpenRouterPropertyDefinition(
                    type = "integer",
                    description = "Максимальное значение (по умолчанию 100)"
                )
            )
        )
    )
    
    override fun execute(arguments: Map<String, String>): String {
        val min = arguments["min"]?.toIntOrNull() ?: DEFAULT_MIN
        val max = arguments["max"]?.toIntOrNull() ?: DEFAULT_MAX
        
        if (min > max) {
            return "Ошибка: минимальное значение больше максимального"
        }
        
        return "Случайное число от $min до $max: ${(min..max).random()}"
    }
    
    companion object {
        private const val DEFAULT_MIN = 1
        private const val DEFAULT_MAX = 100
    }
}
