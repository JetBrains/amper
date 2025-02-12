/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.telemetry.getAttribute
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.spans.FilteredSpans
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.expect

@MacOnly
class AmperIosProjectsTest : AmperCliTestBase() {

    @Test
    fun `framework for simple for iosSimulatorArm64`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("ios/interop"),
            "task", ":interop:frameworkIosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        result.readTelemetrySpans().konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    fun `framework for simple for iosArm64`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("ios/interop"),
            "task", ":interop:frameworkIosArm64",
            assertEmptyStdErr = false,
        )
        result.readTelemetrySpans().konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    fun `buildIosApp for simple for iosSimulatorArm64`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("ios/interop"),
            "task", ":interop:buildIosAppIosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        result.withTelemetrySpans {
            xcodeProjectGenSpans.assertNone()
            assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
            xcodebuildSpans.assertZeroExitCode()
        }
    }

    @Test
    fun `build for simple for iosSimulatorArm64`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("ios/interop"),
            "build", "-p", "iosSimulatorArm64",
            assertEmptyStdErr = false,
            copyToTempDir = true,
        )
        result.withTelemetrySpans {
            xcodeProjectGenSpans.assertNone()
            assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
            xcodebuildSpans.assertZeroExitCode()
        }
    }

    @Test
    fun `build for outdated-xcode-proj updates the project`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("ios/outdated-xcode-proj"),
            "build", "-p", "iosSimulatorArm64",
            assertEmptyStdErr = false,
            copyToTempDir = true,
        )
        result.withTelemetrySpans {
            xcodeProjectGenSpans.assertNone()
            assertTrue { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
            xcodebuildSpans.assertZeroExitCode()
        }
    }

    @Test
    fun `framework for compose for iosSimulatorArm64`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("ios/compose"),
            "task", ":compose:frameworkIosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        result.readTelemetrySpans().konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    fun `framework for compose for iosArm64`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("ios/compose"),
            "task", ":compose:frameworkIosArm64",
            assertEmptyStdErr = false,
        )
        result.readTelemetrySpans().konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    fun `buildIosApp for compose app for iosSimulatorArm64`() = runSlowTest {
        val firstBuildResult = runCli(
            projectRoot = testProject(name = "ios/compose"),
            "task", ":compose:buildIosAppIosSimulatorArm64",
            assertEmptyStdErr = false,
            copyToTempDir = true,
        )
        firstBuildResult.readTelemetrySpans().xcodeProjectGenSpans.assertSingle()

        val secondBuildResult = runCli(
            projectRoot = firstBuildResult.projectRoot, // new root in temp dir
            "task", ":compose:buildIosAppIosSimulatorArm64",
            assertEmptyStdErr = false,
        )
        secondBuildResult.withTelemetrySpans {
            xcodeProjectGenSpans.assertNone()
            assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
        }
    }

    @Test
    fun `build for compose app for all ios archs (arm64 without signing)`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("ios/compose"),
            "build", // build all the platforms
            assertEmptyStdErr = false,
            copyToTempDir = true,
        )

        expect(1) {
            // for iosArm64
            ("`DEVELOPMENT_TEAM` build setting is not detected in the Xcode project. " +
                    "Adding `CODE_SIGNING_ALLOWED=NO` to disable signing. " +
                    "You can still sign the app manually later.").toRegex(RegexOption.LITERAL)
                .findAll(result.stdout).count()
        }
        result.withTelemetrySpans {
            xcodeProjectGenSpans.assertSingle()
            xcodebuildSpans.assertTimes(3)
        }
    }

    @Test
    @Ignore("until AMPER-4070 is fixed")
    fun `run kotlin tests in simulator`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("ios/simpleTests"),
            "test",
            assertEmptyStdErr = false,
        )
        result.withTelemetrySpans {
            val testsStdOut = iosKotlinTests.assertZeroExitCode().getAttribute(AttributeKey.stringKey("stdout"))
            assertTrue(testsStdOut.contains("##teamcity[testSuiteFinished name='SimpleTest']"))
        }
    }

    @Test
    fun `compose-multiplatform - build debug with xcodebuild`() = runSlowTest {
        val result = runXcodebuild(
            "-project", (Dirs.amperTestProjectsRoot / "ios/non-intel/module.xcodeproj").pathString,
            "-scheme", "app",
            "-configuration", "Debug",
            "-arch", "x86_64",
            "-sdk", "iphonesimulator",
        )
        assertNotEquals(illegal = 0, actual = result.exitCode)
        assertContains(result.stdout, """
            ERROR: Platform 'iosX64' is not found for iOS module 'non-intel'.
            The module has declared platforms: IOS_ARM64 IOS_SIMULATOR_ARM64.
            Please declare the required platform explicitly in the module's file.
        """.trimIndent())
    }
}

private fun FilteredSpans.assertZeroExitCode() = assertSingle().apply {
    assertEquals(0, getAttribute(AttributeKey.longKey("exit-code")))
}

private fun FilteredSpans.assertZeroExitCode(times: Int) = assertTimes(times).forEach {
    assertEquals(0, it.getAttribute(AttributeKey.longKey("exit-code")))
}