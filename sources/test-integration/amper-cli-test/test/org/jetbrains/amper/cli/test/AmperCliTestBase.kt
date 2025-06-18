/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.AmperCliWithWrapperTestBase
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.LocalAmperPublication
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.android.AndroidTools
import org.jetbrains.amper.test.processes.TestReporterProcessOutputListener
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.pathString

abstract class AmperCliTestBase : AmperCliWithWrapperTestBase() {
    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    protected val tempRoot: Path
        get() = tempDirExtension.path

    override fun buildDir(): Path = tempRoot

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

    /**
     * Finds the directory of the test project with the given [name].
     * This function does not create a temporary copy, and the test project should not be modified by tests.
     * The [runCli] function uses a temporary location for the `build` directory, which will preserve the test project.
     *
     * The test projects are not expected to contain the Amper wrappers themselves.
     * The [runCli] function uses locally published wrappers by default without modifying the test project.
     */
    protected fun testProject(name: String): Path = Dirs.amperTestProjectsRoot.resolve(name)

    /**
     * Creates a new temporary empty directory to use as a test project.
     * Files may be created by the test in this directory, as it will automatically be cleaned up after the test.
     */
    protected fun newEmptyProjectDir(): Path = tempRoot.resolve("new").also { it.createDirectories() }

    protected suspend fun runCli(
        projectRoot: Path,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        copyToTempDir: Boolean = false,
        modifyTempProjectBeforeRun: (tempProjectDir: Path) -> Unit = {},
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
            modifyTempProjectBeforeRun(tempProjectDir)
            tempProjectDir
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
            outputListener = TestReporterProcessOutputListener("xcodebuild", testReporter),
        )
    }
}
