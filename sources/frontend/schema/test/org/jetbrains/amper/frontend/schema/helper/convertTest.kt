/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.createSchemaNode
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.test.golden.GoldenTest
import java.nio.file.Path

fun GoldenTest.convertTest(caseName: String, expectedErrors: String = "") =
    ConvertTestRun(caseName, expectedErrors, baseTestResourcesPath()).doTest()

private class ConvertTestRun(
    caseName: String,
    private val expectedErrors: String = "",
    override val base: Path,
) : BaseFrontendTestRun(caseName) {

    override val expectPostfix = ".yaml"

    override fun GoldenTest.getInputContent(inputPath: Path): String {
        val module = with(ctx) {
            val pathResolver = FrontendPathResolver(
                intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
            )
            val inputFile = pathResolver.loadVirtualFile(inputPath)
            val buildCtx = BuildCtx(pathResolver, ctx.problemReporter)
            with(buildCtx) {
                val tree = readTree(inputFile, moduleAType) ?: error("No tree for $inputFile")
                createSchemaNode<Module>(tree as MapLikeValue<Refined>)
            }
        }
        TestTraceValidationVisitor().visit(module)
        return ctx.problemReporter.getDiagnostics().joinToString { it.message }
    }

    override fun GoldenTest.getExpectContent(expectedPath: Path) = expectedErrors
}
