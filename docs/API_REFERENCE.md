# API Reference / Справочник по API

## Обзор архитектуры / Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Main Application                          │
│  ┌──────────┐  ┌─────────────┐  ┌───────────┐  ┌─────────────┐  │
│  │ConsoleUI │  │OpenRouter   │  │RAGService │  │ToolRegistry │  │
│  │          │  │Agent        │  │           │  │             │  │
│  └────┬─────┘  └──────┬──────┘  └─────┬─────┘  └──────┬──────┘  │
│       │               │               │               │          │
└───────┼───────────────┼───────────────┼───────────────┼──────────┘
        │               │               │               │
        │    ┌──────────┴──────────┐    │    ┌─────────┴─────────┐
        │    │ OpenRouterClient    │    │    │   MCP Servers     │
        │    │ (HTTP API)          │    │    │ - Notion (8081)   │
        │    └─────────────────────┘    │    │ - Weather (8082)  │
        │                               │    │ - Git (8083)      │
        │    ┌──────────────────────────┴────┴───────────────────┐
        │    │              Document Index DB                     │
        │    │              (SQLite)                               │
        │    └────────────────────────────────────────────────────┘
```

## Core Components / Основные компоненты

### OpenRouterAgent

Главный агент для обработки сообщений пользователя.

```kotlin
class OpenRouterAgent(
    client: OpenRouterClient,
    toolRegistry: ToolRegistry,
    deviceSearchExecutor: DeviceSearchService?,
    ragService: RagService?
)
```

#### Методы

| Метод | Описание |
|-------|----------|
| `suspend fun processMessage(message: String): ChatResponse` | Обрабатывает сообщение пользователя и возвращает ответ |
| `fun clearHistory()` | Очищает историю диалога |

#### Пример использования
```kotlin
val agent = OpenRouterAgent(client, toolRegistry, null, ragService)
val response = agent.processMessage("Сколько будет 2 + 2?")
println(response.response) // "4"
```

---

### RagService

Сервис для поиска информации в документации проекта.

```kotlin
class RagService(
    embeddingClient: EmbeddingClient,
    storage: DocumentIndexStorage
)
```

#### Методы

| Метод | Описание |
|-------|----------|
| `suspend fun search(query: String, limit: Int, minSimilarity: Double): List<SearchResult>` | Семантический поиск по документации |
| `fun hasDocuments(): Boolean` | Проверяет наличие проиндексированных документов |
| `fun getDocumentCount(): Int` | Возвращает количество документов в индексе |
| `fun getAllDocuments(): List<Document>` | Возвращает все проиндексированные документы |

#### SearchResult
```kotlin
data class SearchResult(
    val text: String,           // Найденный фрагмент текста
    val similarity: Double,     // Коэффициент сходства (0.0-1.0)
    val source: String,         // Источник (путь к файлу)
    val title: String?,         // Заголовок документа
    val metadata: Map<String, String> // Дополнительные метаданные
)
```

#### Пример использования
```kotlin
val results = ragService.search(
    query = "как настроить MCP сервер",
    limit = 5,
    minSimilarity = 0.6
)
results.forEach { result ->
    println("${result.title}: ${result.text.take(100)}...")
}
```

---

### ToolRegistry

Реестр инструментов для агента.

```kotlin
class ToolRegistry {
    companion object {
        fun createDefault(): ToolRegistry
    }
}
```

#### Методы

| Метод | Описание |
|-------|----------|
| `fun register(tool: Tool)` | Регистрирует новый инструмент |
| `fun get(name: String): Tool?` | Получает инструмент по имени |
| `fun getAll(): List<Tool>` | Получает все зарегистрированные инструменты |

---

### McpClient

Клиент для подключения к MCP серверам.

```kotlin
class McpClient private constructor(transport: Transport) {
    companion object {
        fun createHttp(baseUrl: String): McpClient
        fun createStdio(command: String, args: List<String>): McpClient
    }
}
```

#### Методы

| Метод | Описание |
|-------|----------|
| `suspend fun initialize(): InitializeResult` | Инициализирует соединение с MCP сервером |
| `suspend fun listTools(): List<McpTool>` | Получает список доступных инструментов |
| `suspend fun callTool(name: String, arguments: JsonObject): ToolCallResult` | Вызывает инструмент |

---

## MCP Servers / MCP Серверы

### Notion MCP Server (порт 8081)

Инструменты для работы с Notion API.

| Инструмент | Описание | Параметры |
|------------|----------|-----------|
| `notion_get_tasks` | Получить задачи из базы данных | `status?: string` |
| `notion_create_task` | Создать новую задачу | `title: string, description?: string, priority?: string` |
| `notion_update_task` | Обновить задачу | `taskId: string, status?: string, priority?: string` |
| `notion_get_page_content` | Получить содержимое страницы | `pageId?: string` |

---

### Weather MCP Server (порт 8082)

Инструменты для получения информации о погоде.

| Инструмент | Описание | Параметры |
|------------|----------|-----------|
| `get_weather` | Получить текущую погоду | `lat?: number, lon?: number, city?: string` |
| `get_forecast` | Получить прогноз погоды | `lat?: number, lon?: number, days?: number` |

---

### Git MCP Server (порт 8083)

Инструменты для работы с Git репозиторием.

| Инструмент | Описание | Параметры |
|------------|----------|-----------|
| `get_current_branch` | Получить текущую ветку | - |
| `get_git_status` | Получить статус репозитория | - |
| `get_open_files` | Получить измененные файлы | - |
| `get_recent_commits` | Получить последние коммиты | `limit?: number` (default: 5) |

#### Пример ответа get_current_branch
```json
{
    "isError": false,
    "content": [{
        "type": "text",
        "text": "main"
    }]
}
```

#### Пример ответа get_git_status
```json
{
    "isError": false,
    "content": [{
        "type": "text",
        "text": " M src/main/kotlin/org/example/Main.kt\n?? docs/new_file.md"
    }]
}
```

---

## Data Models / Модели данных

### ChatResponse
```kotlin
data class ChatResponse(
    val response: String,           // Текст ответа
    val toolCalls: List<ToolCall>,  // Список вызванных инструментов
    val model: String?,             // Использованная модель
    val inputTokens: Int?,          // Токены на входе
    val outputTokens: Int?          // Токены на выходе
)
```

### ToolCall
```kotlin
data class ToolCall(
    val toolName: String,   // Имя инструмента
    val arguments: String,  // Аргументы (JSON)
    val result: String      // Результат выполнения
)
```

### Document
```kotlin
data class Document(
    val id: String,
    val text: String,
    val embedding: FloatArray,
    val source: String,
    val title: String?,
    val chunkIndex: Int,
    val metadata: Map<String, String>
)
```

---

## Configuration / Конфигурация

### local.properties
```properties
# Required / Обязательные
OPENROUTER_API_KEY=sk-or-v1-ваш_ключ

