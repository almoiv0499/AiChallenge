package org.example.chatai.domain.tools

import org.example.chatai.domain.api.OpenRouterPropertyDefinition
import org.example.chatai.domain.api.OpenRouterToolDefinition
import org.example.chatai.domain.api.OpenRouterToolParameters
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Инструмент для математических вычислений
 */
class CalculatorTool : AgentTool {
    override val name = "calculator"
    override val description = "Математические вычисления"
    
    override fun getDefinition() = OpenRouterToolDefinition(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "operation" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Операция: add, subtract, multiply, divide, power, sqrt",
                    enum = Operation.entries.map { it.value }
                ),
                "a" to OpenRouterPropertyDefinition(
                    type = "number",
                    description = "Первое число"
                ),
                "b" to OpenRouterPropertyDefinition(
                    type = "number",
                    description = "Второе число (не требуется для sqrt)"
                )
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
        
        return try {
            "Результат: ${operation.calculate(a, b)}"
        } catch (e: IllegalArgumentException) {
            "Ошибка: ${e.message}"
        }
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
            DIVIDE -> requireB(b) {
                require(it != 0.0) { "Деление на ноль" }
                a / it
            }
            POWER -> requireB(b) { a.pow(it) }
            SQRT -> {
                require(a >= 0) { "Корень из отрицательного числа" }
                sqrt(a)
            }
        }
        
        private fun requireB(b: Double?, block: (Double) -> Double): Double {
            requireNotNull(b) { "Требуется второе число" }
            return block(b)
        }
        
        companion object {
            fun fromString(value: String): Operation? = entries.find { it.value == value }
        }
    }
}
