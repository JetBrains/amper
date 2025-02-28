/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperTestBasicTest : AmperCliTestBase() {

    @Test
    fun `test using reflection`() = runSlowTest {
        runCli(testProject("jvm-test-using-reflection"), "test", "--platform=jvm")
    }

    @Test
    fun `jvm test with JVM arg`() = runSlowTest {
        val testProject = testProject("jvm-kotlin-test-systemprop")
        runCli(testProject, "test", "--jvm-args=-Dmy.system.prop=hello")

        // should fail without the system prop
        runCli(
            projectRoot = testProject,
            "test",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        // should fail with an incorrect value for the system prop
        runCli(
            projectRoot = testProject,
            "test",
            "--jvm-args=-Dmy.system.prop=WRONG",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
    }
}
