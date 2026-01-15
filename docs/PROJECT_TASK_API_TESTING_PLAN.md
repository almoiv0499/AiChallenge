# План тестирования Project Task API

Пошаговый план для проверки работоспособности сервиса управления задачами проекта.

## Подготовка

### Шаг 1: Запуск приложения
1. Убедитесь, что все зависимости установлены
2. Запустите приложение:
   ```bash
   gradlew.bat run --console=plain
   ```
3. Дождитесь сообщения о запуске сервисов:
   ```
   ✅ Project Task API Server: http://localhost:8084
   ```

### Шаг 2: Проверка доступности сервиса
```bash
curl http://localhost:8084/api/health
```

**Ожидаемый результат:**
```json
{
  "status": "ok",
  "service": "Project Task API"
}
```

---

## Базовое тестирование CRUD операций

### Шаг 3: Создание задачи (POST /api/tasks)

**Тест 3.1: Создание простой задачи**
```bash
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Тестовая задача 1",
    "description": "Описание тестовой задачи",
    "priority": "MEDIUM"
  }'
```

**Проверьте:**
- ✅ Статус ответа: `201 Created`
- ✅ В ответе есть поле `id` (UUID)
- ✅ Статус задачи: `TODO`
- ✅ Приоритет: `MEDIUM`
- ✅ Поля `createdAt` и `updatedAt` заполнены

**Сохраните `id` задачи для следующих тестов.**

---

**Тест 3.2: Создание задачи с полными данными**
```bash
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Критическая задача",
    "description": "Срочная задача с дедлайном",
    "priority": "CRITICAL",
    "assignee": "developer@example.com",
    "dueDate": "2025-02-15",
    "tags": ["urgent", "backend"],
    "estimatedHours": 8.0,
    "milestone": "MVP",
    "epic": "Core Features"
  }'
```

**Проверьте:**
- ✅ Все поля сохранены корректно
- ✅ Приоритет: `CRITICAL`
- ✅ Теги: `["urgent", "backend"]`
- ✅ `estimatedHours`: `8.0`

---

**Тест 3.3: Создание задачи с зависимостями**
```bash
# Сначала создайте задачу-зависимость
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Задача-зависимость",
    "priority": "HIGH"
  }' > dependency_task.json

# Извлеките ID из ответа, затем создайте задачу с зависимостью
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Задача с зависимостью",
    "priority": "HIGH",
    "dependencies": ["<ID_ЗАВИСИМОСТИ>"]
  }'
```

**Проверьте:**
- ✅ Поле `dependencies` содержит ID зависимости

---

### Шаг 4: Получение задачи по ID (GET /api/tasks/{id})

```bash
curl http://localhost:8084/api/tasks/<ID_ЗАДАЧИ>
```

**Проверьте:**
- ✅ Статус ответа: `200 OK`
- ✅ Все поля задачи соответствуют созданным
- ✅ Формат дат: ISO 8601

**Тест 4.2: Получение несуществующей задачи**
```bash
curl http://localhost:8084/api/tasks/non-existent-id
```

**Проверьте:**
- ✅ Статус ответа: `404 Not Found`
- ✅ Есть сообщение об ошибке

---

### Шаг 5: Получение списка задач (GET /api/tasks)

**Тест 5.1: Получение всех задач**
```bash
curl "http://localhost:8084/api/tasks"
```

**Проверьте:**
- ✅ Статус ответа: `200 OK`
- ✅ Структура ответа:
  ```json
  {
    "tasks": [...],
    "total": <число>,
    "page": 1,
    "pageSize": 50
  }
  ```
- ✅ Все созданные задачи присутствуют в списке

---

**Тест 5.2: Фильтрация по статусу**
```bash
curl "http://localhost:8084/api/tasks?status=TODO"
```

**Проверьте:**
- ✅ Возвращаются только задачи со статусом `TODO`

**Тест 5.3: Фильтрация по приоритету**
```bash
curl "http://localhost:8084/api/tasks?priority=CRITICAL"
```

**Проверьте:**
- ✅ Возвращаются только задачи с приоритетом `CRITICAL`

**Тест 5.4: Фильтрация по нескольким статусам**
```bash
curl "http://localhost:8084/api/tasks?status=TODO&status=IN_PROGRESS"
```

