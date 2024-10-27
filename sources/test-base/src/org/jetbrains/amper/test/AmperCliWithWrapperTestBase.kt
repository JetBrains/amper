/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
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
    private val httpServer = HttpServerExtension(wwwRoot = TestUtil.m2repository)

    /**
     * The files that were downloaded by the Amper wrapper so far in the current test.
     */
    protected val requestedFiles: List<Path>
        get() = httpServer.requestedFiles

    protected val scriptNameForCurrentOs: String = if (OsFamily.current.isWindows) "amper.bat" else "amper"

    companion object {
        @JvmStatic
        @BeforeAll
        fun checkAmperPublication() {
            LocalAmperPublication.checkPublicationIntegrity()
        }
    }

    /**
     * Runs the Amper CLI in the given [workingDir] with the given [args].
     *
     * This function uses the OS-specific wrapper script located in [workingDir] by default, or the given
     * [customAmperScriptPath] if non-null.
     *
     * The [bootstrapCacheDir] is the location where the Amper distribution is downloaded by the script.
     *
     * The [customJavaHome] path points to the home directory of the Java distribution to use for Amper.
     * If null, the script handles the download of a suitable JRE.
     */
    protected suspend fun runAmper(
        workingDir: Path,
        args: List<String>,
        environment: Map<String, String> = emptyMap(),
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        bootstrapCacheDir: Path = TestUtil.sharedTestCaches,
        customJavaHome: Path? = null,
        customAmperScriptPath: Path? = null,
        stdin: ProcessInput = ProcessInput.Empty,
    ): ProcessResult {
        check(workingDir.exists()) { "Cannot run Amper: the specified working directory $workingDir does not exist." }
        check(workingDir.isDirectory()) { "Cannot run Amper: the specified working directory $workingDir is not a directory." }

        val amperScript = customAmperScriptPath ?: workingDir.resolve(if (OsFamily.current.isWindows) "amper.bat" else "amper")
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
        }
        val result = runProcessAndCaptureOutput(
            workingDir = workingDir,
            command = listOf(amperScript.absolutePathString()) + args,
            environment = buildMap {
                // tells the wrapper to download the distribution and JRE through our local HTTP server
                this["AMPER_DOWNLOAD_ROOT"] = httpServer.wwwRootUrl
                this["AMPER_JRE_DOWNLOAD_ROOT"] = httpServer.cacheRootUrl

                this["AMPER_BOOTSTRAP_CACHE_DIR"] = bootstrapCacheDir.pathString

                if (customJavaHome != null) {
                    this["AMPER_JAVA_HOME"] = customJavaHome.pathString
                }
                this["AMPER_JAVA_OPTIONS"] = extraJvmArgs.joinToString(" ")
                putAll(environment)
            },
            input = stdin,
            outputListener = SimplePrintOutputListener(stdoutPrefix = "[amper out] ", stderrPrefix = "[amper err] "),
        )

        assertEquals(
            expected = expectedExitCode,
            actual = result.exitCode,
            message = "Exit code must be $expectedExitCode, but got ${result.exitCode}. Process stderr:\n${result.stderr}"
        )
        if (assertEmptyStdErr) {
            assertTrue(result.stderr.isBlank(), "Process stderr must be empty for Amper call: $amperScript ${args.joinToString(" ")}\nStderr was:\n${result.stderr}")
        }
        return result
    }
}
