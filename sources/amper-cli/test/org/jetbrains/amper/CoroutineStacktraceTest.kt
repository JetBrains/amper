/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.jetbrains.amper.diagnostics.CoroutinesDebug
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class CoroutineStacktraceTest {
    init {
        CoroutinesDebug.setupCoroutinesInstrumentation()
    }

    @Test
    fun smoke() {
        try {
            runBlocking {
                fun5()
            }
            fail("must throw")
        } catch (t: Throwable) {
            val stacktrace = t.stackTraceToString()
            stacktrace.assertContainsCallsTo("fun1", count = 1)
//            stacktrace.assertContainsCallsTo("fun2TailRec", count = 3) // can't do this yet, apparently
            stacktrace.assertContainsCallsTo("fun3", count = 1)
//            stacktrace.assertContainsCallsTo("fun4Inline", count = 1) // can't do this yet, apparently
            stacktrace.assertContainsCallsTo("fun5", count = 1)
        }
    }

    private fun String.assertContainsCallsTo(funName: String, count: Int) {
        val actualCount = lines().count { it.contains(".$funName") }
        assertTrue("should contain $count call(s) to $funName. Got $actualCount:\n$this") {
            actualCount == count
        }
    }

    suspend fun fun1() {
        yield()
        throw Exception("exception at ${System.currentTimeMillis()}")
    }

    tailrec suspend fun fun2TailRec(depth: Int = 2) {
        if (depth == 0) {
            fun1()
        } else {
            fun2TailRec(depth - 1)
        }
    }

    suspend fun fun3() {
        yield()
        fun2TailRec()
    }

    suspend inline fun fun4Inline() {
        yield()
        fun3()
    }

    suspend fun fun5() {
        yield()
        fun4Inline()
    }
}
