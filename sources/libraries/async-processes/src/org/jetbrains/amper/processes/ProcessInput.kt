/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import kotlinx.coroutines.channels.Channel
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream

sealed interface ProcessInput {

    /**
     * The child process inherits the standard input from the current process.
     *
     * Warning: `System.setIn()` doesn't change the input to be inherited.
     */
    data object Inherit : ProcessInput {
        override suspend fun writeTo(processStdin: OutputStream) = Unit // do not close the stream here
    }

    /**
     * Nothing is sent to the standard input of the child process, the stream is immediately closed.
     */
    data object Empty : ProcessInput {
        override suspend fun writeTo(processStdin: OutputStream) {
            // We just close the input by default.
            processStdin.close()
        }
    }

    /**
     * The given [input] text is written as UTF-8 in a single write operation to the child process' standard input.
     */
    data class Text(val input: String) : ProcessInput {
        override suspend fun writeTo(processStdin: OutputStream) = processStdin.use {
            it.write(input.encodeToByteArray())
        }
    }

    /**
     * Provides an ability to pipe the output of one process (A) to the input of another (B).
     * Use the [pipeInListener] as an output listener of the process (A), whose output we need as input.
     *
     * When the process (A) terminates, the stdin stream of the process (B) is closed.
     *
     * If the process (B) terminates earlier than the (A) one or if other input error occurs,
     * the pipe is silently dropped.
     */
    class Pipe : ProcessInput {
        private val outputLinesChannel = Channel<String>(capacity = Channel.UNLIMITED)

        val pipeInListener = object : ProcessOutputListener {
            override fun onStdoutLine(line: String, pid: Long) {
                outputLinesChannel.trySend(line)
            }
            override fun onStderrLine(line: String, pid: Long) = Unit

            override fun onProcessTerminated(exitCode: Int, pid: Long) {
                outputLinesChannel.close()
            }
        }

        override suspend fun writeTo(processStdin: OutputStream) {
            try {
                PrintStream(processStdin, true, Charsets.UTF_8).use { stream ->
                    for (line in outputLinesChannel) {
                        stream.println(line)
                    }
                }
            } catch (_: IOException) {
                // broken pipe - just abort
                outputLinesChannel.close()
            }
        }
    }

    /**
     * IMPORTANT: The implementor is responsible for closing the stream in the end.
     */
    suspend fun writeTo(processStdin: OutputStream)
}
