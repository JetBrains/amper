/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.ismVisitor.accept
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.EqualsVisitor
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schemaConverter.psi.ConverterImpl
import java.nio.file.Path

fun TestBase.convertTest(caseName: String, expectedErrors: String = "", expectedModule: Module? = null) =
    ConvertTestRun(caseName, expectedErrors, baseTestResourcesPath, expectedModule).doTest()

private class ConvertTestRun(
    caseName: String,
    private val expectedErrors: String = "",
    override val base: Path,
    private val expectedModule: Module? = null
) : BaseTestRun(caseName) {
    override fun TestBase.getInputContent(inputPath: Path): String {
        val module = with(ctx) {
            val pathResolver = FrontendPathResolver(
                intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
            )
            val inputParentFile = pathResolver.loadVirtualFile(inputPath.parent)
            val inputFile = pathResolver.loadVirtualFile(inputPath)
            ConverterImpl(inputParentFile, pathResolver, ctx.problemReporter)
                .convertModule(inputFile)!!
        }
        TestTraceValidationVisitor().visit(module)
        expectedModule?.accept(EqualsVisitor(module))
        return ctx.problemReporter.getDiagnostics().joinToString { it.message }
    }

    override fun TestBase.getExpectContent(inputPath: Path, expectedPath: Path) = expectedErrors
}
