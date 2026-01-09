/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.AmperCliWithWrapperTestBase
import org.jetbrains.amper.test.JavaHomeMode
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.LocalAmperPublication
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.android.AndroidTools
import org.jetbrains.amper.test.processes.TestReporterProcessOutputListener
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.io.path.useLines

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

    protected fun copyProjectToTempDir(projectRoot: Path): Path {
        val tempProjectDir = tempRoot / UUID.randomUUID().toString() / projectRoot.fileName
        tempProjectDir.createDirectories()
        projectRoot.copyToRecursively(target = tempProjectDir, overwrite = false, followLinks = true)
        return tempProjectDir
    }

    protected suspend fun runCli(
        projectDir: Path,
        vararg args: String,
        expectedExitCode: Int? = 0,
        assertEmptyStdErr: Boolean = true,
        copyToTempDir: Boolean = false,
        modifyTempProjectBeforeRun: (tempProjectDir: Path) -> Unit = {},
        stdin: ProcessInput = ProcessInput.Empty,
        amperJvmArgs: List<String> = emptyList(),
        customAmperScriptPath: Path = tempWrappersDir.resolve(scriptNameForCurrentOs),
        amperJavaHomeMode: JavaHomeMode = JavaHomeMode.ForceUnset,
        configureAndroidHome: Boolean = false,
        environment: Map<String, String> = emptyMap(),
    ): AmperCliResult {
        println("Running Amper CLI with '${args.toList()}' on $projectDir")

        val effectiveProjectDir = if (copyToTempDir) {
            val tempProjectDir = copyProjectToTempDir(projectDir)
            modifyTempProjectBeforeRun(tempProjectDir)
            tempProjectDir
        } else {
            projectDir
        }

        val buildOutputRoot = tempRoot.resolve("build")

        val amperVersion = findAmperVersion(customAmperScriptPath)
        val useNewArgs = amperVersion.knowsAboutNewRootAndBuildArgs()
        val effectiveArgs = buildList {
            if (useNewArgs) {
                add("--shared-cache-dir=${Dirs.userCacheRoot.absolutePathString()}")
            } else {
                add("--build-output=$buildOutputRoot")
                add("--shared-caches-root=${Dirs.userCacheRoot.absolutePathString()}")
            }
            addAll(args)
        }

        val result = runAmper(
            workingDir = effectiveProjectDir,
            args = effectiveArgs,
            environment = buildMap {
                if (configureAndroidHome) {
                    putAll(AndroidTools.getOrInstallForTests().environment())
                }
                if (useNewArgs) {
                    put("AMPER_BUILD_DIR", buildOutputRoot.pathString)
                }
                put("AMPER_NO_GRADLE_DAEMON", "1")
                putAll(environment)
            },
            bootstrapCacheDir = Dirs.userCacheRoot,
            expectedExitCode = expectedExitCode,
            assertEmptyStdErr = assertEmptyStdErr,
            stdin = stdin,
            amperJvmArgs = amperJvmArgs,
            amperJavaHomeMode = amperJavaHomeMode,
            customAmperScriptPath = customAmperScriptPath,
        )

        testReporter.publishEntry("Amper[${result.pid}] arguments", args.joinToString(" "))
        testReporter.publishEntry("Amper[${result.pid}] working dir", effectiveProjectDir.pathString)
        testReporter.publishEntry("Amper[${result.pid}] exit code", result.exitCode.toString())
        val logsDir = result.logsDir
        if (logsDir != null) {
            testReporter.publishDirectory(logsDir)
        }

        return result
    }

    private fun ComparableVersion.knowsAboutNewRootAndBuildArgs(): Boolean {
        // We want our dev versions to be considered less than release versions (0.10.0-dev-123 < 0.10.0).
        // ComparableVersion hardcodes the known modifiers (alpha, beta, etc.), so the best we can do is consider dev
        // versions alpha (we don't use alpha anyway in Amper, so this is an acceptable workaround).
        val adjustedVersion = ComparableVersion(canonical.replace("-dev-", "-alpha-"))

        // happens to work fine with 1.0-SNAPSHOT too :D
        return adjustedVersion >= ComparableVersion("0.10.0-dev-3701")
    }

    private fun findAmperVersion(customAmperScriptPath: Path): ComparableVersion {
        val versionLine = customAmperScriptPath.useLines { lines ->
            lines.firstOrNull { "amper_version=" in it }
                ?: error(
                    "Version line not found in $customAmperScriptPath. First few lines:\n" +
                            lines.take(20).joinToString("\n")
                )
        }
        return ComparableVersion(versionLine.substringAfter("amper_version=").trim())
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
