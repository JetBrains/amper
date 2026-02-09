/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.ContinuationInterceptor

/**
 * The result of a completed process.
 */
class ProcessResult(
    /**
     * The command line that was executed.
     */
    val command: List<String>,
    /**
     * The ID identifying this process when it was alive.
     */
    val pid: Long,
    /**
     * The exit code of the process.
     */
    val exitCode: Int,
    /**
     * If [errorStreamRedirected] is false, [stdout] contains the whole standard output of the process, decoded as
     * UTF-8 text.
     * If [errorStreamRedirected] is true, [stdout] contains both the merged stdout and stderr of the process,
     * interlaced as they were written by the process.
     */
    val stdout: String,
    /**
     * The whole standard error stream of the process, decoded as UTF-8 text, or the empty string if
     * [errorStreamRedirected] is true (in that case, the stderr content is in [stdout], interlaced with the standard
     * output).
     */
    val stderr: String,
    /**
     * Whether the error stream was redirected to the standard output of the process.
     * If true, [stderr] is empty and [stdout] contains both streams interlaced together.
     */
    val errorStreamRedirected: Boolean,
)

/**
 * Starts a new process based on this [ProcessBuilder], and awaits its completion.
 * While waiting, stdout and stderr are sent to the given [outputListener], but are also fully captured in memory, so
 * they can be returned as a [ProcessResult]. Make sure the process doesn't output too much data, otherwise prefer
 * [run][ProcessBuilder.run].
 *
 * The given [input] is used to send data to the standard input of the started process (if non-empty).
 *
 * > Note: this function replaces any previous stream configuration made via
 * [redirectOutput][ProcessBuilder.redirectOutput], [redirectInput][ProcessBuilder.redirectInput],
 * [redirectError][ProcessBuilder.redirectError], but it respects
 * [redirectErrorStream][ProcessBuilder.redirectErrorStream].
 *
 * If the current coroutine is canceled before the process has terminated, the process is killed (first normally, then
 * forcibly), and the stream readers cleaned up. If the process is not killable and hangs, this function will also hang
 * instead of returning (otherwise the zombie process could leak).
 *
 * **Note:** since the blocking reads of standard streams are not cancellable, this function may have to wait for the
 * read of the current line to complete before returning (on cancellation). This is to ensure no coroutines are leaked.
 * This wait should be reasonably short anyway because the process is killed on cancellation, so no more output
 * should be written in that case.
 *
 * If the JVM is terminated gracefully (Ctrl+C / SIGINT), this function **requests the process destruction** but doesn't
 * wait for its completion (we mustn't block the JVM shutdown).
 *
 * @return a [ProcessResult] encapsulating information about the process, including its entire stdout and stderr.
 */
internal suspend fun ProcessBuilder.runAndCaptureOutput(
    input: ProcessInput = ProcessInput.Empty,
    outputListener: ProcessOutputListener = ProcessOutputListener.NOOP,
    redirectErrorStream: Boolean = false,
    onStart: (pid: Long) -> Unit = {},
): ProcessResult {
    contract {
        callsInPlace(onStart, InvocationKind.EXACTLY_ONCE)
    }
    val capture = ProcessOutputListener.InMemoryCapture()
    val pid: Long
    val exitCode = run(
        outputListener = outputListener + capture,
        input = input,
        onStart = {
            pid = it
            onStart(it)
        },
    )
    return ProcessResult(
        command = command().toList(),
        exitCode = exitCode,
        pid = pid,
        stdout = capture.stdout,
        stderr = capture.stderr,
        errorStreamRedirected = redirectErrorStream,
    )
}

/**
 * Starts a new process based on this [ProcessBuilder], and awaits its completion.
 * While waiting, stdout and stderr are sent to the given [outputListener].
 *
 * The given [input] is used to send data to the standard input of the started process (if non-empty).
 *
 * > Note: this function replaces any previous stream configuration made via
 * [redirectOutput][ProcessBuilder.redirectOutput], [redirectInput][ProcessBuilder.redirectInput],
 * [redirectError][ProcessBuilder.redirectError], but it respects
 * [redirectErrorStream][ProcessBuilder.redirectErrorStream].
 *
 * If the current coroutine is canceled before the process has terminated, the process is killed (first normally, then
 * forcibly), and the stream readers cleaned up. If the process is not killable and hangs, this function will also hang
 * instead of returning (otherwise the zombie process could leak).
 *
 * **Note:** since the blocking reads of standard streams are not cancellable, this function may have to wait for the
 * read of the current line to complete before returning (on cancellation). This is to ensure no coroutines are leaked.
 * This wait should be reasonably short anyway because the process is killed on cancellation, so no more output
 * should be written in that case.
 *
 * If the JVM is terminated gracefully (Ctrl+C / SIGINT), this function **requests the process destruction** but doesn't
 * wait for its completion (we mustn't block the JVM shutdown).
 *
 * @return the exit code of the process
 */
internal suspend fun ProcessBuilder.run(
    outputListener: ProcessOutputListener,
    input: ProcessInput = ProcessInput.Empty,
    onStart: (pid: Long) -> Unit = {},
): Int {
    contract {
        callsInPlace(onStart, InvocationKind.EXACTLY_ONCE)
    }
    return withContext(Dispatchers.IO) {
        redirectOutput(ProcessBuilder.Redirect.PIPE)
        redirectError(ProcessBuilder.Redirect.PIPE)
        redirectInput(input.stdinRedirection)
        start().withGuaranteedTermination { process ->
            onStart(process.pid())
            launch {
                // input writing is asynchronous
                input.writeTo(process.outputStream)
            }
            process.awaitListening(outputListener)
        }
    }
}

private val ProcessInput.stdinRedirection: ProcessBuilder.Redirect
    get() = when (this) {
        ProcessInput.Inherit -> ProcessBuilder.Redirect.INHERIT
        ProcessInput.Empty,
        is ProcessInput.Text,
        is ProcessInput.Pipe -> ProcessBuilder.Redirect.PIPE
    }

/**
 * Starts a new process based on this [ProcessBuilder], and awaits its completion.
 *
 * While waiting, all standard streams of the child process are inherited from the current process.
 * This function is useful when starting interactive processes that may require user input, or when running processes
 * for which the output should just be printed normally to the console like the rest of the code.
 *
 * > Note: this function replaces any previous stream configuration made via
 * [redirectOutput][ProcessBuilder.redirectOutput], [redirectInput][ProcessBuilder.redirectInput],
 * [redirectError][ProcessBuilder.redirectError], but it respects
 * [redirectErrorStream][ProcessBuilder.redirectErrorStream].
 *
 * If the current coroutine is canceled before the process has terminated, the process is killed (first normally, then
 * forcibly). If the process is not killable and hangs, this function will also hang instead of returning (otherwise
 * the zombie process could leak).
 *
 * If the JVM is terminated gracefully (Ctrl+C / SIGINT), this function **requests the process destruction** but doesn't
 * wait for its completion (we mustn't block the JVM shutdown).
 *
 * @return the exit code of the process
 */
internal suspend fun ProcessBuilder.runWithInheritedIO(onStart: (pid: Long) -> Unit = {}): Int {
    contract {
        callsInPlace(onStart, InvocationKind.EXACTLY_ONCE)
    }
    return withContext(Dispatchers.IO) {
        inheritIO()
            .start()
            .withGuaranteedTermination { process ->
                onStart(process.pid())
                process.onExit().await().exitValue()
            }
    }
}
