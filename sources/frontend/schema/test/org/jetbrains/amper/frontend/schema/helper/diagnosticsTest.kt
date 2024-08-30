/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.old.helper.TestBase
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun TestBase.diagnosticsTest(caseName: String, systemInfo: SystemInfo = DefaultSystemInfo,
                    vararg levels: Level = arrayOf(Level.Error, Level.Fatal)) =
    DiagnosticsTestRun(caseName, systemInfo, baseTestResourcesPath, levels).doTest()

class DiagnosticsTestRun(
    caseName: String,
    private val systemInfo: SystemInfo,
    override val base: Path,
    private val levels: Array<out Level>
) : BaseTestRun(caseName) {

    override fun TestBase.getInputContent(inputPath: Path): String {
        // Fix paths, so they will point to resources.
        val readCtx = FrontendPathResolver(
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
            transformPsiFile = PsiFile::removeDiagnosticAnnotations
        )
        val buildDirFile = readCtx.loadVirtualFile(buildDir)
        val inputFile = readCtx.loadVirtualFile(inputPath)
        val cleared = readCtx.toPsiFile(inputFile)!!.text

        with(ctx) {
            doBuild(TestProjectContext(buildDirFile, listOf(inputFile), readCtx), systemInfo)
        }
        // Collect errors.
        val errors = with(ctx) { problemReporter.getDiagnostics(*levels) }
        val annotated = annotateTextWithDiagnostics(cleared, errors) {
            it.replace(buildDir.absolutePathString() + File.separator, "")
        }
        return annotated.trimTrailingWhitespacesAndEmptyLines()
    }

    override fun TestBase.getExpectContent(inputPath: Path, expectedPath: Path) =
        readContentsAndReplace(inputPath, base).trimTrailingWhitespacesAndEmptyLines()

    override val expectIsInput: Boolean = true
}