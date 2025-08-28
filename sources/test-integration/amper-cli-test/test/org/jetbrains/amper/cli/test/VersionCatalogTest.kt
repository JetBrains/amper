/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class VersionCatalogTest : AmperCliTestBase() {

    @Test
    fun testCatalogAtRoot() = runSlowTest {
        runCli(testProject("version-catalog-root"), "build")
        val result = runCli(testProject("version-catalog-root"), "show", "dependencies")
        result.assertStdoutContains("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    }

    @Test
    fun testCatalogUnderGradle() = runSlowTest {
        runCli(testProject("version-catalog-gradle"), "build")
        val result = runCli(testProject("version-catalog-root"), "show", "dependencies")
        result.assertStdoutContains("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    }

    @Test
    fun testBothCatalogs_gradleIsIgnored() = runSlowTest {
        val result = runCli(
            projectRoot = testProject(name = "version-catalog-root-and-gradle"),
            "build",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        result.assertStderrContains("No catalog value for the key libs.kotlinx.datetime")
    }
}
