# Документация для разработчиков: AiChallenge (OpenRouter Agent)

## Обзор проекта

**AiChallenge** — это AI-агент на Kotlin, который взаимодействует с пользователем через терминальный интерфейс, использует OpenRouter API для обработки сообщений и предоставляет доступ к внешним сервисам через протокол MCP (Model Context Protocol).

### Ключевые возможности

| Функция | Описание |
|---------|----------|
| Tool Calling | Вызов инструментов (калькулятор, время, поиск и др.) через AI |
| MCP серверы | Интеграция с Notion, Weather API, Git через MCP протокол |
| RAG | Поиск информации в локальной документации с использованием эмбеддингов |
| История сжатия | Автоматическое сжатие истории разговора для экономии токенов |
| Android Emulator | Автоматизация поиска на Android эмулятора |

---

## Архитектура

```
┌─────────────────────────────────────────────────────────────────┐
│                          Main.kt                                │
│                    (Точка входа, инициализация)                 │
└───────────────┬─────────────────────────────────────────────────┘
                │
    ┌───────────▼───────────┐
    │   OpenRouterAgent     │◄──── ToolRegistry (встроенные инструменты)
    │   (Обработка сообщений│
    │    агентский цикл)    │◄──── RagService (поиск в документации)
    └───────────┬───────────┘
                │
    ┌───────────▼───────────┐
    │   OpenRouterClient    │──────► OpenRouter API
    │   (HTTP-клиент)       │
    └───────────────────────┘
                
    ┌───────────────────────┐
    │      MCP Clients      │──────► MCP Servers (локальные)
    │   (HTTP/Stdio)        │        ├── NotionMcpServer (8081)
    └───────────────────────┘        ├── WeatherMcpServer (8082)
                                     └── GitMcpServer (8083)
```

### Модули проекта

| Пакет | Назначение |
|-------|------------|
| `agent/` | AI-агент и Android-автоматизация |
| `client/` | HTTP-клиент для OpenRouter API |
| `config/` | Конфигурация и константы |
| `embedding/` | RAG: эмбеддинги, индексация, поиск |
| `mcp/` | MCP-клиент, модели и транспорты |
| `mcp/server/` | Локальные MCP-серверы |
| `models/` | Модели данных для API |
| `storage/` | Персистентное хранение (SQLite) |
| `tools/` | Инструменты агента и адаптеры |
| `ui/` | Терминальный интерфейс |

---

## Компоненты и их ответственность

### OpenRouterAgent

**Путь:** `agent/OpenRouterAgent.kt`

Основной AI-агент, реализующий цикл обработки сообщений с поддержкой function calling.

**Ответственность:**
- Обработка пользовательских сообщений через `processMessage()`
- Агентский цикл с итеративными вызовами инструментов
- Парсинг function_call из текстовых ответов модели
- Сжатие истории разговора при превышении порога
- Управление системным промптом

**Ключевые методы:**

```kotlin
suspend fun processMessage(userMessage: String): ChatResponse  // Точка входа
private suspend fun executeAgentLoop(): ChatResponse           // Цикл до 3 итераций
private suspend fun sendRequest(): OpenRouterResponse          // Отправка в API
private suspend fun compressHistoryIfNeeded()                  // Сжатие истории
```

**Особенности парсинга function_call:**

Агент обрабатывает function_call в нескольких форматах:
1. Стандартный JSON-RPC формат от API
2. JSON в markdown-блоке (` ```json ... ``` `)
3. Inline JSON в тексте ответа
4. Regex fallback для неполных форматов

### ToolRegistry и AgentTool

**Путь:** `tools/OpenRouterTools.kt`

Реестр инструментов и интерфейс для создания инструментов.

```kotlin
interface AgentTool {
    val name: String
    val description: String
    fun getDefinition(): OpenRouterTool
    fun execute(arguments: Map<String, String>): String
}
```

**Встроенные инструменты:**

| Инструмент | Описание | Параметры |
|------------|----------|-----------|
| `get_current_time` | Текущее время | `timezone` (опц.) |
| `calculator` | Математические операции | `operation`, `a`, `b` |
| `search` | Поиск информации (эмуляция) | `query` |
| `random_number` | Генерация случайного числа | `min`, `max` |

### McpToolAdapter

**Путь:** `tools/McpToolAdapter.kt`

Адаптер для преобразования MCP-инструментов в формат `AgentTool`.

**Механизм работы:**
1. Получает `McpTool` и `McpClient`
2. Конвертирует JSON Schema в `OpenRouterTool`
3. При вызове `execute()` — вызывает `mcpClient.callTool()`

---

## MCP (Model Context Protocol)

### McpClient

**Путь:** `mcp/McpClient.kt`

Клиент для взаимодействия с MCP-серверами. Поддерживает два транспорта:

```kotlin
// HTTP транспорт (по умолчанию)
val client = McpClient.createHttp(baseUrl = "http://localhost:8081/mcp")

// Stdio транспорт (для локальных процессов)
val client = McpClient.createStdio()
```

