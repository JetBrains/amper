/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.ismVisitor.accept
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.EqualsVisitor
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.convertModule
import java.nio.file.Path

context(TestBase)
fun convertTest(caseName: String, expectedErrors: String = "", expectedModule: Module? = null) =
    ConvertTestRun(caseName, expectedErrors, baseTestResourcesPath, expectedModule).doTest()

class ConvertTestRun(
    caseName: String,
    private val expectedErrors: String = "",
    override val base: Path,
    private val expectedModule: Module? = null
) : BaseTestRun(caseName) {
    context(TestBase, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path): String {
        val module = with(ctx) {
            val pathResolver = FrontendPathResolver()
            val inputParentFile = pathResolver.loadVirtualFile(inputPath.parent)
            val inputFile = pathResolver.loadVirtualFile(inputPath)
            with(ConvertCtx(inputParentFile, pathResolver)) {
                convertModule(inputFile)!!
            }
        }
        TestTraceValidationVisitor().visit(module)
        expectedModule?.accept(EqualsVisitor(module))
        return ctx.problemReporter.getErrors().joinToString { it.message }
    }

    context(TestBase, TestProblemReporterContext)
    override fun getExpectContent(inputPath: Path, expectedPath: Path) = expectedErrors
}