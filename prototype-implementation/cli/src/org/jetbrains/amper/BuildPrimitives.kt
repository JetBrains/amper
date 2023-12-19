/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import com.google.common.util.concurrent.Futures
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.util.ShellQuoting
import org.jetbrains.amper.util.UnboundedExecutor
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.Callable
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString

/**
 * Ordinary operations like running processes and copying files require
 * a comprehensive support in a build system to make it observable
 */
object BuildPrimitives {
    /**
     * Delete one file on background, report errors if any to log warning only
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun deleteLater(file: Path) {
        // just in case global scope will be cancelled
        file.toFile().deleteOnExit()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                file.deleteIfExists()
            } catch (t: Throwable) {
                logger.warn("Unable to delete file '$file': ${t.message}", t)
            }
        }
    }

    // TODO sometimes handling entire stdout/stderr in memory won't work
    //  do we want to offload big (and probably only big outputs) to the disk?
    class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)
    suspend fun runProcessAndGetOutput(command: List<String>, workingDir: Path): ProcessResult {
        // make it all cancellable and running in a different context
        @Suppress("BlockingMethodInNonBlockingContext")
        val process = ProcessBuilder()
            .command(*command.toTypedArray())
            .directory(workingDir.toFile())
            .start()

        // we are not interested whether this operation fails or timeouts
        Futures.submit({ try {
            @Suppress("BlockingMethodInNonBlockingContext")
            process.outputStream.close()
        } catch (t: Throwable) {
            logger.warn("Unable to close process stdin: ${t.message}", t)
        }}, UnboundedExecutor.INSTANCE)

        // we may receive waitFor earlier than we read all pipes
        // also read from non-networking streams is not cancellable, so do it with real threads on background
        val stdoutJob = Futures.submit(Callable { readStreamAndPrintToConsole(process.inputStream, System.out) }, UnboundedExecutor.INSTANCE)
        val stderrJob = Futures.submit(Callable { readStreamAndPrintToConsole(process.errorStream, System.err) }, UnboundedExecutor.INSTANCE)

        // TODO must terminate the process as well, better on background
        //  cancellation should be covered by tests, it's almost impossible to get it right from the first time
        val rc = runInterruptible { process.waitFor() }

        val stdout = runInterruptible { stdoutJob.get() }.toString()
        val stderr = runInterruptible { stderrJob.get() }.toString()

        val result = ProcessResult(exitCode = rc, stdout = stdout, stderr = stderr)
        return result
    }

    suspend fun runProcessAndAssertExitCode(command: List<String>, workingDir: Path, span: Span) {
        require(command.isNotEmpty())

        logger.info("Calling ${ShellQuoting.quoteArgumentsPosixShellWay(command)}")

        val result = runProcessAndGetOutput(command, workingDir)
        val stdout = result.stdout
        val stderr = result.stderr

        span.setAttribute("exit-code", result.exitCode.toLong())
        span.setAttribute("stdout", stdout)
        span.setAttribute("stderr", stderr)

        if (result.exitCode != 0) {
            error(
                "${command.first()} exited with exit code ${result.exitCode}" +
                        (if (stderr.isNotEmpty()) "\nSTDERR:\n${stderr}\n" else "") +
                        (if (stdout.isNotEmpty()) "\nSTDOUT:\n${stdout}\n" else "")
            )
        }
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

    private fun readStreamAndPrintToConsole(inputStream: InputStream, consoleStream: PrintStream): StringBuilder {
        val sb = StringBuilder()
        inputStream.reader().forEachLine {
            sb.appendLine(it)
            consoleStream.println(it)
        }
        return sb
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}