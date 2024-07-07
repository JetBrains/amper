/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.backend.test.assertions.FilteredSpans
import org.jetbrains.amper.backend.test.assertions.spansNamed
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.diagnostics.getAttribute
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AmperIosProjectsTest : AmperIntegrationTestBase() {

    private val iosTestDataRoot: Path = TestUtil
        .amperSourcesRoot
        .resolve("amper-backend-test/testData/projects/ios")

    private fun TestCollector.setupIosTestProject(testProjectName: String): ProjectContext =
        setupTestProject(iosTestDataRoot.resolve(testProjectName), copyToTemp = false)

    private val TestCollector.xcodebuildSpans: FilteredSpans
        get() = spansNamed("xcodebuild")

    private val TestCollector.konancSpans: FilteredSpans
        get() = spansNamed("konanc")

    private fun FilteredSpans.assertZeroExitCode(times: Int = 1) = assertTimes(times).forEach {
        assertEquals(0, it.getAttribute(AttributeKey.longKey("exit-code")))
    }

    @Test
    @MacOnly
    fun `framework for simple for iosSimulatorArm64`() = runTestWithCollector {
        val projectName = "interop"
        val task = "frameworkIosSimulatorArm64"
        val projectContext = setupIosTestProject(projectName)
        AmperBackend(projectContext).runTask(projectName, task)
        konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    @MacOnly
    fun `framework for simple for iosArm64`() = runTestWithCollector {
        val projectName = "interop"
        val task = "frameworkIosArm64"
        val projectContext = setupIosTestProject(projectName)
        AmperBackend(projectContext).runTask(projectName, task)
        konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    @MacOnly
    fun `buildIosApp for simple for iosSimulatorArm64`() = runTestWithCollector {
        val projectName = "interop"
        val task = "buildIosAppIosSimulatorArm64"
        val projectContext = setupIosTestProject(projectName)
        AmperBackend(projectContext).runTask(projectName, task)
        xcodebuildSpans.assertZeroExitCode()
    }

    @Test
    @MacOnly
    fun `build for simple for iosSimulatorArm64`() = runTestWithCollector {
        val projectContext = setupIosTestProject("interop")
        AmperBackend(projectContext).build(setOf(Platform.IOS_SIMULATOR_ARM64))
        xcodebuildSpans.assertZeroExitCode()
    }

    @Test
    @MacOnly
    fun `framework for compose for iosSimulatorArm64`() = runTestWithCollector {
        val projectName = "compose"
        val task = "frameworkIosSimulatorArm64"
        val projectContext = setupIosTestProject(projectName)
        AmperBackend(projectContext).runTask(projectName, task)
        konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    @MacOnly
    fun `framework for compose for iosArm64`() = runTestWithCollector {
        val projectName = "compose"
        val task = "frameworkIosArm64"
        val projectContext = setupIosTestProject(projectName)
        AmperBackend(projectContext).runTask(projectName, task)
        konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    @MacOnly
    fun `buildIosApp for compose app for iosSimulatorArm64`() = runTestWithCollector {
        val projectName = "compose"
        val task = "buildIosAppIosSimulatorArm64"
        val projectContext = setupIosTestProject(projectName)
        AmperBackend(projectContext).runTask(projectName, task)
        xcodebuildSpans.assertZeroExitCode()
    }

    @Test
    @MacOnly
    fun `build for compose app for iosSimulatorArm64`() = runTestWithCollector {
        val projectContext = setupIosTestProject("compose")
        AmperBackend(projectContext).build(setOf(Platform.IOS_SIMULATOR_ARM64))
        xcodebuildSpans.assertZeroExitCode()
    }
}
