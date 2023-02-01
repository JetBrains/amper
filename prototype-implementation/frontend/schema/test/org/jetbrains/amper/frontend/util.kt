/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.parser.parseModule
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

fun moduleParseTest(
    caseName: String,
    expectedErrors: List<String> = emptyList(),
) {
    val file = getTestDataResource("$caseName.yaml")
    val ctx = TestProblemReporterContext()
    with(ctx) {
        val module = parseModule(file.toPath())
        module
    }
    assertEquals(ctx.problemReporter.getErrors().map { it.message }, expectedErrors)
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