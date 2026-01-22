package org.example.config

/**
 * Конфигурация параметров локальной LLM (Ollama) для оптимизации под конкретную задачу.
 * Позволяет настраивать температуру, окно контекста, max tokens и промпт-шаблон.
 *
 * Переменные окружения:
 * - OLLAMA_TEMPERATURE  — температура (0.0–2.0). Ниже = точнее, выше = креативнее.
 * - OLLAMA_MAX_TOKENS   — макс. токенов ответа.
 * - OLLAMA_NUM_CTX      — размер контекстного окна (токены).
 * - OLLAMA_TOP_P        — nucleus sampling (опционально).
 * - OLLAMA_REPEAT_PENALTY — штраф за повторы (опционально).
 * - OLLAMA_SYSTEM_PROMPT — системный промпт. Пусто = использовать шаблон по умолчанию.
 * - OLLAMA_PROMPT_PRESET — "default" | "optimized" для выбора шаблона.
 */
object OllamaLlmConfig {
    const val TEMPERATURE_ENV = "OLLAMA_TEMPERATURE"
    const val MAX_TOKENS_ENV = "OLLAMA_MAX_TOKENS"
    const val NUM_CTX_ENV = "OLLAMA_NUM_CTX"
    const val TOP_P_ENV = "OLLAMA_TOP_P"
    const val REPEAT_PENALTY_ENV = "OLLAMA_REPEAT_PENALTY"
    const val SYSTEM_PROMPT_ENV = "OLLAMA_SYSTEM_PROMPT"
    const val PROMPT_PRESET_ENV = "OLLAMA_PROMPT_PRESET"

    /** Температура по умолчанию. 0.3–0.5 хорошо для точных ответов. */
    const val DEFAULT_TEMPERATURE = 0.1

    /** Макс. токенов ответа. */
    const val DEFAULT_MAX_TOKENS = 2048

    /** Размер контекстного окна (зависит от модели, например 8192 для llama3.2). */
    const val DEFAULT_NUM_CTX = 8192

    const val DEFAULT_TOP_P = 0.9
    const val DEFAULT_REPEAT_PENALTY = 1.1

    fun loadTemperature(): Double =
        System.getenv(TEMPERATURE_ENV)?.toDoubleOrNull() ?: DEFAULT_TEMPERATURE

    fun loadMaxTokens(): Int? =
        System.getenv(MAX_TOKENS_ENV)?.toIntOrNull()

    fun loadNumCtx(): Int? =
        System.getenv(NUM_CTX_ENV)?.toIntOrNull()

    fun loadTopP(): Double? =
        System.getenv(TOP_P_ENV)?.toDoubleOrNull()

    fun loadRepeatPenalty(): Double? =
        System.getenv(REPEAT_PENALTY_ENV)?.toDoubleOrNull()

    fun loadSystemPrompt(): String? {
        val explicit = System.getenv(SYSTEM_PROMPT_ENV)
        if (!explicit.isNullOrBlank()) return explicit.trim()
        return when (System.getenv(PROMPT_PRESET_ENV)?.lowercase()) {
            "optimized" -> OPTIMIZED_CHAT_PROMPT
            else -> DEFAULT_CHAT_PROMPT
        }
    }

    /**
     * Промпт по умолчанию — универсальный помощник.
     */
    val DEFAULT_CHAT_PROMPT = """
        Ты полезный AI-ассистент. Отвечай на вопросы пользователя кратко и по делу.
        Используй дружелюбный и профессиональный тон.
        Поддерживай русский и английский языки.
    """.trimIndent()

    /**
     * Оптимизированный промпт под задачу чат-ассистента:
     * точные, структурированные ответы, минимум «воды», явные ограничения.
     */
    val OPTIMIZED_CHAT_PROMPT = """
        Ты — точный и лаконичный AI-ассистент.

        Правила:
        - Отвечай по существу: сначала главное, затем детали при необходимости.
        - Избегай длинных вступлений, повторений и общих фраз.
        - Поддерживай русский и английский. Язык ответа — как у пользователя.
        - Если не знаешь — скажи честно и предложи, где искать.
        - Для фактов и кода давай конкретные формулировки или примеры.

        Формат: короткие абзацы, списки — где уместно. Без эмодзи, если пользователь не просит.
    """.trimIndent()

    fun load(): LoadedOllamaLlmConfig = LoadedOllamaLlmConfig(
        temperature = loadTemperature(),
        maxTokens = loadMaxTokens(),
        numCtx = loadNumCtx(),
        topP = loadTopP(),
        repeatPenalty = loadRepeatPenalty(),
        systemPrompt = loadSystemPrompt() ?: DEFAULT_CHAT_PROMPT
    )
}
