package org.example.storage

import java.io.File

/**
 * Вспомогательный класс для определения пути к базам данных
 * Решает проблему с правами доступа в Docker контейнерах
 */
object DatabasePathHelper {
    /**
     * Получить путь к базе данных с учетом прав доступа
     * 
     * @param defaultName имя файла БД по умолчанию
     * @return полный путь к БД в директории с правами записи
     */
    fun getDbPath(defaultName: String): String {
        // Используем переменную окружения DB_PATH если задана
        val dbDir = System.getenv("DB_PATH")?.takeIf { it.isNotBlank() }
            // Иначе используем системную временную директорию (обычно /tmp в Docker)
            ?: (System.getenv("TMPDIR")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("java.io.tmpdir")?.takeIf { it.isNotBlank() }
                ?: "/tmp")
        
        // Создаем директорию, если её нет (для локальной разработки)
        val dbDirFile = File(dbDir)
        if (!dbDirFile.exists()) {
            try {
                dbDirFile.mkdirs()
            } catch (e: Exception) {
                // Игнорируем ошибки создания директории (может не иметь прав в контейнере)
            }
        }
        
        return "$dbDir/$defaultName"
    }
}
