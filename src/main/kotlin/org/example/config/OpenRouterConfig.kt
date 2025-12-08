package org.example.config

object OpenRouterConfig {
    const val API_URL = "https://openrouter.ai/api/v1/responses"
    const val DEFAULT_MODEL = "openai/gpt-4o-mini"
    const val MAX_AGENT_ITERATIONS = 5
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
}

