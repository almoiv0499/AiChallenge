package org.example.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.mcp.McpClient
import org.example.mcp.McpTool
import org.example.models.OpenRouterPropertyDefinition
import org.example.models.OpenRouterTool
import org.example.models.OpenRouterToolParameters

class McpToolAdapter(
    private val mcpTool: McpTool,
    private val mcpClient: McpClient
) : AgentTool {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun safeGetJsonArray(element: JsonElement?): JsonArray? {
        return when {
            element == null || element is JsonNull -> null
            element is JsonArray -> element
            else -> null
        }
    }

    override val name: String = mcpTool.name
    override val description: String = mcpTool.description ?: "MCP tool: ${mcpTool.name}"

    override fun getDefinition(): OpenRouterTool {
        val inputSchemaElement = mcpTool.inputSchema
        val inputSchema = when {
            inputSchemaElement == null || inputSchemaElement is JsonNull -> null
            inputSchemaElement is JsonObject -> inputSchemaElement
            else -> null
        }
        val propertiesElement = inputSchema?.get("properties")
        val properties = when {
            propertiesElement == null || propertiesElement is JsonNull -> emptyMap()
            propertiesElement is JsonObject -> propertiesElement
            else -> emptyMap()
        }
        val required = inputSchema?.get("required")?.let {
            when {
                it is JsonArray -> it.map { elem -> elem.jsonPrimitive.content }
                it.jsonPrimitive?.isString == true -> listOf(it.jsonPrimitive.content)
                else -> emptyList<String>()
            }
        } ?: emptyList()

        val openRouterProperties = properties.mapValues { (key, value) ->
            val propObj = when {
                value is JsonNull -> null
                value is JsonObject -> value
                else -> null
            }
            val type = propObj?.get("type")?.jsonPrimitive?.content ?: "string"
            val description = propObj?.get("description")?.jsonPrimitive?.content
            val enum = safeGetJsonArray(propObj?.get("enum"))?.map { it.jsonPrimitive.content }
            OpenRouterPropertyDefinition(
                type = type,
                description = description,
                enum = enum
            )
        }

        return OpenRouterTool(
            name = name,
            description = description,
            parameters = OpenRouterToolParameters(
                properties = openRouterProperties,
                required = required
            )
        )
    }

    override fun execute(arguments: Map<String, String>): String {
        return try {
            val jsonArguments = buildJsonObject {
                arguments.forEach { (key, value) ->
                    val jsonValue = try {
                        json.parseToJsonElement(value)
                    } catch (e: Exception) {
                        json.parseToJsonElement("\"$value\"")
                    }
                    put(key, jsonValue)
                }
            }
            val result: JsonElement = runBlocking {
                mcpClient.callTool(name, jsonArguments)
            }
            formatResult(result)
        } catch (e: Exception) {
            "Ошибка при вызове MCP инструмента '$name': ${e.message}"
        }
    }

    private fun formatResult(result: JsonElement): String {
        val resultObj = when {
            result is JsonNull -> null
            result is JsonObject -> result
            else -> null
        }
        return when {
            resultObj != null && resultObj.containsKey("object") && resultObj["object"]?.jsonPrimitive?.content == "page" -> {
                formatNotionPage(resultObj)
            }
            resultObj != null && resultObj.containsKey("id") && resultObj.containsKey("url") -> {
                formatNotionPage(resultObj)
            }
            resultObj != null && resultObj.containsKey("lat") && resultObj.containsKey("lon") && resultObj.containsKey("current") -> {
                formatWeather(resultObj)
            }
            result is JsonArray -> {
                val items = result
                if (items.isEmpty()) {
                    "Данные не найдены"
                } else {
                    items.joinToString("\n\n") { item ->
                        val itemObj = when {
                            item is JsonNull -> null
                            item is JsonObject -> item
                            else -> null
                        }
                        if (itemObj != null && itemObj.containsKey("object") && itemObj["object"]?.jsonPrimitive?.content == "page") {
                            formatNotionPage(itemObj)
                        } else {
                            json.encodeToString(JsonElement.serializer(), item)
                        }
                    }
                }
            }
            else -> {
                json.encodeToString(JsonElement.serializer(), result)
            }
        }
    }

    private fun formatNotionPage(pageObj: JsonObject): String {
        val id = pageObj["id"]?.jsonPrimitive?.content ?: "N/A"
        val url = pageObj["url"]?.jsonPrimitive?.content ?: "N/A"
        val createdTime = pageObj["created_time"]?.jsonPrimitive?.content
        val lastEditedTime = pageObj["last_edited_time"]?.jsonPrimitive?.content
        val archived = pageObj["archived"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val inTrash = pageObj["in_trash"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val propertiesElement = pageObj["properties"]
        val properties = when {
            propertiesElement == null || propertiesElement is JsonNull -> buildJsonObject {}
            propertiesElement is JsonObject -> propertiesElement
            else -> buildJsonObject {}
        }
        val title = extractTitleFromProperties(properties)
        val publicUrl = pageObj["public_url"]?.jsonPrimitive?.content
        val iconElement = pageObj["icon"]
        val icon = when {
            iconElement == null || iconElement is JsonNull -> null
            iconElement is JsonObject -> iconElement.get("emoji")?.jsonPrimitive?.content
            else -> null
        }
        return buildString {
            appendLine("📄 Страница Notion")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            if (icon != null) {
                appendLine("Иконка: $icon")
            }
            if (title.isNotEmpty()) {
                appendLine("Название: $title")
            }
            appendLine("ID: $id")
            appendLine("URL: $url")
            if (publicUrl != null) {
                appendLine("Публичный URL: $publicUrl")
            }
            if (createdTime != null) {
                appendLine("Создана: $createdTime")
            }
            if (lastEditedTime != null) {
                appendLine("Изменена: $lastEditedTime")
            }
            if (archived) {
                appendLine("⚠️ Архивная")
            }
            if (inTrash) {
                appendLine("🗑️ В корзине")
            }
            if (properties.isNotEmpty()) {
                appendLine("\nСвойства:")
                properties.forEach { (key, value) ->
                    val propObj = when {
                        value is JsonNull -> null
                        value is JsonObject -> value
                        else -> null
                    }
                    if (propObj != null) {
                        val propType = propObj["type"]?.jsonPrimitive?.content ?: "unknown"
                        val propValue = extractPropertyValue(propObj, propType)
                        if (propValue.isNotEmpty()) {
                            appendLine("  • $key ($propType): $propValue")
                        }
                    }
                }
            }
        }
    }

    private fun extractTitleFromProperties(properties: JsonObject): String {
        properties.values.forEach { prop ->
            val propObj = when {
                prop is JsonNull -> null
                prop is JsonObject -> prop
                else -> null
            }
            if (propObj != null) {
                val propType = propObj["type"]?.jsonPrimitive?.content
                if (propType == "title") {
                    val titleArray = safeGetJsonArray(propObj["title"])
                    return titleArray?.mapNotNull { item ->
                        when {
                            item is JsonNull -> null
                            item is JsonObject -> item["plain_text"]?.jsonPrimitive?.content
                            else -> null
                        }
                    }?.joinToString("") ?: ""
                }
            }
        }
        return ""
    }

    private fun extractPropertyValue(propObj: JsonObject, propType: String): String {
        return when (propType) {
            "title" -> {
                safeGetJsonArray(propObj["title"])?.mapNotNull { item ->
                    when {
                        item is JsonNull -> null
                        item is JsonObject -> item["plain_text"]?.jsonPrimitive?.content
                        else -> null
                    }
                }?.joinToString("") ?: ""
            }
            "rich_text" -> {
                safeGetJsonArray(propObj["rich_text"])?.mapNotNull { item ->
                    when {
                        item is JsonNull -> null
                        item is JsonObject -> item["plain_text"]?.jsonPrimitive?.content
                        else -> null
                    }
                }?.joinToString("") ?: ""
            }
            "number" -> propObj["number"]?.jsonPrimitive?.content ?: ""
            "select" -> {
                val selectElement = propObj["select"]
                when {
                    selectElement == null || selectElement is JsonNull -> ""
                    selectElement is JsonObject -> selectElement.get("name")?.jsonPrimitive?.content ?: ""
                    else -> ""
                }
            }
            "status" -> {
                val statusElement = propObj["status"]
                when {
                    statusElement == null || statusElement is JsonNull -> ""
                    statusElement is JsonObject -> statusElement.get("name")?.jsonPrimitive?.content ?: ""
                    else -> ""
                }
            }
            "date" -> {
                val dateElement = propObj["date"]
                val dateObj = when {
                    dateElement == null || dateElement is JsonNull -> null
                    dateElement is JsonObject -> dateElement
                    else -> null
                }
                val start = dateObj?.get("start")?.jsonPrimitive?.content ?: ""
                val end = dateObj?.get("end")?.jsonPrimitive?.content
                if (end != null) "$start - $end" else start
            }
            "checkbox" -> if (propObj["checkbox"]?.jsonPrimitive?.content?.toBoolean() == true) "✓" else "✗"
            "url" -> propObj["url"]?.jsonPrimitive?.content ?: ""
            "email" -> propObj["email"]?.jsonPrimitive?.content ?: ""
            "phone_number" -> propObj["phone_number"]?.jsonPrimitive?.content ?: ""
            else -> ""
        }
    }

    private fun formatWeather(weatherObj: JsonObject): String {
        val lat = weatherObj["lat"]?.jsonPrimitive?.content ?: "N/A"
        val lon = weatherObj["lon"]?.jsonPrimitive?.content ?: "N/A"
        val timezone = weatherObj["timezone"]?.jsonPrimitive?.content ?: "N/A"
        val currentElement = weatherObj["current"]
        val current = when {
            currentElement == null || currentElement is JsonNull -> null
            currentElement is JsonObject -> currentElement
            else -> null
        }
        val daily = safeGetJsonArray(weatherObj["daily"])
        val hourly = safeGetJsonArray(weatherObj["hourly"])
        return buildString {
            appendLine("🌤️ Погода")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Координаты: $lat, $lon")
            appendLine("Часовой пояс: $timezone")
            if (current != null) {
                appendLine("\n📊 Текущая погода:")
                val temp = current["temp"]?.jsonPrimitive?.content ?: "N/A"
                val feelsLike = current["feels_like"]?.jsonPrimitive?.content ?: "N/A"
                val humidity = current["humidity"]?.jsonPrimitive?.content ?: "N/A"
                val pressure = current["pressure"]?.jsonPrimitive?.content ?: "N/A"
                val windSpeed = current["wind_speed"]?.jsonPrimitive?.content ?: "N/A"
                val windDeg = current["wind_deg"]?.jsonPrimitive?.content ?: "N/A"
                val clouds = current["clouds"]?.jsonPrimitive?.content ?: "N/A"
                val uvi = current["uvi"]?.jsonPrimitive?.content ?: "N/A"
                val weatherArray = safeGetJsonArray(current["weather"])
                val weatherDesc = weatherArray?.firstOrNull()?.jsonObject?.let { w ->
                    val main = w["main"]?.jsonPrimitive?.content ?: ""
                    val description = w["description"]?.jsonPrimitive?.content ?: ""
                    if (description.isNotEmpty()) description else main
                } ?: "N/A"
                appendLine("  Температура: ${temp}°C")
                appendLine("  Ощущается как: ${feelsLike}°C")
                appendLine("  Описание: $weatherDesc")
                appendLine("  Влажность: $humidity%")
                appendLine("  Давление: $pressure гПа")
                appendLine("  Скорость ветра: $windSpeed м/с")
                if (windDeg != "N/A") {
                    appendLine("  Направление ветра: $windDeg°")
                }
                appendLine("  Облачность: $clouds%")
                appendLine("  UV индекс: $uvi")
            }
            if (daily != null && daily.isNotEmpty()) {
                appendLine("\n📅 Прогноз на 8 дней:")
                daily.take(3).forEachIndexed { index, day ->
                    if (day is JsonNull || day !is JsonObject) {
                        return@forEachIndexed
                    }
                    val dayObj = day
                    val dt = dayObj["dt"]?.jsonPrimitive?.content?.toLongOrNull()
                    val tempElement = dayObj["temp"]
                    val tempObj = when {
                        tempElement == null || tempElement is JsonNull -> null
                        tempElement is JsonObject -> tempElement
                        else -> null
                    }
                    val tempMax = tempObj?.get("max")?.jsonPrimitive?.content ?: "N/A"
                    val tempMin = tempObj?.get("min")?.jsonPrimitive?.content ?: "N/A"
                    val weatherArray = safeGetJsonArray(dayObj["weather"])
                    val weatherDesc = weatherArray?.firstOrNull()?.let { weatherItem ->
                        when (weatherItem) {
                            is JsonNull -> null
                            is JsonObject -> {
                                weatherItem["description"]?.jsonPrimitive?.content
                                    ?: weatherItem["main"]?.jsonPrimitive?.content
                                    ?: ""
                            }

                            else -> null
                        }
                    } ?: "N/A"
                    val dayLabel = when (index) {
                        0 -> "Сегодня"
                        1 -> "Завтра"
                        else -> "День ${index + 1}"
                    }
                    appendLine("  $dayLabel: $tempMin°C / $tempMax°C, $weatherDesc")
                }
            }
            if (hourly != null && hourly.isNotEmpty()) {
                appendLine("\n⏰ Почасовой прогноз (первые 6 часов):")
                hourly.take(6).forEachIndexed { index, hour ->
                    if (hour is JsonNull || hour !is JsonObject) {
                        return@forEachIndexed
                    }
                    val hourObj = hour
                    val temp = hourObj["temp"]?.jsonPrimitive?.content ?: "N/A"
                    val weatherArray = safeGetJsonArray(hourObj["weather"])
                    val weatherDesc = weatherArray?.firstOrNull()?.let { weatherItem ->
                        when (weatherItem) {
                            is JsonNull -> null
                            is JsonObject -> weatherItem["description"]?.jsonPrimitive?.content ?: ""
                            else -> null
                        }
                    } ?: "N/A"
                    appendLine("  Через ${index + 1}ч: $temp°C, $weatherDesc")
                }
            }
        }
    }
}
