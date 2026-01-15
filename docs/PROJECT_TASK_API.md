# Project Task API

REST API сервис для управления задачами проекта. Работает независимо от MCP серверов.

## Запуск

Сервис автоматически запускается при старте приложения на порту **8084**.

```
http://localhost:8084/api
```

## Endpoints

### Health Check

```
GET /api/health
```

Проверка работоспособности сервиса.

**Response:**
```json
{
  "status": "ok",
  "service": "Project Task API"
}
```

### Создать задачу

```
POST /api/tasks
```

**Request Body:**
```json
{
  "title": "Интеграция платежей",
  "description": "Добавить поддержку Stripe API",
  "priority": "HIGH",
  "assignee": "developer@example.com",
  "dueDate": "2025-02-15",
  "tags": ["backend", "payment"],
  "dependencies": [],
  "estimatedHours": 8.0,
  "milestone": "MVP",
  "epic": "Payment Integration"
}
```

**Response:** `201 Created`
```json
{
  "id": "uuid",
  "title": "Интеграция платежей",
  "status": "TODO",
  "priority": "HIGH",
  ...
}
```

### Получить задачи

```
GET /api/tasks?status=TODO&priority=HIGH&page=1&pageSize=50
```

**Query Parameters:**
- `status` - фильтр по статусу (можно несколько: `?status=TODO&status=IN_PROGRESS`)
- `priority` - фильтр по приоритету
- `assignee` - фильтр по исполнителю
- `tags` - фильтр по тегам
- `milestone` - фильтр по milestone
- `epic` - фильтр по epic
- `overdue` - только просроченные задачи (`true`/`false`)
- `search` - поиск по title и description
- `page` - номер страницы (по умолчанию 1)
- `pageSize` - размер страницы (по умолчанию 50)

**Response:**
```json
{
  "tasks": [...],
  "total": 100,
  "page": 1,
  "pageSize": 50
}
```

### Получить задачу по ID

```
GET /api/tasks/{id}
```

**Response:** `200 OK` или `404 Not Found`

### Обновить задачу

```
PUT /api/tasks/{id}
```

**Request Body:**
```json
{
  "status": "IN_PROGRESS",
  "priority": "CRITICAL",
  "assignee": "developer@example.com",
  "dueDate": "2025-02-20",
  "actualHours": 4.0
}
```

Все поля опциональны. Обновляются только указанные поля.

**Response:** `200 OK` или `404 Not Found`

### Удалить задачу

```
DELETE /api/tasks/{id}
```

**Response:** `204 No Content` или `404 Not Found`

### Получить статус проекта

```
GET /api/project/status
```

**Response:**
```json
{
  "totalTasks": 150,
  "tasksByStatus": {
    "TODO": 50,
    "IN_PROGRESS": 30,
    "DONE": 70
  },
  "tasksByPriority": {
    "CRITICAL": 10,
    "HIGH": 40,
    "MEDIUM": 80,
    "LOW": 20
  },
  "overdueTasks": 5,
  "tasksByAssignee": {
    "developer@example.com": 25,
    "designer@example.com": 15
  },
  "completionRate": 46.67,
  "upcomingDeadlines": [...],
  "blockedTasks": [...],
  "criticalTasks": [...]
}
```

### Получить загрузку команды

```
GET /api/team/capacity
```

**Response:**
```json
{
  "team": [
    {
      "assignee": "developer@example.com",
      "totalTasks": 25,
      "tasksByStatus": {
        "TODO": 10,
        "IN_PROGRESS": 10,
        "DONE": 5
      },
      "estimatedHours": 120.0,
      "actualHours": 80.0,
      "overdueTasks": 2,
      "workload": 66.67
    }
  ],
  "totalEstimatedHours": 500.0,
  "totalActualHours": 350.0
}
```

## Модели данных

### TaskPriority
- `LOW`
- `MEDIUM`
- `HIGH`
- `CRITICAL`

### TaskStatus
- `TODO`
- `IN_PROGRESS`
- `IN_REVIEW`
- `BLOCKED`
- `DONE`
- `CANCELLED`

### ProjectTask
```json
{
  "id": "string (UUID)",
  "title": "string",
  "description": "string (optional)",
  "status": "string (TaskStatus)",
  "priority": "string (TaskPriority)",
  "assignee": "string (optional)",
  "dueDate": "string (ISO 8601 date, optional)",
  "tags": ["string"],
  "dependencies": ["string (task IDs)"],
  "createdAt": "string (ISO 8601 datetime)",
  "updatedAt": "string (ISO 8601 datetime)",
  "createdBy": "string (optional)",
  "estimatedHours": "number (optional)",
  "actualHours": "number (optional)",
  "milestone": "string (optional)",
  "epic": "string (optional)"
}
```

## Примеры использования

### Создать задачу с высоким приоритетом

```bash
curl -X POST http://localhost:8084/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Критический баг в платежах",
    "description": "Платежи не проходят для пользователей из ЕС",
    "priority": "CRITICAL",
    "assignee": "backend-dev@example.com",
    "dueDate": "2025-01-20",
    "tags": ["bug", "payment", "critical"]
  }'
```

### Получить все критические задачи

```bash
curl "http://localhost:8084/api/tasks?priority=CRITICAL"
```

### Получить просроченные задачи

```bash
curl "http://localhost:8084/api/tasks?overdue=true"
```

### Обновить статус задачи

```bash
curl -X PUT http://localhost:8084/api/tasks/{task-id} \
  -H "Content-Type: application/json" \
  -d '{
    "status": "IN_PROGRESS",
    "actualHours": 2.5
  }'
```

### Получить статус проекта

```bash
curl http://localhost:8084/api/project/status
```

## Хранение данных

Данные хранятся в SQLite базе данных `project_tasks.db` в корне проекта.

## Интеграция

Сервис работает независимо от MCP серверов и может быть использован:
- Напрямую через REST API
- Через HTTP клиент в коде
- Через будущие инструменты агента
