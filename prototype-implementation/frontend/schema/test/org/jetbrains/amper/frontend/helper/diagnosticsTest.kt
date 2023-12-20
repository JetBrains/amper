/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.ReaderCtx
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.old.helper.BuildFileAware
import org.jetbrains.amper.frontend.old.helper.TestBase
import java.io.File
import java.io.StringReader
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText


context(TestBase)
fun diagnosticsTest(caseName: String, systemInfo: SystemInfo = DefaultSystemInfo) =
    DiagnosticsTestRun(caseName, systemInfo, base).doTest()

class DiagnosticsTestRun(
    caseName: String,
    private val systemInfo: SystemInfo,
    override val base: Path,
) : BaseTestRun(caseName) {

    context(BuildFileAware, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path): String {
        // Fix paths, so they will point to resources.
        val processPath = Path(".").absolute().normalize()
        val testResourcesPath = processPath / base
        val cleared = inputPath.readText().removeDiagnosticsAnnotations()
        val readCtx = ReaderCtx {
            // If path is absolute - then we had read it internally within schema converter.
            // Then we need to adjust it to the testResources directory.
            val resolved = if (it.isAbsolute) {
                val relative = processPath.relativize(it.absolute())
                testResourcesPath.resolve(relative)
            } else it.absolute()

            resolved.takeIf { resolved.exists() }
                ?.readText()?.removeDiagnosticsAnnotations()
                ?.let { StringReader(it) }
        }

        doBuild(readCtx, listOf(inputPath), systemInfo = systemInfo)

        // Collect errors.
        val errors = problemReporter.getErrors()
        val annotated = annotateTextWithDiagnostics(cleared, errors) {
            it.replace(buildDir.absolutePathString() + File.separator, "")
        }
        return annotated.trimTrailingWhitespacesAndEmptyLines()
    }

    context(BuildFileAware, TestProblemReporterContext)
    override fun getExpectContent(inputPath: Path, expectedPath: Path) =
        readContentsAndReplace(inputPath, base).trimTrailingWhitespacesAndEmptyLines()
}