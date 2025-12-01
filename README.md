# GigaChat Agent

Простой AI-агент на Kotlin, который работает с GigaChat API от Сбера.

## Функции агента

- **get_current_time** - получение текущего времени
- **calculator** - математические вычисления (add, subtract, multiply, divide, power, sqrt)
- **search** - поиск информации (эмуляция)
- **random_number** - генерация случайного числа в заданном диапазоне

## Требования

- JDK 17+ (установите JAVA_HOME)
- Gradle (включён в проект как wrapper)
- Ключ авторизации GigaChat API

## Настройка

### 1. Получите ключ авторизации

Зарегистрируйтесь и получите ключ в [личном кабинете GigaChat](https://developers.sber.ru/portal/products/gigachat-api).

### 2. Установите переменную окружения

**Windows (CMD):**
```cmd
set GIGACHAT_AUTH_KEY=ваш_ключ_авторизации
```

**Windows (PowerShell):**
```powershell
$env:GIGACHAT_AUTH_KEY="ваш_ключ_авторизации"
```

**Linux/macOS:**
```bash
export GIGACHAT_AUTH_KEY=ваш_ключ_авторизации
```

**IntelliJ IDEA:**
1. Edit Configurations → Main
2. Environment variables → добавьте `GIGACHAT_AUTH_KEY=ваш_ключ`

## Запуск

### Windows (CMD)
```cmd
set GIGACHAT_AUTH_KEY=ваш_ключ
gradlew.bat run --console=plain
```

### Windows (PowerShell)
```powershell
$env:GIGACHAT_AUTH_KEY="ваш_ключ"; .\gradlew.bat run --console=plain
```

### Linux/macOS
```bash
GIGACHAT_AUTH_KEY=ваш_ключ ./gradlew run --console=plain
```

## Использование

После запуска вы увидите терминальный интерфейс чата:

```
╔══════════════════════════════════════════════════════════════╗
║           🤖 GigaChat Agent - Терминальный чат 🤖            ║
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
- "Вычисли квадратный корень из 144"

## Структура проекта

```
src/main/kotlin/org/example/
├── Main.kt                          # Точка входа и терминальный интерфейс
├── agent/
│   └── GigaChatAgent.kt            # AI агент с поддержкой function calling
├── client/
│   └── GigaChatClient.kt           # HTTP клиент для GigaChat API
├── models/
│   └── GigaChatModels.kt           # Модели данных API
└── tools/
    └── GigaChatTools.kt            # Инструменты агента
```

## API Reference

Агент использует GigaChat API:
- Авторизация: `https://ngw.devices.sberbank.ru:9443/api/v2/oauth`
- Chat Completions: `https://gigachat.devices.sberbank.ru/api/v1/chat/completions`

