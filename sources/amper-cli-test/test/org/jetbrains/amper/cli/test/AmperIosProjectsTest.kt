/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.telemetry.getAttribute
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.collectSpansFromCli
import org.jetbrains.amper.test.spans.FilteredSpans
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.expect

@MacOnly
class AmperIosProjectsTest : AmperCliTestBase() {
    override val testDataRoot: Path
        get() = Dirs.amperTestProjectsRoot / "ios"

    @Test
    fun `framework for simple for iosSimulatorArm64`() = runSlowTest {
        collectSpansFromCli {
            runCli(
                backendTestProjectName = "interop",
                "task", ":interop:frameworkIosSimulatorArm64",
                assertEmptyStdErr = false,
            )
        }.konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    fun `framework for simple for iosArm64`() = runSlowTest {
        collectSpansFromCli {
            runCli(
                backendTestProjectName = "interop",
                "task", ":interop:frameworkIosArm64",
                assertEmptyStdErr = false,
            )
        }.konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    fun `buildIosApp for simple for iosSimulatorArm64`() = runSlowTest {
        collectSpansFromCli {
            runCli(
                backendTestProjectName = "interop",
                "task", ":interop:buildIosAppIosSimulatorArm64",
                assertEmptyStdErr = false,
            )
        }.run {
            xcodeProjectGenSpans.assertNone()
            assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
            xcodebuildSpans.assertZeroExitCode()
        }
    }

    @Test
    fun `build for simple for iosSimulatorArm64`() = runSlowTest {
        collectSpansFromCli {
            runCliInTempDir(
                backendTestProjectName = "interop",
                "build", "-p", "iosSimulatorArm64",
                assertEmptyStdErr = false,
            )
        }.run {
            xcodeProjectGenSpans.assertNone()
            assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
            xcodebuildSpans.assertZeroExitCode()
        }
    }

    @Test
    fun `build for outdated-xcode-proj updates the project`() = runSlowTest {
        collectSpansFromCli {
            runCliInTempDir(
                backendTestProjectName = "outdated-xcode-proj",
                "build", "-p", "iosSimulatorArm64",
                assertEmptyStdErr = false,
            )
        }.run {
            xcodeProjectGenSpans.assertNone()
            assertTrue { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
            xcodebuildSpans.assertZeroExitCode()
        }
    }

    @Test
    fun `framework for compose for iosSimulatorArm64`() = runSlowTest {
        collectSpansFromCli {
            runCli(
                backendTestProjectName = "compose",
                "task", ":compose:frameworkIosSimulatorArm64",
                assertEmptyStdErr = false,
            )
        }.konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    fun `framework for compose for iosArm64`() = runSlowTest {
        collectSpansFromCli {
            runCli(
                backendTestProjectName = "compose",
                "task", ":compose:frameworkIosArm64",
                assertEmptyStdErr = false,
            )
        }.konancSpans.assertZeroExitCode(times = 2)
    }

    @Test
    fun `buildIosApp for compose app for iosSimulatorArm64`() = runSlowTest {
        val projectPath: Path
        collectSpansFromCli {
            val result = runCliInTempDir(
                backendTestProjectName = "compose",
                "task", ":compose:buildIosAppIosSimulatorArm64",
                assertEmptyStdErr = false,
            )
            projectPath = result.projectRoot
        }.xcodeProjectGenSpans.assertSingle()

        collectSpansFromCli {
            runCli(
                projectRoot = projectPath,
                "task", ":compose:buildIosAppIosSimulatorArm64",
                assertEmptyStdErr = false,
            )
        }.run {
            xcodeProjectGenSpans.assertNone()
            assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
        }
    }

    @Test
    fun `build for compose app for all ios archs (arm64 without signing)`() = runSlowTest {
        collectSpansFromCli {
            val result = runCliInTempDir(
                backendTestProjectName = "compose",
                "build", // build all the platforms
                assertEmptyStdErr = false,
            )

            expect(1) {
                // for iosArm64
                ("`DEVELOPMENT_TEAM` build setting is not detected in the Xcode project. " +
                        "Adding `CODE_SIGNING_ALLOWED=NO` to disable signing. " +
                        "You can still sign the app manually later.").toRegex(RegexOption.LITERAL)
                    .findAll(result.stdout).count()
            }
        }.run {
            xcodeProjectGenSpans.assertSingle()
            xcodebuildSpans.assertTimes(3)
        }
    }

    @Test
    fun `run kotlin tests in simulator`() = runSlowTest {
        collectSpansFromCli {
            runCli(
                backendTestProjectName = "simpleTests",
                "test",
                assertEmptyStdErr = false,
            )
        }.run {
            val testsStdOut = iosKotlinTests.assertZeroExitCode().getAttribute(AttributeKey.stringKey("stdout"))
            assertTrue(testsStdOut.contains("##teamcity[testSuiteFinished name='SimpleTestsKt']"))
        }
    }

    @Test
    fun `compose-multiplatform - build debug with xcodebuild`() = runSlowTest {
        val result = runXcodebuild(
            "-project", (testDataRoot / "non-intel" / "module.xcodeproj").pathString,
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