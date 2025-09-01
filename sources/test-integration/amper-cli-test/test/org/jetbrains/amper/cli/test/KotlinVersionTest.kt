/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertLogStartsWith
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.readTelemetrySpans
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.cli.test.utils.withTelemetrySpans
import org.jetbrains.amper.test.WindowsOnly
import org.jetbrains.amper.test.spans.assertEachKotlinJvmCompilationSpan
import org.jetbrains.amper.test.spans.assertEachKotlinNativeCompilationSpan
import org.jetbrains.amper.test.spans.assertKotlinJvmCompilationSpan
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.slf4j.event.Level
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class KotlinVersionTest : AmperCliTestBase() {

    @Test
    fun `run kotlin hello world with compiler version 2_0_0`() = runSlowTest {
        val result = runCli(projectRoot = testProject("kotlin-helloworld-custom-version-2.0.0"), "run")

        result.assertLogStartsWith("Process exited with exit code 0", level = Level.INFO)

        result.readTelemetrySpans().assertKotlinJvmCompilationSpan {
            doesNotHaveCompilerArgument("-language-version")
            doesNotHaveCompilerArgument("-api-version")
            hasAmperModule("kotlin-helloworld-custom-version-2.0.0")
        }
    }

    @Test
    fun `run kotlin hello world with compiler version 2_2_0`() = runSlowTest {
        val result = runCli(projectRoot = testProject("kotlin-helloworld-custom-version-2.2.0"), "run")

        result.assertLogStartsWith("Process exited with exit code 0", level = Level.INFO)

        result.readTelemetrySpans().assertKotlinJvmCompilationSpan {
            doesNotHaveCompilerArgument("-language-version")
            doesNotHaveCompilerArgument("-api-version")
            hasAmperModule("kotlin-helloworld-custom-version-2.2.0")
        }
    }

    @Test
    fun `run kotlin hello world with compiler version 2_2_10`() = runSlowTest {
        val result = runCli(projectRoot = testProject("kotlin-helloworld-custom-version-2.2.10"), "run")

        result.assertLogStartsWith("Process exited with exit code 0", level = Level.INFO)

        result.readTelemetrySpans().assertKotlinJvmCompilationSpan {
            doesNotHaveCompilerArgument("-language-version=2.2")
            hasAmperModule("kotlin-helloworld-custom-version-2.2.10")
        }
    }

    @Test
    fun `run jvm with language version 2_1`() = runSlowTest {
        val result = runCli(projectRoot = testProject("jvm-language-version-2.1"), "run")

        result.assertLogStartsWith("Process exited with exit code 0", level = Level.INFO)

        result.readTelemetrySpans().assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version=2.1")
            hasAmperModule("jvm-language-version-2.1")
        }
    }

    @Test
    fun `run jvm with language version 2_0`() = runSlowTest {
        val result = runCli(projectRoot = testProject("jvm-language-version-2.0"), "run")

        result.assertStdoutContains("Hello, world!")
        result.assertLogStartsWith("Process exited with exit code 0", level = Level.INFO)

        result.readTelemetrySpans().assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version=2.0")
            hasAmperModule("jvm-language-version-2.0")
        }
    }

    @Test
    fun `build native with language version 2_1`() = runSlowTest {
        val result = runCli(projectRoot = testProject("native-language-version-2.1"), "build")

        result.readTelemetrySpans().assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version=2.1")
        }
    }

    @Test
    fun `build native with language version 2_0`() = runSlowTest {
        val result = runCli(projectRoot = testProject("native-language-version-2.0"), "build")

        result.readTelemetrySpans().assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version=2.0")
        }
    }

    @Test
    @WindowsOnly
    fun `run native with language version 2_1`() = runSlowTest {
        val result = runCli(projectRoot = testProject("native-language-version-2.1"), "run")

        result.assertStdoutContains("Hello, native!")
        result.assertLogStartsWith("Process exited with exit code 0", level = Level.INFO)

        result.readTelemetrySpans().assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version=2.1")
        }
    }

    @Test
    @WindowsOnly
    fun `run native with language version 2_0`() = runSlowTest {
        val result = runCli(projectRoot = testProject("native-language-version-2.0"), "run")

        result.assertStdoutContains("Hello, native!")
        result.assertLogStartsWith("Process exited with exit code 0", level = Level.INFO)

        result.readTelemetrySpans().assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version=2.0")
        }
    }

    @Test
    fun `build multiplatform with language version 2_1`() = runSlowTest {
        val result = runCli(projectRoot = testProject("multiplatform-language-version-2.1"), "build")

        result.withTelemetrySpans {
            assertEachKotlinJvmCompilationSpan {
                hasCompilerArgument("-language-version=2.1")
            }
            assertEachKotlinNativeCompilationSpan {
                hasCompilerArgument("-language-version=2.1")
            }
        }
    }

    @Test
    fun `build multiplatform with language version 2_0`() = runSlowTest {
        val result = runCli(projectRoot = testProject("multiplatform-language-version-2.0"), "build")

        result.withTelemetrySpans {
            assertEachKotlinJvmCompilationSpan {
                hasCompilerArgument("-language-version=2.0")
            }
            assertEachKotlinNativeCompilationSpan {
                hasCompilerArgument("-language-version=2.0")
            }
        }
    }
}