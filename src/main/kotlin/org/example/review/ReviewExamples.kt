package org.example.review

import java.io.File
import java.sql.DriverManager

/**
 * Примеры кода для тестирования AI Code Review.
 * Этот файл содержит намеренные проблемы, которые reviewer должен обнаружить.
 */
object ReviewExamples {
    
    // ⚠️ Security Issue: SQL Injection vulnerability
    fun getUserByName(username: String): String? {
        val connection = DriverManager.getConnection("jdbc:sqlite:test.db")
        // UNSAFE: прямая конкатенация пользовательского ввода в SQL
        val query = "SELECT * FROM users WHERE name = '$username'"
        val statement = connection.createStatement()
        val result = statement.executeQuery(query)
        return if (result.next()) result.getString("email") else null
        // Resource leak: connection не закрывается
    }
    
    // ⚠️ Performance Issue: O(n²) complexity
    fun findDuplicates(items: List<String>): List<String> {
        val duplicates = mutableListOf<String>()
        for (i in items.indices) {
            for (j in items.indices) {
                if (i != j && items[i] == items[j] && items[i] !in duplicates) {
                    duplicates.add(items[i])
                }
            }
        }
        return duplicates
    }
    
    // ⚠️ Logic Issue: возможен NPE
    fun processUserData(userData: Map<String, Any?>): String {
        val name = userData["name"] as String  // Unsafe cast, может быть null
        val age = userData["age"] as Int       // Unsafe cast
        return "User: $name, Age: $age"
    }
    
    // ⚠️ Resource Leak: файл не закрывается
    fun readFileContent(path: String): String {
        val file = File(path)
        val reader = file.bufferedReader()
        return reader.readText()
        // reader не закрывается!
    }
    
    // ✅ Good Example: правильная обработка ресурсов
    fun readFileSafely(path: String): String {
        return File(path).bufferedReader().use { it.readText() }
    }
    
    // ✅ Good Example: безопасная работа с nullable
    fun processUserDataSafely(userData: Map<String, Any?>): String? {
        val name = userData["name"] as? String ?: return null
        val age = userData["age"] as? Int ?: return null
        return "User: $name, Age: $age"
    }
    
    // ✅ Good Example: O(n) complexity с использованием Set
    fun findDuplicatesOptimized(items: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        
        for (item in items) {
            if (item in seen) {
                duplicates.add(item)
            } else {
                seen.add(item)
            }
        }
        return duplicates.toList()
    }
}
