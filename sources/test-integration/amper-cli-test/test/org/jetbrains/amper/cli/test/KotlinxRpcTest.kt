/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.Test

class KotlinxRpcTest : AmperCliTestBase() {

    @Test
    fun `kotlinx-rpc projects can build`() = runSlowTest {
        // TODO 'run' the server and the client together
        val result = runCli(testProject(name = "kotlin-rpc"), "build")

        result.assertStdoutContains("Build successful")
    }

    @Test
    fun `kotlinx-rpc compiler error on non-annotated service`() = runSlowTest {
        val result = runCli(
            testProject(name = "kotlin-rpc-error"),
            "build",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        result.stderr.contains("1st type argument is marked with @Rpc annotation, but inferred type is class Any")
    }
}
