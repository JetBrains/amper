/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.DefaultModel
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.diagnostics.AomModelDiagnosticFactories
import org.jetbrains.amper.frontend.old.helper.TestBase
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

fun TestBase.diagnosticsTest(
    caseName: String,
    systemInfo: SystemInfo = DefaultSystemInfo,
    vararg levels: Level = arrayOf(Level.Error, Level.Fatal),
    additionalFiles: List<String> = emptyList()
) =
    DiagnosticsTestRun(caseName, systemInfo, baseTestResourcesPath, levels, additionalFiles).doTest()

class DiagnosticsTestRun(
    caseName: String,
    private val systemInfo: SystemInfo,
    override val base: Path,
    private val levels: Array<out Level>,
    private val additionalPaths: List<String>,
) : BaseTestRun(caseName) {

    override fun TestBase.getInputContent(inputPath: Path): String {
        // Fix paths, so they will point to resources.
        val readCtx = FrontendPathResolver(
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
            transformPsiFile = PsiFile::removeDiagnosticAnnotations
        )
        val buildDirFile = readCtx.loadVirtualFile(buildDir)
        val inputFile = readCtx.loadVirtualFile(inputPath.absolute())
        val cleared = readCtx.toPsiFile(inputFile)!!.text
        val additionalFiles = additionalPaths.map { readCtx.loadVirtualFile((base / it).absolute()) }

        with(ctx) {
            val moduleFiles = listOf(inputFile).plus(additionalFiles).sortedBy { it.path }
            val projectContext = TestProjectContext(buildDirFile, moduleFiles, readCtx)
            val resultModules = doBuild(projectContext, systemInfo) ?: return@with
            val model = DefaultModel(projectContext.projectRootDir.toNioPath(), resultModules)
            AomModelDiagnosticFactories.forEach { diagnostic ->
                with(diagnostic) { model.analyze() }
            }
        }
        // Collect errors.
        val errors = with(ctx) { problemReporter.getDiagnostics(*levels) }
        val annotated = annotateTextWithDiagnostics(inputPath.absolute(), cleared, errors) {
            it.replace(buildDir.absolutePathString() + File.separator, "")
        }
        return annotated.trimTrailingWhitespacesAndEmptyLines()
    }

    override fun TestBase.getExpectContent(inputPath: Path, expectedPath: Path) =
        readContentsAndReplace(inputPath, base).trimTrailingWhitespacesAndEmptyLines()

    override val expectIsInput: Boolean = true
}