**Проверьте:**
- ✅ Возвращаются задачи с обоими статусами

---

**Тест 5.5: Фильтрация по исполнителю**
```bash
curl "http://localhost:8084/api/tasks?assignee=developer@example.com"
```

**Проверьте:**
- ✅ Возвращаются только задачи назначенного исполнителя

---

**Тест 5.6: Фильтрация по тегам**
```bash
curl "http://localhost:8084/api/tasks?tags=urgent"
```

**Проверьте:**
- ✅ Возвращаются задачи с указанным тегом

---

**Тест 5.7: Поиск по тексту**
```bash
curl "http://localhost:8084/api/tasks?search=критическая"
```

**Проверьте:**
- ✅ Возвращаются задачи, содержащие "критическая" в title или description

---

**Тест 5.8: Пагинация**
```bash
# Первая страница
curl "http://localhost:8084/api/tasks?page=1&pageSize=2"

# Вторая страница
curl "http://localhost:8084/api/tasks?page=2&pageSize=2"
```

**Проверьте:**
- ✅ Размер страницы соответствует `pageSize`
- ✅ На разных страницах разные задачи
- ✅ Поле `total` показывает общее количество

---

**Тест 5.9: Комбинированные фильтры**
```bash
curl "http://localhost:8084/api/tasks?status=TODO&priority=HIGH&assignee=developer@example.com"
```

**Проверьте:**
- ✅ Применяются все фильтры одновременно

---

### Шаг 6: Обновление задачи (PUT /api/tasks/{id})

**Тест 6.1: Обновление статуса**
```bash
curl -X PUT http://localhost:8084/api/tasks/<ID_ЗАДАЧИ> \
  -H "Content-Type: application/json" \
  -d '{
    "status": "IN_PROGRESS"
  }'
```

**Проверьте:**
- ✅ Статус ответа: `200 OK`
- ✅ Статус задачи изменился на `IN_PROGRESS`
- ✅ Поле `updatedAt` обновилось

---

**Тест 6.2: Обновление нескольких полей**
```bash
curl -X PUT http://localhost:8084/api/tasks/<ID_ЗАДАЧИ> \
  -H "Content-Type: application/json" \
  -d '{
    "status": "IN_PROGRESS",
    "priority": "HIGH",
    "assignee": "new-developer@example.com",
    "actualHours": 4.0
  }'
```

**Проверьте:**
- ✅ Все указанные поля обновились
- ✅ Не указанные поля остались без изменений

---

**Тест 6.3: Обновление несуществующей задачи**
```bash
curl -X PUT http://localhost:8084/api/tasks/non-existent-id \
  -H "Content-Type: application/json" \
  -d '{"status": "DONE"}'
```

**Проверьте:**
- ✅ Статус ответа: `404 Not Found`

---

### Шаг 7: Удаление задачи (DELETE /api/tasks/{id})

**Тест 7.1: Удаление задачи**
```bash
curl -X DELETE http://localhost:8084/api/tasks/<ID_ЗАДАЧИ>
```

**Проверьте:**
- ✅ Статус ответа: `204 No Content`
- ✅ Задача больше не возвращается при запросе по ID
- ✅ Задача исчезла из списка задач

---

**Тест 7.2: Удаление несуществующей задачи**
```bash
curl -X DELETE http://localhost:8084/api/tasks/non-existent-id
```

**Проверьте:**
- ✅ Статус ответа: `404 Not Found`

---

## Тестирование аналитики

### Шаг 8: Статус проекта (GET /api/project/status)

**Подготовка:**
1. Создайте несколько задач с разными статусами и приоритетами
2. Создайте задачи с просроченными дедлайнами (дата в прошлом)
3. Создайте задачи с ближайшими дедлайнами (в течение 7 дней)

**Тест 8.1: Получение статуса проекта**
```bash
curl http://localhost:8084/api/project/status
```

**Проверьте:**
- ✅ Статус ответа: `200 OK`
- ✅ Структура ответа:
  ```json
  {
    "totalTasks": <число>,
    "tasksByStatus": {...},
    "tasksByPriority": {...},
    "overdueTasks": <число>,
    "tasksByAssignee": {...},
    "completionRate": <число>,
    "upcomingDeadlines": [...],
    "blockedTasks": [...],
    "criticalTasks": [...]
  }
  ```
