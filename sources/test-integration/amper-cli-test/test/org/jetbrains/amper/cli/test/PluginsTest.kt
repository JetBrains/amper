/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.assertStdoutDoesNotContain
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.normalizeLineSeparators
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
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
    fun `execution avoidance - enabled by default, disabled for no-outputs tasks`() = runSlowTest {
        val r1 = runCli(
            projectRoot = testProject("extensibility/single-local-plugin"),
            "task", ":app1:print-generated-sources@build-konfig",
            copyToTempDir = true,
        )

        with(r1) {
            assertStdoutContains("Generating Build Konfig...")
            assertStdoutContains("Printing generated Build Konfig sources...")
        }

        // Incremental re-run, two times
        repeat(2) {
            println("test: run print sources #$it")

            val r2 = runCli(
                projectRoot = r1.projectRoot,
                "task", ":app1:print-generated-sources@build-konfig"
            )

            with(r2) {
                assertStdoutDoesNotContain("Generating Build Konfig...")
                assertStdoutContains("Printing generated Build Konfig sources...")
            }
        }
    }

    @Test
    fun `execution avoidance - disabled when explicitly opted-out`() = runSlowTest {
        val r1 = runCli(
            projectRoot = testProject("extensibility/multiple-local-plugins"),
            "show", "tasks",
            copyToTempDir = true,
        )

        repeat(3) {
            println("test: run print sources #$it")
            val r2 = runCli(
                projectRoot = r1.projectRoot,
                "task", ":app:print-generated-sources@build-konfig",
            )

            with(r2) {
                assertStdoutContains("Generating Build Konfig...")
            }
        }
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

        runCli(
            projectRoot = r.projectRoot,
            "run", "-m", "app",
        )

        assertContains(
            charSequence = (r.projectRoot / "app" / "konfig.properties").readText().normalizeLineSeparators(),
            other = """
                ID=chair-red-dog
                VERSION=1.0
            """.trimIndent()
        )
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
                    "${projectRoot / "not-a-plugin" / "module.yaml"}:1:1: Unexpected product type for plugin. Expected jvm/amper-plugin, got jvm/app",
                    "${projectRoot / "plugin-empty-id" / "module.yaml"}:5:29: Plugin schema class `com.example.Settings` is not found",
                ),
                actual = parseErrors(),
            )
            assertStderrContains("""
                Plugin id must be unique across the project
                ╰─ There are multiple plugins with the id `hello`:
                   ╰─ ${projectRoot / "plugin-a" / "module.yaml"}:4:7
                   ╰─ ${projectRoot / "plugin-b" / "module.yaml"}:4:7
                   ╰─ ${projectRoot / "hello"}
            """.trimIndent())
            assertStdoutContains("Processing local plugin schema for [plugin-empty-id, plugin-no-plugin-block, hello]...")
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

    @Test
    fun `invalid references`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("extensibility/invalid-references"),
            "show", "tasks",
            copyToTempDir = true,
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )
        with(result) {
            val pluginYaml = projectRoot / "plugin1" / "plugin.yaml"
            assertEquals(
                expected = sortedSetOf(
                    "${pluginYaml}:11:7: The value of type `ModuleConfigurationForPlugin` cannot be assigned to the type `Nested`",
                    "${pluginYaml}:12:7: The value of type `mapping {string : Element}` cannot be assigned to the type `Nested`",
                    "${pluginYaml}:6:7: The value of type `string` cannot be assigned to the type `boolean`",
                    "${pluginYaml}:9:7: The value of type `Settings` cannot be assigned to the type `path`",
                    "${pluginYaml}:7:7: The value of type `boolean` cannot be used in string interpolation",
                    "${pluginYaml}:4:5: No value for required property 'int'.",
                ),
                actual = parseErrors(),
            )
        }
    }

    private fun AmperCliResult.parseErrors(): Set<String> = CliErrorLikeRegex.findAll(stderr).map {
        it.groups["error"]!!.value
    }.toSortedSet()
}

private val CliErrorLikeRegex = "ERROR\\s+(?<error>.*)".toRegex()