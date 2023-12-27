/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schemaConverter.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.convertModuleViaSnake
import java.nio.file.Path
import kotlin.io.path.reader

context(TestBase)
fun convertTest(caseName: String, expectedErrors: String = "") =
    ConvertTestRun(caseName, expectedErrors, base).doTest()

class ConvertTestRun(
    caseName: String,
    private val expectedErrors: String = "",
    override val base: Path,
) : BaseTestRun(caseName) {
    context(TestBase, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path): String {
        with(ctx) {
            with(ConvertCtx(inputPath.parent)) {
                convertModuleViaSnake { inputPath.reader() }
            }
        }
        return ctx.problemReporter.getErrors().map { it.message }.joinToString()
    }

    context(TestBase, TestProblemReporterContext)
    override fun getExpectContent(inputPath: Path, expectedPath: Path) = expectedErrors
}