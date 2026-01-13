# Руководство по стилю кода / Code Style Guide

## Общие принципы / General Principles

### Язык программирования
- Основной язык: **Kotlin**
- Версия: 1.9+
- Целевая JVM: 17+

### Именование / Naming Conventions

#### Файлы и классы
```kotlin
// Файлы называются по имени главного класса в PascalCase
// File: OpenRouterAgent.kt
class OpenRouterAgent { ... }

// Файлы с расширениями функций называются по типу + Extensions
// File: StringExtensions.kt
fun String.toSnakeCase(): String { ... }
```

#### Переменные и функции
```kotlin
// camelCase для переменных и функций
val userName: String
fun processMessage(input: String): String

// SCREAMING_SNAKE_CASE для констант
const val MAX_RETRIES = 3
const val API_BASE_URL = "https://api.example.com"

// Приватные свойства с подчеркиванием (опционально)
private val _state = MutableStateFlow<State>(State.Idle)
val state: StateFlow<State> = _state.asStateFlow()
```

#### Пакеты
```kotlin
// Все в нижнем регистре, без подчеркиваний
package org.example.embedding
package org.example.mcp.server
```

### Форматирование / Formatting

#### Отступы и пробелы
```kotlin
// 4 пробела для отступов (НЕ табы)
class Example {
    fun method() {
        if (condition) {
            doSomething()
        }
    }
}

// Пробелы вокруг операторов
val sum = a + b
val isValid = condition1 && condition2

// Пробел после запятой
fun method(arg1: Int, arg2: String, arg3: Boolean)
```

#### Длина строки
- Максимальная длина строки: **120 символов**
- Для строковых литералов: разбиение через `trimIndent()` или `trimMargin()`

```kotlin
val longString = """
    Это длинная строка,
    которая разбита на несколько строк
    для лучшей читаемости
""".trimIndent()
```

### Структура классов / Class Structure

```kotlin
class ExampleClass(
    // 1. Параметры конструктора
    private val dependency: Dependency,
    private val config: Config
) {
    // 2. Companion object (если есть)
    companion object {
        const val TAG = "ExampleClass"
        fun create(): ExampleClass = ExampleClass(...)
    }
    
    // 3. Свойства
    private val cache = mutableMapOf<String, Any>()
    val publicProperty: String = "value"
    
    // 4. Блок init (если нужен)
    init {
        initialize()
    }
    
    // 5. Публичные методы
    fun publicMethod(): Result { ... }
    
    // 6. Приватные методы
    private fun privateHelper(): Unit { ... }
}
```

## Kotlin-специфичные правила / Kotlin-Specific Rules

### Null Safety
```kotlin
// Используйте nullable типы осознанно
fun findUser(id: String): User?

// Предпочитайте safe calls
val name = user?.name ?: "Unknown"

// Избегайте !! кроме тестов
// ❌ Плохо
val name = user!!.name

// ✅ Хорошо
val name = user?.name ?: throw IllegalStateException("User must not be null")
```

### Data Classes
```kotlin
// Используйте data classes для моделей данных
data class UserResponse(
    val id: String,
    val name: String,
    val email: String?
)

// Используйте default values для опциональных полей
data class Config(
    val timeout: Long = 30_000L,
    val retries: Int = 3,
    val enableLogging: Boolean = false
)
```

### Extension Functions
```kotlin
// Группируйте расширения по целевому типу
// File: StringExtensions.kt
fun String.toBase64(): String = Base64.encode(this.toByteArray())
fun String.truncate(maxLength: Int): String = 
    if (length > maxLength) take(maxLength) + "..." else this

// Избегайте расширений, которые изменяют состояние
// ❌ Плохо
fun MutableList<Int>.addAndSort(item: Int) { 
    add(item); sort() 
}

// ✅ Хорошо
fun List<Int>.withSorted(item: Int): List<Int> = 
    (this + item).sorted()
```

### Coroutines
```kotlin
// Используйте structured concurrency
class UserRepository(
    private val coroutineScope: CoroutineScope
) {
    // Suspend functions для асинхронных операций
    suspend fun fetchUser(id: String): User = withContext(Dispatchers.IO) {
        api.getUser(id)
    }
    
    // Используйте supervisorScope для независимых задач
    suspend fun fetchAllData() = supervisorScope {
        val users = async { fetchUsers() }
        val posts = async { fetchPosts() }
        DataBundle(users.await(), posts.await())
    }
}

// Правильный выбор Dispatcher
// Dispatchers.IO - для I/O операций (сеть, файлы, БД)
// Dispatchers.Default - для CPU-интенсивных операций
// Dispatchers.Main - для UI (Android)
```

