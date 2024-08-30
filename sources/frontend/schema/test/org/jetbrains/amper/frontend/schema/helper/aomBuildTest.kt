/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.old.helper.TestBase
import java.nio.file.Path

fun TestBase.aomTest(
    caseName: String,
    systemInfo: SystemInfo = DefaultSystemInfo,
    adjustCtx: TestProjectContext.() -> Unit = {},
) = BuildAomTestRun(caseName, systemInfo, baseTestResourcesPath, adjustCtx).doTest()

open class BuildAomTestRun(
    caseName: String,
    private val systemInfo: SystemInfo = DefaultSystemInfo,
    override val base: Path,
    private val adjustCtx: TestProjectContext.() -> Unit = {},
) : BaseTestRun(caseName) {
    override fun TestBase.getInputContent(inputPath: Path): String {
        val readCtx = FrontendPathResolver(
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
            transformPsiFile = PsiFile::removeDiagnosticAnnotations,
        )

        // Read module.
        val buildDirFile = readCtx.loadVirtualFile(buildDir)
        val inputFile = readCtx.loadVirtualFile(inputPath)
        val fioCtx = TestProjectContext(buildDirFile, listOf(inputFile), readCtx)
        fioCtx.adjustCtx()

        val module = with(ctx) { doBuild(fioCtx, systemInfo)?.first() }

        // Check errors absence.
        with(ctx) {
            assert(problemReporter.getDiagnostics().isEmpty()) {
                "Expected no errors, but got ${
                    problemReporter.getDiagnostics()
                        .joinToString(prefix = "\n\t", postfix = "\n", separator = "\n\t")
                }"
            }
        }

        // Return module's textual representation.
        return module?.prettyPrintForGoldFile() ?: error("Could not read and parse")
    }

    override fun TestBase.getExpectContent(inputPath: Path, expectedPath: Path) =
        readContentsAndReplace(expectedPath, base)
}
