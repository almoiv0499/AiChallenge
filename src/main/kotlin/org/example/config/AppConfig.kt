package org.example.config

import java.io.File
import java.util.Properties

object AppConfig {
    const val API_KEY_ENV = "OPENROUTER_API_KEY"
    const val NOTION_API_KEY_ENV = "NOTION_API_KEY"
    const val WEATHER_API_KEY_ENV = "WEATHER_API_KEY"
    const val NOTION_DATABASE_ID_ENV = "NOTION_DATABASE_ID"
    const val NOTION_PAGE_ID_ENV = "NOTION_PAGE_ID"
    const val WEATHER_LATITUDE_ENV = "WEATHER_LATITUDE"
    const val WEATHER_LONGITUDE_ENV = "WEATHER_LONGITUDE"
    const val MCP_TRANSPORT_ENV = "MCP_TRANSPORT"
    const val ANDROID_SDK_PATH_ENV = "ANDROID_SDK_PATH"
    const val RERANKER_THRESHOLD_ENV = "RERANKER_THRESHOLD"
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
    fun loadNotionPageId(): String? {
        return loadPageIdFromEnvironment()
            ?: loadPageIdFromPropertiesFile()
    }
    fun loadWeatherLatitude(): Double? {
        val value = loadLatitudeFromEnvironment() ?: loadLatitudeFromPropertiesFile()
        return value?.toDoubleOrNull()
    }
    fun loadWeatherLongitude(): Double? {
        val value = loadLongitudeFromEnvironment() ?: loadLongitudeFromPropertiesFile()
        return value?.toDoubleOrNull()
    }
    
    fun loadMcpTransport(): org.example.mcp.transport.TransportType {
        val value = loadTransportFromEnvironment() ?: loadTransportFromPropertiesFile()
        return when (value?.uppercase()) {
            "STDIO" -> org.example.mcp.transport.TransportType.STDIO
            "HTTP" -> org.example.mcp.transport.TransportType.HTTP
            else -> org.example.mcp.transport.TransportType.HTTP // Default to HTTP
        }
    }
    
    private fun loadTransportFromEnvironment(): String? = System.getenv(MCP_TRANSPORT_ENV)
    private fun loadTransportFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(MCP_TRANSPORT_ENV)
    }
    
    fun loadAndroidSdkPath(): String? {
        return loadAndroidSdkPathFromEnvironment() ?: loadAndroidSdkPathFromPropertiesFile()
    }
    
    fun loadRerankerThreshold(): Double? {
        val value = loadRerankerThresholdFromEnvironment() ?: loadRerankerThresholdFromPropertiesFile()
        return value?.toDoubleOrNull()?.coerceIn(0.0, 1.0)
    }
    
    private fun loadRerankerThresholdFromEnvironment(): String? = System.getenv(RERANKER_THRESHOLD_ENV)
    private fun loadRerankerThresholdFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(RERANKER_THRESHOLD_ENV)
    }
    
    fun loadAdbPath(androidSdkPath: String): String {
        val platformTools = File(androidSdkPath, "platform-tools")
        val adbFile = File(platformTools, if (System.getProperty("os.name").lowercase().contains("win")) "adb.exe" else "adb")
        return adbFile.absolutePath
    }
    
    fun loadEmulatorPath(androidSdkPath: String): String {
        val emulatorDir = File(androidSdkPath, "emulator")
        val emulatorFile = File(emulatorDir, if (System.getProperty("os.name").lowercase().contains("win")) "emulator.exe" else "emulator")
        return emulatorFile.absolutePath
    }
    
    private fun loadAndroidSdkPathFromEnvironment(): String? = System.getenv(ANDROID_SDK_PATH_ENV)
    private fun loadAndroidSdkPathFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(ANDROID_SDK_PATH_ENV)
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
    private fun loadPageIdFromEnvironment(): String? = System.getenv(NOTION_PAGE_ID_ENV)
    private fun loadPageIdFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(NOTION_PAGE_ID_ENV)
    }
    private fun loadLatitudeFromEnvironment(): String? = System.getenv(WEATHER_LATITUDE_ENV)
    private fun loadLatitudeFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(WEATHER_LATITUDE_ENV)
    }
    private fun loadLongitudeFromEnvironment(): String? = System.getenv(WEATHER_LONGITUDE_ENV)
    private fun loadLongitudeFromPropertiesFile(): String? {
        val file = File(LOCAL_PROPERTIES_FILE)
        if (!file.exists()) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }.getProperty(WEATHER_LONGITUDE_ENV)
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
