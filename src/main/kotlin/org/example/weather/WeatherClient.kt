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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WeatherClient(private val apiKey: String) {
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
    private val baseUrl = "https://api.openweathermap.org/data/2.5"

    suspend fun getCurrentWeather(lat: Double, lon: Double, units: String = "metric"): WeatherResponse {
        return withContext(Dispatchers.IO) {
            try {
                val currentUrl = "$baseUrl/weather?lat=$lat&lon=$lon&units=$units&appid=$apiKey"
                val forecastUrl = "$baseUrl/forecast?lat=$lat&lon=$lon&units=$units&appid=$apiKey"
                val currentResponse = httpClient.get(currentUrl)
                if (!currentResponse.status.isSuccess()) {
                    val errorBody = currentResponse.bodyAsText()
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString(WeatherErrorResponse.serializer(), errorBody)
                        when (errorResponse.cod) {
                            400 -> "Неверный запрос. Проверьте параметры (широта и долгота). Ошибка: ${errorResponse.message}"
                            401 -> "Не авторизован. Проверьте правильность API ключа. Ошибка: ${errorResponse.message}"
                            404 -> "Данные о погоде не найдены для указанных координат. Ошибка: ${errorResponse.message}"
                            429 -> "Превышен лимит запросов. Попробуйте позже. Ошибка: ${errorResponse.message}"
                            else -> "Ошибка API: ${errorResponse.message} (код: ${errorResponse.cod})"
                        }
                    } catch (e: Exception) {
                        "HTTP ${currentResponse.status.value}: $errorBody"
                    }
                    throw WeatherException(errorMessage)
                }
                val currentText = currentResponse.bodyAsText()
                val forecastResponse = httpClient.get(forecastUrl)
                val forecastText = if (forecastResponse.status.isSuccess()) {
                    forecastResponse.bodyAsText()
                } else {
                    "{}"
                }
                val currentData = json.parseToJsonElement(currentText).jsonObject
                val forecastData = if (forecastText != "{}") {
                    try {
                        val parsed = json.parseToJsonElement(forecastText)
                        if (parsed is kotlinx.serialization.json.JsonObject) {
                            parsed
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
                convertToWeatherResponse(currentData, forecastData, lat, lon)
            } catch (e: WeatherException) {
                throw e
            } catch (e: Exception) {
                throw WeatherException("Ошибка при получении данных о погоде: ${e.message}")
            }
        }
    }

    private fun safeGetJsonObject(element: kotlinx.serialization.json.JsonElement?): kotlinx.serialization.json.JsonObject? {
        return when {
            element == null || element is JsonNull -> null
            element is kotlinx.serialization.json.JsonObject -> element
            else -> null
        }
    }

    private fun safeGetJsonPrimitive(element: kotlinx.serialization.json.JsonElement?): kotlinx.serialization.json.JsonPrimitive? {
        return when {
            element == null || element is JsonNull -> null
            element is kotlinx.serialization.json.JsonPrimitive -> element
            else -> null
        }
    }

    private fun convertToWeatherResponse(
        currentData: kotlinx.serialization.json.JsonObject,
        forecastData: kotlinx.serialization.json.JsonObject?,
        lat: Double,
        lon: Double
    ): WeatherResponse {
        val current = safeGetJsonObject(currentData["main"])
        val weatherElement = currentData["weather"]
        val weatherArray = try {
            when {
                weatherElement == null || weatherElement is JsonNull -> null
                weatherElement is JsonArray -> weatherElement.firstOrNull()?.let {
                    if (it is kotlinx.serialization.json.JsonObject) it else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        val wind = safeGetJsonObject(currentData["wind"])
        val clouds = safeGetJsonObject(currentData["clouds"])
        val sys = safeGetJsonObject(currentData["sys"])
        val currentWeather = org.example.weather.CurrentWeather(
            dt = safeGetJsonPrimitive(currentData["dt"])?.content?.toLongOrNull() ?: 0L,
            sunrise = safeGetJsonPrimitive(sys?.get("sunrise"))?.content?.toLongOrNull(),
            sunset = safeGetJsonPrimitive(sys?.get("sunset"))?.content?.toLongOrNull(),
            temp = safeGetJsonPrimitive(current?.get("temp"))?.content?.toDoubleOrNull() ?: 0.0,
            feelsLike = safeGetJsonPrimitive(current?.get("feels_like"))?.content?.toDoubleOrNull() ?: 0.0,
            pressure = safeGetJsonPrimitive(current?.get("pressure"))?.content?.toIntOrNull() ?: 0,
            humidity = safeGetJsonPrimitive(current?.get("humidity"))?.content?.toIntOrNull() ?: 0,
            dewPoint = safeGetJsonPrimitive(current?.get("temp"))?.content?.toDoubleOrNull() ?: 0.0,
            uvi = 0.0,
            clouds = safeGetJsonPrimitive(clouds?.get("all"))?.content?.toIntOrNull() ?: 0,
            visibility = safeGetJsonPrimitive(currentData["visibility"])?.content?.toIntOrNull(),
            windSpeed = safeGetJsonPrimitive(wind?.get("speed"))?.content?.toDoubleOrNull() ?: 0.0,
            windDeg = safeGetJsonPrimitive(wind?.get("deg"))?.content?.toIntOrNull() ?: 0,
            windGust = safeGetJsonPrimitive(wind?.get("gust"))?.content?.toDoubleOrNull(),
            weather = listOf(
                org.example.weather.WeatherCondition(
                    id = safeGetJsonPrimitive(weatherArray?.get("id"))?.content?.toIntOrNull() ?: 0,
                    main = safeGetJsonPrimitive(weatherArray?.get("main"))?.content ?: "",
                    description = safeGetJsonPrimitive(weatherArray?.get("description"))?.content ?: "",
                    icon = safeGetJsonPrimitive(weatherArray?.get("icon"))?.content ?: ""
                )
            ),
            rain = null,
            snow = null
        )
        val listElement = forecastData?.get("list")
        val hourly = try {
            when {
                listElement == null || listElement is JsonNull -> {
                    emptyList()
                }
                listElement is JsonArray -> {
                    listElement.mapNotNull { item ->
                        try {
                            val itemObj = if (item is kotlinx.serialization.json.JsonObject) {
                                item
                            } else {
                                return@mapNotNull null
                            }
                            val main = safeGetJsonObject(itemObj["main"])
                            val weatherElement = itemObj["weather"]
                            val weather = try {
                                when {
                                    weatherElement == null || weatherElement is JsonNull -> null
                                    weatherElement is JsonArray -> weatherElement.firstOrNull()?.let { 
                                        if (it is kotlinx.serialization.json.JsonObject) it else null
                                    }
                                    else -> null
                                }
                            } catch (e: Exception) {
                                null
                            }
                            val wind = safeGetJsonObject(itemObj["wind"])
                            val clouds = safeGetJsonObject(itemObj["clouds"])
            org.example.weather.HourlyForecast(
                dt = safeGetJsonPrimitive(itemObj["dt"])?.content?.toLongOrNull() ?: 0L,
                temp = safeGetJsonPrimitive(main?.get("temp"))?.content?.toDoubleOrNull() ?: 0.0,
                feelsLike = safeGetJsonPrimitive(main?.get("feels_like"))?.content?.toDoubleOrNull() ?: 0.0,
                pressure = safeGetJsonPrimitive(main?.get("pressure"))?.content?.toIntOrNull() ?: 0,
                humidity = safeGetJsonPrimitive(main?.get("humidity"))?.content?.toIntOrNull() ?: 0,
                dewPoint = safeGetJsonPrimitive(main?.get("temp"))?.content?.toDoubleOrNull() ?: 0.0,
                uvi = 0.0,
                clouds = safeGetJsonPrimitive(clouds?.get("all"))?.content?.toIntOrNull() ?: 0,
                visibility = null,
                windSpeed = safeGetJsonPrimitive(wind?.get("speed"))?.content?.toDoubleOrNull() ?: 0.0,
                windDeg = safeGetJsonPrimitive(wind?.get("deg"))?.content?.toIntOrNull() ?: 0,
                windGust = safeGetJsonPrimitive(wind?.get("gust"))?.content?.toDoubleOrNull(),
                weather = listOf(
                    org.example.weather.WeatherCondition(
                        id = safeGetJsonPrimitive(weather?.get("id"))?.content?.toIntOrNull() ?: 0,
                        main = safeGetJsonPrimitive(weather?.get("main"))?.content ?: "",
                        description = safeGetJsonPrimitive(weather?.get("description"))?.content ?: "",
                        icon = safeGetJsonPrimitive(weather?.get("icon"))?.content ?: ""
                    )
                ),
                pop = safeGetJsonPrimitive(itemObj["pop"])?.content?.toDoubleOrNull() ?: 0.0,
                rain = null,
                snow = null
            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                else -> {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
        val timezone = safeGetJsonPrimitive(currentData["timezone"])?.content?.toIntOrNull()?.let { 
            "UTC${if (it >= 0) "+" else ""}${it / 3600}"
        } ?: "UTC"
        return org.example.weather.WeatherResponse(
            lat = lat,
            lon = lon,
            timezone = timezone,
            timezoneOffset = safeGetJsonPrimitive(currentData["timezone"])?.content?.toIntOrNull() ?: 0,
            current = currentWeather,
            minutely = null,
            hourly = hourly,
            daily = null,
            alerts = null
        )
    }

    fun close() {
        httpClient.close()
    }
}
