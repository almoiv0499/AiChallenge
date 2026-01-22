package org.example.chat

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.client.ollama.OllamaClient
import org.example.client.ollama.OllamaChatService
import org.example.config.OllamaLlmConfig
import org.example.config.LoadedOllamaLlmConfig
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Главный класс для запуска чат-сервера с локальной моделью Ollama.
 * Параметры LLM (температура, контекст, max tokens, промпт) задаются через
 * переменные окружения — см. [OllamaLlmConfig] и OLLAMA_OPTIMIZATION.md.
 */
fun main(args: Array<String>) = runBlocking {
    println("🚀 Запуск чат-сервера с локальной моделью Ollama...")
    
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434/api"
    val model = System.getenv("OLLAMA_MODEL") ?: "llama3.2"
    val llmConfig: LoadedOllamaLlmConfig = OllamaLlmConfig.load()
    val systemPrompt = llmConfig.systemPrompt
    
    // Проверяем и запускаем Ollama, если нужно
    ensureOllamaRunning()
    
    // Инициализируем клиент Ollama
    val ollamaClient = OllamaClient(baseUrl = ollamaBaseUrl, defaultModel = model)
    
    // Проверяем доступность Ollama
    if (!ollamaClient.isAvailable()) {
        println("❌ Ollama API недоступен на $ollamaBaseUrl")
        println("   Убедитесь, что Ollama запущен: ollama serve")
        return@runBlocking
    }
    
    // Проверяем наличие модели
    val models = try {
        ollamaClient.listModels()
    } catch (e: Exception) {
        println("⚠️ Не удалось получить список моделей: ${e.message}")
        emptyList()
    }
    
    val modelToUse = if (models.any { it.name == model }) {
        println("✅ Модель '$model' найдена")
        model
    } else {
        if (models.isNotEmpty()) {
            println("⚠️ Модель '$model' не найдена, используется: ${models.first().name}")
            models.first().name
        } else {
            println("⚠️ Модели не найдены, используется модель по умолчанию: $model")
            println("   Установите модель: ollama pull $model")
            model
        }
    }
    
    // Создаем сервис чата
    val chatService = OllamaChatService(
        ollamaClient = ollamaClient,
        model = modelToUse,
        systemPrompt = systemPrompt
    )
    
    // Создаем хранилище истории
    val historyStorage = ChatHistoryStorage()
    
    val chatApiServer = ChatApiServer(
        ollamaClient = ollamaClient,
        chatService = chatService,
        historyStorage = historyStorage,
        model = modelToUse,
        llmConfig = llmConfig
    )
    
    // Запускаем сервер на всех интерфейсах (0.0.0.0) для доступа извне
    val server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
        chatApiServer.configureApiServer(this)
    }
    
    server.start(wait = false)
    
    println("✅ Чат-сервер запущен!")
    println("   🌐 Веб-интерфейс: http://localhost:$port")
    println("   📡 API: http://localhost:$port/api")
    println("   🦙 Модель: $modelToUse")
    println("   📝 История диалога сохраняется в базу данных")
    println("   ⚙️ LLM: temp=${llmConfig.temperature}, max_tokens=${llmConfig.maxTokens}, num_ctx=${llmConfig.numCtx}")
    println()
    println("   Для остановки нажмите Ctrl+C")
    
    // Держим приложение запущенным
    try {
        while (true) {
            delay(Long.MAX_VALUE)
        }
    } catch (e: InterruptedException) {
        println("\n🛑 Остановка сервера...")
        server.stop(1000, 5000)
        ollamaClient.close()
    }
}

/**
 * Проверяет и запускает Ollama, если он не запущен
 */
private suspend fun ensureOllamaRunning() {
    val ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434/api"
    val ollamaClient = OllamaClient(baseUrl = ollamaBaseUrl)
    
    if (ollamaClient.isAvailable()) {
        println("✅ Ollama уже запущен")
        ollamaClient.close()
        return
    }
    
    println("🔍 Ollama не запущен, пытаемся запустить...")
    
    // Пытаемся найти и запустить Ollama
    val ollamaPath = findOllamaExecutable()
    if (ollamaPath != null) {
        println("📦 Найден Ollama: $ollamaPath")
        println("   Запуск Ollama в фоновом режиме...")
        
        try {
            val process = ProcessBuilder(ollamaPath, "serve")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            
            // Ждем, пока Ollama запустится
            var attempts = 0
            while (attempts < 30) {
                delay(1000)
                if (ollamaClient.isAvailable()) {
                    println("✅ Ollama успешно запущен")
                    ollamaClient.close()
                    return
                }
                attempts++
            }
            
            println("⚠️ Ollama запущен, но не отвечает на запросы")
            println("   Проверьте логи или запустите вручную: ollama serve")
        } catch (e: Exception) {
            println("⚠️ Не удалось запустить Ollama автоматически: ${e.message}")
            println("   Запустите вручную: ollama serve")
        }
    } else {
        println("⚠️ Ollama не найден в системе")
        println("   Установите Ollama: https://ollama.ai")
        println("   Или запустите вручную: ollama serve")
    }
    
    ollamaClient.close()
}

/**
 * Ищет исполняемый файл Ollama в системе
 */
private fun findOllamaExecutable(): String? {
    // Проверяем переменную окружения
    val ollamaEnv = System.getenv("OLLAMA_PATH")
    if (ollamaEnv != null && File(ollamaEnv).exists()) {
        return ollamaEnv
    }
    
    // Проверяем стандартные пути
    val possiblePaths = listOf(
        "ollama", // В PATH
        "/usr/local/bin/ollama",
        "/usr/bin/ollama",
        "C:\\Program Files\\Ollama\\ollama.exe",
        System.getProperty("user.home") + "/.local/bin/ollama"
    )
    
    for (path in possiblePaths) {
        try {
            if (path == "ollama") {
                // Проверяем через which/where
                val process = if (System.getProperty("os.name").lowercase().contains("win")) {
                    ProcessBuilder("where", "ollama").start()
                } else {
                    ProcessBuilder("which", "ollama").start()
                }
                
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    return "ollama"
                }
            } else {
                val file = File(path)
                if (file.exists() && file.canExecute()) {
                    return path
                }
            }
        } catch (e: Exception) {
            // Продолжаем поиск
        }
    }
    
    return null
}