- ✅ `totalTasks` соответствует количеству созданных задач
- ✅ `tasksByStatus` содержит корректные подсчеты
- ✅ `tasksByPriority` содержит корректные подсчеты
- ✅ `overdueTasks` показывает количество просроченных задач
- ✅ `completionRate` рассчитывается правильно (процент DONE задач)
- ✅ `upcomingDeadlines` содержит задачи с дедлайнами в ближайшие 7 дней
- ✅ `blockedTasks` содержит задачи со статусом `BLOCKED`
- ✅ `criticalTasks` содержит задачи с приоритетом `CRITICAL`

---

### Шаг 9: Загрузка команды (GET /api/team/capacity)

**Подготовка:**
1. Создайте задачи с разными исполнителями
2. Укажите `estimatedHours` и `actualHours` для некоторых задач

**Тест 9.1: Получение загрузки команды**
```bash
curl http://localhost:8084/api/team/capacity
```

**Проверьте:**
- ✅ Статус ответа: `200 OK`
- ✅ Структура ответа:
  ```json
  {
    "team": [
      {
        "assignee": "...",
        "totalTasks": <число>,
        "tasksByStatus": {...},
        "estimatedHours": <число>,
        "actualHours": <число>,
        "overdueTasks": <число>,
        "workload": <число>
      }
    ],
    "totalEstimatedHours": <число>,
    "totalActualHours": <число>
  }
  ```
- ✅ Для каждого исполнителя есть запись
- ✅ `totalTasks` соответствует количеству задач исполнителя
- ✅ `estimatedHours` и `actualHours` суммируются правильно
- ✅ `workload` рассчитывается корректно (actual/estimated * 100)
- ✅ `overdueTasks` показывает просроченные задачи исполнителя

---

## Тестирование граничных случаев и ошибок

### Шаг 10: Валидация данных

**Тест 10.1: Создание задачи без обязательных полей**
```bash
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Проверьте:**
- ✅ Статус ответа: `400 Bad Request`
- ✅ Есть сообщение об ошибке

---

**Тест 10.2: Создание задачи с невалидным приоритетом**
```bash
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Задача",
    "priority": "INVALID_PRIORITY"
  }'
```

**Проверьте:**
- ✅ Задача создается, но приоритет обрабатывается корректно (fallback на MEDIUM)

---

**Тест 10.3: Создание задачи с невалидной датой**
```bash
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Задача",
    "dueDate": "invalid-date"
  }'
```

**Проверьте:**
- ✅ Обработка ошибки (либо валидация, либо сохранение как есть)

---

**Тест 10.4: Обновление с пустым телом запроса**
```bash
curl -X PUT http://localhost:8084/api/tasks/<ID_ЗАДАЧИ> \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Проверьте:**
- ✅ Статус ответа: `200 OK`
- ✅ Задача не изменилась (все поля опциональны)

---

### Шаг 11: Тестирование просроченных задач

**Тест 11.1: Создание задачи с просроченным дедлайном**
```bash
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Просроченная задача",
    "dueDate": "2020-01-01",
    "status": "TODO"
  }'
```

**Проверьте:**
- ✅ Задача создается
- ✅ При запросе статуса проекта `overdueTasks` увеличивается
- ✅ Фильтр `?overdue=true` находит эту задачу

---

**Тест 11.2: Фильтр просроченных задач**
```bash
curl "http://localhost:8084/api/tasks?overdue=true"
```

**Проверьте:**
- ✅ Возвращаются только просроченные задачи
- ✅ Задачи со статусом `DONE` или `CANCELLED` не включаются

---

### Шаг 12: Тестирование зависимостей

**Тест 12.1: Создание задачи с несуществующей зависимостью**
```bash
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Задача с несуществующей зависимостью",
    "dependencies": ["non-existent-id"]
  }'
```

**Проверьте:**
- ✅ Задача создается (валидация зависимостей опциональна)

---

## Интеграционное тестирование

### Шаг 13: Полный сценарий работы

**Сценарий:**
1. Создайте несколько задач с разными параметрами
2. Обновите статусы некоторых задач
3. Получите список задач с фильтрами
4. Проверьте статус проекта
5. Проверьте загрузку команды
6. Удалите одну из задач
7. Проверьте, что удаленная задача исчезла из всех списков

