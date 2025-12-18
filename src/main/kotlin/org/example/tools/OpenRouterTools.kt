package org.example.tools

import org.example.models.OpenRouterTool
import org.example.models.OpenRouterToolParameters
import org.example.models.OpenRouterPropertyDefinition
import org.example.ui.ConsoleUI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.pow
import kotlin.math.sqrt

interface AgentTool {
    val name: String
    val description: String
    fun getDefinition(): OpenRouterTool
    fun execute(arguments: Map<String, String>): String
}

class TimeTool : AgentTool {
    override val name = "get_current_time"
    override val description = "Получить текущее время"
    override fun getDefinition() = OpenRouterTool(
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

class CalculatorTool : AgentTool {
    override val name = "calculator"
    override val description = "Математические вычисления"
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "operation" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Операция: add, subtract, multiply, divide, power, sqrt",
                    enum = Operation.entries.map { it.value }
                ),
                "a" to OpenRouterPropertyDefinition(type = "number", description = "Первое число"),
                "b" to OpenRouterPropertyDefinition(type = "number", description = "Второе число (не требуется для sqrt)")
            ),
            required = listOf("operation", "a")
        )
    )
    override fun execute(arguments: Map<String, String>): String {
        val operationStr = arguments["operation"] ?: return "Ошибка: не указана операция"
        val a = arguments["a"]?.toDoubleOrNull() ?: return "Ошибка: некорректное значение a"
        val b = arguments["b"]?.toDoubleOrNull()
        val operation = Operation.fromString(operationStr) ?: return "Ошибка: неизвестная операция $operationStr"
        return try {
            "Результат: ${operation.calculate(a, b)}"
        } catch (e: IllegalArgumentException) {
            "Ошибка: ${e.message}"
        }
    }
    private enum class Operation(val value: String) {
        ADD("add"), SUBTRACT("subtract"), MULTIPLY("multiply"),
        DIVIDE("divide"), POWER("power"), SQRT("sqrt");
        fun calculate(a: Double, b: Double?): Double = when (this) {
            ADD -> requireB(b) { a + it }
            SUBTRACT -> requireB(b) { a - it }
            MULTIPLY -> requireB(b) { a * it }
            DIVIDE -> requireB(b) { require(it != 0.0) { "Деление на ноль" }; a / it }
            POWER -> requireB(b) { a.pow(it) }
            SQRT -> { require(a >= 0) { "Корень из отрицательного числа" }; sqrt(a) }
        }
        private fun requireB(b: Double?, block: (Double) -> Double): Double {
            requireNotNull(b) { "Требуется второе число" }
            return block(b)
        }
        companion object {
            fun fromString(value: String) = entries.find { it.value == value }
        }
    }
}

class SearchTool : AgentTool {
    override val name = "search"
    override val description = "Поиск информации"
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "query" to OpenRouterPropertyDefinition(type = "string", description = "Поисковый запрос")
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

class RandomNumberTool : AgentTool {
    override val name = "random_number"
    override val description = "Случайное число"
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "min" to OpenRouterPropertyDefinition(type = "integer", description = "Минимальное значение (по умолчанию 1)"),
                "max" to OpenRouterPropertyDefinition(type = "integer", description = "Максимальное значение (по умолчанию 100)")
            )
        )
    )
    override fun execute(arguments: Map<String, String>): String {
        val min = arguments["min"]?.toIntOrNull() ?: DEFAULT_MIN
        val max = arguments["max"]?.toIntOrNull() ?: DEFAULT_MAX
        if (min > max) return "Ошибка: минимальное значение больше максимального"
        return "Случайное число от $min до $max: ${(min..max).random()}"
    }
    companion object {
        private const val DEFAULT_MIN = 1
        private const val DEFAULT_MAX = 100
    }
}

class ToolRegistry {
    private val tools = mutableMapOf<String, AgentTool>()
    fun register(tool: AgentTool) {
        tools[tool.name] = tool
        ConsoleUI.printToolRegistered(tool.name)
    }
    fun getTool(name: String): AgentTool? = tools[name]
    fun getAllTools(): List<AgentTool> = tools.values.toList()
    fun getToolDefinitions(): List<OpenRouterTool> = tools.values.map { it.getDefinition() }
    companion object {
        fun createDefault() = ToolRegistry().apply {
            register(TimeTool())
            register(CalculatorTool())
            register(SearchTool())
            register(RandomNumberTool())
        }
    }
}

