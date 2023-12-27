/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.ReaderCtx
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.old.helper.TestBase
import java.io.StringReader
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText


context(TestBase)
fun aomTest(
    caseName: String,
    systemInfo: SystemInfo = DefaultSystemInfo,
    adjustCtx: TestFioContext.() -> Unit = {},
) = BuildAomTestRun(caseName, systemInfo, base, adjustCtx).doTest()

open class BuildAomTestRun(
    caseName: String,
    private val systemInfo: SystemInfo = DefaultSystemInfo,
    override val base: Path,
    private val adjustCtx: TestFioContext.() -> Unit = {},
) : BaseTestRun(caseName) {
    context(TestBase, TestProblemReporterContext)
    override fun getInputContent(inputPath: Path): String {
        // Fix paths, so they will point to resources.
        val processPath = Path(".").absolute().normalize()
        val testResourcesPath = processPath / base
        val readCtx = ReaderCtx {
            val path = it.absolute().normalize()
            val resolved = if (!path.startsWith(testResourcesPath)) {
                val relative = processPath.relativize(path)
                testResourcesPath.resolve(relative)
            } else path

            resolved.takeIf { resolved.exists() }
                ?.readText()?.removeDiagnosticsAnnotations()
                ?.let { StringReader(it) }
        }

        // Read module.

        val fioCtx = TestFioContext(buildDir, listOf(inputPath))
        fioCtx.adjustCtx()
        val module = doBuild(readCtx, fioCtx, systemInfo)?.first()

        // Check errors absence.
        assert(problemReporter.getErrors().isEmpty()) {
            "Expected no errors, but got ${problemReporter.getErrors()
                .joinToString(prefix = "\n\t", postfix = "\n", separator = "\n\t")}"
        }

        // Return module's textual representation.
        return module?.prettyPrint() ?: error("Could not read and parse")
    }

    context(TestBase, TestProblemReporterContext)
    override fun getExpectContent(inputPath: Path, expectedPath: Path) =
        readContentsAndReplace(expectedPath, base)
}