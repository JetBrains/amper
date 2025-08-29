/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.test.golden.GoldenTest
import org.jetbrains.amper.test.golden.readContentsAndReplace
import java.nio.file.Path
import kotlin.test.assertNotNull

fun GoldenTest.aomTest(
    caseName: String,
    systemInfo: SystemInfo = DefaultSystemInfo,
    expectedError: String? = null,
    adjustCtx: TestProjectContext.() -> Unit = {},
) = BuildAomTestRun(caseName, systemInfo, baseTestResourcesPath(), expectedError, adjustCtx).doTest()

open class BuildAomTestRun(
    caseName: String,
    private val systemInfo: SystemInfo,
    override val base: Path,
    private val expectedError: String? = null,
    private val adjustCtx: TestProjectContext.() -> Unit = {},
) : BaseFrontendTestRun(caseName) {
    override fun GoldenTest.getInputContent(inputPath: Path): String {
        val readCtx = FrontendPathResolver(
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
            transformPsiFile = PsiFile::removeDiagnosticAnnotations,
        )

        // Read module.
        val buildDirFile = readCtx.loadVirtualFile(buildDir())
        val inputFile = readCtx.loadVirtualFile(inputPath)
        val fioCtx = TestProjectContext(buildDirFile, listOf(inputFile), readCtx)
        fioCtx.adjustCtx()

        val module = with(problemReporter) { doBuild(fioCtx, systemInfo)?.first() }

        // Check errors absence.
        if (expectedError == null) {
            assert(problemReporter.problems.isEmpty()) {
                "Expected no errors, but got ${
                    problemReporter.problems
                        .joinToString(prefix = "\n\t", postfix = "\n", separator = "\n\t") { it.message }
                }"
            }
        } else {
            val diagnostic = problemReporter.problems.firstOrNull { it.message == expectedError }
            assertNotNull(
                diagnostic,
                "Expected an error $expectedError, but got ${
                    problemReporter.problems
                        .joinToString(prefix = "\n\t", postfix = "\n", separator = "\n\t") { it.message }
                }"
            )
            // Check that lazily initialized diagnostic source doesn't produce any error
            problemReporter.problems.forEach { (it as? PsiBuildProblem)?.source }
        }

        // Return module's textual representation.
        return module?.prettyPrintForGoldFile() ?: error("Could not read and parse")
    }

    override fun GoldenTest.getExpectContent(expectedPath: Path) =
        this.readContentsAndReplace(expectedPath, base)
}
