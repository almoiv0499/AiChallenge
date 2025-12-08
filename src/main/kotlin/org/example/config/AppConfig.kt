package org.example.config

import java.io.File
import java.util.Properties

object AppConfig {
    const val API_KEY_ENV = "OPENROUTER_API_KEY"
    const val LOCAL_PROPERTIES_FILE = "local.properties"
    fun loadApiKey(): String {
        return loadFromEnvironment()
            ?: loadFromPropertiesFile()
            ?: throwApiKeyNotFoundError()
    }
    private fun loadFromEnvironment(): String? = System.getenv(API_KEY_ENV)
    private fun loadFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(API_KEY_ENV)
    }
    private fun throwApiKeyNotFoundError(): Nothing {
        error("""
            ❌ API ключ $API_KEY_ENV не найден!
            
            Варианты настройки:
            1. Создайте файл $LOCAL_PROPERTIES_FILE в корне проекта:
               $API_KEY_ENV=ваш_ключ
               
            2. Или установите переменную окружения:
               Windows: set $API_KEY_ENV=ваш_ключ
               Linux/Mac: export $API_KEY_ENV=ваш_ключ
        """.trimIndent())
    }
}
