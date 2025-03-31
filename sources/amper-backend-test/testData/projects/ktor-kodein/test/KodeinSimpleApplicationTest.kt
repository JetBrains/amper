/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.samples.kodein

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.util.*

/**
 * Integration tests for the [myKodeinApp] module from KodeinSimpleApplication.
 */
class KodeinSimpleApplicationTest {
    /**
     * A test that creates the application with a custom [Kodein]
     * that maps to a custom [Random] instance.
     *
     * This [Random] always returns a constant value '7'.
     * We then call the single defined entry point to verify
     * that when used the Random generator the result is predictable and constant.
     *
     * Additionally, we test that the [Random.next] function has been called once.
     */
    @Test
    fun testProvideFakeRandom() {
        val log = arrayListOf<String>()
        testApplication {
            /** Calls the [myKodeinApp] module with a [Random] class that always return 7. */
            application {
                myKodeinApp(DI {
                    bind<Random>() with singleton {
                        object : Random() {
                            override fun next(bits: Int): Int = 7.also { log += "Random.next" }
                        }
                    }
                })
            }

            /**
             * Checks that the single route, returns a constant value '7' from the mock,
             * and that the [Random.next] has been called just once
             */
            val response = client.get("/")
            assertEquals("Random number in 0..99: 7", response.bodyAsText())
            assertEquals(listOf("Random.next"), log)
        }
    }
}