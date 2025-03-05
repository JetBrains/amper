/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperPackageTest : AmperCliTestBase() {

    @Test
    fun `package command produces an executable jar`() = runSlowTest {
        val projectRoot = testProject("spring-boot")
        val r = runCli(
            projectRoot = projectRoot,
            "package"
        )

        assertTrue(
            "Executable jar file should exist after packaging"
        ) {
            (r.buildOutputRoot / "tasks" / "_spring-boot_executableJarJvm" / "spring-boot-jvm-executable.jar").exists()
        }
    }
}
