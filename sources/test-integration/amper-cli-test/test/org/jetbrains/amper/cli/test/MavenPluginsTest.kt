/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.assertStdoutDoesNotContain
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.Disabled
import kotlin.test.Test

class MavenPluginsTest : AmperCliTestBase() {
    @Test
    fun `surefire plugin test goal exists as task`() = runSlowTest {
        runCli(
            projectRoot = testProject("extensibility-maven/surefire-plugin"),
            "show", "tasks",
            copyToTempDir = true,
        ).assertStdoutContains("maven-surefire-plugin.test")
    }

    @Test
    fun `no maven tasks appear on the task list if no maven plugins are specified`() = runSlowTest {
        runCli(
            // Just some project with java source code.
            projectRoot = testProject("java-kotlin-mixed"),
            "show", "tasks",
            copyToTempDir = true,
        ).assertStdoutDoesNotContain("maven")
    }

    @Test
    @Disabled("Need to support multiple output roots in the JvmCompileTask: https://youtrack.jetbrains.com/issue/AMPER-4859/Support-multiple-output-roots-of-JVM-compilation")
    fun `surefire plugin test goal executes as task`() = runSlowTest {
        `run app-maven-surefire-plugin-test task`("surefire-plugin")
            .assertStdoutContains("Hello from surefire execution")
    }

    @Test
    @Disabled("Need to support multiple output roots in the JvmCompileTask: https://youtrack.jetbrains.com/issue/AMPER-4859/Support-multiple-output-roots-of-JVM-compilation")
    fun `surefire plugin test goal executes as task with dependency between modules`() = runSlowTest {
        `run app-maven-surefire-plugin-test task`("surefire-plugin-multi-module")
            .assertStdoutContains("Hello from surefire execution")
    }

    @Test
    @Disabled("Need to support multiple output roots in the JvmCompileTask: https://youtrack.jetbrains.com/issue/AMPER-4859/Support-multiple-output-roots-of-JVM-compilation")
    fun `surefire plugin test goal executes with junit assertion and test fails`() = runSlowTest {
        `run app-maven-surefire-plugin-test task`("surefire-plugin-junit-assertion", expectedExitCode = 1)
            .assertStderrContains("expected: <foo> but was: <bar>")
    }
    
    @Test
    @Disabled("Need to support multiple output roots in the JvmCompileTask: https://youtrack.jetbrains.com/issue/AMPER-4859/Support-multiple-output-roots-of-JVM-compilation")
    fun `surefire plugin test goal executes with junit filter and skips one test`() = runSlowTest {
        `run app-maven-surefire-plugin-test task`("surefire-plugin-two-tests-with-filter").apply {
            assertStdoutContains("Hello from firstTest")
            assertStdoutDoesNotContain("Hello from secondTest")
        }
    }

    /**
     * Run `:app:maven-surefire-plugin.test` task for the specified project from `extensibility-maven` directory.
     */
    private suspend fun `run app-maven-surefire-plugin-test task`(
        projectWithMavenPath: String,
        expectedExitCode: Int = 0,
    ) = runCli(
        projectRoot = testProject("extensibility-maven/$projectWithMavenPath"),
        "task", ":app:maven-surefire-plugin.test",
        copyToTempDir = true,
        expectedExitCode = expectedExitCode,
        assertEmptyStdErr = expectedExitCode == 0
    )
}