**Выполните последовательно:**
```bash
# 1. Создание задач
TASK1=$(curl -s -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "Задача 1", "priority": "HIGH"}' | jq -r '.id')

TASK2=$(curl -s -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "Задача 2", "priority": "MEDIUM", "assignee": "dev@example.com"}' | jq -r '.id')

# 2. Обновление статуса
curl -X PUT http://localhost:8084/api/tasks/$TASK1 \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_PROGRESS"}'

# 3. Получение списка
curl "http://localhost:8084/api/tasks?status=IN_PROGRESS"

# 4. Статус проекта
curl http://localhost:8084/api/project/status

# 5. Загрузка команды
curl http://localhost:8084/api/team/capacity

# 6. Удаление задачи
curl -X DELETE http://localhost:8084/api/tasks/$TASK1

# 7. Проверка удаления
curl http://localhost:8084/api/tasks/$TASK1
# Должен вернуть 404
```

---

## Проверка базы данных

### Шаг 14: Прямая проверка SQLite

**Тест 14.1: Проверка структуры БД**
```bash
sqlite3 project_tasks.db ".schema project_tasks"
```

**Проверьте:**
- ✅ Таблица `project_tasks` существует
- ✅ Все необходимые колонки присутствуют
- ✅ Индексы созданы

---

**Тест 14.2: Проверка данных**
```bash
sqlite3 project_tasks.db "SELECT COUNT(*) FROM project_tasks;"
sqlite3 project_tasks.db "SELECT id, title, status, priority FROM project_tasks LIMIT 5;"
```

**Проверьте:**
- ✅ Количество записей соответствует созданным задачам
- ✅ Данные сохранены корректно

---

## Чек-лист финальной проверки

- [ ] Сервис запускается без ошибок
- [ ] Health check возвращает `ok`
- [ ] CRUD операции работают корректно
- [ ] Фильтрация и поиск работают
- [ ] Пагинация работает
- [ ] Статус проекта рассчитывается правильно
- [ ] Загрузка команды рассчитывается правильно
- [ ] Просроченные задачи определяются корректно
- [ ] Обработка ошибок работает (404, 400)
- [ ] Данные сохраняются в БД
- [ ] Обновление полей работает частично (только указанные поля)
- [ ] Зависимости сохраняются и извлекаются

---

## Дополнительные тесты (опционально)

### Тест производительности
- Создайте 100+ задач
- Проверьте время ответа на запросы
- Проверьте работу пагинации на больших объемах

### Тест конкурентного доступа
- Одновременные запросы на создание/обновление задач
- Проверка целостности данных

---

## Инструменты для тестирования

### Рекомендуемые инструменты:
1. **curl** - для базового тестирования (используется в примерах)
2. **Postman** - для удобного тестирования API
3. **httpie** - альтернатива curl с лучшим форматированием
4. **jq** - для парсинга JSON в bash скриптах

### Пример с httpie:
```bash
http POST localhost:8084/api/tasks title="Задача" priority=HIGH
http GET localhost:8084/api/tasks status==TODO
```

---

## Решение проблем

### Проблема: Сервис не запускается
- Проверьте, что порт 8084 свободен
- Проверьте логи приложения
- Убедитесь, что все зависимости установлены

### Проблема: Ошибки базы данных
- Проверьте права доступа к файлу `project_tasks.db`
- Убедитесь, что SQLite драйвер доступен

### Проблема: Неправильные ответы
- Проверьте формат JSON в запросах
- Убедитесь, что заголовок `Content-Type: application/json` установлен

---

## Отчет о тестировании

После выполнения всех тестов заполните отчет:

```
Дата тестирования: ___________
Версия API: ___________

Результаты:
- Базовые CRUD операции: [ ] PASS [ ] FAIL
- Фильтрация и поиск: [ ] PASS [ ] FAIL
- Аналитика: [ ] PASS [ ] FAIL
- Обработка ошибок: [ ] PASS [ ] FAIL
- Интеграционные тесты: [ ] PASS [ ] FAIL

Найденные проблемы:
1. ___________
2. ___________

Общий результат: [ ] PASS [ ] FAIL
```
