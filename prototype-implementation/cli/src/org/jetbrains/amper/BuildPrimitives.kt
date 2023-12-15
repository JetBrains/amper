/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.runInterruptible
import org.jetbrains.amper.util.UnboundedExecutor
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.Callable

/**
 * Ordinary operations like running processes and copying files require
 * a comprehensive support in a build system to make it observable
 */
object BuildPrimitives {
    class ProcessResult(val exitCode: Int, val stdout: StringBuilder, val stderr: StringBuilder)
    suspend fun runProcessAndGetOutput(command: List<String>, workingDir: Path): ProcessResult {
        // make it all cancellable and running in a different context
        @Suppress("BlockingMethodInNonBlockingContext")
        val process = ProcessBuilder()
            .command(*command.toTypedArray())
            .directory(workingDir.toFile())
            .start()

        Futures.submit({ try {
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

        val stdout = runInterruptible { stdoutJob.get() }
        val stderr = runInterruptible { stderrJob.get() }

        val result = ProcessResult(exitCode = rc, stdout = stdout, stderr = stderr)
        return result
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