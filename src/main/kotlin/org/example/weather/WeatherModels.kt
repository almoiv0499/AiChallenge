package org.example.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    @SerialName("timezone_offset") val timezoneOffset: Int,
    val current: CurrentWeather? = null,
    val minutely: List<MinutelyForecast>? = null,
    val hourly: List<HourlyForecast>? = null,
    val daily: List<DailyForecast>? = null,
    val alerts: List<WeatherAlert>? = null
)

@Serializable
data class CurrentWeather(
    val dt: Long,
    val sunrise: Long? = null,
    val sunset: Long? = null,
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    val pressure: Int,
    val humidity: Int,
    @SerialName("dew_point") val dewPoint: Double,
    val uvi: Double,
    val clouds: Int,
    val visibility: Int? = null,
    @SerialName("wind_speed") val windSpeed: Double,
    @SerialName("wind_deg") val windDeg: Int,
    @SerialName("wind_gust") val windGust: Double? = null,
    val weather: List<WeatherCondition>,
    val rain: RainData? = null,
    val snow: SnowData? = null
)

@Serializable
data class WeatherCondition(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

@Serializable
data class RainData(
    @SerialName("1h") val oneHour: Double? = null
)

@Serializable
data class SnowData(
    @SerialName("1h") val oneHour: Double? = null
)

@Serializable
data class MinutelyForecast(
    val dt: Long,
    val precipitation: Double
)

@Serializable
data class HourlyForecast(
    val dt: Long,
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    val pressure: Int,
    val humidity: Int,
    @SerialName("dew_point") val dewPoint: Double,
    val uvi: Double,
    val clouds: Int,
    val visibility: Int? = null,
    @SerialName("wind_speed") val windSpeed: Double,
    @SerialName("wind_deg") val windDeg: Int,
    @SerialName("wind_gust") val windGust: Double? = null,
    val weather: List<WeatherCondition>,
    val pop: Double,
    val rain: RainData? = null,
    val snow: SnowData? = null
)

@Serializable
data class DailyForecast(
    val dt: Long,
    val sunrise: Long? = null,
    val sunset: Long? = null,
    val moonrise: Long? = null,
    val moonset: Long? = null,
    @SerialName("moon_phase") val moonPhase: Double,
    val summary: String? = null,
    val temp: TemperatureRange,
    @SerialName("feels_like") val feelsLike: FeelsLikeRange,
    val pressure: Int,
    val humidity: Int,
    @SerialName("dew_point") val dewPoint: Double,
    @SerialName("wind_speed") val windSpeed: Double,
    @SerialName("wind_deg") val windDeg: Int,
    @SerialName("wind_gust") val windGust: Double? = null,
    val weather: List<WeatherCondition>,
    val clouds: Int,
    val uvi: Double,
    val pop: Double,
    val rain: Double? = null,
    val snow: Double? = null
)

@Serializable
data class TemperatureRange(
    val day: Double,
    val min: Double,
    val max: Double,
    val night: Double,
    val eve: Double,
    val morn: Double
)

@Serializable
data class FeelsLikeRange(
    val day: Double,
    val night: Double,
    val eve: Double,
    val morn: Double
)

@Serializable
data class WeatherAlert(
    @SerialName("sender_name") val senderName: String,
    val event: String,
    val start: Long,
    val end: Long,
    val description: String,
    val tags: List<String>? = null
)
