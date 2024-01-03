/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.frontend.ismVisitor.accept
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.EqualsVisitor
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schemaConverter.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.convertModule
import java.nio.file.Path
import kotlin.io.path.reader

context(TestBase)
fun convertTest(caseName: String, expectedErrors: String = "", usePsiConverter: Boolean = false, expectedModule: Module? = null) =
    ConvertTestRun(caseName, expectedErrors, base, usePsiConverter, expectedModule).doTest()

class ConvertTestRun(
    caseName: String,
    private val expectedErrors: String = "",
    override val base: Path,
    private val usePsiConverter: Boolean = false,
    private val expectedModule: Module? = null
) : BaseTestRun(caseName) {
    context(TestBase, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path): String {
        val module = with(ctx) {
            with(ConvertCtx(inputPath.parent)) {
                convertModule (usePsiConverter) { inputPath.reader() }
            }
        }
        TestTraceValidationVisitor().visit(module)
        expectedModule?.accept(EqualsVisitor(module))
        return ctx.problemReporter.getErrors().map { it.message }.joinToString()
    }

    context(TestBase, TestProblemReporterContext)
    override fun getExpectContent(inputPath: Path, expectedPath: Path) = expectedErrors
}