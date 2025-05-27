/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.createSchemaNode
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.reading.readTree
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
            val buildCtx = BuildCtx(pathResolver, ctx)
            with(buildCtx) {
                val tree = readTree(inputFile, moduleAType) ?: error("No tree for $inputFile")
                createSchemaNode<Module>(tree as MapLikeValue<Refined>)
            }
        }
        TestTraceValidationVisitor().visit(module)
//        expectedModule?.accept(EqualsVisitor(module))
        return ctx.problemReporter.getDiagnostics().joinToString { it.message }
    }

    override fun TestBase.getExpectContent(inputPath: Path, expectedPath: Path) = expectedErrors
}
