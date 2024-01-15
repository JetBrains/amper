/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.frontend.old.helper.TestBase
import java.nio.file.Path
import kotlin.io.path.Path


context(TestBase)
fun doTestWithInput(caseName: String, postfix: String, base: Path = Path("testResources"), input: () -> String) =
    OnlyExpectTestRun(caseName, postfix, input, base).doTest()

class OnlyExpectTestRun(
    caseName: String,
    postfix: String,
    private val input: () -> String,
    override val base: Path
) : BaseTestRun(caseName) {
    override val expectPostfix = postfix
    context(TestBase, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path) = input()
}