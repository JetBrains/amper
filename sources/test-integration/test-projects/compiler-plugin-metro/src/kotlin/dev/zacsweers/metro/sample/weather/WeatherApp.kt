// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.sample.weather

import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.table
import dev.zacsweers.metro.Inject
import kotlin.time.Instant
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Inject
class WeatherApp(private val repository: WeatherRepository) {
  suspend operator fun invoke(query: String, log: (String, isError: Boolean) -> Unit) {
    byLocation(query)
      .onSuccess { weather ->
        val location = weather.location
        val message = buildString {
          appendLine("Weather for ${location.name}, ${location.region ?: location.country}:")

          val current = weather.current
          appendLine("\nCurrent conditions:")
          appendLine("Temperature: ${current.temperature}°C")
          appendLine("Humidity: ${current.humidity}%")
          appendLine("Wind Speed: ${current.windSpeed} km/h")
          appendLine("Description: ${current.description}")

          appendLine("\nHourly forecast:")

          val hourlyTable = formatHourlyForecast(weather.hourlyForecast)
          appendLine(hourlyTable)
        }
        log(message, false)
      }
      .onFailure { error -> log("Error fetching weather: ${error.message}", true) }
  }

  private suspend fun byLocation(query: String): Result<WeatherInfo> = coroutineScope {
    try {
      val locations = repository.searchLocation(query).getOrThrow()
      if (locations.isEmpty()) {
        Result.failure(NoSuchElementException("Location not found: $query"))
      } else {
        val location = locations.first()
        val weather = repository.getWeather(location.latitude, location.longitude).getOrThrow()

        Result.success(
          WeatherInfo(
            location =
              LocationInfo(
                name = location.name,
                region = location.region,
                country = location.country,
              ),
            current =
              CurrentWeatherInfo(
                temperature = weather.current.temperature,
                humidity = weather.current.humidity,
                windSpeed = weather.current.windSpeed,
                description = getWeatherDescription(weather.current.weatherCode),
              ),
            hourlyForecast =
              weather.hourly.time.zip(
                weather.hourly.temperatures.zip(weather.hourly.weatherCodes)
              ) { time, (temp, code) ->
                HourlyForecastInfo(
                  time = LocalDateTime.parse(time).toInstant(TimeZone.UTC),
                  temperature = temp,
                  description = getWeatherDescription(code),
                )
              },
          )
        )
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun getWeatherDescription(code: Int): String =
    when (code) {
      0 -> "Clear sky"
      1,
      2,
      3 -> "Partly cloudy"
      45,
      48 -> "Foggy"
      51,
      53,
      55 -> "Drizzle"
      61,
      63,
      65 -> "Rain"
      71,
      73,
      75 -> "Snow"
      77 -> "Snow grains"
      80,
      81,
      82 -> "Rain showers"
      85,
      86 -> "Snow showers"
      95 -> "Thunderstorm"
      96,
      99 -> "Thunderstorm with hail"
      else -> "Unknown"
    }

  private fun formatHourlyForecast(forecast: List<HourlyForecastInfo>): String {
    return table {
        cellStyle {
          border = true
          alignment = TextAlignment.MiddleCenter
        }

        header {
          row {
            cell("Time") { alignment = TextAlignment.MiddleCenter }
            cell("Temperature")
            cell("Conditions") { alignment = TextAlignment.MiddleCenter }
          }
        }

        forecast.take(24).forEach { hour ->
          val localTime = hour.time.toLocalDateTime(TimeZone.currentSystemDefault())
          val timeStr =
            "${localTime.hour.toString().padStart(2, '0')}:${
          localTime.minute.toString().padStart(2, '0')
        }"

          row {
            cell(timeStr)
            cell("${hour.temperature}°C")
            cell(hour.description)
          }
        }
      }
      .toString()
  }
}

data class WeatherInfo(
  val location: LocationInfo,
  val current: CurrentWeatherInfo,
  val hourlyForecast: List<HourlyForecastInfo>,
)

data class LocationInfo(val name: String, val region: String?, val country: String)

data class CurrentWeatherInfo(
  val temperature: Double,
  val humidity: Double,
  val windSpeed: Double,
  val description: String,
)

data class HourlyForecastInfo(val time: Instant, val temperature: Double, val description: String)
