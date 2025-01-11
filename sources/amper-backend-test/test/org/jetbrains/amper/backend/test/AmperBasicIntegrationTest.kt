/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.spans.assertJavaCompilationSpan
import org.jetbrains.amper.test.spans.assertKotlinJvmCompilationSpan
import org.jetbrains.amper.test.spans.kotlinJvmCompilationSpans
import org.jetbrains.amper.test.spans.withAmperModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// TODO: review and merged with other integration test suite
// This test was initially testing Gradle-based example projects.
// It was decoupled from the Gradle-based examples, and split into AmperExamples2Test and AmperBasicIntegrationTest.
class AmperBasicIntegrationTest : AmperIntegrationTestBase() {

    private suspend fun TestCollector.setupExampleProject(testProjectName: String): CliContext =
        setupTestProject(Dirs.amperTestProjectsRoot.resolve(testProjectName), copyToTemp = true)

    @Test
    fun `jvm-default-compiler-settings`() = runTestWithCollector {
        AmperBackend(setupExampleProject("jvm-default-compiler-settings")).run {
            assertHasTasks(jvmAppTasks + jvmTestTasks)
            runApplication()
        }

        // testing some default compiler arguments
        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "2.0")
            hasCompilerArgument("-api-version", "2.0")
            hasCompilerArgument("-Xjdk-release=17")
        }
        assertStdoutContains("Hello, World")
    }

    @Test
    fun `jvm-explicit-compiler-settings`() = runTestWithCollector {
        AmperBackend(setupExampleProject("jvm-explicit-compiler-settings")).run {
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
    fun `jvm-failed-test`() = runTestWithCollector {
        val exception = assertFailsWith<UserReadableError> {
            AmperBackend(setupExampleProject("jvm-failed-test")).test()
        }
        assertEquals(
            "Task ':jvm-failed-test:testJvm' failed: JVM tests failed for module 'jvm-failed-test' with exit code 1 (see errors above)",
            exception.message
        )
        assertStdoutContains("MethodSource [className = 'FailedTest', methodName = 'shouldFail', methodParameterTypes = '']")
        assertStdoutContains("=> java.lang.AssertionError: Expected value to be true.")
    }

    @Test
    fun `multi-module`() = runTestWithCollector {
        val projectContext = setupExampleProject("multi-module")

        AmperBackend(projectContext).run {
            assertHasTasks(jvmAppTasks, module = "app")
            assertHasTasks(jvmBaseTasks + jvmTestTasks, module = "shared")
            runApplication()
        }

        kotlinJvmCompilationSpans.withAmperModule("app").assertSingle()
        kotlinJvmCompilationSpans.withAmperModule("shared").assertSingle()
        assertStdoutContains("Hello, World!")

        clearTerminalRecording()

        AmperBackend(projectContext).test()
        assertStdoutContains("Test run finished after")
    }
}

private val jvmBaseTasks = listOf("compileJvm", "resolveDependenciesJvm")
private val jvmTestTasks = listOf("compileJvmTest", "resolveDependenciesJvmTest")
private val jvmAppTasks = jvmBaseTasks + listOf("runJvm")
