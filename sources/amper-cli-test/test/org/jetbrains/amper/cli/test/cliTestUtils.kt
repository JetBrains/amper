/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.test.spans.FilteredSpans
import org.jetbrains.amper.test.spans.SpansTestCollector
import org.jetbrains.amper.test.spans.spansNamed
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.fileSize
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

inline fun runSlowTest(crossinline testBody: suspend TestScope.() -> Unit): TestResult =
    runTest(timeout = 15.minutes) { testBody() }

/**
 * Asserts that the directory at this [Path] contains all the files at the given [expectedRelativePaths].
 */
internal fun Path.assertContainsRelativeFiles(vararg expectedRelativePaths: String) {
    val actualFiles = walk()
        .onEach { assertTrue(it.fileSize() > 0, "File should not be empty: $it") }
        .map { it.relativeTo(this).joinToString("/") }
        .sorted()
        .toList()
    // comparing multi-line strings instead of lists for easier comparison of test failures
    assertEquals(expectedRelativePaths.joinToString("\n"), actualFiles.joinToString("\n"))
}

// FIXME this should never be needed, because task output paths should be internal.
//  User-visible artifacts should be placed in user-visible directories (use some convention).
internal fun AmperCliTestBase.AmperCliResult.getTaskOutputPath(taskName: String): Path =
    buildOutputRoot / "tasks" / taskName.replace(':', '_')

internal fun AmperCliTestBase.AmperCliResult.readTelemetrySpans(): SpansTestCollector {
    return object : SpansTestCollector {
        override val spans: List<SpanData> = telemetrySpans
        override fun clearSpans() = throw UnsupportedOperationException("Cannot modify deserialized spans")
    }
}

internal fun AmperCliTestBase.AmperCliResult.withTelemetrySpans(block: SpansTestCollector.() -> Unit) {
    readTelemetrySpans().apply(block)
}

internal val SpansTestCollector.xcodebuildSpans: FilteredSpans
    get() = spansNamed("xcodebuild")

internal val SpansTestCollector.iosKotlinTests: FilteredSpans
    get() = spansNamed("ios-kotlin-test")

internal val SpansTestCollector.konancSpans: FilteredSpans
    get() = spansNamed("konanc")

internal val SpansTestCollector.xcodeProjectGenSpans
    get() = spansNamed("xcode project generation")

internal val SpansTestCollector.xcodeProjectManagementSpans
    get() = spansNamed("xcode project management")

internal val UpdatedAttribute = AttributeKey.booleanKey("updated")
