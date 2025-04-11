/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.Test

class TypoTest : AmperCliTestBase() {

    @Test
    fun `using tasks command should suggest 'task' and 'show tasks'`() {
        testTypo("tasks", expectedError = "no such subcommand tasks. (Possible subcommands: show tasks, task)")
    }

    @Test
    fun `using settings command should suggest 'show settings'`() {
        testTypo("settings", expectedError = "no such subcommand settings. Did you mean show settings?")
    }

    @Test
    fun `using modules command should suggest 'show modules'`() {
        testTypo("modules", expectedError = "no such subcommand modules. Did you mean show modules?")
    }

    private fun testTypo(vararg command: String, expectedError: String) = runSlowTest {
        val result1 = runCli(newEmptyProjectDir(), *command, expectedExitCode = 1, assertEmptyStdErr = false)
        result1.assertStderrContains("Error: $expectedError")
    }
}
