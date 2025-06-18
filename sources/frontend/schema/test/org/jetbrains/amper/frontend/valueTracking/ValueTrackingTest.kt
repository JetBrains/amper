/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.valueTracking

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.descendants
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.api.linkedAmperValue
import org.jetbrains.amper.frontend.schema.helper.BaseFrontendTestRun
import org.jetbrains.amper.frontend.schema.helper.ModifiablePsiIntelliJApplicationConfigurator
import org.jetbrains.amper.frontend.schema.helper.TestProjectContext
import org.jetbrains.amper.test.golden.GoldenTest
import org.jetbrains.amper.test.golden.GoldenTestBase
import org.jetbrains.amper.test.golden.readContentsAndReplace
import org.jetbrains.amper.test.golden.trimTrailingWhitespacesAndEmptyLines
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Ignore

@Ignore
class ValueTrackingTest : GoldenTestBase(Path("testResources") / "valueTracking") {
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

    @Test
    fun `test with template overrides`() {
        trackingTest("with-template-overrides", propertyName = "enabled")
    }
}


private fun GoldenTest.trackingTest(caseName: String,
                                        propertyName: String = "settings",
                                        customPostfix: String? = null)
    = TrackingTestRun(caseName, baseTestResourcesPath(), propertyName, customPostfix).doTest()

private class TrackingTestRun(
    caseName: String,
    override val base: Path,
    private val propertyName: String,
    private val customPostfix: String? = null
) : BaseFrontendTestRun(caseName) {

    override val expectPostfix: String
        get() = customPostfix ?: super.expectPostfix

    override fun GoldenTest.getInputContent(inputPath: Path): String {
        // Fix paths, so they will point to resources.
        val readCtx = FrontendPathResolver(
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator
        )
        val buildDirFile = readCtx.loadVirtualFile(buildDir())
        val inputFile = readCtx.loadVirtualFile(inputPath)
        val psiFile = readCtx.toPsiFile(inputFile) ?: error("no psi file")

        with(ctx) { doBuild(TestProjectContext(buildDirFile, listOf(inputFile), readCtx), DefaultSystemInfo) }
        val queriedNode = psiFile.descendants().filterIsInstance<PsiNamedElement>().firstOrNull { it.name == propertyName }!!
        val linkedValue = queriedNode.getUserData(linkedAmperValue)
        return tracesInfo(linkedValue, psiFile, null, emptySet(), TracesPresentation.Tests)
            .trimTrailingWhitespacesAndEmptyLines()
    }

    override fun GoldenTest.getExpectContent(expectedPath: Path) =
        readContentsAndReplace(expectedPath, base).trimTrailingWhitespacesAndEmptyLines()
}