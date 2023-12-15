/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.frontend.old.helper.BuildFileAware
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.test.assertEquals

/**
 * Base test, that derives standard and input paths from case name.
 */
open class BaseTestRun(
    protected val caseName: String,
) {
    open val base: Path = Path("testResources")
    open val expectPostfix: String = ".result.txt"
    open val inputPostfix: String = ".yaml"

    protected val ctx = TestProblemReporterContext()

    context(BuildFileAware, TestProblemReporterContext)
    open fun getInputContent(path: Path): String = path.readText()

    context(BuildFileAware, TestProblemReporterContext)
    open fun getExpectContent(path: Path): String = path.readText()

    context(TestWithBuildFile)
    open fun doTest() = with(ctx) {
        with(buildFile) {
            val input = base / "$caseName$inputPostfix"
            val inputContent = getInputContent(input)

            val expect = base / "$caseName$expectPostfix"
            val expectContent = getExpectContent(expect)

            assertEquals(expectContent, inputContent)
        }
    }
}