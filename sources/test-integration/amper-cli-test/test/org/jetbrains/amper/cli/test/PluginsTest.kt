/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.assertStdoutDoesNotContain
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.AmperCliResult
import kotlin.io.path.div
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun `invalid plugins`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility/invalid-plugins"),
            "show", "tasks",
            copyToTempDir = true,
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )

        with(result) {
            assertEquals(
                expected = sortedSetOf(
                    "Plugin id must be unique across the project", // Sources are asserted below
                    "${projectRoot / "not-a-plugin" / "module.yaml"}:1:10: Unexpected product type for plugin. Expected jvm/amper-plugin, got jvm/app",
                    "${projectRoot / "plugin-empty-id" / "module.yaml"}:5:29: Plugin schema class `com.example.Settings` is not found",
                ),
                actual = parseErrors(),
            )
            assertStderrContains("""
                Plugin id must be unique across the project
                ╰─ There are multiple plugins with the id `hello`:
                   ╰─ ${projectRoot / "plugin-a" / "module.yaml"}:4:7
                   ╰─ ${projectRoot / "plugin-b" / "module.yaml"}:4:7
                   ╰─ ${projectRoot / "hello" / "module.yaml"}:1:10
            """.trimIndent())
            assertStdoutContains("Processing local plugin schema for [plugin-empty-id, plugin-no-plugin-block, hello, hello, hello]...")
        }
    }

    @Test
    fun `missing plugins`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility/missing-plugins"),
            "show", "tasks",
            copyToTempDir = true,
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )

        with(result) {
            assertEquals(
                expected = sortedSetOf(
                    "${projectRoot / "project.yaml"}:6:5: Plugin module `existing-but-not-included` is not included in the project `modules` list",
                    "${projectRoot / "project.yaml"}:7:5: Plugin module `non-existing` is not found",
                ),
                actual = parseErrors(),
            )
            assertStdoutDoesNotContain("Processing local plugin schema for")
        }
    }

    private fun AmperCliResult.parseErrors(): Set<String> = CliErrorLikeRegex.findAll(stderr).map {
        it.groups["error"]!!.value
    }.toSortedSet()
}

private val CliErrorLikeRegex = "ERROR\\s+(?<error>.*)".toRegex()