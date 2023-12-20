/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.ismVisitor.accept
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schemaConverter.convertModuleViaSnake
import org.jetbrains.amper.frontend.schemaConverter.psi.standalone.convertModulePsi
import java.io.File
import java.nio.file.Path
import kotlin.io.path.reader
import kotlin.test.assertEquals
import kotlin.test.fail

fun moduleConvertTest(
    caseName: String,
    expectedErrors: List<String> = emptyList(),
    expectedModule: Module? = null
) {
    moduleConvertTestImpl(caseName, expectedErrors, expectedModule = expectedModule, convertFn = { path ->
        convertModuleViaSnake(path)
    })
}

fun moduleConvertPsiTest(
    caseName: String,
    expectedErrors: List<String> = emptyList(),
    expectedModule: Module? = null
) {
    moduleConvertTestImpl(caseName, expectedErrors, expectedModule = expectedModule, convertFn = { path ->
        convertModulePsi(path.reader())
    })
}

private fun moduleConvertTestImpl(
    caseName: String,
    expectedErrors: List<String> = emptyList(),
    expectedModule: Module? = null,
    convertFn: ProblemReporterContext.(Path) -> Module
) {
    val file = getTestDataResource("$caseName.yaml")
    val ctx = TestProblemReporterContext()
    val module = with(ctx) {
        with (ctx) {
            convertFn(file.toPath())
        }
    }
    assertEquals(ctx.problemReporter.getErrors().map { it.message }, expectedErrors)
    expectedModule?.accept(EqualsVisitor(module))
}

private class TestProblemReporter : CollectingProblemReporter() {
    override fun doReportMessage(message: BuildProblem) {}
    fun getErrors(): List<BuildProblem> = problems.filter { it.level == Level.Error }
}

private class TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter()
}

internal fun getTestDataResource(testDataFileName: String): File {
    val file = File(".").absoluteFile
        .resolve("testResources/$testDataFileName")
    if (!file.exists()) fail("Resource $testDataFileName not found. Looked at ${file.canonicalPath}")
    return file
}