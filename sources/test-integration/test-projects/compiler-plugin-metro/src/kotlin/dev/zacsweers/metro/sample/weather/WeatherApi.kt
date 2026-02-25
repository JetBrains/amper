// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.sample.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface WeatherApi {
  suspend fun getWeatherByCoordinates(latitude: Double, longitude: Double): WeatherResponse

  suspend fun getLocationBySearch(query: String): List<GeocodingResult>
}

@Serializable data class WeatherResponse(val current: CurrentWeather, val hourly: HourlyForecast)

@Serializable
data class CurrentWeather(
  @SerialName("temperature_2m") val temperature: Double,
  @SerialName("relative_humidity_2m") val humidity: Double,
  @SerialName("weather_code") val weatherCode: Int,
  @SerialName("wind_speed_10m") val windSpeed: Double,
)

@Serializable
data class HourlyForecast(
  val time: List<String>,
  @SerialName("temperature_2m") val temperatures: List<Double>,
  @SerialName("weather_code") val weatherCodes: List<Int>,
)

@Serializable data class GeocodingResponse(val results: List<GeocodingResult>)

@Serializable
data class GeocodingResult(
  val name: String,
  val latitude: Double,
  val longitude: Double,
  val country: String,
  @SerialName("admin1") val region: String? = null,
)
