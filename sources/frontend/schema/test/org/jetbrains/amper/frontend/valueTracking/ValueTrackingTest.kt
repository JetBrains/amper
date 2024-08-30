/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.valueTracking

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.descendants
import org.jetbrains.amper.core.system.DefaultSystemInfo
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

    @Test
    fun `test collection from collection property`() {
        trackingTest("collection", propertyName = "freeCompilerArgs", customPostfix = ".fca.result.txt")
    }
}


context(TestBase)
private fun trackingTest(caseName: String,
                         propertyName: String = "settings",
                         fetchForValue: Boolean = false,
                         customPostfix: String? = null)
    = TrackingTestRun(caseName, baseTestResourcesPath, propertyName, fetchForValue, customPostfix).doTest()

private class TrackingTestRun(
    caseName: String,
    override val base: Path,
    private val propertyName: String,
    private val fetchForValue: Boolean = false,
    private val customPostfix: String? = null
) : BaseTestRun(caseName) {

    override val expectPostfix: String
        get() = customPostfix ?: super.expectPostfix

    context(TestBase, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path): String {
        // Fix paths, so they will point to resources.
        val readCtx = FrontendPathResolver(
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator
        )
        val buildDirFile = readCtx.loadVirtualFile(buildDir)
        val inputFile = readCtx.loadVirtualFile(inputPath)
        val psiFile = readCtx.toPsiFile(inputFile) ?: throw IllegalStateException("no psi file")

        doBuild(TestProjectContext(buildDirFile, listOf(inputFile), readCtx), DefaultSystemInfo)
        val queriedNode = psiFile.descendants().filterIsInstance<PsiNamedElement>().firstOrNull { it.name == propertyName }!!
        val linkedValue = queriedNode.getUserData(linkedAmperValue)
        return tracesInfo(linkedValue, psiFile, null, emptySet(), TracesPresentation.Tests)
            .trimTrailingWhitespacesAndEmptyLines()
    }

    context(TestBase, TestProblemReporterContext)
    override fun getExpectContent(inputPath: Path, expectedPath: Path) =
        readContentsAndReplace(expectedPath, base).trimTrailingWhitespacesAndEmptyLines()

    override val expectIsInput: Boolean = false
}