package org.example.weather

import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoWeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: CurrentWeatherData,
    val hourly: List<OpenMeteoHourlyForecast>,
    val daily: List<OpenMeteoDailyForecast>
)

@Serializable
data class CurrentWeatherData(
    val time: String,
    val temperature: Double,
    val humidity: Int,
    val weatherCode: Int,
    val windSpeed: Double,
    val windDirection: Int
)

@Serializable
data class OpenMeteoHourlyForecast(
    val time: String,
    val temperature: Double
)

@Serializable
data class OpenMeteoDailyForecast(
    val time: String,
    val maxTemperature: Double,
    val minTemperature: Double,
    val weatherCode: Int
)

class OpenMeteoException(message: String) : Exception(message)

