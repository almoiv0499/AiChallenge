package org.example.project

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Хранилище задач проекта в SQLite
 */
class ProjectTaskStorage(private val dbPath: String = "project_tasks.db") {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    init {
        loadDriver()
        initializeDatabase()
    }
    
    private fun loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("SQLite JDBC driver not found in classpath", e)
        }
    }
    
    private fun getConnection(): Connection {
        val url = "jdbc:sqlite:$dbPath"
        return try {
            DriverManager.getConnection(url)
        } catch (e: Exception) {
            throw RuntimeException("Failed to connect to database: ${e.message}", e)
        }
    }
    
    private fun initializeDatabase() {
        try {
            getConnection().use { connection ->
                // Таблица задач
                connection.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS project_tasks (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT,
                        status TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        assignee TEXT,
                        due_date TEXT,
                        tags TEXT,
                        dependencies TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        created_by TEXT,
                        estimated_hours REAL,
                        actual_hours REAL,
                        milestone TEXT,
                        epic TEXT
                    )
                """.trimIndent())
                
                // Индексы для быстрого поиска
                connection.createStatement().execute("""
                    CREATE INDEX IF NOT EXISTS idx_status ON project_tasks(status)
                """.trimIndent())
                connection.createStatement().execute("""
                    CREATE INDEX IF NOT EXISTS idx_priority ON project_tasks(priority)
                """.trimIndent())
                connection.createStatement().execute("""
                    CREATE INDEX IF NOT EXISTS idx_assignee ON project_tasks(assignee)
                """.trimIndent())
                connection.createStatement().execute("""
                    CREATE INDEX IF NOT EXISTS idx_due_date ON project_tasks(due_date)
                """.trimIndent())
            }
        } catch (e: SQLException) {
            throw RuntimeException("Failed to initialize database", e)
        }
    }
    
    /**
     * Сохранить или обновить задачу
     */
    fun saveOrUpdateTask(task: ProjectTask): Boolean {
        return try {
            getConnection().use { connection ->
                val sql = """
                    INSERT INTO project_tasks (
                        id, title, description, status, priority, assignee, due_date,
                        tags, dependencies, created_at, updated_at, created_by,
                        estimated_hours, actual_hours, milestone, epic
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        title = excluded.title,
                        description = excluded.description,
                        status = excluded.status,
                        priority = excluded.priority,
                        assignee = excluded.assignee,
                        due_date = excluded.due_date,
                        tags = excluded.tags,
                        dependencies = excluded.dependencies,
                        updated_at = excluded.updated_at,
                        estimated_hours = excluded.estimated_hours,
                        actual_hours = excluded.actual_hours,
                        milestone = excluded.milestone,
                        epic = excluded.epic
                """.trimIndent()
                
                val statement = connection.prepareStatement(sql)
                statement.setString(1, task.id)
                statement.setString(2, task.title)
                statement.setString(3, task.description)
                statement.setString(4, task.status)
                statement.setString(5, task.priority)
                statement.setString(6, task.assignee)
                statement.setString(7, task.dueDate)
                statement.setString(8, task.tags.joinToString(","))
                statement.setString(9, task.dependencies.joinToString(","))
                statement.setString(10, task.createdAt)
                statement.setString(11, task.updatedAt)
                statement.setString(12, task.createdBy)
                statement.setObject(13, task.estimatedHours)
                statement.setObject(14, task.actualHours)
                statement.setString(15, task.milestone)
                statement.setString(16, task.epic)
                
                statement.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            false
        }
    }
    
    /**
     * Получить задачу по ID
     */
    fun getTaskById(id: String): ProjectTask? {
        return try {
            getConnection().use { connection ->
                val sql = """
                    SELECT * FROM project_tasks WHERE id = ?
                """.trimIndent()
                val statement = connection.prepareStatement(sql)
                statement.setString(1, id)
                val resultSet = statement.executeQuery()
                
                if (resultSet.next()) {
                    mapRowToTask(resultSet)
                } else {
                    null
                }
            }
        } catch (e: SQLException) {
            null
        }
    }
    
    /**
     * Получить все задачи с фильтрами
     */
    fun getTasks(filters: TaskFilters? = null, page: Int = 1, pageSize: Int = 50): Pair<List<ProjectTask>, Int> {
        return try {
            getConnection().use { connection ->
                val whereClauses = mutableListOf<String>()
                val params = mutableListOf<Any>()
                
                filters?.let { f ->
                    f.status?.let {
                        whereClauses.add("status IN (${it.joinToString(",") { "?" }})")
                        it.forEach { params.add(it) }
                    }
                    f.priority?.let {
                        whereClauses.add("priority IN (${it.joinToString(",") { "?" }})")
                        it.forEach { params.add(it) }
                    }
                    f.assignee?.let {
                        whereClauses.add("assignee = ?")
                        params.add(it)
                    }
                    f.milestone?.let {
                        whereClauses.add("milestone = ?")
                        params.add(it)
                    }
                    f.epic?.let {
                        whereClauses.add("epic = ?")
                        params.add(it)
                    }
                    f.overdue?.let {
                        if (it) {
                            whereClauses.add("due_date IS NOT NULL AND due_date < date('now') AND status NOT IN ('DONE', 'CANCELLED')")
                        }
                    }
                    f.search?.let {
                        whereClauses.add("(title LIKE ? OR description LIKE ?)")
                        val searchPattern = "%$it%"
                        params.add(searchPattern)
                        params.add(searchPattern)
                    }
                    f.tags?.let {
                        if (it.isNotEmpty()) {
                            // SQLite не поддерживает массивы, используем LIKE для поиска в строке
                            val tagConditions = it.map { tag ->
                                "tags LIKE ?"
                            }
                            whereClauses.add("(${tagConditions.joinToString(" OR ")})")
                            it.forEach { tag ->
                                params.add("%$tag%")
                            }
                        }
                    }
                }
                
                val whereClause = if (whereClauses.isNotEmpty()) {
                    "WHERE ${whereClauses.joinToString(" AND ")}"
                } else {
                    ""
                }
                
                // Подсчет общего количества
                val countSql = "SELECT COUNT(*) FROM project_tasks $whereClause"
                val countStatement = connection.prepareStatement(countSql)
                params.forEachIndexed { index, param ->
                    countStatement.setObject(index + 1, param)
                }
                val countResult = countStatement.executeQuery()
                val total = if (countResult.next()) countResult.getInt(1) else 0
                
                // Получение задач с пагинацией
                val offset = (page - 1) * pageSize
                val sql = """
                    SELECT * FROM project_tasks 
                    $whereClause
                    ORDER BY 
                        CASE priority
                            WHEN 'CRITICAL' THEN 1
                            WHEN 'HIGH' THEN 2
                            WHEN 'MEDIUM' THEN 3
                            WHEN 'LOW' THEN 4
                        END,
                        due_date ASC NULLS LAST,
                        created_at DESC
                    LIMIT ? OFFSET ?
                """.trimIndent()
                
                val statement = connection.prepareStatement(sql)
                params.forEachIndexed { index, param ->
                    statement.setObject(index + 1, param)
                }
                statement.setInt(params.size + 1, pageSize)
                statement.setInt(params.size + 2, offset)
                
                val resultSet = statement.executeQuery()
                val tasks = mutableListOf<ProjectTask>()
                while (resultSet.next()) {
                    tasks.add(mapRowToTask(resultSet))
                }
                
                Pair(tasks, total)
            }
        } catch (e: SQLException) {
            Pair(emptyList(), 0)
        }
    }
    
    /**
     * Удалить задачу
     */
    fun deleteTask(id: String): Boolean {
        return try {
            getConnection().use { connection ->
                val sql = "DELETE FROM project_tasks WHERE id = ?"
                val statement = connection.prepareStatement(sql)
                statement.setString(1, id)
                statement.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            false
        }
    }
    
    /**
     * Получить все задачи (для статистики)
     */
    fun getAllTasks(): List<ProjectTask> {
        return try {
            getConnection().use { connection ->
                val sql = "SELECT * FROM project_tasks"
                val statement = connection.prepareStatement(sql)
                val resultSet = statement.executeQuery()
                val tasks = mutableListOf<ProjectTask>()
                while (resultSet.next()) {
                    tasks.add(mapRowToTask(resultSet))
                }
                tasks
            }
        } catch (e: SQLException) {
            emptyList()
        }
    }
    
    private fun mapRowToTask(resultSet: java.sql.ResultSet): ProjectTask {
        val tagsStr = resultSet.getString("tags") ?: ""
        val dependenciesStr = resultSet.getString("dependencies") ?: ""
        
        return ProjectTask(
            id = resultSet.getString("id"),
            title = resultSet.getString("title"),
            description = resultSet.getString("description"),
            status = resultSet.getString("status"),
            priority = resultSet.getString("priority"),
            assignee = resultSet.getString("assignee"),
            dueDate = resultSet.getString("due_date"),
            tags = if (tagsStr.isNotEmpty()) tagsStr.split(",") else emptyList(),
            dependencies = if (dependenciesStr.isNotEmpty()) dependenciesStr.split(",") else emptyList(),
            createdAt = resultSet.getString("created_at"),
            updatedAt = resultSet.getString("updated_at"),
            createdBy = resultSet.getString("created_by"),
            estimatedHours = resultSet.getObject("estimated_hours") as? Double,
            actualHours = resultSet.getObject("actual_hours") as? Double,
            milestone = resultSet.getString("milestone"),
            epic = resultSet.getString("epic")
        )
    }
}
