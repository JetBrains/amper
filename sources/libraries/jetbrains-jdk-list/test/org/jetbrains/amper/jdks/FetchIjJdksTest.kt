/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdks

import io.ktor.client.*
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FetchIjJdksTest {
    private val testHttpClient = HttpClient {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay()
        }

        // has to be after HttpRequestRetry because we use retryOnTimeout
        install(HttpTimeout) {
            connectTimeoutMillis = 5.seconds.inWholeMilliseconds
            requestTimeoutMillis = 2.minutes.inWholeMilliseconds
        }
    }

    @Test
    fun canFetchJdks() = runBlocking {
        val jdks = testHttpClient.fetchIjJdks()
        assert(jdks.isNotEmpty())
    }
}
