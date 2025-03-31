/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.samples.kodein

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import java.security.SecureRandom
import java.util.*

/**
 * An entry point of the embedded-server program:
 *
 * io.ktor.samples.kodein.KodeinSimpleApplicationKt.main
 *
 * This would start and wait a web-server at port 8080 using Netty,
 * and would load the 'myKodeinApp' ktor module.
 */
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::myKodeinApp).start(wait = true)
}

/**
 * The main and only module of the application.
 * This module creates a Kodein container and sets
 * maps a Random to a singleton based on SecureRandom.
 * And then configures the application.
 */
fun Application.myKodeinApp() = myKodeinApp(DI {
    bind<Random>() with singleton { SecureRandom() }
})

/**
 * This is the application module that has a
 * preconfigured [kodein] instance as input.
 *
 * The idea of this method, is that the different modules
 * can call this with several configured kodein variants
 * and also you can call it from the tests setting mocks
 * instead of the default mappings.
 */
fun Application.myKodeinApp(kodein: DI) {
    val random by kodein.instance<Random>()

    routing {
        get("/") {
            val range = 0 until 100
            call.respondText("Random number in $range: ${random[range]}")
        }
    }
}

/**
 * Convenience [Random] extension operator method to get a random integral value inside the specified [range].
 */
private operator fun Random.get(range: IntRange) = range.first + this.nextInt(range.last - range.first)