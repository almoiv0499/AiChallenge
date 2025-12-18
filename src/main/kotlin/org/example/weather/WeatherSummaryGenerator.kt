package org.example.weather

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates weather summary with temperature and clothing recommendations.
 */
object WeatherSummaryGenerator {
    
    /**
     * Generates a summary from weather data including temperature and clothing recommendations.
     * 
     * @param weatherData The current weather data
     * @return A formatted summary string
     */
    fun generateSummary(weatherData: WeatherResponse): String {
        val current = weatherData.current ?: return "Погодные данные недоступны"
        
        val temp = current.temp
        val feelsLike = current.feelsLike
        val description = current.weather.firstOrNull()?.description ?: "неизвестно"
        val windSpeed = current.windSpeed
        val humidity = current.humidity
        
        val timestamp = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(current.dt),
            ZoneId.systemDefault()
        )
        val timeStr = timestamp.format(DateTimeFormatter.ofPattern("HH:mm"))
        
        val clothingRecommendation = generateClothingRecommendation(temp, feelsLike, windSpeed, humidity, description)
        
        return buildString {
            appendLine("🌤️ Погода на $timeStr")
            appendLine("Температура: ${temp.toInt()}°C (ощущается как ${feelsLike.toInt()}°C)")
            appendLine("Условия: $description")
            appendLine("Ветер: ${windSpeed.toInt()} м/с")
            appendLine("Влажность: $humidity%")
            appendLine()
            appendLine("👕 Рекомендации по одежде:")
            appendLine(clothingRecommendation)
        }
    }
    
    /**
     * Generates clothing recommendations based on weather conditions.
     */
    private fun generateClothingRecommendation(
        temp: Double,
        feelsLike: Double,
        windSpeed: Double,
        humidity: Int,
        description: String
    ): String {
        val effectiveTemp = feelsLike
        val isRainy = description.contains("дождь", ignoreCase = true) || 
                      description.contains("rain", ignoreCase = true) ||
                      description.contains("ливень", ignoreCase = true)
        val isSnowy = description.contains("снег", ignoreCase = true) || 
                      description.contains("snow", ignoreCase = true)
        val isWindy = windSpeed > 7.0
        val isHumid = humidity > 70
        
        return buildString {
            when {
                effectiveTemp < -10 -> {
                    appendLine("• Теплая зимняя куртка")
                    appendLine("• Шапка, шарф, перчатки")
                    appendLine("• Термобелье")
                    appendLine("• Теплая обувь")
                }
                effectiveTemp < 0 -> {
                    appendLine("• Зимняя куртка")
                    appendLine("• Шапка и перчатки")
                    appendLine("• Теплая обувь")
                }
                effectiveTemp < 10 -> {
                    appendLine("• Демисезонная куртка")
                    if (isWindy) appendLine("• Ветровка поверх")
                    appendLine("• Длинные брюки")
                    appendLine("• Закрытая обувь")
                }
                effectiveTemp < 20 -> {
                    appendLine("• Легкая куртка или кофта")
                    appendLine("• Длинные брюки или джинсы")
                    appendLine("• Легкая обувь")
                }
                effectiveTemp < 25 -> {
                    appendLine("• Легкая одежда (футболка, рубашка)")
                    appendLine("• Легкие брюки или шорты")
                    appendLine("• Легкая обувь")
                }
                else -> {
                    appendLine("• Легкая летняя одежда")
                    appendLine("• Шорты или легкие брюки")
                    appendLine("• Легкая обувь или сандалии")
                    if (isHumid) appendLine("• Легкая дышащая ткань")
                }
            }
            
            if (isRainy) {
                appendLine("• Дождевик или зонт")
                appendLine("• Водонепроницаемая обувь")
            }
            
            if (isSnowy) {
                appendLine("• Водонепроницаемая обувь")
                appendLine("• Теплые носки")
            }
            
            if (isWindy && effectiveTemp < 15) {
                appendLine("• Ветрозащитная одежда")
            }
        }
    }
}