**Жизненный цикл:**
1. `initialize()` — инициализация и обмен capabilities
2. `listTools()` — получение списка инструментов
3. `callTool(name, arguments)` — вызов инструмента
4. `close()` — закрытие соединения

### MCP Серверы

Локальные серверы на базе Ktor, реализующие MCP-протокол:

| Сервер | Порт | Инструменты |
|--------|------|-------------|
| NotionMcpServer | 8081 | `notion_get_tasks`, `notion_create_task`, `get_notion_page`, `append_notion_block` |
| WeatherMcpServer | 8082 | `get_weather`, `get_forecast` |
| GitMcpServer | 8083 | `get_current_branch`, `get_git_status`, `get_open_files`, `get_recent_commits` |

**Пример регистрации сервера:**

```kotlin
val gitMcpServer = GitMcpServer()
embeddedServer(Netty, port = 8083) {
    gitMcpServer.configureMcpServer(this)
}.start(wait = false)
```

### Модели MCP

**Путь:** `mcp/McpModels.kt`

```kotlin
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int?,
    val method: String,
    val params: JsonElement?
)

@Serializable
data class McpTool(
    val name: String,
    val description: String?,
    @SerialName("inputSchema") val inputSchema: JsonElement?
)
```

---

## RAG (Retrieval-Augmented Generation)

### Компоненты RAG

```
┌──────────────┐     ┌─────────────────┐     ┌──────────────────────┐
│ TextChunker  │ ──► │ EmbeddingClient │ ──► │ DocumentIndexStorage │
│ (Разбивка)   │     │ (OpenAI Ada)    │     │ (SQLite)             │
└──────────────┘     └─────────────────┘     └──────────────────────┘
                              │
                     ┌────────▼────────┐
                     │   RagService    │
                     │ (Поиск/Индекс)  │
                     └─────────────────┘
```

### EmbeddingClient

**Путь:** `embedding/EmbeddingClient.kt`

Генерация эмбеддингов через OpenRouter API (модель `text-embedding-ada-002`).

```kotlin
val embeddings = embeddingClient.generateEmbedding("текст для индексации")
// Возвращает FloatArray размерности 1536
```

### DocumentIndexer

**Путь:** `embedding/DocumentIndexer.kt`

Пайплайн индексации документов:

1. **Разбивка** — `TextChunker` делит текст на чанки с перекрытием
2. **Эмбеддинги** — генерация векторов для каждого чанка
3. **Сохранение** — SQLite индекс с метаданными

```kotlin
val indexer = DocumentIndexer(embeddingClient, storage)
val chunksIndexed = indexer.indexDocument(
    documentId = "doc1",
    text = fileContent,
    source = "docs/README.md",
    title = "Документация"
)
```

### RagService

**Путь:** `embedding/RagService.kt`

Высокоуровневый сервис для семантического поиска:

```kotlin
val results = ragService.search(
    query = "как настроить MCP сервер",
    limit = 5,
    minSimilarity = 0.7
)
```

**Возвращает:** `List<SearchResult>` с полями `text`, `title`, `similarity`, `metadata`.

---

## Storage

### HistoryStorage

**Путь:** `storage/HistoryStorage.kt`

Хранение сжатых summaries разговора в SQLite.

**Схема таблицы:**
```sql
CREATE TABLE conversation_summaries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    summary TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    message_count INTEGER NOT NULL DEFAULT 0
)
```

**Ключевые методы:**
- `saveSummary(summary, messageCount)` — сохранение summary
- `getLatestSummary()` — последний summary для восстановления контекста
- `clearAllSummaries()` — очистка истории

### DocumentIndexStorage

**Путь:** `embedding/DocumentIndexStorage.kt`

SQLite хранилище для RAG индекса с поддержкой косинусного сходства.

---

## Конфигурация

### AppConfig

**Путь:** `config/AppConfig.kt`

Загрузка конфигурации из переменных окружения или `local.properties`:

| Переменная | Описание | Обязательно |
|------------|----------|-------------|
| `OPENROUTER_API_KEY` | API ключ OpenRouter | ✅ |
| `NOTION_API_KEY` | API ключ Notion | ❌ |
| `NOTION_DATABASE_ID` | ID базы данных Notion | ❌ |
| `NOTION_PAGE_ID` | ID страницы Notion | ❌ |
| `ANDROID_SDK_PATH` | Путь к Android SDK | ❌ |
| `MCP_TRANSPORT` | Транспорт MCP (HTTP/STDIO) | ❌ |

**Приоритет загрузки:** Переменная окружения → `local.properties`

### OpenRouterConfig

**Путь:** `config/OpenRouterConfig.kt`

Константы и настройки агента:

