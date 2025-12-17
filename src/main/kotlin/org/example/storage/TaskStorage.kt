package org.example.storage

import org.example.reminder.Task
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Storage layer for persisting task state in SQLite database.
 * Assumes the database layer already exists and is usable.
 */
class TaskStorage(private val dbPath: String = "tasks.db") {
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
                connection.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS tasks (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        status TEXT,
                        last_updated INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        } catch (e: SQLException) {
            throw RuntimeException("Failed to initialize database", e)
        }
    }
    
    /**
     * Saves or updates a task in the database.
     * If task with same ID exists, updates it; otherwise inserts new record.
     */
    fun saveOrUpdateTask(task: Task): Boolean {
        return try {
            getConnection().use { connection ->
                val sql = """
                    INSERT INTO tasks (id, name, status, last_updated) 
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        name = excluded.name,
                        status = excluded.status,
                        last_updated = excluded.last_updated
                """.trimIndent()
                val statement = connection.prepareStatement(sql)
                statement.setString(1, task.id)
                statement.setString(2, task.name)
                statement.setString(3, task.status)
                statement.setLong(4, System.currentTimeMillis() / 1000)
                statement.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            false
        }
    }
    
    /**
     * Saves or updates multiple tasks in a single transaction.
     */
    fun saveOrUpdateTasks(tasks: List<Task>): Boolean {
        return try {
            getConnection().use { connection ->
                connection.autoCommit = false
                try {
                    val sql = """
                        INSERT INTO tasks (id, name, status, last_updated) 
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            name = excluded.name,
                            status = excluded.status,
                            last_updated = excluded.last_updated
                    """.trimIndent()
                    val statement = connection.prepareStatement(sql)
                    val timestamp = System.currentTimeMillis() / 1000
                    
                    for (task in tasks) {
                        statement.setString(1, task.id)
                        statement.setString(2, task.name)
                        statement.setString(3, task.status)
                        statement.setLong(4, timestamp)
                        statement.addBatch()
                    }
                    
                    statement.executeBatch()
                    connection.commit()
                    true
                } catch (e: Exception) {
                    connection.rollback()
                    false
                } finally {
                    connection.autoCommit = true
                }
            }
        } catch (e: SQLException) {
            false
        }
    }
    
    /**
     * Retrieves all tasks from the database.
     */
    fun getAllTasks(): List<Task> {
        return try {
            getConnection().use { connection ->
                val sql = "SELECT id, name, status FROM tasks"
                val statement = connection.prepareStatement(sql)
                val resultSet = statement.executeQuery()
                val tasks = mutableListOf<Task>()
                
                while (resultSet.next()) {
                    tasks.add(
                        Task(
                            id = resultSet.getString("id"),
                            name = resultSet.getString("name"),
                            status = resultSet.getString("status"),
                            dueDate = null // Due date not stored in DB for now
                        )
                    )
                }
                tasks
            }
        } catch (e: SQLException) {
            emptyList()
        }
    }
    
    /**
     * Retrieves a task by ID.
     */
    fun getTaskById(id: String): Task? {
        return try {
            getConnection().use { connection ->
                val sql = "SELECT id, name, status FROM tasks WHERE id = ?"
                val statement = connection.prepareStatement(sql)
                statement.setString(1, id)
                val resultSet = statement.executeQuery()
                
                if (resultSet.next()) {
                    Task(
                        id = resultSet.getString("id"),
                        name = resultSet.getString("name"),
                        status = resultSet.getString("status"),
                        dueDate = null
                    )
                } else {
                    null
                }
            }
        } catch (e: SQLException) {
            null
        }
    }
    
    /**
     * Deletes a task by ID.
     */
    fun deleteTask(id: String): Boolean {
        return try {
            getConnection().use { connection ->
                val sql = "DELETE FROM tasks WHERE id = ?"
                val statement = connection.prepareStatement(sql)
                statement.setString(1, id)
                statement.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            false
        }
    }
    
    /**
     * Clears all tasks from the database.
     * @return Number of deleted tasks, or -1 if error occurred
     */
    fun clearAllTasks(): Int {
        return try {
            getConnection().use { connection ->
                val sql = "DELETE FROM tasks"
                val statement = connection.prepareStatement(sql)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            -1
        }
    }
}

