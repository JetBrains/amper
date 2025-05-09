/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.spans

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import kotlin.test.assertEquals
import kotlin.test.fail

val amperModuleKey: AttributeKey<String> = AttributeKey.stringKey("amper-module")

fun <T> SpanData.assertHasAttribute(key: AttributeKey<T>, value: T) {
    val actualValue = attributes[key]
        ?: fail("Attribute '$key' is missing in span '$name'")
    assertEquals(value, actualValue, "Wrong value for attribute '$key' in span $name: expected '$value' but was '$actualValue'")
}

fun SpansTestCollector.assertKotlinJvmCompilationSpan(assertions: CompilationSpanAssertions.() -> Unit = {}) {
    val kotlinSpan = kotlinJvmCompilationSpans.assertSingle()
    CompilationSpanAssertions(kotlinSpan, "compiler-args").assertions()
}

fun SpansTestCollector.assertEachKotlinJvmCompilationSpan(assertions: CompilationSpanAssertions.() -> Unit = {}) {
    kotlinJvmCompilationSpans.all().forEach { kotlinSpan ->
        CompilationSpanAssertions(kotlinSpan, "compiler-args").assertions()
    }
}

fun SpansTestCollector.assertJavaCompilationSpan(assertions: CompilationSpanAssertions.() -> Unit = {}) {
    val javacSpan = javaCompilationSpans.assertSingle()
    CompilationSpanAssertions(javacSpan, "args").assertions()
}

fun SpansTestCollector.assertEachKotlinNativeCompilationSpan(assertions: CompilationSpanAssertions.() -> Unit = {}) {
    kotlinNativeCompilationSpans.all().forEach { kotlinSpan ->
        CompilationSpanAssertions(kotlinSpan, "args").assertions()
    }
}