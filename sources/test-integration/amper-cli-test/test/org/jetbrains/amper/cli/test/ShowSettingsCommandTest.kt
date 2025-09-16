/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.golden.BaseTestRun
import org.jetbrains.amper.test.golden.GoldenTest
import org.jetbrains.amper.test.golden.readContentsAndReplace
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class ShowSettingsCommandTest : AmperCliTestBase(), GoldenTest {

    // FIXME this is not the build dir. Why are we doing this?
    override fun buildDir(): Path = tempRoot

    @Test
    fun `show settings command prints settings for single module`() = runSlowTest {
        val r = runCli(projectRoot = testProject("java-kotlin-mixed"), "show", "settings")

        ShowSettingsTestRun(
            caseName = "single-module",
            base = Path("testResources/showSettings"),
            cliResult = r,
        ).doTest()
    }

    @Test
    fun `show settings command prints settings for specified module`() = runSlowTest {
        val r = runCli(projectRoot = testProject("jvm-multimodule-tests"), "show", "settings", "--module", "one")

        ShowSettingsTestRun(
            caseName = "jvm-multimodule-tests_specified-module-one",
            base = Path("testResources/showSettings"),
            cliResult = r,
        ).doTest()
    }

    @Test
    fun `show settings command prints settings for specified modules`() = runSlowTest {
        val r = runCli(projectRoot = testProject("jvm-multimodule-tests"), "show", "settings", "-m", "one", "-m", "two")

        ShowSettingsTestRun(
            caseName = "jvm-multimodule-tests_specified-modules-one-and-two",
            base = Path("testResources/showSettings"),
            cliResult = r,
        ).doTest()
    }

    @Test
    fun `show settings command prints settings for all modules - jvm only`() = runSlowTest {
        val r = runCli(projectRoot = testProject("jvm-multimodule-tests"), "show", "settings", "--all-modules")

        ShowSettingsTestRun(
            caseName = "jvm-multimodule-tests_all-modules",
            base = Path("testResources/showSettings"),
            cliResult = r,
        ).doTest()
    }

    @Test
    fun `show settings command prints settings for all modules - multiplatform`() = runSlowTest {
        val r = runCli(projectRoot = testProject("compose-multiplatform-room"), "show", "settings", "--all-modules")

        ShowSettingsTestRun(
            caseName = "compose-multiplatform-room_all-modules",
            base = Path("testResources/showSettings"),
            cliResult = r,
        ).doTest()
    }

    @Test
    fun `show settings command fails if no module selected in non-interactive mode`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-multimodule-tests"),
            "show", "settings",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        r.assertStderrContains("Please specify the module(s) to inspect with --module, or use --all-modules to inspect all modules")
    }
}

private class ShowSettingsTestRun(
    caseName: String,
    override val base: Path,
    private val cliResult: AmperCliResult
) : BaseTestRun(caseName) {
    override fun GoldenTest.getInputContent(inputPath: Path): String = cliResult.stdoutClean

    override fun GoldenTest.getExpectContent(expectedPath: Path): String {
        // This is the actual check.
        if (!expectedPath.exists()) expectedPath.writeText("")
        return readContentsAndReplace(expectedPath, base)
    }
}
