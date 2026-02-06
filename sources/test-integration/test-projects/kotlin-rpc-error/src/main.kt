/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.rpc.krpc.ktor.client.*
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import java.net.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

fun main() = runBlocking {
    val httpClient = HttpClient { installKrpc() }
    val rpcClient = httpClient.rpc {
        url { takeFrom("ws://localhost:8080/awesome") }

        rpcConfig {
            serialization {
                json()
            }
        }
    }

    val service = rpcClient.withService<Any>() // should be a compiler error thanks to the plugin (because not annotated)
}
