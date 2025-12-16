package org.example.weather

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenMeteoClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 30000
        }
    }
    private val baseUrl = "https://api.open-meteo.com/v1/forecast"

    suspend fun getCurrentWeather(latitude: Double, longitude: Double): OpenMeteoWeatherResponse {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(latitude, longitude)
                val response = httpClient.get(url)
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw OpenMeteoException("HTTP ${response.status.value}: $errorBody")
                }
                val responseText = response.bodyAsText()
                val jsonObject = json.parseToJsonElement(responseText).jsonObject
                parseWeatherResponse(jsonObject, latitude, longitude)
            } catch (e: OpenMeteoException) {
                throw e
            } catch (e: Exception) {
                throw OpenMeteoException("Ошибка при получении данных о погоде: ${e.message}")
            }
        }
    }

    private fun buildUrl(latitude: Double, longitude: Double): String {
        val params = listOf(
            "latitude=$latitude",
            "longitude=$longitude",
            "current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m",
            "hourly=temperature_2m,weather_code",
            "daily=weather_code,temperature_2m_max,temperature_2m_min",
            "timezone=auto"
        )
        return "$baseUrl?${params.joinToString("&")}"
    }

    private fun parseWeatherResponse(
        jsonObject: kotlinx.serialization.json.JsonObject,
        latitude: Double,
        longitude: Double
    ): OpenMeteoWeatherResponse {
        val current = jsonObject["current"]?.jsonObject
        val hourly = jsonObject["hourly"]?.jsonObject
        val daily = jsonObject["daily"]?.jsonObject
        val timezone = jsonObject["timezone"]?.jsonPrimitive?.content ?: "UTC"
        val currentTimeArray = current?.get("time")?.jsonArray
        val currentTempArray = current?.get("temperature_2m")?.jsonArray
        val currentHumidityArray = current?.get("relative_humidity_2m")?.jsonArray
        val currentWeatherCodeArray = current?.get("weather_code")?.jsonArray
        val currentWindSpeedArray = current?.get("wind_speed_10m")?.jsonArray
        val currentWindDirectionArray = current?.get("wind_direction_10m")?.jsonArray
        val currentTime = currentTimeArray?.firstOrNull()?.jsonPrimitive?.content ?: ""
        val currentTemp = currentTempArray?.firstOrNull()?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val currentHumidity = currentHumidityArray?.firstOrNull()?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val currentWeatherCode = currentWeatherCodeArray?.firstOrNull()?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val currentWindSpeed = currentWindSpeedArray?.firstOrNull()?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val currentWindDirection = currentWindDirectionArray?.firstOrNull()?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val hourlyTimes = hourly?.get("time")?.jsonArray?.mapNotNull { it.jsonPrimitive.content }?.take(24) ?: emptyList()
        val hourlyTemps = hourly?.get("temperature_2m")?.jsonArray?.mapNotNull { it.jsonPrimitive.content.toDoubleOrNull() }?.take(24) ?: emptyList()
        val dailyTimes = daily?.get("time")?.jsonArray?.mapNotNull { it.jsonPrimitive.content }?.take(7) ?: emptyList()
        val dailyMaxTemps = daily?.get("temperature_2m_max")?.jsonArray?.mapNotNull { it.jsonPrimitive.content.toDoubleOrNull() }?.take(7) ?: emptyList()
        val dailyMinTemps = daily?.get("temperature_2m_min")?.jsonArray?.mapNotNull { it.jsonPrimitive.content.toDoubleOrNull() }?.take(7) ?: emptyList()
        val dailyWeatherCodes = daily?.get("weather_code")?.jsonArray?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }?.take(7) ?: emptyList()
        return OpenMeteoWeatherResponse(
            latitude = latitude,
            longitude = longitude,
            timezone = timezone,
            current = CurrentWeatherData(
                time = currentTime,
                temperature = currentTemp,
                humidity = currentHumidity,
                weatherCode = currentWeatherCode,
                windSpeed = currentWindSpeed,
                windDirection = currentWindDirection
            ),
            hourly = hourlyTimes.zip(hourlyTemps).map { (time, temp) ->
                OpenMeteoHourlyForecast(time = time, temperature = temp)
            },
            daily = dailyTimes.mapIndexed { index, time ->
                OpenMeteoDailyForecast(
                    time = time,
                    maxTemperature = dailyMaxTemps.getOrNull(index) ?: 0.0,
                    minTemperature = dailyMinTemps.getOrNull(index) ?: 0.0,
                    weatherCode = dailyWeatherCodes.getOrNull(index) ?: 0
                )
            }
        )
    }

    fun close() {
        httpClient.close()
    }
}

