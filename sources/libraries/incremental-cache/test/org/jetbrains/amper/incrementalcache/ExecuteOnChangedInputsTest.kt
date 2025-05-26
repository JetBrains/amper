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
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ExecuteOnChangedInputsTest {
    @TempDir
    lateinit var tempDir: Path

    private val executeOnChanged by lazy {
        ExecuteOnChangedInputs(stateRoot = tempDir / "incremental.state", codeVersion = "1")
    }
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
    fun changeCodeVersionNumber() {
        val file = tempDir.resolve("file.txt").also { it.writeText("a") }

        fun call(codeVersion: String) = runBlocking {
            ExecuteOnChangedInputs(tempDir / "incremental.state", codeVersion = codeVersion).execute(
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

    @Test
    fun `excluded outputs are ignored for up-to-date checks`() {
        runBlocking {
            val regularOutput = tempDir.resolve("regular.txt")
            val excludedOutput = tempDir.resolve("excluded.txt")

            fun call() = runBlocking {
                executeOnChanged.execute("1", emptyMap(), emptyList()) {
                    regularOutput.writeText("regular-1")
                    excludedOutput.writeText("excluded-1")
                    executionsCount.incrementAndGet()
                    ExecuteOnChangedInputs.ExecutionResult(
                        outputs = listOf(regularOutput, excludedOutput),
                        excludedOutputs = setOf(excludedOutput)
                    )
                }
            }

            call()
            assertEquals(1, executionsCount.get(), "Should execute for the first time")
            assertEquals("regular-1", regularOutput.readText())
            assertEquals("excluded-1", excludedOutput.readText())

            call()
            assertEquals(1, executionsCount.get(), "Should not execute again, as no files changed")

            excludedOutput.writeText("excluded-2")
            call()
            assertEquals(1, executionsCount.get(), "Should not execute again, as only excluded files changed")
            assertEquals("excluded-2", excludedOutput.readText(), "The change to the excluded file should persist")

            // Modify regular output - should trigger re-execution
            regularOutput.writeText("regular-2")
            call()
            assertEquals(2, executionsCount.get(), "Should execute again due to output change")
            assertEquals("regular-1", regularOutput.readText(), "The new exec should overwrite the regular file")
            assertEquals("excluded-1", excludedOutput.readText(), "The new exec should overwrite the excluded file")
        }
    }

    @Test
    fun `excluded file in parent directory output`() {
        runBlocking {
            val outputDir = tempDir.resolve("output-dir")
            val regularOutput = outputDir.resolve("regular.txt")
            val excludedOutput = outputDir.resolve("excluded.txt")

            fun call() = runBlocking {
                executeOnChanged.execute("1", emptyMap(), emptyList()) {
                    outputDir.createDirectories()
                    regularOutput.writeText("regular-1")
                    excludedOutput.writeText("excluded-1")
                    executionsCount.incrementAndGet()
                    ExecuteOnChangedInputs.ExecutionResult(
                        outputs = listOf(outputDir), // Only the directory is in outputs
                        excludedOutputs = setOf(excludedOutput) // But a file inside is excluded
                    )
                }
            }

            call()
            assertEquals(1, executionsCount.get(), "Should execute for the first time")
            assertEquals("regular-1", regularOutput.readText())
            assertEquals("excluded-1", excludedOutput.readText())

            call()
            assertEquals(1, executionsCount.get(), "Should not execute again, as no files changed")

            excludedOutput.writeText("excluded-2")
            call()
            assertEquals(1, executionsCount.get(), "Should not execute again, as only excluded files changed")
            assertEquals("excluded-2", excludedOutput.readText(), "The change to the excluded file should persist")

            regularOutput.writeText("regular-2")
            call()
            assertEquals(2, executionsCount.get(), "Should execute again due to output change")
            assertEquals("regular-1", regularOutput.readText(), "The new exec should overwrite the regular file")
            assertEquals("excluded-1", excludedOutput.readText(), "The new exec should overwrite the excluded file")
        }
    }

    @Test
    fun `excluded subdirectory`() {
        runBlocking {
            val outputDir = tempDir.resolve("output-dir")
            val regularSubdir = outputDir.resolve("regular-subdir")
            val excludedSubdir = outputDir.resolve("excluded-subdir")
            val regularOutput = regularSubdir.resolve("file.txt")
            val excludedOutput = excludedSubdir.resolve("file.txt")

            fun call() = runBlocking {
                executeOnChanged.execute("1", emptyMap(), emptyList()) {
                    regularSubdir.createDirectories()
                    excludedSubdir.createDirectories()

                    outputDir.createDirectories()
                    regularOutput.writeText("regular-1")
                    excludedOutput.writeText("excluded-1")
                    executionsCount.incrementAndGet()
                    ExecuteOnChangedInputs.ExecutionResult(
                        outputs = listOf(outputDir), // The parent directory is in outputs
                        excludedOutputs = setOf(excludedSubdir) // An entire subdirectory is excluded
                    )
                }
            }


            call()
            assertEquals(1, executionsCount.get(), "Should execute for the first time")
            assertEquals("regular-1", regularOutput.readText())
            assertEquals("excluded-1", excludedOutput.readText())

            call()
            assertEquals(1, executionsCount.get(), "Should not execute again, as no files changed")

            excludedOutput.writeText("excluded-2")
            call()
            assertEquals(1, executionsCount.get(), "Should not execute again, as only excluded files changed")
            assertEquals("excluded-2", excludedOutput.readText(), "The change to the excluded file should persist")

            excludedSubdir.resolve("new-excluded-file").writeText("new-excluded")
            call()
            assertEquals(1, executionsCount.get(), "Should not execute again, as only excluded files changed")
            assertTrue(excludedSubdir.resolve("new-excluded-file").exists(), "The new file should stay")

            regularOutput.writeText("regular-2")
            call()
            assertEquals(2, executionsCount.get(), "Should execute again due to output change")
            assertEquals("regular-1", regularOutput.readText(), "The new exec should overwrite the regular file")
            assertEquals("excluded-1", excludedOutput.readText(), "The new exec should overwrite the excluded file")

            excludedSubdir.deleteRecursively()
            call()
            assertEquals(2, executionsCount.get(), "Should not execute again, as the deleted dir was excluded")
            assertFalse(excludedSubdir.exists(), "The excluded directory should stay removed")

            regularSubdir.deleteRecursively()
            call()
            assertEquals(3, executionsCount.get(), "Should execute again due to output change")
            assertEquals("regular-1", regularOutput.readText(), "The new exec should overwrite the regular file")
            assertEquals("excluded-1", excludedOutput.readText(), "The new exec should overwrite the excluded file")
        }
    }
}
