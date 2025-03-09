/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.test.logs.readLogs
import org.jetbrains.amper.cli.test.otlp.serialization.decodeOtlpTraces
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.AmperCliWithWrapperTestBase
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.LocalAmperPublication
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.android.AndroidTools
import org.jetbrains.amper.test.processes.TestReporterProcessOutputListener
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.event.Level
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class AmperCliTestBase : AmperCliWithWrapperTestBase() {
    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    protected val tempRoot: Path
        get() = tempDirExtension.path

    companion object {
        /**
         * A temp directory where we placed the wrapper scripts that will be used to run Amper.
         * We don't want to add them to the test projects themselves, because we don't want to pollute git.
         */
        private val tempWrappersDir: Path by lazy {
            Dirs.tempDir.resolve("local-cli-wrappers").createDirectories().also {
                LocalAmperPublication.setupWrappersIn(it)
            }
        }
    }

    protected fun testProject(name: String): Path = Dirs.amperTestProjectsRoot.resolve(name)

    data class AmperCliResult(
        val projectRoot: Path,
        val buildOutputRoot: Path,
        val logsDir: Path?, // null if it doesn't exist (e.g. the command didn't write logs)
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val infoLogs by lazy {
            logsDir?.resolve("info.log")?.readLogs()
                ?: fail("The logs dir doesn't exist, cannot get info logs")
        }

        val debugLogs by lazy {
            logsDir?.resolve("debug.log")?.readLogs()
                ?: fail("The logs dir doesn't exist, cannot get debug logs")
        }

        val telemetrySpans by lazy {
            val tracesFile = logsDir?.resolve("opentelemetry_traces.jsonl")
                ?: fail("The logs dir doesn't exist, cannot get OpenTelemetry traces")
            assertTrue(tracesFile.exists(), "OpenTelemetry traces file not found at $tracesFile")
            Json.decodeOtlpTraces(tracesFile.readLines())
        }

        fun assertSomeStdoutLineContains(text: String) {
            assertTrue("No line in stdout contains the text '$text':\n" + stdout.trim()) {
                stdout.lineSequence().any { text in it }
            }
        }
        fun assertStdoutContains(text: String) {
            assertTrue("Stdout does not contain the text '$text':\n" + stdout.trim()) {
               text in stdout
            }
        }
        fun assertStdoutDoesNotContain(text: String) {
            assertFalse("Stdout should not contain the text '$text':\n" + stdout.trim()) {
               text in stdout
            }
        }
        fun assertStderrContains(text: String) {
            assertTrue("Stdout does not contain the text '$text':\n" + stderr.trim()) {
               text in stderr
            }
        }
        fun assertStdoutContainsLine(expectedLine: String, nOccurrences: Int = 1) {
            val suffix = if (nOccurrences > 1) " $nOccurrences times" else " once"
            val count = stdout.lines().count { it == expectedLine }
            assertTrue("stdout should contain line '$expectedLine'$suffix (got $count occurrences)") {
                count == nOccurrences
            }
        }
        fun assertLogStartsWith(msgPrefix: String, level: Level) {
            assertTrue("Log message with level=$level and starting with '$msgPrefix' was not found") {
                val logs = if (level >= Level.INFO) infoLogs else debugLogs
                logs.any { it.level == level && it.message.startsWith(msgPrefix) }
            }
        }
        fun assertLogContains(text: String, level: Level) {
            assertTrue("Log message with level=$level and containing '$text' was not found") {
                val logs = if (level >= Level.INFO) infoLogs else debugLogs
                logs.any { it.level == level && text in it.message }
            }
        }
    }

    protected suspend fun runCli(
        projectRoot: Path,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        copyToTempDir: Boolean = false,
        stdin: ProcessInput = ProcessInput.Empty,
        amperJvmArgs: List<String> = emptyList(),
        customAmperScriptPath: Path? = tempWrappersDir.resolve(scriptNameForCurrentOs),
        configureAndroidHome: Boolean = true,
        environment: Map<String, String> = emptyMap(),
    ): AmperCliResult {
        println("Running Amper CLI with '${args.toList()}' on $projectRoot")

        val effectiveProjectRoot = if (copyToTempDir) {
            val tempProjectDir = tempRoot / UUID.randomUUID().toString() / projectRoot.fileName
            tempProjectDir.createDirectories()
            projectRoot.copyToRecursively(target = tempProjectDir, overwrite = false, followLinks = true)
        } else {
            projectRoot
        }

        val buildOutputRoot = tempRoot.resolve("build")

        val result = runAmper(
            workingDir = effectiveProjectRoot,
            args = buildList {
                add("--build-output=$buildOutputRoot")
                add("--shared-caches-root=${Dirs.userCacheRoot}")
                addAll(args)
            },
            environment = buildMap {
                if (configureAndroidHome) {
                    putAll(AndroidTools.getOrInstallForTests().environment())
                }
                put("AMPER_NO_GRADLE_DAEMON", "1")
                putAll(environment)
            },
            bootstrapCacheDir = Dirs.userCacheRoot,
            expectedExitCode = expectedExitCode,
            assertEmptyStdErr = assertEmptyStdErr,
            stdin = stdin,
            amperJvmArgs = amperJvmArgs,
            customAmperScriptPath = customAmperScriptPath,
        )

        testReporter.publishEntry("Amper[${result.pid}] arguments", args.joinToString(" "))
        testReporter.publishEntry("Amper[${result.pid}] working dir", effectiveProjectRoot.pathString)
        testReporter.publishEntry("Amper[${result.pid}] exit code", result.exitCode.toString())

        return AmperCliResult(
            projectRoot = effectiveProjectRoot,
            buildOutputRoot = buildOutputRoot,
            // Logs dirs contain the date, so max() gives the latest.
            // This should be correct because we don't run the CLI concurrently in a single test.
            logsDir = (buildOutputRoot / "logs").takeIf { it.exists() }?.listDirectoryEntries()?.maxOrNull(),
            stdout = result.stdout,
            stderr = result.stderr,
            exitCode = result.exitCode,
        )
    }

    protected suspend fun runXcodebuild(
        vararg buildArgs: String,
        workingDir: Path = tempRoot,
    ): ProcessResult {
        return runProcessAndCaptureOutput(
            workingDir = workingDir,
            command = listOf(
                "xcrun", "xcodebuild",
                *buildArgs,
                "build",
            ),
            environment = baseEnvironmentForWrapper(),
            outputListener = TestReporterProcessOutputListener("xcodebuild", testReporter),
        )
    }
}
