/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import kotlin.test.Test

class MavenPluginsTest : AmperCliTestBase() {
    @Test
    fun `surefire plugin test goal exists as task`() = runSlowTest {
        runCli(
            projectRoot = testProject("extensibility-maven/surefire-plugin"),
            "show", "tasks",
            copyToTempDir = true,
        )
    }

    @Test
    fun `surefire plugin test goal executes as task`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility-maven/surefire-plugin"),
            "task", ":app1:maven-surefire-plugin.test",
            copyToTempDir = true,
        )
        result.assertStdoutContains("Hello from surefire execution")
    }

    @Test
    fun `surefire plugin test goal executes as task with dependency between modules`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility-maven/surefire-plugin-multi-module"),
            "task", ":app1:maven-surefire-plugin.test",
            copyToTempDir = true,
        )
        result.assertStdoutContains("Hello from surefire execution")
    }
}