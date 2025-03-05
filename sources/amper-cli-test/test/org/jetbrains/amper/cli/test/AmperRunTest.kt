/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.test.MacOnly
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperRunTest : AmperCliTestBase() {

    @Test
    fun `run command help prints dash dash`() = runSlowTest {
        val r = runCli(testProject("jvm-kotlin-test-smoke"), "run", "--help")

        // Check that '--' is printed before program arguments
        val string = "Usage: amper run [<options>] -- [<app_arguments>]..."

        assertTrue("There should be '$string' in `run --help` output") {
            r.stdout.lines().any { it == string }
        }
    }

    @Test
    fun `run with access to resource as dir`() = runSlowTest {
        // This project tests reading a directory entry from the resources.
        // NoSuchElementException means it failed.
        runCli(testProject("jvm-read-resource-dir"), "run")
    }

    @Test
    fun `run spring boot`() = runSlowTest {
        // Spring-core relies on ClassLoader::getResources for component scanning (to find bean definitions in the jar).
        // It expects the jar to contain directory entries and not just files.
        // Spring's PathMatchingResourcePatternResolver::doFindAllClassPathResources represents packages as a resources
        // (ex. org/springframework/boot/). So directory entries need to be resources inside the jar.
        // If directory entries are missing, the symptom is that Spring can't load the context.
        runCli(testProject("spring-boot"), "run")
    }

    @Test
    fun `run executable jar`() = runSlowTest {
        runCli(testProject("spring-boot"), "run", "-v", "release")
    }

    @Test
    fun `run works with stdin for jvm`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-input"),
            "run", "--module", "jvm-app",
            stdin = ProcessInput.Text("Hello World!\nBye World."),
        )

        assertContains(r.stdout, "Input: 'Hello World!'")
    }

    @Test
    @MacOnly
    fun `run works with stdin for native`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-input"),
            "run", "--module", "macos-app",
            stdin = ProcessInput.Text("Hello World!\nBye World."),
        )

        assertContains(r.stdout, "Input: 'Hello World!'")
    }

    @Test
    fun `jvm run with JVM arg`() = runSlowTest {
        val testProject = testProject("jvm-run-print-systemprop")
        val result1 = runCli(testProject, "run", "--jvm-args=-Dmy.system.prop=hello")
        assertEquals("my.system.prop=hello", result1.stdout.trim().lines().last())

        val result2 = runCli(testProject, "run", "--jvm-args=\"-Dmy.system.prop=hello world\"")
        assertEquals("my.system.prop=hello world", result2.stdout.trim().lines().last())

        val result3 = runCli(testProject, "run", "--jvm-args=-Dmy.system.prop=hello\\ world")
        assertEquals("my.system.prop=hello world", result3.stdout.trim().lines().last())
    }
}