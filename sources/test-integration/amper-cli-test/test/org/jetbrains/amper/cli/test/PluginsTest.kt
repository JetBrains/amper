/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import kotlin.test.Test

class PluginsTest : AmperCliTestBase() {
    @Test
    fun `single plugin - contributes source file when enabled`() = runSlowTest {
        val r1 = runCli(
            projectRoot = testProject("extensibility/single-local-plugin"),
            "show", "tasks",
            copyToTempDir = true,
        )

        with(r1) {
            assertStdoutContains("task :app1:generate-konfig@build-konfig -> :build-konfig-plugin:runtimeClasspathJvm")
            assertStdoutContains(
                "task :app1:print-generated-sources@build-konfig -> " +
                        ":build-konfig-plugin:runtimeClasspathJvm, :app1:generate-konfig@build-konfig"
            )
        }

        val r2 = runCli(
            projectRoot = r1.projectRoot,
            "run", "-m", "app1",
        )

        r2.assertStdoutContains("version: 1.0+hello; id: table-green-geese")
    }

    @Test
    fun `single plugin - no effect when no enabled`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("extensibility/single-local-plugin"),
            "build", "-m", "app2",
            copyToTempDir = true,
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        r.assertStderrContains("Unresolved reference 'Konfig'")
    }

    @Test
    fun `two plugins - enabled`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("extensibility/multiple-local-plugins"),
            "task", ":app:say@hello",
            copyToTempDir = true,
        )
        val slash = r.projectRoot.fileSystem.separator

        with(r) {
            assertStdoutContains("Hello!")
            assertStdoutContains("tasks${slash}_app_say@hello")
            assertStdoutContains("multiple-local-plugins${slash}app")
        }

        val r2 = runCli(
            projectRoot = r.projectRoot,
            "task", ":build-konfig-plugin:say@hello",
        )

        with(r2) {
            assertStdoutContains("Hello!")
            assertStdoutContains("tasks${slash}_build-konfig-plugin_say@hello")
            assertStdoutContains("multiple-local-plugins${slash}build-konfig-plugin")
        }

        val r3 = runCli(
            projectRoot = r.projectRoot,
            "task", ":hello-plugin:say@hello",
        )

        with(r3) {
            assertStdoutContains("Hello!")
            assertStdoutContains("tasks${slash}_hello-plugin_say@hello")
            assertStdoutContains("multiple-local-plugins${slash}hello-plugin")
        }
    }
}