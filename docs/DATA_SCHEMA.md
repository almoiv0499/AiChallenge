# Схемы данных / Data Schemas

## SQLite Databases / Базы данных SQLite

### document_index.db - Индекс документов RAG

Хранит эмбеддинги документации проекта для семантического поиска.

```sql
CREATE TABLE documents (
    id TEXT PRIMARY KEY,           -- Уникальный ID документа
    text TEXT NOT NULL,            -- Текст чанка
    embedding BLOB NOT NULL,       -- Эмбеддинг (FloatArray, 1536 dim)
    source TEXT NOT NULL,          -- Путь к исходному файлу
    title TEXT,                    -- Заголовок документа
    chunk_index INTEGER NOT NULL,  -- Индекс чанка в документе
    metadata TEXT                  -- JSON с дополнительными метаданными
);

CREATE INDEX idx_documents_source ON documents(source);
CREATE INDEX idx_documents_title ON documents(title);
```

#### Пример данных
```json
{
    "id": "doc_README_a1b2c3d4",
    "text": "## Функции агента\n- get_current_time - получение текущего времени...",
    "source": "C:/Workspaces/AiChallenge/README.md",
    "title": "Основная документация проекта",
    "chunk_index": 0,
    "metadata": {
        "file": "README.md",
        "path": "C:/Workspaces/AiChallenge/README.md",
        "type": "markdown"
    }
}
```

---

### conversation_history.db - История диалогов

Хранит сжатые резюме диалогов для экономии токенов.

```sql
CREATE TABLE summaries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    summary TEXT NOT NULL,           -- Сжатое резюме диалога
    created_at TEXT NOT NULL,        -- Дата создания (ISO 8601)
    message_count INTEGER NOT NULL,  -- Количество сжатых сообщений
    token_estimate INTEGER           -- Оценка количества токенов
);

CREATE INDEX idx_summaries_created_at ON summaries(created_at);
```

#### Пример данных
```json
{
    "id": 1,
    "summary": "Пользователь спрашивал о настройке MCP серверов. Обсудили порты 8081, 8082, 8083 для Notion, Weather и Git серверов соответственно.",
    "created_at": "2026-01-12T10:30:00Z",
    "message_count": 10,
    "token_estimate": 150
}
```

---

### tasks.db - База задач

Хранит задачи и напоминания.

```sql
CREATE TABLE tasks (
    id TEXT PRIMARY KEY,             -- ID задачи из Notion
    title TEXT NOT NULL,             -- Название задачи
    description TEXT,                -- Описание задачи
    status TEXT NOT NULL,            -- Статус: todo, in_progress, done
    priority TEXT,                   -- Приоритет: low, medium, high
    due_date TEXT,                   -- Дедлайн (ISO 8601)
    last_reminder TEXT,              -- Время последнего напоминания
    created_at TEXT NOT NULL,        -- Дата создания
    updated_at TEXT NOT NULL         -- Дата обновления
);

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
```

---

## JSON-RPC Models / Модели JSON-RPC

### JsonRpcRequest
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

### JsonRpcResponse
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": { ... },
    "error": null
}
```

### JsonRpcError
```json
{
    "code": -32602,
    "message": "Invalid params",
    "data": { ... }
}
```

---

## MCP Protocol Models / Модели MCP протокола

### InitializeResult
```json
{
    "protocolVersion": "2025-06-18",
    "capabilities": {
        "tools": {
            "listChanged": true
        }
    },
    "serverInfo": {
        "name": "GitMcpServer",
        "version": "1.0.0"
    }
}
```

### McpTool
```json
{
    "name": "get_current_branch",
    "description": "Получить текущую активную ветку Git репозитория",
    "inputSchema": {
        "type": "object",
        "properties": {},
        "required": []
    }
}
```

### ToolCallResult
```json
{
    "isError": false,
    "content": [
        {
            "type": "text",
            "text": "main"
        }
    ]
}
```

---

## OpenRouter API Models / Модели OpenRouter API

### ChatRequest
```json
{
    "model": "openai/gpt-4o-mini-2024-07-18",
    "messages": [
        {
            "role": "system",
            "content": "Ты — AI-ассистент..."
        },
        {
            "role": "user",
            "content": "Привет!"
        }
    ],
    "tools": [
        {
            "type": "function",
            "function": {
                "name": "calculator",
                "description": "Математические вычисления",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "operation": {
                            "type": "string",
                            "enum": ["add", "subtract", "multiply", "divide"]
                        },
                        "a": { "type": "number" },
                        "b": { "type": "number" }
                    },
                    "required": ["operation", "a", "b"]
                }
            }
        }
    ],
    "tool_choice": "auto",
    "max_tokens": 4096,
    "temperature": 0.7
}
```

### ChatResponse
```json
{
    "id": "gen-xxx",
    "model": "openai/gpt-4o-mini-2024-07-18",
    "choices": [
        {
            "index": 0,
            "message": {
                "role": "assistant",
                "content": "Привет! Чем могу помочь?",
                "tool_calls": null
            },
            "finish_reason": "stop"
        }
    ],
    "usage": {
        "prompt_tokens": 150,
        "completion_tokens": 25,
        "total_tokens": 175
    }
}
```

### ToolCall (in response)
```json
{
    "id": "call_xxx",
    "type": "function",
    "function": {
        "name": "calculator",
        "arguments": "{\"operation\": \"add\", \"a\": 2, \"b\": 2}"
    }
}
```

---

## Embedding Models / Модели эмбеддингов

### EmbeddingRequest
```json
{
    "model": "text-embedding-3-small",
    "input": "Текст для эмбеддинга"
}
```

### EmbeddingResponse
```json
{
    "object": "list",
    "data": [
        {
            "object": "embedding",
            "index": 0,
            "embedding": [0.0023, -0.0091, 0.0156, ...]
        }
    ],
    "model": "text-embedding-3-small",
    "usage": {
        "prompt_tokens": 8,
        "total_tokens": 8
    }
}
```

---

## Internal Models / Внутренние модели

### SearchResult
```kotlin
data class SearchResult(
    val text: String,
    val similarity: Double,
    val source: String,
    val title: String?,
    val metadata: Map<String, String>
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

### ChatResponse
```kotlin
data class ChatResponse(
    val response: String,
    val toolCalls: List<ToolCall>,
    val model: String?,
    val inputTokens: Int?,
    val outputTokens: Int?
)
```

### ToolCall
```kotlin
data class ToolCall(
    val toolName: String,
    val arguments: String,
    val result: String
)
```

---

## Configuration Files / Конфигурационные файлы

### local.properties
```properties
# Required
OPENROUTER_API_KEY=sk-or-v1-xxx

# Optional - Notion
NOTION_API_KEY=secret_xxx
NOTION_DATABASE_ID=xxx-xxx-xxx-xxx
NOTION_PAGE_ID=xxx-xxx-xxx-xxx

# Optional - Weather
WEATHER_API_KEY=xxx
WEATHER_LATITUDE=55.7558
WEATHER_LONGITUDE=37.6173

# Optional - Android
ANDROID_SDK_PATH=C:\Users\...\Android\Sdk
```

### gradle.properties
```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m
```

