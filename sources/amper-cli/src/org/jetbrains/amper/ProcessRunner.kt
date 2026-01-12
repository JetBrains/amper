/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.amper.cli.telemetry.setProcessResultAttributes
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.intellij.CommandLineUtils
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.util.ShellQuoting
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.jvm.javaClass

/**
 * Ordinary operations like running processes and copying files require
 * a comprehensive support in a build system to make it observable
 */
class ProcessRunner {
    /**
     * Starts a new process with the given [command] in [workingDir], and awaits the result.
     * While waiting, stdout and stderr are printed to the console, but they are also entirely collected in memory as
     * part of the returned [ProcessResult].
     *
     * It is not supported to write to the standard input of the started process.
     *
     * If a [span] is provided, extra attributes are added to it about the process result (exit code, stdout, stderr).
     *
     * If this function is cancelled before the process has terminated, it kills the process (first normally then
     * forcibly), and cleans the stream readers. If the process is not killable and hangs, this function will also hang
     * instead of returning (otherwise the zombie process could leak).
     *
     * **Note:** since the blocking reads of standard streams are not cancellable, this function may have to wait for
     * the read of the current line to complete before returning. This is to ensure no coroutines are leaked.
     * This wait should be reasonably short anyway because the process is killed on cancellation, so no more output
     * should be written in that case.
     */
    // TODO sometimes capturing the entire stdout/stderr in memory won't work
    //  do we want to offload big (and probably only big outputs) to the disk?
    suspend fun runProcessAndGetOutput(
        workingDir: Path,
        command: List<String>,
        span: Span? = null,
        environment: Map<String, String> = emptyMap(),
        outputListener: ProcessOutputListener,
        redirectErrorStream: Boolean = false,
        input: ProcessInput = ProcessInput.Empty,
    ): ProcessResult {
        logger.debug("[cmd] ${ShellQuoting.quoteArgumentsPosixShellWay(command.toList())}")

        val result = withContext(Dispatchers.IO) {
            // Why quoteCommandLineForCurrentPlatform:
            // ProcessBuilder does not correctly escape its arguments on Windows
            // generally, JDK developers do not think that executed command should receive the same arguments as passed to ProcessBuilder
            // see, e.g., https://bugs.openjdk.org/browse/JDK-8131908
            // this code is mostly tested by AmperBackendTest.simple multiplatform cli on jvm
            runProcessAndCaptureOutput(
                workingDir = workingDir,
                command = CommandLineUtils.quoteCommandLineForCurrentPlatform(command),
                environment = environment,
                redirectErrorStream = redirectErrorStream,
                input = input,
                outputListener = outputListener,
            )
        }
        span?.setProcessResultAttributes(result)
        return result
    }

    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
    }
}

