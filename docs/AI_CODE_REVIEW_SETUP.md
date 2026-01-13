# 🤖 Настройка AI Code Review Pipeline

Этот документ описывает, как настроить автоматический AI Code Review для вашего репозитория.

## Быстрый старт

### 1. Добавить секреты в GitHub

Перейдите в **Settings → Secrets and variables → Actions** и добавьте:

| Secret | Описание | Обязательно |
|--------|----------|-------------|
| `OPENROUTER_API_KEY` | API ключ OpenRouter | ✅ Да |

> **Примечание:** `GITHUB_TOKEN` предоставляется автоматически GitHub Actions.

### 2. Проверить workflow файл

Убедитесь, что файл `.github/workflows/ai-code-review.yml` присутствует в репозитории.

### 3. Создать Pull Request

Создайте PR и наблюдайте за автоматическим review в комментариях!

---

## Структура компонентов

```
.github/
└── workflows/
    └── ai-code-review.yml    # GitHub Actions workflow

src/main/kotlin/org/example/review/
├── CodeReviewRunner.kt       # Точка входа для CI
├── CodeReviewService.kt      # Логика review с LLM
├── CodeReviewTest.kt         # Тестирование pipeline
└── GitHubClient.kt           # Клиент GitHub API
```

---

## Конфигурация workflow

### Триггеры

По умолчанию workflow запускается при:
- Открытии PR (`opened`)
- Синхронизации (новые коммиты) (`synchronize`)
- Повторном открытии (`reopened`)
- Готовности к review (`ready_for_review`)

```yaml
on:
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review]
```

### Изменение модели LLM

В файле `CodeReviewService.kt` измените модель:

```kotlin
// Текущая (баланс скорости и качества)
put("model", "anthropic/claude-sonnet-4")

// Альтернативы:
put("model", "anthropic/claude-opus-4")      // Максимальное качество
put("model", "openai/gpt-4-turbo")           // OpenAI альтернатива
put("model", "deepseek/deepseek-v3")         // Бюджетный вариант
```

### Настройка RAG контекста

RAG автоматически использует документацию из:
- `docs/*.md`
- `README.md`

Для индексации новых документов:
```bash
./gradlew runIndexDocs
```

---

## Локальное тестирование

### 1. Настройка окружения

```bash
# Добавить API ключ в local.properties
echo "OPENROUTER_API_KEY=sk-or-v1-ваш_ключ" >> local.properties

# Или через переменные окружения
export OPENROUTER_API_KEY="sk-or-v1-ваш_ключ"
export GITHUB_TOKEN="ghp_ваш_токен"
```

### 2. Тестирование компонентов

```bash
# Тест RAG
./gradlew runIndexDocs
./gradlew runSearchTest

# Тест MCP
./gradlew runGitMcpTest

# Полный тест pipeline
./gradlew runCodeReviewTest
```

### 3. Локальный запуск для реального PR

```bash
export GITHUB_TOKEN="ghp_ваш_токен"

./gradlew runCodeReview --args="--pr=123 --repo=owner/repo"
```

---

## Формат вывода

### JSON результат (review-output.json)

```json
{
  "summary": "PR добавляет аутентификацию. Найдена одна проблема безопасности.",
  "verdict": "request_changes",
  "issues": [
    {
      "severity": "security",
      "file": "src/auth/AuthService.kt",
      "line": 47,
      "title": "SQL Injection vulnerability",
      "description": "User input is concatenated directly into SQL query",
      "suggestion": "Use parameterized queries instead"
    }
  ],
  "positive_notes": [
    "Good separation of concerns",
    "Comprehensive error handling"
  ]
}
```

### PR комментарий

```markdown
## 🤖 AI Code Review

**Verdict:** 🔴 REQUEST_CHANGES

### Summary
PR добавляет аутентификацию. Найдена одна проблема безопасности.

### Issues Found (1)
| Severity | File | Description |
|----------|------|-------------|
| 🔒 security | `AuthService.kt:47` | SQL Injection vulnerability |

### ✨ Positive Notes
- Good separation of concerns
- Comprehensive error handling
```

---

## Troubleshooting

### Проблема: "OPENROUTER_API_KEY не найден"

**Решение:** Добавьте секрет в GitHub:
1. Settings → Secrets and variables → Actions
2. New repository secret
3. Name: `OPENROUTER_API_KEY`
4. Value: ваш ключ от OpenRouter

### Проблема: "GITHUB_TOKEN не найден"

**Решение:** Этот токен должен предоставляться автоматически. Проверьте права workflow:

```yaml
permissions:
  contents: read
  pull-requests: write
```

### Проблема: "RAG индекс пуст"

**Решение:** Индексация выполняется автоматически в CI, но для ускорения можно закэшировать:

```yaml
- name: Restore RAG Index Cache
  uses: actions/cache@v4
  with:
    path: document_index.db
    key: rag-index-${{ hashFiles('docs/**/*.md') }}
```

### Проблема: Workflow не запускается

**Проверьте:**
1. PR не в статусе "Draft"
2. Workflow файл в правильной директории
3. YAML синтаксис корректен

---

## Расширенная конфигурация

### Блокирование PR при критических issues

Раскомментируйте в workflow:

```yaml
if [ "$CRITICAL_COUNT" -gt 0 ]; then
  echo "::error::Found $CRITICAL_COUNT critical/security issues"
  exit 1  # Раскомментируйте для блокировки
fi
```

### Фильтрация файлов

В `CodeReviewRunner.kt` измените фильтр:

```kotlin
val relevantFiles = files
    .filter { it.status != "removed" }
    .filter { 
        it.filename.endsWith(".kt") || 
        it.filename.endsWith(".java") ||
        it.filename.endsWith(".ts") ||
        it.filename.endsWith(".py")  // Добавить Python
    }
    .take(10)  // Увеличить лимит файлов
```

### Изменение severity для блокировки

```kotlin
// В workflow или CodeReviewRunner
val blockingSeverities = setOf("critical", "security")
val shouldBlock = result.issues.any { it.severity in blockingSeverities }
```

---

## Стоимость и лимиты

### Примерная стоимость за review

| Модель | Input (100K tokens) | Output (2K tokens) | Итого |
|--------|---------------------|--------------------| ------|
| Claude Sonnet 4 | ~$0.30 | ~$0.03 | ~$0.33 |
| Claude Opus 4 | ~$1.50 | ~$0.15 | ~$1.65 |
| GPT-4 Turbo | ~$1.00 | ~$0.06 | ~$1.06 |

### Рекомендации по оптимизации

1. **Кэширование RAG индекса** — экономит ~$0.01 на embedding вызовах
2. **Ограничение размера diff** — установлен лимит 50K символов
3. **Фильтрация файлов** — анализируются только исходные файлы кода

---

## Чеклист готовности

- [ ] `OPENROUTER_API_KEY` добавлен в GitHub Secrets
- [ ] `.github/workflows/ai-code-review.yml` присутствует
- [ ] Документация в `docs/` актуальна (для RAG)
- [ ] Локальные тесты проходят (`runCodeReviewTest`)
- [ ] Тестовый PR создан и review работает
