/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.valueTracking

import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.api.linkedAmperValue
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.ModifiablePsiIntelliJApplicationConfigurator
import org.jetbrains.amper.frontend.schema.helper.TestProblemReporterContext
import org.jetbrains.amper.frontend.schema.helper.TestProjectContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LinkedValuesTest : TestBase(Path("testResources") / "valueTracking") {

    // See: https://youtrack.jetbrains.com/issue/AMPER-3896
    @Test
    fun `check that linked value point to the latest ValueBase`() {
        // Do build
        val caretPsi = doBuildWithCaret(
            baseTestResourcesPath / "linked-value-is-the-latest.yaml",
            baseTestResourcesPath / "linked-value-is-the-latest-template.module-template.yaml"
        )

        // Extract caret key value element.
        val caretKeyValue = caretPsi?.parentOfType<YAMLKeyValue>(true) ?: fail("Caret element is not a YAMLKeyValue: ${caretPsi?.text}")
        val linkedValue = caretKeyValue.getUserData(linkedAmperValue)
        val valuesList = generateSequence(linkedValue) { it.trace?.precedingValue }.drop(1).toList()

        // Do assertions.
        assertEquals(2, valuesList.size, "Expected 2 preceding values, but got: ${valuesList.size}")
    }

    /**
     * Copy files, extracting `<caret>` from [fileToAnalyze] to the [buildDir] and then build AOM.
     *
     * @return [PsiElement] at the caret, if any
     */
    private fun doBuildWithCaret(
        fileToAnalyze: Path,
        vararg additionalFiles: Path,
    ): PsiElement? {
        // Extract caret.
        val (caret, newContent) = fileToAnalyze.readAndExtractCaret()
        caret ?: fail("No caret detected in $fileToAnalyze")
        val inputFilePath = buildDir.findOrCreateFile("module.yaml").apply { writeText(newContent) }
        additionalFiles.forEach { it.copyTo(buildDir / it.name) }

        // Do build model.
        val ctx = FrontendPathResolver(intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator)
        val buildDirFile = ctx.loadVirtualFile(buildDir)
        val inputFile = ctx.loadVirtualFile(inputFilePath)
        val testProjectContext = TestProjectContext(buildDirFile, listOf(inputFile), ctx)
        val problemCtx = TestProblemReporterContext()
        with(problemCtx) { doBuild(testProjectContext, DefaultSystemInfo) }
        val errorDiagnostics = problemCtx.problemReporter.getDiagnostics()
        assertTrue(errorDiagnostics.isEmpty(), "Expected no problems, but got: $errorDiagnostics")

        // Get psi element at the caret.
        val psiFile = ctx.toPsiFile(inputFile) ?: error("no psi file")
        return psiFile.findElementAt(caret)
    }

    /**
     * Read file and extract `<caret>` if any.
     *
     * @return Optional caret offset `to` file content without caret indicator
     */
    private fun Path.readAndExtractCaret(): Pair<Int?, String> {
        val caretIndicator = "<caret>"
        val content = readText().normalizeLineEndings()
        val caretOffset = content.indexOf(caretIndicator)
        return if (caretOffset == -1) null to content
        else caretOffset to content.removeRange(caretOffset, caretOffset + caretIndicator.length)
    }

    private val CR = "\r"
    private val LF = "\n"
    private val CRLF = "\r\n"
    fun String.normalizeLineEndings(): String {
        return this.replace(CRLF, LF)
            .replace(CR, LF)
    }
}