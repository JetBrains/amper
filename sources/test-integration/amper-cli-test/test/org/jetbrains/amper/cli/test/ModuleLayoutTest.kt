/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test

@Execution(ExecutionMode.CONCURRENT)
class ModuleLayoutTest : AmperCliTestBase() {

    @Test
    fun `maven-like module layout`() = runSlowTest {
        runCli(projectRoot = testProject("maven-like-module-layout"), "test")
    }

    @Test
    fun `wrong module layout`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("wrong-module-layout"),
            "run",
            expectedExitCode = 1,
            assertEmptyStdErr = false
        )
        result.assertStderrContains("The JVM main class was not found for application module 'wrong-module-layout'")
    }

    @Test
    fun `module-layout inconsistent`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("module-layout-inconsistent"),
            "run",
            expectedExitCode = 1,
            assertEmptyStdErr = false
        )

        result.assertStderrContains("Module layout maven-like is only supported in JVM modules (jvm/app or jvm/lib)")
    }
}
