/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.test.assertEqualsIgnoreLineSeparator
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Base test, that derives standard and input paths from case name.
 */
open class BaseTestRun(
    protected val caseName: String,
) {
    open val base: Path get() = Path("testResources")
    open val expectPostfix: String = ".result.txt"
    open val inputPostfix: String = ".yaml"
    private val inputAmperPostfix: String = ".amper"

    protected val ctx = TestProblemReporterContext()

    open fun TestBase.getInputContent(inputPath: Path): String = inputPath.readText()

    open fun TestBase.getExpectContent(inputPath: Path, expectedPath: Path): String = expectedPath.readText()

    open val expectIsInput = false

    context(TestBase)
    open fun doTest() = with(ctx) {
        with(buildFile) {
            val input = base / "$caseName$inputPostfix"
            val inputContent = getInputContent(input)

            val expect = base / "$caseName$expectPostfix"
            val expectContent = getExpectContent(input, expect)
            assertEqualsIgnoreLineSeparator(expectContent, inputContent, if (expectIsInput) input else expect)

            val inputAmper = base / "$caseName$inputAmperPostfix"
            if (inputAmper.exists()) {
                problemReporter.clearAll()
                val inputAmperContent = getInputContent(inputAmper)

                val expectAmper = (base / "$caseName.result.amper.txt").takeIf { it.exists() }
                    ?: (base / "$caseName$expectPostfix")
                val expectAmperContent = getExpectContent(inputAmper, expectAmper)

                assertEqualsIgnoreLineSeparator(expectAmperContent, inputAmperContent, if (expectIsInput) inputAmper else expect)
            }
        }
    }
}
