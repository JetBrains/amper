/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.processes.GradleDaemonShutdownHook
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.AmperCliWithWrapperTestBase
import org.jetbrains.amper.test.LocalAmperPublication
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.TestReporterExtension
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.TestUtil.androidHome
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

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
            TestUtil.tempDir.resolve("local-cli-wrappers").createDirectories().also {
                LocalAmperPublication.setupWrappersIn(it)
            }
        }
    }

    protected abstract val testDataRoot: Path

    data class CliTempDirRunResult(
        val processResult: ProcessResult,
        val tempProjectDir: Path,
    )

    protected suspend fun runCli(
        backendTestProjectName: String,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        stdin: ProcessInput = ProcessInput.Empty,
    ): ProcessResult {
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
    ): CliTempDirRunResult {
        val projectRoot = testDataRoot.resolve(backendTestProjectName)
        check(projectRoot.isDirectory()) {
            "Project root is not a directory: $projectRoot"
        }

        val tempProjectDir = tempRoot / UUID.randomUUID().toString() / projectRoot.fileName
        tempProjectDir.createDirectories()
        projectRoot.copyToRecursively(target = tempProjectDir, overwrite = false, followLinks = true)

        return CliTempDirRunResult(
            tempProjectDir = tempProjectDir,
            processResult = runCli(
                projectRoot = tempProjectDir,
                args = args,
                expectedExitCode = expectedExitCode,
                assertEmptyStdErr = assertEmptyStdErr,
                stdin = stdin,
            )
        )
    }

    protected suspend fun runCli(
        projectRoot: Path,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        stdin: ProcessInput = ProcessInput.Empty,
        customAmperScriptPath: Path? = tempWrappersDir.resolve(scriptNameForCurrentOs),
    ): ProcessResult {
        println("Running Amper CLI with '${args.toList()}' on $projectRoot")

        val buildOutputRoot = tempRoot.resolve("build")

        val result = runAmper(
            workingDir = projectRoot,
            args = buildList {
                add("--build-output")
                add(buildOutputRoot.pathString)
                addAll(args)
            },
            environment = mapOf(
                "ANDROID_HOME" to androidHome.pathString,
                GradleDaemonShutdownHook.NO_DAEMON_ENV to "1",
            ),
            expectedExitCode = expectedExitCode,
            assertEmptyStdErr = assertEmptyStdErr,
            stdin = stdin,
            customAmperScriptPath = customAmperScriptPath,
        )

        val stdout = result.stdout.fancyPrependIndent("STDOUT: ").ifEmpty { "STDOUT: <no-output>" }
        val stderr = result.stderr.fancyPrependIndent("STDERR: ").ifEmpty { "STDERR: <no-output>" }

        // TODO also assert no ERRORs or WARNs in logs by default

        val message = "Result of running Amper CLI with '${args.toList()}' on $projectRoot:\n$stdout\n$stderr"

        // it should be enough to publish to junit reporter, but IDEA runner does not pick it up
        // if tests were run from Gradle
        testReporter.publishEntry(message)

        return result
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
