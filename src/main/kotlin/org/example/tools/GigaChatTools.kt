package org.example.tools

import org.example.models.GigaChatFunction
import org.example.models.GigaChatFunctionParameters
import org.example.models.GigaChatPropertyDefinition
import org.example.ui.ConsoleUI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.pow
import kotlin.math.sqrt

interface GigaChatTool {
    val name: String
    val description: String
    fun getDefinition(): GigaChatFunction
    fun execute(arguments: Map<String, String>): String
}

class GigaChatTimeTool : GigaChatTool {
    override val name = "get_current_time"
    override val description = "Получить текущую дату и время"

    override fun getDefinition(): GigaChatFunction = GigaChatFunction(
        name = name,
        description = description,
        parameters = GigaChatFunctionParameters(
            properties = mapOf(
                "timezone" to GigaChatPropertyDefinition(
                    type = "string",
                    description = "Часовой пояс (например, 'UTC', 'Europe/Moscow'). По умолчанию системный."
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

class GigaChatCalculatorTool : GigaChatTool {
    override val name = "calculator"
    override val description = "Выполняет математические вычисления: сложение, вычитание, умножение, деление, возведение в степень, квадратный корень"

    override fun getDefinition() = GigaChatFunction(
        name = name,
        description = description,
        parameters = GigaChatFunctionParameters(
            properties = mapOf(
                "operation" to GigaChatPropertyDefinition(
                    type = "string",
                    description = "Операция: add, subtract, multiply, divide, power, sqrt",
                    enum = Operation.entries.map { it.value }
                ),
                "a" to GigaChatPropertyDefinition(type = "number", description = "Первое число"),
                "b" to GigaChatPropertyDefinition(type = "number", description = "Второе число (не требуется для sqrt)")
            ),
            required = listOf("operation", "a")
        )
    )

    override fun execute(arguments: Map<String, String>): String {
        val operationStr = arguments["operation"] ?: return "Ошибка: не указана операция"
        val a = arguments["a"]?.toDoubleOrNull() ?: return "Ошибка: некорректное значение a"
        val b = arguments["b"]?.toDoubleOrNull()
        val operation = Operation.fromString(operationStr)
            ?: return "Ошибка: неизвестная операция $operationStr"
        return runCatching { "Результат: ${operation.calculate(a, b)}" }
            .getOrElse { "Ошибка: ${it.message}" }
    }

    private enum class Operation(val value: String) {
        ADD("add"),
        SUBTRACT("subtract"),
        MULTIPLY("multiply"),
        DIVIDE("divide"),
        POWER("power"),
        SQRT("sqrt");

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

class GigaChatSearchTool : GigaChatTool {
    override val name = "search"
    override val description = "Поиск информации по запросу"

    override fun getDefinition() = GigaChatFunction(
        name = name,
        description = description,
        parameters = GigaChatFunctionParameters(
            properties = mapOf(
                "query" to GigaChatPropertyDefinition(type = "string", description = "Поисковый запрос")
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

class GigaChatRandomNumberTool : GigaChatTool {
    override val name = "random_number"
    override val description = "Генерирует случайное число в заданном диапазоне"

    override fun getDefinition() = GigaChatFunction(
        name = name,
        description = description,
        parameters = GigaChatFunctionParameters(
            properties = mapOf(
                "min" to GigaChatPropertyDefinition(
                    type = "integer",
                    description = "Минимальное значение (по умолчанию $DEFAULT_MIN)"
                ),
                "max" to GigaChatPropertyDefinition(
                    type = "integer",
                    description = "Максимальное значение (по умолчанию $DEFAULT_MAX)"
                )
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

class GigaChatToolRegistry {
    private val tools = mutableMapOf<String, GigaChatTool>()

    fun register(tool: GigaChatTool) {
        tools[tool.name] = tool
        ConsoleUI.printToolRegistered(tool.name)
    }

    fun getTool(name: String): GigaChatTool? = tools[name]

    fun getAllTools(): List<GigaChatTool> = tools.values.toList()

    fun getToolDefinitions(): List<GigaChatFunction> = tools.values.map { it.getDefinition() }

    companion object {
        fun createDefault() = GigaChatToolRegistry().apply {
            register(GigaChatTimeTool())
            register(GigaChatCalculatorTool())
            register(GigaChatSearchTool())
            register(GigaChatRandomNumberTool())
        }
    }
}
