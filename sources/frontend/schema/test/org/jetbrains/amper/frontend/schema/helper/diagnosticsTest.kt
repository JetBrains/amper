/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.psi.PsiFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.test.golden.GoldenTest
import org.jetbrains.amper.test.golden.readContentsAndReplace
import org.jetbrains.amper.test.golden.trimTrailingWhitespacesAndEmptyLines
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

fun GoldenTest.diagnosticsTest(
    caseName: String,
    additionalFiles: List<String> = emptyList()
) = DiagnosticsTestRun(caseName, baseTestResourcesPath(), additionalFiles).doTest()

class DiagnosticsTestRun(
    caseName: String,
    override val base: Path,
    private val additionalPaths: List<String>,
) : BaseFrontendTestRun(caseName) {

    override val expectPostfix: String = ".yaml"
    override val expectAmperPostfix: String = ".amper"

    override fun GoldenTest.getInputContent(inputPath: Path): String {
        // Fix paths, so they will point to resources.
        val readCtx = FrontendPathResolver(
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
            transformPsiFile = PsiFile::removeDiagnosticAnnotations
        )
        val buildDirFile = readCtx.loadVirtualFile(buildDir())
        val inputFile = readCtx.loadVirtualFile(inputPath.absolute())
        val cleared = readCtx.toPsiFile(inputFile)!!.text
        val additionalFiles = additionalPaths.map { readCtx.loadVirtualFile((base / it).absolute()) }

        with(problemReporter) {
            val moduleFiles = listOf(inputFile).plus(additionalFiles).sortedBy { it.path }
            val projectContext = TestProjectContext(buildDirFile, moduleFiles, readCtx)
            projectContext.readProjectModel()
        }
        // Collect errors.
        val errors = problemReporter.problems
        val annotated = annotateTextWithDiagnostics(inputPath.absolute(), cleared, errors) {
            it.replace(buildDir().absolutePathString() + File.separator, "")
        }
        return annotated.trimTrailingWhitespacesAndEmptyLines()
    }

    override fun GoldenTest.getExpectContent(expectedPath: Path) =
        readContentsAndReplace(expectedPath, base).trimTrailingWhitespacesAndEmptyLines()
}
