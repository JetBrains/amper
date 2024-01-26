/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.amper.backend.test.assertions.FilteredSpans
import org.jetbrains.amper.backend.test.assertions.assertHasAttribute
import org.jetbrains.amper.backend.test.assertions.spansNamed
import org.jetbrains.amper.backend.test.assertions.withAttribute
import org.jetbrains.amper.backend.test.extensions.LogCollectorExtension
import org.jetbrains.amper.backend.test.extensions.OpenTelemetryCollectorExtension
import org.jetbrains.amper.backend.test.extensions.StdStreamCollectorExtension
import org.jetbrains.amper.backend.test.extensions.StderrCollectorExtension
import org.jetbrains.amper.backend.test.extensions.StdoutCollectorExtension
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.diagnostics.getAttribute
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.tinylog.Level
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class IntegrationTestBase {
    @TempDir
    private lateinit var tempDir: Path

    private val tempRoot: Path by lazy {
        // Always run tests in a directory with space, tests quoting in a lot of places
        tempDir.resolve("space test")
    }

    @RegisterExtension
    protected val logCollector = LogCollectorExtension()

    @RegisterExtension
    protected val stdoutCollector: StdStreamCollectorExtension = StdoutCollectorExtension()

    @RegisterExtension
    protected val stderrCollector: StdStreamCollectorExtension = StderrCollectorExtension()

    @RegisterExtension
    protected val openTelemetryCollector = OpenTelemetryCollectorExtension()

    protected val kotlinJvmCompilationSpans: FilteredSpans
        get() = openTelemetryCollector.spansNamed("kotlin-compilation")

    private val userCacheRoot: AmperUserCacheRoot = AmperUserCacheRoot(TestUtil.userCacheRoot)

    protected fun setupTestProject(testProjectPath: Path, copyToTemp: Boolean): ProjectContext {
        require(testProjectPath.exists()) { "Test project is missing at $testProjectPath" }

        val projectRoot = if (copyToTemp) testProjectPath.copyToTempRoot() else testProjectPath
        return ProjectContext(
            projectRoot = AmperProjectRoot(projectRoot),
            projectTempRoot = AmperProjectTempRoot(tempRoot.resolve("projectTemp")),
            userCacheRoot = userCacheRoot,
            buildOutputRoot = AmperBuildOutputRoot(tempRoot.resolve("buildOutput")),
        )
    }

    protected fun resetCollectors() {
        logCollector.reset()
        stdoutCollector.reset()
        stderrCollector.reset()
        openTelemetryCollector.reset()
    }

    @OptIn(ExperimentalPathApi::class)
    private fun Path.copyToTempRoot(): Path = tempRoot.resolve(fileName).also { dir ->
        dir.createDirectories()
        copyToRecursively(target = dir, followLinks = true, overwrite = false)
    }

    protected fun assertInfoLogStartsWith(msgPrefix: String) = assertLogStartsWith(msgPrefix, level = Level.INFO)

    protected fun assertLogStartsWith(msgPrefix: String, level: Level) {
        assertTrue("Log message with level=$level and starting with '$msgPrefix' was not found") {
            logCollector.entries.any { it.level == level && it.message.startsWith(msgPrefix) }
        }
    }

    protected fun assertInfoLogContains(msgPrefix: String) = assertLogContains(msgPrefix, level = Level.INFO)

    protected fun assertLogContains(text: String, level: Level) {
        assertTrue("Log message with level=$level and containing '$text' was not found") {
            logCollector.entries.any { it.level == level && text in it.message }
        }
    }

    protected fun assertStdoutContains(text: String) {
        assertTrue("No line in stdout contains the text '$text'") {
            stdoutCollector.capturedText().lineSequence().any { text in it }
        }
    }

    protected fun assertKotlinJvmCompilationSpan(assertions: KotlinJvmCompilationSpanAssertions.() -> Unit = {}) {
        val kotlinSpan = kotlinJvmCompilationSpans.assertSingle()
        KotlinJvmCompilationSpanAssertions(kotlinSpan).assertions()
    }
}

private val amperModuleKey = AttributeKey.stringKey("amper-module")
private val compilerArgsKey = AttributeKey.stringArrayKey("compiler-args")

fun FilteredSpans.withAmperModule(name: String) = withAttribute(amperModuleKey, name)

class KotlinJvmCompilationSpanAssertions(private val span: SpanData) {
    private val compilerArgs: List<String>
        get() = span.getAttribute(compilerArgsKey)

    fun hasAmperModule(name: String) {
        span.assertHasAttribute(amperModuleKey, name)
    }

    fun hasCompilerArgument(argument: String) {
        assertTrue("Compiler argument '$argument' is missing. Actual args: $compilerArgs") {
            compilerArgs.contains(argument)
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
        compilerArgs.zipWithNext().firstOrNull { it.first == previous }?.second
}
