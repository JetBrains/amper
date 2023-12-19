/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.frontend.old.helper.BuildFileAware
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import java.nio.file.Path


context(TestWithBuildFile)
fun doTestWithInput(caseName: String, postfix: String, input: () -> String) =
    OnlyExpectTestRun(caseName, postfix, input).doTest()

class OnlyExpectTestRun(
    caseName: String,
    postfix: String,
    private val input: () -> String,
) : BaseTestRun(caseName) {
    override val expectPostfix = postfix
    context(BuildFileAware, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path) = input()
}