### Sealed Classes и When
```kotlin
// Используйте sealed classes для ограниченных иерархий
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

// When должен покрывать все случаи
fun handleResult(result: Result<User>) = when (result) {
    is Result.Success -> showUser(result.data)
    is Result.Error -> showError(result.message)
    Result.Loading -> showLoading()
    // Не нужен else - компилятор проверит полноту
}
```

## Архитектурные паттерны / Architecture Patterns

### MCP Server Pattern
```kotlin
class CustomMcpServer(
    private val service: CustomService
) {
    // Стандартная структура MCP сервера
    fun configureMcpServer(application: Application) {
        application.install(ContentNegotiation) { json(json) }
        application.install(CORS) { anyHost() }
        application.routing {
            post("/mcp") { handleMcpRequest(call) }
        }
    }
    
    private suspend fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return when (request.method) {
            "initialize" -> handleInitialize(request)
            "tools/list" -> handleToolsList(request)
            "tools/call" -> handleToolCall(request)
            else -> errorResponse(request.id, "Method not found")
        }
    }
}
```

### Service Pattern
```kotlin
// Сервисы должны быть stateless где возможно
class RagService(
    private val embeddingClient: EmbeddingClient,
    private val storage: DocumentIndexStorage
) {
    // Методы должны быть чистыми функциями
    suspend fun search(query: String, limit: Int = 10): List<SearchResult> {
        val embedding = embeddingClient.getEmbedding(query)
        return storage.findSimilar(embedding, limit)
    }
}
```

### Tool Registration Pattern
```kotlin
// Регистрация инструментов через ToolRegistry
class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()
    
    fun register(tool: Tool) {
        tools[tool.name] = tool
    }
    
    fun getAll(): List<Tool> = tools.values.toList()
}

// Создание инструмента
class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Performs mathematical calculations"
    override val inputSchema = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("expression", buildJsonObject {
                put("type", "string")
                put("description", "Mathematical expression to evaluate")
            })
        })
    }
    
    override suspend fun execute(arguments: JsonObject): ToolResult {
        // Implementation
    }
}
```

## Обработка ошибок / Error Handling

```kotlin
// Используйте Result type для операций, которые могут упасть
suspend fun fetchData(): Result<Data> = runCatching {
    api.getData()
}

// Создавайте специфичные исключения
class TokenLimitExceededException(
    message: String,
    val currentTokens: Int,
    val maxTokens: Int
) : Exception(message)

// Логируйте ошибки с контекстом
try {
    processData(data)
} catch (e: Exception) {
    logger.error("Failed to process data: ${e.message}", e)
    throw ProcessingException("Data processing failed", e)
}
```

## Документация / Documentation

### KDoc
```kotlin
/**
 * Сервис для поиска информации в документации проекта с использованием RAG.
 * 
 * @property embeddingClient Клиент для генерации эмбеддингов текста
 * @property storage Хранилище индексированных документов
 * @constructor Создает RAG сервис с указанными зависимостями
 */
class RagService(
    private val embeddingClient: EmbeddingClient,
    private val storage: DocumentIndexStorage
) {
    /**
     * Выполняет семантический поиск по документации.
     *
     * @param query Поисковый запрос на естественном языке
     * @param limit Максимальное количество результатов (по умолчанию 10)
     * @param minSimilarity Минимальный порог сходства от 0.0 до 1.0
     * @return Список найденных фрагментов документации, отсортированных по релевантности
     * @throws EmbeddingException Если не удалось сгенерировать эмбеддинг запроса
     */
    suspend fun search(
        query: String,
        limit: Int = 10,
        minSimilarity: Double = 0.7
    ): List<SearchResult>
}
```

## Тестирование / Testing

```kotlin
// Используйте descriptive names для тестов
class RagServiceTest {
    @Test
    fun `search returns empty list when no documents match query`() {
        // Given
        val service = createService(emptyStorage())
        
        // When
        val results = runBlocking { service.search("nonexistent") }
        
        // Then
        assertTrue(results.isEmpty())
    }
    
    @Test
    fun `search respects minSimilarity threshold`() {
        // ...
    }
}

// Используйте фабричные методы для создания test fixtures
private fun createService(storage: DocumentIndexStorage = mockStorage()): RagService {
    return RagService(mockEmbeddingClient(), storage)
}
```

## Git Commit Messages

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Типы коммитов
- `feat`: Новая функциональность
- `fix`: Исправление бага
- `docs`: Только документация
- `style`: Форматирование, без изменения кода
- `refactor`: Рефакторинг без изменения поведения
- `test`: Добавление тестов
- `chore`: Обслуживание, зависимости

### Примеры
```
feat(rag): add semantic search for project documentation

Implement RAG service with:
- Document indexing with chunking
- Embedding generation via OpenRouter
- SQLite storage for embeddings
- Cosine similarity search

Closes #123
```

```
fix(mcp): handle null params in tool call requests

Previously, the server would crash when params was JsonNull.
Now it properly validates and returns an error response.
```

