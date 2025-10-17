/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.aomBuilder.doReadProjectModel
import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import org.jetbrains.amper.frontend.helpers.readProjectContextWithTestFrontendResolver
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals

class RequiredValuesTest : FrontendTestCaseBase(Path("testResources") / "required-values") {
    @Test
    fun `invalid platforms`() {
        diagnosticsTest("0-invalid-platforms/module")
    }

    @Test
    fun `invalid repository url`() {
        diagnosticsTest("1-invalid-repository-url/module")
    }

    @Test
    fun `no credentials file`() {
        diagnosticsTest("2-no-credentials-file/module")
    }

    @Test
    fun `missing username key`() {
        diagnosticsTest("3-missing-username-key/module")
    }

    @Test
    fun `serialization enabled invalid`() {
        diagnosticsTest("4-serialization-enabled-invalid/module")
    }

    @Test
    fun `invalid modules`() {
        val testProjectDir = base.resolve("multi-module-with-invalids")
        val problemReporter = CollectingProblemReporter()
        val model = with(problemReporter) {
            val context = readProjectContextWithTestFrontendResolver(testProjectDir)
            context.doReadProjectModel(pluginData = emptyList(), mavenPluginXmls = emptyList())
        }
        val actual = model.unreadableModuleFiles.map { it.toNioPath().relativeTo(testProjectDir) }
        val expectedUnreadableModulePaths = listOf(Path("b/module.yaml"), Path("c/module.yaml"))
        assertEquals(expectedUnreadableModulePaths, actual)
        assertEquals(2, problemReporter.problems.size)
        assertEquals(1, model.modules.size) // module 'a'
    }
}