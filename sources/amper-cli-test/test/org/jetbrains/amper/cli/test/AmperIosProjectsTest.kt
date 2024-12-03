/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.diagnostics.getAttribute
import org.jetbrains.amper.test.CliSpanCollector.Companion.runCliTestWithCollector
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.spans.FilteredSpans
import org.jetbrains.amper.test.spans.SpansTestCollector
import org.jetbrains.amper.test.spans.spansNamed
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

class AmperIosProjectsTest : AmperCliTestBase() {
    override val testDataRoot: Path
        get() = TestUtil.amperSourcesRoot
            .resolve("amper-backend-test/testData/projects/ios")

    @Test
    @MacOnly
    fun `framework for simple for iosSimulatorArm64`() = runCliTestWithCollector {
        runCli(
            backendTestProjectName = "interop",
            "task", ":interop:frameworkIosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    @MacOnly
    fun `framework for simple for iosArm64`() = runCliTestWithCollector {
        runCli(
            backendTestProjectName = "interop",
            "task", ":interop:frameworkIosArm64",
            assertEmptyStdErr = false,
        )
        konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    @MacOnly
    fun `buildIosApp for simple for iosSimulatorArm64`() = runCliTestWithCollector {
        runCli(
            backendTestProjectName = "interop",
            "task", ":interop:buildIosAppIosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        xcodeProjectGenSpans.assertNone()
        assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
        xcodebuildSpans.assertZeroExitCode()
    }

    @Test
    @MacOnly
    fun `build for simple for iosSimulatorArm64`() = runCliTestWithCollector {
        runCliInTempDir(
            backendTestProjectName = "interop",
            "build", "-p", "iosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        xcodeProjectGenSpans.assertNone()
        assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
        xcodebuildSpans.assertZeroExitCode()
    }

    @Test
    @MacOnly
    fun `build for outdated-xcode-proj updates the project`() = runCliTestWithCollector {
        runCliInTempDir(
            backendTestProjectName = "outdated-xcode-proj",
            "build", "-p", "iosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        xcodeProjectGenSpans.assertNone()
        assertTrue { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
        xcodebuildSpans.assertZeroExitCode()
    }

    @Test
    @MacOnly
    fun `framework for compose for iosSimulatorArm64`() = runCliTestWithCollector {
        runCli(
            backendTestProjectName = "compose",
            "task", ":compose:frameworkIosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    @MacOnly
    fun `framework for compose for iosArm64`() = runCliTestWithCollector {
        runCli(
            backendTestProjectName = "compose",
            "task", ":compose:frameworkIosArm64",
            assertEmptyStdErr = false,
        )
        konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    @MacOnly
    fun `buildIosApp for compose app for iosSimulatorArm64`() = runCliTestWithCollector {
        val (_, path) = runCliInTempDir(
            backendTestProjectName = "compose",
            "task", ":compose:buildIosAppIosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        xcodeProjectGenSpans.assertSingle()
        clearSpans()

        runCli(
            projectRoot = path,
            "task", ":compose:buildIosAppIosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        xcodeProjectGenSpans.assertNone()
        assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
    }

    @Test
    @MacOnly
    fun `build for compose app for all ios archs (arm64 without signing)`() = runCliTestWithCollector {
        val (result, _) = runCliInTempDir(
            backendTestProjectName = "compose",
            "build", // build all the platforms
            assertEmptyStdErr = false,
        )

        xcodeProjectGenSpans.assertSingle()
        xcodebuildSpans.assertTimes(3)
        expect(1) {
            // for iosArm64
            ("`DEVELOPMENT_TEAM` build setting is not detected in the Xcode project. " +
                    "Adding `CODE_SIGNING_ALLOWED=NO` to disable signing. " +
                    "You can still sign the app manually later.").toRegex(RegexOption.LITERAL)
                .findAll(result.stdout).count()
        }
    }

    @Test
    @MacOnly
    fun `run kotlin tests in simulator`() = runCliTestWithCollector {
        runCli(
            backendTestProjectName = "simpleTests",
            "test",
            assertEmptyStdErr = false,
        )
        val testsStdOut = iosKotlinTests.assertZeroExitCode().getAttribute(AttributeKey.stringKey("stdout"))
        assertTrue(testsStdOut.contains("##teamcity[testSuiteFinished name='SimpleTestsKt']"))
    }
}

private val SpansTestCollector.xcodebuildSpans: FilteredSpans
    get() = spansNamed("xcodebuild")

private val SpansTestCollector.iosKotlinTests: FilteredSpans
    get() = spansNamed("ios-kotlin-test")

private val SpansTestCollector.konancSpans: FilteredSpans
    get() = spansNamed("konanc")

private val SpansTestCollector.xcodeProjectGenSpans
    get() = spansNamed("xcode project generation")

private val SpansTestCollector.xcodeProjectManagementSpans
    get() = spansNamed("xcode project management")

private val UpdatedAttribute = AttributeKey.booleanKey("updated")

private fun FilteredSpans.assertZeroExitCode() = assertSingle().apply {
    assertEquals(0, getAttribute(AttributeKey.longKey("exit-code")))
}

private fun FilteredSpans.assertZeroExitCode(times: Int) = assertTimes(times).forEach {
    assertEquals(0, it.getAttribute(AttributeKey.longKey("exit-code")))
}