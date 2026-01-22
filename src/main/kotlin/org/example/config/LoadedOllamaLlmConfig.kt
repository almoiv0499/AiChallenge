package org.example.config

import org.example.client.ollama.OllamaOptions

/**
 * Загруженная конфигурация LLM (со всеми значениями по умолчанию).
 */
data class LoadedOllamaLlmConfig(
    val temperature: Double = OllamaLlmConfig.DEFAULT_TEMPERATURE,
    val maxTokens: Int? = OllamaLlmConfig.DEFAULT_MAX_TOKENS,
    val topP: Double? = OllamaLlmConfig.DEFAULT_TOP_P,
    val repeatPenalty: Double? = OllamaLlmConfig.DEFAULT_REPEAT_PENALTY,
    val numCtx: Int? = OllamaLlmConfig.DEFAULT_NUM_CTX,
    val systemPrompt: String = OllamaLlmConfig.DEFAULT_CHAT_PROMPT
) {
    /** Собирает OllamaOptions для передачи в API. */
    fun toOllamaOptions(): OllamaOptions = OllamaOptions(
        temperature = temperature,
        numPredict = maxTokens ?: OllamaLlmConfig.DEFAULT_MAX_TOKENS,
        numCtx = numCtx ?: OllamaLlmConfig.DEFAULT_NUM_CTX,
        topP = topP,
        repeatPenalty = repeatPenalty
    )
}
