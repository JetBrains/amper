/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TestTask
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.TestResultsFormat
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.test.FilterMode
import org.jetbrains.amper.test.TestFilter
import kotlin.io.path.pathString

class NativeTestTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    private val projectRoot: AmperProjectRoot,
    private val commonRunSettings: CommonRunSettings,
    override val platform: Platform,
) : TestTask {
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        DeadLockMonitor.disable()

        val compileTaskResult = dependenciesResult.filterIsInstance<NativeLinkTask.Result>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val executable = compileTaskResult.linkedBinary

        return spanBuilder("native-test")
            .setAttribute("executable", executable.pathString)
            .use { span ->
                val workingDir = module.source.moduleDir ?: projectRoot.path

                val result = BuildPrimitives.runProcessAndGetOutput(
                    workingDir = workingDir,
                    command = buildList {
                        add(executable.pathString)
                        val testFilterArg = testFilterArg()
                        if (testFilterArg != null) {
                            add(testFilterArg)
                        }
                        val ktestLogger = when (commonRunSettings.testResultsFormat) {
                            TestResultsFormat.Pretty -> "gtest"
                            TestResultsFormat.TeamCity -> "teamcity"
                        }
                        add("--ktest_logger=$ktestLogger")
                    },
                    span = span,
                    // We need to respect the exact output of the tests, so we shouldn't go through the Mordant
                    // terminal, because it processes line wrapping and tab conversions to spaces with tab stops.
                    // This can break TeamCity messages for instance.
                    outputListener = ProcessOutputListener.Streaming(),
                )
                if (result.exitCode != 0) {
                    userReadableError("Kotlin/Native $platform tests failed for module '${module.userReadableName}' with exit code ${result.exitCode} (see errors above)")
                }

                object : TaskResult {}
            }
    }

    // see CLI args by running the test executable help, or check there:
    // https://code.jetbrains.team/p/kt/repositories/kotlin/files/df027063420af0abd48c64ef598b1c5b0b5d7b1b/kotlin-native/runtime/src/main/kotlin/kotlin/native/internal/test/TestRunner.kt?tab=source&line=188&lines-count=34
    private fun testFilterArg(): String? {
        if (commonRunSettings.testFilters.isEmpty()) {
            return null
        }
        val nativeFilters = commonRunSettings.testFilters.map { it.toKNativeTestFilter() }
        val includeFilters = nativeFilters.filter { it.mode == FilterMode.Include }.joinToString(":") { it.pattern }
        val excludeFilters = nativeFilters.filter { it.mode == FilterMode.Exclude }.joinToString(":") { it.pattern }

        val filterArgValue = when {
            excludeFilters.isEmpty() -> includeFilters
            else -> "$includeFilters-$excludeFilters"
        }
        return "--ktest_filter=$filterArgValue"
    }
}

private data class KNativeTestFilter(val pattern: String, val mode: FilterMode)

private fun TestFilter.toKNativeTestFilter(): KNativeTestFilter = when (this) {
    is TestFilter.SpecificTestInclude -> KNativeTestFilter(
        pattern = toKotlinNativeFormat(),
        mode = FilterMode.Include,
    )
    is TestFilter.SuitePattern -> KNativeTestFilter(
        pattern = "${pattern.replace('/', '.')}.*",
        mode = mode,
    )
}

private fun TestFilter.SpecificTestInclude.toKotlinNativeFormat(): String {
    val nestedClassSuffix = if (nestedClassName != null) ".$nestedClassName" else ""
    return "$suiteFqn$nestedClassSuffix.$testName"
}
