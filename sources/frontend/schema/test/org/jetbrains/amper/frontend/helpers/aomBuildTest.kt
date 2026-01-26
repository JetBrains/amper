/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helpers

import org.jetbrains.amper.frontend.aomBuilder.doReadProjectModel
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.system.info.SystemInfo
import kotlin.test.assertNotNull

fun FrontendTestCaseBase.aomTest(
    caseName: String,
    systemInfo: SystemInfo = SystemInfo.CurrentHost,
    expectedError: String? = null,
    printDefaults: Boolean = false,
    adjustCtx: TestProjectContext.() -> Unit = {},
) = BuildAomTestRun(caseName, this, systemInfo, expectedError, printDefaults, adjustCtx).doTest()

open class BuildAomTestRun(
    caseName: String,
    testCase: FrontendTestCaseBase,
    private val systemInfo: SystemInfo,
    private val expectedError: String? = null,
    private val printDefaults: Boolean,
    private val adjustCtx: TestProjectContext.() -> Unit = {},
) : FrontendTest(caseName, testCase) {

    override fun getInputContent(): String {
        // Read module.
        val fioCtx = projectContext.apply(adjustCtx)
        val module = with(problemReporter) {
            fioCtx.doReadProjectModel(
                pluginData = emptyList(),
                mavenPluginsWithXmls = emptyList(),
                systemInfo = systemInfo,
            ).modules.firstOrNull()
        }

        // Check errors absence.
        checkForExpectedErrors()

        // Return module's textual representation.
        return module?.prettyPrintForGoldFile(printDefaults) ?: error("Could not read and parse")
    }

    protected fun checkForExpectedErrors() =
        if (expectedError == null) assertNoReportedErrors()
        else {
            val diagnostic = problemReporter.problems.firstOrNull { it.message == expectedError }
            assertNotNull(
                diagnostic,
                "Expected an error $expectedError, but got ${
                    problemReporter.problems
                        .joinToString(prefix = "\n\t", postfix = "\n", separator = "\n\t") { it.message }
                }"
            )
            // Check that lazily initialized diagnostic source doesn't produce any error
            problemReporter.problems.forEach { (it as? PsiBuildProblem)?.source }
        }
}
