/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.old.helper.TestBase
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

context(TestBase)
fun diagnosticsTest(caseName: String, systemInfo: SystemInfo = DefaultSystemInfo) =
    DiagnosticsTestRun(caseName, systemInfo, baseTestResourcesPath).doTest()

class DiagnosticsTestRun(
    caseName: String,
    private val systemInfo: SystemInfo,
    override val base: Path,
) : BaseTestRun(caseName) {

    context(TestBase, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path): String {
        // Fix paths, so they will point to resources.
        val readCtx = FrontendPathResolver(
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
            transformPsiFile = PsiFile::removeDiagnosticAnnotations
        )
        val cleared = readCtx.path2PsiFile(inputPath)!!.text

        doBuild(readCtx, TestFioContext(buildDir, listOf(inputPath)) ,systemInfo)

        // Collect errors.
        val errors = problemReporter.getErrors()
        val annotated = annotateTextWithDiagnostics(cleared, errors) {
            it.replace(buildDir.absolutePathString() + File.separator, "")
        }
        return annotated.trimTrailingWhitespacesAndEmptyLines()
    }

    context(TestBase, TestProblemReporterContext)
    override fun getExpectContent(inputPath: Path, expectedPath: Path) =
        readContentsAndReplace(inputPath, base).trimTrailingWhitespacesAndEmptyLines()
}