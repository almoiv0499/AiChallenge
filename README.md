# AI Agent - Терминальный чат

AI-агент на Kotlin с поддержкой **офлайн режима** (локальная модель Ollama) и облачного режима (OpenRouter API).

## 🦙 Офлайн режим (по умолчанию)

Приложение **автоматически использует локальную модель Ollama** для работы без интернета.

### Быстрый старт (офлайн режим)

1. **Установите Ollama**: https://ollama.ai
2. **Запустите Ollama сервер**:
   - Windows: скачайте и запустите `Ollama.exe`
   - Linux/Mac: `ollama serve`
3. **Установите модель**:
   ```bash
   ollama pull llama3.2
   ```
4. **Запустите приложение**:
   ```bash
   # Windows
   gradlew.bat run --console=plain
   
   # Linux/macOS  
   ./gradlew run --console=plain
   ```

Приложение автоматически обнаружит Ollama и запустится в офлайн режиме! 🎉

## 🌐 Облачный режим (OpenRouter)

Если Ollama недоступен, приложение автоматически переключится на OpenRouter API.

### Настройка OpenRouter

1. **Получите API ключ**: Зарегистрируйтесь на [OpenRouter](https://openrouter.ai/)
2. **Установите ключ**:

Создайте файл `local.properties` в корне проекта:

```properties
OPENROUTER_API_KEY=sk-or-v1-ваш_ключ
```

Или установите переменную окружения:

```bash
# Windows
set OPENROUTER_API_KEY=sk-or-v1-ваш_ключ

# Linux/macOS
export OPENROUTER_API_KEY=sk-or-v1-ваш_ключ
```

### Принудительное использование режима

```bash
# Принудительно использовать Ollama
set FORCE_MODE=OLLAMA

# Принудительно использовать OpenRouter
set FORCE_MODE=OPENROUTER

# Отключить автоматическое использование Ollama
set USE_OLLAMA=FALSE
```

## Требования

- JDK 17+
- Gradle (включён в проект как wrapper)
- **Для офлайн режима**: Ollama (https://ollama.ai)
- **Для облачного режима**: API ключ OpenRouter (опционально)

## Запуск

```bash
# Windows
gradlew.bat run --console=plain

# Linux/macOS  
./gradlew run --console=plain
```

Приложение автоматически определит доступный режим:
- Если Ollama доступен → **офлайн режим** (локальная модель)
- Если Ollama недоступен → **облачный режим** (OpenRouter, требует API ключ)

## Использование

### Офлайн режим (Ollama)

```
╔══════════════════════════════════════════════════════════════╗
║        🦙 Ollama Agent - Офлайн терминальный чат 🦙          ║
╠══════════════════════════════════════════════════════════════╣
║  Режим: Офлайн (локальная модель)                            ║
║  Модель работает полностью локально, без интернета          ║
╠══════════════════════════════════════════════════════════════╣
║  Команды:                                                    ║
║    /exit            - выход из программы                     ║
║    /clear           - очистить историю разговора             ║
║    /help            - показать справку                       ║
║    /models          - показать список доступных моделей       ║
║    /running         - показать запущенные модели в памяти    ║
╠══════════════════════════════════════════════════════════════╣
║  💡 Примеры запросов:                                        ║
║    "Привет! Расскажи о себе"                                 ║
║    "Что такое Kotlin?"                                       ║
║    "Напиши функцию для сортировки массива"                   ║
╚══════════════════════════════════════════════════════════════╝
```

### Облачный режим (OpenRouter)

```
╔══════════════════════════════════════════════════════════════╗
║         🤖 OpenRouter Agent - Терминальный чат 🤖            ║
╠══════════════════════════════════════════════════════════════╣
║  Команды:                                                    ║
║    /exit            - выход из программы                     ║
║    /clear           - очистить историю разговора             ║
║    /help            - показать справку                       ║
║    /help <вопрос>   - поиск в документации проекта (RAG)     ║
║    /tools           - переключить отправку инструментов      ║
╚══════════════════════════════════════════════════════════════╝
```

### Примеры запросов

- "Сколько будет 25 * 4?"
- "Который сейчас час?"
- "Сгенерируй случайное число от 1 до 100"
- "Найди информацию о Kotlin"
- "На какой ветке я сейчас?" - использует Git MCP сервер
- "Покажи последние коммиты"
- "Какая погода в Москве?"
- **"Найди на устройстве: Kotlin programming"** - запустит Android эмулятор и выполнит поиск

### Помощь по проекту (RAG)

Команда `/help <вопрос>` использует RAG (Retrieval-Augmented Generation) для поиска ответов в документации проекта:

```
/help как настроить MCP сервер?
/help какие правила именования в Kotlin?
/help API для работы с Notion
/help схема базы данных
```

Система автоматически:
1. Генерирует эмбеддинг вашего вопроса
2. Ищет релевантные фрагменты в проиндексированной документации
3. Добавляет Git контекст (текущая ветка, измененные файлы)
4. Формирует ответ на основе найденной информации

## Настройка Android эмулятора (опционально)

Для использования функции поиска на Android эмуляторе:

1. Установите Android SDK
2. Создайте AVD (Android Virtual Device) через Android Studio
3. Добавьте в `local.properties`:
   ```properties
   ANDROID_SDK_PATH=C:\Users\YourName\AppData\Local\Android\Sdk
   ```
   Или через переменную окружения:
   ```bash
   set ANDROID_SDK_PATH=C:\Users\YourName\AppData\Local\Android\Sdk
   ```

Подробнее см. `docs/TESTING_GUIDE.md`

## Новые возможности

### 🦙 Офлайн режим (Ollama)
- **Автоматическое определение** доступности Ollama
- **Работа без интернета** - все вычисления локально
- Поддержка множества моделей (llama3.2, mistral, codellama и др.)
- Управление моделями через команды `/models` и `/running`
- Настройка через переменные окружения:
  - `OLLAMA_MODEL` - выбор модели (по умолчанию: llama3.2)
  - `OLLAMA_BASE_URL` - URL Ollama API (по умолчанию: http://localhost:11434/api)

### RAG (Retrieval-Augmented Generation)
- Автоматическая индексация документации проекта
- Семантический поиск по README, API docs, схемам данных
- Интеллектуальные ответы на основе документации
- Команда `/help <вопрос>` для поиска информации

### Git Integration (MCP)
- Получение текущей ветки (`get_current_branch`)
- Статус репозитория (`get_git_status`)  
- Список измененных файлов (`get_open_files`)
- История коммитов (`get_recent_commits`)
- Git контекст автоматически добавляется в RAG поиск

### MCP stdio Transport (Stage 2)
- Поддержка stdio транспорта для MCP протокола
- Неблокирующий I/O с использованием Kotlin Coroutines
- Конфигурируемый выбор транспорта (HTTP/stdio)

### Android Emulator Agent (Stage 3)
- Автоматический запуск Android эмулятора
- Поиск на устройстве через Chrome браузер
- Определение намерений пользователя для автоматизации

## Структура проекта

```
src/main/kotlin/org/example/
├── Main.kt                         # Точка входа
├── config/
│   ├── AppConfig.kt               # Загрузка конфигурации
│   └── OpenRouterConfig.kt        # Константы API
├── ui/
│   └── ConsoleUI.kt               # Терминальный интерфейс
├── agent/
│   ├── OpenRouterAgent.kt         # AI агент с tool calling
│   └── android/                   # Android эмулятор агент
│       ├── DeviceSearchExecutor.kt
│       ├── AdbCommandExecutor.kt
│       ├── AndroidEmulatorController.kt
│       └── DeviceSearchService.kt
├── client/
│   └── OpenRouterClient.kt        # HTTP клиент
├── mcp/
│   ├── McpClient.kt              # MCP клиент
│   ├── McpModels.kt              # MCP модели
│   ├── transport/                # Транспорты MCP
│   │   ├── Transport.kt
│   │   ├── HttpTransport.kt
│   │   └── StdioTransport.kt
│   └── server/                    # MCP серверы
│       ├── NotionMcpServer.kt
│       ├── WeatherMcpServer.kt
│       └── StdioMcpServer.kt
├── models/
│   ├── OpenRouterModels.kt        # Модели API
│   └── Models.kt                  # Общие модели
└── tools/
    └── OpenRouterTools.kt         # Инструменты агента
```

## Документация

### Внутренняя документация проекта
- [API Reference](docs/API_REFERENCE.md) - справочник по API компонентов
- [Style Guide](docs/STYLE_GUIDE.md) - руководство по стилю кода Kotlin
- [Data Schema](docs/DATA_SCHEMA.md) - схемы данных и модели
- [RAG Integration](docs/RAG_INTEGRATION.md) - интеграция RAG
- [Testing Guide](docs/TESTING_GUIDE.md) - руководство по тестированию

### Внешние ресурсы
- [OpenRouter API Documentation](https://openrouter.ai/docs/api/api-reference/responses/create-responses)
- Endpoint: `POST https://openrouter.ai/api/v1/responses`
