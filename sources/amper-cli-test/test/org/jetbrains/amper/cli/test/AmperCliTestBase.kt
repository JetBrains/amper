/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.AmperCliWithWrapperTestBase
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.LocalAmperPublication
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.TestReporterExtension
import org.jetbrains.amper.test.android.AndroidTools
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.test.assertTrue

abstract class AmperCliTestBase : AmperCliWithWrapperTestBase() {
    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    @RegisterExtension
    private val testReporter = TestReporterExtension()

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

    protected abstract val testDataRoot: Path

    data class AmperCliResult(
        val projectRoot: Path,
        val buildOutputRoot: Path,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
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
        fun assertStdoutContainsLine(expectedLine: String, nOccurrences: Int = 1) {
            val suffix = if (nOccurrences > 1) " $nOccurrences times" else " once"
            val count = stdout.lines().count { it == expectedLine }
            assertTrue("stdout should contain line '$expectedLine'$suffix (got $count occurrences)") {
                count == nOccurrences
            }
        }
    }

    protected suspend fun runCli(
        backendTestProjectName: String,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        stdin: ProcessInput = ProcessInput.Empty,
    ): AmperCliResult {
        val projectRoot = testDataRoot.resolve(backendTestProjectName)
        check(projectRoot.isDirectory()) {
            "Project root is not a directory: $projectRoot"
        }

        return runCli(
            projectRoot,
            *args,
            expectedExitCode = expectedExitCode,
            assertEmptyStdErr = assertEmptyStdErr,
            stdin = stdin,
        )
    }

    protected suspend fun runCliInTempDir(
        backendTestProjectName: String,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        stdin: ProcessInput = ProcessInput.Empty,
    ): AmperCliResult {
        val projectRoot = testDataRoot.resolve(backendTestProjectName)
        check(projectRoot.isDirectory()) {
            "Project root is not a directory: $projectRoot"
        }

        val tempProjectDir = tempRoot / UUID.randomUUID().toString() / projectRoot.fileName
        tempProjectDir.createDirectories()
        projectRoot.copyToRecursively(target = tempProjectDir, overwrite = false, followLinks = true)

        return runCli(
            projectRoot = tempProjectDir,
            args = args,
            expectedExitCode = expectedExitCode,
            assertEmptyStdErr = assertEmptyStdErr,
            stdin = stdin,
        )
    }

    protected suspend fun runCli(
        projectRoot: Path,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        stdin: ProcessInput = ProcessInput.Empty,
        customAmperScriptPath: Path? = tempWrappersDir.resolve(scriptNameForCurrentOs),
    ): AmperCliResult {
        println("Running Amper CLI with '${args.toList()}' on $projectRoot")

        val buildOutputRoot = tempRoot.resolve("build")

        val result = runAmper(
            workingDir = projectRoot,
            args = buildList {
                add("--build-output=$buildOutputRoot")
                add("--shared-caches-root=${Dirs.userCacheRoot}")
                addAll(args)
            },
            environment = mapOf(
                "ANDROID_HOME" to AndroidTools.getOrInstallForTests().androidHome.pathString,
                "AMPER_NO_GRADLE_DAEMON" to "1",
            ),
            bootstrapCacheDir = Dirs.userCacheRoot,
            expectedExitCode = expectedExitCode,
            assertEmptyStdErr = assertEmptyStdErr,
            stdin = stdin,
            customAmperScriptPath = customAmperScriptPath,
        )

        testReporter.publishEntry("Amper[${result.pid}] arguments", args.joinToString(" "))
        testReporter.publishEntry("Amper[${result.pid}] working dir", projectRoot.pathString)
        testReporter.publishEntry("Amper[${result.pid}] exit code", result.exitCode.toString())
        testReporter.publishEntry("Amper[${result.pid}] stdout", result.stdout.ifBlank { "<empty>" })
        testReporter.publishEntry("Amper[${result.pid}] stderr", result.stderr.ifBlank { "<empty>" })

        return AmperCliResult(
            projectRoot = projectRoot,
            buildOutputRoot = buildOutputRoot,
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
            outputListener = object : ProcessOutputListener {
                override fun onStdoutLine(line: String, pid: Long) {
                    println("[xcodebuild out / $pid] $line")
                }

                override fun onStderrLine(line: String, pid: Long) {
                    println("[xcodebuild err / $pid] $line")
                }
            },
        )
    }

    private fun String.fancyPrependIndent(prepend: String): String {
        val trim = trim()
        if (trim.isEmpty()) return trim

        return trim.prependIndent(prepend).trim()
    }
}
