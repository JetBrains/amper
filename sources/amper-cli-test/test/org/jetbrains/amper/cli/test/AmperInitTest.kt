/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertContainsRelativeFiles
import org.jetbrains.amper.cli.test.utils.assertFileContentEquals
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.test.LocalAmperPublication
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperInitTest : AmperCliTestBase() {

    @Test
    fun `init generates a project from the given template`() = runSlowTest {
        val newRoot = newEmptyProjectDir()
        runCli(newRoot, "init", "multiplatform-cli")

        newRoot.assertContainsRelativeFiles(
            ".editorconfig",
            "amper",
            "amper.bat",
            "jvm-cli/module.yaml",
            "linux-cli/module.yaml",
            "macos-cli/module.yaml",
            "project.yaml",
            "shared/module.yaml",
            "shared/src/World.kt",
            "shared/src/main.kt",
            "shared/src@jvm/World.kt",
            "shared/src@linux/World.kt",
            "shared/src@macos/World.kt",
            "shared/src@mingw/World.kt",
            "shared/test/test.kt",
            "windows-cli/module.yaml",
        )
    }

    @Test
    fun `init overwrites existing wrapper scripts`() = runSlowTest {
        val newRoot = newEmptyProjectDir()

        val bashWrapper = newRoot.resolve("amper")
        val batWrapper = newRoot.resolve("amper.bat")

        bashWrapper.writeText("w1")
        batWrapper.writeText("w2")

        runCli(newRoot, "init", "multiplatform-cli")

        assertTrue(batWrapper.readText().count { it == '\r' } > 10,
            "Windows wrapper must have \\r in line separators: $batWrapper")
        assertTrue(bashWrapper.readText().count { it == '\r' } == 0,
            "Unix wrapper must not have \\r in line separators: $bashWrapper")

        if (OsFamily.current.isUnix) {
            assertTrue("Unix wrapper must be executable: $bashWrapper") { bashWrapper.isExecutable() }
        }

        assertFileContentEquals(LocalAmperPublication.wrapperSh, bashWrapper)
        assertFileContentEquals(LocalAmperPublication.wrapperBat, batWrapper)
    }

    @Test
    fun `init doesn't replace existing files - single`() = runSlowTest {
        val newRoot = newEmptyProjectDir()
        val existingModuleFile = newRoot.resolve("jvm-cli/module.yaml").also { it.createParentDirectories() }
        existingModuleFile.writeText("some text in module.yaml")

        val r = runCli(newRoot, "init", "multiplatform-cli", expectedExitCode = 1, assertEmptyStdErr = false)
        val expectedStderr = """
            ERROR: The following files already exist in the output directory and would be overwritten by the generation:
              jvm-cli/module.yaml

            Please move, rename, or delete them before running the command again.
        """.trimIndent()
        r.assertStderrContains(expectedStderr)

        newRoot.assertContainsRelativeFiles("jvm-cli/module.yaml")
        assertEquals("some text in module.yaml", existingModuleFile.readText())
    }

    @Test
    fun `init doesn't replace existing files - multiple`() = runSlowTest {
        val newRoot = newEmptyProjectDir()
        val existingProjectFile = newRoot.resolve("project.yaml")
        val existingModuleFile = newRoot.resolve("jvm-cli/module.yaml").createParentDirectories()
        existingProjectFile.writeText("some text in project.yaml")
        existingModuleFile.writeText("some text in module.yaml")

        val r = runCli(newRoot, "init", "multiplatform-cli", expectedExitCode = 1, assertEmptyStdErr = false)
        val expectedStderr = """
            ERROR: The following files already exist in the output directory and would be overwritten by the generation:
              jvm-cli/module.yaml
              project.yaml
            
            Please move, rename, or delete them before running the command again.
        """.trimIndent()
        r.assertStderrContains(expectedStderr)

        newRoot.assertContainsRelativeFiles("jvm-cli/module.yaml", "project.yaml")
        assertEquals("some text in project.yaml", existingProjectFile.readText())
        assertEquals("some text in module.yaml", existingModuleFile.readText())
    }
}