package org.example.config

object OpenRouterConfig {
    const val API_URL = "https://openrouter.ai/api/v1/responses"
    const val DEFAULT_MODEL = Models.OPEN_AI
    const val MAX_AGENT_ITERATIONS = 3
    const val MAX_TOKENS = 5000.0
    var ENABLE_TOOLS = true
    var ENABLE_TASK_REMINDER = false // По умолчанию отключено
    const val HISTORY_COMPRESSION_THRESHOLD = 3
    const val HISTORY_COMPRESSION_KEEP_LAST = 1
    val MODELS_WITHOUT_TOOLS = setOf(
        "deepseek/deepseek-v3.2"
    )
    fun supportsTools(model: String): Boolean = model !in MODELS_WITHOUT_TOOLS
    object Headers {
        const val AUTHORIZATION = "Authorization"
        const val CONTENT_TYPE = "Content-Type"
    }
    object Roles {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"
        const val DEVELOPER = "developer"
    }
    object MessageType {
        const val MESSAGE = "message"
        const val FUNCTION_CALL = "function_call"
        const val FUNCTION_CALL_OUTPUT = "function_call_output"
    }
    object Temperature {
        const val LOW = 0.0
        const val DEFAULT = 0.7
        const val HIGH = 1.2
        const val VERY_HIGH = 2.0
    }
    object Status {
        const val COMPLETED = "completed"
        const val INCOMPLETE = "incomplete"
        const val IN_PROGRESS = "in_progress"
    }
    object Models {
        const val OPEN_AI = "openai/gpt-4o-mini-2024-07-18"
        const val DEEPSEEK = "deepseek/deepseek-v3.2"
        const val GLM = "z-ai/glm-4.6v"
    }
}

