# Руководство по автоматическому деплою на Railway

## Описание задачи

Этот проект реализует автоматический деплой Kotlin + Ktor приложения на платформу Railway (PaaS хостинг).

### Что делает система:

1. **Контейнеризация** - приложение упаковывается в Docker образ
2. **Автоматическая сборка** - при пуше в main/master ветку запускается CI/CD пайплайн
3. **Деплой на Railway** - автоматический деплой через Railway API
4. **Управление переменными окружения** - автоматическая установка необходимых ключей

## Структура решения

```
├── Dockerfile                    # Docker образ для контейнеризации
├── .github/workflows/
│   └── deploy.yml               # GitHub Actions workflow для CI/CD
└── src/main/kotlin/org/example/deployment/
    ├── RailwayClient.kt         # Клиент для Railway API
    ├── DeploymentService.kt     # Сервис управления деплоями
    └── DeployMain.kt            # Главная функция для ручного деплоя
```

## Предварительные требования

### 1. Railway аккаунт

1. Зарегистрируйтесь на [Railway](https://railway.app/)
2. Создайте новый проект
3. Получите API токен:
   - Откройте [Railway Dashboard](https://railway.app/account)
   - Перейдите в раздел "API Tokens"
   - Создайте новый токен и сохраните его

### 2. Настройка проекта на Railway

1. Создайте новый проект в Railway
2. Создайте сервис (например, из Docker образа)
3. Сохраните следующие ID:
   - **Project ID** - можно найти в URL проекта или через API
   - **Service ID** - можно найти в URL сервиса или через API

### 3. Настройка GitHub Secrets

В настройках репозитория GitHub (`Settings` -> `Secrets and variables` -> `Actions`) добавьте следующие секреты:

- `RAILWAY_TOKEN` - API токен Railway
- `RAILWAY_PROJECT_ID` - ID проекта в Railway
- `RAILWAY_SERVICE_ID` - ID сервиса в Railway
- `OPENROUTER_API_KEY` - (опционально) ключ OpenRouter API
- `NOTION_API_KEY` - (опционально) ключ Notion API
- `WEATHER_API_KEY` - (опционально) ключ Weather API

## Использование

### Автоматический деплой через GitHub Actions

Пайплайн запускается автоматически при:
- Пуше в ветки `main` или `master`
- Ручном запуске через `workflow_dispatch`

**Workflow выполняет:**
1. Сборку приложения (Gradle build)
2. Запуск тестов
3. Сборку Docker образа
4. Публикацию образа в GitHub Container Registry
5. Деплой на Railway с установкой переменных окружения

### Ручной деплой через CLI

#### Способ 1: Через Gradle задачу

```bash
./gradlew deployRailway \
  -PrailwayToken=ваш_токен \
  -PprojectId=ваш_project_id \
  -PserviceId=ваш_service_id
```

#### Способ 2: Через переменные окружения

```bash
export RAILWAY_TOKEN=ваш_токен
export RAILWAY_PROJECT_ID=ваш_project_id
export RAILWAY_SERVICE_ID=ваш_service_id

./gradlew deployRailway
```

#### Способ 3: Через JAR файл

```bash
# Сборка приложения
./gradlew build

# Запуск деплоя
java -jar build/libs/ваше-приложение.jar \
  --railway-token=ваш_токен \
  --project-id=ваш_project_id \
  --service-id=ваш_service_id
```

#### Способ 4: Через local.properties

Добавьте в `local.properties`:

```properties
RAILWAY_TOKEN=ваш_токен
RAILWAY_PROJECT_ID=ваш_project_id
RAILWAY_SERVICE_ID=ваш_service_id
OPENROUTER_API_KEY=ваш_ключ
NOTION_API_KEY=ваш_ключ
WEATHER_API_KEY=ваш_ключ
```

Затем просто запустите:

```bash
./gradlew deployRailway
```

## Проверка деплоя

После успешного деплоя:

1. Откройте [Railway Dashboard](https://railway.app/)
2. Выберите ваш проект
3. Перейдите в сервис
4. Проверьте логи деплоя
5. Убедитесь, что сервис запущен и доступен

### Health Check

Приложение автоматически проверяет доступность через health check endpoint:

```bash
curl http://ваш-домен.railway.app/health
```

## Устранение проблем

### Ошибка: "RAILWAY_TOKEN не указан"

Убедитесь, что:
- Токен установлен в GitHub Secrets (для автоматического деплоя)
- Токен передается через переменную окружения или параметр (для ручного деплоя)

### Ошибка: "Проект не найден"

Проверьте:
- Правильность `RAILWAY_PROJECT_ID`
- Права доступа токена к проекту

### Ошибка: "Сервис не найден"

Проверьте:
- Правильность `RAILWAY_SERVICE_ID`
- Что сервис существует в указанном проекте

### Деплой не завершается

- Проверьте логи в Railway Dashboard
- Убедитесь, что все переменные окружения установлены корректно
- Проверьте, что Docker образ собран успешно

### Ошибка сборки Docker образа

Убедитесь, что:
- Dockerfile находится в корне проекта
- Все зависимости указаны в `build.gradle.kts`
- Приложение компилируется без ошибок

## Railway API Ограничения

⚠️ **Важно:** Railway API имеет ограничения:
- Rate limiting: ~100 запросов в минуту
- Токен действителен в течение 90 дней (рекомендуется обновлять)

## Мониторинг

После деплоя рекомендуется настроить:
- Мониторинг доступности через Railway Metrics
- Логирование через Railway Logs
- Алерты на критические ошибки

## Альтернативные платформы

Это решение можно адаптировать для других платформ:
- **Heroku** - через Heroku CLI API
- **Render** - через Render API
- **Fly.io** - через Fly API
- **DigitalOcean App Platform** - через DO API

Для адаптации нужно изменить `RailwayClient.kt` под API выбранной платформы.

## Безопасность

⚠️ **Важные рекомендации по безопасности:**
- Никогда не коммитьте токены в репозиторий
- Используйте GitHub Secrets для хранения чувствительных данных
- Регулярно обновляйте API токены
- Используйте минимальные необходимые права доступа для токенов

## Примеры использования

### Полный цикл деплоя с нуля

```bash
# 1. Клонирование репозитория
git clone https://github.com/ваш-username/ваш-репозиторий.git
cd ваш-репозиторий

# 2. Настройка local.properties
cat > local.properties << EOF
RAILWAY_TOKEN=ваш_токен
RAILWAY_PROJECT_ID=ваш_project_id
RAILWAY_SERVICE_ID=ваш_service_id
OPENROUTER_API_KEY=ваш_ключ
EOF

# 3. Локальный деплой
./gradlew deployRailway

# 4. Или автоматический деплой через push
git add .
git commit -m "Setup deployment"
git push origin main  # Запустит GitHub Actions workflow
```

## Дополнительные ресурсы

- [Railway Documentation](https://docs.railway.app/)
- [Railway API Reference](https://docs.railway.app/develop/api)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Docker Documentation](https://docs.docker.com/)

---

**Примечание:** Этот пайплайн может не работать полностью из-за необходимости настройки ключей и токенов. Убедитесь, что все секреты настроены правильно перед использованием.
