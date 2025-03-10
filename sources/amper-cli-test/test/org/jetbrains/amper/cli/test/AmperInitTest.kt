/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperInitTest : AmperCliTestBase() {

    @Test
    fun `init works`() = runSlowTest {
        val p = tempRoot.resolve("new").also { it.createDirectories() }
        runCli(p, "init", "multiplatform-cli")

        val files = p.walk()
            .map { it.relativeTo(p).pathString.replace("\\", "/") }
            .filter { !it.startsWith("build/") }
            .sorted()
            .joinToString("\n")
        assertEquals(
            """
                .editorconfig
                amper
                amper.bat
                jvm-cli/module.yaml
                linux-cli/module.yaml
                macos-cli/module.yaml
                project.yaml
                shared/module.yaml
                shared/src/World.kt
                shared/src/main.kt
                shared/src@jvm/World.kt
                shared/src@linux/World.kt
                shared/src@macos/World.kt
                shared/src@mingw/World.kt
                shared/test/test.kt
                windows-cli/module.yaml
            """.trimIndent(),
            files
        )
    }

    @Test
    fun `init won't replace existing files`() = runSlowTest {
        val p = tempRoot.resolve("new").also { it.createDirectories() }
        val exampleFile = p.resolve("jvm-cli/module.yaml").also { it.createParentDirectories() }
        exampleFile.writeText("some text")

        val r = runCli(p, "init", "multiplatform-cli", expectedExitCode = 1, assertEmptyStdErr = false)
        val stderr = r.stderr.replace("\r", "")

        val expectedText = "ERROR: Files already exist in the project root:\n  jvm-cli/module.yaml"
        assertTrue(message = "stderr output does not contain '$expectedText':\n$stderr") {
            stderr.contains(expectedText)
        }

        val files = p.walk()
            .map { it.relativeTo(p).pathString.replace("\\", "/") }
            .sorted()
            .joinToString("\n")
        assertEquals(
            """
                jvm-cli/module.yaml
            """.trimIndent(),
            files
        )
        assertEquals("some text", exampleFile.readText())
    }
}