/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

val client = HttpClient(CIO) {

    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        retryOnException(maxRetries = 3, retryOnTimeout = true)
        exponentialDelay()
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 10000000
        requestTimeoutMillis = 60000
        socketTimeoutMillis = 1000000
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
val dispatcher = Dispatchers.IO.limitedParallelism(10)

fun main() {
    runBlocking {
        coroutineScope {
            for (i in 1..100) {
                launch(dispatcher) {
                    val startTime = System.currentTimeMillis()
                    val response: HttpResponse =
                        client.get("https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-iossimulatorarm64/0.8.4/skiko-iossimulatorarm64-0.8.4.klib")
                    println("${response.status}: ${System.currentTimeMillis() - startTime}ms")
                }
            }
        }
    }
}