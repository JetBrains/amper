/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.test.spans.assertJavaCompilationSpan
import org.jetbrains.amper.test.spans.assertKotlinJvmCompilationSpan
import org.jetbrains.amper.test.spans.kotlinJvmCompilationSpans
import org.jetbrains.amper.test.spans.withAmperModule
import kotlin.io.path.name
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

    @Test
    fun `jvm-default-compiler-settings`() = runSlowTest {
        val projectRoot = testProject("jvm-default-compiler-settings")
        val tasksResult = runCli(projectRoot = projectRoot, "show", "tasks")
        tasksResult.assertHasTasks(jvmAppTasks + jvmTestTasks)

        val runResult = runCli(projectRoot = projectRoot, "run")
        // testing some default compiler arguments
        runResult.readTelemetrySpans().assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "2.1")
            hasCompilerArgument("-api-version", "2.1")
            hasCompilerArgument("-Xjdk-release=17")
        }
        runResult.assertStdoutContains("Hello, World")
    }

    @Test
    fun `jvm-explicit-compiler-settings`() = runSlowTest {
        val projectRoot = testProject("jvm-explicit-compiler-settings")
        val tasksResult = runCli(projectRoot = projectRoot, "show", "tasks")
        tasksResult.assertHasTasks(jvmAppTasks + jvmTestTasks)

        val runResult = runCli(projectRoot = projectRoot, "run")
        with(runResult.readTelemetrySpans()) {
            assertKotlinJvmCompilationSpan {
                hasCompilerArgument("-language-version", "1.8") // explicit
                hasCompilerArgument("-Xjdk-release=17") // explicit
            }
            assertJavaCompilationSpan {
                hasCompilerArgument("--release", "17")
            }
        }
        runResult.assertStdoutContains("Hello, World")
    }

    @Test
    fun `multi-module`() = runSlowTest {
        val projectRoot = testProject("multi-module")
        val tasksResult = runCli(projectRoot = projectRoot, "show", "tasks")
        tasksResult.assertHasTasks(jvmAppTasks, module = "app")
        tasksResult.assertHasTasks(jvmBaseTasks + jvmTestTasks, module = "shared")

        val runResult = runCli(projectRoot = projectRoot, "run")
        with(runResult.readTelemetrySpans()) {
            kotlinJvmCompilationSpans.withAmperModule("app").assertSingle()
            kotlinJvmCompilationSpans.withAmperModule("shared").assertSingle()
        }
        runResult.assertStdoutContains("Hello, World!")

        val testResult = runCli(projectRoot = projectRoot, "test")
        testResult.assertStdoutContains("Test run finished after")
    }
}

private val jvmBaseTasks = listOf("compileJvm", "resolveDependenciesJvm")
private val jvmTestTasks = listOf("compileJvmTest", "resolveDependenciesJvmTest")
private val jvmAppTasks = jvmBaseTasks + listOf("runJvm")

private fun AmperCliTestBase.AmperCliResult.assertHasTasks(expectedTasks: Iterable<String>, module: String? = null) {
    val taskNames = stdout.lines()
        .filter { it.trim().startsWith("task :") }
        .map { it.trim().removePrefix("task ").substringBefore(" -> ") }
    expectedTasks.forEach { task ->
        val expected = ":${module ?: projectRoot.name}:$task"
        assertTrue("Task named '$expected' should be present, but found only:\n" + taskNames.joinToString("\n")) {
            taskNames.contains(expected)
        }
    }
}
