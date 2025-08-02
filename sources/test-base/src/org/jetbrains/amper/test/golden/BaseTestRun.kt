/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.golden

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.test.assertEqualsIgnoreLineSeparator
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.readText

/**
 * Base test, that derives standard and input paths from case name.
 */
abstract class BaseTestRun(
    protected val caseName: String,
) {
    open val base: Path get() = Path("testResources")
    open val expectPostfix: String = ".result.txt"

    protected val problemReporter = CollectingProblemReporter()

    abstract fun GoldenTest.getInputContent(inputPath: Path): String

    open fun GoldenTest.getExpectContent(expectedPath: Path): String =
        expectedPath.readText().replaceDefaultVersions()

    context(goldenTest: GoldenTest)
    protected fun doTest(expect: Path, input: Path = expect) {
        val inputContent = goldenTest.getInputContent(input)
        val expectContent = goldenTest.getExpectContent(expect)
        return assertEqualsIgnoreLineSeparator(expectContent, inputContent, expect)
    }

    context(_: GoldenTest)
    open fun doTest() = doTest(expect = base / "$caseName$expectPostfix")

}

fun String.replaceDefaultVersions() = this
    .replace("#kotlinVersion", UsedVersions.kotlinVersion)
    .replace("#composeDefaultVersion", UsedVersions.composeVersion)
