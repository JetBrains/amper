/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.cli.CliEnvironmentInitializer
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class CoroutineStacktraceTest {
    init {
        CliEnvironmentInitializer.setupCoroutinesDebugProbes()
    }

    @Test
    @Ignore(value = "AMPER-396 CLI: Provide coroutine stacktraces")
    fun smoke() {
        try {
            runBlocking {
                fun3()
            }
            fail("must throw")
        } catch (t: Throwable) {
            val string = t.stackTraceToString()
            assertTrue(string.contains(".fun1"), "must contain .fun1: $string")
            assertTrue(string.contains(".fun2"), "must contain .fun2: $string")
            assertTrue(string.contains(".fun3"), "must contain .fun3: $string")
        }
    }

    suspend fun fun1() {
        delay(10)
        throw Exception("exception at ${System.currentTimeMillis()}")
    }

    suspend fun fun2() {
        fun1()
        delay(10)
    }

    suspend fun fun3() {
        fun2()
        delay(10)
    }
}
