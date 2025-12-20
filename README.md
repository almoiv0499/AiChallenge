# OpenRouter Agent

AI-агент на Kotlin с использованием [OpenRouter API](https://openrouter.ai/docs/api/api-reference/responses/create-responses).

## Функции агента

- **get_current_time** - получение текущего времени
- **calculator** - математические вычисления (add, subtract, multiply, divide, power, sqrt)
- **search** - поиск информации (эмуляция)
- **random_number** - генерация случайного числа в заданном диапазоне

## Требования

- JDK 17+
- Gradle (включён в проект как wrapper)
- API ключ OpenRouter

## Настройка

### 1. Получите API ключ

Зарегистрируйтесь на [OpenRouter](https://openrouter.ai/) и получите API ключ.

### 2. Установите ключ

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

## Запуск

```bash
# Windows
gradlew.bat run --console=plain

# Linux/macOS  
./gradlew run --console=plain
```

## Использование

```
╔══════════════════════════════════════════════════════════════╗
║         🤖 OpenRouter Agent - Терминальный чат 🤖            ║
╠══════════════════════════════════════════════════════════════╣
║  Команды:                                                    ║
║    /exit  - выход из программы                               ║
║    /clear - очистить историю разговора                       ║
║    /help  - показать справку                                 ║
╚══════════════════════════════════════════════════════════════╝
```

### Примеры запросов

- "Сколько будет 25 * 4?"
- "Который сейчас час?"
- "Сгенерируй случайное число от 1 до 100"
- "Найди информацию о Kotlin"
- **"Найди на устройстве: Kotlin programming"** - запустит Android эмулятор и выполнит поиск
- **"Find on device: Android development"** - поиск на эмуляторе (английский)

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

## API Reference

- [OpenRouter API Documentation](https://openrouter.ai/docs/api/api-reference/responses/create-responses)
- Endpoint: `POST https://openrouter.ai/api/v1/responses`
