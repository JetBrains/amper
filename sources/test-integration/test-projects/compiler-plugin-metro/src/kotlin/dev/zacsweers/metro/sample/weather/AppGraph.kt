// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.sample.weather

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

interface AppGraph {

  val app: WeatherApp

  @Provides @SingleIn(AppScope::class) fun provideJson(): Json = Json { ignoreUnknownKeys = true }

  @SingleIn(AppScope::class)
  @Provides
  fun httpClient(httpClientEngineFactory: HttpClientEngineFactory<*>, json: Json): HttpClient =
    HttpClient(httpClientEngineFactory) {
      expectSuccess = true
      install(HttpRequestRetry) {
        retryOnExceptionOrServerErrors(maxRetries = 2)
        exponentialDelay()
      }
      install(ContentNegotiation) { json(json) }
    }
}
