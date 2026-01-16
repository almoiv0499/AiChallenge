# Android Implementation Guide

## Описание проекта

Android-приложение для чата с AI ассистентом, построенное с использованием современного стека:

- **Архитектура:** MVVM (Model-View-ViewModel)
- **UI:** Jetpack Compose
- **DI:** Koin
- **Network:** Ktor
- **Database:** Room
- **Reactive:** Flow / StateFlow
- **Async:** Coroutines

## Структура проекта

```
android/
├── app/                          # Основной модуль приложения
│   ├── src/main/java/org/example/aichallenge/
│   │   ├── MainActivity.kt       # Главная Activity
│   │   ├── di/                   # Dependency Injection (Koin)
│   │   └── ui/                   # UI слой (Compose)
│   │       ├── screen/           # Экраны
│   │       ├── components/       # UI компоненты
│   │       ├── viewmodel/        # ViewModels
│   │       └── theme/            # Тема приложения
│   └── build.gradle.kts
│
└── chatAI/                       # Модуль бизнес-логики
    ├── src/main/java/org/example/chatai/
    │   ├── data/                 # Data слой
    │   │   ├── model/            # Модели данных
    │   │   ├── local/            # Room (DAO, Database)
    │   │   └── repository/       # Репозитории
    │   ├── domain/               # Domain слой
    │   │   ├── api/              # API сервисы (Ktor)
    │   │   └── usecase/          # Use Cases
    └── build.gradle.kts
```

## Архитектурные решения

### 1. Модульная архитектура

**Модуль `chatAI`:**
- Инкапсулирует всю бизнес-логику чата
- Не зависит от Android Framework (кроме Room)
- Может быть переиспользован в других проектах

**Модуль `app`:**
- Содержит UI и Android-специфичный код
- Зависит от модуля `chatAI`
- Использует Jetpack Compose для UI

### 2. MVVM Pattern

```
View (Compose) <-- StateFlow --> ViewModel <-- UseCase --> Repository <-- DataSource
```

- **View:** Jetpack Compose UI компоненты
- **ViewModel:** Управляет состоянием UI, использует UseCase
- **UseCase:** Инкапсулирует бизнес-логику
- **Repository:** Абстракция над источниками данных
- **DataSource:** Room Database, API Service

### 3. Reactive Programming

- **StateFlow** для состояния UI (ViewModel → View)
- **Flow** для реактивного получения данных из Room
- **Coroutines** для асинхронных операций

### 4. Dependency Injection (Koin)

Все зависимости регистрируются в `AppModule`:
- Database и DAO
- Repository
- API Service
- Use Cases
- ViewModels

## База данных (Room)

### Схема данных

```kotlin
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: MessageRole,      // system, user, assistant, tool, mcp
    val content: String,
    val timestamp: Long
)
```

### Роли сообщений

- **SYSTEM** - системные сообщения (промпты)
- **USER** - сообщения пользователя
- **ASSISTANT** - ответы AI ассистента
- **TOOL** - результаты вызова инструментов
- **MCP** - сообщения от MCP серверов

### Восстановление истории

При запуске приложения история автоматически загружается из Room через Flow:

```kotlin
fun getAllMessages(): Flow<List<ChatMessage>>
```

UI автоматически обновляется при изменении данных в базе.

## API Integration (Ktor)

### OpenRouter API Service

```kotlin
class OpenRouterApiService(
    private val apiKey: String,
    private val baseUrl: String = "https://openrouter.ai/api/v1"
)
```

Использует:
- Ktor Android engine
- JSON serialization
- Logging interceptor
- Content negotiation

## Сборка проекта

### Локальная сборка

```bash
# Linux/macOS
./gradlew :app:assembleDebug

# Windows
gradlew.bat :app:assembleDebug
```

APK будет находиться в `android/app/build/outputs/apk/debug/app-debug.apk`

### Установка на устройство

```bash
./gradlew :app:installDebug
```

## CI/CD (GitHub Actions)

### Workflow

1. **Checkout code** - получение кода из репозитория
2. **Set up JDK 17** - настройка Java окружения
3. **Build debug APK** - сборка приложения
4. **Upload APK artifact** - публикация APK
5. **Create ZIP archive** - создание ZIP архива
6. **Upload ZIP artifact** - публикация ZIP

### Скачивание APK

После успешного запуска workflow:

1. Перейдите в раздел **Actions** в GitHub
2. Выберите последний успешный run
3. В разделе **Artifacts** скачайте:
   - `app-debug-apk` - APK файл
   - `app-debug-zip` - ZIP архив с APK

## Настройка API ключа

### Локально

1. Создайте файл `android/app/src/main/res/values/api_keys.xml` (не коммитьте его):
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="openrouter_api_key">sk-or-v1-ваш_ключ</string>
</resources>
```

2. Или установите переменную окружения:
```bash
export OPENROUTER_API_KEY=sk-or-v1-ваш_ключ
```

### В CI/CD

Добавьте секрет в GitHub:
1. Settings → Secrets and variables → Actions
2. New repository secret
3. Name: `OPENROUTER_API_KEY`
4. Value: ваш API ключ

## Особенности реализации

### 1. Модульность

Модуль `chatAI` полностью независим от Android UI, что позволяет:
- Легко тестировать бизнес-логику
- Переиспользовать код в других проектах
- Изолировать изменения в UI от логики

### 2. Реактивность

Использование Flow и StateFlow обеспечивает:
- Автоматическое обновление UI при изменении данных
- Нет необходимости в ручном обновлении списка сообщений
- Поддержка lifecycle-aware подписок

### 3. Обработка ошибок

- Используется `Result<T>` для обработки ошибок в UseCase
- ViewModel отображает ошибки через StateFlow
- UI показывает ошибки через Snackbar

### 4. Восстановление истории

- История автоматически сохраняется в Room при каждом сообщении
- При перезапуске приложения история загружается из базы
- Используется Flow для реактивного обновления UI

## Требования

- **minSdk:** 24 (Android 7.0)
- **targetSdk:** 34 (Android 14)
- **Java:** 17
- **Kotlin:** 2.0.0
- **Gradle:** 8.5+

## Зависимости

### Основные библиотеки

- **Jetpack Compose:** 2024.06.00
- **Room:** 2.6.1
- **Ktor:** 2.3.12
- **Koin:** 3.5.3
- **Coroutines:** 1.7.3
- **Serialization:** 1.7.1

## Дальнейшее развитие

1. **Добавление инструментов (tools):** интеграция MCP серверов
2. **Поддержка медиа:** отправка изображений и файлов
3. **Офлайн режим:** кэширование ответов
4. **Темная тема:** улучшение оформления
5. **Настройки:** выбор модели, температуры и т.д.
