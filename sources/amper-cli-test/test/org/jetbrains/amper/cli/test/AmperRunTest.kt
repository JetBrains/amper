/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.readTelemetrySpans
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.test.LinuxOnly
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.WindowsOnly
import org.jetbrains.amper.test.spans.kotlinJvmCompilationSpans
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.slf4j.event.Level
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperRunTest : AmperCliTestBase() {

    private val specialCmdChars = "&()[]{}^=;!'+,`~"
    private val argumentsWithSpecialChars = listOf(
        "simple123",
        "my arg2",
        "my arg3 :\"'<>\$ && || ; \"\" $specialCmdChars ${specialCmdChars.chunked(1).joinToString(" ")}",
    )

    @Test
    fun `run command help prints dash dash`() = runSlowTest {
        val r = runCli(projectRoot = testProject("jvm-kotlin-test-smoke"), "run", "--help")

        // Check that '--' is printed before program arguments
        val string = "Usage: amper run [<options>] -- [<app_arguments>]..."

        assertTrue("There should be '$string' in `run --help` output") {
            r.stdout.lines().any { it == string }
        }
    }

    @Test
    fun `mixed java kotlin`() = runSlowTest {
        val result = runCli(projectRoot = testProject("java-kotlin-mixed"), "run")
        result.assertLogStartsWith("Process exited with exit code 0", Level.INFO)
        result.assertStdoutContains("Output: <XYZ>")
    }

    @Test
    fun `simple multiplatform cli on jvm`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("simple-multiplatform-cli"),
            "run", "--module=jvm-cli", "--", *argumentsWithSpecialChars.toTypedArray(),
        )

        val expectedOutput = """Hello Multiplatform CLI 12: JVM World
ARG0: <${argumentsWithSpecialChars[0]}>
ARG1: <${argumentsWithSpecialChars[1]}>
ARG2: <${argumentsWithSpecialChars[2]}>"""
        result.assertStdoutContains(expectedOutput)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli on mac`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("simple-multiplatform-cli"),
            "run", "--module=macos-cli", "--", *argumentsWithSpecialChars.toTypedArray(),
        )

        val expectedOutput = """Hello Multiplatform CLI 12: Mac World
ARG0: <${argumentsWithSpecialChars[0]}>
ARG1: <${argumentsWithSpecialChars[1]}>
ARG2: <${argumentsWithSpecialChars[2]}>"""
        result.assertStdoutContains(expectedOutput)
    }

    @Test
    @LinuxOnly
    fun `simple multiplatform cli on linux`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("simple-multiplatform-cli"),
            "run", "--module=linux-cli", "--platform=linuxX64", "--", *argumentsWithSpecialChars.toTypedArray(),
        )

        val expectedOutput = """Hello Multiplatform CLI 12: Linux World
ARG0: <${argumentsWithSpecialChars[0]}>
ARG1: <${argumentsWithSpecialChars[1]}>
ARG2: <${argumentsWithSpecialChars[2]}>"""
        result.assertStdoutContains(expectedOutput)
    }

    @Test
    @WindowsOnly
    fun `simple multiplatform cli on windows`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("simple-multiplatform-cli"),
            "run", "--module=windows-cli", "--", *argumentsWithSpecialChars.toTypedArray(),
        )

        val expectedOutput = """Hello Multiplatform CLI 12: Windows (Mingw) World
ARG0: <${argumentsWithSpecialChars[0]}>
ARG1: <${argumentsWithSpecialChars[1]}>
ARG2: <${argumentsWithSpecialChars[2]}>"""
        result.assertStdoutContains(expectedOutput)
    }

    @Test
    fun `run with jvm resource from dependency`() = runSlowTest {
        val result = runCli(projectRoot = testProject("jvm-resources"), "run")
        result.assertStdoutContains("String from resources: Stuff From Resources")
    }

    @Test
    fun `run with access to resource as dir`() = runSlowTest {
        // This project tests reading a directory entry from the resources.
        // NoSuchElementException means it failed.
        runCli(projectRoot = testProject("jvm-read-resource-dir"), "run")
    }

    @Test
    fun `run spring boot`() = runSlowTest {
        // Spring-core relies on ClassLoader::getResources for component scanning (to find bean definitions in the jar).
        // It expects the jar to contain directory entries and not just files.
        // Spring's PathMatchingResourcePatternResolver::doFindAllClassPathResources represents packages as a resources
        // (ex. org/springframework/boot/). So directory entries need to be resources inside the jar.
        // If directory entries are missing, the symptom is that Spring can't load the context.
        runCli(projectRoot = testProject("spring-boot"), "run")
    }

    @Test
    fun `run executable jar`() = runSlowTest {
        runCli(projectRoot = testProject("spring-boot"), "run", "-v", "release")
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
        val projectRoot = testProject("jvm-run-print-systemprop")
        val result1 = runCli(projectRoot, "run", "--jvm-args=-Dmy.system.prop=hello")
        result1.assertStdoutContains("my.system.prop=hello")

        val result2 = runCli(projectRoot, "run", "--jvm-args=\"-Dmy.system.prop=hello world\"")
        result2.assertStdoutContains("my.system.prop=hello world")

        val result3 = runCli(projectRoot, "run", "--jvm-args=-Dmy.system.prop=hello\\ world")
        result3.assertStdoutContains("my.system.prop=hello world")
    }

    @Test
    fun `do not call kotlinc again if sources were not changed`() = runSlowTest {
        val projectRoot = testProject("jvm-language-version-1.9")

        val result1 = runCli(projectRoot = projectRoot, "run")
        result1.assertStdoutContains("Hello, world!")
        result1.readTelemetrySpans().kotlinJvmCompilationSpans.assertSingle()

        val result2 = runCli(projectRoot = projectRoot, "run")
        result2.assertStdoutContains("Hello, world!")
        result2.readTelemetrySpans().kotlinJvmCompilationSpans.assertNone()
    }

    @Test
    fun `exit code is propagated for JVM`() = runSlowTest {
        val projectRoot = testProject("jvm-exit-code")

        val result = runCli(projectRoot = projectRoot, "run", expectedExitCode = 5, assertEmptyStdErr = false)
        result.assertStderrContains("Process exited with exit code 5")
    }

    @Test
    @LinuxOnly
    fun `exit code is propagated for Linux`() = runSlowTest {
        val projectRoot = testProject("multiplatform-cli-exit-code")

        val result = runCli(
            projectRoot = projectRoot,
            "run", "--module", "linux-cli", "--platform=linuxX64",
            expectedExitCode = 5, assertEmptyStdErr = false,
        )
        result.assertStderrContains("Process exited with exit code 5")
    }

    @Test
    @MacOnly
    fun `exit code is propagated for macOS`() = runSlowTest {
        val projectRoot = testProject("multiplatform-cli-exit-code")

        val result = runCli(
            projectRoot = projectRoot,
            "run", "--module", "macos-cli",
            expectedExitCode = 5, assertEmptyStdErr = false,
        )
        result.assertStderrContains("Process exited with exit code 5")
    }

    @Test
    @WindowsOnly
    fun `exit code is propagated for Windows`() = runSlowTest {
        val projectRoot = testProject("multiplatform-cli-exit-code")

        val result = runCli(
            projectRoot = projectRoot,
            "run", "--module", "windows-cli",
            expectedExitCode = 5, assertEmptyStdErr = false,
        )
        result.assertStderrContains("Process exited with exit code 5")
    }

    @Test
    fun `spring-boot-kotlin allOpen enabled should work`() = runSlowTest {
        val projectRoot = testProject("spring-boot-kotlin")

        val result = runCli(projectRoot = projectRoot, "run")

        result.assertStdoutContains("Started MainKt")
    }
}
