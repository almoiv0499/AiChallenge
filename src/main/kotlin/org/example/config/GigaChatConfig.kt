package org.example.config

object GigaChatConfig {
    const val AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    const val API_BASE_URL = "https://gigachat.devices.sberbank.ru/api/v1"
    const val CHAT_COMPLETIONS_ENDPOINT = "/chat/completions"
    const val DEFAULT_MODEL = "GigaChat"
    const val DEFAULT_SCOPE = "GIGACHAT_API_PERS"
    const val TOKEN_REFRESH_MARGIN_MS = 60_000L
    const val MAX_AGENT_ITERATIONS = 5
    object Headers {
        const val RQ_UID = "RqUID"
        const val AUTHORIZATION = "Authorization"
    }
    object Roles {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"
        const val FUNCTION = "function"
    }
    object FinishReasons {
        const val FUNCTION_CALL = "function_call"
        const val STOP = "stop"
    }
}

