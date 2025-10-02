/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.golden.GoldFileTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.Path
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class ShowSettingsCommandTest : AmperCliTestBase() {

    private fun AmperCliResult.checkGold(caseName: String) = GoldFileTest(
        caseName = caseName,
        base = Path("testResources/showSettings"),
    ) { stdoutClean }.doTest()
    
    @Test
    fun `show settings command prints settings for single module`() = runSlowTest {
        val r = runCli(projectRoot = testProject("java-kotlin-mixed"), "show", "settings")

        r.checkGold("single-module")
    }

    @Test
    fun `show settings command prints settings for specified module`() = runSlowTest {
        val r = runCli(projectRoot = testProject("jvm-multimodule-tests"), "show", "settings", "--module", "one")

        r.checkGold("jvm-multimodule-tests_specified-module-one")
    }

    @Test
    fun `show settings command prints settings for specified modules`() = runSlowTest {
        val r = runCli(projectRoot = testProject("jvm-multimodule-tests"), "show", "settings", "-m", "one", "-m", "two")

        r.checkGold("jvm-multimodule-tests_specified-modules-one-and-two")
    }

    @Test
    fun `show settings command prints settings for all modules - jvm only`() = runSlowTest {
        val r = runCli(projectRoot = testProject("jvm-multimodule-tests"), "show", "settings", "--all-modules")

        r.checkGold("jvm-multimodule-tests_all-modules")
    }

    @Test
    fun `show settings command prints settings for all modules - multiplatform`() = runSlowTest {
        val r = runCli(projectRoot = testProject("compose-multiplatform-room"), "show", "settings", "--all-modules")
        
        r.checkGold("compose-multiplatform-room_all-modules")
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