# Optional / Опциональные
NOTION_API_KEY=secret_xxx
NOTION_DATABASE_ID=xxx-xxx-xxx
NOTION_PAGE_ID=xxx-xxx-xxx
WEATHER_API_KEY=xxx
WEATHER_LATITUDE=55.7558
WEATHER_LONGITUDE=37.6173
ANDROID_SDK_PATH=C:\Users\...\Android\Sdk
```

### OpenRouterConfig
```kotlin
object OpenRouterConfig {
    const val BASE_URL = "https://openrouter.ai/api/v1"
    const val MODEL = "openai/gpt-4o-mini-2024-07-18"
    const val MAX_TOKENS = 4096
    var ENABLE_TOOLS = true
    var ENABLE_TASK_REMINDER = false
}
```

---

## CLI Commands / Команды CLI

| Команда | Описание |
|---------|----------|
| `/exit`, `/quit`, `/q` | Выход из программы |
| `/clear` | Очистить историю диалога |
| `/clear-tasks` | Очистить базу данных задач |
| `/help` | Показать справку |
| `/help_by_project <вопрос>` | Поиск информации в документации проекта через RAG |
| `/tools` | Переключить использование инструментов |
| `/tasks` | Переключить напоминания о задачах |

---

## Error Handling / Обработка ошибок

### TokenLimitExceededException
Выбрасывается когда запрос превышает лимит токенов модели.

```kotlin
class TokenLimitExceededException(
    message: String,
    val currentTokens: Int,
    val maxTokens: Int
) : Exception(message)
```

### Обработка в коде
```kotlin
try {
    val response = agent.processMessage(input)
} catch (e: TokenLimitExceededException) {
    println("Превышен лимит токенов: ${e.currentTokens}/${e.maxTokens}")
    println("Используйте /clear для очистки истории")
}
```

---

## HTTP Endpoints / HTTP эндпоинты

### MCP Protocol Endpoint
Все MCP серверы используют единый формат JSON-RPC 2.0:

```
POST /mcp
Content-Type: application/json
```

#### Request
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
        "name": "get_current_branch",
        "arguments": {}
    }
}
```

#### Response
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
        "isError": false,
        "content": [{
            "type": "text",
            "text": "main"
        }]
    }
}
```

---

## Embedding API / API эмбеддингов

### EmbeddingClient
```kotlin
class EmbeddingClient(apiKey: String) {
    suspend fun getEmbedding(text: String): FloatArray
    suspend fun getEmbeddings(texts: List<String>): List<FloatArray>
    fun close()
}
```

### Используемая модель
- Модель: `text-embedding-3-small` (через OpenRouter)
- Размерность: 1536
- Максимальная длина: 8191 токенов

---

## Gradle Tasks / Gradle задачи

```bash
# Запуск приложения
./gradlew run --console=plain

# Индексация документации
./gradlew runIndexDocs

# Тестовый поиск в индексе
./gradlew runSearchTest

# Сборка JAR
./gradlew jar

# Запуск тестов
./gradlew test
```

