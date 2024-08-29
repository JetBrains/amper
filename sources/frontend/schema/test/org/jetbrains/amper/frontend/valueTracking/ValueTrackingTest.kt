/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.valueTracking

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.descendants
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.api.linkedAmperValue
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.BaseTestRun
import org.jetbrains.amper.frontend.schema.helper.ModifiablePsiIntelliJApplicationConfigurator
import org.jetbrains.amper.frontend.schema.helper.TestProblemReporterContext
import org.jetbrains.amper.frontend.schema.helper.TestProjectContext
import org.jetbrains.amper.frontend.schema.helper.readContentsAndReplace
import org.jetbrains.amper.frontend.schema.helper.trimTrailingWhitespacesAndEmptyLines
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

class ValueTrackingTest : TestBase(Path("testResources") / "valueTracking") {
    @Test
    fun `test no template`() {
        trackingTest("no-template")
    }

    @Test
    fun `test with template`() {
        trackingTest("with-template")
    }

    @Test
    fun `test collection`() {
        trackingTest("collection")
    }
}


context(TestBase)
private fun trackingTest(caseName: String, systemInfo: SystemInfo = DefaultSystemInfo
) = TrackingTestRun(caseName, systemInfo, baseTestResourcesPath).doTest()

private class TrackingTestRun(
    caseName: String,
    private val systemInfo: SystemInfo,
    override val base: Path
) : BaseTestRun(caseName) {

    context(TestBase, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path): String {
        // Fix paths, so they will point to resources.
        val readCtx = FrontendPathResolver(
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator
        )
        val buildDirFile = readCtx.loadVirtualFile(buildDir)
        val inputFile = readCtx.loadVirtualFile(inputPath)
        val psiFile = readCtx.toPsiFile(inputFile) ?: throw IllegalStateException("no psi file")

        doBuild(TestProjectContext(buildDirFile, listOf(inputFile), readCtx), systemInfo)
        val settingsNode = psiFile.descendants().filterIsInstance<PsiNamedElement>().firstOrNull { it.name == "settings" }!!
        val linkedValue = settingsNode.getUserData(linkedAmperValue)
        return tracesInfo(linkedValue, psiFile, null, emptySet(), TracesPresentation.Tests)
            .trimTrailingWhitespacesAndEmptyLines()
    }

    context(TestBase, TestProblemReporterContext)
    override fun getExpectContent(inputPath: Path, expectedPath: Path) =
        readContentsAndReplace(expectedPath, base).trimTrailingWhitespacesAndEmptyLines()

    override val expectIsInput: Boolean = false
}