/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.incrementalcache

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class ExecuteOnChangedInputsTest {
    @TempDir
    lateinit var tempDir: Path

    private val executeOnChanged by lazy { ExecuteOnChangedInputs(tempDir / "incremental.state") }
    private val executionsCount = AtomicInteger(0)

    @Test
    fun trackingFile() {
        val file = tempDir.resolve("file.txt")

        fun call() = runBlocking {
            executeOnChanged.execute("1", emptyMap(), listOf(file)) {
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(emptyList())
            }
        }

        // initial, MISSING state
        call()
        assertEquals(executionsCount.get(), 1)

        // up-to-date, file is still MISSING
        call()
        assertEquals(executionsCount.get(), 1)

        // changed
        file.writeText("1")
        call()
        assertEquals(executionsCount.get(), 2)

        // up-to-date
        call()
        assertEquals(executionsCount.get(), 2)
    }

    @Test
    fun changeAmperBuildNumber() {
        val file = tempDir.resolve("file.txt").also { it.writeText("a") }

        fun call(amperBuild: String) = runBlocking {
            ExecuteOnChangedInputs(tempDir / "incremental.state", currentAmperBuildNumber = amperBuild).execute(
                "1", emptyMap(), listOf(file)) {
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(emptyList())
            }
        }

        // initial
        call("1")
        assertEquals(executionsCount.get(), 1)

        // up-to-date
        call("1")
        assertEquals(executionsCount.get(), 1)

        // changed
        call("2")
        assertEquals(executionsCount.get(), 2)

        // up-to-date
        call("2")
        assertEquals(executionsCount.get(), 2)
    }

    @Test
    fun trackingFileInSubdirectory() {
        val dir = tempDir.resolve("dir").also { it.createDirectories() }
        val file = dir.resolve("file.txt")

        fun call() = runBlocking {
            executeOnChanged.execute("1", emptyMap(), listOf(dir)) {
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(emptyList())
            }
        }

        // initial, MISSING state
        call()
        assertEquals(executionsCount.get(), 1)

        // up-to-date, file is still MISSING
        call()
        assertEquals(executionsCount.get(), 1)

        // changed
        file.writeText("1")
        call()
        assertEquals(executionsCount.get(), 2)

        // up-to-date
        call()
        assertEquals(executionsCount.get(), 2)
    }

    @Test
    fun trackingEmptyDirectories() {
        val dir = tempDir.resolve("dir").also { it.createDirectories() }
        val subdir = dir.resolve("subdir")

        fun call() = runBlocking {
            executeOnChanged.execute("1", emptyMap(), listOf(dir)) {
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(emptyList())
            }
        }

        // initial, MISSING state
        call()
        assertEquals(executionsCount.get(), 1)

        // up-to-date, subdir is still MISSING
        call()
        assertEquals(executionsCount.get(), 1)

        // changed
        subdir.createDirectories()
        call()
        assertEquals(executionsCount.get(), 2)

        // up-to-date
        call()
        assertEquals(executionsCount.get(), 2)
    }

    @Test
    fun `executes on missing output`() {
        runBlocking {
            val output = tempDir.resolve("out.txt")
            val result1 = executeOnChanged.execute("1", emptyMap(), emptyList()) {
                output.writeText("1")
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(listOf(output))
            }
            assertEquals(listOf(output), result1.outputs)
            assertEquals("1", output.readText())

            // up-to-date
            val result2 = executeOnChanged.execute("1", emptyMap(), emptyList()) {
                output.writeText("2")
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(listOf(output))
            }
            assertEquals(listOf(output), result2.outputs)
            assertEquals("1", output.readText())

            output.deleteExisting()

            // output was deleted
            val result3 = executeOnChanged.execute("1", emptyMap(), emptyList()) {
                output.writeText("3")
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(listOf(output))
            }
            assertEquals(listOf(output), result3.outputs)
            assertEquals("3", output.readText())
        }
        assertEquals(2, executionsCount.get())
    }

    @Test
    fun `output properties`() {
        runBlocking {
            val result1 = executeOnChanged.execute("1", emptyMap(), emptyList()) {
                ExecuteOnChangedInputs.ExecutionResult(emptyList(), mapOf("k" to "v", "e" to ""))
            }
            assertEquals("e:|k:v", result1.outputProperties.entries.sortedBy { it.key }.joinToString("|") { "${it.key}:${it.value}"})

            val result2 = executeOnChanged.execute("1", emptyMap(), emptyList()) {
                fail("should not reach")
            }
            assertEquals("e:|k:v", result2.outputProperties.entries.sortedBy { it.key }.joinToString("|") { "${it.key}:${it.value}"})
        }
    }

    @Test
    fun `reporting missing output must fail`() {
        assertFailsWith(NoSuchFileException::class) {
            runBlocking {
                executeOnChanged.execute("1", emptyMap(), emptyList()) {
                    ExecuteOnChangedInputs.ExecutionResult(listOf(tempDir.resolve("1.out")))
                }
            }
        }
    }
}
