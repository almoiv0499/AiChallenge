# Настройка переменных окружения в Railway

## Проблема

Если приложение на Railway выдает ошибку:
```
❌ API ключ OPENROUTER_API_KEY не найден!
```

Это означает, что переменные окружения не установлены или не доступны приложению.

## Решения

### Способ 1: Установка через Railway Dashboard (Рекомендуется)

1. Откройте [Railway Dashboard](https://railway.app/)
2. Выберите ваш проект
3. Выберите сервис
4. Перейдите в раздел **Variables**
5. Добавьте необходимые переменные:
   - `OPENROUTER_API_KEY` - ваш ключ OpenRouter API
   - `NOTION_API_KEY` - (опционально) ключ Notion API
   - `WEATHER_API_KEY` - (опционально) ключ Weather API

### Способ 2: Установка через Railway CLI

```bash
# Установите Railway CLI
curl -fsSL https://railway.app/install.sh | sh

# Войдите в Railway
railway login

# Свяжите проект
railway link

# Установите переменные окружения
railway variables set OPENROUTER_API_KEY=ваш_ключ
railway variables set NOTION_API_KEY=ваш_ключ
railway variables set WEATHER_API_KEY=ваш_ключ
```

### Способ 3: Через GitHub Actions (Автоматически)

Workflow автоматически устанавливает переменные из GitHub Secrets при деплое.

**Убедитесь, что в GitHub Secrets настроены:**
- `RAILWAY_TOKEN` - API токен Railway
- `RAILWAY_PROJECT_ID` - ID проекта
- `RAILWAY_SERVICE_ID` - ID сервиса
- `OPENROUTER_API_KEY` - ключ OpenRouter API
- `NOTION_API_KEY` - (опционально) ключ Notion API
- `WEATHER_API_KEY` - (опционально) ключ Weather API

### Способ 4: Через railway.toml (Для локальной разработки)

Создайте файл `railway.toml` в корне проекта:

```toml
[build]
builder = "DOCKERFILE"
dockerfilePath = "Dockerfile"

[deploy]
startCommand = "java $JAVA_OPTS -jar app.jar"
restartPolicyType = "ON_FAILURE"
restartPolicyMaxRetries = 10
```

Переменные окружения лучше устанавливать через Dashboard или CLI, а не в файле (из соображений безопасности).

## Проверка переменных окружения

После установки переменных проверьте их наличие:

```bash
# Через Railway CLI
railway variables

# Или через Dashboard
# Variables -> Service Variables
```

## Важные замечания

1. **Переменные окружения применяются при следующем деплое** - после установки переменных нужно перезапустить сервис или сделать новый деплой.

2. **Секреты должны быть установлены в GitHub** - если используете автоматический деплой через GitHub Actions, убедитесь, что все секреты настроены.

3. **Проверьте права доступа** - убедитесь, что Railway токен имеет права на установку переменных окружения.

4. **Переменные чувствительны к регистру** - используйте точные имена: `OPENROUTER_API_KEY`, а не `openrouter_api_key`.

## Устранение проблем

### Переменные не применяются

1. Проверьте, что переменные установлены в правильном сервисе
2. Перезапустите сервис в Railway Dashboard
3. Проверьте логи деплоя в GitHub Actions

### Ошибка при установке через CLI

```bash
# Убедитесь, что вы авторизованы
railway whoami

# Проверьте связь с проектом
railway status

# Попробуйте установить переменную с явным указанием сервиса
railway variables set OPENROUTER_API_KEY=ваш_ключ --service <SERVICE_ID>
```

### Переменные видны, но приложение их не читает

1. Убедитесь, что имя переменной точно совпадает (регистр важен)
2. Проверьте, что переменные установлены для правильного сервиса
3. Перезапустите сервис после установки переменных

## Рекомендуемый порядок действий

1. ✅ Установите переменные через Railway Dashboard
2. ✅ Убедитесь, что все секреты настроены в GitHub
3. ✅ Запустите новый деплой через GitHub Actions
4. ✅ Проверьте логи приложения в Railway Dashboard

---

**Примечание:** После установки переменных окружения через любой способ, Railway автоматически перезапустит сервис с новыми переменными.
