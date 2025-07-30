/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.Test

class PowerAssertTest : AmperCliTestBase() {

    @Test
    fun `power-assert errors are only reported on assert by default`() = runSlowTest {
        val result = runCli(
            testProject(name = "kotlin-powerassert"),
            "test",
            "--include-module=default-functions",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        result.assertStdoutContains("""
            assert(name1 == name2 + name1[2])
                   |     |  |     | |    |
                   |     |  |     | |    o
                   |     |  |     | george
                   |     |  |     fredo
                   |     |  fred
                   |     false
                   george
        """.trimIndent())

        // assertEquals should be processed by default
        result.assertStdoutContains("org.opentest4j.AssertionFailedError: expected: <george> but was: <ed>")
    }

    @Test
    fun `power-assert errors are properly reported on custom functions`() = runSlowTest {
        val result = runCli(
            testProject(name = "kotlin-powerassert"),
            "test",
            "--include-module=custom-functions",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        result.assertStdoutContains("""
            assert(name1 == name2 + name1[2])
                   |     |  |     | |    |
                   |     |  |     | |    o
                   |     |  |     | george
                   |     |  |     fredo
                   |     |  fred
                   |     false
                   george
        """.trimIndent())

        // assertEquals should be processed because explicitly mentioned in the settings
        result.assertStdoutContains("""
            assertEquals(name1, name2.substring(2, name2.length))
                         |      |     |            |     |
                         |      |     |            |     4
                         |      |     |            fred
                         |      |     ed
                         |      fred
                         george
        """.trimIndent())
    }
}
