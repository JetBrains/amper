/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.tasks.NativeTestRunSettings
import org.jetbrains.amper.tasks.AllRunSettings
import org.jetbrains.amper.tasks.TestResultsFormat
import org.jetbrains.amper.test.FilterMode
import org.jetbrains.amper.test.TestFilter

/**
 * Converts these [AllRunSettings] to a list of arguments that will be passed to the Kotlin Native test executable.
 */
// see CLI args by running the test executable help, or check there:
// https://code.jetbrains.team/p/kt/repositories/kotlin/files/df027063420af0abd48c64ef598b1c5b0b5d7b1b/kotlin-native/runtime/src/main/kotlin/kotlin/native/internal/test/TestRunner.kt?tab=source&line=188&lines-count=34
internal fun NativeTestRunSettings.toNativeTestExecutableArgs(): List<String> = buildList {
    add("--ktest_logger=${testResultsFormat.ktestLoggerName}")

    val testFilterArg = testFilters.toTestFilterArg()
    if (testFilterArg != null) {
        add(testFilterArg)
    }
}

private val TestResultsFormat.ktestLoggerName: String
    get() = when (this) {
        TestResultsFormat.Pretty -> "gtest"
        TestResultsFormat.TeamCity -> "teamcity"
    }

private fun List<TestFilter>.toTestFilterArg(): String? {
    if (isEmpty()) {
        return null
    }
    val nativeFilters = map { it.toKNativeTestFilter() }
    val includeFilters = nativeFilters.filter { it.mode == FilterMode.Include }.joinToString(":") { it.pattern }
    val excludeFilters = nativeFilters.filter { it.mode == FilterMode.Exclude }.joinToString(":") { it.pattern }

    val filterArgValue = when {
        excludeFilters.isEmpty() -> includeFilters
        else -> "$includeFilters-$excludeFilters"
    }
    return "--ktest_filter=$filterArgValue"
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
