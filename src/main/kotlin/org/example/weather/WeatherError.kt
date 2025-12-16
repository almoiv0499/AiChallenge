package org.example.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherErrorResponse(
    val cod: Int,
    val message: String,
    val parameters: List<String>? = null
)

class WeatherException(message: String) : Exception(message)
