// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.sample.weather

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

@ContributesBinding(AppScope::class)
@Inject
class OpenMeteoApi(private val httpClient: HttpClient) : WeatherApi {
  private val forecastUrl = "https://api.open-meteo.com/v1/forecast"
  private val geocodingUrl = "https://geocoding-api.open-meteo.com/v1/search"

  override suspend fun getWeatherByCoordinates(
    latitude: Double,
    longitude: Double,
  ): WeatherResponse =
    httpClient
      .get(forecastUrl) {
        parameter("latitude", latitude)
        parameter("longitude", longitude)
        parameter("current", "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m")
        parameter("hourly", "temperature_2m,weather_code")
        parameter("timezone", "auto")
      }
      .body()

  override suspend fun getLocationBySearch(query: String): List<GeocodingResult> =
    httpClient
      .get(geocodingUrl) {
        parameter("name", query)
        parameter("count", 5)
        parameter("language", "en")
      }
      .body<GeocodingResponse>()
      .results
}
