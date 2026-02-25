// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.sample.weather

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Super trivial repository for demonstration purposes. */
interface WeatherRepository {
  suspend fun searchLocation(query: String): Result<List<GeocodingResult>>

  suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherResponse>
}

@ContributesBinding(AppScope::class)
@Inject
class WeatherRepositoryImpl(private val weatherApi: WeatherApi) : WeatherRepository {
  override suspend fun searchLocation(query: String): Result<List<GeocodingResult>> = runCatching {
    weatherApi.getLocationBySearch(query)
  }

  override suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherResponse> =
    runCatching {
      weatherApi.getWeatherByCoordinates(latitude, longitude)
    }
}
