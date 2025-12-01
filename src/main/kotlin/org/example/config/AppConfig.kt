package org.example.config

import java.io.File
import java.util.Properties

object AppConfig {
    const val AUTH_KEY_ENV = "GIGACHAT_AUTH_KEY"
    const val LOCAL_PROPERTIES_FILE = "local.properties"
    fun loadAuthorizationKey(): String {
        return loadFromEnvironment()
            ?: loadFromPropertiesFile()
            ?: throwAuthKeyNotFoundError()
    }
    private fun loadFromEnvironment(): String? = System.getenv(AUTH_KEY_ENV)
    private fun loadFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(AUTH_KEY_ENV)
    }
    private fun throwAuthKeyNotFoundError(): Nothing {
        error("""
            ❌ Ключ авторизации $AUTH_KEY_ENV не найден!
            
            Варианты настройки:
            1. Создайте файл $LOCAL_PROPERTIES_FILE в корне проекта:
               $AUTH_KEY_ENV=ваш_ключ
               
            2. Или установите переменную окружения:
               Windows: set $AUTH_KEY_ENV=ваш_ключ
               Linux/Mac: export $AUTH_KEY_ENV=ваш_ключ
        """.trimIndent())
    }
}

