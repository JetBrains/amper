/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.spans

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.amper.telemetry.getAttribute
import kotlin.test.assertTrue
import kotlin.test.fail

class CompilationSpanAssertions(
    private val span: SpanData,
    private val compilerArgsAttributeKeyName: String,
) {
    private val compilerArgs: List<String>
        get() = span.getAttribute(AttributeKey.stringArrayKey(compilerArgsAttributeKeyName))

    fun hasAmperModule(name: String) {
        span.assertHasAttribute(amperModuleKey, name)
    }

    fun hasCompilerArgument(argument: String) {
        assertTrue("Compiler argument '$argument' is missing. Actual args: $compilerArgs") {
            compilerArgs.contains(argument)
        }
    }

    fun hasCompilerArgumentStartingWith(argumentPrefix: String) {
        assertTrue("Compiler argument starting with '$argumentPrefix' is missing. Actual args: $compilerArgs") {
            compilerArgs.any { it.startsWith(argumentPrefix) }
        }
    }

    fun hasCompilerArgument(name: String, expectedValue: String) {
        hasCompilerArgument(name)
        val actualValue = compilerArgAfter(name)
            ?: fail("Compiler argument '$name' has no value. Actual args: $compilerArgs")
        if (actualValue != expectedValue) {
            fail("Compiler argument '$name' has value '$actualValue', expected '$expectedValue'")
        }
    }

    private fun compilerArgAfter(previous: String): String? =
        compilerArgs.zipWithNext().lastOrNull { it.first == previous }?.second
}