```kotlin
object OpenRouterConfig {
    const val API_URL = "https://openrouter.ai/api/v1/responses"
    const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    const val MAX_AGENT_ITERATIONS = 3        // Макс. итераций цикла
    const val HISTORY_COMPRESSION_THRESHOLD = 3  // Порог сжатия
    var ENABLE_TOOLS = true                   // Включение инструментов
}
```

---

## Android Emulator Agent

**Путь:** `agent/android/`

Автоматизация поиска на Android эмуляторе через ADB.

### Компоненты

| Класс | Ответственность |
|-------|-----------------|
| `DeviceSearchService` | Оркестрация поиска |
| `AndroidEmulatorController` | Запуск/управление эмулятором |
| `AdbCommandExecutor` | Выполнение ADB команд |

### Workflow

1. Проверка запущенного эмулятора
2. Запуск эмулятора (если нужно)
3. Открытие Chrome с поисковым запросом

```kotlin
val result = deviceSearchService.executeSearch("Kotlin programming")
// "Successfully launched Chrome browser on Android emulator..."
```

**Триггеры активации:**
- "найди на устройстве: ..."
- "поиск в эмуляторе: ..."
- "search on device: ..."

---

## Примеры использования

### Добавление нового инструмента

```kotlin
class MyCustomTool : AgentTool {
    override val name = "my_custom_tool"
    override val description = "Описание инструмента"
    
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "param1" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Описание параметра"
                )
            ),
            required = listOf("param1")
        )
    )
    
    override fun execute(arguments: Map<String, String>): String {
        val param1 = arguments["param1"] ?: return "Ошибка: param1 не указан"
        return "Результат для $param1"
    }
}

// Регистрация
toolRegistry.register(MyCustomTool())
```

### Создание MCP сервера

```kotlin
class CustomMcpServer {
    fun configureMcpServer(application: Application) {
        application.routing {
            post("/mcp") { request ->
                when (request.method) {
                    "initialize" -> handleInitialize(request)
                    "tools/list" -> handleToolsList(request)
                    "tools/call" -> handleToolCall(request)
                }
            }
        }
    }
}
```

### Индексация документации

```bash
# Через Gradle
./gradlew runIndexDocs

# Или программно
val indexer = ProjectDocsIndexer.create()
indexer?.indexProjectDocumentation()
```

---

## Входные/выходные данные

### OpenRouterAgent.processMessage()

**Вход:**
```kotlin
val userMessage: String = "Сколько будет 2+2?"
```

**Выход:**
```kotlin
data class ChatResponse(
    val response: String,           // Текст ответа
    val toolCalls: List<ToolCallResult>,  // Вызванные инструменты
    val apiResponse: ApiResponse?   // Парсинг JSON из ответа (опц.)
)
```

### McpClient.callTool()

**Вход:**
```kotlin
val name = "get_weather"
val arguments = buildJsonObject { put("city", "Moscow") }
```

**Выход:**
```json
{
  "isError": false,
  "content": [{ "type": "text", "text": "Temperature: 15°C" }]
}
```

---

## Ограничения и Edge Cases

### Ограничения

| Ограничение | Значение | Примечание |
|-------------|----------|------------|
| Макс. итераций агента | 3 | Предотвращает бесконечные циклы |
| Порог сжатия истории | 3 сообщения | После 3 сообщений — сжатие |
| Размер чанка RAG | ~500 токенов | Оптимум для Ada embeddings |
| Timeout HTTP | 60 сек | Для API запросов |

### Edge Cases

1. **Пустой ответ API** — возвращается сообщение об ошибке
2. **Function call в тексте** — агент парсит JSON из markdown и inline текста
3. **Неподдерживаемый инструмент** — возврат ошибки с именем инструмента
4. **SQLite недоступна** — fallback без сохранения истории
5. **Отсутствие Android SDK** — DeviceSearchService не создаётся

---

## Запуск и интеграция

### Требования

- JDK 17+
- Gradle Wrapper (включён)
- API ключ OpenRouter

### Быстрый старт

```bash
# 1. Настройка ключа
echo "OPENROUTER_API_KEY=sk-or-v1-ваш_ключ" > local.properties

# 2. Запуск
./gradlew run --console=plain
```

### Команды чата

| Команда | Действие |
|---------|----------|
| `/exit` | Выход |
| `/clear` | Очистка истории |
| `/help <вопрос>` | RAG поиск в документации |
| `/tools` | Переключение инструментов |

### Gradle задачи

```bash
./gradlew run              # Запуск агента
./gradlew runIndexDocs     # Индексация документации
./gradlew runSearchTest    # Тест поиска RAG
./gradlew test             # Запуск тестов
```

---

## Зависимости

| Библиотека | Версия | Назначение |
|------------|--------|------------|
| Ktor | 2.3.12 | HTTP клиент/сервер |
| kotlinx-serialization | 1.7.1 | JSON сериализация |
| SQLite JDBC | 3.44.1.0 | Локальное хранилище |
| Logback | 1.5.6 | Логирование |
