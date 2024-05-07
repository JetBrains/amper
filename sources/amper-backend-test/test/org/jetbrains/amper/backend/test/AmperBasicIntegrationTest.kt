/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import org.gradle.tooling.internal.consumer.ConnectorServices
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteExisting
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// TODO: review and merged with other integration test suite
// This test was initially testing Gradle-based example projects.
// It was decoupled from the Gradle-based examples, and split into AmperExamples2Test and AmperBasicIntegrationTest.
class AmperBasicIntegrationTest : IntegrationTestBase() {

    private val exampleProjectsRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")

    private fun setupExampleProject(testProjectName: String): ProjectContext {
        return setupTestProject(exampleProjectsRoot.resolve(testProjectName), copyToTemp = true)
    }

    @Test
    fun `jvm-default-compiler-settings`() = runTestInfinitely {
        AmperBackend(setupExampleProject("jvm-default-compiler-settings"), backgroundScope).run {
            assertHasTasks(jvmAppTasks + jvmTestTasks)
            runApplication()
        }

        // testing some default compiler arguments
        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "1.9")
            hasCompilerArgument("-api-version", "1.9")
            hasCompilerArgument("-Xjdk-release=17")
        }
        assertStdoutContains("Hello, World")
    }

    @Test
    fun `jvm-explicit-compiler-settings`() = runTestInfinitely {
        AmperBackend(setupExampleProject("jvm-explicit-compiler-settings"), backgroundScope).run {
            assertHasTasks(jvmAppTasks + jvmTestTasks)
            runApplication()
        }

        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "1.8") // explicit
            hasCompilerArgument("-Xjdk-release=17") // explicit
        }
        assertJavaCompilationSpan {
            hasCompilerArgument("--release", "17")
        }
        assertStdoutContains("Hello, World")
    }

    @Test
    fun `jvm-failed-test`() = runTestInfinitely {
        val exception = assertFailsWith<TaskExecutor.TaskExecutionFailed> {
            AmperBackend(setupExampleProject("jvm-failed-test"), backgroundScope).test()
        }
        assertEquals(
            "Task ':jvm-failed-test:testJvm' failed: JVM tests failed for module 'jvm-failed-test' with exit code 1 (see errors above)",
            exception.message
        )
        assertStdoutContains("MethodSource [className = 'FailedTest', methodName = 'shouldFail', methodParameterTypes = '']")
        assertStdoutContains("=> java.lang.AssertionError: Expected value to be true.")
    }

    @Test
    fun `multi-module`() = runTestInfinitely {
        val projectContext = setupExampleProject("multi-module")

        AmperBackend(projectContext, backgroundScope).run {
            assertHasTasks(jvmAppTasks, module = "app")
            assertHasTasks(jvmBaseTasks + jvmTestTasks, module = "shared")
            runApplication()
        }

        kotlinJvmCompilationSpans.withAmperModule("app").assertSingle()
        kotlinJvmCompilationSpans.withAmperModule("shared").assertSingle()
        assertStdoutContains("Hello, World!")

        resetCollectors()

        AmperBackend(projectContext, backgroundScope).test()
        assertStdoutContains("Test run finished after")
    }

    private fun AmperBackend.assertHasTasks(tasks: Iterable<String>, module: String? = null) {
        showTasks()
        tasks.forEach { task ->
            assertStdoutContains(":${module ?: context.projectRoot.path.name}:$task")
        }
    }
}

private val jvmBaseTasks = listOf("compileJvm", "resolveDependenciesJvm")
private val jvmTestTasks = listOf("compileJvmTest", "resolveDependenciesJvmTest")
private val jvmAppTasks = jvmBaseTasks + listOf("runJvm")
