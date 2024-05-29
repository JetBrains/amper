/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.downloader

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import kotlin.time.Duration.Companion.hours

// mostly based on intellij:community/platform/build-scripts/downloader/src/ktor.kt

val httpClient: HttpClient by lazy {
    // HttpTimeout is not used - CIO engine handles that
    HttpClient(CIO) {
        expectSuccess = true

        engine {
            requestTimeout = 2.hours.inWholeMilliseconds
        }

        install(ContentEncoding) {
            deflate(1.0F)
            gzip(0.9F)
        }

        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 3)
            exponentialDelay()
        }

        install(UserAgent) {
            agent = "JetBrains Amper"
        }
    }
}
