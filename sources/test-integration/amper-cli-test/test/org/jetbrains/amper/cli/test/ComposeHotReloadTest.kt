/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStderrDoesNotContain
import org.jetbrains.amper.cli.test.utils.readTelemetrySpans
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.telemetry.getListAttribute
import org.jetbrains.amper.test.spans.SpansTestCollector
import org.jetbrains.amper.test.spans.spansNamed
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertContains

/**
 * Since on the CI there is no X11 environment, an app cannot be run because the agent also launches a desktop devtools
 * app. The only what happens in this test is it verified that everything is wired correctly.
 *
 * Note: asserting that stderr is empty makes no sense in this case because when there is no X11 the agent writes to
 * stderr about it.
 */
class ComposeHotReloadTest : AmperCliTestBase() {

    @Test
    fun `compose hot reload run wires agent env vars and properties for jvm-app`() = runSlowTest {
        val projectRoot = testProject("compose-hot-reload")

        val result = runCli(
            projectRoot = projectRoot,
            "run",
            "--compose-hot-reload-mode",
            assertEmptyStdErr = false,
        )

        result.readTelemetrySpans().assertHotReloadJavaExecSpan("compose-hot-reload")
    }

    @Test
    fun `compose hot reload run wires agent env vars and properties for multiplatform-lib`() = runSlowTest {
        val projectRoot = testProject("compose-hot-reload-lib")

        val result = runCli(
            projectRoot = projectRoot,
            "run",
            "-m",
            "compose-hot-reload-lib",
            "--main-class",
            "MainKt",
            "--compose-hot-reload-mode",
            assertEmptyStdErr = false,
        )

        result.readTelemetrySpans().assertHotReloadJavaExecSpan("compose-hot-reload-lib")
    }

    @Test
    fun `compose hot reload run wires agent env vars and properties for for jvm-lib`() = runSlowTest {
        val projectRoot = testProject("compose-hot-reload-jvm-lib")

        val result = runCli(
            projectRoot = projectRoot,
            "run",
            "-m",
            "compose-hot-reload-jvm-lib",
            "--main-class",
            "MainKt",
            "--compose-hot-reload-mode",
            assertEmptyStdErr = false,
        )

        result.readTelemetrySpans().assertHotReloadJavaExecSpan("compose-hot-reload-jvm-lib")
    }

    @Test
    fun `compose hot reload on non-compose jvm app should fail`() = runSlowTest {
        val projectRoot = testProject("jvm-run-print-systemprop")

        val result = runCli(
            projectRoot = projectRoot,
            "run",
            "--compose-hot-reload-mode",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        result.assertStderrContains("Compose must be enabled to use Compose Hot Reload mode")
    }

    @Test
    fun `compose hot reload on android app should fail`() = runSlowTest {
        val projectRoot = testProject("compose-resources-demo")

        val result = runCli(
            projectRoot = projectRoot,
            "run",
            "-m", "app-android",
            "--compose-hot-reload-mode",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        result.assertStderrContains("Compose Hot Reload is only supported in jvm/app applications")
    }

    @Test
    fun `compose hot reload with platform android in multi-module should fail`() = runSlowTest {
        val projectRoot = testProject("compose-resources-demo")

        val result = runCli(
            projectRoot = projectRoot,
            "run",
            "--platform=android",
            "--compose-hot-reload-mode",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        result.assertStderrContains("Compose Hot Reload doesn't support running on the 'android' platform")
    }

    @Test
    fun `compose hot reload without module in multi-module picks desktop jvm app`() = runSlowTest {
        val projectRoot = testProject("compose-resources-demo")

        val result = runCli(
            projectRoot = projectRoot,
            "run",
            "--compose-hot-reload-mode",
            assertEmptyStdErr = false,
        )

        result.readTelemetrySpans().assertHotReloadJavaExecSpan("app-jvm")

        // Ensure we did not complain about multiple apps
        result.assertStderrDoesNotContain("There are several matching application modules")
    }

    private fun SpansTestCollector.assertHotReloadJavaExecSpan(moduleName: String) {
        val javaExecSpan = spansNamed("java-exec").assertSingle()

        val jvmArgs = javaExecSpan.getListAttribute("jvm-args")
        assertContains(jvmArgs, "-Dcompose.reload.devToolsEnabled=true")

        val javaAgent = jvmArgs.single { it.startsWith("-javaagent:") }
        assertContains(javaAgent, "hot-reload-agent")

        val envVars = javaExecSpan.getListAttribute("env-vars")
        assertTrue(envVars.any { it.startsWith("AMPER_SERVER_PORT=") })
        assertTrue(envVars.any { it.startsWith("AMPER_BUILD_ROOT=") })
        assertContains(envVars, "AMPER_BUILD_TASK=:$moduleName:reloadJvm")
    }
}
