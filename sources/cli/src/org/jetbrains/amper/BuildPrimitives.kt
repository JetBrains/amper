/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.intellij.CommandLineUtils
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.awaitAndGetAllOutput
import org.jetbrains.amper.processes.withGuaranteedTermination
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.pathString

/**
 * Ordinary operations like running processes and copying files require
 * a comprehensive support in a build system to make it observable
 */
object BuildPrimitives {
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
        command: List<String>,
        workingDir: Path,
        span: Span? = null,
        environment: Map<String, String> = emptyMap(),
    ): ProcessResult {
        require(command.isNotEmpty()) { "Cannot start a process with an empty command line" }

        val result = withContext(Dispatchers.IO) {
            // Why quoteCommandLineForCurrentPlatform:
            // ProcessBuilder does not correctly escape its arguments on Windows
            // generally, JDK developers do not think that executed command should receive the same arguments as passed to ProcessBuilder
            // see, e.g., https://bugs.openjdk.org/browse/JDK-8131908
            // this code is mostly tested by AmperBackendTest.simple multiplatform cli on jvm
            val process = ProcessBuilder(CommandLineUtils.quoteCommandLineForCurrentPlatform(command))
                .directory(workingDir.toFile())
                .also { it.environment().putAll(environment) }
                .start()

            process.withGuaranteedTermination {
                try {
                    process.outputStream.close()
                } catch (t: IOException) {
                    // we are not interested whether this operation fails
                    logger.warn("Unable to close process stdin: ${t.message}", t)
                }
                process.awaitAndGetAllOutput(
                    onStdoutLine = System.out::println,
                    onStderrLine = System.err::println,
                )
            }
        }
        if (span != null) {
            span.setAttribute("exit-code", result.exitCode.toLong())
            span.setAttribute("stdout", result.stdout)
            span.setAttribute("stderr", result.stderr)
        }
        return result
    }

    // defaults are selected for build system-stuff
    @OptIn(ExperimentalPathApi::class)
    suspend fun copy(from: Path, to: Path, overwrite: Boolean = false, followLinks: Boolean = true) {
        // Do not change coroutine context, we want to stay in tasks pool

        spanBuilder("copy")
            .setAttribute("from", from.pathString)
            .setAttribute("to", to.pathString)
            .useWithScope {
                // TODO is it really interruptible here?
                runInterruptible {
                    from.copyToRecursively(to, overwrite = overwrite, followLinks = followLinks)
                }
            }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
