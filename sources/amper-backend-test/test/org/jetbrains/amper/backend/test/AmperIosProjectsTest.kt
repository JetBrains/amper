/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.amper.backend.test

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.amper.backend.test.assertions.FilteredSpans
import org.jetbrains.amper.backend.test.assertions.spansNamed
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.diagnostics.getAttribute
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.Test
import kotlin.test.assertEquals

class AmperIosProjectsTest : AmperIntegrationTestBase() {

    private val iosTestDataRoot: Path = TestUtil
        .amperSourcesRoot
        .resolve("amper-backend-test/testData/projects/ios")

    private fun setupIosTestProject(testProjectName: String, backgroundScope: CoroutineScope): ProjectContext =
        setupTestProject(iosTestDataRoot.resolve(testProjectName), copyToTemp = false, backgroundScope = backgroundScope)

    private suspend fun AmperBackend.runTask(vararg parts: String) = runTask(TaskName.fromHierarchy(parts.toList()))

    protected val xcodebuildSpans: FilteredSpans
        get() = openTelemetryCollector.spansNamed("xcodebuild")

    @Test
    fun `framework for simple for iosSimulatorArm64`() = runTestInfinitely {
        val projectName = "interop"
        val task = "frameworkIosSimulatorArm64"
        val projectContext = setupIosTestProject(projectName, backgroundScope = backgroundScope)
        AmperBackend(projectContext).runTask(projectName, task)
        assertStdoutContains("finished ':$projectName:$task'")
    }

    @Test
    fun `framework for simple for iosArm64`() = runTestInfinitely {
        val projectName = "interop"
        val task = "frameworkIosArm64"
        val projectContext = setupIosTestProject(projectName, backgroundScope = backgroundScope)
        AmperBackend(projectContext).runTask(projectName, task)
        assertStdoutContains("finished ':$projectName:$task'")
    }

    @Test
    fun `buildIosApp for simple for iosSimulatorArm64`() = runTestInfinitely {
        val projectName = "interop"
        val task = "buildIosAppIosSimulatorArm64"
        val projectContext = setupIosTestProject(projectName, backgroundScope = backgroundScope)
        AmperBackend(projectContext).runTask(projectName, task)
        assertStdoutContains("finished ':$projectName:$task'")
    }

    @Test
    fun `build for simple for iosSimulatorArm64`() = runTestInfinitely {
        val projectContext = setupIosTestProject("interop", backgroundScope = backgroundScope)
        AmperBackend(projectContext).compile(setOf(Platform.IOS_SIMULATOR_ARM64))
        val exitcode = xcodebuildSpans.assertSingle().getAttribute(AttributeKey.longKey("exit-code"))
        assertEquals(0, exitcode)
    }

    @Test
    fun `framework for compose for iosSimulatorArm64`() = runTestInfinitely {
        val projectName = "compose"
        val task = "frameworkIosSimulatorArm64"
        val projectContext = setupIosTestProject(projectName, backgroundScope = backgroundScope)
        AmperBackend(projectContext).runTask(projectName, task)
        assertStdoutContains("finished ':$projectName:$task'")
    }

    @Test
    fun `buildIosApp for compose app for iosSimulatorArm64`() = runTestInfinitely {
        val projectName = "compose"
        val task = "buildIosAppIosSimulatorArm64"
        val projectContext = setupIosTestProject(projectName, backgroundScope = backgroundScope)
        AmperBackend(projectContext).runTask(projectName, task)
        assertStdoutContains("finished ':$projectName:$task'")
    }

    @Test
    fun `build for compose app for iosSimulatorArm64`() = runTestInfinitely {
        val projectContext = setupIosTestProject("compose", backgroundScope = backgroundScope)
        AmperBackend(projectContext).compile(setOf(Platform.IOS_SIMULATOR_ARM64))
        val exitcode = xcodebuildSpans.assertSingle().getAttribute(AttributeKey.longKey("exit-code"))
        assertEquals(0, exitcode)
    }

}
