# Быстрый старт тестирования Project Task API

Краткая инструкция для быстрой проверки работоспособности сервиса.

## Шаг 1: Запуск приложения

```bash
gradlew.bat run --console=plain
```

Дождитесь сообщения:
```
✅ Project Task API Server: http://localhost:8084
```

## Шаг 2: Автоматическое тестирование (PowerShell)

Запустите скрипт автоматического тестирования:

```powershell
.\test_project_api.ps1
```

Скрипт выполнит базовые тесты и покажет результаты.

## Шаг 3: Ручное тестирование (минимальный набор)

### 1. Проверка доступности
```bash
curl http://localhost:8084/api/health
```

**Ожидаемый результат:**
```json
{"status":"ok","service":"Project Task API"}
```

### 2. Создание задачи
```bash
curl -X POST http://localhost:8084/api/tasks ^
  -H "Content-Type: application/json" ^
  -d "{\"title\":\"Тестовая задача\",\"priority\":\"HIGH\"}"
```

**Сохраните `id` из ответа для следующих тестов.**

### 3. Получение задачи
```bash
curl http://localhost:8084/api/tasks/<ID_ЗАДАЧИ>
```

### 4. Получение списка задач
```bash
curl http://localhost:8084/api/tasks
```

### 5. Обновление задачи
```bash
curl -X PUT http://localhost:8084/api/tasks/<ID_ЗАДАЧИ> ^
  -H "Content-Type: application/json" ^
  -d "{\"status\":\"IN_PROGRESS\"}"
```

### 6. Статус проекта
```bash
curl http://localhost:8084/api/project/status
```

### 7. Удаление задачи
```bash
curl -X DELETE http://localhost:8084/api/tasks/<ID_ЗАДАЧИ>
```

## Что проверить

✅ Все запросы возвращают корректные HTTP статусы:
- `200 OK` - успешный GET/PUT
- `201 Created` - успешный POST
- `204 No Content` - успешный DELETE
- `404 Not Found` - ресурс не найден

✅ JSON ответы валидны и содержат ожидаемые поля

✅ Данные сохраняются и извлекаются корректно

✅ Фильтры работают (попробуйте `?priority=HIGH`)

## Полное тестирование

Для детального тестирования всех функций используйте:
- **План тестирования:** `docs/PROJECT_TASK_API_TESTING_PLAN.md`
- **Документация API:** `docs/PROJECT_TASK_API.md`

## Решение проблем

**Сервис не отвечает:**
- Проверьте, что приложение запущено
- Проверьте, что порт 8084 свободен
- Проверьте логи приложения

**Ошибки базы данных:**
- Убедитесь, что файл `project_tasks.db` может быть создан
- Проверьте права доступа к директории проекта

**Ошибки валидации:**
- Убедитесь, что JSON валиден
- Проверьте заголовок `Content-Type: application/json`
