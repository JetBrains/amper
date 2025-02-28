/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import kotlin.test.Test
import kotlin.test.assertTrue

class SmokeTest : AmperCliTestBase() {

    @Test
    fun smoke() = runSlowTest {
        runCli(testProject("jvm-kotlin-test-smoke"), "show", "tasks")
    }

    @Test
    fun `graceful failure on unknown task name`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-kotlin-test-smoke"),
            "task", "unknown",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val errorMessage = "ERROR: Task 'unknown' was not found in the project"

        assertTrue("Expected stderr to contain the message: '$errorMessage'") {
            errorMessage in r.stderr
        }
    }

    @Test
    fun `graceful failure on unknown task name with suggestions`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-kotlin-test-smoke"),
            "task", "compile",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val errorMessage = """
            ERROR: Task 'compile' was not found in the project, maybe you meant one of:
               :jvm-kotlin-test-smoke:compileJvm
               :jvm-kotlin-test-smoke:compileJvmTest
               :jvm-kotlin-test-smoke:compileMetadataCommon
               :jvm-kotlin-test-smoke:compileMetadataCommonTest
               :jvm-kotlin-test-smoke:compileMetadataJvm
               :jvm-kotlin-test-smoke:compileMetadataJvmTest
        """.trimIndent()

        assertTrue("Expected stderr to contain the message:\n$errorMessage\n\nActual stderr:\n${r.stderr}") {
            errorMessage in r.stderr
        }
    }
}