/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.downloader

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import kotlin.time.Duration.Companion.hours

// mostly based on intellij:community/platform/build-scripts/downloader/src/ktor.kt

val httpClient: HttpClient by lazy {
    HttpClient {
        expectSuccess = true

        install(ContentEncoding) {
            deflate(1.0F)
            gzip(0.9F)

            // Tells the server that no compression is also acceptable.
            // This is useful when a request is the download of a file that is already compressed.
            identity()
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay()
        }

        // has to be after HttpRequestRetry because we use retryOnTimeout
        install(HttpTimeout) {
            requestTimeoutMillis = 2.hours.inWholeMilliseconds
        }

        install(UserAgent) {
            agent = "JetBrains Amper"
        }
    }
}
