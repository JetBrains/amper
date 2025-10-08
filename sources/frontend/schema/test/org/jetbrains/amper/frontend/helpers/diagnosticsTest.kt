/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helpers

import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.test.golden.trimTrailingWhitespacesAndEmptyLines
import java.io.File
import kotlin.io.path.div
import kotlin.io.path.pathString

fun FrontendTestCaseBase.diagnosticsTest(
    caseName: String,
    additionalFiles: List<String> = emptyList(),
    additionalProblemChecks: (List<BuildProblem>) -> Unit = {},
) = DiagnosticsTestRun(caseName, this, additionalFiles, additionalProblemChecks).doTest()

class DiagnosticsTestRun(
    caseName: String,
    testCase: FrontendTestCaseBase,
    private val additionalPaths: List<String>,
    private val additionalProblemChecks: (List<BuildProblem>) -> Unit,
) : FrontendTest(caseName, testCase) {

    override val expectPostfix: String = ".yaml"

    override fun doTest() {
        super.doTest()
        additionalProblemChecks(problemReporter.problems)
    }

    override fun getInputContent(): String {
        // Fix paths, so they will point to the resources.
        val inputFile = pathResolver.loadVirtualFile(inputPath)
        val cleared = pathResolver.toPsiFile(inputFile)!!.text
        val additionalModules = additionalPaths.map { pathResolver.loadVirtualFile((base / it)) }

        with(problemReporter) {
            val moduleFiles = listOf(inputFile).plus(additionalModules).sortedBy { it.path }
            projectContext.copy(amperModuleFiles = moduleFiles).readProjectModel(pluginData = emptyList())
        }

        // Collect errors.
        val annotated = annotateTextWithDiagnostics(inputPath, cleared, problemReporter.problems) {
            it.replace(buildDir.pathString + File.separator, "")
        }
        return annotated.trimTrailingWhitespacesAndEmptyLines()
    }
}
