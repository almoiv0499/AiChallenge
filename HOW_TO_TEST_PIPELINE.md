# 🧪 Как проверить работу пайплайнов

## Быстрый способ

### 1. Обновите версию

Измените файл `VERSION`:

```bash
# Измените версию
echo "1.0.1" > VERSION

# Закоммитьте и запушьте
git add VERSION
git commit -m "Test: проверка пайплайна деплоя"
git push origin main
```

Это автоматически запустит GitHub Actions workflow `Deploy to Railway`.

### 2. Проверьте GitHub Actions

1. Откройте вкладку **Actions** в вашем GitHub репозитории
2. Найдите запущенный workflow `Deploy to Railway`
3. Проверьте логи выполнения каждого шага:
   - ✅ Build application
   - ✅ Run tests
   - ✅ Build and push Docker image
   - ✅ Deploy to Railway

### 3. Проверьте деплой

После успешного деплоя проверьте endpoints:

```bash
# Замените на ваш URL Railway
DEPLOYMENT_URL="https://ваш-проект.railway.app"

# Health Check
curl $DEPLOYMENT_URL/api/health

# Deployment Test
curl $DEPLOYMENT_URL/api/deployment/test

# Version Info
curl $DEPLOYMENT_URL/api/version
```

## Автоматическое тестирование

### Использование скриптов

#### Linux/macOS
```bash
chmod +x scripts/test-deployment.sh
./scripts/test-deployment.sh https://ваш-проект.railway.app
```

#### Windows PowerShell
```powershell
.\scripts\test-deployment.ps1 -Url "https://ваш-проект.railway.app"
```

### GitHub Actions Workflow для тестирования

1. Перейдите в **Actions** -> **Test Deployment**
2. Нажмите **Run workflow**
3. Укажите URL вашего деплоя
4. Запустите workflow

Workflow автоматически запускается каждый день в 2:00 UTC.

## Что проверяется

### ✅ Endpoints

1. **`/api/health`** - базовая работоспособность
   ```json
   {
     "status": "ok",
     "service": "Project Task API"
   }
   ```

2. **`/api/deployment/test`** - информация о деплое
   ```json
   {
     "status": "success",
     "message": "Deployment test endpoint is working!",
     "version": "1.0.1",
     "timestamp": 1234567890,
     "environment": "production",
     "deployment": {
       "platform": "Railway",
       "status": "active"
     }
   }
   ```

3. **`/api/version`** - версия приложения
   ```json
   {
     "version": "1.0.1",
     "application": "OpenRouter Agent",
     "deployment": "Railway"
   }
   ```

## Файлы для проверки

### Основные файлы

- **`VERSION`** - версия приложения (обновляйте для триггера деплоя)
- **`CHANGELOG.md`** - история изменений
- **`TEST_DEPLOYMENT.md`** - подробная документация по тестированию

### Скрипты

- **`scripts/test-deployment.sh`** - скрипт для Linux/macOS
- **`scripts/test-deployment.ps1`** - скрипт для Windows

### Workflows

- **`.github/workflows/deploy.yml`** - основной пайплайн деплоя
- **`.github/workflows/test-deployment.yml`** - пайплайн для тестирования

## Проверка локально

Если хотите проверить endpoints локально:

```bash
# Запустите приложение
./gradlew run

# В другом терминале проверьте endpoints
curl http://localhost:8084/api/health
curl http://localhost:8084/api/deployment/test
curl http://localhost:8084/api/version
```

**Примечание:** Локально приложение запускается на порту 8084 (Project Task API Server).

## Устранение проблем

### Пайплайн не запускается

1. Проверьте, что вы пушите в ветку `main` или `master`
2. Убедитесь, что изменили файлы, которые не игнорируются (см. `paths-ignore` в workflow)
3. Проверьте, что workflow файл находится в `.github/workflows/`

### Деплой не работает

1. Проверьте, что все секреты настроены в GitHub:
   - `RAILWAY_TOKEN`
   - `RAILWAY_PROJECT_ID`
   - `RAILWAY_SERVICE_ID`

2. Проверьте логи в Railway Dashboard

3. Убедитесь, что Railway проект и сервис существуют

### Endpoints не отвечают

1. Проверьте, что приложение запущено в Railway
2. Проверьте логи в Railway Dashboard
3. Убедитесь, что порт правильный (Railway использует переменную `PORT`)

## Пример полного цикла

```bash
# 1. Обновить версию
echo "1.0.2" > VERSION

# 2. Обновить CHANGELOG
echo "## [1.0.2] - $(date +%Y-%m-%d)" >> CHANGELOG.md
echo "- Тестовая проверка пайплайна" >> CHANGELOG.md

# 3. Закоммитить
git add VERSION CHANGELOG.md
git commit -m "Test: проверка пайплайна v1.0.2"
git push origin main

# 4. Подождать завершения деплоя (проверить в GitHub Actions)

# 5. Проверить endpoints
curl https://ваш-проект.railway.app/api/deployment/test
```

---

**Совет:** Обновляйте `VERSION` файл при каждом релизе для отслеживания версий и автоматического триггера деплоя!
