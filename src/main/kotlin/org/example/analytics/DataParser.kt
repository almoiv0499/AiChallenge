package org.example.analytics

import kotlinx.serialization.json.*
import java.io.File

/**
 * Универсальный парсер данных для CSV, JSON, JSON Lines
 */
object DataParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Автоматическое определение формата и парсинг файла
     */
    fun autoDetect(path: String): ParsedData {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("Файл не найден: $path")
        }

        val type = detectType(file)
        return when (type) {
            DataType.CSV -> parseCSV(path)
            DataType.JSON -> parseJSON(path)
            DataType.JSON_LINES -> parseJSONLines(path)
            DataType.LOG -> parseLog(path)
            DataType.UNKNOWN -> throw IllegalArgumentException("Неподдерживаемый формат файла: ${file.extension}")
        }
    }

    /**
     * Определение типа файла по расширению и содержимому
     */
    private fun detectType(file: File): DataType {
        val extension = file.extension.lowercase()

        // Сначала проверяем по расширению
        when (extension) {
            "csv" -> return DataType.CSV
            "log" -> return DataType.LOG
            "jsonl", "ndjson" -> return DataType.JSON_LINES
            "json" -> {
                // Для JSON проверяем содержимое - это массив/объект или JSON Lines?
                val firstLine = file.useLines { it.firstOrNull() } ?: return DataType.JSON
                val trimmed = firstLine.trim()
                return if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                    // Проверяем, есть ли несколько JSON объектов на разных строках
                    val lines = file.readLines().filter { it.isNotBlank() }
                    if (lines.size > 1 && lines.all { it.trim().startsWith("{") }) {
                        DataType.JSON_LINES
                    } else {
                        DataType.JSON
                    }
                } else {
                    DataType.UNKNOWN
                }
            }
            "txt" -> {
                // Для txt проверяем содержимое
                val firstLine = file.useLines { it.firstOrNull() } ?: return DataType.LOG
                return if (firstLine.trim().startsWith("{")) DataType.JSON_LINES else DataType.LOG
            }
        }

        return DataType.UNKNOWN
    }

    /**
     * Парсинг CSV файла
     */
    fun parseCSV(path: String): ParsedData {
        val file = File(path)
        val lines = file.readLines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return createEmptyParsedData(file, DataType.CSV)
        }

        // Определяем разделитель (запятая, точка с запятой, табуляция)
        val delimiter = detectCSVDelimiter(lines.first())

        // Первая строка - заголовки
        val headers = parseCSVLine(lines.first(), delimiter)

        // Остальные строки - данные
        val records = lines.drop(1).map { line ->
            val values = parseCSVLine(line, delimiter)
            headers.mapIndexed { index, header ->
                header to (values.getOrNull(index) ?: "")
            }.toMap()
        }

        return ParsedData(
            source = DataSource(
                path = path,
                type = DataType.CSV,
                sizeBytes = file.length(),
                fileName = file.name
            ),
            records = records,
            columns = headers,
            totalRecords = records.size,
            preview = createPreview(records, headers)
        )
    }

    /**
     * Парсинг JSON файла (массив объектов или вложенная структура)
     */
    fun parseJSON(path: String): ParsedData {
        val file = File(path)
        val content = file.readText()

        if (content.isBlank()) {
            return createEmptyParsedData(file, DataType.JSON)
        }

        val jsonElement = json.parseToJsonElement(content)
        val records = extractRecordsFromJson(jsonElement)
        val columns = extractColumnsFromRecords(records)

        return ParsedData(
            source = DataSource(
                path = path,
                type = DataType.JSON,
                sizeBytes = file.length(),
                fileName = file.name
            ),
            records = records,
            columns = columns,
            totalRecords = records.size,
            preview = createPreview(records, columns)
        )
    }

    /**
     * Парсинг JSON Lines (NDJSON) файла
     */
    fun parseJSONLines(path: String): ParsedData {
        val file = File(path)
        val lines = file.readLines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return createEmptyParsedData(file, DataType.JSON_LINES)
        }

        val records = lines.mapNotNull { line ->
            try {
                val jsonObject = json.parseToJsonElement(line).jsonObject
                jsonObjectToMap(jsonObject)
            } catch (e: Exception) {
                null // Пропускаем некорректные строки
            }
        }

        val columns = extractColumnsFromRecords(records)

        return ParsedData(
            source = DataSource(
                path = path,
                type = DataType.JSON_LINES,
                sizeBytes = file.length(),
                fileName = file.name
            ),
            records = records,
            columns = columns,
            totalRecords = records.size,
            preview = createPreview(records, columns)
        )
    }

    /**
     * Парсинг лог-файла
     */
    fun parseLog(path: String): ParsedData {
        val file = File(path)
        val lines = file.readLines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return createEmptyParsedData(file, DataType.LOG)
        }

        // Парсим логи в простой формат: номер строки -> содержимое
        val records = lines.mapIndexed { index, line ->
            mapOf(
                "line_number" to (index + 1).toString(),
                "content" to line,
                "level" to extractLogLevel(line),
                "timestamp" to extractTimestamp(line)
            )
        }

        val columns = listOf("line_number", "timestamp", "level", "content")

        return ParsedData(
            source = DataSource(
                path = path,
                type = DataType.LOG,
                sizeBytes = file.length(),
                fileName = file.name
            ),
            records = records,
            columns = columns,
            totalRecords = records.size,
            preview = createPreview(records.take(10), columns)
        )
    }

    // ===== Вспомогательные функции =====

    private fun detectCSVDelimiter(line: String): Char {
        val delimiters = listOf(',', ';', '\t', '|')
        return delimiters.maxByOrNull { delimiter ->
            line.count { it == delimiter }
        } ?: ','
    }

    private fun parseCSVLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())

        return result
    }

    private fun extractRecordsFromJson(element: JsonElement): List<Map<String, String>> {
        return when (element) {
            is JsonArray -> element.mapNotNull { item ->
                when (item) {
                    is JsonObject -> jsonObjectToMap(item)
                    else -> null
                }
            }
            is JsonObject -> {
                // Ищем все массивы внутри объекта
                val arrays = element.entries.filter { it.value is JsonArray }
                if (arrays.isNotEmpty()) {
                    // Объединяем все массивы, добавляя поле _type для идентификации
                    val allRecords = mutableListOf<Map<String, String>>()

                    // Сначала добавляем скалярные поля верхнего уровня как метаданные
                    val metadata = element.entries
                        .filter { it.value is JsonPrimitive }
                        .associate { it.key to (it.value as JsonPrimitive).content }

                    for ((arrayName, arrayValue) in arrays) {
                        val arrayRecords = (arrayValue as JsonArray).mapNotNull { item ->
                            when (item) {
                                is JsonObject -> {
                                    val record = jsonObjectToMap(item).toMutableMap()
                                    record["_type"] = arrayName // Добавляем тип записи
                                    record
                                }
                                else -> null
                            }
                        }
                        allRecords.addAll(arrayRecords)
                    }

                    // Если есть метаданные, добавляем их как отдельную запись
                    if (metadata.isNotEmpty()) {
                        val metaRecord = metadata.toMutableMap()
                        metaRecord["_type"] = "_metadata"
                        allRecords.add(0, metaRecord)
                    }

                    allRecords
                } else {
                    // Возвращаем сам объект как единственную запись
                    listOf(jsonObjectToMap(element))
                }
            }
            else -> emptyList()
        }
    }

    private fun jsonObjectToMap(obj: JsonObject, prefix: String = ""): Map<String, String> {
        val result = mutableMapOf<String, String>()

        for ((key, value) in obj.entries) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"

            when (value) {
                is JsonPrimitive -> result[fullKey] = value.content
                is JsonArray -> {
                    // Для массивов сохраняем как JSON строку или извлекаем простые значения
                    if (value.all { it is JsonPrimitive }) {
                        result[fullKey] = value.joinToString(", ") {
                            (it as? JsonPrimitive)?.content ?: it.toString()
                        }
                    } else {
                        result[fullKey] = value.toString()
                    }
                }
                is JsonObject -> {
                    // Рекурсивно разворачиваем вложенные объекты
                    result.putAll(jsonObjectToMap(value, fullKey))
                }
                is JsonNull -> result[fullKey] = ""
            }
        }

        return result
    }

    private fun extractColumnsFromRecords(records: List<Map<String, String>>): List<String> {
        val allColumns = mutableSetOf<String>()
        records.forEach { record -> allColumns.addAll(record.keys) }
        return allColumns.toList().sorted()
    }

    private fun extractLogLevel(line: String): String {
        val levels = listOf("ERROR", "WARN", "WARNING", "INFO", "DEBUG", "TRACE", "FATAL", "CRITICAL")
        val upperLine = line.uppercase()
        return levels.find { upperLine.contains(it) } ?: "UNKNOWN"
    }

    private fun extractTimestamp(line: String): String {
        // Пытаемся найти timestamp в начале строки
        val timestampPatterns = listOf(
            Regex("""^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}"""),
            Regex("""^\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2}"""),
            Regex("""^\[\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}"""),
            Regex("""^\d{2}:\d{2}:\d{2}""")
        )

        for (pattern in timestampPatterns) {
            val match = pattern.find(line)
            if (match != null) {
                return match.value.trim('[', ']')
            }
        }

        return ""
    }

    private fun createPreview(records: List<Map<String, String>>, columns: List<String>): String {
        if (records.isEmpty()) return "Данные отсутствуют"

        val previewRecords = records.take(5)
        val previewColumns = columns.take(6)

        return buildString {
            appendLine("Превью данных (первые ${previewRecords.size} записей):")
            appendLine()

            for ((index, record) in previewRecords.withIndex()) {
                appendLine("Запись ${index + 1}:")
                for (col in previewColumns) {
                    val value = record[col] ?: ""
                    val displayValue = if (value.length > 50) "${value.take(50)}..." else value
                    appendLine("  $col: $displayValue")
                }
                appendLine()
            }

            if (columns.size > 6) {
                appendLine("... и еще ${columns.size - 6} колонок")
            }
        }
    }

    private fun createEmptyParsedData(file: File, type: DataType): ParsedData {
        return ParsedData(
            source = DataSource(
                path = file.absolutePath,
                type = type,
                sizeBytes = file.length(),
                fileName = file.name
            ),
            records = emptyList(),
            columns = emptyList(),
            totalRecords = 0,
            preview = "Файл пуст"
        )
    }
}
