// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.sample.weather

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.engine.okhttp.OkHttpEngine
import okhttp3.OkHttpClient

@ContributesTo(AppScope::class)
interface JvmDataModule {
  companion object {
    @Provides
    @SingleIn(AppScope::class)
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClientEngine(okHttpClientLazy: Lazy<OkHttpClient>): HttpClientEngineFactory<*> {
      return object : HttpClientEngineFactory<OkHttpConfig> {
        override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine {
          return OkHttpEngine(
            OkHttpConfig().apply { preconfigured = okHttpClientLazy.value }.apply(block)
          )
        }
      }
    }
  }
}
