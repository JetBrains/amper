/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.Platform
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.processes.TestReporterProcessOutputListener
import org.jetbrains.amper.test.server.HttpServerExtension
import org.jetbrains.amper.test.server.WwwResult
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.RegisterExtension
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AmperCliWithWrapperTestBase {

    @RegisterExtension
    protected val testReporter = TestReporterExtension()

    @RegisterExtension
    private val httpServer = HttpServerExtension(
        wwwRoot = Dirs.m2repository,
        wwwInterceptor = { urlPath ->
            val amperCliDistRegex = Regex("org/jetbrains/amper/cli/(?<version>[^/]+)/.*")
            val match = amperCliDistRegex.matchEntire(urlPath)
            if (match != null && match.groups["version"]!!.value != "1.0-SNAPSHOT") {
                // we only want to serve 1.0-SNAPSHOT from local m2 (and serve other versions from the real maven)
                WwwResult.Download(url = "https://packages.jetbrains.team/maven/p/amper/amper/$urlPath")
            } else {
                WwwResult.LocalFile
            }
        }
    )

    /**
     * The URL to use to download the locally published Amper wrapper and distribution (from the local test server).
     */
    protected val localAmperDistRepoUrl: String
        get() = httpServer.wwwRootUrl

    /**
     * The files that were downloaded by the Amper wrapper so far in the current test.
     */
    protected val requestedFiles: List<Path>
        get() = httpServer.requestedFiles

    protected val scriptNameForCurrentOs: String = if (OsFamily.current.isWindows) "amper.bat" else "amper"

    companion object {

        // This is to limit resource consumption when using lots of parallel CLI tests
        private const val maxParallelAmperProcesses = 4
        private val amperProcessSemaphore = Semaphore(permits = maxParallelAmperProcesses)

        @JvmStatic
        @BeforeAll
        fun checkAmperPublication() {
            LocalAmperPublication.checkPublicationIntegrity()
        }
    }

    protected fun baseEnvironmentForWrapper(): Map<String, String> = buildMap {
        // tells the wrapper to download the distribution and JRE through our local HTTP server
        this["AMPER_DOWNLOAD_ROOT"] = httpServer.wwwRootUrl
        this["AMPER_JRE_DOWNLOAD_ROOT"] = httpServer.cacheRootUrl
        this["AMPER_BOOTSTRAP_CACHE_DIR"] = Dirs.userCacheRoot.pathString
    }

    /**
     * Runs the Amper CLI in the given [workingDir] with the given [args].
     *
     * This function uses the OS-specific wrapper script located in [workingDir] by default, or the given
     * [customAmperScriptPath] if non-null.
     *
     * @param bootstrapCacheDir the location where the Amper script should download the Amper distribution and JRE
     * @param amperJavaHomeMode defines how the test Amper process should get its JRE.
     * See the docs on [AmperJavaHomeMode] values for more info.
     */
    protected suspend fun runAmper(
        workingDir: Path,
        args: List<String>,
        environment: Map<String, String> = emptyMap(),
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        bootstrapCacheDir: Path? = null,
        amperJvmArgs: List<String> = emptyList(),
        amperJavaHomeMode: AmperJavaHomeMode = AmperJavaHomeMode.Inherit,
        customAmperScriptPath: Path? = null,
        stdin: ProcessInput = ProcessInput.Empty,
    ): ProcessResult {
        check(workingDir.exists()) { "Cannot run Amper: the specified working directory $workingDir does not exist." }
        check(workingDir.isDirectory()) { "Cannot run Amper: the specified working directory $workingDir is not a directory." }

        val isWindows = OsFamily.current.isWindows
        val amperScript = customAmperScriptPath ?: workingDir.resolve(if (isWindows) "amper.bat" else "amper")
        check(amperScript.exists()) {
            "Amper script not found at $amperScript\n" +
                    "You can use LocalAmperPublication.setupWrappersIn(dir) to copy wrappers into the test project dir."
        }
        check(amperScript.isExecutable()) { "Cannot run Amper script because it is not executable: $amperScript" }
        check(amperScript.isRegularFile()) { "Cannot run Amper script because it is not a file: $amperScript" }

        val isDebuggingTest = ManagementFactory.getRuntimeMXBean().inputArguments.any { it.startsWith("-agentlib:") }
        val extraJvmArgs = buildList {
            // When debugging tests, we run the Amper CLI with jdwp to be able to attach a debugger to it.
            // The CLI process will wait for a debugger to attach on a dynamic port. You can click "Attach debugger"
            // in the IDEA console to automatically launch and attach a remote debugger.
            if (isDebuggingTest) {
                add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y")
            }
            addAll(amperJvmArgs)
        }
        val currentPlatformForIJ = if (isWindows) Platform.WINDOWS else Platform.UNIX
        val result = amperProcessSemaphore.withPermit {
            runProcessAndCaptureOutput(
                workingDir = workingDir,
                // proper quotes/escaping, workaround for the time-old bug https://bugs.openjdk.org/browse/JDK-8131908
                command = CommandLineUtil.toCommandLine(amperScript.absolutePathString(), args, currentPlatformForIJ),
                environment = buildMap {
                    putAll(baseEnvironmentForWrapper())

                    // Override (and add to) the base env
                    bootstrapCacheDir?.let {
                        this["AMPER_BOOTSTRAP_CACHE_DIR"] = it.pathString
                    }

                    when (amperJavaHomeMode) {
                        is AmperJavaHomeMode.Inherit -> {} // do nothing and just get whatever is there
                        // explicit reset (cannot call remove because we don't have the actual env from ProcessBuilder here)
                        is AmperJavaHomeMode.ForceUnset -> this["AMPER_JAVA_HOME"] = ""
                        is AmperJavaHomeMode.Custom -> this["AMPER_JAVA_HOME"] = amperJavaHomeMode.jreHomePath.pathString
                    }

                    this["AMPER_JAVA_OPTIONS"] = extraJvmArgs.joinToString(" ")
                    putAll(environment)
                },
                input = stdin,
                outputListener = TestReporterProcessOutputListener("amper", testReporter),
            )
        }

        val stdout = result.stdout.prependIndentWithEmptyMark("[amper out] ")
        val stderr = result.stderr.prependIndentWithEmptyMark("[amper err] ")
        val relevantAmperOutput = if (expectedExitCode == 0) stderr else "$stdout\n$stderr"
        assertEquals(
            expected = expectedExitCode,
            actual = result.exitCode,
            message = "Exit code must be $expectedExitCode, but got ${result.exitCode} for Amper call (PID ${result.pid}):\n$amperScript ${args.joinToString(" ")}\nOutput:\n$relevantAmperOutput"
        )
        if (assertEmptyStdErr) {
            assertTrue(result.stderr.isBlank(), "Process stderr must be empty for Amper call:\n$amperScript ${args.joinToString(" ")}\nAmper STDERR:\n${result.stderr}")
        }
        return result
    }
}

/**
 * Defines how a test Amper process should get its JRE (especially when tests are run using Amper itself).
 */
sealed class AmperJavaHomeMode {
    /**
     * Inherit `AMPER_JAVA_HOME` from the caller's environment.
     * When tests are run by Amper, the test Amper process will use the same JRE as the Amper process running the tests.
     */
    data object Inherit : AmperJavaHomeMode()
    /**
     * Explicitly reset AMPER_JAVA_HOME (make it empty) even if the caller's environment contains it (for example, when
     * running tests with Amper itself).
     * This forces the test Amper process to download the JRE to the AMPER_BOOTSTRAP_CACHE_DIR if not present there.
     */
    data object ForceUnset : AmperJavaHomeMode()
    /**
     * Use the given [jreHomePath] as JRE home for the test Amper process.
     */
    data class Custom(val jreHomePath: Path) : AmperJavaHomeMode()
}

private fun String.prependIndentWithEmptyMark(indent: String): String =
    trim().ifEmpty { "<empty>" }.prependIndent(indent)
