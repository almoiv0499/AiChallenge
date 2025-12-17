package org.example.config

import java.io.File
import java.util.Properties

object AppConfig {
    const val API_KEY_ENV = "OPENROUTER_API_KEY"
    const val NOTION_API_KEY_ENV = "NOTION_API_KEY"
    const val WEATHER_API_KEY_ENV = "WEATHER_API_KEY"
    const val NOTION_DATABASE_ID_ENV = "NOTION_DATABASE_ID"
    const val LOCAL_PROPERTIES_FILE = "local.properties"
    fun loadApiKey(): String {
        return loadFromEnvironment()
            ?: loadFromPropertiesFile()
            ?: throwApiKeyNotFoundError()
    }
    fun loadNotionApiKey(): String {
        return loadNotionFromEnvironment()
            ?: loadNotionFromPropertiesFile()
            ?: "empty"
    }
    fun loadWeatherApiKey(): String {
        return loadWeatherFromEnvironment()
            ?: loadWeatherFromPropertiesFile()
            ?: "empty"
    }
    fun loadNotionDatabaseId(): String? {
        return loadDatabaseIdFromEnvironment()
            ?: loadDatabaseIdFromPropertiesFile()
    }
    private fun loadFromEnvironment(): String? = System.getenv(API_KEY_ENV)
    private fun loadFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(API_KEY_ENV)
    }
    private fun loadNotionFromEnvironment(): String? = System.getenv(NOTION_API_KEY_ENV)
    private fun loadNotionFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(NOTION_API_KEY_ENV)
    }
    private fun loadWeatherFromEnvironment(): String? = System.getenv(WEATHER_API_KEY_ENV)
    private fun loadWeatherFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(WEATHER_API_KEY_ENV)
    }
    private fun loadDatabaseIdFromEnvironment(): String? = System.getenv(NOTION_DATABASE_ID_ENV)
    private fun loadDatabaseIdFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(NOTION_DATABASE_ID_ENV)
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
