package org.example.tools

import kotlinx.coroutines.runBlocking
import org.example.models.OpenRouterPropertyDefinition
import org.example.models.OpenRouterTool
import org.example.models.OpenRouterToolParameters
import org.example.weather.OpenMeteoClient
import org.example.weather.OpenMeteoException

class WeatherTool : AgentTool {
    private val client = OpenMeteoClient()
    override val name = "get_weather"
    override val description = "Получить погоду"
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "latitude" to OpenRouterPropertyDefinition(
                    type = "number",
                    description = "Широта в градусах (от -90 до 90, например: 55.7558 для Москвы)"
                ),
                "longitude" to OpenRouterPropertyDefinition(
                    type = "number",
                    description = "Долгота в градусах (от -180 до 180, например: 37.6173 для Москвы)"
                )
            ),
            required = listOf("latitude", "longitude")
        )
    )
    override fun execute(arguments: Map<String, String>): String {
        val latitude = arguments["latitude"]?.toDoubleOrNull()
            ?: return "Ошибка: не указана или некорректна широта"
        val longitude = arguments["longitude"]?.toDoubleOrNull()
            ?: return "Ошибка: не указана или некорректна долгота"
        if (latitude !in -90.0..90.0) {
            return "Ошибка: широта должна быть в диапазоне от -90 до 90"
        }
        if (longitude !in -180.0..180.0) {
            return "Ошибка: долгота должна быть в диапазоне от -180 до 180"
        }
        return try {
            val weather = runBlocking { client.getCurrentWeather(latitude, longitude) }
            formatWeatherResponse(weather)
        } catch (e: OpenMeteoException) {
            "Ошибка при получении данных о погоде: ${e.message}"
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }
    private fun formatWeatherResponse(weather: org.example.weather.OpenMeteoWeatherResponse): String {
        val current = weather.current
        val weatherDescription = getWeatherDescription(current.weatherCode)
        val windDirection = getWindDirection(current.windDirection)
        val sb = StringBuilder()
        sb.appendLine("🌤️ Погода для координат (${weather.latitude}, ${weather.longitude})")
        sb.appendLine("📍 Часовой пояс: ${weather.timezone}")
        sb.appendLine()
        sb.appendLine("📊 Текущая погода:")
        sb.appendLine("   🌡️ Температура: ${String.format("%.1f", current.temperature)}°C")
        sb.appendLine("   💧 Влажность: ${current.humidity}%")
        sb.appendLine("   ☁️ Условия: $weatherDescription")
        sb.appendLine("   💨 Ветер: ${String.format("%.1f", current.windSpeed)} км/ч, направление: $windDirection")
        sb.appendLine("   🕐 Время: ${current.time}")
        if (weather.daily.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📅 Прогноз на 7 дней:")
            weather.daily.take(7).forEachIndexed { index, daily ->
                val dayDescription = getWeatherDescription(daily.weatherCode)
                sb.appendLine("   ${index + 1}. ${daily.time}: ${String.format("%.1f", daily.maxTemperature)}°C / ${String.format("%.1f", daily.minTemperature)}°C, $dayDescription")
            }
        }
        if (weather.hourly.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("⏰ Прогноз на ближайшие 24 часа (первые 6 часов):")
            weather.hourly.take(6).forEach { hourly ->
                sb.appendLine("   ${hourly.time}: ${String.format("%.1f", hourly.temperature)}°C")
            }
        }
        return sb.toString().trim()
    }
    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Ясно"
            1 -> "Преимущественно ясно"
            2 -> "Переменная облачность"
            3 -> "Пасмурно"
            45 -> "Туман"
            48 -> "Туман с инеем"
            51 -> "Легкая морось"
            53 -> "Умеренная морось"
            55 -> "Сильная морось"
            56 -> "Легкая ледяная морось"
            57 -> "Сильная ледяная морось"
            61 -> "Небольшой дождь"
            63 -> "Умеренный дождь"
            65 -> "Сильный дождь"
            66 -> "Легкий ледяной дождь"
            67 -> "Сильный ледяной дождь"
            71 -> "Небольшой снег"
            73 -> "Умеренный снег"
            75 -> "Сильный снег"
            77 -> "Снежные зерна"
            80 -> "Небольшой ливень"
            81 -> "Умеренный ливень"
            82 -> "Сильный ливень"
            85 -> "Небольшой снегопад"
            86 -> "Сильный снегопад"
            95 -> "Гроза"
            96 -> "Гроза с градом"
            99 -> "Сильная гроза с градом"
            else -> "Неизвестные условия (код: $code)"
        }
    }
    private fun getWindDirection(degrees: Int): String {
        val directions = listOf(
            "С", "ССВ", "СВ", "ВСВ", "В", "ВЮВ", "ЮВ", "ЮЮВ",
            "Ю", "ЮЮЗ", "ЮЗ", "ЗЮЗ", "З", "ЗСЗ", "СЗ", "ССЗ"
        )
        val index = ((degrees + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }
}





